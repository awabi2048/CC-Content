@file:Suppress("DEPRECATION")

package jp.awabi2048.cccontent.features.arena

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiLoreFrame
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreBlock
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.InventoryMenuDefinition
import com.awabi2048.ccsystem.api.gui.InventoryMenuView
import com.awabi2048.ccsystem.api.gui.MenuElement
import com.awabi2048.ccsystem.api.gui.MenuRoute
import com.awabi2048.ccsystem.core.gui.GuiItemMarker
import jp.awabi2048.cccontent.gui.GuiMenuItems
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import java.util.UUID
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer

object ArenaSessionInfoLayout {
    const val MENU_SIZE = 45
    val SESSION_SLOTS = listOf(20, 22, 24)
    const val LIFT_SLOT = 38
    const val INFO_SLOT = 40

    val MENU_TITLE: String
        get() = ArenaI18n.text(null, "arena.ui.broadcast.title")
}

class ArenaSessionInfoMenu(
    private val plugin: JavaPlugin,
    private val arenaManager: ArenaManager
) {

    private val activeUpdateTasks = mutableMapOf<UUID, BukkitRunnable>()

    init {
        CCSystem.getAPI().getMenuRuntimeService().register(
            InventoryMenuDefinition(
                owner = OWNER,
                id = MENU_ID,
                renderer = { renderView() },
                actions = emptyMap()
            )
        )
    }

    fun openMenu(player: Player) {
        CCSystem.getAPI().getMenuRuntimeService().open(player, MenuRoute(OWNER, MENU_ID))
        startUpdateTask(player)
    }

    private fun startUpdateTask(player: Player) {
        stopUpdateTask(player.uniqueId)
        val task = object : BukkitRunnable() {
            override fun run() {
                if (!player.isOnline) {
                    stopUpdateTask(player.uniqueId)
                    return
                }
                val route = CCSystem.getAPI().getMenuNavigationService().currentRoute(player)
                if (route?.owner != OWNER || route.id != MENU_ID) {
                    stopUpdateTask(player.uniqueId)
                    return
                }
                CCSystem.getAPI().getMenuRuntimeService().refresh(player)
            }
        }
        activeUpdateTasks[player.uniqueId] = task
        task.runTaskTimer(plugin, 10L, 10L)
    }

    private fun stopUpdateTask(playerId: UUID) {
        activeUpdateTasks.remove(playerId)?.cancel()
    }

    private fun render(inventory: Inventory) {
        GuiMenuItems.fillFramed(inventory)
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

        inventory.setItem(ArenaSessionInfoLayout.LIFT_SLOT, buildLiftItem())
        inventory.setItem(ArenaSessionInfoLayout.INFO_SLOT, buildInfoItem())
    }

    private fun renderView(): InventoryMenuView {
        val inventory = Bukkit.createInventory(null, ArenaSessionInfoLayout.MENU_SIZE, ArenaSessionInfoLayout.MENU_TITLE)
        render(inventory)
        return InventoryMenuView(
            size = ArenaSessionInfoLayout.MENU_SIZE,
            title = LegacyComponentSerializer.legacySection().deserialize(ArenaSessionInfoLayout.MENU_TITLE),
            elements = (0 until inventory.size).mapNotNull { slot ->
                val item = inventory.getItem(slot) ?: return@mapNotNull null
                MenuElement(slot, item, GuiItemMarker.role(item) ?: GuiElementRole.CONTENT)
            },
            standardFrame = false
        )
    }

    private fun buildSessionItem(session: ArenaSession): ItemStack {
        val themeIcon = arenaManager.getTheme(session.themeId)?.config(session.promoted)?.iconMaterial ?: Material.ROTTEN_FLESH
        val item = ItemStack(themeIcon)
        val meta = item.itemMeta ?: return item

        val title = session.inviteMissionTitle
            ?: ArenaI18n.text(null, "arena.ui.broadcast.default_title")
        meta.setDisplayName(ArenaI18n.text(null, "arena.ui.mission.item_name", "mission" to title))

        val hasArrived = session.participants.any { playerId ->
            Bukkit.getPlayer(playerId)?.world?.name == session.worldName
        }
        val waveLine = if (session.startedWaves.isEmpty()) {
            if (hasArrived) {
                ArenaI18n.text(null, "arena.ui.broadcast.wave.get_ready")
            } else {
                null
            }
        } else {
            val displayWave = session.startedWaves.maxOrNull() ?: session.currentWave.coerceAtLeast(1)
            val isLastWave = displayWave >= session.waves
            val waveBase = if (isLastWave) {
                ArenaI18n.text(null, "arena.ui.broadcast.wave.last")
            } else {
                ArenaI18n.text(null, "arena.ui.broadcast.wave.normal", "wave" to displayWave)
            }
            val cleared = session.clearedWaves.contains(displayWave)
            if (cleared) {
                "$waveBase ${ArenaI18n.text(null, "arena.ui.broadcast.wave.clear")}"
            } else {
                waveBase
            }
        }

        val participantOrder = if (session.sidebarParticipantOrder.isNotEmpty()) {
            session.sidebarParticipantOrder
        } else {
            session.participants.toList()
        }

        val participantLines = participantOrder.map { playerId ->
            val name = Bukkit.getPlayer(playerId)?.name
                ?: session.sidebarParticipantNames[playerId]
                ?: "Unknown"
            val status = arenaManager.resolveParticipantStatus(session, playerId)
            ArenaI18n.text(null, "arena.ui.broadcast.player_line", "name" to name, "status" to status)
        }

        val lastMsg = session.lastOageMessage
        val radioLine = if (lastMsg != null) {
            "\u00a7a$lastMsg"
        } else {
            "\u00a77..."
        }

        meta.lore(
            CCSystem.getAPI().getLoreService().render(
                GuiLoreSpec.Blocks(buildList {
                    waveLine?.let { add(GuiLoreBlock(listOf(GuiLoreLine.Text(it)))) }
                    add(GuiLoreBlock(buildList {
                        add(GuiLoreLine.Text(ArenaI18n.text(null, "arena.ui.broadcast.players_header")))
                        participantLines.forEach { add(GuiLoreLine.Text(it)) }
                        add(GuiLoreLine.Spacer)
                        add(GuiLoreLine.Text(ArenaI18n.text(null, "arena.ui.broadcast.radio_header")))
                        add(GuiLoreLine.Text(radioLine))
                    }))
                })
            )
        )
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
        item.itemMeta = meta
        return item
    }

    private fun buildEmptySlotItem(): ItemStack {
        val item = ItemStack(Material.GLASS)
        val meta = item.itemMeta ?: return item
        meta.setDisplayName(
            ArenaI18n.text(null, "arena.ui.broadcast.empty_slot")
        )
        meta.lore(emptyList())
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
        item.itemMeta = meta
        return item
    }

    private fun buildLiftItem(): ItemStack {
        val (material, displayName) = when (arenaManager.getEntranceLiftStatus()) {
            ArenaLiftStatus.OCCUPIED -> Material.CHEST_MINECART to ArenaI18n.text(null, "arena.ui.broadcast.lift.occupied")
            ArenaLiftStatus.READY -> Material.MINECART to ArenaI18n.text(null, "arena.ui.broadcast.lift.ready")
            ArenaLiftStatus.RETURNING -> Material.FURNACE_MINECART to ArenaI18n.text(null, "arena.ui.broadcast.lift.returning")
            ArenaLiftStatus.UNAVAILABLE -> Material.BARRIER to ArenaI18n.text(null, "arena.ui.broadcast.lift.unavailable")
        }
        val item = ItemStack(material)

        val meta = item.itemMeta ?: return item
        meta.setDisplayName(displayName)
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
        item.itemMeta = meta
        return item
    }

    private fun buildInfoItem(): ItemStack {
        val item = ItemStack(Material.BOOK)
        val meta = item.itemMeta ?: return item
        meta.setDisplayName(
            ArenaI18n.text(null, "arena.ui.broadcast.info.name")
        )
        meta.lore(CCSystem.getAPI().getLoreService().render(GuiLoreSpec.Rich(ArenaI18n.stringList(null, "arena.ui.broadcast.info.lore").map { GuiLoreLine.Text(it) }, GuiLoreFrame.NONE)))
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
        item.itemMeta = meta
        return item
    }

    companion object {
        private const val OWNER = "cc-content"
        private const val MENU_ID = "arena-session-info"
    }
}
