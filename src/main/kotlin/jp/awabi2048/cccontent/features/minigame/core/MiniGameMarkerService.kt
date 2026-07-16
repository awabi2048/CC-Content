package jp.awabi2048.cccontent.features.minigame.core

import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.entity.EntityType
import org.bukkit.entity.Marker
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.block.Action
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

data class PlacedMiniGameMarker(
    val marker: MiniGameMarker,
    val location: Location,
    val entity: Marker
)

class MiniGameMarkerService(
    private val plugin: JavaPlugin,
    private val pdc: MiniGamePdc,
    private val accessPolicy: MiniGameAccessPolicy,
    private val settings: MiniGameSettings,
    private val gameRunning: (MiniGameId) -> Boolean
) : Listener {
    fun markers(game: MiniGameId): List<PlacedMiniGameMarker> {
        val world = plugin.server.getWorld(game.worldUuid) ?: return emptyList()
        return world.getEntitiesByClass(Marker::class.java)
            .mapNotNull { entity ->
                val marker = pdc.readEntity(entity) ?: return@mapNotNull null
                if (marker.game != game) return@mapNotNull null
                PlacedMiniGameMarker(marker, entity.location.clone(), entity)
            }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    fun onPlace(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND || event.action != Action.RIGHT_CLICK_BLOCK) return
        val item = event.item ?: return
        val data = pdc.readItem(item) ?: return
        val type = data.markerType ?: return
        event.isCancelled = true
        val player = event.player
        if (player.world.uid != data.worldUuid) {
            player.sendMessage(MiniGameMessages.text(player, "messages.wrong_world"))
            return
        }
        val game = MiniGameId(data.worldUuid, data.gameId)
        if (gameRunning(game)) {
            player.sendMessage(MiniGameMessages.text(player, "messages.edit_locked"))
            return
        }
        if (!accessPolicy.canEdit(player, game.worldUuid, data.ownerUuid)) {
            player.sendMessage(MiniGameMessages.text(player, "messages.no_edit_permission"))
            return
        }
        val block = event.clickedBlock ?: return
        val location = block.location.clone().add(0.5, 1.0, 0.5)
        val checkpointIndex = if (type in setOf(
                MiniGameMarkerType.CHECKPOINT,
                MiniGameMarkerType.START,
                MiniGameMarkerType.CUP,
                MiniGameMarkerType.WATER_HAZARD,
                MiniGameMarkerType.BUNKER
            )) {
            markers(game)
                .filter { it.marker.type == type }
                .mapNotNull { it.marker.checkpointIndex }
                .maxOrNull()?.plus(1) ?: 1
        } else {
            null
        }
        val marker = MiniGameMarker(UUID.randomUUID(), game, data.ownerUuid, type, checkpointIndex)
        val entity = location.world?.spawnEntity(location, EntityType.MARKER) as? Marker ?: return
        entity.isPersistent = true
        pdc.markEntity(entity, marker)
        consumeOne(player, item)
        player.sendMessage(
            MiniGameMessages.text(
                player,
                "messages.marker_placed",
                "type" to type.name.lowercase(),
                "index" to (checkpointIndex ?: "-")
            )
        )
        player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.4f)
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    fun onRemove(event: EntityDamageByEntityEvent) {
        val marker = event.entity as? Marker ?: return
        val data = pdc.readEntity(marker) ?: return
        val player = event.damager as? Player ?: return
        event.isCancelled = true
        if (gameRunning(data.game)) return
        if (!accessPolicy.canEdit(player, data.game.worldUuid, data.ownerUuid)) {
            player.sendMessage(MiniGameMessages.text(player, "messages.no_edit_permission"))
            return
        }
        marker.remove()
        player.sendMessage(MiniGameMessages.text(player, "messages.marker_removed", "type" to data.type.name.lowercase()))
    }

    private fun consumeOne(player: Player, item: ItemStack) {
        item.amount -= 1
        if (item.amount <= 0) player.inventory.setItemInMainHand(null)
    }
}
