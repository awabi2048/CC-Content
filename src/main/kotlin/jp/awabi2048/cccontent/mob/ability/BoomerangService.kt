package jp.awabi2048.cccontent.mob.ability

import org.bukkit.Bukkit
import org.bukkit.FluidCollisionMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.LivingEntity
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Vector
import java.util.UUID
import java.util.WeakHashMap
import kotlin.random.Random

class BoomerangService private constructor(private val plugin: JavaPlugin) {
    data class LaunchSpec(
        val ownerId: UUID,
        val targetId: UUID,
        val start: Location,
        val velocityPerTick: Vector,
        val damage: Double,
        val maxLifetimeTicks: Long,
        val handItem: ItemStack? = null,
        val onReturn: (() -> Unit)? = null,
        val trailEffect: ((Location) -> Unit)? = null,
        val onHitPlayer: ((LivingEntity) -> Unit)? = null
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
        var returning: Boolean,
        var lineProgress: Double,
        val outboundHitPlayers: MutableSet<UUID>,
        val returnHitPlayers: MutableSet<UUID>,
        val flyingDisplay: FlyingItemDisplay,
        val handItem: ItemStack?,
        val onReturn: (() -> Unit)?,
        val trailEffect: ((Location) -> Unit)?,
        val onHitPlayer: ((LivingEntity) -> Unit)?,
        var ownerDead: Boolean = false
    )

