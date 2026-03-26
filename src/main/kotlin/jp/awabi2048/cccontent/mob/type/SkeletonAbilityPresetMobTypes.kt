package jp.awabi2048.cccontent.mob.type

import jp.awabi2048.cccontent.mob.CustomMobRuntime
import jp.awabi2048.cccontent.mob.MobSpawnContext
import jp.awabi2048.cccontent.mob.ability.MobAbility
import jp.awabi2048.cccontent.mob.ability.skeleton.SkeletonBoomerangAbility
import jp.awabi2048.cccontent.mob.ability.skeleton.SkeletonRangedAbility
import jp.awabi2048.cccontent.mob.ability.skeleton.SkeletonShieldAbility
import org.bukkit.Material
import org.bukkit.entity.EntityType
import org.bukkit.inventory.ItemStack

abstract class SkeletonAbilityPresetMobType(
    id: String,
    abilities: List<MobAbility>,
    private val defaultMainHand: Material,
    private val defaultOffHand: Material? = null
) : AbilityMobType(
    id = id,
    baseEntityType = EntityType.SKELETON,
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

class SkeletonFastArrowMobType : SkeletonAbilityPresetMobType(
    id = "skeleton_fast_arrow",
    abilities = listOf(
        SkeletonRangedAbility(
            id = "skeleton_fast_arrow_ranged",
            arrowSpeedMultiplier = SkeletonRangedAbility.DEFAULT_FAST_ARROW_SPEED_MULTIPLIER,
            intervalMultiplier = 1.0,
            effectArrowChance = 0.0
        )
    ),
    defaultMainHand = Material.BOW
)

class SkeletonRapidShotMobType : SkeletonAbilityPresetMobType(
    id = "skeleton_rapid_shot",
    abilities = listOf(
        SkeletonRangedAbility(
            id = "skeleton_rapid_shot_ranged",
            arrowSpeedMultiplier = 1.0,
            intervalMultiplier = SkeletonRangedAbility.DEFAULT_RAPID_INTERVAL_MULTIPLIER,
            effectArrowChance = 0.0
        )
    ),
    defaultMainHand = Material.BOW
)

class SkeletonEffectArrowMobType : SkeletonAbilityPresetMobType(
    id = "skeleton_effect_arrow",
    abilities = listOf(
        SkeletonRangedAbility(
            id = "skeleton_effect_arrow_ranged",
            arrowSpeedMultiplier = 1.0,
            intervalMultiplier = 1.0,
            effectArrowChance = SkeletonRangedAbility.DEFAULT_EFFECT_ARROW_CHANCE
        )
    ),
    defaultMainHand = Material.BOW
)

class SkeletonCurveShotMobType : SkeletonAbilityPresetMobType(
    id = "skeleton_curve_shot",
    abilities = emptyList(),
    defaultMainHand = Material.BOW
)

class SkeletonShieldMobType : SkeletonAbilityPresetMobType(
    id = "skeleton_shield_only",
    abilities = listOf(SkeletonShieldAbility()),
    defaultMainHand = Material.STONE_SWORD,
    defaultOffHand = Material.SHIELD
)

class SkeletonBoomerangMobType : SkeletonAbilityPresetMobType(
    id = "skeleton_boomerang",
    abilities = listOf(SkeletonBoomerangAbility()),
    defaultMainHand = Material.BONE
)
