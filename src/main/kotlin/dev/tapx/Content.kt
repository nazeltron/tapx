package dev.tapx

import kotlin.math.pow

// All static game content. Every visual is described by colours/shapes here and drawn in
// code by screen/Art.kt onto the map screen.

const val WAVES = 8              // normal monsters per stage before the boss
const val BOSS_TIME = 30.0       // seconds to kill a boss
const val PRESTIGE_STAGE = 25    // stage needed to prestige
const val PET_CHARGE = 15        // taps per pet attack
const val ALLY_COOLDOWN = 1.0    // seconds between each ally's attacks
const val TAP_DMG_GROWTH = 1.035 // hero damage per level (times level, times milestones)
const val TAP_COST_BASE = 8.0
const val TAP_COST_GROWTH = 1.09 // hero level cost per level
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
    World("Coral Deep", "ocean", "#0a3a6a" to "#2a8ac0", "#1a5a8a", "#e06a8a", "#b0406a", "#d8c890", "#f0e4b0", "#8a7a50"),
    World("Sky Citadel", "sky", "#5ab0ff" to "#fff0d8", "#d8e8ff", "#ffffff", "#c8d8f0", "#8ad06a", "#b0f08a", "#7a5a3a"),
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
    Hero("sly", "Sly the Ninja", 7e14, 6.5e11, Look("#f0c8a0", "#111111", "hood", "#2a2a2a", "#d23a3a", "#1a1a1a", "dagger", hood = "#1a1a1a")),
    Hero("bruna", "Bruna the Smith", 9e15, 5.5e12, Look("#d9a07a", "#a0522d", "bob", "#6b4a3a", "#c0c0c0", "#3a2a20", "mace")),
    Hero("coral", "Captain Coral", 1.2e17, 4.5e13, Look("#e0b090", "#2a1a0a", "bandana", "#e8e0d0", "#2a6ab0", "#2a3a5a", "gun", band = "#2a6ab0", cape = "#2a6ab0")),
    Hero("astra", "Astra the Star Mage", 1.6e18, 3.8e14, Look("#f6d8c0", "#ffd8f0", "wizard", "#5a4ac0", "#ffe34a", "#2a2060", "staff", hat = "#3a2a8a", glow = "#ff9af0")),
)

/** The player's fighter: a beetle knight with a round shield and a warhammer (Art.drawBeetleKnight). */
val HERO_LOOK = Look("#f6cba4", "#a8703a", "beetle", "#8e8498", "#a8703a", "#2c2532", "hammer", boots = "#3a2a24")

/** Hero tap damage doubles every 50 hero levels. */
fun tapMilestoneMult(level: Int): Double = Math.pow(2.0, (level / 50).toDouble())

// ---------------------------------------------------------------- training (gold upgrades, reset on prestige)
class Training(val id: String, val name: String, val icon: String, val base: Double, val max: Int, val desc: (Int) -> String)

val TRAINING = listOf(
    Training("t_sword", "Sword Training", "blade", 5e3, 25) { "+${it * 10}% tap damage" },
    Training("t_ally", "Leadership", "banner", 1e4, 25) { "+${it * 10}% ally damage" },
    Training("t_crit", "Precision", "fang", 5e4, 20) { "+${"%.1f".format(it * 0.5)}% crit chance, +${it * 10}% crit damage" },
    Training("t_gold", "Treasure Sense", "idol", 2e5, 25) { "+${it * 6}% gold" },
    Training("t_boss", "Titan Hunter", "crown", 1e6, 20) { "+${it * 10}% boss damage" },
    Training("t_mana", "Meditation", "crystal", 2e6, 15) { "+${it * 3} max mana, +${it * 5}% regen" },
)

fun trainingCost(t: Training, level: Int) = t.base * 4.0.pow(level)

