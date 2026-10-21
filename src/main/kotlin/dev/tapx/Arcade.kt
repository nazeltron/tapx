package dev.tapx

import dev.tapx.screen.MapScreen
import dev.tapx.screen.Palette
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket
import net.minecraft.network.protocol.game.ServerboundSwingPacket
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.WorldCreator
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.craftbukkit.entity.CraftEntity
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.FoodLevelChangeEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerGameModeChangeEvent
import org.bukkit.event.player.PlayerInputEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.generator.ChunkGenerator
import com.destroystokyo.paper.event.player.PlayerStartSpectatingEntityEvent
import com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent
import java.util.UUID
import kotlin.math.tan

/**
 * The "arcade": an empty void world where each player's camera is locked in front of their own
 * map screen, so the game fills their whole view. Players are put in spectator mode (no hotbar,
 * hand, hearts or crosshair) and spectate an invisible camera entity.
 *
 * Input: left-click (arm swing packet) taps, WASD/space/shift arrive as client input packets.
 */
class Arcade(private val plugin: TapXPlugin) : Listener {
    lateinit var world: World
        private set

    private class Seat(val slot: Int, val camera: ItemDisplay)

    private val seats = HashMap<UUID, Seat>()
    private val reserved = HashMap<UUID, Int>() // slots being set up (teleport in progress)
    private val leaving = HashSet<UUID>()
    private val allowCamera = HashSet<UUID>()
    private val reachKey = NamespacedKey(plugin, "screen_reach")

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

    // ---------------------------------------------------------- screen geometry
    private val cols get() = Session.SCREEN_COLS
    private val rows get() = Session.SCREEN_ROWS

    /** Where the camera sits for a slot: centred on the screen, at the distance that fills the view. */
    private fun cameraLocation(slot: Int, zoom: Double): Location {
        val fov = Math.toRadians(plugin.config.getDouble("screen-fov", 70.0).coerceIn(30.0, 110.0))
        val distance = rows / (2 * tan(fov / 2)) / (if (zoom > 0) zoom else 1.0)
        // frames: top row at y=102, columns sx-3..sx+3, hanging in the z=0 block row facing south
        return Location(world, slot * 64 + 0.5, 101.0, 1.0 / 16 + distance, 180f, 0f)
    }

    private fun screenOrigin(slot: Int) = BlockPos(slot * 64 - cols / 2, 102, 0)

