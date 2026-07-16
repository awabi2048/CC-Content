package jp.awabi2048.cccontent.features.minigame.core

import org.bukkit.NamespacedKey
import org.bukkit.entity.Entity
import org.bukkit.entity.Marker
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import java.util.UUID

data class MiniGameItemData(
    val worldUuid: UUID,
    val ownerUuid: UUID,
    val gameId: String,
    val markerType: MiniGameMarkerType? = null,
    val manager: Boolean = false
)

/** アイテムとMarkerエンティティで同じ識別情報を使うためのPDC境界。 */
class MiniGamePdc(plugin: JavaPlugin) {
    private val worldKey = NamespacedKey(plugin, "minigame_world_uuid")
    private val ownerKey = NamespacedKey(plugin, "minigame_owner_uuid")
    private val gameKey = NamespacedKey(plugin, "minigame_game_id")
    private val typeKey = NamespacedKey(plugin, "minigame_marker_type")
    private val managerKey = NamespacedKey(plugin, "minigame_manager")
    private val checkpointIndexKey = NamespacedKey(plugin, "minigame_checkpoint_index")

    fun markItem(item: ItemStack, data: MiniGameItemData) {
        require(data.gameId in MiniGameSupportedGames.ids) { "unsupported mini-game id: ${data.gameId}" }
        val meta = item.itemMeta ?: return
        val container = meta.persistentDataContainer
        container.set(worldKey, PersistentDataType.STRING, data.worldUuid.toString())
        container.set(ownerKey, PersistentDataType.STRING, data.ownerUuid.toString())
        container.set(gameKey, PersistentDataType.STRING, data.gameId)
        data.markerType?.let { container.set(typeKey, PersistentDataType.STRING, it.name) }
        if (data.manager) container.set(managerKey, PersistentDataType.BYTE, 1.toByte())
        item.itemMeta = meta
    }

    fun readItem(item: ItemStack?): MiniGameItemData? {
        val container = item?.itemMeta?.persistentDataContainer ?: return null
        return read(container)
    }

    fun markEntity(entity: Entity, data: MiniGameMarker, checkpointIndex: Int? = data.checkpointIndex) {
        require(data.game.gameId in MiniGameSupportedGames.ids) { "unsupported mini-game id: ${data.game.gameId}" }
        val container = entity.persistentDataContainer
        container.set(worldKey, PersistentDataType.STRING, data.game.worldUuid.toString())
        container.set(ownerKey, PersistentDataType.STRING, data.ownerUuid.toString())
        container.set(gameKey, PersistentDataType.STRING, data.game.gameId)
        container.set(typeKey, PersistentDataType.STRING, data.type.name)
        checkpointIndex?.let { container.set(checkpointIndexKey, PersistentDataType.INTEGER, it) }
    }

    fun readEntity(entity: Entity): MiniGameMarker? {
        val container = entity.persistentDataContainer
        val base = read(container) ?: return null
        val type = base.markerType ?: return null
        val index = container.get(checkpointIndexKey, PersistentDataType.INTEGER)
        return runCatching {
            MiniGameMarker(entity.uniqueId, MiniGameId(base.worldUuid, base.gameId), base.ownerUuid, type, index)
        }.getOrNull()
    }

    fun isMarker(entity: Entity): Boolean = entity is Marker && readEntity(entity) != null

    private fun read(container: PersistentDataContainer): MiniGameItemData? {
        val world = container.get(worldKey, PersistentDataType.STRING)?.let(::parseUuid) ?: return null
        val owner = container.get(ownerKey, PersistentDataType.STRING)?.let(::parseUuid) ?: return null
        if (owner == UUID(0L, 0L)) return null
        val gameId = container.get(gameKey, PersistentDataType.STRING)?.takeIf { it.isNotBlank() } ?: return null
        if (gameId !in MiniGameSupportedGames.ids) return null
        val markerType = container.get(typeKey, PersistentDataType.STRING)?.let { runCatching { MiniGameMarkerType.valueOf(it) }.getOrNull() }
        val manager = container.get(managerKey, PersistentDataType.BYTE) == 1.toByte()
        if (markerType == null && !manager) return null
        return MiniGameItemData(world, owner, gameId, markerType, manager)
    }

    private fun parseUuid(value: String): UUID? = runCatching { UUID.fromString(value) }.getOrNull()
}
