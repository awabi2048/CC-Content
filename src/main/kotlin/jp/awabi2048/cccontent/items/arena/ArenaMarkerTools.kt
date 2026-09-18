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

class ArenaLobbyMarkerToolItem : CustomItem {
    override val feature: String = "arena"
    override val id: String = "lobby_marker_tool"
    override val displayName: String = "§6アリーナロビー用マーカーツール"
    override val itemModel = NamespacedKey.minecraft("blaze_rod")
    override val lore: List<String> = emptyList()

    override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)

    override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
        val item = CCContent.instance.getAdminMarkerToolService().createTool("arena.lobby_marker_tool", player)
        item.amount = amount
        return item
    }

    override fun matches(item: ItemStack): Boolean {
        val service = CCContent.instance.getAdminMarkerToolService()
        // 旧 arena.other_marker_tool の読み替え。arena.lift_tool は対象外。
        return service.isTool(item, "arena.lobby_marker_tool") || service.isTool(item, "arena.other_marker_tool")
    }
}

class ArenaMechanicMarkerToolItem : CustomItem {
    override val feature: String = "arena"
    override val id: String = "mechanic_marker_tool"
    override val displayName: String = "§6アリーナギミックマーカーツール"
    override val itemModel = NamespacedKey.minecraft("blaze_rod")
    override val lore: List<String> = emptyList()

    override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)

    override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
        val item = CCContent.instance.getAdminMarkerToolService().createTool("arena.mechanic_marker_tool", player)
        item.amount = amount
        return item
    }

    override fun matches(item: ItemStack): Boolean {
        return CCContent.instance.getAdminMarkerToolService().isTool(item, "arena.mechanic_marker_tool")
    }
}
