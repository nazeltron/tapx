package dev.tapx

import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Discord linking through DiscordSRV, on the same bot as DrillSim but linked separately: /link gives
 * a TapX code ("TAPX-KQZMW", letters only, so it can never match DrillSim's 4-digit codes) that
 * players DM to the DrillSim bot. TapX reads the DMs itself (a JDA listener on its DiscordSRV, whose
 * own DM linking is off) and stores the link in TapX's DiscordSRV, so TapX and DrillSim links are
 * separate. DrillSim's DiscordSRV also answers every DM ("that's not a 4-number code"); that stray
 * reply is the same bot, so TapX deletes it. See setup/discordsrv-config.yml.
 * Linked players who are also in the Discord server get the [LINK_REWARDS]; the first link also
 * gives gems and pet eggs. Talks to DiscordSRV by reflection, so TapX starts without it.
 */
object Discord : Listener {
    private lateinit var plugin: TapXPlugin
    private val unlinkConfirm = ConcurrentHashMap<UUID, Long>()
    private val codeCooldown = ConcurrentHashMap<UUID, Long>()
    /** Discord id -> (in the server, checked at) for REST lookups when the member cache misses. */
    private val memberCache = ConcurrentHashMap<String, Pair<Boolean, Long>>()
    /** TapX link codes ("KQZMW" without the TAPX- prefix) -> (player, expires at). */
    private val codes = ConcurrentHashMap<String, Pair<UUID, Long>>()
    /** DM channel id -> until when the bot's other replies there (DrillSim's) are deleted. */
    private val cleanup = ConcurrentHashMap<String, Long>()
    @Volatile private var listenedJda: Any? = null
    private const val MARK = "**TapX** »"
    private const val CODE_LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ"

    fun available() = Bukkit.getPluginManager().isPluginEnabled("DiscordSRV")

    private fun dsrv(): Any? = runCatching {
        Class.forName("github.scarsz.discordsrv.DiscordSRV", true, Bukkit.getPluginManager().getPlugin("DiscordSRV")!!.javaClass.classLoader)
            .getMethod("getPlugin").invoke(null)
    }.getOrNull()

    private fun linkManager(): Any? = runCatching { dsrv()?.let { it.javaClass.getMethod("getAccountLinkManager").invoke(it) } }.getOrNull()

    private fun call(o: Any, name: String, vararg args: Any): Any? {
        val m = o.javaClass.methods.first { m -> m.name == name && m.parameterCount == args.size && m.parameterTypes.zip(args).all { (t, a) -> t.isInstance(a) } }
        m.isAccessible = true
        return m.invoke(o, *args)
    }

    private fun discordId(uuid: UUID): String? = runCatching { linkManager()?.let { call(it, "getDiscordId", uuid) as String? } }.getOrNull()

    /** Whether the Discord user is in the main Discord server (true when it can't be checked). */
    private fun inServer(id: String): Boolean {
        if (!plugin.config.getBoolean("discord.require-in-server", true)) return true
        return runCatching {
            val guild = dsrv()?.let { call(it, "getMainGuild") } ?: return true
            if (call(guild, "getMemberById", id) != null) return true
            memberCache[id]?.let { (v, at) -> if (System.currentTimeMillis() - at < 300_000) return v }
            val found = runCatching { call(call(guild, "retrieveMemberById", id)!!, "complete") != null }.getOrDefault(false)
            memberCache[id] = found to System.currentTimeMillis()
            found
        }.getOrDefault(true)
    }

    fun invite(): String = Cmds.validUrl(if (::plugin.isInitialized) plugin.config.getString("discord.invite", "")!! else "", DEFAULT_INVITE)
    const val DEFAULT_INVITE = "https://discord.gg/drillnetwork"
    private fun bot() = plugin.config.getString("discord.bot-name", "DrillSim")!!

    private fun inviteLink() = "<click:open_url:'${invite()}'><hover:show_text:'<gray>Click to open'><#8ea1e1><u>${invite()}</u></hover></click>"

    private fun rewardLines(p: Player) = LINK_REWARDS.forEach { p.sayRaw("  <dark_gray>• <white>$it") }

    fun enable(p: TapXPlugin) {
        plugin = p
        p.server.pluginManager.registerEvents(this, p)
        Cmds.register("discord", listOf("dc", "disc"), perm = null, usage = "/discord", tab = { emptyList() }) { c ->
            c.sender.sayRaw("")
            c.sender.sayRaw(" <#5865f2><b>DISCORD</b> <dark_gray>» ${inviteLink()}")
            c.sender.sayRaw(" <gray>Join for giveaways and news, then type <aqua>/link</aqua> for free rewards:")
            (c.sender as? Player)?.let(::rewardLines) ?: LINK_REWARDS.forEach { c.sender.sayRaw("  <dark_gray>• <white>$it") }
            c.sender.sayRaw("")
        }
        Cmds.register("link", listOf("linkdiscord", "discordlink", "verify"), perm = null, usage = "/link", tab = { emptyList() }) { c ->
            val pl = c.needPlayer() ?: return@register
            if (!available()) return@register pl.say("<red>Linking isn't available right now. Please tell a staff member.")
            Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable { link(pl) })
        }
        Cmds.register("unlink", listOf("unlinkdiscord"), perm = null, usage = "/unlink", tab = { emptyList() }) { c ->
            val pl = c.needPlayer() ?: return@register
            if (!available()) return@register pl.say("<red>Linking isn't available right now.")
            if ((unlinkConfirm[pl.uniqueId] ?: 0) < System.currentTimeMillis()) {
                unlinkConfirm[pl.uniqueId] = System.currentTimeMillis() + 10_000
                return@register pl.say("<gray>You'll lose <gold>x$LINK_GOLD gold<gray>, <red>x$LINK_DAMAGE damage<gray>, the free auto tap and the extra mana. Type <red>/unlink <gray>again within 10 seconds to confirm.")
            }
            unlinkConfirm.remove(pl.uniqueId)
            Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                if (discordId(pl.uniqueId) == null) return@Runnable pl.say("<gray>Your Discord isn't linked. <dark_gray>(/link)")
                runCatching { linkManager()?.let { call(it, "unlink", pl.uniqueId) } }
                    .onFailure { return@Runnable pl.say("<red>Couldn't unlink right now. Please try again.") }
                check(pl)
            })
        }
        // read the bot's DMs (re-attached when DiscordSRV reconnects)
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, Runnable { if (available()) attachListener() }, 100L, 400L)
        // check links in the background; reward the first link
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, Runnable { if (available()) Bukkit.getOnlinePlayers().forEach(::check) }, 200L, 400L)
        val remind = (p.config.getDouble("discord.reminder-minutes", 10.0) * 60 * 20).toLong()
        if (remind > 0) Bukkit.getScheduler().runTaskTimer(plugin, Runnable { remind() }, remind, remind)
    }

    private fun link(pl: Player) {
        val id = discordId(pl.uniqueId)
        if (id != null) {
            if (inServer(id)) {
                pl.sayRaw("")
                pl.sayRaw(" <gray>Your Discord is linked. Rewards active:")
                rewardLines(pl)
                pl.sayRaw(" <dark_gray>(/unlink to remove it)")
                pl.sayRaw("")
            } else {
                pl.sayRaw("")
                pl.sayRaw(" <gray>Your Discord is linked, but you're <red>not in our server<gray>.")
                pl.sayRaw(" <gray>Join to turn your rewards on: ${inviteLink()}")
                pl.sayRaw("")
            }
            check(pl)
            return
        }
        if ((codeCooldown[pl.uniqueId] ?: 0) > System.currentTimeMillis()) return pl.say("<gray>Please wait a few seconds before asking for a new code.")
        codeCooldown[pl.uniqueId] = System.currentTimeMillis() + 10_000
        if (dsrv()?.let { runCatching { call(it, "getJda") }.getOrNull() } == null) return pl.say("<red>Linking isn't available right now (the bot is offline). Please tell a staff member.")
        codes.entries.removeIf { it.value.first == pl.uniqueId || it.value.second < System.currentTimeMillis() }
        var raw: String
        do { raw = (1..5).map { CODE_LETTERS.random() }.joinToString("") } while (codes.putIfAbsent(raw, pl.uniqueId to System.currentTimeMillis() + 15 * 60_000) != null)
        val code = "TAPX-$raw"
        pl.sayRaw("")
        pl.sayRaw(" <#5865f2><b>LINK YOUR DISCORD</b>")
        pl.sayRaw(" <white>1. <gray>Join our Discord: ${inviteLink()}")
        pl.sayRaw(" <white>2. <gray>DM this code to the <#8ea1e1>${bot()} <gray>bot: <click:copy_to_clipboard:'$code'><hover:show_text:'<gray>Click to copy'><b><#ffd54a>$code</#ffd54a></b></hover></click> <dark_gray>(click to copy, works for 15 min)")
        pl.sayRaw(" <dark_gray>This links your TapX account only; DrillSim has its own /link.")
        pl.sayRaw(" <gray>Link your Discord to get:")
        rewardLines(pl)
        pl.sayRaw("")
    }

    /** Reads the link (any thread) and turns the rewards on or off. */
    private fun check(pl: Player) {
        if (!pl.isOnline) return
        val id = discordId(pl.uniqueId)
        val active = id != null && inServer(id)
        val s = plugin.session(pl) ?: return
        s.post {
            if (s.d.discordLinked == active) return@post
            s.d.discordLinked = active
            val first = active && !s.d.discordRewarded
            if (first) {
                s.d.discordRewarded = true
                s.d.gems += LINK_GEMS
                repeat(LINK_EGGS) { s.game.grantEggs(PETS.random().id, 1) }
            }
            plugin.save(s)
            Bukkit.getScheduler().runTask(plugin, Runnable {
                if (active) {
                    pl.say("<green><b>DISCORD LINKED!</b> <gray>Thanks for joining! Your rewards are now active:")
                    rewardLines(pl)
                    pl.playSound(pl.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f)
                    if (first) Bukkit.broadcast(mm("$PREFIX<white>${pl.name} <gray>linked their Discord and unlocked <gold>x$LINK_GOLD gold<gray>, <red>x$LINK_DAMAGE damage <gray>and <aqua>$LINK_GEMS gems<gray>! <dark_gray>(/link)"))
                } else if (id != null) pl.say("<gray>You're no longer in our Discord server, so your <aqua>/link <gray>rewards are off. Rejoin: ${inviteLink()}")
                else pl.say("<gray>Your Discord was unlinked, so your <aqua>/link <gray>rewards are off. Type <aqua>/link <gray>to link again.")
                TabList.refresh(pl)
            })
        }
    }

    // ---------------------------------------------------------------- DMs to the bot
    private fun attachListener() {
        val jda = dsrv()?.let { runCatching { call(it, "getJda") }.getOrNull() } ?: return
        if (jda === listenedJda) return
        runCatching {
            val iface = Class.forName("github.scarsz.discordsrv.dependencies.jda.api.hooks.EventListener", true, jda.javaClass.classLoader)
            val proxy = java.lang.reflect.Proxy.newProxyInstance(iface.classLoader, arrayOf(iface)) { self, m, args ->
                when (m.name) {
                    "onEvent" -> { runCatching { onDiscordEvent(jda, args!![0]) }.onFailure { plugin.logger.warning("Discord DM handling failed: $it") }; null }
                    "hashCode" -> System.identityHashCode(self)
                    "equals" -> self === args?.get(0)
                    "toString" -> "TapX link listener"
                    else -> null
                }
            }
            val arr = java.lang.reflect.Array.newInstance(Any::class.java, 1).also { java.lang.reflect.Array.set(it, 0, proxy) }
            jda.javaClass.methods.first { it.name == "addEventListener" }.invoke(jda, arr)
            listenedJda = jda
            plugin.logger.info("Listening for TapX link codes in the bot's DMs.")
        }.onFailure { plugin.logger.warning("Couldn't listen to the Discord bot's DMs: $it") }
    }

    private fun reply(msg: Any, text: String) = runCatching { call(call(msg, "reply", "$MARK $text")!!, "queue") }

    private fun onDiscordEvent(jda: Any, e: Any) {
        if (e.javaClass.simpleName != "PrivateMessageReceivedEvent") return
        val msg = call(e, "getMessage") ?: return
        val author = call(msg, "getAuthor") ?: return
        val authorId = call(author, "getId") as String
        val content = (call(msg, "getContentRaw") as String).trim()
        val channelId = call(call(msg, "getChannel")!!, "getId") as String
        val selfId = call(call(jda, "getSelfUser")!!, "getId") as String
        if (authorId == selfId) {
            // DrillSim's DiscordSRV answering a TapX code ("Are you sure that's your code?"): remove it
            if (!content.startsWith(MARK) && (cleanup[channelId] ?: 0) > System.currentTimeMillis()) runCatching { call(call(msg, "delete")!!, "queue") }
            return
        }
        if (call(author, "isBot") == true) return
        val norm = content.uppercase().replace(Regex("[^A-Z0-9]"), "")
        if (!norm.startsWith("TAPX")) return // DrillSim's own codes and anything else: not ours
        cleanup[channelId] = System.currentTimeMillis() + 10_000
        val raw = norm.removePrefix("TAPX")
        val entry = codes[raw]?.takeIf { it.second > System.currentTimeMillis() }
            ?: return run { reply(msg, "I don't know that TapX code. Type `/link` on the TapX server to get a new one."); Unit }
        val lm = linkManager() ?: return run { reply(msg, "Linking isn't available right now, please try again later."); Unit }
        val already = runCatching { call(lm, "getUuid", authorId) as UUID? }.getOrNull()
        if (already != null && already != entry.first) {
            val name = Bukkit.getOfflinePlayer(already).name ?: already.toString()
            reply(msg, "This Discord account is already linked to **$name** on TapX. Type `/unlink` on TapX as $name first.")
            return
        }
        codes.remove(raw)
        if (discordId(entry.first) != null) runCatching { call(lm, "unlink", entry.first) }
        call(lm, "link", authorId, entry.first)
        val name = Bukkit.getOfflinePlayer(entry.first).name ?: "your account"
        reply(msg, "Your Discord is now linked to **$name** on TapX! Your rewards turn on in a few seconds. (DrillSim links separately.)")
        Bukkit.getPlayer(entry.first)?.let(::check)
    }

    @EventHandler
    fun onJoin(e: PlayerJoinEvent) {
        if (!available()) return
        val p = e.player
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, Runnable { check(p) }, 60L)
    }

    private fun remind() {
        if (!available()) return
        for (pl in Bukkit.getOnlinePlayers()) {
            if (plugin.session(pl)?.d?.discordLinked == true) continue
            pl.sayRaw("$PREFIX<#5865f2><b>FREE REWARDS</b> <gray>Link your Discord for <gold>x$LINK_GOLD gold<gray>, <red>x$LINK_DAMAGE damage<gray>, an auto tap and <aqua>$LINK_GEMS gems<gray>! " +
                "<click:run_command:'/link'><hover:show_text:'<gray>Click to get your code'><#8ea1e1><b>CLICK HERE</b> <gray>or type <white>/link</hover></click>")
        }
    }
}
