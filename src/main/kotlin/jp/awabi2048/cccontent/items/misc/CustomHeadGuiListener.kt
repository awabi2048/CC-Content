package jp.awabi2048.cccontent.items.misc

import jp.awabi2048.cccontent.gui.SimpleConfirmationDialog
import jp.awabi2048.cccontent.items.CustomItemManager
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.plugin.java.JavaPlugin

class CustomHeadGuiListener(private val plugin: JavaPlugin) : Listener {

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val holder = event.inventory.holder as? CustomHeadSelectionHolder ?: return
        event.isCancelled = true

        if (event.rawSlot < 0 || event.rawSlot >= event.inventory.size) {
            return
        }

        val selected = holder.slotToHead[event.rawSlot] ?: return
        val selectedItem = event.currentItem ?: return
        if (selectedItem.type == Material.AIR) return

        player.closeInventory()
        showConfirmDialog(player, holder.variant, selected)
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        if (event.inventory.holder !is CustomHeadSelectionHolder) return
        if (event.rawSlots.any { it in 0 until event.inventory.size }) {
            event.isCancelled = true
        }
    }

    private fun showConfirmDialog(player: Player, variant: CustomHeadVariant, choice: CustomHeadChoice) {
        val headPreview = HeadDatabaseBridge.getHead(plugin, choice.hdbId)
        if (headPreview == null) {
            player.sendMessage("§cHeadDatabaseからヘッドを取得できませんでした: ${choice.hdbId}")
            player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.8f)
            return
        }

        val resolvedName = choice.displayName
            ?: headPreview.itemMeta?.displayName()?.let { net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(it) }
            ?: "ヘッド(${choice.hdbId})"

        SimpleConfirmationDialog.show(
            player = player,
            title = Component.text("§0§l交換確認"),
            body = Component.text("§f『$resolvedName§f』と交換しますか？\n§7実行するとカスタムヘッド券を1つ消費します。"),
            confirmText = Component.text("§a交換する"),
            cancelText = Component.text("§cキャンセル"),
            onConfirm = { target ->
                executeExchange(target, variant, choice, headPreview)
            }
        )
    }

    private fun executeExchange(
        player: Player,
        variant: CustomHeadVariant,
        choice: CustomHeadChoice,
        headItem: ItemStack
    ) {
        val tokenFullId = "misc.custom_head.${variant.variantId}"
        if (!consumeToken(player, tokenFullId)) {
            player.sendMessage("§c交換に必要なカスタムヘッド券が見つかりませんでした")
            player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.8f)
            return
        }

        val overflow = player.inventory.addItem(headItem.clone().apply { amount = 1 })
        overflow.values.forEach { drop ->
            player.world.dropItemNaturally(player.location, drop)
        }

        player.sendMessage("§aヘッドを受け取りました: §f${choice.hdbId}")
        player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.1f)
    }

    private fun consumeToken(player: Player, tokenFullId: String): Boolean {
        val inv = player.inventory
        for (slot in 0 until inv.size) {
            val stack = inv.getItem(slot) ?: continue
            val identified = CustomItemManager.identify(stack) ?: continue
            if (identified.fullId != tokenFullId) continue

            if (stack.amount > 1) {
                stack.amount -= 1
            } else {
                inv.setItem(slot, null)
            }
            return true
        }
        return false
    }

    companion object {
        fun openSelectionGui(plugin: JavaPlugin, player: Player, variant: CustomHeadVariant) {
            if (!HeadDatabaseBridge.isAvailable(plugin)) {
                player.sendMessage("§cHeadDatabase が有効でないため、このアイテムは使用できません")
                player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.8f)
                return
            }

            val rows = (((variant.heads.size + 6) / 7) + 2).coerceIn(3, 6)
            val size = rows * 9
            val maxHeads = (rows - 2) * 7

            val headCount = variant.heads.size
            if (headCount > maxHeads) {
                plugin.logger.warning(
                    "[CustomHead] head定義が上限を超えています: variant=${variant.variantId}, count=$headCount, max=$maxHeads"
                )
            }

            val slotToHead = linkedMapOf<Int, CustomHeadChoice>()
            val inventory = Bukkit.createInventory(
                null as InventoryHolder?,
                size,
                variant.guiTitle
            )

            val blackPane = createPane(Material.BLACK_STAINED_GLASS_PANE)
            val grayPane = createPane(Material.GRAY_STAINED_GLASS_PANE)

            for (slot in 0..8) {
                inventory.setItem(slot, blackPane)
            }
            val footerStart = (rows - 1) * 9
            for (slot in footerStart until size) {
                inventory.setItem(slot, blackPane)
            }

            for (row in 1 until (rows - 1)) {
                for (col in 0..8) {
                    inventory.setItem(row * 9 + col, grayPane)
                }
            }

            val themeIcon = ItemStack(variant.themeIconMaterial).apply {
                val meta = itemMeta
                meta?.setDisplayName(variant.themeIconName)
                itemMeta = meta
            }
            inventory.setItem(footerStart + 4, themeIcon)

            val heads = variant.heads.take(maxHeads)
            var index = 0
            for (row in 1 until (rows - 1)) {
                for (col in 1..7) {
                    if (index >= heads.size) break
                    val choice = heads[index++]
                    val slot = row * 9 + col
                    val icon = createHeadIcon(plugin, choice)
                    inventory.setItem(slot, icon)
                    slotToHead[slot] = choice
                }
            }

            val holder = CustomHeadSelectionHolder(variant, slotToHead, inventory)
            val holderInventory = Bukkit.createInventory(holder, size, variant.guiTitle)
            for (slot in 0 until size) {
                holderInventory.setItem(slot, inventory.getItem(slot))
            }

            player.openInventory(holderInventory)
        }

        private fun createPane(material: Material): ItemStack {
            val item = ItemStack(material)
            val meta: ItemMeta? = item.itemMeta
            meta?.setDisplayName(" ")
            meta?.isHideTooltip = true
            item.itemMeta = meta
            return item
        }

        private fun createHeadIcon(plugin: JavaPlugin, choice: CustomHeadChoice): ItemStack {
            val base = HeadDatabaseBridge.getHead(plugin, choice.hdbId) ?: ItemStack(Material.BARRIER)
            val meta = base.itemMeta
            choice.displayName?.let { meta?.setDisplayName(it) }
            if (choice.lore.isNotEmpty()) {
                meta?.lore = choice.lore.toMutableList()
            }
            if (base.type == Material.BARRIER) {
                meta?.setDisplayName("§c取得失敗: ${choice.hdbId}")
                val lore = choice.lore.toMutableList()
                lore.add(0, "§7HeadDatabaseのIDを確認してください")
                meta?.lore = lore
            }
            base.itemMeta = meta
            return base
        }
    }
}

private class CustomHeadSelectionHolder(
    val variant: CustomHeadVariant,
    val slotToHead: Map<Int, CustomHeadChoice>,
    private val inventory: Inventory
) : InventoryHolder {
    override fun getInventory(): Inventory = inventory
}
