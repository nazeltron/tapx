package dev.tapx

import dev.tapx.screen.Res
import dev.tapx.screen.blit
import java.awt.image.BufferedImage

fun main() {
    Res.k = 2
    val dst = BufferedImage(1792, 1024, BufferedImage.TYPE_INT_RGB)
    val g = dst.createGraphics(); g.scale(2.0, 2.0)
    val g1 = dst.createGraphics()
    val spr = BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB)
    spr.createGraphics().apply { color = java.awt.Color(255, 0, 0, 128); fillOval(0, 0, 200, 200) }
    val opaque = BufferedImage(1000, 1000, BufferedImage.TYPE_INT_RGB)
    fun t(name: String, n: Int, f: () -> Unit) { repeat(200) { f() }; val a = System.nanoTime(); repeat(n) { f() }; println("$name %.3f ms".format((System.nanoTime() - a) / n / 1e6)) }
    t("blit argb 200", 2000) { g.blit(spr, 10, 10) }
    t("drawImage identity argb 200", 2000) { g1.drawImage(spr, 20, 20, null) }
    t("blit rgb 1000", 500) { g.blit(opaque, 0, 0) }
    t("drawImage identity rgb 1000", 500) { g1.drawImage(opaque, 0, 0, null) }
}
