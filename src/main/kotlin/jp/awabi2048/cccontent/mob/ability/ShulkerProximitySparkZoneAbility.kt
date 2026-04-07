package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobRuntimeContext
import org.bukkit.Color
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.entity.Shulker
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class ShulkerProximitySparkZoneAbility(
    override val id: String,
    private val zoneRadius: Double = 3.2,
    private val scanRange: Double = 18.0,
    private val sparkCooldownTicks: Long = 20L,
    private val sparkBoltCount: Int = 4,
    private val sparkMaxBoltLength: Double = 4.2,
    private val sparkSegmentLength: Double = 0.58,
    private val sparkJitterAmount: Double = 0.34,
    private val sparkBranchProbability: Double = 0.28,
    private val sparkBranchLengthDecay: Double = 0.65,
    private val sparkMaxBranchDepth: Int = 2
) : MobAbility {

    data class Runtime(
        var sparkCooldownRemainingTicks: Long = 0L
    ) : MobAbilityRuntime

    override fun tickIntervalTicks(): Long = 1L

    override fun createRuntime(context: jp.awabi2048.cccontent.mob.MobSpawnContext): MobAbilityRuntime {
        return Runtime()
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val shulker = context.entity as? Shulker ?: return
        val rt = runtime as? Runtime ?: return

        if (rt.sparkCooldownRemainingTicks > 0L) {
            rt.sparkCooldownRemainingTicks = (rt.sparkCooldownRemainingTicks - context.tickDelta).coerceAtLeast(0L)
        }

        renderZoneRing(shulker, zoneRadius)

        if (!context.isCombatActive()) {
            return
        }

        val inZonePlayer = shulker.getNearbyEntities(scanRange, scanRange, scanRange)
            .asSequence()
            .mapNotNull { it as? Player }
            .firstOrNull { player ->
                player.isValid && !player.isDead && player.world.uid == shulker.world.uid &&
                    player.location.distanceSquared(shulker.location) <= zoneRadius * zoneRadius
            }
            ?: return

        if (rt.sparkCooldownRemainingTicks > 0L) {
            return
        }

        emitSpark(shulker, inZonePlayer)
        rt.sparkCooldownRemainingTicks = sparkCooldownTicks.coerceAtLeast(1L)
    }

    private fun renderZoneRing(shulker: Shulker, radius: Double) {
        val world = shulker.world
        val center = shulker.location.clone().add(0.0, 0.8, 0.0)
        val points = 14
        repeat(points) { index ->
            val angle = (2.0 * PI * index.toDouble() / points.toDouble()) + (shulker.ticksLived.toDouble() * 0.07)
            val point = center.clone().add(cos(angle) * radius, 0.0, sin(angle) * radius)
            world.spawnParticle(Particle.END_ROD, point, 1, 0.0, 0.0, 0.0, 0.0)
            world.spawnParticle(
                Particle.DUST_COLOR_TRANSITION,
                point,
                1,
                0.02,
                0.02,
                0.02,
                0.0,
                Particle.DustTransition(Color.fromRGB(110, 210, 255), Color.fromRGB(240, 250, 255), 0.9f)
            )
        }
    }

    private fun emitSpark(shulker: Shulker, target: Player) {
        val world = shulker.world
        val origin = shulker.location.clone().add(0.0, shulker.height * 0.45, 0.0)
        world.playSound(origin, org.bukkit.Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.35f, 1.8f)
        world.playSound(origin, org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.7f, 1.4f)
        val immediateTarget = target
        if (immediateTarget.isValid && !immediateTarget.isDead && immediateTarget.world.uid == shulker.world.uid) {
            val service = jp.awabi2048.cccontent.mob.MobService.getInstance(org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(javaClass))
            service?.issueDirectDamagePermit(shulker.uniqueId, immediateTarget.uniqueId)
            immediateTarget.damage(0.01, shulker)
        }
        val segments = mutableListOf<Segment>()
        repeat(sparkBoltCount.coerceAtLeast(1)) {
            val direction = target.location.toVector().subtract(origin.toVector())
                .takeIf { it.lengthSquared() > 0.0001 }
                ?.normalize()
                ?.add(randomNoise().multiply(0.45))
                ?.normalize()
                ?: randomNoise().normalize()
            generateBranch(
                start = origin.toVector().clone(),
                direction = direction,
                remainingLength = sparkMaxBoltLength,
                depth = 0,
                delayTicks = 0,
                result = segments
            )
        }

        val grouped = segments.groupBy { it.delayTicks }.toSortedMap()
        grouped.forEach { (delay, delayedSegments) ->
            org.bukkit.Bukkit.getScheduler().runTaskLater(
                org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(javaClass),
                Runnable {
                    if (!shulker.isValid || shulker.isDead) return@Runnable
                    delayedSegments.forEach { segment -> renderSegment(world, segment) }
                },
                delay.toLong().coerceAtLeast(0L)
            )
        }
    }

    private fun generateBranch(
        start: org.bukkit.util.Vector,
        direction: org.bukkit.util.Vector,
        remainingLength: Double,
        depth: Int,
        delayTicks: Int,
        result: MutableList<Segment>
    ) {
        var current = start.clone()
        var currentDir = direction.clone()
        var remaining = remainingLength
        while (remaining > 0.0) {
            currentDir = org.bukkit.util.Vector(
                currentDir.x + Random.nextDouble(-sparkJitterAmount, sparkJitterAmount),
                currentDir.y + Random.nextDouble(-sparkJitterAmount, sparkJitterAmount),
                currentDir.z + Random.nextDouble(-sparkJitterAmount, sparkJitterAmount)
            ).let { v ->
                val len = v.length()
                if (len < 0.001) currentDir.clone() else v.multiply(1.0 / len)
            }

            val step = sparkSegmentLength.coerceAtMost(remaining)
            val next = current.clone().add(currentDir.clone().multiply(step))
            result.add(Segment(current.clone(), next.clone(), depth, delayTicks))

            if (depth < sparkMaxBranchDepth && Random.nextDouble() < sparkBranchProbability) {
                val branchDir = org.bukkit.util.Vector(
                    currentDir.x + Random.nextDouble(-sparkJitterAmount, sparkJitterAmount),
                    currentDir.y + Random.nextDouble(-sparkJitterAmount, sparkJitterAmount),
                    currentDir.z + Random.nextDouble(-sparkJitterAmount, sparkJitterAmount)
                ).let { v ->
                    val len = v.length()
                    if (len < 0.001) currentDir.clone() else v.multiply(1.0 / len)
                }
                generateBranch(
                    start = next.clone(),
                    direction = branchDir,
                    remainingLength = remaining * sparkBranchLengthDecay,
                    depth = depth + 1,
                    delayTicks = delayTicks + 1,
                    result = result
                )
            }

            current = next
            remaining -= step
        }
    }

    private fun renderSegment(world: org.bukkit.World, segment: Segment) {
        val delta = segment.end.clone().subtract(segment.start)
        val length = delta.length()
        if (length < 0.01) return
        val step = delta.normalize().multiply(0.6)
        val steps = (length / 0.6).toInt().coerceAtLeast(1)
        var cursor = segment.start.clone()
        repeat(steps) {
            val loc = org.bukkit.Location(world, cursor.x, cursor.y, cursor.z)
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 1, 0.05, 0.05, 0.05, 0.02)
            world.spawnParticle(
                Particle.DUST_COLOR_TRANSITION,
                loc,
                1,
                0.06,
                0.06,
                0.06,
                0.0,
                if (segment.depth == 0) {
                    Particle.DustTransition(Color.fromRGB(100, 200, 255), Color.fromRGB(220, 240, 255), 1.2f)
                } else {
                    Particle.DustTransition(Color.fromRGB(140, 210, 255), Color.fromRGB(230, 245, 255), 1.1f)
                }
            )
            cursor.add(step)
        }
    }

    private fun randomNoise(): org.bukkit.util.Vector {
        return org.bukkit.util.Vector(
            Random.nextDouble(-1.0, 1.0),
            Random.nextDouble(-0.5, 0.5),
            Random.nextDouble(-1.0, 1.0)
        )
    }

    private data class Segment(
        val start: org.bukkit.util.Vector,
        val end: org.bukkit.util.Vector,
        val depth: Int,
        val delayTicks: Int
    )
}
