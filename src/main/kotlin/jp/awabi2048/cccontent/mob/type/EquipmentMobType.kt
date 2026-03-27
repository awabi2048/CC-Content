package jp.awabi2048.cccontent.mob.type

import jp.awabi2048.cccontent.mob.CustomMobRuntime
import jp.awabi2048.cccontent.mob.MobSpawnContext
import jp.awabi2048.cccontent.mob.ability.MobAbility
import org.bukkit.Material
import org.bukkit.entity.EntityType
import org.bukkit.inventory.ItemStack

open class EquipmentMobType(
    id: String,
    baseEntityType: EntityType,
    abilities: List<MobAbility>,
    private val defaultMainHand: Material,
    private val defaultOffHand: Material? = null
) : AbilityMobType(
    id = id,
    baseEntityType = baseEntityType,
    abilities = abilities
) {
    override fun applyDefaultEquipment(context: MobSpawnContext, runtime: CustomMobRuntime?) {
        val equipment = context.entity.equipment ?: return
        if (equipment.itemInMainHand.type.isAir) {
            equipment.setItemInMainHand(ItemStack(defaultMainHand))
        }
        val offHand = defaultOffHand
        if (offHand != null && equipment.itemInOffHand.type.isAir) {
            equipment.setItemInOffHand(ItemStack(offHand))
        }
    }
}
