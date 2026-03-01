package jp.awabi2048.cccontent.items.misc

import jp.awabi2048.cccontent.items.CustomItem
import jp.awabi2048.cccontent.items.PoisonousPotatoComponentPack
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.block.Block
import org.bukkit.block.Container
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import java.util.Base64
import java.util.UUID

private object StorageBoxKeys {
    val BOX_MARKER = NamespacedKey("cccontent", "storage_box_marker")
    val CAPACITY = NamespacedKey("cccontent", "storage_box_capacity")
    val ENTRIES = NamespacedKey("cccontent", "storage_box_entries")
    val SELECTED = NamespacedKey("cccontent", "storage_box_selected")
    val AUTO_STORE = NamespacedKey("cccontent", "storage_box_auto")
    val USE_FROM_BOX = NamespacedKey("cccontent", "storage_box_use")
    val CONTAINER_IO = NamespacedKey("cccontent", "storage_box_container_io")
    val INSTANCE_ID = NamespacedKey("cccontent", "storage_box_instance")
}

private data class StorageEntry(
    val prototype: ItemStack,
    var count: Long
)

private data class StorageBoxState(
    val capacity: Int,
    val entries: MutableList<StorageEntry?>,
    var selectedIndex: Int,
    var autoStore: Boolean,
    var useFromBox: Boolean,
    var containerIo: Boolean
) {
    fun nonEmptyIndices(): List<Int> = entries.indices.filter { idx -> entries[idx]?.count ?: 0L > 0L }
    fun hasTemplate(): Boolean = entries.any { (it?.count ?: 0L) > 0L }

    fun selectedEntryOrNull(): StorageEntry? {
        val entry = entries.getOrNull(selectedIndex) ?: return null
        return if (entry.count > 0L) entry else null
    }

    fun normalizeSelection() {
        val nonEmpty = nonEmptyIndices()
        if (nonEmpty.isEmpty()) {
            selectedIndex = 0
            return
        }
        if (selectedIndex in nonEmpty) return
        selectedIndex = nonEmpty.first()
    }
}

private object StorageBoxCodec {
    private const val RECORD_SEPARATOR = "|"
    private const val FIELD_SEPARATOR = ","
    private const val MAX_ITEMS_PER_SLOT = 64L * 4096L
    private const val LORE_SEPARATOR = "§8§m――――――――――――――――――――――――"

    fun isStorageBox(item: ItemStack?): Boolean {
        val stack = item ?: return false
        val meta = stack.itemMeta ?: return false
        return meta.persistentDataContainer.has(StorageBoxKeys.BOX_MARKER, PersistentDataType.BYTE)
    }

    fun getCapacity(item: ItemStack): Int {
        val meta = item.itemMeta ?: return 1
        return meta.persistentDataContainer.get(StorageBoxKeys.CAPACITY, PersistentDataType.INTEGER)?.coerceIn(1, 9) ?: 1
    }

    fun getInstanceId(item: ItemStack?): String? {
        val stack = item ?: return null
        val meta = stack.itemMeta ?: return null
        return meta.persistentDataContainer.get(StorageBoxKeys.INSTANCE_ID, PersistentDataType.STRING)
    }

    fun maxItemsPerSlot(): Long = MAX_ITEMS_PER_SLOT

    fun createHoldActionBar(item: ItemStack): Component? {
        if (!isStorageBox(item)) return null

        val state = loadState(item)
        val selected = state.selectedEntryOrNull()
        val infoEntry = selected ?: state.entries.getOrNull(state.selectedIndex) ?: state.entries.firstOrNull { it != null }
        val itemName = infoEntry?.let {
            val material = it.prototype.type
            val key = if (material.isBlock) {
                "block.minecraft.${material.key.key}"
            } else {
                "item.minecraft.${material.key.key}"
            }
            Component.translatable(key)
                .color(NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false)
        } ?: Component.text("なし", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)

        val count = selected?.count ?: 0L
        val max = maxItemsPerSlot()
        return Component.empty()
            .append(itemName)
            .append(Component.text(" "))
            .append(Component.text(count.toString(), NamedTextColor.AQUA))
            .append(Component.text("/$max", NamedTextColor.GRAY))
    }

    fun loadState(item: ItemStack): StorageBoxState {
        val capacity = getCapacity(item)
        val meta = item.itemMeta ?: return StorageBoxState(capacity, MutableList(capacity) { null }, 0, false, true, true)
        val pdc = meta.persistentDataContainer
        val entries = MutableList<StorageEntry?>(capacity) { null }
        val raw = pdc.get(StorageBoxKeys.ENTRIES, PersistentDataType.STRING)

        if (!raw.isNullOrBlank()) {
            raw.split(RECORD_SEPARATOR).forEach { record ->
                val parts = record.split(FIELD_SEPARATOR, limit = 3)
                if (parts.size < 3) return@forEach
                val index = parts[0].toIntOrNull() ?: return@forEach
                val count = parts[1].toLongOrNull() ?: return@forEach
                if (index !in 0 until capacity || count <= 0L) return@forEach
                val decoded = try {
                    Base64.getDecoder().decode(parts[2])
                } catch (_: IllegalArgumentException) {
                    return@forEach
                }
                val prototype = try {
                    ItemStack.deserializeBytes(decoded)
                } catch (_: Exception) {
                    return@forEach
                }
                if (prototype.type == Material.AIR) return@forEach
                prototype.amount = 1
                entries[index] = StorageEntry(prototype, count.coerceAtMost(MAX_ITEMS_PER_SLOT))
            }
        }

        val selected = pdc.get(StorageBoxKeys.SELECTED, PersistentDataType.INTEGER)?.coerceIn(0, capacity - 1) ?: 0
        val autoStore = pdc.get(StorageBoxKeys.AUTO_STORE, PersistentDataType.BYTE)?.toInt() == 1
        val useFromBox = pdc.get(StorageBoxKeys.USE_FROM_BOX, PersistentDataType.BYTE)?.toInt() != 0
        val containerIo = pdc.get(StorageBoxKeys.CONTAINER_IO, PersistentDataType.BYTE)?.toInt() != 0

        val state = StorageBoxState(capacity, entries, selected, autoStore, useFromBox, containerIo)
        state.normalizeSelection()
        return state
    }

