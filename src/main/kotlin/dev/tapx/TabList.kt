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

    /** Staff rank: given by command (saved), else from permissions (ops show as ADMIN). */
    fun staff(p: Player): StaffRank? = staffDef(plugin.session(p)?.d?.staff) ?: when {
        p.hasPermission("tapx.tag.owner") && !p.isOp -> staffDef("owner")
        p.hasPermission("tapx.tag.admin") || p.isOp -> staffDef("admin")
        p.hasPermission("tapx.tag.mod") -> staffDef("mod")
        p.hasPermission("tapx.tag.helper") -> staffDef("helper")
        else -> null
    }

    /** The tag in front of a name: staff rank if any, else the store rank ("" for none). */
    fun tag(p: Player): String = staff(p)?.let { "<b><${it.color}>${it.name}</${it.color}></b>" } ?: rankTag(plugin.session(p)?.d?.rank)

    /** Colour of a player's name: staff colour, else rank colour (rainbow for LEGEND), else grey. */
    private fun nameColor(p: Player): Pair<String, String> {
        staff(p)?.let { return "<${it.color}>" to "</${it.color}>" }
        val r = rankDef(plugin.session(p)?.d?.rank) ?: return "<#aaaaaa>" to "</#aaaaaa>"
        return if (r.color == "rainbow") "<rainbow>" to "</rainbow>" else "<${r.color}>" to "</${r.color}>"
    }

    private fun nameOf(p: Player) = Essentials.nick(p)?.let { "<italic>$it</italic>" } ?: p.name

    private fun stageColor(stage: Int) = when {
        stage >= 300 -> "#ff55ff"; stage >= 150 -> "#ff9a3a"; stage >= 75 -> "#5a8aff"; stage >= 25 -> "#55ffff"; else -> "#55ff55"
    }

    private fun prestigeColor(n: Int) = when {
        n >= 50 -> "#b05aff"; n >= 20 -> "#ff5555"; n >= 5 -> "#55ffff"; n > 0 -> "#ffffff"; else -> "#aaaaaa"
    }

    /** One row like DrillSim's: "RANK Name  ✦stage [prestiges]", sorted staff > rank > stage. */
    fun refresh(p: Player) {
        if (!p.isOnline) return
        val d = plugin.session(p)?.d
        val (open, close) = nameColor(p)
        val t = tag(p).ifEmpty { "<b><#aaaaaa>MEMBER</#aaaaaa></b>" }
        val best = d?.stats?.bestStage ?: 1
        val pr = d?.stats?.prestiges ?: 0
        val afk = if (p.uniqueId in Essentials.afk) " <gray><i>AFK</i>" else ""
        val sc = stageColor(best)
        p.playerListName(mm("$t <b>$open${nameOf(p)}$close</b>  <b><$sc>✦$best</$sc></b> <b><dark_gray>[</dark_gray><${prestigeColor(pr)}>$pr</${prestigeColor(pr)}><dark_gray>]</dark_gray></b>$afk"))
        p.playerListOrder = (staff(p)?.order ?: 0) + (rankIndex(d?.rank) + 1) * 100 + minOf(99, best / 10)
    }

    fun refreshAll() {
        val online = Bukkit.getOnlinePlayers().filter { it.uniqueId !in Essentials.vanished }
        val tps = Bukkit.getTPS()[0].coerceAtMost(20.0)
        val title = plugin.config.getString("tab.header", "<b><#ff4ae0>Tap</#ff4ae0><white>X</white></b>")!!
        val season = plugin.config.getString("tab.season", "SEASON I")!!
        val address = plugin.config.getString("tab.address", "")!!.ifBlank { Discord.invite().removePrefix("https://").removePrefix("http://") }.uppercase()
        val top = ServerEvents.label()?.let { "<b><rainbow>ADMIN ABUSE</rainbow></b> <dark_gray>|</dark_gray> <rainbow>$it</rainbow> <gray>for <yellow>${fmtTime(ServerEvents.secondsLeft())}" }
            ?: if (FlashSale.active()) "<b><yellow>⚡ FLASH SALE</yellow></b> <dark_gray>|</dark_gray> <white>${FlashSale.percent}% off the gem shop" else null
        val line = "<#ff4ae0><st>                                                  </st>"
        for (p in Bukkit.getOnlinePlayers()) {
            refresh(p)
            val ping = p.ping
            val pingColor = if (ping < 80) "#55ff55" else if (ping < 180) "#ffff55" else "#ff5555"
            p.sendPlayerListHeaderAndFooter(
                mm("${top?.let { "$it\n" } ?: ""}$line\n$title\n<b><white>$season</white></b>\n"),
                mm("\n<b><#ff4ae0>ONLINE: </#ff4ae0><white>${online.size}</white> <dark_gray>|</dark_gray> <#ff4ae0>TPS: </#ff4ae0><white>${"%.0f".format(tps)}</white> " +
                    "<dark_gray>|</dark_gray> <#ff4ae0>PING: </#ff4ae0><$pingColor>$ping</$pingColor></b>\n\n" +
                    "<b><gradient:#ff4ae0:#b05aff>$address</gradient></b>\n$line"),
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
        val (open, close) = nameColor(p)
        val linked = if (Discord.available()) (if (d?.discordLinked == true) "<green>✔ " else "<red>✖ ") else ""
        val card = mm(
            "${tag(p).ifEmpty { "<gray>Player" }} <white>${p.name}\n" +
                "<gray>Stage: <white>${d?.stage ?: 1} <dark_gray>(best ${d?.stats?.bestStage ?: 1})\n" +
                "<gray>Prestiges: <white>${d?.stats?.prestiges ?: 0}\n" +
                "<gray>Tap level: <white>${d?.tapLevel ?: 1}\n" +
                "<gray>Discord: ${if (d?.discordLinked == true) "<green>linked" else "<red>not linked"}",
        )
        val head = mm("$linked${tag(p).let { if (it.isEmpty()) "" else "$it " }}$open${nameOf(p)}$close <dark_gray>» </dark_gray>")
            .hoverEvent(HoverEvent.showText(card))
        val color = if (rank != null && rankIndex(rank.key) >= 3) "<white>" else "<#d8dbe8>"
        e.renderer(ChatRenderer.viewerUnaware { _, _, msg -> head.append(mm(color).append(msg)) })
    }
}
