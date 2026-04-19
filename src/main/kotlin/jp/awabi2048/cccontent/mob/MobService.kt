package jp.awabi2048.cccontent.mob

import jp.awabi2048.cccontent.features.arena.ArenaI18n
import jp.awabi2048.cccontent.mob.type.HuskBowOnlyMobType
import jp.awabi2048.cccontent.mob.type.HuskBowSwapMobType
import jp.awabi2048.cccontent.mob.type.HuskLeapOnlyMobType
import jp.awabi2048.cccontent.mob.type.HuskNormalMobType
import jp.awabi2048.cccontent.mob.type.HuskShieldOnlyMobType
import jp.awabi2048.cccontent.mob.type.HuskWeakeningAuraMobType
import jp.awabi2048.cccontent.mob.type.BoggedBowShieldMobType
import jp.awabi2048.cccontent.mob.type.BoggedCurveBackstepMobType
import jp.awabi2048.cccontent.mob.type.BoggedNormalMobType
import jp.awabi2048.cccontent.mob.type.BoggedPlainMobType
import jp.awabi2048.cccontent.mob.type.BoggedRapidShotMobType
import jp.awabi2048.cccontent.mob.type.BoggedWeaponThrowCloseMobType
import jp.awabi2048.cccontent.mob.type.BoggedBoomerangMobType
import jp.awabi2048.cccontent.mob.type.BatVenomMobType
import jp.awabi2048.cccontent.mob.type.AshenSpiritMobType
import jp.awabi2048.cccontent.mob.type.BlazeBeamMobType
import jp.awabi2048.cccontent.mob.type.BlazeMeleeMobType
import jp.awabi2048.cccontent.mob.type.BlazeNormalMobType
import jp.awabi2048.cccontent.mob.type.BlazePowerMobType
import jp.awabi2048.cccontent.mob.type.BlazeRapidMobType
import jp.awabi2048.cccontent.mob.type.DrownedRaiderAxeMobType
import jp.awabi2048.cccontent.mob.type.DrownedTridentGuardMobType
import jp.awabi2048.cccontent.mob.type.DrownedUnarmedMobType
import jp.awabi2048.cccontent.mob.type.DesertTempleSandGolemMobType
import jp.awabi2048.cccontent.mob.type.GuardianBeamBurstMobType
import jp.awabi2048.cccontent.mob.type.GuardianDrainMobType
import jp.awabi2048.cccontent.mob.type.GuardianNormalMobType
import jp.awabi2048.cccontent.mob.type.GuardianSmallMobType
import jp.awabi2048.cccontent.mob.type.GreatFrogMobType
import jp.awabi2048.cccontent.mob.type.IronGolemMagnetMobType
import jp.awabi2048.cccontent.mob.type.IronGolemNormalMobType
import jp.awabi2048.cccontent.mob.type.SilverfishBigPoisonMobType
import jp.awabi2048.cccontent.mob.type.SilverfishPlainMobType
import jp.awabi2048.cccontent.mob.type.SilverfishStealthFangMobType
import jp.awabi2048.cccontent.mob.type.SlimeLargeMobType
import jp.awabi2048.cccontent.mob.type.SlimeMediumMobType
import jp.awabi2048.cccontent.mob.type.SlimeMiniMobType
import jp.awabi2048.cccontent.mob.type.SlimePoisonMobType
import jp.awabi2048.cccontent.mob.type.SlimeSmallMobType
import jp.awabi2048.cccontent.mob.type.SlimeWitherMobType
import jp.awabi2048.cccontent.mob.type.MagmaCubeLargeMobType
import jp.awabi2048.cccontent.mob.type.MagmaCubeMiniMobType
import jp.awabi2048.cccontent.mob.type.MagmaCubeMediumMobType
import jp.awabi2048.cccontent.mob.type.MagmaCubeSmallMobType
import jp.awabi2048.cccontent.mob.type.SkeletonPlainMobType
import jp.awabi2048.cccontent.mob.type.SkeletonBoomerangMobType
import jp.awabi2048.cccontent.mob.type.SkeletonCurveShotMobType
import jp.awabi2048.cccontent.mob.type.SkeletonEffectArrowMobType
import jp.awabi2048.cccontent.mob.type.SkeletonFastArrowMobType
import jp.awabi2048.cccontent.mob.type.SkeletonBowShieldMobType
import jp.awabi2048.cccontent.mob.type.SkeletonCurveBackstepMobType
import jp.awabi2048.cccontent.mob.type.SkeletonNormalMobType
import jp.awabi2048.cccontent.mob.type.SkeletonRapidShotMobType
import jp.awabi2048.cccontent.mob.type.SkeletonShieldMobType
import jp.awabi2048.cccontent.mob.type.SkeletonWeaponThrowCloseMobType
import jp.awabi2048.cccontent.mob.type.RaidenCreeperMobType
import jp.awabi2048.cccontent.mob.type.SpiderBroodlingMobType
import jp.awabi2048.cccontent.mob.type.SpiderBroodmotherMobType
import jp.awabi2048.cccontent.mob.type.SpiderFerociousMobType
import jp.awabi2048.cccontent.mob.type.SpiderPlainMobType
import jp.awabi2048.cccontent.mob.type.SpiderStealthMobType
import jp.awabi2048.cccontent.mob.type.SpiderSwiftMobType
import jp.awabi2048.cccontent.mob.type.SpiderVenomFrenzyMobType
import jp.awabi2048.cccontent.mob.type.StrayBowShieldMobType
import jp.awabi2048.cccontent.mob.type.StrayCurveBackstepMobType
import jp.awabi2048.cccontent.mob.type.StrayNormalMobType
import jp.awabi2048.cccontent.mob.type.StrayPlainMobType
import jp.awabi2048.cccontent.mob.type.StrayRapidShotMobType
import jp.awabi2048.cccontent.mob.type.StrayWeaponThrowCloseMobType
import jp.awabi2048.cccontent.mob.type.WitherSkeletonBowGuardMobType
import jp.awabi2048.cccontent.mob.type.WitherSkeletonSwapMobType
import jp.awabi2048.cccontent.mob.type.WitherSkeletonWitherBoomerangMobType
import jp.awabi2048.cccontent.mob.type.WitherKnightMobType
import jp.awabi2048.cccontent.mob.type.EndWitherSentinelMobType
import jp.awabi2048.cccontent.mob.type.WaterSpiritEliteMobType
import jp.awabi2048.cccontent.mob.type.WaterSpiritMobType
import jp.awabi2048.cccontent.mob.type.WitchEliteMobType
import jp.awabi2048.cccontent.mob.type.WitchNormalMobType
import jp.awabi2048.cccontent.mob.type.EndermanPhaseMobType
import jp.awabi2048.cccontent.mob.type.EndermanDrainMobType
import jp.awabi2048.cccontent.mob.type.EndermanMirrorMobType
import jp.awabi2048.cccontent.mob.type.EndermanEyeSummonerMobType
import jp.awabi2048.cccontent.mob.type.EndermanMistDelayMobType
import jp.awabi2048.cccontent.mob.type.EndermanSmallBackstabMobType
import jp.awabi2048.cccontent.mob.type.EndermanVoidCarrierMobType
import jp.awabi2048.cccontent.mob.type.EndermanRiftCarrierMobType
import jp.awabi2048.cccontent.mob.type.EndermanGraveCarrierMobType
import jp.awabi2048.cccontent.mob.type.EndermitePoisonMobType
import jp.awabi2048.cccontent.mob.type.ShulkerRhythmMobType
import jp.awabi2048.cccontent.mob.type.ShulkerLaserMobType
import jp.awabi2048.cccontent.mob.type.ShulkerDisruptorMobType
import jp.awabi2048.cccontent.mob.type.ShulkerMimicMobType
import jp.awabi2048.cccontent.mob.type.EnderEyeHunterMobType
import jp.awabi2048.cccontent.mob.type.EnderEyeOrbitMobType
import jp.awabi2048.cccontent.mob.type.EnderEyeBeamMobType
import jp.awabi2048.cccontent.mob.type.EnderGhostMobType
import jp.awabi2048.cccontent.mob.type.EnderShooterMobType
import jp.awabi2048.cccontent.mob.type.EndCrystalSentinelMobType
import jp.awabi2048.cccontent.mob.type.WitherGhostMobType
import jp.awabi2048.cccontent.mob.type.ZombieBowOnlyMobType
import jp.awabi2048.cccontent.mob.type.ZombieBowSwapMobType
import jp.awabi2048.cccontent.mob.type.ZombieLeapOnlyMobType
import jp.awabi2048.cccontent.mob.type.ZombieNormalMobType
import jp.awabi2048.cccontent.mob.type.ZombieShieldOnlyMobType
import jp.awabi2048.cccontent.mob.ability.BoomerangService
import jp.awabi2048.cccontent.mob.ability.HomingArrowService
import jp.awabi2048.cccontent.mob.ability.ThrownWeaponService
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.FluidCollisionMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.EntityType
import org.bukkit.entity.Entity
import org.bukkit.entity.AbstractArrow
import org.bukkit.entity.Ageable
import org.bukkit.entity.EvokerFangs
import org.bukkit.entity.Fireball
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.entity.ShulkerBullet
import org.bukkit.entity.SmallFireball
import org.bukkit.entity.Slime
import org.bukkit.entity.LingeringPotion
import org.bukkit.entity.ThrownPotion
import org.bukkit.entity.Zombie
import org.bukkit.entity.Mob
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityCombustEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.entity.ProjectileLaunchEvent
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.entity.EntityTransformEvent
import org.bukkit.event.entity.EntityTeleportEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.PotionMeta
import org.bukkit.potion.PotionType
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Vector
import org.bukkit.damage.DamageSource
import org.bukkit.damage.DamageType
import kotlin.random.Random
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.WeakHashMap

