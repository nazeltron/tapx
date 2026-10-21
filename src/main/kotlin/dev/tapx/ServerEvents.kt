package dev.tapx

import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * Server-wide boosts ("ADMIN ABUSE", ported from DrillSim's /event): while one runs, everyone's
 * gold, damage, relics, mana or luck is multiplied. Started by admins (or the web store / piggy bank
 * from the console), announced in chat and shown on the game screen (a banner, then a strip).
 * Events survive restarts.
 */
object ServerEvents {
    class Type(val key: String, val name: String, val color: String)

    val TYPES = listOf(
        Type("gold", "Gold", "#ffd23a"), Type("damage", "Damage", "#ff5a4a"), Type("relics", "Relics", "#c58aff"),
        Type("mana", "Mana", "#4aa8ff"), Type("luck", "Luck", "#58d06a"),
    )

    private class Ev(val mult: Double, val end: Long, val length: Long)

    private val active = ConcurrentHashMap<String, Ev>()
    private var plugin: TapXPlugin? = null
    private lateinit var file: File

    /** The multiplier for [key] right now (1 when nothing runs). Safe from any thread. */
    fun mult(key: String): Double {
        val e = active[key] ?: return 1.0
        return if (e.end > System.currentTimeMillis()) e.mult else 1.0
    }

    /** Short label for HUDs, e.g. "2x EVERYTHING" or "3x GOLD + DAMAGE"; null when no event runs. */
    fun label(): String? {
        val now = System.currentTimeMillis()
        val on = TYPES.filter { (active[it.key]?.end ?: 0) > now }
        if (on.isEmpty()) return null
        val m = on.maxOf { active[it.key]!!.mult }
        val what = if (on.size == TYPES.size) "EVERYTHING" else on.joinToString(" + ") { it.name.uppercase() }
        return "${fmtMult(m)}x $what"
    }

    fun secondsLeft(): Double {
        val now = System.currentTimeMillis()
        return active.values.filter { it.end > now }.minOfOrNull { (it.end - now) / 1000.0 } ?: 0.0
    }

    private fun fmtMult(m: Double) = if (m % 1.0 == 0.0) m.toLong().toString() else "%.2f".format(m).trimEnd('0').trimEnd('.')

    private fun rainbow(text: String, shift: Int = 0) = "<rainbow:$shift>$text</rainbow>"

    fun enable(p: TapXPlugin) {
        plugin = p
        file = File(p.dataFolder, "events.yml")
        val y = YamlConfiguration.loadConfiguration(file)
        for (k in y.getKeys(false)) active[k] = Ev(y.getDouble("$k.mult"), y.getLong("$k.end"), y.getLong("$k.length"))
        p.server.scheduler.runTaskTimer(p, Runnable { everySecond() }, 20L, 20L)
        register()
    }

    private fun save() {
        val y = YamlConfiguration()
        for ((k, e) in active) { y.set("$k.mult", e.mult); y.set("$k.end", e.end); y.set("$k.length", e.length) }
        y.save(file)
    }

    fun start(keys: List<String>, minutes: Double, mult: Double, by: String) {
        val now = System.currentTimeMillis()
        val ms = (minutes * 60_000).toLong()
        for (k in keys) active[k] = Ev(mult, now + ms, ms)
        save()
        val label = label() ?: return
        plugin?.logger?.info("$by started event ${keys.joinToString(",")} x$mult for $minutes min")
        val lines = listOf(
            "",
            "<b>${rainbow("ADMIN ABUSE")}</b> <yellow><b>STARTED!</b></yellow>",
            "<white>${rainbow(label)} <gray>for <white>${fmtTime(minutes * 60)}",
            "<gray>Everyone online gets it! <dark_gray>(/events)",
            "",
        )
        for (l in lines) Bukkit.broadcast(mm(l))
        // shown on the game screen itself (a banner, then a strip under the HP bar); no boss bar or title
        for (pl in Bukkit.getOnlinePlayers()) pl.playSound(pl.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f)
    }

    /** Turn an event on without any server (for the preview renderer). */
    internal fun startQuietly(keys: List<String>, minutes: Double, mult: Double) {
        val ms = (minutes * 60_000).toLong()
        for (k in keys) active[k] = Ev(mult, System.currentTimeMillis() + ms, ms)
    }

    fun stop(keys: List<String>) {
        keys.forEach { active.remove(it) }
        save()
    }

    private fun everySecond() {
        val now = System.currentTimeMillis()
        val ended = active.filterValues { it.end <= now }.keys
        if (ended.isNotEmpty()) {
            ended.forEach { active.remove(it) }
            save()
            if (label() == null) {
                Bukkit.broadcast(mm("$PREFIX<gray>The <b>${rainbow("ADMIN ABUSE")}</b> <gray>has <red>ended</red>. Thanks for playing!"))
                for (pl in Bukkit.getOnlinePlayers()) pl.playSound(pl.location, Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1f)
            }
        }
    }

    private fun parseTypes(s: String?): List<String>? = when (s?.lowercase()) {
        null -> null
        "all", "everything" -> TYPES.map { it.key }
        else -> TYPES.firstOrNull { it.key == s.lowercase() }?.let { listOf(it.key) }
    }

    private fun register() {
        val typeNames = TYPES.map { it.key } + "all"
        Cmds.register("event", listOf("globalevent", "gevent", "adminabuse"), "tapx.admin", "/event <start|stop|gems|giveaway>",
            tab = { c -> when (c.args.size) {
                1 -> listOf("start", "stop", "gems", "giveaway")
                2 -> if (c.arg(0) == "start" || c.arg(0) == "stop") typeNames else listOf("10", "50", "100")
                3 -> if (c.arg(0) == "start") listOf("15", "30", "60") else listOf("1", "3", "5")
                4 -> listOf("1.5", "2", "3")
                else -> emptyList()
            } }) { c ->
            when (c.arg(0)?.lowercase()) {
                "start" -> {
                    val types = parseTypes(c.arg(1)); val minutes = c.num(2); val mult = c.num(3) ?: 2.0
                    if (types == null || minutes == null || minutes <= 0 || minutes > 1440 || mult <= 1 || mult > 100) {
                        c.sender.say("<red>Usage: /event start <${typeNames.joinToString("|")}> <minutes 1-1440> [multiplier, default 2]"); return@register
                    }
                    start(types, minutes, mult, c.sender.name)
                }
                "stop", "end" -> {
                    val types = parseTypes(c.arg(1)) ?: run { c.sender.say("<red>Usage: /event stop <type|all>"); return@register }
                    stop(types); c.sender.say("<green>Stopped.")
                }
                "gems" -> {
                    val n = c.arg(1)?.toLongOrNull()?.takeIf { it > 0 } ?: run { c.sender.say("<red>Usage: /event gems <amount> (every online player)"); return@register }
                    for (pl in Bukkit.getOnlinePlayers()) plugin?.giveGems(pl.name, n, "event by ${c.sender.name}", paid = false)
                    Bukkit.broadcast(mm("$PREFIX<aqua><b>GEM RAIN!</b> <white>Everyone online gets <aqua><b>$n gems</b></aqua>!"))
                }
                "giveaway" -> {
                    val n = c.arg(1)?.toLongOrNull()?.takeIf { it > 0 } ?: run { c.sender.say("<red>Usage: /event giveaway <gems> [winners]"); return@register }
                    val winners = (c.int(2) ?: 1).coerceAtLeast(1)
                    giveaway(n, winners, c.sender.name)
                }
                else -> c.sender.say("<yellow>/event start <type|all> <minutes> [multi]<gray>, <yellow>/event stop <type|all><gray>, <yellow>/event gems <n><gray>, <yellow>/event giveaway <gems> [winners]")
            }
        }
        Cmds.register("events", listOf("activeevents", "eventlist", "boosts"), perm = null) { c ->
            val label = label()
            if (label == null) c.sender.say("<gray>No server events are running right now. Keep an eye on <aqua>/discord</aqua>!")
            else c.sender.say("<b>${rainbow("ADMIN ABUSE")}</b> <dark_gray>» ${rainbow(label)} <dark_gray>- <white>${fmtTime(secondsLeft())} <gray>left")
        }
    }

    private fun giveaway(gems: Long, winners: Int, by: String) {
        val p = plugin ?: return
        Bukkit.broadcast(mm("$PREFIX<aqua><b>GEM GIVEAWAY!</b> <white>$winners lucky player${if (winners > 1) "s" else ""} will win <aqua>$gems gems</aqua>! Drawing in 3 seconds..."))
        for (i in 3 downTo 1) p.server.scheduler.runTaskLater(p, Runnable {
            for (pl in Bukkit.getOnlinePlayers()) {
                pl.showTitle(Title.title(mm("<aqua><b>GIVEAWAY"), mm("<white>$i"), Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ZERO)))
                pl.playSound(pl.location, Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f)
            }
        }, (3 - i) * 20L)
        p.server.scheduler.runTaskLater(p, Runnable {
            val won = Bukkit.getOnlinePlayers().shuffled().take(winners)
            for (w in won) {
                p.giveGems(w.name, gems, "giveaway by $by", paid = false)
                w.showTitle(Title.title(mm("<green><b>YOU WON!"), mm("<aqua>+$gems gems")))
                w.playSound(w.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f)
            }
            Bukkit.broadcast(mm("$PREFIX<aqua><b>GIVEAWAY WINNERS:</b> <white>" + won.joinToString("<gray>, <white>") { it.name }))
        }, 60L)
    }
}
