package dev.tapx

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dev.tapx.screen.Palette
import dev.tapx.screen.TopEntry
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabExecutor
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class TapXPlugin : JavaPlugin(), Listener, TabExecutor {
    private val gson: Gson = GsonBuilder().create()
    private val sessions = ConcurrentHashMap<UUID, Session>()
    private val top = ConcurrentHashMap<UUID, TopEntry>()
    private lateinit var playersDir: File
    lateinit var arcade: Arcade
        private set

    // One writer thread keeps saves for the same player in order.
    private val writer: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "TapX-save").apply { isDaemon = true } }

    /** Game loops (drawing the screens) run here, off the main server thread. */
    lateinit var gameThreads: ScheduledExecutorService
        private set

    @Suppress("UnstableApiUsage")
    val version: String get() = pluginMeta.version

    override fun onEnable() {
        saveDefaultConfig()
        playersDir = File(dataFolder, "players").apply { mkdirs() }
        Palette.toMap(0) // build the colour table now rather than on the first frame
        val n = AtomicInteger()
        gameThreads = Executors.newScheduledThreadPool(config.getInt("render-threads", 2).coerceIn(1, 8)) {
            Thread(it, "TapX-game-${n.incrementAndGet()}").apply { isDaemon = true }
        }
        arcade = Arcade(this)
        arcade.load()
        loadLeaderboard()
        server.pluginManager.registerEvents(this, this)
        server.pluginManager.registerEvents(arcade, this)
        getCommand("tapx")?.setExecutor(this)
        getCommand("tapx")?.tabCompleter = this
        server.onlinePlayers.forEach(::loadSession)
        val every = config.getLong("autosave-minutes", 5).coerceAtLeast(1) * 60 * 20
        server.scheduler.runTaskTimer(this, Runnable { sessions.values.forEach(::save) }, every, every)
        logger.info("TapX v$version enabled - /tapx to play")
    }

    override fun onDisable() {
        if (::arcade.isInitialized) arcade.leaveAll()
        for (s in sessions.values) {
            s.stop()
            val json = gson.toJson(s.d)
            val id = s.player.uniqueId
            writer.execute { writeNow(id, json) }
        }
        sessions.clear()
        if (::gameThreads.isInitialized) gameThreads.shutdownNow()
        writer.shutdown()
        writer.awaitTermination(10, TimeUnit.SECONDS)
    }

    fun session(p: Player): Session? = sessions[p.uniqueId]

    // ---------------------------------------------------------- storage
    private fun file(uuid: UUID) = File(playersDir, "$uuid.json")

    private fun loadData(p: Player): PlayerData {
        val f = file(p.uniqueId)
        val data = if (f.exists()) {
            try { gson.fromJson(f.readText(), PlayerData::class.java) } catch (e: Exception) {
                logger.warning("Could not read ${f.name}, keeping a backup and starting fresh: ${e.message}")
                f.copyTo(File(playersDir, "${p.uniqueId}.broken-${System.currentTimeMillis()}.json"), overwrite = true)
                null
            }
        } else null
        return (data ?: PlayerData().apply { gems = config.getLong("starting-gems", 100) }).also { it.name = p.name }
    }

    private fun loadSession(p: Player) {
        sessions[p.uniqueId] = Session(this, p, loadData(p))
    }

    /** Save a player's progress (serialised on the game thread if they're playing). */
    fun save(s: Session) {
        val id = s.player.uniqueId
        s.post {
            val json = gson.toJson(s.d)
            top[id] = TopEntry(id, s.d.name, s.d.stats.bestStage, s.d.stats.prestiges)
            writer.execute { writeNow(id, json) }
        }
    }

    private fun writeNow(uuid: UUID, json: String) {
        val f = file(uuid)
        val tmp = File(playersDir, "$uuid.json.tmp")
        tmp.writeText(json)
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }

    // ---------------------------------------------------------- leaderboard
    private fun loadLeaderboard() {
        playersDir.listFiles { f -> f.name.endsWith(".json") && !f.name.contains(".broken") }?.forEach { f ->
            try {
                val d = gson.fromJson(f.readText(), PlayerData::class.java) ?: return@forEach
                val id = UUID.fromString(f.nameWithoutExtension)
                top[id] = TopEntry(id, d.name, d.stats.bestStage, d.stats.prestiges)
            } catch (_: Exception) { }
        }
    }

    fun leaderboard(): List<TopEntry> {
        for (s in sessions.values) top[s.player.uniqueId] = TopEntry(s.player.uniqueId, s.player.name, s.d.stats.bestStage, s.d.stats.prestiges)
        return top.values.sortedWith(compareByDescending<TopEntry> { it.best }.thenByDescending { it.prestiges })
    }

    // ---------------------------------------------------------- events
    @EventHandler
    fun onJoin(e: PlayerJoinEvent) {
        val p = e.player
        loadSession(p)
        val s = sessions[p.uniqueId] ?: return
        if (autoStart(p)) {
            // give the client a moment to finish loading the world, then straight into the game
            server.scheduler.runTaskLater(this, Runnable { if (p.isOnline) arcade.enter(p, s) }, 20L)
        } else arcade.recover(p, s)
    }

    private fun autoStart(p: Player) = config.getBoolean("auto-start", true) && !p.hasPermission("tapx.bypass")

    @EventHandler
    fun onQuit(e: PlayerQuitEvent) {
        if (arcade.isPlaying(e.player)) arcade.leave(e.player)
        val s = sessions.remove(e.player.uniqueId) ?: return
        save(s)
    }

    // ---------------------------------------------------------- commands
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        when (args.getOrNull(0)?.lowercase()) {
            null, "play", "open" -> {
                val p = sender as? Player ?: return reply(sender, "<red>Only players can play TapX.")
                val s = sessions[p.uniqueId] ?: return true
                if (arcade.isPlaying(p)) return reply(sender, "<gray>You're already playing! Sneak (shift) to leave.")
                if (p.isDead) return true
                reply(sender, "<gold>TapX</gold> <gray>- <white>left-click</white> to attack, <white>WASD</white> to move, <white>space</white> to select, <white>shift</white> to go back.")
                arcade.enter(p, s)
            }
            "leave", "exit" -> {
                val p = sender as? Player ?: return true
                if (!config.getBoolean("allow-exit", false) && !p.hasPermission("tapx.admin")) return reply(sender, "<red>You can't leave the game on this server.")
                arcade.leave(p)
            }
            "top" -> {
                reply(sender, "<gold><b>TapX Top Players")
                leaderboard().take(10).forEachIndexed { i, e -> reply(sender, "<yellow>#${i + 1} <white>${e.name} <gray>- stage ${e.best}, ${e.prestiges} prestiges") }
            }
            "admin" -> admin(sender, args.drop(1))
            else -> help(sender)
        }
        return true
    }

    private fun help(sender: CommandSender) {
        reply(sender, "<gold><b>TapX</b> <gray>v$version - a Tap Titans-style idle game")
        reply(sender, "<yellow>/tapx <gray>- open the game screen")
        reply(sender, "<yellow>/tapx leave <gray>- leave the game screen (if allowed)")
        reply(sender, "<yellow>/tapx top <gray>- top players")
        if (sender.hasPermission("tapx.admin")) {
            reply(sender, "<yellow>/tapx admin give <player> <gold|gems|relics> <amount>")
            reply(sender, "<yellow>/tapx admin stage <player> <stage>")
            reply(sender, "<yellow>/tapx admin reset <player>")
        }
    }

    private fun admin(sender: CommandSender, args: List<String>) {
        if (!sender.hasPermission("tapx.admin")) { reply(sender, "<red>You don't have permission."); return }
        val target = args.getOrNull(1)?.let { Bukkit.getPlayerExact(it) }
        val s = target?.let { sessions[it.uniqueId] }
        if (s == null) { reply(sender, "<red>That player is not online."); return }
        when (args.getOrNull(0)?.lowercase()) {
            "give" -> {
                val amount = args.getOrNull(3)?.toDoubleOrNull()
                val what = args.getOrNull(2)?.lowercase()
                if (amount == null || amount <= 0 || what !in setOf("gold", "gems", "relics")) {
                    reply(sender, "<red>Usage: /tapx admin give <player> <gold|gems|relics> <amount>"); return
                }
                s.post {
                    when (what) {
                        "gold" -> s.game.addGold(amount)
                        "gems" -> s.d.gems += amount.toLong()
                        else -> s.d.relics += amount.toLong()
                    }
                }
                reply(sender, "<green>Gave ${fmt(amount)} $what to ${target.name}.")
            }
            "stage" -> {
                val st = args.getOrNull(2)?.toIntOrNull()
                if (st == null || st < 1) { reply(sender, "<red>Usage: /tapx admin stage <player> <stage>"); return }
                s.post {
                    s.d.stage = st; s.d.wave = 0; s.d.farming = false
                    s.d.maxStage = maxOf(s.d.maxStage, st)
                    s.d.stats.bestStage = maxOf(s.d.stats.bestStage, st)
                    s.game.monster = null
                }
                reply(sender, "<green>${target.name} is now on stage $st.")
            }
            "reset" -> {
                s.post { s.resetProgress() }
                save(s)
                reply(sender, "<green>Reset ${target.name}'s TapX progress.")
            }
            else -> help(sender)
        }
    }

    private fun reply(sender: CommandSender, text: String): Boolean {
        sender.sendMessage(mm(text))
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        val admin = sender.hasPermission("tapx.admin") && args[0].equals("admin", true)
        val opts = when (args.size) {
            1 -> listOf("play", "leave", "top", "help") + if (sender.hasPermission("tapx.admin")) listOf("admin") else emptyList()
            2 -> if (admin) listOf("give", "stage", "reset") else emptyList()
            3 -> if (admin) Bukkit.getOnlinePlayers().map { it.name } else emptyList()
            4 -> if (admin && args[1].equals("give", true)) listOf("gold", "gems", "relics") else emptyList()
            else -> emptyList()
        }
        return opts.filter { it.startsWith(args.last(), ignoreCase = true) }
    }
}
