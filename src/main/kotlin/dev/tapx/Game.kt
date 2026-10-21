package dev.tapx

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/** What the presentation layer (menus, sounds) wants to hear about. */
interface GameEvents {
    fun onTap(damage: Double, crit: Boolean, auto: Boolean) {}
    fun onKill(m: Monster, gold: Double, gems: Int) {}
    fun onSpawn(m: Monster) {}
    fun onStage(skipped: Int) {}
    fun onBossFail() {}
    fun onSkill(id: String, damage: Double) {}
    fun onPetAttack(damage: Double) {}
    fun onAllyAttack(id: String, damage: Double) {}
    fun onAchievement(group: AchGroup, target: Double, gems: Int) {}
}

class Monster(val maxHp: Double, val boss: Boolean, val mimic: Boolean, val name: String, val seed: Long, val lord: Boolean = false) {
    var hp = maxHp
    var dead = false
    var age = 0.0
    var deathT = 0.0
    var hitT = 0.0
}

class GearRoll(val gear: Gear, val isNew: Boolean)

/** All rules of the game for one player. No Bukkit calls, so it can run on any thread. */
class Game(var d: PlayerData, private val events: GameEvents) {
    var monster: Monster? = null
    var bossTimer = 0.0
    private var autoTapAcc = 0.0
    private var rainTimer = 0.0
    private var nextSpawn = 0.0
    /** Seconds until each ally's next attack. Allies strike once every [ALLY_COOLDOWN] seconds. */
    private val allyTimers = HashMap<String, Double>()

    // ---------------------------------------------------------- lookups
    fun art(id: String) = d.artifacts[id] ?: 0
    fun heroLvl(id: String) = d.heroes[id] ?: 0
    fun skillLvl(id: String) = d.skills[id] ?: 0
    fun buffOn(id: String) = (d.buffs[id] ?: 0.0) > 0
    fun petLevel(id: String?) = d.pets[id]?.lvl ?: 0
    /** Skill tree node level. */
    fun st(id: String) = d.skillTree[id] ?: 0
    fun owns(id: String) = id in d.owned
    fun shopLevel(id: String) = d.shopLevels[id] ?: 0

    fun gearValue(id: String): Double {
        val g = gearDef(id) ?: return 0.0
        val lvl = d.gear[id] ?: return 0.0
        return slotDef(g.slot).base * RARITIES[g.r].mult * (1 + 0.5 * (lvl - 1))
    }

    private fun gearBonus(stat: String) =
        GEAR_SLOTS.filter { it.stat == stat }.sumOf { s -> d.equipped[s.id]?.let { gearValue(it) } ?: 0.0 }

    fun petBonus(stat: String): Double {
        val p = petDef(d.activePet) ?: return 0.0
        return if (p.stat == stat) p.per * petLevel(p.id) else 0.0
    }

    // ---------------------------------------------------------- formulas
    fun monsterHp(stage: Int) = 10 * 1.39.pow(stage - 1) * (1 + ((stage - 1) / 5) * 0.08)
    fun isLordStage(stage: Int) = stage % 25 == 0
    fun bossHp(stage: Int) = monsterHp(stage) * 6 * (1 - 0.04 * art("crown")) * (if (isLordStage(stage)) 3.0 else 1.0)
    fun goldMult() = (1 + 0.25 * art("idol")) * (1 + gearBonus("gold")) * (1 + petBonus("gold")) * (1 + 0.1 * st("r_greed")) *
        (if (owns("vip_gold")) 2.0 else 1.0) * (if (buffOn("golden_hour")) 2.0 else 1.0) *
        (if (buffOn("midas")) skillPower("midas", skillLvl("midas")) else 1.0)
    fun killGold(stage: Int) = ceil(monsterHp(stage) * 0.2) * goldMult()

    private fun allDamageMult() = (1 + 0.25 * art("blade")) * (1 + 0.1 * st("w_vets")) * (if (owns("double_dmg")) 2.0 else 1.0) *
        (if (buffOn("surge")) 2.0 else 1.0)
    private fun bossDamageMult() = 1 + petBonus("boss") + 0.1 * st("k_bane") + 0.1 * art("slayer")

