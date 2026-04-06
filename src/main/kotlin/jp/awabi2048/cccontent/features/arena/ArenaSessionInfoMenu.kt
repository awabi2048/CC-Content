package jp.awabi2048.cccontent.features.arena

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import java.util.UUID
import kotlin.math.round

object ArenaSessionInfoLayout {
    const val MENU_SIZE = 45
    val SESSION_SLOTS = listOf(20, 22, 24)
    const val LIFT_SLOT = 38
    const val INFO_SLOT = 40

    val MENU_TITLE: String
        get() = ArenaI18n.text(null, "arena.ui.broadcast.title", "\u00a78\u30a2\u30ea\u30fc\u30ca\u5b9f\u6cc1")
}

class ArenaSessionInfoHolder(
    val viewerId: UUID
) : InventoryHolder {
    var backingInventory: Inventory? = null

    override fun getInventory(): Inventory {
        return backingInventory ?: Bukkit.createInventory(this, ArenaSessionInfoLayout.MENU_SIZE, ArenaSessionInfoLayout.MENU_TITLE)
    }
}

class ArenaSessionInfoMenu(
    private val plugin: JavaPlugin,
    private val arenaManager: ArenaManager
) : Listener {

    private val activeUpdateTasks = mutableMapOf<UUID, BukkitRunnable>()

    fun openMenu(player: Player) {
        val holder = ArenaSessionInfoHolder(player.uniqueId)
        val inventory = Bukkit.createInventory(holder, ArenaSessionInfoLayout.MENU_SIZE, ArenaSessionInfoLayout.MENU_TITLE)
        holder.backingInventory = inventory
        render(inventory)
        player.openInventory(inventory)
        startUpdateTask(player, holder)
    }

    private fun startUpdateTask(player: Player, holder: ArenaSessionInfoHolder) {
        stopUpdateTask(player.uniqueId)
        val task = object : BukkitRunnable() {
            override fun run() {
                if (!player.isOnline) {
                    stopUpdateTask(player.uniqueId)
                    return
                }
                val inv = holder.backingInventory ?: run {
                    stopUpdateTask(player.uniqueId)
                    return
                }
                if (player.openInventory.topInventory != inv) {
                    stopUpdateTask(player.uniqueId)
                    return
                }
                render(inv)
            }
        }
        activeUpdateTasks[player.uniqueId] = task
        task.runTaskTimer(plugin, 10L, 10L)
    }

    private fun stopUpdateTask(playerId: UUID) {
        activeUpdateTasks.remove(playerId)?.cancel()
    }

    private fun render(inventory: Inventory) {
        val sessions = arenaManager.getActiveSessions()
        val maxSlots = ArenaSessionInfoLayout.SESSION_SLOTS.size

        for (i in 0 until maxSlots) {
            val slot = ArenaSessionInfoLayout.SESSION_SLOTS[i]
            if (i < sessions.size) {
                inventory.setItem(slot, buildSessionItem(sessions[i]))
            } else {
                inventory.setItem(slot, buildEmptySlotItem())
            }
        }

        inventory.setItem(ArenaSessionInfoLayout.LIFT_SLOT, buildLiftItem(sessions))
        inventory.setItem(ArenaSessionInfoLayout.INFO_SLOT, buildInfoItem())
    }

    private fun buildSessionItem(session: ArenaSession): ItemStack {
        val themeIcon = arenaManager.getTheme(session.themeId)?.iconMaterial ?: Material.ROTTEN_FLESH
        val item = ItemStack(themeIcon)
        val meta = item.itemMeta ?: return item

        val title = session.inviteMissionTitle
            ?: ArenaI18n.text(null, "arena.ui.broadcast.default_title", "\u00a7f\u30a2\u30ea\u30fc\u30ca\u30af\u30a8\u30b9\u30c8")
        meta.setDisplayName(title)

        val lore = mutableListOf<String>()
        val separator = ArenaI18n.text(null, "arena.ui.separator", "\u00a78\u00a7m\u2015\u2015\u2015\u2015\u2015\u2015\u2015\u2015\u2015\u2015\u2015\u2015\u2015\u2015\u2015\u2015\u2015\u2015\u2015\u2015")
        lore.add(separator)

        val inGetReady = !session.stageStarted || session.startedWaves.isEmpty()
        val waveLine = if (inGetReady) {
            ArenaI18n.text(null, "arena.ui.broadcast.wave.get_ready", "\u00a77\u00ab \u00a76Wave \u25ef (Last Wave) \u00a77\u00bb \u00a7e\u5230\u7740\uff01")
        } else {
            val displayWave = session.startedWaves.maxOrNull() ?: session.currentWave.coerceAtLeast(1)
            val isLastWave = displayWave >= session.waves
            val waveBase = if (isLastWave) {
                ArenaI18n.text(null, "arena.ui.broadcast.wave.last", "\u00a77\u00ab \u00a76Wave \u25ef (Last Wave) \u00a77\u00bb")
            } else {
                ArenaI18n.text(null, "arena.ui.broadcast.wave.normal", "\u00a77\u00ab \u00a76Wave {wave} \u00a77\u00bb", "wave" to displayWave)
            }
            val cleared = session.clearedWaves.contains(displayWave)
            if (cleared) {
                "$waveBase ${ArenaI18n.text(null, "arena.ui.broadcast.wave.clear", "\u00a7dCLEAR")}"
            } else {
                waveBase
            }
        }
        lore.add(waveLine)
        lore.add(separator)
        lore.add(ArenaI18n.text(null, "arena.ui.broadcast.players_header", "\u00a7f\u2759 \u00a77\u53c2\u52a0\u30d7\u30ec\u30a4\u30e4\u30fc"))

        val participantOrder = if (session.sidebarParticipantOrder.isNotEmpty()) {
            session.sidebarParticipantOrder
        } else {
            session.participants.toList()
        }

        for (playerId in participantOrder) {
            val name = Bukkit.getPlayer(playerId)?.name
                ?: session.sidebarParticipantNames[playerId]
                ?: "Unknown"
            val status = arenaManager.resolveParticipantStatus(session, playerId)
            val statusStripped = status.replace("\u00a7.".toRegex(), "")
            val onlinePlayer = Bukkit.getPlayer(playerId)
            if (statusStripped == "ALIVE" && onlinePlayer != null && onlinePlayer.isOnline) {
                val maxHealth = onlinePlayer.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.value ?: 20.0
                val currentHealth = onlinePlayer.health.coerceIn(0.0, maxHealth)
                val hearts = (currentHealth / (maxHealth / 10.0)).let { round(it).toInt() }.coerceIn(0, 10)
                val healthBar = "\u00a7c" + "\u2764".repeat(hearts) + "\u00a78" + "\u2764".repeat(10 - hearts)
                lore.add(ArenaI18n.text(null, "arena.ui.broadcast.player_line", "\u00a77\u30fb \u00a7b{name} {status}", "name" to name, "status" to healthBar))
            } else {
                lore.add(ArenaI18n.text(null, "arena.ui.broadcast.player_line", "\u00a77\u30fb \u00a7b{name} {status}", "name" to name, "status" to status))
            }
        }

        lore.add("")
        lore.add(ArenaI18n.text(null, "arena.ui.broadcast.radio_header", "\u00a7f\u2759 \u00a77\u7121\u7dda"))

        val lastMsg = session.lastOageMessage
        if (lastMsg != null) {
            lore.add("\u00a7a$lastMsg")
        } else {
            lore.add("\u00a77...")
        }

        lore.add(separator)
        meta.lore = lore
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
        item.itemMeta = meta
        return item
    }

    private fun buildEmptySlotItem(): ItemStack {
        val item = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
        val meta = item.itemMeta ?: return item
        meta.setDisplayName(
            ArenaI18n.text(null, "arena.ui.broadcast.empty_slot", "\u00a7e\u4f59\u88d5\u3042\u308a")
        )
        meta.lore = emptyList()
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
        item.itemMeta = meta
        return item
    }

    private fun buildLiftItem(sessions: List<ArenaSession>): ItemStack {
        val hasAnySession = sessions.isNotEmpty()
        val item: ItemStack
        val displayName: String

        if (!hasAnySession) {
            item = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
            displayName = ArenaI18n.text(null, "arena.ui.broadcast.lift.no_session", "\u00a78-")
        } else {
            val anyReady = sessions.any { arenaManager.getLiftStatusForSession(it) == ArenaLiftStatus.READY }
            val anyOccupied = sessions.any { arenaManager.getLiftStatusForSession(it) == ArenaLiftStatus.OCCUPIED }
            when {
                anyOccupied -> {
                    item = ItemStack(Material.RED_STAINED_GLASS_PANE)
                    displayName = ArenaI18n.text(null, "arena.ui.broadcast.lift.occupied", "\u00a7c\u30ea\u30d5\u30c8\u4f7f\u7528\u4e2d")
                }
                anyReady -> {
                    item = ItemStack(Material.GREEN_STAINED_GLASS_PANE)
                    displayName = ArenaI18n.text(null, "arena.ui.broadcast.lift.ready", "\u00a7a\u30ea\u30d5\u30c8\u4f7f\u7528\u53ef")
                }
                else -> {
                    item = ItemStack(Material.YELLOW_STAINED_GLASS_PANE)
                    displayName = ArenaI18n.text(null, "arena.ui.broadcast.lift.preparing", "\u00a7e\u30ea\u30d5\u30c8\u6e96\u5099\u4e2d")
                }
            }
        }

        val meta = item.itemMeta ?: return item
        meta.setDisplayName(displayName)
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
        item.itemMeta = meta
        return item
    }

    private fun buildInfoItem(): ItemStack {
        val item = ItemStack(Material.BOOK)
        val meta = item.itemMeta ?: return item
        meta.setDisplayName(
            ArenaI18n.text(null, "arena.ui.broadcast.info.name", "\u00a7bInfo")
        )
        meta.lore = ArenaI18n.stringList(
            null,
            "arena.ui.broadcast.info.lore",
            listOf(
                "\u00a77\u304a\u3042\u3052\u3061\u3083\u3093\u304c\u57fa\u5730\u304b\u3089",
                "\u00a77\u30b5\u30dd\u30fc\u30c8\u3092\u884c\u3063\u3066\u3044\u307e\u3059"
            )
        )
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
        item.itemMeta = meta
        return item
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val holder = event.view.topInventory.holder as? ArenaSessionInfoHolder ?: return
        event.isCancelled = true
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        val holder = event.view.topInventory.holder as? ArenaSessionInfoHolder ?: return
        event.isCancelled = true
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val holder = event.view.topInventory.holder as? ArenaSessionInfoHolder ?: return
        stopUpdateTask(holder.viewerId)
    }
}
