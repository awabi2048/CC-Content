package jp.awabi2048.cccontent.mob.ability

import org.bukkit.Bukkit
import org.bukkit.FluidCollisionMode
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Vector
import java.util.UUID
import java.util.WeakHashMap

class BoomerangService private constructor(private val plugin: JavaPlugin) {
    data class LaunchSpec(
        val ownerId: UUID,
        val targetId: UUID,
        val start: Location,
        val velocityPerTick: Vector,
        val damage: Double,
        val maxLifetimeTicks: Long,
        val hitRadius: Double = 0.45
    )

    private data class ActiveBoomerang(
        val ownerId: UUID,
        val targetId: UUID,
        val worldId: UUID,
        val origin: Vector,
        val direction: Vector,
        val speedPerTick: Double,
        val damage: Double,
        val maxLifetimeTicks: Long,
        var remainingTicks: Long,
        val hitRadius: Double,
        var returning: Boolean,
        var lineProgress: Double,
        val outboundHitPlayers: MutableSet<UUID>,
        val returnHitPlayers: MutableSet<UUID>
    )

    companion object {
        private val instances = WeakHashMap<JavaPlugin, BoomerangService>()
        private const val RETURN_START_DISTANCE = 16.0
        private const val RETURN_CATCH_DISTANCE_SQUARED = 1.44

        fun getInstance(plugin: JavaPlugin): BoomerangService {
            synchronized(instances) {
                return instances.getOrPut(plugin) { BoomerangService(plugin) }
            }
        }
    }

    private val updateIntervalTicks = 2L
    private val boomerangs = mutableMapOf<UUID, ActiveBoomerang>()
    private var task: BukkitTask? = null

    fun launch(spec: LaunchSpec): Boolean {
        if (boomerangs.containsKey(spec.ownerId)) {
            return false
        }
        val world = spec.start.world ?: return false
        val direction = spec.velocityPerTick.clone().normalize()
        if (!direction.isFinite) {
            return false
        }
        val speed = spec.velocityPerTick.length().coerceAtLeast(0.01)

        ensureTask()
        boomerangs[spec.ownerId] = ActiveBoomerang(
            ownerId = spec.ownerId,
            targetId = spec.targetId,
            worldId = world.uid,
            origin = spec.start.toVector(),
            direction = direction,
            speedPerTick = speed,
            damage = spec.damage,
            maxLifetimeTicks = spec.maxLifetimeTicks,
            remainingTicks = spec.maxLifetimeTicks,
            hitRadius = spec.hitRadius,
            returning = false,
            lineProgress = 0.0,
            outboundHitPlayers = mutableSetOf(),
            returnHitPlayers = mutableSetOf()
        )
        return true
    }

    fun hasActive(ownerId: UUID): Boolean {
        return boomerangs.containsKey(ownerId)
    }

    fun cancelByOwner(ownerId: UUID) {
        boomerangs.remove(ownerId)
    }

    fun shutdown() {
        task?.cancel()
        task = null
        boomerangs.clear()
        synchronized(instances) {
            instances.remove(plugin)
        }
    }

    private fun ensureTask() {
        if (task != null) {
            return
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable { tick() }, updateIntervalTicks, updateIntervalTicks)
    }

