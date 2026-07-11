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
            // ログイン時間や探索実績はチュートリアル全体の累計として扱うため、ランクIDだけを更新して進捗を引き継ぐ。
            taskProgress = taskProgress.copy(rankId = nextRank.name)
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
