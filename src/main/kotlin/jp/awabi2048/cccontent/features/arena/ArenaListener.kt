package jp.awabi2048.cccontent.features.arena

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityTargetLivingEntityEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerInteractEvent
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
    fun onEntityTarget(event: EntityTargetLivingEntityEvent) {
        arenaManager.handleMobTarget(event)
    }

    @EventHandler
    fun onBarrierClick(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        val clicked = event.clickedBlock?.location ?: return
        if (arenaManager.handleDoorClick(event.player, clicked)) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        arenaManager.stopSession(
            event.player,
            ArenaI18n.text(event.player, "arena.messages.session.ended_by_logout", "&cログアウトしたためアリーナを終了しました")
        )
    }

    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        arenaManager.stopSession(
            event.player,
            ArenaI18n.text(event.player, "arena.messages.session.ended_by_death", "&c死亡したためアリーナを終了しました")
        )
    }
}
