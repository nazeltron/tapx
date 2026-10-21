package dev.tapx

import dev.tapx.Items.item
import dev.tapx.Items.pane
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Material.*
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** A 6-row chest menu. Subclasses describe their contents in [draw]; only changed slots are re-sent. */
abstract class Menu(val s: Session, title: String) : InventoryHolder {
    private val inv: Inventory = Bukkit.createInventory(this, 54, mm(title))
    private val actions = arrayOfNulls<(ClickType) -> Unit>(54)
    private val shown = arrayOfNulls<ItemStack>(54)

    val g get() = s.game
    val d get() = s.game.d

    override fun getInventory() = inv

    /** How often to redraw, in session ticks (1 tick = 0.1 s). */
    open val refreshEvery = 5

    abstract fun draw()

    fun slot(i: Int, stack: ItemStack?, action: ((ClickType) -> Unit)? = null) {
        actions[i] = action
        if (shown[i] != stack) {
            inv.setItem(i, stack)
            shown[i] = stack
        }
    }

    fun fill(slots: Iterable<Int>, mat: Material = BLACK_STAINED_GLASS_PANE) = slots.forEach { slot(it, pane(mat)) }

    fun click(i: Int, type: ClickType) {
        if (i in 0 until 54) actions[i]?.invoke(type)
    }

    /** A purchase button: plays a sound for success/failure and redraws. */
    fun buy(ok: Boolean) {
        if (ok) s.sfx(Sfx.BUY) else s.sfx(Sfx.ERROR)
        draw()
    }

    /** Bottom row navigation shared by every screen. */
    fun nav(active: String) {
        fill(listOf(52))
        if (active == "battle") {
            slot(45, item(COMPARATOR, "<yellow>Stats & Settings", listOf("<gray>Sound, stats and reset", "<dark_gray>TapX v${s.plugin.version}"))) { s.open(SettingsMenu(s)) }
        } else {
            slot(45, item(ARROW, "<green><b>Back to Battle", listOf("<gray>Keep fighting!"))) { s.open(BattleMenu(s)) }
        }
        val tabs = listOf(
            Triple("hero", item(IRON_SWORD, "<#ff8a4a><b>Sword Master", listOf("<gray>Tap damage, prestige,", "<gray>spells and perks") + badge(g.d.gold >= g.tapUpgradeInfo().second)), { SwordMasterMenu(s) }),
            Triple("allies", item(IRON_HELMET, "<#4ac3e0><b>Allies", listOf("<gray>Heroes who fight for you") + badge(HEROES.indices.any { g.heroUnlocked(it) && d.gold >= g.heroUpgradeInfo(HEROES[it]).second })), { AlliesMenu(s) }),
            Triple("gear", item(LEATHER_CHESTPLATE, "<#ffd23a><b>Equipment", listOf("<gray>${d.gear.size}/${GEAR.size} collected"), dye = org.bukkit.Color.fromRGB(0xe3b72e)), { GearMenu(s) }),
            Triple("pets", item(petDef(d.activePet)?.icon ?: FOX_SPAWN_EGG, "<#7ad85a><b>Pets", listOf("<gray>Companions that strike", "<gray>as you tap")), { PetsMenu(s) }),
            Triple("artifacts", item(HEART_OF_THE_SEA, "<#c58aff><b>Artifacts", listOf("<gray>Spend relics on", "<gray>permanent upgrades") + badge(ARTIFACTS.any { (it.max == 0 || g.art(it.id) < it.max) && d.relics >= g.artifactCost(it, g.art(it.id)) })), { ArtifactsMenu(s) }),
            Triple("shop", item(CHEST, "<#3ad1c0><b>Shop", listOf("<gray>Chests, pet deals", "<gray>(gems only)") + badge(g.freeChestReady())), { ShopMenu(s) }),
        )
        tabs.forEachIndexed { i, (id, stack, make) ->
            if (id == active) stack.editMeta { it.setEnchantmentGlintOverride(true) }
            slot(46 + i, stack) { if (id != active) s.open(make()) }
        }
        slot(53, item(BARRIER, "<red>Close", listOf("<gray>Your allies keep earning", "<gray>gold while you're away"))) { s.plugin.server.scheduler.runTask(s.plugin, Runnable { s.player.closeInventory() }) }
    }

    private fun badge(on: Boolean) = if (on) listOf("", "<red><b>(!) Something to buy") else emptyList()

    fun multButton(at: Int) {
        val label = if (d.mult == 0) "MAX" else "x${d.mult}"
        slot(at, item(HOPPER, "<green><b>Buy $label", listOf("<gray>Click to change how many", "<gray>levels you buy at once"), amount = if (d.mult == 0) 1 else min(d.mult, 64))) {
            d.mult = when (d.mult) { 1 -> 10; 10 -> 100; 100 -> 0; else -> 1 }
            s.sfx(Sfx.CLICK)
            draw()
        }
    }

