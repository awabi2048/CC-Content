package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobRuntimeContext
import jp.awabi2048.cccontent.mob.MobDamagedContext
import jp.awabi2048.cccontent.mob.MobSpawnContext
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.AreaEffectCloud
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector
import kotlin.math.roundToLong
import kotlin.random.Random

data class BuffEffectEntry(
    val effectType: PotionEffectType,
    val durationTicks: Int,
    val amplifier: Int
)

data class DebuffCloudEntry(
    val effectType: PotionEffectType,
    val durationTicks: Int,
    val amplifier: Int,
    val color: Color
)

class WitchRetreatBuffAbility(
    override val id: String,
    private val retreatTriggerDistance: Double = 4.0,
    private val retreatTriggerDurationTicks: Long = 20L,
    private val retreatSearchDistance: Double = 12.0,
    private val retreatCooldownTicks: Long = 100L,
    private val buffRadius: Double = 8.0,
    private val buffIntervalTicks: Long = 100L,
    private val buffEffects: List<BuffEffectEntry> = listOf(
        BuffEffectEntry(PotionEffectType.SPEED, 120, 0),
        BuffEffectEntry(PotionEffectType.REGENERATION, 120, 0)
    ),
    private val retreatDebuffPool: List<DebuffCloudEntry> = listOf(
        DebuffCloudEntry(PotionEffectType.SLOWNESS, 80, 0, Color.fromRGB(90, 95, 130)),
        DebuffCloudEntry(PotionEffectType.WEAKNESS, 80, 0, Color.fromRGB(96, 82, 96)),
        DebuffCloudEntry(PotionEffectType.POISON, 80, 0, Color.fromRGB(74, 138, 64)),
        DebuffCloudEntry(PotionEffectType.BLINDNESS, 60, 0, Color.fromRGB(40, 40, 40))
    ),
    private val retreatDebuffCloudRadius: Float = 2.5f,
    private val retreatDebuffCloudDurationTicks: Int = 80,
    private val meleeRetreatChance: Double = 0.5,
    private val particleIntervalTicks: Long = 8L
) : MobAbility {

    data class Runtime(
        var retreatCooldownTicks: Long = 0L,
        var buffCooldownTicks: Long = 0L,
        var particleCooldownTicks: Long = 0L,
        var closeRangeTicks: Long = 0L,
        var searchPhaseOffsetSteps: Int = 0
    ) : MobAbilityRuntime

    override fun tickIntervalTicks(): Long = 1L

    override fun useLoadAdaptiveTickInterval(): Boolean = true

    override fun createRuntime(context: MobSpawnContext): MobAbilityRuntime {
        return Runtime(searchPhaseOffsetSteps = Random.nextInt(0, SEARCH_PHASE_VARIANTS))
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val rt = runtime as? Runtime ?: return
        val entity = context.entity

        if (rt.retreatCooldownTicks > 0L) {
            rt.retreatCooldownTicks = (rt.retreatCooldownTicks - context.tickDelta).coerceAtLeast(0L)
        }
        if (rt.buffCooldownTicks > 0L) {
            rt.buffCooldownTicks = (rt.buffCooldownTicks - context.tickDelta).coerceAtLeast(0L)
        }
        if (rt.particleCooldownTicks > 0L) {
            rt.particleCooldownTicks = (rt.particleCooldownTicks - context.tickDelta).coerceAtLeast(0L)
        }

        if (!context.isCombatActive()) return

        if (rt.particleCooldownTicks <= 0L) {
            spawnAmbientParticles(entity)
            rt.particleCooldownTicks = particleIntervalTicks
        }

        tryRetreat(entity, rt, context)

        if (rt.buffCooldownTicks <= 0L) {
            applyBuffs(entity, rt, context)
        }
    }

    override fun onDamaged(context: MobDamagedContext, runtime: MobAbilityRuntime?) {
        if (!context.isCombatActive()) return
        if (context.event.isCancelled) return
        val rt = runtime as? Runtime ?: return
        val attacker = context.attacker ?: return
        val damager = context.event.damager as? LivingEntity ?: return
        if (damager.uniqueId != attacker.uniqueId) return
        if (Random.nextDouble() >= meleeRetreatChance.coerceIn(0.0, 1.0)) return
        tryRetreatFromThreat(context.entity, attacker, rt, context.loadSnapshot.abilityCooldownMultiplier)
    }

    private fun spawnAmbientParticles(entity: LivingEntity) {
        val height = entity.height
        val center = entity.location.clone().add(0.0, height * 0.5, 0.0)
        entity.world.spawnParticle(Particle.WITCH, center, 3, 0.25, 0.35, 0.25, 0.02)
    }

    private fun tryRetreat(entity: LivingEntity, rt: Runtime, context: MobRuntimeContext) {
        if (rt.retreatCooldownTicks > 0L) {
            rt.closeRangeTicks = 0L
            return
        }
        if (!entity.isOnGround) {
            rt.closeRangeTicks = 0L
            return
        }

        val nearbyPlayers = entity.getNearbyEntities(
            retreatTriggerDistance, retreatTriggerDistance, retreatTriggerDistance
        ).filterIsInstance<Player>()
            .filter { it.isValid && !it.isDead && it.gameMode != org.bukkit.GameMode.SPECTATOR }

        if (nearbyPlayers.isEmpty()) {
            rt.closeRangeTicks = 0L
            return
        }

        rt.closeRangeTicks = (rt.closeRangeTicks + context.tickDelta).coerceAtMost(retreatTriggerDurationTicks)
        if (rt.closeRangeTicks < retreatTriggerDurationTicks) {
            return
        }

        val closest = nearbyPlayers.minByOrNull { it.location.distanceSquared(entity.location) } ?: return

        tryRetreatFromThreat(entity, closest, rt, context.loadSnapshot.abilityCooldownMultiplier)
    }

    private fun tryRetreatFromThreat(
        entity: LivingEntity,
        threat: LivingEntity,
        rt: Runtime,
        cooldownMultiplier: Double
    ) {
        if (rt.retreatCooldownTicks > 0L) {
            rt.closeRangeTicks = 0L
            return
        }
        if (!entity.isOnGround) {
            rt.closeRangeTicks = 0L
            return
        }

        val away = entity.location.toVector()
            .subtract(threat.location.toVector())
            .setY(0.0)
        if (away.lengthSquared() < 0.0001) return

        val destination = findSafeTeleportDestination(entity, away.normalize())
            ?: return

        val originCenter = entity.location.clone().add(0.0, entity.height * 0.5, 0.0)
        entity.world.spawnParticle(Particle.WITCH, originCenter, 12, 0.3, 0.5, 0.3, 0.05)
        entity.world.playSound(entity.location, Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.4f)

        spawnRetreatDebuffCloud(entity.location)
        entity.teleport(destination)

        val destCenter = destination.clone().add(0.0, entity.height * 0.5, 0.0)
        entity.world.spawnParticle(Particle.WITCH, destCenter, 12, 0.3, 0.5, 0.3, 0.05)

        rt.retreatCooldownTicks = (retreatCooldownTicks * cooldownMultiplier)
            .roundToLong().coerceAtLeast(retreatCooldownTicks)
        rt.closeRangeTicks = 0L
    }

    private fun spawnRetreatDebuffCloud(location: Location) {
        val world = location.world ?: return
        val selected = retreatDebuffPool.randomOrNull() ?: return
        val cloud = world.spawn(location, AreaEffectCloud::class.java)
        cloud.radius = retreatDebuffCloudRadius
        cloud.duration = retreatDebuffCloudDurationTicks
        cloud.setWaitTime(5)
        cloud.reapplicationDelay = 20
        cloud.setColor(selected.color)
        cloud.addCustomEffect(
            PotionEffect(selected.effectType, selected.durationTicks, selected.amplifier, false, true, true),
            true
        )
    }

    private fun findSafeTeleportDestination(entity: LivingEntity, awayDirection: Vector): Location? {
        val start = entity.location
        val perp = Vector(-awayDirection.z, 0.0, awayDirection.x)
        val maxDistance = retreatSearchDistance.toInt()
        if (maxDistance < MIN_RETREAT_DISTANCE_BLOCKS) {
            return null
        }

        for (distance in MIN_RETREAT_DISTANCE_BLOCKS..maxDistance) {
            for (lateralOffset in listOf(0.0, 0.6, -0.6, 1.2, -1.2)) {
                val offset = awayDirection.clone().multiply(distance.toDouble())
                    .add(perp.clone().multiply(lateralOffset))

                val candidate = start.clone().add(offset)
                candidate.yaw = start.yaw
                candidate.pitch = start.pitch

                if (isSafeLocation(candidate)) return candidate

                val upCandidate = candidate.clone().add(0.0, 1.0, 0.0)
                upCandidate.yaw = start.yaw
                upCandidate.pitch = start.pitch
                if (isSafeLocation(upCandidate)) return upCandidate
            }
        }
        return null
    }

    private fun isSafeLocation(loc: Location): Boolean {
        val below = loc.clone().add(0.0, -0.1, 0.0).block
        val feet = loc.block
        val head = loc.clone().add(0.0, 1.0, 0.0).block
        return below.type.isSolid && feet.isPassable && head.isPassable
    }

    private fun applyBuffs(entity: LivingEntity, rt: Runtime, context: MobRuntimeContext) {
        val nearbyMobs = entity.getNearbyEntities(buffRadius, buffRadius, buffRadius)
            .filterIsInstance<LivingEntity>()
            .filter { it !is Player && it.isValid && !it.isDead }

        val targets = nearbyMobs + entity

        val effects = buffEffects.map { entry ->
            PotionEffect(entry.effectType, entry.durationTicks, entry.amplifier, false, true, true)
        }

        targets.forEach { target ->
            effects.forEach { effect ->
                target.addPotionEffect(effect)
            }
        }

        entity.world.spawnParticle(
            Particle.WITCH,
            entity.location.clone().add(0.0, entity.height * 0.5, 0.0),
            10, 0.4, 0.5, 0.4, 0.03
        )

        rt.buffCooldownTicks = (buffIntervalTicks * context.loadSnapshot.abilityCooldownMultiplier)
            .roundToLong().coerceAtLeast(buffIntervalTicks)
    }

    companion object {
        private const val MIN_RETREAT_DISTANCE_BLOCKS = 8
        private const val SEARCH_PHASE_VARIANTS = 16
    }
}
