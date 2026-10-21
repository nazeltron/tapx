package dev.tapx

import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.Material.*

// All static game content. Visuals are vanilla items, so no resource pack is needed.

const val WAVES = 8              // normal monsters per stage before the boss
const val BOSS_TIME = 30.0       // seconds to kill a boss
const val PRESTIGE_STAGE = 25    // stage needed to prestige
const val PET_CHARGE = 15        // taps per pet attack
const val SKILL_MAX = 10
const val FREE_CHEST_MS = 4L * 3600 * 1000
const val DEAL_MS = 6L * 3600 * 1000

class World(
    val name: String, val color: String, val icon: Material,
    val sky: Material, val ground: Material,
    val mobs: List<String>, val boss: Material,
)

val WORLDS = listOf(
    World("Meadow Ruins", "#7cd35a", GRASS_BLOCK, LIGHT_BLUE_STAINED_GLASS_PANE, LIME_STAINED_GLASS_PANE,
        listOf("ZOMBIE", "SKELETON", "SPIDER", "CREEPER", "SLIME", "WITCH", "PILLAGER"), CREEPER_HEAD),
    World("Sunscorch Dunes", "#f2b45a", SAND, ORANGE_STAINED_GLASS_PANE, YELLOW_STAINED_GLASS_PANE,
        listOf("HUSK", "CAVE_SPIDER", "SILVERFISH", "VINDICATOR", "PILLAGER", "CAMEL_HUSK"), RAVAGER_SPAWN_EGG),
    World("Frostpeak", "#bfe6ff", SNOW_BLOCK, CYAN_STAINED_GLASS_PANE, WHITE_STAINED_GLASS_PANE,
        listOf("STRAY", "POLAR_BEAR", "DROWNED", "GUARDIAN", "PHANTOM", "BOGGED"), ELDER_GUARDIAN_SPAWN_EGG),
    World("Gloomwood", "#9a7ad8", DARK_OAK_LOG, BLUE_STAINED_GLASS_PANE, GREEN_STAINED_GLASS_PANE,
        listOf("WITCH", "VEX", "EVOKER", "RAVAGER", "BREEZE", "CREAKING", "BOGGED"), WARDEN_SPAWN_EGG),
    World("Ember Depths", "#ff6a2a", MAGMA_BLOCK, RED_STAINED_GLASS_PANE, BROWN_STAINED_GLASS_PANE,
        listOf("BLAZE", "GHAST", "MAGMA_CUBE", "HOGLIN", "PIGLIN_BRUTE", "WITHER_SKELETON", "ZOGLIN", "STRIDER"), WITHER_SKELETON_SKULL),
    World("Crystal Void", "#c58aff", AMETHYST_BLOCK, PURPLE_STAINED_GLASS_PANE, BLACK_STAINED_GLASS_PANE,
        listOf("ENDERMAN", "ENDERMITE", "SHULKER", "VEX", "PHANTOM"), DRAGON_HEAD),
)

fun worldFor(stage: Int) = WORLDS[((stage - 1) / 10) % WORLDS.size]

