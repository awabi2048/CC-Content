package jp.awabi2048.cccontent.features.resourcecollection

import com.awabi2048.ccsystem.CCSystem
import jp.awabi2048.cccontent.items.CustomItem
import jp.awabi2048.cccontent.items.CustomItemManager
import jp.awabi2048.cccontent.persistence.ContentPdcKeys
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin

class ResourceCollectionItems(plugin: JavaPlugin) {
    private data class Definition(
        val id: String,
        val model: NamespacedKey,
        val tags: Set<String> = emptySet(),
        val stackable: Boolean = true
    )

    private val resourceIdKey = ContentPdcKeys.resourceId
    private val resourceTagsKey = ContentPdcKeys.resourceTags
    private val definitions = listOf(
        Definition("mica_flake", vanilla(Material.AMETHYST_SHARD), setOf("mineral", "insulating")),
        Definition("rock_salt", vanilla(Material.QUARTZ), setOf("mineral", "seasoning")),
        Definition("calcite_fragment", vanilla(Material.CALCITE), setOf("mineral", "carbonate")),
        Definition("sulfur", vanilla(Material.GLOWSTONE_DUST), setOf("mineral", "reactive")),
        Definition("pine_cone", vanilla(Material.SPRUCE_SAPLING), setOf("fuel", "smoke", "decoration")),
        Definition("tree_resin", vanilla(Material.SLIME_BALL), setOf("adhesive", "waterproofing")),
        Definition("birch_outer_bark", vanilla(Material.PAPER), setOf("tinder", "wrapping", "lining")),
        Definition("tannin_bark", vanilla(Material.BROWN_DYE), setOf("tannin", "dye", "preservative")),
        Definition("tinder_fungus", vanilla(Material.BROWN_MUSHROOM), setOf("tinder", "fungal_material")),
        Definition("acacia_gum", vanilla(Material.HONEYCOMB), setOf("adhesive", "binder")),
        Definition("aromatic_wood_chip", vanilla(Material.DEAD_BUSH), setOf("aromatic_smoke", "aging_material")),
        Definition("burl_wood", vanilla(Material.STRIPPED_DARK_OAK_LOG), setOf("decorative_wood", "masterwork_handle")),
        Definition("straw", vanilla(Material.PAPER), setOf("plant", "fiber")),
        Definition("sprouted_potato", vanilla(Material.POTATO), setOf("plant", "seed")),
        Definition("vegetable_leaves", vanilla(Material.AZALEA_LEAVES), setOf("plant", "leaf")),
        Definition("cocoa_pulp", vanilla(Material.MELON_SLICE), setOf("plant", "fruit_pulp")),
        Definition("wart_fiber", vanilla(Material.STRING), setOf("plant", "fiber", "nether")),
        Definition("heartwood", vanilla(Material.STRIPPED_OAK_LOG), setOf("wood", "structural")),
        Definition("bark", vanilla(Material.PAPER), setOf("wood", "bark")),
        Definition("timber_beam", vanilla(Material.STRIPPED_OAK_LOG), setOf("wood", "processed", "structural")),
        Definition("chisel", vanilla(Material.IRON_PICKAXE), stackable = false),
        Definition("geology_guide", vanilla(Material.KNOWLEDGE_BOOK), stackable = false),
        Definition("woodworking_hatchet", vanilla(Material.IRON_AXE), stackable = false),
        Definition("woodworking_knife", vanilla(Material.SHEARS), stackable = false),
        Definition("forest_guide", vanilla(Material.WRITABLE_BOOK), stackable = false),
        Definition("gathering_guide", vanilla(Material.BOOK), stackable = false),
        Definition("gathering_sickle", resourceModel("gathering_sickle"), stackable = false),
        Definition("rice", resourceModel("rice"), setOf("plant", "food")),
        Definition("onion", resourceModel("onion"), setOf("plant", "food")),
        Definition("soybean", resourceModel("soybean"), setOf("plant", "food")),
        Definition("tomato", resourceModel("tomato"), setOf("plant", "food")),
        Definition("ginger", resourceModel("ginger"), setOf("plant", "food")),
        Definition("spice_leaf", resourceModel("spice_leaf"), setOf("plant", "food")),
        Definition("tea_leaf", resourceModel("tea_leaf"), setOf("plant", "food")),
        Definition("hops", resourceModel("hops"), setOf("plant", "food")),
        Definition("coffee_bean", resourceModel("coffee_bean"), setOf("plant", "food")),
        Definition("daikon", resourceModel("daikon"), setOf("plant", "food")),
        Definition("green_onion", resourceModel("green_onion"), setOf("plant", "food")),
        Definition("blueberry", resourceModel("blueberry"), setOf("plant", "food")),
        Definition("grape", resourceModel("grape"), setOf("plant", "food")),
        Definition("strawberry", resourceModel("strawberry"), setOf("plant", "food"))
    )

    fun register() {
        CustomItemManager.unregisterByPrefix("resource.")
        definitions.forEach { CustomItemManager.register(ResourceItem(it)) }
    }

    fun unregister() {
        CustomItemManager.unregisterByPrefix("resource.")
    }

    private inner class ResourceItem(private val definition: Definition) : CustomItem {
        override val feature = "resource"
        override val id = definition.id
        override val displayName = definition.id
        override val itemModel = definition.model
        override val canPlace = false
        override val canStack = definition.stackable

        override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)

        override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
            val item = ItemStack(Material.POISONOUS_POTATO, if (definition.stackable) amount else 1)
            val meta = item.itemMeta
            meta.displayName(Component.text(message(player, "custom_items.resource.${definition.id}.name"))
                .decoration(TextDecoration.ITALIC, false))
            meta.lore(listOf(Component.text(message(player, "custom_items.resource.${definition.id}.description"), NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)))
            meta.setItemModel(itemModel)
            meta.setMaxStackSize(if (definition.stackable) 64 else 1)
            meta.persistentDataContainer.set(resourceIdKey, PersistentDataType.STRING, definition.id)
            if (definition.tags.isNotEmpty()) {
                meta.persistentDataContainer.set(
                    resourceTagsKey,
                    PersistentDataType.STRING,
                    definition.tags.sorted().joinToString(",")
                )
            }
            item.itemMeta = meta
            return item
        }

        override fun matches(item: ItemStack): Boolean =
            item.type == Material.POISONOUS_POTATO &&
                item.itemMeta?.persistentDataContainer?.get(resourceIdKey, PersistentDataType.STRING) == definition.id

        private fun message(player: Player?, key: String): String =
            CCSystem.getAPI().getI18nString(player, key).replace('&', '§')
    }

    companion object {
        private fun vanilla(material: Material): NamespacedKey = NamespacedKey.minecraft(material.key.key)
        private fun resourceModel(id: String): NamespacedKey =
            NamespacedKey("kota_server", "custom_item/resource/$id")
    }
}
