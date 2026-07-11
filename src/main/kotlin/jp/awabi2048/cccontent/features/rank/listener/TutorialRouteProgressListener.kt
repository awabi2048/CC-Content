package jp.awabi2048.cccontent.features.rank.listener

import jp.awabi2048.cccontent.features.rank.RankManager
import jp.awabi2048.cccontent.features.rank.RankStorage
import jp.awabi2048.cccontent.features.rank.tutorial.TutorialRank
import jp.awabi2048.cccontent.features.rank.tutorial.task.TutorialTaskChecker
import jp.awabi2048.cccontent.features.rank.tutorial.task.TutorialTaskLoader
import jp.awabi2048.cccontent.features.rank.tutorial.task.NetherPortalCreationPredicate
import jp.awabi2048.cccontent.features.rank.localization.MessageProvider
import me.awabi2048.myworldmanager.api.event.MwmWorldCreatedEvent
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerPortalEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.event.world.PortalCreateEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import java.util.UUID
import kotlin.math.min

/**
 * 固定ランク経路で必要な特殊進捗とポータル導線を扱う。
 *
 * MyWorldManagerの公開イベントを直接購読する。MyWorldManagerがない場合はこのリスナー自体を登録しない。
 */
class TutorialRouteProgressListener(
    private val plugin: Plugin,
    private val rankManager: RankManager,
    private val taskChecker: TutorialTaskChecker,
    private val taskLoader: TutorialTaskLoader,
    private val storage: RankStorage,
    private val messageProvider: MessageProvider
) : Listener {
    private val lastActiveAt: MutableMap<UUID, Long> = mutableMapOf()
    private val lastSafeLocation: MutableMap<UUID, Location> = mutableMapOf()
    private val suspendedPlayers: MutableSet<UUID> = mutableSetOf()

    fun startActiveTimeTask(): Int {
        return plugin.server.scheduler.scheduleSyncRepeatingTask(plugin, Runnable {
            Bukkit.getOnlinePlayers().forEach { player ->
                if (isRecentlyActive(player.uniqueId)) {
                    val progress = rankManager.getPlayerTutorial(player.uniqueId).taskProgress
                    when {
                        isResourceNether(player.world) -> progress.activeNetherResourceTime += 1
                        player.world.environment == World.Environment.NORMAL -> progress.activeOverworldTime += 1
                    }
                    evaluateAndSave(player)
                }
            }
        }, 1200L, 1200L)
    }

    @EventHandler(ignoreCancelled = true)
    fun onMove(event: PlayerMoveEvent) {
        val player = event.player
        if (event.from.x != event.to.x ||
            event.from.y != event.to.y ||
            event.from.z != event.to.z ||
            event.from.yaw != event.to.yaw ||
            event.from.pitch != event.to.pitch
        ) {
            markActive(player)
        }
        if (!isPortalBlock(player.location.block.type)) {
            lastSafeLocation[player.uniqueId] = player.location.clone()
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onInteract(event: PlayerInteractEvent) {
        val player = event.player
        markActive(player)

        val clicked = event.clickedBlock ?: return
        val itemType = event.item?.type ?: return
        if (clicked.type == Material.END_PORTAL_FRAME && itemType == Material.ENDER_EYE) {
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                if (hasEndPortalNear(clicked.location)) {
                    val tutorial = rankManager.getPlayerTutorial(player.uniqueId)
                    tutorial.taskProgress.endPortalOpened = true
                    evaluateAndSave(player)
                }
            }, 1L)
        }
    }

    /** ネザーポータルのブロックが実際に生成された場合だけ着火要件を更新する。 */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onPortalCreate(event: PortalCreateEvent) {
        if (!NetherPortalCreationPredicate.isEstablished(event.reason, event.blocks.map { it.type })) return
        val player = event.entity as? Player ?: return
        if (event.world.environment != World.Environment.NORMAL) return

        val tutorial = rankManager.getPlayerTutorial(player.uniqueId)
        tutorial.taskProgress.netherPortalIgnited = true
        evaluateAndSave(player)
    }

    @EventHandler(ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        markActive(event.player)
    }

    @EventHandler(ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        markActive(event.player)
    }

    @EventHandler(ignoreCancelled = true)
    fun onCraft(event: CraftItemEvent) {
        val player = event.whoClicked as? Player ?: return
        markActive(player)
        if (event.recipe.result.type != Material.ENDER_EYE) {
            return
        }

        val crafted = estimateCraftedAmount(event)
        if (crafted <= 0) {
            return
        }

        val tutorial = rankManager.getPlayerTutorial(player.uniqueId)
        tutorial.taskProgress.enderEyesCrafted += crafted
        evaluateAndSave(player)
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    fun onPortal(event: PlayerPortalEvent) {
        val player = event.player
        val fromType = event.from.block.type
        val cause = event.cause

        if (cause == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL || fromType == Material.NETHER_PORTAL) {
            evaluateAndSave(player)
            if (rankManager.getTutorialRank(player.uniqueId).level < TutorialRank.PIONEER.level) {
                cancelPortal(event, player, messageProvider.getMessage("tutorial_rank.task.nether_locked"))
                return
            }
            event.isCancelled = true
            plugin.server.scheduler.runTask(plugin, Runnable {
                dispatchResourceTeleport(player, "nether:a")
            })
            return
        }

        if (cause == PlayerTeleportEvent.TeleportCause.END_PORTAL || fromType == Material.END_PORTAL) {
            evaluateAndSave(player)
            if (rankManager.getTutorialRank(player.uniqueId).level < TutorialRank.ADVENTURER.level) {
                cancelPortal(event, player, messageProvider.getMessage("tutorial_rank.task.end_locked"))
                return
            }
            event.isCancelled = true
            plugin.server.scheduler.runTask(plugin, Runnable {
                dispatchResourceTeleport(player, "end:a")
            })
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        lastActiveAt.remove(event.player.uniqueId)
        lastSafeLocation.remove(event.player.uniqueId)
        suspendedPlayers.remove(event.player.uniqueId)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onMyWorldCreated(event: MwmWorldCreatedEvent) {
        val tutorial = rankManager.getPlayerTutorial(event.ownerUuid)
        tutorial.taskProgress.myWorldCreated = true
        Bukkit.getPlayer(event.ownerUuid)?.let { evaluateAndSave(it) } ?: storage.saveTutorialRank(tutorial)
    }

    private fun markActive(player: Player) {
        lastActiveAt[player.uniqueId] = System.currentTimeMillis()
    }

    private fun isRecentlyActive(playerUuid: UUID): Boolean {
        val lastActive = lastActiveAt[playerUuid] ?: return false
        return System.currentTimeMillis() - lastActive <= ACTIVE_WINDOW_MILLIS
    }

    private fun evaluateAndSave(player: Player) {
        if (player.uniqueId in suspendedPlayers) return
        val tutorial = rankManager.getPlayerTutorial(player.uniqueId)
        val requirement = try {
            taskLoader.getRequirement(tutorial.currentRank.name)
        } catch (e: IllegalArgumentException) {
            suspendedPlayers += player.uniqueId
            plugin.logger.severe("チュートリアルランク進行を停止しました: player=${player.uniqueId}, rank=${tutorial.currentRank.name}, reason=${e.message}")
            return
        }
        if (!tutorial.isMaxRank() && taskChecker.isAllTasksComplete(tutorial.taskProgress, requirement, player)) {
            rankManager.rankUpByTask(player.uniqueId)
        }
        storage.saveTutorialRank(rankManager.getPlayerTutorial(player.uniqueId))
    }

    private fun cancelPortal(event: PlayerPortalEvent, player: Player, message: String) {
        event.isCancelled = true
        player.sendMessage(message)
        lastSafeLocation[player.uniqueId]?.let { safe ->
            plugin.server.scheduler.runTask(plugin, Runnable {
                if (player.isOnline) {
                    player.teleport(safe)
                }
            })
        }
    }

    private fun dispatchResourceTeleport(player: Player, target: String) {
        plugin.server.dispatchCommand(plugin.server.consoleSender, "resource teleport $target ${player.name}")
    }

    private fun estimateCraftedAmount(event: CraftItemEvent): Int {
        val resultAmount = event.recipe.result.amount.coerceAtLeast(1)
        if (!event.isShiftClick) {
            return resultAmount
        }

        val required = event.inventory.matrix
            .filterNotNull()
            .filter { it.type != Material.AIR && it.amount > 0 }
            .groupingBy(ItemStack::getType)
            .eachCount()

        if (required.isEmpty()) {
            return resultAmount
        }

        val inventoryCounts = event.whoClicked.inventory.contents
            .filterNotNull()
            .filter { it.type != Material.AIR && it.amount > 0 }
            .groupBy { it.type }
            .mapValues { (_, stacks) -> stacks.sumOf { it.amount } }

        val maxCrafts = required.entries.minOfOrNull { (material, amount) ->
            (inventoryCounts[material] ?: 0) / amount.coerceAtLeast(1)
        } ?: 1

        return min(maxCrafts.coerceAtLeast(1) * resultAmount, 64)
    }

    private fun hasEndPortalNear(center: Location): Boolean {
        val world = center.world ?: return false
        val baseX = center.blockX
        val baseY = center.blockY
        val baseZ = center.blockZ
        for (x in -4..4) {
            for (y in -2..2) {
                for (z in -4..4) {
                    if (world.getBlockAt(baseX + x, baseY + y, baseZ + z).type == Material.END_PORTAL) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun isPortalBlock(material: Material): Boolean {
        return material == Material.NETHER_PORTAL || material == Material.END_PORTAL
    }

    private fun isResourceNether(world: World): Boolean {
        return world.environment == World.Environment.NETHER && world.name.startsWith("resource_nether.")
    }

    private companion object {
        private const val ACTIVE_WINDOW_MILLIS = 70_000L
    }
}