class MobService(private val plugin: JavaPlugin) {
    private data class WitchEffectEntry(
        val effectType: PotionEffectType,
        val durationTicks: Int,
        val amplifier: Int,
        val color: Color
    )

    companion object {
        private val instances = WeakHashMap<JavaPlugin, MobService>()
        private const val VISUAL_SHULKER_BULLET_TAG = "cc.mob.visual_shulker_bullet"
        private const val STEALTH_FANG_KNOCKUP = 0.55

        fun getInstance(plugin: JavaPlugin): MobService? {
            synchronized(instances) {
                return instances[plugin]
            }
        }
    }

    private data class SessionLoadMetrics(
        var accumulatedCpuNanos: Long = 0L,
        var projectileLaunchCount: Int = 0,
        var activeMobCount: Int = 0,
        var emaCustomMspt: Double = 0.0,
        var emaActiveMobCount: Double = 0.0,
        var emaProjectilesPerSecond: Double = 0.0,
        var lastSnapshot: MobLoadSnapshot = MobLoadSnapshot.DEFAULT,
        var lastUpdatedMillis: Long = System.currentTimeMillis(),
        var lastSeenMillis: Long = System.currentTimeMillis()
    )

    private val LOAD_UPDATE_INTERVAL_MILLIS = 1000L
    private val LOAD_METRICS_TTL_MILLIS = 60_000L
    private val LOAD_EMA_ALPHA = 0.35

    private val CPU_PRESSURE_MSPT = 2.5
    private val MOB_PRESSURE_COUNT = 48.0
    private val PROJECTILE_PRESSURE_PER_SEC = 24.0

    private val CPU_WEIGHT = 0.65
    private val MOB_WEIGHT = 0.25
    private val PROJECTILE_WEIGHT = 0.10

    private val customMobTypes = mutableMapOf<String, MobType>()
    private val customEntityMobTypes = mutableMapOf<String, EntityMobType>()
    private val activeMobs = mutableMapOf<UUID, ActiveMob>()
    private val activeEntityMobs = mutableMapOf<UUID, ActiveEntityMob>()
    private val definitions = mutableMapOf<String, MobDefinition>()
    private val sessionLoadMetrics = mutableMapOf<String, SessionLoadMetrics>()
    private val managedProjectilePermits = mutableMapOf<UUID, Int>()
    private val directDamagePermits = mutableMapOf<Pair<UUID, UUID>, Int>()
    private val mobTypeKey = NamespacedKey(plugin, "mob_type_id")
    private val mobDefinitionKey = NamespacedKey(plugin, "mob_definition_id")
    private val mobFeatureKey = NamespacedKey(plugin, "mob_feature_id")
    private val customMobKey = NamespacedKey(plugin, "is_custom_mob")
    private val skeletonEffectArrowKey = NamespacedKey(plugin, "skeleton_effect_arrow")
    private val skeletonEffectArrowTypeKey = NamespacedKey(plugin, "skeleton_effect_arrow_type")
    private val skeletonEffectArrowAmplifierKey = NamespacedKey(plugin, "skeleton_effect_arrow_amp")
    private val skeletonEffectArrowDurationKey = NamespacedKey(plugin, "skeleton_effect_arrow_duration")
    private val mobShotArrowKey = NamespacedKey(plugin, "mob_shot_arrow")
    private val customProjectileDamageKey = NamespacedKey(plugin, "custom_projectile_damage")
    private val blazeDirectHitAppliedKey = NamespacedKey(plugin, "blaze_direct_hit_applied")
    private val downgradeAoEProtectUntilTickKey = NamespacedKey(plugin, "downgrade_aoe_protect_until")
    private val stealthFangKey = NamespacedKey(plugin, "stealth_fang")
    private val shulkerLevitationBulletKey = NamespacedKey(plugin, "shulker_levitation_bullet")
    private val shulkerLevitationAmplifierKey = NamespacedKey(plugin, "shulker_levitation_bullet_amp")
    private val shulkerLevitationDurationKey = NamespacedKey(plugin, "shulker_levitation_bullet_duration")
    private var tickTask: BukkitTask? = null

    private val ELITE_WITCH_MAGIC_CONVERT_CHANCE = 0.3
    private val ELITE_WITCH_MAGIC_TARGET_SEARCH_RADIUS = 24.0
    private val ELITE_WITCH_MAGIC_PROJECTILE_COUNT = 3
    private val ELITE_WITCH_MAGIC_STEP_DISTANCE = 4.0 / 20.0
    private val ELITE_WITCH_MAGIC_ORIGIN_SPREAD = 0.4
    private val ELITE_WITCH_MAGIC_HOMING_END_TICKS = 50L
    private val ELITE_WITCH_MAGIC_HIT_DISTANCE = 1.1
    private val ELITE_WITCH_MAGIC_HOMING_STEER_FACTOR = 0.18
    private val ELITE_WITCH_MAGIC_MAX_LIFETIME_TICKS = 120L
    private val ELITE_WITCH_MAGIC_SOUND_PITCH = 1.666f
    private val ELITE_WITCH_DEFAULT_EFFECT_COLOR = Color.fromRGB(140, 60, 175)
    private val ELITE_WITCH_EFFECT_AMPLIFIER_BONUS = 1
    private val eliteWitchEffectTable: List<WitchEffectEntry> = buildEliteWitchEffectTable()
    private val DOWNGRADE_AOE_PROTECTION_TICKS = 15L
    private val DOWNGRADE_AOE_DAMAGE_CAUSES = setOf(
        EntityDamageEvent.DamageCause.ENTITY_EXPLOSION,
        EntityDamageEvent.DamageCause.BLOCK_EXPLOSION,
        EntityDamageEvent.DamageCause.FIRE,
        EntityDamageEvent.DamageCause.FIRE_TICK,
        EntityDamageEvent.DamageCause.LAVA,
        EntityDamageEvent.DamageCause.HOT_FLOOR,
        EntityDamageEvent.DamageCause.CAMPFIRE
    )

    init {
        synchronized(instances) {
            instances[plugin] = this
        }
        registerMobType(ZombieNormalMobType())
        registerMobType(ZombieLeapOnlyMobType())
        registerMobType(ZombieBowOnlyMobType())
        registerMobType(ZombieBowSwapMobType())
        registerMobType(ZombieShieldOnlyMobType())
        registerMobType(HuskNormalMobType())
        registerMobType(HuskLeapOnlyMobType())
        registerMobType(HuskBowOnlyMobType())
        registerMobType(HuskBowSwapMobType())
        registerMobType(HuskShieldOnlyMobType())
        registerMobType(HuskWeakeningAuraMobType())
        registerMobType(RaidenCreeperMobType())
        registerMobType(SkeletonNormalMobType())
        registerMobType(SkeletonPlainMobType())
        registerMobType(SkeletonFastArrowMobType())
        registerMobType(SkeletonRapidShotMobType())
        registerMobType(SkeletonEffectArrowMobType())
        registerMobType(SkeletonCurveShotMobType())
        registerMobType(SkeletonShieldMobType())
        registerMobType(SkeletonBoomerangMobType())
        registerMobType(SkeletonCurveBackstepMobType())
        registerMobType(SkeletonBowShieldMobType())
        registerMobType(SkeletonWeaponThrowCloseMobType())
        registerMobType(SpiderPlainMobType())
        registerMobType(SpiderStealthMobType())
        registerMobType(SpiderBroodmotherMobType())
        registerMobType(SpiderBroodlingMobType())
        registerMobType(SpiderSwiftMobType())
        registerMobType(SpiderVenomFrenzyMobType())
        registerMobType(SpiderFerociousMobType())
        registerMobType(SilverfishPlainMobType())
        registerMobType(SilverfishBigPoisonMobType())
        registerMobType(SilverfishStealthFangMobType())
        registerMobType(IronGolemNormalMobType())
        registerMobType(IronGolemMagnetMobType())
        registerMobType(DesertTempleSandGolemMobType())
        registerMobType(GuardianNormalMobType())
        registerMobType(GuardianSmallMobType())
        registerMobType(GuardianBeamBurstMobType())
        registerMobType(GuardianDrainMobType())
        registerMobType(BoggedPlainMobType())
        registerMobType(BoggedNormalMobType())
        registerMobType(BoggedRapidShotMobType())
        registerMobType(BoggedCurveBackstepMobType())
        registerMobType(BoggedBowShieldMobType())
        registerMobType(BoggedWeaponThrowCloseMobType())
        registerMobType(BoggedBoomerangMobType())
        registerMobType(StrayPlainMobType())
        registerMobType(StrayNormalMobType())
        registerMobType(StrayRapidShotMobType())
        registerMobType(StrayCurveBackstepMobType())
        registerMobType(StrayBowShieldMobType())
        registerMobType(StrayWeaponThrowCloseMobType())
        registerMobType(MagmaCubeLargeMobType())
        registerMobType(MagmaCubeMediumMobType())
        registerMobType(MagmaCubeSmallMobType())
        registerMobType(MagmaCubeMiniMobType())
        registerMobType(WitherSkeletonSwapMobType())
        registerMobType(WitherSkeletonBowGuardMobType())
        registerMobType(WitherSkeletonWitherBoomerangMobType())
        registerMobType(WitherKnightMobType())
        registerMobType(SlimeSmallMobType())
        registerMobType(SlimeMediumMobType())
        registerMobType(SlimeLargeMobType())
        registerMobType(SlimeMiniMobType())
        registerMobType(SlimePoisonMobType())
        registerMobType(SlimeWitherMobType())
        registerMobType(BlazeNormalMobType())
        registerMobType(BlazePowerMobType())
        registerMobType(BlazeRapidMobType())
        registerMobType(BlazeMeleeMobType())
        registerMobType(BlazeBeamMobType())
        registerMobType(DrownedUnarmedMobType())
        registerMobType(DrownedTridentGuardMobType())
        registerMobType(DrownedRaiderAxeMobType())
        registerMobType(WaterSpiritMobType())
        registerMobType(WaterSpiritEliteMobType())
        registerMobType(AshenSpiritMobType())
        registerMobType(GreatFrogMobType())
        registerMobType(WitchNormalMobType())
        registerMobType(WitchEliteMobType())
        registerMobType(BatVenomMobType())
        registerMobType(EndermanPhaseMobType())
        registerMobType(EndermanDrainMobType())
        registerMobType(EndermanMirrorMobType())
        registerMobType(EndermanEyeSummonerMobType())
        registerMobType(EndermanMistDelayMobType())
        registerMobType(EndermanSmallBackstabMobType())
        registerMobType(EndermanVoidCarrierMobType())
        registerMobType(EndermanRiftCarrierMobType())
        registerMobType(EndermanGraveCarrierMobType())
        registerMobType(EndWitherSentinelMobType())
        registerMobType(EndermitePoisonMobType())
        registerMobType(ShulkerRhythmMobType())
        registerMobType(ShulkerLaserMobType())
        registerMobType(ShulkerDisruptorMobType())
        registerMobType(WitherGhostMobType())
        registerMobType(ShulkerMimicMobType())
        registerMobType(EnderEyeHunterMobType())
        registerMobType(EnderEyeOrbitMobType())
        registerMobType(EnderEyeBeamMobType())
        registerMobType(EnderGhostMobType())
        registerMobType(EnderShooterMobType())
        registerEntityMobType(EndCrystalSentinelMobType())
    }

