package jp.awabi2048.cccontent.features.rank.tutorial

import jp.awabi2048.cccontent.features.rank.tutorial.task.TaskProgress
import java.util.UUID

/**
 * プレイヤーのチュートリアルランク情報
 */
data class PlayerTutorialRank(
    /** プレイヤーのUUID */
    val playerUuid: UUID,
    
    /** 現在のランク */
    var currentRank: TutorialRank = TutorialRank.NEWBIE,
    
    /** タスク進捗 */
    var taskProgress: TaskProgress = TaskProgress(playerUuid, currentRank.name),
    
    /** 最後の更新日時 */
    var lastUpdated: Long = System.currentTimeMillis(),
    
    /** サーバーにログインした時刻（プレイ時間計算用） */
    var lastPlayTime: Long = System.currentTimeMillis()
) {
    /**
     * ランクアップを実行
     * @return ランクアップに成功した場合true
     */
    fun rankUp(): Boolean {
        if (currentRank == TutorialRank.ATTAINER) {
            return false  // 最終ランクではランクアップしない
        }
        
        val nextRank = TutorialRank.values().getOrNull(currentRank.ordinal + 1)
        if (nextRank != null) {
            currentRank = nextRank
            taskProgress = TaskProgress(playerUuid, nextRank.name)  // 新しいランクのタスク進捗を初期化
            lastUpdated = System.currentTimeMillis()
            return true
        }
        return false
    }
    
    /**
     * 次のランクを取得
     */
    fun getNextRank(): TutorialRank? {
        return TutorialRank.values().getOrNull(currentRank.ordinal + 1)
    }
    
    /**
     * 最終ランクに到達しているか
     */
    fun isMaxRank(): Boolean {
        return currentRank == TutorialRank.ATTAINER
    }
}