fun mobEgg(mob: String): Material = Material.matchMaterial("${mob}_SPAWN_EGG") ?: ZOMBIE_SPAWN_EGG
fun mobLabel(mob: String) = mob.lowercase().split('_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

// ---------------------------------------------------------------- allies
class Hero(val id: String, val name: String, val cost: Double, val dps: Double, val icon: Material)

val HEROES = listOf(
    Hero("pip", "Pip the Squire", 50.0, 4.0, WOODEN_SWORD),
    Hero("wren", "Wren the Archer", 600.0, 35.0, BOW),
    Hero("ember", "Ember the Mage", 7.2e3, 300.0, BLAZE_ROD),
    Hero("grom", "Grom the Bruiser", 8.6e4, 2.6e3, IRON_AXE),
    Hero("nyx", "Nyx the Rogue", 1e6, 2.2e4, SHEARS),
    Hero("sol", "Sister Sol", 1.2e7, 1.9e5, MACE),
    Hero("bolt", "Bolt the Gunner", 1.5e8, 1.6e6, CROSSBOW),
    Hero("moss", "Moss the Druid", 1.8e9, 1.4e7, FLOWERING_AZALEA),
    Hero("dread", "The Dread Knight", 2.2e10, 1.2e8, NETHERITE_SWORD),
    Hero("vale", "Vale the Valkyrie", 3e11, 1.05e9, TRIDENT),
    Hero("wyrm", "Wyrmcaller", 4e12, 9e9, DRAGON_BREATH),
    Hero("void", "Oracle of the Void", 5.5e13, 8e10, ENDER_EYE),
)

/** Ally damage multipliers unlocked at level milestones. */
val HERO_MILESTONES = listOf(10 to 2.0, 25 to 2.0, 50 to 2.5, 75 to 2.0, 100 to 3.0, 150 to 2.5, 200 to 2.5, 300 to 3.0, 400 to 3.0, 500 to 4.0)

// ---------------------------------------------------------------- spells
class Skill(val id: String, val name: String, val req: Int, val cost: Double, val mana: Int, val timed: Boolean, val icon: Material)

val SKILLS = listOf(
    Skill("thunder", "Thunder Strike", 15, 250.0, 10, false, LIGHTNING_ROD),
    Skill("frenzy", "Frenzy Blades", 40, 5e3, 25, true, GOLDEN_SWORD),
    Skill("warcry", "War Cry", 75, 8e4, 30, true, GOAT_HORN),
    Skill("midas", "Midas Touch", 110, 1.5e6, 30, true, GOLD_BLOCK),
    Skill("berserk", "Berserk", 150, 3e7, 35, true, NETHERITE_AXE),
    Skill("eagle", "Eagle Eye", 200, 6e8, 40, true, SPYGLASS),
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
        "thunder" -> "Deal ${fmt(p)}x tap damage instantly"
        "frenzy" -> "Auto-tap $x times per second"
        "warcry" -> "Ally DPS x$x"
        "midas" -> "All gold x$x"
        "berserk" -> "Tap damage x$x"
        "eagle" -> "+$x% critical chance"
        else -> ""
    }
}

// ---------------------------------------------------------------- perks (gems)
class Perk(val id: String, val name: String, val gems: Int, val duration: Double, val desc: String, val icon: Material)

val PERKS = listOf(
    Perk("surge", "Power Surge", 40, 600.0, "Double ALL damage for 10 minutes", REDSTONE),
    Perk("phantom", "Phantom Hand", 50, 600.0, "Auto-tap 6 times per second for 10 minutes", PHANTOM_MEMBRANE),
    Perk("shower", "Gold Shower", 30, 0.0, "Instantly get gold worth 60 kills", RAW_GOLD),
    Perk("flask", "Mana Flask", 15, 0.0, "Instantly refill your mana", EXPERIENCE_BOTTLE),
)

// ---------------------------------------------------------------- artifacts (relics)
class Artifact(val id: String, val name: String, val base: Int, val max: Int, val icon: Material, val desc: (Int) -> String)

val ARTIFACTS = listOf(
    Artifact("blade", "Blade of Ages", 2, 0, ECHO_SHARD) { "+${it * 25}% all damage" },
    Artifact("gauntlet", "Titan Gauntlet", 2, 0, NETHERITE_INGOT) { "+${it * 40}% tap damage" },
    Artifact("banner", "War Banner", 2, 0, RED_BANNER) { "+${it * 35}% ally DPS" },
    Artifact("idol", "Golden Idol", 3, 0, RAW_GOLD_BLOCK) { "+${it * 25}% gold from monsters" },
    Artifact("clover", "Lucky Clover", 3, 25, LILY_PAD) { "+$it% critical chance" },
    Artifact("fang", "Dragon Fang", 3, 0, BONE) { "+${it * 50}% critical damage" },
    Artifact("hourglass", "Hourglass of Ages", 4, 15, CLOCK) { "+${it * 2}s boss fight timer" },
    Artifact("crystal", "Mana Crystal", 4, 20, AMETHYST_SHARD) { "+${it * 5} max mana, +${it * 10}% mana regen" },
    Artifact("tome", "Arcane Tome", 4, 20, ENCHANTED_BOOK) { "+${it * 4}s spell duration" },
    Artifact("boots", "Warp Boots", 5, 20, LEATHER_BOOTS) { "${it * 2}% chance to skip a stage" },
    Artifact("crown", "Tyrant Crown", 5, 15, GOLDEN_HELMET) { "-${it * 4}% boss HP" },
    Artifact("key", "Lucky Key", 4, 15, TRIAL_KEY) { "+$it% treasure mimic chance" },
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
    GearSlot("helm", "Helmet", "crit", "critical damage", 0.35),
    GearSlot("armor", "Armor", "ally", "ally DPS", 0.3),
    GearSlot("aura", "Aura", "gold", "gold", 0.25),
)

