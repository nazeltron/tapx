package dev.tapx
import net.minecraft.world.level.material.MapColor
import java.io.File
/** Writes the 64 base map colours as JSON (used by the bot test that rebuilds screens). */
fun main(args: Array<String>) {
    val json = (0 until 64).joinToString(",", "[", "]") { i ->
        val c = MapColor.MATERIAL_COLORS[i]?.col ?: 0
        "[${(c shr 16) and 255},${(c shr 8) and 255},${c and 255}]"
    }
    File(args[0]).writeText(json)
}
