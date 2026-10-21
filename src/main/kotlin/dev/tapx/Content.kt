package dev.tapx

// All static game content. Every visual is described by colours/shapes here and drawn in
// code by screen/Art.kt onto the map screen.

const val WAVES = 8              // normal monsters per stage before the boss
const val BOSS_TIME = 30.0       // seconds to kill a boss
const val PRESTIGE_STAGE = 25    // stage needed to prestige
const val PET_CHARGE = 15        // taps per pet attack
const val SKILL_MAX = 10
const val FREE_CHEST_MS = 4L * 3600 * 1000
const val DEAL_MS = 6L * 3600 * 1000

class World(
    val name: String, val kind: String, val sky: Pair<String, String>,
    val far: String, val mid: String, val midDark: String,
    val ground: String, val groundTop: String, val dirt: String,
)

val WORLDS = listOf(
    World("Meadow Ruins", "castle", "#7cc6f2" to "#e4f5ff", "#b4cadb", "#e7d8b8", "#c9b38c", "#c9b48c", "#e8dcc0", "#6d5640"),
    World("Sunscorch Dunes", "dunes", "#f59a52" to "#ffe3a6", "#eab27a", "#dc9c58", "#b97a3a", "#e0b46c", "#f3d397", "#8f6430"),
    World("Frostpeak", "peaks", "#86b2e6" to "#eef6ff", "#c4d6ee", "#f7fbff", "#a9c0de", "#dde8f5", "#ffffff", "#6f84a3"),
    World("Gloomwood", "forest", "#262a52" to "#6c5b8f", "#3d3a68", "#26284a", "#191a33", "#3b4a3b", "#56705a", "#211c1b"),
    World("Ember Depths", "lava", "#2a0a0a" to "#912e12", "#4c1812", "#2c100c", "#190807", "#3a1c15", "#5c2d1e", "#160807"),
    World("Crystal Void", "void", "#0c0a29" to "#3d1e72", "#2c2463", "#5c40b4", "#3a2780", "#2a2152", "#4a3a8c", "#110c26"),
)

fun worldFor(stage: Int) = WORLDS[((stage - 1) / 10) % WORLDS.size]

/** How a chibi character looks. */
data class Look(
    val skin: String, val hair: String, val hairStyle: String, val outfit: String, val trim: String,
    val pants: String, val weapon: String, val boots: String = "#4a2f1f", val cape: String? = null,
    val hat: String? = null, val hood: String? = null, val band: String? = null, val helm: String? = null,
    val beard: String? = null, val glow: String? = null,
    // equipment overrides (hero only)
    val blade: String? = null, val bladeGlow: String? = null, val helmGear: String? = null,
    val helmColor: String? = null, val aura: String? = null, val phase: Double = 0.0,
)

// ---------------------------------------------------------------- allies
class Hero(val id: String, val name: String, val cost: Double, val dps: Double, val look: Look)

val HEROES = listOf(
    Hero("pip", "Pip the Squire", 50.0, 4.0, Look("#f6c9a0", "#7a4a24", "bob", "#4f86d9", "#f1d36b", "#3a3f5c", "spear")),
    Hero("wren", "Wren the Archer", 600.0, 35.0, Look("#f3c39b", "#d0482c", "long", "#3f8a4a", "#e9c46a", "#4a3a2a", "bow")),
    Hero("ember", "Ember the Mage", 7.2e3, 300.0, Look("#f0c4a4", "#e9e4f5", "wizard", "#9b59d0", "#f2c94c", "#43305c", "staff", hat = "#7b3fbf", glow = "#ff7a3a")),
    Hero("grom", "Grom the Bruiser", 8.6e4, 2.6e3, Look("#9bc47a", "#2f2a24", "bald", "#8a5a3a", "#c0c0c0", "#3b2f28", "axe", beard = "#2f2a24")),
    Hero("nyx", "Nyx the Rogue", 1e6, 2.2e4, Look("#e9b993", "#2b2b3a", "hood", "#3a4466", "#b23a48", "#23263a", "dagger", hood = "#2e3550")),
    Hero("sol", "Sister Sol", 1.2e7, 1.9e5, Look("#f6d2b0", "#f4e3a1", "long", "#f3f1e8", "#e0b23c", "#c9c2ad", "mace")),
    Hero("bolt", "Bolt the Gunner", 1.5e8, 1.6e6, Look("#c98e63", "#1f1f1f", "bandana", "#5c6b3a", "#d9a441", "#3a3a2a", "gun", band = "#e2463c")),
    Hero("moss", "Moss the Druid", 1.8e9, 1.4e7, Look("#e8c29d", "#5a8c3a", "long", "#6b8f3a", "#a8d672", "#4a3a24", "leaf")),
    Hero("dread", "The Dread Knight", 2.2e10, 1.2e8, Look("#cfc8c0", "#555555", "helmet", "#3d4452", "#c23b3b", "#2a2e38", "sword", helm = "#596273", cape = "#7a1f2b")),
    Hero("vale", "Vale the Valkyrie", 3e11, 1.05e9, Look("#f4d0b0", "#f6f0d8", "long", "#6fb0e6", "#ffe08a", "#3d5a80", "spear", cape = "#ffffff")),
    Hero("wyrm", "Wyrmcaller", 4e12, 9e9, Look("#d9a77c", "#c93a2a", "spiky", "#8a2a1f", "#f2b134", "#3a1f1a", "scythe", cape = "#f2b134")),
    Hero("void", "Oracle of the Void", 5.5e13, 8e10, Look("#b9a8e6", "#2a1a4a", "hood", "#5a3aa8", "#7ef0ff", "#24164a", "staff", hood = "#3a1f6e", glow = "#7ef0ff")),
)

