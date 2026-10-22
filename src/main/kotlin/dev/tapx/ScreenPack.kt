package dev.tapx

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.papermc.paper.network.ChannelInitializeListenerHolder
import net.kyori.adventure.key.Key
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerResourcePackStatusEvent.Status
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO

/**
 * TapX's one resource pack, sent to everyone on join (required, so it's offered even to players who
 * join through Minehut). It has:
 *  - the text shader that animates rank tags, names and the tab list ([TextFx]),
 *  - the game's own "bubbly" sound effects (tapx:hit, tapx:coin ..., made by tools/make_sounds.py),
 *  - blank crosshair / XP bar / locator bar sprites for the game screen.
 * Links tried in order: `resource-pack-url` if set, the public GitHub copy when it matches this build
 * (checked at startup), the Minecraft port itself (a connection that starts with "GET " gets the
 * pack; no extra port or web host), then the GitHub copy without a hash check.
 */
class ScreenPack(private val plugin: TapXPlugin) : org.bukkit.event.Listener {
    val zip: ByteArray = buildZip()
    private val sha1: ByteArray = MessageDigest.getInstance("SHA-1").digest(zip)
    private val hash = sha1.joinToString("") { "%02x".format(it) }
    private val listenerKey = Key.key("tapx", "pack")

    /** Players whose client has the pack loaded (they hear the custom sounds). */
    private val loaded = ConcurrentHashMap.newKeySet<UUID>()
    fun hasPack(p: Player) = p.uniqueId in loaded

    fun enable() {
        plugin.dataFolder.mkdirs()
        plugin.dataFolder.resolve("tapx-pack.zip").writeBytes(zip)
        checkGithub()
        if (!enabled()) return
        ChannelInitializeListenerHolder.addListener(listenerKey) { ch -> ch.pipeline().addFirst("tapx_http", HttpSniffer()) }
    }

    fun disable() {
        if (ChannelInitializeListenerHolder.hasListener(listenerKey)) ChannelInitializeListenerHolder.removeListener(listenerKey)
    }

    private fun enabled() = plugin.config.getBoolean("resource-pack", true)

    /** The game-port link (served by [HttpSniffer]); null if the player's address is unknown. */
    private fun portUrl(p: Player): String? {
        val host = p.virtualHost ?: return null
        val name = host.hostString.trimEnd('.').ifBlank { return null }
        val port = if (host.port > 0) host.port else Bukkit.getPort()
        return "http://${if (':' in name) "[$name]" else name}:$port/tapx/$hash.zip"
    }

    /** The public copy; configs written by older versions still point at the old two-pack files. */
    private fun githubUrl(): String? {
        val c = plugin.config.getString("resource-pack-fallback-url", DEFAULT_FALLBACK)!!
        return (if (c in OLD_FALLBACKS) DEFAULT_FALLBACK else c).takeIf { it.isNotBlank() }
    }

    /** Set at startup when the public (GitHub) copy is byte-for-byte this build's pack: then it's the main link. */
    @Volatile private var githubOk = false