    fun heroMilestoneMult(lvl: Int) = HERO_MILESTONES.filter { lvl >= it.first }.fold(1.0) { m, x -> m * x.second }
    fun heroBaseDps(h: Hero, lvl: Int) = if (lvl > 0) h.dps * lvl * heroMilestoneMult(lvl) else 0.0
    fun heroCost(h: Hero, lvl: Int) = h.cost * 1.075.pow(lvl) * max(0.5, 1 - 0.02 * st("w_bargain"))
    fun allyMult() = (1 + 0.35 * art("banner")) * (1 + gearBonus("ally")) * (1 + petBonus("ally")) * (1 + 0.15 * st("w_rally")) * allDamageMult()
    fun rawHeroDps() = HEROES.sumOf { heroBaseDps(it, heroLvl(it.id)) }
    fun heroDps(buffed: Boolean = true) =
        rawHeroDps() * allyMult() * (if (buffed && buffOn("warcry")) skillPower("warcry", skillLvl("warcry")) else 1.0)
    /** Damage of one ally's attack (it attacks once per [ALLY_COOLDOWN] seconds). */
    fun allyHit(h: Hero) = heroBaseDps(h, heroLvl(h.id)) * ALLY_COOLDOWN * allyMult() *
        (if (buffOn("warcry")) skillPower("warcry", skillLvl("warcry")) else 1.0)

    fun tapBase(l: Int) = l * 1.05.pow(l - 1)
    fun tapCost(l: Int) = 5 * 1.074.pow(l - 1)
    fun tapDamage(buffed: Boolean = true): Double {
        val base = tapBase(d.tapLevel) + (0.03 + 0.005 * st("w_drill")) * rawHeroDps() * allyMult()
        return base * (if (buffOn("battle_frenzy")) 3.0 else 1.0) * (1 + 0.4 * art("gauntlet")) * (1 + gearBonus("tap")) * (1 + petBonus("tap")) * (1 + 0.15 * st("k_edge")) * allDamageMult() *
            (if (buffed && buffOn("berserk")) skillPower("berserk", skillLvl("berserk")) else 1.0)
    }

    fun critChance() = min(0.9, 0.02 + 0.01 * art("clover") + 0.005 * st("k_eye") + (if (buffOn("eagle")) skillPower("eagle", skillLvl("eagle")) / 100 else 0.0))
    fun critMult() = 8 * (1 + 0.5 * art("fang")) * (1 + gearBonus("crit")) * (1 + petBonus("crit")) * (1 + 0.25 * st("k_heavy"))
    fun bossTime() = BOSS_TIME + 2 * art("hourglass")
    fun maxMana() = 50 + 5 * art("crystal") + 5 * st("s_deep") + (if (owns("mana_well")) 25 else 0)
    fun manaRegen() = (1.0 / 3) * (1 + 0.1 * art("crystal")) * (1 + petBonus("mana")) * (1 + 0.1 * st("s_flow"))
    fun spellDuration() = 30.0 + 4 * art("tome") + 3 * st("s_long")
    fun thunderMult() = skillPower("thunder", skillLvl("thunder")) * (1 + 0.25 * st("s_storm") + 0.4 * art("totem") + petBonus("thunder"))
    private fun autoTapRate() =
        (if (buffOn("frenzy")) skillPower("frenzy", skillLvl("frenzy")) else 0.0) + (if (buffOn("phantom")) 6.0 else 0.0) + shopLevel("auto_tap")
    fun mimicChance() = 0.025 + 0.01 * art("key") + 0.005 * st("r_mimic")
    fun skipChance() = 0.02 * art("boots") + 0.01 * st("r_warp")
    fun wispRate() = 1 + 0.08 * art("lantern")

    // ---------------------------------------------------------- skill tree
    fun spTotal() = d.stats.bestStage / 20 + d.stats.prestiges * 2 + d.bonusSp
    fun spFree() = spTotal() - d.skillTree.values.sum()
    fun buyNode(id: String): Boolean {
        val n = SKILL_TREE.firstOrNull { it.id == id } ?: return false
        if (st(id) >= n.max || spFree() <= 0) return false
        d.skillTree[id] = st(id) + 1
        return true
    }

    fun relicsFor(stage: Int) =
        if (stage < PRESTIGE_STAGE) 0L else floor((stage - 15.0).pow(1.6) / 6 * (1 + 0.1 * d.stats.prestiges)).toLong()
    fun artifactCost(a: Artifact, lvl: Int) = ceil(a.base * 1.45.pow(lvl)).toLong()
    fun skillCost(s: Skill, lvl: Int) = s.cost * 12.0.pow(lvl)
    fun petDamage() = if (d.activePet != null) tapDamage() * (5 + 3 * petLevel(d.activePet)) * (1 + 0.3 * art("collar")) else 0.0

