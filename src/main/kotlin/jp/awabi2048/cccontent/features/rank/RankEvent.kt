package jp.awabi2048.cccontent.features.rank

import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * プレイヤーのランクが変更されたときに発火するイベント
 */
class PlayerRankChangeEvent(
    /** ランクが変更されたプレイヤー */
    val player: Player,
    
    /** ランクのタイプ */
    val rankType: RankType,
    
    /** 変更前のティア */
    val oldTier: RankTier,
    
    /** 変更後のティア */
    val newTier: RankTier,
    
    /** 現在のスコア */
    val currentScore: Long
) : Event() {
    companion object {
        private val handlers = HandlerList()
        
        @JvmStatic
        fun getHandlerList(): HandlerList = handlers
    }
    
    override fun getHandlers(): HandlerList = handlers
}

/**
 * プレイヤーがランクアップしたときに発火するイベント
 */
class PlayerRankUpEvent(
    /** ランクアップしたプレイヤー */
    val player: Player,
    
    /** ランクのタイプ */
    val rankType: RankType,
    
    /** アップ前のティア */
    val fromTier: RankTier,
    
    /** アップ後のティア */
    val toTier: RankTier,
    
    /** 現在のスコア */
    val currentScore: Long
) : Event() {
    companion object {
        private val handlers = HandlerList()
        
        @JvmStatic
        fun getHandlerList(): HandlerList = handlers
    }
    
    override fun getHandlers(): HandlerList = handlers
}

/**
 * プレイヤーのランクにスコアが追加されたときに発火するイベント
 */
class PlayerScoreAddEvent(
    /** スコアが追加されたプレイヤー */
    val player: Player,
    
    /** ランクのタイプ */
    val rankType: RankType,
    
    /** 追加されたスコア量 */
    val addedScore: Long,
    
    /** 変更前のスコア */
    val oldScore: Long,
    
    /** 変更後のスコア */
    val newScore: Long
) : Event() {
    companion object {
        private val handlers = HandlerList()
        
        @JvmStatic
        fun getHandlerList(): HandlerList = handlers
    }
    
    override fun getHandlers(): HandlerList = handlers
}
