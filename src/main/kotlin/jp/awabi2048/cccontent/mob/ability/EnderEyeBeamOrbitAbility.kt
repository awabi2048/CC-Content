package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobRuntimeContext
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Guardian
import org.bukkit.entity.Player
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class EnderEyeBeamOrbitAbility(
    override val id: String,
    private val orbitRadius: Double = 3.0,
    private val orbitAngularSpeed: Double = 0.14,
    private val orbitYJitter: Double = 0.12
) : MobAbility {

    data class Runtime(
        var angle: Double = 0.0,
        var searchPhaseOffsetSteps: Int = 0
    ) : MobAbilityRuntime

    override fun tickIntervalTicks(): Long = 1L

    override fun createRuntime(context: jp.awabi2048.cccontent.mob.MobSpawnContext): MobAbilityRuntime {
        return Runtime(
            angle = Random.nextDouble(0.0, Math.PI * 2.0),
            searchPhaseOffsetSteps = Random.nextInt(0, SEARCH_PHASE_VARIANTS)
        )
    }

    override fun onSpawn(context: jp.awabi2048.cccontent.mob.MobSpawnContext, runtime: MobAbilityRuntime?) {
        val guardian = context.entity as? Guardian ?: return
        Bukkit.getMobGoals().removeAllGoals(guardian)
        guardian.setAI(false)
        guardian.setGravity(false)
        guardian.isInvisible = true
        guardian.isSilent = true
        guardian.isCollidable = false
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val guardian = context.entity as? Guardian ?: return
        val rt = runtime as? Runtime ?: return
        val target = resolveTarget(guardian) ?: return
        if (!target.isValid || target.isDead || target.world.uid != guardian.world.uid) return

        rt.angle += orbitAngularSpeed * context.tickDelta.toDouble()
        val center = target.eyeLocation.clone().add(0.0, 1.0, 0.0)
        val destination = center.clone().add(
            cos(rt.angle) * orbitRadius,
            Random.nextDouble(-orbitYJitter, orbitYJitter),
            sin(rt.angle) * orbitRadius
        )

        val facing = faceTowards(destination, target.eyeLocation.clone())
        guardian.teleport(facing)
        guardian.target = target
    }

    private fun resolveTarget(guardian: Guardian): Player? {
        val fixed = MobAbilityUtils.resolveTarget(guardian) as? Player
        if (fixed != null && fixed.isValid && !fixed.isDead && fixed.world.uid == guardian.world.uid) {
            return fixed
        }
        return guardian.getNearbyEntities(32.0, 24.0, 32.0)
            .asSequence()
            .mapNotNull { it as? Player }
            .filter { it.isValid && !it.isDead && it.world.uid == guardian.world.uid }
            .minByOrNull { it.location.distanceSquared(guardian.location) }
    }

    private fun faceTowards(from: Location, to: Location): Location {
        val delta = to.toVector().subtract(from.toVector())
        val yaw = Math.toDegrees(atan2(-delta.x, delta.z)).toFloat()
        val xz = kotlin.math.sqrt(delta.x * delta.x + delta.z * delta.z)
        val pitch = (-Math.toDegrees(atan2(delta.y, xz.coerceAtLeast(1.0e-6)))).toFloat()
        return from.clone().apply {
            this.yaw = yaw
            this.pitch = pitch
        }
    }

    private companion object {
        const val SEARCH_PHASE_VARIANTS = 16
    }
}
