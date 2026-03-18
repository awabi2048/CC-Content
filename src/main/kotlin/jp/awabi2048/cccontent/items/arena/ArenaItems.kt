package jp.awabi2048.cccontent.items.arena

import jp.awabi2048.cccontent.items.CustomItem
import jp.awabi2048.cccontent.items.CustomItemI18n
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.util.Locale

private val arenaItemKey by lazy {
    NamespacedKey(Bukkit.getPluginManager().getPlugin("CC-Content")!!, "arena_item")
}

abstract class ArenaSimpleItem(
    private val material: Material,
    private val modelData: Int?,
    final override val id: String,
    final override val displayName: String,
    final override val lore: List<String>
) : CustomItem {
    final override val feature: String = "arena"

    final override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)

    final override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
        val item = ItemStack(material, amount.coerceAtLeast(1))
        val meta = item.itemMeta ?: return item
        val name = CustomItemI18n.text(player, "custom_items.$feature.$id.name", displayName)
        val localizedLore = CustomItemI18n.list(player, "custom_items.$feature.$id.lore", lore)
        meta.displayName(Component.text(name))
        meta.lore(localizedLore.map { Component.text(it) })
        modelData?.let { meta.setCustomModelData(it) }
        meta.persistentDataContainer.set(arenaItemKey, PersistentDataType.STRING, id)
        item.itemMeta = meta
        return item
    }

    final override fun matches(item: ItemStack): Boolean {
        val meta = item.itemMeta ?: return false
        val raw = meta.persistentDataContainer.get(arenaItemKey, PersistentDataType.STRING) ?: return false
        return raw == id
    }
}

class ArenaTicketItem : ArenaSimpleItem(
    material = Material.PAPER,
    modelData = 4001,
    id = "ticket",
    displayName = "§6アリーナチケット",
    lore = listOf("§7アリーナへの参加チケット")
)

class ArenaMedalItem : ArenaSimpleItem(
    material = Material.SUNFLOWER,
    modelData = 4002,
    id = "medal",
    displayName = "§6アリーナメダル",
    lore = listOf("§7アリーナでの戦いの証")
)

class ArenaPrizeItem : ArenaSimpleItem(
    material = Material.CHEST,
    modelData = 4003,
    id = "prize",
    displayName = "§6アリーナ報酬箱",
    lore = listOf("§7アリーナでの勝利報酬")
)

class ArenaDecayedEquipmentItem(
    private val kindId: String,
    kindLabel: String,
    modelKey: String
) : ArenaSimpleItem(
    material = Material.POISONOUS_POTATO,
    modelData = null,
    id = "decayed_$kindId",
    displayName = "§7朽ちた$kindLabel",
    lore = listOf("§8かつての力を失った残骸")
) {
    override val itemModel: NamespacedKey = NamespacedKey.minecraft(modelKey)
}

class ArenaMobTokenItem(private val mobTypeId: String) : ArenaSimpleItem(
    material = Material.POISONOUS_POTATO,
    modelData = null,
    id = "mob_token_${sanitizeMobTypeId(mobTypeId)}",
    displayName = "§6${mobTypeId.lowercase(Locale.ROOT)}のトークン",
    lore = listOf("§7アリーナ討伐証", "§8rarity=common")
) {
    override val itemModel: NamespacedKey = NamespacedKey.minecraft("stone_sword")

    companion object {
        private fun sanitizeMobTypeId(typeId: String): String {
            return typeId.trim().lowercase(Locale.ROOT).replace(Regex("[^a-z0-9_]+"), "_")
        }
    }
}