    fun registerMobType(mobType: MobType) {
        customMobTypes[mobType.id.lowercase()] = mobType
    }

    fun registerEntityMobType(mobType: EntityMobType) {
        customEntityMobTypes[mobType.id.lowercase()] = mobType
    }

    fun resolveMobType(typeId: String): MobType? {
        customMobTypes[typeId.trim().lowercase()]?.let { return it }

        val entityType = try {
            EntityType.valueOf(typeId.trim().uppercase())
        } catch (_: IllegalArgumentException) {
            return null
        }

        if (!entityType.isAlive) {
            return null
        }

        return VanillaMobType(entityType.name, entityType)
    }

    fun resolveEntityMobType(typeId: String): EntityMobType? {
        customEntityMobTypes[typeId.trim().lowercase()]?.let { return it }

        val entityType = try {
            EntityType.valueOf(typeId.trim().uppercase())
        } catch (_: IllegalArgumentException) {
            return null
        }

        return VanillaEntityMobType(entityType.name, entityType)
    }

    fun resolveRewardCategoryId(typeId: String): String? {
        val normalized = typeId.trim().lowercase(Locale.ROOT)
        if (normalized.isBlank()) {
            return null
        }

        customMobTypes[normalized]?.let { return it.rewardCategoryId }
        customEntityMobTypes[normalized]?.let { return it.rewardCategoryId }

        val entityType = try {
            EntityType.valueOf(normalized.uppercase(Locale.ROOT))
        } catch (_: IllegalArgumentException) {
            return null
        }

        if (!entityType.isAlive) {
            return null
        }

        return MobRewardCategory.resolveEntityCategoryId(entityType)
    }

    fun reloadDefinitions(): Map<String, MobDefinition> {
        val file = ensureCommonDefinitionFile()
        val loaded = loadDefinitions(file, null, "[MobService]")
        definitions.clear()
        definitions.putAll(loaded)

        if (definitions.isEmpty()) {
            plugin.logger.severe("[MobService] config/mob_definition.yml が空のためモブ定義を利用できません")
        }

        return definitions.toMap()
    }

    fun getDefinitions(): Map<String, MobDefinition> {
        if (definitions.isEmpty()) {
            reloadDefinitions()
        }
        return definitions.toMap()
    }

    fun getDefinition(id: String): MobDefinition? {
        if (definitions.isEmpty()) {
            reloadDefinitions()
        }
        return definitions[id]
    }

    fun getDefinitionIds(): Set<String> {
        if (definitions.isEmpty()) {
            reloadDefinitions()
        }
        return definitions.keys
    }

    fun getSessionLoadSnapshot(sessionKey: String?): MobLoadSnapshot {
        val key = normalizeSessionKey(sessionKey)
        return sessionLoadMetrics[key]?.lastSnapshot ?: MobLoadSnapshot.DEFAULT
    }

    fun getSpawnThrottle(sessionKey: String?): MobSpawnThrottle {
        val snapshot = getSessionLoadSnapshot(sessionKey)
        return MobSpawnThrottle(
            intervalMultiplier = snapshot.spawnIntervalMultiplier,
            skipChance = snapshot.spawnSkipChance
        )
    }

    fun recordProjectileLaunch(sessionKey: String?) {
        val key = normalizeSessionKey(sessionKey)
        val metrics = sessionLoadMetrics.getOrPut(key) { SessionLoadMetrics() }
        metrics.projectileLaunchCount += 1
        metrics.lastSeenMillis = System.currentTimeMillis()
    }

    fun spawnByDefinitionId(definitionId: String, location: Location, options: MobSpawnOptions): LivingEntity? {
        val definition = getDefinition(definitionId) ?: return null
        return spawn(definition, location, options)
    }

    fun spawnEntityByDefinitionId(
        definitionId: String,
        location: Location,
        options: EntityMobSpawnOptions
    ): Entity? {
        val definition = getDefinition(definitionId) ?: return null
        return spawnEntity(definition, location, options)
    }

    fun loadDefinitions(file: File, rootPath: String? = null, logPrefix: String): Map<String, MobDefinition> {
        val config = YamlConfiguration.loadConfiguration(file)
        val section = if (rootPath.isNullOrBlank()) {
            config
        } else {
            config.getConfigurationSection(rootPath)
        }

        if (section == null) {
            plugin.logger.severe("$logPrefix mob_definition のルートセクションが見つかりません: ${rootPath ?: "<root>"}")
            return emptyMap()
        }

        val loaded = mutableMapOf<String, MobDefinition>()
        for (mobId in section.getKeys(false)) {
            val mobSection = section.getConfigurationSection(mobId)
            if (mobSection == null) {
                plugin.logger.severe("$logPrefix mob_definition.yml の読み込み失敗: $mobId")
                continue
            }

            val typeId = mobSection.getString("type")?.trim().orEmpty()
            if (typeId.isBlank()) {
                plugin.logger.severe("$logPrefix mob_definition.yml の type が空です: $mobId")
                continue
            }
            val resolvedTypeId = resolveMobType(typeId)?.id
                ?: resolveEntityMobType(typeId)?.id
            if (resolvedTypeId == null) {
                plugin.logger.severe("$logPrefix mob_definition.yml の type が不正です: $mobId type=$typeId")
                continue
            }

            val equipment = parseEquipment(mobId, mobSection, logPrefix)
            val spawnConditions = parseSpawnConditions(mobId, mobSection, logPrefix)
            loaded[mobId] = MobDefinition(
                id = mobId,
                typeId = resolvedTypeId,
                health = mobSection.getDouble("health", 20.0).coerceAtLeast(1.0),
                attack = mobSection.getDouble("attack", 1.0).coerceAtLeast(0.0),
                movementSpeed = mobSection.getDouble("movement_speed", 0.23).coerceAtLeast(0.01),
                armor = mobSection.getDouble("armor", 0.0).coerceAtLeast(0.0),
                mobTokenDropChance = mobSection
                    .getDouble("mob_token_drop_chance", -1.0)
                    .takeIf { it >= 0.0 }
                    ?.coerceIn(0.0, 1.0),
                equipment = equipment,
                spawnConditions = spawnConditions
            )
        }
        return loaded
    }

    private fun parseSpawnConditions(
        mobId: String,
        section: ConfigurationSection,
        logPrefix: String
    ): Set<MobSpawnCondition> {
        val values = section.getStringList("spawn_conditions")
        if (values.isEmpty()) {
            return emptySet()
        }

        val parsed = mutableSetOf<MobSpawnCondition>()
        values.forEach { raw ->
            val normalized = raw.trim().uppercase()
            if (normalized.isEmpty()) {
                return@forEach
            }

            val condition = try {
                MobSpawnCondition.valueOf(normalized)
            } catch (_: IllegalArgumentException) {
                plugin.logger.warning("$logPrefix mob_definition.yml の spawn_conditions が不正です: $mobId condition=$raw")
                null
            }
            if (condition != null) {
                parsed.add(condition)
            }
        }
        return parsed
    }

