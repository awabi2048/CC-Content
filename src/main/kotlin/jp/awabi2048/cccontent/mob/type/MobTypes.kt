package jp.awabi2048.cccontent.mob.type

import com.destroystokyo.paper.entity.ai.VanillaGoal
import jp.awabi2048.cccontent.mob.CustomMobRuntime
import jp.awabi2048.cccontent.mob.MobRuntimeContext
import jp.awabi2048.cccontent.mob.MobSpawnContext
import jp.awabi2048.cccontent.mob.ability.AreaEffectPulseAbility
import jp.awabi2048.cccontent.mob.ability.ArmorMagnetPullAbility
import jp.awabi2048.cccontent.mob.ability.BoomerangAbility
import jp.awabi2048.cccontent.mob.ability.BackstepAbility
import jp.awabi2048.cccontent.mob.ability.BlazeSoundControllerAbility
import jp.awabi2048.cccontent.mob.ability.BlazePackGapDashAbility
import jp.awabi2048.cccontent.mob.ability.BlazeVolleyAbility
import jp.awabi2048.cccontent.mob.ability.ClimbingLeapAbility
import jp.awabi2048.cccontent.mob.ability.DashToTargetOffsetAbility
import jp.awabi2048.cccontent.mob.ability.DrownedAquaticPursuitAbility
import jp.awabi2048.cccontent.mob.ability.GenericBeamAbility
import jp.awabi2048.cccontent.mob.ability.GuardianBeamAbility
import jp.awabi2048.cccontent.mob.ability.GuardianSpineShotAbility
import jp.awabi2048.cccontent.mob.ability.GuardianWaterDiveSplashAbility
import jp.awabi2048.cccontent.mob.ability.LeapAbility
import jp.awabi2048.cccontent.mob.ability.LightningBranchAbility
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
import jp.awabi2048.cccontent.mob.ability.StealthFangAbility
import jp.awabi2048.cccontent.mob.ability.SplitOnDeathAbility
import jp.awabi2048.cccontent.mob.ability.TridentThrowAbility
import jp.awabi2048.cccontent.mob.ability.WeaponThrowAbility
import jp.awabi2048.cccontent.mob.ability.WaterSpiritAbility
import jp.awabi2048.cccontent.mob.ability.WeaponSwapAbility
import jp.awabi2048.cccontent.mob.ability.BuffEffectEntry
import jp.awabi2048.cccontent.mob.ability.GreatFrogAbility
import jp.awabi2048.cccontent.mob.ability.BatSwoopAbility
import jp.awabi2048.cccontent.mob.ability.AshenSpiritAbility
import jp.awabi2048.cccontent.mob.ability.WitchRetreatBuffAbility
import jp.awabi2048.cccontent.mob.ability.MagmaLandingBurstAbility
import jp.awabi2048.cccontent.mob.ability.MagmaStageDeathAbility
import jp.awabi2048.cccontent.mob.ability.TriFlameShotAbility
import jp.awabi2048.cccontent.mob.ability.WitherBoomerangAbility
import jp.awabi2048.cccontent.mob.ability.EndermanPhaseDefenseAbility
import jp.awabi2048.cccontent.mob.ability.EndermanRayTeleportAbility
import jp.awabi2048.cccontent.mob.ability.EndermanStrikeWarpAbility
import jp.awabi2048.cccontent.mob.ability.EndermanAmbientParticleAbility
import jp.awabi2048.cccontent.mob.ability.EndermanBackstabTeleportAbility
import jp.awabi2048.cccontent.mob.ability.EndermanMistDelayTeleportAbility
import jp.awabi2048.cccontent.mob.ability.EndermitePoisonAssaultAbility
import jp.awabi2048.cccontent.mob.ability.ShulkerCarrierArtilleryAbility
import jp.awabi2048.cccontent.mob.ability.ShulkerBurstShotAbility
import jp.awabi2048.cccontent.mob.ability.ShulkerCloseDefenseBarrageAbility
import jp.awabi2048.cccontent.mob.ability.ShulkerProximitySparkZoneAbility
import jp.awabi2048.cccontent.mob.ability.ShulkerEndRodLaserAbility
import jp.awabi2048.cccontent.mob.ability.ShulkerSniperShotAbility
import jp.awabi2048.cccontent.mob.ability.ConeAttackAbility
import jp.awabi2048.cccontent.mob.ability.EnderEyeBeamOrbitAbility
import jp.awabi2048.cccontent.mob.ability.EnderEyeHunterAbility
import jp.awabi2048.cccontent.mob.ability.EnderEyeSummonerAbility
import jp.awabi2048.cccontent.mob.ability.FloatingHeadVisualAbility
import jp.awabi2048.cccontent.mob.ability.StationaryTurretVisualAbility
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.enchantments.Enchantment
import org.bukkit.Material
import org.bukkit.entity.EntityType
import org.bukkit.entity.Creeper
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

