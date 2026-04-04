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
    private val defaultMainHand: Material? = null,
    private val defaultOffHand: Material? = null,
    private val defaultHelmet: Material? = null,
    private val defaultChestplate: Material? = null,
    private val defaultLeggings: Material? = null,
    private val defaultBoots: Material? = null
) : AbilityMobType(
    id = id,
    baseEntityType = baseEntityType,
    abilities = abilities
) {
    override fun applyDefaultEquipment(context: MobSpawnContext, runtime: CustomMobRuntime?) {
        val equipment = context.entity.equipment ?: return
        val mainHand = defaultMainHand
        if (mainHand != null) {
            equipment.setItemInMainHand(ItemStack(mainHand))
        }
        val offHand = defaultOffHand
        if (offHand != null) {
            equipment.setItemInOffHand(ItemStack(offHand))
        }
        val helmet = defaultHelmet
        if (helmet != null) {
            equipment.helmet = ItemStack(helmet)
        }
        val chestplate = defaultChestplate
        if (chestplate != null) {
            equipment.chestplate = ItemStack(chestplate)
        }
        val leggings = defaultLeggings
        if (leggings != null) {
            equipment.leggings = ItemStack(leggings)
        }
        val boots = defaultBoots
        if (boots != null) {
            equipment.boots = ItemStack(boots)
        }
    }
}