    fun spawn(definition: MobDefinition, location: Location, options: MobSpawnOptions): LivingEntity? {
        val world = location.world ?: return null
        val mobType = resolveMobType(definition.typeId)
        if (mobType == null) {
            plugin.logger.warning("[MobService] 未登録の mob type です: ${definition.typeId}")
            return null
        }
        val sessionKey = resolveSessionKey(options, world.name)

        val entity = world.spawnEntity(location, mobType.baseEntityType) as? LivingEntity ?: return null
        markEntity(entity, definition, mobType, options)
        enforceStrictSpawnState(entity)
        applyDefinitionStats(entity, definition)

        val spawnContext = MobSpawnContext(plugin, entity, definition, mobType, options)
        val runtime = mobType.createRuntime(spawnContext)
        activeMobs[entity.uniqueId] = ActiveMob(
            entityId = entity.uniqueId,
            definition = definition,
            mobType = mobType,
            featureId = options.featureId,
            sessionKey = sessionKey,
            combatActiveProvider = options.combatActiveProvider,
            metadata = options.metadata,
            runtime = runtime
        )

        clearImplicitEquipmentIfNeeded(entity, mobType)
        mobType.applyDefaultEquipment(spawnContext, runtime)
        mobType.onSpawn(spawnContext, runtime)
        applyDefinitionEquipment(entity, definition, onlyIfEmpty = false)

        if (plugin.isEnabled) {
            Bukkit.getPluginManager().callEvent(
                jp.awabi2048.cccontent.mob.event.CustomMobSpawnEvent(entity, definition, options)
            )
        }

        return entity
    }

    fun spawnEntity(definition: MobDefinition, location: Location, options: EntityMobSpawnOptions): Entity? {
        val world = location.world ?: return null
        val mobType = resolveEntityMobType(definition.typeId)
        if (mobType == null) {
            plugin.logger.warning("[MobService] 未登録の entity mob type です: ${definition.typeId}")
            return null
        }
        val sessionKey = resolveEntitySessionKey(options, world.name)

        val entity = world.spawnEntity(location, mobType.baseEntityType)
        markEntityLike(entity, definition, mobType, options)

        val spawnContext = EntityMobSpawnContext(plugin, entity, definition, mobType, options)
        val runtime = mobType.createRuntime(spawnContext)
        activeEntityMobs[entity.uniqueId] = ActiveEntityMob(
            entityId = entity.uniqueId,
            definition = definition,
            mobType = mobType,
            featureId = options.featureId,
            sessionKey = sessionKey,
            combatActiveProvider = options.combatActiveProvider,
            metadata = options.metadata,
            runtime = runtime
        )

        mobType.onSpawn(spawnContext, runtime)

        if (plugin.isEnabled) {
            Bukkit.getPluginManager().callEvent(
                jp.awabi2048.cccontent.mob.event.CustomEntityMobSpawnEvent(entity, definition, options)
            )
        }

        return entity
    }

    fun getActiveMob(entityId: UUID): ActiveMob? {
        return activeMobs[entityId]
    }

    fun getActiveEntityMob(entityId: UUID): ActiveEntityMob? {
        return activeEntityMobs[entityId]
    }

    fun resolveCustomMobDisplayNameByDamager(damager: Entity, viewer: Player?): String? {
        val activeMob = resolveActiveMobByDamager(damager) ?: return null
        val categoryId = activeMob.mobType.rewardCategoryId
        val key = "custom_items.arena.mob_token.token_names.$categoryId"
        return ArenaI18n.text(viewer, key, categoryId)
    }

    private fun resolveActiveMobByDamager(damager: Entity): ActiveMob? {
        activeMobs[damager.uniqueId]?.let { return it }

        val projectile = damager as? Projectile ?: return null
        val shooterEntity = projectile.shooter as? Entity ?: return null
        return activeMobs[shooterEntity.uniqueId]
    }

    fun markSkeletonEffectArrow(
        arrow: AbstractArrow,
        effectTypeName: String,
        amplifier: Int,
        durationTicks: Int
    ) {
        val container = arrow.persistentDataContainer
        container.set(skeletonEffectArrowKey, PersistentDataType.BYTE, 1)
        container.set(skeletonEffectArrowTypeKey, PersistentDataType.STRING, effectTypeName)
        container.set(skeletonEffectArrowAmplifierKey, PersistentDataType.INTEGER, amplifier)
        container.set(skeletonEffectArrowDurationKey, PersistentDataType.INTEGER, durationTicks)
    }

    fun markMobShotArrow(arrow: AbstractArrow) {
        arrow.persistentDataContainer.set(mobShotArrowKey, PersistentDataType.BYTE, 1)
    }

    fun markCustomProjectileDamage(projectile: Projectile, damage: Double) {
        projectile.persistentDataContainer.set(customProjectileDamageKey, PersistentDataType.DOUBLE, damage.coerceAtLeast(0.0))
    }

    fun markStealthFang(fangs: EvokerFangs) {
        fangs.persistentDataContainer.set(stealthFangKey, PersistentDataType.BYTE, 1)
    }

    fun markShulkerLevitationBullet(projectile: Projectile, amplifier: Int, durationTicks: Int) {
        val container = projectile.persistentDataContainer
        container.set(shulkerLevitationBulletKey, PersistentDataType.BYTE, 1)
        container.set(shulkerLevitationAmplifierKey, PersistentDataType.INTEGER, amplifier.coerceAtLeast(0))
        container.set(shulkerLevitationDurationKey, PersistentDataType.INTEGER, durationTicks.coerceAtLeast(1))
    }

    fun handleStealthFangDamage(event: EntityDamageByEntityEvent) {
        val fangs = event.damager as? EvokerFangs ?: return
        if (!fangs.persistentDataContainer.has(stealthFangKey, PersistentDataType.BYTE)) return

        event.damage = (event.damage * 0.5).coerceAtLeast(0.0)
        val target = event.entity as? Player ?: return
        target.velocity = target.velocity.add(Vector(0.0, STEALTH_FANG_KNOCKUP, 0.0))
    }

    fun issueManagedProjectilePermit(shooterId: UUID) {
        managedProjectilePermits[shooterId] = (managedProjectilePermits[shooterId] ?: 0) + 1
    }

    fun issueDirectDamagePermit(attackerId: UUID, targetId: UUID) {
        val key = attackerId to targetId
        directDamagePermits[key] = (directDamagePermits[key] ?: 0) + 1
    }

    private fun consumeManagedProjectilePermit(shooterId: UUID): Boolean {
        val current = managedProjectilePermits[shooterId] ?: return false
        if (current <= 1) {
            managedProjectilePermits.remove(shooterId)
        } else {
            managedProjectilePermits[shooterId] = current - 1
        }
        return true
    }

    private fun consumeDirectDamagePermit(attackerId: UUID, targetId: UUID): Boolean {
        val key = attackerId to targetId
        val current = directDamagePermits[key] ?: return false
        if (current <= 1) {
            directDamagePermits.remove(key)
        } else {
            directDamagePermits[key] = current - 1
        }
        return true
    }

    fun isManagedCustomProjectile(projectile: Projectile): Boolean {
        return projectile.persistentDataContainer.has(customProjectileDamageKey, PersistentDataType.DOUBLE)
    }

    fun handleProjectileEffects(event: EntityDamageByEntityEvent) {
        ThrownWeaponService.getInstance(plugin).handleProjectileDamage(event)

        val target = event.entity as? LivingEntity ?: return
        val damager = event.damager

        val projectile = damager as? Projectile
        if (projectile != null) {
            val customDamage = projectile.persistentDataContainer.get(customProjectileDamageKey, PersistentDataType.DOUBLE)
            if (customDamage != null) {
                if (isManagedBlazeFireball(projectile)) {
                    event.damage = 0.0
                } else {
                    event.damage = customDamage.coerceAtLeast(0.0)
                }
            }
        }

        val arrow = damager as? AbstractArrow ?: return
        val container = arrow.persistentDataContainer
        if (container.get(skeletonEffectArrowKey, PersistentDataType.BYTE)?.toInt() != 1) {
            return
        }

        val effectTypeName = container.get(skeletonEffectArrowTypeKey, PersistentDataType.STRING) ?: return
        val effectType = PotionEffectType.getByName(effectTypeName) ?: return
        val amplifier = container.get(skeletonEffectArrowAmplifierKey, PersistentDataType.INTEGER) ?: 0
        val durationTicks = (container.get(skeletonEffectArrowDurationKey, PersistentDataType.INTEGER) ?: 200).coerceAtLeast(1)
        target.addPotionEffect(PotionEffect(effectType, durationTicks, amplifier, false, true, true))
    }

    fun handleEntityDamaged(event: EntityDamageByEntityEvent, damaged: Entity) {
        val activeMob = activeEntityMobs[damaged.uniqueId] ?: return
        val snapshot = getSessionLoadSnapshot(activeMob.sessionKey)
        val startedAt = System.nanoTime()
        activeMob.mobType.onDamaged(
            EntityMobDamagedContext(
                plugin = plugin,
                entity = damaged,
                activeMob = activeMob,
                event = event,
                attacker = event.damager as? LivingEntity,
                loadSnapshot = snapshot
            ),
            activeMob.runtime
        )
        addCpuNanos(activeMob.sessionKey, System.nanoTime() - startedAt)
    }

    private fun clearImplicitEquipmentIfNeeded(entity: LivingEntity, mobType: MobType) {
        val equipment = entity.equipment ?: return
        equipment.setItemInMainHand(ItemStack(Material.AIR))
        equipment.setItemInOffHand(ItemStack(Material.AIR))
        equipment.helmet = ItemStack(Material.AIR)
        equipment.chestplate = ItemStack(Material.AIR)
        equipment.leggings = ItemStack(Material.AIR)
        equipment.boots = ItemStack(Material.AIR)
    }

