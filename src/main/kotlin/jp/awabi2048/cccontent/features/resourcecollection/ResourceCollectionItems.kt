package jp.awabi2048.cccontent.features.resourcecollection

import com.awabi2048.ccsystem.CCSystem
import jp.awabi2048.cccontent.items.CustomItem
import jp.awabi2048.cccontent.items.CustomItemManager
import net.kyori.adventure.text.Component
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
        val model: Material,
        val tags: Set<String> = emptySet(),
        val base: Material = Material.POISONOUS_POTATO,
        val stackable: Boolean = true
    )

    private val resourceIdKey = NamespacedKey(plugin, "resource_id")
    private val resourceTagsKey = NamespacedKey(plugin, "resource_tags")
    private val definitions = listOf(
        Definition("mica_flake", Material.AMETHYST_SHARD, setOf("mineral", "insulating")),
        Definition("rock_salt", Material.QUARTZ, setOf("mineral", "seasoning")),
        Definition("calcite_fragment", Material.CALCITE, setOf("mineral", "carbonate")),
        Definition("sulfur", Material.GLOWSTONE_DUST, setOf("mineral", "reactive")),
        Definition("pine_cone", Material.SPRUCE_SAPLING, setOf("fuel", "smoke", "decoration")),
        Definition("tree_resin", Material.SLIME_BALL, setOf("adhesive", "waterproofing")),
        Definition("birch_outer_bark", Material.PAPER, setOf("tinder", "wrapping", "lining")),
        Definition("tannin_bark", Material.BROWN_DYE, setOf("tannin", "dye", "preservative")),
        Definition("tinder_fungus", Material.BROWN_MUSHROOM, setOf("tinder", "fungal_material")),
        Definition("acacia_gum", Material.HONEYCOMB, setOf("adhesive", "binder")),
        Definition("aromatic_wood_chip", Material.DEAD_BUSH, setOf("aromatic_smoke", "aging_material")),
        Definition("burl_wood", Material.STRIPPED_DARK_OAK_LOG, setOf("decorative_wood", "masterwork_handle")),
        Definition("straw", Material.PAPER, setOf("plant", "fiber")),
        Definition("heartwood", Material.STRIPPED_OAK_LOG, setOf("wood", "structural")),
        Definition("bark", Material.PAPER, setOf("wood", "bark")),
        Definition("timber_beam", Material.STRIPPED_OAK_LOG, setOf("wood", "processed", "structural")),
        Definition("chisel", Material.IRON_PICKAXE, base = Material.IRON_PICKAXE, stackable = false),
        Definition("geology_guide", Material.KNOWLEDGE_BOOK, base = Material.KNOWLEDGE_BOOK, stackable = false),
        Definition("mining_hammer", Material.IRON_PICKAXE, base = Material.IRON_PICKAXE, stackable = false),
        Definition("felling_axe", Material.IRON_AXE, base = Material.IRON_AXE, stackable = false),
        Definition("woodworking_hatchet", Material.IRON_AXE, base = Material.IRON_AXE, stackable = false),
        Definition("woodworking_knife", Material.SHEARS, base = Material.SHEARS, stackable = false),
        Definition("forest_guide", Material.WRITABLE_BOOK, base = Material.WRITABLE_BOOK, stackable = false),
        Definition("gathering_guide", Material.BOOK, base = Material.BOOK, stackable = false),
        Definition("gathering_sickle", Material.IRON_HOE, base = Material.IRON_HOE, stackable = false),
        Definition("cultivation_hoe", Material.DIAMOND_HOE, base = Material.DIAMOND_HOE, stackable = false)
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
        override val itemModel = NamespacedKey.minecraft(definition.model.key.key)
        override val canPlace = false
        override val canStack = definition.stackable

        override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)

        override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
            val item = ItemStack(definition.base, if (definition.stackable) amount else 1)
            val meta = item.itemMeta
            meta.displayName(Component.text(message(player, "custom_items.resource.${definition.id}.name"))
                .decoration(TextDecoration.ITALIC, false))
            meta.lore(listOf(Component.text(message(player, "custom_items.resource.${definition.id}.description"))
                .decoration(TextDecoration.ITALIC, false)))
            if (definition.tags.isNotEmpty()) {
                meta.persistentDataContainer.set(resourceIdKey, PersistentDataType.STRING, definition.id)
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
            definition.tags.isNotEmpty() &&
                item.itemMeta?.persistentDataContainer?.get(resourceIdKey, PersistentDataType.STRING) == definition.id

        private fun message(player: Player?, key: String): String =
            CCSystem.getAPI().getI18nString(player, key).replace('&', '§')
    }
}
