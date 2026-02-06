package jp.awabi2048.cccontent.features.rank

/**
 * ランクシステムのタイプ
 * アリーナ、スキマダンジョン、カスタムアイテムそれぞれが独立したランクを持つ
 */
enum class RankType(
    val displayName: String,
    val feature: String
) {
    // アリーナランク
    ARENA("§cアリーナランク", "arena"),
    
    // スキマダンジョンランク
    SUKIMA_DUNGEON("§2スキマダンジョンランク", "sukima_dungeon"),
    
    // カスタムアイテムランク
    CUSTOM_ITEM("§dカスタムアイテムランク", "custom_item");
    
    companion object {
        /**
         * フィーチャー名からRankTypeを取得
         * @param feature フィーチャー名
         * @return 対応するRankType、見つからない場合はnull
         */
        fun fromFeature(feature: String): RankType? {
            return values().firstOrNull { it.feature == feature }
        }
    }
}