    companion object {
        private val instances = WeakHashMap<JavaPlugin, BoomerangService>()
        private const val RETURN_START_DISTANCE = 16.0
        private const val RETURN_CATCH_DISTANCE = 1.6
        private const val SWEEP_SOUND_INTERVAL_TICKS = 4L
        private const val BOOMERANG_TOKEN_DROP_CHANCE = 0.25

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
        if (boomerangs.containsKey(spec.ownerId)) return false
        val world = spec.start.world ?: return false
        val direction = spec.velocityPerTick.clone().normalize()
        if (!direction.isFinite) return false
        val speed = spec.velocityPerTick.length().coerceAtLeast(0.01)

        val flyingDisplay = FlyingItemDisplay.spawn(world, spec.start, spec.handItem ?: ItemStack(Material.BONE))

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
            returning = false,
            lineProgress = 0.0,
            outboundHitPlayers = mutableSetOf(),
            returnHitPlayers = mutableSetOf(),
            flyingDisplay = flyingDisplay,
            handItem = spec.handItem,
            onReturn = spec.onReturn,
            trailEffect = spec.trailEffect,
            onHitPlayer = spec.onHitPlayer
        )
        return true
    }

    fun hasActive(ownerId: UUID): Boolean = boomerangs.containsKey(ownerId)

    fun markOwnerDead(ownerId: UUID) {
        boomerangs[ownerId]?.ownerDead = true
    }

    fun cancelByOwner(ownerId: UUID) {
        val active = boomerangs.remove(ownerId) ?: return
        cleanup(active)
    }

    fun shutdown() {
        task?.cancel()
        task = null
        boomerangs.values.forEach { cleanup(it) }
        boomerangs.clear()
        synchronized(instances) { instances.remove(plugin) }
    }

    private fun cleanup(active: ActiveBoomerang) {
        active.flyingDisplay.cleanup()
        restoreHandItem(active.ownerId, active.handItem)
    }

    private fun cleanupNoRestore(active: ActiveBoomerang) {
        active.flyingDisplay.cleanup()
    }

    private fun restoreHandItem(ownerId: UUID, handItem: ItemStack?) {
        if (handItem == null) return
        val owner = Bukkit.getEntity(ownerId) as? LivingEntity ?: return
        if (!owner.isValid || owner.isDead) return
        val equipment = owner.equipment ?: return
        if (equipment.itemInMainHand.type.isAir) {
            equipment.setItemInMainHand(handItem)
        }
    }

    private fun ensureTask() {
        if (task != null) return
        task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable { tick() }, updateIntervalTicks, updateIntervalTicks)
    }

    private fun tick() {
        val toRemove = mutableListOf<UUID>()
        for ((ownerId, active) in boomerangs.toList()) {
            val world = Bukkit.getWorld(active.worldId)
            val owner = Bukkit.getEntity(active.ownerId) as? LivingEntity

            if (world == null) {
                cleanup(active)
                toRemove.add(ownerId)
                continue
            }

            val ownerAlive = owner != null && owner.isValid && !owner.isDead && owner.world.uid == active.worldId

            if (!ownerAlive) {
                if (!active.ownerDead) active.ownerDead = true
                tickOwnerDead(world, active)
                cleanupNoRestore(active)
                toRemove.add(ownerId)
                continue
            }

            val safeOwner = owner!!

            active.remainingTicks -= updateIntervalTicks
            if (active.remainingTicks <= 0L) {
                cleanup(active)
                toRemove.add(ownerId)
                continue
            }

            if (active.returning) {
                if (tickReturn(world, active, safeOwner)) {
                    cleanup(active)
                    toRemove.add(ownerId)
                }
            } else {
                if (tickOutbound(world, active, safeOwner)) {
                    cleanup(active)
                    toRemove.add(ownerId)
                }
            }
        }
        toRemove.forEach { boomerangs.remove(it) }

        if (boomerangs.isEmpty()) {
            task?.cancel()
            task = null
        }
    }

    private fun tickOwnerDead(
        world: org.bukkit.World,
        active: ActiveBoomerang
    ) {
        val display = active.flyingDisplay
        val currentPos = if (display.isValid()) {
            Bukkit.getEntity(display.id)?.location?.toVector()
                ?: active.origin.clone().add(active.direction.clone().multiply(active.lineProgress))
        } else {
            active.origin.clone().add(active.direction.clone().multiply(active.lineProgress))
        }

        val distFromOrigin = currentPos.distance(active.origin)
        val maxDist = active.speedPerTick * active.maxLifetimeTicks.toDouble()

        if (!active.returning && distFromOrigin < maxDist) {
            val travelStep = active.speedPerTick * updateIntervalTicks.toDouble()
            val previousProgress = active.lineProgress
            var nextProgress = previousProgress + travelStep

            if (nextProgress >= RETURN_START_DISTANCE) {
                active.returning = true
                nextProgress = RETURN_START_DISTANCE
            }

            val next = active.origin.clone().add(active.direction.clone().multiply(nextProgress))
            active.lineProgress = nextProgress

            if (display.isValid()) {
                val elapsedTicks = active.maxLifetimeTicks - active.remainingTicks
                display.updatePosition(next, active.direction, elapsedTicks)
                active.trailEffect?.invoke(Location(world, next.x, next.y, next.z))
            }
            return
        }

        if (Random.nextDouble() < BOOMERANG_TOKEN_DROP_CHANCE) {
            val dropLoc = currentPos.toLocation(world).add(0.0, 0.5, 0.0)
            val token = world.dropItem(dropLoc, createBoomerangToken())
            token.pickupDelay = 0
            token.velocity = org.bukkit.util.Vector(0.0, 0.1, 0.0)
        }
    }

    private fun createBoomerangToken(): ItemStack {
        return requireNotNull(jp.awabi2048.cccontent.items.CustomItemManager.createItem("arena.boomerang_token", 1)) {
            "arena.boomerang_token is not registered"
        }
    }

    private fun tickOutbound(
        world: org.bukkit.World,
        active: ActiveBoomerang,
        owner: LivingEntity
    ): Boolean {
        val travelStep = active.speedPerTick * updateIntervalTicks.toDouble()
        val previousProgress = active.lineProgress
        var nextProgress = previousProgress + travelStep

        if (nextProgress >= RETURN_START_DISTANCE) {
            active.returning = true
            nextProgress = RETURN_START_DISTANCE
        }

        val previous = active.origin.clone().add(active.direction.clone().multiply(previousProgress))
        val next = active.origin.clone().add(active.direction.clone().multiply(nextProgress))

        val displacement = next.clone().subtract(previous)
        val segDist = displacement.length()

        if (segDist > 0.0001) {
            val startLoc = Location(world, previous.x, previous.y, previous.z)
            val blockHit = world.rayTraceBlocks(startLoc, displacement.clone().normalize(), segDist, FluidCollisionMode.NEVER, true)
            if (blockHit != null) {
                val hitLoc = blockHit.hitPosition.toLocation(world)
                world.spawnParticle(Particle.CRIT, hitLoc, 6, 0.05, 0.05, 0.05, 0.01)
                return true
            }

            active.flyingDisplay.checkHitsAlongSegment(
                world = world,
                from = previous,
                to = next,
                ownerId = active.ownerId,
                hitSet = active.outboundHitPlayers,
                owner = owner,
                damage = active.damage,
                onHitPlayer = active.onHitPlayer
            )
        }

        active.flyingDisplay.updatePosition(next, displacement, active.maxLifetimeTicks - active.remainingTicks)
        active.trailEffect?.invoke(Location(world, next.x, next.y, next.z))
        active.lineProgress = nextProgress

        if (active.returning) {
            val totalElapsed = active.maxLifetimeTicks - active.remainingTicks
            if (totalElapsed > 0L && totalElapsed % SWEEP_SOUND_INTERVAL_TICKS == 0L) {
                world.playSound(Location(world, next.x, next.y, next.z), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.6f, 1.5f)
            }
        }
        return false
    }

    private fun tickReturn(
        world: org.bukkit.World,
        active: ActiveBoomerang,
        owner: LivingEntity
    ): Boolean {
        val ownerPos = owner.eyeLocation.toVector()
        val display = active.flyingDisplay
        val currentBoomerangPos = if (display.isValid()) {
            Bukkit.getEntity(display.id)?.location?.toVector()
                ?: active.origin.clone().add(active.direction.clone().multiply(active.lineProgress))
        } else {
            active.origin.clone().add(active.direction.clone().multiply(active.lineProgress))
        }

        val toOwner = ownerPos.clone().subtract(currentBoomerangPos)
        val distToOwner = toOwner.length()

        if (distToOwner <= RETURN_CATCH_DISTANCE) {
            world.playSound(owner.location, Sound.ITEM_TRIDENT_RETURN, 0.6f, 1.2f)
            active.onReturn?.invoke()
            return true
        }

        val returnSpeed = active.speedPerTick * updateIntervalTicks.toDouble()
        val moveDir = toOwner.normalize()
        val moveStep = moveDir.multiply(returnSpeed.coerceAtMost(distToOwner))
        val newPos = currentBoomerangPos.clone().add(moveStep)

        active.flyingDisplay.checkHitsAlongSegment(
            world = world,
            from = currentBoomerangPos,
            to = newPos,
            ownerId = active.ownerId,
            hitSet = active.returnHitPlayers,
            owner = owner,
            damage = active.damage,
            onHitPlayer = active.onHitPlayer
        )

        active.flyingDisplay.updatePosition(newPos, moveDir, active.maxLifetimeTicks - active.remainingTicks)
        active.trailEffect?.invoke(Location(world, newPos.x, newPos.y, newPos.z))

        val totalElapsed = active.maxLifetimeTicks - active.remainingTicks
        if (totalElapsed > 0L && totalElapsed % SWEEP_SOUND_INTERVAL_TICKS == 0L) {
            world.playSound(Location(world, newPos.x, newPos.y, newPos.z), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.6f, 1.5f)
        }
        return false
    }

    private val Vector.isFinite: Boolean
        get() = x.isFinite() && y.isFinite() && z.isFinite()
}
