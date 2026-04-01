package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobDamagedContext
import jp.awabi2048.cccontent.mob.MobRuntimeContext
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Allay
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityCombustEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Vector
import java.util.ArrayDeque
import java.util.UUID
import kotlin.math.*
import kotlin.random.Random

class WaterSpiritAbility(
    override val id: String,
    private val approachDistance: Double = DEFAULT_APPROACH_DISTANCE,
    private val closeRangeDamage: Double = DEFAULT_CLOSE_RANGE_DAMAGE,
    private val farRangeOrbDamage: Double = DEFAULT_FAR_RANGE_ORB_DAMAGE,
    private val sharedCooldownTicks: Long = DEFAULT_SHARED_COOLDOWN_TICKS,
    private val freezeTicks: Long = DEFAULT_FREEZE_TICKS,
    private val orbSpeed: Double = DEFAULT_ORB_SPEED,
    private val orbCount: Int = DEFAULT_ORB_COUNT,
    private val orbitRadius: Double = DEFAULT_ORBIT_RADIUS,
    private val orbitSpeed: Double = DEFAULT_ORBIT_SPEED
) : MobAbility {

    data class Runtime(
        var sharedCooldownTicks: Long = 0L,
        var frozenTicks: Long = 0L,
        var ambientParticleTicks: Long = 0L,
        var orbitalAngle: Double = 0.0,
        var currentAngularSpeed: Double = ANGULAR_SPEED,
        var transitionStartAngularSpeed: Double = ANGULAR_SPEED,
        var targetAngularSpeed: Double = ANGULAR_SPEED,
        var directionChangeTicks: Int = 0,
        var directionTransitionTicksRemaining: Int = 0,
        var smoothedY: Double = 0.0,
        var hasSmoothedY: Boolean = false,
        var delayedTargetUuid: UUID? = null,
        var delayedTargetCenters: ArrayDeque<Vector> = ArrayDeque(),
        var searchPhaseOffsetSteps: Int = 0,
        var syncTask: BukkitTask? = null,
        var damageListener: Listener? = null,
        var combustListener: Listener? = null,
        var interactListener: Listener? = null,
        var plugin: JavaPlugin? = null
    ) : MobAbilityRuntime

    override fun createRuntime(context: jp.awabi2048.cccontent.mob.MobSpawnContext): MobAbilityRuntime {
        return Runtime(
            sharedCooldownTicks = sharedCooldownTicks,
            searchPhaseOffsetSteps = Random.nextInt(0, SEARCH_PHASE_VARIANTS)
        )
    }

    override fun onSpawn(context: jp.awabi2048.cccontent.mob.MobSpawnContext, runtime: MobAbilityRuntime?) {
        val allay = context.entity as? Allay ?: return
        Bukkit.getMobGoals().removeAllGoals(allay)
        allay.isSilent = true
        allay.setGravity(false)

        val rt = runtime as? Runtime ?: return
        rt.plugin = context.plugin

        allay.world!!.playSound(allay.location, Sound.ENTITY_WITHER_SHOOT, 0.6f, 1.2f)

        val damageListener = object : Listener {
            @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
            fun onDamage(event: EntityDamageEvent) {
                if (event.entity.uniqueId == allay.uniqueId) {
                    when (event.cause) {
                        EntityDamageEvent.DamageCause.SUFFOCATION,
                        EntityDamageEvent.DamageCause.FIRE,
                        EntityDamageEvent.DamageCause.FIRE_TICK,
                        EntityDamageEvent.DamageCause.LAVA -> event.isCancelled = true
                        else -> {}
                    }
                }
            }
        }
        Bukkit.getPluginManager().registerEvents(damageListener, context.plugin)
        rt.damageListener = damageListener

        val combustListener = object : Listener {
            @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
            fun onCombust(event: EntityCombustEvent) {
                if (event.entity.uniqueId == allay.uniqueId) {
                    event.isCancelled = true
                }
            }
        }
        Bukkit.getPluginManager().registerEvents(combustListener, context.plugin)
        rt.combustListener = combustListener

        val interactListener = object : Listener {
            @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
            fun onInteract(event: PlayerInteractEntityEvent) {
                if (event.rightClicked.uniqueId == allay.uniqueId) {
                    event.isCancelled = true
                }
            }
        }
        Bukkit.getPluginManager().registerEvents(interactListener, context.plugin)
        rt.interactListener = interactListener

        rt.syncTask = Bukkit.getScheduler().runTaskTimer(context.plugin, Runnable {
            if (!allay.isValid || allay.isDead) {
                rt.syncTask?.cancel()
                rt.syncTask = null
                return@Runnable
            }

            allay.fireTicks = 0
            allay.velocity = Vector(0, 0, 0)

            if (rt.frozenTicks > 0L) {
                rt.frozenTicks -= 1L
                val targetPlayer = findNearestPlayer(allay)
                if (targetPlayer != null && targetPlayer.isValid && !targetPlayer.isDead) {
                    val from = allay.location.clone().add(0.0, 1.0, 0.0)
                    val to = targetPlayer.location.clone().add(0.0, 1.0, 0.0)
                    val dir = to.toVector().subtract(from.toVector())
                    val yaw = Math.toDegrees(atan2(-dir.x, dir.z)).toFloat()
                    val pitch = (-Math.toDegrees(atan2(dir.y, sqrt(dir.x * dir.x + dir.z * dir.z)))).toFloat()
                    val newLoc = allay.location.clone()
                    newLoc.yaw = yaw
                    newLoc.pitch = pitch
                    allay.teleport(newLoc)
                }
                if (rt.frozenTicks == 0L) {
                    val tp = findNearestPlayer(allay)
                    if (tp != null && tp.isValid && !tp.isDead) {
                        val dx = allay.location.x - tp.location.x
                        val dz = allay.location.z - tp.location.z
                        rt.orbitalAngle = atan2(dz, dx)
                    }
                }
                return@Runnable
            }

            val targetPlayer = findNearestPlayer(allay)
            if (targetPlayer == null || !targetPlayer.isValid || targetPlayer.isDead) {
                rt.hasSmoothedY = false
                rt.delayedTargetUuid = null
                rt.delayedTargetCenters.clear()
                return@Runnable
            }

            if (rt.delayedTargetUuid != targetPlayer.uniqueId) {
                rt.delayedTargetUuid = targetPlayer.uniqueId
                rt.delayedTargetCenters.clear()
            }

            rt.delayedTargetCenters.addLast(targetPlayer.location.toVector())
            val delayedCenter = if (rt.delayedTargetCenters.size > TARGET_POSITION_DELAY_TICKS) {
                rt.delayedTargetCenters.removeFirst()
            } else {
                rt.delayedTargetCenters.first()
            }

            rt.directionChangeTicks += 1
            if (rt.directionChangeTicks >= DIRECTION_CHANGE_INTERVAL_TICKS) {
                rt.directionChangeTicks = 0
                rt.targetAngularSpeed = if (Random.nextBoolean()) ANGULAR_SPEED else -ANGULAR_SPEED
                rt.transitionStartAngularSpeed = rt.currentAngularSpeed
                rt.directionTransitionTicksRemaining = DIRECTION_TRANSITION_TICKS
            }

            if (rt.directionTransitionTicksRemaining > 0) {
                val elapsedTicks = DIRECTION_TRANSITION_TICKS - rt.directionTransitionTicksRemaining + 1
                val t = elapsedTicks.toDouble() / DIRECTION_TRANSITION_TICKS.toDouble()
                rt.currentAngularSpeed =
                    rt.transitionStartAngularSpeed + (rt.targetAngularSpeed - rt.transitionStartAngularSpeed) * t
                rt.directionTransitionTicksRemaining -= 1
            } else {
                rt.currentAngularSpeed = rt.targetAngularSpeed
            }
            rt.orbitalAngle += rt.currentAngularSpeed

            val cosA = cos(rt.orbitalAngle)
            val sinA = sin(rt.orbitalAngle)
            val px = delayedCenter.x
            val pz = delayedCenter.z
            val targetY = targetPlayer.location.y + ORBIT_HEIGHT_OFFSET

            if (!rt.hasSmoothedY) {
                rt.smoothedY = targetY
                rt.hasSmoothedY = true
            } else {
                rt.smoothedY += (targetY - rt.smoothedY) * Y_FOLLOW_LERP_FACTOR
            }

            val newX = px + ORBIT_RADIUS * cosA
            val newZ = pz + ORBIT_RADIUS * sinA
            val newY = rt.smoothedY

            val newLoc = Location(allay.world, newX, newY, newZ, allay.location.yaw, allay.location.pitch)
            allay.teleport(newLoc)

            val from = allay.location.clone().add(0.0, 1.0, 0.0)
            val to = targetPlayer.location.clone().add(0.0, 1.0, 0.0)
            val dir = to.toVector().subtract(from.toVector())
            val yaw = Math.toDegrees(atan2(-dir.x, dir.z)).toFloat()
            val pitch = (-Math.toDegrees(atan2(dir.y, sqrt(dir.x * dir.x + dir.z * dir.z)))).toFloat()
            val faceLoc = allay.location.clone()
            faceLoc.yaw = yaw
            faceLoc.pitch = pitch
            allay.teleport(faceLoc)
        }, 1L, 1L)
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val rt = runtime as? Runtime ?: return

        if (rt.sharedCooldownTicks > 0L) {
            rt.sharedCooldownTicks -= 10L
        }
        rt.ambientParticleTicks += 10L

        val entity = context.entity

        spawnAmbientParticles(entity, rt)

        if (!context.isCombatActive()) return

        val target = findNearestPlayer(entity) ?: return
        if (!target.isValid || target.isDead) return

        val distance = entity.location.distance(target.location)

        if (rt.sharedCooldownTicks <= 0L) {
            if (distance <= approachDistance) {
                executeMeleeAttack(entity, target)
                rt.sharedCooldownTicks = sharedCooldownTicks
                if (ENABLE_TELEPORT) {
                    scheduleTeleport(entity, rt, freeFarAttack = true)
                }
            } else if (entity.hasLineOfSight(target)) {
                executeFarAttack(context.plugin, entity, target)
                rt.sharedCooldownTicks = (sharedCooldownTicks * context.loadSnapshot.abilityCooldownMultiplier).roundToLong().coerceAtLeast(sharedCooldownTicks)
            }
        }
    }

    override fun onDamaged(context: MobDamagedContext, runtime: MobAbilityRuntime?) {
        val entity = context.entity
        val world = entity.world ?: return
        world.playSound(entity.location, Sound.ENTITY_BLAZE_HURT, 0.8f, 0.75f)
    }

    override fun onDeath(context: jp.awabi2048.cccontent.mob.MobDeathContext, runtime: MobAbilityRuntime?) {
        val rt = runtime as? Runtime ?: return
        val entity = context.entity
        entity.world?.playSound(entity.location, Sound.ENTITY_BLAZE_DEATH, 1.0f, 0.75f)
        rt.syncTask?.cancel()
        rt.syncTask = null
        rt.damageListener?.let { HandlerList.unregisterAll(it) }
        rt.damageListener = null
        rt.combustListener?.let { HandlerList.unregisterAll(it) }
        rt.combustListener = null
        rt.interactListener?.let { HandlerList.unregisterAll(it) }
        rt.interactListener = null
    }

    private fun findNearestPlayer(entity: LivingEntity): Player? {
        return entity.getNearbyEntities(SEARCH_RANGE, SEARCH_RANGE, SEARCH_RANGE)
            .asSequence()
            .filterIsInstance<Player>()
            .filter { it.isValid && !it.isDead }
            .minByOrNull { it.location.distanceSquared(entity.location) }
    }

    private fun spawnAmbientParticles(entity: LivingEntity, rt: Runtime) {
        if (rt.ambientParticleTicks % 5L != 0L) return
        val loc = entity.location.clone().add(0.0, 1.0, 0.0)
        val world = loc.world ?: return
        for (i in 0 until 20) {
            val angle = Random.nextDouble(0.0, Math.PI * 2)
            val radius = 0.9 + Random.nextDouble(0.0, 0.6)
            val x = cos(angle) * radius
            val z = sin(angle) * radius
            val y = Random.nextDouble(-0.5, 0.5)
            world.spawnParticle(Particle.DOLPHIN, loc.clone().add(x, y, z), 1, 0.0, 0.0, 0.0, 0.0)
        }
    }

    private fun performTeleportEffect(from: Location, to: Location) {
        val world = from.world ?: return
        world.playSound(from, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.8f, 1.5f)
        world.spawnParticle(Particle.DRIPPING_DRIPSTONE_WATER, from.clone().add(0.0, 1.0, 0.0), 120, 1.0, 1.0, 1.0, 0.0)
    }

    private fun executeMeleeAttack(entity: LivingEntity, target: LivingEntity) {
        val loc = target.location.clone().add(0.0, 1.0, 0.0)
        val world = loc.world ?: return
        world.spawnParticle(Particle.SPLASH, loc, 20, 0.3, 0.5, 0.3, 0.1)
        world.spawnParticle(Particle.BUBBLE_COLUMN_UP, loc, 10, 0.2, 0.8, 0.2, 0.05)
        world.playSound(loc, Sound.ENTITY_ELDER_GUARDIAN_DEATH, 0.8f, 2.0f)
        target.damage(closeRangeDamage, entity)
    }

    private fun scheduleTeleport(entity: LivingEntity, rt: Runtime, freeFarAttack: Boolean = false) {
        val plugin = rt.plugin ?: return
        val entityId = entity.uniqueId
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            val alive = Bukkit.getEntity(entityId) as? LivingEntity
            if (alive == null || !alive.isValid || alive.isDead) return@Runnable
            val world = alive.world ?: return@Runnable
            val currentLoc = alive.location

            val target = findNearestPlayer(alive)
            val tp = if (target != null && target.isValid && !target.isDead) target else return@Runnable

            val angle = Random.nextDouble(0.0, Math.PI * 2)
            val newX = tp.location.x + ORBIT_RADIUS * cos(angle)
            val newZ = tp.location.z + ORBIT_RADIUS * sin(angle)
            val newY = tp.location.y + ORBIT_HEIGHT_OFFSET
            val newLoc = Location(world, newX, newY, newZ, currentLoc.yaw, currentLoc.pitch)

            performTeleportEffect(currentLoc, newLoc)

            alive.teleport(newLoc)

            rt.frozenTicks = freezeTicks
            rt.orbitalAngle = angle

            if (freeFarAttack && alive.hasLineOfSight(tp)) {
                executeFarAttack(plugin, alive, tp)
            }
        }, 5L)
    }

    private fun executeFarAttack(plugin: JavaPlugin, entity: LivingEntity, target: LivingEntity) {
        val world = entity.world ?: return
        val origin = entity.location.clone().add(0.0, 1.0, 0.0)

        for (i in 0 until orbCount) {
            val angleOffset = (i.toDouble() / orbCount) * Math.PI * 2
            launchRotatingOrb(plugin, entity, target, origin, angleOffset, farRangeOrbDamage)
        }

        world.playSound(entity.location, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 0.6f, 1.4f)
    }

    private fun launchRotatingOrb(
        plugin: JavaPlugin,
        entity: LivingEntity,
        target: LivingEntity,
        origin: Location,
        angleOffset: Double,
        damage: Double
    ) {
        val world = origin.world ?: return
        var currentPos = origin.clone()
        var traveledDistance = 0.0
        val maxDistance = 30.0
        val stepDistance = orbSpeed / 20.0
        var tickCount = 0L

        object : BukkitRunnable() {
            override fun run() {
                if (!entity.isValid || entity.isDead || !target.isValid || target.isDead) {
                    cancel()
                    return
                }

                if (traveledDistance >= maxDistance) {
                    cancel()
                    return
                }

                tickCount++
                val orbitAngle = angleOffset + tickCount * orbitSpeed
                val orbitOffsetX = cos(orbitAngle) * orbitRadius
                val orbitOffsetZ = sin(orbitAngle) * orbitRadius
                val orbitOffsetY = sin(orbitAngle * 0.7) * orbitRadius * 0.5

                val targetPos = target.location.clone().add(0.0, 1.0, 0.0)
                val toTarget = targetPos.toVector().subtract(currentPos.toVector())
                val distToTarget = toTarget.length()

                if (distToTarget < 1.2) {
                    target.damage(damage, entity)
                    world.spawnParticle(Particle.SPLASH, currentPos, 8, 0.2, 0.2, 0.2, 0.05)
                    world.playSound(currentPos, Sound.ENTITY_PLAYER_SPLASH, 0.5f, 1.2f)
                    cancel()
                    return
                }

                val moveDir = toTarget.normalize().multiply(stepDistance)
                currentPos.add(moveDir.x + orbitOffsetX * 0.1, moveDir.y + orbitOffsetY * 0.05, moveDir.z + orbitOffsetZ * 0.1)
                traveledDistance += stepDistance

                val dustColor = Color.fromRGB(100, 180, 255)
                world.spawnParticle(
                    Particle.DUST,
                    currentPos.clone(),
                    2,
                    0.1, 0.1, 0.1,
                    0.0,
                    Particle.DustOptions(dustColor, 0.6f)
                )
                world.spawnParticle(Particle.BUBBLE, currentPos.clone(), 1, 0.05, 0.05, 0.05, 0.0)
            }
        }.runTaskTimer(plugin, 0L, 1L)
    }

    companion object {
        const val DEFAULT_APPROACH_DISTANCE = 5.0
        const val DEFAULT_CLOSE_RANGE_DAMAGE = 5.0
        const val DEFAULT_FAR_RANGE_ORB_DAMAGE = 3.5
        const val DEFAULT_SHARED_COOLDOWN_TICKS = 60L
        const val DEFAULT_FREEZE_TICKS = 20L
        const val DEFAULT_ORB_SPEED = 16.0
        const val DEFAULT_ORB_COUNT = 3
        const val DEFAULT_ORBIT_RADIUS = 0.4
        const val DEFAULT_ORBIT_SPEED = 0.15
        private const val SEARCH_PHASE_VARIANTS = 16
        private const val SEARCH_RANGE = 40.0

        private const val ENABLE_TELEPORT = false
        private const val ORBIT_RADIUS = 6.0
        private const val ANGULAR_SPEED = 0.039269908169872414 // 45°/sec = 2.25°/tick
        private const val ORBIT_HEIGHT_OFFSET = 2.0
        private const val TARGET_POSITION_DELAY_TICKS = 20
        private const val DIRECTION_CHANGE_INTERVAL_TICKS = 20
        private const val DIRECTION_TRANSITION_TICKS = 10
        private const val Y_FOLLOW_LERP_FACTOR = 0.08
    }
}
