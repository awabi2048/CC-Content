package jp.awabi2048.cccontent.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuDialogButton
import com.awabi2048.ccsystem.api.gui.MenuDialogHandler
import com.awabi2048.ccsystem.api.gui.MenuDialogRequest
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import net.kyori.adventure.text.Component
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
        CCSystem.getAPI().getMenuDialogService().show(
            player,
            MenuDialogRequest(
                owner = "cc-content",
                id = "simple-confirmation",
                title = title,
                body = listOf(body),
                confirm = MenuDialogButton(
                    confirmText,
                    MenuDialogHandler { target, _ ->
                        onConfirm(target)
                        MenuActionResult.Success(MenuUpdate.Close)
                    }
                ),
                cancel = MenuDialogButton(
                    cancelText,
                    MenuDialogHandler { target, _ ->
                        onCancel?.invoke(target)
                        MenuActionResult.Success(MenuUpdate.Close)
                    }
                )
            )
        )
    }
}
