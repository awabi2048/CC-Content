package jp.awabi2048.cccontent.mob

import jp.awabi2048.cccontent.mob.type.HuskBowOnlyMobType
import jp.awabi2048.cccontent.mob.type.HuskBowSwapMobType
import jp.awabi2048.cccontent.mob.type.HuskLeapOnlyMobType
import jp.awabi2048.cccontent.mob.type.HuskNormalMobType
import jp.awabi2048.cccontent.mob.type.HuskShieldOnlyMobType
import jp.awabi2048.cccontent.mob.type.HuskWeakeningAuraMobType
import jp.awabi2048.cccontent.mob.type.GuardianBeamBurstMobType
import jp.awabi2048.cccontent.mob.type.GuardianDrainMobType
import jp.awabi2048.cccontent.mob.type.GuardianNormalMobType
import jp.awabi2048.cccontent.mob.type.GuardianSmallMobType
import jp.awabi2048.cccontent.mob.type.IronGolemMagnetMobType
import jp.awabi2048.cccontent.mob.type.IronGolemNormalMobType
import jp.awabi2048.cccontent.mob.type.SilverfishBigPoisonMobType
import jp.awabi2048.cccontent.mob.type.SilverfishPlainMobType
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
import jp.awabi2048.cccontent.mob.type.SpiderBroodlingMobType
import jp.awabi2048.cccontent.mob.type.SpiderBroodmotherMobType
import jp.awabi2048.cccontent.mob.type.SpiderFerociousMobType
import jp.awabi2048.cccontent.mob.type.SpiderPlainMobType
import jp.awabi2048.cccontent.mob.type.SpiderStealthMobType
import jp.awabi2048.cccontent.mob.type.SpiderSwiftMobType
import jp.awabi2048.cccontent.mob.type.SpiderVenomFrenzyMobType
import jp.awabi2048.cccontent.mob.type.ZombieBowOnlyMobType
import jp.awabi2048.cccontent.mob.type.ZombieBowSwapMobType
import jp.awabi2048.cccontent.mob.type.ZombieLeapOnlyMobType
import jp.awabi2048.cccontent.mob.type.ZombieNormalMobType
import jp.awabi2048.cccontent.mob.type.ZombieShieldOnlyMobType
import jp.awabi2048.cccontent.mob.ability.BoomerangService
import jp.awabi2048.cccontent.mob.ability.HomingArrowService
import jp.awabi2048.cccontent.mob.ability.ThrownWeaponService
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.attribute.Attribute
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.EntityType
import org.bukkit.entity.AbstractArrow
import org.bukkit.entity.Ageable
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Zombie
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityCombustEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.io.File
import java.util.UUID
import java.util.WeakHashMap

class MobService(private val plugin: JavaPlugin) {
    companion object {
        private val instances = WeakHashMap<JavaPlugin, MobService>()

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
    private val activeMobs = mutableMapOf<UUID, ActiveMob>()
    private val definitions = mutableMapOf<String, MobDefinition>()
    private val sessionLoadMetrics = mutableMapOf<String, SessionLoadMetrics>()
    private val mobTypeKey = NamespacedKey(plugin, "mob_type_id")
    private val mobDefinitionKey = NamespacedKey(plugin, "mob_definition_id")
    private val mobFeatureKey = NamespacedKey(plugin, "mob_feature_id")
    private val customMobKey = NamespacedKey(plugin, "is_custom_mob")
    private val skeletonEffectArrowKey = NamespacedKey(plugin, "skeleton_effect_arrow")
    private val skeletonEffectArrowTypeKey = NamespacedKey(plugin, "skeleton_effect_arrow_type")
    private val skeletonEffectArrowAmplifierKey = NamespacedKey(plugin, "skeleton_effect_arrow_amp")
    private val skeletonEffectArrowDurationKey = NamespacedKey(plugin, "skeleton_effect_arrow_duration")
    private var tickTask: BukkitTask? = null

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
        registerMobType(IronGolemNormalMobType())
        registerMobType(IronGolemMagnetMobType())
        registerMobType(GuardianNormalMobType())
        registerMobType(GuardianSmallMobType())
        registerMobType(GuardianBeamBurstMobType())
        registerMobType(GuardianDrainMobType())
    }

