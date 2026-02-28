package jp.awabi2048.cccontent.items.misc

import jp.awabi2048.cccontent.items.CustomItem
import jp.awabi2048.cccontent.items.CustomItemI18n
import net.kyori.adventure.text.Component
import org.bukkit.NamespacedKey
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

object RadioCassetteKeys {
    val playerKey = NamespacedKey("cccontent", "radio_cassette_player")
    val cassetteIdKey = NamespacedKey("cccontent", "radio_cassette_id")

    fun getCassetteId(item: ItemStack?): String? {
        val meta = item?.itemMeta ?: return null
        return meta.persistentDataContainer.get(cassetteIdKey, PersistentDataType.STRING)
    }
}

class RadioCassettePlayerItem : CustomItem {
    override val feature: String = "misc"
    override val id: String = "radio_cassette_player"
    override val displayName: String = "§6ラジカセ"
    override val lore: List<String> = listOf(
        "§7右クリックでラジカセメニューを開きます",
        "§7カセットを挿入して再生開始すると",
        "§7停止するまでループ再生します"
    )

    override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)

    override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
        val item = ItemStack(Material.POISONOUS_POTATO, amount)
        val meta = item.itemMeta ?: return item

        val name = CustomItemI18n.text(player, "custom_items.$feature.$id.name", displayName)
        val localizedLore = CustomItemI18n.list(player, "custom_items.$feature.$id.lore", lore)

        meta.displayName(Component.text(name))
        meta.lore(localizedLore.map { Component.text(it) })
        meta.persistentDataContainer.set(RadioCassetteKeys.playerKey, PersistentDataType.BYTE, 1)
        item.itemMeta = meta
        return item
    }

    override fun matches(item: ItemStack): Boolean {
        if (item.type != Material.POISONOUS_POTATO) return false
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(RadioCassetteKeys.playerKey, PersistentDataType.BYTE)
    }

    override fun onRightClick(player: Player, event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return
        if (event.hand != EquipmentSlot.HAND) return

        RadioCassetteGui.open(player)
        event.isCancelled = true
    }
}

class CassetteTapeItem(private val definition: CassetteDefinition) : CustomItem {
    override val feature: String = "misc"
    override val id: String = "cassette_${definition.id}"
    override val displayName: String = "§bカセットテープ: §f${definition.songName}"
    override val lore: List<String> = buildList {
        if (definition.description.isNotEmpty()) {
            addAll(definition.description)
        }
        add("§7再生時間: §e${definition.durationSeconds}秒")
        add("§7ラジカセGUIでクリックして挿入")
    }

    override fun createItem(amount: Int): ItemStack {
        val item = ItemStack(Material.POISONOUS_POTATO, amount)
        val meta = item.itemMeta ?: return item
        meta.displayName(Component.text(displayName))
        meta.lore(lore.map { Component.text(it) })
        meta.persistentDataContainer.set(RadioCassetteKeys.cassetteIdKey, PersistentDataType.STRING, definition.id)
        item.itemMeta = meta
        return item
    }

    override fun matches(item: ItemStack): Boolean {
        val cassetteId = RadioCassetteKeys.getCassetteId(item) ?: return false
        return cassetteId == definition.id
    }
}
