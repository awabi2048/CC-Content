package jp.awabi2048.cccontent.features.rank.event

import jp.awabi2048.cccontent.features.rank.tutorial.TutorialRank
import jp.awabi2048.cccontent.features.rank.profession.Profession
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * チュートリアルランクアップイベント
 */
class TutorialRankUpEvent(
    val player: Player,
    val oldRank: TutorialRank,
    val newRank: TutorialRank,
    val currentExp: Long
) : Event() {
    companion object {
        private val handlers = HandlerList()
        
        @JvmStatic
        fun getHandlerList(): HandlerList = handlers
    }
    
    override fun getHandlers(): HandlerList = handlers
}

/**
 * 職業選択イベント
 */
class ProfessionSelectedEvent(
    val player: Player,
    val profession: Profession
) : Event() {
    companion object {
        private val handlers = HandlerList()
        
        @JvmStatic
        fun getHandlerList(): HandlerList = handlers
    }
    
    override fun getHandlers(): HandlerList = handlers
}

/**
 * 職業変更イベント
 */
class ProfessionChangedEvent(
    val player: Player,
    val oldProfession: Profession,
    val newProfession: Profession
) : Event() {
    companion object {
        private val handlers = HandlerList()
        
        @JvmStatic
        fun getHandlerList(): HandlerList = handlers
    }
    
    override fun getHandlers(): HandlerList = handlers
}

/**
 * スキル習得イベント
 */
class SkillAcquiredEvent(
    val player: Player,
    val profession: Profession,
    val skillId: String,
    val skillName: String
) : Event() {
    companion object {
        private val handlers = HandlerList()
        
        @JvmStatic
        fun getHandlerList(): HandlerList = handlers
    }
    
    override fun getHandlers(): HandlerList = handlers
}

/**
 * 職業経験値獲得イベント
 */
class PlayerExperienceGainEvent(
    val player: Player,
    val profession: Profession,
    val amount: Long,
    val newExp: Long
) : Event() {
    companion object {
        private val handlers = HandlerList()
        
        @JvmStatic
        fun getHandlerList(): HandlerList = handlers
    }
    
    override fun getHandlers(): HandlerList = handlers
}
