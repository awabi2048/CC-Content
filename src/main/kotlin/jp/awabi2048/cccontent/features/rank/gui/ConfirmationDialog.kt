package jp.awabi2048.cccontent.features.rank.gui

import jp.awabi2048.cccontent.gui.SimpleConfirmationDialog
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player

object ConfirmationDialog {
    fun show(
        player: Player,
        title: Component,
        bodyMessage: Component,
        confirmText: Component,
        cancelText: Component,
        onConfirm: (Player) -> Unit,
        onCancel: ((Player) -> Unit)? = null
    ) {
        // Keep the rank-facing facade stable while sharing the actual confirmation dialog implementation.
        SimpleConfirmationDialog.show(player, title, bodyMessage, confirmText, cancelText, onConfirm, onCancel)
    }
}
