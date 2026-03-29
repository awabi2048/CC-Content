package jp.awabi2048.cccontent.mob.type

import jp.awabi2048.cccontent.mob.CustomMobRuntime
import jp.awabi2048.cccontent.mob.MobSpawnContext
import jp.awabi2048.cccontent.mob.ability.AreaEffectPulseAbility
import jp.awabi2048.cccontent.mob.ability.ArmorMagnetPullAbility
import jp.awabi2048.cccontent.mob.ability.BoomerangAbility
import jp.awabi2048.cccontent.mob.ability.BackstepAbility
import jp.awabi2048.cccontent.mob.ability.ClimbingLeapAbility
import jp.awabi2048.cccontent.mob.ability.CurveShotAbility
import jp.awabi2048.cccontent.mob.ability.GuardianBeamAbility
import jp.awabi2048.cccontent.mob.ability.LeapAbility
import jp.awabi2048.cccontent.mob.ability.LinearProjectileAbility
import jp.awabi2048.cccontent.mob.ability.MeleeKnockbackBoostAbility
import jp.awabi2048.cccontent.mob.ability.PeriodicCobwebAbility
import jp.awabi2048.cccontent.mob.ability.PlayerTargetAssistAbility
import jp.awabi2048.cccontent.mob.ability.PoisonOnMeleeHitAbility
import jp.awabi2048.cccontent.mob.ability.ProjectileAndFireImmunityAbility
import jp.awabi2048.cccontent.mob.ability.RangedAttackAbility
import jp.awabi2048.cccontent.mob.ability.RandomInvisibilityAbility
import jp.awabi2048.cccontent.mob.ability.ShieldAbility
import jp.awabi2048.cccontent.mob.ability.SplitOnDeathAbility
import jp.awabi2048.cccontent.mob.ability.WeaponThrowAbility
import jp.awabi2048.cccontent.mob.ability.WeaponSwapAbility
import org.bukkit.Color
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.enchantments.Enchantment
import org.bukkit.Material
import org.bukkit.entity.EntityType
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import kotlin.random.Random

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

class HuskNormalMobType : EquipmentMobType(
    id = "husk_normal",
    baseEntityType = EntityType.HUSK,
    abilities = emptyList()
)

class HuskLeapOnlyMobType : EquipmentMobType(
    id = "husk_light_leap",
    baseEntityType = EntityType.HUSK,
    abilities = listOf(LeapAbility(id = "husk_leap")),
    defaultMainHand = Material.IRON_SWORD,
    defaultChestplate = Material.CHAINMAIL_CHESTPLATE
)

class HuskBowOnlyMobType : EquipmentMobType(
    id = "husk_archer",
    baseEntityType = EntityType.HUSK,
    abilities = listOf(
        RangedAttackAbility(
            id = "husk_bow",
            retreatOnCloseRange = true,
            rangedWeaponTypes = setOf(Material.BOW)
        )
    ),
    defaultMainHand = Material.BOW,
    defaultHelmet = Material.LEATHER_HELMET,
    defaultBoots = Material.LEATHER_BOOTS
)

class HuskBowSwapMobType : EquipmentMobType(
    id = "husk_archer_swap",
    baseEntityType = EntityType.HUSK,
    abilities = listOf(
        WeaponSwapAbility(
            id = "husk_weapon_swap",
            meleeWeapon = Material.STONE_SWORD,
            rangedWeapon = Material.BOW
        ),
        RangedAttackAbility(
            id = "husk_swap_bow",
            retreatOnCloseRange = true,
            rangedWeaponTypes = setOf(Material.BOW)
        )
    ),
    defaultMainHand = Material.STONE_SWORD,
    defaultHelmet = Material.IRON_HELMET,
    defaultBoots = Material.IRON_BOOTS
)

