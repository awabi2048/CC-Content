package jp.awabi2048.cccontent.features.arena

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityTargetLivingEntityEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerAnimationEvent
import org.bukkit.event.player.PlayerQuitEvent

class ArenaListener(private val arenaManager: ArenaManager) : Listener {

    @EventHandler
    fun onEntityDeath(event: EntityDeathEvent) {
        arenaManager.handleMobDeath(event)
    }

    @EventHandler(ignoreCancelled = true)
    fun onEntityDamage(event: EntityDamageEvent) {
        arenaManager.handleMobFallDamage(event)
    }

    @EventHandler(ignoreCancelled = true)
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
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
        arenaManager.handleInviteTargetUnavailable(event.player)
        arenaManager.stopSession(
            event.player,
            ArenaI18n.text(event.player, "arena.messages.session.ended_by_death", "&c死亡したためアリーナを終了しました")
        )
    }
}
