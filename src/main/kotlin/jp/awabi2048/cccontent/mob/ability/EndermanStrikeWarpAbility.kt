package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobAttackContext
import jp.awabi2048.cccontent.mob.MobDamagedContext
import jp.awabi2048.cccontent.mob.MobRuntimeContext
import org.bukkit.Location
import org.bukkit.attribute.Attribute
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.entity.EntityDamageByEntityEvent
import kotlin.math.min
import kotlin.random.Random

class EndermanStrikeWarpAbility(
    override val id: String,
    private val lateralWarpRadius: Double = 2.5,
    private val swapCooldownTicks: Long = 60L,
    private val swapChance: Double = 0.24,
    private val healCapRateFromMaxHealth: Double = 0.33
) : MobAbility {

    data class Runtime(var swapCooldownRemainingTicks: Long = 0L) : MobAbilityRuntime

    override fun tickIntervalTicks(): Long = 1L

    override fun createRuntime(context: jp.awabi2048.cccontent.mob.MobSpawnContext): MobAbilityRuntime {
        return Runtime()
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val rt = runtime as? Runtime ?: return
        if (rt.swapCooldownRemainingTicks > 0L) {
            rt.swapCooldownRemainingTicks = (rt.swapCooldownRemainingTicks - context.tickDelta).coerceAtLeast(0L)
        }
    }

    override fun onAttack(context: MobAttackContext, runtime: MobAbilityRuntime?) {
        if (!isDirectMeleeAttack(context.event, context.entity)) {
            return
        }

        val target = context.target as? Player ?: return
        if (!target.isValid || target.isDead || target.world.uid != context.entity.world.uid) {
            return
        }

        val dealtDamage = context.event.finalDamage.coerceAtLeast(0.0)
        val maxHealth = context.entity.getAttribute(Attribute.MAX_HEALTH)?.value ?: context.entity.maxHealth
        val healCap = (maxHealth * healCapRateFromMaxHealth).coerceAtLeast(0.0)
        if (dealtDamage > 0.0 && healCap > 0.0) {
            val healed = min(dealtDamage, healCap)
            context.entity.health = (context.entity.health + healed).coerceAtMost(maxHealth)
        }

        val destination = EndThemeEffects.findNearbyTeleportLocation(context.entity.location, lateralWarpRadius) ?: return
        performTeleport(
            plugin = context.plugin,
            entity = context.entity,
            destination = destination,
            withDebuffField = true
        )
    }

    override fun onDamaged(context: MobDamagedContext, runtime: MobAbilityRuntime?) {
        val rt = runtime as? Runtime ?: return
        if (rt.swapCooldownRemainingTicks > 0L) {
            return
        }

        val attacker = resolvePlayerAttacker(context.event) ?: return
        if (!attacker.isValid || attacker.isDead || attacker.world.uid != context.entity.world.uid) {
            return
        }
        if (Random.nextDouble() >= swapChance.coerceIn(0.0, 1.0)) {
            return
        }

        val attackerLoc = attacker.location.clone()
        val endermanLoc = context.entity.location.clone()
        attacker.teleport(endermanLoc)
        performTeleport(
            plugin = context.plugin,
            entity = context.entity,
            destination = attackerLoc,
            withDebuffField = true
        )
        rt.swapCooldownRemainingTicks = swapCooldownTicks.coerceAtLeast(1L)
    }

    private fun resolvePlayerAttacker(event: EntityDamageByEntityEvent): Player? {
        val direct = event.damager as? Player
        if (direct != null) {
            return direct
        }
        val projectile = event.damager as? Projectile ?: return null
        return projectile.shooter as? Player
    }

    private fun performTeleport(
        plugin: org.bukkit.plugin.java.JavaPlugin,
        entity: LivingEntity,
        destination: Location,
        withDebuffField: Boolean
    ) {
        val world = entity.world
        EndThemeEffects.playTeleportSound(world, entity.location)
        world.spawnParticle(org.bukkit.Particle.PORTAL, entity.location.clone().add(0.0, 1.0, 0.0), 32, 0.5, 0.7, 0.5, 0.02)
        entity.teleport(destination)
        EndThemeEffects.playTeleportSound(world, destination)
        if (withDebuffField) {
            EndThemeEffects.spawnTeleportDebuffField(plugin, entity, destination)
        }
    }

    private fun isDirectMeleeAttack(event: EntityDamageByEntityEvent, attacker: LivingEntity): Boolean {
        val damager = event.damager as? LivingEntity ?: return false
        return damager.uniqueId == attacker.uniqueId
    }
}