val HERO_LOOK = Look("#f6cba4", "#ffd23a", "spiky", "#2f3448", "#e8b33a", "#2a2a36", "sword", cape = "#d8302f")

/** Ally damage multipliers unlocked at level milestones. */
val HERO_MILESTONES = listOf(10 to 2.0, 25 to 2.0, 50 to 2.5, 75 to 2.0, 100 to 3.0, 150 to 2.5, 200 to 2.5, 300 to 3.0, 400 to 3.0, 500 to 4.0)

// ---------------------------------------------------------------- spells
class Skill(val id: String, val name: String, val req: Int, val cost: Double, val mana: Int, val timed: Boolean)

val SKILLS = listOf(
    Skill("thunder", "Thunder Strike", 15, 250.0, 10, false),
    Skill("frenzy", "Frenzy Blades", 40, 5e3, 25, true),
    Skill("warcry", "War Cry", 75, 8e4, 30, true),
    Skill("midas", "Midas Touch", 110, 1.5e6, 30, true),
    Skill("berserk", "Berserk", 150, 3e7, 35, true),
    Skill("eagle", "Eagle Eye", 200, 6e8, 40, true),
)

fun skillPower(id: String, level: Int): Double {
    val l = maxOf(1, level) - 1
    return when (id) {
        "thunder" -> 50.0 + 30 * l
        "frenzy" -> 8.0 + 2 * l
        "warcry" -> 1.5 + 0.35 * l
        "midas" -> 1.5 + 0.3 * l
        "berserk" -> 2.0 + 0.5 * l
        "eagle" -> 10.0 + 4 * l
        else -> 1.0
    }
}

fun skillDesc(id: String, level: Int): String {
    val p = skillPower(id, level)
    val x = if (p % 1.0 == 0.0) p.toLong().toString() else "%.2f".format(p).trimEnd('0')
    return when (id) {
        "thunder" -> "Deal ${fmt(p)}x tap damage"
        "frenzy" -> "Auto-tap $x times/sec"
        "warcry" -> "Ally DPS x$x"
        "midas" -> "All gold x$x"
        "berserk" -> "Tap damage x$x"
        "eagle" -> "+$x% crit chance"
        else -> ""
    }
}

// ---------------------------------------------------------------- perks (gems)
class Perk(val id: String, val name: String, val gems: Int, val duration: Double, val desc: String)

val PERKS = listOf(
    Perk("surge", "Power Surge", 40, 600.0, "x2 ALL damage for 10 min"),
    Perk("phantom", "Phantom Hand", 50, 600.0, "Auto-tap 6/sec for 10 min"),
    Perk("shower", "Gold Shower", 30, 0.0, "Gold worth 60 kills"),
    Perk("flask", "Mana Flask", 15, 0.0, "Refill your mana"),
)

// ---------------------------------------------------------------- artifacts (relics)
class Artifact(val id: String, val name: String, val base: Int, val max: Int, val desc: (Int) -> String)

val ARTIFACTS = listOf(
    Artifact("blade", "Blade of Ages", 2, 0) { "+${it * 25}% all damage" },
    Artifact("gauntlet", "Titan Gauntlet", 2, 0) { "+${it * 40}% tap damage" },
    Artifact("banner", "War Banner", 2, 0) { "+${it * 35}% ally DPS" },
    Artifact("idol", "Golden Idol", 3, 0) { "+${it * 25}% gold" },
    Artifact("clover", "Lucky Clover", 3, 25) { "+$it% crit chance" },
    Artifact("fang", "Dragon Fang", 3, 0) { "+${it * 50}% crit damage" },
    Artifact("hourglass", "Hourglass", 4, 15) { "+${it * 2}s boss timer" },
    Artifact("crystal", "Mana Crystal", 4, 20) { "+${it * 5} max mana" },
    Artifact("tome", "Arcane Tome", 4, 20) { "+${it * 4}s spell time" },
    Artifact("boots", "Warp Boots", 5, 20) { "${it * 2}% stage skip" },
    Artifact("crown", "Tyrant Crown", 5, 15) { "-${it * 4}% boss HP" },
    Artifact("key", "Lucky Key", 4, 15) { "+$it% mimic chance" },
)