    fun header(at: Int, mat: Material, name: String, lore: List<String> = emptyList()) = slot(at, item(mat, name, lore))

    fun goldLine() = "$GOLD <yellow>${fmt(d.gold)} gold"
    fun gemLine() = "$GEM <aqua>${fmt(d.gems)} gems"
}

fun bar(frac: Double, len: Int = 10): String {
    val f = (frac.coerceIn(0.0, 1.0) * len).toInt()
    return "<green>" + "■".repeat(f) + "<dark_gray>" + "■".repeat(len - f)
}

// ======================================================================
// Battle screen
// ======================================================================
class BattleMenu(s: Session) : Menu(s, "<dark_gray>⚔ TapX") {
    override val refreshEvery = 1

    private val frame = listOf(23, 24, 25, 32, 34)
    private val allySlots = listOf(18, 19, 20, 27, 28, 29)
    private val tapArea = (9..35).toSet()

    override fun draw() {
        val world = worldFor(d.stage)
        val m = g.monster

        // row 0: info bar
        slot(0, item(TOTEM_OF_UNDYING, "<gold><b>Achievements", listOf("<gray>${d.achievements.size}/${ACH_GROUPS.sumOf { it.tiers.size }} unlocked", "<gray>Click to view"))) { s.open(AchievementsMenu(s)) }
        slot(1, item(OAK_SIGN, "<yellow><b>Top Players", listOf("<gray>Highest stages on the server"))) { s.open(TopMenu(s)) }
        fill(listOf(2, 6))
        if (d.stage > 1) {
            val pw = worldFor(d.stage - 1)
            slot(3, item(pw.icon, "<gray>Stage ${d.stage - 1}", listOf("<dark_gray>${pw.name}"), amount = stageAmount(d.stage - 1)))
        } else fill(listOf(3))
        slot(4, item(world.icon, "<${world.color}><b>Stage ${d.stage}</b> <gray>— ${world.name}", listOf("<gray>Highest stage: <white>${d.maxStage}"), amount = stageAmount(d.stage), glint = true))
        val nw = worldFor(d.stage + 1)
        slot(5, item(nw.icon, "<gray>Stage ${d.stage + 1}", listOf("<dark_gray>${nw.name}"), amount = stageAmount(d.stage + 1)))
        slot(7, item(GOLD_INGOT, "$GOLD <yellow><b>${fmt(d.gold)}</b> gold", listOf(gemLine(), "$RELIC <red>${fmt(d.relics)} relics")))
        when {
            m != null && m.boss && !m.dead ->
                slot(8, item(CLOCK, "<red><b>BOSS!</b> <white>${"%.1f".format(g.bossTimer)}s", listOf("<gray>Defeat it before time runs out"), amount = max(1, ceil(g.bossTimer).toInt()), glint = g.bossTimer < 8))
            d.farming ->
                slot(8, item(SKELETON_SKULL, "<gray>Farming stage ${d.stage}", listOf("<gray>Press <red>Fight Boss</red> when ready")))
            else ->
                slot(8, item(SKELETON_SKULL, "<white><b>Wave ${min(d.wave + 1, WAVES)}/$WAVES", listOf("<gray>Defeat $WAVES monsters", "<gray>to face the boss"), amount = min(d.wave + 1, WAVES)))
        }

        // row 1: HP bar
        val frac = if (m == null) 0.0 else m.hp / m.maxHp
        val filled = if (m == null || m.dead) 0 else max(1, ceil(frac * 9).toInt())
        val hpName = if (m == null) "<gray>..." else "<red>❤ <white><b>${m.name}</b> <gray>${fmt(ceil(m.hp))} / ${fmt(m.maxHp)} HP"
        for (i in 0 until 9) {
            val mat = if (i < filled) (if (m?.boss == true) MAGENTA_STAINED_GLASS_PANE else RED_STAINED_GLASS_PANE) else GRAY_STAINED_GLASS_PANE
            slot(9 + i, item(mat, hpName, listOf("<yellow>Click anywhere in the battle to attack!")), ::tapClick)
        }

        // rows 2-3: the scene
        for (i in listOf(22, 26)) slot(i, pane(world.sky), ::tapClick)
        slot(35, pane(world.ground), ::tapClick)
        val flashMat = s.flashMat
        for (i in frame) {
            val row2 = i < 27
            val stack = when {
                s.coinT > 0 -> item(GOLD_NUGGET, "<yellow>+${fmt(s.lastGold)} gold", amount = 1 + (i % 3))
                flashMat != null -> pane(flashMat)
                else -> pane(if (row2) world.sky else world.ground)
            }
            slot(i, stack, ::tapClick)
        }

        // allies: show the six strongest hired
        val hired = HEROES.filter { g.heroLvl(it.id) > 0 }.takeLast(6)
        allySlots.forEachIndexed { k, slotI ->
            val h = hired.getOrNull(k)
            if (h == null) slot(slotI, pane(if (slotI < 27) world.sky else world.ground), ::tapClick)
            else {
                val lvl = g.heroLvl(h.id)
                slot(slotI, item(h.icon, "<aqua>${h.name} <gray>Lv. $lvl", listOf("<gray>DPS: <white>${fmt(g.heroBaseDps(h, lvl) * g.allyMult())}"), glint = (s.allySwing[h.id] ?: 0.0) > 0), ::tapClick)
            }
        }

        // pet
        val pet = petDef(d.activePet)
        if (pet == null) slot(21, pane(world.sky), ::tapClick)
        else slot(21, item(pet.icon, "<green>${pet.name} <gray>Lv. ${g.petLevel(pet.id)}", listOf("<gray>Charge: ${bar(d.petCharge.toDouble() / PET_CHARGE)} <white>${d.petCharge}/$PET_CHARGE", "<gray>Strikes for <white>${fmt(g.petDamage())}"), amount = max(1, d.petCharge), glint = s.petT > 0), ::tapClick)

        // hero + weapon
        val weapon = gearDef(d.equipped["weapon"])
        slot(30, item(PLAYER_HEAD, "<gold><b>Sword Master</b> <gray>Lv. ${d.tapLevel}", heroLore(), head = s.player.playerProfile), ::tapClick)
        slot(31, item(weapon?.icon ?: IRON_SWORD, "<white>⚔ ${fmt(g.tapDamage())} <gray>tap damage", listOf("<gray>Crit: <white>${(g.critChance() * 100).toInt()}% <gray>for <white>x${fmt(g.critMult())}"), glint = s.swingT > 0), ::tapClick)

        // monster (or the wisp)
        if (m == null || m.dead) slot(33, item(BONE_MEAL, "<gray>Defeated!"), ::tapClick)
        else {
            val title = if (m.boss) "<dark_red><b>☠ ${m.name}" else if (m.mimic) "<gold><b>${m.name}" else "<red><b>${m.name}"
            val lore = mutableListOf("<gray>${m.kind}", "<gray>HP: <white>${fmt(ceil(m.hp))} <gray>/ ${fmt(m.maxHp)}", "")
            if (s.lastHitT > 0) lore += s.lastHit
            lore += "<yellow>Click to attack!"
            slot(33, item(m.icon, title, lore, glint = m.boss || m.mimic), ::tapClick)
        }
        if (s.wispSlot >= 0) slot(s.wispSlot, item(GLOW_INK_SAC, "<aqua><b>✦ A wisp! ✦", listOf("<gray>Click it for a surprise"), glint = true)) { s.catchWisp() }

        // row 4: mana, spells, boss button
        slot(36, item(LAPIS_LAZULI, "<blue><b>Mana</b> <white>${floor(d.mana).toInt()}/${g.maxMana()}", listOf(bar(d.mana / g.maxMana()), "<gray>Regenerates over time"), amount = max(1, min(64, floor(d.mana).toInt()))))
        SKILLS.forEachIndexed { i, sk -> slot(37 + i, skillItem(sk)) { if (g.castSkill(sk.id)) draw() else s.sfx(Sfx.ERROR) } }
        fill(listOf(43))
        when {
            d.farming -> slot(44, item(REDSTONE_BLOCK, "<red><b>FIGHT BOSS!", listOf("<gray>Challenge the stage ${d.stage} boss"), glint = true)) { g.fightBoss(); draw() }
            m != null && m.boss && !m.dead -> slot(44, item(OAK_DOOR, "<gray><b>Leave Battle", listOf("<gray>Go back to farming monsters"))) { g.leaveBoss(); draw() }
            else -> fill(listOf(44))
        }
        nav("battle")
    }

