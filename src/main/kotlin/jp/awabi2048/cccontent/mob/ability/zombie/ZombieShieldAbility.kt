package jp.awabi2048.cccontent.mob.ability.zombie

import jp.awabi2048.cccontent.mob.MobDamagedContext
import jp.awabi2048.cccontent.mob.MobRuntimeContext
import jp.awabi2048.cccontent.mob.ability.MobAbility
import jp.awabi2048.cccontent.mob.ability.MobAbilityRuntime
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Projectile
import kotlin.random.Random

class ZombieShieldAbility : MobAbility {
    override val id: String = "zombie_shield"

    data class Runtime(var blockCooldownTicks: Long = 0L) : MobAbilityRuntime

    override fun createRuntime(context: jp.awabi2048.cccontent.mob.MobSpawnContext): MobAbilityRuntime {
        return Runtime()
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val abilityRuntime = runtime as? Runtime ?: return
        if (abilityRuntime.blockCooldownTicks > 0L) {
            abilityRuntime.blockCooldownTicks -= 10L
        }
    }

    override fun onDamaged(context: MobDamagedContext, runtime: MobAbilityRuntime?) {
        if (!context.isCombatActive()) return

        val abilityRuntime = runtime as? Runtime ?: return
        val event = context.event
        if (event.isCancelled) return

        val damager = event.damager
        if (damager is Projectile) {
            blockDamage(context.entity, event, consumeCooldown = false)
            return
        }

        if (abilityRuntime.blockCooldownTicks > 0L) return

        val attacker = context.attacker ?: return
        if (!isFrontAttack(context.entity, attacker)) return
        if (Random.nextDouble() >= MELEE_BLOCK_CHANCE) return

        blockDamage(context.entity, event, consumeCooldown = true)
        abilityRuntime.blockCooldownTicks = BLOCK_COOLDOWN_TICKS
    }

    private fun isFrontAttack(entity: LivingEntity, attacker: LivingEntity): Boolean {
        val facing = entity.location.direction.clone().setY(0.0)
        if (facing.lengthSquared() < 0.0001) return true

        val toAttacker = attacker.location.toVector().subtract(entity.location.toVector()).setY(0.0)
        if (toAttacker.lengthSquared() < 0.0001) return true

        return facing.normalize().dot(toAttacker.normalize()) >= FRONT_DOT_THRESHOLD
    }

    private fun blockDamage(
        entity: LivingEntity,
        event: org.bukkit.event.entity.EntityDamageByEntityEvent,
        consumeCooldown: Boolean
    ) {
        event.damage = 0.0
        event.isCancelled = true

        entity.world.spawnParticle(Particle.SWEEP_ATTACK, entity.location.add(0.0, 1.0, 0.0), 1, 0.0, 0.0, 0.0, 0.0)
        entity.world.spawnParticle(Particle.CRIT, entity.location.add(0.0, 1.0, 0.0), 6, 0.2, 0.25, 0.2, 0.01)
        val pitch = if (consumeCooldown) 0.9f else 1.1f
        entity.world.playSound(entity.location, Sound.ITEM_SHIELD_BLOCK, 1.0f, pitch)
    }

    private companion object {
        const val BLOCK_COOLDOWN_TICKS = 100L
        const val MELEE_BLOCK_CHANCE = 0.5
        const val FRONT_DOT_THRESHOLD = 0.2
    }
}