    fun registerMobType(mobType: MobType) {
        customMobTypes[mobType.id.lowercase()] = mobType
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
            val resolvedType = resolveMobType(typeId)
            if (resolvedType == null) {
                plugin.logger.severe("$logPrefix mob_definition.yml の type が不正です: $mobId type=$typeId")
                continue
            }

            val equipment = parseEquipment(mobId, mobSection, logPrefix)
            val spawnConditions = parseSpawnConditions(mobId, mobSection, logPrefix)
            loaded[mobId] = MobDefinition(
                id = mobId,
                typeId = resolvedType.id,
                health = mobSection.getDouble("health", 20.0).coerceAtLeast(1.0),
                attack = mobSection.getDouble("attack", 1.0).coerceAtLeast(0.0),
                movementSpeed = mobSection.getDouble("movement_speed", 0.23).coerceAtLeast(0.01),
                armor = mobSection.getDouble("armor", 0.0).coerceAtLeast(0.0),
                scale = mobSection.getDouble("scale", 1.0).coerceAtLeast(0.1),
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
        enforceStrictSpawnState(entity)
        markEntity(entity, definition, mobType, options)
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

        mobType.applyDefaultEquipment(spawnContext, runtime)
        mobType.onSpawn(spawnContext, runtime)
        applyDefinitionEquipment(entity, definition, onlyIfEmpty = false)
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (!entity.isValid || entity.isDead) return@Runnable
            enforceStrictSpawnState(entity)
        }, 1L)
        return entity
    }

    fun getActiveMob(entityId: UUID): ActiveMob? {
        return activeMobs[entityId]
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

    fun handleProjectileEffects(event: EntityDamageByEntityEvent) {
        ThrownWeaponService.getInstance(plugin).handleProjectileDamage(event)

        val target = event.entity as? LivingEntity ?: return
        val arrow = event.damager as? AbstractArrow ?: return
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

    fun handleShootBow(event: EntityShootBowEvent) {
        val shooter = event.entity as? LivingEntity ?: return
        val activeMob = activeMobs[shooter.uniqueId] ?: return
        event.isCancelled = true
    }

    fun handleProjectileHit(event: ProjectileHitEvent) {
        ThrownWeaponService.getInstance(plugin).handleProjectileHit(event)
        val arrow = event.entity
        if (arrow != null) {
            HomingArrowService.getInstance(plugin).handleHit(arrow.uniqueId)
        }
    }

    fun handleEntityPickupItem(event: EntityPickupItemEvent) {
        ThrownWeaponService.getInstance(plugin).handleItemPickup(event)
    }

    fun startTickTask(intervalTicks: Long = 10L) {
        if (tickTask != null) {
            return
        }

        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            val activeCountBySession = mutableMapOf<String, Int>()
            val iterator = activeMobs.entries.iterator()
            while (iterator.hasNext()) {
                val (_, activeMob) = iterator.next()
                val entity = Bukkit.getEntity(activeMob.entityId) as? LivingEntity
                if (entity == null || !entity.isValid || entity.isDead) {
                    iterator.remove()
                    continue
                }

                activeCountBySession[activeMob.sessionKey] = (activeCountBySession[activeMob.sessionKey] ?: 0) + 1

                activeMob.tickCount += intervalTicks
                val snapshot = getSessionLoadSnapshot(activeMob.sessionKey)
                val startedAt = System.nanoTime()
                activeMob.mobType.onTick(
                    MobRuntimeContext(plugin, entity, activeMob, snapshot),
                    activeMob.runtime
                )
                val elapsedNanos = System.nanoTime() - startedAt
                addCpuNanos(activeMob.sessionKey, elapsedNanos)
            }

            refreshSessionLoadSnapshots(activeCountBySession)
        }, intervalTicks, intervalTicks)
    }

    fun shutdown() {
        tickTask?.cancel()
        tickTask = null
        activeMobs.clear()
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
    }

    fun handleAttack(event: EntityDamageByEntityEvent, attacker: LivingEntity) {
        val activeMob = activeMobs[attacker.uniqueId] ?: return
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
        val snapshot = getSessionLoadSnapshot(activeMob.sessionKey)
        val startedAt = System.nanoTime()
        activeMob.mobType.onDeath(
            MobDeathContext(plugin, entity, activeMob, event, snapshot),
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
    }

    private fun applyDefinitionStats(entity: LivingEntity, definition: MobDefinition) {
        entity.getAttribute(Attribute.MAX_HEALTH)?.baseValue = definition.health
        entity.getAttribute(Attribute.ATTACK_DAMAGE)?.baseValue = definition.attack
        entity.getAttribute(Attribute.MOVEMENT_SPEED)?.baseValue = definition.movementSpeed
        entity.getAttribute(Attribute.ARMOR)?.baseValue = definition.armor
        entity.getAttribute(Attribute.SCALE)?.baseValue = definition.scale
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
