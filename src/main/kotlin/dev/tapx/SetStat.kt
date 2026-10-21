package dev.tapx

/** Stats /tapx set understands (prefixed ones take an id: ally.pip, artifact.blade ...). */
val STAT_NAMES: List<String> = listOf(
    "gold", "gems", "relics", "stage", "maxstage", "beststage", "taplevel", "mana", "prestiges", "kills", "taps", "bosses",
    "crits", "skillpoints", "rank", "tutorial", "wheel", "discord",
) + HEROES.map { "ally.${it.id}" } + ARTIFACTS.map { "artifact.${it.id}" } + SKILLS.map { "skill.${it.id}" } +
    SKILL_TREE.map { "tree.${it.id}" } + PETS.map { "pet.${it.id}" } + GEAR.map { "gear.${it.id}" } +
    SHOP_ITEMS.filter { it.kind == "perm" }.map { "pass.${it.id}" } + SHOP_ITEMS.filter { it.kind == "level" }.map { "level.${it.id}" }

/** Set one stat on a save. Returns a MiniMessage result line. */
fun setStat(d: PlayerData, stat: String, value: String): String {
    val n = value.toDoubleOrNull()
    val i = n?.toInt()
    fun bad() = "<red>'$value' isn't a valid value for $stat."
    fun ok() = "<green>Set ${d.name}'s $stat to $value."
    val (kind, id) = stat.split('.', limit = 2).let { it[0] to it.getOrNull(1) }
    if (id != null) {
        val v = i ?: if (kind == "pass") -1 else return bad()
        val map: MutableMap<String, Int>? = when (kind) {
            "ally" -> d.heroes.takeIf { HEROES.any { it.id == id } }
            "artifact" -> d.artifacts.takeIf { ARTIFACTS.any { it.id == id } }
            "skill" -> d.skills.takeIf { SKILLS.any { it.id == id } }
            "tree" -> d.skillTree.takeIf { SKILL_TREE.any { it.id == id } }
            "gear" -> d.gear.takeIf { GEAR.any { it.id == id } }
            "level" -> d.shopLevels.takeIf { SHOP_ITEMS.any { it.id == id && it.kind == "level" } }
            else -> null
        }
        when {
            kind == "pet" && PETS.any { it.id == id } -> {
                if (v <= 0) d.pets.remove(id) else d.pets.getOrPut(id) { PetData() }.lvl = v
                if (v <= 0 && d.activePet == id) d.activePet = null
                if (v > 0 && d.activePet == null) d.activePet = id
            }
            kind == "pass" && SHOP_ITEMS.any { it.id == id && it.kind == "perm" } -> {
                val on = value.lowercase() in setOf("true", "yes", "on", "1")
                if (on) d.owned += id else d.owned -= id
            }
            map != null -> {
                if (v <= 0) map.remove(id) else map[id] = v
                if (kind == "gear") d.equipped.entries.removeIf { it.value == id && v <= 0 }
                if (kind == "level" && id == "sp_pack") d.bonusSp = 3 * v
            }
            else -> return "<red>Unknown stat '$stat'."
        }
        return ok()
    }
    when (stat) {
        "gold" -> d.gold = n ?: return bad()
        "gems" -> d.gems = n?.toLong() ?: return bad()
        "relics" -> d.relics = n?.toLong() ?: return bad()
        "stage" -> { d.stage = i?.coerceAtLeast(1) ?: return bad(); d.wave = 0; d.farming = false; d.maxStage = maxOf(d.maxStage, d.stage); d.stats.bestStage = maxOf(d.stats.bestStage, d.stage) }
        "maxstage" -> d.maxStage = i?.coerceAtLeast(1) ?: return bad()
        "beststage" -> d.stats.bestStage = i?.coerceAtLeast(1) ?: return bad()
        "taplevel" -> d.tapLevel = i?.coerceAtLeast(1) ?: return bad()
        "mana" -> d.mana = n ?: return bad()
        "prestiges" -> d.stats.prestiges = i ?: return bad()
        "kills" -> d.stats.kills = n?.toLong() ?: return bad()
        "taps" -> d.stats.taps = n?.toLong() ?: return bad()
        "bosses" -> d.stats.bosses = n?.toLong() ?: return bad()
        "crits" -> d.stats.crits = n?.toLong() ?: return bad()
        "skillpoints" -> d.bonusSp = i ?: return bad()
        "rank" -> d.rank = if (value.lowercase() in setOf("none", "off", "remove")) null else rankDef(value.lowercase())?.key ?: return bad()
        "tutorial" -> d.tutorial = if (value.lowercase() in setOf("done", "off", "skip")) -1 else i ?: return bad()
        "wheel" -> d.wheelDay = -1 // lets them spin again today
        "discord" -> d.discordLinked = value.lowercase() in setOf("true", "yes", "on", "1")
        else -> return "<red>Unknown stat '$stat'. Try: ${STAT_NAMES.take(18).joinToString()}, ally.<id>, artifact.<id>, pet.<id>, pass.<id> ..."
    }
    return ok()
}
