package jp.awabi2048.cccontent.mob.ability

import org.bukkit.Bukkit
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.AbstractArrow
import org.bukkit.entity.LivingEntity
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Vector
import java.util.UUID
import java.util.WeakHashMap
import kotlin.math.acos
import kotlin.math.min

class HomingArrowService private constructor(private val plugin: JavaPlugin) {
    data class LaunchSpec(
        val arrowId: UUID,
        val targetId: UUID,
        val accuracy: Double = 0.75,
        val turnStrength: Double = 0.15,
        val maxTurnDegrees: Double = 12.0,
        val maxLifetimeTicks: Long = 200L
    )

    private data class ActiveHomingArrow(
        val arrowId: UUID,
        var targetId: UUID,
        val accuracy: Double,
        val turnStrength: Double,
        val maxTurnRadians: Double,
        var remainingTicks: Long
    )

    companion object {
        private val instances = WeakHashMap<JavaPlugin, HomingArrowService>()

        fun getInstance(plugin: JavaPlugin): HomingArrowService {
            synchronized(instances) {
                return instances.getOrPut(plugin) { HomingArrowService(plugin) }
            }
        }
    }

    private val updateIntervalTicks = 1L
    private val arrows = mutableMapOf<UUID, ActiveHomingArrow>()
    private var task: BukkitTask? = null

    fun launch(spec: LaunchSpec) {
        ensureTask()
        arrows[spec.arrowId] = ActiveHomingArrow(
            arrowId = spec.arrowId,
            targetId = spec.targetId,
            accuracy = spec.accuracy,
            turnStrength = spec.turnStrength,
            maxTurnRadians = Math.toRadians(spec.maxTurnDegrees),
            remainingTicks = spec.maxLifetimeTicks
        )
    }

    fun handleHit(arrowId: UUID) {
        arrows.remove(arrowId)
    }

    fun shutdown() {
        task?.cancel()
        task = null
        arrows.clear()
        synchronized(instances) {
            instances.remove(plugin)
        }
    }

    private fun ensureTask() {
        if (task != null) return
        task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable { tick() }, updateIntervalTicks, updateIntervalTicks)
    }

    private fun tick() {
        val iterator = arrows.entries.iterator()
        while (iterator.hasNext()) {
            val (_, active) = iterator.next()

            active.remainingTicks--
            if (active.remainingTicks <= 0L) {
                iterator.remove()
                continue
            }

            val arrow = Bukkit.getEntity(active.arrowId) as? AbstractArrow
            if (arrow == null || !arrow.isValid || arrow.isDead) {
                iterator.remove()
                continue
            }

            val target = Bukkit.getEntity(active.targetId) as? LivingEntity
            if (target == null || !target.isValid || target.isDead) {
                continue
            }

            if (Math.random() >= active.accuracy) {
                continue
            }

            val arrowVel = arrow.velocity
            val arrowPos = arrow.location.toVector()
            val targetPos = target.eyeLocation.toVector()
            val toTarget = targetPos.clone().subtract(arrowPos)
            val distance = toTarget.length()

            if (distance < 0.5) {
                continue
            }

            val desiredDir = toTarget.normalize()
            val currentDir = arrowVel.clone().normalize()
            val currentSpeed = arrowVel.length()

            if (currentSpeed < 0.01) {
                continue
            }

            val dot = currentDir.dot(desiredDir).coerceIn(-1.0, 1.0)
            val angleToTarget = acos(dot)

            if (angleToTarget < 0.01) {
                continue
            }

            val maxTurnThisTick = active.maxTurnRadians
            val turnAngle = min(angleToTarget, maxTurnThisTick)
            val blendFactor = if (angleToTarget > 0.001) turnAngle / angleToTarget else 0.0

            val newDir = currentDir.clone().multiply(1.0 - blendFactor).add(desiredDir.clone().multiply(blendFactor))
            val newDirLen = newDir.length()
            if (newDirLen < 0.001) {
                continue
            }
            newDir.normalize()

            val blendedSpeed = currentSpeed * (1.0 - active.turnStrength) + currentSpeed * active.turnStrength
            arrow.velocity = newDir.multiply(blendedSpeed)
        }

        if (arrows.isEmpty()) {
            task?.cancel()
            task = null
        }
    }
}