    fun saveState(item: ItemStack, state: StorageBoxState) {
        val meta = item.itemMeta ?: return
        val pdc = meta.persistentDataContainer
        val records = mutableListOf<String>()
        for (i in 0 until state.capacity) {
            val entry = state.entries[i] ?: continue
            if (entry.count <= 0L) continue
            val prototype = entry.prototype.clone().apply { amount = 1 }
            val bytes = prototype.serializeAsBytes()
            val b64 = Base64.getEncoder().encodeToString(bytes)
            records += "$i,${"${entry.count}"},$b64"
        }

        state.normalizeSelection()
        pdc.set(StorageBoxKeys.ENTRIES, PersistentDataType.STRING, records.joinToString(RECORD_SEPARATOR))
        pdc.set(StorageBoxKeys.SELECTED, PersistentDataType.INTEGER, state.selectedIndex)
        pdc.set(StorageBoxKeys.AUTO_STORE, PersistentDataType.BYTE, if (state.autoStore) 1 else 0)
        pdc.set(StorageBoxKeys.USE_FROM_BOX, PersistentDataType.BYTE, if (state.useFromBox) 1 else 0)
        pdc.set(StorageBoxKeys.CONTAINER_IO, PersistentDataType.BYTE, if (state.containerIo) 1 else 0)
        item.itemMeta = meta
    }

    fun initializeMeta(item: ItemStack, capacity: Int) {
        val meta = item.itemMeta ?: return
        val pdc = meta.persistentDataContainer
        pdc.set(StorageBoxKeys.BOX_MARKER, PersistentDataType.BYTE, 1)
        pdc.set(StorageBoxKeys.CAPACITY, PersistentDataType.INTEGER, capacity)
        if (!pdc.has(StorageBoxKeys.INSTANCE_ID, PersistentDataType.STRING)) {
            pdc.set(StorageBoxKeys.INSTANCE_ID, PersistentDataType.STRING, UUID.randomUUID().toString())
        }
        if (!pdc.has(StorageBoxKeys.USE_FROM_BOX, PersistentDataType.BYTE)) {
            pdc.set(StorageBoxKeys.USE_FROM_BOX, PersistentDataType.BYTE, 1)
        }
        if (!pdc.has(StorageBoxKeys.CONTAINER_IO, PersistentDataType.BYTE)) {
            pdc.set(StorageBoxKeys.CONTAINER_IO, PersistentDataType.BYTE, 1)
        }
        item.itemMeta = meta
    }

    fun applyVisual(item: ItemStack, openedInventory: Boolean): ItemStack {
        if (!isStorageBox(item)) return item

        val amount = item.amount.coerceAtLeast(1)
        val capacity = getCapacity(item)
        val instanceId = getInstanceId(item)
        val state = loadState(item)
        val selected = state.selectedEntryOrNull()
        val selectedTemplate = state.entries.getOrNull(state.selectedIndex) ?: state.entries.firstOrNull { it != null }
        val infoEntry = selected ?: selectedTemplate
        val selectedType = infoEntry?.prototype?.type ?: Material.CHEST

        val forcePotatoVisual = openedInventory || !state.useFromBox

        val visual = if (forcePotatoVisual) {
            ItemStack(Material.POISONOUS_POTATO, amount)
        } else {
            (selected?.prototype?.clone() ?: ItemStack(Material.CHEST)).apply { this.amount = amount }
        }

        initializeMeta(visual, capacity)
        if (instanceId != null) {
            val meta = visual.itemMeta
            meta?.persistentDataContainer?.set(StorageBoxKeys.INSTANCE_ID, PersistentDataType.STRING, instanceId)
            if (meta != null) {
                visual.itemMeta = meta
            }
        }
        saveState(visual, state)

        val visualMeta = visual.itemMeta
        if (visualMeta != null) {
            val count = selected?.count ?: 0L
            val maxCount = maxItemsPerSlot()
            val countColor = if (count >= maxCount) "§c" else "§b"
            val stackBase = (infoEntry?.prototype?.maxStackSize ?: 64).coerceAtLeast(1)
            val stacks = count / stackBase
            val remainder = count % stackBase
            val autoPickup = if (state.autoStore) "§aオン" else "§cオフ"
            val storedName = infoEntry?.let { getTranslatedItemNameComponent(it.prototype.type) }
                ?: Component.text("なし").decoration(TextDecoration.ITALIC, false)

            visualMeta.displayName(Component.text("Storage Box").decoration(TextDecoration.ITALIC, false))
            visualMeta.lore(
                listOf(
                    Component.text(LORE_SEPARATOR).decoration(TextDecoration.ITALIC, false),
                    Component.text("§f§l| §7収納中 ").decoration(TextDecoration.ITALIC, false).append(storedName),
                    Component.text("§f§l| §7個数 ${countColor}$count§7/$maxCount §7(${stacks}st + $remainder)").decoration(TextDecoration.ITALIC, false),
                    Component.text("§f§l| §7自動回収 $autoPickup").decoration(TextDecoration.ITALIC, false),
                    Component.text(LORE_SEPARATOR).decoration(TextDecoration.ITALIC, false)
                )
            )
            if (forcePotatoVisual) {
                visualMeta.setItemModel(NamespacedKey.minecraft(selectedType.key.key))
            }
            visual.itemMeta = visualMeta
        }

        if (forcePotatoVisual) {
            PoisonousPotatoComponentPack.applyNonConsumable(visual)
        }

        return visual
    }

    fun updateAllVisuals(player: Player, openedInventory: Boolean) {
        val inv = player.inventory
        for (slot in 0 until inv.size) {
            val stack = inv.getItem(slot) ?: continue
            if (!isStorageBox(stack)) continue
            val updated = applyVisual(stack, openedInventory)
            inv.setItem(slot, updated)
        }
    }

    fun storeStack(state: StorageBoxState, stack: ItemStack): Int {
        if (stack.type == Material.AIR || stack.amount <= 0) return 0
        if (stack.maxStackSize <= 1) return 0
        val amount = stack.amount

        for (i in 0 until state.capacity) {
            val entry = state.entries[i] ?: continue
            if (!entry.prototype.isSimilar(stack)) continue
            val base = entry.count.coerceAtLeast(0L)
            val space = (MAX_ITEMS_PER_SLOT - base).coerceAtLeast(0L)
            if (space <= 0L) return 0
            val inserted = minOf(amount.toLong(), space).toInt()
            entry.count = base + inserted.toLong()
            if (state.nonEmptyIndices().size == 1) {
                state.selectedIndex = i
            }
            return inserted
        }

        val emptyIndex = (0 until state.capacity).firstOrNull { idx ->
            val entry = state.entries[idx]
            entry == null || entry.count <= 0L
        } ?: return 0

        val inserted = minOf(amount.toLong(), MAX_ITEMS_PER_SLOT).toInt()
        if (inserted <= 0) return 0

        val prototype = stack.clone().apply { this.amount = 1 }
        state.entries[emptyIndex] = StorageEntry(prototype, inserted.toLong())
        if (state.nonEmptyIndices().size == 1) {
            state.selectedIndex = emptyIndex
        }
        return inserted
    }

