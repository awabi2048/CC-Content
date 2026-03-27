package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobRuntimeContext
import org.bukkit.Material
import org.bukkit.entity.LivingEntity
import org.bukkit.inventory.ItemStack

class WeaponSwapAbility(
    override val id: String,
    private val meleeWeapon: Material = Material.IRON_SWORD,
    private val rangedWeapon: Material = Material.BOW,
    private val rangedDistanceSquared: Double = 36.0
) : MobAbility {
    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        if (!context.isCombatActive()) return

        val entity = context.entity
        val target = MobAbilityUtils.resolveTarget(entity)
        if (target == null) {
            ensureMainHand(entity, meleeWeapon)
            return
        }

        val distanceSquared = entity.location.distanceSquared(target.location)
        if (distanceSquared >= rangedDistanceSquared) {
            ensureMainHand(entity, rangedWeapon)
        } else {
            ensureMainHand(entity, meleeWeapon)
        }
    }

    private fun ensureMainHand(entity: LivingEntity, material: Material) {
        val equipment = entity.equipment ?: return
        if (equipment.itemInMainHand.type != material) {
            equipment.setItemInMainHand(ItemStack(material))
        }
    }
}
