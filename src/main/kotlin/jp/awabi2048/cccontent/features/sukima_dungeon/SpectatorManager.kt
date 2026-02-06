package jp.awabi2048.cccontent.features.sukima_dungeon

import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.*

/**
 * スペクテーター管理クラス
 * ダウン・死亡プレイヤーをスペクテーターモードで観戦
 */
class SpectatorManager(private val plugin: JavaPlugin) {
    
    // スペクテーター中のプレイヤー（UUID -> 元のGameMode）
    private val spectators: MutableMap<UUID, GameMode> = mutableMapOf()
    
    /**
     * プレイヤーをスペクテーターモードに変更
     * @param player プレイヤー
     */
    fun setSpectator(player: Player) {
        if (spectators.containsKey(player.uniqueId)) {
            return  // 既にスペクテーター中
        }
        
        // 元のGameModeを保存
        spectators[player.uniqueId] = player.gameMode
        
        // スペクテーターモードに変更
        player.gameMode = GameMode.SPECTATOR
        
        plugin.logger.info("[SukimaDungeon] ${player.name} がスペクテーターモードになりました")
    }
    
    /**
     * プレイヤーをスペクテーターモードから戻す
     * @param player プレイヤー
     */
    fun removeSpectator(player: Player) {
        val originalGameMode = spectators.remove(player.uniqueId) ?: return
        
        // 元のGameModeに戻す
        player.gameMode = originalGameMode
        
        plugin.logger.info("[SukimaDungeon] ${player.name} がスペクテーターモードを終了しました")
    }
    
    /**
     * プレイヤーがスペクテーター中か確認
     * @param player プレイヤー
     * @return スペクテーター中か
     */
    fun isSpectator(player: Player): Boolean {
        return spectators.containsKey(player.uniqueId)
    }
    
    /**
     * 全スペクテーターを取得
     * @return スペクテーター中のプレイヤーUUIDのリスト
     */
    fun getAllSpectators(): List<UUID> {
        return spectators.keys.toList()
    }
    
    /**
     * スペクテーター数を取得
     * @return スペクテーター数
     */
    fun getSpectatorCount(): Int {
        return spectators.size
    }
    
    /**
     * 全スペクテーターを通常モードに戻す
     */
    fun restoreAllSpectators() {
        for ((playerUUID, originalGameMode) in spectators.toList()) {
            val player = org.bukkit.Bukkit.getPlayer(playerUUID) ?: continue
            
            player.gameMode = originalGameMode
            spectators.remove(playerUUID)
            
            plugin.logger.info("[SukimaDungeon] ${player.name} がスペクテーターモードを終了しました")
        }
    }
    
    /**
     * クリーンアップ
     */
    fun cleanup() {
        restoreAllSpectators()
        spectators.clear()
    }
}