// ---------------------------------------------------------------- equipment
class Rarity(val name: String, val color: String, val mult: Double)

val RARITIES = listOf(
    Rarity("Common", "#c0c8d2", 1.0),
    Rarity("Rare", "#4fa3ff", 2.4),
    Rarity("Epic", "#b46cff", 5.0),
    Rarity("Legendary", "#ffb43a", 11.0),
)

class GearSlot(val id: String, val name: String, val stat: String, val label: String, val base: Double)

val GEAR_SLOTS = listOf(
    GearSlot("weapon", "Weapon", "tap", "tap damage", 0.3),
    GearSlot("helm", "Helmet", "crit", "crit damage", 0.35),
    GearSlot("armor", "Armor", "ally", "ally DPS", 0.3),
    GearSlot("aura", "Aura", "gold", "gold", 0.25),
)

class Gear(
    val id: String, val slot: String, val name: String, val r: Int,
    val blade: String? = null, val glow: String? = null, val helm: String? = null, val color: String? = null,
    val outfit: String? = null, val trim: String? = null, val aura: String? = null,
)

val GEAR = listOf(
    Gear("w_rusty", "weapon", "Rusty Blade", 0, blade = "#b98b6b"),
    Gear("w_iron", "weapon", "Iron Claymore", 0, blade = "#dfe6ee"),
    Gear("w_frost", "weapon", "Frost Edge", 1, blade = "#bff1ff", glow = "#5fd7ff"),
    Gear("w_ember", "weapon", "Ember Saber", 1, blade = "#ffb27a", glow = "#ff5a2a"),
    Gear("w_thunder", "weapon", "Thunder Fang", 2, blade = "#fff6a8", glow = "#ffe53a"),
    Gear("w_void", "weapon", "Void Reaper", 3, blade = "#d6b5ff", glow = "#9b4dff"),
    Gear("h_cap", "helm", "Leather Cap", 0, helm = "cap", color = "#8a5a3a"),
    Gear("h_iron", "helm", "Iron Visor", 0, helm = "visor", color = "#9aa6b5"),
    Gear("h_horn", "helm", "Horned Helm", 1, helm = "horned", color = "#6c7686"),
    Gear("h_circlet", "helm", "Sun Circlet", 2, helm = "circlet", color = "#ffd24a"),
    Gear("h_crown", "helm", "Crown of Kings", 3, helm = "crown", color = "#ffc21a"),
    Gear("a_tunic", "armor", "Traveler Tunic", 0, outfit = "#3f6fb5", trim = "#e9d27a"),
    Gear("a_chain", "armor", "Chainmail", 0, outfit = "#8e99a8", trim = "#5b6575"),
    Gear("a_forest", "armor", "Ranger Garb", 1, outfit = "#3f8a4a", trim = "#c9a24a"),
    Gear("a_royal", "armor", "Royal Plate", 2, outfit = "#e8eef5", trim = "#ffc83a"),
    Gear("a_dragon", "armor", "Dragonscale", 3, outfit = "#a8242a", trim = "#ffb43a"),
    Gear("u_spark", "aura", "Sparkle Aura", 0, aura = "#ffffff"),
    Gear("u_leaf", "aura", "Verdant Aura", 1, aura = "#7dff7a"),
    Gear("u_flame", "aura", "Flame Aura", 2, aura = "#ff7a2a"),
    Gear("u_star", "aura", "Starfall Aura", 3, aura = "#ffe34a"),
)

fun gearDef(id: String?) = GEAR.firstOrNull { it.id == id }
fun slotDef(id: String) = GEAR_SLOTS.first { it.id == id }

// ---------------------------------------------------------------- pets
class Pet(
    val id: String, val name: String, val stat: String, val label: String, val per: Double,
    val body: String, val belly: String, val wing: String, val ears: String,
    val fuzzy: Boolean = false, val sleepy: Boolean = false, val bow: Boolean = false,
)

val PETS = listOf(
    Pet("nova", "Nova", "tap", "tap damage", 0.15, "#ffbf2e", "#fff1d0", "#f0902a", "fox"),
    Pet("bubbles", "Bubbles", "ally", "ally DPS", 0.15, "#4a6ee0", "#9fb5ff", "#2f4aa8", "horns", fuzzy = true),
    Pet("toto", "Toto", "gold", "gold", 0.12, "#7a9a3a", "#d8e0a0", "#4f6a22", "fox", sleepy = true),
    Pet("soot", "Soot", "crit", "crit damage", 0.25, "#3fbf4a", "#f2d24a", "#2a8f34", "horns"),
    Pet("hamy", "Hamy", "mana", "mana regen", 0.08, "#c9372c", "#ffe6c8", "#ffffff", "fox", bow = true),
    Pet("gloop", "Gloop", "boss", "boss damage", 0.2, "#b06cff", "#e8d0ff", "#7a3ad0", "none"),
)

