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
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO

/**
 * Two tiny resource packs. The UI pack (everyone, from join) has the text shader that animates rank
 * tags, names and the tab list in tab and chat ([TextFx]). The screen pack, only for players on the
 * game screen, blanks the crosshair, XP bar and
 * locator bar (the only HUD left once players aren't in spectator mode). It is served over plain
 * HTTP on the Minecraft port itself: a connection that starts with "GET " gets the pack instead of the game,
 * so no extra port or web host is needed. `resource-pack-url` in the config overrides this (for
 * proxies, or to host it elsewhere: the file is saved as plugins/TapX/tapx-screen-pack.zip).
 */
class ScreenPack(private val plugin: TapXPlugin) : org.bukkit.event.Listener {
    val zip: ByteArray = buildZip()
    private val sha1: ByteArray = MessageDigest.getInstance("SHA-1").digest(zip)
    private val hash = sha1.joinToString("") { "%02x".format(it) }
    val uiZip: ByteArray = buildUiZip()
    private val uiSha1: ByteArray = MessageDigest.getInstance("SHA-1").digest(uiZip)
    private val uiHash = uiSha1.joinToString("") { "%02x".format(it) }
    private val listenerKey = Key.key("tapx", "pack")

    fun enable() {
        plugin.dataFolder.mkdirs()
        plugin.dataFolder.resolve("tapx-screen-pack.zip").writeBytes(zip)
        plugin.dataFolder.resolve("tapx-ui-pack.zip").writeBytes(uiZip)
        checkGithub()
        if (!plugin.config.getBoolean("hide-crosshair", true) && !uiEnabled()) return
        ChannelInitializeListenerHolder.addListener(listenerKey) { ch -> ch.pipeline().addFirst("tapx_http", HttpSniffer()) }
    }

    fun disable() {
        if (ChannelInitializeListenerHolder.hasListener(listenerKey)) ChannelInitializeListenerHolder.removeListener(listenerKey)
    }

    /** The game-port link (served by [HttpSniffer]); null if the player's address is unknown. */
    private fun portUrl(p: Player, ui: Boolean): String? {
        val host = p.virtualHost ?: return null
        val name = host.hostString.trimEnd('.').ifBlank { return null }
        val port = if (host.port > 0) host.port else Bukkit.getPort()
        return "http://${if (':' in name) "[$name]" else name}:$port/tapx/${if (ui) uiHash else hash}.zip"
    }

    private fun githubUrl(ui: Boolean) =
        plugin.config.getString(if (ui) "ui-pack-fallback-url" else "resource-pack-fallback-url", if (ui) DEFAULT_UI_FALLBACK else DEFAULT_FALLBACK)!!.takeIf { it.isNotBlank() }

    /** Set at startup when the public (GitHub) copy is byte-for-byte this build's pack: then it's the main link. */
    @Volatile private var githubOk = false
    @Volatile private var githubUiOk = false

