package jp.awabi2048.cccontent.mob

import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Entity
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityCombustEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.event.entity.EntityTransformEvent
import org.bukkit.event.entity.EntityRemoveEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.entity.ProjectileLaunchEvent
import org.bukkit.event.entity.SlimeSplitEvent
import org.bukkit.event.entity.EntityTeleportEvent
import org.bukkit.event.player.PlayerInteractEntityEvent

class MobEventListener(private val mobService: MobService) : Listener {

    @EventHandler(ignoreCancelled = true)
    fun onEntityDamage(event: EntityDamageByEntityEvent) {
        mobService.handleProjectileEffects(event)
        mobService.handleStealthFangDamage(event)

        mobService.handleEntityDamaged(event, event.entity)

        val damaged = event.entity as? LivingEntity
        if (damaged != null) {
            mobService.handleDamaged(event, damaged)
        }

        val attacker = event.damager as? LivingEntity
        if (attacker != null) {
            mobService.handleAttack(event, attacker)
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onEntityGenericDamage(event: EntityDamageEvent) {
        if (event is EntityDamageByEntityEvent) {
            return
        }
        val damaged = event.entity as? LivingEntity ?: return
        mobService.handleDamaged(event, damaged)
    }

    @EventHandler(ignoreCancelled = true)
    fun onEntityCombust(event: EntityCombustEvent) {
        val entity = event.entity as? LivingEntity ?: return
        mobService.handleCombust(event, entity)
    }

    @EventHandler
    fun onEntityDeath(event: EntityDeathEvent) {
        mobService.handleDeath(event)
    }

    @EventHandler(ignoreCancelled = true)
    fun onEntityGenericDeath(event: EntityRemoveEvent) {
        if (event.cause != EntityRemoveEvent.Cause.DEATH) {
            return
        }
        mobService.handleEntityDeath(event.entity)
    }

    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val cause = event.entity.lastDamageCause as? EntityDamageByEntityEvent ?: return
        val mobName = mobService.resolveCustomMobDisplayNameByDamager(cause.damager, event.entity) ?: return
        val message = event.deathMessage ?: return

        val replacementCandidates = buildReplacementCandidates(cause.damager)
        var replaced = false
        var newMessage = message
        for (candidate in replacementCandidates) {
            if (candidate.isBlank()) continue
            if (newMessage.contains(candidate)) {
                newMessage = newMessage.replace(candidate, mobName)
                replaced = true
                break
            }
        }

        if (replaced) {
            event.deathMessage = newMessage
        }
    }

    private fun buildReplacementCandidates(damager: Entity): List<String> {
        val candidates = mutableListOf<String>()
        damager.customName?.let { candidates.add(it) }
        candidates.add(damager.name)

        val projectile = damager as? Projectile
        val shooter = projectile?.shooter as? Entity
        if (shooter != null) {
            shooter.customName?.let { candidates.add(it) }
            candidates.add(shooter.name)
        }
        return candidates.distinct()
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
    fun onProjectileLaunch(event: ProjectileLaunchEvent) {
        mobService.handleProjectileLaunch(event)
    }

    @EventHandler(ignoreCancelled = true)
    fun onPlayerInteractEntity(event: PlayerInteractEntityEvent) {
        mobService.handlePlayerInteractEntity(event)
    }

    @EventHandler(ignoreCancelled = true)
    fun onEntityPickupItem(event: EntityPickupItemEvent) {
        mobService.handleEntityPickupItem(event)
    }

    @EventHandler(ignoreCancelled = true)
    fun onSlimeSplit(event: SlimeSplitEvent) {
        mobService.handleSlimeSplit(event)
    }

    @EventHandler(ignoreCancelled = true)
    fun onEntityTransform(event: EntityTransformEvent) {
        mobService.handleEntityTransform(event)
    }

    @EventHandler(ignoreCancelled = true)
    fun onEntityTeleport(event: EntityTeleportEvent) {
        mobService.handleEntityTeleport(event)
    }
}