class Gear(val id: String, val slot: String, val name: String, val r: Int, val icon: Material, val dye: Color? = null)

val GEAR = listOf(
    Gear("w_rusty", "weapon", "Rusty Blade", 0, STONE_SWORD),
    Gear("w_iron", "weapon", "Iron Claymore", 0, IRON_SWORD),
    Gear("w_frost", "weapon", "Frost Edge", 1, DIAMOND_SWORD),
    Gear("w_ember", "weapon", "Ember Saber", 1, GOLDEN_SWORD),
    Gear("w_thunder", "weapon", "Thunder Fang", 2, TRIDENT),
    Gear("w_void", "weapon", "Void Reaper", 3, NETHERITE_SWORD),
    Gear("h_cap", "helm", "Leather Cap", 0, LEATHER_HELMET, Color.fromRGB(0x8a5a3a)),
    Gear("h_iron", "helm", "Iron Visor", 0, IRON_HELMET),
    Gear("h_horn", "helm", "Horned Helm", 1, CHAINMAIL_HELMET),
    Gear("h_circlet", "helm", "Sun Circlet", 2, DIAMOND_HELMET),
    Gear("h_crown", "helm", "Crown of Kings", 3, GOLDEN_HELMET),
    Gear("a_tunic", "armor", "Traveler Tunic", 0, LEATHER_CHESTPLATE, Color.fromRGB(0x3f6fb5)),
    Gear("a_chain", "armor", "Chainmail", 0, CHAINMAIL_CHESTPLATE),
    Gear("a_forest", "armor", "Ranger Garb", 1, LEATHER_CHESTPLATE, Color.fromRGB(0x3f8a4a)),
    Gear("a_royal", "armor", "Royal Plate", 2, DIAMOND_CHESTPLATE),
    Gear("a_dragon", "armor", "Dragonscale", 3, NETHERITE_CHESTPLATE),
    Gear("u_spark", "aura", "Sparkle Aura", 0, GLOWSTONE_DUST),
    Gear("u_leaf", "aura", "Verdant Aura", 1, EMERALD),
    Gear("u_flame", "aura", "Flame Aura", 2, BLAZE_POWDER),
    Gear("u_star", "aura", "Starfall Aura", 3, NETHER_STAR),
)

fun gearDef(id: String?) = GEAR.firstOrNull { it.id == id }
fun slotDef(id: String) = GEAR_SLOTS.first { it.id == id }

// ---------------------------------------------------------------- pets
class Pet(val id: String, val name: String, val icon: Material, val stat: String, val label: String, val per: Double)

val PETS = listOf(
    Pet("nova", "Nova", FOX_SPAWN_EGG, "tap", "tap damage", 0.15),
    Pet("bubbles", "Bubbles", ALLAY_SPAWN_EGG, "ally", "ally DPS", 0.15),
    Pet("toto", "Toto", PARROT_SPAWN_EGG, "gold", "gold", 0.12),
    Pet("soot", "Soot", AXOLOTL_SPAWN_EGG, "crit", "critical damage", 0.25),
    Pet("hamy", "Hamy", BEE_SPAWN_EGG, "mana", "mana regen", 0.08),
    Pet("gloop", "Gloop", FROG_SPAWN_EGG, "boss", "boss damage", 0.2),
)

fun petDef(id: String?) = PETS.firstOrNull { it.id == id }
fun petEggsForLevel(level: Int) = level * 2

// ---------------------------------------------------------------- shop chests
class Chest(val id: String, val name: String, val gems: Int, val items: Int, val odds: DoubleArray, val icon: Material)