class HuskShieldOnlyMobType : EquipmentMobType(
    id = "husk_heavy_shield",
    baseEntityType = EntityType.HUSK,
    abilities = emptyList(),
    defaultMainHand = Material.GOLDEN_SWORD,
    defaultHelmet = Material.IRON_HELMET,
    defaultChestplate = Material.IRON_CHESTPLATE
)

class HuskWeakeningAuraMobType : EquipmentMobType(
    id = "husk_weakening_aura",
    baseEntityType = EntityType.HUSK,
    abilities = listOf(
        AreaEffectPulseAbility(
            id = "husk_aura_weakness",
            intervalTicks = 100L,
            radius = 4.0,
            effectType = PotionEffectType.WEAKNESS,
            effectDurationTicks = 60,
            effectAmplifier = 0
        )
    ),
    defaultMainHand = Material.STONE_SWORD,
    defaultHelmet = Material.CHAINMAIL_HELMET
)

class IronGolemNormalMobType : EquipmentMobType(
    id = "iron_golem_normal",
    baseEntityType = EntityType.IRON_GOLEM,
    abilities = listOf(
        PlayerTargetAssistAbility(id = "iron_golem_normal_target_assist"),
        ProjectileAndFireImmunityAbility(id = "iron_golem_normal_immunity"),
        MeleeKnockbackBoostAbility(id = "iron_golem_normal_knockback")
    )
)

class IronGolemMagnetMobType : EquipmentMobType(
    id = "iron_golem_magnet",
    baseEntityType = EntityType.IRON_GOLEM,
    abilities = listOf(
        PlayerTargetAssistAbility(id = "iron_golem_magnet_target_assist"),
        ProjectileAndFireImmunityAbility(id = "iron_golem_magnet_immunity"),
        ArmorMagnetPullAbility(id = "iron_golem_magnet_pull")
    )
)

class GuardianNormalMobType : EquipmentMobType(
    id = "guardian_normal",
    baseEntityType = EntityType.GUARDIAN,
    abilities = listOf(
        GuardianBeamAbility(
            id = "guardian_normal_beam",
            cooldownTicks = 90L,
            chargeTicks = 30L,
            minRange = 2.0,
            maxRange = 16.0,
            directDamageMultiplier = 1.0,
            directBonusDamage = 1.5,
            explosionRadius = 0.0,
            directKnockback = 1.5,
            splashKnockback = 0.0,
            chargePulseIntervalTicks = 0L
        )
    )
)

class GuardianSmallMobType : EquipmentMobType(
    id = "guardian_small",
    baseEntityType = EntityType.GUARDIAN,
    abilities = listOf(
        GuardianBeamAbility(
            id = "guardian_small_beam",
            cooldownTicks = 70L,
            chargeTicks = 24L,
            minRange = 2.0,
            maxRange = 14.0,
            directDamageMultiplier = 0.75,
            directBonusDamage = 1.0,
            explosionRadius = 0.0,
            directKnockback = 1.25,
            splashKnockback = 0.0,
            chargePulseIntervalTicks = 0L
        )
    )
)

class GuardianBeamBurstMobType : EquipmentMobType(
    id = "guardian_beam_burst",
    baseEntityType = EntityType.GUARDIAN,
    abilities = listOf(
        GuardianBeamAbility(
            id = "guardian_burst_beam",
            cooldownTicks = 105L,
            chargeTicks = 35L,
            minRange = 2.0,
            maxRange = 18.0,
            directDamageMultiplier = 1.1,
            directBonusDamage = 3.0,
            explosionRadius = 3.6,
            directKnockback = 2.3,
            splashKnockback = 1.2,
            splashVerticalBoost = 0.26,
            chargePulseIntervalTicks = 0L,
            decorationIntervalTicks = 10L,
            decorationEffect = GuardianBeamAbility.centeredBoxDecorationEffect(
                Color.fromRGB(220, 40, 40),
                Color.fromRGB(255, 255, 255),
                0.55,
                0.85,
                0.55,
                particleCount = 24,
                size = 1.2f
            ),
            impactParticle = Particle.EXPLOSION_EMITTER,
            impactParticleCount = 1,
            impactTargetParticles = GuardianBeamAbility.dustTransitionEffect(
                Color.fromRGB(220, 40, 40),
                Color.fromRGB(255, 255, 255),
                1.35f
            )
        )
    )
)

