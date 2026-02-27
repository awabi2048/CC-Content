package jp.awabi2048.cccontent.features.sukima_dungeon.listeners

import jp.awabi2048.cccontent.features.sukima_dungeon.DungeonManager
import jp.awabi2048.cccontent.features.sukima_dungeon.DungeonSessionManager
import jp.awabi2048.cccontent.features.sukima_dungeon.CustomItemManager
import jp.awabi2048.cccontent.features.sukima_dungeon.ScoreboardManager
import jp.awabi2048.cccontent.features.sukima_dungeon.generator.MazeGenerator
import jp.awabi2048.cccontent.features.sukima_dungeon.generator.StructureBuilder
import jp.awabi2048.cccontent.features.sukima_dungeon.generator.StructureLoader
import jp.awabi2048.cccontent.features.sukima_dungeon.DungeonTier
import jp.awabi2048.cccontent.features.sukima_dungeon.gui.DungeonEntranceGui
import jp.awabi2048.cccontent.features.sukima_dungeon.gui.DungeonConfirmGui
import jp.awabi2048.cccontent.features.sukima_dungeon.gui.DungeonJoinGui
import jp.awabi2048.cccontent.features.sukima_dungeon.gui.DungeonExitGui
import jp.awabi2048.cccontent.features.sukima_dungeon.MessageManager
import jp.awabi2048.cccontent.features.sukima_dungeon.PortalManager
import jp.awabi2048.cccontent.features.sukima_dungeon.PortalSession
import jp.awabi2048.cccontent.features.sukima_dungeon.isSukimaDungeonWorld
import org.bukkit.event.player.PlayerInteractEntityEvent

import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.plugin.java.JavaPlugin

class EntranceListener(private val plugin: JavaPlugin, private val loader: StructureLoader) : Listener {

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? org.bukkit.entity.Player ?: return
        