    private fun tick() {
        val iterator = boomerangs.entries.iterator()
        while (iterator.hasNext()) {
            val (_, active) = iterator.next()
            val world = Bukkit.getWorld(active.worldId)
            val owner = Bukkit.getEntity(active.ownerId) as? LivingEntity
            if (world == null || owner == null || !owner.isValid || owner.isDead || owner.world.uid != active.worldId) {
                iterator.remove()
                continue
            }

            active.remainingTicks -= updateIntervalTicks
            if (active.remainingTicks <= 0L) {
                iterator.remove()
                continue
            }

            val travelStep = active.speedPerTick * updateIntervalTicks.toDouble()
            val previousProgress = active.lineProgress
            val currentDirectionSign = if (active.returning) -1.0 else 1.0
            var nextProgress = previousProgress + currentDirectionSign * travelStep

            if (!active.returning && nextProgress >= RETURN_START_DISTANCE) {
                active.returning = true
                nextProgress = RETURN_START_DISTANCE - (nextProgress - RETURN_START_DISTANCE)
            }

            val previous = active.origin.clone().add(active.direction.clone().multiply(previousProgress))
            val next = active.origin.clone().add(active.direction.clone().multiply(nextProgress))

            val displacement = next.clone().subtract(previous)
            val distance = displacement.length()
            if (distance <= 0.0001) {
                active.lineProgress = nextProgress
                continue
            }

            val startLoc = Location(world, previous.x, previous.y, previous.z)
            val blockHit = world.rayTraceBlocks(startLoc, displacement.clone().normalize(), distance, FluidCollisionMode.NEVER, true)
            if (blockHit != null) {
                world.spawnParticle(Particle.CRIT, blockHit.hitPosition.toLocation(world), 6, 0.05, 0.05, 0.05, 0.01)
                iterator.remove()
                continue
            }

            damageHitPlayersAlongSegment(world, active, previous, next, owner)

            active.lineProgress = nextProgress
            if (active.returning && nextProgress <= 0.0) {
                if (owner.eyeLocation.toVector().distanceSquared(active.origin) <= RETURN_CATCH_DISTANCE_SQUARED) {
                    world.playSound(owner.location, Sound.ITEM_TRIDENT_RETURN, 0.6f, 1.2f)
                }
                iterator.remove()
                continue
            }

            val particleLoc = Location(world, next.x, next.y, next.z)
            world.spawnParticle(Particle.CRIT, particleLoc, 3, 0.06, 0.06, 0.06, 0.01)
            world.spawnParticle(Particle.CLOUD, particleLoc, 2, 0.04, 0.04, 0.04, 0.001)
        }

        if (boomerangs.isEmpty()) {
            task?.cancel()
            task = null
        }
    }

    private fun damageHitPlayersAlongSegment(
        world: org.bukkit.World,
        active: ActiveBoomerang,
        from: Vector,
        to: Vector,
        owner: LivingEntity
    ) {
        val minX = minOf(from.x, to.x) - active.hitRadius
        val maxX = maxOf(from.x, to.x) + active.hitRadius
        val minY = minOf(from.y, to.y) - active.hitRadius
        val maxY = maxOf(from.y, to.y) + active.hitRadius
        val minZ = minOf(from.z, to.z) - active.hitRadius
        val maxZ = maxOf(from.z, to.z) + active.hitRadius

        val nearby = world.getNearbyEntities(
            Location(world, (minX + maxX) * 0.5, (minY + maxY) * 0.5, (minZ + maxZ) * 0.5),
            (maxX - minX) * 0.5,
            (maxY - minY) * 0.5,
            (maxZ - minZ) * 0.5
        )

        for (entity in nearby) {
            if (entity.uniqueId == active.ownerId) {
                continue
            }
            val living = entity as? LivingEntity ?: continue
            if (living.isDead || !living.isValid) {
                continue
            }
            if (!isPlayer(living)) {
                continue
            }

            val hitSet = if (active.returning) active.returnHitPlayers else active.outboundHitPlayers
            if (!hitSet.add(living.uniqueId)) {
                continue
            }
            if (!intersectsSegment(living, from, to, active.hitRadius)) {
                hitSet.remove(living.uniqueId)
                continue
            }

            living.damage(active.damage, owner)
            world.spawnParticle(Particle.SWEEP_ATTACK, living.location.add(0.0, 1.0, 0.0), 1, 0.0, 0.0, 0.0, 0.0)
            world.playSound(living.location, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, if (active.returning) 1.25f else 1.15f)
        }
    }

    private fun intersectsSegment(entity: LivingEntity, from: Vector, to: Vector, radius: Double): Boolean {
        val center = entity.boundingBox.center
        val ab = to.clone().subtract(from)
        val abLenSq = ab.lengthSquared()
        if (abLenSq <= 1e-6) {
            return center.distanceSquared(from) <= radius * radius
        }
        val ap = center.clone().subtract(from)
        val t = (ap.dot(ab) / abLenSq).coerceIn(0.0, 1.0)
        val closest = from.clone().add(ab.multiply(t))
        return center.distanceSquared(closest) <= radius * radius
    }

    private fun isPlayer(entity: Entity): Boolean {
        return entity.type == org.bukkit.entity.EntityType.PLAYER
    }

    private val Vector.isFinite: Boolean
        get() = x.isFinite() && y.isFinite() && z.isFinite()
}