    private fun tapClick(@Suppress("UNUSED_PARAMETER") t: ClickType) = s.playerTap()

    private fun stageAmount(stage: Int) = ((stage - 1) % 64) + 1

    private fun heroLore(): List<String> {
        val lore = mutableListOf("<gray>Tap damage: <white>${fmt(g.tapDamage())}", "<gray>Ally DPS: <white>${fmt(g.heroDps())}", "")
        for (slot in GEAR_SLOTS) {
            val gear = gearDef(d.equipped[slot.id])
            lore += if (gear == null) "<dark_gray>${slot.name}: none" else "<gray>${slot.name}: <${RARITIES[gear.r].color}>${gear.name}"
        }
        if (d.buffs.isNotEmpty()) {
            lore += ""
            for ((id, t) in d.buffs) lore += "<yellow>${(SKILLS.firstOrNull { it.id == id }?.name ?: PERKS.firstOrNull { it.id == id }?.name ?: id)} <gray>${fmtTime(t)}"
        }
        return lore
    }

    private fun skillItem(sk: Skill): ItemStack {
        val lvl = g.skillLvl(sk.id)
        if (lvl == 0) return item(GRAY_DYE, "<dark_gray>${sk.name} <gray>(locked)", listOf("<gray>Learn it in the <#ff8a4a>Sword Master</#ff8a4a> tab", "<gray>Needs tap level ${sk.req}"))
        val active = d.buffs[sk.id]
        if (active != null) return item(sk.icon, "<yellow><b>${sk.name}</b> <white>${ceil(active).toInt()}s", listOf("<gray>${skillDesc(sk.id, lvl)}"), amount = max(1, min(64, ceil(active).toInt())), glint = true)
        val ready = d.mana >= sk.mana
        return item(sk.icon, (if (ready) "<aqua><b>" else "<gray>") + sk.name + " <blue>${sk.mana} mana",
            listOf("<gray>${skillDesc(sk.id, lvl)}" + (if (sk.timed) " for ${g.spellDuration().toInt()}s" else ""), "", if (ready) "<yellow>Click to cast!" else "<red>Not enough mana"))
    }
}

