package jp.awabi2048.cccontent.mob.ability

import com.destroystokyo.paper.profile.ProfileProperty
import jp.awabi2048.cccontent.mob.MobDeathContext
import jp.awabi2048.cccontent.mob.MobRuntimeContext
import jp.awabi2048.cccontent.mob.MobSpawnContext
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.entity.Display
import org.bukkit.entity.Guardian
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class EnderEyeBeamOrbitAbility(
    override val id: String,
    private val orbitRadius: Double = 3.0,
    private val orbitAngularSpeed: Double = 0.14,
    private val orbitYJitter: Double = 0.12,
    private val headTextureValue: String = DEFAULT_HEAD_TEXTURE_VALUE,
    private val approachSpeed: Double = 0.18,
    private val retreatSpeed: Double = 0.22,
    private val holdDurationTicks: Long = 40L,
    private val orbitMinDurationTicks: Long = 60L,
    private val orbitMaxDurationTicks: Long = 120L,
    private val attackHoldDistance: Double = 2.4,
    private val retreatTargetDistance: Double = 7.5,
    private val ambientParticle: Particle = Particle.PORTAL,
    private val ambientParticleCount: Int = 4,
    private val displayScale: Float = 1.4f
) : MobAbility {

    enum class MovementPhase {
        ORBIT,
        APPROACH,
        HOLD,
        RETREAT
    }

    data class Runtime(
        var angle: Double = 0.0,
        var displayId: UUID? = null,
        var phase: MovementPhase = MovementPhase.ORBIT,
        var phaseTicksRemaining: Long = 0L,
        var elapsedTicks: Long = 0L
    ) : MobAbilityRuntime

    override fun tickIntervalTicks(): Long = 1L

    override fun createRuntime(context: MobSpawnContext): MobAbilityRuntime {
        return Runtime(
            angle = Random.nextDouble(0.0, Math.PI * 2.0),
            phaseTicksRemaining = randomOrbitDuration()
        )
    }

    override fun onSpawn(context: MobSpawnContext, runtime: MobAbilityRuntime?) {
        val guardian = context.entity as? Guardian ?: return
        val rt = runtime as? Runtime ?: return

        Bukkit.getMobGoals().removeAllGoals(guardian)
        guardian.setAI(false)
        guardian.setGravity(false)
        guardian.isInvisible = true
        guardian.isSilent = true
        guardian.isCollidable = false

        val display = guardian.world.spawn(
            guardian.location.clone().add(0.0, 0.85, 0.0),
            ItemDisplay::class.java
        )
        display.setItemStack(createHeadItem())
        display.billboard = Display.Billboard.FIXED
        display.isInvulnerable = true
        display.interpolationDuration = 1
        display.teleportDuration = 1
        display.brightness = Display.Brightness(15, 15)
        display.transformation = headDisplayTransformation(displayScale)
        display.addScoreboardTag("cc.mob.ender_eye_beam_display")
        rt.displayId = display.uniqueId
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val guardian = context.entity as? Guardian ?: return
        val rt = runtime as? Runtime ?: return
        val display = rt.displayId?.let { Bukkit.getEntity(it) as? ItemDisplay } ?: return
        if (!display.isValid) {
            return
        }

        val target = resolveTarget(guardian)
        if (target == null) {
            guardian.target = null
            display.teleport(guardian.location.clone().add(0.0, 0.85, 0.0))
            return
        }

        rt.elapsedTicks += context.tickDelta
        val destination = computeDestination(guardian, rt, target, context.tickDelta)
        val facing = faceTowards(destination, target.eyeLocation.clone())

        display.teleport(facing)
        guardian.teleport(destination.clone().add(0.0, -0.85, 0.0))

        advancePhase(rt, guardian, target, context.tickDelta)
        guardian.target = target.takeIf { rt.phase == MovementPhase.HOLD }
        spawnAmbient(display)
    }

    override fun onDeath(context: MobDeathContext, runtime: MobAbilityRuntime?) {
        val rt = runtime as? Runtime ?: return
        cleanupDisplay(rt)
    }

    private fun computeDestination(
        guardian: Guardian,
        rt: Runtime,
        target: Player,
        tickDelta: Long
    ): Location {
        val currentHeadLocation = guardian.location.clone().add(0.0, 0.85, 0.0)
        return when (rt.phase) {
            MovementPhase.ORBIT -> {
                rt.angle += orbitAngularSpeed * tickDelta.toDouble()
                target.eyeLocation.clone().add(
                    cos(rt.angle) * orbitRadius,
                    0.85 + Random.nextDouble(-orbitYJitter, orbitYJitter),
                    sin(rt.angle) * orbitRadius
                )
            }

            MovementPhase.APPROACH -> moveTowards(
                currentHeadLocation,
                radialPosition(target, rt.angle, attackHoldDistance, 0.85),
                approachSpeed * tickDelta.toDouble()
            )

            MovementPhase.HOLD -> radialPosition(target, rt.angle, attackHoldDistance, 0.85).add(
                0.0,
                sin(rt.elapsedTicks.toDouble() * 0.16) * 0.15,
                0.0
            )

            MovementPhase.RETREAT -> moveTowards(
                currentHeadLocation,
                radialPosition(target, rt.angle, retreatTargetDistance, 1.1),
                retreatSpeed * tickDelta.toDouble()
            )
        }
    }

    private fun advancePhase(rt: Runtime, guardian: Guardian, target: Player, tickDelta: Long) {
        rt.phaseTicksRemaining = (rt.phaseTicksRemaining - tickDelta).coerceAtLeast(0L)
        val distance = guardian.location.distance(target.location)

        when (rt.phase) {
            MovementPhase.ORBIT -> {
                if (rt.phaseTicksRemaining <= 0L) {
                    rt.phase = MovementPhase.APPROACH
                    rt.phaseTicksRemaining = 50L
                }
            }

            MovementPhase.APPROACH -> {
                if (distance <= attackHoldDistance + 0.35 || rt.phaseTicksRemaining <= 0L) {
                    rt.phase = MovementPhase.HOLD
                    rt.phaseTicksRemaining = holdDurationTicks
                }
            }

            MovementPhase.HOLD -> {
                if (rt.phaseTicksRemaining <= 0L) {
                    rt.phase = MovementPhase.RETREAT
                    rt.phaseTicksRemaining = 45L
                }
            }

            MovementPhase.RETREAT -> {
                if (distance >= retreatTargetDistance || rt.phaseTicksRemaining <= 0L) {
                    rt.phase = MovementPhase.ORBIT
                    rt.phaseTicksRemaining = randomOrbitDuration()
                }
            }
        }
    }

    private fun radialPosition(target: Player, angle: Double, radius: Double, yOffset: Double): Location {
        return target.eyeLocation.clone().add(
            cos(angle) * radius,
            yOffset,
            sin(angle) * radius
        )
    }

    private fun moveTowards(from: Location, to: Location, step: Double): Location {
        val delta = to.toVector().subtract(from.toVector())
        val distance = delta.length()
        if (distance <= step || distance <= 0.0001) {
            return to
        }
        return from.clone().add(delta.normalize().multiply(step))
    }

    private fun spawnAmbient(display: ItemDisplay) {
        if (ambientParticleCount <= 0) {
            return
        }
        display.world.spawnParticle(
            ambientParticle,
            display.location,
            ambientParticleCount,
            0.15,
            0.15,
            0.15,
            0.02
        )
    }

    private fun resolveTarget(guardian: Guardian): Player? {
        val fixed = (guardian.target as? Player)?.takeIf { isValidTarget(guardian, it) }
        if (fixed != null) {
            return fixed
        }

        return guardian.getNearbyEntities(32.0, 24.0, 32.0)
            .asSequence()
            .mapNotNull { it as? Player }
            .filter { isValidTarget(guardian, it) }
            .minByOrNull { it.location.distanceSquared(guardian.location) }
    }

    private fun isValidTarget(guardian: Guardian, player: Player): Boolean {
        if (!player.isValid || player.isDead) {
            return false
        }
        if (player.gameMode == GameMode.SPECTATOR) {
            return false
        }
        return player.world.uid == guardian.world.uid
    }

    private fun faceTowards(from: Location, to: Location): Location {
        val delta = to.toVector().subtract(from.toVector())
        val yaw = Math.toDegrees(atan2(-delta.x, delta.z)).toFloat()
        val xz = sqrt(delta.x * delta.x + delta.z * delta.z)
        val pitch = (-Math.toDegrees(atan2(delta.y, xz.coerceAtLeast(1.0e-6)))).toFloat()
        return from.clone().apply {
            this.yaw = yaw
            this.pitch = pitch
        }
    }

    private fun cleanupDisplay(runtime: Runtime) {
        val display = runtime.displayId?.let { Bukkit.getEntity(it) as? ItemDisplay } ?: return
        if (display.isValid) {
            display.remove()
        }
    }

    private fun createHeadItem(): ItemStack {
        val item = ItemStack(Material.PLAYER_HEAD)
        val skullMeta = item.itemMeta as? SkullMeta ?: return item
        val profile = Bukkit.createProfile(UUID.randomUUID(), "ender_eye_beam")
        profile.setProperty(ProfileProperty("textures", headTextureValue))
        skullMeta.playerProfile = profile
        item.itemMeta = skullMeta
        return item
    }

    private fun randomOrbitDuration(): Long {
        return Random.nextLong(orbitMinDurationTicks, orbitMaxDurationTicks + 1)
    }

    private companion object {
        const val DEFAULT_HEAD_TEXTURE_VALUE =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOWU2MmEzMTdmMmU4ZjM0OWEyN2UyOTZjZTIyNWI5ZThiMTI3ZDg4YmU2MWFhZWJmMTY2MDRiZmEyYWQ4MTMwOCJ9fX0="
    }
}
