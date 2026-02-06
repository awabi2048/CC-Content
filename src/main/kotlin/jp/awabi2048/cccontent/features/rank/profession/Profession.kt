package jp.awabi2048.cccontent.features.rank.profession

/**
 * プレイヤーが選択できる職業の定義
 */
enum class Profession(
    val id: String,
    val displayColorCode: String
) {
    /**
     * 木こり - 木の伐採で経験値を獲得
     */
    LUMBERJACK("lumberjack", "§8"),
    
    /**
     * 醸造家 - ポーション醸造で経験値を獲得
     */
    BREWER("brewer", "§5"),
    
    /**
     * 鉱夫 - 鉱石採掘で経験値を獲得
     */
    MINER("miner", "§7");
    
    companion object {
        /**
         * IDから職業を取得
         */
        fun fromId(id: String): Profession? {
            return values().firstOrNull { it.id == id }
        }
    }
}
