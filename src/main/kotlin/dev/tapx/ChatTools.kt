package dev.tapx

import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextReplacementConfig
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import java.net.URI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import kotlin.random.Random

/** Staff level from a staff rank or permissions: 0 player, 1 helper, 2 mod, 3 admin, 4 owner (ops and console: 4). */
fun staffLevel(s: CommandSender): Int {
    val p = s as? Player ?: return 4
    if (p.isOp) return 4
    TabList.staff(p)?.let { return it.order / 1000 }
    return when {
        p.hasPermission("tapx.staff.admin") -> 3
        p.hasPermission("tapx.staff.mod") -> 2
        p.hasPermission("tapx.staff.helper") || p.hasPermission("tapx.warn") -> 1
        else -> 0
    }
}

fun staffName(level: Int) = when (level) { 1 -> "Helper"; 2 -> "Mod"; 3 -> "Admin"; else -> "Owner" }

private val plain = PlainTextComponentSerializer.plainText()

/**
 * Chat filter and anti-spam (ported from DrillSim): blocked words (with leetspeak and spacing
 * tricks), links to other servers, chat delay, repeated messages, excessive caps and letter spam.
 * Blocked messages are shown to staff as an ALERT. Mentioning an online player's name pings them.
 * Mods and up (or tapx.chat.bypass) skip the spam checks; nobody skips the word filter.
 */
object ChatFilter : Listener {
    private lateinit var plugin: TapXPlugin
    private val lastSent = ConcurrentHashMap<UUID, Long>()
    private val recent = ConcurrentHashMap<UUID, ArrayDeque<Pair<String, Long>>>()
    @Volatile private var words: List<Pair<String, Regex>> = emptyList()
    @Volatile private var strict: List<Pair<String, Regex>> = emptyList()

    private val LEET = mapOf('0' to 'o', '1' to 'i', '3' to 'e', '4' to 'a', '5' to 's', '7' to 't', '8' to 'b', '@' to 'a', '$' to 's', '!' to 'i', '|' to 'i', '+' to 't')
    private val IP = Regex("""\b\d{1,3}(\s*[.,]\s*\d{1,3}){3}\b""")
    private val DOMAIN = Regex("""\b[a-z0-9-]{2,}\s*(\.|\(dot\)|\[dot]| dot )\s*(com|net|org|gg|io|me|xyz|club|us|co|tk|uk|ca|de|fr|ru|pl|nl|eu|online|site|fun|pro|world|host|minehut\.gg|aternos\.me|ploudos\.me)\b""", RegexOption.IGNORE_CASE)

    fun enable(p: TapXPlugin) {
        plugin = p
        reload()
        p.server.pluginManager.registerEvents(this, p)
        Cmds.register("chatfilter", listOf("filter", "swearfilter"), "tapx.chat.filter", "/chatfilter <add|remove|list> [word]",
            tab = { c -> if (c.args.size == 1) listOf("add", "remove", "list", "addstrict") else emptyList() }) { c ->
            val w = c.arg(1)?.lowercase()
            when (c.arg(0)?.lowercase()) {
                "add", "addstrict" -> {
                    w ?: return@register c.sender.say("<red>Supply a word to filter!")
                    val key = if (c.arg(0) == "addstrict") "chat-filter.strict-words" else "chat-filter.words"
                    val list = p.config.getStringList(key)
                    if (w in list) return@register c.sender.say("<red>That word is already blocked!")
                    p.config.set(key, list + w); p.saveConfig(); reload(); c.sender.say("<gray>Added <white>$w <gray>to the swear filter.")
                }
                "remove", "del" -> {
                    w ?: return@register c.sender.say("<red>Supply a word to unfilter!")
                    var found = false
                    for (key in listOf("chat-filter.words", "chat-filter.strict-words")) {
                        val list = p.config.getStringList(key)
                        if (w in list) { p.config.set(key, list - w); found = true }
                    }
                    if (!found) return@register c.sender.say("<red>That word is not blocked!")
                    p.saveConfig(); reload(); c.sender.say("<gray>Removed <white>$w <gray>from the swear filter.")
                }
                "list" -> c.sender.say("<red>The filtered words are: <gray>${(words + strict).map { it.first }.distinct().sorted().joinToString(", ")}")
                else -> c.sender.say("<yellow>/chatfilter add <word><gray>, <yellow>/chatfilter addstrict <word> <dark_gray>(blocked even inside other words)<gray>, <yellow>/chatfilter remove <word><gray>, <yellow>/chatfilter list")
            }
        }
        Cmds.register("chatdelay", listOf("slowmode", "slowchat"), "tapx.chat.delay", "/chatdelay <seconds|off>", tab = { listOf("0", "2", "3", "5", "10") }) { c ->
            val n = c.arg(0)?.let { if (it.equals("off", true)) 0.0 else it.toDoubleOrNull() }
                ?: return@register c.sender.say("<gray>Chat delay is <white>${delay()}s<gray>. Usage: /chatdelay <seconds|off>")
            p.config.set("chat-filter.delay-seconds", n.coerceIn(0.0, 300.0)); p.saveConfig()
            Bukkit.broadcast(mm("$PREFIX<gray>Chat delay is now ${if (n <= 0) "<red>off" else "<white>${fmtNum(n)}s"}<gray>."))
        }
    }

