package dev.tapx

import dev.tapx.screen.MapScreen
import io.papermc.paper.entity.TeleportFlag
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.WorldCreator
import org.bukkit.entity.Entity
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDismountEvent
import org.bukkit.event.entity.FoodLevelChangeEvent
import org.bukkit.event.player.PlayerAnimationEvent
import org.bukkit.event.player.PlayerAnimationType
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.generator.ChunkGenerator
import java.util.UUID
import kotlin.math.tan

/**
 * The "arcade": an empty void world where each player sits frozen in front of their own map
 * screen. Look around to move the cursor, left-click to click, scroll to scroll, sneak to leave.
 */
class Arcade(private val plugin: TapXPlugin) : Listener {
    lateinit var world: World
        private set

    private class Seat(val slot: Int, val seat: Entity, val eye: Location)

    private val seats = HashMap<UUID, Seat>()
    private val reserved = HashMap<UUID, Int>() // slots being set up (teleport in progress)
    private val leaving = HashSet<UUID>()

    /** Generates nothing: an endless empty sky. A fixed spawn skips the slow spawn-point search. */
    private class VoidGenerator : ChunkGenerator() {
        override fun getFixedSpawnLocation(world: World, random: java.util.Random) = Location(world, 0.5, 100.0, 0.5)
    }

    fun load() {
        val name = plugin.config.getString("arcade-world", "tapx_arcade")!!
        world = Bukkit.getWorld(name) ?: WorldCreator(name).generator(VoidGenerator()).generateStructures(false).createWorld()!!
        world.clearWeatherDuration = Int.MAX_VALUE
        world.setStorm(false)
        world.time = 6000
        world.isAutoSave = false
    }

    fun isPlaying(p: Player) = p.uniqueId in seats

