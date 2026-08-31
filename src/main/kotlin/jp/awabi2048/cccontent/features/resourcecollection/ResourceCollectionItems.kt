package jp.awabi2048.cccontent.features.resourcecollection

import com.awabi2048.ccsystem.CCSystem
import jp.awabi2048.cccontent.items.CustomItem
import jp.awabi2048.cccontent.items.CustomItemManager
import jp.awabi2048.cccontent.items.ContentItemModels
import jp.awabi2048.cccontent.persistence.ContentPdcKeys
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.Consumable
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation

class ResourceCollectionItems(plugin: JavaPlugin) {
    private data class Definition(
        val id: String,
        val tags: Set<String> = emptySet(),
        val stackable: Boolean = true
    )

    private val resourceIdKey = ContentPdcKeys.resourceId
    private val resourceTagsKey = ContentPdcKeys.resourceTags
    private val definitions = listOf(
        resourceDefinition("mica_flake", setOf("mineral", "insulating")),
        resourceDefinition("rock_salt", setOf("mineral", "seasoning")),
        resourceDefinition("calcite_fragment", setOf("mineral", "carbonate")),
        resourceDefinition("sulfur", setOf("mineral", "reactive")),
        resourceDefinition("pine_cone", setOf("fuel", "smoke", "decoration")),
        resourceDefinition("tree_resin", setOf("adhesive", "waterproofing")),
        resourceDefinition("birch_outer_bark", setOf("tinder", "wrapping", "lining")),
        resourceDefinition("tannin_bark", setOf("tannin", "dye", "preservative")),
        resourceDefinition("tinder_fungus", setOf("tinder", "fungal_material")),
        resourceDefinition("acacia_gum", setOf("adhesive", "binder")),
        resourceDefinition("aromatic_wood_chip", setOf("aromatic_smoke", "aging_material")),
        resourceDefinition("burl_wood", setOf("decorative_wood", "masterwork_handle")),
        resourceDefinition("straw", setOf("plant", "fiber")),
        resourceDefinition("sprouted_potato", setOf("plant", "seed")),
        resourceDefinition("vegetable_leaves", setOf("plant", "leaf")),
        resourceDefinition("cocoa_pulp", setOf("plant", "fruit_pulp")),
        resourceDefinition("wart_fiber", setOf("plant", "fiber", "nether")),
        resourceDefinition("heartwood", setOf("wood", "structural")),
        resourceDefinition("bark", setOf("wood", "bark")),
        resourceDefinition("timber_beam", setOf("wood", "processed", "structural")),
        resourceDefinition("chisel", stackable = false),
        resourceDefinition("geology_guide", stackable = false),
        resourceDefinition("woodworking_hatchet", stackable = false),
        resourceDefinition("woodworking_knife", stackable = false),
        resourceDefinition("forest_guide", stackable = false),
        resourceDefinition("gathering_guide", stackable = false),
        resourceDefinition("gathering_sickle", stackable = false),
        resourceDefinition("rice", setOf("plant", "food")),
        resourceDefinition("onion", setOf("plant", "food")),
        resourceDefinition("soybean", setOf("plant", "food")),
        resourceDefinition("tomato", setOf("plant", "food")),
        resourceDefinition("ginger", setOf("plant", "food")),
        resourceDefinition("spice_leaf", setOf("plant", "food")),
        resourceDefinition("tea_leaf", setOf("plant", "food")),
        resourceDefinition("hops", setOf("plant", "food")),
        resourceDefinition("coffee_bean", setOf("plant", "food")),
        resourceDefinition("daikon", setOf("plant", "food")),
        resourceDefinition("green_onion", setOf("plant", "food")),
        resourceDefinition("blueberry", setOf("plant", "food")),
        resourceDefinition("grape", setOf("plant", "food")),
        resourceDefinition("strawberry", setOf("plant", "food"))
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
        override val itemModel = ContentItemModels.resource(definition.id)
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
            if (definition.id == "gathering_sickle") {
                item.setData(DataComponentTypes.MAX_DAMAGE, 256)
                item.setData(
                    DataComponentTypes.CONSUMABLE,
                    Consumable.consumable()
                        .consumeSeconds(3600.0f)
                        .animation(ItemUseAnimation.BRUSH)
                        .hasConsumeParticles(false)
                )
            }
            return item
        }

        override fun matches(item: ItemStack): Boolean =
            item.type == Material.POISONOUS_POTATO &&
                item.itemMeta?.persistentDataContainer?.get(resourceIdKey, PersistentDataType.STRING) == definition.id

        private fun message(player: Player?, key: String): String =
            CCSystem.getAPI().getLocalized(player, jp.awabi2048.cccontent.util.ContentLocalizationKeys.text(key, "custom_items.resource.")).replace('&', '§')
    }

    companion object {
        private fun resourceDefinition(id: String, tags: Set<String> = emptySet(), stackable: Boolean = true): Definition =
            Definition(id, tags, stackable)
    }
}