    fun handleEntityTransform(event: EntityTransformEvent) {
        if (event.transformReason != EntityTransformEvent.TransformReason.DROWNED) {
            return
        }
        val zombie = event.entity as? Zombie ?: return
        val activeMob = activeMobs[zombie.uniqueId] ?: return
        if (activeMob.mobType.id != "drowned_unarmed" &&
            activeMob.mobType.id != "drowned_trident_guard" &&
            activeMob.mobType.id != "drowned_raider_axe"
        ) {
            return
        }
        event.isCancelled = true
    }

    fun handleEntityTeleport(event: EntityTeleportEvent) {
        val living = event.entity as? LivingEntity ?: return
        val activeMob = activeMobs[living.uniqueId] ?: return
        if (!activeMob.mobType.id.startsWith("enderman_")) return
    }

    fun handleShootBow(event: EntityShootBowEvent) {
        val shooter = event.entity as? LivingEntity ?: return
        val activeMob = activeMobs[shooter.uniqueId] ?: return
        if (activeMob.mobType.hasCustomRangedAttack()) {
            event.isCancelled = true
        }
    }

    fun handleProjectileLaunch(event: ProjectileLaunchEvent) {
        val projectile = event.entity as? Projectile ?: return
        if (projectile is ShulkerBullet && projectile.scoreboardTags.contains(VISUAL_SHULKER_BULLET_TAG)) {
            return
        }
        val shooter = projectile.shooter as? LivingEntity ?: return
        val activeMob = activeMobs[shooter.uniqueId] ?: return

        if (activeMob.mobType.id == "witch_elite" && projectile is ThrownPotion) {
            handleEliteWitchPotionLaunch(event, shooter, activeMob, projectile)
            return
        }

        if (projectile !is SmallFireball && projectile !is Fireball) {
            return
        }

        if (!activeMob.mobType.id.startsWith("blaze_")) {
            return
        }

        if (!consumeManagedProjectilePermit(shooter.uniqueId)) {
            event.isCancelled = true
            projectile.remove()
        }
    }

    private fun handleEliteWitchPotionLaunch(
        event: ProjectileLaunchEvent,
        shooter: LivingEntity,
        activeMob: ActiveMob,
        potion: ThrownPotion
    ) {
        val launchLocation = potion.location.clone()
        val launchVelocity = potion.velocity.clone()
        val effectEntry = pickEliteWitchEffectEntry()
        val boostedEffects = listOf(
            PotionEffect(
                effectEntry.effectType,
                effectEntry.durationTicks,
                (effectEntry.amplifier + ELITE_WITCH_EFFECT_AMPLIFIER_BONUS).coerceAtLeast(0),
                false,
                true,
                true
            )
        )
        val effectColor = effectEntry.color

        if (Random.nextDouble() < ELITE_WITCH_MAGIC_CONVERT_CHANCE) {
            event.isCancelled = true
            potion.remove()
            val target = resolveEliteWitchMagicTarget(shooter) ?: run {
                spawnEliteLingeringPotionProjectile(shooter, launchLocation, launchVelocity, boostedEffects, effectColor)
                return
            }
            launchEliteWitchMagicVolley(
                shooter = shooter,
                activeMob = activeMob,
                target = target,
                boostedEffects = boostedEffects,
                effectColor = effectColor
            )
            return
        }

        event.isCancelled = true
        potion.remove()
        spawnEliteLingeringPotionProjectile(shooter, launchLocation, launchVelocity, boostedEffects, effectColor)
    }

    private fun spawnEliteLingeringPotionProjectile(
        shooter: LivingEntity,
        spawnLocation: Location,
        velocity: Vector,
        boostedEffects: List<PotionEffect>,
        effectColor: Color
    ) {
        val item = ItemStack(Material.LINGERING_POTION)
        val meta = item.itemMeta as? PotionMeta ?: return
        meta.basePotionType = PotionType.WATER
        meta.color = effectColor
        boostedEffects.forEach { meta.addCustomEffect(it, true) }
        item.itemMeta = meta

        val world = shooter.world
        val lingering = world.spawn(spawnLocation, LingeringPotion::class.java)
        lingering.shooter = shooter
        lingering.velocity = velocity
        lingering.item = item
    }

    private fun resolveEliteWitchMagicTarget(shooter: LivingEntity): Player? {
        val directTarget = (shooter as? Mob)?.target as? Player
        if (directTarget != null && directTarget.isValid && !directTarget.isDead && directTarget.gameMode != org.bukkit.GameMode.SPECTATOR && directTarget.world.uid == shooter.world.uid) {
            return directTarget
        }
        return shooter.getNearbyEntities(ELITE_WITCH_MAGIC_TARGET_SEARCH_RADIUS, ELITE_WITCH_MAGIC_TARGET_SEARCH_RADIUS, ELITE_WITCH_MAGIC_TARGET_SEARCH_RADIUS)
            .asSequence()
            .filterIsInstance<Player>()
            .filter { it.isValid && !it.isDead && it.gameMode != org.bukkit.GameMode.SPECTATOR }
            .minByOrNull { it.location.distanceSquared(shooter.location) }
    }

    private fun launchEliteWitchMagicVolley(
        shooter: LivingEntity,
        activeMob: ActiveMob,
        target: Player,
        boostedEffects: List<PotionEffect>,
        effectColor: Color
    ) {
        val origin = shooter.eyeLocation.clone().add(0.0, -0.25, 0.0)
        shooter.world.playSound(origin, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.0f, ELITE_WITCH_MAGIC_SOUND_PITCH)
        shooter.world.playSound(origin, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, ELITE_WITCH_MAGIC_SOUND_PITCH)

        val forward = target.eyeLocation.toVector().subtract(origin.toVector())
            .takeIf { it.lengthSquared() > 1.0e-6 }
            ?.normalize()
            ?: shooter.location.direction.clone().normalize()
        val planeU = createPlaneBasisU(forward)
        val planeV = forward.clone().crossProduct(planeU).normalize()
        val clockDirections = listOf(
            planeV.clone(),
            planeU.clone().multiply(0.8660254037844386).add(planeV.clone().multiply(-0.5)).normalize(),
            planeU.clone().multiply(-0.8660254037844386).add(planeV.clone().multiply(-0.5)).normalize()
        )

        repeat(ELITE_WITCH_MAGIC_PROJECTILE_COUNT) { index ->
            val laneOffset = clockDirections[index].clone().multiply(ELITE_WITCH_MAGIC_ORIGIN_SPREAD)
            val laneOrigin = origin.clone().add(laneOffset)
            launchEliteWitchMagicProjectile(
                shooter = shooter,
                activeMob = activeMob,
                initialTarget = target,
                origin = laneOrigin,
                initialDirection = clockDirections[index],
                forwardDirection = forward,
                boostedEffects = boostedEffects,
                effectColor = effectColor
            )
        }
    }

    private fun launchEliteWitchMagicProjectile(
        shooter: LivingEntity,
        activeMob: ActiveMob,
        initialTarget: Player,
        origin: Location,
        initialDirection: Vector,
        forwardDirection: Vector,
        boostedEffects: List<PotionEffect>,
        effectColor: Color
    ) {
        val world = origin.world ?: return
        var currentPos = origin.clone()
        var targetRef: LivingEntity = initialTarget
        var ageTicks = 0L
        var flightDirection = initialDirection.clone().normalize()
        val planeNormal = forwardDirection.clone().crossProduct(initialDirection).normalize()
        object : BukkitRunnable() {
            override fun run() {
                if (!shooter.isValid || shooter.isDead) {
                    cancel()
                    return
                }

                if (!targetRef.isValid || targetRef.isDead || targetRef.world.uid != shooter.world.uid) {
                    targetRef = resolveEliteWitchMagicTarget(shooter) ?: run {
                        cancel()
                        return
                    }
                }

                ageTicks += 1
                if (ageTicks > ELITE_WITCH_MAGIC_MAX_LIFETIME_TICKS) {
                    cancel()
                    return
                }

                val targetEye = targetRef.eyeLocation
                val toTarget = targetEye.toVector().subtract(currentPos.toVector())
                if (toTarget.length() < ELITE_WITCH_MAGIC_HIT_DISTANCE) {
                    targetRef.damage(activeMob.definition.attack.coerceAtLeast(0.0), shooter)
                    boostedEffects.forEach { targetRef.addPotionEffect(it) }
                    world.spawnParticle(Particle.ENTITY_EFFECT, currentPos, 1, effectColor)
                    cancel()
                    return
                }

                if (ageTicks <= ELITE_WITCH_MAGIC_HOMING_END_TICKS && toTarget.lengthSquared() > 1.0e-6) {
                    val desiredDir = projectOntoPlane(toTarget.normalize(), planeNormal)
                    flightDirection = flightDirection.multiply(1.0 - ELITE_WITCH_MAGIC_HOMING_STEER_FACTOR)
                        .add(desiredDir.multiply(ELITE_WITCH_MAGIC_HOMING_STEER_FACTOR))
                        .normalize()
                }

                val displacement = flightDirection.clone().multiply(ELITE_WITCH_MAGIC_STEP_DISTANCE)

                val displacementLength = displacement.length()
                if (displacementLength > 1.0e-6) {
                    val blockHit = world.rayTraceBlocks(
                        currentPos,
                        displacement.clone().normalize(),
                        displacementLength,
                        FluidCollisionMode.NEVER,
                        true
                    )
                    if (blockHit != null) {
                        cancel()
                        return
                    }
                }

                currentPos.add(displacement)
                world.spawnParticle(Particle.ENTITY_EFFECT, currentPos, 1, effectColor)
                world.spawnParticle(
                    Particle.DUST_COLOR_TRANSITION,
                    currentPos,
                    1,
                    0.02,
                    0.02,
                    0.02,
                    0.0,
                    Particle.DustTransition(effectColor, Color.BLACK, 0.8f)
                )
            }
        }.runTaskTimer(plugin, 0L, 1L)
    }

