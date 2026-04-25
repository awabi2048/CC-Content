package jp.awabi2048.cccontent

import com.awabi2048.ccsystem.CCSystem
import jp.awabi2048.cccontent.command.CCCommand
import jp.awabi2048.cccontent.command.GiveCommand
import jp.awabi2048.cccontent.config.CoreConfigManager
import jp.awabi2048.cccontent.items.CustomItemI18n
import jp.awabi2048.cccontent.items.CustomItemInteractionListener
import jp.awabi2048.cccontent.items.CustomItemManager
import jp.awabi2048.cccontent.items.misc.BigLight
import jp.awabi2048.cccontent.items.misc.AutoIgnitionBoosterConfig
import jp.awabi2048.cccontent.items.misc.AutoIgnitionBoosterItem
import jp.awabi2048.cccontent.items.misc.AutoIgnitionBoosterListener
import jp.awabi2048.cccontent.items.misc.AirCannonConfig
import jp.awabi2048.cccontent.items.misc.AirCannonItem
import jp.awabi2048.cccontent.items.misc.CassetteTapeItem
import jp.awabi2048.cccontent.items.misc.CustomHeadConfigRegistry
import jp.awabi2048.cccontent.items.misc.CustomHeadGuiListener
import jp.awabi2048.cccontent.items.misc.CustomHeadItem
import jp.awabi2048.cccontent.items.misc.SmallLight
import jp.awabi2048.cccontent.items.misc.GulliverItemListener
import jp.awabi2048.cccontent.items.misc.GulliverConfig
import jp.awabi2048.cccontent.items.misc.GulliverScaleManager
import jp.awabi2048.cccontent.items.misc.HeadDatabaseBridge
import jp.awabi2048.cccontent.items.misc.RadioCassetteConfig
import jp.awabi2048.cccontent.items.misc.RadioCassetteGuiListener
import jp.awabi2048.cccontent.items.misc.RadioCassettePlaybackManager
import jp.awabi2048.cccontent.items.misc.RadioCassettePlayerItem
import jp.awabi2048.cccontent.items.misc.StorageBoxGuiListener
import jp.awabi2048.cccontent.items.misc.StorageBoxSingleItem
import jp.awabi2048.cccontent.items.misc.StorageBoxTripleItem
import jp.awabi2048.cccontent.items.misc.TransparentGlowItemFrameItem
import jp.awabi2048.cccontent.items.misc.TransparentItemFrameItem
import jp.awabi2048.cccontent.items.misc.TransparentItemFrameListener
import jp.awabi2048.cccontent.items.marker.AdminMarkerToolService
import jp.awabi2048.cccontent.items.sukima.*
import jp.awabi2048.cccontent.items.brewery.BreweryMockClockItem
import jp.awabi2048.cccontent.items.brewery.BreweryMockYeastItem
import jp.awabi2048.cccontent.items.brewery.BrewerySampleFilterItem
import jp.awabi2048.cccontent.items.arena.*
import jp.awabi2048.cccontent.features.arena.ArenaCommand
import jp.awabi2048.cccontent.features.arena.ArenaI18n
import jp.awabi2048.cccontent.features.arena.ArenaItemListener
import jp.awabi2048.cccontent.features.arena.ArenaListener
import jp.awabi2048.cccontent.features.arena.ArenaManager
import jp.awabi2048.cccontent.features.arena.ArenaSessionInfoMenu
import jp.awabi2048.cccontent.features.arena.ArenaEnchantPedestalMenu
import jp.awabi2048.cccontent.features.arena.mission.ArenaMissionService
import jp.awabi2048.cccontent.features.brewery.BreweryFeature
import jp.awabi2048.cccontent.features.cooking.CookingFeature
import jp.awabi2048.cccontent.features.rank.RankManager
import jp.awabi2048.cccontent.features.rank.impl.RankManagerImpl
import jp.awabi2048.cccontent.features.rank.impl.YamlRankStorage
import jp.awabi2048.cccontent.features.rank.localization.LanguageLoader
import jp.awabi2048.cccontent.features.rank.localization.MessageProviderImpl
import jp.awabi2048.cccontent.features.rank.command.RankCommand
import jp.awabi2048.cccontent.features.rank.job.IgnoreBlockStore
import jp.awabi2048.cccontent.features.rank.job.ProfessionCombatExpListener
import jp.awabi2048.cccontent.features.rank.job.ProfessionMinerExpListener
import jp.awabi2048.cccontent.features.rank.profession.Profession
import jp.awabi2048.cccontent.features.rank.profession.SkillTreeRegistry
import jp.awabi2048.cccontent.features.rank.profession.skilltree.ConfigBasedSkillTree
import jp.awabi2048.cccontent.features.rank.skill.SkillEffectEngine
import jp.awabi2048.cccontent.features.rank.skill.SkillEffectRegistry
import jp.awabi2048.cccontent.features.rank.skill.handlers.*
import jp.awabi2048.cccontent.features.rank.skill.listeners.*
import jp.awabi2048.cccontent.features.rank.tutorial.task.TutorialTaskLoader
import jp.awabi2048.cccontent.features.rank.tutorial.task.TutorialTaskCheckerImpl
import jp.awabi2048.cccontent.features.rank.listener.*
// SukimaDungeon (GitHub版)
import jp.awabi2048.cccontent.features.common.BGMManager
import jp.awabi2048.cccontent.features.sukima_dungeon.*
import jp.awabi2048.cccontent.features.sukima_dungeon.generator.StructureLoader
import jp.awabi2048.cccontent.features.sukima_dungeon.generator.StructureBuilder
import jp.awabi2048.cccontent.features.sukima_dungeon.mobs.MobManager
import jp.awabi2048.cccontent.features.sukima_dungeon.items.ItemManager
import jp.awabi2048.cccontent.features.sukima_dungeon.listeners.*
import jp.awabi2048.cccontent.features.sukima_dungeon.tasks.SpecialTileTask
import jp.awabi2048.cccontent.mob.MobEventListener
import jp.awabi2048.cccontent.mob.MobService
import jp.awabi2048.cccontent.util.FeatureInitializationLogger
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.scheduler.BukkitTask
import java.io.File
import java.util.jar.JarFile

class CCContent : JavaPlugin(), Listener {
    
