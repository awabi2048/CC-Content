package jp.awabi2048.cccontent.features.arena

import com.awabi2048.ccsystem.api.localization.generated.ContentArenaKeys

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiItemSpec
import com.awabi2048.ccsystem.api.gui.GuiLoreFrame
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreBlock
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiMenuDisplaySpec
import com.awabi2048.ccsystem.api.gui.GuiNameSpec
import com.awabi2048.ccsystem.api.gui.GuiNameStyle
import com.awabi2048.ccsystem.api.gui.InventoryMenuDefinition
import com.awabi2048.ccsystem.api.gui.InventoryMenuView
import com.awabi2048.ccsystem.api.gui.MenuElement
import com.awabi2048.ccsystem.api.gui.MenuRoute
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import java.util.UUID

object ArenaSessionInfoLayout {
    const val MENU_SIZE = 45
    val SESSION_SLOTS = listOf(20, 22, 24)
    const val LIFT_SLOT = 38
    const val INFO_SLOT = 40

    val MENU_TITLE: String
        get() = ArenaI18n.text(null, ContentArenaKeys.ARENA_UI_BROADCAST_TITLE)
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

    private fun renderElements(): List<MenuElement> {
        val sessions = arenaManager.getActiveSessions()
        return buildList {
            ArenaSessionInfoLayout.SESSION_SLOTS.forEachIndexed { index, slot ->
                if (index < sessions.size) {
                    add(buildSessionElement(slot, sessions[index]))
                } else {
                    add(buildEmptySlotElement(slot))
                }
            }
            add(buildLiftElement())
            add(buildInfoElement())
        }
    }

    private fun renderView(): InventoryMenuView {
        return InventoryMenuView(
            size = ArenaSessionInfoLayout.MENU_SIZE,
            title = elements().title(GuiNameSpec.Text(ArenaSessionInfoLayout.MENU_TITLE, GuiNameStyle.DEFAULT)),
            elements = renderElements(),
            standardFrame = true
        )
    }

    private fun buildSessionElement(slot: Int, session: ArenaSession): MenuElement {
        val themeIcon = arenaManager.getTheme(session.themeId)?.config(session.promoted)?.iconMaterial ?: Material.ROTTEN_FLESH
        val title = session.inviteMissionTitle
            ?: ArenaI18n.text(null, ContentArenaKeys.ARENA_UI_BROADCAST_DEFAULT_TITLE)

        val hasArrived = session.participants.any { playerId ->
            Bukkit.getPlayer(playerId)?.world?.name == session.worldName
        }
        val waveLine = if (session.startedWaves.isEmpty()) {
            if (hasArrived) {
                ArenaI18n.text(null, ContentArenaKeys.ARENA_UI_BROADCAST_WAVE_GET_READY)
            } else {
                null
            }
        } else {
            val displayWave = session.startedWaves.maxOrNull() ?: session.currentWave.coerceAtLeast(1)
            val isLastWave = displayWave >= session.waves
            val waveBase = if (isLastWave) {
                ArenaI18n.text(null, ContentArenaKeys.ARENA_UI_BROADCAST_WAVE_LAST)
            } else {
                ArenaI18n.text(null, ContentArenaKeys.ARENA_UI_BROADCAST_WAVE_NORMAL, "wave" to displayWave)
            }
            val cleared = session.clearedWaves.contains(displayWave)
            if (cleared) {
                "$waveBase ${ArenaI18n.text(null, ContentArenaKeys.ARENA_UI_BROADCAST_WAVE_CLEAR)}"
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
            ArenaI18n.text(null, ContentArenaKeys.ARENA_UI_BROADCAST_PLAYER_LINE, "name" to name, "status" to status)
        }

        val lastMsg = session.lastOageMessage
        val radioLine = if (lastMsg != null) {
            "\u00a7a$lastMsg"
        } else {
            "\u00a77..."
        }

        return display(
            slot = slot,
            material = themeIcon,
            name = ArenaI18n.text(null, ContentArenaKeys.ARENA_UI_MISSION_ITEM_NAME, "mission" to title),
            lore = GuiLoreSpec.Blocks(buildList {
                waveLine?.let { add(GuiLoreBlock(listOf(GuiLoreLine.Text(it)))) }
                add(GuiLoreBlock(buildList {
                    add(GuiLoreLine.Text(ArenaI18n.text(null, ContentArenaKeys.ARENA_UI_BROADCAST_PLAYERS_HEADER)))
                    participantLines.forEach { add(GuiLoreLine.Text(it)) }
                    add(GuiLoreLine.Spacer)
                    add(GuiLoreLine.Text(ArenaI18n.text(null, ContentArenaKeys.ARENA_UI_BROADCAST_RADIO_HEADER)))
                    add(GuiLoreLine.Text(radioLine))
                }))
            }),
        )
    }

    private fun buildEmptySlotElement(slot: Int): MenuElement =
        display(
            slot = slot,
            material = Material.GLASS,
            name = ArenaI18n.text(null, ContentArenaKeys.ARENA_UI_BROADCAST_EMPTY_SLOT),
            lore = GuiLoreSpec.None,
        )

    private fun buildLiftElement(): MenuElement {
        val (material, displayName) = when (arenaManager.getEntranceLiftStatus()) {
            ArenaLiftStatus.OCCUPIED -> Material.CHEST_MINECART to ArenaI18n.text(null, ContentArenaKeys.ARENA_UI_BROADCAST_LIFT_OCCUPIED)
            ArenaLiftStatus.READY -> Material.MINECART to ArenaI18n.text(null, ContentArenaKeys.ARENA_UI_BROADCAST_LIFT_READY)
            ArenaLiftStatus.RETURNING -> Material.FURNACE_MINECART to ArenaI18n.text(null, ContentArenaKeys.ARENA_UI_BROADCAST_LIFT_RETURNING)
            ArenaLiftStatus.UNAVAILABLE -> Material.BARRIER to ArenaI18n.text(null, ContentArenaKeys.ARENA_UI_BROADCAST_LIFT_UNAVAILABLE)
        }
        return display(ArenaSessionInfoLayout.LIFT_SLOT, material, displayName, GuiLoreSpec.None)
    }

    private fun buildInfoElement(): MenuElement =
        display(
            slot = ArenaSessionInfoLayout.INFO_SLOT,
            material = Material.BOOK,
            name = ArenaI18n.text(null, ContentArenaKeys.ARENA_UI_BROADCAST_INFO_NAME),
            lore = GuiLoreSpec.Rich(
                ArenaI18n.stringList(null, ContentArenaKeys.ARENA_UI_BROADCAST_INFO_LORE).map(GuiLoreLine::Text),
                GuiLoreFrame.NONE,
            ),
        )

    private fun display(
        slot: Int,
        material: Material,
        name: String,
        lore: GuiLoreSpec,
    ): MenuElement = elements().menuDisplay(
        GuiMenuDisplaySpec(
            slot = slot,
            item = GuiItemSpec(
                material = material,
                name = GuiNameSpec.Text(name, GuiNameStyle.DEFAULT),
                lore = lore,
                role = GuiElementRole.CONTENT,
                amount = 1,
            ),
        ),
    )

    private fun elements() = CCSystem.getAPI().getGuiElementService()

    companion object {
        private const val OWNER = "cc-content"
        private const val MENU_ID = "arena-session-info"
    }
}
