package jp.awabi2048.cccontent.items.arena

import jp.awabi2048.cccontent.CCContent
import jp.awabi2048.cccontent.items.CustomItem
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class ArenaMarkerToolItem : CustomItem {
    override val feature: String = "arena"
    override val id: String = "marker_tool"
    override val displayName: String = "§6アリーナ管理マーカーツール"
    override val itemModel = NamespacedKey.minecraft("blaze_rod")
    override val lore: List<String> = emptyList()

    override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)

    override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
        val item = CCContent.instance.getAdminMarkerToolService().createTool("arena.marker_tool", player)
        item.amount = amount
        return item
    }

    override fun matches(item: ItemStack): Boolean {
        return CCContent.instance.getAdminMarkerToolService().isTool(item, "arena.marker_tool")
    }
}