fun petDef(id: String?) = PETS.firstOrNull { it.id == id }
fun petEggsForLevel(level: Int) = level * 2

// ---------------------------------------------------------------- shop chests
class Chest(val id: String, val name: String, val gems: Int, val items: Int, val odds: DoubleArray, val body: String, val band: String)

val CHESTS = listOf(
    Chest("wood", "Wooden Chest", 120, 2, doubleArrayOf(0.75, 0.21, 0.035, 0.005), "#c98a3a", "#7d7d86"),
    Chest("silver", "Silver Chest", 300, 3, doubleArrayOf(0.45, 0.4, 0.13, 0.02), "#2f6fd0", "#d8dde5"),
    Chest("titan", "Titan Chest", 900, 5, doubleArrayOf(0.2, 0.42, 0.28, 0.1), "#f2b81e", "#b5781a"),
)

// ---------------------------------------------------------------- achievements
class AchGroup(
    val key: String, val name: String,
    val stat: (PlayerData) -> Double, val tiers: List<Double>, val gems: List<Int>, val text: (Double) -> String,
)

val ACH_GROUPS = listOf(
    AchGroup("kills", "Monster Slayer", { it.stats.kills.toDouble() }, listOf(50.0, 500.0, 5e3, 5e4, 5e5), listOf(5, 10, 20, 40, 80)) { "Defeat ${fmt(it)} monsters" },
    AchGroup("stage", "Explorer", { it.stats.bestStage.toDouble() }, listOf(10.0, 25.0, 50.0, 100.0, 150.0, 250.0), listOf(5, 10, 25, 50, 80, 120)) { "Reach stage ${it.toLong()}" },
    AchGroup("taps", "Tap Master", { it.stats.taps.toDouble() }, listOf(100.0, 1e3, 1e4, 1e5, 1e6), listOf(5, 10, 20, 40, 80)) { "Tap ${fmt(it)} times" },
    AchGroup("crits", "Sharp Eye", { it.stats.crits.toDouble() }, listOf(25.0, 250.0, 2500.0, 25000.0), listOf(5, 15, 30, 60)) { "Land ${fmt(it)} crits" },
    AchGroup("bosses", "Boss Hunter", { it.stats.bosses.toDouble() }, listOf(5.0, 25.0, 100.0, 500.0, 2000.0), listOf(10, 20, 40, 80, 150)) { "Defeat ${fmt(it)} bosses" },
    AchGroup("gold", "Hoarder", { it.stats.goldEarned }, listOf(1e4, 1e8, 1e12, 1e18, 1e24), listOf(5, 15, 30, 60, 120)) { "Earn ${fmt(it)} gold" },
    AchGroup("allies", "Recruiter", { it.heroes.size.toDouble() }, listOf(3.0, 6.0, 9.0, 12.0), listOf(10, 20, 40, 80)) { "Hire ${it.toLong()} allies" },
    AchGroup("prestige", "Reborn", { it.stats.prestiges.toDouble() }, listOf(1.0, 5.0, 20.0, 50.0), listOf(25, 50, 100, 200)) { "Prestige ${it.toLong()} times" },
    AchGroup("casts", "Spellslinger", { it.stats.casts.toDouble() }, listOf(10.0, 100.0, 1000.0, 5000.0), listOf(10, 20, 40, 80)) { "Cast ${fmt(it)} spells" },
    AchGroup("gear", "Collector", { it.gear.size.toDouble() }, listOf(3.0, 8.0, 14.0, GEAR.size.toDouble()), listOf(10, 25, 50, 150)) { "Collect ${it.toLong()} gear" },
    AchGroup("wisps", "Wisp Catcher", { it.stats.wisps.toDouble() }, listOf(5.0, 50.0, 250.0), listOf(10, 25, 60)) { "Catch ${it.toLong()} wisps" },
)

// ---------------------------------------------------------------- monster names
val NAME_A = listOf("Gor", "Bub", "Zar", "Mok", "Fen", "Skul", "Gri", "Vex", "Plo", "Drak", "Nib", "Hux", "Kra", "Lum", "Ooz", "Ral", "Bor", "Snag", "Wib", "Qua")
val NAME_B = listOf("ble", "gax", "rin", "zle", "mog", "ix", "ox", "ath", "uk", "ling", "bert", "ra", "nok", "pip", "dor", "goo")
val TITLES = listOf("the Vile", "the Huge", "the Unkind", "Muck Lord", "the Ancient", "the Glutton", "Bonecrusher", "the Eternal", "the Dreadful")