    private fun fmtNum(n: Double) = if (n % 1.0 == 0.0) n.toLong().toString() else n.toString()

    fun reload() {
        fun pattern(w: String) = letters(w).map { "${it}+" }.joinToString("")
        words = plugin.config.getStringList("chat-filter.words").filter { letters(it).isNotEmpty() }
            .map { it to Regex("^${pattern(it)}(s|es|er|ers|ing|ed|y)?$") }
        strict = plugin.config.getStringList("chat-filter.strict-words").filter { letters(it).isNotEmpty() }
            .map { it to Regex(pattern(it)) }
    }

    private fun delay() = plugin.config.getDouble("chat-filter.delay-seconds", 2.0)

    /** Lowercase, leetspeak undone, letters only ("F.u_C|<" -> "fuck"-ish). Word patterns allow repeated letters ("fuuuck"). */
    private fun letters(s: String): String {
        val sb = StringBuilder()
        for (ch0 in s.lowercase()) { val ch = LEET[ch0] ?: ch0; if (ch in 'a'..'z') sb.append(ch) }
        return sb.toString()
    }

    /** The blocked word in [msg], if any. */
    fun badWord(msg: String): String? {
        val parts = msg.split(Regex("\\s+"))
        for (t in parts.map(::letters).filter { it.isNotEmpty() }) for ((w, rx) in words) if (rx.matches(t)) return w
        val joined = letters(msg)
        for ((w, rx) in strict) if (rx.containsMatchIn(joined)) return w
        // single letters typed apart: "f u c k"
        val singles = letters(parts.filter { it.length == 1 }.joinToString(""))
        if (singles.length >= 3) for ((w, rx) in words) if (Regex(rx.pattern.removePrefix("^").removeSuffix("$")).containsMatchIn(singles)) return w
        return null
    }

    private fun advertises(msg: String): Boolean {
        val allowed = plugin.config.getStringList("chat-filter.allowed-links").map { it.lowercase() } +
            listOf(Discord.invite(), Cmds.storeUrl()).map { it.lowercase().removePrefix("https://").removePrefix("http://") }
        var m = msg.lowercase()
        for (a in allowed) m = m.replace(a.lowercase(), "")
        return IP.containsMatchIn(m) || DOMAIN.containsMatchIn(m)
    }

    private fun block(p: Player, msg: String, why: String) {
        p.sendMessage(mm("<red>✖ <red>You cannot send that here!"))
        val alert = mm("<red><b>ALERT</b> <dark_gray>» <white>${p.name} <gray>tried to say ($why): \"<white>${esc(msg)}<gray>\"")
        for (o in Bukkit.getOnlinePlayers()) if (staffLevel(o) >= 1) o.sendMessage(alert)
        plugin.logger.info("Chat filter ($why) blocked ${p.name}: $msg")
    }

