package dev.tapx

import io.papermc.paper.chat.ChatRenderer
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.HoverEvent
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

/**
 * The player list (a styled header and footer with live info, rank tags, stage and AFK in the
 * names, sorted by rank) and the chat format (rank tag, name, hover card with the player's stats).
 */
object TabList : Listener {
    private lateinit var plugin: TapXPlugin

    fun enable(p: TapXPlugin) {
        plugin = p
        p.server.pluginManager.registerEvents(this, p)
        p.server.scheduler.runTaskTimer(p, Runnable { refreshAll() }, 40L, 40L)
    }

    private fun staffTag(p: Player): Pair<String, Int>? = when {
        p.hasPermission("tapx.tag.owner") && !p.isOp -> "<b><gradient:#ff4a4a:#ff9a3a>[OWNER]</gradient></b>" to 1000
        p.hasPermission("tapx.tag.admin") || p.isOp -> "<b><red>[ADMIN]</red></b>" to 900
        p.hasPermission("tapx.tag.mod") -> "<b><#4ab8ff>[MOD]</#4ab8ff></b>" to 800
        p.hasPermission("tapx.tag.helper") -> "<b><#58d06a>[HELPER]</#58d06a></b>" to 700
        else -> null
    }

    /** The tag in front of a name: staff tag if any, else the store rank. */
    fun tag(p: Player): String = staffTag(p)?.first ?: rankTag(plugin.session(p)?.d?.rank)

    private fun nameOf(p: Player) = Essentials.nick(p)?.let { "<italic>$it</italic>" } ?: p.name

    fun refresh(p: Player) {
        if (!p.isOnline) return
        val d = plugin.session(p)?.d
        val rank = rankDef(d?.rank)
        val nameColor = if (rank != null && rank.color != "rainbow") "<${rank.color}>" else "<white>"
        val afk = if (p.uniqueId in Essentials.afk) " <gray><i>AFK</i>" else ""
        val stage = d?.let { " <dark_gray>✦ <yellow>${it.stats.bestStage}" } ?: ""
        val t = tag(p)
        p.playerListName(mm("${if (t.isNotEmpty()) "$t " else ""}$nameColor${nameOf(p)}$stage$afk"))
        p.playerListOrder = (staffTag(p)?.second ?: 0) + (rankIndex(d?.rank) + 1) * 100 + minOf(99, (d?.stats?.bestStage ?: 0) / 10)
    }

    fun refreshAll() {
        val online = Bukkit.getOnlinePlayers().filter { it.uniqueId !in Essentials.vanished }
        val tps = Bukkit.getTPS()[0].coerceAtMost(20.0)
        val tpsColor = if (tps >= 18) "<green>" else if (tps >= 15) "<yellow>" else "<red>"
        val event = ServerEvents.label()?.let { "\n<b><rainbow>ADMIN ABUSE</rainbow></b> <dark_gray>» <rainbow>$it</rainbow> <gray>(${fmtTime(ServerEvents.secondsLeft())})" } ?: ""
        val sale = if (FlashSale.active()) "\n<yellow><b>⚡ FLASH SALE:</b> <white>${FlashSale.percent}% off the gem shop!" else ""
        val title = plugin.config.getString("tab.title", "TAPX")!!
        val sub = plugin.config.getString("tab.subtitle", "Tap Titans, right in Minecraft")!!
        for (p in Bukkit.getOnlinePlayers()) {
            refresh(p)
            val d = plugin.session(p)?.d
            val me = d?.let { "\n<gray>Stage <white>${it.stage}</white>  <dark_gray>|</dark_gray>  <gray>Best <white>${it.stats.bestStage}</white>  <dark_gray>|</dark_gray>  <aqua>${fmt(it.gems)} gems</aqua>" } ?: ""
            p.sendPlayerListHeaderAndFooter(
                mm("\n<gradient:#ffcf3a:#ff7a3a:#ff5a8a><b>✦ $title ✦</b></gradient>\n<gray>$sub\n"),
                mm("$me\n<gray>Online <white>${online.size}</white>  <dark_gray>|</dark_gray>  <gray>Ping <white>${p.ping}ms</white>  <dark_gray>|</dark_gray>  <gray>TPS $tpsColor${"%.1f".format(tps)}$event$sale\n" +
                    "<aqua>/store</aqua> <dark_gray>•</dark_gray> <aqua>/discord</aqua> <dark_gray>•</dark_gray> <aqua>/events</aqua> <dark_gray>•</dark_gray> <aqua>/rules</aqua>\n"),
            )
        }
    }

    @EventHandler
    fun onJoin(e: PlayerJoinEvent) {
        plugin.server.scheduler.runTaskLater(plugin, Runnable { refreshAll() }, 5L)
        val p = e.player
        if (!p.hasPlayedBefore() && plugin.config.getBoolean("chat.first-join-broadcast", true)) {
            val n = Bukkit.getOfflinePlayers().size
            e.joinMessage(mm("<gradient:#ffcf3a:#ff5a8a><b>TAPX</b></gradient> <dark_gray>» <white>${p.name} <gray>joined for the first time! <dark_gray>[<yellow>#$n<dark_gray>]"))
        } else e.joinMessage(mm("<dark_gray>[<green>+<dark_gray>] <gray>${p.name}"))
    }

    @EventHandler
    fun onQuit(e: org.bukkit.event.player.PlayerQuitEvent) {
        e.quitMessage(mm("<dark_gray>[<red>-<dark_gray>] <gray>${e.player.name}"))
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onChat(e: AsyncChatEvent) {
        if (!plugin.config.getBoolean("chat.format", true)) return
        val p = e.player
        val d = plugin.session(p)?.d
        val rank = rankDef(d?.rank)
        val nameColor = if (rank != null && rank.color != "rainbow") "<${rank.color}>" else "<white>"
        val linked = if (Discord.available()) (if (d?.discordLinked == true) "<green>✔ " else "<red>✖ ") else ""
        val card = mm(
            "${tag(p).ifEmpty { "<gray>Player" }} <white>${p.name}\n" +
                "<gray>Stage: <white>${d?.stage ?: 1} <dark_gray>(best ${d?.stats?.bestStage ?: 1})\n" +
                "<gray>Prestiges: <white>${d?.stats?.prestiges ?: 0}\n" +
                "<gray>Tap level: <white>${d?.tapLevel ?: 1}\n" +
                "<gray>Discord: ${if (d?.discordLinked == true) "<green>linked" else "<red>not linked"}",
        )
        val head = mm("$linked${tag(p).let { if (it.isEmpty()) "" else "$it " }}$nameColor${nameOf(p)} <dark_gray>» </dark_gray>")
            .hoverEvent(HoverEvent.showText(card))
        val color = if (rank != null && rankIndex(rank.key) >= 3) "<white>" else "<#d8dbe8>"
        e.renderer(ChatRenderer.viewerUnaware { _, _, msg -> head.append(mm(color).append(msg)) })
    }
}
