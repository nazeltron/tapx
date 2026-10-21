package dev.tapx

import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.title.Title
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.entity.Player
import java.time.Duration
import kotlin.random.Random

enum class Sfx(val sound: Sound, val volume: Float, val pitch: Float) {
    HIT(Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.35f, 1.4f),
    CRIT(Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.6f, 1.0f),
    KILL(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f),
    BOSS(Sound.ENTITY_ENDER_DRAGON_GROWL, 0.35f, 1.3f),
    BOSS_KILL(Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.2f),
    LEVEL_UP(Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.0f),
    BUY(Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.6f),
    CLICK(Sound.UI_BUTTON_CLICK, 0.5f, 1.2f),
    ERROR(Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f),
    THUNDER(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.4f, 1.4f),
    SPELL(Sound.ENTITY_EVOKER_CAST_SPELL, 0.6f, 1.2f),
    PET(Sound.ENTITY_FOX_AMBIENT, 0.8f, 1.4f),
    CHEST(Sound.BLOCK_CHEST_OPEN, 0.7f, 1.0f),
    LEGENDARY(Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f),
    EQUIP(Sound.ITEM_ARMOR_EQUIP_DIAMOND, 0.7f, 1.0f),
    WISP(Sound.ENTITY_ALLAY_ITEM_GIVEN, 0.8f, 1.2f),
    FAIL(Sound.ENTITY_WITHER_SKELETON_AMBIENT, 0.5f, 0.8f),
    ACHIEVEMENT(Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.5f, 1.5f),
}

/** One online player's game: owns the [Game], the open menu, and all the feedback (sounds, flashes, boss bar). */
class Session(val plugin: TapXPlugin, val player: Player, data: PlayerData) : GameEvents {
    val game = Game(data, this)
    val d get() = game.d

