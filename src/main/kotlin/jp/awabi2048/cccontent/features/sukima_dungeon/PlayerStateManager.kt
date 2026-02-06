package jp.awabi2048.cccontent.features.sukima_dungeon

import org.bukkit.entity.Player
import java.util.*

/**
 * プレイヤー状態管理クラス
 * マルチプレイ時のプレイヤーの状態（ダウン、復活等）を管理
 */
class PlayerStateManager {
    
    /**
     * プレイヤー状態の定義
     */
    enum class PlayerState {
        ACTIVE,         // 通常状態（アクティブ）
        DOWN,          // ダウン状態
        RECOVERING,    // 復活中
        DEAD          // 死亡
    }
    
    /**
     * プレイヤー状態データクラス
     */
    data class PlayerStatus(
        val playerUUID: UUID,
        var state: PlayerState = PlayerState.ACTIVE,
        var downTime: Long = 0L,  // ダウンした時刻
        var recoveryTime: Long = 0L,  // 復活に必要な時間（ミリ秒）
        var health: Double = 20.0,
        var deathCount: Int = 0  // 死亡回数
    )
    
    // プレイヤー状態の保存
    private val playerStates: MutableMap<UUID, PlayerStatus> = mutableMapOf()
    
    // セッション内のプレイヤー（UUID -> プレイヤー名）
    private val sessionPlayers: MutableMap<UUID, String> = mutableMapOf()
    
    /**
     * プレイヤーをセッションに追加
     * @param player プレイヤー
     * @param recoveryTime 復活に必要な時間（秒）
     */
    fun addPlayer(player: Player, recoveryTime: Int = 30) {
        val status = PlayerStatus(
            playerUUID = player.uniqueId,
            recoveryTime = (recoveryTime * 1000L),  // 秒からミリ秒に変換
            health = player.health
        )
        
        playerStates[player.uniqueId] = status
        sessionPlayers[player.uniqueId] = player.name
    }
    
    /**
     * プレイヤーをセッションから削除
     * @param player プレイヤー
     */
    fun removePlayer(player: Player) {
        playerStates.remove(player.uniqueId)
        sessionPlayers.remove(player.uniqueId)
    }
    
    /**
     * プレイヤーをダウン状態にする
     * @param player プレイヤー
     */
    fun setPlayerDown(player: Player) {
        val status = playerStates[player.uniqueId] ?: return
        status.state = PlayerState.DOWN
        status.downTime = System.currentTimeMillis()
    }
    
    /**
     * プレイヤーを復活させる
     * @param player プレイヤー
     */
    fun recoverPlayer(player: Player) {
        val status = playerStates[player.uniqueId] ?: return
        status.state = PlayerState.RECOVERING
        
        // 復活時間後に通常状態に戻す
        // （実装側で非同期処理が必要な場合は、別途タスクで処理）
    }
    
    /**
     * プレイヤーを復活完了状態にする
     * @param player プレイヤー
     */
    fun completeRecovery(player: Player) {
        val status = playerStates[player.uniqueId] ?: return
        status.state = PlayerState.ACTIVE
    }
    
    /**
     * プレイヤーを死亡させる
     * @param player プレイヤー
     */
    fun setPlayerDead(player: Player) {
        val status = playerStates[player.uniqueId] ?: return
        status.state = PlayerState.DEAD
        status.deathCount++
    }
    
    /**
     * プレイヤーの状態を取得
     * @param player プレイヤー
     * @return プレイヤー状態
     */
    fun getPlayerState(player: Player): PlayerState {
        return playerStates[player.uniqueId]?.state ?: PlayerState.ACTIVE
    }
    
    /**
     * プレイヤーがダウン状態か確認
     * @param player プレイヤー
     * @return ダウン状態か
     */
    fun isPlayerDown(player: Player): Boolean {
        return getPlayerState(player) == PlayerState.DOWN
    }
    
    /**
     * プレイヤーが復活中か確認
     * @param player プレイヤー
     * @return 復活中か
     */
    fun isPlayerRecovering(player: Player): Boolean {
        return getPlayerState(player) == PlayerState.RECOVERING
    }
    
    /**
     * プレイヤーが死亡しているか確認
     * @param player プレイヤー
     * @return 死亡しているか
     */
    fun isPlayerDead(player: Player): Boolean {
        return getPlayerState(player) == PlayerState.DEAD
    }
    
    /**
     * ダウン時間を取得（ミリ秒）
     * @param player プレイヤー
     * @return ダウン時間、ダウンしていない場合は 0
     */
    fun getDownTime(player: Player): Long {
        val status = playerStates[player.uniqueId] ?: return 0
        if (status.state != PlayerState.DOWN) return 0
        
        return System.currentTimeMillis() - status.downTime
    }
    
    /**
     * 復活に必要な残り時間を取得（ミリ秒）
     * @param player プレイヤー
     * @return 残り時間、復活不要な場合は 0
     */
    fun getRecoveryTimeRemaining(player: Player): Long {
        val status = playerStates[player.uniqueId] ?: return 0
        val downTime = getDownTime(player)
        val remaining = status.recoveryTime - downTime
        
        return remaining.coerceAtLeast(0)
    }
    
    /**
     * 全プレイヤーの状態を取得
     * @return プレイヤーUUID -> プレイヤー状態 のマップ
     */
    fun getAllPlayerStates(): Map<UUID, PlayerStatus> {
        return playerStates.toMap()
    }
    
    /**
     * セッション内のプレイヤー数を取得
     * @return プレイヤー数
     */
    fun getPlayerCount(): Int {
        return playerStates.size
    }
    
    /**
     * アクティブなプレイヤー数を取得
     * @return アクティブなプレイヤー数
     */
    fun getActivePlayerCount(): Int {
        return playerStates.values.count { it.state == PlayerState.ACTIVE }
    }
    
    /**
     * ダウン中のプレイヤー数を取得
     * @return ダウン中のプレイヤー数
     */
    fun getDownPlayerCount(): Int {
        return playerStates.values.count { it.state == PlayerState.DOWN }
    }
    
    /**
     * 死亡プレイヤー数を取得
     * @return 死亡プレイヤー数
     */
    fun getDeadPlayerCount(): Int {
        return playerStates.values.count { it.state == PlayerState.DEAD }
    }
    
    /**
     * クリーンアップ
     */
    fun cleanup() {
        playerStates.clear()
        sessionPlayers.clear()
    }
}
