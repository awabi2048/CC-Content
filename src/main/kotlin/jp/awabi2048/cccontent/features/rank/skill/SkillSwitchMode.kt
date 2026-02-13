package jp.awabi2048.cccontent.features.rank.skill

/**
 * 能動スキルの切替様式
 */
enum class SkillSwitchMode(val id: String, val displayName: String) {
    /**
     * 職業ツールを持っている状態でFキー押下で切り替え
     */
    TOOL_F_KEY("tool_f_key", "ツールを持ってFキー押下"),

    /**
     * 全てのFキー押下で切り替え
     */
    ANY_F_KEY("any_f_key", "全てのFキー押下"),

    /**
     * メニューからのみ切り替え可能
     */
    MENU_ONLY("menu_only", "メニューからのみ");

    companion object {
        fun fromId(id: String): SkillSwitchMode? {
            return values().find { it.id == id }
        }
    }
}
