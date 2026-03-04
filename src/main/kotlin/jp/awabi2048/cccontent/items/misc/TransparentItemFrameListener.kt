package jp.awabi2048.cccontent.items.misc

import jp.awabi2048.cccontent.items.CustomItemManager
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.NamespacedKey
import org.bukkit.entity.GlowItemFrame
import org.bukkit.entity.ItemFrame
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.hanging.HangingBreakEvent
import org.bukkit.event.hanging.HangingBreakByEntityEvent
import org.bukkit.event.hanging.HangingPlaceEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import java.util.UUID

class TransparentItemFrameListener(private val plugin: JavaPlugin) : Listener {
    private val frameTypeKey = NamespacedKey(plugin, "transparent_frame_type")

    private val activeSneakPlayers = mutableSetOf<UUID>()
    private val playerRevealedFrames = mutableMapOf<UUID, MutableSet<UUID>>()
    private val frameRevealOwners = mutableMapOf<UUID, MutableSet<UUID>>()

    init {
        startRevealTask()
    }

    @EventHandler
    fun onFramePlace(event: HangingPlaceEvent) {
        val frame = event.entity as? ItemFrame ?: return
        val kind = resolvePlacedFrameKind(event) ?: return

        markTransparentFrame(frame, kind)
        frame.isVisible = true
        makeInvisibleAfterOneSecond(frame)
    }

    @EventHandler
    fun onFrameBreak(event: HangingBreakEvent) {
        val frame = event.entity as? ItemFrame ?: return
        val kind = readTransparentKind(frame) ?: return

        event.isCancelled = true
        clearFrameRevealState(frame.uniqueId)

        if (frame.isValid) {
            frame.remove()
        }

        val breaker = (event as? HangingBreakByEntityEvent)?.remover as? Player
        if (breaker?.gameMode == GameMode.CREATIVE) {
            return
        }

        CustomItemManager.createItem(kind.fullId, 1)?.let { item ->
            frame.world.dropItemNaturally(frame.location, item)
        }
    }

    @EventHandler
    fun onSneakToggle(event: PlayerToggleSneakEvent) {
        val player = event.player
        if (event.isSneaking && isHoldingTransparentFrameItem(player.uniqueId)) {
            activeSneakPlayers.add(player.uniqueId)
        } else {
            clearPlayerRevealState(player.uniqueId)
            activeSneakPlayers.remove(player.uniqueId)
        }
    }

    @EventHandler
    fun onHeldSlotChange(event: PlayerItemHeldEvent) {
        val player = event.player
        if (player.uniqueId !in activeSneakPlayers) return
        if (!isHoldingTransparentFrameItem(player.uniqueId)) {
            clearPlayerRevealState(player.uniqueId)
            activeSneakPlayers.remove(player.uniqueId)
        }
    }

    @EventHandler
    fun onSwapHands(event: PlayerSwapHandItemsEvent) {
        val player = event.player
        if (player.uniqueId !in activeSneakPlayers) return
        Bukkit.getScheduler().runTask(plugin, Runnable {
            if (!isHoldingTransparentFrameItem(player.uniqueId)) {
                clearPlayerRevealState(player.uniqueId)
                activeSneakPlayers.remove(player.uniqueId)
            }
        })
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val uuid = event.player.uniqueId
        clearPlayerRevealState(uuid)
        activeSneakPlayers.remove(uuid)
    }

    private fun startRevealTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            val stalePlayers = mutableListOf<UUID>()

            for (uuid in activeSneakPlayers) {
                val player = Bukkit.getPlayer(uuid)
                if (player == null || !player.isOnline || !player.isSneaking || !isHoldingTransparentFrameItem(uuid)) {
                    clearPlayerRevealState(uuid)
                    stalePlayers += uuid
                    continue
                }

                val nearbyFrameIds = mutableSetOf<UUID>()
                val nearby = player.world.getNearbyEntities(player.location, 8.0, 8.0, 8.0)
                for (entity in nearby) {
                    val frame = entity as? ItemFrame ?: continue
                    if (readTransparentKind(frame) == null) continue

                    nearbyFrameIds += frame.uniqueId
                    setFrameRevealedBy(frame, uuid)
                }

                val previouslyRevealed = playerRevealedFrames[uuid] ?: mutableSetOf()
                val toHide = previouslyRevealed.filter { it !in nearbyFrameIds }
                toHide.forEach { frameId -> removeRevealOwner(frameId, uuid) }
            }