    fun storeStackExistingOnly(state: StorageBoxState, stack: ItemStack): Int {
        if (stack.type == Material.AIR || stack.amount <= 0) return 0
        if (stack.maxStackSize <= 1) return 0

        val amount = stack.amount
        for (i in 0 until state.capacity) {
            val entry = state.entries[i] ?: continue
            if (entry.count <= 0L) continue
            if (!entry.prototype.isSimilar(stack)) continue

            val space = (MAX_ITEMS_PER_SLOT - entry.count).coerceAtLeast(0L)
            if (space <= 0L) return 0
            val inserted = minOf(amount.toLong(), space).toInt()
            entry.count += inserted.toLong()
            return inserted
        }

        return 0
    }

    fun storeAllFromContainer(container: Inventory, boxState: StorageBoxState, existingOnly: Boolean = true): Int {
        var moved = 0

        for (slot in 0 until container.size) {
            val stack = container.getItem(slot) ?: continue
            if (stack.type == Material.AIR) continue
            if (isStorageBox(stack)) continue

            val inserted = if (existingOnly) {
                storeStackExistingOnly(boxState, stack)
            } else {
                storeStack(boxState, stack)
            }
            if (inserted <= 0) continue

            moved += inserted
            if (inserted >= stack.amount) {
                container.setItem(slot, null)
            } else {
                stack.amount -= inserted
                container.setItem(slot, stack)
            }
        }

        return moved
    }

    fun moveSelectedToContainer(container: Inventory, state: StorageBoxState): Int {
        val entry = state.selectedEntryOrNull() ?: return 0
        val maxPerStack = entry.prototype.maxStackSize.coerceAtLeast(1)
        var moved = 0

        while (entry.count > 0L) {
            val request = minOf(maxPerStack.toLong(), entry.count).toInt()
            if (request <= 0) break

            val out = entry.prototype.clone().apply { amount = request }
            val overflow = container.addItem(out)
            val overflowAmount = overflow.values.sumOf { it.amount }
            val accepted = request - overflowAmount
            if (accepted <= 0) break

            moved += accepted
            entry.count -= accepted.toLong()
            if (accepted < request) break
        }

        if (entry.count <= 0L) {
            val idx = state.entries.indexOf(entry)
            if (idx >= 0) {
                state.entries[idx] = null
            }
        }
        state.normalizeSelection()
        return moved
    }

    fun storeAllFromInventory(player: Player, protectedSlot: Int, boxState: StorageBoxState, existingOnly: Boolean = false): Int {
        val inv = player.inventory
        var moved = 0

        for (slot in 0 until inv.size) {
            if (slot == protectedSlot) continue
            val stack = inv.getItem(slot) ?: continue
            if (stack.type == Material.AIR) continue
            if (isStorageBox(stack)) continue

            val inserted = if (existingOnly) {
                storeStackExistingOnly(boxState, stack)
            } else {
                storeStack(boxState, stack)
            }
            if (inserted <= 0) continue

            moved += inserted
            if (inserted >= stack.amount) {
                inv.setItem(slot, null)
            } else {
                stack.amount -= inserted
                inv.setItem(slot, stack)
            }
        }

        return moved
    }

    fun withdrawSelectedToInventory(player: Player, boxState: StorageBoxState): Int {
        return withdrawFromIndexToInventory(player, boxState, boxState.selectedIndex, fillInventory = false)
    }

    fun withdrawFromIndexToInventory(player: Player, boxState: StorageBoxState, index: Int, fillInventory: Boolean): Int {
        val entry = boxState.entries.getOrNull(index) ?: return 0
        if (entry.count <= 0L) return 0

        val maxPerStack = entry.prototype.maxStackSize.coerceAtLeast(1)
        var moved = 0

        if (!fillInventory) {
            val request = minOf(maxPerStack.toLong(), entry.count).toInt()
            if (request <= 0) return 0
            val out = entry.prototype.clone().apply { amount = request }
            val overflow = player.inventory.addItem(out)
            val overflowAmount = overflow.values.sumOf { it.amount }
            moved = request - overflowAmount
        } else {
            var remaining = entry.count
            while (remaining > 0L) {
                val request = minOf(maxPerStack.toLong(), remaining).toInt()
                val out = entry.prototype.clone().apply { amount = request }
                val overflow = player.inventory.addItem(out)
                val overflowAmount = overflow.values.sumOf { it.amount }
                val accepted = request - overflowAmount
                if (accepted <= 0) break
                moved += accepted
                remaining -= accepted.toLong()
                if (accepted < request) break
            }
        }

        if (moved <= 0) return 0

        entry.count = (entry.count - moved.toLong()).coerceAtLeast(0L)
        if (entry.count <= 0L) {
            boxState.entries[index] = null
        }

        boxState.normalizeSelection()
        return moved
    }

    fun moveSelectionToNextNonEmpty(state: StorageBoxState, forward: Boolean): Int? {
        val nonEmpty = state.nonEmptyIndices()
        if (nonEmpty.isEmpty()) return null
        if (nonEmpty.size == 1) {
            state.selectedIndex = nonEmpty.first()
            return state.selectedIndex
        }

        val currentPos = nonEmpty.indexOf(state.selectedIndex).let { if (it >= 0) it else 0 }
        val nextPos = if (forward) (currentPos + 1) % nonEmpty.size else (currentPos - 1 + nonEmpty.size) % nonEmpty.size
        state.selectedIndex = nonEmpty[nextPos]
        return state.selectedIndex
    }

    fun decrementSelected(state: StorageBoxState, amount: Long): Boolean {
        if (amount <= 0L) return false
        val selected = state.selectedEntryOrNull() ?: return false
        if (selected.count < amount) return false
        selected.count -= amount
        if (selected.count <= 0L) {
            val idx = state.entries.indexOf(selected)
            if (idx >= 0) {
                state.entries[idx] = null
            }
        }
        state.normalizeSelection()
        return true
    }

    private fun getTranslatedItemNameComponent(material: Material): Component {
        val key = if (material.isBlock) {
            "block.minecraft.${material.key.key}"
        } else {
            "item.minecraft.${material.key.key}"
        }
        return Component.translatable(key)
            .color(NamedTextColor.YELLOW)
            .decoration(TextDecoration.ITALIC, false)
    }
}

