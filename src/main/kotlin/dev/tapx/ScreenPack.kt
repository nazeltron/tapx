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
        if (!plugin.config.getBoolean("hide-crosshair", true) && !uiEnabled()) return
        ChannelInitializeListenerHolder.addListener(listenerKey) { ch -> ch.pipeline().addFirst("tapx_http", HttpSniffer()) }
    }

    fun disable() {
        if (ChannelInitializeListenerHolder.hasListener(listenerKey)) ChannelInitializeListenerHolder.removeListener(listenerKey)
    }

    private fun url(p: Player, ui: Boolean = false): String? {
        plugin.config.getString(if (ui) "ui-pack-url" else "resource-pack-url", "")?.takeIf { it.isNotBlank() }?.let { return it }
        val host = p.virtualHost ?: return null
        val name = host.hostString.trimEnd('.').ifBlank { return null }
        val port = if (host.port > 0) host.port else Bukkit.getPort()
        return "http://${if (':' in name) "[$name]" else name}:$port/tapx/${if (ui) uiHash else hash}.zip"
    }

    private val triedFallback = java.util.concurrent.ConcurrentHashMap.newKeySet<java.util.UUID>()
    private val triedUiFallback = java.util.concurrent.ConcurrentHashMap.newKeySet<java.util.UUID>()

    private fun uiEnabled() = plugin.config.getBoolean("text-animations", true)

    /** The UI pack (animated ranks, names and tab), for everyone from the moment they join. */
    fun sendUi(p: Player) {
        if (!uiEnabled()) return
        triedUiFallback.remove(p.uniqueId)
        val url = url(p, ui = true) ?: return sendUiFallback(p)
        p.addResourcePack(UI_PACK_ID, url, uiSha1, "TapX animates ranks, names and the tab list.", false)
    }

    private fun sendUiFallback(p: Player) {
        val url = plugin.config.getString("ui-pack-fallback-url", DEFAULT_UI_FALLBACK)?.takeIf { it.isNotBlank() } ?: return
        if (!triedUiFallback.add(p.uniqueId)) return
        p.addResourcePack(UI_PACK_ID, url, null, "TapX animates ranks, names and the tab list.", false)
    }

    @org.bukkit.event.EventHandler
    fun onJoin(e: org.bukkit.event.player.PlayerJoinEvent) {
        val p = e.player
        plugin.server.scheduler.runTaskLater(plugin, Runnable { if (p.isOnline) sendUi(p) }, 10L)
    }

    fun send(p: Player) {
        if (!plugin.config.getBoolean("hide-crosshair", true)) return
        triedFallback.remove(p.uniqueId)
        val url = url(p) ?: return sendFallback(p)
        p.addResourcePack(PACK_ID, url, sha1, "TapX hides the crosshair while you play.", false)
    }

    /** Second try from a public copy (GitHub by default), for hosts where the game port can't serve it. */
    private fun sendFallback(p: Player) {
        val url = plugin.config.getString("resource-pack-fallback-url", DEFAULT_FALLBACK)?.takeIf { it.isNotBlank() } ?: return
        if (!triedFallback.add(p.uniqueId)) return
        p.addResourcePack(PACK_ID, url, null, "TapX hides the crosshair while you play.", false)
    }

    @org.bukkit.event.EventHandler
    fun onStatus(e: org.bukkit.event.player.PlayerResourcePackStatusEvent) {
        val p = e.player
        if (e.id == UI_PACK_ID) {
            if (e.status in setOf(org.bukkit.event.player.PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD,
                    org.bukkit.event.player.PlayerResourcePackStatusEvent.Status.INVALID_URL,
                    org.bukkit.event.player.PlayerResourcePackStatusEvent.Status.FAILED_RELOAD) && p.uniqueId !in triedUiFallback)
                plugin.server.scheduler.runTaskLater(plugin, Runnable { if (p.isOnline) sendUiFallback(p) }, 10L)
            return
        }
        if (e.id != PACK_ID || !plugin.arcade.isPlaying(e.player)) return
        when (e.status) {
            org.bukkit.event.player.PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD,
            org.bukkit.event.player.PlayerResourcePackStatusEvent.Status.INVALID_URL,
            org.bukkit.event.player.PlayerResourcePackStatusEvent.Status.FAILED_RELOAD -> {
                if (p.uniqueId !in triedFallback) {
                    plugin.logger.info("${p.name} couldn't download the screen pack from this server (${e.status}); trying the backup link.")
                    plugin.server.scheduler.runTaskLater(plugin, Runnable { if (p.isOnline) sendFallback(p) }, 10L)
                } else plugin.logger.warning("${p.name} couldn't load the screen pack (${e.status}). Set resource-pack-url in the TapX config.")
            }
            org.bukkit.event.player.PlayerResourcePackStatusEvent.Status.DECLINED ->
                p.sendMessage(mm("<gray>Tip: accept the <white>TapX resource pack</white> to hide the crosshair (Multiplayer → Edit server → Server Resource Packs: <green>Enabled</green>)."))
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
