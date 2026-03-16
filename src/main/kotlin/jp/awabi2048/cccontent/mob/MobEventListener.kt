package jp.awabi2048.cccontent.mob

import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDeathEvent

class MobEventListener(private val mobService: MobService) : Listener {

    @EventHandler(ignoreCancelled = true)
    fun onEntityDamage(event: EntityDamageByEntityEvent) {
        val damaged = event.entity as? LivingEntity
        if (damaged != null) {
            mobService.handleDamaged(event, damaged)
        }

        val attacker = when (val damager = event.damager) {
            is LivingEntity -> damager
            is Projectile -> damager.shooter as? LivingEntity
            else -> null
        }
        if (attacker != null) {
            mobService.handleAttack(event, attacker)
        }
    }

    @EventHandler
    fun onEntityDeath(event: EntityDeathEvent) {
        mobService.handleDeath(event)
    }
}