// ======================================================================
// Sword Master tab: tap upgrade, prestige, spells, perks
// ======================================================================
class SwordMasterMenu(s: Session) : Menu(s, "<dark_gray>⚔ Sword Master") {
    override fun draw() {
        fill(0..44)
        header(4, GOLD_INGOT, goldLine(), listOf(gemLine()))
        multButton(8)

        val (n, cost) = g.tapUpgradeInfo()
        val before = g.tapDamage(false)
        val ok = d.gold >= cost
        slot(11, item(PLAYER_HEAD, "<gold><b>Sword Master</b> <gray>Lv. <aqua>${d.tapLevel}", listOf(
            "<gray>Tap damage: <white>${fmt(before)}",
            "<gray>Crit: <white>${(g.critChance() * 100).toInt()}% <gray>for <white>x${fmt(g.critMult())}",
            "",
            "<gray>Cost: $GOLD <yellow>${fmt(cost)}",
            if (ok) "<green>Click to level up${if (n > 1) " x$n" else ""}!" else "<red>Not enough gold",
        ), head = s.player.playerProfile, glint = ok)) { buy(g.buyTap()) }

        val canP = g.canPrestige()
        slot(15, item(NETHER_STAR, "<light_purple><b>Prestige", if (canP) listOf(
            "<gray>Restart from stage 1 and earn",
            "$RELIC <red><b>${fmt(g.relicsFor(d.maxStage))} relics</b> <gray>for artifacts.",
            "", "<gray>You keep gems, artifacts,", "<gray>equipment, pets and achievements.",
            "", "<yellow>Click to prestige",
        ) else listOf("<gray>Restart your adventure in", "<gray>exchange for precious relics.", "", "<red>Reach stage $PRESTIGE_STAGE"), glint = canP)) {
            if (canP) s.open(ConfirmMenu(s, "Prestige for ${fmt(g.relicsFor(d.maxStage))} relics?",
                listOf("<gray>Your gold, tap level, allies", "<gray>and spells reset to the start."),
                onYes = { s.doPrestige() }, back = { SwordMasterMenu(s) }))
            else s.sfx(Sfx.ERROR)
        }

        slot(18, item(ENCHANTING_TABLE, "<aqua><b>Spells", listOf("<gray>Cast spells with mana", "<gray>from the battle screen")))
        SKILLS.forEachIndexed { i, sk ->
            val lvl = g.skillLvl(sk.id)
            val lore = mutableListOf("<gray>${skillDesc(sk.id, lvl)}" + (if (sk.timed) " for ${g.spellDuration().toInt()}s" else ""), "<blue>${sk.mana} mana", "")
            var canBuy = false
            when {
                lvl >= SKILL_MAX -> lore += "<gold>MAX LEVEL"
                d.tapLevel < sk.req -> lore += "<red>Unlocks at tap level ${sk.req}"
                else -> {
                    val c = g.skillCost(sk, lvl)
                    canBuy = d.gold >= c
                    lore += "<gray>${if (lvl == 0) "Learn" else "Upgrade"}: $GOLD <yellow>${fmt(c)}"
                    lore += if (canBuy) "<green>Click to ${if (lvl == 0) "learn" else "upgrade"}!" else "<red>Not enough gold"
                }
            }
            slot(20 + i, item(if (lvl == 0 && d.tapLevel < sk.req) GRAY_DYE else sk.icon, "<aqua><b>${sk.name}</b> <gray>Lv. $lvl", lore, amount = max(1, lvl), glint = canBuy)) { buy(g.buySkill(sk.id)) }
        }

        slot(27, item(DIAMOND, "<aqua><b>Perks", listOf("<gray>Powerful boosts bought", "<gray>with gems")))
        PERKS.forEachIndexed { i, p ->
            val active = d.buffs[p.id]
            val lore = mutableListOf("<gray>${p.desc}", "")
            lore += if (active != null) "<yellow>Active: ${fmtTime(active)}" else "<gray>Cost: $GEM <aqua>${p.gems}"
            if (active == null) lore += if (d.gems >= p.gems) "<green>Click to use!" else "<red>Not enough gems"
            slot(29 + i * 2, item(p.icon, "<green><b>${p.name}", lore, glint = active != null)) { buy(g.buyPerk(p.id)) }
        }
        nav("hero")
    }
}

