package dev.tapx

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.PluginCommand
import org.bukkit.entity.Player

/** Chat prefix used by every TapX message. */
const val PREFIX = "<gradient:#ffcf3a:#ff5a8a><b>TAPX</b></gradient> <dark_gray>»</dark_gray> "

fun CommandSender.say(text: String) = sendMessage(mm(PREFIX + text))
fun CommandSender.sayRaw(text: String) = sendMessage(mm(text))

/**
 * Commands registered from code (no plugin.yml entry needed). A command replaces a vanilla one with
 * the same name (/msg, /tp ...) but not one from another plugin (EssentialsX keeps its own) unless
 * `commands.override-plugins` is true. Every label is also available as /tapx:<label>.
 */
object Cmds {
    lateinit var plugin: TapXPlugin

    /** The web store address (config store-url). */
    fun storeUrl(): String = if (::plugin.isInitialized) plugin.config.getString("store-url", "")!!.ifBlank { DEFAULT_STORE } else DEFAULT_STORE
    const val DEFAULT_STORE = "https://tapx.paynow.store"

    class Ctx(val sender: CommandSender, val args: List<String>) {
        val player get() = sender as? Player
        fun arg(i: Int) = args.getOrNull(i)
        fun int(i: Int) = args.getOrNull(i)?.toIntOrNull()
        fun num(i: Int) = args.getOrNull(i)?.toDoubleOrNull()
        /** The player named at [i], or the sender if absent; null (and a message) if neither works. */
        fun target(i: Int, others: String? = null): Player? {
            val name = args.getOrNull(i)
            if (name == null) return player ?: run { sender.say("<red>Name a player."); null }
            if (others != null && !sender.hasPermission(others) && name != sender.name) { sender.say("<red>You can't do that to other players."); return null }
            return Bukkit.getPlayer(name) ?: run { sender.say("<red>$name is not online."); null }
        }
        fun needPlayer(): Player? = player ?: run { sender.say("<red>Only players can do that."); null }
    }

    fun register(
        name: String, aliases: List<String> = emptyList(), perm: String? = "tapx.cmd.$name", usage: String = "/$name",
        tab: (Ctx) -> List<String> = { c -> if (c.args.size <= 2) Bukkit.getOnlinePlayers().map { it.name } else emptyList() },
        exec: (Ctx) -> Unit,
    ) {
        val cmd = object : Command(name, "TapX: $usage", usage, aliases) {
            override fun execute(sender: CommandSender, label: String, args: Array<out String>): Boolean {
                if (perm != null && !sender.hasPermission(perm)) { sender.say("<red>You don't have permission to use that."); return true }
                try { exec(Ctx(sender, args.toList())) } catch (e: Exception) {
                    sender.say("<red>Something went wrong: ${e.message}")
                    plugin.logger.warning("/$label failed: $e")
                }
                return true
            }
            override fun tabComplete(sender: CommandSender, alias: String, args: Array<out String>): List<String> {
                if (perm != null && !sender.hasPermission(perm)) return emptyList()
                return tab(Ctx(sender, args.toList())).filter { it.startsWith(args.lastOrNull() ?: "", ignoreCase = true) }
            }
        }
        if (perm != null) cmd.permission = perm
        val map = Bukkit.getCommandMap()
        map.register("tapx", cmd)
        val overridePlugins = plugin.config.getBoolean("commands.override-plugins", false)
        for (label in listOf(name) + aliases) {
            val existing = map.knownCommands[label]
            if (existing === cmd) continue
            if (existing == null || existing !is PluginCommand || overridePlugins) map.knownCommands[label] = cmd
        }
    }
}
