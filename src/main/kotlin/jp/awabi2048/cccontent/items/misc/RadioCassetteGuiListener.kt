package jp.awabi2048.cccontent.items.misc

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

object RadioCassettePlaybackManager {
    private lateinit var plugin: JavaPlugin
    private const val PLAY_VOLUME = 4.0f
    private const val PLAY_PITCH = 1.0f

    private data class PlayerState(
        var insertedCassetteId: String? = null,
        var playingCassetteId: String? = null,
        var playbackTask: BukkitTask? = null
    )

    private val states = mutableMapOf<UUID, PlayerState>()

    fun initialize(plugin: JavaPlugin) {
        this.plugin = plugin
    }

    fun shutdown() {
        states.values.forEach { state -> state.playbackTask?.cancel() }
        states.clear()
    }

    fun handleQuit(player: Player) {
        stop(player)
        states.remove(player.uniqueId)
    }

    fun insert(player: Player, cassetteId: String) {
        val state = states.getOrPut(player.uniqueId) { PlayerState() }
        state.insertedCassetteId = cassetteId
    }

    fun getInserted(player: Player): CassetteDefinition? {
        val cassetteId = states[player.uniqueId]?.insertedCassetteId ?: return null
        return RadioCassetteConfig.getById(cassetteId)
    }

    fun getPlaying(player: Player): CassetteDefinition? {
        val cassetteId = states[player.uniqueId]?.playingCassetteId ?: return null
        return RadioCassetteConfig.getById(cassetteId)
    }

    fun isPlaying(player: Player): Boolean {
        return states[player.uniqueId]?.playbackTask != null
    }

    fun start(player: Player): Boolean {
        val state = states.getOrPut(player.uniqueId) { PlayerState() }
        val insertedId = state.insertedCassetteId
        if (insertedId == null) {
            player.sendMessage("§cカセットテープが挿入されていません")
            return false
        }

        val cassette = RadioCassetteConfig.getById(insertedId)
        if (cassette == null) {
            player.sendMessage("§c挿入されたカセット設定が見つかりません")
            return false
        }

        stop(player)
        val periodTicks = (cassette.durationSeconds * 20L).coerceAtLeast(20L)

        player.playSound(player.location, cassette.soundKey, PLAY_VOLUME, PLAY_PITCH)
        val task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            if (!player.isOnline) {
                stop(player)
                return@Runnable
            }
            player.playSound(player.location, cassette.soundKey, PLAY_VOLUME, PLAY_PITCH)
        }, periodTicks, periodTicks)

        state.playingCassetteId = cassette.id
        state.playbackTask = task
        player.sendMessage("§a再生を開始しました: §f${cassette.songName}")
        return true
    }

    fun stop(player: Player): Boolean {
        val state = states[player.uniqueId] ?: return false
        val wasPlaying = state.playbackTask != null

        state.playbackTask?.cancel()
        state.playbackTask = null

        val playing = state.playingCassetteId?.let { RadioCassetteConfig.getById(it) }
        if (playing != null) {
            player.stopSound(playing.soundKey)
        }
        state.playingCassetteId = null
        return wasPlaying
    }
}

object RadioCassetteGui {
    const val CASSETTE_SLOT = 22
    const val START_SLOT = 38
    const val INFO_SLOT = 40
    const val STOP_SLOT = 42

    private const val TITLE = "§0§lラジカセ"

    fun open(player: Player) {
        val holder = RadioCassetteHolder(player.uniqueId)
        val inventory = Bukkit.createInventory(holder, 45, TITLE)
        holder.backingInventory = inventory
        refresh(player, inventory)
        player.openInventory(inventory)
    }

    fun refresh(player: Player, inventory: Inventory) {
        for (slot in 0 until 45) {
            inventory.setItem(slot, createPane(Material.GRAY_STAINED_GLASS_PANE))
        }

        for (slot in 0..8) {
            inventory.setItem(slot, createPane(Material.BLACK_STAINED_GLASS_PANE))
        }
        for (slot in 36..44) {
            inventory.setItem(slot, createPane(Material.BLACK_STAINED_GLASS_PANE))
        }

        val inserted = RadioCassettePlaybackManager.getInserted(player)
        val playing = RadioCassettePlaybackManager.getPlaying(player)
        val infoLore = buildList {
            add("§7挿入中: ${inserted?.songName ?: "なし"}")
            add("§7再生中: ${playing?.songName ?: "なし"}")
            add("§7状態: ${if (RadioCassettePlaybackManager.isPlaying(player)) "再生中" else "停止中"}")
        }
        inventory.setItem(
            START_SLOT,
            createIcon(
                Material.LIME_DYE,
                "§a§l再生開始",
                listOf("§7挿入されているカセットを", "§7ループ再生します")
            )
        )
        inventory.setItem(INFO_SLOT, createIcon(Material.BOOK, "§e§l再生情報", infoLore))
        inventory.setItem(
            STOP_SLOT,
            createIcon(
                Material.RED_DYE,
                "§c§l再生停止",
                listOf("§7現在の再生を停止します")
            )
        )

        if (inserted == null) {
            inventory.setItem(
                CASSETTE_SLOT,
                createIcon(Material.BARRIER, "§7カセットテープ", listOf("§7未挿入", "§7下段インベントリのカセットを", "§7クリックして挿入します"))
            )
        } else {
            val cassetteIcon = CassetteTapeItem(inserted).createItem(1)
            val meta = cassetteIcon.itemMeta
            if (meta != null) {
                val lore = (meta.lore() ?: emptyList()).toMutableList()
                lore.add(Component.text("§a挿入中"))
                meta.lore(lore)
                cassetteIcon.itemMeta = meta
            }
            inventory.setItem(CASSETTE_SLOT, cassetteIcon)
        }
    }