    companion object {
        lateinit var instance: CCContent
            private set
        
        val rankManager: RankManager
            get() = instance.rankManagerInstance 
                ?: throw IllegalStateException("RankManager is not initialized")
        
        lateinit var languageManager: LanguageLoader
            private set
    }
    
    private var playTimeTrackerTaskId: Int = -1
    private var persistenceFlushTask: BukkitTask? = null
    private lateinit var featureInitLogger: FeatureInitializationLogger
    private lateinit var coreConfig: YamlConfiguration
    private var languageErrorsByFeature: Map<String, List<String>> = emptyMap()
    
    // SukimaDungeon マネージャー (GitHub版)
    private lateinit var structureLoader: StructureLoader
    private lateinit var mobManager: MobManager
    private lateinit var itemManager: ItemManager
    private lateinit var markerManager: MarkerManager
    private lateinit var adminMarkerToolService: AdminMarkerToolService
    private lateinit var arenaManager: ArenaManager
    private var arenaMissionService: ArenaMissionService? = null
    private var arenaSessionInfoMenu: ArenaSessionInfoMenu? = null
    private var arenaEnchantPedestalMenu: ArenaEnchantPedestalMenu? = null
    private lateinit var sharedMobService: MobService
    private lateinit var breweryFeature: BreweryFeature
    private lateinit var cookingFeature: CookingFeature
    private var rankManagerInstance: RankManagerImpl? = null
    private var ignoreBlockStoreInstance: IgnoreBlockStore? = null
    private lateinit var contentEnabledAtStartup: ContentEnabledSettings
    private var arenaFeatureReady: Boolean = false
    private var arenaFeatureFailureReason: String? = null
    private var customItemsLanguageAvailable: Boolean = true

    private data class ContentEnabledSettings(
        val arena: Boolean,
        val rank: Boolean,
        val brewery: Boolean,
        val cooking: Boolean,
        val sukimaDungeon: Boolean
    )
    
    fun getMarkerManager(): MarkerManager = markerManager
    fun getAdminMarkerToolService(): AdminMarkerToolService = adminMarkerToolService
    fun getItemManager(): ItemManager = itemManager
    fun getStructureLoader(): StructureLoader = structureLoader
    fun getRankManager(): RankManager? = rankManagerInstance
    fun getCoreConfig(): FileConfiguration = coreConfig
    fun getPersistenceFlushIntervalMinutes(): Long {
        return coreConfig.getLong("persistence.flush_interval_minutes", 1L).coerceAtLeast(1L)
    }

