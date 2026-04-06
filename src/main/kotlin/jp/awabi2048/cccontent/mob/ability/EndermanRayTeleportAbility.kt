package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobRuntimeContext
import org.bukkit.Color
import org.bukkit.FluidCollisionMode
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.attribute.Attribute
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.random.Random

class EndermanRayTeleportAbility(
    override val id: String,
    private val cooldownTicks: Long = 120L,
    private val chargeTicks: Long = 160L,
    private val rayCount: Int = 8,
    private val rayRange: Double = 32.0,
    private val rayHitRadius: Double = 0.7,
    private val drainPercentFromTargetMaxHealth: Double = 0.10
) : MobAbility {

    data class Runtime(
        var cooldownRemainingTicks: Long = 0L,
        var chargeRemainingTicks: Long = 0L,
        var startLocation: Location? = null,
        var rayDirections: List<Vector> = emptyList(),
        var rayEndpoints: List<Location> = emptyList(),
        var chargeElapsedTicks: Long = 0L,
        var hitPlayerId: java.util.UUID? = null,
        var hitLocation: Location? = null,
        var searchPhaseOffsetSteps: Int = 0
    ) : MobAbilityRuntime

    override fun tickIntervalTicks(): Long = 1L

    override fun createRuntime(context: jp.awabi2048.cccontent.mob.MobSpawnContext): MobAbilityRuntime {
        return Runtime(searchPhaseOffsetSteps = Random.nextInt(0, SEARCH_PHASE_VARIANTS))
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val rt = runtime as? Runtime ?: return
        if (rt.cooldownRemainingTicks > 0L) {
            rt.cooldownRemainingTicks = (rt.cooldownRemainingTicks - context.tickDelta).coerceAtLeast(0L)
        }

        if (!context.isCombatActive()) {
            clearChargeState(context.entity, rt)
            return
        }

        if (rt.chargeRemainingTicks > 0L) {
            processCharging(context, rt)
            return
        }

        if (rt.cooldownRemainingTicks > 0L) {
            return
        }
        if (!MobAbilityUtils.shouldProcessSearchTick(
                context.activeMob.tickCount,
                context.loadSnapshot.searchIntervalMultiplier,
                rt.searchPhaseOffsetSteps,
                baseStepTicks = 1L
            )) {
            return
        }
        if (Random.nextDouble() < context.loadSnapshot.abilityExecutionSkipChance) {
            return
        }

        val target = MobAbilityUtils.resolveTarget(context.entity) as? Player ?: return
        if (!target.isValid || target.isDead || !context.entity.hasLineOfSight(target)) {
            return
        }

        startCharge(context.entity, rt)
    }

    private fun startCharge(entity: LivingEntity, runtime: Runtime) {
        val world = entity.world
        runtime.chargeRemainingTicks = chargeTicks.coerceAtLeast(1L)
        runtime.startLocation = entity.location.clone()
        runtime.hitPlayerId = null
        runtime.hitLocation = null
        runtime.chargeElapsedTicks = 0L

        val yawBase = Math.toRadians(entity.location.yaw.toDouble()) + Random.nextDouble(-0.3, 0.3)
        val directions = (0 until rayCount.coerceAtLeast(1)).map { index ->
            val theta = yawBase + (Math.PI * 2.0 * index.toDouble() / rayCount.coerceAtLeast(1).toDouble())
            Vector(-sin(theta), 0.0, cos(theta)).normalize()
        }
        runtime.rayDirections = directions
        runtime.rayEndpoints = directions.map { direction ->
            val hit = world.rayTraceBlocks(
                runtime.startLocation ?: entity.location,
                direction,
                rayRange,
                FluidCollisionMode.NEVER,
                true
            )
            hit?.hitPosition?.toLocation(world) ?: (runtime.startLocation ?: entity.location).clone().add(direction.clone().multiply(rayRange))
        }

        entity.addPotionEffect(org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY, chargeTicks.toInt() + 5, 0, false, false, false))
        world.playSound(entity.location, org.bukkit.Sound.ENTITY_ENDERMAN_SCREAM, 0.8f, 1.5f)
    }

    private fun processCharging(context: MobRuntimeContext, runtime: Runtime) {
        val entity = context.entity
        val world = entity.world
        val start = runtime.startLocation ?: entity.location.clone()
        val maxReach = (runtime.chargeElapsedTicks.toDouble() * RAY_EXPAND_BLOCKS_PER_TICK).coerceAtMost(rayRange)

        if (maxReach > 0.0) {
            runtime.rayEndpoints.forEach { end ->
                renderRayTelegraph(world, start, end, maxReach)
            }
        }

        if (runtime.hitPlayerId == null && maxReach > 0.0) {
            val hit = findFirstHitPlayer(world, entity.uniqueId, start, runtime.rayEndpoints, maxReach)
            if (hit != null) {
                runtime.hitPlayerId = hit.uniqueId
                runtime.hitLocation = hit.location.clone()
            }
        }

        runtime.chargeElapsedTicks += context.tickDelta

        runtime.chargeRemainingTicks = (runtime.chargeRemainingTicks - context.tickDelta).coerceAtLeast(0L)
        if (runtime.chargeRemainingTicks > 0L) {
            return
        }

        executeTeleport(context, runtime)
        val baseCooldown = cooldownTicks.coerceAtLeast(1L)
        runtime.cooldownRemainingTicks = (baseCooldown * context.loadSnapshot.abilityCooldownMultiplier).roundToLong().coerceAtLeast(baseCooldown)
    }

    private fun executeTeleport(context: MobRuntimeContext, runtime: Runtime) {
        val entity = context.entity
        val destination = runtime.hitLocation
            ?: runtime.rayEndpoints.randomOrNull()
            ?: runtime.startLocation
            ?: entity.location

        val safe = EndThemeEffects.findNearbyTeleportLocation(destination, 3.0, attempts = 16) ?: destination
        EndThemeEffects.playTeleportSound(entity.world, entity.location)
        entity.world.spawnParticle(Particle.PORTAL, entity.location.clone().add(0.0, 1.0, 0.0), 68, 0.9, 0.9, 0.9, 0.06)
        entity.teleport(safe)
        EndThemeEffects.playTeleportSound(entity.world, safe)
        EndThemeEffects.spawnTeleportDebuffField(context.plugin, entity, safe)

        val hitPlayer = runtime.hitPlayerId?.let { org.bukkit.Bukkit.getPlayer(it) }
        if (hitPlayer != null && hitPlayer.isValid && !hitPlayer.isDead && hitPlayer.world.uid == entity.world.uid) {
            val maxTargetHealth = hitPlayer.getAttribute(Attribute.MAX_HEALTH)?.value ?: hitPlayer.maxHealth
            val drained = (maxTargetHealth * drainPercentFromTargetMaxHealth).coerceAtLeast(0.0)
            if (drained > 0.0) {
                hitPlayer.damage(drained, entity)
                val maxSelfHealth = entity.getAttribute(Attribute.MAX_HEALTH)?.value ?: entity.maxHealth
                entity.health = (entity.health + drained).coerceAtMost(maxSelfHealth)
            }
        }

        clearChargeState(entity, runtime)
    }

    private fun clearChargeState(entity: LivingEntity, runtime: Runtime) {
        runtime.chargeRemainingTicks = 0L
        runtime.startLocation = null
        runtime.rayDirections = emptyList()
        runtime.rayEndpoints = emptyList()
        runtime.chargeElapsedTicks = 0L
        runtime.hitPlayerId = null
        runtime.hitLocation = null
        entity.removePotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY)
    }

    private fun renderRayTelegraph(world: org.bukkit.World, from: Location, to: Location, maxReach: Double) {
        val direction = to.toVector().subtract(from.toVector())
        val length = direction.length()
        if (length <= 0.0001) return
        val normalized = direction.normalize()
        val renderLength = minOf(length, maxReach)
        if (renderLength <= 0.0001) return

        var traveled = 0.0
        var cursor = from.clone().add(0.0, 0.1, 0.0)
        while (traveled <= renderLength) {
            val noiseX = Random.nextDouble(-RAY_NOISE_RADIUS, RAY_NOISE_RADIUS)
            val noiseY = Random.nextDouble(-RAY_NOISE_RADIUS, RAY_NOISE_RADIUS)
            val noiseZ = Random.nextDouble(-RAY_NOISE_RADIUS, RAY_NOISE_RADIUS)
            world.spawnParticle(
                Particle.DUST,
                cursor.clone().add(noiseX, noiseY, noiseZ),
                1,
                0.0,
                0.0,
                0.0,
                0.0,
                Particle.DustOptions(Color.fromRGB(171, 112, 230), 1.0f)
            )
            cursor = cursor.add(normalized.clone().multiply(RAY_RENDER_STEP))
            traveled += RAY_RENDER_STEP
        }
    }

    private fun findFirstHitPlayer(
        world: org.bukkit.World,
        ownerId: java.util.UUID,
        from: Location,
        rayEnds: List<Location>,
        maxReach: Double
    ): Player? {
        for (end in rayEnds) {
            val segment = end.toVector().subtract(from.toVector())
            val length = segment.length()
            if (length <= 0.0001) continue
            val dir = segment.normalize()
            var traveled = 0.0
            val cappedLength = minOf(length, maxReach)
            var cursor = from.clone().add(0.0, 0.1, 0.0)
            while (traveled <= cappedLength) {
                val nearby = world.getNearbyEntities(cursor, rayHitRadius, rayHitRadius, rayHitRadius)
                for (entity in nearby) {
                    val player = entity as? Player ?: continue
                    if (player.uniqueId == ownerId || !player.isValid || player.isDead) continue
                    return player
                }
                cursor = cursor.add(dir.clone().multiply(RAY_RENDER_STEP))
                traveled += RAY_RENDER_STEP
            }
        }
        return null
    }

    private companion object {
        const val SEARCH_PHASE_VARIANTS = 16
        const val RAY_EXPAND_BLOCKS_PER_TICK = 1.0 / 20.0
        const val RAY_RENDER_STEP = 0.1
        const val RAY_NOISE_RADIUS = 0.03
    }
}