    private fun createPane(material: Material): ItemStack {
        val item = ItemStack(material)
        val meta: ItemMeta? = item.itemMeta
        meta?.setDisplayName(" ")
        meta?.isHideTooltip = true
        item.itemMeta = meta
        return item
    }

    private fun createIcon(material: Material, name: String, lore: List<String>): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item
        meta.displayName(Component.text(name))
        if (lore.isNotEmpty()) {
            meta.lore(lore.map { Component.text(it) })
        }
        item.itemMeta = meta
        return item
    }
}

class RadioCassetteGuiListener : Listener {

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val holder = event.view.topInventory.holder as? RadioCassetteHolder ?: return
        if (holder.owner != player.uniqueId) {
            event.isCancelled = true
            return
        }

        val topInventory = event.view.topInventory
        val topSize = topInventory.size

        if (event.rawSlot in 0 until topSize) {
            event.isCancelled = true
            handleTopInventoryClick(player, event.rawSlot)
            RadioCassetteGui.refresh(player, topInventory)
            return
        }

        if (player.gameMode.name != "CREATIVE") {
            if (!consumeCassetteFromPlayerInventoryClick(player, event)) {
                return
            }
        } else {
            if (!peekCassetteFromPlayerInventoryClick(player, event)) {
                return
            }
        }

        event.isCancelled = true
        RadioCassetteGui.refresh(player, topInventory)
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        val holder = event.view.topInventory.holder as? RadioCassetteHolder ?: return
        val topSize = event.view.topInventory.size
        if (event.rawSlots.any { it in 0 until topSize }) {
            event.isCancelled = true
            return
        }

        val player = event.whoClicked as? Player ?: return
        if (holder.owner != player.uniqueId) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        RadioCassettePlaybackManager.handleQuit(event.player)
    }

    private fun handleTopInventoryClick(player: Player, slot: Int) {
        when (slot) {
            RadioCassetteGui.START_SLOT -> {
                RadioCassettePlaybackManager.start(player)
            }

            RadioCassetteGui.STOP_SLOT -> {
                val stopped = RadioCassettePlaybackManager.stop(player)
                if (stopped) {
                    player.sendMessage("§c再生を停止しました")
                } else {
                    player.sendMessage("§7再生中のカセットはありません")
                }
            }

            RadioCassetteGui.INFO_SLOT -> {
                val inserted = RadioCassettePlaybackManager.getInserted(player)?.songName ?: "なし"
                val playing = RadioCassettePlaybackManager.getPlaying(player)?.songName ?: "なし"
                player.sendMessage("§e[ラジカセ] §7挿入中: §f$inserted§7 / 再生中: §f$playing")
            }

            RadioCassetteGui.CASSETTE_SLOT -> {
                player.sendMessage("§7下段インベントリのカセットをクリックすると挿入できます")
            }
        }
    }

    private fun consumeCassetteFromPlayerInventoryClick(player: Player, event: InventoryClickEvent): Boolean {
        val cassetteDef = resolveCassetteFromClick(player, event) ?: return false
        if (!consumeOneFromClick(player, event)) {
            return false
        }

        RadioCassettePlaybackManager.insert(player, cassetteDef.id)
        player.sendMessage("§aカセットを挿入しました: §f${cassetteDef.songName}")
        return true
    }

    private fun peekCassetteFromPlayerInventoryClick(player: Player, event: InventoryClickEvent): Boolean {
        val cassetteDef = resolveCassetteFromClick(player, event) ?: return false
        RadioCassettePlaybackManager.insert(player, cassetteDef.id)
        player.sendMessage("§aカセットを挿入しました: §f${cassetteDef.songName} §7(クリエイティブのため未消費)")
        return true
    }

    private fun resolveCassetteFromClick(player: Player, event: InventoryClickEvent): CassetteDefinition? {
        val cassetteId = if (event.click == ClickType.NUMBER_KEY) {
            val hotbarItem = player.inventory.getItem(event.hotbarButton)
            RadioCassetteKeys.getCassetteId(hotbarItem)
        } else {
            RadioCassetteKeys.getCassetteId(event.currentItem)
        } ?: return null

        return RadioCassetteConfig.getById(cassetteId)
    }

    private fun consumeOneFromClick(player: Player, event: InventoryClickEvent): Boolean {
        if (event.click == ClickType.NUMBER_KEY) {
            val slot = event.hotbarButton
            val stack = player.inventory.getItem(slot) ?: return false
            if (stack.amount <= 1) {
                player.inventory.setItem(slot, null)
            } else {
                stack.amount -= 1
            }
            return true
        }

        val clickedInventory = event.clickedInventory ?: return false
        if (clickedInventory != event.view.bottomInventory) {
            return false
        }

        val slot = event.slot
        val stack = clickedInventory.getItem(slot) ?: return false
        if (stack.amount <= 1) {
            clickedInventory.setItem(slot, null)
        } else {
            stack.amount -= 1
        }
        return true
    }
}

class RadioCassetteHolder(val owner: UUID) : InventoryHolder {
    lateinit var backingInventory: Inventory
    override fun getInventory(): Inventory = backingInventory
}
