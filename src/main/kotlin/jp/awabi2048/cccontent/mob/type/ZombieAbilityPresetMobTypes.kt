package jp.awabi2048.cccontent.mob.type

import jp.awabi2048.cccontent.mob.CustomMobRuntime
import jp.awabi2048.cccontent.mob.MobSpawnContext
import jp.awabi2048.cccontent.mob.ability.MobAbility
import jp.awabi2048.cccontent.mob.ability.zombie.ZombieBowAbility
import jp.awabi2048.cccontent.mob.ability.zombie.ZombieLeapAbility
import jp.awabi2048.cccontent.mob.ability.zombie.ZombieShieldAbility
import jp.awabi2048.cccontent.mob.ability.zombie.ZombieWeaponSwapAbility
import org.bukkit.Material
import org.bukkit.entity.EntityType
import org.bukkit.inventory.ItemStack

abstract class ZombieAbilityPresetMobType(
    id: String,
    abilities: List<MobAbility>,
    private val defaultMainHand: Material,
    private val defaultOffHand: Material? = null
) : AbilityMobType(
    id = id,
    baseEntityType = EntityType.ZOMBIE,
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

class ZombieLeapOnlyMobType : ZombieAbilityPresetMobType(
    id = "zombie_leap_only",
    abilities = listOf(ZombieLeapAbility()),
    defaultMainHand = Material.IRON_SWORD
)

class ZombieBowOnlyMobType : ZombieAbilityPresetMobType(
    id = "zombie_bow_only",
    abilities = listOf(ZombieBowAbility()),
    defaultMainHand = Material.BOW
)

class ZombieBowSwapMobType : ZombieAbilityPresetMobType(
    id = "zombie_bow_swap",
    abilities = listOf(
        ZombieWeaponSwapAbility(),
        ZombieBowAbility()
    ),
    defaultMainHand = Material.IRON_SWORD
)

class ZombieShieldOnlyMobType : ZombieAbilityPresetMobType(
    id = "zombie_shield_only",
    abilities = listOf(ZombieShieldAbility()),
    defaultMainHand = Material.IRON_SWORD,
    defaultOffHand = Material.SHIELD
)
