package dev.tapx

import com.destroystokyo.paper.profile.PlayerProfile
import org.bukkit.BanEntry
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.Sound
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * The warning system (ported from DrillSim's /warn): every reason has a punishment ladder, so the
 * 1st, 2nd, 3rd... active warn for the same rule gets harsher (verbal → mutes → permanent mute, or
 * bans). Warns expire after `warns.expire-days`; the full history is kept. Staff level comes from
 * the staff rank (/rank set <player> helper|mod|admin|owner) or ops; harsher punishments need a
 * higher level. Players who were offline see their warning when they join.
 */
object Warns : Listener {
    private lateinit var plugin: TapXPlugin
    private lateinit var file: File
    private lateinit var data: YamlConfiguration

    sealed class Punish(val label: String, val level: Int) {
        object Verbal : Punish("verbal warning", 1)
        object Log : Punish("logged only", 1)
        object Kick : Punish("kick", 1)
        class Mute(val d: Duration?) : Punish(if (d == null) "permanent mute" else "mute ${fmtDur(d)}", 1)
        class Ban(val d: Duration?) : Punish(if (d == null) "permanent ban" else "ban ${fmtDur(d)}", if (d == null) 3 else 2)
        object IpBan : Punish("IP ban", 3)
    }

    class Reason(val id: String, val name: String, val color: String, val desc: String, val ladder: List<Punish>)

    private fun h(n: Long) = Duration.ofHours(n)
    private fun d(n: Long) = Duration.ofDays(n)
    private val CHAT_LADDER = listOf(Punish.Verbal, Punish.Mute(h(3)), Punish.Mute(h(6)), Punish.Mute(h(12)), Punish.Mute(h(24)), Punish.Mute(null))
    private val BAN_LADDER = listOf(Punish.Ban(d(3)), Punish.Ban(d(12)), Punish.Ban(null))

    val REASONS = listOf(
        Reason("racism", "Racism", "#aa0000", "Racist language of any kind.", listOf(Punish.Mute(null))),
        Reason("homophobia", "Homophobia", "#aa0000", "Homophobic language of any kind.", listOf(Punish.Mute(null))),
        Reason("advertising", "Advertising", "#aa0000", "Advertising other servers or services.", listOf(Punish.Mute(h(24)), Punish.Mute(null))),
        Reason("swearing", "Swearing / Bypass", "#ff5555", "Profanity or bypassing the chat filter.", CHAT_LADDER),
        Reason("spamming", "Spamming", "#ff5555", "Spamming chat.", CHAT_LADDER),
        Reason("staffdisrespect", "Staff Disrespect", "#ff5555", "Disrespecting a staff member.", CHAT_LADDER),
        Reason("askingforop", "Asking For OP", "#ff5555", "Asking for rank, OP or staff.", CHAT_LADDER),
        Reason("caps", "Full Caps", "#ff5555", "Typing in mass capitals.", CHAT_LADDER),
        Reason("ignoringstaff", "Ignoring Staff", "#ff5555", "Refusing to respond to or comply with staff.", CHAT_LADDER),
        Reason("inappropriate", "Inappropriate Chat", "#ff5555", "Inappropriate or sexual chat.", CHAT_LADDER),
        Reason("toxicity", "Toxicity", "#ff5555", "Being toxic or harassing players.", CHAT_LADDER),
        Reason("botting", "Botting", "#aa0000", "Running bot accounts on the server.", listOf(Punish.IpBan)),
        Reason("terror", "Terror Talk", "#aa0000", "Threats of real-world violence or terrorism.", listOf(Punish.Ban(null))),
        Reason("threats", "Threats Towards Server", "#aa0000", "Threatening the server, staff or infrastructure.", listOf(Punish.Ban(null))),
        Reason("cheating", "Cheating", "#ffaa00", "Unauthorised or cheat clients, auto-clickers.", listOf(Punish.Ban(d(14)), Punish.Ban(null))),
        Reason("macros", "Macros", "#ffaa00", "Macros or auto-tapping tools.", BAN_LADDER),
        Reason("boosting", "Boosting", "#ffaa00", "Boosting stats or leaderboards.", BAN_LADDER),
        Reason("blackmail", "Blackmail", "#ffaa00", "Blackmailing another player.", BAN_LADDER),
        Reason("cmdspam", "Spamming Commands", "#ffaa00", "Spamming commands.", listOf(Punish.Kick, Punish.Ban(h(3)), Punish.Ban(d(1)), Punish.Ban(d(3)))),
        Reason("scamming", "Scamming", "#aaaaaa", "Scamming players. (manual)", listOf(Punish.Log)),
        Reason("impersonation", "Impersonation", "#aaaaaa", "Pretending to be staff. (manual)", listOf(Punish.Log)),
        Reason("irltrading", "IRL Trading", "#aaaaaa", "Real-money trades. (manual)", listOf(Punish.Log)),
        Reason("nonenglish", "Non-English Chat", "#aaaaaa", "Please keep chat in English. (manual)", listOf(Punish.Log)),
        Reason("rejoinspam", "Rejoin Spam", "#aaaaaa", "Join-spam. (manual)", listOf(Punish.Log)),
        Reason("wastingtime", "Wasting Staff Time", "#aaaaaa", "Wasting staff time. (manual)", listOf(Punish.Log)),
    )

    fun fmtDur(d: Duration): String = when {
        d.toDays() >= 1 && d.toHours() % 24 == 0L -> "${d.toDays()} day${if (d.toDays() > 1) "s" else ""}"
        d.toHours() >= 1 -> "${d.toHours()} hour${if (d.toHours() > 1) "s" else ""}"
        else -> "${d.toMinutes()} min"
    }

    class Warn(val reason: String, val by: String, val note: String, val time: Long, var active: Boolean, val punishment: String)

    fun enable(p: TapXPlugin) {
        plugin = p
        file = File(p.dataFolder, "warns.yml")
        data = YamlConfiguration.loadConfiguration(file)
        p.server.pluginManager.registerEvents(this, p)
        register()
    }

    private fun save() = synchronized(data) { data.save(file) }

    private fun expireMs() = plugin.config.getDouble("warns.expire-days", 30.0) * 86_400_000

    private fun log(id: UUID): MutableList<Warn> = synchronized(data) {
        data.getMapList("players.$id.log").map { m ->
            Warn(m["reason"].toString(), m["by"].toString(), m["note"]?.toString() ?: "", (m["time"] as Number).toLong(), m["active"] as? Boolean ?: true, m["punishment"]?.toString() ?: "")
        }.toMutableList()
    }

    private fun setLog(id: UUID, name: String?, l: List<Warn>) = synchronized(data) {
        if (name != null) data.set("players.$id.name", name)
        data.set("players.$id.log", l.map { mapOf("reason" to it.reason, "by" to it.by, "note" to it.note, "time" to it.time, "active" to it.active, "punishment" to it.punishment) })
    }

    private fun isActive(w: Warn) = w.active && System.currentTimeMillis() - w.time < expireMs()
    fun active(id: UUID) = log(id).filter(::isActive)

    private fun reason(s: String?): Reason? {
        val k = s?.lowercase()?.replace(Regex("[^a-z]"), "") ?: return null
        return REASONS.firstOrNull { it.id == k } ?: REASONS.firstOrNull { it.name.lowercase().replace(Regex("[^a-z]"), "") == k }
            ?: REASONS.filter { it.id.startsWith(k) }.singleOrNull()
    }

    private fun nextPunish(id: UUID, r: Reason): Punish {
        val n = active(id).count { it.reason == r.id }
        return r.ladder[minOf(n, r.ladder.size - 1)]
    }

    private fun target(name: String): OfflinePlayer? = Bukkit.getPlayerExact(name) ?: Bukkit.getOfflinePlayerIfCached(name)

    private fun register() {
        val players = { _: Cmds.Ctx -> Bukkit.getOnlinePlayers().map { it.name } }
        Cmds.register("warn", listOf("punish"), null, "/warn <player> [reason] [note]",
            tab = { c -> if (staffLevel(c.sender) < 1) emptyList() else when (c.args.size) { 1 -> players(c); 2 -> REASONS.map { it.id }; else -> emptyList() } }) { c ->
            if (staffLevel(c.sender) < 1) return@register c.sender.say("<red>This command is for <white>Helper<red> and above.")
            val t = c.arg(0)?.let(::target) ?: return@register usage(c.sender)
            if (t.uniqueId == (c.sender as? Player)?.uniqueId) return@register c.sender.say("<red>You can't warn yourself.")
            val r = c.arg(1)?.let { reason(it) ?: return@register c.sender.say("<red>Unknown reason <white>$it<red>. Use <white>/warnreasons<red>.") }
            if (r == null) return@register menu(c.sender, t)
            warn(c.sender, t, r, c.args.drop(2).joinToString(" "))
        }
        Cmds.register("warnreasons", listOf("warnlist"), null, "/warnreasons", tab = { emptyList() }) { c ->
            if (staffLevel(c.sender) < 1) return@register c.sender.say("<red>This command is for <white>Helper<red> and above.")
            c.sender.sayRaw("<red><b>WARN REASONS")
            for (r in REASONS) c.sender.sayRaw(" <dark_gray>- <${r.color}>${r.id} <gray>${r.desc} <dark_gray>(${r.ladder.joinToString(" → ") { it.label }})")
        }
        Cmds.register("warns", listOf("checkwarns"), null, "/warns <player>", tab = players) { c ->
            if (staffLevel(c.sender) < 1) return@register c.sender.say("<red>This command is for <white>Helper<red> and above.")
            val t = c.arg(0)?.let(::target) ?: return@register c.sender.say("<gray>Usage: <white>/warns <player>")
            showWarns(c.sender, t.uniqueId, t.name ?: c.arg(0)!!)
        }
        Cmds.register("mywarns", emptyList(), null, "/mywarns", tab = { emptyList() }) { c ->
            val p = c.needPlayer() ?: return@register
            val a = active(p.uniqueId)
            c.sender.sayRaw("<red><b>YOUR WARNS")
            if (a.isEmpty()) c.sender.sayRaw("<green>You have no active warns. Keep it that way!")
            for (w in a) c.sender.sayRaw(" <dark_gray>- <red>${reason(w.reason)?.name ?: w.reason} <gray>${ago(w.time)} ago${if (w.punishment.isNotEmpty()) " <dark_gray>(${w.punishment})" else ""}")
            c.sender.sayRaw("<gray>Warns expire after <white>${plugin.config.getDouble("warns.expire-days", 30.0).toInt()} days<gray>.")
        }
        Cmds.register("unwarn", listOf("delwarn"), null, "/unwarn <player> <reason>", tab = { c -> when (c.args.size) { 1 -> players(c); 2 -> REASONS.map { it.id }; else -> emptyList() } }) { c ->
            if (staffLevel(c.sender) < 2) return@register c.sender.say("<red>Removing warns needs <white>Mod<red> or higher.")
            val t = c.arg(0)?.let(::target); val r = reason(c.arg(1))
            if (t == null || r == null) return@register c.sender.say("<gray>Usage: <white>/unwarn <player> <reason>")
            val l = log(t.uniqueId)
            val w = l.lastOrNull { it.reason == r.id && isActive(it) } ?: return@register c.sender.say("<red>They have no active <white>${r.name} <red>warns.")
            w.active = false; setLog(t.uniqueId, t.name, l); save()
            staff("<dark_gray>[<red>WARN<dark_gray>] <white>${c.sender.name} <gray>removed a <white>${r.name} <gray>warn from <white>${t.name}")
        }
        Cmds.register("clearwarns", listOf("resetwarns"), null, "/clearwarns <player>", tab = players) { c ->
            if (staffLevel(c.sender) < 3) return@register c.sender.say("<red>Clearing warns needs <white>Admin<red> or higher.")
            val t = c.arg(0)?.let(::target) ?: return@register c.sender.say("<gray>Usage: <white>/clearwarns <player>")
            val l = log(t.uniqueId); l.forEach { it.active = false }; setLog(t.uniqueId, t.name, l); save()
            staff("<dark_gray>[<red>WARN<dark_gray>] <white>${c.sender.name} <gray>cleared <red>all<gray> of <white>${t.name}<gray>'s warns. <dark_gray>(history kept)")
        }
        Cmds.register("warnhistory", listOf("warnlog"), null, "/warnhistory <player> [page]", tab = players) { c ->
            if (staffLevel(c.sender) < 1) return@register c.sender.say("<red>This command is for <white>Helper<red> and above.")
            val t = c.arg(0)?.let(::target) ?: return@register c.sender.say("<gray>Usage: <white>/warnhistory <player> [page]")
            val l = log(t.uniqueId).reversed()
            val page = (c.int(1) ?: 1).coerceAtLeast(1)
            val pages = maxOf(1, (l.size + 7) / 8)
            c.sender.sayRaw("<red><b>HISTORY</b> <dark_gray>» <white>${t.name} <gray>(page $page/$pages)")
            if (l.isEmpty()) c.sender.sayRaw("<green>No warns ever.")
            for (w in l.drop((page - 1) * 8).take(8)) c.sender.sayRaw(
                " <dark_gray>[${if (isActive(w)) "<red>active" else "<gray>expired"}<dark_gray>] <white>${reason(w.reason)?.name ?: w.reason} <dark_gray>| <gray>by <white>${w.by} <dark_gray>| <gray>${ago(w.time)} ago" +
                    (if (w.punishment.isNotEmpty()) " <dark_gray>| <gray>${w.punishment}" else "") + (if (w.note.isNotEmpty()) " <dark_gray>| <gray>${ChatFilter.esc(w.note)}" else ""))
            if (page < pages) c.sender.sayRaw("<gray>Next page: <white>/warnhistory ${t.name} ${page + 1}")
        }
    }

    private fun usage(s: CommandSender) {
        for (l in listOf("<dark_gray><st>                                        ", "<red><b>WARN</b> <dark_gray>» <gray>Usage:",
                "<white>/warn <player> <gray>- pick a reason from a clickable list", "<white>/warn <player> <reason> [note] <gray>- warn directly",
                "<white>/warns <player><gray>, <white>/warnhistory <player><gray>, <white>/unwarn <player> <reason><gray>, <white>/clearwarns <player>",
                "<white>/warnreasons <gray>- list reason IDs", "<dark_gray><st>                                        ")) s.sayRaw(l)
    }

    private fun ago(t: Long) = fmtTime((System.currentTimeMillis() - t) / 1000.0)

    private fun staff(msg: String) {
        val c = mm(msg)
        for (o in Bukkit.getOnlinePlayers()) if (staffLevel(o) >= 1) o.sendMessage(c)
        Bukkit.getConsoleSender().sendMessage(c)
    }

    private fun showWarns(s: CommandSender, id: UUID, name: String) {
        val a = active(id)
        s.sayRaw("<red><b>WARNS</b> <dark_gray>» <white>$name")
        if (a.isEmpty()) s.sayRaw("<green>No active warns.")
        for ((r, ws) in a.groupBy { it.reason }) s.sayRaw(" <dark_gray>- <red>${reason(r)?.name ?: r} <white>x${ws.size} <dark_gray>(last ${ago(ws.last().time)} ago by ${ws.last().by})")
        s.sayRaw("<gray>Total active: <red>${a.size} <dark_gray>| <gray>Full log: <white>/warnhistory $name")
    }

    /** The clickable reason list (instead of DrillSim's chest menu: players here are on the game screen). */
    private fun menu(s: CommandSender, t: OfflinePlayer) {
        val name = t.name ?: "?"
        val a = active(t.uniqueId)
        s.sayRaw("<dark_gray><st>                                        ")
        s.sayRaw("<red><b>WARN</b> <dark_gray>» <white>$name <gray>(active warns: <red>${a.size}<gray>) <dark_gray>- click a reason")
        val lvl = staffLevel(s)
        for (r in REASONS) {
            val n = a.count { it.reason == r.id }
            val next = nextPunish(t.uniqueId, r)
            val locked = next.level > lvl
            val line = if (locked) " <dark_gray>🔒 <${r.color}>${r.name} <dark_gray>(#${n + 1} → ${next.label}, needs ${staffName(next.level)})"
            else " <click:run_command:'/warn $name ${r.id}'><hover:show_text:'<gray>${r.desc}<newline><white>Click to <red><b>WARN</b><newline><gray>Next: <white>${next.label}'>" +
                "<dark_gray>▸ <${r.color}>${r.name} <dark_gray>(#${n + 1} → <gray>${next.label}<dark_gray>)</hover></click>"
            s.sayRaw(line)
        }
        s.sayRaw("<dark_gray><st>                                        ")
    }

    fun warn(s: CommandSender, t: OfflinePlayer, r: Reason, note: String) {
        val p = nextPunish(t.uniqueId, r)
        val lvl = staffLevel(s)
        if (p.level > lvl) return s.say("<red>That warn would trigger a <white>${p.label}<red>, which only <white>${staffName(p.level)}<red>+ can issue. Pass it to a ${staffName(p.level)}.")
        val l = log(t.uniqueId)
        val n = l.count { it.reason == r.id && isActive(it) } + 1
        l += Warn(r.id, s.name, note, System.currentTimeMillis(), true, p.label)
        setLog(t.uniqueId, t.name, l)
        val name = t.name ?: "?"
        val notice = buildList {
            add("<gray><st>                                        ")
            add("<red><b>WARNING</b> <dark_gray>» <gray>You were warned for <white>${r.name}")
            add("<gray>${r.desc}")
            if (note.isNotBlank()) add("<gray>Note: <white>${ChatFilter.esc(note)}")
            add("<gray>This is warn <red>#$n <gray>for this rule. See <white>/mywarns")
            add(when (p) {
                Punish.Verbal -> "<yellow>This is your verbal warning. <gray>(No punishment)"
                Punish.Log -> "<gray>Please follow the rules. <dark_gray>(/rules)"
                Punish.Kick -> "<red>You have been kicked."
                is Punish.Mute -> if (p.d == null) "<red>You have been <dark_red><b>PERMANENTLY MUTED</b><red>." else "<red>You have been muted for <yellow>${fmtDur(p.d)}<red>."
                is Punish.Ban, Punish.IpBan -> "<red>You have been banned."
            })
            add("<gray>Appeal at <white>/discord")
            add("<gray><st>                                        ")
        }
        val online = t.player
        if (online != null && p !is Punish.Ban && p != Punish.IpBan && p != Punish.Kick) {
            notice.forEach { online.sendMessage(mm(it)) }
            online.playSound(online.location, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.6f)
        } else if (online == null && p !is Punish.Ban && p != Punish.IpBan) synchronized(data) {
            data.set("players.${t.uniqueId}.pending", data.getStringList("players.${t.uniqueId}.pending") + notice)
        }
        val reasonText = "${r.name}${if (note.isNotBlank()) " ($note)" else ""}"
        when (p) {
            is Punish.Mute -> Essentials.muteFor(t.uniqueId, p.d?.toMillis())
            Punish.Kick -> online?.kick(mm("<red>You have been kicked for <white>${r.name}<red>.<newline><gray>Appeal at /discord"))
            is Punish.Ban -> {
                t.ban<BanEntry<PlayerProfile>>(reasonText, p.d?.let { Instant.now().plus(it) }, s.name)
                online?.kick(mm("<red>You have been ${if (p.d == null) "<dark_red><b>PERMANENTLY BANNED</b>" else "banned for <yellow>${fmtDur(p.d)}"}<red>.<newline><gray>Reason: <white>$reasonText<newline><gray>Appeal at ${Discord.invite()}"))
            }
            Punish.IpBan -> {
                if (online != null) online.banIp(reasonText, null as Instant?, s.name, true)
                else t.ban<BanEntry<PlayerProfile>>(reasonText, null as Instant?, s.name)
            }
            else -> {}
        }
        save()
        staff("<dark_gray>[<red>WARN<dark_gray>] <white>${s.name} <gray>warned <white>$name <gray>for <${r.color}>${r.name} <dark_gray>(<red>#$n <dark_gray>→ <gray>${p.label}<dark_gray>)" +
            if (note.isNotBlank()) " <dark_gray>| <gray>${ChatFilter.esc(note)}" else "")
    }

    @EventHandler
    fun onJoin(e: PlayerJoinEvent) {
        val p = e.player
        val pending = synchronized(data) { data.getStringList("players.${p.uniqueId}.pending").also { if (it.isNotEmpty()) data.set("players.${p.uniqueId}.pending", null) } }
        if (pending.isEmpty()) return
        save()
        plugin.server.scheduler.runTaskLater(plugin, Runnable { if (p.isOnline) pending.forEach { p.sendMessage(mm(it)) } }, 60L)
    }
}
