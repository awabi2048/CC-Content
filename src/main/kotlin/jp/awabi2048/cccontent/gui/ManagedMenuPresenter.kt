package jp.awabi2048.cccontent.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiInventoryPolicy
import com.awabi2048.ccsystem.api.gui.ManagedInventoryMenuRequest
import com.awabi2048.ccsystem.api.gui.ManagedMenuTransition
import com.awabi2048.ccsystem.api.gui.MenuRoute
import com.awabi2048.ccsystem.api.gui.MenuSoundPolicy
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory

object ManagedMenuPresenter {
    fun open(
        player: Player,
        inventory: Inventory,
        menuId: String = inventory.holder?.javaClass?.simpleName ?: "inventory",
        transition: ManagedMenuTransition = ManagedMenuTransition.PRESERVE_HISTORY,
        policy: GuiInventoryPolicy = permissivePolicy(inventory),
        openSound: MenuSoundPolicy = MenuSoundPolicy.Default,
    ): Boolean = CCSystem.getAPI().getMenuRuntimeService().present(
        player,
        ManagedInventoryMenuRequest(
            route = MenuRoute(OWNER, menuId),
            inventory = inventory,
            transition = transition,
            policy = policy,
            openSound = openSound,
        )
    )

    private fun permissivePolicy(inventory: Inventory): GuiInventoryPolicy =
        GuiInventoryPolicy(
            inputSlots = (0 until inventory.size).toSet(),
            allowPlayerInventoryInteraction = true,
        )

    private const val OWNER = "cc-content"
}
