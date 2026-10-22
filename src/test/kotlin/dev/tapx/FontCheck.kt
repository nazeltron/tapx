package dev.tapx

import dev.tapx.screen.GameFont
import dev.tapx.screen.hiRes
import java.awt.Color
import java.io.File
import javax.imageio.ImageIO

/** Renders a few labels with GameFont into build/preview/font-check.png. */
fun main() {
    val (img, g) = hiRes(300, 80, opaque = true)
    g.color = Color(40, 50, 120); g.fillRect(0, 0, 300, 80)
    GameFont.drawOutlined(g, "FREE CHEST", 10, 10, Color.WHITE, 2)
    GameFont.drawOutlined(g, "Level up 1.23B", 10, 40, Color(255, 210, 40), 1)
    GameFont.draw(g, "plain text", 150, 40, Color.WHITE, 1)
    g.dispose()
    File("build/preview").mkdirs()
    ImageIO.write(img, "png", File("build/preview/font-check.png"))
}
