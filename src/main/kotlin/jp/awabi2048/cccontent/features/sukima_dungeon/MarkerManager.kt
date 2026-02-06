package jp.awabi2048.cccontent.features.sukima_dungeon

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.plugin.java.JavaPlugin

/**
 * マーカー管理クラス
 * ダンジョン内のマーカーブロックを検出・処理
 */
class MarkerManager(private val plugin: JavaPlugin) {
    
    // マーカーの種類定義
    enum class MarkerType {
        MINIBOSS,      // ボスエリア
        MOB_SPAWN,     // モブスポーン地点
        REST_AREA,     // 休憩エリア
        TREASURE,      // 宝箱
        TRAP,          // トラップ
        UNKNOWN        // 不明
    }
    
    /**
     * 指定された範囲内のマーカーを検索
     * @param world ワールド
     * @param center 中心位置
     * @param radiusXZ 水平検索範囲
     * @param radiusY 垂直検索範囲
     * @return マーカー位置 -> マーカータイプ のマップ
     */
    fun findMarkers(
        world: World,
        center: Location,
        radiusXZ: Double = 32.0,
        radiusY: Double = 20.0
    ): Map<Location, MarkerType> {
        val markers = mutableMapOf<Location, MarkerType>()
        
        val minX = (center.x - radiusXZ).toInt()
        val maxX = (center.x + radiusXZ).toInt()
        val minY = (center.y - radiusY).toInt()
        val maxY = (center.y + radiusY).toInt()
        val minZ = (center.z - radiusXZ).toInt()
        val maxZ = (center.z + radiusXZ).toInt()
        
        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) {
                    val block = world.getBlockAt(x, y, z)
                    val markerType = detectMarker(block)
                    
                    if (markerType != MarkerType.UNKNOWN) {
                        markers[block.location] = markerType
                    }
                }
            }
        }
        
        return markers
    }
    
    /**
     * ブロックからマーカータイプを検出
     * @param block ブロック
     * @return マーカータイプ
     */
    private fun detectMarker(block: Block): MarkerType {
        return when (block.type) {
            // ボスエリア: 赤いブロック
            Material.RED_CONCRETE,
            Material.RED_CONCRETE_POWDER,
            Material.REDSTONE_BLOCK -> MarkerType.MINIBOSS
            
            // モブスポーン地点: 黄色いブロック
            Material.YELLOW_CONCRETE,
            Material.YELLOW_CONCRETE_POWDER -> MarkerType.MOB_SPAWN
            
            // 休憩エリア: 緑のブロック
            Material.LIME_CONCRETE,
            Material.LIME_CONCRETE_POWDER -> MarkerType.REST_AREA
            
            // 宝箱: オレンジブロック
            Material.ORANGE_CONCRETE,
            Material.ORANGE_CONCRETE_POWDER -> MarkerType.TREASURE
            
            // トラップ: 紫ブロック
            Material.PURPLE_CONCRETE,
            Material.PURPLE_CONCRETE_POWDER -> MarkerType.TRAP
            
            else -> MarkerType.UNKNOWN
        }
    }
    
    /**
     * マーカーを処理（カスタム処理）
     * @param markerLocation マーカー位置
     * @param markerType マーカータイプ
     * @return 処理成功したか
     */
    fun processMarker(markerLocation: Location, markerType: MarkerType): Boolean {
        return try {
            val block = markerLocation.block
            
            when (markerType) {
                MarkerType.MINIBOSS -> {
                    // ボスエリア処理
                    plugin.logger.info("[SukimaDungeon] ボスエリア検出: ${markerLocation.x}, ${markerLocation.y}, ${markerLocation.z}")
                    true
                }
                MarkerType.MOB_SPAWN -> {
                    // モブスポーン処理
                    plugin.logger.info("[SukimaDungeon] モブスポーン地点検出: ${markerLocation.x}, ${markerLocation.y}, ${markerLocation.z}")
                    true
                }
                MarkerType.REST_AREA -> {
                    // 休憩エリア処理
                    plugin.logger.info("[SukimaDungeon] 休憩エリア検出: ${markerLocation.x}, ${markerLocation.y}, ${markerLocation.z}")
                    true
                }
                MarkerType.TREASURE -> {
                    // 宝箱処理
                    plugin.logger.info("[SukimaDungeon] 宝箱検出: ${markerLocation.x}, ${markerLocation.y}, ${markerLocation.z}")
                    true
                }
                MarkerType.TRAP -> {
                    // トラップ処理
                    plugin.logger.info("[SukimaDungeon] トラップ検出: ${markerLocation.x}, ${markerLocation.y}, ${markerLocation.z}")
                    true
                }
                MarkerType.UNKNOWN -> false
            }
        } catch (e: Exception) {
            plugin.logger.warning("[SukimaDungeon] マーカー処理中にエラー: ${e.message}")
            false
        }
    }
    
    /**
     * マーカーブロックを削除
     * @param location マーカー位置
     */
    fun removeMarker(location: Location) {
        try {
            location.block.type = Material.AIR
            plugin.logger.info("[SukimaDungeon] マーカーを削除しました: ${location.x}, ${location.y}, ${location.z}")
        } catch (e: Exception) {
            plugin.logger.warning("[SukimaDungeon] マーカー削除中にエラー: ${e.message}")
        }
    }
    
    /**
     * マーカータイプの表示名を取得
     * @param markerType マーカータイプ
     * @return 表示名
     */
    fun getMarkerTypeName(markerType: MarkerType): String {
        return when (markerType) {
            MarkerType.MINIBOSS -> "ボスエリア"
            MarkerType.MOB_SPAWN -> "モブスポーン地点"
            MarkerType.REST_AREA -> "休憩エリア"
            MarkerType.TREASURE -> "宝箱"
            MarkerType.TRAP -> "トラップ"
            MarkerType.UNKNOWN -> "不明"
        }
    }
}
