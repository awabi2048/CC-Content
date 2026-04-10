package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.ability.MobAbility
import jp.awabi2048.cccontent.mob.ability.MobAbilityRuntime
import jp.awabi2048.cccontent.mob.MobDamagedContext
import jp.awabi2048.cccontent.mob.MobDeathContext
import jp.awabi2048.cccontent.mob.MobRuntimeContext
import jp.awabi2048.cccontent.mob.MobSpawnContext
import org.bukkit.Bukkit
import org.bukkit.FluidCollisionMode
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Display
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.entity.Wither
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class EndWitherSentinelAbility(
    override val id: String,
    private val orbitRadius: Double = 5.3333333333,
    private val orbitHeightOffset: Double = 1.5,
    private val attackCooldownTicks: Long = 55L
) : MobAbility {

    override fun tickIntervalTicks(): Long = 1L

    data class SkullProjectile(
        var position: Location,
        var velocity: Vector,
        val targetId: UUID,
        var remainingTicks: Int,
        val displayId: UUID
    )

    data class Runtime(
        var attackCooldown: Long = 0L,
        var ambientTicks: Long = 0L,
        var soundTicks: Long = 0L,
        var ambientOrbitTicks: Long = 0L,
        var orbitState: OrbitFollowState = OrbitFollowState(0.03),
        var bodyDisplayId: UUID? = null,
        val projectiles: MutableList<SkullProjectile> = mutableListOf()
    ) : MobAbilityRuntime

    private val orbitConfig = OrbitFollowConfig(
        orbitRadius = orbitRadius,
        orbitHeightOffset = orbitHeightOffset,
        targetPositionDelayTicks = 12,
        directionChangeIntervalTicks = 30,
        directionTransitionTicks = 12,
        baseAngularSpeed = 0.03,
        yFollowLerpFactor = 0.12,
        stationarySpeedThreshold = 0.02,
        stationaryEnterTicks = 3,
        stationaryExitTicks = 3,
        stationaryHoverAmplitude = 0.35,
        stationaryHoverAngularSpeed = 0.16
    )

    override fun createRuntime(context: MobSpawnContext): MobAbilityRuntime = Runtime()

    override fun onSpawn(context: MobSpawnContext, runtime: MobAbilityRuntime?) {
        val wither = context.entity as? Wither ?: return
        val rt = runtime as? Runtime ?: return

        Bukkit.getMobGoals().removeAllGoals(wither)
        wither.setAI(false)
        wither.setGravity(false)
        wither.isSilent = true
        hideBossBar(wither)

        rt.bodyDisplayId = spawnBodyDisplay(wither).uniqueId

        wither.world.playSound(wither.location, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.9f, 0.6f)
        wither.world.playSound(wither.location, Sound.ENTITY_WITHER_AMBIENT, 0.55f, 0.75f)
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val wither = context.entity as? Wither ?: return
        val rt = runtime as? Runtime ?: return
        if (!wither.isValid || wither.isDead) return
        hideBossBar(wither)

        val target = findNearestPlayer(wither)
        if (target != null) {
            val orbit = OrbitFollowController.update(
                state = rt.orbitState,
                config = orbitConfig,
                targetUuid = target.uniqueId,
                followerX = wither.location.x,
                followerZ = wither.location.z,
                targetX = target.location.x,
                targetY = target.location.y,
                targetZ = target.location.z
            )

            val facing = calculateFacing(orbit.x, orbit.y, orbit.z, target.eyeLocation)

            wither.teleport(Location(wither.world, orbit.x, orbit.y, orbit.z, facing.first, facing.second))
        }

        syncBodyDisplay(wither, rt)

        rt.attackCooldown = (rt.attackCooldown - context.tickDelta).coerceAtLeast(0L)
        if (target != null && rt.attackCooldown <= 0L && wither.hasLineOfSight(target)) {
            launchSkull(wither, rt, target)
            rt.attackCooldown = attackCooldownTicks
        }

        rt.soundTicks += context.tickDelta
        rt.ambientOrbitTicks += context.tickDelta
        spawnAmbientSatellites(wither, rt)
        if (rt.soundTicks >= 60L) {
            rt.soundTicks = 0L
            wither.world.playSound(wither.location, Sound.ENTITY_WITHER_AMBIENT, 0.5f, 1.6f)
            wither.world.playSound(wither.location, Sound.BLOCK_BEACON_AMBIENT, 0.35f, 0.7f)
        }

        tickProjectiles(context, wither, rt)
    }

    override fun onDamaged(context: MobDamagedContext, runtime: MobAbilityRuntime?) {
        val wither = context.entity as? Wither ?: return
        wither.world.playSound(wither.location, Sound.ENTITY_WITHER_HURT, 0.55f, 1.1f)
    }

    override fun onDeath(context: MobDeathContext, runtime: MobAbilityRuntime?) {
        val wither = context.entity as? Wither ?: return
        val rt = runtime as? Runtime ?: return

        rt.bodyDisplayId?.let(::removeDisplay)
        rt.bodyDisplayId = null
        rt.projectiles.forEach { removeDisplay(it.displayId) }
        rt.projectiles.clear()

        wither.world.playSound(wither.location, Sound.ENTITY_WITHER_DEATH, 0.8f, 1.35f)
        wither.world.playSound(wither.location, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.8f, 0.55f)
    }

    private fun spawnBodyDisplay(wither: Wither): ItemDisplay {
        return wither.world.spawn(wither.location.clone().add(0.0, 1.5, 0.0), ItemDisplay::class.java).apply {
            setItemStack(ItemStack(Material.WITHER_SKELETON_SKULL))
            billboard = Display.Billboard.FIXED
            brightness = Display.Brightness(15, 15)
            transformation = Transformation(
                Vector3f(0f, 0f, 0f),
                Quaternionf(),
                Vector3f(0.8f, 0.8f, 0.8f),
                Quaternionf()
            )
        }
    }

    private fun syncBodyDisplay(wither: Wither, runtime: Runtime) {
        val display = runtime.bodyDisplayId?.let { Bukkit.getEntity(it) as? ItemDisplay }
        if (display == null || !display.isValid) {
            runtime.bodyDisplayId = spawnBodyDisplay(wither).uniqueId
            return
        }
        display.teleport(wither.location.clone().add(0.0, 1.5, 0.0))
    }

    private fun calculateFacing(x: Double, y: Double, z: Double, target: Location): Pair<Float, Float> {
        val dx = target.x - x
        val dy = target.y - y
        val dz = target.z - z
        val horizontal = sqrt(dx * dx + dz * dz).coerceAtLeast(1.0e-6)
        val yaw = Math.toDegrees(atan2(-dx, dz)).toFloat()
        val pitch = Math.toDegrees(-atan2(dy, horizontal)).toFloat()
        return yaw to pitch
    }

    private fun findNearestPlayer(wither: Wither): Player? {
        return wither.world.players
            .asSequence()
            .filter { it.isValid && !it.isDead && it.gameMode != GameMode.SPECTATOR }
            .filter { it.location.distanceSquared(wither.location) <= TARGET_SEARCH_RADIUS_SQUARED }
            .minByOrNull { it.location.distanceSquared(wither.location) }
    }

    private fun launchSkull(wither: Wither, runtime: Runtime, target: Player) {
        val origin = wither.location.clone().add(0.0, 1.4, 0.0)
        val direction = target.eyeLocation.toVector().subtract(origin.toVector())
            .takeIf { it.lengthSquared() > 1.0e-6 }
            ?.normalize()
            ?: Vector(0.0, 0.0, 1.0)
        val display = spawnProjectileDisplay(origin, direction)

        runtime.projectiles += SkullProjectile(
            position = origin,
            velocity = direction.multiply(PROJECTILE_SPEED_PER_TICK),
            targetId = target.uniqueId,
            remainingTicks = PROJECTILE_MAX_TICKS,
            displayId = display.uniqueId
        )

        wither.world.playSound(origin, Sound.ENTITY_WITHER_SHOOT, 0.8f, 1.15f)
        wither.world.playSound(origin, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.6f, 0.8f)
    }

    private fun spawnProjectileDisplay(location: Location, direction: Vector): ItemDisplay {
        return location.world.spawn(location, ItemDisplay::class.java).apply {
            setItemStack(ItemStack(Material.WITHER_SKELETON_SKULL))
            billboard = Display.Billboard.FIXED
            brightness = Display.Brightness(15, 15)
            transformation = createProjectileTransformation(direction)
        }
    }

    private fun createProjectileTransformation(direction: Vector): Transformation {
        val normalized = direction.clone().takeIf { it.lengthSquared() > 1.0e-6 }?.normalize() ?: Vector(0.0, 0.0, 1.0)
        val yaw = atan2(-normalized.x, normalized.z).toFloat()
        val pitch = (-atan2(normalized.y, sqrt(normalized.x * normalized.x + normalized.z * normalized.z))).toFloat()
        val rotation = Quaternionf().rotateY(yaw).rotateX(pitch)
        return Transformation(
            Vector3f(0f, 0f, 0f),
            rotation,
            Vector3f(0.55f, 0.55f, 0.55f),
            Quaternionf()
        )
    }

    private fun tickProjectiles(context: MobRuntimeContext, wither: Wither, runtime: Runtime) {
        val world = wither.world
        val iterator = runtime.projectiles.iterator()
        while (iterator.hasNext()) {
            val projectile = iterator.next()
            if (projectile.remainingTicks <= 0) {
                removeDisplay(projectile.displayId)
                iterator.remove()
                continue
            }

            val target = Bukkit.getPlayer(projectile.targetId)
                ?.takeIf { it.isValid && !it.isDead && it.gameMode != GameMode.SPECTATOR && it.world.uid == world.uid }
            if (target != null) {
                val desired = target.eyeLocation.toVector().subtract(projectile.position.toVector())
                if (desired.lengthSquared() > 1.0e-6) {
                    val steer = desired.normalize().multiply(PROJECTILE_SPEED_PER_TICK)
                    projectile.velocity = projectile.velocity.multiply(1.0 - HOMING_STEER).add(steer.multiply(HOMING_STEER))
                }
            }

            if (projectile.velocity.lengthSquared() > 1.0e-6) {
                projectile.velocity = projectile.velocity.normalize().multiply(PROJECTILE_SPEED_PER_TICK)
            }

            val from = projectile.position.clone()
            val velocity = projectile.velocity.clone()
            val distance = velocity.length()
            if (distance <= 1.0e-6) {
                removeDisplay(projectile.displayId)
                iterator.remove()
                continue
            }

            val direction = velocity.clone().normalize()
            val blockHit = world.rayTraceBlocks(from, direction, distance, FluidCollisionMode.NEVER, true)
            if (blockHit != null) {
                world.spawnParticle(Particle.SMOKE, from, 10, 0.15, 0.15, 0.15, 0.01)
                world.playSound(from, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.45f, 0.7f)
                removeDisplay(projectile.displayId)
                iterator.remove()
                continue
            }

            val hitPlayer = world.rayTraceEntities(from, direction, distance, PROJECTILE_HIT_RADIUS) {
                it is Player && it.isValid && !it.isDead && it.gameMode != GameMode.SPECTATOR
            }?.hitEntity as? Player

            if (hitPlayer != null) {
                val damage = (context.definition.attack * 0.9).coerceAtLeast(0.0)
                if (damage > 0.0) {
                    hitPlayer.damage(damage, wither)
                }
                hitPlayer.addPotionEffect(PotionEffect(PotionEffectType.WITHER, 50, 0, false, true, true))
                world.spawnParticle(Particle.EXPLOSION, hitPlayer.location.clone().add(0.0, 1.0, 0.0), 1, 0.0, 0.0, 0.0, 0.0)
                world.playSound(hitPlayer.location, Sound.ENTITY_WITHER_HURT, 0.6f, 1.5f)
                removeDisplay(projectile.displayId)
                iterator.remove()
                continue
            }

            projectile.position = from.add(velocity)
            projectile.remainingTicks -= 1

            val display = Bukkit.getEntity(projectile.displayId) as? ItemDisplay
            if (display != null && display.isValid) {
                display.teleport(projectile.position)
                val facingDirection = target?.eyeLocation?.toVector()?.subtract(projectile.position.toVector())
                    ?.takeIf { it.lengthSquared() > 1.0e-6 }
                    ?: projectile.velocity
                display.transformation = createProjectileTransformation(facingDirection)
            }

            world.spawnParticle(Particle.SMOKE, projectile.position, 2, 0.03, 0.03, 0.03, 0.0)
            world.spawnParticle(Particle.WITCH, projectile.position, 1, 0.01, 0.01, 0.01, 0.0)
        }
    }

    private fun spawnAmbientSatellites(wither: Wither, runtime: Runtime) {
        val center = wither.location.clone().add(0.0, 1.0, 0.0)
        val baseAngle = runtime.ambientOrbitTicks * AMBIENT_ORBIT_SPEED_PER_TICK
        repeat(4) { idx ->
            val angle = baseAngle + (Math.PI * 2.0 * idx / 4.0)
            val point = center.clone().add(cos(angle) * SATELLITE_RADIUS, sin(angle * 1.6) * 0.24, sin(angle) * SATELLITE_RADIUS)
            wither.world.spawnParticle(Particle.WITCH, point, 1, 0.02, 0.02, 0.02, 0.0)
            wither.world.spawnParticle(Particle.FALLING_OBSIDIAN_TEAR, point, 1, 0.01, 0.01, 0.01, 0.0)
        }
    }

    private fun hideBossBar(wither: Wither) {
        // Keep the NMS reflection local here so the rest of the mob system stays free from version-locked server internals.
        val methodNames = listOf("setShouldDrawBossBar", "setBossBarVisible", "setShowBossBar")
        methodNames.forEach { name ->
            runCatching {
                val method = wither.javaClass.methods.firstOrNull { it.name == name && it.parameterCount == 1 }
                method?.invoke(wither, false)
            }
        }

        runCatching {
            val handle = wither.javaClass.getMethod("getHandle").invoke(wither) ?: return@runCatching
            val bossEvent = findBossEvent(handle) ?: return@runCatching
            bossEvent.javaClass.methods.firstOrNull { it.name == "setVisible" && it.parameterCount == 1 }
                ?.invoke(bossEvent, false)
        }
    }

    private fun findBossEvent(instance: Any): Any? {
        var current: Class<*>? = instance.javaClass
        while (current != null && current != Any::class.java) {
            current.declaredFields.forEach { field ->
                runCatching {
                    field.isAccessible = true
                    val value = field.get(instance) ?: return@runCatching
                    if (value.javaClass.name.endsWith("ServerBossEvent")) {
                        return value
                    }
                }
            }
            current = current.superclass
        }
        return null
    }

    private fun removeDisplay(entityId: UUID) {
        val display = Bukkit.getEntity(entityId) as? ItemDisplay ?: return
        if (display.isValid) {
            display.remove()
        }
    }

    private companion object {
        const val TARGET_SEARCH_RADIUS_SQUARED = 40.0 * 40.0
        const val PROJECTILE_SPEED_PER_TICK = 0.62
        const val PROJECTILE_MAX_TICKS = 60
        const val PROJECTILE_HIT_RADIUS = 0.55
        const val HOMING_STEER = 0.15
        const val AMBIENT_ORBIT_SPEED_PER_TICK = 0.09
        const val SATELLITE_RADIUS = 0.7666666667
    }
}
