package jp.awabi2048.cccontent.mob.ability.zombie

import jp.awabi2048.cccontent.mob.MobRuntimeContext
import jp.awabi2048.cccontent.mob.ability.MobAbility
import jp.awabi2048.cccontent.mob.ability.MobAbilityRuntime
import org.bukkit.Material
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import org.bukkit.inventory.ItemStack

class ZombieWeaponSwapAbility : MobAbility {
    override val id: String = "zombie_weapon_swap"

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        if (!context.isCombatActive()) return

        val entity = context.entity
        val target = resolveTarget(entity)
        if (target == null) {
            ensureMainHand(entity, Material.IRON_SWORD)
            return
        }

        val distanceSquared = entity.location.distanceSquared(target.location)
        if (distanceSquared >= BOW_MODE_MIN_DISTANCE_SQUARED) {
            ensureMainHand(entity, Material.BOW)
        } else {
            ensureMainHand(entity, Material.IRON_SWORD)
        }
    }

    private fun resolveTarget(entity: LivingEntity): LivingEntity? {
        val target = (entity as? Mob)?.target as? LivingEntity ?: return null
        if (!target.isValid || target.isDead) {
            return null
        }
        return target
    }

    private fun ensureMainHand(entity: LivingEntity, material: Material) {
        val equipment = entity.equipment ?: return
        if (equipment.itemInMainHand.type != material) {
            equipment.setItemInMainHand(ItemStack(material))
        }
    }

    private companion object {
        const val BOW_MODE_MIN_DISTANCE_SQUARED = 36.0
    }
}
