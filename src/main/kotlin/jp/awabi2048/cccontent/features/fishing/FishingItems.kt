package jp.awabi2048.cccontent.features.fishing

import com.awabi2048.ccsystem.CCSystem
import org.bukkit.entity.Player
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.persistence.PersistentDataType
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer

class FishingItems(plugin: JavaPlugin) {
    val rodId = NamespacedKey(plugin, "fishing_rod_id")
    val fishId = NamespacedKey(plugin, "fish_id")
    val fishWeight = NamespacedKey(plugin, "fish_weight_grams")
    val fishQuality = NamespacedKey(plugin, "fish_quality")
    val fishSize = NamespacedKey(plugin, "fish_size_cm")

    fun markRod(item: ItemStack): ItemStack {
        require(item.type == Material.FISHING_ROD)
        item.itemMeta = item.itemMeta?.also { it.persistentDataContainer.set(rodId, PersistentDataType.STRING, "cc_content_fishing_rod") }
        return item
    }

    fun isRod(item: ItemStack): Boolean = item.type == Material.FISHING_ROD &&
        (item.itemMeta?.persistentDataContainer?.has(rodId, PersistentDataType.STRING) == true)

    fun createCatch(player: Player, catch: FishCatch): ItemStack {
        val item = ItemStack(catch.material)
        item.itemMeta = item.itemMeta?.also { meta ->
            meta.displayName(LegacyComponentSerializer.legacySection().deserialize(message(player,
                "fishing.catch_item.name",
                "fish" to message(player, "fishing.catalog.item.${catch.fishId}"),
                "quality" to message(player, "fishing.quality.${catch.quality.id}"),
                "weight" to catch.weightGrams
            )))
            meta.persistentDataContainer.set(fishId, PersistentDataType.STRING, catch.fishId)
            meta.persistentDataContainer.set(fishWeight, PersistentDataType.INTEGER, catch.weightGrams)
            meta.persistentDataContainer.set(fishQuality, PersistentDataType.STRING, catch.quality.id)
            meta.persistentDataContainer.set(fishSize, PersistentDataType.INTEGER, catch.sizeCm)
        }
        return item
    }

    private fun message(player: Player, key: String, vararg placeholders: Pair<String, Any?>): String =
        CCSystem.getAPI().getI18nString(
            player,
            key,
            placeholders.associate { (name, value) -> name to (value ?: "null") }
        ).replace('&', '§')
}
