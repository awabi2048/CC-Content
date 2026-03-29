package jp.awabi2048.cccontent.features.arena

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityTargetLivingEntityEvent
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.entity.EntityMountEvent
import org.bukkit.event.player.PlayerAnimationEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerQuitEvent

class ArenaListener(private val arenaManager: ArenaManager) : Listener {

    @EventHandler
    fun onEntityDeath(event: EntityDeathEvent) {
        arenaManager.handleMobDeath(event)
    }

    @EventHandler(ignoreCancelled = true)
    fun onEntityDamage(event: EntityDamageEvent) {
        arenaManager.handleParticipantDamage(event)
        if (event.isCancelled) {
            return
        }
        arenaManager.handleMobFallDamage(event)
    }

    @EventHandler(ignoreCancelled = true)
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        arenaManager.handleParticipantFriendlyFire(event)
        if (event.isCancelled) {
            return
        }
        arenaManager.handleDownedPlayerAttack(event)
        if (event.isCancelled) {
            return
        }
        arenaManager.handleMobFriendlyFire(event)
    }

    @EventHandler(ignoreCancelled = true)
    fun onPlayerAnimation(event: PlayerAnimationEvent) {
        arenaManager.handleMultiplayerInviteSwing(event)
    }

    @EventHandler(ignoreCancelled = true)
    fun onEntityTarget(event: EntityTargetLivingEntityEvent) {
        arenaManager.handleMobTarget(event)
    }

    @EventHandler(ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        arenaManager.handleArenaBlockBreak(event)
    }

    @EventHandler(ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        arenaManager.handleArenaBlockPlace(event)
    }

    @EventHandler(ignoreCancelled = true)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        arenaManager.handleArenaInteract(event)
    }

    @EventHandler(ignoreCancelled = true)
    fun onPlayerInteractEntity(event: PlayerInteractEntityEvent) {
        arenaManager.handleArenaInteractEntity(event)
    }

    @EventHandler(ignoreCancelled = true)
    fun onEntityMount(event: EntityMountEvent) {
        arenaManager.handleArenaEntityMount(event)
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        arenaManager.handleInviteTargetUnavailable(event.player)
        arenaManager.stopSession(
            event.player,
            ArenaI18n.text(event.player, "arena.messages.session.ended_by_logout", "&cログアウトしたためアリーナを終了しました")
        )
    }

    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        if (arenaManager.getSession(event.player) == null) return
        event.deathMessage = null
        arenaManager.notifyParticipantDeath(event.player)
        arenaManager.handleInviteTargetUnavailable(event.player)
        arenaManager.stopSession(
            event.player,
            ArenaI18n.text(event.player, "arena.messages.session.ended_by_death", "&c死亡したためアリーナを終了しました")
        )
    }
}
