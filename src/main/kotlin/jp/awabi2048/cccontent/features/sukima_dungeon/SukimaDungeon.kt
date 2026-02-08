package jp.awabi2048.cccontent.features.sukima_dungeon

import jp.awabi2048.cccontent.features.sukima_dungeon.generator.StructureBuilder
import jp.awabi2048.cccontent.features.sukima_dungeon.generator.StructureLoader
import jp.awabi2048.cccontent.features.sukima_dungeon.mobs.MobManager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin

class SukimaDungeon : JavaPlugin(), Listener {
    private lateinit var structureLoader: StructureLoader
    private lateinit var mobManager: MobManager
    private lateinit var itemManager: jp.awabi2048.cccontent.features.sukima_dungeon.items.ItemManager
    private lateinit var markerManager: MarkerManager

    fun getMarkerManager(): MarkerManager = markerManager
    fun getItemManager(): jp.awabi2048.cccontent.features.sukima_dungeon.items.ItemManager = itemManager

    override fun onEnable() {
        logger.info("SukimaDungeon has been enabled!")
        
        reloadPlugin()
        BGMManager.loadConfig()

        val commandExecutor = MazeCommand(this, structureLoader)
        getCommand("sukima_dungeon")?.setExecutor(commandExecutor)
        getCommand("sukima_dungeon")?.tabCompleter = commandExecutor
        
        server.pluginManager.registerEvents(this, this)
        server.pluginManager.registerEvents(DungeonListener(), this)
        server.pluginManager.registerEvents(jp.awabi2048.cccontent.features.sukima_dungeon.listeners.EntranceListener(this, structureLoader), this)
        server.pluginManager.registerEvents(jp.awabi2048.cccontent.features.sukima_dungeon.listeners.GravityListener(), this)
        server.pluginManager.registerEvents(jp.awabi2048.cccontent.features.sukima_dungeon.listeners.TalismanListener(this), this)
        server.pluginManager.registerEvents(jp.awabi2048.cccontent.features.sukima_dungeon.listeners.TrapListener(this), this)
        server.pluginManager.registerEvents(jp.awabi2048.cccontent.features.sukima_dungeon.listeners.SproutListener(this), this)
        server.pluginManager.registerEvents(jp.awabi2048.cccontent.features.sukima_dungeon.listeners.ItemPickupListener(this), this)
        server.pluginManager.registerEvents(jp.awabi2048.cccontent.features.sukima_dungeon.listeners.CompassListener(this), this)
        server.pluginManager.registerEvents(jp.awabi2048.cccontent.features.sukima_dungeon.listeners.MobTargetListener(this), this)
        
        markerManager = MarkerManager(this)
        server.pluginManager.registerEvents(markerManager, this)
        markerManager.startParticleTask()
        
        PortalManager.init(this)
        
        // Start sprout particle task
        SproutManager.startParticleTask(this)
        
        // Start scoreboard and session check task
        server.scheduler.runTaskTimer(this, Runnable {
            for (session in DungeonSessionManager.getAllSessions().toList()) {
                session.updateElapsed() // 経過時間を更新
                
                val player = session.player
                if (player != null && player.isOnline) {
                    ScoreboardManager.updateScoreboard(player)
                }
                
                // 制限時間チェック
                val remaining = session.getRemainingTime()
                
                // 警告処理 (おあげちゃんのランダムメッセージ)
                when (remaining) {
                    in 59001..61000 -> {
                        player?.let { p ->
                            if (p.isOnline) {
                                val msg = MessageManager.getMessage(p, "oage_collapse_warn")
                                p.sendMessage("§e§l[おあげちゃん] §f「$msg」")
                                p.playSound(p.location, org.bukkit.Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f)
                            }
                        }
                    }
                    in 4001..6000 -> {
                        player?.let { p ->
                            if (p.isOnline) {
                                val msg = MessageManager.getMessage(p, "oage_collapse")
                                p.sendMessage("§e§l[おあげちゃん] §f「$msg」")
                                p.playSound(p.location, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.5f)
                            }
                        }
                    }
                }

                if (remaining <= 0) {
                    player?.let { p ->
                        if (p.isOnline) {
                            if (session.isCollapsing) {
                                // Task 19 & 20: FAILURE
                                val failMsg = MessageManager.getList(p, "oage_fail").random()
                                MessageManager.sendOageMessage(p, " 「$failMsg」")
                                
                                // Remove Dungeon Items (Sprouts and other loot)
                                p.inventory.contents.filterNotNull().forEach { item ->
                                    if (CustomItemManager.isDungeonItem(item)) {
                                        item.amount = 0
                                    }
                                }
                                p.playSound(p.location, org.bukkit.Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.5f)
                            } else {
                                p.sendMessage(MessageManager.getMessage(p, "dungeon_closing"))
                            }
                            
                            // 退出時におあげちゃんの御札を削除
                            p.inventory.contents.filterNotNull().forEach { item ->
                                if (CustomItemManager.isTalismanItem(item)) {
                                    item.amount = 0
                                }
                            }

                            p.teleport(server.worlds[0].spawnLocation)
                        }
                    }
                    
                    // オフラインでもセッション終了とワールド削除のトリガーを引く
                    DungeonSessionManager.endSession(session.playerUUID)
                    player?.let { ScoreboardManager.removeScoreboard(it) }
                    
                    session.dungeonWorldName?.let { worldName ->
                        server.getWorld(worldName)?.let { world ->
                            if (world.players.isEmpty()) {
                                DungeonManager.deleteDungeonWorld(world)
                            }
                        }
                    }
                }
            }
            mobManager.tick()
            jp.awabi2048.cccontent.features.sukima_dungeon.tasks.SpecialTileTask.tick(this@SukimaDungeon, mobManager)
        }, 20L, 20L)

        // Load sessions
        DungeonSessionManager.loadSessions(this)
    }


    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        PlayerDataManager.load(event.player)
        
        // ダンジョン内にいる場合はBGMを再開
        if (isSukimaDungeonWorld(event.player.world)) {
            BGMManager.play(event.player, "default")
        }
    }

    fun reloadPlugin() {
        // Create default config
        saveDefaultConfig()
        reloadConfig()
        
        // Load messages
        MessageManager.load(this)
        
        if (!::structureLoader.isInitialized) {
            structureLoader = StructureLoader(this)
        }
        structureLoader.loadThemes()
        
        // Re-initialize or refresh managers
        if (!::mobManager.isInitialized) {
            mobManager = MobManager(this)
        }
        mobManager.load()
        
        if (!::itemManager.isInitialized) {
            itemManager = jp.awabi2048.cccontent.features.sukima_dungeon.items.ItemManager(this)
        }
        itemManager.load()
        
        // MarkerManager doesn't have a reload method, but we can ensure it's loaded if needed
        // (Currently done in onEnable)

        StructureBuilder.init(structureLoader, mobManager, itemManager)
        BGMManager.loadConfig()
        
        logger.info("SukimaDungeon configuration reloaded successfully!")
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        BGMManager.stop(event.player)
        PlayerDataManager.unload(event.player)
        MenuCooldownManager.clearCooldown(event.player.uniqueId)
    }

    override fun onDisable() {
        BGMManager.stopAll()
        DungeonSessionManager.saveSessions(this)
        for (player in server.onlinePlayers) {
            PlayerDataManager.unload(player)
            MenuCooldownManager.clearCooldown(player.uniqueId)
        }
        logger.info("SukimaDungeon has been enabled!")
    }
}
