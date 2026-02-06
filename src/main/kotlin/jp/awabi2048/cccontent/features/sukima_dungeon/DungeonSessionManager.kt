package jp.awabi2048.cccontent.features.sukima_dungeon

import jp.awabi2048.cccontent.items.sukima_dungeon.DungeonTier
import org.bukkit.Location
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * ダンジョンセッション管理マネージャー
 * シングルトン パターンで全セッションを一元管理
 */
object DungeonSessionManager {
    
    // セッション保存（UUID -> Session）
    private val sessions: MutableMap<UUID, DungeonSession> = ConcurrentHashMap()
    
    /**
     * セッションを開始（完全版）
     */
     fun startSession(
         player: Player,
         tier: DungeonTier,
         themeName: String,
         durationSeconds: Int,
         totalSprouts: Int,
         gridWidth: Int,
         gridLength: Int,
         worldName: String,
         minibossMarkers: List<Location> = emptyList(),
         mobSpawnPoints: List<Location> = emptyList(),
         restCells: List<Location> = emptyList()
     ): DungeonSession {
         val session = DungeonSession(
             playerUUID = player.uniqueId,
             tier = tier,
             themeName = themeName,
             durationSeconds = durationSeconds,
             totalSprouts = totalSprouts,
             gridWidth = gridWidth,
             gridLength = gridLength,
             worldName = worldName,
             minibossMarkers = minibossMarkers,
             mobSpawnPoints = mobSpawnPoints,
             restCells = restCells
         )
         
         sessions[player.uniqueId] = session
         return session
     }
     
     /**
      * セッションを開始（簡略版）
      */
     fun startSession(
         player: Player,
         tier: DungeonTier,
         themeName: String,
         sizeName: String,
         durationSeconds: Int,
         totalSprouts: Int,
         worldName: String,
         escapeLocation: Location?
     ): DungeonSession {
         val session = DungeonSession(
             playerUUID = player.uniqueId,
             tier = tier,
             themeName = themeName,
             durationSeconds = durationSeconds,
             totalSprouts = totalSprouts,
             gridWidth = 1,  // ダミー値
             gridLength = 1, // ダミー値
             worldName = worldName,
             escapeLocation = escapeLocation
         )
         
         sessions[player.uniqueId] = session
         return session
     }
    
    /**
     * セッションを終了
     */
    fun endSession(playerUUID: UUID) {
        sessions.remove(playerUUID)
    }
    
    /**
     * プレイヤーのセッションを取得
     */
    fun getSession(player: Player): DungeonSession? {
        return sessions[player.uniqueId]
    }
    
     /**
      * 全セッションを取得（コレクション）
      */
     fun getAllSessionsAsCollection(): Collection<DungeonSession> {
         return sessions.values.toList()
     }
     
     /**
      * 全セッションを取得（マップ）
      */
     fun getAllSessions(): Map<UUID, DungeonSession> {
         return sessions.toMap()
     }
    
    /**
     * すべてのセッションの経過時間を更新
     */
    fun updateAllSessions() {
        for (session in sessions.values) {
            session.updateElapsed()
        }
    }
    
    /**
     * セッション数を取得
     */
    fun getSessionCount(): Int {
        return sessions.size
    }
    
    /**
     * セッションが存在するか確認
     */
    fun hasSession(player: Player): Boolean {
        return sessions.containsKey(player.uniqueId)
    }
    
    /**
     * すべてのセッションをクリア
     */
    fun clearAllSessions() {
        sessions.clear()
    }
    
     /**
      * デバッグ用：セッション情報を取得
      */
     fun getSessionInfo(player: Player): String {
         val session = sessions[player.uniqueId] ?: return "セッションなし"
         
         return """
             === ダンジョンセッション情報 ===
             テーマ: ${session.themeName}
             ティア: ${session.tier}
             ワールド: ${session.worldName}
             進捗: ${session.getProgress()}
             経過: ${session.getElapsedFormatted()}
             残り: ${session.getRemainingFormatted()}
             状態: ${if (session.isCollapsing) "崩壊中" else "進行中"}
         """.trimIndent()
     }
     
     /**
      * セッションが存在するか確認（UUID）
      */
     fun hasSession(playerUUID: UUID): Boolean {
         return sessions.containsKey(playerUUID)
     }
}