// ======================================================================
// Allies tab
// ======================================================================
class AlliesMenu(s: Session) : Menu(s, "<dark_gray>⚔ Allies") {
    private val slots = listOf(10, 11, 12, 13, 14, 15, 16, 20, 21, 22, 23, 24)

    override fun draw() {
        fill(0..44)
        header(4, IRON_HELMET, "<aqua><b>${fmt(g.heroDps())}</b> <gray>Ally DPS", listOf(goldLine(), "<gray>Allies attack for you,", "<gray>even while you're away!"))
        multButton(8)
        HEROES.forEachIndexed { i, h ->
            val at = slots[i]
            if (!g.heroUnlocked(i)) {
                if (i > 0 && !g.heroUnlocked(i - 1)) slot(at, pane(BLACK_STAINED_GLASS_PANE))
                else slot(at, item(GRAY_DYE, "<dark_gray><b>???", listOf("<gray>Hire ${HEROES[i - 1].name}", "<gray>to reveal this ally")))
                return@forEachIndexed
            }
            val lvl = g.heroLvl(h.id)
            val (n, cost) = g.heroUpgradeInfo(h)
            val gain = (g.heroBaseDps(h, lvl + n) - g.heroBaseDps(h, lvl)) * g.allyMult()
            val next = HERO_MILESTONES.firstOrNull { it.first > lvl }
            val ok = d.gold >= cost
            val lore = mutableListOf(
                "<gray>Level: <aqua>$lvl",
                "<gray>DPS: <white>${fmt(g.heroBaseDps(h, lvl) * g.allyMult())}",
                if (next != null) "<gray>x${next.second} damage at Lv. ${next.first}" else "<gold>Fully mastered!",
                "",
                "<gray>Cost: $GOLD <yellow>${fmt(cost)}",
                "<gray>Gain: <green>+${fmt(gain)} DPS",
                if (ok) "<green>Click to ${if (lvl == 0) "hire" else "level up"}${if (n > 1) " x$n" else ""}!" else "<red>Not enough gold",
            )
            slot(at, item(h.icon, "<aqua><b>${h.name}", lore, amount = max(1, min(64, lvl)), glint = ok)) { buy(g.buyHero(h.id)) }
        }
        nav("allies")
    }
}

// ======================================================================
// Equipment tab
// ======================================================================
class GearMenu(s: Session) : Menu(s, "<dark_gray>⛨ Equipment") {
    override fun draw() {
        fill(0..44)
        slot(0, item(ARMOR_STAND, "<yellow><b>Equipped", listOf("<gray>Click any piece below", "<gray>you own to equip it")))
        GEAR_SLOTS.forEachIndexed { i, gs ->
            val id = d.equipped[gs.id]
            val gear = gearDef(id)
            if (gear == null) slot(1 + i, item(LIGHT_GRAY_STAINED_GLASS_PANE, "<gray>${gs.name}: <dark_gray>empty"))
            else slot(1 + i, gearItem(gear, equipped = true))
        }
        header(8, CHEST, "<gold><b>${d.gear.size}/${GEAR.size}</b> <gray>Equipment", listOf("<gray>Open chests in the <#3ad1c0>Shop", "<gray>to find more. Duplicates", "<gray>level a piece up."))
        val rows = listOf("weapon" to 9, "helm" to 18, "armor" to 27, "aura" to 36)
        for ((slotId, start) in rows) {
            val gs = slotDef(slotId)
            slot(start, item(LECTERN, "<white><b>${gs.name}s", listOf("<gray>Boost: ${gs.label}")))
            GEAR.filter { it.slot == slotId }.forEachIndexed { k, gear -> slot(start + 1 + k, gearItem(gear, equipped = d.equipped[slotId] == gear.id)) { equip(gear) } }
        }
        nav("gear")
    }

