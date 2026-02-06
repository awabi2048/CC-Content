package jp.awabi2048.cccontent.features.sukima_dungeon

import jp.awabi2048.cccontent.items.sukima_dungeon.DungeonTier
import org.bukkit.Location
import java.util.UUID

/**
 * ダンジョンセッション
 * プレイヤーがダンジョンに入場してから脱出するまでの情報を管理
 */
data class DungeonSession(
    // 基本情報
    val playerUUID: UUID,
    val tier: DungeonTier,
    val themeName: String,
    val durationSeconds: Int,
    
    // ダンジョン情報
    val totalSprouts: Int,
    val gridWidth: Int,
    val gridLength: Int,
    val worldName: String,
    val escapeLocation: Location? = null, // 脱出地点
    
    // マーカー情報
    val minibossMarkers: List<Location> = emptyList(),
    val mobSpawnPoints: List<Location> = emptyList(),
    val restCells: List<Location> = emptyList(),
    
    // プレイ進捗
    var collectedSprouts: Int = 0,
    var elapsedMillis: Long = 0,
    var lastUpdate: Long = System.currentTimeMillis(),
    
    // 崩壊状態
    var isCollapsing: Boolean = false,
    var collapseRemainingMillis: Long = 0
) {
    /**
     * セッション経過時間を更新
     */
    fun updateElapsed() {
        val delta = System.currentTimeMillis() - lastUpdate
        
        if (isCollapsing) {
            collapseRemainingMillis = (collapseRemainingMillis - delta).coerceAtLeast(0)
        } else {
            elapsedMillis += delta
        }
        
        lastUpdate = System.currentTimeMillis()
    }
    
    /**
     * 残り時間を取得（ミリ秒）
     */
    fun getRemainingTime(): Long {
        return if (isCollapsing) {
            collapseRemainingMillis
        } else {
            (durationSeconds * 1000L - elapsedMillis).coerceAtLeast(0)
        }
    }
    
    /**
     * セッション完了判定（全芽回収）
     */
    fun isCompleted(): Boolean {
        return collectedSprouts >= totalSprouts && !isCollapsing
    }
    
    /**
     * 進捗を文字列で取得（例: "5 / 10 個"）
     */
    fun getProgress(): String {
        return "$collectedSprouts / $totalSprouts 個"
    }
    
    /**
     * 経過時間をMM:SS形式で取得
     */
    fun getElapsedFormatted(): String {
        val seconds = (elapsedMillis / 1000).toInt()
        val minutes = seconds / 60
        val secs = seconds % 60
        return String.format("%02d:%02d", minutes, secs)
    }
    
     /**
      * 残り時間をMM:SS形式で取得
      */
     fun getRemainingFormatted(): String {
         val remaining = getRemainingTime() / 1000
         val minutes = (remaining / 60).toInt()
         val secs = (remaining % 60).toInt()
         return String.format("%02d:%02d", minutes, secs)
     }
     
     /**
      * 経過時間を取得（ミリ秒）
      */
     fun getElapsed(): Long {
         return elapsedMillis
     }
     

 }