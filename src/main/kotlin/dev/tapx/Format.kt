package dev.tapx

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

private val SUFFIXES = listOf("", "K", "M", "B", "T", "Qa", "Qi", "Sx", "Sp", "Oc", "No", "Dc")

/** Short number format: 1234 -> 1.23K, 5e15 -> 5.00Qa, beyond Dc -> aa, ab, ... */
fun fmt(n: Double): String {
    if (n.isNaN()) return "0"
    if (n.isInfinite()) return "∞"
    if (n < 0) return "-" + fmt(-n)
    if (n < 1000) {
        if (n < 10 && n % 1.0 != 0.0) return (floor(n * 10) / 10).toString()
        return floor(n).toLong().toString()
    }
    val e = floor(log10(n) / 3).toInt()
    var v = n / 1000.0.pow(e)
    val s = if (e < SUFFIXES.size) SUFFIXES[e] else {
        val i = e - SUFFIXES.size
        "${'a' + (i / 26) % 26}${'a' + i % 26}"
    }
    val d = if (v >= 100) 0 else if (v >= 10) 1 else 2
    val p = 10.0.pow(d)
    v = floor(v * p) / p
    return "%.${d}f".format(v) + s
}

fun fmt(n: Long) = fmt(n.toDouble())
fun fmt(n: Int) = fmt(n.toDouble())

fun fmtTime(seconds: Double): String {
    val s = maxOf(0L, seconds.toLong())
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return when {
        h > 0 -> "${h}h ${"%02d".format(m)}m"
        m > 0 -> "$m:${"%02d".format(sec)}"
        else -> "${sec}s"
    }
}

private val MM = MiniMessage.miniMessage()

/** MiniMessage text with the default item italics turned off. */
fun mm(s: String): Component = MM.deserialize("<!i>$s")

const val GOLD = "<#ffcf3a>●</#ffcf3a>"
const val GEM = "<aqua>♦</aqua>"
const val RELIC = "<red>❤</red>"