class GuardianDrainMobType : EquipmentMobType(
    id = "guardian_drain",
    baseEntityType = EntityType.GUARDIAN,
    abilities = listOf(
        GuardianBeamAbility(
            id = "guardian_drain_beam",
            cooldownTicks = 95L,
            chargeTicks = 40L,
            minRange = 2.0,
            maxRange = 16.0,
            directDamageMultiplier = 0.8,
            directBonusDamage = 1.5,
            explosionRadius = 0.0,
            directKnockback = 1.5,
            splashKnockback = 0.0,
            chargePulseIntervalTicks = 30L,
            chargePulseDamage = 3.0,
            chargePulseSelfHeal = 2.0,
            chargeDebuffType = PotionEffectType.WEAKNESS,
            chargeDebuffDurationTicks = 60,
            chargeDebuffAmplifier = 0,
            chargePulseSound = Sound.ENTITY_GUARDIAN_ATTACK,
            decorationIntervalTicks = 10L,
            decorationEffect = GuardianBeamAbility.centeredBoxDecorationEffect(
                Color.fromRGB(0, 110, 150),
                Color.fromRGB(255, 255, 255),
                0.55,
                0.85,
                0.55,
                particleCount = 24,
                size = 1.2f
            ),
            activationSound = Sound.ENTITY_GUARDIAN_DEATH,
            activationSoundPitch = 2.0f,
            activationSoundOnlyFirst = true,
            activationTargetParticles = { location ->
                location.world.spawnParticle(Particle.WARPED_SPORE, location, 100, 0.55, 0.45, 0.55, 0.02)
                location.world.spawnParticle(
                    Particle.DUST_COLOR_TRANSITION,
                    location,
                    18,
                    0.45,
                    0.45,
                    0.45,
                    0.0,
                    Particle.DustTransition(Color.fromRGB(0, 110, 150), Color.fromRGB(255, 255, 255), 1.25f)
                )
            }
        )
    )
)

