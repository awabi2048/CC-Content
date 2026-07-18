package jp.awabi2048.cccontent

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.config.ConfigClassification
import com.awabi2048.ccsystem.api.config.ConfigMigration
import com.awabi2048.ccsystem.api.config.ManagedConfigSpec
import com.awabi2048.ccsystem.api.gui.MenuTargetPolicy
import com.awabi2048.ccsystem.api.gui.PublicMenuDefinition
import jp.awabi2048.cccontent.command.CCCommand
import jp.awabi2048.cccontent.command.ContentFeatureStatus
import jp.awabi2048.cccontent.command.ContentOperationalState
import jp.awabi2048.cccontent.command.ContentItemGrantProvider
import jp.awabi2048.cccontent.command.StructureCommand
import jp.awabi2048.cccontent.config.CoreConfigManager
import jp.awabi2048.cccontent.config.FeatureConfigManager
import jp.awabi2048.cccontent.config.ResourceConfigurationValidator
import jp.awabi2048.cccontent.featurestate.ContentFeatureCatalog
import jp.awabi2048.cccontent.items.CustomItemI18n
import jp.awabi2048.cccontent.items.CustomItemInteractionListener
import jp.awabi2048.cccontent.items.CustomItemManager
import jp.awabi2048.cccontent.items.misc.BigLight
import jp.awabi2048.cccontent.items.misc.AutoIgnitionBoosterConfig
import jp.awabi2048.cccontent.items.misc.AutoIgnitionBoosterItem
import jp.awabi2048.cccontent.items.misc.AutoIgnitionBoosterListener
import jp.awabi2048.cccontent.items.misc.AirCannonConfig
import jp.awabi2048.cccontent.items.misc.AirCannonItem
import jp.awabi2048.cccontent.items.misc.CustomHeadConfigRegistry
import jp.awabi2048.cccontent.items.misc.CustomHeadGuiListener
import jp.awabi2048.cccontent.items.misc.CustomHeadItem
import jp.awabi2048.cccontent.items.misc.LargeExperienceBottleListener
import jp.awabi2048.cccontent.items.misc.SparklingStoneItem
import jp.awabi2048.cccontent.items.misc.SmallLight
import jp.awabi2048.cccontent.items.misc.GulliverItemListener
import jp.awabi2048.cccontent.items.misc.GulliverConfig
import jp.awabi2048.cccontent.items.misc.GulliverScaleManager
import jp.awabi2048.cccontent.items.misc.HeadDatabaseBridge
import jp.awabi2048.cccontent.items.misc.StorageBoxGuiListener
import jp.awabi2048.cccontent.items.misc.StorageBoxSingleItem
import jp.awabi2048.cccontent.items.misc.StopwatchItem
import jp.awabi2048.cccontent.items.misc.StorageBoxTripleItem
import jp.awabi2048.cccontent.items.misc.TransparentGlowItemFrameItem
import jp.awabi2048.cccontent.items.misc.TransparentItemFrameItem
import jp.awabi2048.cccontent.items.misc.TransparentItemFrameListener
import jp.awabi2048.cccontent.items.misc.ExquisiteSweetBerryItem
import jp.awabi2048.cccontent.items.misc.LargeExperienceBottleItem
import jp.awabi2048.cccontent.items.misc.OldPickaxeItem
import jp.awabi2048.cccontent.items.misc.WornBootsItem
import jp.awabi2048.cccontent.items.marker.AdminMarkerToolService
import jp.awabi2048.cccontent.items.misc.BoxedDaiginjoItem
import jp.awabi2048.cccontent.items.misc.HalfDrunkDaiginjoItem
import jp.awabi2048.cccontent.items.misc.AcornCookieItem
import jp.awabi2048.cccontent.items.misc.TaroSnackItem
import jp.awabi2048.cccontent.items.misc.DecentArmorItem
import jp.awabi2048.cccontent.items.misc.IgnitionLogicItem
import jp.awabi2048.cccontent.items.misc.FireproofLeggingsItem
import jp.awabi2048.cccontent.items.misc.FireproofListener
import jp.awabi2048.cccontent.items.misc.FireworkLoaderItem
import jp.awabi2048.cccontent.items.misc.LampDeviceItem
import jp.awabi2048.cccontent.items.misc.ExpGeneratorItem
import jp.awabi2048.cccontent.items.misc.DenseWindChargeItem
import jp.awabi2048.cccontent.items.misc.MagicConcaveLensItem
import jp.awabi2048.cccontent.items.misc.MagicConvexLensItem
import jp.awabi2048.cccontent.items.misc.BronzeNozzleItem
import jp.awabi2048.cccontent.items.misc.AirTriggerItem
import jp.awabi2048.cccontent.items.misc.DecentBowItem
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
import jp.awabi2048.cccontent.features.arena.ArenaTokenExchangeMenu
import jp.awabi2048.cccontent.features.arena.mission.ArenaMissionService
import jp.awabi2048.cccontent.features.brewery.BreweryFeature
import jp.awabi2048.cccontent.features.cooking.CookingFeature
import jp.awabi2048.cccontent.features.fishing.FishingFeature
import jp.awabi2048.cccontent.features.catalog.CatalogCommand
import jp.awabi2048.cccontent.features.catalog.CatalogStore
import jp.awabi2048.cccontent.features.catalog.CatalogType
import jp.awabi2048.cccontent.features.resourcecollection.ResourceCollectionFeature
import jp.awabi2048.cccontent.features.party.PartyCommand
import jp.awabi2048.cccontent.features.party.PartyController
import jp.awabi2048.cccontent.features.party.PartyListener
import jp.awabi2048.cccontent.features.party.PartyMenuListener
import jp.awabi2048.cccontent.features.party.PartySettingsLoader
import jp.awabi2048.cccontent.features.minigame.core.MiniGameMarkerItem
import jp.awabi2048.cccontent.features.minigame.core.MiniGameMarkerType
import jp.awabi2048.cccontent.features.minigame.core.MiniGameManagerItem
import jp.awabi2048.cccontent.features.minigame.core.MiniGameRuntime
import jp.awabi2048.cccontent.features.npc.menu.NpcMenuService
import jp.awabi2048.cccontent.features.rank.RankManager
import jp.awabi2048.cccontent.features.rank.impl.RankManagerImpl
import jp.awabi2048.cccontent.features.rank.impl.YamlRankStorage
import jp.awabi2048.cccontent.features.rank.localization.LanguageLoader
import jp.awabi2048.cccontent.features.rank.localization.MessageProvider
import jp.awabi2048.cccontent.features.rank.localization.MessageProviderImpl
import jp.awabi2048.cccontent.features.rank.command.RankCommand
import jp.awabi2048.cccontent.features.rank.job.IgnoreBlockStore
import jp.awabi2048.cccontent.features.rank.job.ProfessionCombatExpListener
import jp.awabi2048.cccontent.features.rank.job.ProfessionMinerExpListener
import jp.awabi2048.cccontent.features.rank.profession.Profession
import jp.awabi2048.cccontent.features.rank.profession.SkillTreeRegistry
import jp.awabi2048.cccontent.features.rank.profession.skilltree.CodeDefinedSkillTree
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
import jp.awabi2048.cccontent.structure.SchemStructureService
import jp.awabi2048.cccontent.util.FeatureInitializationLogger
import net.luckperms.api.LuckPerms
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.scheduler.BukkitTask
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
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
    private var arenaTokenExchangeMenu: ArenaTokenExchangeMenu? = null
    private lateinit var sharedMobService: MobService
    private var breweryFeature: BreweryFeature? = null
    private var cookingFeature: CookingFeature? = null
    private var fishingFeature: FishingFeature? = null
    private lateinit var catalogStore: CatalogStore
    private var resourceCollectionFeature: ResourceCollectionFeature? = null
    private var partyController: PartyController? = null
    private var minigameRuntime: MiniGameRuntime? = null
    private lateinit var npcMenuService: NpcMenuService
    private var rankManagerInstance: RankManagerImpl? = null
    private var ignoreBlockStoreInstance: IgnoreBlockStore? = null
    private lateinit var activeContentEnabledSettings: Map<String, Boolean>
    private var arenaFeatureReady: Boolean = false
    private var arenaFeatureFailureReason: String? = null
    private var customItemsLanguageAvailable: Boolean = true

    private class FeatureUnavailableCommand(private val featureId: String) : CommandExecutor, TabCompleter {
        override fun onCommand(
            sender: CommandSender,
            command: Command,
            label: String,
            args: Array<String>
        ): Boolean {
            sender.sendMessage(
                jp.awabi2048.cccontent.command.ContentManagementI18n.text(
                    sender,
                    "feature_unavailable",
                    "feature" to jp.awabi2048.cccontent.command.ContentManagementI18n.text(sender, "feature.$featureId")
                )
            )
            return true
        }

        override fun onTabComplete(
            sender: CommandSender,
            command: Command,
            alias: String,
            args: Array<String>
        ): List<String> = emptyList()
    }
    
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
        CCSystem.getAPI().getMenuCommandService().unregisterOwner("cc-content")
        saveSplitLanguageResources()
        coreConfig = CoreConfigManager.load(this)
        catalogStore = CatalogStore(File(dataFolder, "data/catalog/state.yml"))
        validateAndRegisterLanguageSources()
        activeContentEnabledSettings = loadContentEnabledSettings()

        languageManager = LanguageLoader(this, "ja_jp")

        featureInitLogger = FeatureInitializationLogger(logger)
        featureInitLogger.registerFeature("Rank System")
        featureInitLogger.registerFeature("Brewery")
        featureInitLogger.registerFeature("Cooking")
        featureInitLogger.registerFeature("Fishing")
        featureInitLogger.registerFeature("Resource Collection")
        featureInitLogger.registerFeature("Arena")
        featureInitLogger.registerFeature("SukimaDungeon")
        featureInitLogger.registerFeature("Party")
        featureInitLogger.registerFeature("Minigame")
        featureInitLogger.registerFeature("Oage Shrine")

        // 無効featureの入口を先に閉じ、有効featureの初期化成功時だけ正規コマンドへ差し替える。
        installFeatureUnavailableCommands()

        CustomItemManager.clear()
        CustomItemManager.setLogger(logger)
        CustomItemI18n.initialize(this)

        initializeFeatureIfEnabled("Party", "party") {
            val controller = PartyController(this, PartySettingsLoader(this).load())
            partyController = controller
            val command = PartyCommand(controller)
            getCommand("party")?.setExecutor(command)
            getCommand("party")?.tabCompleter = command
            server.pluginManager.registerEvents(PartyListener(controller), this)
            server.pluginManager.registerEvents(PartyMenuListener(controller), this)
            featureInitLogger.setStatus("Party", FeatureInitializationLogger.Status.SUCCESS)
            featureInitLogger.addSummaryMessage("Party", "Bukkitコマンドとオンライン通知を登録")
        }

        initializeFeatureIfEnabled("Minigame", "minigame") {
            check(featureInitLogger.getStatus("Party") == FeatureInitializationLogger.Status.SUCCESS) {
                "Minigame requires an operational Party feature"
            }
            val controller = checkNotNull(partyController) { "Minigame requires the Party feature" }
            // 参加者を確定するPartyServiceを必ず注入し、全員自動参加へ戻さない。
            val runtime = MiniGameRuntime(this, controller.service)
            minigameRuntime = runtime
            runtime.initialize()
            featureInitLogger.setStatus("Minigame", FeatureInitializationLogger.Status.SUCCESS)
        }

        GulliverConfig.initialize(this)
        AutoIgnitionBoosterConfig.initialize(this)
        AirCannonConfig.initialize(this)
        CustomHeadConfigRegistry.initialize(this)
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
        if (minigameRuntime != null) registerMiniGameItems()

        try {
            val menuService = NpcMenuService(
                plugin = this,
                professionProvider = { playerId -> temporaryBrewerProfessionByLuckPerms(playerId) }
            )
            menuService.initialize()
            npcMenuService = menuService
            featureInitLogger.setStatus("Oage Shrine", FeatureInitializationLogger.Status.SUCCESS)
            featureInitLogger.addSummaryMessage("Oage Shrine", "NPCメニュー登録")
        } catch (e: Exception) {
            featureInitLogger.setStatus("Oage Shrine", FeatureInitializationLogger.Status.FAILURE)
            featureInitLogger.addDetailMessage("Oage Shrine", "[Oage Shrine] 初期化失敗: ${e.message}")
            logger.warning("[Oage Shrine] 初期化に失敗しました: ${e.message}")
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
                    overEnchanterConfigProvider = {
                        FeatureConfigManager.load(this, FeatureConfigManager.ARENA_OVER_ENCHANTER_PATH)
                    },
                    missionServiceProvider = { arenaMissionService },
                    arenaManagerProvider = { arenaManager }
                )
                arenaTokenExchangeMenu = ArenaTokenExchangeMenu(this).apply { initialize() }
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
                arenaTokenExchangeMenu = null
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

        initializeFeatureIfEnabled("Fishing", "fishing") {
            if (rankManagerInstance == null) {
                throw IllegalStateException("Fishing requires the Rank System")
            }
            val feature = FishingFeature(this, catalogStore)
            fishingFeature = feature
            feature.initialize(featureInitLogger)
        }

        initializeFeatureIfEnabled("Resource Collection", "resource_collection") {
            val rankManager = rankManagerInstance
                ?: throw IllegalStateException("Resource Collection requires the Rank System")
            val feature = ResourceCollectionFeature(this, rankManager)
            resourceCollectionFeature = feature
            feature.initialize()
            featureInitLogger.setStatus("Resource Collection", FeatureInitializationLogger.Status.SUCCESS)
        }

        registerCatalogCommands()

        CCSystem.getAPI().getItemGrantService().register(ContentItemGrantProvider())
        val structureCommand = StructureCommand(
            structureService = SchemStructureService(this),
            onArenaStructureSaved = {
                if (::arenaManager.isInitialized) {
                    arenaManager.reloadThemes()
                }
            },
            onSukimaStructureSaved = {
                if (::structureLoader.isInitialized) {
                    structureLoader.loadThemes()
                }
            }
        )
        val ccCommand = CCCommand(
            structureCommand = structureCommand,
            onReload = { reloadConfigFiles() },
            onRestart = { restartPlugin() },
            onClearBlockPlacementData = { clearBlockPlacementData() },
            mobDefinitionIdsProvider = { sharedMobService.getDefinitionIds() },
            onSummonMob = { definitionId, location -> summonConfiguredMob(definitionId, location) },
            onUpdateDay = { target ->
                when (target) {
                    null -> {
                        val arenaUpdated = arenaMissionService?.updateToday() ?: true
                        val oageUpdated = if (::npcMenuService.isInitialized) npcMenuService.resetPartTime() else false
                        arenaUpdated && oageUpdated
                    }
                    "arena" -> arenaMissionService?.updateToday() ?: false
                    else -> false
                }
            },
            onCompleteOageDaily = { player ->
                if (::npcMenuService.isInitialized) {
                    npcMenuService.completeOageDaily(player.uniqueId)
                } else {
                    false
                }
            },
            npcMenuIdsProvider = { if (::npcMenuService.isInitialized) npcMenuService.getMenuIds() else emptyList() },
            onOpenNpcMenu = { menuId, player ->
                if (::npcMenuService.isInitialized) {
                    npcMenuService.open(menuId, player)
                } else {
                    false
                }
            },
            onNpcMenuMaintenance = { menuId, action ->
                if (!::npcMenuService.isInitialized || menuId != "oage_shrine") {
                    false
                } else {
                    when (action) {
                        "reset-delivery" -> npcMenuService.resetDelivery()
                        "reset-part-time" -> npcMenuService.resetPartTime()
                        "reset-shop-daily" -> npcMenuService.resetShopDaily()
                        "reset-shop-weekly" -> npcMenuService.resetShopWeekly()
                        else -> false
                    }
                }
            },
            contentStatusProvider = { contentOperationalStatuses() },
            onSetContentEnabled = { featureId, enabled ->
                val previous = isContentEnabled(featureId)
                CoreConfigManager.setContentEnabled(this, featureId, enabled)
                try {
                    restartPluginLifecycle("content_enabled.$featureId=$enabled")
                } catch (changeFailure: Exception) {
                    runCatching {
                        CoreConfigManager.setContentEnabled(this, featureId, previous)
                        restartPluginLifecycle("content_enabled.$featureId rollback=$previous")
                    }.onFailure(changeFailure::addSuppressed)
                    throw changeFailure
                }
            }
        )

        getCommand("cc-content")?.setExecutor(ccCommand)
        getCommand("cc-content")?.tabCompleter = ccCommand
        getCommand("cc")?.setExecutor(ccCommand)
        getCommand("cc")?.tabCompleter = ccCommand

        if (isContentEnabled("arena")) {
            ArenaI18n.initialize(this)
            val arenaCommand = ArenaCommand(
                arenaManagerProvider = { if (::arenaManager.isInitialized) arenaManager else null },
                missionService = arenaMissionService,
                sessionInfoMenu = arenaSessionInfoMenu,
                pedestalMenu = arenaEnchantPedestalMenu,
                tokenExchangeMenu = arenaTokenExchangeMenu,
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
        if (isContentEnabled("arena") && arenaFeatureReady && ::arenaManager.isInitialized) {
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
            arenaTokenExchangeMenu?.let {
                server.pluginManager.registerEvents(it, this)
            }
        }
        server.pluginManager.registerEvents(CustomHeadGuiListener(this), this)
        server.pluginManager.registerEvents(CustomItemInteractionListener(), this)
        server.pluginManager.registerEvents(LargeExperienceBottleListener(), this)
        server.pluginManager.registerEvents(FireproofListener(), this)
        if (::npcMenuService.isInitialized) {
            server.pluginManager.registerEvents(npcMenuService, this)
        }
        server.pluginManager.registerEvents(StorageBoxGuiListener(this), this)
        server.pluginManager.registerEvents(TransparentItemFrameListener(this), this)
        CustomItemManager.getItem("misc.stopwatch")?.let { item ->
            if (item is StopwatchItem) {
                server.pluginManager.registerEvents(item, this)
            }
        }

        server.scheduler.runTaskTimer(this, GulliverScaleManager(), 0L, 1L)

        featureInitLogger.printSummary()

        startPersistenceFlushTask()
    }

    private fun stopPlugin() {
        val failures = mutableListOf<Throwable>()
        fun cleanup(name: String, action: () -> Unit) {
            runCatching(action).onFailure { error ->
                logger.warning("[CCContent] 停止処理に失敗しました: $name: ${error.message}")
                failures += error
            }
        }

        cleanup("minigame") { minigameRuntime?.shutdown() }
        minigameRuntime = null
        cleanup("arena token exchange") { arenaTokenExchangeMenu?.shutdown() }
        cleanup("listeners") { HandlerList.unregisterAll(this as org.bukkit.plugin.Plugin) }
        cleanup("scheduler") { server.scheduler.cancelTasks(this) }

        cleanup("unlock batch break") { UnlockBatchBreakHandler.stopAll() }
        cleanup("blast mine") { BlastMineHandler.stopAll() }
        cleanup("break speed boost") { BreakSpeedBoostHandler.clearAllBoosts() }
        cleanup("attack reach boost") { AttackReachBoostHandler.removeAllModifiers() }
        cleanup("unlocked item token") { UnlockItemTokenHandler.Companion.clearUnlockedItems() }
        cleanup("replant") { ReplantHandler.clearAllProcessed() }
        cleanup("skill effect cache") { SkillEffectEngine.clearAllCache() }
        cleanup("skill effect registry") { SkillEffectRegistry.clear() }
        cleanup("skill tree registry") { SkillTreeRegistry.clear() }

        cleanup("rank boss bars") { rankManagerInstance?.hideAllProfessionBossBars() }
        cleanup("rank data") { rankManagerInstance?.saveData() }
        cleanup("ignore block data") { ignoreBlockStoreInstance?.flush() }

        cleanup("brewery") { breweryFeature?.shutdown() }
        breweryFeature = null
        cleanup("cooking") { cookingFeature?.shutdown() }
        cookingFeature = null
        cleanup("fishing") { fishingFeature?.shutdown() }
        fishingFeature = null
        cleanup("resource collection") { resourceCollectionFeature?.shutdown() }
        resourceCollectionFeature = null
        cleanup("party") { partyController?.close() }
        partyController = null
        cleanup("arena mission") { arenaMissionService?.shutdown() }
        arenaSessionInfoMenu = null
        arenaEnchantPedestalMenu = null
        arenaTokenExchangeMenu = null
        cleanup("arena providers") {
            if (::arenaManager.isInitialized) {
                arenaManager.setPedestalMenuProvider(null)
                arenaManager.setMissionService(null)
            }
        }
        arenaMissionService = null
        cleanup("arena") { if (::arenaManager.isInitialized) arenaManager.shutdown() }

        cleanup("sukima portal") { PortalManager.shutdown() }
        cleanup("bgm") { BGMManager.stopAll() }
        cleanup("dungeon sessions") { DungeonSessionManager.saveSessions(this) }

        cleanup("persistence task") { persistenceFlushTask?.cancel() }
        persistenceFlushTask = null
        cleanup("shared mob service") { if (::sharedMobService.isInitialized) sharedMobService.shutdown() }

        server.onlinePlayers.forEach { player ->
            cleanup("player data: ${player.uniqueId}") { PlayerDataManager.unload(player) }
            cleanup("menu cooldown: ${player.uniqueId}") { MenuCooldownManager.clearCooldown(player.uniqueId) }
            cleanup("rank boss bar: ${player.uniqueId}") { rankManagerInstance?.hideProfessionBossBar(player.uniqueId) }
        }

        cleanup("player data cache") { PlayerDataManager.clearAll() }
        cleanup("menu cooldown cache") { MenuCooldownManager.clearAll() }
        cleanup("custom items") { CustomItemManager.clear() }
        cleanup("head database bridge") { HeadDatabaseBridge.reset() }
        rankManagerInstance = null
        ignoreBlockStoreInstance = null
        playTimeTrackerTaskId = -1

        if (failures.isNotEmpty()) {
            val failure = IllegalStateException("CC-Content停止処理に${failures.size}件失敗しました")
            failures.forEach(failure::addSuppressed)
            throw failure
        }
        logger.info("[CCContent] 停止処理を完了しました")
    }

    override fun onEnable() {
        synchronizeConfigurationResources()
        validateConfigurationFiles()
        startPlugin()
    }

    private fun loadContentEnabledSettings(): Map<String, Boolean> {
        return ContentFeatureCatalog.features.associate { feature ->
            val path = "content_enabled.${feature.id}"
            check(coreConfig.isBoolean(path)) { "$path must be a boolean" }
            feature.id to coreConfig.getBoolean(path)
        }
    }

    private fun startPersistenceFlushTask() {
        persistenceFlushTask?.cancel()
        val intervalTicks = getPersistenceFlushIntervalMinutes() * 60L * 20L
        persistenceFlushTask = server.scheduler.runTaskTimer(this, Runnable {
            if (isContentEnabled("sukima_dungeon")) {
                DungeonSessionManager.flushIfDirty(this)
            }
            if (isContentEnabled("brewery")) {
                breweryFeature?.flushDirty()
            }
        }, intervalTicks, intervalTicks)
    }

    private fun isContentEnabled(contentKey: String): Boolean {
        return activeContentEnabledSettings[contentKey]
            ?: throw IllegalArgumentException("Unknown content feature: $contentKey")
    }

    private fun contentOperationalStatuses(): Map<String, ContentFeatureStatus> {
        val runtimeNames = mapOf(
            "arena" to "Arena",
            "rank" to "Rank System",
            "brewery" to "Brewery",
            "cooking" to "Cooking",
            "fishing" to "Fishing",
            "resource_collection" to "Resource Collection",
            "sukima_dungeon" to "SukimaDungeon",
            "party" to "Party",
            "minigame" to "Minigame"
        )
        return ContentFeatureCatalog.features.associate { feature ->
            val state = if (!isContentEnabled(feature.id)) {
                ContentOperationalState.DISABLED
            } else {
                when (featureInitLogger.getStatus(checkNotNull(runtimeNames[feature.id]))) {
                    FeatureInitializationLogger.Status.SUCCESS -> ContentOperationalState.ENABLED
                    FeatureInitializationLogger.Status.WARNING -> ContentOperationalState.WARNING
                    FeatureInitializationLogger.Status.FAILURE, null -> ContentOperationalState.FAILURE
                }
            }
            feature.id to ContentFeatureStatus(
                configuredEnabled = isContentEnabled(feature.id),
                operationalState = state
            )
        }
    }

    private fun initializeFeatureIfEnabled(featureName: String, contentKey: String, initialize: () -> Unit) {
        if (!isContentEnabled(contentKey)) {
            featureInitLogger.setStatus(featureName, FeatureInitializationLogger.Status.WARNING)
            featureInitLogger.addSummaryMessage(featureName, "configで無効化(content_enabled.$contentKey=false)")
            logger.info("[$featureName] content_enabled.$contentKey=false のため無効化されています")
            return
        }

        try {
            initialize()
        } catch (e: Exception) {
            closeFeatureEntryPointsAfterFailure(contentKey)
            featureInitLogger.setStatus(featureName, FeatureInitializationLogger.Status.FAILURE)
            featureInitLogger.addDetailMessage(featureName, "[$featureName] 初期化失敗: ${e.message}")
            logger.warning("[$featureName] 初期化に失敗しました: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun installFeatureUnavailableCommands() {
        setFeatureUnavailableCommand("rank", "rank")
        setFeatureUnavailableCommand("rankmenu", "rank")
        setFeatureUnavailableCommand("party", "party")
        setFeatureUnavailableCommand("arenaa", "arena")
        setFeatureUnavailableCommand("sukima_dungeon", "sukima_dungeon")
    }

    private fun setFeatureUnavailableCommand(commandName: String, featureId: String) {
        val command = getCommand(commandName) ?: return
        val unavailable = FeatureUnavailableCommand(featureId)
        command.setExecutor(unavailable)
        command.tabCompleter = unavailable
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
            val messageProvider = MessageProviderImpl(languageManager)
            rankManager.setMessageProvider(messageProvider)
            rankManager.initBossBarManager(this)

            val translator = jp.awabi2048.cccontent.features.rank.tutorial.task.EntityBlockTranslator(messageProvider)
            val rankCommand = RankCommand(rankManager, messageProvider, null, null, translator)

            val ignoreBlockStore = IgnoreBlockStore(File(dataFolder, "data/rank/ignore_blocks.yml"))
            ignoreBlockStoreInstance = ignoreBlockStore

            // スキル効果システムを初期化
            initializeSkillEffectSystem(rankManager, ignoreBlockStore)

            // スキルツリーを登録
            registerSkillTrees()

            // チュートリアルランク タスクシステムの初期化
            val (taskLoader, taskChecker) = initializeTutorialTaskSystem(rankManager, storage, ignoreBlockStore, messageProvider)
            rankCommand.setTutorialTaskSystem(taskLoader, taskChecker)

            // 追加のランク系リスナー登録
            val minerListener = ProfessionMinerExpListener(this, rankManager, ignoreBlockStore)
            val combatExpListener = ProfessionCombatExpListener(
                rankManager,
                FeatureConfigManager.load(this, FeatureConfigManager.RANK_SETTINGS_PATH)
            )
            // 依存設定の構築が完了した後にだけ利用者向け入口を開く。
            server.pluginManager.registerEvents(minerListener, this)
            server.pluginManager.registerEvents(combatExpListener, this)
            server.pluginManager.registerEvents(rankCommand, this)
            server.pluginManager.registerEvents(
                jp.awabi2048.cccontent.features.rank.listener.TutorialRankUpListener(rankManager, messageProvider),
                this
            )
            registerRankCommands(rankCommand)

            // すべての登録完了後に定期処理を開始し、初期化失敗時の残存を防ぐ。
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
            
            // 登録されたスキルツリーをカウント
            val skillTreeCount = Profession.values().count { SkillTreeRegistry.getSkillTree(it) != null }
            featureInitLogger.addSummaryMessage("Rank System", "スキル${skillTreeCount}種登録")
            featureInitLogger.setStatus("Rank System", FeatureInitializationLogger.Status.SUCCESS)
        } catch (e: Exception) {
            if (playTimeTrackerTaskId >= 0) server.scheduler.cancelTask(playTimeTrackerTaskId)
            playTimeTrackerTaskId = -1
            SkillEffectEngine.clearAllCache()
            SkillEffectRegistry.clear()
            SkillTreeRegistry.clear()
            setFeatureUnavailableCommand("rank", "rank")
            setFeatureUnavailableCommand("rankmenu", "rank")
            rankManagerInstance = null
            featureInitLogger.setStatus("Rank System", FeatureInitializationLogger.Status.FAILURE)
            featureInitLogger.addDetailMessage("Rank System", "[Rank System] 初期化失敗: ${e.message}")
            logger.warning("ランクシステムの初期化に失敗しました: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun registerRankCommands(rankCommand: RankCommand) {
        CCSystem.getAPI().getMenuCommandService().register(
            PublicMenuDefinition(
                owner = "cc-content",
                id = "rank",
                permission = "cc-content.rank",
                targetPolicy = MenuTargetPolicy.SELF_ONLY,
                argumentKeys = setOf("view"),
                opener = { player, arguments ->
                    if (arguments["view"].equals("skill", ignoreCase = true)) {
                        rankCommand.openSkillTreeDirect(player)
                    } else {
                        rankCommand.openRankMenu(player)
                    }
                }
            )
        )
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
            FisherBonusHandler.all().forEach(SkillEffectRegistry::register)

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
        ignoreBlockStore: IgnoreBlockStore,
        messageProvider: MessageProvider
    ): Pair<TutorialTaskLoader?, TutorialTaskCheckerImpl?> {
        try {
            if (server.pluginManager.getPlugin("MyWorldManager") == null) {
                logger.severe("MyWorldManagerが見つからないため、チュートリアルランク進行機能を無効化します。その他のランク機能は継続します。")
                return Pair(null, null)
            }
            // ランクアップ要件は固定仕様としてコード側に集約する。
            
            // TutorialTaskLoader を初期化
            val taskLoader = TutorialTaskLoader()
            
            // TutorialTaskChecker を作成
            val taskChecker = TutorialTaskCheckerImpl()
            val routeProgressListener = TutorialRouteProgressListener(this, rankManager, taskChecker, taskLoader, storage, messageProvider)

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
            server.pluginManager.registerEvents(routeProgressListener, this)
            routeProgressListener.startActiveTimeTask()
            
            return Pair(taskLoader, taskChecker)
        } catch (e: Exception) {
            logger.warning("チュートリアルランク タスクシステムの初期化に失敗しました: ${e.message}")
            e.printStackTrace()
            return Pair(null, null)
        }
    }
    
    /**
     * 各職業のスキルツリーを登録
     */
    private fun registerSkillTrees() {
        SkillTreeRegistry.clear()
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
            try {
                val skillTree = CodeDefinedSkillTree.create(profession)
                skillTree.getAllSkills().values.mapNotNull { it.effect?.type }.distinct().forEach { effectType ->
                    val handler = requireNotNull(SkillEffectRegistry.getHandler(effectType)) {
                        "${profession.id}: スキル効果 '$effectType' のハンドラが登録されていません"
                    }
                    require(handler.supportsProfession(profession.id)) {
                        "${profession.id}: スキル効果 '$effectType' はこの職業をサポートしていません"
                    }
                }
                skillTree.getAllSkills().values.mapNotNull { it.effect }.forEach { effect ->
                    require(SkillEffectRegistry.getHandler(effect.type)?.validateParams(effect) == true) {
                        "${profession.id}: スキル効果 '${effect.type}' のパラメータが無効です"
                    }
                }
                SkillTreeRegistry.register(profession, skillTree)
            } catch (e: Exception) {
                e.printStackTrace()
                val error = "コード定義スキルツリー登録失敗 (${profession.id}): ${e.message}"
                logger.warning(error)
                errors += error
            }
        }

        if (errors.isNotEmpty()) {
            throw IllegalStateException("Rank feature 初期化失敗: ${errors.joinToString(" | ")}")
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
        CustomItemManager.register(BoxedDaiginjoItem)
        CustomItemManager.register(SparklingStoneItem())
        CustomItemManager.register(ExquisiteSweetBerryItem())
        CustomItemManager.register(OldPickaxeItem())
        CustomItemManager.register(WornBootsItem())
        CustomItemManager.register(LargeExperienceBottleItem())
        
        // 新規カスタムアイテム (misc)
        CustomItemManager.register(HalfDrunkDaiginjoItem())
        CustomItemManager.register(AcornCookieItem())
        CustomItemManager.register(TaroSnackItem())
        DecentArmorItem.createAll().forEach { CustomItemManager.register(it) }
        CustomItemManager.register(IgnitionLogicItem())
        CustomItemManager.register(FireproofLeggingsItem())
        CustomItemManager.register(FireworkLoaderItem())
        CustomItemManager.register(LampDeviceItem())
        CustomItemManager.register(ExpGeneratorItem())
        CustomItemManager.register(DenseWindChargeItem())
        CustomItemManager.register(MagicConcaveLensItem())
        CustomItemManager.register(MagicConvexLensItem())
        CustomItemManager.register(BronzeNozzleItem())
        CustomItemManager.register(AirTriggerItem())
        CustomItemManager.register(DecentBowItem())
        CustomItemManager.register(StopwatchItem(this))
        
        registerCustomHeadItems()

        if (isContentEnabled("brewery")) {
            CustomItemManager.register(BrewerySampleFilterItem(this))
            CustomItemManager.register(BreweryMockClockItem(this))
            CustomItemManager.register(BreweryMockYeastItem(this))
        }

        if (isContentEnabled("arena")) {
            CustomItemManager.register(ArenaStructureMarkerToolItem())
            CustomItemManager.register(ArenaOtherMarkerToolItem())
            CustomItemManager.register(ArenaLiftToolItem())
            CustomItemManager.register(ArenaMechanicMarkerToolItem())
            CustomItemManager.register(ArenaEnchantShardItem())
        }

        if (isContentEnabled("sukima_dungeon")) {
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

    private fun registerMiniGameItems() {
        val pdc = checkNotNull(minigameRuntime) { "Minigame runtime is not initialized" }.markerPdc()
        CustomItemManager.register(MiniGameManagerItem(pdc))
        CustomItemManager.register(MiniGameManagerItem(pdc, "hideandseek"))
        CustomItemManager.register(MiniGameManagerItem(pdc, "chase"))
        CustomItemManager.register(MiniGameManagerItem(pdc, "colosseum"))
        CustomItemManager.register(MiniGameManagerItem(pdc, "endergolf"))
        setOf(MiniGameMarkerType.START, MiniGameMarkerType.CHECKPOINT, MiniGameMarkerType.GOAL, MiniGameMarkerType.JAIL).forEach { type ->
            CustomItemManager.register(MiniGameMarkerItem(pdc, type))
        }
        CustomItemManager.register(MiniGameMarkerItem(pdc, MiniGameMarkerType.START, "colosseum"))
        setOf(MiniGameMarkerType.START, MiniGameMarkerType.CUP, MiniGameMarkerType.WATER_HAZARD, MiniGameMarkerType.BUNKER).forEach { type ->
            CustomItemManager.register(MiniGameMarkerItem(pdc, type, "endergolf"))
        }
    }

    private fun closeFeatureEntryPointsAfterFailure(contentKey: String) {
        when (contentKey) {
            "party" -> {
                runCatching { partyController?.close() }
                partyController = null
                setFeatureUnavailableCommand("party", "party")
            }
            "minigame" -> {
                runCatching { minigameRuntime?.shutdown() }
                minigameRuntime = null
            }
            "rank" -> {
                if (playTimeTrackerTaskId >= 0) server.scheduler.cancelTask(playTimeTrackerTaskId)
                playTimeTrackerTaskId = -1
                setFeatureUnavailableCommand("rank", "rank")
                setFeatureUnavailableCommand("rankmenu", "rank")
            }
            "sukima_dungeon" -> setFeatureUnavailableCommand("sukima_dungeon", "sukima_dungeon")
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

    private fun temporaryBrewerProfessionByLuckPerms(playerId: java.util.UUID): Profession? {
        // 一時措置: Rank側の職業判定が安定するまで、奉納品の日課だけ LuckPerms の brewer グループで醸造家判定を代替する。
        val luckPerms = server.servicesManager.getRegistration(LuckPerms::class.java)?.provider ?: return null
        val user = luckPerms.userManager.getUser(playerId) ?: luckPerms.userManager.loadUser(playerId).getNow(null) ?: return null
        return if (user.primaryGroup.equals("brewer", ignoreCase = true)) Profession.BREWER else null
    }

    /**
     * config 配下の設定を再読込
     */
    private fun reloadConfigFiles() {
        restartPluginLifecycle("config reload")
    }

    private fun ensureCCSystemAvailable() {
        val ccSystemPlugin = server.pluginManager.getPlugin("CC-System")
        if (ccSystemPlugin == null || !ccSystemPlugin.isEnabled) {
            throw IllegalStateException("CC-System が有効化されていないため CC-Content を起動できません")
        }
    }

    private fun validateAndRegisterLanguageSources() {
        val api = CCSystem.getAPI()
        val validationResult = api.validateI18nSource(CCSystem.instance, contentLanguageFeatureByFile())
        languageErrorsByFeature = validationResult.errorsByFeature
        customItemsLanguageAvailable = !hasLanguageErrorsFor("custom_items")
    }

    private fun hasLanguageErrorsFor(feature: String): Boolean {
        return languageErrorsFor(feature).isNotEmpty()
    }

    private fun languageErrorsFor(feature: String): List<String> {
        return languageErrorsByFeature[feature].orEmpty()
    }

    private fun contentLanguageFeatureByFile(): Map<String, String> {
        return buildMap {
            put("content/arena.yml", "arena")
            put("content/custom_items.yml", "custom_items")
            put("content/brewery.yml", "brewery")
            put("content/cooking.yml", "cooking")
            put("content/fishing.yml", "fishing")
            put("content/sukima_dungeon.yml", "sukima_dungeon")
            put("content/rank.yml", "rank")
            put("content/profession.yml", "rank")
            put("content/skill.yml", "rank")
            put("content/tutorial_rank.yml", "rank")
            put("content/mission.yml", "rank")
            put("content/gui.yml", "rank")
            put("content/minigame.yml", "minigame")
            put("content/resource_collection.yml", "resource_collection")
            put("content/management.yml", "management")
        }
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

    private fun mergeMissingLanguageKeys() {
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
                        return@forEach
                    }

                    val defaults = jar.getInputStream(entry).use { input ->
                        YamlConfiguration.loadConfiguration(InputStreamReader(input, StandardCharsets.UTF_8))
                    }
                    val current = YamlConfiguration.loadConfiguration(target)
                    val missingKeys = defaults.getKeys(true)
                        .filter { key -> !defaults.isConfigurationSection(key) && !current.contains(key) }

                    if (missingKeys.isEmpty()) {
                        return@forEach
                    }

                    missingKeys.forEach { key ->
                        current.set(key, defaults.get(key))
                    }
                    current.save(target)
                    logger.info("[CCContent][Lang] 欠けている言語キーを補完しました: ${entry.name} (${missingKeys.size} keys)")
                }
        }
    }

    /**
     * プラグインを再起動相当に再初期化
     */
    private fun restartPlugin() {
        restartPluginLifecycle("restart")
    }

    /**
     * 全 feature を停止してから設定を検証し、同じ起動経路で再初期化する。
     * 個別 feature の reload は状態・イベント・コマンドの取り残しを作るため使用しない。
     */
    private fun restartPluginLifecycle(reason: String) {
        ArenaI18n.clearCache()
        stopPlugin()
        saveSplitLanguageResources()
        mergeMissingLanguageKeys()
        synchronizeConfigurationResources()
        validateConfigurationFiles()
        startPlugin()
        logger.info("[CCContent] $reason lifecycle を完了しました")
    }

    private fun synchronizeConfigurationResources() {
        registerManagedConfigs()
    }

    private fun validateConfigurationFiles() {
        val configRoot = File(dataFolder, "config").toPath()
        val errors = ResourceConfigurationValidator.validateConfigDirectory(configRoot)
        if (errors.isNotEmpty()) {
            throw IllegalStateException(
                "CC-Content設定の検証に失敗しました (${errors.size}件):\n${errors.joinToString("\n")}"
            )
        }
    }

    private fun registerManagedConfigs() {
        val codeSource = runCatching { File(javaClass.protectionDomain.codeSource.location.toURI()) }
            .getOrNull() ?: return
        if (!codeSource.isFile) return

        val paths = JarFile(codeSource).use { jar ->
            jar.entries().asSequence()
                .filter { !it.isDirectory && it.name.startsWith("config/") && it.name.endsWith(".yml") }
                .map { it.name }
                .toList()
        }
        val bundledMarkers = listOf(
            "/themes/",
            "/recipe",
            "ingredient_definition",
            "mob_definition",
            "/mob_type",
            "/drop",
            "/reward",
            "/collection",
            "/custom_item/",
            "/npc/menu",
            "/minigame/",
            "/job_exp",
            "/mission",
            "/loot",
            "/theme"
        )
        val specs = paths.map { resourcePath ->
            val bundled = getResource(resourcePath)?.use { input ->
                YamlConfiguration.loadConfiguration(InputStreamReader(input, StandardCharsets.UTF_8))
            } ?: error("Missing bundled config: $resourcePath")
            val currentVersion = bundled.getInt("config_version", 1)
            val classification = if (bundledMarkers.any(resourcePath::contains)) {
                ConfigClassification.BUNDLED_DEFINITION
            } else {
                ConfigClassification.MANAGED_CONFIG
            }
            ManagedConfigSpec(
                owner = "cc-content",
                sourcePlugin = this,
                resourcePath = resourcePath,
                targetPath = File(dataFolder, resourcePath).toPath(),
                currentVersion = currentVersion,
                classification = classification,
                migrations = if (currentVersion > 1) {
                    (1 until currentVersion).associateWith {
                        ConfigMigration { configuration ->
                            configuration.set("schema_version", null)
                            if (classification == ConfigClassification.BUNDLED_DEFINITION) {
                                bundled.getKeys(false)
                                    .filter { key -> key != "config_version" }
                                    .forEach { key -> configuration.set(key, bundled.get(key)) }
                            } else {
                                bundled.getKeys(true)
                                    .filter { key -> key != "config_version" }
                                    .filterNot(bundled::isConfigurationSection)
                                    .filterNot(configuration::contains)
                                    .forEach { key -> configuration.set(key, bundled.get(key)) }
                            }
                        }
                    }
                } else {
                    emptyMap()
                },
                validator = com.awabi2048.ccsystem.api.config.ConfigValidator {},
                reloadAction = { restartPluginLifecycle("cc config reload") }
            )
        }
        CCSystem.getAPI().getConfigSchemaService().register("cc-content", specs)
        val result = CCSystem.getAPI().getConfigSchemaService().prepare("cc-content")
        check(result.successful) {
            "CC-Content Config preparation failed: ${result.statuses.filter { it.message != null }}"
        }
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
        val feature = BreweryFeature(this, catalogStore)
        breweryFeature = feature
        feature.initialize(featureInitLogger)
    }

    private fun initializeCooking() {
        val feature = CookingFeature(this, { rankManagerInstance }, catalogStore)
        cookingFeature = feature
        feature.initialize(featureInitLogger)
    }

    private fun registerCatalogCommands() {
        val command = CatalogCommand(
            store = catalogStore,
            items = { type ->
                when (type) {
                    CatalogType.FISHING -> fishingFeature?.catalogItems().orEmpty()
                    CatalogType.COOKING -> cookingFeature?.catalogItems().orEmpty()
                    CatalogType.BREWERY -> breweryFeature?.catalogItems().orEmpty()
                }
            },
            isAvailable = { type ->
                contentOperationalStatuses()[type.id]?.operationalState == ContentOperationalState.ENABLED
            },
            fishingSearchTarget = { playerId -> fishingFeature?.getSearchTarget(playerId) },
            setFishingSearchTarget = { player, fishId -> fishingFeature?.setSearchTarget(player, fishId) }
        )
        CCSystem.getAPI().getMenuCommandService().register(
            PublicMenuDefinition(
                owner = "cc-content",
                id = "catalog",
                permission = "cc-content.catalog",
                targetPolicy = MenuTargetPolicy.SELF_ONLY,
                argumentKeys = setOf("type", "page"),
                opener = command::openFromPublicRoute
            )
        )
        server.pluginManager.registerEvents(command, this)
        listOf("catalog", "cookdex", "brewdex").forEach { name ->
            getCommand(name)?.setExecutor(command)
        }
        fishingFeature?.registerDictionary { player ->
            command.open(player, CatalogType.FISHING)
        }
    }

    /**
     * SukimaDungeon マネージャーを初期化（GitHub版）
     */
    private fun initializeSukimaDungeon() {
        try {
            // 設定をリロード
            loadSukimaDungeonConfiguration()
            BGMManager.loadConfig()
            
            val commandExecutor = MazeCommand(this, structureLoader)
            
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

            // 全初期化の完了後にだけ利用者向け入口を開く。
            getCommand("sukima_dungeon")?.setExecutor(commandExecutor)
            getCommand("sukima_dungeon")?.tabCompleter = commandExecutor
            
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
        // 旧部分再読込の入口も、全 feature の停止・検証・再初期化へ統一する。
        restartPluginLifecycle("SukimaDungeon reload")
    }

    private fun loadSukimaDungeonConfiguration() {
        // SukimaDungeon用config読み込み (config/core.yml)
        SukimaConfigHelper.reload(this)
        
        // Load messages
        MessageManager.load(this)
        
        // 完全再初期化では旧managerを再利用せず、現在の設定と依存を持つ新規instanceを構築する。
        structureLoader = StructureLoader(this)
        structureLoader.loadThemes()
        
        mobManager = MobManager(this, sharedMobService)
        mobManager.load()
        
        itemManager = ItemManager(this)
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
        runCatching { CCSystem.getAPI().getItemGrantService().unregister("cc-content") }
        runCatching { CCSystem.getAPI().getConfigSchemaService().unregister("cc-content") }
        runCatching { CCSystem.getAPI().getMenuCommandService().unregisterOwner("cc-content") }
        logger.info("CC-Content v${pluginMeta.version} が無効化されました")
    }
}