/** Ally damage multipliers unlocked at level milestones. */
val HERO_MILESTONES = listOf(10 to 2.0, 25 to 2.0, 50 to 2.5, 75 to 2.0, 100 to 3.0, 150 to 2.5, 200 to 2.5, 300 to 3.0, 400 to 3.0, 500 to 4.0)

// ---------------------------------------------------------------- spells
class Skill(val id: String, val name: String, val req: Int, val cost: Double, val mana: Int, val timed: Boolean)

val SKILLS = listOf(
    Skill("thunder", "Thunder Strike", 10, 250.0, 10, false),
    Skill("frenzy", "Frenzy Blades", 25, 5e3, 25, true),
    Skill("warcry", "War Cry", 50, 8e4, 30, true),
    Skill("midas", "Midas Touch", 75, 1.5e6, 30, true),
    Skill("berserk", "Berserk", 100, 3e7, 35, true),
    Skill("eagle", "Eagle Eye", 130, 6e8, 40, true),
    Skill("haste", "Haste", 160, 1.2e10, 35, true),
    Skill("clone", "Shadow Clone", 200, 2.5e11, 40, true),
    Skill("meteor", "Meteor Storm", 240, 5e12, 50, false),
    Skill("execute", "Execute", 280, 1e14, 45, false),
    Skill("petfrenzy", "Pet Frenzy", 330, 2e15, 30, true),
    Skill("timewarp", "Time Warp", 380, 4e16, 35, false),
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
        "haste" -> 1.5 + 0.15 * l            // ally attack speed
        "clone" -> 4.0 + 0.6 * l             // auto-taps per second, always critical
        "meteor" -> 150.0 + 75 * l           // x tap damage
        "execute" -> 4.0 + 0.75 * l          // % of the monster's max HP (bosses take a third)
        "petfrenzy" -> maxOf(2.0, 6.0 - l / 3)  // taps per pet attack
        "timewarp" -> 6.0 + 1.5 * l          // seconds added to a boss fight
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
        "haste" -> "Allies attack x$x as fast"
        "clone" -> "+$x crit taps/sec"
        "meteor" -> "Deal ${fmt(p)}x tap damage"
        "execute" -> "Deal $x% of max HP"
        "petfrenzy" -> "Pet strikes every ${p.toInt()} taps"
        "timewarp" -> "+${x}s boss timer (in a boss fight)"
        else -> ""
    }
}

// ---------------------------------------------------------------- gem shop (gems are the paid currency)
/** Something bought with gems. [kind]: boost (timed), instant, perm (one-time), level (levelled), chest, eggs, skin. */
class ShopItem(
    val id: String, val section: String, val name: String, val desc: String, val gems: Int,
    val icon: String, val kind: String, val duration: Double = 0.0, val maxLevel: Int = 1,
)

val SHOP_SECTIONS = listOf(
    "offers" to "SPECIAL OFFERS",
    "rank" to "RANKS - PERKS FOREVER",
    "perm" to "GAMEPASSES",
    "boost" to "BOOSTS",
    "instant" to "INSTANT REWARDS",
    "chest" to "CHESTS - FIND EQUIPMENT",
    "eggs" to "PET EGGS",
    "skin" to "HERO SKINS",
)

