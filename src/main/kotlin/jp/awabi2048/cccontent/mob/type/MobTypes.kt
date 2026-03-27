package jp.awabi2048.cccontent.mob.type

import jp.awabi2048.cccontent.mob.ability.BoomerangAbility
import jp.awabi2048.cccontent.mob.ability.BackstepAbility
import jp.awabi2048.cccontent.mob.ability.CurveShotAbility
import jp.awabi2048.cccontent.mob.ability.LeapAbility
import jp.awabi2048.cccontent.mob.ability.RangedAttackAbility
import jp.awabi2048.cccontent.mob.ability.ShieldAbility
import jp.awabi2048.cccontent.mob.ability.WeaponThrowAbility
import jp.awabi2048.cccontent.mob.ability.WeaponSwapAbility
import org.bukkit.Material
import org.bukkit.entity.EntityType

class ArenaEnhancedZombieMobType : EquipmentMobType(
    id = "arena_enhanced_zombie",
    baseEntityType = EntityType.ZOMBIE,
    abilities = listOf(
        LeapAbility(id = "zombie_leap"),
        ShieldAbility(id = "zombie_shield", breakDisablesShieldPermanently = true),
        WeaponSwapAbility(id = "zombie_weapon_swap"),
        RangedAttackAbility(
            id = "zombie_bow",
            retreatOnCloseRange = true,
            rangedWeaponTypes = setOf(Material.BOW)
        )
    ),
    defaultMainHand = Material.IRON_SWORD,
    defaultOffHand = Material.SHIELD
)

class ZombieLeapOnlyMobType : EquipmentMobType(
    id = "zombie_leap_only",
    baseEntityType = EntityType.ZOMBIE,
    abilities = listOf(LeapAbility(id = "zombie_leap")),
    defaultMainHand = Material.IRON_SWORD
)

class ZombieBowOnlyMobType : EquipmentMobType(
    id = "zombie_bow_only",
    baseEntityType = EntityType.ZOMBIE,
    abilities = listOf(
        RangedAttackAbility(
            id = "zombie_bow",
            retreatOnCloseRange = true,
            rangedWeaponTypes = setOf(Material.BOW)
        )
    ),
    defaultMainHand = Material.BOW
)

class ZombieBowSwapMobType : EquipmentMobType(
    id = "zombie_bow_swap",
    baseEntityType = EntityType.ZOMBIE,
    abilities = listOf(
        WeaponSwapAbility(id = "zombie_weapon_swap"),
        RangedAttackAbility(
            id = "zombie_bow",
            retreatOnCloseRange = true,
            rangedWeaponTypes = setOf(Material.BOW)
        )
    ),
    defaultMainHand = Material.IRON_SWORD
)

class ZombieShieldOnlyMobType : EquipmentMobType(
    id = "zombie_shield_only",
    baseEntityType = EntityType.ZOMBIE,
    abilities = listOf(ShieldAbility(id = "zombie_shield", breakDisablesShieldPermanently = true)),
    defaultMainHand = Material.IRON_SWORD,
    defaultOffHand = Material.SHIELD
)

class SkeletonFastArrowMobType : EquipmentMobType(
    id = "skeleton_fast_arrow",
    baseEntityType = EntityType.SKELETON,
    abilities = listOf(
        RangedAttackAbility(
            id = "skeleton_fast_arrow_ranged",
            arrowSpeedMultiplier = RangedAttackAbility.DEFAULT_FAST_ARROW_SPEED_MULTIPLIER,
            intervalMultiplier = 1.0,
            effectArrowChance = 0.0
        )
    ),
    defaultMainHand = Material.BOW
)

class SkeletonRapidShotMobType : EquipmentMobType(
    id = "skeleton_rapid_shot",
    baseEntityType = EntityType.SKELETON,
    abilities = listOf(
        RangedAttackAbility(
            id = "skeleton_rapid_shot_ranged",
            arrowSpeedMultiplier = 1.0,
            intervalMultiplier = RangedAttackAbility.DEFAULT_RAPID_INTERVAL_MULTIPLIER,
            effectArrowChance = 0.0
        )
    ),
    defaultMainHand = Material.BOW
)

class SkeletonEffectArrowMobType : EquipmentMobType(
    id = "skeleton_effect_arrow",
    baseEntityType = EntityType.SKELETON,
    abilities = listOf(
        RangedAttackAbility(
            id = "skeleton_effect_arrow_ranged",
            arrowSpeedMultiplier = 1.0,
            intervalMultiplier = 1.0,
            effectArrowChance = RangedAttackAbility.DEFAULT_EFFECT_ARROW_CHANCE
        )
    ),
    defaultMainHand = Material.BOW
)

class SkeletonCurveShotMobType : EquipmentMobType(
    id = "skeleton_curve_shot",
    baseEntityType = EntityType.SKELETON,
    abilities = listOf(
        CurveShotAbility(id = "skeleton_curve_shot_curve")
    ),
    defaultMainHand = Material.BOW
)

class SkeletonShieldMobType : EquipmentMobType(
    id = "skeleton_shield_only",
    baseEntityType = EntityType.SKELETON,
    abilities = listOf(ShieldAbility(id = "skeleton_shield", breakDisablesShieldPermanently = true)),
    defaultMainHand = Material.STONE_SWORD,
    defaultOffHand = Material.SHIELD
)

class SkeletonBoomerangMobType : EquipmentMobType(
    id = "skeleton_boomerang",
    baseEntityType = EntityType.SKELETON,
    abilities = listOf(BoomerangAbility(id = "skeleton_boomerang")),
    defaultMainHand = Material.BONE
)

class SkeletonCurveBackstepMobType : EquipmentMobType(
    id = "skeleton_curve_backstep",
    baseEntityType = EntityType.SKELETON,
    abilities = listOf(
        CurveShotAbility(id = "skeleton_curve_backstep_curve"),
        BackstepAbility(
            id = "skeleton_curve_backstep_backstep",
            cooldownTicks = 20L
        )
    ),
    defaultMainHand = Material.BOW
)

class SkeletonBowShieldMobType : EquipmentMobType(
    id = "skeleton_bow_shield",
    baseEntityType = EntityType.SKELETON,
    abilities = listOf(
        ShieldAbility(id = "skeleton_bow_shield", breakDisablesShieldPermanently = true)
    ),
    defaultMainHand = Material.BOW,
    defaultOffHand = Material.SHIELD
)

class SkeletonWeaponThrowCloseMobType : EquipmentMobType(
    id = "skeleton_weapon_throw_close",
    baseEntityType = EntityType.SKELETON,
    abilities = listOf(
        WeaponThrowAbility(id = "skeleton_weapon_throw_close")
    ),
    defaultMainHand = Material.STONE_SWORD
)
