package jp.awabi2048.cccontent.gui

import io.papermc.paper.dialog.Dialog
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.type.DialogType
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickCallback
import org.bukkit.entity.Player

object SimpleConfirmationDialog {
    fun show(
        player: Player,
        title: Component,
        body: Component,
        confirmText: Component,
        cancelText: Component,
        onConfirm: (Player) -> Unit,
        onCancel: ((Player) -> Unit)? = null
    ) {
        val confirmButton = ActionButton.builder(confirmText)
            .width(150)
            .action(
                DialogAction.customClick(
                    DialogActionCallback { _, audience ->
                        val target = audience as? Player ?: return@DialogActionCallback
                        onConfirm(target)
                    },
                    ClickCallback.Options.builder().uses(1).build()
                )
            )
            .build()

        val cancelButton = ActionButton.builder(cancelText)
            .width(150)
            .action(
                DialogAction.customClick(
                    DialogActionCallback { _, audience ->
                        val target = audience as? Player ?: return@DialogActionCallback
                        onCancel?.invoke(target)
                    },
                    ClickCallback.Options.builder().uses(1).build()
                )
            )
            .build()

        val dialog = Dialog.create { factory ->
            factory.empty()
                .base(
                    DialogBase.builder(title)
                        .body(listOf(DialogBody.plainMessage(body, 280)))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .build()
                )
                .type(DialogType.confirmation(confirmButton, cancelButton))
        }

        player.showDialog(dialog)
    }
}
