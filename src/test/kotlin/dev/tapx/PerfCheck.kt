package dev.tapx

import dev.tapx.screen.GameScreen
import dev.tapx.screen.Palette
import dev.tapx.screen.ScreenHost
import dev.tapx.screen.Sfx
import dev.tapx.screen.TopEntry

/**
 * Plays a fresh game for [minutes] (default 15) at 20 fps with 5 taps a second and prints, every
 * minute, the frame cost, heap use and the size of the screen's collections, to catch slowdowns
 * that build up over time. ./gradlew perfCheck
 */
fun main(args: Array<String>) {
    val minutes = args.getOrNull(0)?.toInt() ?: 15
    val data = PlayerData().apply { gold = 1e4; gems = 100 }
    lateinit var screen: GameScreen
    val events = object : GameEvents {
        override fun onTap(damage: Double, crit: Boolean, auto: Boolean) = screen.onTap(damage, crit, auto)
        override fun onKill(m: Monster, gold: Double, gems: Int) = screen.onKill(m, gold, gems)
        override fun onSpawn(m: Monster) = screen.onSpawn(m)
    }
    val game = Game(data, events)
    val host = object : ScreenHost {
        override val game = game
        override val playerName = "Steve"
        override val version = "perf"
        override fun sfx(s: Sfx) {}
        override fun exit() {}
        override fun leaderboard() = emptyList<TopEntry>()
        override fun resetProgress() {}
        override val allowExit = false
        override fun setZoom(zoom: Double) {}
    }
    screen = GameScreen(host, 896, 512)
    data.tutorial = -1
    val pw = screen.pw
    val ids = ByteArray(pw * screen.ph)
    val prev = IntArray(pw * screen.ph) { -1 }
    val dirty = BooleanArray(pw / 128 * (screen.ph / 128) * 8)
    val dt = 0.05
    var tapAcc = 0.0
    fun sizes(): String = GameScreen::class.java.declaredFields.mapNotNull { f ->
        f.isAccessible = true
        when (val v = runCatching { f.get(screen) }.getOrNull()) { is Collection<*> -> "${f.name}=${v.size}"; is Map<*, *> -> "${f.name}=${v.size}"; else -> null }
    }.filter { !it.endsWith("=0") }.joinToString(" ")
    for (min in 1..minutes) {
        var render = 0L; var conv = 0L; var changed = 0L
        repeat(1200) {
            tapAcc += 5 * dt
            while (tapAcc >= 1) { tapAcc--; game.tap(false) }
            game.update(dt); screen.update(dt)
            if (it % 20 == 0) { // buy upgrades like a player would
                while (game.d.gold >= game.tapCost(game.d.tapLevel)) game.buyTap()
                HEROES.forEachIndexed { i, h -> if (game.heroUnlocked(i) && game.d.gold >= game.heroCost(h, game.heroLvl(h.id))) game.buyHero(h.id) }
            }
            val a = System.nanoTime(); screen.render(); val b = System.nanoTime()
            changed += Palette.convertChanged(screen.pixels, prev, ids, pw, dirty); dirty.fill(false)
            render += b - a; conv += System.nanoTime() - b
        }
        System.gc(); Thread.sleep(200)
        val rt = Runtime.getRuntime()
        println("min %2d stage %3d | render %.2f ms, palette %.2f ms, changed px %d | heap %d MB | %s".format(
            min, data.stage, render / 1200 / 1e6, conv / 1200 / 1e6, changed / 1200, (rt.totalMemory() - rt.freeMemory()) / 1048576, sizes()))
    }
}
