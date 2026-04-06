package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobRuntimeContext
import org.bukkit.FluidCollisionMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.EntityType
import org.bukkit.entity.Display.Billboard
import org.bukkit.entity.Guardian
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.UUID
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class GuardianSpineShotAbility(
    override val id: String,
    private val cooldownTicks: Long = 60L,
    private val triggerMinDistance: Double = 1.5,
    private val triggerMaxDistance: Double = 6.0,
    private val spikeCount: Int = 8,
    private val spikeSpeedBlocksPerSecond: Double = 24.0,
    private val hitRadius: Double = 0.55,
    private val damageMultiplier: Double = 0.9,
    private val spikeScale: Float = 1.0f,
    private val gravityPerTick: Double = 0.034,
    private val projectileLifetimeTicks: Long = 200L,
    private val spreadAngleDegrees: Double = 15.0,
    private val backstepHorizontalSpeed: Double = 0.82,
    private val backstepVerticalSpeed: Double = 0.26
) : MobAbility {

    data class Runtime(
        var cooldownTicks: Long = 0L,
        var searchPhaseOffsetSteps: Int = 0
    ) : MobAbilityRuntime

    override fun createRuntime(context: jp.awabi2048.cccontent.mob.MobSpawnContext): MobAbilityRuntime {
        return Runtime(searchPhaseOffsetSteps = Random.nextInt(0, SEARCH_PHASE_VARIANTS))
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val rt = runtime as? Runtime ?: return
        if (rt.cooldownTicks > 0L) {
            rt.cooldownTicks = (rt.cooldownTicks - context.tickDelta).coerceAtLeast(0L)
        }

        val guardian = context.entity as? Guardian ?: return
        if (!context.isCombatActive()) return
        if (rt.cooldownTicks > 0L) return

        if (!MobAbilityUtils.shouldProcessSearchTick(context.activeMob.tickCount, context.loadSnapshot.searchIntervalMultiplier, rt.searchPhaseOffsetSteps)) {
            return
        }
        if (Random.nextDouble() < context.loadSnapshot.abilityExecutionSkipChance) {
            return
        }

        val target = MobAbilityUtils.resolveTarget(guardian) as? Player ?: return
        if (!target.isValid || target.isDead || target.world.uid != guardian.world.uid) {
            return
        }
        guardian.target = target
        val distance = guardian.location.distance(target.location)
        if (distance < triggerMinDistance || distance > triggerMaxDistance) {
            return
        }

        fireSpineVolley(context, guardian, target)
        rt.cooldownTicks = (cooldownTicks * context.loadSnapshot.abilityCooldownMultiplier).roundToLong().coerceAtLeast(cooldownTicks)
    }

    private fun fireSpineVolley(context: MobRuntimeContext, guardian: Guardian, target: Player) {
        val world = guardian.world
        val count = spikeCount.coerceAtLeast(1)
        val sharedHitSet = mutableSetOf<UUID>()
        val baseDamage = (guardian.getAttribute(Attribute.ATTACK_DAMAGE)?.value ?: context.definition.attack)
            .coerceAtLeast(0.0) * damageMultiplier
        val baseDirection = target.eyeLocation.toVector().subtract(guardian.eyeLocation.toVector()).normalize()
        if (baseDirection.lengthSquared() < 1.0e-6) {
            return
        }

        world.playSound(guardian.location, Sound.ENTITY_GUARDIAN_DEATH_LAND, 0.9f, 0.75f)

        repeat(count) {
            val direction = randomDirectionInCone(baseDirection, spreadAngleDegrees)
            val delayTicks = Random.nextLong(0L, 4L)
            object : BukkitRunnable() {
                override fun run() {
                    if (!guardian.isValid || guardian.isDead) {
                        return
                    }
                    launchSingleSpine(
                        plugin = context.plugin,
                        guardian = guardian,
                        direction = direction,
                        damage = baseDamage,
                        hitPlayers = sharedHitSet
                    )
                }
            }.runTaskLater(context.plugin, delayTicks)
        }

        applyBackstepMovement(guardian, target)
    }

    private fun applyBackstepMovement(guardian: Guardian, target: Player) {
        val away = guardian.location.toVector().subtract(target.location.toVector()).setY(0.0)
        if (away.lengthSquared() < 1.0e-6) {
            return
        }

        val horizontal = away.normalize().multiply(backstepHorizontalSpeed)
        val vertical = guardian.velocity.y.coerceAtLeast(backstepVerticalSpeed)
        guardian.velocity = Vector(horizontal.x, vertical, horizontal.z)
    }

    private fun launchSingleSpine(
        plugin: org.bukkit.plugin.java.JavaPlugin,
        guardian: Guardian,
        direction: Vector,
        damage: Double,
        hitPlayers: MutableSet<UUID>
    ) {
        val world = guardian.world
        val start = guardian.location.clone().add(0.0, guardian.height * 0.5, 0.0)
        val display = world.spawnEntity(start, EntityType.ITEM_DISPLAY) as ItemDisplay
        display.setItemStack(ItemStack(Material.LIGHTNING_ROD))
        display.billboard = Billboard.FIXED

        var current = start.toVector()
        var velocity = direction.clone().normalize().multiply((spikeSpeedBlocksPerSecond / 20.0).coerceAtLeast(0.05))
        var livedTicks = 0L

        updateDisplayTransform(display, current, velocity)

        object : BukkitRunnable() {
            override fun run() {
                if (!guardian.isValid || guardian.isDead || !display.isValid) {
                    cleanup()
                    return
                }

                val previous = current.clone()
                val step = velocity.clone()
                val stepLength = step.length()
                if (stepLength < 0.0001) {
                    cleanup()
                    return
                }
                val next = current.clone().add(step)
                val ray = world.rayTraceBlocks(
                    Location(world, previous.x, previous.y, previous.z),
                    step.clone().normalize(),
                    stepLength,
                    FluidCollisionMode.NEVER,
                    true
                )
                if (ray != null) {
                    cleanup()
                    return
                }

                val hitPlayer = findHitPlayer(world, previous, next, guardian.uniqueId, hitPlayers)
                if (hitPlayer != null) {
                    hitPlayer.damage(damage, guardian)
                    hitPlayer.velocity = hitPlayer.velocity.add(step.clone().normalize().multiply(0.7).setY(0.16))
                    world.playSound(hitPlayer.location, Sound.ENTITY_PLAYER_HURT, 0.65f, 1.5f)
                    cleanup()
                    return
                }

                current = next
                livedTicks += 1L

                velocity = velocity.clone().add(Vector(0.0, -gravityPerTick, 0.0))
                updateDisplayTransform(display, current, velocity)

                if (livedTicks >= projectileLifetimeTicks) {
                    cleanup()
                }
            }

            private fun cleanup() {
                cancel()
                if (display.isValid) {
                    display.remove()
                }
            }
        }.runTaskTimer(plugin, 1L, 1L)
    }

    private fun findHitPlayer(
        world: org.bukkit.World,
        from: Vector,
        to: Vector,
        ownerId: UUID,
        hitPlayers: MutableSet<UUID>
    ): Player? {
        val center = Location(world, (from.x + to.x) * 0.5, (from.y + to.y) * 0.5, (from.z + to.z) * 0.5)
        val nearby = world.getNearbyEntities(center, hitRadius + 0.3, hitRadius + 0.3, hitRadius + 0.3)
        for (entity in nearby) {
            val player = entity as? Player ?: continue
            if (player.uniqueId == ownerId || !player.isValid || player.isDead) continue
            if (!intersectsSegment(player, from, to)) continue
            if (!hitPlayers.add(player.uniqueId)) continue
            return player
        }
        return null
    }

    private fun intersectsSegment(player: Player, from: Vector, to: Vector): Boolean {
        val eye = player.eyeLocation.toVector()
        val segment = to.clone().subtract(from)
        val segmentLengthSquared = segment.lengthSquared()
        if (segmentLengthSquared <= 1.0e-6) {
            return eye.distanceSquared(from) <= hitRadius * hitRadius
        }

        val t = eye.clone().subtract(from).dot(segment) / segmentLengthSquared
        val clamped = t.coerceIn(0.0, 1.0)
        val closest = from.clone().add(segment.multiply(clamped))
        val dx = eye.x - closest.x
        val dz = eye.z - closest.z
        val horizontalSquared = dx * dx + dz * dz
        val vertical = kotlin.math.abs(eye.y - closest.y)
        return horizontalSquared <= hitRadius * hitRadius && vertical <= hitRadius
    }

    private fun updateDisplayTransform(display: ItemDisplay, position: Vector, direction: Vector) {
        val world = display.world
        display.teleport(Location(world, position.x, position.y, position.z))

        if (direction.lengthSquared() < 1.0e-6) {
            return
        }

        val dir = direction.clone().normalize()
        val rotation = Quaternionf().rotateTo(
            Vector3f(0f, 1f, 0f),
            Vector3f(dir.x.toFloat(), dir.y.toFloat(), dir.z.toFloat())
        )
        display.transformation = Transformation(
            Vector3f(0f, 0f, 0f),
            rotation,
            Vector3f(spikeScale * 0.5f, spikeScale * 1.5f, spikeScale * 0.5f),
            Quaternionf()
        )
    }

    private fun randomDirectionInCone(baseDirection: Vector, angleDegrees: Double): Vector {
        val axis = baseDirection.clone().normalize()
        val angleRadians = Math.toRadians(angleDegrees.coerceAtLeast(0.0))
        val cosMax = cos(angleRadians)
        val cosTheta = Random.nextDouble(cosMax, 1.0)
        val sinTheta = sqrt((1.0 - cosTheta * cosTheta).coerceAtLeast(0.0))
        val phi = Random.nextDouble(0.0, Math.PI * 2.0)

        val reference = if (abs(axis.y) < 0.999) Vector(0.0, 1.0, 0.0) else Vector(1.0, 0.0, 0.0)
        val tangent = axis.clone().crossProduct(reference).normalize()
        val bitangent = tangent.clone().crossProduct(axis).normalize()

        return axis.clone().multiply(cosTheta)
            .add(tangent.multiply(cos(phi) * sinTheta))
            .add(bitangent.multiply(sin(phi) * sinTheta))
            .normalize()
    }

    private companion object {
        const val SEARCH_PHASE_VARIANTS = 16
    }
}
