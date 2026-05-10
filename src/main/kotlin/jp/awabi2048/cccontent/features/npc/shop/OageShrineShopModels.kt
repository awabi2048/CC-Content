package jp.awabi2048.cccontent.features.npc.shop

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta

data class OageShrineShopTabDefinition(
    val tabId: String,
    val purchaseLimitDaily: Int?,
    val purchaseLimitWeekly: Int?,
    val items: List<OageShrineShopItemDefinition>
)

data class OageShrineShopItemDefinition(
    val goodsId: String,
    val price: Double,
    val purchaseLimitDaily: Int?,
    val purchaseLimitWeekly: Int?,
    val itemId: String,
    val displayNameOverride: String?,
    val source: OageShrineShopItemSource
)

sealed interface OageShrineShopItemSource {
    fun resolve(plugin: org.bukkit.plugin.java.JavaPlugin, player: Player?): ItemStack?
}

data class HeadDatabaseShopItemSource(
    val hdbId: String
) : OageShrineShopItemSource {
    override fun resolve(plugin: org.bukkit.plugin.java.JavaPlugin, player: Player?): ItemStack? {
        return jp.awabi2048.cccontent.items.misc.HeadDatabaseBridge.getHead(plugin, hdbId)
    }
}

data class CustomItemShopItemSource(
    val rawId: String
) : OageShrineShopItemSource {
    override fun resolve(plugin: org.bukkit.plugin.java.JavaPlugin, player: Player?): ItemStack? {
        val exact = jp.awabi2048.cccontent.items.CustomItemManager.createItemForPlayer(rawId, player, 1)
        if (exact != null) {
            return exact
        }

        val matches = jp.awabi2048.cccontent.items.CustomItemManager.getAllItemIds()
            .filter { it == rawId || it.endsWith(".$rawId") }

        val fullId = matches.singleOrNull() ?: return null
        return jp.awabi2048.cccontent.items.CustomItemManager.createItemForPlayer(fullId, player, 1)
    }
}

data class OageShrineShopResolvedItem(
    val tab: OageShrineShopTabDefinition,
    val item: OageShrineShopItemDefinition,
    val purchaseItem: ItemStack,
    val previewItem: ItemStack,
    val displayName: String,
    val sourceLore: List<String>
)