class RaidenCreeperMobType : EquipmentMobType(
    id = "creeper_raiden",
    baseEntityType = EntityType.CREEPER,
    abilities = listOf(
        LightningBranchAbility(
            id = "creeper_raiden_lightning",
            boltCount = 4,
            damagedBoltCount = 8,
            cooldownTicks = 20L,
            minRange = 0.0,
            maxRange = 5.0,
            preCastTicks = 6L,
            postCastFreezeTicks = 6L,
            branchProbability = 0.35,
            branchLengthDecay = 0.65,
            damageMultiplier = 2.0
        )
    )
) {
    override fun onSpawn(context: MobSpawnContext, runtime: CustomMobRuntime?) {
        super.onSpawn(context, runtime)
        val creeper = context.entity as? Creeper ?: return
        Bukkit.getMobGoals().removeGoal(creeper, VanillaGoal.CREEPER_SWELL)
        creeper.isPowered = true
        creeper.explosionRadius = 0
        creeper.maxFuseTicks = Int.MAX_VALUE
        creeper.fuseTicks = 0
        creeper.isIgnited = false
    }

    override fun onTick(context: MobRuntimeContext, runtime: CustomMobRuntime?) {
        super.onTick(context, runtime)
        val creeper = context.entity as? Creeper ?: return
        creeper.fuseTicks = 0
        creeper.isIgnited = false
    }
}

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
        PlayerTargetAssistAbility(id = "guardian_normal_target_assist"),
        GuardianBeamAbility(
            id = "guardian_normal_beam",
            cooldownTicks = 90L,
            chargeTicks = 30L,
            minRange = 6.0,
            maxRange = 16.0,
            directDamageMultiplier = 1.0,
            directBonusDamage = 1.5,
            explosionRadius = 0.0,
            directKnockback = 1.5,
            splashKnockback = 0.0,
            chargePulseIntervalTicks = 40L,
            chargePulseDamage = 2.0
        ),
        GuardianSpineShotAbility(id = "guardian_normal_spine_shot", cooldownTicks = 60L, spikeScale = 1.0f),
        GuardianWaterDiveSplashAbility(id = "guardian_normal_water_dive", jumpCooldownTicks = 70L)
    )
)

class GuardianSmallMobType : EquipmentMobType(
    id = "guardian_small",
    baseEntityType = EntityType.GUARDIAN,
    abilities = listOf(
        PlayerTargetAssistAbility(id = "guardian_small_target_assist"),
        GuardianBeamAbility(
            id = "guardian_small_beam",
            cooldownTicks = 70L,
            chargeTicks = 24L,
            minRange = 6.0,
            maxRange = 14.0,
            directDamageMultiplier = 0.75,
            directBonusDamage = 1.0,
            explosionRadius = 0.0,
            directKnockback = 1.25,
            splashKnockback = 0.0,
            chargePulseIntervalTicks = 40L,
            chargePulseDamage = 1.5
        ),
        GuardianSpineShotAbility(
            id = "guardian_small_spine_shot",
            cooldownTicks = 60L,
            spikeScale = 1.0f,
            damageMultiplier = 0.7,
            triggerMaxDistance = 5.5
        ),
        GuardianWaterDiveSplashAbility(id = "guardian_small_water_dive", jumpCooldownTicks = 70L, splashDamage = 3.0, splashRadius = 3.2)
    )
)

class GuardianBeamBurstMobType : EquipmentMobType(
    id = "guardian_beam_burst",
    baseEntityType = EntityType.ELDER_GUARDIAN,
    abilities = listOf(
        PlayerTargetAssistAbility(id = "guardian_beam_burst_target_assist"),
        GuardianBeamAbility(
            id = "guardian_burst_beam",
            cooldownTicks = 105L,
            chargeTicks = 35L,
            minRange = 6.0,
            maxRange = 18.0,
            directDamageMultiplier = 1.1,
            directBonusDamage = 3.0,
            explosionRadius = 3.6,
            directKnockback = 2.3,
            splashKnockback = 1.2,
            splashVerticalBoost = 0.26,
            chargePulseIntervalTicks = 40L,
            chargePulseDamage = 2.5,
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
        ),
        GuardianSpineShotAbility(
            id = "guardian_beam_burst_spine_shot",
            cooldownTicks = 60L,
            spikeScale = 1.0f,
            damageMultiplier = 1.1,
            triggerMaxDistance = 6.5
        ),
        GuardianWaterDiveSplashAbility(id = "guardian_beam_burst_water_dive", jumpCooldownTicks = 70L, splashDamage = 5.0, splashRadius = 4.2)
    )
)

