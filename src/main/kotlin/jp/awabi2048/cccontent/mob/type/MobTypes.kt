package jp.awabi2048.cccontent.mob.type

import jp.awabi2048.cccontent.mob.CustomMobRuntime
import jp.awabi2048.cccontent.mob.MobSpawnContext
import jp.awabi2048.cccontent.mob.ability.AreaEffectPulseAbility
import jp.awabi2048.cccontent.mob.ability.ArmorMagnetPullAbility
import jp.awabi2048.cccontent.mob.ability.BoomerangAbility
import jp.awabi2048.cccontent.mob.ability.BackstepAbility
import jp.awabi2048.cccontent.mob.ability.BlazeSoundControllerAbility
import jp.awabi2048.cccontent.mob.ability.BlazeVolleyAbility
import jp.awabi2048.cccontent.mob.ability.ClimbingLeapAbility
import jp.awabi2048.cccontent.mob.ability.DashToTargetOffsetAbility
import jp.awabi2048.cccontent.mob.ability.DrownedAquaticPursuitAbility
import jp.awabi2048.cccontent.mob.ability.GenericBeamAbility
import jp.awabi2048.cccontent.mob.ability.GuardianBeamAbility
import jp.awabi2048.cccontent.mob.ability.LeapAbility
import jp.awabi2048.cccontent.mob.ability.LinearProjectileAbility
import jp.awabi2048.cccontent.mob.ability.MeleeKnockbackBoostAbility
import jp.awabi2048.cccontent.mob.ability.MobShootUtil
import jp.awabi2048.cccontent.mob.ability.PeriodicCobwebAbility
import jp.awabi2048.cccontent.mob.ability.PlayerTargetAssistAbility
import jp.awabi2048.cccontent.mob.ability.PoisonOnMeleeHitAbility
import jp.awabi2048.cccontent.mob.ability.PotionSlimeAbility
import jp.awabi2048.cccontent.mob.ability.ProjectileAndFireImmunityAbility
import jp.awabi2048.cccontent.mob.ability.ProximityFlamePulseAbility
import jp.awabi2048.cccontent.mob.ability.SlimeMergeAbility
import jp.awabi2048.cccontent.mob.ability.RangedAttackAbility
import jp.awabi2048.cccontent.mob.ability.RandomInvisibilityAbility
import jp.awabi2048.cccontent.mob.ability.ShieldAbility
import jp.awabi2048.cccontent.mob.ability.SplitOnDeathAbility
import jp.awabi2048.cccontent.mob.ability.WeaponThrowAbility
import jp.awabi2048.cccontent.mob.ability.GrudgeAuraAbility
import jp.awabi2048.cccontent.mob.ability.WaterSpiritAbility
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
    baseEntityType = EntityType.ELDER_GUARDIAN,
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
                particleCount = 48,
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
                particleCount = 48,
                size = 1.2f
            ),
            activationSound = Sound.ENTITY_GUARDIAN_DEATH,
            activationSoundPitch = 2.0f,
            activationSoundOnlyFirst = true,
            activationSoundOnPulse = true,
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
    abilities = listOf(
        RangedAttackAbility(
            id = "skeleton_normal_ranged"
        )
    ),
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
        RangedAttackAbility(
            id = "skeleton_curve_shot_ranged",
            homingConfig = MobShootUtil.HomingConfig()
        )
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
        RangedAttackAbility(
            id = "skeleton_curve_backstep_ranged",
            homingConfig = MobShootUtil.HomingConfig()
        ),
        BackstepAbility(
            id = "skeleton_curve_backstep_backstep",
            cooldownTicks = 20L,
            shootArrowOnLand = true,
            horizontalSpeed = 1.8,
            verticalSpeed = 0.9,
            homingConfig = MobShootUtil.HomingConfig()
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
        RangedAttackAbility(id = "skeleton_bow_shield_ranged"),
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
        equipment.setItemInMainHand(ItemStack(randomGoldenTool()))
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

class BoggedPlainMobType : EquipmentMobType(
    id = "bogged_plain",
    baseEntityType = EntityType.BOGGED,
    abilities = emptyList()
)

class BoggedNormalMobType : EquipmentMobType(
    id = "bogged_normal",
    baseEntityType = EntityType.BOGGED,
    abilities = listOf(
        RangedAttackAbility(
            id = "bogged_normal_ranged"
        )
    ),
    defaultMainHand = Material.BOW
)

class BoggedRapidShotMobType : EquipmentMobType(
    id = "bogged_rapid",
    baseEntityType = EntityType.BOGGED,
    abilities = listOf(
        RangedAttackAbility(
            id = "bogged_rapid_shot_ranged",
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

class BoggedCurveBackstepMobType : EquipmentMobType(
    id = "bogged_curve_backstep",
    baseEntityType = EntityType.BOGGED,
    abilities = listOf(
        RangedAttackAbility(
            id = "bogged_curve_backstep_ranged",
            homingConfig = MobShootUtil.HomingConfig()
        ),
        BackstepAbility(
            id = "bogged_curve_backstep_backstep",
            cooldownTicks = 20L,
            shootArrowOnLand = true,
            horizontalSpeed = 1.8,
            verticalSpeed = 0.9,
            homingConfig = MobShootUtil.HomingConfig()
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

class BoggedBowShieldMobType : EquipmentMobType(
    id = "bogged_heavy_bow_shield",
    baseEntityType = EntityType.BOGGED,
    abilities = listOf(
        RangedAttackAbility(id = "bogged_bow_shield_ranged"),
        ShieldAbility(id = "bogged_bow_shield", breakDisablesShieldPermanently = true)
    ),
    defaultMainHand = Material.BOW,
    defaultOffHand = Material.SHIELD,
    defaultHelmet = Material.LEATHER_HELMET,
    defaultChestplate = Material.IRON_CHESTPLATE,
    defaultLeggings = Material.LEATHER_LEGGINGS,
    defaultBoots = Material.LEATHER_BOOTS
)

class BoggedWeaponThrowCloseMobType : EquipmentMobType(
    id = "bogged_throw_close",
    baseEntityType = EntityType.BOGGED,
    abilities = listOf(
        WeaponThrowAbility(id = "bogged_weapon_throw_close")
    ),
    defaultHelmet = Material.LEATHER_HELMET,
    defaultChestplate = Material.CHAINMAIL_CHESTPLATE
) {
    override fun applyDefaultEquipment(context: MobSpawnContext, runtime: CustomMobRuntime?) {
        super.applyDefaultEquipment(context, runtime)
        val equipment = context.entity.equipment ?: return
        equipment.setItemInMainHand(ItemStack(randomGoldenTool()))
    }
}

class StrayPlainMobType : EquipmentMobType(
    id = "stray_plain",
    baseEntityType = EntityType.STRAY,
    abilities = emptyList()
)

class StrayNormalMobType : EquipmentMobType(
    id = "stray_normal",
    baseEntityType = EntityType.STRAY,
    abilities = listOf(
        RangedAttackAbility(
            id = "stray_normal_ranged"
        )
    ),
    defaultMainHand = Material.BOW
)

class StrayRapidShotMobType : EquipmentMobType(
    id = "stray_rapid",
    baseEntityType = EntityType.STRAY,
    abilities = listOf(
        RangedAttackAbility(
            id = "stray_rapid_shot_ranged",
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

class StrayCurveBackstepMobType : EquipmentMobType(
    id = "stray_curve_backstep",
    baseEntityType = EntityType.STRAY,
    abilities = listOf(
        RangedAttackAbility(
            id = "stray_curve_backstep_ranged",
            homingConfig = MobShootUtil.HomingConfig()
        ),
        BackstepAbility(
            id = "stray_curve_backstep_backstep",
            cooldownTicks = 20L,
            shootArrowOnLand = true,
            horizontalSpeed = 1.8,
            verticalSpeed = 0.9,
            homingConfig = MobShootUtil.HomingConfig()
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

class StrayBowShieldMobType : EquipmentMobType(
    id = "stray_heavy_bow_shield",
    baseEntityType = EntityType.STRAY,
    abilities = listOf(
        RangedAttackAbility(id = "stray_bow_shield_ranged"),
        ShieldAbility(id = "stray_bow_shield", breakDisablesShieldPermanently = true)
    ),
    defaultMainHand = Material.BOW,
    defaultOffHand = Material.SHIELD,
    defaultHelmet = Material.LEATHER_HELMET,
    defaultChestplate = Material.IRON_CHESTPLATE,
    defaultLeggings = Material.LEATHER_LEGGINGS,
    defaultBoots = Material.LEATHER_BOOTS
)

class StrayWeaponThrowCloseMobType : EquipmentMobType(
    id = "stray_throw_close",
    baseEntityType = EntityType.STRAY,
    abilities = listOf(
        WeaponThrowAbility(id = "stray_weapon_throw_close")
    ),
    defaultHelmet = Material.LEATHER_HELMET,
    defaultChestplate = Material.CHAINMAIL_CHESTPLATE
) {
    override fun applyDefaultEquipment(context: MobSpawnContext, runtime: CustomMobRuntime?) {
        super.applyDefaultEquipment(context, runtime)
        val equipment = context.entity.equipment ?: return
        equipment.setItemInMainHand(ItemStack(randomGoldenTool()))
    }
}

class SlimeSmallMobType : EquipmentMobType(
    id = "slime_merge_small",
    baseEntityType = EntityType.SLIME,
    abilities = listOf(
        ProjectileAndFireImmunityAbility(id = "slime_merge_small_immunity"),
        SlimeMergeAbility(
            id = "slime_merge_small_merge",
            selfDefinitionId = "slime_merge_small",
            mergeOrder = listOf("slime_merge_small", "slime_merge_medium", "slime_merge_large")
        )
    )
)

class SlimeMediumMobType : EquipmentMobType(
    id = "slime_merge_medium",
    baseEntityType = EntityType.SLIME,
    abilities = listOf(
        ProjectileAndFireImmunityAbility(id = "slime_merge_medium_immunity"),
        SlimeMergeAbility(
            id = "slime_merge_medium_merge",
            selfDefinitionId = "slime_merge_medium",
            mergeOrder = listOf("slime_merge_small", "slime_merge_medium", "slime_merge_large")
        )
    )
)

class SlimeLargeMobType : EquipmentMobType(
    id = "slime_merge_large",
    baseEntityType = EntityType.SLIME,
    abilities = listOf(
        ProjectileAndFireImmunityAbility(id = "slime_merge_large_immunity"),
        SlimeMergeAbility(
            id = "slime_merge_large_merge",
            selfDefinitionId = "slime_merge_large",
            mergeOrder = listOf("slime_merge_small", "slime_merge_medium", "slime_merge_large")
        ),
        LeapAbility(
            id = "slime_merge_large_leap",
            cooldownTicks = 100L,
            minRangeSquared = 9.0,
            maxRange = 10.0,
            horizontalSpeed = 1.5,
            verticalSpeed = 0.7
        )
    )
)

class SlimePoisonMobType : EquipmentMobType(
    id = "slime_poison",
    baseEntityType = EntityType.SLIME,
    abilities = listOf(
        ProjectileAndFireImmunityAbility(id = "slime_poison_immunity"),
        PotionSlimeAbility(
            id = "slime_poison_effect",
            effectType = PotionEffectType.POISON,
            effectDurationTicks = 80,
            effectAmplifier = 0,
            cloudColor = Color.GREEN
        )
    )
)

class SlimeWitherMobType : EquipmentMobType(
    id = "slime_wither",
    baseEntityType = EntityType.SLIME,
    abilities = listOf(
        ProjectileAndFireImmunityAbility(id = "slime_wither_immunity"),
        PotionSlimeAbility(
            id = "slime_wither_effect",
            effectType = PotionEffectType.WITHER,
            effectDurationTicks = 80,
            effectAmplifier = 0,
            cloudColor = Color.GRAY
        )
    )
)

class BlazeNormalMobType : EquipmentMobType(
    id = "blaze_normal",
    baseEntityType = EntityType.BLAZE,
    abilities = listOf(
        BlazeSoundControllerAbility(id = "blaze_normal_sound", pitch = 1.0f),
        BlazeVolleyAbility(
            id = "blaze_normal_volley",
            projectileKind = BlazeVolleyAbility.ProjectileKind.SMALL_FIREBALL,
            projectileSpeed = 0.95,
            inaccuracy = 0.02,
            cooldownTicks = 70L,
            burstCount = 3,
            burstIntervalTicks = 6L,
            damageMultiplier = 1.0
        )
    )
)

class BlazePowerMobType : EquipmentMobType(
    id = "blaze_power",
    baseEntityType = EntityType.BLAZE,
    abilities = listOf(
        BlazeSoundControllerAbility(id = "blaze_power_sound", pitch = 0.8f),
        BlazeVolleyAbility(
            id = "blaze_power_volley",
            projectileKind = BlazeVolleyAbility.ProjectileKind.FIREBALL,
            projectileSpeed = 0.78,
            inaccuracy = 0.028,
            cooldownTicks = 117L,
            burstCount = 3,
            burstIntervalTicks = 8L,
            damageMultiplier = 2.0
        )
    )
)

class BlazeRapidMobType : EquipmentMobType(
    id = "blaze_rapid",
    baseEntityType = EntityType.BLAZE,
    abilities = listOf(
        BlazeSoundControllerAbility(id = "blaze_rapid_sound", pitch = 1.3f),
        BlazeVolleyAbility(
            id = "blaze_rapid_volley",
            projectileKind = BlazeVolleyAbility.ProjectileKind.SMALL_FIREBALL,
            projectileSpeed = 0.9,
            inaccuracy = 0.11,
            cooldownTicks = 96L,
            burstCount = 10,
            burstIntervalTicks = 4L,
            recoilPerShot = 0.13,
            damageMultiplier = 0.52
        )
    )
)

class BlazeMeleeMobType : EquipmentMobType(
    id = "blaze_melee",
    baseEntityType = EntityType.BLAZE,
    abilities = listOf(
        BlazeSoundControllerAbility(id = "blaze_melee_sound", pitch = 1.2f),
        DashToTargetOffsetAbility(
            id = "blaze_melee_dash",
            triggerMinDistance = 10.0,
            cooldownTicks = 90L,
            dashSpeed = 1.6,
            dashVerticalSpeed = 0.2,
            hoverHeight = 3.0,
            hoverDurationTicks = 10L,
            maxDashDurationTicks = 24L,
            stabilizationSpeedThreshold = 0.2,
            stabilizationRequiredTicks = 6L,
            explosionPower = 4.2f,
            requireLineOfSight = true
        ),
        ProximityFlamePulseAbility(
            id = "blaze_melee_flame_pulse",
            intervalTicks = 30L,
            radius = 5.0,
            damage = 16.0,
            fireTicks = 60,
            particleCount = 280,
            particleSpreadX = 8.0,
            particleSpreadY = 8.0,
            particleSpreadZ = 8.0
        )
    )
)

class BlazeBeamMobType : EquipmentMobType(
    id = "blaze_beam",
    baseEntityType = EntityType.BLAZE,
    abilities = listOf(
        BlazeSoundControllerAbility(id = "blaze_beam_sound", pitch = 0.7f),
        GenericBeamAbility(
            id = "blaze_beam_attack",
            cooldownTicks = 105L,
            chargeTicks = 40L,
            minRange = 2.0,
            maxRange = 19.0,
            hitRadius = 0.9,
            damageMultiplier = 1.15,
            bonusDamage = 2.0,
            directKnockback = 0.85,
            verticalBoost = 0.08,
            beamStep = 0.45,
            requireLineOfSight = true
        )
    )
)

class DrownedNormalMobType : EquipmentMobType(
    id = "zombie_ocean_normal",
    baseEntityType = EntityType.ZOMBIE,
    abilities = listOf(
        DrownedAquaticPursuitAbility(id = "drowned_normal_aquatic_pursuit"),
        PlayerTargetAssistAbility(id = "drowned_normal_target_assist")
    ),
    defaultMainHand = Material.STONE_SWORD
)

class DrownedWarriorMobType : EquipmentMobType(
    id = "zombie_ocean_warrior",
    baseEntityType = EntityType.ZOMBIE,
    abilities = listOf(
        DrownedAquaticPursuitAbility(id = "drowned_warrior_aquatic_pursuit"),
        PlayerTargetAssistAbility(id = "drowned_warrior_target_assist"),
        ShieldAbility(id = "drowned_warrior_shield", breakDisablesShieldPermanently = true),
        BackstepAbility(
            id = "drowned_warrior_backstep",
            cooldownTicks = 100L,
            horizontalSpeed = 0.8,
            verticalSpeed = 0.35
        )
    ),
    defaultMainHand = Material.STONE_SWORD,
    defaultOffHand = Material.SHIELD
)

class DrownedGrudgeMobType : EquipmentMobType(
    id = "drowned_grudge",
    baseEntityType = EntityType.DROWNED,
    abilities = listOf(
        PlayerTargetAssistAbility(id = "drowned_grudge_target_assist"),
        GrudgeAuraAbility(
            id = "drowned_grudge_aura",
            radius = 5.0,
            debuffIntervalTicks = 120L,
            debuffDurationSlownessTicks = 100,
            debuffAmplifierSlowness = 1,
            debuffDurationBlindnessTicks = 60,
            damageIntervalTicks = 160L,
            damageAmount = 3.0
        ),
    ),
    defaultMainHand = Material.TRIDENT
)

class DrownedPowerThrowMobType : EquipmentMobType(
    id = "drowned_power_throw",
    baseEntityType = EntityType.DROWNED,
    abilities = listOf(
        PlayerTargetAssistAbility(id = "drowned_power_throw_target_assist"),
    ),
    defaultMainHand = Material.TRIDENT
)

class WaterSpiritMobType : EquipmentMobType(
    id = "water_spirit",
    baseEntityType = EntityType.ALLAY,
    abilities = listOf(
        WaterSpiritAbility(
            id = "water_spirit_attack",
            approachDistance = 5.0,
            closeRangeDamage = 5.0,
            farRangeOrbDamage = 3.5,
            sharedCooldownTicks = 120L
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