    private fun createPlaneBasisU(normal: Vector): Vector {
        val worldUp = Vector(0.0, 1.0, 0.0)
        val basis = normal.clone().crossProduct(worldUp)
        if (basis.lengthSquared() > 1.0e-6) {
            return basis.normalize()
        }
        return normal.clone().crossProduct(Vector(1.0, 0.0, 0.0)).normalize()
    }

    private fun projectOntoPlane(direction: Vector, planeNormal: Vector): Vector {
        val projected = direction.clone().subtract(planeNormal.clone().multiply(direction.dot(planeNormal)))
        if (projected.lengthSquared() <= 1.0e-6) {
            return direction
        }
        return projected.normalize()
    }

    private fun pickEliteWitchEffectEntry(): WitchEffectEntry {
        return eliteWitchEffectTable.randomOrNull()
            ?: WitchEffectEntry(PotionEffectType.POISON, 80, 0, ELITE_WITCH_DEFAULT_EFFECT_COLOR)
    }

    private fun buildEliteWitchEffectTable(): List<WitchEffectEntry> {
        val miningFatigue = resolveMiningFatigueEffectType()
        val table = mutableListOf(
            WitchEffectEntry(PotionEffectType.SLOWNESS, 100, 0, Color.fromRGB(78, 92, 122)),
            WitchEffectEntry(PotionEffectType.WEAKNESS, 100, 0, Color.fromRGB(102, 88, 106)),
            WitchEffectEntry(PotionEffectType.POISON, 100, 0, Color.fromRGB(66, 134, 60)),
            WitchEffectEntry(PotionEffectType.BLINDNESS, 60, 0, Color.fromRGB(36, 36, 36)),
            WitchEffectEntry(PotionEffectType.WITHER, 80, 0, Color.fromRGB(52, 52, 52))
        )
        if (miningFatigue != null) {
            table += WitchEffectEntry(miningFatigue, 100, 0, Color.fromRGB(58, 95, 116))
        }
        return table
    }

    private fun resolveMiningFatigueEffectType(): PotionEffectType? {
        return PotionEffectType.getByName("MINING_FATIGUE")
            ?: PotionEffectType.getByName("SLOW_DIGGING")
    }

    fun handleProjectileHit(event: ProjectileHitEvent) {
        ThrownWeaponService.getInstance(plugin).handleProjectileHit(event)
        val projectile = event.entity
        HomingArrowService.getInstance(plugin).handleHit(projectile.uniqueId)

        applyShulkerLevitationHit(projectile, event.hitEntity as? LivingEntity)

        applyBlazeDirectHitDamage(projectile, event.hitEntity as? LivingEntity)

        val arrow = projectile as? AbstractArrow ?: return
        if (!arrow.persistentDataContainer.has(mobShotArrowKey, PersistentDataType.BYTE)) {
            return
        }
        arrow.remove()
    }

    private fun applyShulkerLevitationHit(projectile: Projectile, hitEntity: LivingEntity?) {
        if (hitEntity == null) return
        val container = projectile.persistentDataContainer
        if (container.get(shulkerLevitationBulletKey, PersistentDataType.BYTE)?.toInt() != 1) {
            return
        }
        val amp = container.get(shulkerLevitationAmplifierKey, PersistentDataType.INTEGER) ?: 2
        val duration = container.get(shulkerLevitationDurationKey, PersistentDataType.INTEGER) ?: 10
        hitEntity.addPotionEffect(PotionEffect(PotionEffectType.LEVITATION, duration.coerceAtLeast(1), amp.coerceAtLeast(0), false, true, true))
    }

    private fun applyBlazeDirectHitDamage(projectile: Projectile, hitEntity: LivingEntity?) {
        if (hitEntity == null) return
        if (projectile !is SmallFireball && projectile !is Fireball) return
        if (projectile.persistentDataContainer.get(blazeDirectHitAppliedKey, PersistentDataType.BYTE)?.toInt() == 1) return

        val damage = projectile.persistentDataContainer.get(customProjectileDamageKey, PersistentDataType.DOUBLE)
            ?.coerceAtLeast(0.0)
            ?: return
        if (damage <= 0.0) return

        val shooter = projectile.shooter as? LivingEntity ?: return
        val activeMob = activeMobs[shooter.uniqueId] ?: return
        if (!activeMob.mobType.id.startsWith("blaze_")) return

        issueDirectDamagePermit(shooter.uniqueId, hitEntity.uniqueId)
        hitEntity.noDamageTicks = 0
        hitEntity.damage(damage, shooter)
        projectile.persistentDataContainer.set(blazeDirectHitAppliedKey, PersistentDataType.BYTE, 1)
    }

    private fun isManagedBlazeFireball(projectile: Projectile): Boolean {
        if (projectile !is SmallFireball && projectile !is Fireball) return false
        val shooter = projectile.shooter as? LivingEntity ?: return false
        val activeMob = activeMobs[shooter.uniqueId] ?: return false
        return activeMob.mobType.id.startsWith("blaze_")
    }

    fun handleEntityPickupItem(event: EntityPickupItemEvent) {
        ThrownWeaponService.getInstance(plugin).handleItemPickup(event)
    }

    fun handleSlimeSplit(event: org.bukkit.event.entity.SlimeSplitEvent) {
        if (getActiveMob(event.entity.uniqueId) != null) {
            event.isCancelled = true
            return
        }
        if (event.entity.persistentDataContainer.has(customMobKey, PersistentDataType.BYTE)) {
            event.isCancelled = true
        }
    }