    private fun checkGithub() = plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
        githubOk = githubUrl()?.let { url ->
            runCatching {
                val c = java.net.URI(url).toURL().openConnection().apply { connectTimeout = 8000; readTimeout = 15000 }
                MessageDigest.getInstance("SHA-1").digest(c.getInputStream().use { it.readBytes() }).joinToString("") { "%02x".format(it) } == hash
            }.getOrDefault(false)
        } ?: false
        plugin.logger.info("Resource pack: public copy ${if (githubOk) "matches this build, using it as the main download" else "doesn't match this build; using the game port first"}.")
    })

    private fun links(p: Player): List<Pair<String, ByteArray?>> {
        plugin.config.getString("resource-pack-url", "")?.takeIf { it.isNotBlank() }?.let { return listOf(it to null) }
        val gh = githubUrl()
        val port = portUrl(p)
        return buildList {
            if (githubOk && gh != null) add(gh to sha1)
            if (port != null) add(port to sha1)
            if (!githubOk && gh != null) add(gh to null) // unverified copy: no hash check
        }
    }

    /** Which link each player is on (index into [links]). */
    private val attempt = ConcurrentHashMap<UUID, Int>()

    private fun offer(p: Player, index: Int): Boolean {
        val l = links(p).getOrNull(index) ?: return false
        attempt[p.uniqueId] = index
        // "required" makes the client show the pack prompt even when its server entry (e.g. Minehut's)
        // has server resource packs disabled; declining a required pack disconnects the player
        val force = plugin.config.getBoolean("force-resource-packs", true)
        val info = net.kyori.adventure.resource.ResourcePackInfo.resourcePackInfo(PACK_ID, java.net.URI(l.first), l.second?.joinToString("") { "%02x".format(it) } ?: "")
        p.sendResourcePacks(net.kyori.adventure.resource.ResourcePackRequest.resourcePackRequest().packs(info).replace(false).required(force)
            .prompt(mm("<gold><b>TapX</b></gold> <white>needs a tiny resource pack for the game screen, its sounds, animated ranks and the tab list.<newline><gray>It's very small and is removed when you leave.")).build())
        return true
    }

    /** Send the pack (once per join; the game screen doesn't need anything extra). */
    fun send(p: Player) { if (enabled() && !attempt.containsKey(p.uniqueId)) offer(p, 0) }

    @org.bukkit.event.EventHandler
    fun onJoin(e: org.bukkit.event.player.PlayerJoinEvent) {
        val p = e.player
        attempt.remove(p.uniqueId); loaded.remove(p.uniqueId)
        plugin.server.scheduler.runTaskLater(plugin, Runnable { if (p.isOnline) send(p) }, 10L)
    }

    @org.bukkit.event.EventHandler
    fun onQuit(e: org.bukkit.event.player.PlayerQuitEvent) {
        attempt.remove(e.player.uniqueId); loaded.remove(e.player.uniqueId)
    }

    @org.bukkit.event.EventHandler
    fun onStatus(e: org.bukkit.event.player.PlayerResourcePackStatusEvent) {
        if (e.id != PACK_ID) return
        val p = e.player
        val idx = attempt[p.uniqueId] ?: 0
        plugin.logger.info("${p.name}: resource pack ${e.status} (${links(p).getOrNull(idx)?.first ?: "?"})")
        when (e.status) {
            Status.SUCCESSFULLY_LOADED -> loaded += p.uniqueId
            Status.FAILED_DOWNLOAD, Status.INVALID_URL, Status.FAILED_RELOAD, Status.DISCARDED -> {
                loaded -= p.uniqueId
                plugin.server.scheduler.runTaskLater(plugin, Runnable {
                    if (!p.isOnline) return@Runnable
                    if (!offer(p, idx + 1)) plugin.logger.warning("${p.name} couldn't load the TapX resource pack from any link. Check that the client allows server resource packs, or set resource-pack-url.")
                }, 10L)
            }
            Status.DECLINED -> {
                loaded -= p.uniqueId
                p.sendMessage(mm("<gray>Tip: accept the <white>TapX resource pack</white> for the game's sounds, animated ranks and a clean game screen (Multiplayer → Edit server → Server Resource Packs: <green>Enabled</green>)."))
            }
            else -> {}
        }
    }

    /** First handler on every new connection: answers HTTP requests, otherwise steps aside. */
    private inner class HttpSniffer : ChannelInboundHandlerAdapter() {
        override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
            val buf = msg as? ByteBuf
            if (buf == null || buf.readableBytes() < 4 || buf.getByte(buf.readerIndex()) != 'G'.code.toByte() ||
                buf.toString(buf.readerIndex(), 4, Charsets.US_ASCII) != "GET ") {
                ctx.pipeline().remove(this)
                ctx.fireChannelRead(msg)
                return
            }
            val line = buf.toString(Charsets.US_ASCII).lineSequence().first()
            buf.release()
            val path = line.split(' ').getOrNull(1) ?: ""
            val ok = path.startsWith("/tapx/")
            val body = if (ok) zip else "Not found".toByteArray()
            val head = "HTTP/1.1 ${if (ok) "200 OK" else "404 Not Found"}\r\n" +
                "Content-Type: ${if (ok) "application/zip" else "text/plain"}\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
            ctx.writeAndFlush(Unpooled.wrappedBuffer(head.toByteArray(Charsets.US_ASCII), body)).addListener(ChannelFutureListener.CLOSE)
        }
    }

    companion object Builder {
        /** The game's sound effects (src/main/resources/sounds/<name>.ogg), played as tapx:<name>. */
        val SOUNDS = listOf("hit", "crit", "kill", "coin", "boss", "boss_kill", "level_up", "buy", "click", "error", "thunder",
            "spell", "pet", "chest", "legendary", "equip", "wisp", "fail", "achievement")

        /** The pack's bytes (deterministic: same plugin version, same bytes and hash). */
        fun buildZip(): ByteArray {
            val out = ByteArrayOutputStream()
            ZipOutputStream(out).use { z ->
                fun put(name: String, bytes: ByteArray) {
                    z.putNextEntry(ZipEntry(name).apply { time = 0 }) // fixed time: same bytes, same hash, every start
                    z.write(bytes)
                    z.closeEntry()
                }
                put("pack.mcmeta", """{"pack":{"description":"TapX: game screen, sounds, animated ranks","pack_format":75,"min_format":75,"max_format":99}}""".toByteArray())
                // animated rank tags, names and tab list
                put("assets/minecraft/shaders/core/rendertype_text.vsh", TextFx.vertexShader().toByteArray())
                // sounds
                put("assets/tapx/sounds.json", SOUNDS.joinToString(",", "{", "}") { "\"$it\":{\"sounds\":[{\"name\":\"tapx:$it\"}]}" }.toByteArray())
                for (s in SOUNDS) put("assets/tapx/sounds/$s.ogg", ScreenPack::class.java.getResourceAsStream("/sounds/$s.ogg")!!.use { it.readBytes() })
                // no crosshair, XP bar or locator bar on the game screen
                fun blank(w: Int, h: Int) = ByteArrayOutputStream().also { ImageIO.write(BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB), "png", it) }.toByteArray()
                val sprites = "assets/minecraft/textures/gui/sprites/hud/"
                put(sprites + "crosshair.png", blank(15, 15))
                put(sprites + "crosshair_attack_indicator_full.png", blank(16, 16))
                put(sprites + "crosshair_attack_indicator_background.png", blank(16, 4))
                put(sprites + "crosshair_attack_indicator_progress.png", blank(16, 4))
                put(sprites + "experience_bar_background.png", blank(182, 5))
                put(sprites + "experience_bar_progress.png", blank(182, 5))
                put(sprites + "locator_bar_background.png", blank(12, 5))
                put(sprites + "locator_bar_background.png.mcmeta", """{"gui":{"scaling":{"type":"nine_slice","width":12,"height":5,"border":{"left":5,"right":5,"top":1,"bottom":1}}}}""".toByteArray())
                for (n in listOf("locator_bar_arrow_up", "locator_bar_arrow_down")) put("$sprites$n.png", blank(7, 5))
                for (n in 0..3) put(sprites + "locator_bar_dot/default_$n.png", blank(9, 9))
                put(sprites + "locator_bar_dot/bowtie.png", blank(9, 9))
            }
            return out.toByteArray()
        }

        private val PACK_ID: UUID = UUID.fromString("7a9b3c1e-2f4d-4e6a-9b8c-0d1e2f3a4b5c")
        const val DEFAULT_FALLBACK = "https://raw.githubusercontent.com/nazeltron/tapx/claude/blissful-fermi-68xow0/pack/tapx-pack.zip"
        private val OLD_FALLBACKS = setOf(
            "https://raw.githubusercontent.com/nazeltron/tapx/claude/blissful-fermi-68xow0/pack/tapx-screen-pack.zip",
            "https://raw.githubusercontent.com/nazeltron/tapx/claude/blissful-fermi-68xow0/pack/tapx-ui-pack.zip",
        )
    }
}