val SHOP_ITEMS = listOf(
    ShopItem("starter", "offers", "Starter Pack", "Titan Chest + 10 eggs + 2 boosts (once)", 300, "chest_titan", "perm"),
    ShopItem("vip_gold", "perm", "Royal Treasury", "x2 gold from everything, forever", 800, "midas", "perm"),
    ShopItem("double_dmg", "perm", "Warrior's Blessing", "x2 ALL damage, forever", 1000, "blade", "perm"),
    ShopItem("auto_tap", "perm", "Auto Tapper", "+1 automatic tap per second", 200, "phantom", "level", maxLevel = 5),
    ShopItem("offline_master", "perm", "Offline Master", "Full offline gold, up to 24 hours", 500, "moon", "perm"),
    ShopItem("mana_well", "perm", "Mana Well", "+25 max mana, forever", 300, "crystal", "perm"),
    ShopItem("sp_pack", "perm", "Skill Book", "+3 skill points", 150, "tome", "level", maxLevel = 20),
    ShopItem("global2x", "perm", "Global x2", "x2 gold, damage AND relics", 2500, "crown", "perm"),
    ShopItem("relics2x", "perm", "Relic Hunter", "x2 relics from prestige", 1200, "relic", "perm"),
    ShopItem("ally2x", "perm", "Ally Fury", "x2 ally damage, forever", 900, "banner", "perm"),
    ShopItem("crit_master", "perm", "Crit Master", "+5% crit chance, +50% crit damage", 900, "fang", "perm"),
    ShopItem("boss_slayer", "perm", "Boss Slayer", "+10s boss timer, +25% boss damage", 700, "hourglass", "perm"),
    ShopItem("pet_trainer", "perm", "Pet Trainer", "x2 pet damage, pets charge faster", 700, "egg", "perm"),
    ShopItem("arcane_flow", "perm", "Arcane Flow", "x2 mana regen", 600, "crystal", "perm"),
    ShopItem("lucky", "perm", "Lucky Charm", "x2 mimics and wisps, better free chest", 600, "clover", "perm"),
    ShopItem("auto_boss", "perm", "Auto Boss", "Fights the boss again by itself", 400, "boots", "perm"),
    ShopItem("surge", "boost", "Power Surge", "x2 ALL damage for 10 min", 40, "surge", "boost", 600.0),
    ShopItem("golden_hour", "boost", "Golden Hour", "x2 gold for 20 min", 60, "clock", "boost", 1200.0),
    ShopItem("phantom", "boost", "Phantom Hand", "Auto-tap 6/sec for 10 min", 50, "phantom", "boost", 600.0),
    ShopItem("battle_frenzy", "boost", "Battle Frenzy", "x3 tap damage for 5 min", 90, "berserk", "boost", 300.0),
    ShopItem("rain", "boost", "Make It Rain", "Auto-levels your allies for 30 min", 100, "shower", "boost", 1800.0),
    ShopItem("flask", "instant", "Mana Flask", "Refill your mana", 15, "flask", "instant"),
    ShopItem("shower", "instant", "Gold Shower", "Gold worth 60 kills", 30, "midas", "instant"),
    ShopItem("mountain", "instant", "Gold Mountain", "Gold worth 500 kills", 250, "shower", "instant"),
    ShopItem("relic_pack", "instant", "Relic Bundle", "Relics worth a quarter prestige", 150, "relic", "instant"),
    ShopItem("warp", "instant", "Warp Scroll", "Skip 10 stages (up to your best)", 100, "boots", "instant"),
    ShopItem("respec", "instant", "Forget Skills", "Refund all skill points", 50, "prestige", "instant"),
    ShopItem("eggs5", "eggs", "Egg Basket", "5 random pet eggs", 100, "egg", "eggs"),
    ShopItem("eggs20", "eggs", "Egg Crate", "20 random pet eggs", 350, "egg", "eggs"),
)

// ---------------------------------------------------------------- hero skins (gems)
class Skin(val id: String, val name: String, val gems: Int, val apply: (Look) -> Look)

