package jp.awabi2048.cccontent.mob

import org.bukkit.entity.LivingEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.entity.EntityShootBowEvent

class MobEventListener(private val mobService: MobService) : Listener {

    @EventHandler(ignoreCancelled = true)
    fun onEntityDamage(event: EntityDamageByEntityEvent) {
        mobService.handleProjectileEffects(event)

        val damaged = event.entity as? LivingEntity
        if (damaged != null) {
            mobService.handleDamaged(event, damaged)
        }

        val attacker = event.damager as? LivingEntity
        if (attacker != null) {
            mobService.handleAttack(event, attacker)
        }
    }

    @EventHandler
    fun onEntityDeath(event: EntityDeathEvent) {
        mobService.handleDeath(event)
    }

    @EventHandler(ignoreCancelled = true)
    fun onShootBow(event: EntityShootBowEvent) {
        mobService.handleShootBow(event)
    }

    @EventHandler(ignoreCancelled = true)
    fun onProjectileHit(event: ProjectileHitEvent) {
        mobService.handleProjectileHit(event)
    }

    @EventHandler(ignoreCancelled = true)
    fun onEntityPickupItem(event: EntityPickupItemEvent) {
        mobService.handleEntityPickupItem(event)
    }
}
