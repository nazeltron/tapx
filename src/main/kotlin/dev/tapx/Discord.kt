package dev.tapx

import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Discord linking through DiscordSRV (like DrillSim): /link gives a code to DM the server's bot.
 * Linked players get x1.25 gold forever and a one-time gift of pet eggs. Talks to DiscordSRV by
 * reflection, so TapX doesn't need it to start; without it /link explains what's missing.
 */
object Discord {
    private lateinit var plugin: TapXPlugin
    private val unlinkConfirm = ConcurrentHashMap<UUID, Long>()
    private val codeCooldown = ConcurrentHashMap<UUID, Long>()

    fun available() = Bukkit.getPluginManager().isPluginEnabled("DiscordSRV")

    private fun linkManager(): Any? = runCatching {
        val cls = Class.forName("github.scarsz.discordsrv.DiscordSRV")
        val inst = cls.getMethod("getPlugin").invoke(null)
        inst.javaClass.getMethod("getAccountLinkManager").invoke(inst)
    }.getOrNull()

    private fun discordId(uuid: UUID): String? = runCatching {
        linkManager()?.let { it.javaClass.getMethod("getDiscordId", UUID::class.java).invoke(it, uuid) as String? }
    }.getOrNull()

    private fun invite() = plugin.config.getString("discord.invite", "")!!.ifBlank { "https://discord.gg/" }

    fun enable(p: TapXPlugin) {
        plugin = p
        Cmds.register("discord", listOf("dc", "disc"), perm = null, usage = "/discord", tab = { emptyList() }) { c ->
            c.sender.sayRaw("")
            c.sender.sayRaw(" <#5865f2><b>DISCORD</b> <dark_gray>» <click:open_url:'${invite()}'><white><u>${invite()}</u></white></click>")
            c.sender.sayRaw(" <gray>Link your account with <aqua>/link</aqua> for <gold>x1.25 gold forever</gold> <gray>and <green>5 free pet eggs</green>!")
            c.sender.sayRaw("")
        }
        Cmds.register("link", listOf("linkdiscord", "discordlink", "verify"), perm = null, usage = "/link", tab = { emptyList() }) { c ->
            val pl = c.needPlayer() ?: return@register
            if (!available()) return@register pl.say("<red>Discord linking isn't set up on this server yet (it needs the DiscordSRV plugin).")
            Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                val id = discordId(pl.uniqueId)
                if (id != null) return@Runnable pl.say("<green>Your Discord is linked! <gray>You get <gold>x1.25 gold</gold> on every kill.")
                if ((codeCooldown[pl.uniqueId] ?: 0) > System.currentTimeMillis()) return@Runnable pl.say("<red>Wait a few seconds before asking for another code.")
                codeCooldown[pl.uniqueId] = System.currentTimeMillis() + 10_000
                val code = runCatching { linkManager()?.let { it.javaClass.getMethod("generateCode", UUID::class.java).invoke(it, pl.uniqueId) as String } }.getOrNull()
                    ?: return@Runnable pl.say("<red>Couldn't get a link code from DiscordSRV. Ask staff.")
                pl.sayRaw("")
                pl.sayRaw(" <#5865f2><b>LINK YOUR DISCORD</b>")
                pl.sayRaw(" <gray>1) Join: <click:open_url:'${invite()}'><aqua><u>${invite()}</u></aqua></click>")
                pl.sayRaw(" <gray>2) DM this code to our bot: <click:copy_to_clipboard:'$code'><hover:show_text:'Click to copy'><yellow><b>$code</b></yellow></hover></click>")
                pl.sayRaw(" <gray>Rewards: <gold>x1.25 gold forever</gold><gray>, <green>5 pet eggs</green>")
                pl.sayRaw("")
            })
        }
        Cmds.register("unlink", listOf("unlinkdiscord"), perm = null, usage = "/unlink", tab = { emptyList() }) { c ->
            val pl = c.needPlayer() ?: return@register
            if (!available()) return@register pl.say("<red>Discord linking isn't set up on this server.")
            if ((unlinkConfirm[pl.uniqueId] ?: 0) < System.currentTimeMillis()) {
                unlinkConfirm[pl.uniqueId] = System.currentTimeMillis() + 10_000
                return@register pl.say("<yellow>Unlinking removes your Discord rewards. Type <white>/unlink</white> again within 10 seconds to confirm.")
            }
            unlinkConfirm.remove(pl.uniqueId)
            Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                runCatching { linkManager()?.let { it.javaClass.getMethod("unlink", UUID::class.java).invoke(it, pl.uniqueId) } }
                pl.say("<gray>Your Discord was unlinked.")
            })
        }
        // check links in the background; reward the first link
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, Runnable { if (available()) checkAll() }, 200L, 400L)
        Bukkit.getScheduler().runTaskTimer(plugin, Runnable { remind() }, 20L * 600, 20L * 600)
    }

    private fun checkAll() {
        for (pl in Bukkit.getOnlinePlayers()) {
            val linked = discordId(pl.uniqueId) != null
            val s = plugin.session(pl) ?: continue
            s.post {
                if (s.d.discordLinked == linked) return@post
                s.d.discordLinked = linked
                val first = linked && !s.d.discordRewarded
                if (first) { s.d.discordRewarded = true; repeat(5) { s.game.grantEggs(PETS.random().id, 1) } }
                plugin.save(s)
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    if (linked) {
                        pl.say("<green><b>DISCORD LINKED!</b> <white>You now get <gold>x1.25 gold</gold>${if (first) " and <green>5 pet eggs</green>" else ""}. Thanks!")
                        pl.playSound(pl.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f)
                        if (first) Bukkit.broadcast(mm("$PREFIX<white>${pl.name} <gray>linked their Discord and unlocked <gold>x1.25 gold</gold>! <dark_gray>(/link)"))
                    } else pl.say("<gray>Your Discord isn't linked any more, so the Discord rewards are off. <aqua>/link")
                    TabList.refresh(pl)
                })
            }
        }
    }

    private fun remind() {
        if (!available()) return
        for (pl in Bukkit.getOnlinePlayers()) {
            if (plugin.session(pl)?.d?.discordLinked == true) continue
            pl.sayRaw("$PREFIX<#5865f2><b>FREE REWARDS</b> <gray>Link your Discord for <gold>x1.25 gold forever</gold>! <click:run_command:'/link'><aqua><b>» CLICK HERE</b></aqua></click>")
        }
    }
}
