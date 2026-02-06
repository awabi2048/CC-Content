package jp.awabi2048.cccontent.features.rank.tutorial

/**
 * チュートリアルランクの定義
 * 
 * 仕様：タスク完了によるランクアップシステム
 * - NEWBIE: 初期ランク（プレイ時間 3分）
 * - VISITOR: 訪問者（複数タスク達成）
 * - PIONEER: 開拓者（複数タスク達成）
 * - ADVENTURER: 冒険者（エンダードラゴン討伐）
 * - ATTAINER: 達成者（最終ランク、職業分岐へ）
 */
enum class TutorialRank(
    val level: Int,
    @Deprecated("タスクベースのシステムに移行したため使用しません", level = DeprecationLevel.WARNING)
    val requiredExp: Long = 0L
) {
    /**
     * 新規プレイヤー - ゲーム開始時のランク
     * タスク: プレイ時間 3分
     */
    NEWBIE(0, 0L),
    
    /**
     * 訪問者 - 基本タスクを達成したプレイヤー向け
     * 次のランク: PIONEER
     */
    VISITOR(1, 0L),
    
    /**
     * 開拓者 - 基本を学んだプレイヤー向け
     * 次のランク: ADVENTURER
     */
    PIONEER(2, 0L),
    
    /**
     * 冒険者 - 経験を積んだプレイヤー向け
     * 次のランク: ATTAINER
     */
    ADVENTURER(3, 0L),
    
    /**
     * 達成者 - チュートリアル完了、職業選択可能
     * 次のランク: なし（職業分岐へ）
     */
    ATTAINER(4, 0L);
    
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