    fun startTickTask(intervalTicks: Long = 1L) {
        if (tickTask != null) {
            return
        }

        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            val activeCountBySession = mutableMapOf<String, Int>()
            val activeMobSnapshot = activeMobs.values.toList()
            val staleIds = mutableListOf<UUID>()
            for (activeMob in activeMobSnapshot) {
                if (activeMobs[activeMob.entityId] !== activeMob) {
                    continue
                }
                val entity = Bukkit.getEntity(activeMob.entityId) as? LivingEntity
                if (entity == null || !entity.isValid || entity.isDead) {
                    staleIds.add(activeMob.entityId)
                    continue
                }
                activeCountBySession[activeMob.sessionKey] = (activeCountBySession[activeMob.sessionKey] ?: 0) + 1

                activeMob.tickCount += intervalTicks
                val snapshot = getSessionLoadSnapshot(activeMob.sessionKey)
                val startedAt = System.nanoTime()
                activeMob.mobType.onTick(
                    MobRuntimeContext(plugin, entity, activeMob, snapshot, tickDelta = intervalTicks),
                    activeMob.runtime
                )
                val elapsedNanos = System.nanoTime() - startedAt
                addCpuNanos(activeMob.sessionKey, elapsedNanos)
            }
            val activeEntityMobSnapshot = activeEntityMobs.values.toList()
            for (activeMob in activeEntityMobSnapshot) {
                if (activeEntityMobs[activeMob.entityId] !== activeMob) {
                    continue
                }
                val entity = Bukkit.getEntity(activeMob.entityId)
                if (entity == null || !entity.isValid || entity.isDead) {
                    staleIds.add(activeMob.entityId)
                    continue
                }
                activeCountBySession[activeMob.sessionKey] = (activeCountBySession[activeMob.sessionKey] ?: 0) + 1

                activeMob.tickCount += intervalTicks
                val snapshot = getSessionLoadSnapshot(activeMob.sessionKey)
                val startedAt = System.nanoTime()
                activeMob.mobType.onTick(
                    EntityMobRuntimeContext(plugin, entity, activeMob, snapshot, tickDelta = intervalTicks),
                    activeMob.runtime
                )
                val elapsedNanos = System.nanoTime() - startedAt
                addCpuNanos(activeMob.sessionKey, elapsedNanos)
            }
            staleIds.forEach { staleId ->
                val activeMob = activeMobs.remove(staleId)
                if (activeMob != null) {
                    val entity = Bukkit.getEntity(staleId) as? LivingEntity
                    if (entity != null) {
                        runDeathCallbacks(activeMob, entity)
                    }
                }
                val activeEntityMob = activeEntityMobs.remove(staleId)
                if (activeEntityMob != null) {
                    val entity = Bukkit.getEntity(staleId)
                    if (entity != null) {
                        runEntityDeathCallbacks(activeEntityMob, entity)
                    }
                }
            }

            refreshSessionLoadSnapshots(activeCountBySession)
        }, intervalTicks, intervalTicks)
    }

    fun shutdown() {
        tickTask?.cancel()
        tickTask = null
        val trackedMobIds = activeMobs.keys.toList()
        trackedMobIds.forEach { despawnTrackedMob(it) }
        val trackedEntityMobIds = activeEntityMobs.keys.toList()
        trackedEntityMobIds.forEach { despawnTrackedEntityMob(it) }
        activeMobs.clear()
        activeEntityMobs.clear()
        managedProjectilePermits.clear()
        directDamagePermits.clear()
        sessionLoadMetrics.clear()
        BoomerangService.getInstance(plugin).shutdown()
        HomingArrowService.getInstance(plugin).shutdown()
        ThrownWeaponService.getInstance(plugin).shutdown()
        synchronized(instances) {
            instances.remove(plugin)
        }
    }

    fun untrack(entityId: UUID) {
        activeMobs.remove(entityId)
        activeEntityMobs.remove(entityId)
    }

    fun despawnTrackedMob(entityId: UUID) {
        val activeMob = activeMobs.remove(entityId) ?: run {
            val entity = Bukkit.getEntity(entityId) as? LivingEntity
            if (entity != null && entity.isValid && !entity.isDead) {
                entity.remove()
            }
            return
        }

        val entity = Bukkit.getEntity(entityId) as? LivingEntity
        if (entity != null) {
            runDeathCallbacks(activeMob, entity)
            if (entity.isValid && !entity.isDead) {
                entity.remove()
            }
        }
    }

    fun despawnTrackedEntityMob(entityId: UUID) {
        val activeMob = activeEntityMobs.remove(entityId) ?: run {
            val entity = Bukkit.getEntity(entityId)
            if (entity != null && entity.isValid && !entity.isDead) {
                entity.remove()
            }
            return
        }

        val entity = Bukkit.getEntity(entityId)
        if (entity != null) {
            runEntityDeathCallbacks(activeMob, entity)
            if (entity.isValid && !entity.isDead) {
                entity.remove()
            }
        }
    }

    fun despawnTrackedMobsByMetadata(definitionId: String, metadataKey: String, metadataValue: String) {
        val targets = activeMobs.values
            .asSequence()
            .filter { it.definition.id == definitionId }
            .filter { it.metadata[metadataKey] == metadataValue }
            .map { it.entityId }
            .toList()

        targets.forEach { despawnTrackedMob(it) }
    }

    fun despawnTrackedEntityMobsByMetadata(definitionId: String, metadataKey: String, metadataValue: String) {
        val targets = activeEntityMobs.values
            .asSequence()
            .filter { it.definition.id == definitionId }
            .filter { it.metadata[metadataKey] == metadataValue }
            .map { it.entityId }
            .toList()

        targets.forEach { despawnTrackedEntityMob(it) }
    }

    fun handleAttack(event: EntityDamageByEntityEvent, attacker: LivingEntity) {
        val activeMob = activeMobs[attacker.uniqueId] ?: return
        if (activeMob.mobType.id.startsWith("blaze_") && event.damager.uniqueId == attacker.uniqueId) {
            val targetId = (event.entity as? LivingEntity)?.uniqueId
            if (targetId != null && consumeDirectDamagePermit(attacker.uniqueId, targetId)) {
                return
            }
            event.damage = 0.0
            event.isCancelled = true
            return
        }
        val snapshot = getSessionLoadSnapshot(activeMob.sessionKey)
        val startedAt = System.nanoTime()
        activeMob.mobType.onAttack(
            MobAttackContext(
                plugin = plugin,
                entity = attacker,
                activeMob = activeMob,
                event = event,
                target = event.entity as? LivingEntity,
                loadSnapshot = snapshot
            ),
            activeMob.runtime
        )
        addCpuNanos(activeMob.sessionKey, System.nanoTime() - startedAt)
    }

    fun handleDamaged(event: EntityDamageByEntityEvent, damaged: LivingEntity) {
        val activeMob = activeMobs[damaged.uniqueId] ?: return
        if (shouldBlockDowngradedAoEDamage(damaged, event.cause)) {
            event.isCancelled = true
            return
        }
        val snapshot = getSessionLoadSnapshot(activeMob.sessionKey)
        val startedAt = System.nanoTime()
        activeMob.mobType.onDamaged(
            MobDamagedContext(
                plugin = plugin,
                entity = damaged,
                activeMob = activeMob,
                event = event,
                attacker = event.damager as? LivingEntity,
                loadSnapshot = snapshot
            ),
            activeMob.runtime
        )
        addCpuNanos(activeMob.sessionKey, System.nanoTime() - startedAt)
    }

    fun handleDamaged(event: EntityDamageEvent, damaged: LivingEntity) {
        val activeMob = activeMobs[damaged.uniqueId] ?: return
        if (shouldBlockDowngradedAoEDamage(damaged, event.cause)) {
            event.isCancelled = true
            return
        }
        val snapshot = getSessionLoadSnapshot(activeMob.sessionKey)
        val startedAt = System.nanoTime()
        activeMob.mobType.onGenericDamaged(
            MobGenericDamagedContext(
                plugin = plugin,
                entity = damaged,
                activeMob = activeMob,
                event = event,
                loadSnapshot = snapshot
            ),
            activeMob.runtime
        )
        addCpuNanos(activeMob.sessionKey, System.nanoTime() - startedAt)
    }

    fun handleCombust(event: EntityCombustEvent, entity: LivingEntity) {
        val activeMob = activeMobs[entity.uniqueId] ?: return
        if (isDowngradeAoEProtectionActive(entity)) {
            event.isCancelled = true
            return
        }
        val snapshot = getSessionLoadSnapshot(activeMob.sessionKey)
        val startedAt = System.nanoTime()
        activeMob.mobType.onCombust(
            MobCombustContext(
                plugin = plugin,
                entity = entity,
                activeMob = activeMob,
                event = event,
                loadSnapshot = snapshot
            ),
            activeMob.runtime
        )
        addCpuNanos(activeMob.sessionKey, System.nanoTime() - startedAt)
    }

    fun handleDeath(event: EntityDeathEvent) {
        val entity = event.entity
        val activeMob = activeMobs.remove(entity.uniqueId) ?: return
        runDeathCallbacks(activeMob, entity, event)
    }

    fun handleEntityDeath(entity: Entity) {
        val activeMob = activeEntityMobs.remove(entity.uniqueId) ?: return
        runEntityDeathCallbacks(activeMob, entity, null)
    }

    private fun runDeathCallbacks(activeMob: ActiveMob, entity: LivingEntity, event: EntityDeathEvent? = null) {
        val snapshot = getSessionLoadSnapshot(activeMob.sessionKey)
        val startedAt = System.nanoTime()
        val deathEvent = event ?: EntityDeathEvent(
            entity,
            DamageSource.builder(DamageType.GENERIC).build(),
            mutableListOf()
        )
        activeMob.mobType.onDeath(
            MobDeathContext(plugin, entity, activeMob, deathEvent, snapshot),
            activeMob.runtime
        )
        addCpuNanos(activeMob.sessionKey, System.nanoTime() - startedAt)
    }

    private fun runEntityDeathCallbacks(activeMob: ActiveEntityMob, entity: Entity, event: EntityDeathEvent? = null) {
        val snapshot = getSessionLoadSnapshot(activeMob.sessionKey)
        val startedAt = System.nanoTime()
        activeMob.mobType.onDeath(
            EntityMobDeathContext(plugin, entity, activeMob, event, snapshot),
            activeMob.runtime
        )
        addCpuNanos(activeMob.sessionKey, System.nanoTime() - startedAt)
    }

    private fun addCpuNanos(sessionKey: String, nanos: Long) {
        if (nanos <= 0L) return
        val metrics = sessionLoadMetrics.getOrPut(sessionKey) { SessionLoadMetrics() }
        metrics.accumulatedCpuNanos += nanos
        metrics.lastSeenMillis = System.currentTimeMillis()
    }

    private fun refreshSessionLoadSnapshots(activeCountBySession: Map<String, Int>) {
        val now = System.currentTimeMillis()

        sessionLoadMetrics.forEach { (sessionKey, metrics) ->
            if (!activeCountBySession.containsKey(sessionKey)) {
                metrics.activeMobCount = 0
            }
        }

        activeCountBySession.forEach { (sessionKey, count) ->
            val metrics = sessionLoadMetrics.getOrPut(sessionKey) { SessionLoadMetrics() }
            metrics.activeMobCount = count
            metrics.lastSeenMillis = now
        }

        val iterator = sessionLoadMetrics.entries.iterator()
        while (iterator.hasNext()) {
            val (_, metrics) = iterator.next()
            if (now - metrics.lastUpdatedMillis < LOAD_UPDATE_INTERVAL_MILLIS) {
                continue
            }

            val elapsedSec = ((now - metrics.lastUpdatedMillis).coerceAtLeast(1L) / 1000.0)
            val sampleMspt = (metrics.accumulatedCpuNanos / 1_000_000.0) / (elapsedSec * 20.0)
            val sampleProjectilesPerSec = metrics.projectileLaunchCount / elapsedSec

            metrics.emaCustomMspt = ema(metrics.emaCustomMspt, sampleMspt, LOAD_EMA_ALPHA)
            metrics.emaActiveMobCount = ema(metrics.emaActiveMobCount, metrics.activeMobCount.toDouble(), LOAD_EMA_ALPHA)
            metrics.emaProjectilesPerSecond = ema(metrics.emaProjectilesPerSecond, sampleProjectilesPerSec, LOAD_EMA_ALPHA)

            metrics.lastSnapshot = buildLoadSnapshot(metrics)
            metrics.accumulatedCpuNanos = 0L
            metrics.projectileLaunchCount = 0
            metrics.lastUpdatedMillis = now

            if (
                metrics.activeMobCount <= 0 &&
                metrics.accumulatedCpuNanos == 0L &&
                metrics.projectileLaunchCount == 0 &&
                now - metrics.lastSeenMillis > LOAD_METRICS_TTL_MILLIS
            ) {
                iterator.remove()
            }
        }
    }

    private fun buildLoadSnapshot(metrics: SessionLoadMetrics): MobLoadSnapshot {
        val cpuRatio = (metrics.emaCustomMspt / CPU_PRESSURE_MSPT).coerceIn(0.0, 1.0)
        val mobRatio = (metrics.emaActiveMobCount / MOB_PRESSURE_COUNT).coerceIn(0.0, 1.0)
        val projectileRatio = (metrics.emaProjectilesPerSecond / PROJECTILE_PRESSURE_PER_SEC).coerceIn(0.0, 1.0)

        val score = (
            cpuRatio * CPU_WEIGHT +
                mobRatio * MOB_WEIGHT +
                projectileRatio * PROJECTILE_WEIGHT
            ).coerceIn(0.0, 1.0)

        val level = when {
            score >= 0.80 -> MobLoadLevel.CRITICAL
            score >= 0.65 -> MobLoadLevel.HOT
            score >= 0.45 -> MobLoadLevel.WARM
            else -> MobLoadLevel.NORMAL
        }

        return when (level) {
            MobLoadLevel.NORMAL -> MobLoadSnapshot(
                level = level,
                score = score,
                abilityCooldownMultiplier = 1.0,
                abilityExecutionSkipChance = 0.0,
                searchIntervalMultiplier = 1,
                spawnIntervalMultiplier = 1.0,
                spawnSkipChance = 0.0
            )
            MobLoadLevel.WARM -> MobLoadSnapshot(
                level = level,
                score = score,
                abilityCooldownMultiplier = 1.1,
                abilityExecutionSkipChance = 0.05,
                searchIntervalMultiplier = 1,
                spawnIntervalMultiplier = 1.1,
                spawnSkipChance = 0.05
            )
            MobLoadLevel.HOT -> MobLoadSnapshot(
                level = level,
                score = score,
                abilityCooldownMultiplier = 1.25,
                abilityExecutionSkipChance = 0.12,
                searchIntervalMultiplier = 2,
                spawnIntervalMultiplier = 1.3,
                spawnSkipChance = 0.20
            )
            MobLoadLevel.CRITICAL -> MobLoadSnapshot(
                level = level,
                score = score,
                abilityCooldownMultiplier = 1.45,
                abilityExecutionSkipChance = 0.20,
                searchIntervalMultiplier = 2,
                spawnIntervalMultiplier = 1.6,
                spawnSkipChance = 0.40
            )
        }
    }

    private fun resolveSessionKey(options: MobSpawnOptions, worldName: String?): String {
        val explicit = options.sessionKey?.trim().orEmpty()
        if (explicit.isNotBlank()) {
            return normalizeSessionKey(explicit)
        }

        val metadataWorld = options.metadata["world"]?.trim().orEmpty().ifBlank { worldName.orEmpty() }
        if (metadataWorld.isNotBlank()) {
            return normalizeSessionKey("${options.featureId}:$metadataWorld")
        }

        return normalizeSessionKey("${options.featureId}:global")
    }

    private fun resolveEntitySessionKey(options: EntityMobSpawnOptions, worldName: String?): String {
        val explicit = options.sessionKey?.trim().orEmpty()
        if (explicit.isNotBlank()) {
            return normalizeSessionKey(explicit)
        }

        val metadataWorld = options.metadata["world"]?.trim().orEmpty().ifBlank { worldName.orEmpty() }
        if (metadataWorld.isNotBlank()) {
            return normalizeSessionKey("${options.featureId}:$metadataWorld")
        }

        return normalizeSessionKey("${options.featureId}:global")
    }

    private fun normalizeSessionKey(sessionKey: String?): String {
        return sessionKey?.trim()?.takeIf { it.isNotBlank() }?.lowercase() ?: "global:default"
    }

    private fun ema(current: Double, sample: Double, alpha: Double): Double {
        return if (current <= 0.0) sample else (current * (1.0 - alpha) + sample * alpha)
    }

    private fun markEntity(entity: LivingEntity, definition: MobDefinition, mobType: MobType, options: MobSpawnOptions) {
        val container = entity.persistentDataContainer
        container.set(mobTypeKey, PersistentDataType.STRING, mobType.id)
        container.set(mobDefinitionKey, PersistentDataType.STRING, definition.id)
        container.set(mobFeatureKey, PersistentDataType.STRING, options.featureId)
        container.set(customMobKey, PersistentDataType.BYTE, if (mobType.isCustom) 1 else 0)
        if (options.metadata.containsKey("slime_downgrade") || options.metadata.containsKey("magma_split_child")) {
            val protectUntil = Bukkit.getCurrentTick().toLong() + DOWNGRADE_AOE_PROTECTION_TICKS
            container.set(downgradeAoEProtectUntilTickKey, PersistentDataType.LONG, protectUntil)
        }
    }

    private fun markEntityLike(
        entity: Entity,
        definition: MobDefinition,
        mobType: EntityMobType,
        options: EntityMobSpawnOptions
    ) {
        val container = entity.persistentDataContainer
        container.set(mobTypeKey, PersistentDataType.STRING, mobType.id)
        container.set(mobDefinitionKey, PersistentDataType.STRING, definition.id)
        container.set(mobFeatureKey, PersistentDataType.STRING, options.featureId)
        container.set(customMobKey, PersistentDataType.BYTE, if (mobType.isCustom) 1 else 0)
    }

    private fun shouldBlockDowngradedAoEDamage(entity: LivingEntity, cause: EntityDamageEvent.DamageCause): Boolean {
        if (!DOWNGRADE_AOE_DAMAGE_CAUSES.contains(cause)) {
            return false
        }
        return isDowngradeAoEProtectionActive(entity)
    }

    private fun isDowngradeAoEProtectionActive(entity: LivingEntity): Boolean {
        val untilTick = entity.persistentDataContainer
            .get(downgradeAoEProtectUntilTickKey, PersistentDataType.LONG)
            ?: return false
        val currentTick = Bukkit.getCurrentTick().toLong()
        if (currentTick <= untilTick) {
            return true
        }
        entity.persistentDataContainer.remove(downgradeAoEProtectUntilTickKey)
        return false
    }

    private fun applyDefinitionStats(entity: LivingEntity, definition: MobDefinition) {
        entity.getAttribute(Attribute.MAX_HEALTH)?.baseValue = definition.health
        entity.getAttribute(Attribute.ATTACK_DAMAGE)?.baseValue = definition.attack
        entity.getAttribute(Attribute.MOVEMENT_SPEED)?.baseValue = definition.movementSpeed
        entity.getAttribute(Attribute.ARMOR)?.baseValue = definition.armor
        entity.health = definition.health
    }

    private fun enforceStrictSpawnState(entity: LivingEntity) {
        if (entity.passengers.isNotEmpty()) {
            entity.passengers.toList().forEach { passenger ->
                entity.removePassenger(passenger)
                passenger.remove()
            }
        }
        val vehicle = entity.vehicle
        if (vehicle != null) {
            entity.leaveVehicle()
            if (entity is Zombie && vehicle.type == EntityType.CHICKEN && vehicle.isValid) {
                vehicle.remove()
            }
        }

        when (entity) {
            is Zombie -> {
                entity.isBaby = false
                cleanupNearbyChickenMounts(entity)
            }
            is Ageable -> entity.setAdult()
        }
    }

    private fun cleanupNearbyChickenMounts(zombie: Zombie) {
        zombie.getNearbyEntities(1.5, 1.5, 1.5)
            .asSequence()
            .filter { it.type == EntityType.CHICKEN }
            .forEach { chicken ->
                if (chicken.passengers.any { passenger -> passenger.uniqueId == zombie.uniqueId }) {
                    chicken.passengers.toList().forEach { passenger ->
                        chicken.removePassenger(passenger)
                        if (passenger.uniqueId != zombie.uniqueId) {
                            passenger.remove()
                        }
                    }
                    chicken.remove()
                }
            }
    }

    private fun applyDefinitionEquipment(entity: LivingEntity, definition: MobDefinition, onlyIfEmpty: Boolean) {
        val equipment = entity.equipment ?: return
        definition.equipment.forEach { (slot, material) ->
            if (onlyIfEmpty && !equipment.getItem(slot).isNullOrAir()) {
                return@forEach
            }
            equipment.setItem(slot, ItemStack(material))
        }
    }

    private fun parseEquipment(mobId: String, section: ConfigurationSection, logPrefix: String): Map<EquipmentSlot, Material> {
        val equipment = mutableMapOf<EquipmentSlot, Material>()
        val equipmentSection = section.getConfigurationSection("equipment") ?: return equipment
        for (slotKey in equipmentSection.getKeys(false)) {
            val slot = parseEquipmentSlot(slotKey)
            if (slot == null) {
                plugin.logger.severe("$logPrefix equipment slot が不正です: $mobId slot=$slotKey")
                continue
            }

            val materialName = equipmentSection.getString(slotKey) ?: continue
            val material = try {
                Material.valueOf(materialName)
            } catch (_: IllegalArgumentException) {
                plugin.logger.severe("$logPrefix equipment material が不正です: $mobId slot=$slotKey material=$materialName")
                continue
            }
            equipment[slot] = material
        }
        return equipment
    }

    private fun parseEquipmentSlot(slotKey: String): EquipmentSlot? {
        return when (slotKey.lowercase()) {
            "helmet" -> EquipmentSlot.HEAD
            "chestplate" -> EquipmentSlot.CHEST
            "leggings" -> EquipmentSlot.LEGS
            "boots" -> EquipmentSlot.FEET
            "main_hand" -> EquipmentSlot.HAND
            "off_hand" -> EquipmentSlot.OFF_HAND
            else -> null
        }
    }

    private fun ItemStack?.isNullOrAir(): Boolean {
        return this == null || this.type.isAir
    }

    private fun ensureCommonDefinitionFile(): File {
        val file = File(plugin.dataFolder, "config/mob_definition.yml")
        if (!file.exists()) {
            file.parentFile.mkdirs()
            plugin.saveResource("config/mob_definition.yml", false)
        }
        return file
    }

}
