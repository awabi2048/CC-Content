package jp.awabi2048.cccontent.features.brewery.model

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.block.Block
import java.util.UUID

data class BreweryLocationKey(
    val worldUid: UUID,
    val x: Int,
    val y: Int,
    val z: Int
) {
    fun toSerialized(): String = "$worldUid:$x:$y:$z"

    fun toLocation(worldOverride: World? = null): Location? {
        val world = worldOverride ?: Bukkit.getWorld(worldUid) ?: return null
        return Location(world, x.toDouble(), y.toDouble(), z.toDouble())
    }

    companion object {
        fun fromBlock(block: Block): BreweryLocationKey {
            return BreweryLocationKey(block.world.uid, block.x, block.y, block.z)
        }

        fun fromLocation(location: Location): BreweryLocationKey {
            val world = location.world ?: throw IllegalArgumentException("world is null")
            return BreweryLocationKey(world.uid, location.blockX, location.blockY, location.blockZ)
        }

        fun parse(raw: String): BreweryLocationKey? {
            val parts = raw.split(':')
            if (parts.size != 4) return null
            return try {
                BreweryLocationKey(
                    worldUid = UUID.fromString(parts[0]),
                    x = parts[1].toInt(),
                    y = parts[2].toInt(),
                    z = parts[3].toInt()
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
