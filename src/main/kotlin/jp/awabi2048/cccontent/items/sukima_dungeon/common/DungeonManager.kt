package jp.awabi2048.cccontent.items.sukima_dungeon.common

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

/**
 * SukimaDungeon ダンジョン管理クラス
 * ダンジョン判定、ワールドの芽管理を担当
 */
class DungeonManager(
    private val plugin: JavaPlugin,
    private val configManager: ConfigManager
) {
    // ワールドの芽の位置を記録
    private val sproutLocations: MutableSet<Location> = mutableSetOf()
    
    // 収穫済みの芽とリスポーン時刻を記録
    private val harvestedSprouts: MutableMap<Location, Long> = mutableMapOf()
    
    // プレイヤーのダンジョン入場記録
    private val playersInDungeon: MutableSet<UUID> = mutableSetOf()
    
    /**
     * プレイヤーがダンジョン内にいるかを判定
     */
    fun isInDungeon(player: Player): Boolean {
        val worldName = player.world.name
        return configManager.dungeonWorlds.contains(worldName)
    }
    
    /**
     * 指定された位置がダンジョン内にあるかを判定
     */
    fun isInDungeon(location: Location): Boolean {
        val worldName = location.world?.name ?: return false
        return configManager.dungeonWorlds.contains(worldName)
    }
    
    /**
     * プレイヤーをダンジョン入場記録に追加
     */
    fun addPlayerToDungeon(player: Player) {
        playersInDungeon.add(player.uniqueId)
        configManager.debug("プレイヤー ${player.name} がダンジョンに入場しました")
    }
    
    /**
     * プレイヤーをダンジョン入場記録から削除
     */
    fun removePlayerFromDungeon(player: Player) {
        playersInDungeon.remove(player.uniqueId)
        configManager.debug("プレイヤー ${player.name} がダンジョンから退出しました")
    }
    
    /**
     * ワールドの芽を登録
     */
    fun registerSprout(location: Location) {
        sproutLocations.add(location)
        configManager.debug("ワールドの芽を登録: ${location.blockX}, ${location.blockY}, ${location.blockZ}")
    }
    
    /**
     * ワールドの芽を削除
     */
    fun unregisterSprout(location: Location) {
        sproutLocations.remove(location)
        configManager.debug("ワールドの芽を削除: ${location.blockX}, ${location.blockY}, ${location.blockZ}")
    }
    
    /**
     * 指定位置にワールドの芽があるかを確認
     */
    fun isSprout(location: Location): Boolean {
        return sproutLocations.contains(location) || 
               location.block.type == Material.MANGROVE_PROPAGULE
    }
    
    /**
     * プレイヤーの近くにあるワールドの芽を検索
     */
    fun findNearestSprout(player: Player, radius: Double): Location? {
        val playerLoc = player.location
        
        if (!isInDungeon(player)) {
            return null
        }
        
        var nearest: Location? = null
        var minDistance = Double.MAX_VALUE
        
        // 登録された芽を検索
        for (sproutLoc in sproutLocations) {
            if (sproutLoc.world != playerLoc.world) continue
            
            val distance = sproutLoc.distance(playerLoc)
            if (distance <= radius && distance < minDistance) {
                // 収穫済みでないか確認
                if (!isHarvested(sproutLoc)) {
                    nearest = sproutLoc
                    minDistance = distance
                }
            }
        }
        
        // 登録されていない芽もブロックスキャンで検索（パフォーマンス考慮）
        if (nearest == null) {
            nearest = scanForSprouts(playerLoc, radius)
        }
        
        return nearest
    }
    
    /**
     * ブロックスキャンでワールドの芽を検索
     */
    private fun scanForSprouts(center: Location, radius: Double): Location? {
        val world = center.world ?: return null
        val radiusInt = radius.toInt()
        
        var nearest: Location? = null
        var minDistance = Double.MAX_VALUE
        
        for (x in -radiusInt..radiusInt) {
            for (y in -radiusInt..radiusInt) {
                for (z in -radiusInt..radiusInt) {
                    val checkLoc = center.clone().add(x.toDouble(), y.toDouble(), z.toDouble())
                    
                    if (checkLoc.block.type == Material.MANGROVE_PROPAGULE) {
                        val distance = checkLoc.distance(center)
                        if (distance <= radius && distance < minDistance) {
                            if (!isHarvested(checkLoc)) {
                                nearest = checkLoc
                                minDistance = distance
                                
                                // 見つけた芽を登録
                                registerSprout(checkLoc)
                            }
                        }
                    }
                }
            }
        }
        
        return nearest
    }
    
    /**
     * ワールドの芽を収穫済みにする
     */
    fun harvestSprout(location: Location) {
        val respawnTime = configManager.sproutRespawnTime * 1000L // 秒→ミリ秒
        val respawnAt = System.currentTimeMillis() + respawnTime
        
        harvestedSprouts[location] = respawnAt
        configManager.debug("ワールドの芽を収穫: ${location.blockX}, ${location.blockY}, ${location.blockZ} (${configManager.sproutRespawnTime}秒後にリスポーン)")
    }
    
    /**
     * 指定位置の芽が収穫済みかを確認
     */
    fun isHarvested(location: Location): Boolean {
        val respawnAt = harvestedSprouts[location] ?: return false
        
        // リスポーン時刻を過ぎていたら収穫済み状態を解除
        if (System.currentTimeMillis() >= respawnAt) {
            harvestedSprouts.remove(location)
            return false
        }
        
        return true
    }
    
    /**
     * すべてのワールドの芽をスキャンして登録
     */
    fun scanAllSprouts() {
        configManager.debug("全ワールドの芽をスキャン開始")
        var count = 0
        
        for (worldName in configManager.dungeonWorlds) {
            val world = plugin.server.getWorld(worldName)
            if (world != null) {
                // ワールド内のすべてのチャンクをスキャン（パフォーマンス注意）
                // 実際の実装では、事前定義された領域のみをスキャンすべき
                configManager.debug("ワールド $worldName をスキャン中...")
            }
        }
        
        configManager.debug("スキャン完了: ${count}個のワールドの芽を登録しました")
    }
    
    /**
     * 定期的なクリーンアップ処理
     */
    fun cleanup() {
        // 期限切れの収穫記録を削除
        val now = System.currentTimeMillis()
        harvestedSprouts.entries.removeIf { it.value <= now }
    }
    
    /**
     * すべてのデータをクリア
     */
    fun clearAll() {
        sproutLocations.clear()
        harvestedSprouts.clear()
        playersInDungeon.clear()
    }
}