package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobDamagedContext
import jp.awabi2048.cccontent.mob.MobRuntimeContext
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.util.Vector
import kotlin.math.roundToLong
import kotlin.random.Random

class EndermanBackstabTeleportAbility(
    override val id: String,
    private val triggerChance: Double = 0.28,
    private val behindDistance: Double = 1.8,
    private val cooldownTicks: Long = 60L,
    private val healPercentOfMaxHealth: Double = 0.06
) : MobAbility {

    data class Runtime(var cooldownRemainingTicks: Long = 0L) : MobAbilityRuntime

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
        if (rt.cooldownRemainingTicks > 0L) return

        val attacker = resolvePlayerAttacker(context.event) ?: return
        if (!attacker.isValid || attacker.isDead || attacker.world.uid != context.entity.world.uid) return
        if (Random.nextDouble() >= triggerChance.coerceIn(0.0, 1.0)) return

        val desiredBehind = computeBehindLocation(attacker, behindDistance)
        val destination = EndThemeEffects.findNearbyTeleportLocation(desiredBehind, 1.6, attempts = 16)
            ?: EndThemeEffects.findNearbyTeleportLocation(context.entity.location, 4.0, attempts = 16)
            ?: return

        val world = context.entity.world
        EndThemeEffects.playTeleportSound(world, context.entity.location)
        world.spawnParticle(Particle.PORTAL, context.entity.location.clone().add(0.0, 1.0, 0.0), 26, 0.45, 0.45, 0.45, 0.02)
        context.entity.teleport(destination)
        EndThemeEffects.playTeleportSound(world, destination)
        world.spawnParticle(Particle.PORTAL, destination.clone().add(0.0, 1.0, 0.0), 22, 0.35, 0.35, 0.35, 0.02)

        val maxHealth = context.entity.getAttribute(Attribute.MAX_HEALTH)?.value ?: context.entity.maxHealth
        val heal = (maxHealth * healPercentOfMaxHealth).coerceAtLeast(0.0)
        if (heal > 0.0) {
            context.entity.health = (context.entity.health + heal).coerceAtMost(maxHealth)
        }

        val baseCooldown = cooldownTicks.coerceAtLeast(1L)
        rt.cooldownRemainingTicks = (baseCooldown * context.loadSnapshot.abilityCooldownMultiplier).roundToLong().coerceAtLeast(baseCooldown)
    }

    private fun computeBehindLocation(player: Player, distance: Double): Location {
        val facing = player.location.direction.clone().setY(0.0)
        val normalized = if (facing.lengthSquared() > 0.0001) facing.normalize() else Vector(0.0, 0.0, 1.0)
        return player.location.clone().subtract(normalized.multiply(distance)).add(0.0, 0.1, 0.0)
    }

    private fun resolvePlayerAttacker(event: org.bukkit.event.entity.EntityDamageByEntityEvent): Player? {
        val direct = event.damager as? Player
        if (direct != null) return direct
        val projectile = event.damager as? Projectile ?: return null
        return projectile.shooter as? Player
    }
}
