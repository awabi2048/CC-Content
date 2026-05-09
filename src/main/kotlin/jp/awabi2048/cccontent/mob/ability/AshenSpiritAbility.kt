@file:Suppress("UNNECESSARY_SAFE_CALL", "USELESS_ELVIS")

package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobDamagedContext
import jp.awabi2048.cccontent.mob.MobRuntimeContext
import org.bukkit.Bukkit
import org.bukkit.FluidCollisionMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import org.bukkit.entity.Player
import org.bukkit.entity.Vex
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityCombustEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Vector
import java.util.UUID
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class AshenSpiritAbility(
    override val id: String,
    private val approachDistance: Double = DEFAULT_APPROACH_DISTANCE,
    private val closeRangeDamage: Double = DEFAULT_CLOSE_RANGE_DAMAGE,
    private val farRangeDashDamage: Double = DEFAULT_FAR_RANGE_DASH_DAMAGE,
    private val sharedCooldownTicks: Long = DEFAULT_SHARED_COOLDOWN_TICKS
) : MobAbility {

    data class AshField(
        val center: Location,
        var remainingTicks: Long,
        var pulseCooldownTicks: Long = 0L
    )

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
        var ambientParticleTicks: Long = 0L,
        var ambientSoundTicks: Long = 0L,
        var orbitState: OrbitFollowState = OrbitFollowState(ANGULAR_SPEED),
        var searchPhaseOffsetSteps: Int = 0,
        var syncTask: BukkitTask? = null,
        var damageListener: Listener? = null,
        var combustListener: Listener? = null,
        var interactListener: Listener? = null,
        var plugin: JavaPlugin? = null,
        var dashTicksRemaining: Long = 0L,
        var dashDirection: Vector = Vector(0.0, 0.0, 0.0),
        var dashHitPlayers: MutableSet<UUID> = mutableSetOf(),
        var ashFields: MutableList<AshField> = mutableListOf()
    ) : MobAbilityRuntime

    override fun createRuntime(context: jp.awabi2048.cccontent.mob.MobSpawnContext): MobAbilityRuntime {
        return Runtime(
            sharedCooldownTicks = sharedCooldownTicks,
            searchPhaseOffsetSteps = Random.nextInt(0, SEARCH_PHASE_VARIANTS)
        )
    }

    override fun onSpawn(context: jp.awabi2048.cccontent.mob.MobSpawnContext, runtime: MobAbilityRuntime?) {
        val vex = context.entity as? Vex ?: return
        Bukkit.getMobGoals().removeAllGoals(vex)
        vex.isSilent = true
        vex.setGravity(false)

        val rt = runtime as? Runtime ?: return
        rt.plugin = context.plugin

        vex.world?.playSound(vex.location, Sound.ENTITY_WITHER_SHOOT, 0.6f, 0.9f)

        val damageListener = object : Listener {
            @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
            fun onDamage(event: EntityDamageEvent) {
                if (event.entity.uniqueId == vex.uniqueId) {
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
                if (event.entity.uniqueId == vex.uniqueId) {
                    event.isCancelled = true
                }
            }
        }
        Bukkit.getPluginManager().registerEvents(combustListener, context.plugin)
        rt.combustListener = combustListener

        val interactListener = object : Listener {
            @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
            fun onInteract(event: PlayerInteractEntityEvent) {
                if (event.rightClicked.uniqueId == vex.uniqueId) {
                    event.isCancelled = true
                }
            }
        }
        Bukkit.getPluginManager().registerEvents(interactListener, context.plugin)
        rt.interactListener = interactListener

        rt.syncTask = Bukkit.getScheduler().runTaskTimer(context.plugin, Runnable {
            if (!vex.isValid || vex.isDead) {
                rt.syncTask?.cancel()
                rt.syncTask = null
                return@Runnable
            }

            vex.fireTicks = 0
            vex.velocity = Vector(0, 0, 0)
            tickAshFields(vex, rt)

            if (rt.dashTicksRemaining > 0L) {
                processDashTick(vex, rt)
                return@Runnable
            }

            if (rt.frozenTicks > 0L) {
                rt.frozenTicks -= 1L
                val targetPlayer = findNearestPlayer(vex)
                if (targetPlayer != null && targetPlayer.isValid && !targetPlayer.isDead) {
                    faceEntity(vex, targetPlayer)
                }
                return@Runnable
            }

            val targetPlayer = findNearestPlayer(vex)
            if (targetPlayer == null || !targetPlayer.isValid || targetPlayer.isDead) {
                OrbitFollowController.clearTarget(rt.orbitState)
                return@Runnable
            }

            val orbitOutput = OrbitFollowController.update(
                state = rt.orbitState,
                config = orbitFollowConfig,
                targetUuid = targetPlayer.uniqueId,
                followerX = vex.location.x,
                followerZ = vex.location.z,
                targetX = targetPlayer.location.x,
                targetY = targetPlayer.location.y,
                targetZ = targetPlayer.location.z
            )

            val newLoc = Location(vex.world, orbitOutput.x, orbitOutput.y, orbitOutput.z, vex.location.yaw, vex.location.pitch)
            vex.teleport(newLoc)
            faceEntity(vex, targetPlayer)
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
        if (rt.dashTicksRemaining > 0L) return

        val target = findNearestPlayer(entity) ?: return
        if (!target.isValid || target.isDead) return

        val distance = entity.location.distance(target.location)
        if (rt.sharedCooldownTicks > 0L) {
            return
        }

        if (distance <= approachDistance) {
            deployAshField(entity, rt)
            rt.sharedCooldownTicks = sharedCooldownTicks
            return
        }

        if (!entity.hasLineOfSight(target)) {
            return
        }

        startDash(entity, target, rt)
        rt.sharedCooldownTicks =
            (sharedCooldownTicks * context.loadSnapshot.abilityCooldownMultiplier).roundToLong().coerceAtLeast(sharedCooldownTicks)
    }

    override fun onDamaged(context: MobDamagedContext, runtime: MobAbilityRuntime?) {
        val entity = context.entity
        val world = entity.world ?: return
        world.playSound(entity.location, Sound.ENTITY_BLAZE_HURT, 0.75f, 0.7f)
    }

    override fun onDeath(context: jp.awabi2048.cccontent.mob.MobDeathContext, runtime: MobAbilityRuntime?) {
        val rt = runtime as? Runtime ?: return
        val entity = context.entity
        entity.world?.playSound(entity.location, Sound.ENTITY_BLAZE_DEATH, 1.0f, 0.65f)
        rt.syncTask?.cancel()
        rt.syncTask = null
        rt.damageListener?.let { HandlerList.unregisterAll(it) }
        rt.damageListener = null
        rt.combustListener?.let { HandlerList.unregisterAll(it) }
        rt.combustListener = null
        rt.interactListener?.let { HandlerList.unregisterAll(it) }
        rt.interactListener = null
        rt.ashFields.clear()
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
        val loc = entity.location.clone().add(0.0, 0.8, 0.0)
        val world = loc.world ?: return
        for (i in 0 until 16) {
            val angle = Random.nextDouble(0.0, Math.PI * 2.0)
            val radius = 1.4 + Random.nextDouble(0.0, 0.9)
            val x = cos(angle) * radius
            val z = sin(angle) * radius
            val y = Random.nextDouble(-0.35, 0.35)
            world.spawnParticle(Particle.SMOKE, loc.clone().add(x, y, z), 1, 0.0, 0.0, 0.0, 0.0)
        }
        world.spawnParticle(Particle.FLAME, loc, 3, 0.6, 0.25, 0.6, 0.01)
    }

    private fun playAmbientSound(entity: LivingEntity, rt: Runtime) {
        if (rt.ambientSoundTicks < 60L) return
        rt.ambientSoundTicks = 0L
        val world = entity.world ?: return
        world.playSound(entity.location, Sound.ENTITY_VEX_AMBIENT, 0.65f, 0.65f)
    }

    private fun deployAshField(entity: LivingEntity, runtime: Runtime) {
        val world = entity.world ?: return
        val center = entity.location.clone().add(0.0, 0.1, 0.0)
        world.playSound(center, Sound.ENTITY_BLAZE_SHOOT, 0.9f, 0.6f)
        world.playSound(center, Sound.ENTITY_GENERIC_EXTINGUISH_FIRE, 0.8f, 0.7f)
        world.spawnParticle(Particle.ASH, center, 110, 2.1, 0.7, 2.1, 0.02)
        world.spawnParticle(Particle.SMOKE, center, 95, 2.0, 0.5, 2.0, 0.02)
        runtime.ashFields.add(AshField(center = center, remainingTicks = ASH_FIELD_DURATION_TICKS))
    }

    private fun tickAshFields(entity: LivingEntity, runtime: Runtime) {
        if (runtime.ashFields.isEmpty()) return
        val world = entity.world ?: return

        val iterator = runtime.ashFields.iterator()
        while (iterator.hasNext()) {
            val field = iterator.next()
            if (field.remainingTicks <= 0L) {
                iterator.remove()
                continue
            }

            val center = field.center
            if (center.world?.uid != world.uid) {
                iterator.remove()
                continue
            }

            world.spawnParticle(Particle.ASH, center, 22, ASH_FIELD_RADIUS, 0.18, ASH_FIELD_RADIUS, 0.01)
            world.spawnParticle(Particle.SMOKE, center, 18, ASH_FIELD_RADIUS * 0.9, 0.16, ASH_FIELD_RADIUS * 0.9, 0.01)

            field.pulseCooldownTicks = (field.pulseCooldownTicks - 1L).coerceAtLeast(0L)
            if (field.pulseCooldownTicks == 0L) {
                applyAshFieldEffects(entity, center)
                field.pulseCooldownTicks = ASH_FIELD_PULSE_INTERVAL_TICKS
            }

            field.remainingTicks -= 1L
        }
    }

    private fun applyAshFieldEffects(owner: LivingEntity, center: Location) {
        val world = center.world ?: return
        world.getNearbyEntities(center, ASH_FIELD_RADIUS, ASH_FIELD_RADIUS, ASH_FIELD_RADIUS)
            .asSequence()
            .filterIsInstance<LivingEntity>()
            .filter { target ->
                target.uniqueId != owner.uniqueId &&
                    target.isValid &&
                    !target.isDead &&
                    (target is Player || target is Mob)
            }
            .forEach { target ->
                target.damage(closeRangeDamage * ASH_FIELD_DAMAGE_MULTIPLIER, owner)
                target.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, ASH_FIELD_BLINDNESS_TICKS, 0, false, true, true))
            }
    }

    private fun startDash(entity: LivingEntity, target: LivingEntity, rt: Runtime) {
        val source = entity.location.clone().add(0.0, 0.7, 0.0)
        val predictedTarget = target.location.clone().add(target.velocity.clone().multiply(0.5)).add(0.0, 0.9, 0.0)
        val direction = predictedTarget.toVector().subtract(source.toVector())
        if (direction.lengthSquared() < 1.0e-6) {
            return
        }

        rt.dashDirection = direction.normalize()
        rt.dashTicksRemaining = DASH_MAX_TICKS
        rt.dashHitPlayers.clear()
        entity.world?.playSound(entity.location, Sound.ENTITY_WITHER_SHOOT, 1.0f, 0.55f)
        entity.world?.playSound(entity.location, Sound.ITEM_FIRECHARGE_USE, 1.0f, 0.8f)
    }

    private fun processDashTick(entity: LivingEntity, rt: Runtime) {
        val world = entity.world ?: return
        val bodyCenterOffset = entity.height * 0.5
        val current = entity.location.clone().add(0.0, bodyCenterOffset, 0.0)
        val step = rt.dashDirection.clone().multiply(DASH_SPEED_PER_TICK)
        val stepLength = step.length()

        if (stepLength > 1.0e-6) {
            val blockHit = world.rayTraceBlocks(
                current,
                step.clone().normalize(),
                stepLength,
                FluidCollisionMode.NEVER,
                true
            )
            if (blockHit != null) {
                endDash(entity, rt)
                return
            }
        }

        val next = current.clone().add(step)
        val teleported = entity.location.clone().apply {
            x = next.x
            y = next.y - bodyCenterOffset
            z = next.z
        }
        entity.teleport(teleported)

        spawnDashCylinder(world, next, rt.dashDirection)
        igniteGround(world, next)
        applyDashHit(entity, rt, next)

        rt.dashTicksRemaining -= 1L
        if (rt.dashTicksRemaining <= 0L) {
            endDash(entity, rt)
        }
    }

    private fun endDash(entity: LivingEntity, rt: Runtime) {
        rt.dashTicksRemaining = 0L
        rt.frozenTicks = DASH_RECOVERY_TICKS
        rt.dashHitPlayers.clear()
        val world = entity.world ?: return
        val loc = entity.location.clone().add(0.0, 0.6, 0.0)
        world.spawnParticle(Particle.SMOKE, loc, 28, 0.8, 0.3, 0.8, 0.02)
        world.spawnParticle(Particle.FLAME, loc, 14, 0.6, 0.25, 0.6, 0.01)
        val targetPlayer = findNearestPlayer(entity) ?: return
        OrbitFollowController.reanchorFromCurrentPosition(
            state = rt.orbitState,
            config = orbitFollowConfig,
            targetUuid = targetPlayer.uniqueId,
            followerX = entity.location.x,
            followerZ = entity.location.z,
            targetX = targetPlayer.location.x,
            targetZ = targetPlayer.location.z
        )
    }

    private fun applyDashHit(entity: LivingEntity, rt: Runtime, center: Location) {
        val world = center.world ?: return
        world.getNearbyEntities(center, DASH_HIT_RADIUS, DASH_HIT_RADIUS, DASH_HIT_RADIUS)
            .asSequence()
            .filterIsInstance<Player>()
            .filter { it.isValid && !it.isDead }
            .forEach { player ->
                if (!rt.dashHitPlayers.add(player.uniqueId)) {
                    return@forEach
                }
                player.damage(farRangeDashDamage * DAMAGE_MULTIPLIER, entity)
                val pushDir = rt.dashDirection.clone().setY(0.0)
                val horizontal = if (pushDir.lengthSquared() > 1.0e-6) {
                    pushDir.normalize().multiply(DASH_HIT_KNOCKBACK)
                } else {
                    Vector(0.0, 0.0, 0.0)
                }
                player.velocity = player.velocity.add(horizontal).setY(max(player.velocity.y, DASH_HIT_VERTICAL))
                player.fireTicks = max(player.fireTicks, DASH_HIT_FIRE_TICKS)
                world.playSound(player.location, Sound.ENTITY_BLAZE_HURT, 0.9f, 0.7f)
            }
    }

    private fun igniteGround(world: org.bukkit.World, around: Location) {
        val x = around.blockX
        val z = around.blockZ
        val baseY = around.blockY
        for (y in baseY downTo (baseY - 3)) {
            val floor = world.getBlockAt(x, y, z)
            val above = world.getBlockAt(x, y + 1, z)
            if (floor.type.isSolid && above.isPassable && above.type != Material.FIRE) {
                above.type = Material.FIRE
                break
            }
        }
    }

    private fun spawnDashCylinder(world: org.bukkit.World, center: Location, direction: Vector) {
        val forward = direction.clone().normalize()
        if (forward.lengthSquared() < 1.0e-6) {
            return
        }
        val (u, v) = buildPerpendicularBasis(forward)
        val offsets = listOf(-0.35, 0.0, 0.35)
        offsets.forEach { axial ->
            val axialOffset = forward.clone().multiply(axial)
            repeat(CYLINDER_POINTS) { index ->
                val angle = (index.toDouble() / CYLINDER_POINTS.toDouble()) * Math.PI * 2.0
                val radial = u.clone().multiply(cos(angle) * CYLINDER_RADIUS)
                    .add(v.clone().multiply(sin(angle) * CYLINDER_RADIUS))
                val pos = center.clone().add(axialOffset).add(radial)
                world.spawnParticle(Particle.FLAME, pos, 1, 0.0, 0.0, 0.0, 0.0)
                world.spawnParticle(Particle.SMOKE, pos, 1, 0.0, 0.0, 0.0, 0.0)
            }
        }
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

    private fun faceEntity(entity: LivingEntity, target: LivingEntity) {
        val from = entity.location.clone().add(0.0, 0.7, 0.0)
        val to = target.location.clone().add(0.0, 1.0, 0.0)
        val dir = to.toVector().subtract(from.toVector())
        val yaw = Math.toDegrees(atan2(-dir.x, dir.z)).toFloat()
        val pitch = (-Math.toDegrees(atan2(dir.y, sqrt(dir.x * dir.x + dir.z * dir.z)))).toFloat()
        val newLoc = entity.location.clone()
        newLoc.yaw = yaw
        newLoc.pitch = pitch
        entity.teleport(newLoc)
    }

    companion object {
        const val DEFAULT_APPROACH_DISTANCE = 5.0
        const val DEFAULT_CLOSE_RANGE_DAMAGE = 5.0
        const val DEFAULT_FAR_RANGE_DASH_DAMAGE = 3.5
        const val DEFAULT_SHARED_COOLDOWN_TICKS = 60L

        private const val SEARCH_PHASE_VARIANTS = 16
        private const val SEARCH_RANGE = 40.0
        private const val ORBIT_RADIUS = 6.0
        private const val ANGULAR_SPEED = 0.019634954084936207
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

        private const val ASH_FIELD_RADIUS = 4.0
        private const val ASH_FIELD_DURATION_TICKS = 100L
        private const val ASH_FIELD_PULSE_INTERVAL_TICKS = 10L
        private const val ASH_FIELD_BLINDNESS_TICKS = 40
        private const val ASH_FIELD_DAMAGE_MULTIPLIER = 0.32

        private const val DASH_MAX_TICKS = 60L
        private const val DASH_SPEED_PER_TICK = 0.8
        private const val DASH_HIT_RADIUS = 1.2
        private const val DASH_HIT_KNOCKBACK = 1.95
        private const val DASH_HIT_VERTICAL = 0.42
        private const val DASH_HIT_FIRE_TICKS = 100
        private const val DASH_RECOVERY_TICKS = 10L

        private const val CYLINDER_POINTS = 12
        private const val CYLINDER_RADIUS = 1.5

        private const val DAMAGE_MULTIPLIER = 2.5
    }
}
