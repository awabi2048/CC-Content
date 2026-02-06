package jp.awabi2048.cccontent.features.sukima_dungeon

import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.*

/**
 * ポータル管理クラス
 * ダンジョン内のポータルエンティティを管理
 */
class PortalManager(private val plugin: JavaPlugin) {
    
    // ポータル位置 -> エンティティ ID
    private val portals: MutableMap<Location, UUID> = mutableMapOf()
    
    // プレイヤー -> ポータルエンティティ
    private val playerPortals: MutableMap<UUID, UUID> = mutableMapOf()
    
    /**
     * ポータルを作成
     * @param location ポータルの位置
     * @return 作成されたポータルエンティティの UUID
     */
    fun createPortal(location: Location): UUID? {
        try {
            // NETHER_PORTAL ブロックで装飾（見た目用）
            // 実際のエンティティは EndCrystal を使用
            val portal = location.world?.spawnEntity(location, EntityType.END_CRYSTAL) ?: return null
            
            // ポータルエンティティを記録
            portals[location] = portal.uniqueId
            
            plugin.logger.info("[SukimaDungeon] ポータルを作成しました: ${location.x}, ${location.y}, ${location.z}")
            return portal.uniqueId
        } catch (e: Exception) {
            plugin.logger.warning("[SukimaDungeon] ポータル作成中にエラー: ${e.message}")
            return null
        }
    }
    
    /**
     * ポータルを削除
     * @param location ポータルの位置
     */
    fun removePortal(location: Location) {
        val portalId = portals[location] ?: return
        
        try {
            // エンティティを取得して削除
            for (entity in location.world?.entities ?: emptyList()) {
                if (entity.uniqueId == portalId) {
                    entity.remove()
                    break
                }
            }
            
            portals.remove(location)
            plugin.logger.info("[SukimaDungeon] ポータルを削除しました: ${location.x}, ${location.y}, ${location.z}")
        } catch (e: Exception) {
            plugin.logger.warning("[SukimaDungeon] ポータル削除中にエラー: ${e.message}")
        }
    }
    
    /**
     * 指定された場所に近いポータルを検索
     * @param location 検索位置
     * @param radius 検索半径
     * @return 見つかったポータル位置、見つからない場合は null
     */
    fun findNearestPortal(location: Location, radius: Double = 10.0): Location? {
        var nearest: Location? = null
        var nearestDistance = Double.MAX_VALUE
        
        for (portalLocation in portals.keys) {
            if (portalLocation.world != location.world) continue
            
            val distance = portalLocation.distance(location)
            if (distance < radius && distance < nearestDistance) {
                nearest = portalLocation
                nearestDistance = distance
            }
        }
        
        return nearest
    }
    
    /**
     * プレイヤーとポータルを関連付ける
     * @param player プレイヤー
     * @param portalLocation ポータルの位置
     */
    fun linkPlayerToPortal(player: Player, portalLocation: Location) {
        val portalId = portals[portalLocation]
        if (portalId != null) {
            playerPortals[player.uniqueId] = portalId
        }
    }
    
    /**
     * プレイヤーのポータル関連付けを解除
     * @param player プレイヤー
     */
    fun unlinkPlayer(player: Player) {
        playerPortals.remove(player.uniqueId)
    }
    
    /**
     * プレイヤーのポータルを取得
     * @param player プレイヤー
     * @return ポータルエンティティ、見つからない場合は null
     */
    fun getPlayerPortal(player: Player): Entity? {
        val portalId = playerPortals[player.uniqueId] ?: return null
        
        return player.world.entities.find { it.uniqueId == portalId }
    }
    
    /**
     * 全ポータルを削除
     */
    fun removeAllPortals() {
        for (location in portals.keys.toList()) {
            removePortal(location)
        }
    }
    
    /**
     * ポータル数を取得
     */
    fun getPortalCount(): Int {
        return portals.size
    }
    
    /**
     * クリーンアップ
     */
    fun cleanup() {
        removeAllPortals()
        playerPortals.clear()
    }
}
