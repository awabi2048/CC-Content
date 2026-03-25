package jp.awabi2048.cccontent.features.arena

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent

class ArenaListener(private val arenaManager: ArenaManager) : Listener {

    @EventHandler
    fun onEntityDeath(event: EntityDeathEvent) {
        arenaManager.handleMobDeath(event)
    }

    @EventHandler
    fun onBarrierClick(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        val clicked = event.clickedBlock?.location ?: return
        if (arenaManager.handleDoorClick(event.player, clicked)) {
            event.isCancelled = true
            return
        }
        if (arenaManager.handleBarrierClick(event.player, clicked)) {
            event.isCancelled = true
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onBarrierBreak(event: BlockBreakEvent) {
        if (!arenaManager.isBarrierBlock(event.block.location)) return
        event.isCancelled = true
        event.player.sendMessage(ArenaI18n.text(event.player, "arena.messages.barrier.cannot_break", "&c結界石は破壊できません"))
    }

    @EventHandler(ignoreCancelled = true)
    fun onBarrierPlace(event: BlockPlaceEvent) {
        if (!arenaManager.isBarrierBlock(event.block.location)) return
        event.isCancelled = true
        event.player.sendMessage(ArenaI18n.text(event.player, "arena.messages.barrier.cannot_place_on", "&c結界石の上には設置できません"))
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityExplode(event: EntityExplodeEvent) {
        event.blockList().removeIf { block -> arenaManager.isBarrierBlock(block.location) }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockExplode(event: BlockExplodeEvent) {
        event.blockList().removeIf { block -> arenaManager.isBarrierBlock(block.location) }
    }

    @EventHandler
    fun onBarrierMenuClick(event: InventoryClickEvent) {
        val topInventory = event.view.topInventory
        if (!arenaManager.handleBarrierRestartMenuClick(event.whoClicked as? org.bukkit.entity.Player ?: return, topInventory, event.rawSlot)) return
        event.isCancelled = true
    }

    @EventHandler
    fun onBarrierMenuDrag(event: InventoryDragEvent) {
        if (!arenaManager.handleBarrierRestartMenuDrag(event.view.topInventory)) return
        event.isCancelled = true
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