    private fun startPlugin() {
        instance = this
        ensureCCSystemAvailable()
        saveSplitLanguageResources()
        coreConfig = CoreConfigManager.load(this)
        validateAndRegisterLanguageSources()
        contentEnabledAtStartup = loadContentEnabledSettings()

        featureInitLogger = FeatureInitializationLogger(logger)
        featureInitLogger.registerFeature("Rank System")
        featureInitLogger.registerFeature("Brewery")
        featureInitLogger.registerFeature("Cooking")
        featureInitLogger.registerFeature("Arena")
        featureInitLogger.registerFeature("SukimaDungeon")

        CustomItemManager.clear()
        CustomItemManager.setLogger(logger)
        CustomItemI18n.initialize(this)

        GulliverConfig.initialize(this)
        AutoIgnitionBoosterConfig.initialize(this)
        AirCannonConfig.initialize(this)
        CustomHeadConfigRegistry.initialize(this)
        RadioCassetteConfig.initialize(this)
        RadioCassettePlaybackManager.initialize(this)

        sharedMobService = MobService(this)
        sharedMobService.reloadDefinitions()
        sharedMobService.startTickTask()

        adminMarkerToolService = AdminMarkerToolService(this)
        server.pluginManager.registerEvents(adminMarkerToolService, this)
        adminMarkerToolService.start()

        if (customItemsLanguageAvailable) {
            registerCustomItems()
        } else {
            logger.warning("[CustomItems] 言語ファイル検証エラーによりカスタムアイテム登録をスキップします")
            languageErrorsFor("custom_items").forEach { error -> logger.warning("[CustomItems][Lang] $error") }
        }

        initializeFeatureIfEnabled("Rank System", "rank") {
            if (hasLanguageErrorsFor("rank")) {
                featureInitLogger.setStatus("Rank System", FeatureInitializationLogger.Status.WARNING)
                featureInitLogger.addSummaryMessage("Rank System", "言語ファイル検証エラーにより無効化")
                languageErrorsFor("rank").forEach { error -> logger.warning("[Rank][Lang] $error") }
                return@initializeFeatureIfEnabled
            }
            initializeRankSystem()
        }

        initializeFeatureIfEnabled("Arena", "arena") {
            if (hasLanguageErrorsFor("arena")) {
                arenaFeatureReady = false
                if (arenaFeatureFailureReason.isNullOrBlank()) {
                    arenaFeatureFailureReason = "Arena言語ファイルの検証エラーにより無効化"
                }
                featureInitLogger.setStatus("Arena", FeatureInitializationLogger.Status.WARNING)
                featureInitLogger.addSummaryMessage("Arena", "言語ファイル検証エラーにより無効化")
                languageErrorsFor("arena").forEach { error -> logger.warning("[Arena][Lang] $error") }
                return@initializeFeatureIfEnabled
            }
            try {
                arenaFeatureReady = false
                arenaFeatureFailureReason = null
                arenaManager = ArenaManager(this, sharedMobService)
                arenaManager.initialize(featureInitLogger)
                arenaMissionService = ArenaMissionService(this, arenaManager)
                arenaManager.setMissionService(arenaMissionService)
                arenaMissionService?.initialize()
                arenaSessionInfoMenu = ArenaSessionInfoMenu(this, arenaManager)
                arenaEnchantPedestalMenu = ArenaEnchantPedestalMenu(
                    plugin = this,
                    coreConfigProvider = { coreConfig },
                    missionServiceProvider = { arenaMissionService },
                    arenaManagerProvider = { arenaManager }
                )
                arenaManager.setPedestalMenuProvider { arenaEnchantPedestalMenu }
                arenaFeatureReady = true
            } catch (e: Exception) {
                runCatching { arenaMissionService?.shutdown() }
                runCatching {
                    if (::arenaManager.isInitialized) {
                        arenaManager.setPedestalMenuProvider(null)
                        arenaManager.setMissionService(null)
                        arenaManager.shutdown()
                    }
                }
                arenaMissionService = null
                arenaSessionInfoMenu = null
                arenaEnchantPedestalMenu = null
                arenaFeatureReady = false
                arenaFeatureFailureReason = e.message?.takeIf { it.isNotBlank() }
                    ?: "Arena feature の初期化に失敗しました"
                throw e
            }
        }

        initializeFeatureIfEnabled("Brewery", "brewery") {
            initializeBrewery()
        }

        initializeFeatureIfEnabled("Cooking", "cooking") {
            initializeCooking()
        }

        val giveCommand = GiveCommand()
        val ccCommand = CCCommand(
            giveCommand = giveCommand,
            onReload = { reloadConfigFiles() },
            onRestart = { restartPlugin() },
            onClearBlockPlacementData = { clearBlockPlacementData() },
            mobDefinitionIdsProvider = { sharedMobService.getDefinitionIds() },
            onSummonMob = { definitionId, location -> summonConfiguredMob(definitionId, location) },
            onBatchBreakDebug = { player, mode, delay, maxChain, autoCollect ->
                val batchMode = UnlockBatchBreakHandler.BatchBreakMode.fromRaw(mode)
                if (batchMode == null) {
                    false
                } else {
                    UnlockBatchBreakHandler.setDebugOverride(player.uniqueId, batchMode, delay, maxChain, autoCollect)
                    true
                }
            },
            onBlastMineDebug = { player, radius, delayTicksPerLayer, autoCollect, lossRate ->
                BlastMineHandler.setDebugOverride(player.uniqueId, radius, autoCollect, lossRate)
                true
            },
            onUpdateDay = { target ->
                when (target) {
                    null, "arena" -> arenaMissionService?.updateToday() ?: false
                    else -> false
                }
            },
            onCreateVoidWorldDebug = { _: Player ->
                if (arenaFeatureReady && ::arenaManager.isInitialized) {
                    arenaManager.createDebugVoidWorld()
                } else {
                    null
                }
            },
            onCloneVoidWorldDebug = { _: Player ->
                if (arenaFeatureReady && ::arenaManager.isInitialized) {
                    arenaManager.cloneDebugVoidWorld()
                } else {
                    null
                }
            },
            onDeleteVoidWorldDebug = { player ->
                if (arenaFeatureReady && ::arenaManager.isInitialized) {
                    arenaManager.deleteDebugVoidWorld(player.world)
                } else {
                    false
                }
            },
            isDebugVoidWorld = { world ->
                arenaFeatureReady && ::arenaManager.isInitialized && arenaManager.isDebugVoidWorld(world)
            }
        )

        getCommand("cc-content")?.setExecutor(ccCommand)
        getCommand("cc-content")?.tabCompleter = ccCommand
        getCommand("cc")?.setExecutor(ccCommand)
        getCommand("cc")?.tabCompleter = ccCommand

        if (isContentEnabledAtStartup("arena")) {
            ArenaI18n.initialize(this)
            val arenaCommand = ArenaCommand(
                arenaManagerProvider = { if (::arenaManager.isInitialized) arenaManager else null },
                missionService = arenaMissionService,
                sessionInfoMenu = arenaSessionInfoMenu,
                pedestalMenu = arenaEnchantPedestalMenu,
                featureEnabledProvider = { arenaFeatureReady },
                featureFailureReasonProvider = { arenaFeatureFailureReason }
            )
            getCommand("arenaa")?.setExecutor(arenaCommand)
            getCommand("arenaa")?.tabCompleter = arenaCommand
        }

        initializeFeatureIfEnabled("SukimaDungeon", "sukima_dungeon") {
            if (hasLanguageErrorsFor("sukima_dungeon")) {
                featureInitLogger.setStatus("SukimaDungeon", FeatureInitializationLogger.Status.WARNING)
                featureInitLogger.addSummaryMessage("SukimaDungeon", "言語ファイル検証エラーにより無効化")
                languageErrorsFor("sukima_dungeon").forEach { error -> logger.warning("[SukimaDungeon][Lang] $error") }
                return@initializeFeatureIfEnabled
            }
            initializeSukimaDungeon()
        }

        server.pluginManager.registerEvents(GulliverItemListener(this), this)
        server.pluginManager.registerEvents(AutoIgnitionBoosterListener(this), this)
        server.pluginManager.registerEvents(MobEventListener(sharedMobService), this)
        if (isContentEnabledAtStartup("arena") && arenaFeatureReady && ::arenaManager.isInitialized) {
            server.pluginManager.registerEvents(ArenaItemListener(), this)
            server.pluginManager.registerEvents(ArenaListener(arenaManager), this)
            arenaMissionService?.let {
                server.pluginManager.registerEvents(it, this)
            }
            arenaSessionInfoMenu?.let {
                server.pluginManager.registerEvents(it, this)
            }
            arenaEnchantPedestalMenu?.let {
                server.pluginManager.registerEvents(it, this)
            }
        }
        server.pluginManager.registerEvents(CustomHeadGuiListener(this), this)
        server.pluginManager.registerEvents(CustomItemInteractionListener(), this)
        server.pluginManager.registerEvents(RadioCassetteGuiListener(), this)
        server.pluginManager.registerEvents(StorageBoxGuiListener(this), this)
        server.pluginManager.registerEvents(TransparentItemFrameListener(this), this)

        server.scheduler.runTaskTimer(this, GulliverScaleManager(), 0L, 1L)

        featureInitLogger.printSummary()

        startPersistenceFlushTask()
    }

