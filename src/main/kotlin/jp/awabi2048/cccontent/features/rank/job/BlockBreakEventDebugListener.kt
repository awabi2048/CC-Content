package jp.awabi2048.cccontent.features.rank.job

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.plugin.java.JavaPlugin

/**
 * BlockBreakEvent が実際に発火しているか確認用の汎用リスナー
 */
class BlockBreakEventDebugListener(private val plugin: JavaPlugin) : Listener {
    @EventHandler
    fun onBlockBreakAny(event: BlockBreakEvent) {
        plugin.logger.info("[DEBUG] BlockBreakEvent fired: ${event.player.name} broke ${event.block.type} (cancelled=${event.isCancelled})")
    }
}
