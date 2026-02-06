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
     * 必要経験値: 0（初期状態）
     */
    VISITOR(1, 0L),
    
    /**
     * 開拓者 - 基本を学んだプレイヤー向け
     * 次のランク: ADVENTURER
     * 必要経験値: 100
     */
    PIONEER(2, 100L),
    
    /**
     * 冒険者 - 経験を積んだプレイヤー向け
     * 次のランク: ATTAINER
     * 必要経験値: 250
     */
    ADVENTURER(3, 250L),
    
    /**
     * 達成者 - チュートリアル完了、職業選択可能
     * 次のランク: なし（職業分岐へ）
     * 必要経験値: 500
     */
    ATTAINER(4, 500L);
    
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
