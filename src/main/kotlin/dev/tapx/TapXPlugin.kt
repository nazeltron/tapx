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
import org.bukkit.configuration.file.YamlConfiguration
import java.time.LocalDateTime
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
    /** Gems for players who weren't online and have no save yet (e.g. bought on the web store first). */
    private val pendingGems = ConcurrentHashMap<String, Long>()
    private lateinit var pendingFile: File
    lateinit var arcade: Arcade
        private set
    lateinit var pack: ScreenPack
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
        pendingFile = File(dataFolder, "pending-gems.yml")
        YamlConfiguration.loadConfiguration(pendingFile).let { y -> y.getKeys(false).forEach { pendingGems[it] = y.getLong(it) } }
        Palette.toMap(0) // build the colour table now rather than on the first frame
        val n = AtomicInteger()
        gameThreads = Executors.newScheduledThreadPool(config.getInt("render-threads", 2).coerceIn(1, 8)) {
            Thread(it, "TapX-game-${n.incrementAndGet()}").apply { isDaemon = true }
        }
        pack = ScreenPack(this).also { it.enable() }
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
        if (::pack.isInitialized) pack.disable()
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
        return (data ?: PlayerData().apply { gems = config.getLong("starting-gems", 25) }).also { it.name = p.name }
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
        deliverPendingGems(p, s)
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
                reply(sender, "<gold>TapX</gold> <gray>- move your <white>mouse</white> to aim, <white>left-click</white> to attack and press buttons, <white>shift</white> to go back.")
                arcade.enter(p, s)
            }
            "leave", "exit" -> {
                val p = sender as? Player ?: return true
                if (!p.hasPermission("tapx.admin")) return reply(sender, "<red>Only operators can leave the game.")
                arcade.leave(p)
            }
            "top" -> {
                reply(sender, "<gold><b>TapX Top Players")
                leaderboard().take(10).forEachIndexed { i, e -> reply(sender, "<yellow>#${i + 1} <white>${e.name} <gray>- stage ${e.best}, ${e.prestiges} prestiges") }
            }
            "admin" -> admin(sender, args.drop(1))
            "gems" -> gems(sender, args.drop(1))
            else -> help(sender)
        }
        return true
    }

    private fun help(sender: CommandSender) {
        reply(sender, "<gold><b>TapX</b> <gray>v$version - a Tap Titans-style idle game")
        reply(sender, "<yellow>/tapx <gray>- open the game screen")
        if (sender.hasPermission("tapx.admin")) reply(sender, "<yellow>/tapx leave <gray>- leave the game screen (ops only)")
        reply(sender, "<yellow>/tapx top <gray>- top players")
        if (sender.hasPermission("tapx.admin")) {
            reply(sender, "<yellow>/tapx admin give <player> <gold|gems|relics> <amount>")
            reply(sender, "<yellow>/tapx admin stage <player> <stage>")
            reply(sender, "<yellow>/tapx admin reset <player>")
            reply(sender, "<yellow>/tapx gems <give|take|set|check> <player> [amount] <gray>- works offline too (for store purchases)")
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
                if (what == "gems") { gems(sender, listOf("give", target.name, amount.toLong().toString())); return }
                s.post { if (what == "gold") s.game.addGold(amount) else s.d.relics += amount.toLong() }
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

    // ---------------------------------------------------------- gems (paid currency)
    /**
     * /tapx gems <give|take|set|check> <player> [amount]
     * Meant for the web store (e.g. PayNow runs "tapx gems give {username} 500" from the console).
     * Works for online players, offline players with a save, and players who never joined yet (queued).
     */
    private fun gems(sender: CommandSender, args: List<String>) {
        if (!sender.hasPermission("tapx.admin")) { reply(sender, "<red>You don't have permission."); return }
        val action = args.getOrNull(0)?.lowercase()
        val name = args.getOrNull(1)
        val amount = args.getOrNull(2)?.toLongOrNull()
        if (action !in setOf("give", "take", "set", "check") || name == null || (action != "check" && (amount == null || amount < 0))) {
            reply(sender, "<red>Usage: /tapx gems <give|take|set|check> <player> [amount]"); return
        }
        fun apply(d: PlayerData): Long {
            when (action) {
                "give" -> { d.gems += amount!!; d.stats.gemsBought += amount }
                "take" -> d.gems = maxOf(0, d.gems - amount!!)
                "set" -> d.gems = amount!!
            }
            return d.gems
        }
        if (action != "check") logGems("${sender.name} $action $name ${amount ?: ""}")
        // 1) online
        val online = Bukkit.getPlayerExact(name)
        val s = online?.let { sessions[it.uniqueId] }
        if (online != null && s != null) {
            s.post {
                val total = apply(s.d)
                if (action == "give") s.screen.gemsReceived(amount!!)
                server.scheduler.runTask(this, Runnable {
                    reply(sender, "<green>${online.name} now has ${fmt(total)} gems.")
                    if (action == "give") reply(online, "<aqua>You received <b>${fmt(amount!!)} gems</b>! Thank you for supporting the server.")
                })
            }
            save(s)
            return
        }
        // 2) offline with a save file
        val uuid = Bukkit.getOfflinePlayerIfCached(name)?.uniqueId ?: findUuidByName(name)
        val f = uuid?.let(::file)
        if (f != null && f.exists()) {
            writer.execute {
                val d = gson.fromJson(f.readText(), PlayerData::class.java)
                val total = apply(d)
                writeNow(uuid, gson.toJson(d))
                server.scheduler.runTask(this, Runnable { reply(sender, "<green>$name (offline) now has ${fmt(total)} gems.") })
            }
            return
        }
        // 3) never joined: queue gifts for their first join
        when (action) {
            "give" -> {
                pendingGems.merge(name.lowercase(), amount!!, Long::plus)
                savePending()
                reply(sender, "<yellow>$name hasn't joined yet. ${fmt(amount)} gems will be delivered on their first join.")
            }
            "check" -> reply(sender, "<gray>$name has no TapX save yet${pendingGems[name.lowercase()]?.let { " (${fmt(it)} gems waiting)" } ?: ""}.")
            else -> reply(sender, "<red>$name has no TapX save yet.")
        }
    }

    private fun findUuidByName(name: String): UUID? =
        top.values.firstOrNull { it.name.equals(name, ignoreCase = true) }?.uuid

    private fun deliverPendingGems(p: Player, s: Session) {
        val n = pendingGems.remove(p.name.lowercase()) ?: return
        savePending()
        s.d.gems += n
        s.d.stats.gemsBought += n
        save(s)
        reply(p, "<aqua>You received <b>${fmt(n)} gems</b>! Thank you for supporting the server.")
    }

    private fun savePending() {
        val y = YamlConfiguration()
        pendingGems.forEach { (k, v) -> y.set(k, v) }
        writer.execute { y.save(pendingFile) }
    }

    private fun logGems(line: String) {
        val f = File(dataFolder, "gems.log")
        writer.execute { f.appendText("${LocalDateTime.now()} $line\n") }
    }

    private fun reply(sender: CommandSender, text: String): Boolean {
        sender.sendMessage(mm(text))
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        val admin = sender.hasPermission("tapx.admin") && args[0].equals("admin", true)
        val gemsCmd = sender.hasPermission("tapx.admin") && args[0].equals("gems", true)
        val opts = when (args.size) {
            1 -> listOf("play", "top", "help") + if (sender.hasPermission("tapx.admin")) listOf("leave", "admin", "gems") else emptyList()
            2 -> when {
                admin -> listOf("give", "stage", "reset")
                gemsCmd -> listOf("give", "take", "set", "check")
                else -> emptyList()
            }
            3 -> if (admin || gemsCmd) Bukkit.getOnlinePlayers().map { it.name } else emptyList()
            4 -> if (admin && args[1].equals("give", true)) listOf("gold", "gems", "relics") else emptyList()
            else -> emptyList()
        }
        return opts.filter { it.startsWith(args.last(), ignoreCase = true) }
    }
}
