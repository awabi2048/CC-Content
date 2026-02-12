package jp.awabi2048.cccontent.features.rank.profession

enum class ProfessionType {
    GENERAL,
    GATHERING,
    CRAFTING,
    COMBAT,
    CREATIVE
}

/**
 * プレイヤーが選択できる職業の定義
 */
enum class Profession(
    val id: String,
    val displayColorCode: String,
    val type: ProfessionType
) {
    /**
     * 木こり - 木の伐採で経験値を獲得
     */
    LUMBERJACK("lumberjack", "§8", ProfessionType.GATHERING),

    /**
     * 醸造家 - ポーション醸造で経験値を獲得
     */
    BREWER("brewer", "§5", ProfessionType.CRAFTING),

    /**
     * 鉱夫 - 鉱石採掘で経験値を獲得
     */
    MINER("miner", "§7", ProfessionType.GATHERING),

    /**
     * 料理人 - 料理で経験値を獲得
     */
    COOK("cook", "§6", ProfessionType.CRAFTING),

    /**
     * 剣士 - モンスター討伐で経験値を獲得
     */
    SWORDSMAN("swordsman", "§c", ProfessionType.COMBAT),

    /**
     * 戦士 - 近接戦闘で経験値を獲得
     */
    WARRIOR("warrior", "§4", ProfessionType.COMBAT),

    /**
     * 農家 - 作物の収穫で経験値を獲得
     */
    FARMER("farmer", "§a", ProfessionType.GATHERING),

    /**
     * 庭師 - 装飾的なブロック設置で経験値を獲得
     */
    GARDENER("gardener", "§2", ProfessionType.CREATIVE),

    /**
     * 大工 - 建築で経験値を獲得
     */
    CARPENTER("carpenter", "§6", ProfessionType.CREATIVE);
    
    companion object {
        /**
         * IDから職業を取得
         */
        fun fromId(id: String): Profession? {
            return values().firstOrNull { it.id == id }
        }
    }
}
