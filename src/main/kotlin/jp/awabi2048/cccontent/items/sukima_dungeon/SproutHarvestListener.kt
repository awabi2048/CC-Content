package jp.awabi2048.cccontent.items.sukima_dungeon

import jp.awabi2048.cccontent.items.sukima_dungeon.common.ConfigManager
import jp.awabi2048.cccontent.items.sukima_dungeon.common.DungeonManager
import jp.awabi2048.cccontent.items.sukima_dungeon.common.MessageManager
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.inventory.ItemStack
import kotlin.random.Random

/**
 * ワールドの芽収穫リスナー
 * MANGROVE_PROPAGULE ブロックを破壊した時にワールドの芽アイテムをドロップ
 */
class SproutHarvestListener(
    private val dungeonManager: DungeonManager,
    private val configManager: ConfigManager,
    private val messageManager: MessageManager
) : Listener {
    
    @EventHandler(priority = EventPriority.NORMAL)
    fun onBlockBreak(event: BlockBreakEvent) {
        val player = event.player
        val block = event.block
        
        // MANGROVE_PROPAGULE ブロックか確認
        if (block.type != Material.MANGROVE_PROPAGULE) {
            return
        }
        
        // ダンジョン内か確認
        if (!dungeonManager.isInDungeon(player)) {
            return
        }
        
        // 既に収穫済みか確認
        if (dungeonManager.isHarvested(block.location)) {
            configManager.debug("収穫済みのワールドの芽です")
            return
        }
        
        // ドロップ判定
        val dropChance = configManager.sproutDropChance
        val roll = Random.nextDouble()
        
        if (roll > dropChance) {
            configManager.debug("ワールドの芽のドロップ判定に失敗 (${roll} > ${dropChance})")
            return
        }
        
        // 通常のドロップをキャンセル
        event.isDropItems = false
        
        // ワールドの芽アイテムをドロップ
        val sproutItem = SproutItem().createItem(1)
        block.world.dropItemNaturally(block.location, sproutItem)
        
        // 収穫済みとして記録
        dungeonManager.harvestSprout(block.location)
        
        // メッセージ送信
        messageManager.send(player, "sprout.harvest")
        
        configManager.debug("${player.name} がワールドの芽を収穫しました")
    }
}