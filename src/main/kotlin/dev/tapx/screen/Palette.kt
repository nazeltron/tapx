package dev.tapx.screen

import net.minecraft.world.level.material.MapColor

/**
 * Converts RGB pixels to Minecraft map colour ids. Maps can only show ~240 colours, so smooth
 * shading would band badly if every pixel just snapped to the closest one. Instead each colour is
 * stored as a mix of two map colours, and a fixed 4x4 pattern (ordered dithering) decides which of
 * the two each pixel gets. The pattern depends only on the pixel position, so still areas stay
 * still (no flicker, no extra bandwidth), and colours that are already map colours stay solid.
 */
object Palette {
    private const val BITS = 6
    private const val N = 1 shl BITS
    private val near = ByteArray(N * N * N)
    private val other = ByteArray(N * N * N)
    private val mix = ByteArray(N * N * N) // 0..16: how many of 16 pattern cells get [other]
    private val BAYER = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)

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
        val pr = DoubleArray(n) { ((rgbs[it] shr 16) and 255).toDouble() }
        val pg = DoubleArray(n) { ((rgbs[it] shr 8) and 255).toDouble() }
        val pb = DoubleArray(n) { (rgbs[it] and 255).toDouble() }
        val max = (N - 1).toDouble()
        for (ri in 0 until N) for (gi in 0 until N) for (bi in 0 until N) {
            val r = ri * 255 / max; val g = gi * 255 / max; val b = bi * 255 / max
            // "redmean" weights: cheap and much closer to human perception than plain RGB
            val wr = 2 + r / 256; val wg = 4.0; val wb = 2 + (255 - r) / 256
            fun dist(x: Double, y: Double, z: Double) = wr * (r - x) * (r - x) + wg * (g - y) * (g - y) + wb * (b - z) * (b - z)
            var c1 = 0
            var d1 = Double.MAX_VALUE
            for (i in 0 until n) { val d = dist(pr[i], pg[i], pb[i]); if (d < d1) { d1 = d; c1 = i } }
            // best second colour: the mix that lands closest, penalising far-apart pairs (they look noisy)
            var c2 = c1; var bestA = 0.0; var bestCost = d1
            for (i in 0 until n) {
                if (i == c1) continue
                val vr = pr[i] - pr[c1]; val vg = pg[i] - pg[c1]; val vb = pb[i] - pb[c1]
                val vv = wr * vr * vr + wg * vg * vg + wb * vb * vb
                if (vv <= 0) continue
                val a = ((wr * (r - pr[c1]) * vr + wg * (g - pg[c1]) * vg + wb * (b - pb[c1]) * vb) / vv).coerceIn(0.0, 1.0)
                if (a <= 0.0) continue
                val cost = dist(pr[c1] + a * vr, pg[c1] + a * vg, pb[c1] + a * vb) + 0.3 * a * (1 - a) * vv
                if (cost < bestCost) { bestCost = cost; bestA = a; c2 = i }
            }
            val k = (ri shl (2 * BITS)) or (gi shl BITS) or bi
            near[k] = ids[c1]
            other[k] = ids[c2]
            mix[k] = Math.round(bestA * 16).toInt().toByte()
        }
    }

    // the top BITS of red, green and blue, side by side
    private fun key(rgb: Int) = ((rgb shr (24 - 3 * BITS)) and ((N - 1) shl (2 * BITS))) or
        ((rgb shr (16 - 2 * BITS)) and ((N - 1) shl BITS)) or ((rgb shr (8 - BITS)) and (N - 1))

    /** The single closest map colour. */
    fun toMap(rgb: Int): Byte = near[key(rgb)]

    /** Convert a whole frame (packed RGB ints, [width] wide) into map colour ids, dithered. */
    fun convert(src: IntArray, dst: ByteArray, width: Int) {
        var i = 0
        val h = src.size / width
        for (y in 0 until h) {
            val row = (y and 3) shl 2
            for (x in 0 until width) {
                val k = key(src[i])
                dst[i] = if (mix[k] > BAYER[row or (x and 3)]) other[k] else near[k]
                i++
            }
        }
    }
}