    fun esc(s: String) = s.replace("<", "\\<")

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onChat(e: AsyncChatEvent) {
        if (!plugin.config.getBoolean("chat-filter.enabled", true)) return
        val p = e.player
        val text = plain.serialize(e.message())
        val bypass = staffLevel(p) >= 2 || p.hasPermission("tapx.chat.bypass")
        badWord(text)?.let { block(p, text, "filter: $it"); e.isCancelled = true; return }
        if (!bypass && plugin.config.getBoolean("chat-filter.block-links", true) && advertises(text)) { block(p, text, "link"); e.isCancelled = true; return }
        val now = System.currentTimeMillis()
        if (!bypass) {
            val wait = (delay() * 1000).toLong()
            val last = lastSent[p.uniqueId] ?: 0
            if (wait > 0 && now - last < wait) {
                e.isCancelled = true
                p.sendMessage(mm("<red><b>HEY!</b> <gray>You have to wait <white>${fmtNum(delay())}s</white> between messages!"))
                return
            }
            val key = letters(text)
            val r = recent.getOrPut(p.uniqueId) { ArrayDeque() }
            synchronized(r) {
                r.removeIf { now - it.second > 60_000 }
                if (key.length >= 2 && r.count { it.first == key } >= plugin.config.getInt("chat-filter.max-repeats", 1)) {
                    e.isCancelled = true
                    p.sendMessage(mm("<red><b>HEY!</b> <gray>Your message has been blocked due to repetition."))
                    return
                }
                r.addLast(key to now); while (r.size > 5) r.removeFirst()
            }
        }
        lastSent[p.uniqueId] = now
        var msg = e.message()
        if (!bypass) {
            val letters = text.filter { it.isLetter() }
            if (letters.length >= 6 && letters.count { it.isUpperCase() } * 100 / letters.length >= plugin.config.getInt("chat-filter.caps-percent", 60)) {
                msg = Component.text(text.lowercase())
                p.sendMessage(mm("<yellow><b>CAPS</b> <dark_gray>» <gray>Please don't use excessive caps."))
            }
            // "heyyyyyyy" -> "heyyy"
            msg = msg.replaceText(TextReplacementConfig.builder().match("(.)\\1{3,}").replacement { b -> b.content(b.content().take(3)) }.build())
        }
        // mentions: highlight and ping
        for (o in Bukkit.getOnlinePlayers()) {
            if (o == p || o.uniqueId in Essentials.vanished) continue
            val rx = Regex("(?i)(?<![A-Za-z0-9_])@?${Regex.escape(o.name)}(?![A-Za-z0-9_])")
            if (!rx.containsMatchIn(text)) continue
            msg = msg.replaceText(TextReplacementConfig.builder().match(rx.pattern).replacement(mm("<yellow><b>@${o.name}</b></yellow>")).build())
            plugin.server.scheduler.runTask(plugin, Runnable {
                o.playSound(o.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.4f)
                o.sendActionBar(mm("<yellow>${p.name} <gray>mentioned you in chat!"))
            })
        }
        e.message(msg)
    }
}

/**
 * Chat games (ported from DrillSim's /chatgame): math, quick type, unscramble, guess the number,
 * missing letters and TapX trivia. One starts on its own every few minutes when enough players are
 * online; the first right answer wins gems and gold.
 */
object ChatGames : Listener {
    private lateinit var plugin: TapXPlugin

    private class Game(val type: String, val title: String, val answers: List<String>, val shown: String, val started: Long, val seconds: Int, val number: Int? = null)

    @Volatile private var game: Game? = null
    private var timeoutTask: org.bukkit.scheduler.BukkitTask? = null
    private val customTrivia = mutableListOf<Pair<String, List<String>>>()

