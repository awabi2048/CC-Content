package jp.awabi2048.cccontent.features.rank

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

/**
 * ランクシステムのイベントリスナーの基本インターフェース
 * プラグインはこのインターフェースを実装してランクイベントをリッスンできる
 */
interface RankListener : Listener {
    
    /**
     * プレイヤーのスコアが追加されたときの処理
     * @param event スコア追加イベント
     */
    @EventHandler
    fun onScoreAdd(event: PlayerScoreAddEvent)
    
    /**
     * プレイヤーのランクが変更されたときの処理
     * @param event ランク変更イベント
     */
    @EventHandler
    fun onRankChange(event: PlayerRankChangeEvent)
    
    /**
     * プレイヤーがランクアップしたときの処理
     * @param event ランクアップイベント
     */
    @EventHandler
    fun onRankUp(event: PlayerRankUpEvent)
}
