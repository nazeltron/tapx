package dev.tapx

import dev.tapx.screen.GameScreen
import dev.tapx.screen.Palette
import dev.tapx.screen.ScreenHost
import dev.tapx.screen.Sfx
import dev.tapx.screen.TopEntry
import net.minecraft.world.level.material.MapColor
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Renders the game screen to PNG files exactly as players will see it (after snapping to the
 * map colour palette). Run with: ./gradlew preview (writes PNGs to build/preview)
 */
fun main(args: Array<String>) {
    val out = File(args.getOrElse(0) { "build/preview" }).apply { mkdirs() }
    val data = PlayerData().apply {
        gold = 1.23e9; gems = 640; relics = 37; stage = 34; maxStage = 41; wave = 5; tapLevel = 180
        HEROES.take(7).forEachIndexed { i, h -> heroes[h.id] = 120 - i * 15 }
        skills["thunder"] = 3; skills["frenzy"] = 2; skills["warcry"] = 1; skills["midas"] = 1
        artifacts["blade"] = 3; artifacts["idol"] = 2
        gear["w_thunder"] = 2; gear["h_horn"] = 1; gear["a_royal"] = 1; gear["u_flame"] = 3; gear["w_iron"] = 1
        equipped["weapon"] = "w_thunder"; equipped["helm"] = "h_horn"; equipped["armor"] = "a_royal"; equipped["aura"] = "u_flame"
        pets["nova"] = PetData().apply { lvl = 3 }; activePet = "nova"; petCharge = 9
        mana = 38.0; buffs["warcry"] = 18.0
    }
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
        override val version = "preview"
        override fun sfx(s: Sfx) {}
        override fun exit() {}
        override fun leaderboard() = listOf(TopEntry(java.util.UUID.randomUUID(), "Steve", 41, 2), TopEntry(java.util.UUID.randomUUID(), "Alex", 33, 1))
        override fun resetProgress() {}
        override val allowExit = false
        override fun setZoom(zoom: Double) {}
    }
    screen = GameScreen(host, 896, 512)
    val ids = ByteArray(896 * 512)
    fun shot(name: String) {
        screen.render()
        Palette.convert(screen.pixels, ids)
        val img = BufferedImage(896, 512, BufferedImage.TYPE_INT_RGB)
        for (i in ids.indices) {
            img.setRGB(i % 896, i / 896, MapColor.getColorFromPackedId(ids[i].toInt() and 255))
        }
        ImageIO.write(img, "png", File(out, "$name.png"))
        ImageIO.write(screen.image, "png", File(out, "$name-raw.png"))
    }
    fun step(n: Int) = repeat(n) { game.update(0.1); screen.update(0.1) }
    data.screenZoom = 1.0
    step(3)
    repeat(4) { game.tap(false); step(1) }
    shot("battle-hero")
    // keyboard helpers: press and release a key (forward, back, left, right, jump, sneak)
    fun key(k: Int) {
        val on = BooleanArray(6); on[k] = true
        screen.render(); screen.input(on[0], on[1], on[2], on[3], on[4], on[5], false, false)
        screen.input(false, false, false, false, false, false, on[4], on[5]); step(1)
    }
    val W = 0; val S = 1; val A = 2; val D = 3; val SPACE = 4; val SHIFT = 5
    key(SHIFT)                     // jump to the Hero tab button
    key(D); key(SPACE); shot("allies")
    key(D); key(SPACE); key(S); key(S); shot("gear")
    key(SHIFT); key(D); key(D); key(SPACE); shot("pets")
    key(D); key(SPACE); key(S); key(S); key(S); key(S); key(S); key(S); key(S); shot("artifacts-scrolled")
    key(SHIFT); key(D); key(SPACE); shot("shop")
    data.stage = 45; game.monster = null; step(8); shot("lava-world")
    data.farming = false; data.wave = WAVES; game.monster = null; step(8); shot("boss")
    screen.calibrate(); step(1); shot("calibrate")
    key(SHIFT)
    // Which pixels change between idle frames? (red = changed) -> bandwidth hot spots
    data.stage = 3; game.monster = null; step(20)
    val diff = BufferedImage(896, 512, BufferedImage.TYPE_INT_RGB)
    var changed = 0
    repeat(10) {
        screen.render(); Palette.convert(screen.pixels, ids); val a = ids.copyOf()
        step(1); screen.render(); Palette.convert(screen.pixels, ids)
        for (i in ids.indices) if (ids[i] != a[i]) { diff.setRGB(i % 896, i / 896, 0xff0000); changed++ }
    }
    println("avg changed pixels per idle frame: ${changed / 10}")
    ImageIO.write(diff, "png", File(out, "idle-diff.png"))
    println("Wrote previews to ${out.absolutePath}")
}
