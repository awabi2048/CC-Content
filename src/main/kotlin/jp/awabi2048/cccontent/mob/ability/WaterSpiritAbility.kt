@file:Suppress("UNNECESSARY_NOT_NULL_ASSERTION", "UNNECESSARY_SAFE_CALL", "USELESS_ELVIS")

package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobDamagedContext
import jp.awabi2048.cccontent.mob.MobRuntimeContext
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.FluidCollisionMode
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
    private val orbitSpeed: Double = DEFAULT_ORBIT_SPEED,
    private val waterColumnExtraAnglesDegrees: List<Double> = emptyList(),
    private val enhancedAmbientOrbit: Boolean = false
) : MobAbility {

    private val orbitFollowConfig = OrbitFollowConfig(
        orbitRadius = ORBIT_RADIUS,
        orbitHeightOffset = ORBIT_HEIGHT_OFFSET,
        targetPositionDelayTicks = TARGET_POSITION_DELAY_TICKS,
        directionChangeIntervalTicks = DIRECTION_CHANGE_INTERVAL_TICKS,
        directionTransitionTicks = DIRECTION_TRANSITION_TICKS,
        baseAngularSpeed = ANGULAR_SPEED,
        yFollowLerpFactor = Y_FOLLOW_LERP_FACTOR,
        stationarySpeedThreshold = STATIONARY_SPEED_THRESHOLD,
        stationaryEnterTicks = STATIONARY_ENTER_TICKS,
        stationaryExitTicks = STATIONARY_EXIT_TICKS,
        stationaryHoverAmplitude = STATIONARY_HOVER_AMPLITUDE,
        stationaryHoverAngularSpeed = STATIONARY_HOVER_ANGULAR_SPEED
    )

    data class Runtime(
        var sharedCooldownTicks: Long = 0L,
        var frozenTicks: Long = 0L,
        var holdLockTicks: Long = 0L,
        var holdAnchorX: Double = 0.0,
        var holdAnchorY: Double = 0.0,
        var holdAnchorZ: Double = 0.0,
        var ambientParticleTicks: Long = 0L,
        var ambientSoundTicks: Long = 0L,
        var ominousAlternate: Boolean = false,
        var orbitState: OrbitFollowState = OrbitFollowState(ANGULAR_SPEED),
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
                        rt.orbitState.orbitalAngle = atan2(dz, dx)
                    }
                }
                return@Runnable
            }

            if (rt.holdLockTicks > 0L) {
                rt.holdLockTicks -= 1L
                val newLoc = Location(allay.world, rt.holdAnchorX, rt.holdAnchorY, rt.holdAnchorZ)
                val targetPlayer = findNearestPlayer(allay)
                if (targetPlayer != null && targetPlayer.isValid && !targetPlayer.isDead) {
                    val from = allay.location.clone().add(0.0, 1.0, 0.0)
                    val to = targetPlayer.location.clone().add(0.0, 1.0, 0.0)
                    val dir = to.toVector().subtract(from.toVector())
                    val yaw = Math.toDegrees(atan2(-dir.x, dir.z)).toFloat()
                    val pitch = (-Math.toDegrees(atan2(dir.y, sqrt(dir.x * dir.x + dir.z * dir.z)))).toFloat()
                    newLoc.yaw = yaw
                    newLoc.pitch = pitch

                    if (rt.holdLockTicks == 0L) {
                        OrbitFollowController.reanchorFromCurrentPosition(
                            state = rt.orbitState,
                            config = orbitFollowConfig,
                            targetUuid = targetPlayer.uniqueId,
                            followerX = allay.location.x,
                            followerZ = allay.location.z,
                            targetX = targetPlayer.location.x,
                            targetZ = targetPlayer.location.z
                        )
                    }
                }
                allay.teleport(newLoc)
                return@Runnable
            }

            val targetPlayer = findNearestPlayer(allay)
            if (targetPlayer == null || !targetPlayer.isValid || targetPlayer.isDead) {
                OrbitFollowController.clearTarget(rt.orbitState)
                return@Runnable
            }

            val orbitOutput = OrbitFollowController.update(
                state = rt.orbitState,
                config = orbitFollowConfig,
                targetUuid = targetPlayer.uniqueId,
                followerX = allay.location.x,
                followerZ = allay.location.z,
                targetX = targetPlayer.location.x,
                targetY = targetPlayer.location.y,
                targetZ = targetPlayer.location.z
            )

            val newLoc = Location(allay.world, orbitOutput.x, orbitOutput.y, orbitOutput.z, allay.location.yaw, allay.location.pitch)
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
            rt.sharedCooldownTicks = (rt.sharedCooldownTicks - context.tickDelta).coerceAtLeast(0L)
        }
        rt.ambientParticleTicks += context.tickDelta
        rt.ambientSoundTicks += context.tickDelta

        val entity = context.entity

        spawnAmbientParticles(entity, rt)
        playAmbientSound(entity, rt)

        if (!context.isCombatActive()) return

        val target = findNearestPlayer(entity) ?: return
        if (!target.isValid || target.isDead) return

        val distance = entity.location.distance(target.location)

        if (rt.sharedCooldownTicks <= 0L) {
            if (distance <= approachDistance) {
                executeMeleeAttack(context.plugin, entity, target)
                rt.sharedCooldownTicks = sharedCooldownTicks
                if (ENABLE_TELEPORT) {
                    scheduleTeleport(entity, rt, freeFarAttack = true)
                }
            } else if (entity.hasLineOfSight(target)) {
                executeFarAttack(context.plugin, entity, target, rt)
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
        if (!enhancedAmbientOrbit && rt.ambientParticleTicks % 5L != 0L) return
        val loc = entity.location.clone().add(0.0, 1.0, 0.0)
        val world = loc.world ?: return

        if (enhancedAmbientOrbit) {
            val baseAngle = rt.ambientParticleTicks.toDouble() * AMBIENT_ORBIT_ANGULAR_SPEED_PER_TICK
            repeat(5) { index ->
                val angle = baseAngle + (Math.PI * 2.0) * index.toDouble() / 5.0
                val offsetX = cos(angle) * ENHANCED_AMBIENT_ORBIT_RADIUS
                val offsetZ = sin(angle) * ENHANCED_AMBIENT_ORBIT_RADIUS
                val point = loc.clone().add(offsetX, 0.0, offsetZ)
                world.spawnParticle(Particle.DRIPPING_WATER, point, 10, 0.1, 0.1, 0.1, 0.0)
                world.spawnParticle(Particle.WITCH, point, 5, 0.1, 0.1, 0.1, 0.0)
            }
            return
        }

        for (i in 0 until 20) {
            val angle = Random.nextDouble(0.0, Math.PI * 2)
            val radius = 1.8 + Random.nextDouble(0.0, 1.2)
            val x = cos(angle) * radius
            val z = sin(angle) * radius
            val y = Random.nextDouble(-0.5, 0.5)
            world.spawnParticle(Particle.DOLPHIN, loc.clone().add(x, y, z), 1, 0.0, 0.0, 0.0, 0.0)
        }
        val ominousCount = if (rt.ominousAlternate) 1 else 0
        rt.ominousAlternate = !rt.ominousAlternate
        for (i in 0 until ominousCount) {
            val angle = Random.nextDouble(0.0, Math.PI * 2)
            val radius = 0.9 + Random.nextDouble(0.0, 0.6)
            val x = cos(angle) * radius
            val z = sin(angle) * radius
            val y = Random.nextDouble(-0.5, 0.5)
            world.spawnParticle(
                Particle.TRIAL_SPAWNER_DETECTION_OMINOUS,
                loc.clone().add(x, y, z),
                15,
                1.0,
                1.0,
                1.0,
                0.0
            )
        }
    }

    private fun playAmbientSound(entity: LivingEntity, rt: Runtime) {
        if (rt.ambientSoundTicks < 60L) return
        rt.ambientSoundTicks = 0L
        val world = entity.world ?: return
        world.playSound(entity.location, Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM, 0.8f, 0.8f)
    }

    private fun performTeleportEffect(from: Location, to: Location) {
        val world = from.world ?: return
        world.playSound(from, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.8f, 1.5f)
        world.spawnParticle(Particle.DRIPPING_DRIPSTONE_WATER, from.clone().add(0.0, 1.0, 0.0), 120, 1.0, 1.0, 1.0, 0.0)
    }

    private fun executeMeleeAttack(plugin: JavaPlugin, entity: LivingEntity, target: LivingEntity) {
        val baseOrigin = target.location.clone().add(0.0, 0.1, 0.0)
        val origins = mutableListOf(baseOrigin)

        if (waterColumnExtraAnglesDegrees.isNotEmpty()) {
            val fromEntity = baseOrigin.toVector().subtract(entity.location.toVector()).setY(0.0)
            if (fromEntity.lengthSquared() > 1.0e-6) {
                val radius = fromEntity.length()
                val baseDirection = fromEntity.normalize()
                waterColumnExtraAnglesDegrees.forEach { degrees ->
                    val rotated = rotateVectorY(baseDirection.clone(), Math.toRadians(degrees)).multiply(radius)
                    val extraOrigin = entity.location.toVector().clone().add(rotated)
                    origins += Location(entity.world, extraOrigin.x, baseOrigin.y, extraOrigin.z)
                }
            }
        }

        origins.forEach { origin ->
            launchWaterColumn(plugin, entity, target, origin)
        }
    }

    private fun launchWaterColumn(
        plugin: JavaPlugin,
        entity: LivingEntity,
        target: LivingEntity,
        startPosition: Location
    ) {
        val world = target.world ?: return
        val currentPos = startPosition.clone()
        val velocity = Vector(0.0, WATER_COLUMN_INITIAL_SPEED, 0.0)
        val hitPlayers = mutableSetOf<UUID>()

        if (target is Player) {
            target.playSound(target.location, Sound.ENTITY_ELDER_GUARDIAN_DEATH, 0.8f, 2.0f)
        } else {
            world.playSound(currentPos, Sound.ENTITY_ELDER_GUARDIAN_DEATH, 0.8f, 2.0f)
        }

        object : BukkitRunnable() {
            override fun run() {
                if (!entity.isValid || entity.isDead) {
                    cancel()
                    return
                }

                currentPos.add(velocity)
                world.spawnParticle(
                    Particle.DRIPPING_DRIPSTONE_WATER,
                    currentPos,
                    15,
                    1.0,
                    1.0,
                    1.0,
                    0.0
                )

                world.getNearbyEntities(currentPos, WATER_COLUMN_HIT_RADIUS, WATER_COLUMN_HIT_RADIUS, WATER_COLUMN_HIT_RADIUS)
                    .asSequence()
                    .filterIsInstance<Player>()
                    .filter { it.isValid && !it.isDead }
                    .forEach { player ->
                        if (hitPlayers.add(player.uniqueId)) {
                            player.damage(closeRangeDamage * DAMAGE_MULTIPLIER, entity)
                            val lifted = player.velocity.clone().apply {
                                y = max(y, WATER_COLUMN_LAUNCH_Y)
                            }
                            player.velocity = lifted
                        }
                    }

                velocity.multiply(WATER_COLUMN_DRAG)
                if (velocity.length() <= WATER_COLUMN_STOP_SPEED) {
                    cancel()
                }
            }
        }.runTaskTimer(plugin, 0L, 1L)
    }

    private fun rotateVectorY(vector: Vector, radians: Double): Vector {
        val cos = cos(radians)
        val sin = sin(radians)
        val x = vector.x * cos - vector.z * sin
        val z = vector.x * sin + vector.z * cos
        return Vector(x, vector.y, z)
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
            rt.orbitState.orbitalAngle = angle

            if (freeFarAttack && alive.hasLineOfSight(tp)) {
                executeFarAttack(plugin, alive, tp)
            }
        }, 5L)
    }

    private fun executeFarAttack(plugin: JavaPlugin, entity: LivingEntity, target: LivingEntity, runtime: Runtime? = null) {
        val world = entity.world ?: return
        runtime?.let {
            it.holdLockTicks = ORB_HOLD_TICKS + ORB_STAGGER_TICKS * (orbCount - 1)
            it.holdAnchorX = entity.location.x
            it.holdAnchorY = entity.location.y
            it.holdAnchorZ = entity.location.z
        }
        scheduleHeldOrbLaunch(plugin, entity, target)

        world.playSound(entity.location, Sound.ENTITY_ELDER_GUARDIAN_AMBIENT, 0.7f, 1.33f)
    }

    private fun buildPerpendicularBasis(forward: Vector): Pair<Vector, Vector> {
        val reference = if (abs(forward.dot(Vector(0.0, 1.0, 0.0))) > 0.95) {
            Vector(1.0, 0.0, 0.0)
        } else {
            Vector(0.0, 1.0, 0.0)
        }

        var u = forward.clone().crossProduct(reference)
        if (u.lengthSquared() < 1.0e-6) {
            u = forward.clone().crossProduct(Vector(0.0, 0.0, 1.0))
        }
        u.normalize()

        val v = u.clone().crossProduct(forward).normalize()
        return u to v
    }

    private fun scheduleHeldOrbLaunch(
        plugin: JavaPlugin,
        entity: LivingEntity,
        target: LivingEntity
    ) {
        object : BukkitRunnable() {
            var ticks = 0L
            var launchedCount = 0

            override fun run() {
                if (!entity.isValid || entity.isDead || !target.isValid || target.isDead) {
                    cancel()
                    return
                }

                val rotation = ticks.toDouble() * ORB_HOLD_ROTATION_SPEED
                val origin = entity.location.clone().add(0.0, 1.0, 0.0)
                val forward = entity.location.direction.clone().normalize()
                val (basisU, basisV) = buildPerpendicularBasis(forward)

                val holdPositions = (0 until orbCount).map { index ->
                    val baseAngle = (index.toDouble() / orbCount.toDouble()) * Math.PI * 2.0
                    val angle = baseAngle + rotation
                    origin.clone().add(
                        basisU.clone().multiply(cos(angle) * ORB_HOLD_RADIUS)
                            .add(basisV.clone().multiply(sin(angle) * ORB_HOLD_RADIUS))
                    )
                }

                holdPositions.drop(launchedCount).forEach { pos ->
                    val world = pos.world ?: return@forEach
                    world.spawnParticle(
                        Particle.DUST_COLOR_TRANSITION,
                        pos,
                        2,
                        0.1 * ORB_PARTICLE_RADIUS_MULTIPLIER,
                        0.1 * ORB_PARTICLE_RADIUS_MULTIPLIER,
                        0.1 * ORB_PARTICLE_RADIUS_MULTIPLIER,
                        0.0,
                        Particle.DustTransition(ORB_COLOR, ORB_OUTER_COLOR, ORB_HOLD_DUST_SIZE)
                    )
                    world.spawnParticle(Particle.BUBBLE, pos, 2, 0.12, 0.12, 0.12, 0.0)
                    world.spawnParticle(Particle.FIREWORK, pos, 2, 0.12, 0.12, 0.12, 0.0)
                }

                if (ticks >= ORB_HOLD_TICKS && launchedCount < orbCount) {
                    val afterHoldTicks = ticks - ORB_HOLD_TICKS
                    if (afterHoldTicks % ORB_STAGGER_TICKS == 0L) {
                        val pos = holdPositions[launchedCount]
                        val angleOffset = (launchedCount.toDouble() / orbCount.toDouble()) * Math.PI * 2.0
                        launchRotatingOrb(plugin, entity, target, pos, angleOffset, farRangeOrbDamage * DAMAGE_MULTIPLIER)
                        if (target is Player) {
                            target.playSound(target.location, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.75f, 1.0f)
                        } else {
                            pos.world?.playSound(pos, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.75f, 1.0f)
                        }
                        launchedCount += 1
                    }
                }

                ticks += 1
                if (launchedCount >= orbCount) {
                    cancel()
                }
            }
        }.runTaskTimer(plugin, 0L, 1L)
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
        val stepDistance = (orbSpeed / 20.0) * ORB_SPEED_MULTIPLIER
        var tickCount = 0L
        var ageTicks = 0L
        var homingTicksRemaining = ORB_HOMING_TICKS
        var flightDirection = target.location.clone().add(0.0, 1.0, 0.0).toVector().subtract(currentPos.toVector()).normalize()

        object : BukkitRunnable() {
            override fun run() {
                if (!entity.isValid || entity.isDead || !target.isValid || target.isDead) {
                    cancel()
                    return
                }

                ageTicks += 1
                if (ageTicks >= ORB_MAX_LIFETIME_TICKS) {
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

                if (distToTarget < ORB_HIT_RADIUS) {
                    target.damage(damage, entity)
                    world.spawnParticle(Particle.SPLASH, currentPos, 8, 0.2, 0.2, 0.2, 0.05)
                    world.spawnParticle(Particle.FIREWORK, currentPos.clone(), 8, 0.2, 0.2, 0.2, 0.0)
                    if (target is Player) {
                        target.playSound(target.location, Sound.ENTITY_PLAYER_SPLASH, 0.5f, 1.2f)
                    } else {
                        world.playSound(currentPos, Sound.ENTITY_PLAYER_SPLASH, 0.5f, 1.2f)
                    }
                    cancel()
                    return
                }

                if (homingTicksRemaining > 0 && toTarget.lengthSquared() > 1.0e-6) {
                    val desiredDir = toTarget.normalize()
                    flightDirection = flightDirection.multiply(1.0 - ORB_HOMING_STEER_FACTOR)
                        .add(desiredDir.multiply(ORB_HOMING_STEER_FACTOR))
                        .normalize()
                    homingTicksRemaining -= 1
                }

                val moveDir = flightDirection.clone().multiply(stepDistance)
                val displacement = Vector(
                    moveDir.x + orbitOffsetX * 0.1,
                    moveDir.y + orbitOffsetY * 0.05,
                    moveDir.z + orbitOffsetZ * 0.1
                )

                val displacementLength = displacement.length()
                if (displacementLength > 1.0e-6) {
                    val blockHit = world.rayTraceBlocks(
                        currentPos,
                        displacement.clone().normalize(),
                        displacementLength,
                        FluidCollisionMode.NEVER,
                        true
                    )
                    if (blockHit != null) {
                        cancel()
                        return
                    }
                }

                currentPos.add(displacement)

                world.spawnParticle(
                    Particle.DUST_COLOR_TRANSITION,
                    currentPos.clone(),
                    2,
                    0.1 * ORB_PARTICLE_RADIUS_MULTIPLIER,
                    0.1 * ORB_PARTICLE_RADIUS_MULTIPLIER,
                    0.1 * ORB_PARTICLE_RADIUS_MULTIPLIER,
                    0.0,
                    Particle.DustTransition(ORB_COLOR, ORB_OUTER_COLOR, ORB_TRAIL_DUST_SIZE)
                )
                world.spawnParticle(Particle.BUBBLE, currentPos.clone(), 1, 0.05, 0.05, 0.05, 0.0)
                world.spawnParticle(Particle.FIREWORK, currentPos.clone(), 1, 0.05, 0.05, 0.05, 0.0)
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
        private const val STATIONARY_SPEED_THRESHOLD = 0.02
        private const val STATIONARY_ENTER_TICKS = 3
        private const val STATIONARY_EXIT_TICKS = 3
        private const val STATIONARY_HOVER_AMPLITUDE = 0.45
        private const val STATIONARY_HOVER_ANGULAR_SPEED = 0.2
        private const val ORB_HOLD_RADIUS = 2.7
        private const val ORB_HOLD_TICKS = 40L
        private const val ORB_STAGGER_TICKS = 5L
        private val ORB_HOLD_ROTATION_SPEED = Math.toRadians(10.0) / 20.0
        private const val ORB_HOLD_DUST_SIZE = 1.6f
        private const val ORB_SPEED_MULTIPLIER = 0.5
        private const val ORB_HOMING_TICKS = 40
        private const val ORB_HOMING_STEER_FACTOR = 0.2
        private const val ORB_MAX_LIFETIME_TICKS = 100L
        private const val ORB_HIT_RADIUS = 1.44
        private const val ORB_PARTICLE_RADIUS_MULTIPLIER = 1.2
        private const val ORB_TRAIL_DUST_SIZE = 0.72f
        private val ORB_COLOR = Color.fromRGB(30, 90, 200)
        private val ORB_OUTER_COLOR = Color.fromRGB(255, 255, 255)
        private const val WATER_COLUMN_INITIAL_SPEED = 0.6
        private const val WATER_COLUMN_DRAG = 0.86
        private const val WATER_COLUMN_STOP_SPEED = 0.01
        private const val WATER_COLUMN_HIT_RADIUS = 0.8
        private const val WATER_COLUMN_LAUNCH_Y = 1.05
        private const val DAMAGE_MULTIPLIER = 2.5
        private const val ENHANCED_AMBIENT_ORBIT_RADIUS = 2.0
        private val AMBIENT_ORBIT_ANGULAR_SPEED_PER_TICK = Math.toRadians(45.0) / 20.0
    }
}