    private val PHRASES = listOf(
        "tap tap tap the titans away", "my allies hit harder than me", "prestige for more relics", "the boss timer is ticking",
        "thunder strike goes boom", "legend rank looks shiny", "never skip leg day or boss day", "gems are a hero's best friend",
        "meteor storm incoming", "one more stage then bed", "shadow clone never misses", "free chest every four hours",
        "spin the daily wheel", "midas touch makes me rich", "titan lord has entered the chat", "pet frenzy is the best spell",
    )

    private fun wordList(): List<String> = (SKILLS.flatMap { it.name.split(' ') } + PETS.map { it.name } + HEROES.flatMap { it.name.split(' ') } +
        WORLDS.flatMap { it.name.split(' ') } + listOf("titan", "prestige", "relic", "artifact", "gems", "boss", "dragon", "knight", "treasure", "mana", "potion", "arcade"))
        .map { it.lowercase().filter(Char::isLetter) }.filter { it.length >= 5 }.distinct()

    /** Questions built from the game content, so the answers are always right. */
    private fun trivia(): List<Pair<String, List<String>>> = buildList {
        add("What is this server called?" to listOf("tapx"))
        add("What rank comes after MVP?" to listOf(RANKS[rankIndex("mvp") + 1].name))
        add("What is the highest rank in the store?" to listOf(RANKS.last().name))
        add("What is the cheapest rank in the store?" to listOf(RANKS.first().name))
        add("How many spells are there?" to listOf("${SKILLS.size}"))
        add("How many monsters do you beat before each boss?" to listOf("$WAVES"))
        add("What currency are ranks bought with?" to listOf("gems", "gem"))
        add("What do you earn when you prestige?" to listOf("relics", "relic"))
        add("How many different pets are there?" to listOf("${PETS.size}"))
        add("What is the first ally you can hire?" to listOf(HEROES[0].name, HEROES[0].name.split(' ')[0]))
        add("How many hours between free chests without a rank?" to listOf("4"))
        add("What command opens the web store? (no /)" to listOf("store"))
        add("What command shows the running events? (no /)" to listOf("events"))
        add("What do you type in chat after someone buys something?" to listOf("gg"))
        add("How many gems does the ${RANKS.last().name} rank cost?" to listOf("${RANKS.last().gems}"))
        add("Which spell adds time to a boss fight?" to listOf(SKILLS.first { it.id == "timewarp" }.name))
        add("Which spell makes your allies attack faster?" to listOf(SKILLS.first { it.id == "haste" }.name))
        add("Which spell turns taps into extra gold?" to listOf(SKILLS.first { it.id == "midas" }.name, "midas"))
        add("What is the very first spell you unlock?" to listOf(SKILLS.first().name, "thunder"))
        add("What is the first world called?" to listOf(WORLDS.first().name))
        add("What is the max level of Sword Training?" to listOf("${TRAINING.first { it.id == "t_sword" }.max}"))
        add("What is our Discord invite code? (discord.gg/...)" to listOf(Discord.invite().substringAfterLast('/')))
        val sk = SKILLS.random()
        add("What hero level do you need to learn ${sk.name}?" to listOf("${sk.req}"))
        val r = RANKS.random()
        add("How many gems does the ${r.name} rank cost?" to listOf("${r.gems}"))
        val p = PETS.random()
        add("Which pet boosts your ${p.label}?" to listOf(p.name))
        addAll(customTrivia)
    }

