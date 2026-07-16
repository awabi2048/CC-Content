package jp.awabi2048.cccontent.features.brewery.garden

import jp.awabi2048.cccontent.items.CustomItem
import jp.awabi2048.cccontent.items.CustomItemI18n
import jp.awabi2048.cccontent.items.CustomItemManager
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

object GardenItems {
    private const val ITEM_PREFIX = "brewery.garden_"

    fun register(plants: Collection<GardenPlant>) {
        CustomItemManager.unregisterByPrefix(ITEM_PREFIX)
        plants.forEach { plant ->
            CustomItemManager.register(GardenCustomItem(plant, true))
            CustomItemManager.register(GardenCustomItem(plant, false))
        }
    }

    fun unregister() {
        CustomItemManager.unregisterByPrefix(ITEM_PREFIX)
    }

    fun seed(plant: GardenPlant, player: Player?): ItemStack = CustomItemManager.createItemForPlayer(plant.itemSeedId, player) ?: error("Garden種アイテムが登録されていません: ${plant.itemSeedId}")
    fun fruit(plant: GardenPlant, player: Player?): ItemStack = CustomItemManager.createItemForPlayer(plant.itemFruitId, player) ?: error("Garden果実アイテムが登録されていません: ${plant.itemFruitId}")
    fun customId(item: ItemStack): String? = CustomItemManager.identify(item)?.fullId
}

private class GardenCustomItem(
    private val plant: GardenPlant,
    private val seed: Boolean
) : CustomItem {
    override val feature: String = "brewery"
    override val id: String = if (seed) "garden_seed_${plant.id}" else "garden_fruit_${plant.id}"
    override val displayName: String = if (seed) plant.seedLanguageKey else plant.fruitLanguageKey
    override val canPlace: Boolean = false

    private val material: Material = if (seed) {
        Material.WHEAT_SEEDS
    } else {
        when (plant.id) {
            "apple" -> Material.APPLE
            "blueberry" -> Material.SWEET_BERRIES
            "grape" -> Material.GLOW_BERRIES
            "strawberry" -> Material.MELON_SLICE
            else -> error("Garden果実の素材が未定義です: ${plant.id}")
        }
    }

    override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)

    override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
        val item = ItemStack(material, amount.coerceAtLeast(1))
        val meta = item.itemMeta
        val languageKey = if (seed) plant.seedLanguageKey else plant.fruitLanguageKey
        meta.displayName(Component.text(CustomItemI18n.text(player, languageKey, languageKey)))
        meta.lore(CustomItemI18n.lore(player, languageKey.removeSuffix(".name") + ".lore", emptyList()))
        item.itemMeta = meta
        return item
    }

    override fun matches(item: ItemStack): Boolean = false
}