val CHESTS = listOf(
    Chest("wood", "Wooden Chest", 120, 2, doubleArrayOf(0.75, 0.21, 0.035, 0.005), CHEST),
    Chest("silver", "Silver Chest", 300, 3, doubleArrayOf(0.45, 0.4, 0.13, 0.02), LIGHT_GRAY_SHULKER_BOX),
    Chest("titan", "Titan Chest", 900, 5, doubleArrayOf(0.2, 0.42, 0.28, 0.1), YELLOW_SHULKER_BOX),
)

// ---------------------------------------------------------------- achievements
class AchGroup(
    val key: String, val name: String, val icon: Material,
    val stat: (PlayerData) -> Double, val tiers: List<Double>, val gems: List<Int>, val text: (Double) -> String,
)

val ACH_GROUPS = listOf(
    AchGroup("kills", "Monster Slayer", IRON_SWORD, { it.stats.kills.toDouble() }, listOf(50.0, 500.0, 5e3, 5e4, 5e5), listOf(5, 10, 20, 40, 80)) { "Defeat ${fmt(it)} monsters" },
    AchGroup("stage", "Explorer", FILLED_MAP, { it.stats.bestStage.toDouble() }, listOf(10.0, 25.0, 50.0, 100.0, 150.0, 250.0), listOf(5, 10, 25, 50, 80, 120)) { "Reach stage ${it.toLong()}" },
    AchGroup("taps", "Tap Master", STONE_BUTTON, { it.stats.taps.toDouble() }, listOf(100.0, 1e3, 1e4, 1e5, 1e6), listOf(5, 10, 20, 40, 80)) { "Tap ${fmt(it)} times" },
    AchGroup("crits", "Sharp Eye", SPYGLASS, { it.stats.crits.toDouble() }, listOf(25.0, 250.0, 2500.0, 25000.0), listOf(5, 15, 30, 60)) { "Land ${fmt(it)} critical hits" },
    AchGroup("bosses", "Boss Hunter", WITHER_SKELETON_SKULL, { it.stats.bosses.toDouble() }, listOf(5.0, 25.0, 100.0, 500.0, 2000.0), listOf(10, 20, 40, 80, 150)) { "Defeat ${fmt(it)} bosses" },
    AchGroup("gold", "Hoarder", GOLD_INGOT, { it.stats.goldEarned }, listOf(1e4, 1e8, 1e12, 1e18, 1e24), listOf(5, 15, 30, 60, 120)) { "Earn ${fmt(it)} gold" },
    AchGroup("allies", "Recruiter", IRON_HELMET, { it.heroes.size.toDouble() }, listOf(3.0, 6.0, 9.0, 12.0), listOf(10, 20, 40, 80)) { "Hire ${it.toLong()} different allies" },
    AchGroup("prestige", "Reborn", NETHER_STAR, { it.stats.prestiges.toDouble() }, listOf(1.0, 5.0, 20.0, 50.0), listOf(25, 50, 100, 200)) { "Prestige ${it.toLong()} time(s)" },
    AchGroup("casts", "Spellslinger", BLAZE_POWDER, { it.stats.casts.toDouble() }, listOf(10.0, 100.0, 1000.0, 5000.0), listOf(10, 20, 40, 80)) { "Cast ${fmt(it)} spells" },
    AchGroup("gear", "Collector", CHEST, { it.gear.size.toDouble() }, listOf(3.0, 8.0, 14.0, GEAR.size.toDouble()), listOf(10, 25, 50, 150)) { "Collect ${it.toLong()} pieces of equipment" },
    AchGroup("wisps", "Wisp Catcher", GLOW_INK_SAC, { it.stats.wisps.toDouble() }, listOf(5.0, 50.0, 250.0), listOf(10, 25, 60)) { "Catch ${it.toLong()} wisps" },
)

// ---------------------------------------------------------------- monster names
val NAME_A = listOf("Gor", "Bub", "Zar", "Mok", "Fen", "Skul", "Gri", "Vex", "Plo", "Drak", "Nib", "Hux", "Kra", "Lum", "Ooz", "Ral", "Bor", "Snag", "Wib", "Qua")
val NAME_B = listOf("ble", "gax", "rin", "zle", "mog", "ix", "ox", "ath", "uk", "ling", "bert", "ra", "nok", "pip", "dor", "goo")
val TITLES = listOf("the Vile", "the Huge", "the Unkind", "Lord of Muck", "the Ancient", "the Gluttonous", "Bonecrusher", "the Eternal", "the Dreadful")
