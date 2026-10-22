package dev.tapx

import io.papermc.paper.event.player.AsyncChatEvent
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.attribute.Attribute
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.FoodLevelChangeEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import java.io.File
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * The everyday server commands (EssentialsX-style): game modes, fly, heal, teleports, spawn, warps,
 * homes, messages, moderation, vanish and more. Everything except the chat/info commands needs
 * tapx.cmd.<name> (ops by default). If EssentialsX is also installed, its commands are kept.
 */
object Essentials : Listener {
    private lateinit var plugin: TapXPlugin
    private lateinit var data: YamlConfiguration
    private lateinit var file: File

    private val back = ConcurrentHashMap<UUID, Location>()
    private val lastMsg = ConcurrentHashMap<UUID, UUID>()
    private val god = ConcurrentHashMap.newKeySet<UUID>()
    val vanished: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    val afk: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    private val mutedUntil = ConcurrentHashMap<UUID, Long>()
    @Volatile var chatMuted = false

    fun enable(p: TapXPlugin) {
        plugin = p
        file = File(p.dataFolder, "essentials.yml")
        data = YamlConfiguration.loadConfiguration(file)
        data.getConfigurationSection("mutes")?.let { m -> for (k in m.getKeys(false)) mutedUntil[UUID.fromString(k)] = m.getLong(k) }
        p.server.pluginManager.registerEvents(this, p)
        registerAll()
    }