    fun enable(p: TapXPlugin) {
        plugin = p
        p.server.pluginManager.registerEvents(this, p)
        val every = p.config.getDouble("chat-games.interval-minutes", 8.0)
        if (every > 0) {
            val ticks = (every * 60 * 20).toLong().coerceAtLeast(20 * 60)
            p.server.scheduler.runTaskTimer(p, Runnable {
                if (game == null && Bukkit.getOnlinePlayers().size >= p.config.getInt("chat-games.min-players", 2)) start(listOf("math", "type", "unscramble", "number", "missingletters", "trivia", "trivia").random(), emptyList())
            }, ticks, ticks)
        }
        Cmds.register("chatgame", listOf("cg", "chatgames"), "tapx.chatgame", "/chatgame <type> [...]",
            tab = { c -> when (c.args.size) { 1 -> listOf("math", "type", "unscramble", "number", "missingletters", "trivia", "stop"); 2 -> if (c.arg(0) == "trivia") listOf("list", "custom") else emptyList(); else -> emptyList() } }) { c ->
            val t = c.arg(0)?.lowercase()
            if (t == null) {
                for (l in listOf("<dark_gray><st>                                        ", "<white><b>Chat Games", "<gray>/chatgame math", "<gray>/chatgame type <dark_gray>[your own words]",
                        "<gray>/chatgame unscramble <dark_gray>[your own word]", "<gray>/chatgame number <dark_gray>[the number]", "<gray>/chatgame missingletters <dark_gray>[your own word]",
                        "<gray>/chatgame trivia <dark_gray>[number]", "<gray>/chatgame trivia list", "<gray>/chatgame trivia custom <dark_gray><question> | <answer>",
                        "<gray>/chatgame stop", "<dark_gray><st>                                        ")) c.sender.sayRaw(l)
                return@register
            }
            if (t == "stop") return@register stop(c.sender)
            if (t == "trivia" && c.arg(1)?.lowercase() == "list") {
                c.sender.sayRaw("<dark_aqua><b>TRIVIA QUESTIONS</b> <dark_gray>(/chatgame trivia <number>)")
                trivia().forEachIndexed { i, (q, a) -> c.sender.sayRaw(" <gray>${i + 1}. <white>$q <dark_gray>(${a.first()})") }
                return@register
            }
            if (t == "trivia" && c.arg(1)?.lowercase() == "custom") {
                val parts = c.args.drop(2).joinToString(" ").split('|').map { it.trim() }
                if (parts.size < 2 || parts.any { it.isEmpty() }) return@register c.sender.say("<red>Usage: <white>/chatgame trivia custom <question> | <answer>")
                if (game != null) return@register c.sender.say("<red>There is already a chat game active.")
                return@register start("trivia", emptyList(), parts[0] to listOf(parts[1]))
            }
            if (game != null) return@register c.sender.say("<red>There is already a chat game active.")
            if (t !in setOf("math", "type", "unscramble", "number", "missingletters", "trivia")) return@register c.sender.say("<red>Unknown game type. <gray>Use: <white>math, type, unscramble, number, missingletters, trivia")
            start(t, c.args.drop(1))
        }
        Cmds.register("stopchatgame", listOf("endchatgame"), "tapx.chatgame", "/stopchatgame", tab = { emptyList() }) { c -> stop(c.sender) }
    }

    private fun stop(s: CommandSender) {
        if (game == null) return s.say("<red>There is no active chat game.")
        game = null; timeoutTask?.cancel()
        broadcast(listOf("<red><b>CHAT GAME CANCELLED", "<gray>The current chat game was cancelled."))
    }

    private fun broadcast(lines: List<String>) {
        Bukkit.broadcast(mm(""))
        for (l in lines) Bukkit.broadcast(mm(" $l"))
        Bukkit.broadcast(mm(""))
    }

    private fun scramble(w: String): String {
        if (w.length < 3) return w
        var s: String
        var tries = 0
        do { s = w.toList().shuffled().joinToString(""); tries++ } while (s == w && tries < 10)
        return s
    }

    private fun mask(w: String): String {
        val idx = w.indices.filter { w[it].isLetter() }.shuffled().take(maxOf(1, w.count(Char::isLetter) * 2 / 5)).toSet()
        return w.mapIndexed { i, ch -> if (i in idx) '_' else ch }.joinToString("")
    }