val SKINS = listOf(
    Skin("pirate", "Sea Rover", 250) { it.copy(hairStyle = "bandana", hair = "#3a2a1a", band = "#c0392b", outfit = "#2a4a6a", trim = "#e8c547", cape = "#1a1a1a") },
    Skin("shadow", "Shadow Knight", 300) { it.copy(hair = "#1a1a2a", outfit = "#2a2a3a", trim = "#8a2aff", cape = "#4a1a6a", blade = "#c8a8ff", bladeGlow = "#8a2aff") },
    Skin("frost", "Frost Warden", 300) { it.copy(hair = "#e0f4ff", outfit = "#3a7ab8", trim = "#bff1ff", cape = "#8ad8ff", blade = "#bff1ff", bladeGlow = "#5fd7ff") },
    Skin("inferno", "Inferno Lord", 400) { it.copy(hair = "#ff5a2a", outfit = "#5a1a10", trim = "#ff9a2a", cape = "#ff5a2a", blade = "#ffb27a", bladeGlow = "#ff5a2a", aura = "#ff7a2a") },
    Skin("golden", "Golden Champion", 500) { it.copy(hair = "#ffd23a", outfit = "#e8c040", trim = "#ffffff", cape = "#ffffff", blade = "#fff6a8", bladeGlow = "#ffe34a", aura = "#ffe34a") },
    Skin("royal", "Royal Highness", 800) { it.copy(helmGear = "crown", helmColor = "#ffc21a", outfit = "#6a2a9a", trim = "#ffd23a", cape = "#c0392b", aura = "#ffffff") },
)

fun skinDef(id: String?) = SKINS.firstOrNull { it.id == id }

// ---------------------------------------------------------------- skill tree (skill points)
class TreeNode(val id: String, val branch: Int, val name: String, val max: Int, val desc: (Int) -> String)

val TREE_BRANCHES = listOf("KNIGHT" to "e8643a", "WARLORD" to "3aa0b8", "SORCERER" to "8a5ad0", "ROGUE" to "58b04a")

val SKILL_TREE = listOf(
    TreeNode("k_edge", 0, "Sharp Edge", 10) { "+${it * 15}% tap damage" },
    TreeNode("k_eye", 0, "Keen Eye", 10) { "+${"%.1f".format(it * 0.5)}% crit chance" },
    TreeNode("k_heavy", 0, "Heavy Blows", 10) { "+${it * 25}% crit damage" },
    TreeNode("k_bane", 0, "Titan Bane", 10) { "+${it * 10}% boss damage" },
    TreeNode("w_rally", 1, "Rally", 10) { "+${it * 15}% ally DPS" },
    TreeNode("w_bargain", 1, "Bargain", 10) { "-${it * 2}% ally costs" },
    TreeNode("w_drill", 1, "Drill Sergeant", 10) { "+${"%.1f".format(it * 0.5)}% of ally DPS to taps" },
    TreeNode("w_vets", 1, "Veterans", 10) { "+${it * 10}% ALL damage" },
    TreeNode("s_deep", 2, "Deep Mana", 10) { "+${it * 5} max mana" },
    TreeNode("s_flow", 2, "Mana Flow", 10) { "+${it * 10}% mana regen" },
    TreeNode("s_long", 2, "Long Spells", 10) { "+${it * 3}s spell time" },
    TreeNode("s_storm", 2, "Storm Caller", 10) { "+${it * 25}% Thunder damage" },
    TreeNode("r_greed", 3, "Greed", 10) { "+${it * 10}% gold" },
    TreeNode("r_mimic", 3, "Mimic Hunter", 10) { "+${"%.1f".format(it * 0.5)}% mimic chance" },
    TreeNode("r_loot", 3, "Boss Loot", 10) { "+${it * 25}% boss gold" },
    TreeNode("r_warp", 3, "Warp Step", 10) { "+$it% stage skip" },
)

// ---------------------------------------------------------------- daily login & quests
/** 7-day login calendar; day 7 gives a few gems. */
val LOGIN_REWARDS = listOf("Gold x50 kills", "2 pet eggs", "Gold x100 kills", "Free gear", "4 pet eggs", "Gold x250 kills", "5 gems")

class QuestDef(val id: String, val text: (Int) -> String, val target: (Int) -> Int, val stat: (PlayerData) -> Long)

