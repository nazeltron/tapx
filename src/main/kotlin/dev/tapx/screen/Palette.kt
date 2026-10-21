package dev.tapx.screen

import net.minecraft.world.level.material.MapColor

/**
 * Converts RGB pixels to Minecraft map colour ids. Maps can only show ~240 colours, so every
 * pixel snaps to the closest one through a 32x32x32 lookup table built once at startup.
 */
object Palette {
    private val lut = ByteArray(32 * 32 * 32)

    init {
        val ids = ArrayList<Byte>()
        val rgbs = ArrayList<Int>()
        for (mc in MapColor.MATERIAL_COLORS) {
            if (mc == null || mc.id == 0) continue
            for (b in MapColor.Brightness.entries) {
                ids += mc.getPackedId(b)
                rgbs += mc.calculateARGBColor(b) and 0xFFFFFF
            }
        }
        val n = ids.size
        val pr = IntArray(n) { (rgbs[it] shr 16) and 255 }
        val pg = IntArray(n) { (rgbs[it] shr 8) and 255 }
        val pb = IntArray(n) { rgbs[it] and 255 }
        for (r5 in 0 until 32) for (g5 in 0 until 32) for (b5 in 0 until 32) {
            val r = r5 * 255 / 31
            val g = g5 * 255 / 31
            val b = b5 * 255 / 31
            var best = 0
            var bestD = Long.MAX_VALUE
            for (i in 0 until n) {
                // "redmean" distance: cheap and much closer to human perception than plain RGB
                val rm = (r + pr[i]) / 2
                val dr = r - pr[i]
                val dg = g - pg[i]
                val db = b - pb[i]
                val d = ((512 + rm) * dr * dr shr 8) + 4L * dg * dg + ((767 - rm) * db * db shr 8)
                if (d < bestD) { bestD = d; best = i }
            }
            lut[(r5 shl 10) or (g5 shl 5) or b5] = ids[best]
        }
    }

    fun toMap(rgb: Int): Byte = lut[((rgb shr 9) and 0x7C00) or ((rgb shr 6) and 0x3E0) or ((rgb shr 3) and 0x1F)]

    /** Convert a whole frame (packed RGB ints) into map colour ids. */
    fun convert(src: IntArray, dst: ByteArray) {
        for (i in src.indices) dst[i] = toMap(src[i])
    }
}
