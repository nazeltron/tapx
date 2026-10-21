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
    fun onAchievement(group: AchGroup, target: Double, gems: Int) {}
}

class Monster(val maxHp: Double, val boss: Boolean, val mimic: Boolean, val name: String, val seed: Long) {
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
    private var nextSpawn = 0.0

    // ---------------------------------------------------------- lookups
    fun art(id: String) = d.artifacts[id] ?: 0
    fun heroLvl(id: String) = d.heroes[id] ?: 0
    fun skillLvl(id: String) = d.skills[id] ?: 0
    fun buffOn(id: String) = (d.buffs[id] ?: 0.0) > 0
    fun petLevel(id: String?) = d.pets[id]?.lvl ?: 0

    fun gearValue(id: String): Double {
        val g = gearDef(id) ?: return 0.0
        val lvl = d.gear[id] ?: return 0.0
        return slotDef(g.slot).base * RARITIES[g.r].mult * (1 + 0.5 * (lvl - 1))
    }

    private fun gearBonus(stat: String) =
        GEAR_SLOTS.filter { it.stat == stat }.sumOf { s -> d.equipped[s.id]?.let { gearValue(it) } ?: 0.0 }

    private fun petBonus(stat: String): Double {
        val p = petDef(d.activePet) ?: return 0.0
        return if (p.stat == stat) p.per * petLevel(p.id) else 0.0
    }

    // ---------------------------------------------------------- formulas
    fun monsterHp(stage: Int) = 10 * 1.39.pow(stage - 1) * (1 + ((stage - 1) / 5) * 0.08)
    fun bossHp(stage: Int) = monsterHp(stage) * 6 * (1 - 0.04 * art("crown"))
    fun goldMult() = (1 + 0.25 * art("idol")) * (1 + gearBonus("gold")) * (1 + petBonus("gold")) *
        (if (buffOn("midas")) skillPower("midas", skillLvl("midas")) else 1.0)
    fun killGold(stage: Int) = ceil(monsterHp(stage) * 0.2) * goldMult()

    private fun allDamageMult() = (1 + 0.25 * art("blade")) * (if (buffOn("surge")) 2.0 else 1.0)

    fun heroMilestoneMult(lvl: Int) = HERO_MILESTONES.filter { lvl >= it.first }.fold(1.0) { m, x -> m * x.second }
    fun heroBaseDps(h: Hero, lvl: Int) = if (lvl > 0) h.dps * lvl * heroMilestoneMult(lvl) else 0.0
    fun heroCost(h: Hero, lvl: Int) = h.cost * 1.075.pow(lvl)
    fun allyMult() = (1 + 0.35 * art("banner")) * (1 + gearBonus("ally")) * (1 + petBonus("ally")) * allDamageMult()
    fun rawHeroDps() = HEROES.sumOf { heroBaseDps(it, heroLvl(it.id)) }
    fun heroDps(buffed: Boolean = true) =
        rawHeroDps() * allyMult() * (if (buffed && buffOn("warcry")) skillPower("warcry", skillLvl("warcry")) else 1.0)

    fun tapBase(l: Int) = l * 1.05.pow(l - 1)
    fun tapCost(l: Int) = 5 * 1.074.pow(l - 1)
    fun tapDamage(buffed: Boolean = true): Double {
        val base = tapBase(d.tapLevel) + 0.03 * rawHeroDps() * allyMult()
        return base * (1 + 0.4 * art("gauntlet")) * (1 + gearBonus("tap")) * (1 + petBonus("tap")) * allDamageMult() *
            (if (buffed && buffOn("berserk")) skillPower("berserk", skillLvl("berserk")) else 1.0)
    }

    fun critChance() = min(0.9, 0.02 + 0.01 * art("clover") + (if (buffOn("eagle")) skillPower("eagle", skillLvl("eagle")) / 100 else 0.0))
    fun critMult() = 8 * (1 + 0.5 * art("fang")) * (1 + gearBonus("crit")) * (1 + petBonus("crit"))
    fun bossTime() = BOSS_TIME + 2 * art("hourglass")
    fun maxMana() = 50 + 5 * art("crystal")
    fun manaRegen() = (1.0 / 3) * (1 + 0.1 * art("crystal")) * (1 + petBonus("mana"))
    fun spellDuration() = 30.0 + 4 * art("tome")
    private fun autoTapRate() =
        (if (buffOn("frenzy")) skillPower("frenzy", skillLvl("frenzy")) else 0.0) + (if (buffOn("phantom")) 6.0 else 0.0)

    fun relicsFor(stage: Int) =
        if (stage < PRESTIGE_STAGE) 0L else floor((stage - 15.0).pow(1.6) / 6 * (1 + 0.1 * d.stats.prestiges)).toLong()
    fun artifactCost(a: Artifact, lvl: Int) = ceil(a.base * 1.45.pow(lvl)).toLong()
    fun skillCost(s: Skill, lvl: Int) = s.cost * 12.0.pow(lvl)
    fun petDamage() = if (d.activePet != null) tapDamage() * (5 + 3 * petLevel(d.activePet)) else 0.0

