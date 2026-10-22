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
    private val DEFAULT_HEADER = "<b>${TextFx.wrap("title", "TapX")}</b>"
    /** Earlier defaults (3.3.x), replaced by the current animated one. */
    private val OLD_HEADERS = setOf("<b><#ff4ae0>Tap</#ff4ae0><white>X</white></b>", "<b><gradient:#ffcf3a:#ff7a3a:#ff5a8a>TapX</gradient></b>")

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
    fun tag(p: Player): String = staff(p)?.let { "<b>${TextFx.wrap("tag:${it.key}", it.name)}</b>" } ?: rankTag(plugin.session(p)?.d?.rank)

    /** Colour of a player's name: the staff or rank colour with a softer animation, else grey. */
    private fun nameColor(p: Player): Pair<String, String> {
        val key = staff(p)?.key ?: rankDef(plugin.session(p)?.d?.rank)?.key ?: return "<#aaaaaa>" to "</#aaaaaa>"
        val c = TextFx.color("name:$key")
        return "<$c>" to "</$c>"
    }

    private fun nameOf(p: Player) = Essentials.nick(p)?.let { "<italic>$it</italic>" } ?: p.name

    private fun stageColor(stage: Int) = when {
        stage >= 300 -> "#ff3cf0"; stage >= 150 -> "#ff8a12"; stage >= 75 -> "#3c7aff"; stage >= 25 -> "#2ff2ff"; else -> "#3dff4a"
    }

    private fun prestigeColor(n: Int) = when {
        n >= 50 -> "#a63cff"; n >= 20 -> "#ff2e3e"; n >= 5 -> "#2ff2ff"; n > 0 -> "#ffffff"; else -> "#aaaaaa"
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
        val pc = prestigeColor(pr)
        p.playerListName(mm("$t $open${nameOf(p)}$close  <$sc>✦$best</$sc> <$pc>[$pr]</$pc>$afk"))
        p.playerListOrder = (staff(p)?.order ?: 0) + (rankIndex(d?.rank) + 1) * 100 + minOf(99, best / 10)
    }

    fun refreshAll() {
        val online = Bukkit.getOnlinePlayers().filter { it.uniqueId !in Essentials.vanished }
        val tps = Bukkit.getTPS()[0].coerceAtMost(20.0)
        val title = plugin.config.getString("tab.header", "")!!.takeUnless { it.isBlank() || it in OLD_HEADERS } ?: DEFAULT_HEADER
        val season = smallCaps(plugin.config.getString("tab.season", "Season I")!!)
        val address = plugin.config.getString("tab.address", "")!!.ifBlank { Discord.invite().removePrefix("https://").removePrefix("http://") }.let(::smallCaps)
        val top = ServerEvents.label()?.let { "<b>${TextFx.wrap("event", "ADMIN ABUSE")}</b> <dark_gray>|</dark_gray> ${TextFx.wrap("event", smallCaps(it))} <gray>${smallCaps("for")} <yellow>${fmtTime(ServerEvents.secondsLeft())}" }
            ?: if (FlashSale.active()) "<b><yellow>⚡ FLASH SALE</yellow></b> <dark_gray>|</dark_gray> <white>${smallCaps("${FlashSale.percent}% off the gem shop")}" else null
        val line = TextFx.wrap("line", "<st>                                                  </st>")
        for (p in Bukkit.getOnlinePlayers()) {
            refresh(p)
            val ping = p.ping
            val pingColor = if (ping < 80) "#3dff4a" else if (ping < 180) "#ffe21a" else "#ff2e3e"
            p.sendPlayerListHeaderAndFooter(
                mm("${top?.let { "$it\n" } ?: ""}$line\n$title\n${TextFx.wrap("season", season)}\n"),
                mm("\n${TextFx.wrap("label", smallCaps("Online:"))} <white>${online.size}</white> <dark_gray>|</dark_gray> ${TextFx.wrap("label", smallCaps("TPS:"))} <white>${"%.0f".format(tps)}</white> " +
                    "<dark_gray>|</dark_gray> ${TextFx.wrap("label", smallCaps("Ping:"))} <$pingColor>$ping</$pingColor>\n\n" +
                    "${TextFx.wrap("address", address)}\n$line"),
            )
        }
    }

    @EventHandler
    fun onJoin(e: PlayerJoinEvent) {
        plugin.server.scheduler.runTaskLater(plugin, Runnable { refreshAll() }, 5L)
        val p = e.player
        if (!p.hasPlayedBefore() && plugin.config.getBoolean("chat.first-join-broadcast", true)) {
            val n = Bukkit.getOfflinePlayers().size
            e.joinMessage(mm("$PREFIX<white>${p.name} <gray>joined for the first time! <dark_gray>[<yellow>#$n<dark_gray>]"))
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
        val linked = if (d?.discordLinked == true) "<!b><green>✔</green></!b> " else ""
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
