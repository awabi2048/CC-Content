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
     * プレイヤーインベントリからのシフト移送先を返します。
     * 処理中は入力をロックし、液体表示・成果物として管理している枠は移送先から除外します。
     */
    fun transferableInputSlots(
        inputSlots: Collection<Int>,
        liquidDisplaySlots: Collection<Int>,
        outputSlots: Collection<Int>,
        processing: Boolean,
    ): List<Int> {
        if (processing) return emptyList()
        val excluded = liquidDisplaySlots.toSet() + outputSlots
        return inputSlots.filter { it >= 0 && it !in excluded }.distinct()
    }
}
