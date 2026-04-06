package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobCombustContext
import jp.awabi2048.cccontent.mob.MobDamagedContext
import jp.awabi2048.cccontent.mob.MobGenericDamagedContext
import org.bukkit.Location
import org.bukkit.entity.Projectile
import org.bukkit.event.entity.EntityDamageEvent
import kotlin.random.Random

class EndermanPhaseDefenseAbility(
    override val id: String,
    private val projectileNegateChance: Double,
    private val evadeRadius: Double = 8.0
) : MobAbility {

    override fun onDamaged(context: MobDamagedContext, runtime: MobAbilityRuntime?) {
        val projectile = context.event.damager as? Projectile ?: return
        if (Random.nextDouble() >= projectileNegateChance.coerceIn(0.0, 1.0)) {
            return
        }

        context.event.isCancelled = true
        val current = context.entity.location
        val destination = EndThemeEffects.findNearbyTeleportLocation(current, evadeRadius) ?: return
        performTeleport(context.plugin, context.entity, destination)
        projectile.remove()
    }

    override fun onGenericDamaged(context: MobGenericDamagedContext, runtime: MobAbilityRuntime?) {
        if (context.event.cause in FIRE_DAMAGE_CAUSES) {
            context.event.isCancelled = true
        }
    }

    override fun onCombust(context: MobCombustContext, runtime: MobAbilityRuntime?) {
        context.event.isCancelled = true
    }

    private fun performTeleport(
        plugin: org.bukkit.plugin.java.JavaPlugin,
        entity: org.bukkit.entity.LivingEntity,
        destination: Location
    ) {
        val world = entity.world
        EndThemeEffects.playTeleportSound(world, entity.location)
        world.spawnParticle(org.bukkit.Particle.PORTAL, entity.location.clone().add(0.0, 1.0, 0.0), 44, 0.8, 0.8, 0.8, 0.05)
        entity.teleport(destination)
        EndThemeEffects.playTeleportSound(world, destination)
        EndThemeEffects.spawnTeleportDebuffField(
            plugin = plugin,
            owner = entity,
            center = destination
        )
    }

    private companion object {
        val FIRE_DAMAGE_CAUSES = setOf(
            EntityDamageEvent.DamageCause.FIRE,
            EntityDamageEvent.DamageCause.FIRE_TICK,
            EntityDamageEvent.DamageCause.LAVA,
            EntityDamageEvent.DamageCause.HOT_FLOOR,
            EntityDamageEvent.DamageCause.CAMPFIRE
        )
    }
}
