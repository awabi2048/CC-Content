package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobDamagedContext
import jp.awabi2048.cccontent.mob.MobRuntimeContext
import org.bukkit.Sound
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import org.bukkit.entity.Projectile
import org.bukkit.event.entity.EntityDamageByEntityEvent
import kotlin.random.Random

class ShieldAbility(
    override val id: String,
    private val blockCooldownTicks: Long = 100L,
    private val meleeBlockChance: Double = 0.5,
    private val frontDotThreshold: Double = 0.2,
    private val shieldBreakDamageThreshold: Double = 8.0,
    private val shieldDownTicks: Long = 80L,
    private val breakDisablesShieldPermanently: Boolean = false
) : MobAbility {
    data class Runtime(
        var blockCooldownTicks: Long = 0L,
        var shieldDownTicks: Long = 0L,
        var shieldBroken: Boolean = false
    ) : MobAbilityRuntime

    override fun createRuntime(context: jp.awabi2048.cccontent.mob.MobSpawnContext): MobAbilityRuntime {
        return Runtime()
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val abilityRuntime = runtime as? Runtime ?: return
        if (abilityRuntime.blockCooldownTicks > 0L) {
            abilityRuntime.blockCooldownTicks = (abilityRuntime.blockCooldownTicks - context.tickDelta).coerceAtLeast(0L)
        }
        if (abilityRuntime.shieldDownTicks > 0L) {
            abilityRuntime.shieldDownTicks = (abilityRuntime.shieldDownTicks - context.tickDelta).coerceAtLeast(0L)
        }
    }

    override fun onDamaged(context: MobDamagedContext, runtime: MobAbilityRuntime?) {
        if (!context.isCombatActive()) return

        val abilityRuntime = runtime as? Runtime ?: return
        if (abilityRuntime.shieldBroken) return
        val event = context.event
        if (event.isCancelled) return
        val target = resolveTarget(context.entity) ?: return
        if (abilityRuntime.shieldDownTicks > 0L) {
            return
        }

        val damager = event.damager
        if (damager is Projectile) {
            val shooter = damager.shooter as? LivingEntity
            if (shooter != null && shooter.uniqueId != target.uniqueId) {
                applyShieldBreakPenalty(context, abilityRuntime)
                return
            }
            if (abilityRuntime.shieldDownTicks > 0L) {
                applyShieldBreakPenalty(context, abilityRuntime)
                return
            }
            blockDamage(context.entity, event, consumeCooldown = false)
            return
        }

        if (abilityRuntime.blockCooldownTicks > 0L) {
            applyShieldBreakPenalty(context, abilityRuntime)
            return
        }

        val attacker = context.attacker ?: return
        if (attacker.uniqueId != target.uniqueId) {
            applyShieldBreakPenalty(context, abilityRuntime)
            return
        }
        if (!isFrontAttack(context.entity, attacker)) {
            applyShieldBreakPenalty(context, abilityRuntime)
            return
        }
        if (Random.nextDouble() >= meleeBlockChance) {
            applyShieldBreakPenalty(context, abilityRuntime)
            return
        }

        blockDamage(context.entity, event, consumeCooldown = true)
        abilityRuntime.blockCooldownTicks = blockCooldownTicks
    }

    private fun resolveTarget(entity: LivingEntity): LivingEntity? {
        val target = (entity as? Mob)?.target as? LivingEntity ?: return null
        if (!target.isValid || target.isDead) {
            return null
        }
        return target
    }

    private fun applyShieldBreakPenalty(context: MobDamagedContext, runtime: Runtime) {
        if (runtime.shieldDownTicks > 0L) {
            return
        }
        if (context.event.finalDamage < shieldBreakDamageThreshold) {
            return
        }
        if (runtime.shieldDownTicks >= shieldDownTicks) {
            return
        }

        if (breakDisablesShieldPermanently) {
            runtime.shieldDownTicks = 0L
            runtime.shieldBroken = true
            context.entity.equipment?.setItemInOffHand(null)
        } else {
            runtime.shieldDownTicks = shieldDownTicks
        }
        context.entity.world.playSound(context.entity.location, Sound.ITEM_SHIELD_BREAK, 0.9f, 1.0f)
    }

    private fun isFrontAttack(entity: LivingEntity, attacker: LivingEntity): Boolean {
        val facing = entity.location.direction.clone().setY(0.0)
        if (facing.lengthSquared() < 0.0001) return true

        val toAttacker = attacker.location.toVector().subtract(entity.location.toVector()).setY(0.0)
        if (toAttacker.lengthSquared() < 0.0001) return true

        return facing.normalize().dot(toAttacker.normalize()) >= frontDotThreshold
    }

    private fun blockDamage(entity: LivingEntity, event: EntityDamageByEntityEvent, consumeCooldown: Boolean) {
        event.damage = 0.0
        event.isCancelled = true

        val pitch = if (consumeCooldown) 0.9f else 1.1f
        entity.world.playSound(entity.location, Sound.ITEM_SHIELD_BLOCK, 1.0f, pitch)
    }
}
