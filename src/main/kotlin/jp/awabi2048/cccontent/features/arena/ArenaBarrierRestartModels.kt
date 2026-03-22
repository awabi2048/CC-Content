package jp.awabi2048.cccontent.features.arena

import org.bukkit.Bukkit
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import java.util.UUID

object ArenaBarrierRestartLayout {
    const val MENU_SIZE = 45
    const val MENU_TITLE = " "
    const val CENTER_SLOT = 22

    val HEADER_SLOTS: List<Int> = (0 until 9).toList()
    val FOOTER_SLOTS: List<Int> = (36 until 45).toList()
    val INNER_SLOTS: List<Int> = (9 until 36).filter { it != CENTER_SLOT }
    val MENU_SLOTS: List<Int> = INNER_SLOTS
}

class ArenaBarrierRestartMenuHolder(
    val ownerId: UUID,
    val worldName: String
) : InventoryHolder {
    var backingInventory: Inventory? = null

    override fun getInventory(): Inventory {
        return backingInventory ?: Bukkit.createInventory(this, ArenaBarrierRestartLayout.MENU_SIZE, ArenaBarrierRestartLayout.MENU_TITLE)
    }
}
