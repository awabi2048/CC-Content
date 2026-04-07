package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobDamagedContext
import jp.awabi2048.cccontent.mob.MobDeathContext
import jp.awabi2048.cccontent.mob.MobRuntimeContext
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.scheduler.BukkitTask
import kotlin.math.roundToLong
import kotlin.random.Random

class EndermanMistDelayTeleportAbility(
    override val id: String,
    private val triggerChance: Double = 0.35,
    private val delayTicks: Long = 14L,
    private val teleportRadius: Double = 6.0,
    private val cooldownTicks: Long = 70L
) : MobAbility {

    data class Runtime(
        var cooldownRemainingTicks: Long = 0L,
        var pendingTask: BukkitTask? = null
    ) : MobAbilityRuntime

    override fun tickIntervalTicks(): Long = 1L

    override fun createRuntime(context: jp.awabi2048.cccontent.mob.MobSpawnContext): MobAbilityRuntime {
        return Runtime()
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val rt = runtime as? Runtime ?: return
        if (rt.cooldownRemainingTicks > 0L) {
            rt.cooldownRemainingTicks = (rt.cooldownRemainingTicks - context.tickDelta).coerceAtLeast(0L)
        }
    }

    override fun onDamaged(context: MobDamagedContext, runtime: MobAbilityRuntime?) {
        val rt = runtime as? Runtime ?: return
        if (!context.isCombatActive()) return
        if (context.event.isCancelled) return
        if (rt.cooldownRemainingTicks > 0L || rt.pendingTask != null) return

        val attacker = resolvePlayerAttacker(context.event)
        if (attacker == null || !attacker.isValid || attacker.isDead || attacker.world.uid != context.entity.world.uid) {
            return
        }

        if (Random.nextDouble() >= triggerChance.coerceIn(0.0, 1.0)) {
            return
        }

        val origin = context.entity.location.clone()
        val world = context.entity.world
        world.spawnParticle(Particle.CLOUD, origin.clone().add(0.0, 1.0, 0.0), 28, 0.65, 0.5, 0.65, 0.01)
        world.spawnParticle(Particle.PORTAL, origin.clone().add(0.0, 1.0, 0.0), 20, 0.45, 0.45, 0.45, 0.02)

        rt.pendingTask = org.bukkit.Bukkit.getScheduler().runTaskLater(context.plugin, Runnable {
            rt.pendingTask = null
            val entity = context.entity
            if (!entity.isValid || entity.isDead) return@Runnable
            val destination = EndThemeEffects.findNearbyTeleportLocation(entity.location, teleportRadius, attempts = 24) ?: return@Runnable
            EndThemeEffects.playTeleportSound(world, entity.location)
            world.spawnParticle(Particle.CLOUD, entity.location.clone().add(0.0, 1.0, 0.0), 20, 0.55, 0.4, 0.55, 0.01)
            entity.teleport(destination)
            EndThemeEffects.playTeleportSound(world, destination)
            world.spawnParticle(Particle.CLOUD, destination.clone().add(0.0, 1.0, 0.0), 28, 0.65, 0.5, 0.65, 0.01)
        }, delayTicks.coerceAtLeast(1L))

        val baseCooldown = cooldownTicks.coerceAtLeast(1L)
        rt.cooldownRemainingTicks = (baseCooldown * context.loadSnapshot.abilityCooldownMultiplier).roundToLong().coerceAtLeast(baseCooldown)
    }

    override fun onDeath(context: MobDeathContext, runtime: MobAbilityRuntime?) {
        val rt = runtime as? Runtime ?: return
        rt.pendingTask?.cancel()
        rt.pendingTask = null
    }

    private fun resolvePlayerAttacker(event: org.bukkit.event.entity.EntityDamageByEntityEvent): Player? {
        val direct = event.damager as? Player
        if (direct != null) return direct
        val projectile = event.damager as? Projectile ?: return null
        return projectile.shooter as? Player
    }
}