    private fun start(type: String, args: List<String>, customQ: Pair<String, List<String>>? = null) {
        val head = "<light_purple><b>CHAT GAME</b> <dark_gray>»"
        val g: Game = when (type) {
            "math" -> {
                val (q, a) = when (Random.nextInt(4)) {
                    0 -> { val x = Random.nextInt(12, 99); val y = Random.nextInt(12, 99); "$x + $y" to x + y }
                    1 -> { val x = Random.nextInt(40, 150); val y = Random.nextInt(5, x); "$x - $y" to x - y }
                    2 -> { val x = Random.nextInt(3, 13); val y = Random.nextInt(3, 13); "$x × $y" to x * y }
                    else -> { val x = Random.nextInt(2, 10); val y = Random.nextInt(2, 10); val z = Random.nextInt(2, 30); "$x × $y + $z" to x * y + z }
                }
                Game(type, "<dark_aqua><b>MATH GAME", listOf("$a"), q, System.currentTimeMillis(), 30).also {
                    broadcast(listOf("$head <dark_aqua><b>MATH GAME", "<white>First player to answer <yellow>$q <white>correctly wins!", "<gray>Type your answer in chat! <dark_gray>You have 30 seconds."))
                }
            }
            "type" -> {
                val phrase = args.joinToString(" ").ifBlank { PHRASES.random() }
                Game(type, "<gold><b>QUICK TYPE", listOf(phrase), phrase, System.currentTimeMillis(), 30).also {
                    // shown with zero-width tags between letters is pointless in chat; show it plainly in yellow
                    broadcast(listOf("$head <gold><b>QUICK TYPE", "<white>Type: <yellow>$phrase", "<gray>First player to type it correctly wins!"))
                }
            }
            "unscramble" -> {
                val w = args.firstOrNull()?.lowercase() ?: wordList().random()
                val s = scramble(w)
                Game(type, "<light_purple><b>UNSCRAMBLE", listOf(w), s, System.currentTimeMillis(), 45).also {
                    broadcast(listOf("$head <light_purple><b>UNSCRAMBLE", "<white>Unscramble this: <yellow>$s", "<gray>First player to type the correct answer wins!"))
                }
            }
            "number" -> {
                val n = args.firstOrNull()?.toIntOrNull() ?: Random.nextInt(1, 31)
                Game(type, "<green><b>GUESS THE NUMBER", listOf("$n"), "", System.currentTimeMillis(), 60, n).also {
                    broadcast(listOf("$head <green><b>GUESS THE NUMBER", "<white>I'm thinking of a number between <yellow>1 <white>and <yellow>30<white>!", "<gray>First player to guess it wins! Type your guess in chat."))
                }
            }
            "missingletters" -> {
                val w = args.joinToString(" ").lowercase().ifBlank { wordList().random() }
                val m = mask(w)
                Game(type, "<yellow><b>MISSING LETTERS", listOf(w), m, System.currentTimeMillis(), 45).also {
                    broadcast(listOf("$head <yellow><b>MISSING LETTERS", "<white>Fill in the blanks: <yellow>$m", "<gray>First player to type the full answer wins!"))
                }
            }
            else -> {
                val all = trivia()
                val (q, a) = customQ ?: args.firstOrNull()?.toIntOrNull()?.let { all.getOrNull(it - 1) } ?: all.random()
                Game("trivia", "<dark_aqua><b>TRIVIA", a, q, System.currentTimeMillis(), 45).also {
                    broadcast(listOf("$head <dark_aqua><b>TRIVIA", "<white>$q", "<gray>First player to answer correctly wins!"))
                }
            }
        }
        game = g
        timeoutTask?.cancel()
        timeoutTask = plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (game === g) { game = null; broadcast(listOf("<red><b>CHAT GAME EXPIRED", "<gray>Nobody answered in time.", "<white>Answer: <green>${g.answers.first()}")) }
        }, g.seconds * 20L)
    }

    private fun clean(s: String) = s.lowercase().trim().replace(Regex("[^a-z0-9 ]"), "").replace(Regex("\\s+"), " ")

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onChat(e: AsyncChatEvent) {
        val g = game ?: return
        val p = e.player
        val text = plain.serialize(e.message()).trim()
        if (g.number != null) {
            val n = text.toIntOrNull() ?: return
            if (n != g.number) {
                p.sendMessage(mm(when { n < 1 || n > 30 -> "<red>The number is between 1 and 30!"; n > g.number -> "<gray>Too high, try again!"; else -> "<gray>Too low, try again!" }))
                return
            }
        } else if (g.answers.none { clean(it) == clean(text) }) return
        synchronized(this) { if (game !== g) return; game = null }
        timeoutTask?.cancel()
        val secs = (System.currentTimeMillis() - g.started) / 1000.0
        val gems = plugin.config.getLong("chat-games.reward-gems", 1)
        val goldKills = plugin.config.getDouble("chat-games.reward-gold-kills", 25.0)
        plugin.server.scheduler.runTask(plugin, Runnable {
            val what = buildList { if (gems > 0) add("<aqua>$gems gem${if (gems > 1) "s" else ""}"); if (goldKills > 0) add("<gold>gold") }.joinToString(" <gray>+ ")
            broadcast(listOf("<green><b>CHAT GAME WON", "<white>${p.name} <gray>won in <light_purple>${"%.1f".format(secs)}s<gray>!",
                "<gray>${if (g.type == "math") "${g.shown} = " else "Answer: "}<green>${g.answers.first()}", "<gray>Reward: $what"))
            if (gems > 0) plugin.giveGems(p.name, gems, "chat game", paid = false)
            if (goldKills > 0) plugin.session(p)?.let { s -> s.post { val v = s.game.killGold(s.d.stage) * goldKills; s.game.addGold(v) } }
            p.playSound(p.location, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f)
        })
    }
}

