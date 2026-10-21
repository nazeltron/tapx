package dev.tapx

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabExecutor
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class TopEntry(val uuid: UUID, val name: String, val best: Int, val prestiges: Int)

class TapXPlugin : JavaPlugin(), Listener, TabExecutor {
    private val gson: Gson = GsonBuilder().create()
    private val sessions = HashMap<UUID, Session>()
    private val top = ConcurrentHashMap<UUID, TopEntry>()
    private lateinit var playersDir: File
    // One writer thread keeps saves for the same player in order.
    private val writer: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "TapX-save").apply { isDaemon = true } }

    @Suppress("UnstableApiUsage")
    val version: String get() = pluginMeta.version

    override fun onEnable() {
        saveDefaultConfig()
        playersDir = File(dataFolder, "players").apply { mkdirs() }
        loadLeaderboard()
        server.pluginManager.registerEvents(this, this)
        getCommand("tapx")?.setExecutor(this)
        getCommand("tapx")?.tabCompleter = this
        server.onlinePlayers.forEach(::loadSession)
        // Game clock: 10 updates per second.
        server.scheduler.runTaskTimer(this, Runnable { sessions.values.toList().forEach { it.tick(0.1) } }, 2L, 2L)
        // Autosave everyone who is playing.
        val every = config.getLong("autosave-minutes", 5).coerceAtLeast(1) * 60 * 20
        server.scheduler.runTaskTimer(this, Runnable { sessions.values.filter { it.active }.forEach(::save) }, every, every)
        logger.info("TapX v$version enabled - /tapx to play")
    }

    override fun onDisable() {
        for (s in sessions.values) {
            if (s.active) s.player.closeInventory()
            val json = gson.toJson(s.d)
            writer.execute { writeNow(s.player.uniqueId, json) }
        }
        sessions.clear()
        writer.shutdown()
        writer.awaitTermination(10, TimeUnit.SECONDS)
    }

    // ---------------------------------------------------------- storage
    private fun file(uuid: UUID) = File(playersDir, "$uuid.json")

    private fun loadData(p: Player): PlayerData {
        val f = file(p.uniqueId)
        val data = if (f.exists()) {
            try { gson.fromJson(f.readText(), PlayerData::class.java) ?: PlayerData() } catch (e: Exception) {
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

    fun save(s: Session) {
        val json = gson.toJson(s.d)
        val uuid = s.player.uniqueId
        updateTop(uuid, s.d)
        writer.execute { writeNow(uuid, json) }
    }

    private fun writeNow(uuid: UUID, json: String) {
        val f = file(uuid)
        val tmp = File(playersDir, "$uuid.json.tmp")
        tmp.writeText(json)
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }

    // ---------------------------------------------------------- leaderboard
    private fun updateTop(uuid: UUID, d: PlayerData) {
        top[uuid] = TopEntry(uuid, d.name, d.stats.bestStage, d.stats.prestiges)
    }

    private fun loadLeaderboard() {
        playersDir.listFiles { f -> f.name.endsWith(".json") && !f.name.contains(".broken") }?.forEach { f ->
            try {
                val d = gson.fromJson(f.readText(), PlayerData::class.java) ?: return@forEach
                updateTop(UUID.fromString(f.nameWithoutExtension), d)
            } catch (_: Exception) { }
        }
    }

    fun leaderboard(): List<TopEntry> {
        sessions.values.forEach { updateTop(it.player.uniqueId, it.d) }
        return top.values.sortedWith(compareByDescending<TopEntry> { it.best }.thenByDescending { it.prestiges })
    }

    // ---------------------------------------------------------- events
    @EventHandler
    fun onJoin(e: PlayerJoinEvent) = loadSession(e.player)

    @EventHandler
    fun onQuit(e: PlayerQuitEvent) {
        val s = sessions.remove(e.player.uniqueId) ?: return
        if (s.active) s.pause() else save(s)
    }

    @EventHandler
    fun onClick(e: InventoryClickEvent) {
        val menu = e.view.topInventory.holder as? Menu ?: return
        e.isCancelled = true
        if (e.clickedInventory == e.view.topInventory) menu.click(e.rawSlot, e.click)
    }

    @EventHandler
    fun onDrag(e: InventoryDragEvent) {
        if (e.view.topInventory.holder is Menu) e.isCancelled = true
    }

    @EventHandler
    fun onClose(e: InventoryCloseEvent) {
        val menu = e.inventory.holder as? Menu ?: return
        if (e.reason == InventoryCloseEvent.Reason.OPEN_NEW) return
        val s = menu.s
        if (s.menu === menu && s.active) s.pause()
    }

    // ---------------------------------------------------------- commands
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val sub = args.getOrNull(0)?.lowercase()
        when (sub) {
            null, "play", "open" -> {
                val p = sender as? Player ?: return reply(sender, "<red>Only players can play TapX.")
                val s = sessions[p.uniqueId] ?: return true
                s.open(BattleMenu(s))
            }
            "top" -> {
                reply(sender, "<gold><b>TapX Top Players")
                leaderboard().take(10).forEachIndexed { i, e -> reply(sender, "<yellow>#${i + 1} <white>${e.name} <gray>— stage ${e.best}, ${e.prestiges} prestiges") }
            }
            "help" -> help(sender)
            "admin" -> admin(sender, args.drop(1))
            else -> help(sender)
        }
        return true
    }

    private fun help(sender: CommandSender) {
        reply(sender, "<gold><b>TapX</b> <gray>v$version — a Tap Titans-style idle game")
        reply(sender, "<yellow>/tapx <gray>— open the game")
        reply(sender, "<yellow>/tapx top <gray>— top players")
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
                if (amount == null || amount <= 0) { reply(sender, "<red>Usage: /tapx admin give <player> <gold|gems|relics> <amount>"); return }
                when (args.getOrNull(2)?.lowercase()) {
                    "gold" -> s.game.addGold(amount)
                    "gems" -> s.d.gems += amount.toLong()
                    "relics" -> s.d.relics += amount.toLong()
                    else -> { reply(sender, "<red>Currency must be gold, gems or relics."); return }
                }
                reply(sender, "<green>Gave ${fmt(amount)} ${args[2]} to ${target.name}.")
            }
            "stage" -> {
                val st = args.getOrNull(2)?.toIntOrNull()
                if (st == null || st < 1) { reply(sender, "<red>Usage: /tapx admin stage <player> <stage>"); return }
                s.d.stage = st
                s.d.wave = 0
                s.d.farming = false
                s.d.maxStage = maxOf(s.d.maxStage, st)
                s.d.stats.bestStage = maxOf(s.d.stats.bestStage, st)
                s.game.monster = null
                reply(sender, "<green>${target.name} is now on stage $st.")
            }
            "reset" -> {
                if (s.active) s.player.closeInventory()
                s.game.d = PlayerData().apply { name = target.name; gems = config.getLong("starting-gems", 100) }
                s.game.monster = null
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
        val opts = when (args.size) {
            1 -> listOf("play", "top", "help") + if (sender.hasPermission("tapx.admin")) listOf("admin") else emptyList()
            2 -> if (args[0].equals("admin", true) && sender.hasPermission("tapx.admin")) listOf("give", "stage", "reset") else emptyList()
            3 -> if (args[0].equals("admin", true) && sender.hasPermission("tapx.admin")) Bukkit.getOnlinePlayers().map { it.name } else emptyList()
            4 -> if (args[0].equals("admin", true) && args[1].equals("give", true)) listOf("gold", "gems", "relics") else emptyList()
            else -> emptyList()
        }
        return opts.filter { it.startsWith(args.last(), ignoreCase = true) }
    }
}
