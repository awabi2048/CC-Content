@file:Suppress("DEPRECATION")

package jp.awabi2048.cccontent.features.minigame.core

import jp.awabi2048.cccontent.items.CustomItem
import jp.awabi2048.cccontent.items.CustomItemI18n
import jp.awabi2048.cccontent.items.PoisonousPotatoComponentPack
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta

class MiniGameManagerItem(
    private val pdc: MiniGamePdc,
    private val configuredGameId: String? = null
) : CustomItem {
    override val feature = "minigame"
    override val id = configuredGameId?.let { "manager_$it" } ?: "manager"
    override val displayName = "§6ミニゲーム管理アイテム"
    override val itemModel = NamespacedKey.minecraft("clock")
    override val canStack = false

    override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)

    override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
        val item = base(amount, player)
        val world = player?.world?.uid ?: UUID_ZERO
        val owner = player?.uniqueId ?: UUID_ZERO
        val gameId = configuredGameId ?: MiniGameRuntime.current?.defaultGameId() ?: MiniGameDefaults.DEFAULT_GAME_ID
        require(gameId in MiniGameSupportedGames.ids) { "unsupported mini-game id: $gameId" }
        pdc.markItem(item, MiniGameItemData(world, owner, gameId, manager = true))
        return item
    }

    override fun matches(item: ItemStack): Boolean = pdc.readItem(item)?.manager == true

    override fun onRightClick(player: Player, event: PlayerInteractEvent) {
        event.isCancelled = true
        MiniGameRuntime.current?.openManager(player, pdc.readItem(event.item))
    }

    private fun base(amount: Int, player: Player?): ItemStack = ItemStack(Material.POISONOUS_POTATO, amount).apply {
        PoisonousPotatoComponentPack.applyNonConsumable(this)
        itemMeta = itemMeta?.also { meta: ItemMeta ->
            meta.setDisplayName(CustomItemI18n.text(player, "custom_items.minigame.manager.name", displayName))
            meta.lore = CustomItemI18n.list(player, "custom_items.minigame.manager.lore", emptyList())
            meta.setItemModel(itemModel)
            meta.setMaxStackSize(1)
        }
    }
}

class MiniGameMarkerItem(
    private val pdc: MiniGamePdc,
    private val markerType: MiniGameMarkerType,
    private val configuredGameId: String? = null
) : CustomItem {
    override val feature = "minigame"
    override val id = if (configuredGameId == null) {
        "marker_${markerType.name.lowercase()}"
    } else {
        "${configuredGameId}_marker_${markerType.name.lowercase()}"
    }
    override val displayName = "§b${configuredGameId ?: "ミニゲーム"} ${markerType.name.lowercase()} marker"
    override val itemModel = NamespacedKey.minecraft("amethyst_shard")
    override val canStack = true

    override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)

    override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
        val item = ItemStack(Material.POISONOUS_POTATO, amount)
        PoisonousPotatoComponentPack.applyNonConsumable(item)
        val fallback = "custom_items.minigame.${markerType.name.lowercase()}"
        item.itemMeta = item.itemMeta?.also { meta ->
            meta.setDisplayName(CustomItemI18n.text(player, "$fallback.name", displayName))
            meta.lore = CustomItemI18n.list(player, "$fallback.lore", emptyList())
            meta.setItemModel(itemModel)
        }
        val world = player?.world?.uid ?: UUID_ZERO
        val owner = player?.uniqueId ?: UUID_ZERO
        val gameId = configuredGameId ?: MiniGameRuntime.current?.defaultGameId() ?: MiniGameDefaults.DEFAULT_GAME_ID
        require(gameId in MiniGameSupportedGames.ids) { "unsupported mini-game id: $gameId" }
        pdc.markItem(item, MiniGameItemData(world, owner, gameId, markerType = markerType))
        return item
    }

    override fun matches(item: ItemStack): Boolean = pdc.readItem(item)?.markerType == markerType
}

object MiniGameDefaults {
    val UUID_ZERO = java.util.UUID(0L, 0L)
    const val DEFAULT_GAME_ID = "race"
}

private val UUID_ZERO = MiniGameDefaults.UUID_ZERO