    /** Download the public copies once and compare them with this build's packs. */
    private fun checkGithub() = plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
        fun same(url: String?, want: String) = url != null && runCatching {
            val c = java.net.URI(url).toURL().openConnection().apply { connectTimeout = 8000; readTimeout = 15000 }
            val got = MessageDigest.getInstance("SHA-1").digest(c.getInputStream().use { it.readBytes() }).joinToString("") { "%02x".format(it) }
            got == want
        }.getOrDefault(false)
        githubOk = same(githubUrl(false), hash)
        githubUiOk = same(githubUrl(true), uiHash)
        plugin.logger.info("Resource packs: public copies ${if (githubOk && githubUiOk) "match this build, using them as the main download" else "don't match this build (screen ${githubOk}, ui ${githubUiOk}); using the game port first"}.")
    })

    /** Links to try, in order: config override, then the verified public copy or the game port, then the other one. */
    private fun links(p: Player, ui: Boolean): List<Pair<String, ByteArray?>> {
        val sha = if (ui) uiSha1 else sha1
        plugin.config.getString(if (ui) "ui-pack-url" else "resource-pack-url", "")?.takeIf { it.isNotBlank() }?.let { return listOf(it to null) }
        val gh = githubUrl(ui)
        val port = portUrl(p, ui)
        val ok = if (ui) githubUiOk else githubOk
        return buildList {
            if (ok && gh != null) add(gh to sha)
            if (port != null) add(port to sha)
            if (!ok && gh != null) add(gh to null) // unverified copy: no hash check
        }
    }

    /** Which link each player is on, per pack (index into [links]). */
    private val attempt = java.util.concurrent.ConcurrentHashMap<Pair<UUID, Boolean>, Int>()

    private fun uiEnabled() = plugin.config.getBoolean("text-animations", true)

    private fun offer(p: Player, ui: Boolean, index: Int): Boolean {
        val l = links(p, ui).getOrNull(index) ?: return false
        attempt[p.uniqueId to ui] = index
        // "required" makes the client show the pack prompt even when its server entry (e.g. Minehut's)
        // has server resource packs disabled; declining a required pack disconnects the player
        val force = plugin.config.getBoolean("force-resource-packs", true)
        val info = net.kyori.adventure.resource.ResourcePackInfo.resourcePackInfo(if (ui) UI_PACK_ID else PACK_ID, java.net.URI(l.first),
            l.second?.joinToString("") { "%02x".format(it) } ?: "")
        p.sendResourcePacks(net.kyori.adventure.resource.ResourcePackRequest.resourcePackRequest().packs(info).replace(false).required(force).prompt(mm(if (ui) "<gold><b>TapX</b></gold> <white>needs a tiny resource pack for the game screen, animated ranks and the tab list.<newline><gray>It's very small and is removed when you leave."
            else "<gold><b>TapX</b></gold> <white>needs a tiny resource pack for the game screen.")).build())
        return true
    }

    /** The UI pack (animated ranks, names and tab), for everyone from the moment they join. */
    fun sendUi(p: Player) { if (uiEnabled()) offer(p, true, 0) }

    /** The screen pack (no crosshair / XP bar), while on the game screen. */
    fun send(p: Player) { if (plugin.config.getBoolean("hide-crosshair", true)) offer(p, false, 0) }

    @org.bukkit.event.EventHandler
    fun onJoin(e: org.bukkit.event.player.PlayerJoinEvent) {
        val p = e.player
        plugin.server.scheduler.runTaskLater(plugin, Runnable { if (p.isOnline) sendUi(p) }, 10L)
    }

    @org.bukkit.event.EventHandler
    fun onStatus(e: org.bukkit.event.player.PlayerResourcePackStatusEvent) {
        val p = e.player
        val ui = when (e.id) { UI_PACK_ID -> true; PACK_ID -> false; else -> return }
        val which = if (ui) "UI pack" else "screen pack"
        val idx = attempt[p.uniqueId to ui] ?: 0
        val link = links(p, ui).getOrNull(idx)?.first ?: "?"
        plugin.logger.info("${p.name}: $which ${e.status} ($link)")
        if (!ui && !plugin.arcade.isPlaying(p)) return
        when (e.status) {
            org.bukkit.event.player.PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD,
            org.bukkit.event.player.PlayerResourcePackStatusEvent.Status.INVALID_URL,
            org.bukkit.event.player.PlayerResourcePackStatusEvent.Status.FAILED_RELOAD,
            org.bukkit.event.player.PlayerResourcePackStatusEvent.Status.DISCARDED -> {
                plugin.server.scheduler.runTaskLater(plugin, Runnable {
                    if (!p.isOnline) return@Runnable
                    if (!offer(p, ui, idx + 1)) plugin.logger.warning("${p.name} couldn't load the TapX $which from any link. Check that the client allows server resource packs, or set ${if (ui) "ui-pack-url" else "resource-pack-url"}.")
                }, 10L)
            }
            org.bukkit.event.player.PlayerResourcePackStatusEvent.Status.DECLINED ->
                p.sendMessage(mm("<gray>Tip: accept the <white>TapX resource packs</white> for animated ranks and a clean game screen (Multiplayer → Edit server → Server Resource Packs: <green>Enabled</green>)."))
            else -> {}
        }
    }

    fun remove(p: Player) {
        if (p.isOnline) p.removeResourcePack(PACK_ID)
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
            val body = if (!ok) "Not found".toByteArray() else if (uiHash in path) uiZip else zip
            val head = "HTTP/1.1 ${if (ok) "200 OK" else "404 Not Found"}\r\n" +
                "Content-Type: ${if (ok) "application/zip" else "text/plain"}\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
            ctx.writeAndFlush(Unpooled.wrappedBuffer(head.toByteArray(Charsets.US_ASCII), body)).addListener(ChannelFutureListener.CLOSE)
        }
    }

    companion object Builder {
        /** The pack's bytes (deterministic: same plugin version, same bytes and hash). */
        fun buildZip(): ByteArray = build()

        /** The UI pack: the animated text shader (see [TextFx]). */
        fun buildUiZip(): ByteArray {
            val out = ByteArrayOutputStream()
            ZipOutputStream(out).use { z ->
                fun put(name: String, bytes: ByteArray) { z.putNextEntry(ZipEntry(name).apply { time = 0 }); z.write(bytes); z.closeEntry() }
                put("pack.mcmeta", """{"pack":{"description":"TapX animated ranks and tab","pack_format":75,"min_format":75,"max_format":99}}""".toByteArray())
                put("assets/minecraft/shaders/core/rendertype_text.vsh", TextFx.vertexShader().toByteArray())
            }
            return out.toByteArray()
        }

        private fun build(): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            fun put(name: String, bytes: ByteArray) {
                z.putNextEntry(ZipEntry(name).apply { time = 0 }) // fixed time: same bytes, same hash, every start
                z.write(bytes)
                z.closeEntry()
            }
            put("pack.mcmeta", """{"pack":{"description":"TapX game screen","pack_format":75,"min_format":75,"max_format":99}}""".toByteArray())
            fun blank(w: Int, h: Int) = ByteArrayOutputStream().also { ImageIO.write(BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB), "png", it) }.toByteArray()
            val sprites = "assets/minecraft/textures/gui/sprites/hud/"
            put(sprites + "crosshair.png", blank(15, 15))
            put(sprites + "crosshair_attack_indicator_full.png", blank(16, 16))
            put(sprites + "crosshair_attack_indicator_background.png", blank(16, 4))
            put(sprites + "crosshair_attack_indicator_progress.png", blank(16, 4))
            put(sprites + "experience_bar_background.png", blank(182, 5))
            put(sprites + "experience_bar_progress.png", blank(182, 5))
            // the locator bar (other players' positions) shows in the same spot
            put(sprites + "locator_bar_background.png", blank(12, 5))
            put(sprites + "locator_bar_background.png.mcmeta", """{"gui":{"scaling":{"type":"nine_slice","width":12,"height":5,"border":{"left":5,"right":5,"top":1,"bottom":1}}}}""".toByteArray())
            for (n in listOf("locator_bar_arrow_up", "locator_bar_arrow_down")) put("$sprites$n.png", blank(7, 5))
            for (n in 0..3) put(sprites + "locator_bar_dot/default_$n.png", blank(9, 9))
            put(sprites + "locator_bar_dot/bowtie.png", blank(9, 9))
        }
        return out.toByteArray()
    }

        private val PACK_ID: UUID = UUID.fromString("7a9b3c1e-2f4d-4e6a-9b8c-0d1e2f3a4b5c")
        private val UI_PACK_ID: UUID = UUID.fromString("7a9b3c1e-2f4d-4e6a-9b8c-0d1e2f3a4b5d")
        const val DEFAULT_UI_FALLBACK = "https://raw.githubusercontent.com/nazeltron/tapx/claude/blissful-fermi-68xow0/pack/tapx-ui-pack.zip"
        const val DEFAULT_FALLBACK = "https://raw.githubusercontent.com/nazeltron/tapx/claude/blissful-fermi-68xow0/pack/tapx-screen-pack.zip"
    }
}
