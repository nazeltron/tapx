package dev.tapx

import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import java.io.File
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil

/** In-game flash sale: every gem price in the TapX shop is cut by [percent] until it ends. */
object FlashSale {
    @Volatile var percent = 0
        private set
    @Volatile var endsAt = 0L
        private set

    fun active() = percent > 0 && System.currentTimeMillis() < endsAt
    /** A gem price after the sale (if any). */
    fun price(gems: Int): Int = if (active()) ceil(gems * (100 - percent) / 100.0).toInt() else gems

    fun start(p: TapXPlugin, pct: Int, minutes: Double) {
        percent = pct.coerceIn(1, 90)
        endsAt = System.currentTimeMillis() + (minutes * 60_000).toLong()
        save(p)
        Bukkit.broadcast(mm(""))
        Bukkit.broadcast(mm("$PREFIX<yellow><b>⚡ FLASH SALE ⚡</b> <white>Everything in the TapX shop is <yellow><b>$percent% OFF</b></yellow> for ${fmtTime(minutes * 60)}!"))
        Bukkit.broadcast(mm(""))
        for (pl in Bukkit.getOnlinePlayers()) {
            pl.showTitle(Title.title(mm("<yellow><b>⚡ FLASH SALE ⚡"), mm("<white>$percent% off the gem shop!")))
            pl.playSound(pl.location, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6f, 1.4f)
        }
    }

    fun stop(p: TapXPlugin) { percent = 0; endsAt = 0; save(p) }

    fun load(p: TapXPlugin) {
        val y = YamlConfiguration.loadConfiguration(File(p.dataFolder, "sale.yml"))
        percent = y.getInt("percent"); endsAt = y.getLong("ends")
    }

    private fun save(p: TapXPlugin) {
        val y = YamlConfiguration(); y.set("percent", percent); y.set("ends", endsAt)
        y.save(File(p.dataFolder, "sale.yml"))
    }
}

/**
 * Web-store purchase broadcasts and the server-wide piggy bank (ported from DrillSim's /payment and
 * /piggybank): every real-money purchase fills it, and when it's full everyone gets an event.
 * Right after a purchase, players who type "GG" in chat get a gold reward.
 */
object Payments : Listener {
    private lateinit var plugin: TapXPlugin
    private var piggy = 0.0
    private var ggUntil = 0L
    private val ggClaimed = ConcurrentHashMap.newKeySet<java.util.UUID>()

    private val goal get() = plugin.config.getDouble("piggy-bank.goal-usd", 100.0).coerceAtLeast(1.0)

    fun enable(p: TapXPlugin) {
        plugin = p
        piggy = YamlConfiguration.loadConfiguration(File(p.dataFolder, "piggy.yml")).getDouble("usd")
        p.server.pluginManager.registerEvents(this, p)
    }

    private fun save() { val y = YamlConfiguration(); y.set("usd", piggy); y.save(File(plugin.dataFolder, "piggy.yml")) }

    fun payment(name: String, usd: Double, pkg: String) {
        val lines = listOf(
            "",
            "<gradient:#ffcf3a:#ff5a8a><b>THANK YOU!</b></gradient>",
            "<white>$name <gray>just supported the server!",
            "<gray>Package: <aqua>${pkg.ifBlank { "Store purchase" }}",
            "<yellow>Type <b>GG</b> in chat in the next 10 seconds for free gold!",
            "",
        )
        lines.forEach { Bukkit.broadcast(mm(it)) }
        for (pl in Bukkit.getOnlinePlayers()) pl.playSound(pl.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.3f)
        ggUntil = System.currentTimeMillis() + 10_000
        ggClaimed.clear()
        addPiggy(usd)
    }

    private fun addPiggy(usd: Double) {
        piggy += usd
        var fills = 0
        while (piggy >= goal) { piggy -= goal; fills++ }
        save()
        repeat(fills) {
            Bukkit.broadcast(mm("$PREFIX<light_purple><b>PIGGY BANK FILLED!</b> <white>Thanks to everyone who supported the server, here's a treat:"))
            ServerEvents.start(ServerEvents.TYPES.map { it.key }, plugin.config.getDouble("piggy-bank.event-minutes", 30.0),
                plugin.config.getDouble("piggy-bank.event-multiplier", 2.0), "piggy bank")
        }
    }

    fun piggyText() = "<light_purple><b>PIGGY BANK</b> <white>$${"%.2f".format(piggy)} <gray>/ <white>$${"%.0f".format(goal)} <gray>- when it fills, everyone gets a <b>${plugin.config.getDouble("piggy-bank.event-multiplier", 2.0)}x EVERYTHING</b> event!"

