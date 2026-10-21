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

/** Coloured rank tag for chat/tab, e.g. "VIP" in bold orange. */
fun rankTag(rank: String?): String {
    val r = rankDef(rank) ?: return ""
    return if (r.color == "rainbow") "<b><rainbow>${r.name}</rainbow></b>" else "<b><${r.color}>${r.name}</${r.color}></b>"
}

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
        dev.tapx.screen.Res.k = config.getInt("screen-resolution", 2).coerceIn(1, 2)
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
        server.pluginManager.registerEvents(pack, this)
        getCommand("tapx")?.setExecutor(this)
        getCommand("tapx")?.tabCompleter = this
        Cmds.plugin = this
        ServerEvents.enable(this)
        FlashSale.load(this)
        Payments.enable(this)
        Promos.enable(this)
        Essentials.enable(this)
        TabList.enable(this)
        Discord.enable(this)
        Cmds.register("rank", listOf("setrank", "ranks"), "tapx.admin", "/rank <set|add|remove|list> <player> [rank]", tab = { c -> rankArgs(c.args) }) { c -> rankCommand(c.sender, c.args) }
        Cmds.register("gamepass", listOf("gamepasses", "gp", "pass"), "tapx.admin", "/gamepass <add|remove|set|list> <player> [pass]", tab = { c -> passArgs(c.args) }) { c -> passCommand(c.sender, c.args) }
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
            "reset", "resetall", "set", "stats", "rank", "pass", "gamepass", "promo", "payment", "flashsale", "piggybank" -> adminTools(sender, args.toList())
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
            reply(sender, "<yellow>/tapx reset <player> <gray>/ <yellow>/tapx resetall confirm <gray>- wipe progress, keep gems and everything bought with gems")
            reply(sender, "<yellow>/tapx set <player> <stat> <value> <gray>/ <yellow>/tapx stats <player>")
            reply(sender, "<yellow>/tapx rank <set|remove> <player> [rank] <gray>/ <yellow>/tapx pass <add|remove> <player> <pass>")
            reply(sender, "<yellow>/tapx promo <create|delete|list> <gray>/ <yellow>/tapx payment <player> <usd> [package] <gray>/ <yellow>/tapx flashsale <percent> <minutes>|stop")
            reply(sender, "<yellow>/event <gray>- server boosts and giveaways")
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

    // ---------------------------------------------------------- editing any player
    /**
     * Change a player's save wherever it is: online (on their game thread), offline (their file), and
     * [then] gets a result line on the main thread. Returns false if there's no save at all.
     */
    fun editPlayer(name: String, then: (String) -> Unit = {}, edit: (PlayerData, Session?) -> String): Boolean {
        val online = Bukkit.getPlayerExact(name)
        val s = online?.let { sessions[it.uniqueId] }
        if (s != null) {
            s.post { val r = edit(s.d, s); save(s); server.scheduler.runTask(this, Runnable { then(r) }) }
            return true
        }
        val uuid = Bukkit.getOfflinePlayerIfCached(name)?.uniqueId ?: findUuidByName(name) ?: return false
        val f = file(uuid)
        if (!f.exists()) return false
        writer.execute {
            val d = gson.fromJson(f.readText(), PlayerData::class.java)
            val r = edit(d, null)
            writeNow(uuid, gson.toJson(d))
            server.scheduler.runTask(this, Runnable { then(r) })
        }
        return true
    }

    /** Give gems (online, offline, or queued for a first join). [paid] counts it as bought (store). */
    fun giveGems(name: String, amount: Long, reason: String, paid: Boolean) {
        logGems("$reason give $name $amount")
        val found = editPlayer(name, { }) { d, s ->
            d.gems += amount
            if (paid) d.stats.gemsBought += amount
            s?.screen?.gemsReceived(amount)
            ""
        }
        if (!found) { pendingGems.merge(name.lowercase(), amount, Long::plus); savePending() }
    }

    /** The web store as a native dialog with an "Open Store" button (plus a chat link). Any thread. */
    @Suppress("UnstableApiUsage")
    fun openStore(p: Player) = server.scheduler.runTask(this, Runnable {
        val url = Cmds.storeUrl()
        p.sayRaw("$PREFIX<gold><b>STORE</b> <gray>» <click:open_url:'$url'><aqua><u>$url</u></aqua></click> <gray>(gems, ranks and gamepasses)")
        runCatching {
            val dialog = io.papermc.paper.dialog.Dialog.create { f ->
                f.empty()
                    .base(io.papermc.paper.registry.data.dialog.DialogBase.builder(mm("<gold><b>TAPX STORE"))
                        .body(listOf(
                            io.papermc.paper.registry.data.dialog.body.DialogBody.plainMessage(mm("<white>Get <aqua>gems</aqua>, <gold>ranks</gold> and <green>gamepasses</green> and support the server!")),
                            io.papermc.paper.registry.data.dialog.body.DialogBody.plainMessage(mm("<gray>Purchases arrive in the game automatically.")),
                        )).build())
                    .type(io.papermc.paper.registry.data.dialog.type.DialogType.multiAction(listOf(
                        io.papermc.paper.registry.data.dialog.ActionButton.create(mm("<green><b>Open Store"), mm("<gray>$url"), 200,
                            io.papermc.paper.registry.data.dialog.action.DialogAction.staticAction(net.kyori.adventure.text.event.ClickEvent.openUrl(url))),
                    )).build())
            }
            p.showDialog(dialog)
        }.onFailure { logger.warning("Couldn't show the store dialog: $it") }
    })

    /** A store rank changed: LuckPerms group (optional) and the tab list. Any thread. */
    fun rankChanged(p: Player, rank: String?, bought: Boolean = true) = server.scheduler.runTask(this, Runnable {
        if (config.getBoolean("ranks.luckperms", true) && server.pluginManager.isPluginEnabled("LuckPerms")) {
            for (r in RANKS) server.dispatchCommand(server.consoleSender, "lp user ${p.name} parent remove ${r.key}")
            if (rank != null) server.dispatchCommand(server.consoleSender, "lp user ${p.name} parent add $rank")
        }
        if (rank != null && bought) Bukkit.broadcast(mm("$PREFIX<white>${p.name} <gray>just bought the ${rankTag(rank)} <gray>rank!"))
        TabList.refresh(p)
    })

    private fun adminTools(sender: CommandSender, args: List<String>) {
        if (!sender.hasPermission("tapx.admin")) { reply(sender, "<red>You don't have permission."); return }
        val sub = args[0].lowercase()
        val who = args.getOrNull(1)
        fun done(msg: String) = sender.say(msg)
        fun noSave(n: String) = sender.say("<red>$n has no TapX save.")
        when (sub) {
            "reset" -> {
                who ?: return sender.say("<red>Usage: /tapx reset <player>")
                if (!editPlayer(who, ::done) { d, s ->
                        val fresh = d.resetKeepingPaid()
                        if (s != null) { s.game.d = fresh; s.game.monster = null } else copyInto(fresh, d)
                        "<green>Reset $who (kept gems and gem purchases)."
                    }) noSave(who)
            }
            "resetall" -> {
                if (who != "confirm") return sender.say("<red>This wipes EVERY player's progress (gems, gamepasses, ranks and skins are kept). Type <white>/tapx resetall confirm")
                for (s in sessions.values) s.post { s.game.d = s.d.resetKeepingPaid(); s.game.monster = null; save(s) }
                val online = sessions.keys.toSet()
                writer.execute {
                    var n = 0
                    playersDir.listFiles { f -> f.name.endsWith(".json") && !f.name.contains(".broken") }?.forEach { f ->
                        val id = runCatching { UUID.fromString(f.nameWithoutExtension) }.getOrNull() ?: return@forEach
                        if (id in online) return@forEach
                        val d = runCatching { gson.fromJson(f.readText(), PlayerData::class.java) }.getOrNull() ?: return@forEach
                        writeNow(id, gson.toJson(d.resetKeepingPaid())); n++
                    }
                    top.clear()
                    server.scheduler.runTask(this, Runnable {
                        sender.say("<green>Reset ${sessions.size} online and $n offline players. Gems and gem purchases were kept.")
                        Bukkit.broadcast(mm("$PREFIX<yellow><b>NEW SEASON!</b> <white>Everyone's progress was reset. Your gems, ranks and gamepasses are safe. Good luck!"))
                    })
                }
            }
            "set" -> {
                val stat = args.getOrNull(2)?.lowercase(); val value = args.getOrNull(3)
                if (who == null || stat == null || value == null) return sender.say("<red>Usage: /tapx set <player> <stat> <value>  <gray>(stats: ${STAT_NAMES.joinToString()})")
                if (!editPlayer(who, ::done) { d, s -> setStat(d, stat, value).also { if (s != null) s.game.monster = null } }) noSave(who)
            }
            "stats" -> {
                who ?: return sender.say("<red>Usage: /tapx stats <player>")
                if (!editPlayer(who, ::done) { d, _ ->
                        "<gold>$who: <gray>stage <white>${d.stage}<gray> (best ${d.stats.bestStage}), gold <white>${fmt(d.gold)}<gray>, gems <white>${d.gems}<gray>, relics <white>${d.relics}" +
                            "<gray>, tap lv <white>${d.tapLevel}<gray>, prestiges <white>${d.stats.prestiges}<gray>, rank <white>${d.rank ?: "none"}<gray>, passes <white>${d.owned.joinToString().ifEmpty { "none" }}" +
                            "<gray>, kills <white>${d.stats.kills}<gray>, taps <white>${d.stats.taps}<gray>, bought gems <white>${d.stats.gemsBought}"
                    }) noSave(who)
            }
            "rank" -> rankCommand(sender, args.drop(1))
            "pass", "gamepass" -> passCommand(sender, args.drop(1))
            "promo" -> when (who) {
                "create" -> {
                    val code = args.getOrNull(2); val uses = args.getOrNull(3)?.toIntOrNull(); val hours = args.getOrNull(4)?.toDoubleOrNull()
                    if (code == null || uses == null || hours == null || args.size < 6) return sender.say("<red>Usage: /tapx promo create <code> <maxUses|0> <hours|0> <rewards...>  <gray>rewards: ${Promos.SYNTAX}")
                    val err = Promos.create(code, uses, hours, args.drop(5).joinToString(" "))
                    sender.say(err?.let { "<red>$it" } ?: "<green>Created code <white>$code<green>. Players use <white>/promo $code")
                }
                "delete" -> sender.say(if (args.getOrNull(2)?.let(Promos::delete) == true) "<green>Deleted." else "<red>No such code.")
                else -> { sender.say("<gold>Promo codes:"); Promos.list().forEach { sender.say("<gray>$it") } }
            }
            "payment" -> {
                val usd = args.getOrNull(2)?.toDoubleOrNull()
                if (who == null || usd == null) return sender.say("<red>Usage: /tapx payment <player> <usd> [package name]")
                Payments.payment(who, usd, args.drop(3).joinToString(" "))
            }
            "piggybank" -> sender.sayRaw(Payments.piggyText())
            "flashsale" -> {
                if (who == "stop") { FlashSale.stop(this); return sender.say("<green>Flash sale stopped.") }
                val pct = who?.toIntOrNull(); val min = args.getOrNull(2)?.toDoubleOrNull()
                if (pct == null || min == null) return sender.say("<red>Usage: /tapx flashsale <percent> <minutes> <gray>or <red>/tapx flashsale stop")
                FlashSale.start(this, pct, min)
            }
        }
    }

    private val rankKeys get() = RANKS.map { it.key } + STAFF_RANKS.map { it.key }
    private val passIds get() = SHOP_ITEMS.filter { it.kind == "perm" }.map { it.id }

    /**
     * /rank set|add <player> <rank>, /rank remove <player> [rank], /rank list <player>. Store ranks
     * (vip..legend) and staff ranks (owner, admin, mod, helper) are separate, so a player can have both.
     */
    fun rankCommand(sender: CommandSender, a: List<String>) {
        if (!sender.hasPermission("tapx.admin")) return sender.say("<red>You don't have permission.")
        val sub = a.getOrNull(0)?.lowercase(); val target = a.getOrNull(1); val key = a.getOrNull(2)?.lowercase()
        fun usage() = sender.say("<red>Usage: /rank <set|add|remove|list> <player> [${rankKeys.joinToString("|")}]")
        if (target == null) return usage()
        val r = rankDef(key); val st = staffDef(key)
        val result: (PlayerData) -> String = when (sub) {
            "set", "add", "give" -> {
                if (r == null && st == null) return usage();
                { d -> if (st != null) d.staff = st.key else d.rank = r!!.key; "<green>$target now has the ${st?.name ?: r!!.name} rank." }
            }
            "remove", "take", "clear" -> when {
                key == null -> { d -> d.rank = null; d.staff = null; "<green>Removed all of $target's ranks." }
                st != null -> { d -> if (d.staff == st.key) d.staff = null; "<green>Removed ${st.name} from $target." }
                r != null -> { d -> if (d.rank == r.key) d.rank = null; "<green>Removed ${r.name} from $target." }
                else -> return usage()
            }
            "list", "check", "info" -> { d -> "<gold>$target: <gray>store rank <white>${rankDef(d.rank)?.name ?: "none"}<gray>, staff rank <white>${staffDef(d.staff)?.name ?: "none"}" }
            else -> return usage()
        }
        val changes = sub !in setOf("list", "check", "info")
        if (!editPlayer(target, { sender.say(it) }) { d, _ -> result(d) }) return sender.say("<red>$target has no TapX save.")
        if (changes) server.scheduler.runTaskLater(this, Runnable {
            Bukkit.getPlayerExact(target)?.let { p -> rankChanged(p, session(p)?.d?.rank, bought = false) }
        }, 2L)
    }

    /** /gamepass add|remove <player> <pass|all>, /gamepass set <player> <pass> <true|false>, /gamepass list <player>. */
    fun passCommand(sender: CommandSender, a: List<String>) {
        if (!sender.hasPermission("tapx.admin")) return sender.say("<red>You don't have permission.")
        val sub = a.getOrNull(0)?.lowercase(); val target = a.getOrNull(1); val id = a.getOrNull(2)?.lowercase()
        fun usage() = sender.say("<red>Usage: /gamepass <add|remove|set|list> <player> [${(passIds + "all").joinToString("|")}] <gray>(set takes true/false)")
        if (target == null) return usage()
        val ids = if (id == "all") passIds else listOfNotNull(id?.takeIf { it in passIds })
        val on = when (sub) {
            "add", "give" -> true
            "remove", "take" -> false
            "set" -> a.getOrNull(3)?.lowercase()?.let { it in setOf("true", "yes", "on", "1") } ?: return usage()
            "list", "check" -> null
            else -> return usage()
        }
        if (on != null && ids.isEmpty()) return usage()
        if (!editPlayer(target, { sender.say(it) }) { d, _ ->
                if (on == null) "<gold>$target's gamepasses: <white>${d.owned.filter { it in passIds }.joinToString().ifEmpty { "none" }}"
                else { if (on) d.owned += ids else d.owned -= ids.toSet(); "<green>${if (on) "Gave" else "Removed"} ${ids.joinToString()} ${if (on) "to" else "from"} $target." }
            }) sender.say("<red>$target has no TapX save.")
    }

    fun rankArgs(args: List<String>): List<String> = when (args.size) {
        1 -> listOf("set", "add", "remove", "list")
        2 -> Bukkit.getOnlinePlayers().map { it.name }
        3 -> if (args[0].lowercase() != "list") rankKeys else emptyList()
        else -> emptyList()
    }

    fun passArgs(args: List<String>): List<String> = when (args.size) {
        1 -> listOf("add", "remove", "set", "list")
        2 -> Bukkit.getOnlinePlayers().map { it.name }
        3 -> if (args[0].lowercase() != "list") passIds + "all" else emptyList()
        4 -> if (args[0].lowercase() == "set") listOf("true", "false") else emptyList()
        else -> emptyList()
    }

    /** Copy a fresh save into an existing object (offline edits change the object in place). */
    private fun copyInto(from: PlayerData, to: PlayerData) {
        for (f in PlayerData::class.java.declaredFields) { f.isAccessible = true; f.set(to, f.get(from)) }
    }

    private fun reply(sender: CommandSender, text: String): Boolean {
        sender.sendMessage(mm(text))
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (args.size >= 2 && sender.hasPermission("tapx.admin")) when (args[0].lowercase()) {
            "rank" -> return rankArgs(args.drop(1)).filter { it.startsWith(args.last(), ignoreCase = true) }
            "pass", "gamepass" -> return passArgs(args.drop(1)).filter { it.startsWith(args.last(), ignoreCase = true) }
        }
        val admin = sender.hasPermission("tapx.admin") && args[0].equals("admin", true)
        val gemsCmd = sender.hasPermission("tapx.admin") && args[0].equals("gems", true)
        val opts = when (args.size) {
            1 -> listOf("play", "top", "help") + if (sender.hasPermission("tapx.admin"))
                listOf("leave", "admin", "gems", "reset", "resetall", "set", "stats", "rank", "pass", "promo", "payment", "flashsale", "piggybank") else emptyList()
            2 -> when {
                admin -> listOf("give", "stage", "reset")
                gemsCmd -> listOf("give", "take", "set", "check")
                args[0].lowercase() in setOf("reset", "set", "stats") -> Bukkit.getOnlinePlayers().map { it.name }
                args[0].equals("rank", true) -> rankArgs(args.drop(1))
                args[0].equals("pass", true) || args[0].equals("gamepass", true) -> passArgs(args.drop(1))
                args[0].equals("promo", true) -> listOf("create", "delete", "list")
                args[0].equals("flashsale", true) -> listOf("10", "25", "50", "stop")
                args[0].equals("resetall", true) -> listOf("confirm")
                else -> emptyList()
            }
            3 -> when {
                admin || gemsCmd -> Bukkit.getOnlinePlayers().map { it.name }
                args[0].equals("set", true) -> STAT_NAMES
                args[0].equals("rank", true) -> rankArgs(args.drop(1))
                args[0].equals("pass", true) || args[0].equals("gamepass", true) -> passArgs(args.drop(1))
                else -> emptyList()
            }
            4 -> if (admin && args[1].equals("give", true)) listOf("gold", "gems", "relics") else emptyList()
            else -> emptyList()
        }
        return opts.filter { it.startsWith(args.last(), ignoreCase = true) }
    }
}
