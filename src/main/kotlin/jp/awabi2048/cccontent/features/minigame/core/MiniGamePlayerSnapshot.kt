package jp.awabi2048.cccontent.features.minigame.core

import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID

/** セッション開始前の状態を保持し、終了時に同じプレイヤーへ復元する。 */
data class MiniGamePlayerSnapshot(
    val playerUuid: UUID,
    val location: Location,
    val inventory: Array<ItemStack?>,
    val armor: Array<ItemStack?>,
    val offHand: ItemStack?,
    val gameMode: GameMode,
    val health: Double,
    val foodLevel: Int,
    val saturation: Float,
    val level: Int,
    val experience: Float,
    val allowFlight: Boolean,
    val flying: Boolean
) {
    fun restore(player: Player) {
        player.inventory.setContents(inventory.map { it?.clone() }.toTypedArray())
        player.inventory.setArmorContents(armor.map { it?.clone() }.toTypedArray())
        player.inventory.setItemInOffHand(offHand?.clone())
        player.gameMode = gameMode
        player.allowFlight = allowFlight
        player.isFlying = flying && allowFlight
        player.foodLevel = foodLevel
        player.saturation = saturation
        player.level = level
        player.exp = experience
        val maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.value ?: 20.0
        player.health = health.coerceIn(0.0, maxHealth).coerceAtLeast(0.5)
        player.teleport(location)
    }

    companion object {
        fun capture(player: Player): MiniGamePlayerSnapshot = MiniGamePlayerSnapshot(
            playerUuid = player.uniqueId,
            location = player.location.clone(),
            inventory = player.inventory.contents.map { it?.clone() }.toTypedArray(),
            armor = player.inventory.armorContents.map { it?.clone() }.toTypedArray(),
            offHand = player.inventory.itemInOffHand.clone(),
            gameMode = player.gameMode,
            health = player.health,
            foodLevel = player.foodLevel,
            saturation = player.saturation,
            level = player.level,
            experience = player.exp,
            allowFlight = player.allowFlight,
            flying = player.isFlying
        )
    }
}