        val holder = event.inventory.holder
        if (holder is DungeonEntranceGui) {
            // Apply cooldown
            if (jp.awabi2048.cccontent.features.sukima_dungeon.MenuCooldownManager.checkAndSetCooldown(player.uniqueId)) {
                event.isCancelled = true
                return
            }

            event.isCancelled = true
            val item = event.currentItem ?: return
            if (item.type == Material.AIR || item.type == Material.GRAY_STAINED_GLASS_PANE || item.type == Material.BLACK_STAINED_GLASS_PANE) return

            val player = event.whoClicked as? org.bukkit.entity.Player ?: return
            val slot = event.rawSlot

            when (slot) {
                43 -> {
                    // Theme Selector
                    player.playSound(player.location, org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.2f)
                    holder.nextTheme(player)
                }
                37 -> {
                    // Play Style Selector
                    player.playSound(player.location, org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f)
                    holder.togglePlayStyle(player)
                }
                38 -> {
                    // Size Selector
                    player.playSound(player.location, org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.1f)
                    holder.nextSize(player)
                }
                22 -> {
                    // Entrance Button
                    val themeName = holder.getCurrentTheme() ?: return
                    val sizeKey = holder.sizes[holder.currentSizeIndex]
                    val isMultiplayer = holder.isMultiplayer
                    val tier = holder.tier
                    
                    player.playSound(player.location, org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f)
                    DungeonConfirmGui(tier, themeName, sizeKey, isMultiplayer).open(player)
                }
            }

        } else if (holder is DungeonConfirmGui) {
            if (jp.awabi2048.cccontent.features.sukima_dungeon.MenuCooldownManager.checkAndSetCooldown(player.uniqueId)) {
                event.isCancelled = true
                return
            }

            event.isCancelled = true
            val slot = event.rawSlot
            when (slot) {
                11 -> {
                    // Confirm
                    player.playSound(player.location, org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.2f)
                    startDungeon(player, holder.tier, holder.themeName, holder.sizeKey, holder.isMultiplayer)
                }
                15 -> {
                    // Cancel
                    player.playSound(player.location, org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f)
                    DungeonEntranceGui(loader, holder.tier).apply {
                        currentThemeIndex = if (holder.themeName == "random") 0 else (loader.getThemeNames().indexOf(holder.themeName) + 1).coerceAtLeast(0)
                        currentSizeIndex = tier.availableSizes.indexOf(holder.sizeKey).coerceAtLeast(0)
                        isMultiplayer = holder.isMultiplayer
                        open(player)
                    }
                }
            }
        } else if (holder is DungeonJoinGui) {
            if (jp.awabi2048.cccontent.features.sukima_dungeon.MenuCooldownManager.checkAndSetCooldown(player.uniqueId)) {
                event.isCancelled = true
                return
            }

            event.isCancelled = true
            val slot = event.rawSlot
            when (slot) {
                13 -> {
                    // Join Button
                    val portal = holder.portal
                    if (portal.isReady) {
                        player.closeInventory()
                        val theme = loader.getTheme(portal.themeName)
                        val themeMessages = theme?.getOageMessages(player) ?: emptyList()
                        enterDungeon(
                            player, portal.tier, portal.themeName, portal.duration, portal.sproutCount,
                            portal.teleportLocation!!, portal.tileSize, portal.gridWidth, portal.gridLength,
                            portal.worldName!!, portal.minibossMarkers, portal.mobSpawnPoints, portal.restCells, true, themeMessages
                        )
                    } else {
                        player.playSound(player.location, org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 1.0f)
                        player.sendMessage(MessageManager.getMessage(player, "prefix") + "§cダンジョンを生成中です。少々お待ちください。")
                    }
                }
                22 -> {
                    // Close Button
                    player.closeInventory()
                    player.playSound(player.location, org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f)
                }
            }
        } else if (holder is DungeonExitGui) {
            if (jp.awabi2048.cccontent.features.sukima_dungeon.MenuCooldownManager.checkAndSetCooldown(player.uniqueId)) {
                event.isCancelled = true
                return
            }

            event.isCancelled = true
            val slot = event.rawSlot
            when (slot) {
                11 -> {
                    // Exit Button
                    player.closeInventory()
                    DungeonManager.escapeDungeon(player.world)
                }
                15 -> {
                    // Cancel Button
                    player.closeInventory()
                    player.playSound(player.location, org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f)
                }
            }
        }
    }

    @EventHandler
    fun onPlayerInteractPortal(event: PlayerInteractEntityEvent) {
        val entity = event.rightClicked
        if (entity is org.bukkit.entity.Interaction) {
            val portal = PortalManager.getPortal(entity.uniqueId) ?: return
            
            // Apply cooldown
            if (jp.awabi2048.cccontent.features.sukima_dungeon.MenuCooldownManager.checkAndSetCooldown(event.player.uniqueId)) {
                return
            }

            if (portal.isReturn) {
                // Task 17: Open exit confirmation
                DungeonExitGui().open(event.player)
            } else {
                // Task 6: Open join menu
                DungeonJoinGui(portal, loader).open(event.player)
            }
            event.player.playSound(event.player.location, org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f)
        }
    }

    private fun startDungeon(player: org.bukkit.entity.Player, tier: DungeonTier, initialThemeName: String, sizeKey: String, isMultiplayer: Boolean) {
        var themeName = initialThemeName
        var isRedirected = false
        if (themeName == "random") {
            val availableThemes = loader.getThemeNames().toList()
            themeName = availableThemes.random()
        } else {
            // Success Rate Check
            if (java.util.Random().nextDouble() > tier.successRate) {
                val availableThemes = loader.getThemeNames().filter { it != themeName }
                if (availableThemes.isNotEmpty()) {
                    themeName = availableThemes.random()
                    isRedirected = true
                }
            }
        }
        
        val theme = loader.getTheme(themeName) ?: return
        
        player.closeInventory()
        // Sound
        player.playSound(player.location, org.bukkit.Sound.BLOCK_PORTAL_TRIGGER, 0.5f, 1.0f)

        val config = jp.awabi2048.cccontent.features.sukima_dungeon.SukimaConfigHelper.getConfig(plugin)
        val size = config.getInt("sizes.$sizeKey.tiles", 5)
        val duration = config.getInt("sizes.$sizeKey.duration", 1800)

        // Immediate: Oage-chan's generating message
        val msg = MessageManager.getMessage(player, "oage_generate")
        MessageManager.sendOageMessage(player, " 「$msg」")

        // Redirect Message if applicable
        if (isRedirected) {
            val redirectMsg = MessageManager.getList(player, "oage_redirect").random()
            MessageManager.sendOageMessage(player, " 「$redirectMsg」")
        }

        // Create portal if multiplayer
        val portalSession = if (isMultiplayer) {
            PortalManager.createPortal(player, tier, themeName, sizeKey).apply {
                dungeonThemeId = theme.id
                this.duration = duration
                this.size = size
                this.tileSize = theme.tileSize
            }
        } else {
            null
        }

        // Get initial participants
        val participants = if (isMultiplayer) {
            mutableListOf(player) // Only owner for now, others join via portal
        } else {
            listOf(player)
        }

        // Waiting message timer
        val waitingMessages = MessageManager.getList(player, "oage_waiting").shuffled().toMutableList()
        val usedMessages = mutableListOf<String>()
        val waitingTask = object : org.bukkit.scheduler.BukkitRunnable() {
            override fun run() {
                if (waitingMessages.isEmpty()) {
                    if (usedMessages.isEmpty()) return
                    waitingMessages.addAll(usedMessages.shuffled())
                    usedMessages.clear()
                }
                val msg = waitingMessages.removeAt(0)
                usedMessages.add(msg)
                participants.forEach { p ->
                    if (p.isOnline) {
                        MessageManager.sendOageMessage(p, " 「$msg」")
                    }
                }
            }
        }.runTaskTimer(plugin, 100L, 100L)

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val centerX = size / 2
            val centerZ = size / 2
            val maze = MazeGenerator.generate(size, size, centerX, centerZ)
            
            plugin.server.scheduler.runTask(plugin, Runnable {
                val world = DungeonManager.createDungeonWorld(theme.id)
                if (world == null) {
                    waitingTask.cancel()
                    plugin.logger.severe("Failed to create dungeon world: world is null")
                    participants.forEach { it.sendMessage(MessageManager.getMessage(it, "command_generator_failed")) }
                    return@Runnable
                }
                DungeonManager.registerTheme(world, theme)
                
                val startLocation = org.bukkit.Location(world, 0.0, 64.0, 0.0)
                
                StructureBuilder.buildSpread(startLocation, maze, theme, centerX, centerZ) { buildResult ->
                    // Delay for entity processing and Sprout population
                    plugin.server.scheduler.runTaskLater(plugin, Runnable {
                        val npcBase = config.getInt("sizes.$sizeKey.npc_base_count", 5)
                        val sproutBase = config.getInt("sizes.$sizeKey.sprout_base_count", 3)
                        val npcVariation = config.getDouble("npc_count_variation", 0.2)
                        val sproutVariation = config.getDouble("sprout_count_variation", 0.2)

                        val finalNpcCount = (npcBase * (1.0 + (java.util.Random().nextDouble() * 2 - 1) * npcVariation)).toInt().coerceAtLeast(0)
                        val finalSproutCount = (sproutBase * (1.0 + (java.util.Random().nextDouble() * 2 - 1) * sproutVariation)).toInt().coerceAtLeast(0)

                        val markerResult = StructureBuilder.processMarkers(startLocation, size, size, theme, finalNpcCount, buildResult.minibossMarkers.keys)
                        val spawnLocations = markerResult.playerSpawns
                        
                        val teleportLocation = if (spawnLocations.isEmpty()) {
                            val markerCount = world.entities.count {
                                (it is org.bukkit.entity.Marker && it.scoreboardTags.contains("sd.marker.spawn")) ||
                                    (it is org.bukkit.entity.ArmorStand && it.customName == "SPAWN")
                            }
                            plugin.logger.warning(
                                "No spawn locations found in generated dungeon. fallback=origin mazeSize=$size theme=${theme.id} world=${world.name} markerCount=$markerCount"
                            )
                            org.bukkit.Location(world, 0.5, 64.0, 0.5)
                        } else {
                            spawnLocations.random()
                        }
                         val sproutCount = jp.awabi2048.cccontent.features.sukima_dungeon.SproutManager.populate(plugin, world, finalSproutCount)

                         if (isMultiplayer && portalSession != null) {
                             portalSession.isReady = true
                             portalSession.teleportLocation = teleportLocation
                             portalSession.worldName = world.name
                             portalSession.sproutCount = sproutCount
                             portalSession.gridWidth = size
                             portalSession.gridLength = size
                             portalSession.minibossMarkers = buildResult.minibossMarkers
                             portalSession.mobSpawnPoints = markerResult.mobSpawnPoints
                             portalSession.restCells = buildResult.restCells
                         }

                         // Stop waiting messages
                         waitingTask.cancel()

                         // Generation finished: Oage-chan's entry message
                         participants.forEach { p ->
                             if (p.isOnline) {
                                 val msg = MessageManager.getMessage(p, "oage_entry")
                                 MessageManager.sendOageMessage(p, " 「$msg」")
                             }
                         }

                         // Actual entry
                         if (!isMultiplayer) {
                             plugin.server.scheduler.runTaskLater(plugin, Runnable {
                                 participants.forEach { p ->
                                     if (!p.isOnline) return@forEach
                                     
                                     val themeMessages = theme.getOageMessages(p)
                                     enterDungeon(p, tier, themeName, duration, sproutCount, teleportLocation, theme.tileSize, size, size, world.name, buildResult.minibossMarkers, markerResult.mobSpawnPoints, buildResult.restCells, isMultiplayer, themeMessages)
                                 }
                             }, 40L)
                         } else {
                             // For multiplayer, announce that it's ready
                             participants.forEach { p ->
                                 p.sendMessage(MessageManager.getMessage(p, "prefix") + "§aダンジョンの生成が完了しました！ポータルから入場できます。")
                                 p.playSound(p.location, org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f)
                             }
                         }
                    }, 10L)
                }
            })
        })
    }

    companion object {
        fun enterDungeon(
            player: org.bukkit.entity.Player,
            tier: DungeonTier,
            themeName: String,
            duration: Int,
            sproutCount: Int,
            teleportLocation: org.bukkit.Location,
            tileSize: Int,
            gridWidth: Int,
            gridLength: Int,
            worldName: String,
            minibossMarkers: Map<Pair<Int, Int>, org.bukkit.Location>,
            mobSpawnPoints: List<org.bukkit.Location>,
            restCells: Set<Pair<Int, Int>>,
            isMultiplayer: Boolean,
            themeMessages: List<String> = emptyList()
        ) {
            player.teleport(teleportLocation)
            player.playSound(player.location, org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f)

            val inv = player.inventory
            var replaced = false
            for (i in 0 until inv.size) {
                val invItem = inv.getItem(i)
                if (CustomItemManager.isBookmarkItem(invItem)) {
                    inv.setItem(i, CustomItemManager.getTalismanItem(player))
                    replaced = true
                    break
                }
            }
            if (!replaced) {
                val talisman = CustomItemManager.getTalismanItem(player)
                if (inv.firstEmpty() != -1) {
                    inv.addItem(talisman)
                } else {
                    player.world.dropItemNaturally(player.location, talisman)
                }
            }
            
            // Task 14: Place return portal if not exists
            runCatching {
                if (PortalManager.getReturnPortal(teleportLocation.world!!) == null) {
                    PortalManager.createReturnPortal(teleportLocation)
                }
            }.onFailure {
                org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(EntranceListener::class.java).logger.warning(
                    "帰還ポータル生成に失敗しました: world=${teleportLocation.world?.name} loc=${teleportLocation.blockX},${teleportLocation.blockY},${teleportLocation.blockZ} error=${it.message}"
                )
            }
            
            // Send theme specific messages after teleport
            if (themeMessages.isNotEmpty()) {
                MessageManager.sendDelayedMessages(player, themeMessages.map { " 「$it」" })
            }
            
            // Setup session and scoreboard
            DungeonSessionManager.startSession(
                player, tier, themeName, duration,
                totalSprouts = sproutCount,
                startLocation = teleportLocation,
                tileSize = tileSize,
                gridWidth = gridWidth,
                gridLength = gridLength,
                worldName = worldName,
                minibossMarkers = minibossMarkers,
                mobSpawnPoints = mobSpawnPoints,
                restCells = restCells,
                isMultiplayer = isMultiplayer
            )
            ScoreboardManager.setupScoreboard(player)
            jp.awabi2048.cccontent.features.sukima_dungeon.BGMManager.loadConfig()
            jp.awabi2048.cccontent.features.sukima_dungeon.BGMManager.play(player, "default")
        }
    }
}