    var menu: Menu? = null
    var active = false
    private var ticks = 0L
    private val bossBar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.RED, BossBar.Overlay.NOTCHED_10)

    // Short-lived visual effects (seconds remaining).
    var flashMat: Material? = null
    private var flashT = 0.0
    var swingT = 0.0
    var petT = 0.0
    var coinT = 0.0
    var lastGold = 0.0
    var lastHit = ""
    var lastHitT = 0.0
    val allySwing = HashMap<String, Double>()
    var wispSlot = -1
    private var wispT = 0.0
    private var wispTimer = Random.nextDouble(30.0, 60.0)

    // Tap rate limiting (stops auto-clickers from going wild).
    private var tapWindowStart = 0L
    private var tapsInWindow = 0

    fun open(m: Menu) {
        if (!active) resume()
        menu = m
        m.draw()
        // Switch screens on the next tick; opening inventories inside a click event is unsafe.
        plugin.server.scheduler.runTask(plugin, Runnable { if (menu === m && player.isOnline) player.openInventory(m.inventory) })
    }

    /** Called when the player opens TapX after having it closed: pay out time-away earnings. */
    private fun resume() {
        active = true
        val away = (System.currentTimeMillis() - d.lastSeen) / 1000.0
        if (away > 30) {
            val g = game.applyOffline(away, plugin.config.getDouble("max-offline-hours", 12.0))
            if (g > 0) msg("<gray>Welcome back! You were away <white>${fmtTime(away)}</white>. Your allies earned $GOLD <yellow>${fmt(g)} gold</yellow>.")
        }
        d.lastSeen = System.currentTimeMillis()
        player.showBossBar(bossBar)
    }

    fun pause() {
        active = false
        menu = null
        d.lastSeen = System.currentTimeMillis()
        player.hideBossBar(bossBar)
        plugin.save(this)
    }

    fun tick(dt: Double) {
        if (!active) return
        ticks++
        game.update(dt)
        d.lastSeen = System.currentTimeMillis()

        flashT -= dt
        if (flashT <= 0) flashMat = null
        swingT -= dt; petT -= dt; coinT -= dt; lastHitT -= dt
        allySwing.replaceAll { _, v -> v - dt }
        if (Random.nextDouble() < 0.12) {
            HEROES.filter { game.heroLvl(it.id) > 0 }.randomOrNull()?.let { allySwing[it.id] = 0.3 }
        }
        updateWisp(dt)
        if (ticks % 10 == 0L) game.checkAchievements()
        updateBossBar()

        val m = menu ?: return
        if (ticks % m.refreshEvery == 0L) m.draw()
    }

    private fun updateWisp(dt: Double) {
        if (wispSlot >= 0) {
            wispT -= dt
            if (wispT <= 0) wispSlot = -1
        } else {
            wispTimer -= dt
            if (wispTimer <= 0 && menu is BattleMenu) {
                wispSlot = listOf(22, 26, 35).random()
                wispT = 8.0
                wispTimer = Random.nextDouble(45.0, 90.0)
            }
        }
    }

    fun catchWisp() {
        if (wispSlot < 0) return
        wispSlot = -1
        val reward = game.wispReward()
        sfx(Sfx.WISP)
        actionBar("<aqua>✦ Wisp caught! <white>$reward")
        menu?.draw()
    }

    private fun updateBossBar() {
        val m = game.monster
        if (m == null) return
        val frac = (m.hp / m.maxHp).toFloat().coerceIn(0f, 1f)
        bossBar.progress(frac)
        if (m.boss && !m.dead) {
            bossBar.color(BossBar.Color.PURPLE)
            bossBar.name(mm("<dark_red><b>☠ ${m.name}</b> <white>${fmt(kotlin.math.ceil(m.hp))} HP <gray>| <yellow>${"%.1f".format(game.bossTimer)}s"))
        } else {
            bossBar.color(if (m.mimic) BossBar.Color.YELLOW else BossBar.Color.RED)
            bossBar.name(mm("<red>${m.name} <white>${fmt(kotlin.math.ceil(m.hp))} HP <gray>| Stage ${d.stage} · ${if (d.farming) "farming" else "wave ${minOf(d.wave + 1, WAVES)}/$WAVES"}"))
        }
    }

    /** A click on the battle area. */
    fun playerTap() {
        val now = System.currentTimeMillis()
        if (now - tapWindowStart >= 1000) {
            tapWindowStart = now
            tapsInWindow = 0
        }
        if (++tapsInWindow > plugin.config.getInt("max-taps-per-second", 20)) return
        game.tap(false)
    }

    fun doPrestige() {
        val gained = game.prestige()
        sfx(Sfx.LEVEL_UP)
        msg("<light_purple><b>Reborn!</b> <gray>You earned $RELIC <red>${fmt(gained)} relics</red>. Spend them in the Artifacts tab.")
        open(ArtifactsMenu(this))
    }

    fun resetProgress() {
        game.d = PlayerData().apply {
            name = player.name
            gems = plugin.config.getLong("starting-gems", 100)
        }
        game.monster = null
        msg("<red>Your TapX progress has been reset.")
        open(BattleMenu(this))
    }

    // ---------------------------------------------------------- feedback helpers
    fun sfx(s: Sfx) {
        if (d.sound) player.playSound(player.location, s.sound, SoundCategory.MASTER, s.volume, s.pitch)
    }

    fun msg(text: String) = player.sendMessage(mm("<dark_gray>[<gold>TapX<dark_gray>] <reset>$text"))
    private fun actionBar(text: String) = player.sendActionBar(mm(text))

    private fun flash(mat: Material, seconds: Double = 0.15) {
        flashMat = mat
        flashT = seconds
    }

    private fun title(main: String, sub: String = "") = player.showTitle(
        Title.title(mm(main), mm(sub), Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1200), Duration.ofMillis(300)))
    )

    // ---------------------------------------------------------- game events
    override fun onTap(damage: Double, crit: Boolean, auto: Boolean) {
        swingT = 0.15
        if (crit) {
            flash(Material.ORANGE_STAINED_GLASS_PANE)
            sfx(Sfx.CRIT)
            lastHit = "<gold><b>CRIT! ${fmt(damage)}"
            actionBar("<gold><b>✦ CRITICAL ${fmt(damage)}!")
        } else {
            if (flashMat == null) flash(Material.WHITE_STAINED_GLASS_PANE, 0.1)
            if (!auto || ticks % 3 == 0L) sfx(Sfx.HIT)
            lastHit = "<white>Hit for ${fmt(damage)}"
            if (!auto) actionBar("<white>⚔ ${fmt(damage)}")
        }
        lastHitT = 0.6
    }

    override fun onKill(m: Monster, gold: Double, gems: Int) {
        coinT = 0.45
        lastGold = gold
        sfx(if (m.boss) Sfx.BOSS_KILL else Sfx.KILL)
        if (gems > 0) msg("<aqua>The boss dropped $GEM $gems gem${if (gems > 1) "s" else ""}!")
        if (m.mimic) actionBar("<gold>The mimic dropped $GOLD ${fmt(gold)} gold!")
    }

    override fun onSpawn(m: Monster) {
        if (m.boss) {
            sfx(Sfx.BOSS)
            title("<dark_red><b>BOSS BATTLE!", "<red>${m.name}")
        } else if (m.mimic) actionBar("<gold><b>A treasure mimic appears! <yellow>(12x gold)")
    }

    override fun onStage(skipped: Int) {
        sfx(Sfx.LEVEL_UP)
        if (skipped > 1) msg("<green>Warp Boots! You skipped to stage ${d.stage}.")
        val w = worldFor(d.stage)
        if (worldFor(d.stage - skipped) != w) title("<${w.color}><b>${w.name}", "<gray>Stage ${d.stage}")
    }

    override fun onBossFail() {
        sfx(Sfx.FAIL)
        msg("<red>The boss escaped!</red> <gray>Get stronger, then press <red>Fight Boss</red>.")
    }

    override fun onSkill(id: String, damage: Double) {
        sfx(Sfx.SPELL)
        val name = SKILLS.first { it.id == id }.name
        if (id == "thunder") {
            sfx(Sfx.THUNDER)
            flash(Material.LIGHT_BLUE_STAINED_GLASS_PANE, 0.5)
            actionBar("<aqua><b>⚡ THUNDER STRIKE ${fmt(damage)}!")
        } else actionBar("<yellow><b>$name!")
    }

    override fun onPetAttack(damage: Double) {
        petT = 0.4
        flash(Material.LIME_STAINED_GLASS_PANE, 0.3)
        sfx(Sfx.PET)
        actionBar("<green><b>${petDef(d.activePet)?.name ?: "Pet"} strikes for ${fmt(damage)}!")
    }

    override fun onAchievement(group: AchGroup, target: Double, gems: Int) {
        sfx(Sfx.ACHIEVEMENT)
        msg("<gold>★ ${group.name}: <white>${group.text(target)} <aqua>+$gems gems")
    }
}