class SkeletonPlainMobType : EquipmentMobType(
    id = "skeleton_plain",
    baseEntityType = EntityType.SKELETON,
    abilities = emptyList()
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

class SilverfishPlainMobType : EquipmentMobType(
    id = "silverfish_plain",
    baseEntityType = EntityType.SILVERFISH,
    abilities = emptyList()
)

class SilverfishBigPoisonMobType : EquipmentMobType(
    id = "silverfish_big_poison",
    baseEntityType = EntityType.SILVERFISH,
    abilities = listOf(
        PoisonOnMeleeHitAbility(
            id = "silverfish_poison_on_hit",
            chance = 0.25,
            durationTicks = 60,
            amplifier = 0
        )
    )
)

class SpiderPlainMobType : EquipmentMobType(
    id = "spider_plain",
    baseEntityType = EntityType.SPIDER,
    abilities = emptyList()
)

class SpiderStealthMobType : EquipmentMobType(
    id = "spider_stealth",
    baseEntityType = EntityType.SPIDER,
    abilities = listOf(
        RandomInvisibilityAbility(
            id = "spider_stealth_invisibility",
            spawnChance = 0.12,
            damagedChance = 0.16,
            durationTicks = 60
        ),
        LeapAbility(
            id = "spider_stealth_leap",
            cooldownTicks = 75L,
            minRangeSquared = 9.0,
            maxRange = 7.0,
            horizontalSpeed = 1.0,
            verticalSpeed = 0.5
        )
    )
)

class SpiderBroodmotherMobType : EquipmentMobType(
    id = "spider_broodmother",
    baseEntityType = EntityType.SPIDER,
    abilities = listOf(
        PeriodicCobwebAbility(
            id = "spider_broodmother_cobweb",
            cycleTicks = 100L,
            webLifetimeTicks = 100L,
            radius = 3,
            maxWebsPerCycle = 8
        ),
        SplitOnDeathAbility(
            id = "spider_broodmother_split",
            childDefinitionId = "spider_broodling",
            splitCount = 4,
            healthMultiplier = 0.5,
            attackMultiplier = 0.5,
            speedMultiplier = 0.5,
            spawnRadius = 1.2
        )
    )
)

class SpiderBroodlingMobType : EquipmentMobType(
    id = "spider_broodling",
    baseEntityType = EntityType.SPIDER,
    abilities = emptyList()
)

class SpiderSwiftMobType : EquipmentMobType(
    id = "spider_swift",
    baseEntityType = EntityType.SPIDER,
    abilities = listOf(
        LeapAbility(
            id = "spider_swift_leap",
            cooldownTicks = 70L,
            minRangeSquared = 9.0,
            maxRange = 8.0,
            horizontalSpeed = 1.05,
            verticalSpeed = 0.52
        ),
        ClimbingLeapAbility(
            id = "spider_swift_climbing_leap",
            cooldownTicks = 60L,
            minRangeSquared = 4.0,
            maxRange = 6.0,
            horizontalSpeed = 1.05,
            verticalSpeed = 0.6
        )
    )
)

class SpiderVenomFrenzyMobType : EquipmentMobType(
    id = "spider_venom_frenzy",
    baseEntityType = EntityType.CAVE_SPIDER,
    abilities = listOf(
        LinearProjectileAbility(
            id = "spider_venom_projectile",
            cooldownTicks = 40L,
            minRange = 3.0,
            maxRange = 5.0,
            speedBlocksPerSecond = 20.0,
            maxTravelDistance = 16.0,
            damageMultiplier = 1.0,
            aggressiveInRange = true,
            trailRenderer = { location ->
                val world = location.world
                if (world != null) {
                    world.spawnParticle(
                        Particle.DUST,
                        location,
                        1,
                        0.1,
                        0.1,
                        0.1,
                        0.0,
                        Particle.DustOptions(Color.fromRGB(26, 90, 26), 1.0f)
                    )
                    world.spawnParticle(Particle.ITEM_SLIME, location, 1, 0.1, 0.1, 0.1, 0.0)
                }
            },
            onHit = { _, target ->
                target.addPotionEffect(PotionEffect(PotionEffectType.POISON, 100, 0, false, true, true))
            }
        )
    )
)

class SpiderFerociousMobType : EquipmentMobType(
    id = "spider_ferocious",
    baseEntityType = EntityType.SPIDER,
    abilities = listOf(
        LinearProjectileAbility(
            id = "spider_web_projectile",
            cooldownTicks = 30L,
            minRange = 3.0,
            maxRange = 5.0,
            speedBlocksPerSecond = 20.0,
            maxTravelDistance = 14.0,
            damageMultiplier = 0.8,
            aggressiveInRange = true,
            trailRenderer = { location ->
                val world = location.world
                if (world != null) {
                    world.spawnParticle(
                        Particle.DUST,
                        location,
                        1,
                        0.1,
                        0.1,
                        0.1,
                        0.0,
                        Particle.DustOptions(Color.WHITE, 1.5f)
                    )
                }
            },
            onHit = { _, target ->
                target.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 80, 0, false, true, true))
                resolveMiningFatigueEffectType()?.let { miningFatigue ->
                    target.addPotionEffect(PotionEffect(miningFatigue, 80, 0, false, true, true))
                }
            }
        )
    )
)

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

private fun resolveMiningFatigueEffectType(): PotionEffectType? {
    return PotionEffectType.getByName("MINING_FATIGUE")
        ?: PotionEffectType.getByName("SLOW_DIGGING")
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
