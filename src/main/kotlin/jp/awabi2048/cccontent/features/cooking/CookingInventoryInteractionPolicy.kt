package jp.awabi2048.cccontent.features.cooking

import org.bukkit.event.inventory.ClickType

/**
 * 料理ステーションのInventory操作を、表示専用領域と入力領域で分離するポリシーです。
 *
 * GUI全体へ一律のクリック禁止を適用すると、プレイヤーインベントリや通常の入力スロットまで
 * 操作不能になります。ここでは入力へ移送できるスロットと、入力スロットで許可するクリックを
 * 意味として定義し、イベント処理とテストが同じ契約を参照できるようにします。
 */
internal object CookingInventoryInteractionPolicy {
    private val NON_INTERACTIVE_INPUT_CLICKS = setOf(
        ClickType.CREATIVE,
        ClickType.UNKNOWN,
        ClickType.WINDOW_BORDER_LEFT,
        ClickType.WINDOW_BORDER_RIGHT,
    )

    /** 通常アイテムを置ける入力スロットで、許可されるクリックかを返します。 */
    fun allowsInputClick(click: ClickType): Boolean = click !in NON_INTERACTIVE_INPUT_CLICKS

    /**
     * 入力領域を次回調理用のワークスペースとして利用できる状態かを返します。
     *
     * 処理中に投入されたアイテムは実行中のバッチへ後から混入させず、次回調理の待機材料として
     * 保存します。完成物を表示している状態では、同じ5枠が成果物表示と重なるため編集できません。
     */
    fun allowsInputWorkspace(state: CookingProcessState?): Boolean =
        state == null || CookingStationStateMachine.isProcessingState(state)

    /**
     * 大釜の液体領域へ材料を投入できる条件です。
     *
     * 釜が空のときは、画面上の枠がクリック可能に見えても投入を受け付けません。表示側と
     * クリック側が別々に「液体あり」を判定すると、Loreだけが出る、またはLoreなしで投入
     * できるといった状態差が生じるため、液体状態と調理状態をここで一つにまとめます。
     */
    fun allowsLiquidAreaMaterialInput(
        contents: CookingLiquidContents,
        state: CookingProcessState?,
    ): Boolean = !contents.isEmpty() && allowsInputWorkspace(state)

    /**
     * プレイヤーインベントリからのシフト移送先を返します。
     * 処理中は次回調理用ワークスペースとしても利用でき、液体表示・成果物として管理している枠は
     * 移送先から除外します。
     */
    fun transferableInputSlots(
        inputSlots: Collection<Int>,
        liquidDisplaySlots: Collection<Int>,
        outputSlots: Collection<Int>,
        inputWorkspaceAllowed: Boolean,
    ): List<Int> {
        if (!inputWorkspaceAllowed) return emptyList()
        val excluded = liquidDisplaySlots.toSet() + outputSlots
        return inputSlots.filter { it >= 0 && it !in excluded }.distinct()
    }
}
