package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobRuntimeContext
import org.bukkit.Color
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.Vector
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MagmaLandingBurstAbility(
    override val id: String,
    private val radius: Double = 4.0,
    private val fireTicks: Int = 80,
    private val damage: Double = 3.0,
    private val horizontalKnockback: Double = 1.15,
    private val verticalKnockback: Double = 0.35
) : MobAbility {

    data class Runtime(
        var wasOnGround: Boolean = true,
        var plugin: JavaPlugin? = null
    ) : MobAbilityRuntime

    override fun createRuntime(context: jp.awabi2048.cccontent.mob.MobSpawnContext): MobAbilityRuntime {
        return Runtime(wasOnGround = context.entity.isOnGround, plugin = context.plugin)
    }

    override fun tickIntervalTicks(): Long = 1L

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val rt = runtime as? Runtime ?: return
        val entity = context.entity
        val onGround = entity.isOnGround
        if (onGround && !rt.wasOnGround) {
            triggerBurst(entity, rt)
        }
        rt.wasOnGround = onGround
    }

    private fun triggerBurst(entity: LivingEntity, runtime: Runtime) {
        val world = entity.world
        val center = entity.location.clone().add(0.0, 0.1, 0.0)
        world.spawnParticle(Particle.FLAME, center, 22, 1.4, 0.6, 1.4, 0.03)
        world.spawnParticle(Particle.SMOKE, center, 12, 1.2, 0.5, 1.2, 0.02)
        world.playSound(center, Sound.ENTITY_MAGMA_CUBE_SQUISH, 1.0f, 0.7f)
        spawnDustTransitionField(entity, center, runtime)

        world.getNearbyEntities(center, radius, radius, radius)
            .asSequence()
            .filterIsInstance<Player>()
            .filter { it.isValid && !it.isDead }
            .forEach { player ->
                player.fireTicks = maxOf(player.fireTicks, fireTicks)
                player.damage(damage, entity)
                val away = player.location.toVector().subtract(center.toVector()).setY(0.0)
                val direction = if (away.lengthSquared() < 1.0e-6) {
                    Vector(0.0, 0.0, 0.0)
                } else {
                    away.normalize().multiply(horizontalKnockback)
                }
                player.velocity = player.velocity.add(direction).setY(maxOf(player.velocity.y, verticalKnockback))
            }
    }

    private fun spawnDustTransitionField(entity: LivingEntity, center: org.bukkit.Location, runtime: Runtime) {
        val plugin = runtime.plugin ?: return
        val world = center.world ?: return
        val transition = Particle.DustTransition(Color.fromRGB(255, 0, 0), Color.fromRGB(0, 0, 0), 0.5f)
        val steps = 10
        val pointsPerStep = ((radius * radius * 10.0) / 3.0).toInt().coerceAtLeast(10)
        val ringWidth = (radius / steps.toDouble()).coerceAtLeast(0.35)

        repeat(steps) { tick ->
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                if (!entity.isValid || entity.isDead) return@Runnable
                val progress = (tick + 1).toDouble() / steps.toDouble()
                val outerRadius = radius * progress
                val innerRadius = (outerRadius - ringWidth).coerceAtLeast(0.0)
                val outerSquared = outerRadius * outerRadius
                val innerSquared = innerRadius * innerRadius
                repeat(pointsPerStep) {
                    val angle = Math.random() * PI * 2.0
                    val distance = sqrt(Math.random() * (outerSquared - innerSquared) + innerSquared)
                    val x = cos(angle) * distance
                    val z = sin(angle) * distance
                    val loc = center.clone().add(x, 0.12 + Math.random() * 0.45, z)
                    world.spawnParticle(Particle.DUST_COLOR_TRANSITION, loc, 1, 0.0, 0.0, 0.0, 0.0, transition)
                }
            }, tick.toLong())
        }
    }
}
