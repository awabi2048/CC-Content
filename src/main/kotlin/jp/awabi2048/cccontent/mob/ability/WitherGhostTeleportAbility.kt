@file:Suppress("USELESS_CAST")

package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobRuntimeContext
import jp.awabi2048.cccontent.mob.MobSpawnContext
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import kotlin.math.ceil

class WitherGhostTeleportAbility(
    override val id: String,
    private val minimumTargetDistance: Double = 10.0,
    private val behindDistance: Double = 1.8,
    private val searchAttempts: Int = 24
) : MobAbility {

    data class Runtime(var lastTeleportTick: Long = -1L) : MobAbilityRuntime

    override fun tickIntervalTicks(): Long = 10L

    override fun createRuntime(context: MobSpawnContext): MobAbilityRuntime {
        return Runtime()
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val rt = runtime as? Runtime ?: return
        val entity = context.entity as? LivingEntity ?: return
        val target = resolveAnchorPlayer(entity) ?: return
        val destination = findDestinationBehindPlayer(target) ?: return
        performTeleport(context, rt, entity, destination)
    }

    private fun resolveAnchorPlayer(entity: LivingEntity): Player? {
        val directTarget = (MobAbilityUtils.resolveTarget(entity) as? Player)?.takeIf {
            isValidTarget(entity, it) && it.location.distanceSquared(entity.location) >= minimumTargetDistance * minimumTargetDistance
        }
        if (directTarget != null) {
            return directTarget
        }

        return entity.getNearbyEntities(32.0, 24.0, 32.0)
            .asSequence()
            .mapNotNull { it as? Player }
            .filter { isValidTarget(entity, it) }
            .filter { it.location.distanceSquared(entity.location) >= minimumTargetDistance * minimumTargetDistance }
            .minByOrNull { it.location.distanceSquared(entity.location) }
    }

    private fun isValidTarget(entity: LivingEntity, player: Player): Boolean {
        if (!player.isValid || player.isDead) return false
        if (player.gameMode == GameMode.SPECTATOR) return false
        return player.world.uid == entity.world.uid
    }

    private fun findDestinationBehindPlayer(player: Player): Location? {
        val facing = player.location.direction.clone().setY(0.0)
        val normalized = if (facing.lengthSquared() > 0.0001) facing.normalize() else org.bukkit.util.Vector(0.0, 0.0, 1.0)
        val desiredBehind = player.location.clone().subtract(normalized.multiply(behindDistance)).add(0.0, 0.1, 0.0)
        return EndThemeEffects.findNearbyTeleportLocation(desiredBehind, 1.6, attempts = searchAttempts)
    }

    private fun performTeleport(context: MobRuntimeContext, rt: Runtime, entity: LivingEntity, destination: Location) {
        val world = entity.world
        val source = entity.location.clone()
        val sourceCenter = source.clone().add(0.0, 1.0, 0.0)
        val destCenter = destination.clone().add(0.0, 0.9, 0.0)

        world.playSound(source, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.6f)
        world.spawnParticle(Particle.PORTAL, sourceCenter, 24, 0.5, 1.0, 0.5, 0.02)
        world.spawnParticle(Particle.SMOKE, sourceCenter, 12, 0.3, 0.25, 0.3, 0.01)

        entity.teleport(destination)

        world.playSound(destination, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.6f)
        world.spawnParticle(Particle.PORTAL, destCenter, 20, 0.4, 0.45, 0.4, 0.02)
        world.spawnParticle(Particle.SMOKE, destCenter, 10, 0.3, 0.25, 0.3, 0.01)

        spawnDelayedTrail(context, destCenter, sourceCenter)

        rt.lastTeleportTick = context.activeMob.tickCount
    }

    private fun spawnDelayedTrail(context: MobRuntimeContext, from: Location, to: Location) {
        val delta = to.toVector().subtract(from.toVector())
        val distance = delta.length()
        if (distance <= 0.5) return

        val totalSegments = maxOf(24, ceil(distance / 0.18).toInt())
        val batches = 4
        val segmentsPerBatch = totalSegments / batches
        val plugin = context.plugin

        for (batch in 0 until batches) {
            val startSegment = batch * segmentsPerBatch
            val endSegment = if (batch == batches - 1) totalSegments else (batch + 1) * segmentsPerBatch
            val delayTicks = (batch + 1).toLong()

            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                for (step in startSegment..endSegment) {
                    val t = step.toDouble() / totalSegments.toDouble()
                    val point = from.clone().add(delta.clone().multiply(t))
                    from.world.spawnParticle(Particle.PORTAL, point, 6, 0.35, 0.9, 0.35, 0.02)
                }
            }, delayTicks)
        }
    }
}
