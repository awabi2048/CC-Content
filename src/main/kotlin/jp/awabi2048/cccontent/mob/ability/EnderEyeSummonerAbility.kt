package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobDeathContext
import jp.awabi2048.cccontent.mob.MobRuntimeContext
import jp.awabi2048.cccontent.mob.MobService
import jp.awabi2048.cccontent.mob.MobSpawnOptions
import org.bukkit.Particle
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToLong
import kotlin.random.Random

class EnderEyeSummonerAbility(
    override val id: String,
    private val summonDefinitionId: String = "ender_eye_hunter",
    private val maxSummonedEyes: Int = 3,
    private val summonCooldownTicks: Long = 90L,
    private val spawnRadius: Double = 3.5
) : MobAbility {

    data class Runtime(
        var cooldownRemainingTicks: Long = 0L,
        var searchPhaseOffsetSteps: Int = 0
    ) : MobAbilityRuntime

    override fun createRuntime(context: jp.awabi2048.cccontent.mob.MobSpawnContext): MobAbilityRuntime {
        return Runtime(searchPhaseOffsetSteps = Random.nextInt(0, SEARCH_PHASE_VARIANTS))
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val rt = runtime as? Runtime ?: return
        if (rt.cooldownRemainingTicks > 0L) {
            rt.cooldownRemainingTicks = (rt.cooldownRemainingTicks - context.tickDelta).coerceAtLeast(0L)
        }
        if (!context.isCombatActive()) {
            return
        }
        if (rt.cooldownRemainingTicks > 0L) {
            return
        }
        if (!MobAbilityUtils.shouldProcessSearchTick(
                context.activeMob.tickCount,
                context.loadSnapshot.searchIntervalMultiplier,
                rt.searchPhaseOffsetSteps
            )) {
            return
        }
        if (Random.nextDouble() < context.loadSnapshot.abilityExecutionSkipChance) {
            return
        }

        val service = MobService.getInstance(context.plugin) ?: return
        val ownerId = context.entity.uniqueId.toString()

        val aliveSummons = context.entity.world.getNearbyEntities(context.entity.location, 64.0, 64.0, 64.0)
            .asSequence()
            .filterIsInstance<org.bukkit.entity.LivingEntity>()
            .count { entity ->
                val active = service.getActiveMob(entity.uniqueId) ?: return@count false
                active.definition.id == summonDefinitionId && active.metadata["summoner_id"] == ownerId
            }

        val missing = (maxSummonedEyes - aliveSummons).coerceAtLeast(0)
        if (missing <= 0) {
            return
        }

        if (service.getDefinition(summonDefinitionId) == null) {
            return
        }

        repeat(missing) { index ->
            val angle = Random.nextDouble(0.0, Math.PI * 2.0) + (Math.PI * 2.0 * index.toDouble() / missing.toDouble())
            val spawn = context.entity.location.clone().add(cos(angle) * spawnRadius, 1.2, sin(angle) * spawnRadius)
            val spawned = service.spawnByDefinitionId(
                summonDefinitionId,
                spawn,
                MobSpawnOptions(
                    featureId = context.activeMob.featureId,
                    sessionKey = context.activeMob.sessionKey,
                    combatActiveProvider = context.activeMob.combatActiveProvider,
                    metadata = context.activeMob.metadata + mapOf(
                        "summoner_id" to ownerId,
                        "summon_slot" to (index % maxSummonedEyes.coerceAtLeast(1)).toString()
                    )
                )
            )
            if (spawned != null) {
                context.entity.world.spawnParticle(Particle.PORTAL, spawn.clone().add(0.0, 0.6, 0.0), 20, 0.35, 0.35, 0.35, 0.04)
                context.entity.world.playSound(spawn, org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.65f)
            }
        }

        val baseCooldown = summonCooldownTicks.coerceAtLeast(1L)
        rt.cooldownRemainingTicks = (baseCooldown * context.loadSnapshot.abilityCooldownMultiplier).roundToLong().coerceAtLeast(baseCooldown)
    }

    override fun onDeath(context: MobDeathContext, runtime: MobAbilityRuntime?) {
        val service = MobService.getInstance(context.plugin) ?: return
        val ownerId = context.entity.uniqueId.toString()
        service.despawnTrackedMobsByMetadata(
            definitionId = summonDefinitionId,
            metadataKey = "summoner_id",
            metadataValue = ownerId
        )
    }

    private companion object {
        const val SEARCH_PHASE_VARIANTS = 16
    }
}
