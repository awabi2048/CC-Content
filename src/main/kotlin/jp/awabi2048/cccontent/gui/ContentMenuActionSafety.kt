package jp.awabi2048.cccontent.gui

import com.awabi2048.ccsystem.api.gui.GuiMenuActionIntent
import com.awabi2048.ccsystem.api.gui.MenuActionSafety
import com.awabi2048.ccsystem.api.gui.MenuGesture

/**
 * CC-Content が管理するメニュー操作の安全区分です。
 *
 * これらの画面は Bukkit inventory の holder で実行され、CC-System Runtime の
 * reversible token を受け取る経路ではありません。状態復元を Runtime に委ねられない
 * 永続操作は、可逆に見えるトグルであっても REVERSIBLE と宣言しません。
 */
object ContentMenuActionSafety {
    fun gesture(
        actionId: String,
        gesture: MenuGesture,
        label: String,
        payload: Map<String, String> = emptyMap(),
        enabled: Boolean = true,
    ): GuiMenuActionIntent.GestureAction = GuiMenuActionIntent.GestureAction(
        actionId = actionId,
        gesture = gesture,
        label = label,
        payload = payload,
        enabled = enabled,
        safety = safetyFor(actionId),
    )

    fun safetyFor(actionId: String): MenuActionSafety = when (actionId) {
        // Catalog: 表示内容と画面遷移だけです。検索対象は一時的な表示条件です。
        "open_journal", "open_detail", "set_search", "clear_search" -> MenuActionSafety.NAVIGATION_ONLY

        // 醸造機の開始・停止はアイテム消費と実時間進行を伴います。
        "brewery_action" -> MenuActionSafety.IRREVERSIBLE

        // ミニゲームの開始・停止、保存済み時間設定の変更は監査から自動実行できません。
        "start", "stop", "adjust_time_increase", "adjust_time_decrease",
        "adjust_preparation_increase", "adjust_preparation_decrease" -> MenuActionSafety.IRREVERSIBLE
        "participants", "toggle_participant", "history_recent", "history_top", "close" ->
            MenuActionSafety.NAVIGATION_ONLY

        // 職業選択は確認画面に入り、確定時だけ永続データを変更します。
        "profession_select" -> MenuActionSafety.CONFIRM_ENTRY
        "profession_confirm" -> MenuActionSafety.IRREVERSIBLE
        "profession_cancel" -> MenuActionSafety.NAVIGATION_ONLY

        // パーティ操作は Dialog/チャット入力、または通知を伴う外部作用です。
        "settings", "invite" -> MenuActionSafety.INPUT_OR_EXTERNAL_SURFACE
        "recruiting", "chat" -> MenuActionSafety.EXTERNAL_SIDE_EFFECT
        "disband" -> MenuActionSafety.CONFIRM_ENTRY

        else -> error("CC-Content GUI action must declare MenuActionSafety: $actionId")
    }
}