    @EventHandler
    fun onChat(e: io.papermc.paper.event.player.AsyncChatEvent) {
        if (System.currentTimeMillis() > ggUntil) return
        val msg = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(e.message()).trim()
        if (!msg.equals("gg", ignoreCase = true)) return
        val p = e.player
        if (!ggClaimed.add(p.uniqueId)) return
        val s = plugin.session(p) ?: return
        s.post { val g = s.game.killGold(s.d.stage) * 20; s.game.addGold(g); plugin.server.scheduler.runTask(plugin, Runnable { p.say("<gold>+${fmt(g)} gold <gray>for the GG!") }) }
    }
}

/** Promo codes: /tapx promo create <code> <maxUses|0> <hours|0> <rewards...>; players use /promo <code>. */
object Promos {
    private lateinit var plugin: TapXPlugin
    private lateinit var file: File

    class Code(val rewards: String, val maxUses: Int, val expires: Long, var uses: Int)
    private val codes = ConcurrentHashMap<String, Code>()

    const val SYNTAX = "gems:<n> gold:<kills> eggs:<n> relics:<n> boost:<surge|golden_hour|phantom|battle_frenzy|rain>:<minutes>"

    fun enable(p: TapXPlugin) {
        plugin = p
        file = File(p.dataFolder, "promos.yml")
        val y = YamlConfiguration.loadConfiguration(file)
        for (k in y.getKeys(false)) codes[k] = Code(y.getString("$k.rewards", "")!!, y.getInt("$k.max"), y.getLong("$k.expires"), y.getInt("$k.uses"))
        Cmds.register("promo", listOf("redeem", "code"), perm = null, usage = "/promo <code>", tab = { emptyList() }) { c ->
            val pl = c.needPlayer() ?: return@register
            val code = c.arg(0)?.lowercase() ?: run { pl.say("<red>Usage: /promo <code>"); return@register }
            redeem(pl, code)
        }
    }

    private fun save() {
        val y = YamlConfiguration()
        for ((k, c) in codes) { y.set("$k.rewards", c.rewards); y.set("$k.max", c.maxUses); y.set("$k.expires", c.expires); y.set("$k.uses", c.uses) }
        y.save(file)
    }

    fun create(code: String, maxUses: Int, hours: Double, rewards: String): String? {
        if (rewards.split(' ').any { parse(it) == null }) return "Bad reward. Use: $SYNTAX"
        codes[code.lowercase()] = Code(rewards, maxUses, if (hours > 0) System.currentTimeMillis() + (hours * 3_600_000).toLong() else 0, 0)
        save()
        return null
    }

    fun delete(code: String) = (codes.remove(code.lowercase()) != null).also { save() }
    fun list() = codes.entries.map { (k, c) -> "$k: ${c.rewards} (${c.uses}/${if (c.maxUses == 0) "∞" else c.maxUses} uses${if (c.expires > 0) ", ends in ${fmtTime((c.expires - System.currentTimeMillis()) / 1000.0)}" else ""})" }

    private fun parse(r: String): ((Game) -> String)? {
        val p = r.split(':')
        if (p[0].lowercase() != "boost" && p.getOrNull(1)?.toDoubleOrNull() == null) return null
        val n = p.getOrNull(1)?.toDoubleOrNull() ?: 0.0
        return when (p[0].lowercase()) {
            "gems" -> { g -> g.d.gems += n.toLong(); "${n.toLong()} gems" }
            "gold" -> { g -> val v = g.killGold(g.d.stage) * n; g.addGold(v); "${fmt(v)} gold" }
            "eggs" -> { g -> repeat(n.toInt()) { g.grantEggs(PETS.random().id, 1) }; "${n.toInt()} pet eggs" }
            "relics" -> { g -> g.d.relics += n.toLong(); "${n.toLong()} relics" }
            "boost" -> {
                val id = p.getOrNull(1) ?: return null
                val min = p.getOrNull(2)?.toDoubleOrNull() ?: return null
                if (SHOP_ITEMS.none { it.id == id && it.kind == "boost" }) return null
                { g -> g.d.buffs[id] = (g.d.buffs[id] ?: 0.0) + min * 60; "${SHOP_ITEMS.first { it.id == id }.name} for ${min.toInt()} min" }
            }
            else -> null
        }
    }

    private fun redeem(p: Player, code: String) {
        val c = codes[code] ?: return p.say("<red>That code doesn't exist.")
        if (c.expires in 1..System.currentTimeMillis()) return p.say("<red>That code has expired.")
        if (c.maxUses > 0 && c.uses >= c.maxUses) return p.say("<red>That code has been used up.")
        val s = plugin.session(p) ?: return
        s.post {
            if (code in s.d.promos) { plugin.server.scheduler.runTask(plugin, Runnable { p.say("<red>You already used that code.") }); return@post }
            s.d.promos += code
            c.uses++
            val got = c.rewards.split(' ').mapNotNull { parse(it)?.invoke(s.game) }
            plugin.server.scheduler.runTask(plugin, Runnable {
                save()
                p.say("<green>Code redeemed! <white>${got.joinToString("<gray>, <white>")}")
                p.playSound(p.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f)
            })
            plugin.save(s)
        }
    }
}