val QUEST_POOL = listOf(
    QuestDef("taps", { "Tap $it times" }, { 300 + it * 2 }, { it.stats.taps }),
    QuestDef("kills", { "Defeat $it monsters" }, { 100 + it / 2 }, { it.stats.kills }),
    QuestDef("bosses", { "Defeat $it bosses" }, { 5 }, { it.stats.bosses }),
    QuestDef("casts", { "Cast $it spells" }, { 5 }, { it.stats.casts }),
    QuestDef("crits", { "Land $it critical hits" }, { 25 }, { it.stats.crits }),
    QuestDef("stages", { "Clear $it stages" }, { 10 }, { it.stats.stagesCleared }),
    QuestDef("upgrades", { "Buy $it upgrades" }, { 25 }, { it.stats.upgrades }),
    QuestDef("wisps", { "Catch $it wisp" }, { 1 }, { it.stats.wisps }),
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
    Artifact("coin", "Ancient Coin", 3, 10) { "+${it * 20}% offline gold" },
    Artifact("collar", "Pet Collar", 3, 0) { "+${it * 30}% pet damage" },
    Artifact("totem", "Storm Totem", 3, 0) { "+${it * 40}% Thunder damage" },
    Artifact("lantern", "Spirit Lantern", 3, 10) { "+${it * 8}% wisp sightings" },
    Artifact("map", "Treasure Map", 3, 0) { "+${it * 50}% mimic gold" },
    Artifact("slayer", "Giant Slayer", 4, 0) { "+${it * 10}% boss damage" },
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
    Pet("sparky", "Sparky", "thunder", "Thunder damage", 0.3, "#ffe34a", "#fff8c0", "#4a8aff", "horns", fuzzy = true),
    Pet("bramble", "Bramble", "offline", "offline gold", 0.2, "#8a6a3a", "#e8d8b0", "#5a9a3a", "fox", sleepy = true),
)

fun petDef(id: String?) = PETS.firstOrNull { it.id == id }
fun petEggsForLevel(level: Int) = level * 2

// ---------------------------------------------------------------- shop chests
class Chest(val id: String, val name: String, val gems: Int, val items: Int, val odds: DoubleArray, val body: String, val band: String)

