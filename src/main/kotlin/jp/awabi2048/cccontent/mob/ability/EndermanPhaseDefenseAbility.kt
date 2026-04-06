package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobCombustContext
import jp.awabi2048.cccontent.mob.MobDamagedContext
import jp.awabi2048.cccontent.mob.MobGenericDamagedContext
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.util.Vector

class EndermanPhaseDefenseAbility(
    override val id: String,
    private val evadeRadius: Double = 8.0
) : MobAbility {

    override fun onDamaged(context: MobDamagedContext, runtime: MobAbilityRuntime?) {
        val attacker = resolvePlayerAttacker(context.event) ?: return
        if (!attacker.isValid || attacker.isDead || attacker.world.uid != context.entity.world.uid) {
            return
        }

        val current = context.entity.location
        val destination = EndThemeEffects.findNearbyTeleportLocation(current, evadeRadius) ?: return
        performTeleport(context.plugin, context.entity, destination)
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
        entity.velocity = Vector(0.0, 0.0, 0.0)
        EndThemeEffects.playTeleportSound(world, entity.location)
        world.spawnParticle(org.bukkit.Particle.PORTAL, entity.location.clone().add(0.0, 1.0, 0.0), 44, 0.8, 0.8, 0.8, 0.05)
        entity.teleport(destination)
        entity.velocity = Vector(0.0, 0.0, 0.0)
        org.bukkit.Bukkit.getScheduler().runTask(plugin, Runnable {
            if (!entity.isValid || entity.isDead) {
                return@Runnable
            }
            entity.velocity = Vector(0.0, 0.0, 0.0)
        })
        EndThemeEffects.playTeleportSound(world, destination)
    }

    private fun resolvePlayerAttacker(event: org.bukkit.event.entity.EntityDamageByEntityEvent): Player? {
        val direct = event.damager as? Player
        if (direct != null) {
            return direct
        }
        val projectile = event.damager as? Projectile ?: return null
        return projectile.shooter as? Player
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
