package jp.awabi2048.cccontent.mob.type

import jp.awabi2048.cccontent.mob.CustomMobRuntime
import jp.awabi2048.cccontent.mob.MobSpawnContext
import jp.awabi2048.cccontent.mob.ability.BoomerangAbility
import jp.awabi2048.cccontent.mob.ability.BackstepAbility
import jp.awabi2048.cccontent.mob.ability.CurveShotAbility
import jp.awabi2048.cccontent.mob.ability.LeapAbility
import jp.awabi2048.cccontent.mob.ability.RangedAttackAbility
import jp.awabi2048.cccontent.mob.ability.ShieldAbility
import jp.awabi2048.cccontent.mob.ability.WeaponThrowAbility
import jp.awabi2048.cccontent.mob.ability.WeaponSwapAbility
import org.bukkit.enchantments.Enchantment
import org.bukkit.Material
import org.bukkit.entity.EntityType
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import kotlin.random.Random

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

class ZombieNormalMobType : EquipmentMobType(
    id = "zombie_normal",
    baseEntityType = EntityType.ZOMBIE,
    abilities = emptyList()
)

class ZombieLeapOnlyMobType : EquipmentMobType(
    id = "zombie_light_leap",
    baseEntityType = EntityType.ZOMBIE,
    abilities = listOf(LeapAbility(id = "zombie_leap")),
    defaultMainHand = Material.IRON_SWORD,
    defaultChestplate = Material.CHAINMAIL_CHESTPLATE
)

class ZombieBowOnlyMobType : EquipmentMobType(
    id = "zombie_archer",
    baseEntityType = EntityType.ZOMBIE,
    abilities = listOf(
        RangedAttackAbility(
            id = "zombie_bow",
            retreatOnCloseRange = true,
            rangedWeaponTypes = setOf(Material.BOW)
        )
    ),
    defaultMainHand = Material.BOW,
    defaultHelmet = Material.LEATHER_HELMET,
    defaultBoots = Material.LEATHER_BOOTS
)

class ZombieBowSwapMobType : EquipmentMobType(
    id = "zombie_archer_swap",
    baseEntityType = EntityType.ZOMBIE,
    abilities = listOf(
        WeaponSwapAbility(
            id = "zombie_weapon_swap",
            meleeWeapon = Material.STONE_SWORD,
            rangedWeapon = Material.BOW
        ),
        RangedAttackAbility(
            id = "zombie_bow",
            retreatOnCloseRange = true,
            rangedWeaponTypes = setOf(Material.BOW)
        )
    ),
    defaultMainHand = Material.STONE_SWORD,
    defaultHelmet = Material.IRON_HELMET,
    defaultBoots = Material.IRON_BOOTS
)

class ZombieShieldOnlyMobType : EquipmentMobType(
    id = "zombie_heavy_shield",
    baseEntityType = EntityType.ZOMBIE,
    abilities = emptyList(),
    defaultMainHand = Material.GOLDEN_SWORD,
    defaultHelmet = Material.IRON_HELMET,
    defaultChestplate = Material.IRON_CHESTPLATE
)

class SkeletonNormalMobType : EquipmentMobType(
    id = "skeleton_normal",
    baseEntityType = EntityType.SKELETON,
    abilities = emptyList(),
    defaultMainHand = Material.BOW
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
    id = "skeleton_rapid",
    baseEntityType = EntityType.SKELETON,
    abilities = listOf(
        RangedAttackAbility(
            id = "skeleton_rapid_shot_ranged",
            arrowSpeedMultiplier = 1.0,
            intervalMultiplier = RangedAttackAbility.DEFAULT_RAPID_INTERVAL_MULTIPLIER,
            effectArrowChance = 0.0
        )
    ),
    defaultMainHand = Material.BOW,
    defaultHelmet = Material.GOLDEN_HELMET,
    defaultBoots = Material.GOLDEN_BOOTS
) {
    override fun applyDefaultEquipment(context: MobSpawnContext, runtime: CustomMobRuntime?) {
        super.applyDefaultEquipment(context, runtime)
        applyGlintBow(context.entity.equipment?.itemInMainHand)
    }
}

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
    defaultMainHand = Material.BOW,
    defaultHelmet = Material.CHAINMAIL_HELMET,
    defaultBoots = Material.CHAINMAIL_BOOTS
) {
    override fun applyDefaultEquipment(context: MobSpawnContext, runtime: CustomMobRuntime?) {
        super.applyDefaultEquipment(context, runtime)
        applyGlintBow(context.entity.equipment?.itemInMainHand)
    }
}

class SkeletonBowShieldMobType : EquipmentMobType(
    id = "skeleton_heavy_bow_shield",
    baseEntityType = EntityType.SKELETON,
    abilities = listOf(
        ShieldAbility(id = "skeleton_bow_shield", breakDisablesShieldPermanently = true)
    ),
    defaultMainHand = Material.BOW,
    defaultOffHand = Material.SHIELD,
    defaultHelmet = Material.LEATHER_HELMET,
    defaultChestplate = Material.IRON_CHESTPLATE,
    defaultLeggings = Material.LEATHER_LEGGINGS,
    defaultBoots = Material.LEATHER_BOOTS
)

class SkeletonWeaponThrowCloseMobType : EquipmentMobType(
    id = "skeleton_throw_close",
    baseEntityType = EntityType.SKELETON,
    abilities = listOf(
        WeaponThrowAbility(id = "skeleton_weapon_throw_close")
    ),
    defaultHelmet = Material.LEATHER_HELMET,
    defaultChestplate = Material.CHAINMAIL_CHESTPLATE
) {
    override fun applyDefaultEquipment(context: MobSpawnContext, runtime: CustomMobRuntime?) {
        super.applyDefaultEquipment(context, runtime)
        val equipment = context.entity.equipment ?: return
        if (equipment.itemInMainHand.type.isAir) {
            equipment.setItemInMainHand(ItemStack(randomGoldenTool()))
        }
    }
}

private fun applyGlintBow(mainHand: ItemStack?) {
    val item = mainHand ?: return
    if (item.type != Material.BOW) return
    if (item.getEnchantmentLevel(Enchantment.UNBREAKING) <= 0) {
        item.addUnsafeEnchantment(Enchantment.UNBREAKING, 1)
    }
    val meta = item.itemMeta ?: return
    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
    item.itemMeta = meta
}

private fun randomGoldenTool(): Material {
    val tools = arrayOf(
        Material.GOLDEN_AXE,
        Material.GOLDEN_PICKAXE,
        Material.GOLDEN_SHOVEL,
        Material.GOLDEN_HOE
    )
    return tools[Random.nextInt(tools.size)]
}