    // Bulk buying for geometric costs.
    fun bulkCost(c0: Double, r: Double, n: Int) = c0 * (r.pow(n) - 1) / (r - 1)
    private fun maxAffordable(c0: Double, r: Double, gold: Double): Int =
        if (gold < c0) 0 else max(1, floor(ln(1 + gold * (r - 1) / c0) / ln(r)).toInt())
    fun buyAmount(c0: Double, r: Double) = if (d.mult == 0) max(1, maxAffordable(c0, r, d.gold)) else d.mult

    // ---------------------------------------------------------- combat
    fun spawnMonster() {
        val boss = !d.farming && d.wave >= WAVES
        val mimic = !boss && Random.nextDouble() < mimicChance()
        val lord = boss && isLordStage(d.stage)
        val hp = if (boss) bossHp(d.stage) else monsterHp(d.stage)
        // Bosses are seeded by stage so a retried boss is the same one.
        val seed = if (boss) d.stage * 7919L + 13 else Random.nextLong()
        val rnd = Random(seed)
        val baseName = NAME_A.random(rnd) + NAME_B.random(rnd)
        val m = when {
            mimic -> Monster(hp, false, true, "Treasure Mimic", seed)
            lord -> Monster(hp, true, false, "Titan Lord $baseName", seed, lord = true)
            boss -> Monster(hp, true, false, "$baseName ${TITLES.random(rnd)}", seed)
            else -> Monster(hp, false, false, baseName, seed)
        }
        monster = m
        if (boss) bossTimer = bossTime()
        events.onSpawn(m)
    }

    private fun dealDamage(amount: Double) {
        val m = monster ?: return
        if (m.dead) return
        m.hp -= if (m.boss) amount * bossDamageMult() else amount
        if (m.hp <= 0) {
            m.hp = 0.0
            killMonster(m)
        }
    }

    fun addGold(g: Double) {
        d.gold += g
        d.stats.goldEarned += g
    }

    private fun killMonster(m: Monster) {
        m.dead = true
        var g = killGold(d.stage)
        if (m.boss) g *= 5 * (1 + 0.25 * st("r_loot")) * (if (m.lord) 3 else 1)
        if (m.mimic) g *= 12 * (1 + 0.5 * art("map"))
        addGold(g)
        d.stats.kills++
        // Gems are the paid currency: bosses only rarely drop one.
        var gems = 0
        if (m.boss) {
            d.stats.bosses++
            if (Random.nextDouble() < (if (m.lord) 0.15 else 0.03)) gems = if (m.lord) 2 else 1
        }
        if (m.lord) d.relics += max(1, d.stage / 25)
        d.gems += gems
        events.onKill(m, g, gems)
        if (m.boss) advanceStage() else if (!d.farming) d.wave = min(WAVES, d.wave + 1)
        nextSpawn = 0.45
    }

    private fun advanceStage() {
        val n = if (Random.nextDouble() < skipChance()) 2 else 1
        d.stage += n
        d.stats.stagesCleared += n
        d.wave = 0
        d.farming = false
        d.maxStage = max(d.maxStage, d.stage)
        d.stats.bestStage = max(d.stats.bestStage, d.stage)
        events.onStage(n)
    }

    fun tap(auto: Boolean) {
        d.stats.taps++
        val m = monster ?: return
        if (m.dead) return
        var dmg = tapDamage()
        val crit = Random.nextDouble() < critChance()
        if (crit) {
            dmg *= critMult()
            d.stats.crits++
        }
        events.onTap(dmg, crit, auto)
        dealDamage(dmg)
        if (d.activePet != null && !auto) {
            d.petCharge++
            if (d.petCharge >= PET_CHARGE) {
                d.petCharge = 0
                val pd = petDamage()
                events.onPetAttack(pd)
                dealDamage(pd)
            }
        }
    }

    fun fightBoss() {
        if (!d.farming) return
        d.farming = false
        d.wave = WAVES
        spawnMonster()
    }

    fun leaveBoss() {
        val m = monster ?: return
        if (!m.boss || m.dead) return
        d.farming = true
        spawnMonster()
    }

