package jp.awabi2048.cccontent.features.sukima_dungeon

import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.*

/**
 * 実績管理クラス
 * ダンジョン内の実績・トロフィーシステムを管理
 */
class AchievementManager(private val plugin: JavaPlugin) {
    
    /**
     * 実績定義
     */
    enum class Achievement(
        val displayName: String,
        val description: String,
        val points: Int = 10  // 実績ポイント
    ) {
        FIRST_DUNGEON("初めてのダンジョン", "ダンジョンに初めて進入した"),
        SPEEDRUNNER("速走者", "5分以内にダンジョンをクリアした", 20),
        ALL_SPROUTS("全て回収", "全てのワールドの芽を回収した", 30),
        BOSS_SLAYER("ボス討伐", "ボスをエリアでクリアした", 25),
        NO_DAMAGE("無傷", "ダメージを受けずにクリアした", 50),
        TREASURE_HUNTER("宝探し", "宝箱を10個見つけた", 15),
        PERFECT_CLEAR("完全クリア", "全ての敵を倒してクリアした", 40),
        DEATH_DEFIER("死神の拒否", "瀕死状態から生き延びた", 20),
        ACHIEVEMENT_HUNTER("実績ハンター", "10個の実績を解除した", 100),
        LEGENDARY_HERO("伝説の勇者", "全ての実績を解除した", 500)
    }
    
    /**
     * プレイヤーの実績進捗
     */
    data class PlayerAchievements(
        val playerUUID: UUID,
        val achievements: MutableSet<Achievement> = mutableSetOf(),
        val totalPoints: Int = 0,
        val completionDate: MutableMap<Achievement, Long> = mutableMapOf()
    )
    
    // プレイヤーの実績（UUID -> 実績データ）
    private val playerAchievements: MutableMap<UUID, PlayerAchievements> = mutableMapOf()
    
    /**
     * プレイヤーが実績を解除
     * @param player プレイヤー
     * @param achievement 実績
     * @return 新たに解除したか
     */
    fun unlockAchievement(player: Player, achievement: Achievement): Boolean {
        val playerData = playerAchievements.getOrPut(player.uniqueId) {
            PlayerAchievements(player.uniqueId)
        }
        
        // 既に解除済みかチェック
        if (playerData.achievements.contains(achievement)) {
            return false
        }
        
        // 実績を解除
        playerData.achievements.add(achievement)
        playerData.completionDate[achievement] = System.currentTimeMillis()
        
        plugin.logger.info("[SukimaDungeon] ${player.name} が実績『${achievement.displayName}』を解除しました")
        
        // 関連実績をチェック
        checkRelatedAchievements(player, playerData)
        
        return true
    }
    
    /**
     * 関連実績をチェック
     */
    private fun checkRelatedAchievements(player: Player, playerData: PlayerAchievements) {
        // ACHIEVEMENT_HUNTER: 10個の実績を解除したか
        if (playerData.achievements.size >= 10) {
            unlockAchievement(player, Achievement.ACHIEVEMENT_HUNTER)
        }
        
        // LEGENDARY_HERO: 全ての実績を解除したか
        if (playerData.achievements.size == Achievement.values().size) {
            unlockAchievement(player, Achievement.LEGENDARY_HERO)
        }
    }
    
    /**
     * プレイヤーが実績を持っているか確認
     * @param player プレイヤー
     * @param achievement 実績
     * @return 持っているか
     */
    fun hasAchievement(player: Player, achievement: Achievement): Boolean {
        return playerAchievements[player.uniqueId]?.achievements?.contains(achievement) ?: false
    }
    
    /**
     * プレイヤーの解除した実績を取得
     * @param player プレイヤー
     * @return 解除した実績のセット
     */
    fun getUnlockedAchievements(player: Player): Set<Achievement> {
        return playerAchievements[player.uniqueId]?.achievements?.toSet() ?: emptySet()
    }
    
    /**
     * プレイヤーの実績ポイント合計を取得
     * @param player プレイヤー
     * @return ポイント合計
     */
    fun getTotalPoints(player: Player): Int {
        val playerData = playerAchievements[player.uniqueId] ?: return 0
        return playerData.achievements.sumOf { it.points }
    }
    
    /**
     * プレイヤーの実績進捗度（パーセント）を取得
     * @param player プレイヤー
     * @return 進捗度（0～100）
     */
    fun getCompletionPercentage(player: Player): Int {
        val playerData = playerAchievements[player.uniqueId] ?: return 0
        val totalAchievements = Achievement.values().size
        
        return if (totalAchievements > 0) {
            (playerData.achievements.size * 100) / totalAchievements
        } else {
            0
        }
    }
    
    /**
     * 実績の詳細情報を取得
     * @param achievement 実績
     * @return 詳細テキスト
     */
    fun getAchievementInfo(achievement: Achievement): String {
        return """
            ${achievement.displayName} (${achievement.points}pt)
            ${achievement.description}
        """.trimIndent()
    }
    
    /**
     * 全実績を取得
     * @return 実績のリスト
     */
    fun getAllAchievements(): List<Achievement> {
        return Achievement.values().toList()
    }
    
    /**
     * プレイヤーのプロフィールを取得
     * @param player プレイヤー
     * @return プロフィール情報
     */
    fun getProfileInfo(player: Player): String {
        val playerData = playerAchievements[player.uniqueId]
        
        return if (playerData != null) {
            """
                === 実績プロフィール ===
                プレイヤー: ${player.name}
                解除実績: ${playerData.achievements.size}/${Achievement.values().size}
                総ポイント: ${getTotalPoints(player)}pt
                進捗度: ${getCompletionPercentage(player)}%
            """.trimIndent()
        } else {
            """
                === 実績プロフィール ===
                プレイヤー: ${player.name}
                解除実績: 0/${Achievement.values().size}
                総ポイント: 0pt
                進捗度: 0%
            """.trimIndent()
        }
    }
    
    /**
     * クリーンアップ
     */
    fun cleanup() {
        playerAchievements.clear()
    }
}