    private fun equip(gear: Gear) {
        if (gear.id !in d.gear) { s.sfx(Sfx.ERROR); return }
        g.equip(gear.id)
        s.sfx(Sfx.EQUIP)
        draw()
    }

    private fun gearItem(gear: Gear, equipped: Boolean): ItemStack {
        val lvl = d.gear[gear.id]
        val r = RARITIES[gear.r]
        val gs = slotDef(gear.slot)
        if (lvl == null) return item(GRAY_STAINED_GLASS_PANE, "<dark_gray>??? <gray>(${r.name} ${gs.name})", listOf("<gray>Not found yet"))
        return item(gear.icon, "<${r.color}><b>${gear.name}", listOf(
            "<${r.color}>${r.name} ${gs.name} <gray>· Lv. $lvl",
            "<green>+${(g.gearValue(gear.id) * 100).toInt()}% ${gs.label}",
            "", if (equipped) "<yellow>Equipped" else "<green>Click to equip",
        ), amount = lvl, glint = equipped, dye = gear.dye)
    }
}

// ======================================================================
// Pets tab
// ======================================================================
class PetsMenu(s: Session) : Menu(s, "<dark_gray>✿ Pets") {
    override fun draw() {
        fill(0..44)
        val active = petDef(d.activePet)
        header(4, active?.icon ?: EGG, if (active != null) "<green><b>${active.name}</b> <gray>is fighting with you" else "<gray>No pet yet",
            listOf("<gray>Your pet charges as you tap and", "<gray>strikes every $PET_CHARGE taps.", "<gray>Get eggs from <#3ad1c0>Daily Deals</#3ad1c0> in the Shop."))
        PETS.forEachIndexed { i, p ->
            val lvl = g.petLevel(p.id)
            val eggs = d.pets[p.id]?.eggs ?: 0
            val isActive = d.activePet == p.id
            val lore = mutableListOf(
                "<gray>Level: <aqua>$lvl" + (if (lvl > 0) " <gray>(eggs $eggs/${petEggsForLevel(lvl)})" else ""),
                "<gray>Bonus: <green>+${(p.per * max(1, lvl) * 100).toInt()}% ${p.label}",
                "<gray>Attack: <white>${5 + 3 * max(1, lvl)}x tap damage",
                "",
                when { lvl == 0 -> "<red>Locked — find eggs in the Shop"; isActive -> "<yellow>Active"; else -> "<green>Click to equip" },
            )
            slot(19 + i + (if (i >= 3) 1 else 0), item(if (lvl == 0) GRAY_DYE else p.icon, "<green><b>${p.name}", lore, amount = max(1, lvl), glint = isActive)) {
                if (lvl == 0) s.sfx(Sfx.ERROR) else { g.setActivePet(p.id); s.sfx(Sfx.PET); draw() }
            }
        }
        nav("pets")
    }
}

// ======================================================================
// Artifacts tab
// ======================================================================
class ArtifactsMenu(s: Session) : Menu(s, "<dark_gray>❤ Artifacts") {
    private val slots = listOf(19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32)

    override fun draw() {
        fill(0..44)
        header(4, HEART_OF_THE_SEA, "$RELIC <red><b>${fmt(d.relics)}</b> <gray>relics", listOf("<gray>Relics come from prestiging.", "<gray>Earned so far: <white>${fmt(d.stats.relicsEarned)}"))
        val canP = g.canPrestige()
        slot(13, item(NETHER_STAR, "<light_purple><b>Prestige", listOf(
            if (canP) "<gray>Earn $RELIC <red>${fmt(g.relicsFor(d.maxStage))} relics" else "<red>Reach stage $PRESTIGE_STAGE",
            "<gray>Prestiges: <white>${d.stats.prestiges}",
        ) + if (canP) listOf("", "<yellow>Click to prestige") else emptyList(), glint = canP)) {
            if (canP) s.open(ConfirmMenu(s, "Prestige for ${fmt(g.relicsFor(d.maxStage))} relics?",
                listOf("<gray>Your gold, tap level, allies", "<gray>and spells reset to the start."),
                onYes = { s.doPrestige() }, back = { ArtifactsMenu(s) }))
            else s.sfx(Sfx.ERROR)
        }
        ARTIFACTS.forEachIndexed { i, a ->
            val lvl = g.art(a.id)
            val maxed = a.max > 0 && lvl >= a.max
            val cost = g.artifactCost(a, lvl)
            val ok = !maxed && d.relics >= cost
            val lore = mutableListOf(
                "<gray>Level: <aqua>$lvl" + (if (a.max > 0) "<gray>/${a.max}" else ""),
                if (lvl > 0) "<green>${a.desc(lvl)}" else "<dark_gray>${a.desc(1)}",
                "",
            )
            if (maxed) lore += "<gold>MAX LEVEL"
            else {
                lore += "<gray>Next: <white>${a.desc(lvl + 1)}"
                lore += "<gray>Cost: $RELIC <red>${fmt(cost)}"
                lore += if (ok) "<green>Click to ${if (lvl == 0) "unlock" else "upgrade"}!" else "<red>Not enough relics"
            }
            slot(slots[i], item(a.icon, "<light_purple><b>${a.name}", lore, amount = max(1, lvl), glint = ok)) { buy(g.buyArtifact(a.id)) }
        }
        nav("artifacts")
    }
}