    fun update(dt: Double) {
        d.stats.playTime += dt
        val it = d.buffs.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            e.setValue(e.value - dt)
            if (e.value <= 0) it.remove()
        }
        d.mana = min(maxMana().toDouble(), d.mana + manaRegen() * dt)
        if (buffOn("rain")) {
            rainTimer -= dt
            if (rainTimer <= 0) { rainTimer = 3.0; autoLevelAlly() }
        }
        val m = monster
        if (m == null || m.dead) {
            nextSpawn -= dt
            if (m == null || nextSpawn <= 0) spawnMonster()
            allyAttacks(dt)
            return
        }
        m.age += dt
        allyAttacks(dt)
        val rate = autoTapRate()
        if (rate > 0) {
            autoTapAcc += rate * dt
            while (autoTapAcc >= 1) {
                autoTapAcc--
                tap(true)
            }
        }
        if (m.boss && !m.dead) {
            bossTimer -= dt
            if (bossTimer <= 0) {
                d.farming = true
                d.wave = WAVES
                events.onBossFail()
                spawnMonster()
            }
        }
    }

    /** Each hired ally strikes on its own 1 second rhythm; an ally that's ready waits for the next monster. */
    private fun allyAttacks(dt: Double) {
        HEROES.forEachIndexed { i, h ->
            if (heroLvl(h.id) == 0) return@forEachIndexed
            val left = (allyTimers[h.id] ?: (i * 0.37 % 1.0)) - dt
            val m = monster
            when {
                left > 0 -> allyTimers[h.id] = left
                m == null || m.dead -> allyTimers[h.id] = 0.0
                else -> {
                    allyTimers[h.id] = left + ALLY_COOLDOWN
                    val dmg = allyHit(h)
                    events.onAllyAttack(h.id, dmg)
                    dealDamage(dmg)
                }
            }
        }
    }

    // ---------------------------------------------------------- purchases
    fun tapUpgradeInfo(): Pair<Int, Double> {
        val c0 = tapCost(d.tapLevel)
        val n = buyAmount(c0, 1.074)
        return n to bulkCost(c0, 1.074, n)
    }

    fun buyTap(): Boolean {
        val (n, cost) = tapUpgradeInfo()
        if (d.gold < cost) return false
        d.gold -= cost
        d.tapLevel += n
        d.stats.upgrades += n
        return true
    }

    fun heroUnlocked(i: Int) = i == 0 || heroLvl(HEROES[i - 1].id) > 0

    /** "Make It Rain": level the cheapest unlocked ally by one, if affordable. */
    private fun autoLevelAlly() {
        val h = HEROES.filterIndexed { i, _ -> heroUnlocked(i) }.minByOrNull { heroCost(it, heroLvl(it.id)) } ?: return
        val cost = heroCost(h, heroLvl(h.id))
        if (d.gold < cost) return
        d.gold -= cost
        d.heroes[h.id] = heroLvl(h.id) + 1
        d.stats.upgrades++
    }
    fun heroUpgradeInfo(h: Hero): Pair<Int, Double> {
        val c0 = heroCost(h, heroLvl(h.id))
        val n = buyAmount(c0, 1.075)
        return n to bulkCost(c0, 1.075, n)
    }

    fun buyHero(id: String): Boolean {
        val i = HEROES.indexOfFirst { it.id == id }
        if (i < 0 || !heroUnlocked(i)) return false
        val (n, cost) = heroUpgradeInfo(HEROES[i])
        if (d.gold < cost) return false
        d.gold -= cost
        d.heroes[id] = heroLvl(id) + n
        d.stats.upgrades += n
        return true
    }

    fun buySkill(id: String): Boolean {
        val sk = SKILLS.first { it.id == id }
        val lvl = skillLvl(id)
        if (lvl >= SKILL_MAX || d.tapLevel < sk.req) return false
        val cost = skillCost(sk, lvl)
        if (d.gold < cost) return false
        d.gold -= cost
        d.skills[id] = lvl + 1
        return true
    }

    fun castSkill(id: String): Boolean {
        val sk = SKILLS.first { it.id == id }
        val lvl = skillLvl(id)
        if (lvl == 0 || d.mana < sk.mana || buffOn(id)) return false
        d.mana -= sk.mana
        d.stats.casts++
        if (id == "thunder") {
            val dmg = tapDamage() * thunderMult()
            events.onSkill(id, dmg)
            dealDamage(dmg)
        } else {
            d.buffs[id] = spellDuration()
            events.onSkill(id, 0.0)
        }
        return true
    }

    fun buyArtifact(id: String): Boolean {
        val a = ARTIFACTS.first { it.id == id }
        val lvl = art(id)
        if (a.max > 0 && lvl >= a.max) return false
        val cost = artifactCost(a, lvl)
        if (d.relics < cost) return false
        d.relics -= cost
        d.artifacts[id] = lvl + 1
        return true
    }

    // ---------------------------------------------------------- gem shop
    /** Levelled items cost 50% more for every level already bought. */
    fun shopPrice(item: ShopItem) = if (item.kind == "level") item.gems * (2 + shopLevel(item.id)) / 2 else item.gems

    fun shopBought(item: ShopItem) = when (item.kind) {
        "perm" -> owns(item.id)
        "level" -> shopLevel(item.id) >= item.maxLevel
        else -> false
    }

    /** Buy a gem shop item. Returns a short result message, or null if it can't be bought. */
    fun buyShopItem(id: String): String? {
        val item = SHOP_ITEMS.firstOrNull { it.id == id } ?: return null
        val price = shopPrice(item)
        if (shopBought(item) || d.gems < price) return null
        if (item.kind == "boost" && buffOn(id)) return null
        if (id == "respec" && d.skillTree.isEmpty()) return null
        if (id == "warp" && d.stage >= d.maxStage) return null
        d.gems -= price
        val msg = when (id) {
            "starter" -> {
                openChestFree(CHESTS.first { it.id == "titan" })
                grantRandomEggs(10)
                d.buffs["surge"] = 600.0; d.buffs["golden_hour"] = 1800.0
                "Starter Pack opened!"
            }
            "auto_tap" -> "Auto Tapper level ${shopLevel(id) + 1}"
            "sp_pack" -> { d.bonusSp += 3; "+3 skill points" }
            "flask" -> { d.mana = maxMana().toDouble(); "Mana refilled" }
            "shower" -> { val g = killGold(d.stage) * 60; addGold(g); "+${fmt(g)} gold" }
            "mountain" -> { val g = killGold(d.stage) * 800; addGold(g); "+${fmt(g)} gold" }
            "relic_pack" -> { val r = max(5L, relicsFor(max(d.maxStage, PRESTIGE_STAGE)) / 4); d.relics += r; "+${fmt(r)} relics" }
            "warp" -> { repeat(min(10, d.maxStage - d.stage)) { advanceStage() }; monster = null; "Warped to stage ${d.stage}" }
            "respec" -> { d.skillTree.clear(); "Skill points refunded" }
            "eggs5" -> { grantRandomEggs(5); "+5 pet eggs" }
            "eggs20" -> { grantRandomEggs(20); "+20 pet eggs" }
            else -> if (item.kind == "boost") { d.buffs[id] = item.duration; "${item.name} active!" } else "${item.name} unlocked!"
        }
        when (item.kind) {
            "perm" -> d.owned += id
            "level" -> d.shopLevels[id] = shopLevel(id) + 1
        }
        return msg
    }

    fun buySkin(id: String): Boolean {
        val skin = skinDef(id) ?: return false
        if (id in d.skins) { d.skin = if (d.skin == id) null else id; return true } // toggle wearing it
        if (d.gems < skin.gems) return false
        d.gems -= skin.gems
        d.skins += id
        d.skin = id
        return true
    }

    private fun grantRandomEggs(n: Int) = repeat(n) { grantEggs(PETS.random().id, 1) }

    // ---------------------------------------------------------- equipment & chests
    private fun rollGear(odds: DoubleArray): Gear {
        var r = Random.nextDouble()
        var rarity = 0
        for (i in odds.indices) {
            if (r < odds[i]) { rarity = i; break }
            r -= odds[i]
        }
        var pool = GEAR.filter { it.r == rarity }
        while (pool.isEmpty() && rarity > 0) pool = GEAR.filter { it.r == --rarity }
        return pool.random()
    }

    private fun grantGear(g: Gear): GearRoll {
        val isNew = g.id !in d.gear
        d.gear[g.id] = (d.gear[g.id] ?: 0) + 1
        if (d.equipped[g.slot] == null) d.equipped[g.slot] = g.id
        return GearRoll(g, isNew)
    }

    fun openChest(id: String): List<GearRoll>? {
        val c = CHESTS.first { it.id == id }
        if (d.gems < c.gems) return null
        d.gems -= c.gems
        return openChestFree(c)
    }

    private fun openChestFree(c: Chest): List<GearRoll> = (0 until c.items).map { i ->
        grantGear(rollGear(if (c.id == "titan" && i == 0) doubleArrayOf(0.0, 0.0, 0.75, 0.25) else c.odds))
    }

    fun freeChestReady() = System.currentTimeMillis() >= d.freeChestAt
    fun openFreeChest(): List<GearRoll>? {
        if (!freeChestReady()) return null
        d.freeChestAt = System.currentTimeMillis() + FREE_CHEST_MS
        return listOf(grantGear(rollGear(CHESTS[0].odds)))
    }

    fun equip(id: String) {
        val g = gearDef(id) ?: return
        if (id in d.gear) d.equipped[g.slot] = id
    }

    // ---------------------------------------------------------- pets
    fun grantEggs(id: String, n: Int) {
        val p = d.pets.getOrPut(id) { PetData() }
        p.eggs += n
        if (p.lvl == 0 && p.eggs > 0) {
            p.lvl = 1
            p.eggs--
            if (d.activePet == null) d.activePet = id
        }
        while (p.eggs >= petEggsForLevel(p.lvl)) {
            p.eggs -= petEggsForLevel(p.lvl)
            p.lvl++
        }
    }

    fun setActivePet(id: String) {
        if (petLevel(id) > 0) {
            d.activePet = id
            d.petCharge = 0
        }
    }

    fun currentDeals(): Deals {
        val block = System.currentTimeMillis() / DEAL_MS
        if (d.deals.block != block) {
            val r = Random(block * 977 + 3)
            d.deals = Deals().apply {
                this.block = block
                repeat(3) {
                    val big = r.nextDouble() < 0.25
                    list += Deal().apply {
                        pet = PETS.random(r).id
                        eggs = if (big) 10 else 3
                        gems = if (big) 250 else 90
                    }
                }
            }
        }
        return d.deals
    }

    fun buyDeal(i: Int): Boolean {
        val deal = currentDeals().list.getOrNull(i) ?: return false
        if (deal.bought || d.gems < deal.gems) return false
        d.gems -= deal.gems
        deal.bought = true
        grantEggs(deal.pet, deal.eggs)
        return true
    }

    // ---------------------------------------------------------- prestige
    fun canPrestige() = d.maxStage >= PRESTIGE_STAGE

    fun prestige(): Long {
        if (!canPrestige()) return 0
        val gained = relicsFor(d.maxStage)
        val old = d
        d = PlayerData().also {
            it.name = old.name
            it.gems = old.gems
            it.relics = old.relics + gained
            it.artifacts = old.artifacts
            it.achievements = old.achievements
            it.stats = old.stats
            it.sound = old.sound
            it.mult = old.mult
            it.gear = old.gear
            it.equipped = old.equipped
            it.pets = old.pets
            it.activePet = old.activePet
            it.freeChestAt = old.freeChestAt
            it.deals = old.deals
            it.screenZoom = old.screenZoom
            it.cursorSpeed = old.cursorSpeed
            it.returnTo = old.returnTo
            it.returnMode = old.returnMode
            it.skillTree = old.skillTree
            it.bonusSp = old.bonusSp
            it.owned = old.owned
            it.shopLevels = old.shopLevels
            it.skins = old.skins
            it.skin = old.skin
            it.login = old.login
            it.quests = old.quests
        }
        d.stats.prestiges++
        d.stats.relicsEarned += gained
        d.mana = maxMana().toDouble()
        monster = null
        return gained
    }

    // ---------------------------------------------------------- achievements
    fun checkAchievements() {
        for (g in ACH_GROUPS) {
            val value = g.stat(d)
            g.tiers.forEachIndexed { i, target ->
                val id = "${g.key}_$i"
                if (id !in d.achievements && value >= target) {
                    d.achievements += id
                    val gems = achievementGems(g, i)
                    addGold(achievementGold(i))
                    d.gems += gems
                    events.onAchievement(g, target, gems)
                }
            }
        }
    }

    /** Gems are paid, so only the final tier of each achievement gives a few. */
    fun achievementGems(g: AchGroup, tier: Int) = if (tier == g.tiers.lastIndex) 5 else 0
    fun achievementGold(tier: Int) = killGold(d.stage) * 15 * (tier + 1)

    // ---------------------------------------------------------- daily login & quests
    private fun today() = System.currentTimeMillis() / 86_400_000L

    fun loginReady() = d.login.lastClaimDay != today()
    fun loginDay() = if (d.login.lastClaimDay >= today() - 1) (d.login.streak % 7) else 0 // index of the next reward

    fun claimLogin(): String? {
        val day = today()
        if (d.login.lastClaimDay == day) return null
        d.login.streak = if (d.login.lastClaimDay == day - 1) d.login.streak + 1 else 1
        d.login.lastClaimDay = day
        return when ((d.login.streak - 1) % 7) {
            0 -> { val g = killGold(d.stage) * 50; addGold(g); "+${fmt(g)} gold" }
            1 -> { grantRandomEggs(2); "+2 pet eggs" }
            2 -> { val g = killGold(d.stage) * 100; addGold(g); "+${fmt(g)} gold" }
            3 -> { val r = grantGear(rollGear(CHESTS[0].odds)); "Got ${r.gear.name}" }
            4 -> { grantRandomEggs(4); "+4 pet eggs" }
            5 -> { val g = killGold(d.stage) * 250; addGold(g); "+${fmt(g)} gold" }
            else -> { d.gems += 5; "+5 gems" }
        }
    }

    fun quests(): QuestData {
        val day = today()
        if (d.quests.day != day) {
            val r = Random(day * 31 + d.name.hashCode())
            d.quests = QuestData().apply {
                this.day = day
                for (q in QUEST_POOL.shuffled(r).take(3)) list += Quest().apply {
                    id = q.id; target = q.target(d.stats.bestStage); start = q.stat(d)
                }
            }
        }
        return d.quests
    }

    fun questDef(q: Quest) = QUEST_POOL.first { it.id == q.id }
    fun questProgress(q: Quest) = (questDef(q).stat(d) - q.start).coerceIn(0, q.target.toLong())

    fun claimQuest(i: Int): String? {
        val q = quests().list.getOrNull(i) ?: return null
        if (q.claimed || questProgress(q) < q.target) return null
        q.claimed = true
        d.stats.questsDone++
        return when (i) {
            0 -> { val g = killGold(d.stage) * 120; addGold(g); "+${fmt(g)} gold" }
            1 -> { grantRandomEggs(3); "+3 pet eggs" }
            else -> { val r = max(2L, d.maxStage / 20L); d.relics += r; "+$r relics" }
        }
    }

    fun questBonusReady() = quests().list.all { it.claimed } && !d.quests.bonusClaimed
    fun claimQuestBonus(): String? {
        if (!questBonusReady()) return null
        d.quests.bonusClaimed = true
        d.gems += 2
        return "+2 gems"
    }

    // ---------------------------------------------------------- wisps
    fun wispReward(): String {
        d.stats.wisps++
        val r = Random.nextDouble()
        return when {
            r < 0.15 -> { grantRandomEggs(1); "+1 pet egg" }
            r < 0.35 -> { d.mana = maxMana().toDouble(); "Mana refilled" }
            else -> { val g = killGold(d.stage) * 25; addGold(g); "+${fmt(g)} gold" }
        }
    }

    // ---------------------------------------------------------- time away
    fun applyOffline(secondsAway: Double, maxHours: Double): Double {
        val secs = min(secondsAway, (if (owns("offline_master")) max(24.0, maxHours) else maxHours) * 3600)
        val it = d.buffs.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            e.setValue(e.value - secs)
            if (e.value <= 0) it.remove()
        }
        d.mana = min(maxMana().toDouble(), d.mana + manaRegen() * secs)
        val dps = heroDps(false)
        if (dps <= 0 || secs < 30) return 0.0
        // allies alone kill at most about one monster per attack
        val kills = min(secs * dps / monsterHp(d.stage), secs / ALLY_COOLDOWN)
        val g = kills * killGold(d.stage) * (if (owns("offline_master")) 1.0 else 0.6) * (1 + 0.2 * art("coin")) * (1 + petBonus("offline"))
        addGold(g)
        return g
    }
}
