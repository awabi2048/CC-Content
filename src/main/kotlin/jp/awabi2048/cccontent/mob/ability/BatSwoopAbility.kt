package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobCombustContext
import jp.awabi2048.cccontent.mob.MobDeathContext
import jp.awabi2048.cccontent.mob.MobRuntimeContext
import jp.awabi2048.cccontent.mob.MobSpawnContext
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Bat
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityCombustEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class BatSwoopAbility(
    override val id: String,
    private val searchRange: Double = DEFAULT_SEARCH_RANGE,
    private val attackRange: Double = DEFAULT_ATTACK_RANGE,
    private val attackCooldownTicks: Long = DEFAULT_ATTACK_COOLDOWN_TICKS,
    private val retreatDurationTicks: Long = DEFAULT_RETREAT_DURATION_TICKS,
    private val effectDurationTicks: Int = DEFAULT_EFFECT_DURATION_TICKS,
    private val effectAmplifier: Int = DEFAULT_EFFECT_AMPLIFIER,
    private val flySpeedMultiplier: Double = DEFAULT_FLY_SPEED_MULTIPLIER,
    private val retreatSpeedMultiplier: Double = DEFAULT_RETREAT_SPEED_MULTIPLIER,
    private val verticalBias: Double = DEFAULT_VERTICAL_BIAS,
    private val forcedScale: Double = DEFAULT_FORCED_SCALE
) : MobAbility {

    enum class Phase { IDLE, APPROACH, ATTACK, RETREAT }

    data class Runtime(
        var phase: Phase = Phase.IDLE,
        var cooldownTicks: Long = 0L,
        var retreatTicks: Long = 0L,
        var scaleApplied: Boolean = false,
        var combustListener: Listener? = null
    ) : MobAbilityRuntime

    override fun tickIntervalTicks(): Long = 1L

    override fun createRuntime(context: MobSpawnContext): MobAbilityRuntime {
        return Runtime()
    }

    override fun onSpawn(context: MobSpawnContext, runtime: MobAbilityRuntime?) {
        val bat = context.entity as? Bat ?: return
        val rt = runtime as? Runtime ?: return

        Bukkit.getMobGoals().removeAllGoals(bat)
        bat.isSilent = false
        bat.setAwake(true)

        val combustListener = object : Listener {
            @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
            fun onCombust(event: EntityCombustEvent) {
                if (event.entity.uniqueId == bat.uniqueId) {
                    event.isCancelled = true
                }
            }
        }
        Bukkit.getPluginManager().registerEvents(combustListener, context.plugin)
        rt.combustListener = combustListener

        bat.getAttribute(Attribute.SCALE)?.baseValue = forcedScale
        rt.scaleApplied = true
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val rt = runtime as? Runtime ?: return
        val entity = context.entity
        val bat = entity as? Bat ?: return

        if (!rt.scaleApplied) {
            bat.getAttribute(Attribute.SCALE)?.baseValue = forcedScale
            rt.scaleApplied = true
        }

        bat.fireTicks = 0

        if (rt.cooldownTicks > 0L) {
            rt.cooldownTicks -= context.tickDelta
        }

        val target = findNearestPlayer(entity)
        if (target != null && target.isValid && !target.isDead) {
            faceTarget(bat, target)
        }

        when (rt.phase) {
            Phase.IDLE -> {
                if (target == null || !target.isValid || target.isDead) return
                if (rt.cooldownTicks > 0L) return
                rt.phase = Phase.APPROACH
            }

            Phase.APPROACH -> {
                if (target == null || !target.isValid || target.isDead) {
                    rt.phase = Phase.IDLE
                    return
                }
                val speed = entity.getAttribute(Attribute.MOVEMENT_SPEED)?.value ?: 0.3
                val dir = directionTo(entity, target, verticalBias)
                bat.velocity = dir.multiply(speed * flySpeedMultiplier)

                if (!context.isCombatActive()) return

                val distance = entity.location.distance(target.location)
                if (distance <= attackRange) {
                    rt.phase = Phase.ATTACK
                }
            }

            Phase.ATTACK -> {
                if (target != null && target.isValid && !target.isDead && context.isCombatActive()) {
                    val attackDamage = entity.getAttribute(Attribute.ATTACK_DAMAGE)?.value ?: 5.0
                    target.damage(attackDamage, entity)

                    val effectType = if (Random.nextBoolean()) PotionEffectType.POISON else PotionEffectType.WITHER
                    target.addPotionEffect(
                        PotionEffect(effectType, effectDurationTicks, effectAmplifier, false, true, true)
                    )

                    target.world?.playSound(
                        target.location,
                        Sound.ENTITY_ZOMBIE_INFECT,
                        1.2f,
                        0.5f
                    )
                    target.world?.spawnParticle(
                        Particle.SMOKE,
                        target.location.clone().add(0.0, 1.0, 0.0),
                        12,
                        0.5,
                        0.5,
                        0.5,
                        0.02
                    )
                }
                rt.phase = Phase.RETREAT
                rt.retreatTicks = retreatDurationTicks
            }

            Phase.RETREAT -> {
                rt.retreatTicks -= context.tickDelta
                if (target != null && target.isValid && !target.isDead) {
                    val speed = entity.getAttribute(Attribute.MOVEMENT_SPEED)?.value ?: 0.3
                    val away = directionTo(target, entity, verticalBias)
                    bat.velocity = away.multiply(speed * retreatSpeedMultiplier)
                }

                if (rt.retreatTicks <= 0L) {
                    rt.phase = Phase.IDLE
                    rt.cooldownTicks = attackCooldownTicks
                }
            }
        }
    }

    override fun onCombust(context: MobCombustContext, runtime: MobAbilityRuntime?) {
        context.event.isCancelled = true
    }

    override fun onDeath(context: MobDeathContext, runtime: MobAbilityRuntime?) {
        val rt = runtime as? Runtime ?: return
        rt.combustListener?.let { HandlerList.unregisterAll(it) }
        rt.combustListener = null
    }

    private fun findNearestPlayer(entity: LivingEntity): Player? {
        return entity.getNearbyEntities(searchRange, searchRange, searchRange)
            .asSequence()
            .filterIsInstance<Player>()
            .filter { it.isValid && !it.isDead }
            .minByOrNull { it.location.distanceSquared(entity.location) }
    }

    private fun faceTarget(entity: LivingEntity, target: LivingEntity) {
        val from = entity.location.clone().add(0.0, 0.5, 0.0)
        val to = target.location.clone().add(0.0, 0.5, 0.0)
        val dir = to.toVector().subtract(from.toVector())
        val yaw = Math.toDegrees(atan2(-dir.x, dir.z)).toFloat()
        val distXZ = sqrt(dir.x * dir.x + dir.z * dir.z)
        val pitch = (-Math.toDegrees(atan2(dir.y, distXZ))).toFloat()
        val loc = entity.location.clone()
        loc.yaw = yaw
        loc.pitch = pitch
        entity.teleport(loc)
    }

    private fun directionTo(from: LivingEntity, to: LivingEntity, yBias: Double): Vector {
        val fLoc = from.location
        val tLoc = to.location
        val dx = tLoc.x - fLoc.x
        val dy = (tLoc.y - fLoc.y) + yBias
        val dz = tLoc.z - fLoc.z
        return Vector(dx, dy, dz).normalize()
    }

    companion object {
        private const val DEFAULT_SEARCH_RANGE = 16.0
        private const val DEFAULT_ATTACK_RANGE = 1.5
        private const val DEFAULT_ATTACK_COOLDOWN_TICKS = 40L
        private const val DEFAULT_RETREAT_DURATION_TICKS = 20L
        private const val DEFAULT_EFFECT_DURATION_TICKS = 60
        private const val DEFAULT_EFFECT_AMPLIFIER = 2
        private const val DEFAULT_FLY_SPEED_MULTIPLIER = 3.0
        private const val DEFAULT_RETREAT_SPEED_MULTIPLIER = 2.5
        private const val DEFAULT_VERTICAL_BIAS = 0.3
        private const val DEFAULT_FORCED_SCALE = 3.0
    }
}