// ======================================================================
// Shop tab (gems only)
// ======================================================================
class ShopMenu(s: Session) : Menu(s, "<dark_gray>♦ Shop") {
    override fun draw() {
        fill(0..44)
        header(4, DIAMOND, gemLine(), listOf("<gray>Earn gems from bosses,", "<gray>achievements and wisps.", "<gray>No real money here!"))
        val ready = g.freeChestReady()
        slot(13, item(BARREL, "<green><b>Free Chest", listOf("<gray>1 piece of equipment + 5 gems", "", if (ready) "<green>Click to open!" else "<gray>Ready in <white>${fmtTime((d.freeChestAt - System.currentTimeMillis()) / 1000.0)}"), glint = ready)) {
            val items = g.openFreeChest()
            if (items == null) s.sfx(Sfx.ERROR) else s.open(LootMenu(s, "Free Chest", items))
        }
        CHESTS.forEachIndexed { i, c ->
            val ok = d.gems >= c.gems
            val odds = c.odds.mapIndexed { r, p -> "<${RARITIES[r].color}>${RARITIES[r].name}: ${"%.1f".format(p * 100)}%" }
            slot(20 + i * 2, item(c.icon, "<gold><b>${c.name}", listOf("<gray>${c.items} pieces of equipment") + (if (c.id == "titan") listOf("<#b46cff>First item is Epic or better!") else emptyList()) + odds +
                listOf("", "<gray>Cost: $GEM <aqua>${c.gems}", if (ok) "<green>Click to open!" else "<red>Not enough gems"), glint = ok)) {
                val items = g.openChest(c.id)
                if (items == null) s.sfx(Sfx.ERROR) else s.open(LootMenu(s, c.name, items))
            }
        }
        val deals = g.currentDeals()
        val left = DEAL_MS - System.currentTimeMillis() % DEAL_MS
        slot(29, item(CLOCK, "<yellow><b>Daily Deals", listOf("<gray>Pet eggs! New deals in", "<white>${fmtTime(left / 1000.0)}")))
        deals.list.forEachIndexed { i, deal ->
            val p = petDef(deal.pet) ?: return@forEachIndexed
            val ok = !deal.bought && d.gems >= deal.gems
            slot(31 + i, item(p.icon, "<green><b>${p.name} <white>x${deal.eggs} eggs", listOf(
                "<gray>Pet bonus: ${p.label}", "",
                if (deal.bought) "<dark_gray>Sold out" else "<gray>Cost: $GEM <aqua>${deal.gems}",
                if (deal.bought) "" else if (ok) "<green>Click to buy!" else "<red>Not enough gems",
            ), amount = deal.eggs, glint = ok)) {
                if (g.buyDeal(i)) { s.sfx(Sfx.PET); s.msg("<green>${p.name} <gray>+${deal.eggs} eggs!"); draw() } else s.sfx(Sfx.ERROR)
            }
        }
        nav("shop")
    }
}

class LootMenu(s: Session, private val name: String, private val loot: List<GearRoll>) : Menu(s, "<dark_gray>$name") {
    init { s.sfx(if (loot.any { it.gear.r >= 3 }) Sfx.LEGENDARY else Sfx.CHEST) }

    override fun draw() {
        fill(0..44)
        header(4, CHEST, "<gold><b>$name opened!")
        val start = 22 - loot.size / 2
        loot.forEachIndexed { i, roll ->
            val r = RARITIES[roll.gear.r]
            slot(start + i, item(roll.gear.icon, "<${r.color}><b>${roll.gear.name}", listOf("<${r.color}>${r.name} ${slotDef(roll.gear.slot).name}", if (roll.isNew) "<yellow><b>NEW!" else "<gray>Now Lv. ${d.gear[roll.gear.id]}"), glint = roll.gear.r >= 2, dye = roll.gear.dye))
        }
        slot(39, item(LIME_CONCRETE, "<green><b>Awesome!", listOf("<gray>Back to the shop"))) { s.open(ShopMenu(s)) }
        slot(41, item(LEATHER_CHESTPLATE, "<yellow><b>See Equipment", dye = org.bukkit.Color.fromRGB(0xe3b72e))) { s.open(GearMenu(s)) }
        nav("loot")
    }
}

