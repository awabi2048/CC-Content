package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobRuntimeContext
import jp.awabi2048.cccontent.mob.MobSpawnContext
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.block.Block
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.Vector
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin

class SandShockwaveAbility(
    override val id: String,
    private val cooldownTicks: Long = 120L,
    private val maxRadius: Double = 16.0,
    private val propagationBlocksPerSecond: Double = 5.0,
    private val angleSegments: Int = 48,
    private val updateIntervalTicks: Long = 4L,
    private val jumpVelocity: Double = 0.42,
    private val hitRadius: Double = 1.2,
    private val soundRadius: Double = 5.0,
    private val soundVolume: Float = 1.0f,
    private val damage: Double = 11.0,
    private val horizontalKnockback: Double = 1.0,
    private val verticalKnockback: Double = 0.25
) : MobAbility {

    data class Front(
        val direction: Vector,
        var distance: Double = 0.0,
        var stopped: Boolean = false
    )

    data class Shockwave(
        val origin: Location,
        val fronts: MutableList<Front>,
        val hitPlayers: MutableSet<UUID> = mutableSetOf(),
        var updateTicks: Long = 0L
    )

    data class Runtime(
        var cooldownTicks: Long = 0L,
        var waitingForLanding: Boolean = false,
        var wasOnGround: Boolean = true,
        val activeShockwaves: MutableList<Shockwave> = mutableListOf()
    ) : MobAbilityRuntime

    override fun tickIntervalTicks(): Long = 1L

    override fun createRuntime(context: MobSpawnContext): MobAbilityRuntime {
        return Runtime(wasOnGround = context.entity.isOnGround)
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val rt = runtime as? Runtime ?: return
        val entity = context.entity

        if (rt.cooldownTicks > 0L) {
            rt.cooldownTicks = (rt.cooldownTicks - context.tickDelta).coerceAtLeast(0L)
        }

        val onGround = entity.isOnGround
        if (rt.waitingForLanding && onGround && !rt.wasOnGround) {
            startShockwave(entity, rt)
        }
        rt.wasOnGround = onGround

        tickShockwaves(entity, context, rt)

        if (!context.isCombatActive()) return
        if (rt.cooldownTicks > 0L || rt.waitingForLanding) return
        if (!entity.isOnGround) return

        val target = MobAbilityUtils.resolveTarget(entity) ?: return
        if (entity.location.distanceSquared(target.location) > maxRadius * maxRadius) return

        MobAbilityUtils.faceTowards(entity, target)
        entity.world.playSound(entity.location, Sound.ENTITY_IRON_GOLEM_DAMAGE, 1.0f, 0.5f)
        entity.velocity = entity.velocity.setY(jumpVelocity)
        rt.waitingForLanding = true
        rt.cooldownTicks = (cooldownTicks * context.loadSnapshot.abilityCooldownMultiplier).roundToLong()
            .coerceAtLeast(cooldownTicks)
    }

    private fun startShockwave(entity: LivingEntity, runtime: Runtime) {
        runtime.waitingForLanding = false
        runtime.activeShockwaves.add(
            Shockwave(
                origin = entity.location.clone(),
                fronts = buildFronts()
            )
        )
        entity.world.playSound(entity.location, Sound.ENTITY_IRON_GOLEM_DAMAGE, 1.0f, 0.5f)
    }

    private fun buildFronts(): MutableList<Front> {
        val count = angleSegments.coerceAtLeast(8)
        return MutableList(count) { index ->
            val angle = (PI * 2.0) * index.toDouble() / count.toDouble()
            Front(Vector(cos(angle), 0.0, sin(angle)))
        }
    }

    private fun tickShockwaves(entity: LivingEntity, context: MobRuntimeContext, runtime: Runtime) {
        if (runtime.activeShockwaves.isEmpty()) return

        for (shockwave in runtime.activeShockwaves) {
            shockwave.updateTicks += context.tickDelta
            playWaveSoundsForPlayers(shockwave)
            if (shockwave.updateTicks < updateIntervalTicks) continue

            val stepDistance = propagationBlocksPerSecond * shockwave.updateTicks.toDouble() / 20.0
            shockwave.updateTicks = 0L

            for (front in shockwave.fronts) {
                if (front.stopped) continue
                front.distance += stepDistance
                if (front.distance > maxRadius) {
                    front.stopped = true
                    continue
                }

                val frontLocation = shockwave.origin.clone().add(front.direction.clone().multiply(front.distance))
                val floor = resolveFloorBlock(frontLocation)
                if (floor == null) {
                    front.stopped = true
                    continue
                }
                val surface = floor.location.clone().add(0.5, 1.05, 0.5)
                if (!isFrontPassable(surface)) {
                    front.stopped = true
                    continue
                }

                spawnFrontParticles(surface, floor)
                damagePlayers(context.plugin, entity, surface, front.direction, front.distance, shockwave.hitPlayers)
            }

            shockwave.fronts.removeIf { it.stopped }
        }

        runtime.activeShockwaves.removeIf { it.fronts.isEmpty() }
    }

    private fun playWaveSoundsForPlayers(shockwave: Shockwave) {
        val world = shockwave.origin.world ?: return
        val soundDistanceOffset = propagationBlocksPerSecond * shockwave.updateTicks.toDouble() / 20.0
        for (player in world.players) {
            if (!player.isValid || player.isDead) continue
            var nearestSurface: Location? = null
            var nearestFloor: Block? = null
            var nearestDistanceSquared = Double.MAX_VALUE

            for (front in shockwave.fronts) {
                if (front.stopped) continue
                val soundDistance = (front.distance + soundDistanceOffset).coerceAtMost(maxRadius)
                val frontLocation = shockwave.origin.clone().add(front.direction.clone().multiply(soundDistance))
                val floor = resolveFloorBlock(frontLocation) ?: continue
                val surface = floor.location.clone().add(0.5, 1.05, 0.5)
                if (!isFrontPassable(surface)) continue

                if (player.location.distanceSquared(surface) > soundRadius * soundRadius) continue

                val distanceSquared = player.location.distanceSquared(surface)
                if (distanceSquared < nearestDistanceSquared) {
                    nearestDistanceSquared = distanceSquared
                    nearestSurface = surface
                    nearestFloor = floor
                }
            }

            val surface = nearestSurface ?: continue
            val floor = nearestFloor ?: continue
            player.playSound(surface, floor.blockData.soundGroup.breakSound, soundVolume, 0.85f)
        }
    }

    private fun resolveFloorBlock(location: Location): Block? {
        val world = location.world ?: return null
        val startY = location.blockY
        for (offset in 0..3) {
            val block = world.getBlockAt(location.blockX, startY - offset, location.blockZ)
            if (block.type.isSolid) {
                return block
            }
        }
        return null
    }

    private fun isFrontPassable(surface: Location): Boolean {
        val feet = surface.block
        val head = surface.clone().add(0.0, 1.0, 0.0).block
        return feet.isPassable && head.isPassable
    }

    private fun spawnFrontParticles(surface: Location, floor: Block) {
        val world = surface.world ?: return
        world.spawnParticle(Particle.BLOCK, surface, 3, 0.28, 0.9, 0.28, 0.12, floor.blockData)
    }

    private fun damagePlayers(
        plugin: JavaPlugin,
        entity: LivingEntity,
        center: Location,
        direction: Vector,
        distance: Double,
        hitPlayers: MutableSet<UUID>
    ) {
        val world = center.world ?: return
        val dynamicHitRadius = waveBandRadius(distance, extraMargin = 0.25)
        world.getNearbyEntities(center, dynamicHitRadius, 1.4, dynamicHitRadius)
            .asSequence()
            .filterIsInstance<Player>()
            .filter { it.isValid && !it.isDead }
            .filter { hitPlayers.add(it.uniqueId) }
            .forEach { player ->
                player.setMetadata(MeleeKnockbackBoostAbility.IGNORE_METADATA_KEY, FixedMetadataValue(plugin, true))
                try {
                    player.damage(damage, entity)
                } finally {
                    player.removeMetadata(MeleeKnockbackBoostAbility.IGNORE_METADATA_KEY, plugin)
                }
                val knockback = direction.clone().normalize().multiply(horizontalKnockback)
                player.velocity = player.velocity.add(knockback).setY(maxOf(player.velocity.y, verticalKnockback))
            }
    }

    private fun waveBandRadius(distance: Double, extraMargin: Double): Double {
        return maxOf(hitRadius, distance * sin(PI / angleSegments.coerceAtLeast(8)) + extraMargin)
    }
}