    private fun save() = plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable { synchronized(data) { data.save(file) } })

    private fun locToYaml(path: String, l: Location) = synchronized(data) { data.set(path, Arcade.locToString(l)) }
    private fun yamlToLoc(path: String) = data.getString(path)?.let(Arcade::stringToLoc)

    fun nick(p: Player): String? = data.getString("nicks.${p.uniqueId}")

    /** Mute for [ms] milliseconds (null = forever); used by warnings. */
    fun muteFor(id: UUID, ms: Long?) {
        mutedUntil[id] = if (ms == null) Long.MAX_VALUE else System.currentTimeMillis() + ms
        saveMutes()
    }

    private fun players() = Bukkit.getOnlinePlayers().map { it.name }

    // ---------------------------------------------------------------- commands
    private fun registerAll() {
        fun gm(name: String, mode: GameMode, aliases: List<String> = emptyList()) =
            Cmds.register(name, aliases, "tapx.cmd.gamemode", "/$name [player]") { c ->
                val t = c.target(0, "tapx.cmd.gamemode.others") ?: return@register
                t.gameMode = mode
                t.say("<gray>Game mode: <white>${mode.name.lowercase()}")
                if (t != c.sender) c.sender.say("<gray>Set ${t.name} to <white>${mode.name.lowercase()}")
            }
        gm("gmc", GameMode.CREATIVE); gm("gms", GameMode.SURVIVAL); gm("gma", GameMode.ADVENTURE); gm("gmsp", GameMode.SPECTATOR)
        Cmds.register("gm", listOf("gamemode"), "tapx.cmd.gamemode", "/gm <c|s|a|sp> [player]",
            tab = { c -> if (c.args.size == 1) listOf("c", "s", "a", "sp") else players() }) { c ->
            val mode = when (c.arg(0)?.lowercase()) {
                "c", "1", "creative" -> GameMode.CREATIVE; "s", "0", "survival" -> GameMode.SURVIVAL
                "a", "2", "adventure" -> GameMode.ADVENTURE; "sp", "3", "spectator" -> GameMode.SPECTATOR
                else -> return@register c.sender.say("<red>Usage: /gm <c|s|a|sp> [player]")
            }
            val t = c.target(1, "tapx.cmd.gamemode.others") ?: return@register
            t.gameMode = mode; c.sender.say("<gray>${t.name}: <white>${mode.name.lowercase()}")
        }
        Cmds.register("fly", usage = "/fly [player]") { c ->
            val t = c.target(0, "tapx.cmd.fly.others") ?: return@register
            t.allowFlight = !t.allowFlight; if (!t.allowFlight) t.isFlying = false
            c.sender.say("<gray>Flight ${if (t.allowFlight) "<green>on" else "<red>off"}<gray> for ${t.name}.")
        }
        Cmds.register("heal", usage = "/heal [player]") { c ->
            val t = c.target(0, "tapx.cmd.heal.others") ?: return@register
            t.health = t.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
            t.foodLevel = 20; t.saturation = 20f; t.fireTicks = 0
            t.activePotionEffects.forEach { t.removePotionEffect(it.type) }
            c.sender.say("<green>Healed ${t.name}.")
        }
        Cmds.register("feed", usage = "/feed [player]") { c ->
            val t = c.target(0, "tapx.cmd.feed.others") ?: return@register
            t.foodLevel = 20; t.saturation = 20f; c.sender.say("<green>Fed ${t.name}.")
        }
        Cmds.register("god", usage = "/god [player]") { c ->
            val t = c.target(0, "tapx.cmd.god.others") ?: return@register
            val on = god.add(t.uniqueId) || run { god.remove(t.uniqueId); false }
            c.sender.say("<gray>God mode ${if (on) "<green>on" else "<red>off"}<gray> for ${t.name}.")
        }
        Cmds.register("speed", usage = "/speed <1-10> [player]", tab = { c -> if (c.args.size == 1) (1..10).map { "$it" } else players() }) { c ->
            val v = c.int(0)?.coerceIn(1, 10) ?: return@register c.sender.say("<red>Usage: /speed <1-10> [player]")
            val t = c.target(1, "tapx.cmd.speed.others") ?: return@register
            if (t.isFlying) t.flySpeed = v / 10f else t.walkSpeed = (0.2f * v / 2).coerceAtMost(1f)
            c.sender.say("<gray>${if (t.isFlying) "Fly" else "Walk"} speed <white>$v<gray> for ${t.name}.")
        }
        Cmds.register("tp", listOf("teleport", "tppos"), "tapx.cmd.tp", "/tp <player> [target] | /tp <x> <y> <z>") { c ->
            if (c.args.size >= 3 && c.num(0) != null) {
                val p = c.needPlayer() ?: return@register
                p.teleport(Location(p.world, c.num(0)!!, c.num(1) ?: return@register, c.num(2) ?: return@register, p.location.yaw, p.location.pitch))
                return@register
            }
            val a = c.arg(0)?.let(Bukkit::getPlayer) ?: return@register c.sender.say("<red>Usage: /tp <player> [target] or /tp <x> <y> <z>")
            if (c.arg(1) != null) {
                val b = Bukkit.getPlayer(c.arg(1)!!) ?: return@register c.sender.say("<red>${c.arg(1)} is not online.")
                a.teleport(b); c.sender.say("<gray>Teleported ${a.name} to ${b.name}.")
            } else { val p = c.needPlayer() ?: return@register; p.teleport(a); p.say("<gray>Teleported to ${a.name}.") }
        }
        Cmds.register("tphere", listOf("s"), "tapx.cmd.tphere", "/tphere <player>") { c ->
            val p = c.needPlayer() ?: return@register
            val t = c.arg(0)?.let(Bukkit::getPlayer) ?: return@register p.say("<red>Usage: /tphere <player>")
            t.teleport(p); p.say("<gray>Brought ${t.name} to you.")
        }
        Cmds.register("tpall", usage = "/tpall") { c ->
            val p = c.needPlayer() ?: return@register
            Bukkit.getOnlinePlayers().filter { it != p }.forEach { it.teleport(p) }; p.say("<gray>Brought everyone to you.")
        }
        Cmds.register("back", usage = "/back") { c ->
            val p = c.needPlayer() ?: return@register
            val l = back[p.uniqueId] ?: return@register p.say("<red>Nowhere to go back to.")
            p.teleport(l); p.say("<gray>Back to your last location.")
        }
        Cmds.register("setspawn", usage = "/setspawn") { c ->
            val p = c.needPlayer() ?: return@register
            locToYaml("spawn", p.location); p.world.spawnLocation = p.location; save(); p.say("<green>Spawn set.")
        }
        Cmds.register("spawn", usage = "/spawn [player]") { c ->
            val t = c.target(0, "tapx.cmd.spawn.others") ?: return@register
            t.teleport(yamlToLoc("spawn") ?: Bukkit.getWorlds()[0].spawnLocation); t.say("<gray>Teleported to spawn.")
        }
        Cmds.register("setwarp", usage = "/setwarp <name>") { c ->
            val p = c.needPlayer() ?: return@register
            val n = c.arg(0)?.lowercase() ?: return@register p.say("<red>Usage: /setwarp <name>")
            locToYaml("warps.$n", p.location); save(); p.say("<green>Warp <white>$n<green> set.")
        }
        Cmds.register("delwarp", usage = "/delwarp <name>", tab = { warps() }) { c ->
            val n = c.arg(0)?.lowercase() ?: return@register
            synchronized(data) { data.set("warps.$n", null) }; save(); c.sender.say("<green>Deleted warp $n.")
        }
        Cmds.register("warp", usage = "/warp <name> [player]", tab = { c -> if (c.args.size == 1) warps() else players() }) { c ->
            val n = c.arg(0)?.lowercase() ?: return@register c.sender.say("<gray>Warps: <white>${warps().joinToString().ifEmpty { "none" }}")
            val l = yamlToLoc("warps.$n") ?: return@register c.sender.say("<red>No warp called $n.")
            val t = c.target(1, "tapx.cmd.warp.others") ?: return@register
            t.teleport(l); t.say("<gray>Warped to <white>$n")
        }
        Cmds.register("warps", usage = "/warps") { c -> c.sender.say("<gray>Warps: <white>${warps().joinToString().ifEmpty { "none" }}") }
        Cmds.register("sethome", usage = "/sethome [name]", tab = { emptyList() }) { c ->
            val p = c.needPlayer() ?: return@register
            val n = c.arg(0)?.lowercase() ?: "home"
            locToYaml("homes.${p.uniqueId}.$n", p.location); save(); p.say("<green>Home <white>$n<green> set.")
        }
        Cmds.register("home", usage = "/home [name]", tab = { c -> homes(c.player) }) { c ->
            val p = c.needPlayer() ?: return@register
            val n = c.arg(0)?.lowercase() ?: "home"
            val l = yamlToLoc("homes.${p.uniqueId}.$n") ?: return@register p.say("<red>No home called $n. Homes: ${homes(p).joinToString().ifEmpty { "none" }}")
            p.teleport(l); p.say("<gray>Welcome home.")
        }
        Cmds.register("delhome", usage = "/delhome [name]", tab = { c -> homes(c.player) }) { c ->
            val p = c.needPlayer() ?: return@register
            synchronized(data) { data.set("homes.${p.uniqueId}.${c.arg(0)?.lowercase() ?: "home"}", null) }; save(); p.say("<green>Home deleted.")
        }
        Cmds.register("homes", usage = "/homes") { c -> c.sender.say("<gray>Homes: <white>${homes(c.player).joinToString().ifEmpty { "none" }}") }

        // chat & info (everyone)
        Cmds.register("msg", listOf("tell", "w", "whisper", "m", "pm", "dm"), perm = null, usage = "/msg <player> <message>") { c ->
            val t = c.arg(0)?.let(Bukkit::getPlayer)?.takeIf { it.uniqueId !in vanished || c.sender.hasPermission("tapx.cmd.vanish") }
                ?: return@register c.sender.say("<red>That player is not online.")
            if (c.args.size < 2) return@register c.sender.say("<red>Usage: /msg <player> <message>")
            whisper(c.sender, t, c.args.drop(1).joinToString(" "))
        }
        Cmds.register("r", listOf("reply"), perm = null, usage = "/r <message>", tab = { emptyList() }) { c ->
            val p = c.needPlayer() ?: return@register
            val t = lastMsg[p.uniqueId]?.let(Bukkit::getPlayer) ?: return@register p.say("<red>Nobody to reply to.")
            if (c.args.isEmpty()) return@register p.say("<red>Usage: /r <message>")
            whisper(p, t, c.args.joinToString(" "))
        }
        Cmds.register("ping", perm = null, usage = "/ping [player]") { c ->
            val t = c.arg(0)?.let(Bukkit::getPlayer) ?: c.player ?: return@register
            c.sender.say("<gray>${t.name}'s ping: <white>${t.ping}ms")
        }
        Cmds.register("list", listOf("online", "who"), perm = null, usage = "/list", tab = { emptyList() }) { c ->
            val visible = Bukkit.getOnlinePlayers().filter { it.uniqueId !in vanished || c.sender.hasPermission("tapx.cmd.vanish") }
            c.sender.say("<gray>Online (<white>${visible.size}<gray>): " + visible.joinToString("<gray>, ") { "${TabList.tag(it)} <white>${it.name}" })
        }
        Cmds.register("afk", perm = null, usage = "/afk", tab = { emptyList() }) { c ->
            val p = c.needPlayer() ?: return@register
            setAfk(p, p.uniqueId !in afk)
        }
        Cmds.register("rules", perm = null, usage = "/rules", tab = { emptyList() }) { c ->
            val rules = plugin.config.getStringList("rules").ifEmpty { listOf("Be kind to everyone.", "No cheating, macros or auto-clickers.", "No spam or advertising.", "Listen to staff.") }
            c.sender.say("<gold><b>Server rules")
            rules.forEachIndexed { i, r -> c.sender.sayRaw(" <yellow>${i + 1}. <white>$r") }
        }
        Cmds.register("store", listOf("shop", "buy", "donate"), perm = null, usage = "/store", tab = { emptyList() }) { c ->
            val p = c.player
            if (p != null) plugin.openStore(p)
            else c.sender.say("<aqua><b>STORE</b> <gray>» <white>${Cmds.storeUrl()}")
        }

        // moderation
        Cmds.register("broadcast", listOf("bc", "announce"), usage = "/broadcast <message>", tab = { emptyList() }) { c ->
            if (c.args.isEmpty()) return@register c.sender.say("<red>Usage: /broadcast <message>")
            Bukkit.broadcast(mm("\n<gradient:#ffcf3a:#ff5a8a><b>ANNOUNCEMENT</b></gradient> <dark_gray>»</dark_gray> <white>${c.args.joinToString(" ").replace("\\n", "<newline>")}\n"))
        }
        Cmds.register("clearchat", listOf("cc"), usage = "/clearchat", tab = { emptyList() }) { c ->
            repeat(100) { Bukkit.getOnlinePlayers().filter { !it.hasPermission("tapx.cmd.clearchat") }.forEach { p -> p.sendMessage(net.kyori.adventure.text.Component.empty()) } }
            Bukkit.broadcast(mm("$PREFIX<gray>Chat was cleared by <white>${c.sender.name}"))
        }
        Cmds.register("mutechat", listOf("mc"), usage = "/mutechat", tab = { emptyList() }) { c ->
            chatMuted = !chatMuted
            Bukkit.broadcast(mm("$PREFIX<gray>Chat is now ${if (chatMuted) "<red>muted" else "<green>open"}<gray>."))
        }
        Cmds.register("mute", usage = "/mute <player> [minutes]") { c ->
            val t = c.arg(0)?.let { Bukkit.getOfflinePlayerIfCached(it) } ?: return@register c.sender.say("<red>Unknown player.")
            val min = c.num(1)
            mutedUntil[t.uniqueId] = if (min != null) System.currentTimeMillis() + (min * 60_000).toLong() else Long.MAX_VALUE
            saveMutes(); c.sender.say("<gray>Muted ${t.name}${min?.let { " for ${it.toInt()} min" } ?: ""}.")
            t.player?.say("<red>You have been muted.")
        }
        Cmds.register("unmute", usage = "/unmute <player>") { c ->
            val t = c.arg(0)?.let { Bukkit.getOfflinePlayerIfCached(it) } ?: return@register c.sender.say("<red>Unknown player.")
            mutedUntil.remove(t.uniqueId); saveMutes(); c.sender.say("<gray>Unmuted ${t.name}."); t.player?.say("<green>You can talk again.")
        }
        Cmds.register("kick", usage = "/kick <player> [reason]") { c ->
            val t = c.arg(0)?.let(Bukkit::getPlayer) ?: return@register c.sender.say("<red>That player is not online.")
            val reason = c.args.drop(1).joinToString(" ").ifBlank { "Kicked by staff" }
            t.kick(mm("<red>$reason")); Bukkit.broadcast(mm("$PREFIX<gray>${t.name} was kicked: <white>$reason"), "tapx.cmd.kick")
        }
        Cmds.register("ban", usage = "/ban <player> [reason]") { c -> ban(c, null) }
        Cmds.register("tempban", usage = "/tempban <player> <time e.g. 2h, 3d> [reason]") { c ->
            val d = c.arg(1)?.let(::parseDuration) ?: return@register c.sender.say("<red>Usage: /tempban <player> <time e.g. 30m, 2h, 7d> [reason]")
            ban(c, d)
        }
        Cmds.register("unban", listOf("pardon"), usage = "/unban <player>", tab = { emptyList() }) { c ->
            val n = c.arg(0) ?: return@register c.sender.say("<red>Usage: /unban <player>")
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "minecraft:pardon $n"); c.sender.say("<green>Unbanned $n.")
        }
        Cmds.register("vanish", listOf("v"), usage = "/vanish") { c ->
            val p = c.needPlayer() ?: return@register
            if (vanished.add(p.uniqueId)) {
                Bukkit.getOnlinePlayers().filter { !it.hasPermission("tapx.cmd.vanish") }.forEach { it.hidePlayer(plugin, p) }
                p.say("<gray>You are now <white>vanished<gray>.")
            } else {
                vanished.remove(p.uniqueId)
                Bukkit.getOnlinePlayers().forEach { it.showPlayer(plugin, p) }
                p.say("<gray>You are <white>visible<gray> again.")
            }
            TabList.refreshAll()
        }
        Cmds.register("invsee", usage = "/invsee <player>") { c ->
            val p = c.needPlayer() ?: return@register
            val t = c.arg(0)?.let(Bukkit::getPlayer) ?: return@register p.say("<red>That player is not online.")
            p.openInventory(t.inventory)
        }
        Cmds.register("enderchest", listOf("ec", "echest"), usage = "/ec [player]") { c ->
            val p = c.needPlayer() ?: return@register
            val t = c.target(0, "tapx.cmd.enderchest.others") ?: return@register
            p.openInventory(t.enderChest)
        }
        Cmds.register("clearinventory", listOf("ci", "clear"), usage = "/ci [player]") { c ->
            val t = c.target(0, "tapx.cmd.clearinventory.others") ?: return@register
            t.inventory.clear(); c.sender.say("<gray>Cleared ${t.name}'s inventory.")
        }
        Cmds.register("hat", usage = "/hat", tab = { emptyList() }) { c ->
            val p = c.needPlayer() ?: return@register
            val hand = p.inventory.itemInMainHand
            if (hand.type == Material.AIR) return@register p.say("<red>Hold something first.")
            val old = p.inventory.helmet; p.inventory.helmet = hand; p.inventory.setItemInMainHand(old ?: ItemStack(Material.AIR))
            p.say("<gray>Nice hat!")
        }
        Cmds.register("repair", listOf("fix"), usage = "/repair [all]", tab = { listOf("all") }) { c ->
            val p = c.needPlayer() ?: return@register
            val items = if (c.arg(0) == "all") p.inventory.contents.filterNotNull() else listOf(p.inventory.itemInMainHand)
            var n = 0
            for (it in items) { val m = it.itemMeta as? Damageable ?: continue; if (m.hasDamage()) { m.damage = 0; it.itemMeta = m; n++ } }
            p.say("<green>Repaired $n item${if (n == 1) "" else "s"}.")
        }
        Cmds.register("workbench", listOf("wb", "craft"), usage = "/workbench", tab = { emptyList() }) { c -> c.needPlayer()?.openWorkbench(null, true) }
        Cmds.register("day", usage = "/day", tab = { emptyList() }) { c -> (c.player?.world ?: Bukkit.getWorlds()[0]).time = 1000; c.sender.say("<gray>Good morning!") }
        Cmds.register("night", usage = "/night", tab = { emptyList() }) { c -> (c.player?.world ?: Bukkit.getWorlds()[0]).time = 13000; c.sender.say("<gray>Good night!") }
        Cmds.register("sun", listOf("clearweather"), usage = "/sun", tab = { emptyList() }) { c -> (c.player?.world ?: Bukkit.getWorlds()[0]).apply { setStorm(false); isThundering = false }; c.sender.say("<gray>The sun is out.") }
        Cmds.register("rain", usage = "/rain", tab = { emptyList() }) { c -> (c.player?.world ?: Bukkit.getWorlds()[0]).setStorm(true); c.sender.say("<gray>Rain started.") }
        Cmds.register("seen", usage = "/seen <player>") { c ->
            val t = c.arg(0)?.let { Bukkit.getOfflinePlayerIfCached(it) } ?: return@register c.sender.say("<red>Unknown player.")
            if (t.isOnline && t.uniqueId !in vanished) c.sender.say("<white>${t.name} <green>is online now.")
            else c.sender.say("<white>${t.name} <gray>was last seen <white>${fmtTime((System.currentTimeMillis() - t.lastSeen) / 1000.0)}<gray> ago.")
        }
        Cmds.register("sudo", usage = "/sudo <player> <command or c:message>") { c ->
            val t = c.arg(0)?.let(Bukkit::getPlayer) ?: return@register c.sender.say("<red>That player is not online.")
            val rest = c.args.drop(1).joinToString(" ")
            if (rest.startsWith("c:")) t.chat(rest.removePrefix("c:")) else t.performCommand(rest.removePrefix("/"))
            c.sender.say("<gray>Done.")
        }
        Cmds.register("nick", listOf("nickname"), usage = "/nick <name|off> [player]") { c ->
            val n = c.arg(0) ?: return@register c.sender.say("<red>Usage: /nick <name|off> [player]")
            val t = c.target(1, "tapx.cmd.nick.others") ?: return@register
            synchronized(data) { data.set("nicks.${t.uniqueId}", if (n.equals("off", true)) null else n.take(16)) }
            save(); TabList.refresh(t); c.sender.say("<gray>Nickname ${if (n.equals("off", true)) "removed" else "set to <white>$n"}<gray>.")
        }
    }

    private fun warps() = data.getConfigurationSection("warps")?.getKeys(false)?.toList() ?: emptyList()
    private fun homes(p: Player?) = p?.let { data.getConfigurationSection("homes.${it.uniqueId}")?.getKeys(false)?.toList() } ?: emptyList()

    private fun saveMutes() {
        synchronized(data) { data.set("mutes", null); for ((k, v) in mutedUntil) data.set("mutes.$k", v) }
        save()
    }

    private fun whisper(from: org.bukkit.command.CommandSender, to: Player, text: String) {
        from.sayRaw("<gray>[<white>me <dark_gray>→ <white>${to.name}<gray>] <white>$text")
        to.sayRaw("<gray>[<white>${from.name} <dark_gray>→ <white>me<gray>] <white>$text")
        to.playSound(to.location, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.6f)
        (from as? Player)?.let { lastMsg[to.uniqueId] = it.uniqueId; lastMsg[it.uniqueId] = to.uniqueId }
    }

    private fun parseDuration(s: String): Duration? {
        val n = s.dropLast(1).toLongOrNull() ?: return null
        return when (s.last().lowercaseChar()) { 's' -> Duration.ofSeconds(n); 'm' -> Duration.ofMinutes(n); 'h' -> Duration.ofHours(n); 'd' -> Duration.ofDays(n); 'w' -> Duration.ofDays(7 * n); else -> null }
    }

    private fun ban(c: Cmds.Ctx, d: Duration?) {
        val name = c.arg(0) ?: return c.sender.say("<red>Usage: /ban <player> [reason]")
        val reason = c.args.drop(if (d == null) 1 else 2).joinToString(" ").ifBlank { "Banned by staff" }
        val t = Bukkit.getOfflinePlayer(name)
        t.ban<org.bukkit.BanEntry<com.destroystokyo.paper.profile.PlayerProfile>>(reason, d?.let { java.time.Instant.now().plus(it) }, c.sender.name)
        t.player?.kick(mm("<red>You are banned: $reason" + (d?.let { "<newline><gray>for ${fmtTime(it.seconds.toDouble())}" } ?: "")))
        Bukkit.broadcast(mm("$PREFIX<gray>${t.name ?: name} was banned${d?.let { " for ${fmtTime(it.seconds.toDouble())}" } ?: ""}: <white>$reason"), "tapx.cmd.ban")
        c.sender.say("<green>Banned ${t.name ?: name}.")
    }

    fun setAfk(p: Player, on: Boolean) {
        if (on == (p.uniqueId in afk)) return
        if (on) afk += p.uniqueId else afk -= p.uniqueId
        Bukkit.broadcast(mm("<gray>* <white>${p.name} <gray>is ${if (on) "now AFK" else "no longer AFK"}"))
        TabList.refresh(p)
    }

    // ---------------------------------------------------------------- listeners
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onChat(e: AsyncChatEvent) {
        val p = e.player
        val until = mutedUntil[p.uniqueId]
        if (until != null) {
            if (until > System.currentTimeMillis()) { e.isCancelled = true; p.say("<red>You are muted."); return }
            mutedUntil.remove(p.uniqueId)
        }
        if (chatMuted && !p.hasPermission("tapx.cmd.mutechat")) { e.isCancelled = true; p.say("<red>Chat is muted right now."); return }
        if (p.uniqueId in afk) plugin.server.scheduler.runTask(plugin, Runnable { setAfk(p, false) })
    }

    @EventHandler
    fun onDamage(e: EntityDamageEvent) { if (e.entity.uniqueId in god) e.isCancelled = true }

    @EventHandler
    fun onHunger(e: FoodLevelChangeEvent) { if (e.entity.uniqueId in god) e.isCancelled = true }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onTeleport(e: PlayerTeleportEvent) {
        if (e.from.world != plugin.arcade.world) back[e.player.uniqueId] = e.from
    }

    @EventHandler
    fun onDeath(e: PlayerDeathEvent) { back[e.entity.uniqueId] = e.entity.location }

    @EventHandler
    fun onJoin(e: PlayerJoinEvent) {
        val p = e.player
        if (!p.hasPermission("tapx.cmd.vanish")) vanished.mapNotNull(Bukkit::getPlayer).forEach { p.hidePlayer(plugin, it) }
        if (!p.hasPlayedBefore()) yamlToLoc("spawn")?.let { p.teleport(it) }
    }

    @EventHandler
    fun onQuit(e: PlayerQuitEvent) { afk -= e.player.uniqueId; lastMsg.remove(e.player.uniqueId) }
}
