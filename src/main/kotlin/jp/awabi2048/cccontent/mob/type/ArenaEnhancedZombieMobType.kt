package jp.awabi2048.cccontent.mob.type

import jp.awabi2048.cccontent.mob.CustomMobRuntime
import jp.awabi2048.cccontent.mob.MobSpawnContext
import jp.awabi2048.cccontent.mob.ability.zombie.ZombieBowAbility
import jp.awabi2048.cccontent.mob.ability.zombie.ZombieLeapAbility
import jp.awabi2048.cccontent.mob.ability.zombie.ZombieShieldAbility
import jp.awabi2048.cccontent.mob.ability.zombie.ZombieWeaponSwapAbility
import org.bukkit.Material
import org.bukkit.entity.EntityType
import org.bukkit.inventory.ItemStack

class ArenaEnhancedZombieMobType : AbilityMobType(
    id = "arena_enhanced_zombie",
    baseEntityType = EntityType.ZOMBIE,
    abilities = listOf(
        ZombieLeapAbility(),
        ZombieShieldAbility(),
        ZombieWeaponSwapAbility(),
        ZombieBowAbility()
    )
) {

    override fun applyDefaultEquipment(context: MobSpawnContext, runtime: CustomMobRuntime?) {
        val equipment = context.entity.equipment ?: return
        if (equipment.itemInMainHand.type.isAir) {
            equipment.setItemInMainHand(ItemStack(Material.IRON_SWORD))
        }
        if (equipment.itemInOffHand.type.isAir) {
            equipment.setItemInOffHand(ItemStack(Material.SHIELD))
        }
    }
}