            stalePlayers.forEach { activeSneakPlayers.remove(it) }
        }, 1L, 2L)
    }

    private fun setFrameRevealedBy(frame: ItemFrame, playerId: UUID) {
        val frameId = frame.uniqueId
        val owners = frameRevealOwners.getOrPut(frameId) { mutableSetOf() }
        owners += playerId

        val frames = playerRevealedFrames.getOrPut(playerId) { mutableSetOf() }
        frames += frameId

        frame.isVisible = true
    }

    private fun clearPlayerRevealState(playerId: UUID) {
        val revealed = playerRevealedFrames.remove(playerId) ?: return
        revealed.forEach { frameId ->
            removeRevealOwner(frameId, playerId)
        }
    }

    private fun removeRevealOwner(frameId: UUID, playerId: UUID) {
        val owners = frameRevealOwners[frameId] ?: return
        owners.remove(playerId)
        if (owners.isNotEmpty()) return

        frameRevealOwners.remove(frameId)
        val frame = Bukkit.getEntity(frameId) as? ItemFrame ?: return
        if (readTransparentKind(frame) != null) {
            frame.isVisible = false
        }
    }

    private fun clearFrameRevealState(frameId: UUID) {
        val owners = frameRevealOwners.remove(frameId) ?: emptySet()
        owners.forEach { owner ->
            playerRevealedFrames[owner]?.remove(frameId)
            if (playerRevealedFrames[owner].isNullOrEmpty()) {
                playerRevealedFrames.remove(owner)
            }
        }
    }

    private fun resolvePlacedFrameKind(event: HangingPlaceEvent): TransparentFrameKind? {
        extractItemStackFromEvent(event)?.let { stack ->
            identifyKind(stack)?.let { return it }
        }

        val player = event.player ?: return null
        val main = identifyKind(player.inventory.itemInMainHand)
        val off = identifyKind(player.inventory.itemInOffHand)
        return selectKindByEntity(event.entity as? ItemFrame, main, off)
    }

    private fun extractItemStackFromEvent(event: HangingPlaceEvent): ItemStack? {
        return try {
            val method = event.javaClass.getMethod("getItemStack")
            method.invoke(event) as? ItemStack
        } catch (_: Exception) {
            null
        }
    }

    private fun selectKindByEntity(
        frame: ItemFrame?,
        main: TransparentFrameKind?,
        off: TransparentFrameKind?
    ): TransparentFrameKind? {
        if (main != null && off == null) return main
        if (off != null && main == null) return off
        if (main == null && off == null) return null
        if (frame is GlowItemFrame) {
            return listOf(main, off).firstOrNull { it == TransparentFrameKind.GLOW }
        }
        return listOf(main, off).firstOrNull { it == TransparentFrameKind.NORMAL }
    }

    private fun identifyKind(stack: ItemStack?): TransparentFrameKind? {
        if (stack == null || stack.type.isAir) return null
        return when (CustomItemManager.identify(stack)?.fullId) {
            TransparentFrameKind.NORMAL.fullId -> TransparentFrameKind.NORMAL
            TransparentFrameKind.GLOW.fullId -> TransparentFrameKind.GLOW
            else -> null
        }
    }

    private fun isHoldingTransparentFrameItem(playerId: UUID): Boolean {
        val player = Bukkit.getPlayer(playerId) ?: return false
        return identifyKind(player.inventory.itemInMainHand) != null || identifyKind(player.inventory.itemInOffHand) != null
    }

    private fun markTransparentFrame(frame: ItemFrame, kind: TransparentFrameKind) {
        frame.persistentDataContainer.set(frameTypeKey, PersistentDataType.STRING, kind.fullId)
    }

    private fun readTransparentKind(frame: ItemFrame): TransparentFrameKind? {
        return when (frame.persistentDataContainer.get(frameTypeKey, PersistentDataType.STRING)) {
            TransparentFrameKind.NORMAL.fullId -> TransparentFrameKind.NORMAL
            TransparentFrameKind.GLOW.fullId -> TransparentFrameKind.GLOW
            else -> null
        }
    }

    private fun makeInvisibleAfterOneSecond(frame: ItemFrame) {
        val frameId = frame.uniqueId
        object : BukkitRunnable() {
            override fun run() {
                val liveFrame = Bukkit.getEntity(frameId) as? ItemFrame
                if (liveFrame == null || !liveFrame.isValid) {
                    cancel()
                    return
                }

                if (!frameRevealOwners.containsKey(liveFrame.uniqueId) && readTransparentKind(liveFrame) != null) {
                    liveFrame.isVisible = false
                }
                cancel()
            }
        }.runTaskLater(plugin, 20L)
    }
}
