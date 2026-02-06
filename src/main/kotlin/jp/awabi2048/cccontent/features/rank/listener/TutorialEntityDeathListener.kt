package jp.awabi2048.cccontent.features.rank.listener

import jp.awabi2048.cccontent.features.rank.RankStorage
import jp.awabi2048.cccontent.features.rank.RankManager
import jp.awabi2048.cccontent.features.rank.tutorial.task.TutorialTaskChecker
import jp.awabi2048.cccontent.features.rank.tutorial.task.TutorialTaskLoader
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent

/**
 * モブまたはボスが倒されたときのリスナー
 * - プレイヤーによる討伐をカウント
 * - タスク完了判定を実行
 * - 自動ランクアップ
 * - データを保存
 */
class TutorialEntityDeathListener(
    private val rankManager: RankManager,
    private val taskChecker: TutorialTaskChecker,
    private val taskLoader: TutorialTaskLoader,
    private val storage: RankStorage
) : Listener {
    
    @EventHandler
    fun onEntityDeath(event: EntityDeathEvent) {
        val killer = event.entity.killer ?: return  // キラーがプレイヤーでない場合は処理しない
        
        val entityType = event.entity.type
        val entityName = entityType.name.lowercase()
        
        val uuid = killer.uniqueId
        val tutorial = rankManager.getPlayerTutorial(uuid)
        
        // ボスか通常モブか判定
        val isBoss = isBossEntity(entityType)
        
        if (isBoss) {
            // ボス討伐数をカウント
            tutorial.taskProgress.addBossKill(entityName)
        } else {
            // 通常モブ討伐数をカウント
            tutorial.taskProgress.addMobKill(entityName)
        }
        
        // タスク完了判定
        val requirement = taskLoader.getRequirement(tutorial.currentRank.name)
        if (taskChecker.isAllTasksComplete(tutorial.taskProgress, requirement, killer)) {
            // すべてのタスクが完了したので、ランクアップ
            if (!tutorial.isMaxRank()) {
                rankManager.rankUpByTask(uuid)
            }
        }
        
        // データを保存
        storage.saveTutorialRank(tutorial)
    }
    
    /**
     * ボスエンティティかどうかを判定
     */
    private fun isBossEntity(entityType: EntityType): Boolean {
        return entityType in listOf(
            EntityType.ENDER_DRAGON,
            EntityType.WITHER,
            EntityType.ELDER_GUARDIAN,
            EntityType.WARDEN
        )
    }
}