val CHESTS = listOf(
    Chest("wood", "Wooden Chest", 60, 2, doubleArrayOf(0.75, 0.21, 0.035, 0.005), "#c98a3a", "#7d7d86"),
    Chest("silver", "Silver Chest", 150, 3, doubleArrayOf(0.45, 0.4, 0.13, 0.02), "#2f6fd0", "#d8dde5"),
    Chest("titan", "Titan Chest", 450, 5, doubleArrayOf(0.2, 0.42, 0.28, 0.1), "#f2b81e", "#b5781a"),
    Chest("legend", "Legendary Chest", 1200, 5, doubleArrayOf(0.0, 0.0, 0.6, 0.4), "#7a2ad0", "#ffd23a"),
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

// ---------------------------------------------------------------- store ranks (gems)
/**
 * A rank bought with gems. Higher ranks include everything below; upgrading costs only the
 * difference. [color] is a MiniMessage colour (or "rainbow").
 */
class Rank(
    val key: String, val name: String, val gems: Int, val color: String, val hex: String,
    val gold: Double, val damage: Double, val autoTaps: Int, val offlineHours: Int, val dailyBonus: Double,
    val mana: Int, val chestHours: Double, val spins: Int, val loginGems: Int, val skin: String?,
)

val RANKS = listOf(
    Rank("vip", "VIP", 500, "#ffaa33", "ffaa33", 1.25, 1.15, 0, 14, 1.25, 10, 3.5, 1, 0, null),
    Rank("pro", "PRO", 1200, "#55ff55", "55ff55", 1.5, 1.3, 1, 16, 1.5, 15, 3.0, 1, 1, null),
    Rank("mvp", "MVP", 3000, "#55ffff", "55ffff", 1.75, 1.5, 1, 18, 1.75, 20, 3.0, 2, 2, null),
    Rank("elite", "ELITE", 6000, "#ff5555", "ff5555", 2.25, 1.75, 2, 20, 2.0, 25, 2.5, 2, 3, "shadow"),
    Rank("titan", "TITAN", 15000, "#ff55ff", "ff55ff", 3.0, 2.25, 3, 24, 2.5, 35, 2.0, 3, 5, "royal"),
    Rank("legend", "LEGEND", 35000, "rainbow", "ffd23a", 4.0, 3.0, 4, 24, 3.0, 50, 1.5, 3, 8, "golden"),
)

/** Every reward of a rank, one per line. */
fun rankRewards(r: Rank): List<String> = buildList {
    add("x${r.gold} gold from everything")
    add("x${r.damage} ALL damage")
    if (r.autoTaps > 0) add("+${r.autoTaps} automatic tap${if (r.autoTaps > 1) "s" else ""} every second")
    add("Offline earnings up to ${r.offlineHours} hours")
    add("Daily wheel & login rewards x${r.dailyBonus}")
    add("${r.spins} daily wheel spin${if (r.spins > 1) "s" else ""}")
    add("+${r.mana} max mana")
    add("Free chest every ${if (r.chestHours % 1.0 == 0.0) r.chestHours.toInt().toString() else r.chestHours.toString()} hours")
    if (r.loginGems > 0) add("+${r.loginGems} gems on day 7 of the login calendar")
    r.skin?.let { s -> add("Free hero skin: ${SKINS.first { it.id == s }.name}") }
    add("${r.name} tag in chat and the tab list" + if (r.color == "rainbow") " (rainbow!)" else "")
}

fun rankDef(key: String?) = RANKS.firstOrNull { it.key == key }

/** Staff ranks, given only by command (/rank set <player> owner). OWNER also gets every LEGEND perk. */
class StaffRank(val key: String, val name: String, val color: String, val order: Int)

val STAFF_RANKS = listOf(
    StaffRank("owner", "OWNER", "#b05aff", 4000), StaffRank("admin", "ADMIN", "#ff3a3a", 3000),
    StaffRank("mod", "MOD", "#4ab8ff", 2000), StaffRank("helper", "HELPER", "#58d06a", 1000),
)

fun staffDef(key: String?) = STAFF_RANKS.firstOrNull { it.key == key }

/** The store rank whose perks apply: the owner gets LEGEND's. */
fun PlayerData.perkRank(): String? = if (staff == "owner") "legend" else rank
fun rankIndex(key: String?) = RANKS.indexOfFirst { it.key == key }

/** "x1.5 gold, x1.35 damage, +1 auto tap, 18h offline" */
fun rankPerks(r: Rank): String = buildList {
    add("x${r.gold} gold"); add("x${r.damage} dmg")
    if (r.autoTaps > 0) add("+${r.autoTaps} auto tap")
    add("${r.offlineHours}h offline")
}.joinToString(", ")

// ---------------------------------------------------------------- daily wheel (label, chance)
val WHEEL = listOf("GOLD" to 0.25, "EGGS" to 0.18, "RELICS" to 0.12, "2X GOLD" to 0.12, "MANA" to 0.12, "BIG GOLD" to 0.10, "CHEST" to 0.08, "GEMS" to 0.03)

// ---------------------------------------------------------------- Discord link rewards
// Active while the account is linked (/link, DM the code to the bot) and the player is in the Discord server.
const val LINK_GOLD = 1.5
const val LINK_DAMAGE = 1.25
const val LINK_AUTO_TAPS = 1
const val LINK_MANA = 10
/** One-time gifts for the first link. */
const val LINK_GEMS = 10L
const val LINK_EGGS = 5

val LINK_REWARDS = listOf(
    "<gold>x$LINK_GOLD gold</gold> from everything",
    "<red>x$LINK_DAMAGE damage</red> (taps and allies)",
    "<yellow>+$LINK_AUTO_TAPS automatic tap</yellow> every second",
    "<aqua>+$LINK_MANA max mana</aqua>",
    "<green>✔</green> next to your name in chat",
    "<light_purple>One-time gift: <aqua>$LINK_GEMS gems</aqua> + <green>$LINK_EGGS pet eggs</green>",
)