    fun enter(p: Player, s: Session) {
        if (isPlaying(p) || p.uniqueId in reserved) return
        if (p.isInsideVehicle) p.leaveVehicle()
        if (p.world != world) s.post { s.d.returnTo = locToString(p.location) }
        val slot = (0..10_000).first { i -> seats.values.none { it.slot == i } && i !in reserved.values }
        reserved[p.uniqueId] = slot
        val cols = Session.SCREEN_COLS
        val rows = Session.SCREEN_ROWS
        // Screen geometry: facing north, top row of frames at y=102, frames hang in the z=0 block row.
        val sx = slot * 64
        val fov = Math.toRadians(plugin.config.getDouble("screen-fov", 70.0).coerceIn(30.0, 110.0))
        val distance = rows / (2 * tan(fov / 2))
        val eye = Location(world, sx + 0.5, 101.0, 1.0 / 16 + distance, 180f, 0f)
        val origin = BlockPos(sx - cols / 2, 102, 0)

        p.teleportAsync(eye.clone().subtract(0.0, 1.62, 0.0)).thenRun {
            reserved.remove(p.uniqueId)
            if (!p.isOnline) return@thenRun
            val seat = world.spawn(eye.clone().subtract(0.0, 1.62, 0.0), ItemDisplay::class.java) { it.isPersistent = false }
            seat.addPassenger(p)
            seats[p.uniqueId] = Seat(slot, seat, eye)
            // hide other arcade players from each other
            for (o in Bukkit.getOnlinePlayers()) if (o != p && isPlaying(o)) { o.hidePlayer(plugin, p); p.hidePlayer(plugin, o) }
            // after the rider settles, nudge the seat so the eyes are exactly where the screen expects
            plugin.server.scheduler.runTaskLater(plugin, Runnable { align(p) }, 3L)
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                align(p)
                if (isPlaying(p)) s.start(MapScreen(p, origin, Direction.NORTH, cols, rows, MAP_ID_BASE))
            }, 12L)
        }
    }

    private fun align(p: Player) {
        val seat = seats[p.uniqueId] ?: return
        val off = seat.eye.toVector().subtract(p.eyeLocation.toVector())
        if (off.lengthSquared() > 0.0004) seat.seat.teleport(seat.seat.location.add(off), TeleportFlag.EntityState.RETAIN_PASSENGERS)
    }

    /** Leave the game screen and go back to where the player came from. */
    fun leave(p: Player, teleportBack: Boolean = true) {
        val seat = seats.remove(p.uniqueId) ?: return
        val s = plugin.session(p)
        s?.stop()
        leaving += p.uniqueId
        try {
            seat.seat.removePassenger(p)
            seat.seat.remove()
        } finally {
            leaving -= p.uniqueId
        }
        for (o in Bukkit.getOnlinePlayers()) if (o != p) { o.showPlayer(plugin, p); p.showPlayer(plugin, o) }
        if (teleportBack) {
            val back = s?.d?.returnTo?.let(::stringToLoc) ?: Bukkit.getWorlds()[0].spawnLocation
            s?.d?.returnTo = null
            leaving += p.uniqueId
            p.teleport(back)
            leaving -= p.uniqueId
        }
        s?.let { plugin.save(it) }
    }

    fun leaveAll() = seats.keys.toList().mapNotNull(Bukkit::getPlayer).forEach { leave(it) }

    /** Called every server tick: turn each player's view direction into a cursor position. */
    fun updateCursors() {
        for ((id, seat) in seats) {
            val p = Bukkit.getPlayer(id) ?: continue
            val e = p.eyeLocation
            val dir = e.direction
            val geo = GEOMETRY.getOrPut(seat.slot) { Geometry(BlockPos(seat.slot * 64 - Session.SCREEN_COLS / 2, 102, 0)) }
            plugin.session(p)?.setCursor(geo.cursor(e.x, e.y, e.z, dir.x, dir.y, dir.z))
        }
    }

    /** Where a line of sight hits the screen (frames face south, so players look north). */
    private class Geometry(val origin: BlockPos) {
        fun cursor(ex: Double, ey: Double, ez: Double, dx: Double, dy: Double, dz: Double): Pair<Int, Int>? {
            val planeZ = origin.z + 1.0 / 16
            if (dz >= -1e-6) return null // must look north
            val t = (planeZ - ez) / dz
            val hx = ex + dx * t
            val hy = ey + dy * t
            val px = ((hx - origin.x) * 128).toInt()
            val py = (((origin.y + 1.0) - hy) * 128).toInt()
            if (px < 0 || py < 0 || px >= Session.SCREEN_COLS * 128 || py >= Session.SCREEN_ROWS * 128) return null
            return px to py
        }
    }

    // ---------------------------------------------------------- events
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onSwing(e: PlayerAnimationEvent) {
        if (e.animationType != PlayerAnimationType.ARM_SWING || !isPlaying(e.player)) return
        plugin.session(e.player)?.click()
    }

    @EventHandler
    fun onScroll(e: PlayerItemHeldEvent) {
        if (!isPlaying(e.player)) return
        var delta = e.newSlot - e.previousSlot
        if (delta > 4) delta -= 9
        if (delta < -4) delta += 9
        plugin.session(e.player)?.scroll(delta)
        e.isCancelled = true
    }

    @EventHandler
    fun onSneak(e: PlayerToggleSneakEvent) {
        if (e.isSneaking && isPlaying(e.player)) leave(e.player)
    }

    @EventHandler
    fun onDismount(e: EntityDismountEvent) {
        val p = e.entity as? Player ?: return
        if (!isPlaying(p) || p.uniqueId in leaving) return
        e.isCancelled = true
        plugin.server.scheduler.runTask(plugin, Runnable { leave(p) })
    }

    @EventHandler
    fun onDamage(e: EntityDamageEvent) {
        if (e.entity.world == world) e.isCancelled = true
    }

    @EventHandler
    fun onHunger(e: FoodLevelChangeEvent) {
        if (e.entity.world == world) e.isCancelled = true
    }

    @EventHandler
    fun onInteract(e: PlayerInteractEvent) {
        if (isPlaying(e.player)) e.isCancelled = true
    }

    @EventHandler
    fun onInteractEntity(e: PlayerInteractEntityEvent) {
        if (isPlaying(e.player)) e.isCancelled = true
    }

    @EventHandler
    fun onDrop(e: PlayerDropItemEvent) {
        if (isPlaying(e.player)) e.isCancelled = true
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onTeleport(e: PlayerTeleportEvent) {
        val p = e.player
        // Teleported away by something else (/spawn, /tpa...): end the game without sending them back.
        if (isPlaying(p) && p.uniqueId !in leaving && e.to.world != world) leave(p, teleportBack = false)
    }

    @EventHandler
    fun onJoin(e: PlayerJoinEvent) {
        val p = e.player
        if (p.world != world) return
        // They logged out (or the server stopped) while playing: send them home.
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            val s = plugin.session(p)
            val back = s?.d?.returnTo?.let(::stringToLoc) ?: Bukkit.getWorlds()[0].spawnLocation
            s?.d?.returnTo = null
            p.teleport(back)
        }, 1L)
    }

    companion object {
        const val MAP_ID_BASE = 1_970_000_000
        private val GEOMETRY = HashMap<Int, Geometry>()

        fun locToString(l: Location) = "${l.world.name};${l.x};${l.y};${l.z};${l.yaw};${l.pitch}"
        fun stringToLoc(s: String): Location? {
            val p = s.split(';')
            if (p.size < 6) return null
            val w = Bukkit.getWorld(p[0]) ?: return null
            return Location(w, p[1].toDouble(), p[2].toDouble(), p[3].toDouble(), p[4].toFloat(), p[5].toFloat())
        }
    }
}
