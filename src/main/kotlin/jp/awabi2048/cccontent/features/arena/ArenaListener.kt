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
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerTeleportEvent

class ArenaListener(private val arenaManager: ArenaManager) : Listener {

    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        val to = event.to
        if (event.from.blockX == to.blockX && event.from.blockY == to.blockY && event.from.blockZ == to.blockZ) {
            return
        }
        arenaManager.handleMove(event.player, to, event.from)
    }

    @EventHandler
    fun onPlayerTeleport(event: PlayerTeleportEvent) {
        arenaManager.handleTeleport(event.player, event.to)
    }

    @EventHandler
    fun onEntityDeath(event: EntityDeathEvent) {
        arenaManager.handleMobDeath(event.entity.uniqueId)
    }

    @EventHandler
    fun onBarrierClick(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        val clicked = event.clickedBlock?.location ?: return
        if (arenaManager.handleBarrierClick(event.player, clicked)) {
            event.isCancelled = true
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onBarrierBreak(event: BlockBreakEvent) {
        if (!arenaManager.isBarrierBlock(event.block.location)) return
        event.isCancelled = true
        event.player.sendMessage("§c結界石は破壊できません")
    }

    @EventHandler(ignoreCancelled = true)
    fun onBarrierPlace(event: BlockPlaceEvent) {
        if (!arenaManager.isBarrierBlock(event.block.location)) return
        event.isCancelled = true
        event.player.sendMessage("§c結界石の上には設置できません")
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
    fun onPlayerQuit(event: PlayerQuitEvent) {
        arenaManager.stopSession(event.player, "§cログアウトしたためアリーナを終了しました")
    }

    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        arenaManager.stopSession(event.player, "§c死亡したためアリーナを終了しました")
    }
}