/**
 * The welcome message (like DrillSim's): the player's face drawn in chat, next to the server name,
 * a short line and clickable Discord and store links.
 */
object Welcome : Listener {
    private lateinit var plugin: TapXPlugin
    private val faces = ConcurrentHashMap<String, List<List<Int>>>()

    /** Steve's face, for players without a skin (offline mode) or when the skin can't be loaded. */
    private val STEVE: List<List<Int>> = run {
        val H = 0x2b1e0d; val S = 0xb6896c; val W = 0xffffff; val E = 0x523d89; val N = 0x94603e; val M = 0x6a4030
        listOf(
            listOf(H, H, H, H, H, H, H, H), listOf(H, H, H, H, H, H, H, H), listOf(H, S, S, S, S, S, S, H), listOf(S, S, S, S, S, S, S, S),
            listOf(S, W, E, S, S, E, W, S), listOf(S, S, S, N, N, S, S, S), listOf(S, S, M, M, M, M, S, S), listOf(S, S, M, S, S, M, S, S),
        )
    }

    fun enable(p: TapXPlugin) { plugin = p; p.server.pluginManager.registerEvents(this, p) }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onJoin(e: PlayerJoinEvent) {
        if (!plugin.config.getBoolean("welcome.enabled", true)) return
        val p = e.player
        val first = !p.hasPlayedBefore()
        val url = p.playerProfile.textures.skin?.toString()
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val face = url?.let { u -> faces[u] ?: runCatching { loadFace(u) }.getOrNull()?.also { faces[u] = it } }
            plugin.server.scheduler.runTaskLater(plugin, Runnable { if (p.isOnline) send(p, face ?: STEVE, first) }, 20L)
        })
    }

    private fun loadFace(url: String): List<List<Int>> {
        val img = URI(url).toURL().openStream().use { ImageIO.read(it) }
        return (0 until 8).map { y ->
            (0 until 8).map { x ->
                val base = img.getRGB(8 + x, 8 + y)
                val hat = img.getRGB(40 + x, 8 + y)
                if ((hat ushr 24) > 128) hat and 0xffffff else base and 0xffffff
            }
        }
    }

    fun send(p: Player, face: List<List<Int>>?, first: Boolean) {
        val discord = Discord.invite()
        val store = Cmds.storeUrl()
        val short = { u: String -> u.removePrefix("https://").removePrefix("http://").trimEnd('/') }
        val address = plugin.config.getString("welcome.address", "")!!
        val text = listOf(
            "",
            "<white><b>${if (first) "WELCOME" else "WELCOME BACK"}</b> <gray>to <b><gradient:#ffcf3a:#ff7a3a:#ff5a8a>TAPX</gradient></b>",
            "<gray>${plugin.config.getString("welcome.line", "Tap titans, hire allies and climb the stages!")}",
            if (first) "<yellow>Your game starts by itself - have fun, ${p.name}!" else "<gray>Your progress is right where you left it.",
            if (address.isNotBlank()) "<white>$address" else "",
            "<click:open_url:'$discord'><hover:show_text:'<blue>Click to join our Discord!'><#5865f2>${short(discord).replaceFirstChar { it.uppercase() }}</hover></click>",
            "<click:open_url:'$store'><hover:show_text:'<light_purple>Click to open the web store!'><light_purple>${short(store).replaceFirstChar { it.uppercase() }}</hover></click>",
            "",
        )
        p.sendMessage(mm("<dark_gray><st>                                                  "))
        for (y in 0 until 8) {
            val left = face?.get(y)?.joinToString("") { "<#%06x>█".format(it) } ?: ""
            p.sendMessage(mm("$left<reset>  ${text[y]}"))
        }
        p.sendMessage(mm("<dark_gray><st>                                                  "))
    }
}

