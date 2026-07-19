package jp.awabi2048.cccontent.features.cooking

import org.bukkit.Bukkit
import org.bukkit.block.Block
import java.util.Base64
import java.util.UUID

internal data class CookingStationKey(
    val worldId: UUID,
    val x: Int,
    val y: Int,
    val z: Int
) {
    fun serialize(): String = "$worldId;$x;$y;$z"

    fun pathKey(): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(serialize().toByteArray(Charsets.UTF_8))

    fun blockIfLoaded(): Block? {
        val world = Bukkit.getWorld(worldId) ?: return null
        if (!world.isChunkLoaded(x shr 4, z shr 4)) return null
        return world.getBlockAt(x, y, z)
    }

    companion object {
        fun from(block: Block): CookingStationKey =
            CookingStationKey(block.world.uid, block.x, block.y, block.z)

        fun deserialize(value: String): CookingStationKey? {
            val parts = value.split(';')
            if (parts.size != 4) return null
            return runCatching {
                CookingStationKey(
                    UUID.fromString(parts[0]),
                    parts[1].toInt(),
                    parts[2].toInt(),
                    parts[3].toInt()
                )
            }.getOrNull()
        }
    }
}
