package jp.awabi2048.cccontent.features.rank

/**
 * ランクティアの定義
 * ランクの階級を表現します
 */
enum class RankTier(
    val displayName: String,
    val level: Int,
    val nextThreshold: Long
) {
    // Bronze (青銅) ティア
    BRONZE_I("§6[青銅Ⅰ]", 1, 1000),
    BRONZE_II("§6[青銅Ⅱ]", 2, 2000),
    BRONZE_III("§6[青銅Ⅲ]", 3, 3000),
    
    // Silver (銀) ティア
    SILVER_I("§7[銀Ⅰ]", 4, 5000),
    SILVER_II("§7[銀Ⅱ]", 5, 7000),
    SILVER_III("§7[銀Ⅲ]", 6, 9000),
    
    // Gold (金) ティア
    GOLD_I("§e[金Ⅰ]", 7, 12000),
    GOLD_II("§e[金Ⅱ]", 8, 15000),
    GOLD_III("§e[金Ⅲ]", 9, 18000),
    
    // Platinum (白金) ティア
    PLATINUM_I("§f[白金Ⅰ]", 10, 22000),
    PLATINUM_II("§f[白金Ⅱ]", 11, 26000),
    PLATINUM_III("§f[白金Ⅲ]", 12, 30000),
    
    // Diamond (ダイアモンド) ティア
    DIAMOND_I("§b[ダイアモンドⅠ]", 13, 35000),
    DIAMOND_II("§b[ダイアモンドⅡ]", 14, 40000),
    DIAMOND_III("§b[ダイアモンドⅢ]", 15, 50000),
    
    // Master (マスター) ティア
    MASTER("§5[マスター]", 16, Long.MAX_VALUE);
    
    companion object {
        /**
         * スコアに基づいてティアを取得
         * @param score 現在のスコア
         * @return 対応するティア
         */
        fun getTierByScore(score: Long): RankTier {
            return values().reversed().firstOrNull { score >= it.nextThreshold || it == BRONZE_I } ?: BRONZE_I
        }
    }
}