abstract class BaseStorageBoxItem(
    override val id: String,
    private val capacity: Int,
    private val defaultDisplayName: String,
    private val defaultLore: List<String>
) : CustomItem {
    override val feature: String = "misc"
    override val displayName: String = defaultDisplayName
    override val lore: List<String> = defaultLore

    override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)

    override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
        val item = ItemStack(Material.POISONOUS_POTATO, amount.coerceAtLeast(1))
        StorageBoxCodec.initializeMeta(item, capacity)
        StorageBoxCodec.saveState(item, StorageBoxState(capacity, MutableList(capacity) { null }, 0, false, true, true))
        return StorageBoxCodec.applyVisual(item, openedInventory = false)
    }

    override fun matches(item: ItemStack): Boolean = StorageBoxCodec.isStorageBox(item)
}

class StorageBoxSingleItem : BaseStorageBoxItem(
    id = "storage_box_single",
    capacity = 1,
    defaultDisplayName = "§6ストレージボックス(1)",
    defaultLore = listOf(
        "§7大量のアイテムを格納できます。",
        "§7手に持って§eShiftを2回§7でメニューを開きます。"
    )
)

class StorageBoxTripleItem : BaseStorageBoxItem(
    id = "storage_box_triple",
    capacity = 3,
    defaultDisplayName = "§6ストレージボックス(3)",
    defaultLore = listOf(
        "§7最大3種類のアイテムを大量に格納できます。",
        "§7手に持って§eShiftを2回§7でメニューを開きます。",
        "§7§eShift+スクロール§7で使用対象を切り替えます。"
    )
)

private enum class StorageBoxOpenHand {
    MAIN,
    OFF
}

private class StorageBoxMenuHolder(
    val owner: UUID,
    val sourceHand: StorageBoxOpenHand,
    val sourceSlot: Int,
    val instanceId: String,
    private val inventory: Inventory
) : InventoryHolder {
    override fun getInventory(): Inventory = inventory
}

private data class PendingBoxUse(
    val sourceHand: StorageBoxOpenHand,
    val mainSlot: Int,
    val instanceId: String,
    val selectedType: Material,
    val beforeUse: ItemStack,
    val expiresAtMs: Long
)

private data class OpenMenuTarget(
    val hand: StorageBoxOpenHand,
    val mainSlot: Int,
    val item: ItemStack
)

private data class StorageBoxLocation(
    val hand: StorageBoxOpenHand,
    val mainSlot: Int,
    val item: ItemStack
)

class StorageBoxGuiListener(private val plugin: JavaPlugin) : Listener {
    private val pendingUseLifetimeMs = 6000L
    private val inventoryOpenedPlayers = mutableSetOf<UUID>()
    private val openedVisualSyncQueued = mutableSetOf<UUID>()
    private val pendingBoxUses = mutableMapOf<UUID, PendingBoxUse>()
    private val suppressSwapOpenPlayers = mutableSetOf<UUID>()
    private val lastMenuClickSoundAt = mutableMapOf<UUID, Long>()
    private val actionBarVisiblePlayers = mutableSetOf<UUID>()

    private val menuTitle = "§8ストレージボックス"
    private val menuSize = 45