// ======================================================================
// Achievements, leaderboard, settings, confirm
// ======================================================================
class AchievementsMenu(s: Session) : Menu(s, "<dark_gray>★ Achievements") {
    private val slots = listOf(10, 11, 12, 13, 14, 15, 16, 20, 21, 22, 23, 24)

    override fun draw() {
        fill(0..44)
        header(4, TOTEM_OF_UNDYING, "<gold><b>Achievements", listOf("<gray>Unlock them for free gems!"))
        ACH_GROUPS.forEachIndexed { i, a ->
            val value = a.stat(d)
            val next = a.tiers.indices.firstOrNull { "${a.key}_$it" !in d.achievements }
            val done = next == null
            val target = a.tiers[next ?: a.tiers.lastIndex]
            val stars = a.tiers.indices.joinToString("") { if ("${a.key}_$it" in d.achievements) "<gold>★" else "<dark_gray>★" }
            slot(slots[i], item(a.icon, "<yellow><b>${a.name}</b> $stars", listOf(
                "<gray>${a.text(target)}" + if (done) " <green>✔" else "",
                "${bar(value / target)} <white>${fmt(min(value, target))}/${fmt(target)}",
                if (done) "<gold>All tiers complete!" else "<gray>Reward: $GEM <aqua>${a.gems[next!!]}",
            ), glint = done))
        }
        nav("battle-sub")
    }
}

class TopMenu(s: Session) : Menu(s, "<dark_gray>★ Top Players") {
    override fun draw() {
        fill(0..44)
        header(4, OAK_SIGN, "<yellow><b>Top Players", listOf("<gray>Ranked by highest stage"))
        val top = s.plugin.leaderboard().take(14)
        val slots = listOf(19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34)
        top.forEachIndexed { i, e ->
            val color = when (i) { 0 -> "<gold>"; 1 -> "<white>"; 2 -> "<#cd7f32>"; else -> "<gray>" }
            slot(slots[i], item(PLAYER_HEAD, "$color<b>#${i + 1}</b> ${e.name}", listOf("<gray>Best stage: <white>${e.best}", "<gray>Prestiges: <white>${e.prestiges}"), amount = i + 1, head = Bukkit.createProfile(e.uuid, e.name)))
        }
        nav("battle-sub")
    }
}

class SettingsMenu(s: Session) : Menu(s, "<dark_gray>⚙ Stats & Settings") {
    override fun draw() {
        fill(0..44)
        val st = d.stats
        slot(11, item(BOOK, "<yellow><b>Your Stats", listOf(
            "<gray>Highest stage: <white>${st.bestStage}",
            "<gray>Monsters defeated: <white>${fmt(st.kills)}",
            "<gray>Bosses defeated: <white>${fmt(st.bosses)}",
            "<gray>Total taps: <white>${fmt(st.taps)}",
            "<gray>Critical hits: <white>${fmt(st.crits)}",
            "<gray>Gold earned: <white>${fmt(st.goldEarned)}",
            "<gray>Spells cast: <white>${fmt(st.casts)}",
            "<gray>Prestiges: <white>${st.prestiges}",
            "<gray>Play time: <white>${fmtTime(st.playTime)}",
        )))
        slot(13, item(if (d.sound) NOTE_BLOCK else JUKEBOX, "<aqua><b>Sound: ${if (d.sound) "<green>ON" else "<red>OFF"}", listOf("<gray>Click to toggle"))) {
            d.sound = !d.sound
            s.sfx(Sfx.CLICK)
            draw()
        }
        slot(15, item(TNT, "<red><b>Reset Progress", listOf("<gray>Wipe ALL TapX progress,", "<gray>including relics and gems.", "<red>This cannot be undone!"))) {
            s.open(ConfirmMenu(s, "Wipe ALL progress?", listOf("<red>Everything will be lost,", "<red>including relics and gems."), onYes = { s.resetProgress() }, back = { SettingsMenu(s) }))
        }
        slot(31, item(PAPER, "<gray>TapX v${s.plugin.version}", listOf("<dark_gray>A Tap Titans-style idle game", "<dark_gray>made with vanilla items only")))
        nav("battle-sub")
    }
}

class ConfirmMenu(
    s: Session, private val question: String, private val details: List<String>,
    private val onYes: () -> Unit, private val back: () -> Menu,
) : Menu(s, "<dark_gray>Are you sure?") {
    override fun draw() {
        fill(0..53)
        slot(13, item(PAPER, "<yellow><b>$question", details))
        slot(29, item(LIME_CONCRETE, "<green><b>Yes", listOf("<gray>Do it!"))) { onYes() }
        slot(33, item(RED_CONCRETE, "<red><b>No", listOf("<gray>Go back"))) { s.open(back()) }
    }
}
