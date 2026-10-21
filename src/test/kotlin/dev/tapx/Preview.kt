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
        screen.overlayCursor(ids)
        val img = BufferedImage(896, 512, BufferedImage.TYPE_INT_RGB)
        for (i in ids.indices) {
            img.setRGB(i % 896, i / 896, MapColor.getColorFromPackedId(ids[i].toInt() and 255))
        }
        ImageIO.write(img, "png", File(out, "$name.png"))
        ImageIO.write(screen.image, "png", File(out, "$name-raw.png"))
    }
    fun step(n: Int) = repeat(n) { game.update(0.1); screen.update(0.1) }
    data.screenZoom = 1.0
    data.gems = 2400
    step(3)
    repeat(4) { game.tap(false); step(1) }
    shot("battle-hero")
    screen.mouse(-12.5, 16.0); shot("hover-monster")
    screen.mouse(12.5, -16.0)
    // mouse helpers: the cursor starts in the middle; 7 screen pixels per degree of head turn
    var cx = 448.0; var cy = 256.0
    fun pointAt(x: Int, y: Int) { screen.mouse((x - cx) / 7, (y - cy) / 7); cx = x.toDouble(); cy = y.toDouble(); screen.render() }
    fun clickAt(x: Int, y: Int) { pointAt(x, y); screen.click(); step(1) }
    fun tab(i: Int) = clickAt(512 + 6 + i * 46 + 22, 6 + 23)
    tab(1); shot("allies")
    tab(2); pointAt(820, 140); shot("skills")
    clickAt(840, 140); clickAt(840, 140); shot("skills-bought")
    tab(6); pointAt(830, 300); shot("quests")
    tab(7); shot("shop")
    repeat(8) { clickAt(888 - 30, 6 + 88 + 10) }; pointAt(820, 300); shot("shop-scrolled")
    repeat(30) { clickAt(888 - 30, 6 + 88 + 10) }; pointAt(820, 300); shot("shop-skins")
    clickAt(840, 300); shot("skin-bought")
    data.stage = 25; data.farming = false; data.wave = WAVES; game.monster = null; step(8); shot("titan-lord")
    data.stage = 65; data.wave = 2; game.monster = null; step(8); shot("ocean")
    data.stage = 75; game.monster = null; step(8); shot("sky")
    screen.calibrate(); step(1); shot("calibrate")
    screen.input(false, false, false, false, false, true, false, false); step(1)
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
    // every character side by side at double size
    run {
        val img = BufferedImage(1200, 520, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = java.awt.Color(0x8ab4d8); g.fillRect(0, 0, 1200, 520)
        val looks = HEROES.map { it.look } + listOf(HERO_LOOK) + SKINS.map { it.apply(HERO_LOOK) }
        looks.forEachIndexed { i, l -> dev.tapx.screen.Art.drawChibi(g, l, 60.0 + (i % 12) * 95.0, 200.0 + (i / 12) * 240.0, 2.0, 0.3, 0.0) }
        dev.tapx.screen.Art.drawPet(g, PETS[0], 1100.0, 420.0, 2.0, 0.3)
        g.dispose()
        val ids2 = ByteArray(1200 * 520); val px = IntArray(1200 * 520); img.getRGB(0, 0, 1200, 520, px, 0, 1200)
        Palette.convert(px, ids2)
        for (i in ids2.indices) img.setRGB(i % 1200, i / 1200, MapColor.getColorFromPackedId(ids2[i].toInt() and 255))
        ImageIO.write(img, "png", File(out, "characters.png"))
    }
    // how long one full frame takes (render + palette), for picking the fps
    data.stage = 34; game.monster = null; step(10)
    repeat(300) { step(1); screen.render(); Palette.convert(screen.pixels, ids) }
    var tr = 0L; var tc = 0L; var tu = 0L
    repeat(300) {
        val a = System.nanoTime(); step(1); val b = System.nanoTime(); screen.render(); val c = System.nanoTime(); Palette.convert(screen.pixels, ids)
        tu += b - a; tr += c - b; tc += System.nanoTime() - c
    }
    println("frame cost: update %.2f ms, render %.2f ms, palette %.2f ms".format(tu / 300 / 1e6, tr / 300 / 1e6, tc / 300 / 1e6))
    println("Wrote previews to ${out.absolutePath}")
}