    private fun stopPlugin() {
        try {
            HandlerList.unregisterAll(this as org.bukkit.plugin.Plugin)
            server.scheduler.cancelTasks(this)

            UnlockBatchBreakHandler.stopAll()
            BlastMineHandler.stopAll()
            BreakSpeedBoostHandler.clearAllBoosts()
            AttackReachBoostHandler.removeAllModifiers()
            UnlockItemTokenHandler.Companion.clearUnlockedItems()
            ReplantHandler.clearAllProcessed()
            SkillEffectEngine.clearAllCache()
            SkillEffectRegistry.clear()
            SkillTreeRegistry.clear()

            rankManagerInstance?.hideAllProfessionBossBars()
            rankManagerInstance?.saveData()
            ignoreBlockStoreInstance?.flush()

            if (::breweryFeature.isInitialized) {
                breweryFeature.shutdown()
            }
            arenaMissionService?.shutdown()
            arenaSessionInfoMenu = null
            arenaEnchantPedestalMenu = null
            if (::arenaManager.isInitialized) {
                arenaManager.setPedestalMenuProvider(null)
                arenaManager.setMissionService(null)
            }
            arenaMissionService = null
            if (::arenaManager.isInitialized) {
                arenaManager.shutdown()
            }

            PortalManager.shutdown()
            RadioCassettePlaybackManager.shutdown()
            BGMManager.stopAll()
            DungeonSessionManager.saveSessions(this)

            persistenceFlushTask?.cancel()
            persistenceFlushTask = null

            if (::sharedMobService.isInitialized) {
                sharedMobService.shutdown()
            }

            for (player in server.onlinePlayers) {
                PlayerDataManager.unload(player)
                MenuCooldownManager.clearCooldown(player.uniqueId)
                rankManagerInstance?.hideProfessionBossBar(player.uniqueId)
            }

            PlayerDataManager.clearAll()
            MenuCooldownManager.clearAll()
            CustomItemManager.clear()
            HeadDatabaseBridge.reset()
            runCatching { unregisterLanguageSources() }
            rankManagerInstance = null
            ignoreBlockStoreInstance = null
            playTimeTrackerTaskId = -1

            logger.info("[CCContent] 停止処理を完了しました")
        } catch (e: Exception) {
            logger.warning("[CCContent] 停止処理中にエラーが発生しました: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun onEnable() {
        startPlugin()
    }

    private fun loadContentEnabledSettings(): ContentEnabledSettings {
        return ContentEnabledSettings(
            arena = coreConfig.getBoolean("content_enabled.arena", true),
            rank = coreConfig.getBoolean("content_enabled.rank", true),
            brewery = coreConfig.getBoolean("content_enabled.brewery", true),
            cooking = coreConfig.getBoolean("content_enabled.cooking", true),
            sukimaDungeon = coreConfig.getBoolean("content_enabled.sukima_dungeon", true)
        )
    }

    private fun startPersistenceFlushTask() {
        persistenceFlushTask?.cancel()
        val intervalTicks = getPersistenceFlushIntervalMinutes() * 60L * 20L
        persistenceFlushTask = server.scheduler.runTaskTimer(this, Runnable {
            if (isContentEnabledAtStartup("sukima_dungeon")) {
                DungeonSessionManager.flushIfDirty(this)
            }
            if (isContentEnabledAtStartup("brewery") && ::breweryFeature.isInitialized) {
                breweryFeature.flushDirty()
            }
        }, intervalTicks, intervalTicks)
    }

    private fun isContentEnabledAtStartup(contentKey: String): Boolean {
        return when (contentKey) {
            "arena" -> contentEnabledAtStartup.arena
            "rank" -> contentEnabledAtStartup.rank
            "brewery" -> contentEnabledAtStartup.brewery
            "cooking" -> contentEnabledAtStartup.cooking
            "sukima_dungeon" -> contentEnabledAtStartup.sukimaDungeon
            else -> true
        }
    }

    private fun initializeFeatureIfEnabled(featureName: String, contentKey: String, initialize: () -> Unit) {
        if (!isContentEnabledAtStartup(contentKey)) {
            featureInitLogger.setStatus(featureName, FeatureInitializationLogger.Status.WARNING)
            featureInitLogger.addSummaryMessage(featureName, "configで無効化(content_enabled.$contentKey=false)")
            logger.info("[$featureName] content_enabled.$contentKey=false のため無効化されています")
            return
        }

        try {
            initialize()
        } catch (e: Exception) {
            featureInitLogger.setStatus(featureName, FeatureInitializationLogger.Status.FAILURE)
            featureInitLogger.addDetailMessage(featureName, "[$featureName] 初期化失敗: ${e.message}")
            logger.warning("[$featureName] 初期化に失敗しました: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun logContentEnabledChangeIfNeeded(reloaded: ContentEnabledSettings) {
        if (reloaded == contentEnabledAtStartup) {
            return
        }

        logger.warning(
            "content_enabled の変更は再起動後に反映されます: " +
                "startup=$contentEnabledAtStartup, reloaded=$reloaded"
        )
    }
    
    /**
     * ランクシステムの初期化
     */
    private fun initializeRankSystem() {
        try {
            // ランクストレージとマネージャーを初期化
            val storage = YamlRankStorage(dataFolder)
            val rankManager = RankManagerImpl(storage)
            rankManagerInstance = rankManager

            // 言語ファイルを読み込み
            val languageLoader = LanguageLoader(this, "ja_jp")
            languageManager = languageLoader
            val messageProvider = MessageProviderImpl(languageLoader)
            rankManager.setMessageProvider(messageProvider)
            rankManager.initBossBarManager(this)

            // /rank, /rankmenu は早い段階で必ず登録する
            // （後続の初期化で例外が発生してもコマンド実行自体は生かす）
            val translator = jp.awabi2048.cccontent.features.rank.tutorial.task.EntityBlockTranslator(messageProvider)
            val rankCommand = RankCommand(rankManager, messageProvider, null, null, translator)
            registerRankCommands(rankCommand)
            server.pluginManager.registerEvents(rankCommand, this)
            server.pluginManager.registerEvents(
                jp.awabi2048.cccontent.features.rank.listener.TutorialRankUpListener(rankManager, rankCommand),
                this
            )

            val ignoreBlockStore = IgnoreBlockStore(File(dataFolder, "data/rank/ignore_blocks.yml"))
            ignoreBlockStoreInstance = ignoreBlockStore

            // スキル効果システムを初期化
            initializeSkillEffectSystem(rankManager, ignoreBlockStore)

            // スキルツリーを登録
            registerSkillTrees()

            // チュートリアルランク タスクシステムの初期化
            val (taskLoader, taskChecker) = initializeTutorialTaskSystem(rankManager, storage, ignoreBlockStore)
            rankCommand.setTutorialTaskSystem(taskLoader, taskChecker)

            // プレイ時間トラッカータスクを起動（1分ごとに更新）
            if (taskLoader != null && taskChecker != null) {
                val playTimeTracker = jp.awabi2048.cccontent.features.rank.tutorial.task.PlayTimeTrackerTask(
                    this,
                    rankManager,
                    taskChecker,
                    taskLoader,
                    storage
                )
                playTimeTrackerTaskId = playTimeTracker.start()
            }

            // 追加のランク系リスナー登録
            val minerListener = ProfessionMinerExpListener(this, rankManager, ignoreBlockStore)
            val combatExpListener = ProfessionCombatExpListener(rankManager, coreConfig)
            server.pluginManager.registerEvents(minerListener, this)
            server.pluginManager.registerEvents(combatExpListener, this)
            
            // 登録されたスキルツリーをカウント
            val skillTreeCount = Profession.values().count { SkillTreeRegistry.getSkillTree(it) != null }
            featureInitLogger.addSummaryMessage("Rank System", "スキル${skillTreeCount}種登録")
            featureInitLogger.setStatus("Rank System", FeatureInitializationLogger.Status.SUCCESS)
        } catch (e: Exception) {
            featureInitLogger.setStatus("Rank System", FeatureInitializationLogger.Status.FAILURE)
            featureInitLogger.addDetailMessage("Rank System", "[Rank System] 初期化失敗: ${e.message}")
            logger.warning("ランクシステムの初期化に失敗しました: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun registerRankCommands(rankCommand: RankCommand) {
        val rankPluginCommand = getCommand("rank")
        if (rankPluginCommand == null) {
            logger.severe("/rank コマンドの登録に失敗しました（plugin.yml の commands.rank を確認してください）")
        } else {
            rankPluginCommand.setExecutor(rankCommand)
            rankPluginCommand.tabCompleter = rankCommand
        }

        val rankMenuPluginCommand = getCommand("rankmenu")
        if (rankMenuPluginCommand == null) {
            logger.severe("/rankmenu コマンドの登録に失敗しました（plugin.yml の commands.rankmenu を確認してください）")
        } else {
            val rankMenuCommand = jp.awabi2048.cccontent.features.rank.command.RankMenuCommand(rankCommand)
            rankMenuPluginCommand.setExecutor(rankMenuCommand)
        }
    }

    /**
     * スキル効果システムの初期化
      */
    private fun initializeSkillEffectSystem(rankManager: RankManager, ignoreBlockStore: IgnoreBlockStore) {
        try {
            val blastMineHandler = BlastMineHandler()

            SkillEffectRegistry.register(BreakSpeedBoostHandler())
            SkillEffectRegistry.register(DropBonusHandler())
            SkillEffectRegistry.register(DurabilitySaveChanceHandler())
            SkillEffectRegistry.register(UnlockBatchBreakHandler(ignoreBlockStore))
            SkillEffectRegistry.register(ReplaceLootTableHandler())
            SkillEffectRegistry.register(blastMineHandler)
            SkillEffectRegistry.register(WindGustHandler())
            SkillEffectRegistry.register(ReplantHandler())
            SkillEffectRegistry.register(FarmerAreaTillingHandler())
            SkillEffectRegistry.register(FarmerAreaHarvestingHandler(this, rankManager, ignoreBlockStore))
            SkillEffectRegistry.register(FarmerAutoReplantingHandler())

            SkillEffectRegistry.register(UnlockSystemHandler())
            SkillEffectRegistry.register(UnlockRecipeHandler())
            SkillEffectRegistry.register(WorkSpeedBonusMockHandler())
            SkillEffectRegistry.register(SuccessRateBonusMockHandler())

            SkillEffectRegistry.register(UnlockItemTokenHandler())

            // 戦闘系スキルハンドラー
            SkillEffectRegistry.register(CombatDamageBoostHandler())
            SkillEffectRegistry.register(WarriorAxeDamageBoostHandler())
            SkillEffectRegistry.register(WarriorBowPowerBoostHandler())
            SkillEffectRegistry.register(WarriorQuickChargeHandler())
            SkillEffectRegistry.register(WarriorArrowSavingHandler())
            SkillEffectRegistry.register(WarriorPiercingHandler())
            SkillEffectRegistry.register(WarriorThreeWayHandler())
            SkillEffectRegistry.register(WarriorSnipeHandler())
            SkillEffectRegistry.register(WarriorAimingHandler())
            SkillEffectRegistry.register(SweepAttackDamageBoostHandler())
            SkillEffectRegistry.register(SwordsmanDrainHandler())
            SkillEffectRegistry.register(AttackReachBoostHandler())
            SkillEffectRegistry.register(SwordsmanUnderdogBuffHandler())

            server.pluginManager.registerEvents(SkillEffectCacheListener(rankManager, this), this)
            server.pluginManager.registerEvents(BlockBreakEffectListener(ignoreBlockStore), this)
            server.pluginManager.registerEvents(FarmerInteractEffectListener(), this)
            server.pluginManager.registerEvents(BatchBreakPreviewListener(), this)
            server.pluginManager.registerEvents(ActiveSkillTriggerListener(), this)
            server.pluginManager.registerEvents(ActiveSkillKeyListener(), this)
            server.pluginManager.registerEvents(CraftEffectListener(), this)
            server.pluginManager.registerEvents(CombatEffectListener(), this)
            server.pluginManager.registerEvents(WarriorBowEffectListener(), this)
            server.pluginManager.registerEvents(SwordsmanDrainListener(), this)
            server.pluginManager.registerEvents(SwordsmanUnderdogBuffListener(this), this)
        } catch (e: Exception) {
            logger.warning("スキル効果システムの初期化に失敗しました: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * チュートリアルランク タスクシステムの初期化
     * @return Pair<TutorialTaskLoader, TutorialTaskChecker>
     */
    private fun initializeTutorialTaskSystem(
        rankManager: RankManager,
        storage: YamlRankStorage,
        ignoreBlockStore: IgnoreBlockStore
    ): Pair<TutorialTaskLoader?, TutorialTaskCheckerImpl?> {
        try {
            // tutorial_tasks.yml ファイルを確認・抽出
            val tutorialTasksFile = File(dataFolder, "config/rank/tutorial_tasks.yml")
            if (!tutorialTasksFile.exists()) {
                extractTutorialTasksFile(tutorialTasksFile)
            }
            
            // TutorialTaskLoader を初期化
            val taskLoader = TutorialTaskLoader()
            taskLoader.loadRequirements(tutorialTasksFile)
            
            // TutorialTaskChecker を作成
            val taskChecker = TutorialTaskCheckerImpl()

            // 7つのイベントリスナーを登録
            server.pluginManager.registerEvents(
                TutorialPlayerJoinListener(rankManager),
                this
            )
            server.pluginManager.registerEvents(
                TutorialPlayerQuitListener(rankManager, taskChecker, taskLoader, storage),
                this
            )
            server.pluginManager.registerEvents(
                TutorialEntityDeathListener(rankManager, taskChecker, taskLoader, storage),
                this
            )
            server.pluginManager.registerEvents(
                TutorialBlockBreakListener(rankManager, taskChecker, taskLoader, storage, ignoreBlockStore),
                this
            )
            server.pluginManager.registerEvents(
                TutorialPlayerExpListener(rankManager, taskChecker, taskLoader, storage),
                this
            )
            
            return Pair(taskLoader, taskChecker)
        } catch (e: Exception) {
            logger.warning("チュートリアルランク タスクシステムの初期化に失敗しました: ${e.message}")
            e.printStackTrace()
            return Pair(null, null)
        }
    }
    
    /**
     * リソースから tutorial_tasks.yml をコピー
     */
    private fun extractTutorialTasksFile(destination: File) {
        val resourceStream = getResource("config/rank/tutorial_tasks.yml")
            ?: throw IllegalArgumentException("リソースが見つかりません: config/rank/tutorial_tasks.yml")
        
        resourceStream.use { input ->
            destination.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }
    
    /**
     * 各職業のスキルツリーを登録
     */
    private fun registerSkillTrees() {
        SkillTreeRegistry.clear()
        val jobDir = File(dataFolder, "config/rank/job").apply { mkdirs() }
        val expFile = File(dataFolder, "config/rank/job_exp.yml")
        val errors = mutableListOf<String>()

        if (!expFile.exists()) {
            try {
                copyResourceFile("config/rank/job_exp.yml", expFile)
            } catch (e: Exception) {
                logger.warning("ジョブ経験値設定ファイルのコピーに失敗しました: ${e.message}")
            }
        }
        
        for (profession in Profession.values()) {
            val ymlFile = File(jobDir, "${profession.id}.yml")
            
            // ファイルが存在しない場合はリソースからコピー
            if (!ymlFile.exists()) {
                try {
                    extractJobFile("${profession.id}.yml", ymlFile)
                } catch (e: Exception) {
                    val error = "スキルツリーファイルのコピーに失敗しました (${profession.id}): ${e.message}"
                    logger.warning(error)
                    errors += error
                    continue
                }
            }
            
            try {
                val skillTree = ConfigBasedSkillTree(profession.id, ymlFile)
                SkillTreeRegistry.register(profession, skillTree)
            } catch (e: Exception) {
                e.printStackTrace()
                val error = "スキルツリー読み込み失敗 (${profession.id}): ${e.message}"
                logger.warning(error)
                errors += error
            }
        }

        if (errors.isNotEmpty()) {
            throw IllegalStateException("Rank feature 初期化失敗: ${errors.joinToString(" | ")}")
        }
    }
    
    /**
     * リソースからジョブファイルをコピー
     */
    private fun extractJobFile(resourceName: String, destination: File) {
        val resourceStream = getResource("config/rank/job/$resourceName")
            ?: throw IllegalArgumentException("リソースが見つかりません: config/rank/job/$resourceName")
        
        resourceStream.use { input ->
            destination.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }
    
    private fun registerCustomItems() {
        // GulliverLight アイテム
        CustomItemManager.register(BigLight())
        CustomItemManager.register(SmallLight())
        CustomItemManager.register(AutoIgnitionBoosterItem())
        CustomItemManager.register(AirCannonItem())
        CustomItemManager.register(StorageBoxSingleItem())
        CustomItemManager.register(StorageBoxTripleItem())
        CustomItemManager.register(TransparentItemFrameItem())
        CustomItemManager.register(TransparentGlowItemFrameItem())
        registerRadioCassetteItems()
        registerCustomHeadItems()

        if (isContentEnabledAtStartup("brewery")) {
            CustomItemManager.register(BrewerySampleFilterItem(this))
            CustomItemManager.register(BreweryMockClockItem(this))
            CustomItemManager.register(BreweryMockYeastItem(this))
        }

        if (isContentEnabledAtStartup("arena")) {
            CustomItemManager.register(ArenaTicketItem())
            CustomItemManager.register(ArenaMedalItem())
            CustomItemManager.register(ArenaPrizeItem())
            CustomItemManager.register(ArenaStructureMarkerToolItem())
            CustomItemManager.register(ArenaOtherMarkerToolItem())
            CustomItemManager.register(ArenaLiftToolItem())
            CustomItemManager.register(ArenaEnchantShardItem())
        }

        if (isContentEnabledAtStartup("sukima_dungeon")) {
            CustomItemManager.register(SukimaCompassTier1Item())
            CustomItemManager.register(SukimaCompassTier2Item())
            CustomItemManager.register(SukimaCompassTier3Item())
            CustomItemManager.register(SukimaMarkerToolItem())
            CustomItemManager.register(SukimaBookmarkBrokenItem())
            CustomItemManager.register(SukimaBookmarkWornItem())
            CustomItemManager.register(SukimaBookmarkFadedItem())
            CustomItemManager.register(SukimaBookmarkNewItem())
            CustomItemManager.register(SukimaTalismanItem())
            CustomItemManager.register(SukimaWorldSproutItem())
        }
    }

    private fun registerCustomHeadItems() {
        CustomItemManager.unregisterByPrefix("misc.custom_head.")
        val variants = CustomHeadConfigRegistry.getAllVariants()
        for (variant in variants) {
            CustomItemManager.register(CustomHeadItem(this, variant))
        }
        logger.info("[CustomHead] カスタムヘッド券を登録しました: ${variants.size}件")
    }

    private fun registerRadioCassetteItems() {
        CustomItemManager.unregisterByPrefix("misc.cassette_")
        CustomItemManager.register(RadioCassettePlayerItem())

        val cassetteDefinitions = RadioCassetteConfig.getAll()
        cassetteDefinitions.forEach { definition ->
            CustomItemManager.register(CassetteTapeItem(definition))
        }

        logger.info("[RadioCassette] カセットテープを登録しました: ${cassetteDefinitions.size}件")
    }
    
    /**
     * config 配下の設定を再読込
     */
    private fun reloadConfigFiles() {
        coreConfig = CoreConfigManager.load(this)
        validateAndRegisterLanguageSources()
        if (hasLanguageErrorsFor("arena")) {
            if (arenaFeatureReady) {
                arenaMissionService?.shutdown()
                arenaSessionInfoMenu = null
                arenaEnchantPedestalMenu = null
                if (::arenaManager.isInitialized) {
                    arenaManager.setMissionService(null)
                    arenaManager.shutdown()
                }
            }
            arenaFeatureReady = false
            arenaFeatureFailureReason = "Arena言語ファイルの検証エラーにより無効化"
            logger.warning("[Arena] 言語ファイル検証エラーを検出したためArena機能を停止します")
            languageErrorsFor("arena").forEach { error -> logger.warning("[Arena][Lang] $error") }
            logger.info("[CCContent] config 配下の再読込を完了しました(Arena無効)")
            return
        }

        if (!arenaFeatureReady && isContentEnabledAtStartup("arena") && arenaFeatureFailureReason != null) {
            logger.warning("[Arena] 現在Arena機能は無効です。再有効化にはサーバー再起動が必要です")
        }
        logContentEnabledChangeIfNeeded(loadContentEnabledSettings())
        SukimaConfigHelper.reload(this)
        MessageManager.load(this)
        BGMManager.loadConfig()

        if (isContentEnabledAtStartup("sukima_dungeon") && ::structureLoader.isInitialized && ::mobManager.isInitialized && ::itemManager.isInitialized) {
            reloadSukimaDungeon()
        }
        sharedMobService.reloadDefinitions()

        GulliverConfig.reload()
        AutoIgnitionBoosterConfig.reload()
        AirCannonConfig.reload()
        CustomHeadConfigRegistry.reload(this)
        RadioCassetteConfig.reload()
        registerCustomHeadItems()
        registerRadioCassetteItems()

        if (::breweryFeature.isInitialized) {
            breweryFeature.reload()
        }
        if (::cookingFeature.isInitialized) {
            cookingFeature.reload()
        }
        if (arenaFeatureReady && ::arenaManager.isInitialized && isContentEnabledAtStartup("arena")) {
            arenaManager.reloadThemes()
        }

        logger.info("[CCContent] config 配下の再読込を完了しました")
    }

    private fun ensureCCSystemAvailable() {
        val ccSystemPlugin = server.pluginManager.getPlugin("CC-System")
        if (ccSystemPlugin == null || !ccSystemPlugin.isEnabled) {
            throw IllegalStateException("CC-System が有効化されていないため CC-Content を起動できません")
        }
    }

    private fun validateAndRegisterLanguageSources() {
        val api = CCSystem.getAPI()
        val validationResult = api.validateI18nSource(this, contentLanguageFeatureByFile())
        languageErrorsByFeature = validationResult.errorsByFeature
        customItemsLanguageAvailable = !hasLanguageErrorsFor("custom_items")

        unregisterLanguageSources(api)

        contentLanguageSources().forEach { (feature, fileNames) ->
            if (!hasLanguageErrorsFor(feature)) {
                api.registerI18nSource("CC-Content:$feature", this, fileNames)
            }
        }
    }

    private fun unregisterLanguageSources(api: com.awabi2048.ccsystem.api.CCSystemAPI = CCSystem.getAPI()) {
        contentLanguageSources().keys.forEach { feature ->
            api.unregisterI18nSource("CC-Content:$feature")
        }
    }

    private fun hasLanguageErrorsFor(feature: String): Boolean {
        return languageErrorsFor(feature).isNotEmpty()
    }

    private fun languageErrorsFor(feature: String): List<String> {
        return languageErrorsByFeature[feature].orEmpty()
    }

    private fun contentLanguageFeatureByFile(): Map<String, String> {
        return buildMap {
            put("arena.yml", "arena")
            put("custom_items.yml", "custom_items")
            put("sukima_dungeon.yml", "sukima_dungeon")
            put("rank.yml", "rank")
            put("profession.yml", "rank")
            put("skill.yml", "rank")
            put("tutorial_rank.yml", "rank")
            put("mission.yml", "rank")
            put("gui.yml", "rank")
        }
    }

    private fun contentLanguageSources(): Map<String, Set<String>> {
        return mapOf(
            "arena" to setOf("arena.yml"),
            "custom_items" to setOf("custom_items.yml"),
            "sukima_dungeon" to setOf("sukima_dungeon.yml"),
            "rank" to setOf("rank.yml", "profession.yml", "skill.yml", "tutorial_rank.yml", "mission.yml", "gui.yml")
        )
    }

    private fun saveSplitLanguageResources() {
        val codeSource = runCatching {
            File(javaClass.protectionDomain.codeSource.location.toURI())
        }.getOrNull() ?: return
        if (!codeSource.isFile) {
            return
        }

        JarFile(codeSource).use { jar ->
            jar.entries().asSequence()
                .filter { !it.isDirectory && it.name.startsWith("lang/") && it.name.endsWith(".yml") }
                .forEach { entry ->
                    val target = File(dataFolder, entry.name)
                    if (!target.exists()) {
                        target.parentFile?.mkdirs()
                        saveResource(entry.name, false)
                    }
                }
        }
    }

    /**
     * プラグインを再起動相当に再初期化
     */
    private fun restartPlugin() {
        ArenaI18n.clearCache()
        stopPlugin()
        startPlugin()
    }
    
    /**
     * リソースファイルをデータフォルダにコピー
     */
    private fun copyResourceFile(resourcePath: String, targetFile: File) {
        try {
            // ターゲットファイルの親ディレクトリを作成
            targetFile.parentFile?.mkdirs()
            
            // リソースから入力ストリームを取得
            val inputStream = this::class.java.classLoader.getResourceAsStream(resourcePath)
                ?: return run {
                    logger.warning("リソースファイルが見つかりません: $resourcePath")
                }
            
            // ファイルにコピー
            inputStream.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            logger.warning("ファイルのコピーに失敗しました ($resourcePath): ${e.message}")
            e.printStackTrace()
        }
    }

    private fun clearBlockPlacementData() {
        val store = ignoreBlockStoreInstance
        if (store == null) {
            logger.warning("ブロック設置データストアが初期化されていないため削除できません")
            return
        }

        store.clearAll()
    }

    private fun initializeBrewery() {
        breweryFeature = BreweryFeature(this)
        breweryFeature.initialize(featureInitLogger)
    }

    private fun initializeCooking() {
        cookingFeature = CookingFeature(this)
        cookingFeature.initialize(featureInitLogger)
    }
    
    /**
     * SukimaDungeon マネージャーを初期化（GitHub版）
     */
    private fun initializeSukimaDungeon() {
        try {
            // 設定をリロード
            reloadSukimaDungeon()
            BGMManager.loadConfig()
            
            // コマンド登録
            val commandExecutor = MazeCommand(this, structureLoader)
            getCommand("sukima_dungeon")?.setExecutor(commandExecutor)
            getCommand("sukima_dungeon")?.tabCompleter = commandExecutor
            
            // リスナー登録
            server.pluginManager.registerEvents(this, this)
            server.pluginManager.registerEvents(DungeonListener(), this)
            server.pluginManager.registerEvents(EntranceListener(this, structureLoader), this)
            server.pluginManager.registerEvents(GravityListener(), this)
            server.pluginManager.registerEvents(TalismanListener(this), this)
            server.pluginManager.registerEvents(TrapListener(this), this)
            server.pluginManager.registerEvents(SproutListener(this), this)
            server.pluginManager.registerEvents(ItemPickupListener(this), this)
            server.pluginManager.registerEvents(CompassListener(this), this)
            server.pluginManager.registerEvents(MobTargetListener(this), this)
            
            markerManager = MarkerManager(this)
            
            PortalManager.init(this)
            
            // Start sprout particle task
            SproutManager.startParticleTask(this)
            
            // Start scoreboard and session check task
            server.scheduler.runTaskTimer(this, Runnable {
                for (session in DungeonSessionManager.getAllSessions().toList()) {
                    session.updateElapsed()
                    
                    val player = session.player
                    if (player != null && player.isOnline) {
                        ScoreboardManager.updateScoreboard(player)
                    }
                    
                    val remaining = session.getRemainingTime()
                    
                    // 警告処理
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
                                    val failMsg = MessageManager.getList(p, "oage_fail").random()
                                    MessageManager.sendOageMessage(p, " 「$failMsg」")
                                    
                                    p.inventory.contents.filterNotNull().forEach { item ->
                                        if (jp.awabi2048.cccontent.features.sukima_dungeon.CustomItemManager.isDungeonItem(item)) {
                                            item.amount = 0
                                        }
                                    }
                                    p.playSound(p.location, org.bukkit.Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.5f)
                                } else {
                                    p.sendMessage(MessageManager.getMessage(p, "dungeon_closing"))
                                }
                                
                                p.inventory.contents.filterNotNull().forEach { item ->
                                    if (jp.awabi2048.cccontent.features.sukima_dungeon.CustomItemManager.isTalismanItem(item)) {
                                        item.amount = 0
                                    }
                                }

                                p.teleport(server.worlds[0].spawnLocation)
                            }
                        }
                        
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
                if (DungeonSessionManager.getAllSessions().isNotEmpty()) {
                    DungeonSessionManager.markDirty()
                }
                mobManager.tick()
                SpecialTileTask.tick(this@CCContent, mobManager)
            }, 20L, 20L)

            // Load sessions
            DungeonSessionManager.loadSessions(this)
            
            featureInitLogger.setStatus("SukimaDungeon", FeatureInitializationLogger.Status.SUCCESS)
        } catch (e: Exception) {
            featureInitLogger.setStatus("SukimaDungeon", FeatureInitializationLogger.Status.FAILURE)
            featureInitLogger.addDetailMessage("SukimaDungeon", "[SukimaDungeon] 初期化失敗: ${e.message}")
            logger.warning("[SukimaDungeon] 初期化に失敗しました: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * SukimaDungeonの設定をリロード
     */
    fun reloadSukimaDungeon() {
        // SukimaDungeon用config読み込み (config/core.yml)
        SukimaConfigHelper.reload(this)
        
        // Load messages
        MessageManager.load(this)
        
        if (!::structureLoader.isInitialized) {
            structureLoader = StructureLoader(this)
        }
        structureLoader.loadThemes()
        
        // Re-initialize or refresh managers
        if (!::mobManager.isInitialized) {
            mobManager = MobManager(this, sharedMobService)
        }
        mobManager.load()
        
        if (!::itemManager.isInitialized) {
            itemManager = ItemManager(this)
        }
        itemManager.load()

        StructureBuilder.init(structureLoader, mobManager, itemManager)
        BGMManager.loadConfig()
    }

    private fun summonConfiguredMob(definitionId: String, location: org.bukkit.Location): org.bukkit.entity.Entity? {
        return sharedMobService.spawnByDefinitionId(
            definitionId,
            location,
            jp.awabi2048.cccontent.mob.MobSpawnOptions(
                featureId = "command",
                metadata = mapOf("source" to "ccc_summon")
            )
        ) ?: sharedMobService.spawnEntityByDefinitionId(
            definitionId,
            location,
            jp.awabi2048.cccontent.mob.EntityMobSpawnOptions(
                featureId = "command",
                metadata = mapOf("source" to "ccc_summon")
            )
        )
    }

    fun getArenaManagerOrNull(): ArenaManager? {
        if (!arenaFeatureReady) return null
        if (!::arenaManager.isInitialized) return null
        return arenaManager
    }
    
    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        PlayerDataManager.load(event.player)
        
        // ダンジョン内にいる場合はBGMを再開
        if (isSukimaDungeonWorld(event.player.world)) {
            BGMManager.play(event.player, "default")
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        BGMManager.stop(event.player)
        PlayerDataManager.unload(event.player)
        MenuCooldownManager.clearCooldown(event.player.uniqueId)
        rankManagerInstance?.hideProfessionBossBar(event.player.uniqueId)
    }
    
    override fun onDisable() {
        stopPlugin()
        logger.info("CC-Content v${description.version} が無効化されました")
    }
}
