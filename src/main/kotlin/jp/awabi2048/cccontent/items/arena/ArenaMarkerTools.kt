package jp.awabi2048.cccontent.items.arena

import jp.awabi2048.cccontent.CCContent
import jp.awabi2048.cccontent.items.CustomItem
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class ArenaStructureMarkerToolItem : CustomItem {
    override val feature: String = "arena"
    override val id: String = "structure_marker_tool"
    override val displayName: String = "§6アリーナ構造マーカーツール"
    override val itemModel = NamespacedKey.minecraft("blaze_rod")
    override val lore: List<String> = emptyList()

    override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)

    override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
        val item = CCContent.instance.getAdminMarkerToolService().createTool("arena.structure_marker_tool", player)
        item.amount = amount
        return item
    }

    override fun matches(item: ItemStack): Boolean {
        return CCContent.instance.getAdminMarkerToolService().isTool(item, "arena.structure_marker_tool")
    }
}

class ArenaOtherMarkerToolItem : CustomItem {
    override val feature: String = "arena"
    override val id: String = "other_marker_tool"
    override val displayName: String = "§6アリーナ補助マーカーツール"
    override val itemModel = NamespacedKey.minecraft("blaze_rod")
    override val lore: List<String> = emptyList()

    override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)

    override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
        val item = CCContent.instance.getAdminMarkerToolService().createTool("arena.other_marker_tool", player)
        item.amount = amount
        return item
    }

    override fun matches(item: ItemStack): Boolean {
        return CCContent.instance.getAdminMarkerToolService().isTool(item, "arena.other_marker_tool")
    }
}

class ArenaLiftToolItem : CustomItem {
    override val feature: String = "arena"
    override val id: String = "lift_tool"
    override val displayName: String = "§6アリーナリフト設置ツール"
    override val itemModel = NamespacedKey.minecraft("blaze_rod")
    override val lore: List<String> = emptyList()

    override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)

    override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
        val item = CCContent.instance.getAdminMarkerToolService().createTool("arena.lift_tool", player)
        item.amount = amount
        return item
    }

    override fun matches(item: ItemStack): Boolean {
        return CCContent.instance.getAdminMarkerToolService().isTool(item, "arena.lift_tool")
    }
}