    // Bulk buying for geometric costs.
    fun bulkCost(c0: Double, r: Double, n: Int) = c0 * (r.pow(n) - 1) / (r - 1)
    private fun maxAffordable(c0: Double, r: Double, gold: Double): Int =
        if (gold < c0) 0 else max(1, floor(ln(1 + gold * (r - 1) / c0) / ln(r)).toInt())
    fun buyAmount(c0: Double, r: Double) = if (d.mult == 0) max(1, maxAffordable(c0, r, d.gold)) else d.mult

    // ---------------------------------------------------------- combat
    fun spawnMonster() {
        val boss = !d.farming && d.wave >= WAVES
        val mimic = !boss && Random.nextDouble() < 0.025 + 0.01 * art("key")
        val hp = if (boss) bossHp(d.stage) else monsterHp(d.stage)
        // Bosses are seeded by stage so a retried boss is the same one.
        val seed = if (boss) d.stage * 7919L + 13 else Random.nextLong()
        val rnd = Random(seed)
        val baseName = NAME_A.random(rnd) + NAME_B.random(rnd)
        val m = when {
            mimic -> Monster(hp, false, true, "Treasure Mimic", seed)
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
        m.hp -= if (m.boss) amount * (1 + petBonus("boss")) else amount
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
        if (m.boss) g *= 5
        if (m.mimic) g *= 12
        addGold(g)
        d.stats.kills++
        var gems = 0
        if (m.boss) {
            d.stats.bosses++
            if (Random.nextDouble() < 0.35) gems = if (Random.nextDouble() < 0.3) 2 else 1
        }
        d.gems += gems
        events.onKill(m, g, gems)
        if (m.boss) advanceStage() else if (!d.farming) d.wave = min(WAVES, d.wave + 1)
        nextSpawn = 0.45
    }

    private fun advanceStage() {
        val n = if (Random.nextDouble() < 0.02 * art("boots")) 2 else 1
        d.stage += n
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
        val m = monster
        if (m == null || m.dead) {
            nextSpawn -= dt
            if (m == null || nextSpawn <= 0) spawnMonster()
            return
        }
        m.age += dt
        val dps = heroDps()
        if (dps > 0) dealDamage(dps * dt)
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
        return true
    }

    fun heroUnlocked(i: Int) = i == 0 || heroLvl(HEROES[i - 1].id) > 0
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
            val dmg = tapDamage() * skillPower("thunder", lvl)
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

    fun buyPerk(id: String): Boolean {
        val p = PERKS.first { it.id == id }
        if (d.gems < p.gems) return false
        if (p.duration > 0 && buffOn(id)) return false
        d.gems -= p.gems
        when (id) {
            "shower" -> addGold(killGold(d.stage) * 60)
            "flask" -> d.mana = maxMana().toDouble()
            else -> d.buffs[id] = p.duration
        }
        return true
    }

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
        return (0 until c.items).map { i ->
            grantGear(rollGear(if (id == "titan" && i == 0) doubleArrayOf(0.0, 0.0, 0.75, 0.25) else c.odds))
        }
    }

    fun freeChestReady() = System.currentTimeMillis() >= d.freeChestAt
    fun openFreeChest(): List<GearRoll>? {
        if (!freeChestReady()) return null
        d.freeChestAt = System.currentTimeMillis() + FREE_CHEST_MS
        d.gems += 5
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
                    d.gems += g.gems[i]
                    events.onAchievement(g, target, g.gems[i])
                }
            }
        }
    }

    // ---------------------------------------------------------- wisps
    fun wispReward(): String {
        d.stats.wisps++
        val r = Random.nextDouble()
        return when {
            r < 0.15 -> { val n = Random.nextInt(3, 8); d.gems += n; "+$n gems" }
            r < 0.35 -> { d.mana = maxMana().toDouble(); "Mana refilled" }
            else -> { val g = killGold(d.stage) * 25; addGold(g); "+${fmt(g)} gold" }
        }
    }

    // ---------------------------------------------------------- time away
    fun applyOffline(secondsAway: Double, maxHours: Double): Double {
        val secs = min(secondsAway, maxHours * 3600)
        val it = d.buffs.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            e.setValue(e.value - secs)
            if (e.value <= 0) it.remove()
        }
        d.mana = min(maxMana().toDouble(), d.mana + manaRegen() * secs)
        val dps = heroDps(false)
        if (dps <= 0 || secs < 30) return 0.0
        val kills = min(secs * dps / monsterHp(d.stage), secs * 2)
        val g = kills * killGold(d.stage) * 0.6
        addGold(g)
        return g
    }
}