/** DrillSim-style /payment (store purchase thank-you) and /flashsale for the web store's console commands. */
object StoreCommands {
    fun enable(p: TapXPlugin) {
        Cmds.register("payment", listOf("purchase", "thankyou"), "tapx.admin", "/payment <player> <usd> [package]", tab = { c -> if (c.args.size == 1) Bukkit.getOnlinePlayers().map { it.name } else emptyList() }) { c ->
            val who = c.arg(0); val usd = c.arg(1)?.removePrefix("$")?.toDoubleOrNull()
            if (who == null || usd == null) return@register c.sender.say("<red>Usage: /payment <player> <usd> [package name]")
            Payments.payment(who, usd, c.args.drop(2).joinToString(" "))
        }
        Cmds.register("flashsale", listOf("sale"), "tapx.admin", "/flashsale <percent> <duration> | stop", tab = { c -> when (c.args.size) { 1 -> listOf("10", "25", "50", "stop"); 2 -> listOf("30m", "1h", "2h", "1d"); else -> emptyList() } }) { c ->
            when {
                c.arg(0)?.lowercase() == "stop" -> { FlashSale.stop(p); c.sender.say("<green>Flash sale stopped.") }
                c.arg(0) == null -> c.sender.say(if (FlashSale.active()) "<yellow>Flash sale: <red>${FlashSale.percent}% off<gray>, ends in <yellow>${fmtTime((FlashSale.endsAt - System.currentTimeMillis()) / 1000.0)}" else "<gray>No flash sale running. <white>/flashsale <percent> <duration> <dark_gray>(e.g. /flashsale 30 2h)")
                else -> {
                    val pct = c.arg(0)?.removeSuffix("%")?.toIntOrNull()?.takeIf { it > 0 } ?: return@register c.sender.say("<red>The percent has to be more than 0.")
                    val min = c.arg(1)?.let(::parseMinutes) ?: return@register c.sender.say("<red>Give a duration, e.g. <white>2h<red>, <white>30m<red>, <white>1h30m<red> or <white>1d<red>.")
                    FlashSale.start(p, pct, min)
                }
            }
        }
    }

    /** "1h30m" -> 90.0, "45" -> 45.0 (minutes). */
    fun parseMinutes(s: String): Double? {
        s.toDoubleOrNull()?.let { return it }
        val rx = Regex("(\\d+(?:\\.\\d+)?)([smhdw])", RegexOption.IGNORE_CASE)
        val parts = rx.findAll(s).toList()
        if (parts.isEmpty() || parts.joinToString("") { it.value }.length != s.length) return null
        return parts.sumOf { m ->
            val n = m.groupValues[1].toDouble()
            when (m.groupValues[2].lowercase()) { "s" -> n / 60; "m" -> n; "h" -> n * 60; "d" -> n * 1440; else -> n * 10080 }
        }
    }
}