    init {
        Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            for (player in Bukkit.getOnlinePlayers()) {
                updateHoldActionBar(player)
            }
        }, 1L, 1L)
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            StorageBoxCodec.updateAllVisuals(player, openedInventory = false)
        }, 1L)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val uuid = event.player.uniqueId
        inventoryOpenedPlayers.remove(uuid)
        openedVisualSyncQueued.remove(uuid)
        pendingBoxUses.remove(uuid)
        suppressSwapOpenPlayers.remove(uuid)
        lastMenuClickSoundAt.remove(uuid)
        actionBarVisiblePlayers.remove(uuid)
    }

    @EventHandler
    fun onInventoryOpen(event: InventoryOpenEvent) {
        val player = event.player as? Player ?: return
        markInventoryOpened(player)
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        inventoryOpenedPlayers.remove(player.uniqueId)
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (inventoryOpenedPlayers.contains(player.uniqueId)) return@Runnable
            StorageBoxCodec.updateAllVisuals(player, openedInventory = false)
        }, 1L)
    }

    @EventHandler(ignoreCancelled = true)
    fun onSwapHandItems(event: PlayerSwapHandItemsEvent) {
        val player = event.player
        if (consumeSwapOpenSuppression(player.uniqueId)) return
        if (isInventoryOpened(player)) return

        val mainItem = event.mainHandItem
        val offItem = event.offHandItem
        val targetHand = when {
            StorageBoxCodec.isStorageBox(mainItem) -> StorageBoxOpenHand.MAIN
            StorageBoxCodec.isStorageBox(offItem) -> StorageBoxOpenHand.OFF
            else -> return
        }

        event.isCancelled = true
        openMenu(player, targetHand)
    }

    @EventHandler
    fun onShiftScrollSelect(event: PlayerItemHeldEvent) {
        val player = event.player
        val pending = pendingBoxUses[player.uniqueId]
        if (pending != null && pending.sourceHand == StorageBoxOpenHand.MAIN && event.previousSlot == pending.mainSlot && event.newSlot != pending.mainSlot) {
            pendingBoxUses.remove(player.uniqueId)
        }

        if (!player.isSneaking) return

        val target = player.inventory.getItem(event.previousSlot) ?: return
        if (!StorageBoxCodec.isStorageBox(target)) return
        if (StorageBoxCodec.getCapacity(target) <= 1) return

        player.inventory.heldItemSlot = event.previousSlot

        val state = StorageBoxCodec.loadState(target)
        val diff = event.newSlot - event.previousSlot
        val forward = if (diff == -8) true else if (diff == 8) false else diff > 0
        StorageBoxCodec.moveSelectionToNextNonEmpty(state, forward) ?: return

        StorageBoxCodec.saveState(target, state)
        val updated = StorageBoxCodec.applyVisual(target, openedInventory = isInventoryOpened(player))
        player.inventory.setItem(event.previousSlot, updated)

        player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.9f, 1.8f)
        updateHoldActionBar(player)
    }

    @EventHandler(ignoreCancelled = true)
    fun onInteractUse(event: PlayerInteractEvent) {
        val eventHand = event.hand ?: return
        val sourceHand = when (eventHand) {
            org.bukkit.inventory.EquipmentSlot.HAND -> StorageBoxOpenHand.MAIN
            org.bukkit.inventory.EquipmentSlot.OFF_HAND -> StorageBoxOpenHand.OFF
            else -> return
        }

        val player = event.player
        val mainSlot = player.inventory.heldItemSlot
        if (sourceHand == StorageBoxOpenHand.OFF && StorageBoxCodec.isStorageBox(player.inventory.itemInMainHand)) {
            return
        }
        val usingItem = sourceItem(player, sourceHand, mainSlot) ?: return
        if (!StorageBoxCodec.isStorageBox(usingItem)) return
        if (event.action != org.bukkit.event.block.Action.RIGHT_CLICK_AIR && event.action != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return

        val targetContainer = if (event.action == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            resolveSupportedContainer(event.clickedBlock)
        } else {
            null
        }

        if (targetContainer != null) {
            val state = StorageBoxCodec.loadState(usingItem)
            if (state.containerIo) {
                if (player.isSneaking) {
                    StorageBoxCodec.moveSelectedToContainer(targetContainer, state)
                    StorageBoxCodec.saveState(usingItem, state)
                    val updated = StorageBoxCodec.applyVisual(usingItem, openedInventory = isInventoryOpened(player))
                    setSourceItem(player, sourceHand, mainSlot, updated)
                    updateHoldActionBar(player)
                    event.isCancelled = true
                    return
                }

                if (state.hasTemplate()) {
                    StorageBoxCodec.storeAllFromContainer(targetContainer, state, existingOnly = true)
                    StorageBoxCodec.saveState(usingItem, state)
                    val updated = StorageBoxCodec.applyVisual(usingItem, openedInventory = isInventoryOpened(player))
                    setSourceItem(player, sourceHand, mainSlot, updated)
                    updateHoldActionBar(player)
                }
                return
            }
        }

        val state = StorageBoxCodec.loadState(usingItem)
        if (!state.useFromBox) {
            event.isCancelled = true
            return
        }
        val selectedEntry = state.selectedEntryOrNull()
        if (selectedEntry == null) {
            event.isCancelled = true
            return
        }
        if (isInventoryOpened(player)) return

        val instanceId = StorageBoxCodec.getInstanceId(usingItem) ?: return
        val beforeUse = usingItem.clone()
        val pending = PendingBoxUse(
            sourceHand = sourceHand,
            mainSlot = mainSlot,
            instanceId = instanceId,
            selectedType = selectedEntry.prototype.type,
            beforeUse = beforeUse,
            expiresAtMs = System.currentTimeMillis() + pendingUseLifetimeMs
        )
        pendingBoxUses[player.uniqueId] = pending

        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            val currentPending = pendingBoxUses[player.uniqueId] ?: return@Runnable
            if (currentPending.instanceId != pending.instanceId) return@Runnable

            val applied = finalizeBoxUse(
                player,
                currentPending.sourceHand,
                currentPending.mainSlot,
                currentPending.beforeUse,
                requireSlotChange = true
            )
            if (applied || System.currentTimeMillis() > currentPending.expiresAtMs) {
                pendingBoxUses.remove(player.uniqueId)
            }
        }, 1L)

        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            val currentPending = pendingBoxUses[player.uniqueId] ?: return@Runnable
            if (currentPending.instanceId == pending.instanceId && System.currentTimeMillis() > currentPending.expiresAtMs) {
                pendingBoxUses.remove(player.uniqueId)
            }
        }, (pendingUseLifetimeMs / 50L).coerceAtLeast(1L))
    }

    @EventHandler(ignoreCancelled = true)
    fun onItemConsume(event: PlayerItemConsumeEvent) {
        val player = event.player
        val pending = pendingBoxUses[player.uniqueId]
        if (pending != null) {
            if (System.currentTimeMillis() > pending.expiresAtMs) {
                pendingBoxUses.remove(player.uniqueId)
                return
            }

            val consumedType = event.item.type
            if (consumedType != pending.selectedType && !StorageBoxCodec.isStorageBox(event.item)) {
                return
            }

            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                val currentPending = pendingBoxUses[player.uniqueId]
                if (currentPending == null || currentPending.instanceId != pending.instanceId) return@Runnable
                finalizeBoxUse(
                    player,
                    currentPending.sourceHand,
                    currentPending.mainSlot,
                    currentPending.beforeUse,
                    requireSlotChange = false
                )
                pendingBoxUses.remove(player.uniqueId)
            }, 1L)
            return
        }

        val consumed = event.item
        if (!StorageBoxCodec.isStorageBox(consumed)) return
        val mainSlot = player.inventory.heldItemSlot
        val beforeUse = consumed.clone()
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            finalizeBoxUse(player, StorageBoxOpenHand.MAIN, mainSlot, beforeUse, requireSlotChange = false)
        }, 1L)
    }

    @EventHandler
    fun onPickup(event: EntityPickupItemEvent) {
        val player = event.entity as? Player ?: return
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            runAutoStore(player)
        }, 1L)
    }

    @EventHandler
    fun onAnyInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        if (event.click == ClickType.SWAP_OFFHAND) {
            suppressNextSwapOpen(player.uniqueId)
        }
        ensureOpenedVisualFromInventoryInteraction(player)

        val holder = event.view.topInventory.holder as? StorageBoxMenuHolder
        if (holder != null) {
            if (holder.owner != player.uniqueId) {
                event.isCancelled = true
                return
            }

            if (isMovingOpenedBoxItem(event, holder, player)) {
                event.isCancelled = true
                return
            }

            if (event.rawSlot in 0 until menuSize) {
                event.isCancelled = true
                if (event.click == ClickType.SWAP_OFFHAND) {
                    return
                }
            } else {
                if (handleBottomInventoryStoreClick(event, holder, player)) {
                    return
                }
                return
            }

            val box = resolveOpenedStorageBox(player, holder) ?: run {
                player.closeInventory()
                player.sendMessage("§c手に持っていたストレージボックスが見つかりません")
                return
            }

            val state = StorageBoxCodec.loadState(box)
            val storageSlots = getStorageSlots(state.capacity)
            val hasTemplate = state.hasTemplate()
            when (event.rawSlot) {
                37 -> {
                    if (!hasTemplate) {
                        return
                    }
                    playMenuClick(player)
                    StorageBoxCodec.storeAllFromInventory(player, openedBoxProtectedSlot(player, holder), state, existingOnly = true)
                }
                40 -> {
                    if (!hasTemplate) {
                        return
                    }
                    playMenuClick(player)
                    state.autoStore = !state.autoStore
                    player.sendMessage("§b自動収納: ${if (state.autoStore) "ON" else "OFF"}")
                }
                43 -> {
                    if (!hasTemplate) {
                        return
                    }
                    playMenuClick(player)
                    state.useFromBox = !state.useFromBox
                    player.sendMessage("§dストレージボックスから使用: ${if (state.useFromBox) "ON" else "OFF"}")
                }
                44 -> {
                    playMenuClick(player)
                    state.containerIo = !state.containerIo
                    player.sendMessage("§aコンテナ連携: ${if (state.containerIo) "ON" else "OFF"}")
                }
                in 18..26 -> {
                    val idx = storageSlots.indexOf(event.rawSlot)
                    if (idx < 0) {
                        return
                    }

                    val cursor = event.cursor
                    val currentEntry = state.entries[idx]
                    if (isValidStoreTarget(cursor) && (currentEntry == null || currentEntry.count <= 0L)) {
                        state.entries[idx] = StorageEntry(cursor.clone().apply { amount = 1 }, 1L)
                        if (cursor.amount <= 1) {
                            player.setItemOnCursor(null)
                        } else {
                            cursor.amount -= 1
                            player.setItemOnCursor(cursor)
                        }
                        state.selectedIndex = idx
                        playMenuClick(player)
                    } else if (currentEntry != null && currentEntry.count > 0L && event.click == ClickType.SHIFT_LEFT) {
                        val moved = StorageBoxCodec.withdrawFromIndexToInventory(player, state, idx, fillInventory = false)
                        if (moved > 0) {
                            playMenuClick(player)
                        }
                    } else if (currentEntry != null && currentEntry.count > 0L && event.click == ClickType.SHIFT_RIGHT) {
                        val moved = StorageBoxCodec.withdrawFromIndexToInventory(player, state, idx, fillInventory = true)
                        if (moved > 0) {
                            playMenuClick(player)
                        }
                    } else if (currentEntry != null) {
                        state.selectedIndex = idx
                        playMenuClick(player)
                    }
                }
            }

            StorageBoxCodec.saveState(box, state)
            val updated = StorageBoxCodec.applyVisual(box, openedInventory = true)
            applyOpenedStorageBox(player, holder, updated)
            renderMenu(event.view.topInventory, state)
            return
        }

        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            runAutoStore(player)
        }, 1L)
    }

    private fun handleBottomInventoryStoreClick(event: InventoryClickEvent, holder: StorageBoxMenuHolder, player: Player): Boolean {
        val click = event.click
        if (click != ClickType.SHIFT_LEFT && click != ClickType.SHIFT_RIGHT) return false

        val clickedInventory = event.clickedInventory ?: return false
        if (clickedInventory != player.inventory) return false

        val source = event.currentItem ?: return false
        if (!isValidStoreTarget(source)) return false

        val box = resolveOpenedStorageBox(player, holder) ?: run {
            player.closeInventory()
            player.sendMessage("§c手に持っていたストレージボックスが見つかりません")
            return true
        }

        val state = StorageBoxCodec.loadState(box)
        if (!state.hasTemplate()) {
            val initIndex = state.selectedIndex.coerceIn(0, state.capacity - 1)
            state.entries[initIndex] = StorageEntry(source.clone().apply { amount = 1 }, 0L)
            state.selectedIndex = initIndex
        }
        val amountToStore = when (click) {
            ClickType.SHIFT_LEFT -> source.amount
            ClickType.SHIFT_RIGHT -> ((source.amount + 1) / 2).coerceAtLeast(1)
            else -> 0
        }
        if (amountToStore <= 0) return false

        val request = source.clone().apply { amount = amountToStore }
        val stored = StorageBoxCodec.storeStack(state, request)

        event.isCancelled = true
        if (stored <= 0) {
            player.sendMessage("§eこのアイテムは格納できません（種類上限または対象外）")
            return true
        }

        playMenuClick(player)

        if (stored >= source.amount) {
            clickedInventory.setItem(event.slot, null)
        } else {
            source.amount -= stored
            clickedInventory.setItem(event.slot, source)
        }

        StorageBoxCodec.saveState(box, state)
        val updated = StorageBoxCodec.applyVisual(box, openedInventory = true)
        applyOpenedStorageBox(player, holder, updated)
        renderMenu(event.view.topInventory, state)
        return true
    }

    @EventHandler
    fun onAnyInventoryDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        ensureOpenedVisualFromInventoryInteraction(player)
        val holder = event.view.topInventory.holder as? StorageBoxMenuHolder
        if (holder != null) {
            if (isOpenedStorageBoxItem(event.oldCursor, holder) || isOpenedStorageBoxItem(player.itemOnCursor, holder)) {
                event.isCancelled = true
                return
            }
            if (event.rawSlots.any { it in 0 until menuSize }) {
                event.isCancelled = true
            }
            return
        }
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            runAutoStore(player)
        }, 1L)
    }

    private fun openMenu(player: Player, preferredHand: StorageBoxOpenHand? = null) {
        val target = resolveOpenTarget(player, preferredHand) ?: return
        val box = target.item
        val instanceId = StorageBoxCodec.getInstanceId(box) ?: return
        val openedVisual = StorageBoxCodec.applyVisual(box, openedInventory = true)
        when (target.hand) {
            StorageBoxOpenHand.MAIN -> player.inventory.setItem(target.mainSlot, openedVisual)
            StorageBoxOpenHand.OFF -> player.inventory.setItemInOffHand(openedVisual)
        }

        val state = StorageBoxCodec.loadState(openedVisual)
        val holderInventory = Bukkit.createInventory(null as InventoryHolder?, menuSize, menuTitle)
        val holder = StorageBoxMenuHolder(player.uniqueId, target.hand, target.mainSlot, instanceId, holderInventory)
        val inv = Bukkit.createInventory(holder, menuSize, menuTitle)
        renderMenu(inv, state)
        player.openInventory(inv)
        player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 2.0f)
    }

    private fun renderMenu(inventory: Inventory, state: StorageBoxState) {
        val blackPane = pane(Material.BLACK_STAINED_GLASS_PANE, " ")
        val darkPane = pane(Material.GRAY_STAINED_GLASS_PANE, " ")
        val emptyPane = pane(Material.WHITE_STAINED_GLASS_PANE, " ")

        for (slot in 0 until menuSize) {
            inventory.setItem(slot, darkPane)
        }

        for (slot in 0..8) {
            inventory.setItem(slot, blackPane)
        }
        for (slot in 36..44) {
            inventory.setItem(slot, blackPane)
        }

        for (slot in 18..26) {
            inventory.setItem(slot, darkPane)
        }

        val storageSlots = getStorageSlots(state.capacity)
        for (idx in storageSlots.indices) {
            val slot = storageSlots[idx]
            val entry = state.entries[idx]
            if (entry == null) {
                inventory.setItem(slot, emptyPane)
                continue
            }

            val iconAmount = if (entry.count > 0L) {
                minOf(entry.count, entry.prototype.maxStackSize.toLong()).toInt().coerceAtLeast(1)
            } else {
                1
            }
            val icon = entry.prototype.clone().apply {
                amount = iconAmount
                val m = itemMeta
                val loreLines = (m?.lore() ?: emptyList()) + listOf(
                    Component.text(" "),
                    Component.text("§7格納数: §f${entry.count}"),
                    if (entry.count > 0L) {
                        Component.text("§7Shift左クリック: 1スタック取り出し")
                    } else {
                        Component.text("§7未格納（この種類を収納対象として指定済み）")
                    },
                    if (entry.count > 0L) {
                        Component.text("§7Shift右クリック: インベントリいっぱいまで取り出し")
                    } else {
                        Component.text("§7この種類をShiftクリックで格納できます")
                    },
                    if (idx == state.selectedIndex) Component.text("§a現在選択中") else Component.text("§8未選択")
                )
                m?.lore(loreLines)
                itemMeta = m
            }
            inventory.setItem(slot, icon)
        }

        if (!state.hasTemplate()) {
            inventory.setItem(
                31,
                button(
                    Material.BOOK,
                    "§e収納対象を指定してください",
                    listOf(
                        "§7中央の白スロットをクリックした状態で",
                        "§7カーソルに持ったアイテムを種類登録できます",
                        "§7登録後、Shiftクリックで格納できます"
                    )
                )
            )
        } else {
            inventory.setItem(37, button(Material.CHEST, "§6インベントリ内一括収納", listOf("§7インベントリ内の対象アイテムを格納")))
            inventory.setItem(
                40,
                button(
                    Material.COMPARATOR,
                    "§b自動収納",
                    listOf("§7現在: ${if (state.autoStore) "§aON" else "§cOFF"}", "§7クリックで切替")
                )
            )
            inventory.setItem(
                43,
                button(
                    Material.REDSTONE_BLOCK,
                    "§dストレージボックスから使用",
                    listOf("§7現在: ${if (state.useFromBox) "§aON" else "§cOFF"}", "§7クリックで切替")
                )
            )
        }

        inventory.setItem(
            44,
            button(
                Material.CHEST_MINECART,
                "§aコンテナ連携",
                listOf("§7現在: ${if (state.containerIo) "§aON" else "§cOFF"}", "§7クリックで切替")
            )
        )
    }

    private fun getStorageSlots(capacity: Int): List<Int> {
        val normalized = capacity.coerceIn(1, 9)
        val startColumn = (9 - normalized) / 2
        return (0 until normalized).map { 18 + startColumn + it }
    }

    private fun resolveSupportedContainer(block: Block?): Inventory? {
        val target = block ?: return null
        if (!isSupportedContainerType(target.type)) return null

        val container = target.state as? Container ?: return null
        return container.inventory
    }

    private fun isSupportedContainerType(type: Material): Boolean {
        if (type == Material.CHEST) return true
        if (type == Material.TRAPPED_CHEST) return true
        if (type == Material.BARREL) return true
        if (type.name.endsWith("_SHULKER_BOX")) return true
        return false
    }

    private fun isValidStoreTarget(stack: ItemStack): Boolean {
        if (stack.type == Material.AIR) return false
        if (stack.maxStackSize <= 1) return false
        if (StorageBoxCodec.isStorageBox(stack)) return false
        return stack.amount > 0
    }

    private fun isMovingOpenedBoxItem(event: InventoryClickEvent, holder: StorageBoxMenuHolder, player: Player): Boolean {
        if (isOpenedStorageBoxItem(event.currentItem, holder)) return true
        if (isOpenedStorageBoxItem(event.cursor, holder)) return true

        if (event.click == ClickType.NUMBER_KEY) {
            val hotbar = event.hotbarButton
            if (hotbar in 0..8 && isOpenedStorageBoxItem(player.inventory.getItem(hotbar), holder)) {
                return true
            }
        }

        if (event.click == ClickType.SWAP_OFFHAND && isOpenedStorageBoxItem(player.inventory.itemInOffHand, holder)) {
            return true
        }

        return false
    }

    private fun isOpenedStorageBoxItem(stack: ItemStack?, holder: StorageBoxMenuHolder): Boolean {
        if (!StorageBoxCodec.isStorageBox(stack)) return false
        return StorageBoxCodec.getInstanceId(stack) == holder.instanceId
    }

    private fun playMenuClick(player: Player) {
        val now = System.currentTimeMillis()
        val last = lastMenuClickSoundAt[player.uniqueId] ?: 0L
        if (now - last < 80L) {
            return
        }
        lastMenuClickSoundAt[player.uniqueId] = now
        player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 2.0f)
    }

    private fun updateHoldActionBar(player: Player) {
        val uuid = player.uniqueId
        val actionBar = StorageBoxCodec.createHoldActionBar(player.inventory.itemInMainHand)
        if (actionBar != null) {
            player.sendActionBar(actionBar)
            actionBarVisiblePlayers.add(uuid)
            return
        }

        if (actionBarVisiblePlayers.remove(uuid)) {
            player.sendActionBar(Component.empty())
        }
    }

    private fun button(material: Material, name: String, lore: List<String>): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta
        meta?.displayName(Component.text(name))
        meta?.lore(lore.map { Component.text(it) })
        item.itemMeta = meta
        return item
    }

    private fun pane(material: Material, name: String): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta
        meta?.displayName(Component.text(name))
        meta?.isHideTooltip = true
        item.itemMeta = meta
        return item
    }

    private fun resolveOpenTarget(player: Player, preferredHand: StorageBoxOpenHand? = null): OpenMenuTarget? {
        val mainSlot = player.inventory.heldItemSlot
        val mainStack = player.inventory.getItem(mainSlot)
        val offStack = player.inventory.itemInOffHand

        fun mainTarget(): OpenMenuTarget? {
            if (!StorageBoxCodec.isStorageBox(mainStack)) return null
            return OpenMenuTarget(StorageBoxOpenHand.MAIN, mainSlot, mainStack ?: return null)
        }

        fun offTarget(): OpenMenuTarget? {
            if (!StorageBoxCodec.isStorageBox(offStack)) return null
            return OpenMenuTarget(StorageBoxOpenHand.OFF, -1, offStack)
        }

        return when (preferredHand) {
            StorageBoxOpenHand.MAIN -> mainTarget() ?: offTarget()
            StorageBoxOpenHand.OFF -> offTarget() ?: mainTarget()
            null -> mainTarget() ?: offTarget()
        }
    }

    private fun resolveOpenedStorageBox(player: Player, holder: StorageBoxMenuHolder): ItemStack? {
        val stack = sourceItem(player, holder.sourceHand, holder.sourceSlot) ?: return null

        if (!StorageBoxCodec.isStorageBox(stack)) return null
        if (StorageBoxCodec.getInstanceId(stack) != holder.instanceId) return null
        return stack
    }

    private fun applyOpenedStorageBox(player: Player, holder: StorageBoxMenuHolder, updated: ItemStack) {
        setSourceItem(player, holder.sourceHand, holder.sourceSlot, updated)
    }

    private fun openedBoxProtectedSlot(player: Player, holder: StorageBoxMenuHolder): Int {
        return when (holder.sourceHand) {
            StorageBoxOpenHand.MAIN -> holder.sourceSlot
            StorageBoxOpenHand.OFF -> offHandSlotIndex(player)
        }
    }

    private fun sourceItem(player: Player, hand: StorageBoxOpenHand, mainSlot: Int): ItemStack? {
        return when (hand) {
            StorageBoxOpenHand.MAIN -> player.inventory.getItem(mainSlot)
            StorageBoxOpenHand.OFF -> player.inventory.itemInOffHand
        }
    }

    private fun setSourceItem(player: Player, hand: StorageBoxOpenHand, mainSlot: Int, item: ItemStack?) {
        when (hand) {
            StorageBoxOpenHand.MAIN -> player.inventory.setItem(mainSlot, item)
            StorageBoxOpenHand.OFF -> player.inventory.setItemInOffHand(item)
        }
    }

    private fun isSameSource(aHand: StorageBoxOpenHand, aSlot: Int, bHand: StorageBoxOpenHand, bSlot: Int): Boolean {
        if (aHand != bHand) return false
        return aHand != StorageBoxOpenHand.MAIN || aSlot == bSlot
    }

    private fun offHandSlotIndex(player: Player): Int = player.inventory.size - 1

    private fun suppressNextSwapOpen(playerId: UUID) {
        if (!suppressSwapOpenPlayers.add(playerId)) return
        Bukkit.getScheduler().runTask(plugin, Runnable {
            suppressSwapOpenPlayers.remove(playerId)
        })
    }

    private fun consumeSwapOpenSuppression(playerId: UUID): Boolean {
        return suppressSwapOpenPlayers.remove(playerId)
    }

    private fun runAutoStore(player: Player) {
        val opened = isInventoryOpened(player)
        val inv = player.inventory
        for (slot in 0 until inv.size) {
            val stack = inv.getItem(slot) ?: continue
            if (!StorageBoxCodec.isStorageBox(stack)) continue

            val state = StorageBoxCodec.loadState(stack)
            if (!state.autoStore) continue
            if (!state.hasTemplate()) continue

            val moved = StorageBoxCodec.storeAllFromInventory(player, slot, state)
            if (moved <= 0) continue

            StorageBoxCodec.saveState(stack, state)
            val updated = StorageBoxCodec.applyVisual(stack, openedInventory = opened)
            inv.setItem(slot, updated)
        }
    }

    private fun ensureOpenedVisualFromInventoryInteraction(player: Player) {
        val uuid = player.uniqueId
        if (inventoryOpenedPlayers.contains(uuid)) return
        if (!openedVisualSyncQueued.add(uuid)) return

        Bukkit.getScheduler().runTask(plugin, Runnable {
            openedVisualSyncQueued.remove(uuid)
            if (!player.isOnline) return@Runnable
            if (inventoryOpenedPlayers.add(uuid)) {
                StorageBoxCodec.updateAllVisuals(player, openedInventory = true)
            }
        })
    }

    private fun markInventoryOpened(player: Player) {
        val uuid = player.uniqueId
        inventoryOpenedPlayers.add(uuid)
        openedVisualSyncQueued.remove(uuid)
        StorageBoxCodec.updateAllVisuals(player, openedInventory = true)
    }

    private fun finalizeBoxUse(
        player: Player,
        sourceHand: StorageBoxOpenHand,
        sourceMainSlot: Int,
        beforeUse: ItemStack,
        requireSlotChange: Boolean
    ): Boolean {
        if (!StorageBoxCodec.isStorageBox(beforeUse)) return false

        val inventory = player.inventory
        val currentAtSource = sourceItem(player, sourceHand, sourceMainSlot)
        if (requireSlotChange && isSameStack(beforeUse, currentAtSource)) return false

        val instanceId = StorageBoxCodec.getInstanceId(beforeUse) ?: return false
        val located = findStorageBoxLocationByInstance(player, instanceId)
        val sameSource = located != null && isSameSource(sourceHand, sourceMainSlot, located.hand, located.mainSlot)

        val boxStack = located?.item ?: beforeUse.clone()
        val displaced = if (sameSource) null else currentAtSource?.takeUnless { it.type == Material.AIR }

        val state = StorageBoxCodec.loadState(boxStack)
        if (!state.useFromBox) return false
        if (!StorageBoxCodec.decrementSelected(state, 1L)) return false

        StorageBoxCodec.saveState(boxStack, state)
        val updatedBox = StorageBoxCodec.applyVisual(boxStack, openedInventory = isInventoryOpened(player))

        if (located != null && !sameSource) {
            setSourceItem(player, located.hand, located.mainSlot, null)
        }
        setSourceItem(player, sourceHand, sourceMainSlot, updatedBox)

        if (displaced != null && !isSameStorageInstance(displaced, instanceId)) {
            val overflow = inventory.addItem(displaced)
            overflow.values.forEach { remain ->
                player.world.dropItemNaturally(player.location, remain)
            }
        }

        return true
    }

    private fun findStorageBoxLocationByInstance(player: Player, instanceId: String): StorageBoxLocation? {
        val off = player.inventory.itemInOffHand
        if (isSameStorageInstance(off, instanceId)) {
            return StorageBoxLocation(StorageBoxOpenHand.OFF, -1, off!!)
        }

        val inv = player.inventory
        for (slot in 0 until offHandSlotIndex(player)) {
            val stack = inv.getItem(slot) ?: continue
            if (!isSameStorageInstance(stack, instanceId)) continue
            return StorageBoxLocation(StorageBoxOpenHand.MAIN, slot, stack)
        }

        return null
    }

    private fun isSameStorageInstance(stack: ItemStack?, instanceId: String): Boolean {
        if (!StorageBoxCodec.isStorageBox(stack)) return false
        return StorageBoxCodec.getInstanceId(stack) == instanceId
    }

    private fun isSameStack(a: ItemStack?, b: ItemStack?): Boolean {
        val left = a?.takeUnless { it.type == Material.AIR }
        val right = b?.takeUnless { it.type == Material.AIR }
        if (left == null && right == null) return true
        if (left == null || right == null) return false
        if (left.amount != right.amount) return false
        return left.isSimilar(right)
    }

    private fun isInventoryOpened(player: Player): Boolean = inventoryOpenedPlayers.contains(player.uniqueId)
}
