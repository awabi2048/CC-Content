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
    private data class Definition(val id: String, val model: Material, val tags: Set<String>)

    private val resourceIdKey = NamespacedKey(plugin, "resource_id")
    private val resourceTagsKey = NamespacedKey(plugin, "resource_tags")
    private val definitions = listOf(
        Definition("mica_flake", Material.AMETHYST_SHARD, setOf("mineral", "insulating")),
        Definition("resin", Material.SLIME_BALL, setOf("wood", "resin")),
        Definition("straw", Material.PAPER, setOf("plant", "fiber"))
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

        override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)

        override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
            val item = ItemStack(Material.POISONOUS_POTATO, amount)
            val meta = item.itemMeta
            meta.displayName(Component.text(message(player, "custom_items.resource.${definition.id}.name"))
                .decoration(TextDecoration.ITALIC, false))
            meta.lore(listOf(Component.text(message(player, "custom_items.resource.${definition.id}.description"))
                .decoration(TextDecoration.ITALIC, false)))
            meta.persistentDataContainer.set(resourceIdKey, PersistentDataType.STRING, definition.id)
            meta.persistentDataContainer.set(
                resourceTagsKey,
                PersistentDataType.STRING,
                definition.tags.sorted().joinToString(",")
            )
            item.itemMeta = meta
            return item
        }

        override fun matches(item: ItemStack): Boolean =
            item.itemMeta?.persistentDataContainer?.get(resourceIdKey, PersistentDataType.STRING) == definition.id

        private fun message(player: Player?, key: String): String =
            CCSystem.getAPI().getI18nString(player, key).replace('&', '§')
    }
}
