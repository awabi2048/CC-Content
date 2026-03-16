package jp.awabi2048.cccontent.mob

import org.bukkit.Material
import org.bukkit.inventory.EquipmentSlot

data class MobDefinition(
    val id: String,
    val typeId: String,
    val health: Double,
    val attack: Double,
    val movementSpeed: Double,
    val armor: Double,
    val scale: Double,
    val equipment: Map<EquipmentSlot, Material>
)