    // ---------------------------------------------------------- enter / leave
    fun enter(p: Player, s: Session) {
        if (isPlaying(p) || p.uniqueId in reserved || !p.isOnline) return
        if (p.isInsideVehicle) p.leaveVehicle()
        if (p.world != world) {
            val from = Arcade.locToString(p.location)
            val mode = p.gameMode.name
            s.post { s.d.returnTo = from; s.d.returnMode = mode }
        }
        val slot = (0..10_000).first { i -> seats.values.none { it.slot == i } && i !in reserved.values }
        reserved[p.uniqueId] = slot
        val cam = cameraLocation(slot, s.d.screenZoom)

        p.teleportAsync(cam).thenRun {
            reserved.remove(p.uniqueId)
            if (!p.isOnline) return@thenRun
            val camera = world.spawn(cam, ItemDisplay::class.java) { it.isPersistent = false; it.teleportDuration = 2 }
            seats[p.uniqueId] = Seat(slot, camera)
            p.gameMode = GameMode.SPECTATOR
            // the screen must be within reach so clicks "hit" it (no miss cooldown on the client)
            p.getAttribute(Attribute.ENTITY_INTERACTION_RANGE)?.let { a ->
                a.removeModifier(reachKey)
                a.addTransientModifier(AttributeModifier(reachKey, 16.0, AttributeModifier.Operation.ADD_NUMBER))
            }
            for (o in Bukkit.getOnlinePlayers()) if (o != p && isPlaying(o)) { o.hidePlayer(plugin, p); p.hidePlayer(plugin, o) }
            installInput(p, s)
            // let the client learn about the camera entity before switching the view to it
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                if (!isPlaying(p)) return@Runnable
                lockCamera(p)
                val border = Palette.toMap(0x14131c)
                s.start(MapScreen(p, screenOrigin(slot), Direction.NORTH, cols, rows, MAP_ID_BASE, pad = 2, borderColor = border))
            }, 10L)
            plugin.server.scheduler.runTaskLater(plugin, Runnable { if (isPlaying(p)) lockCamera(p, resend = true) }, 40L)
        }
    }

    private fun lockCamera(p: Player, resend: Boolean = false) {
        val seat = seats[p.uniqueId] ?: return
        allowCamera += p.uniqueId
        try {
            if (p.spectatorTarget != seat.camera) p.spectatorTarget = seat.camera
            else if (resend) (p as CraftPlayer).handle.connection.send(ClientboundSetCameraPacket((seat.camera as CraftEntity).handle))
        } finally {
            allowCamera -= p.uniqueId
        }
    }

    /** Move the camera closer/further so the screen fits this player's field of view. */
    fun applyZoom(p: Player, zoom: Double) {
        val seat = seats[p.uniqueId] ?: return
        seat.camera.teleport(cameraLocation(seat.slot, zoom))
    }

    /** Leave the game screen: restore game mode and send the player back where they came from. */
    fun leave(p: Player, teleportBack: Boolean = true, restoreMode: Boolean = true) {
        val seat = seats.remove(p.uniqueId) ?: return
        val s = plugin.session(p)
        s?.stop()
        removeInput(p)
        leaving += p.uniqueId
        try {
            if (p.spectatorTarget != null) p.spectatorTarget = null
            seat.camera.remove()
            p.getAttribute(Attribute.ENTITY_INTERACTION_RANGE)?.removeModifier(reachKey)
            for (o in Bukkit.getOnlinePlayers()) if (o != p) { o.showPlayer(plugin, p); p.showPlayer(plugin, o) }
            if (restoreMode) p.gameMode = s?.d?.returnMode?.let { runCatching { GameMode.valueOf(it) }.getOrNull() } ?: GameMode.SURVIVAL
            if (teleportBack) p.teleport(s?.d?.returnTo?.let(::stringToLoc) ?: Bukkit.getWorlds()[0].spawnLocation)
            s?.d?.returnTo = null
            s?.d?.returnMode = null
        } finally {
            leaving -= p.uniqueId
        }
        s?.let { plugin.save(it) }
    }

    fun leaveAll() = seats.keys.toList().mapNotNull(Bukkit::getPlayer).forEach { leave(it) }

    /** Players who logged out while playing get their game mode/location back (when not auto-starting). */
    fun recover(p: Player, s: Session) {
        if (p.world != world && s.d.returnMode == null) return
        leaving += p.uniqueId
        try {
            p.gameMode = s.d.returnMode?.let { runCatching { GameMode.valueOf(it) }.getOrNull() } ?: GameMode.SURVIVAL
            if (p.world == world) p.teleport(s.d.returnTo?.let(::stringToLoc) ?: Bukkit.getWorlds()[0].spawnLocation)
        } finally {
            leaving -= p.uniqueId
        }
        s.d.returnTo = null
        s.d.returnMode = null
    }

    // ---------------------------------------------------------- input
    // Left-clicks arrive as arm-swing packets; we read them straight off the connection so they're
    // instant and work in spectator mode.
    private fun installInput(p: Player, s: Session) {
        val ch = (p as CraftPlayer).handle.connection.connection.channel
        ch.eventLoop().execute {
            if (ch.pipeline().get(HANDLER) == null && ch.pipeline().get("packet_handler") != null) {
                ch.pipeline().addBefore("packet_handler", HANDLER, object : ChannelInboundHandlerAdapter() {
                    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
                        if (msg is ServerboundSwingPacket) s.click()
                        super.channelRead(ctx, msg)
                    }
                })
            }
        }
    }

    private fun removeInput(p: Player) {
        val ch = (p as CraftPlayer).handle.connection.connection.channel
        ch.eventLoop().execute { if (ch.pipeline().get(HANDLER) != null) ch.pipeline().remove(HANDLER) }
    }

    @EventHandler
    fun onInput(e: PlayerInputEvent) {
        if (isPlaying(e.player)) plugin.session(e.player)?.input(e.input)
    }

    // ---------------------------------------------------------- keep the camera locked
    @EventHandler
    fun onStopSpectating(e: PlayerStopSpectatingEntityEvent) {
        if (isPlaying(e.player) && e.player.uniqueId !in leaving) e.isCancelled = true // shift = "back" in the game
    }

    @EventHandler
    fun onStartSpectating(e: PlayerStartSpectatingEntityEvent) {
        if (isPlaying(e.player) && e.player.uniqueId !in allowCamera) e.isCancelled = true
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onGameMode(e: PlayerGameModeChangeEvent) {
        val p = e.player
        if (isPlaying(p) && p.uniqueId !in leaving && e.newGameMode != GameMode.SPECTATOR) {
            // an admin changed their game mode: let them out where they are
            plugin.server.scheduler.runTask(plugin, Runnable { leave(p, teleportBack = false, restoreMode = false) })
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onTeleport(e: PlayerTeleportEvent) {
        val p = e.player
        if (!isPlaying(p) || p.uniqueId in leaving) return
        if (e.cause == PlayerTeleportEvent.TeleportCause.SPECTATE) { e.isCancelled = true; return } // spectator menu
        // teleported away by something else (/spawn, /tpa...): end the game without sending them back
        if (!e.isCancelled && e.to.world != world) plugin.server.scheduler.runTask(plugin, Runnable { leave(p, teleportBack = false) })
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

    companion object {
        const val MAP_ID_BASE = 1_970_000_000
        private const val HANDLER = "tapx_input"

        fun locToString(l: Location) = "${l.world.name};${l.x};${l.y};${l.z};${l.yaw};${l.pitch}"
        fun stringToLoc(s: String): Location? {
            val p = s.split(';')
            if (p.size < 6) return null
            val w = Bukkit.getWorld(p[0]) ?: return null
            return Location(w, p[1].toDouble(), p[2].toDouble(), p[3].toDouble(), p[4].toFloat(), p[5].toFloat())
        }
    }
}
