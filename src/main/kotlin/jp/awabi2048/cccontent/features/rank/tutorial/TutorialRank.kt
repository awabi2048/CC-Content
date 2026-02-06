package jp.awabi2048.cccontent.features.rank.tutorial

/**
 * チュートリアルランクの定義
 */
enum class TutorialRank(
    val level: Int,
    val requiredExp: Long
) {
    /**
     * 訪問者 - ゲーム開始時のランク
     * 次のランク: PIONEER
     * 必要経験値: 100（このランクから次に上がるまでに必要）
     */
    VISITOR(1, 100L),
    
    /**
     * 開拓者 - 基本を学んだプレイヤー向け
     * 次のランク: ADVENTURER
     * 必要経験値: 200（このランクから次に上がるまでに必要）
     */
    PIONEER(2, 200L),
    
    /**
     * 冒険者 - 経験を積んだプレイヤー向け
     * 次のランク: ATTAINER
     * 必要経験値: 300（このランクから次に上がるまでに必要）
     */
    ADVENTURER(3, 300L),
    
    /**
     * 達成者 - チュートリアル完了、職業選択可能
     * 次のランク: なし（職業分岐へ）
     * 必要経験値: Long.MAX_VALUE（最終ランク）
     */
    ATTAINER(4, Long.MAX_VALUE);
    
    companion object {
        /**
         * 次のランクを取得
         * @return 次のランク、最終ランクの場合はnull
         */
        fun TutorialRank.getNext(): TutorialRank? {
            return values().getOrNull(this.ordinal + 1)
        }
        
        /**
         * 指定されたレベルのランクを取得
         */
        fun getByLevel(level: Int): TutorialRank? {
            return values().firstOrNull { it.level == level }
        }
    }
}
