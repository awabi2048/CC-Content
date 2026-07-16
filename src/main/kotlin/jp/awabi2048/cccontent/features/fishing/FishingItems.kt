package jp.awabi2048.cccontent.features.fishing

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiLoreBlock
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import jp.awabi2048.cccontent.items.CustomItem
import jp.awabi2048.cccontent.items.CustomItemI18n
import jp.awabi2048.cccontent.items.CustomItemManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin

class FishingItems(
    private val plugin: JavaPlugin,
    private val settings: FishingSettings
) {
    private val fishId = NamespacedKey(plugin, "fish_id")
    private val fishWeight = NamespacedKey(plugin, "fish_weight_grams")
    private val fishQuality = NamespacedKey(plugin, "fish_quality")
    private val fishSize = NamespacedKey(plugin, "fish_size_cm")
    private val rodType = NamespacedKey(plugin, "fishing_rod_type")
    private val rodDamage = NamespacedKey(plugin, "fishing_rod_damage")

    fun registerBaseItems() {
        CustomItemManager.unregisterByPrefix("fishing.")
        settings.rods.forEach { CustomItemManager.register(FishingRodCustomItem(it)) }
        settings.baits.forEach { CustomItemManager.register(FishingBaitCustomItem(it)) }
    }

    fun registerDictionary(opener: (Player) -> Unit, hinter: (Player, PlayerInteractEvent) -> Unit) {
        CustomItemManager.register(FishDictionaryCustomItem(opener, hinter))
    }

    fun unregister() {
        CustomItemManager.unregisterByPrefix("fishing.")
    }

    fun resolveRod(item: ItemStack): RodDefinition? {
        if (item.type != Material.FISHING_ROD) return null
        val id = item.itemMeta?.persistentDataContainer?.get(rodType, PersistentDataType.STRING) ?: return null
        return settings.rods.firstOrNull { it.id == id }
    }

    fun isUsableRod(item: ItemStack): Boolean {
        if (item.type != Material.FISHING_ROD) return false
        val rod = resolveRod(item) ?: return true
        val damage = item.itemMeta?.persistentDataContainer?.get(rodDamage, PersistentDataType.INTEGER) ?: 0
        return damage < rod.maxDurability
    }

    fun damageRod(item: ItemStack): Boolean {
        val rod = resolveRod(item) ?: return false
        val meta = item.itemMeta ?: return false
        val damage = (meta.persistentDataContainer.get(rodDamage, PersistentDataType.INTEGER) ?: 0) + 1
        meta.persistentDataContainer.set(rodDamage, PersistentDataType.INTEGER, damage)
        meta.lore(rodLore(null, rod, damage))
        item.itemMeta = meta
        return damage >= rod.maxDurability
    }

    fun resolveBait(item: ItemStack): BaitDefinition? {
        val fullId = CustomItemManager.identify(item)?.fullId ?: return null
        if (!fullId.startsWith("fishing.bait_")) return null
        val id = fullId.removePrefix("fishing.bait_")
        return settings.baits.firstOrNull { it.id == id }
    }

    fun consumeBait(player: Player): BaitDefinition? {
        val item = player.inventory.itemInOffHand
        val bait = resolveBait(item) ?: return null
        if (item.amount <= 1) player.inventory.setItemInOffHand(ItemStack(Material.AIR))
        else item.amount -= 1
        player.playSound(player.location, Sound.ENTITY_GENERIC_EAT, 0.45f, 1.65f)
        return bait
    }

    fun createCatch(player: Player, catch: FishCatch): ItemStack {
        val item = ItemStack(catch.material)
        val meta = item.itemMeta
        meta.displayName(Component.text(message(player, "fishing.catalog.item.${catch.fishId}"))
            .decoration(TextDecoration.ITALIC, false))
        meta.lore(CCSystem.getAPI().getLoreService().render(catchLoreSpec(player, catch, includeName = false)))
        meta.persistentDataContainer.set(fishId, PersistentDataType.STRING, catch.fishId)
        meta.persistentDataContainer.set(fishWeight, PersistentDataType.INTEGER, catch.weightGrams)
        meta.persistentDataContainer.set(fishQuality, PersistentDataType.STRING, catch.quality.id)
        meta.persistentDataContainer.set(fishSize, PersistentDataType.INTEGER, catch.sizeCm)
        item.itemMeta = meta
        return item
    }

    fun catchInformationLines(player: Player, catch: FishCatch): List<Component> =
        CCSystem.getAPI().getLoreService().render(catchLoreSpec(player, catch, includeName = true))

    private fun catchLoreSpec(player: Player, catch: FishCatch, includeName: Boolean): GuiLoreSpec.Blocks =
        GuiLoreSpec.Blocks(listOf(
            GuiLoreBlock(buildList {
                if (includeName) {
                    add(GuiLoreLine.StyledText(
                        message(player, "fishing.catalog.item.${catch.fishId}"),
                        qualityColor(catch.quality),
                        false
                    ))
                }
                add(GuiLoreLine.Text(message(player, "fishing.dictionary.description.${catch.fishId}")))
            }),
            GuiLoreBlock(listOf(
                GuiLoreLine.Data(message(player, "fishing.catch_item.size"), "${catch.sizeCm}cm", "§a"),
                GuiLoreLine.Data(message(player, "fishing.catch_item.weight"), "${catch.weightGrams}g", "§b"),
                GuiLoreLine.Data(
                    message(player, "fishing.catch_item.quality"),
                    catch.quality.stars,
                    qualityColor(catch.quality)
                )
            ))
        ))

    private fun qualityColor(quality: FishQuality): String = when (quality) {
        FishQuality.COMMON -> "§f"
        FishQuality.RARE -> "§a"
        FishQuality.LEGENDARY -> "§d"
    }

    private fun rodLore(player: Player?, rod: RodDefinition, damage: Int): List<Component> =
        CCSystem.getAPI().getLoreService().render(
            GuiLoreSpec.Blocks(listOf(
                GuiLoreBlock(listOf(GuiLoreLine.Text(message(player, "custom_items.fishing.rod_standard.description")))),
                GuiLoreBlock(listOf(
                    GuiLoreLine.Data(message(player, "custom_items.fishing.data.power"), formatPercent(rod.powerMultiplier), "§c"),
                    GuiLoreLine.Data(message(player, "custom_items.fishing.data.finesse"), formatPercent(rod.finesseMultiplier), "§d"),
                    GuiLoreLine.Data(message(player, "custom_items.fishing.data.durability"), "${rod.maxDurability - damage}/${rod.maxDurability}", "§f")
                ))
            ))
        )

    private fun formatPercent(multiplier: Double): String = "${(multiplier * 100.0).toInt()}%"

    private fun message(player: Player?, key: String): String =
        CCSystem.getAPI().getI18nString(player, key).replace('&', '§')

    private inner class FishingRodCustomItem(private val definition: RodDefinition) : CustomItem {
        override val feature = "fishing"
        override val id = "rod_${definition.id}"
        override val displayName = "Fishing Rod"
        override val canStack = false

        override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)

        override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
            val item = ItemStack(Material.FISHING_ROD, 1)
            val meta = item.itemMeta
            meta.displayName(Component.text(message(player, "custom_items.fishing.rod_${definition.id}.name")))
            meta.lore(rodLore(player, definition, 0))
            meta.persistentDataContainer.set(rodType, PersistentDataType.STRING, definition.id)
            meta.persistentDataContainer.set(rodDamage, PersistentDataType.INTEGER, 0)
            item.itemMeta = meta
            return item
        }

        override fun matches(item: ItemStack): Boolean =
            item.type == Material.FISHING_ROD &&
                item.itemMeta?.persistentDataContainer?.get(rodType, PersistentDataType.STRING) == definition.id

        override fun updateLocalization(item: ItemStack, player: Player?) {
            val damage = item.itemMeta?.persistentDataContainer?.get(rodDamage, PersistentDataType.INTEGER) ?: 0
            val localized = createItemForPlayer(player, 1)
            val meta = localized.itemMeta
            meta.persistentDataContainer.set(rodDamage, PersistentDataType.INTEGER, damage)
            meta.lore(rodLore(player, definition, damage))
            item.itemMeta = meta
        }
    }

    private inner class FishingBaitCustomItem(private val definition: BaitDefinition) : CustomItem {
        override val feature = "fishing"
        override val id = "bait_${definition.id}"
        override val displayName = "Fishing Bait"
        override val itemModel = NamespacedKey.minecraft(definition.material.key.key)

        override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)

        override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
            val item = ItemStack(Material.POISONOUS_POTATO, amount)
            val meta = item.itemMeta
            meta.displayName(
                Component.text(
                    CustomItemI18n.text(
                        player,
                        "custom_items.fishing.bait_${definition.id}.name",
                        displayName
                    )
                ).decoration(TextDecoration.ITALIC, false)
            )
            item.itemMeta = meta
            return item
        }

        override fun matches(item: ItemStack): Boolean = false
    }

    private inner class FishDictionaryCustomItem(
        private val opener: (Player) -> Unit,
        private val hinter: (Player, PlayerInteractEvent) -> Unit
    ) : CustomItem {
        override val feature = "fishing"
        override val id = "dictionary"
        override val displayName = "Fish Dictionary"
        override val canStack = false
        override val canPlace = false

        override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)

        override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
            val item = ItemStack(Material.BOOK)
            val meta = item.itemMeta
            meta.displayName(Component.text(message(player, "custom_items.fishing.dictionary.name")))
            meta.lore(CCSystem.getAPI().getLoreService().render(
                GuiLoreSpec.Blocks(listOf(GuiLoreBlock(listOf(
                    GuiLoreLine.Text(message(player, "custom_items.fishing.dictionary.description")),
                    GuiLoreLine.Action(
                        message(player, "custom_items.fishing.dictionary.operation"),
                        message(player, "custom_items.fishing.dictionary.action")
                    ),
                    GuiLoreLine.Action(
                        message(player, "custom_items.fishing.dictionary.hint_operation"),
                        message(player, "custom_items.fishing.dictionary.hint_action")
                    )
                ))))
            ))
            item.itemMeta = meta
            return item
        }

        override fun matches(item: ItemStack): Boolean = false

        override fun onRightClick(player: Player, event: PlayerInteractEvent) {
            event.isCancelled = true
            player.playSound(player.location, Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.25f)
            opener(player)
        }

        override fun onLeftClick(player: Player, event: PlayerInteractEvent) {
            hinter(player, event)
        }
    }
}
