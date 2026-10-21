package dev.tapx.screen

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponents
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.decoration.GlowItemFrame
import net.minecraft.world.entity.decoration.ItemFrame
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.saveddata.maps.MapId
import net.minecraft.world.level.saveddata.maps.MapItemSavedData
import net.minecraft.world.phys.Vec3
import org.bukkit.craftbukkit.CraftWorld
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.entity.Player
import java.util.Optional
import java.util.UUID

/**
 * A wall of invisible glow item frames holding maps, sent only to one player (no real entities,
 * no blocks). Each map is a 128x128 tile of the 2D screen; only changed pixels are re-sent.
 *
 * @param origin the block of the top-left frame
 * @param facing the direction the player looks in (frames face the opposite way)
 */
class MapScreen(
    private val player: Player,
    private val origin: BlockPos,
    private val facing: Direction,
    val cols: Int,
    val rows: Int,
    private val mapIdBase: Int,
    /** Extra tiles of plain border around the screen, so other FOVs/aspect ratios see edges, not sky. */
    private val pad: Int = 0,
    private val borderColor: Byte = 0,
) {
    val width = cols * 128
    val height = rows * 128
    private val entityIds = ArrayList<Int>()
    private val sent = Array(cols * rows) { ByteArray(128 * 128) }
    private var spawned = false
    private val right: Direction = facing.clockWise

    private fun send(p: Packet<*>) = (player as CraftPlayer).handle.connection.send(p)

    fun tileBlock(col: Int, row: Int): BlockPos = origin.relative(right, col).below(row)

    private fun spawnFrame(pos: BlockPos, mapId: Int) {
        val level = (player.world as CraftWorld).handle
        val frameDir = facing.opposite
        val frame = GlowItemFrame(level, pos, frameDir)
        frame.isInvisible = true
        frame.isSilent = true
        val map = ItemStack(Items.FILLED_MAP)
        map.set(DataComponents.MAP_ID, MapId(mapId))
        frame.entityData.set(ItemFrame.DATA_ITEM, map)
        entityIds += frame.id
        send(ClientboundAddEntityPacket(frame.id, UUID.randomUUID(), pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(),
            0f, 0f, EntityType.GLOW_ITEM_FRAME, frameDir.get3DDataValue(), Vec3.ZERO, 0.0))
        frame.entityData.nonDefaultValues?.let { send(ClientboundSetEntityDataPacket(frame.id, it)) }
    }

    fun spawn() {
        for (row in 0 until rows) for (col in 0 until cols) {
            val i = row * cols + col
            spawnFrame(tileBlock(col, row), mapIdBase + i)
            sent[i].fill(-1) // force the first full upload
        }
        if (pad > 0) {
            // every border tile shows the same plain map, sent once
            val borderId = mapIdBase + cols * rows
            for (row in -pad until rows + pad) for (col in -pad until cols + pad) {
                if (row in 0 until rows && col in 0 until cols) continue
                spawnFrame(tileBlock(col, row), borderId)
            }
            send(ClientboundMapItemDataPacket(MapId(borderId), 0.toByte(), true, Optional.empty(),
                Optional.of(MapItemSavedData.MapPatch(0, 0, 128, 128, ByteArray(128 * 128) { borderColor }))))
        }
        spawned = true
    }

    fun remove() {
        if (!spawned) return
        spawned = false
        send(ClientboundRemoveEntitiesPacket(*entityIds.toIntArray()))
        entityIds.clear()
    }

    /**
     * Upload a full frame of map colour ids (width*height, row-major). Each 128x128 tile is checked
     * in horizontal bands so scattered changes don't force a whole-tile resend. Returns bytes sent.
     */
    fun push(pixels: ByteArray): Int {
        if (!spawned) return 0
        var bytes = 0
        for (row in 0 until rows) for (col in 0 until cols) {
            val tile = sent[row * cols + col]
            val id = MapId(mapIdBase + row * cols + col)
            var band = 0
            while (band < 128) {
                var minX = 128; var minY = 128; var maxX = -1; var maxY = -1
                for (y in band until band + BAND) {
                    val src = (row * 128 + y) * width + col * 128
                    val t = y * 128
                    for (x in 0 until 128) {
                        if (pixels[src + x] != tile[t + x]) {
                            if (x < minX) minX = x
                            if (x > maxX) maxX = x
                            if (y < minY) minY = y
                            maxY = y
                        }
                    }
                }
                band += BAND
                if (maxX < 0) continue
                val w = maxX - minX + 1
                val h = maxY - minY + 1
                val patch = ByteArray(w * h)
                for (y in 0 until h) {
                    val src = (row * 128 + minY + y) * width + col * 128 + minX
                    System.arraycopy(pixels, src, patch, y * w, w)
                    System.arraycopy(pixels, src, tile, (minY + y) * 128 + minX, w)
                }
                send(ClientboundMapItemDataPacket(id, 0.toByte(), true, Optional.empty(), Optional.of(MapItemSavedData.MapPatch(minX, minY, w, h, patch))))
                bytes += patch.size
            }
        }
        return bytes
    }

    companion object {
        private const val BAND = 16
    }
}