class GuardianDrainMobType : EquipmentMobType(
    id = "guardian_drain",
    baseEntityType = EntityType.GUARDIAN,
    abilities = listOf(
        PlayerTargetAssistAbility(id = "guardian_drain_target_assist"),
        GuardianBeamAbility(
            id = "guardian_drain_beam",
            cooldownTicks = 95L,
            chargeTicks = 40L,
            minRange = 6.0,
            maxRange = 16.0,
            directDamageMultiplier = 0.8,
            directBonusDamage = 1.5,
            explosionRadius = 0.0,
            directKnockback = 1.5,
            splashKnockback = 0.0,
            chargePulseIntervalTicks = 40L,
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
        ),
        GuardianSpineShotAbility(
            id = "guardian_drain_spine_shot",
            cooldownTicks = 60L,
            spikeScale = 1.0f,
            damageMultiplier = 0.85,
            triggerMaxDistance = 6.0
        ),
        GuardianWaterDiveSplashAbility(id = "guardian_drain_water_dive", jumpCooldownTicks = 70L, splashDamage = 4.5, splashRadius = 4.0)
    )
)

class SkeletonPlainMobType : EquipmentMobType(
    id = "skeleton_plain",
    baseEntityType = EntityType.SKELETON,
    abilities = emptyList(),
    defaultMainHand = Material.BOW
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
    defaultChestplate = Material.CHAINMAIL_CHESTPLATE,
    defaultHelmetTexture = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTk3ZDYwMmIwYWYwNmU5MzRlYjQ2NDQyMzQ5ZTE1ZTFiOGQ1YWFmY2Q1ZmQ4Zjg2ZDc5NWNkNGVlOWNhMDY0ZSJ9fX0="
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

class SilverfishStealthFangMobType : EquipmentMobType(
    id = "silverfish_stealth_fang",
    baseEntityType = EntityType.SILVERFISH,
    abilities = listOf(
        StealthFangAbility(
            id = "silverfish_stealth_fang_ability",
            stealthCycleTicks = 160L,
            stealthDurationTicks = 60
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
    abilities = emptyList(),
    defaultMainHand = Material.BOW
)

class BoggedNormalMobType : EquipmentMobType(
    id = "bogged_normal",
    baseEntityType = EntityType.BOGGED,
    abilities = listOf(
        RangedAttackAbility(
            id = "bogged_normal_ranged",
            effectArrowChance = 0.3,
            effectArrowAmplifier = 1,
            effectArrowType = PotionEffectType.POISON,
            confusionArrowChance = 0.3,
            confusionDurationTicks = 200
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
            effectArrowChance = 0.3,
            effectArrowAmplifier = 1,
            effectArrowType = PotionEffectType.POISON,
            confusionArrowChance = 0.3,
            confusionDurationTicks = 200
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
            homingConfig = MobShootUtil.HomingConfig(),
            effectArrowChance = 0.3,
            effectArrowAmplifier = 1,
            effectArrowType = PotionEffectType.POISON,
            confusionArrowChance = 0.3,
            confusionDurationTicks = 200
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
        RangedAttackAbility(id = "bogged_bow_shield_ranged", effectArrowChance = 0.3, effectArrowAmplifier = 1, effectArrowType = PotionEffectType.POISON, confusionArrowChance = 0.3, confusionDurationTicks = 200),
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

class BoggedBoomerangMobType : EquipmentMobType(
    id = "bogged_boomerang",
    baseEntityType = EntityType.BOGGED,
    abilities = listOf(BoomerangAbility(id = "bogged_boomerang")),
    defaultMainHand = Material.BONE
)

class StrayPlainMobType : EquipmentMobType(
    id = "stray_plain",
    baseEntityType = EntityType.STRAY,
    abilities = emptyList(),
    defaultMainHand = Material.BOW
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

class MagmaCubeLargeMobType : EquipmentMobType(
    id = "magma_cube_large",
    baseEntityType = EntityType.MAGMA_CUBE,
    abilities = listOf(
        ProjectileAndFireImmunityAbility(id = "magma_cube_large_immunity", playSoundOnProjectileBlock = true),
        MagmaLandingBurstAbility(id = "magma_cube_large_landing_burst"),
        LeapAbility(
            id = "magma_cube_large_leap",
            cooldownTicks = 35L,
            minRangeSquared = 4.0,
            maxRange = 10.0,
            horizontalSpeed = 0.75,
            verticalSpeed = 0.34,
            farJumpStartRangeSquared = 36.0,
            farHorizontalSpeed = 1.35,
            farVerticalSpeed = 0.68
        ),
        MagmaStageDeathAbility(
            id = "magma_cube_large_split",
            explosionPower = 0.0f,
            lavaRadius = 2,
            lavaLevels = listOf(7),
            childDefinitionId = "magma_cube_small"
        )
    )
)

class MagmaCubeMediumMobType : EquipmentMobType(
    id = "magma_cube_medium",
    baseEntityType = EntityType.MAGMA_CUBE,
    abilities = listOf(
        ProjectileAndFireImmunityAbility(id = "magma_cube_medium_immunity", playSoundOnProjectileBlock = true),
        MagmaStageDeathAbility(
            id = "magma_cube_medium_death",
            explosionPower = 1.0f,
            lavaRadius = 1,
            lavaLevels = listOf(7),
            childDefinitionId = "magma_cube_mini"
        )
    )
)

class MagmaCubeSmallMobType : EquipmentMobType(
    id = "magma_cube_small",
    baseEntityType = EntityType.MAGMA_CUBE,
    abilities = listOf(
        ProjectileAndFireImmunityAbility(id = "magma_cube_small_immunity", playSoundOnProjectileBlock = true),
        MagmaStageDeathAbility(
            id = "magma_cube_small_death",
            explosionPower = 0.5f,
            lavaRadius = 0,
            lavaLevels = listOf(7),
            childDefinitionId = "magma_cube_mini"
        )
    )
)

class MagmaCubeMiniMobType : EquipmentMobType(
    id = "magma_cube_mini",
    baseEntityType = EntityType.MAGMA_CUBE,
    abilities = listOf(
        ProjectileAndFireImmunityAbility(id = "magma_cube_mini_immunity", playSoundOnProjectileBlock = true),
        MagmaStageDeathAbility(
            id = "magma_cube_mini_death",
            explosionPower = 0.0f,
            lavaRadius = 0,
            lavaLevels = listOf(7)
        )
    )
)

class WitherSkeletonSwapMobType : EquipmentMobType(
    id = "wither_skeleton_swap",
    baseEntityType = EntityType.WITHER_SKELETON,
    abilities = listOf(
        WeaponSwapAbility(
            id = "wither_skeleton_swap_weapon_swap",
            meleeWeapon = Material.STONE_SWORD,
            rangedWeapon = Material.BOW,
            rangedDistanceSquared = 36.0
        ),
        RangedAttackAbility(
            id = "wither_skeleton_swap_ranged",
            retreatOnCloseRange = true,
            rangedWeaponTypes = setOf(Material.BOW)
        )
    ),
    defaultMainHand = Material.STONE_SWORD,
    defaultHelmet = Material.CHAINMAIL_HELMET,
    defaultBoots = Material.CHAINMAIL_BOOTS
)

class WitherSkeletonBowGuardMobType : EquipmentMobType(
    id = "wither_skeleton_bow_guard",
    baseEntityType = EntityType.WITHER_SKELETON,
    abilities = listOf(
        TriFlameShotAbility(
            id = "wither_skeleton_bow_guard_tri_flame",
            spreadAngleDegrees = 10.0,
            retreatOnCloseRange = true,
            retreatMinDistanceSquared = 25.0
        ),
        BackstepAbility(
            id = "wither_skeleton_bow_guard_backstep",
            cooldownTicks = 20L,
            shootArrowOnLand = true,
            horizontalSpeed = 1.8,
            verticalSpeed = 0.9,
            homingConfig = MobShootUtil.HomingConfig()
        ),
        ShieldAbility(id = "wither_skeleton_bow_guard_shield", breakDisablesShieldPermanently = true)
    ),
    defaultMainHand = Material.BOW,
    defaultOffHand = Material.SHIELD,
    defaultHelmet = Material.LEATHER_HELMET,
    defaultChestplate = Material.IRON_CHESTPLATE,
    defaultLeggings = Material.LEATHER_LEGGINGS,
    defaultBoots = Material.LEATHER_BOOTS
)

class WitherSkeletonWitherBoomerangMobType : EquipmentMobType(
    id = "wither_skeleton_wither_boomerang",
    baseEntityType = EntityType.WITHER_SKELETON,
    abilities = listOf(WitherBoomerangAbility(id = "wither_skeleton_wither_boomerang_ability")),
    defaultMainHand = Material.BONE
)

class SlimeSmallMobType : EquipmentMobType(
    id = "slime_merge_small",
    baseEntityType = EntityType.SLIME,
    abilities = listOf(
        ProjectileAndFireImmunityAbility(id = "slime_merge_small_immunity", playSoundOnProjectileBlock = true),
        SlimeMergeAbility(
            id = "slime_merge_small_merge",
            selfDefinitionId = "slime_merge_small",
            mergeOrder = listOf("slime_merge_mini", "slime_merge_small", "slime_merge_large")
        )
    )
)

class SlimeMediumMobType : EquipmentMobType(
    id = "slime_merge_medium",
    baseEntityType = EntityType.SLIME,
    abilities = listOf(
        ProjectileAndFireImmunityAbility(id = "slime_merge_medium_immunity", playSoundOnProjectileBlock = true),
        SlimeMergeAbility(
            id = "slime_merge_medium_merge",
            selfDefinitionId = "slime_merge_medium",
            mergeOrder = listOf("slime_merge_mini", "slime_merge_medium", "slime_merge_large")
        )
    )
)

class SlimeLargeMobType : EquipmentMobType(
    id = "slime_merge_large",
    baseEntityType = EntityType.SLIME,
    abilities = listOf(
        ProjectileAndFireImmunityAbility(id = "slime_merge_large_immunity", playSoundOnProjectileBlock = true),
        SlimeMergeAbility(
            id = "slime_merge_large_merge",
            selfDefinitionId = "slime_merge_large",
            mergeOrder = listOf("slime_merge_mini", "slime_merge_small", "slime_merge_large")
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

class SlimeMiniMobType : EquipmentMobType(
    id = "slime_merge_mini",
    baseEntityType = EntityType.SLIME,
    abilities = listOf(
        ProjectileAndFireImmunityAbility(id = "slime_merge_mini_immunity", playSoundOnProjectileBlock = true)
    )
)

class SlimePoisonMobType : EquipmentMobType(
    id = "slime_poison",
    baseEntityType = EntityType.SLIME,
    abilities = listOf(
        ProjectileAndFireImmunityAbility(id = "slime_poison_immunity", playSoundOnProjectileBlock = true),
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
        ProjectileAndFireImmunityAbility(id = "slime_wither_immunity", playSoundOnProjectileBlock = true),
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
        BlazePackGapDashAbility(id = "blaze_normal_pack_gap_dash", requiredBlazeCount = 3, targetCheckRadius = 8.0),
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
        BlazePackGapDashAbility(id = "blaze_power_pack_gap_dash", requiredBlazeCount = 3, targetCheckRadius = 8.0),
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
        BlazePackGapDashAbility(id = "blaze_rapid_pack_gap_dash", requiredBlazeCount = 3, targetCheckRadius = 8.0),
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
        BlazePackGapDashAbility(id = "blaze_melee_pack_gap_dash", requiredBlazeCount = 3, targetCheckRadius = 8.0),
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
        BlazePackGapDashAbility(id = "blaze_beam_pack_gap_dash", requiredBlazeCount = 3, targetCheckRadius = 8.0),
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

class DrownedUnarmedMobType : EquipmentMobType(
    id = "drowned_unarmed",
    baseEntityType = EntityType.DROWNED,
    abilities = listOf(
        DrownedAquaticPursuitAbility(id = "drowned_unarmed_aquatic_pursuit"),
        PlayerTargetAssistAbility(id = "drowned_unarmed_target_assist"),
        BackstepAbility(
            id = "drowned_unarmed_backstep",
            cooldownTicks = 95L,
            horizontalSpeed = 0.78,
            verticalSpeed = 0.33
        )
    ),
    defaultHelmet = Material.LEATHER_HELMET,
    defaultBoots = Material.LEATHER_BOOTS
)

class DrownedTridentGuardMobType : EquipmentMobType(
    id = "drowned_trident_guard",
    baseEntityType = EntityType.DROWNED,
    abilities = listOf(
        DrownedAquaticPursuitAbility(
            id = "drowned_trident_guard_aquatic_pursuit",
            meleeReachMultiplier = 1.5
        ),
        PlayerTargetAssistAbility(id = "drowned_trident_guard_target_assist"),
        ShieldAbility(id = "drowned_trident_guard_shield", breakDisablesShieldPermanently = true),
        BackstepAbility(
            id = "drowned_trident_guard_backstep",
            cooldownTicks = 100L,
            horizontalSpeed = 0.82,
            verticalSpeed = 0.34
        ),
        TridentThrowAbility(
            id = "drowned_trident_guard_throw",
            throwCooldownTicks = 90L,
            homingConfig = MobShootUtil.HomingConfig(
                accuracy = 0.9,
                turnStrength = 0.2,
                maxTurnDegrees = 14.0
            )
        )
    ),
    defaultMainHand = Material.TRIDENT,
    defaultOffHand = Material.SHIELD,
    defaultHelmet = Material.CHAINMAIL_HELMET,
    defaultChestplate = Material.CHAINMAIL_CHESTPLATE,
    defaultLeggings = Material.CHAINMAIL_LEGGINGS,
    defaultBoots = Material.CHAINMAIL_BOOTS
)

class DrownedRaiderAxeMobType : EquipmentMobType(
    id = "drowned_raider_axe",
    baseEntityType = EntityType.DROWNED,
    abilities = listOf(
        DrownedAquaticPursuitAbility(id = "drowned_raider_axe_aquatic_pursuit"),
        PlayerTargetAssistAbility(id = "drowned_raider_axe_target_assist"),
        LeapAbility(
            id = "drowned_raider_axe_leap",
            cooldownTicks = 70L,
            minRangeSquared = 9.0,
            maxRange = 8.0,
            horizontalSpeed = 1.05,
            verticalSpeed = 0.5
        ),
        BackstepAbility(
            id = "drowned_raider_axe_backstep",
            cooldownTicks = 90L,
            horizontalSpeed = 0.86,
            verticalSpeed = 0.35
        )
    ),
    defaultMainHand = Material.DIAMOND_AXE,
    defaultHelmet = Material.GOLDEN_HELMET,
    defaultBoots = Material.GOLDEN_BOOTS
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

class WaterSpiritEliteMobType : EquipmentMobType(
    id = "water_spirit_elite",
    baseEntityType = EntityType.ALLAY,
    abilities = listOf(
        WaterSpiritAbility(
            id = "water_spirit_elite_attack",
            approachDistance = 5.0,
            closeRangeDamage = 5.0,
            farRangeOrbDamage = 3.5,
            sharedCooldownTicks = 120L,
            orbCount = 6,
            waterColumnExtraAnglesDegrees = listOf(120.0, 240.0),
            enhancedAmbientOrbit = true
        )
    )
)

class AshenSpiritMobType : EquipmentMobType(
    id = "ashen_spirit",
    baseEntityType = EntityType.VEX,
    abilities = listOf(
        AshenSpiritAbility(
            id = "ashen_spirit_attack",
            approachDistance = 5.0,
            closeRangeDamage = 5.0,
            farRangeDashDamage = 3.5,
            sharedCooldownTicks = 120L
        )
    )
)

class GreatFrogMobType : EquipmentMobType(
    id = "frog_big",
    baseEntityType = EntityType.FROG,
    abilities = listOf(
        GreatFrogAbility(id = "frog_big_predator")
    )
)

class WitchNormalMobType : EquipmentMobType(
    id = "witch_normal",
    baseEntityType = EntityType.WITCH,
    abilities = emptyList()
)

class BatVenomMobType : EquipmentMobType(
    id = "bat_venom",
    baseEntityType = EntityType.BAT,
    abilities = listOf(BatSwoopAbility(id = "bat_venom_swoop"))
)

class WitchEliteMobType : EquipmentMobType(
    id = "witch_elite",
    baseEntityType = EntityType.WITCH,
    abilities = listOf(
        WitchRetreatBuffAbility(
            id = "witch_elite_retreat_buff",
            retreatTriggerDistance = 4.0,
            retreatTriggerDurationTicks = 20L,
            retreatSearchDistance = 12.0,
            retreatCooldownTicks = 100L,
            buffRadius = 8.0,
            buffIntervalTicks = 100L,
            buffEffects = listOf(
                BuffEffectEntry(PotionEffectType.SPEED, 120, 0),
                BuffEffectEntry(PotionEffectType.REGENERATION, 120, 0)
            )
        )
    )
)

class EndermanPhaseMobType : EquipmentMobType(
    id = "enderman_phase",
    baseEntityType = EntityType.ENDERMAN,
    abilities = listOf(
        EndermanPhaseDefenseAbility(id = "enderman_phase_defense", evadeRadius = 0.5),
        EndermanAmbientParticleAbility(
            id = "enderman_phase_ambient",
            portalCount = 4,
            dustCount = 4,
            innerColor = Color.fromRGB(118, 57, 197),
            outerColor = Color.fromRGB(37, 12, 70),
            dustSize = 0.88f
        ),
        EndermanRayTeleportAbility(id = "enderman_phase_ray"),
        EndermanStrikeWarpAbility(id = "enderman_phase_strike", swapChance = 0.16)
    )
)

class EndermanDrainMobType : EquipmentMobType(
    id = "enderman_drain",
    baseEntityType = EntityType.ENDERMAN,
    abilities = listOf(
        EndermanPhaseDefenseAbility(id = "enderman_drain_defense", evadeRadius = 0.5),
        EndermanAmbientParticleAbility(
            id = "enderman_drain_ambient",
            portalCount = 5,
            dustCount = 5,
            innerColor = Color.fromRGB(156, 72, 214),
            outerColor = Color.fromRGB(52, 20, 92),
            dustSize = 0.95f
        ),
        EndermanRayTeleportAbility(id = "enderman_drain_ray"),
        EndermanStrikeWarpAbility(id = "enderman_drain_strike", swapChance = 0.24)
    )
)

class EndermanMirrorMobType : EquipmentMobType(
    id = "enderman_mirror",
    baseEntityType = EntityType.ENDERMAN,
    abilities = listOf(
        EndermanPhaseDefenseAbility(id = "enderman_mirror_defense", evadeRadius = 0.5),
        EndermanAmbientParticleAbility(
            id = "enderman_mirror_ambient",
            portalCount = 4,
            dustCount = 6,
            innerColor = Color.fromRGB(180, 95, 228),
            outerColor = Color.fromRGB(73, 29, 126),
            dustSize = 1.0f
        ),
        EndermanRayTeleportAbility(id = "enderman_mirror_ray"),
        EndermanStrikeWarpAbility(id = "enderman_mirror_strike", swapChance = 0.32)
    )
)

class EndermanEyeSummonerMobType : EquipmentMobType(
    id = "enderman_eye_summoner",
    baseEntityType = EntityType.ENDERMAN,
    abilities = listOf(
        EndermanPhaseDefenseAbility(id = "enderman_eye_summoner_defense", evadeRadius = 0.5),
        EndermanAmbientParticleAbility(
            id = "enderman_eye_summoner_ambient",
            portalCount = 6,
            dustCount = 7,
            innerColor = Color.fromRGB(204, 116, 240),
            outerColor = Color.fromRGB(92, 37, 153),
            dustSize = 1.05f
        ),
        EndermanRayTeleportAbility(id = "enderman_eye_summoner_ray"),
        EndermanStrikeWarpAbility(id = "enderman_eye_summoner_strike", swapChance = 0.2),
        EnderEyeSummonerAbility(id = "enderman_eye_summoner_spawn")
    )
)

class ShulkerRhythmMobType : EquipmentMobType(
    id = "shulker_rhythm",
    baseEntityType = EntityType.SHULKER,
    abilities = listOf(
        ShulkerCarrierArtilleryAbility(
            id = "shulker_rhythm_carrier",
            teleportIntervalTicks = 180L,
            prioritizeBarrierMarkerOnFinalWave = false
        ),
        ShulkerBurstShotAbility(id = "shulker_rhythm_burst", cooldownTicks = 40L, burstCount = 3, burstIntervalTicks = 5L)
    )
)

class ShulkerLaserMobType : EquipmentMobType(
    id = "shulker_laser",
    baseEntityType = EntityType.SHULKER,
    abilities = listOf(
        ShulkerCarrierArtilleryAbility(
            id = "shulker_laser_carrier",
            teleportIntervalTicks = 220L,
            prioritizeBarrierMarkerOnFinalWave = false
        ),
        ShulkerEndRodLaserAbility(
            id = "shulker_laser_end_rod",
            cooldownTicks = 40L,
            burstCount = 3,
            burstIntervalTicks = 5L,
            allowBurstAlternative = true,
            altBurstCount = 3,
            altBurstIntervalTicks = 5L
        )
    )
)

class ShulkerDisruptorMobType : EquipmentMobType(
    id = "shulker_disruptor",
    baseEntityType = EntityType.SHULKER,
    abilities = listOf(
        ShulkerCarrierArtilleryAbility(
            id = "shulker_disruptor_carrier",
            teleportIntervalTicks = 180L,
            prioritizeBarrierMarkerOnFinalWave = true
        ),
        ShulkerBurstShotAbility(id = "shulker_disruptor_burst", cooldownTicks = 40L, burstCount = 3, burstIntervalTicks = 5L)
    )
)

class EnderEyeHunterMobType : EquipmentMobType(
    id = "ender_eye_hunter",
    baseEntityType = EntityType.VEX,
    abilities = listOf(
        EnderEyeHunterAbility(
            id = "ender_eye_hunter_ai",
            orbitRadius = 6.0,
            orbitHeightOffset = 2.2,
            shotCooldownTicks = 46L,
            projectileSpeedPerTick = 0.7734375,
            shotWayCount = 3,
            shotSpreadDegrees = 60.0,
            headTextureValue = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDM5ZjFjMGRkY2Y1MzgzM2JhYzVmYmY1NzcxNWY3YzI1M2VlZmQyODcyZmYyN2U0YTg5M2JlMzA1MjliYzY4NSJ9fX0=",
            ambientParticle = Particle.TRIAL_SPAWNER_DETECTION,
            ambientParticleCount = 3,
            ambientParticleRadius = 0.5
        )
    )
)

class EnderEyeOrbitMobType : EquipmentMobType(
    id = "ender_eye_orbit",
    baseEntityType = EntityType.VEX,
    abilities = listOf(
        EnderEyeHunterAbility(
            id = "ender_eye_orbit_ai",
            orbitRadius = 7.2,
            orbitHeightOffset = 2.4,
            shotCooldownTicks = 54L,
            projectileSpeedPerTick = 0.6875,
            ambientParticle = Particle.TRIAL_SPAWNER_DETECTION_OMINOUS,
            ambientParticleCount = 3,
            ambientParticleRadius = 0.5
        )
    )
)

class EndermanMistDelayMobType : EquipmentMobType(
    id = "enderman_mist_delay",
    baseEntityType = EntityType.ENDERMAN,
    abilities = listOf(
        EndermanAmbientParticleAbility(
            id = "enderman_mist_delay_ambient",
            portalCount = 6,
            dustCount = 4,
            innerColor = Color.fromRGB(188, 170, 220),
            outerColor = Color.fromRGB(80, 74, 96),
            dustSize = 0.95f
        ),
        EndermanMistDelayTeleportAbility(id = "enderman_mist_delay_teleport"),
        EndermanStrikeWarpAbility(id = "enderman_mist_delay_strike", swapChance = 0.18)
    )
)

class EndermanSmallBackstabMobType : EquipmentMobType(
    id = "enderman_small_backstab",
    baseEntityType = EntityType.ENDERMAN,
    abilities = listOf(
        EndermanAmbientParticleAbility(
            id = "enderman_small_backstab_ambient",
            portalCount = 3,
            dustCount = 3,
            innerColor = Color.fromRGB(164, 112, 230),
            outerColor = Color.fromRGB(52, 24, 96),
            dustSize = 0.72f
        ),
        EndermanBackstabTeleportAbility(id = "enderman_small_backstab_teleport", triggerChance = 0.32),
        EndermanPhaseDefenseAbility(id = "enderman_small_backstab_defense", evadeRadius = 0.6)
    )
)

class EndermitePoisonMobType : EquipmentMobType(
    id = "endermite_poison",
    baseEntityType = EntityType.ENDERMITE,
    abilities = listOf(
        EndermitePoisonAssaultAbility(id = "endermite_poison_assault")
    )
)

class ShulkerTurretSniperMobType : EquipmentMobType(
    id = "shulker_turret_sniper",
    baseEntityType = EntityType.SHULKER,
    abilities = listOf(
        ShulkerCarrierArtilleryAbility(
            id = "shulker_turret_sniper_carrier",
            teleportIntervalTicks = 170L,
            prioritizeBarrierMarkerOnFinalWave = false
        ),
        ShulkerProximitySparkZoneAbility(id = "shulker_turret_sniper_zone_spark"),
        ShulkerSniperShotAbility(id = "shulker_turret_sniper_shot")
    )
)

class ShulkerTurretBarrageMobType : EquipmentMobType(
    id = "shulker_turret_barrage",
    baseEntityType = EntityType.SHULKER,
    abilities = listOf(
        ShulkerCarrierArtilleryAbility(
            id = "shulker_turret_barrage_carrier",
            teleportIntervalTicks = 190L,
            prioritizeBarrierMarkerOnFinalWave = false
        ),
        ShulkerProximitySparkZoneAbility(id = "shulker_turret_barrage_zone_spark"),
        ShulkerCloseDefenseBarrageAbility(id = "shulker_turret_barrage_barrage")
    )
)

class EnderEyeBeamMobType : EquipmentMobType(
    id = "ender_eye_beam",
    baseEntityType = EntityType.GUARDIAN,
    abilities = listOf(
        EnderEyeBeamOrbitAbility(id = "ender_eye_beam_orbit", orbitRadius = 3.0),
        GuardianBeamAbility(
            id = "ender_eye_beam_guardian_laser",
            cooldownTicks = 72L,
            chargeTicks = 22L,
            minRange = 2.0,
            maxRange = 15.0,
            directDamageMultiplier = 0.0,
            directBonusDamage = 0.0,
            directKnockback = 0.0,
            splashKnockback = 0.0,
            chargePulseIntervalTicks = 10L,
            chargePulseDamage = 2.0,
            chargePulseSound = Sound.ENTITY_GUARDIAN_ATTACK,
            chargePulseSoundVolume = 0.6f,
            chargePulseSoundPitch = 1.4f,
            chargeParticle = Particle.PORTAL
        )
    )
)

private const val ENDER_GHOST_HEAD_TEXTURE =
    "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjg4MzM0MjJmZDBlYmFhMzMxNWU5NTEwMGE5Y2IyOTA4OWQ0N2FkYzQ5OWMzMzA1MWVhOGE5OGM4MDc2ZGFjYSJ9fX0="

private const val ENDER_SHOOTER_HEAD_TEXTURE =
    "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTg4YjFjZDk1NzQ2NzJlOGUzMjYyZjIxMGMwZGRkYmMwODJlYTc1NjllOGU3MGYwYzA3YjRiZWU3NWUzMmY2MiJ9fX0="

class EnderGhostMobType : EquipmentMobType(
    id = "ender_ghost",
    baseEntityType = EntityType.VEX,
    abilities = listOf(
        FloatingHeadVisualAbility(
            id = "ender_ghost_visual",
            headTextureValue = ENDER_GHOST_HEAD_TEXTURE
        ),
        ConeAttackAbility(
            id = "ender_ghost_cone_attack",
            range = 4.0,
            coneHalfAngleDegrees = 30.0,
            cooldownTicks = 20L,
            damageMultiplier = 1.0
        )
    )
)

class EnderShooterMobType : EquipmentMobType(
    id = "ender_shooter",
    baseEntityType = EntityType.GUARDIAN,
    abilities = listOf(
        StationaryTurretVisualAbility(
            id = "ender_shooter_visual",
            headTextureValue = ENDER_SHOOTER_HEAD_TEXTURE
        ),
        GuardianBeamAbility(
            id = "ender_shooter_guardian_laser",
            cooldownTicks = 80L,
            chargeTicks = 30L,
            minRange = 2.0,
            maxRange = 16.0,
            directDamageMultiplier = 0.0,
            directBonusDamage = 0.0,
            directKnockback = 0.8,
            splashKnockback = 0.0,
            chargePulseIntervalTicks = 10L,
            chargePulseDamage = 2.5,
            chargePulseSound = Sound.ENTITY_GUARDIAN_ATTACK,
            chargePulseSoundVolume = 0.7f,
            chargePulseSoundPitch = 1.2f,
            chargeParticle = Particle.PORTAL
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
