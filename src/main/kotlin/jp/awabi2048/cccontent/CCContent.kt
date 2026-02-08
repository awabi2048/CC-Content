package jp.awabi2048.cccontent

import jp.awabi2048.cccontent.command.CCCommand
import jp.awabi2048.cccontent.command.GiveCommand
import jp.awabi2048.cccontent.items.CustomItemManager
import jp.awabi2048.cccontent.items.misc.BigLight
import jp.awabi2048.cccontent.items.misc.SmallLight
import jp.awabi2048.cccontent.items.misc.GulliverItemListener
import jp.awabi2048.cccontent.items.misc.GulliverConfig
import jp.awabi2048.cccontent.items.misc.GulliverScaleManager
import jp.awabi2048.cccontent.items.arena.*
import jp.awabi2048.cccontent.features.arena.ArenaCommand
import jp.awabi2048.cccontent.features.arena.ArenaItemListener
import jp.awabi2048.cccontent.features.arena.ArenaListener
import jp.awabi2048.cccontent.features.arena.ArenaManager
import jp.awabi2048.cccontent.features.rank.RankManager
import jp.awabi2048.cccontent.features.rank.impl.RankManagerImpl
import jp.awabi2048.cccontent.features.rank.impl.YamlRankStorage
import jp.awabi2048.cccontent.features.rank.localization.LanguageLoader
import jp.awabi2048.cccontent.features.rank.localization.MessageProviderImpl
import jp.awabi2048.cccontent.features.rank.command.RankCommand
import jp.awabi2048.cccontent.features.rank.job.IgnoreBlockStore
import jp.awabi2048.cccontent.features.rank.job.ProfessionMinerExpListener
import jp.awabi2048.cccontent.features.rank.profession.Profession
import jp.awabi2048.cccontent.features.rank.profession.SkillTreeRegistry
import jp.awabi2048.cccontent.features.rank.profession.skilltree.ConfigBasedSkillTree
import jp.awabi2048.cccontent.features.rank.tutorial.task.TutorialTaskLoader
import jp.awabi2048.cccontent.features.rank.tutorial.task.TutorialTaskCheckerImpl
import jp.awabi2048.cccontent.features.rank.listener.*
// SukimaDungeon (GitHub版)
import jp.awabi2048.cccontent.features.sukima_dungeon.*
import jp.awabi2048.cccontent.features.sukima_dungeon.generator.StructureLoader
import jp.awabi2048.cccontent.features.sukima_dungeon.generator.StructureBuilder
import jp.awabi2048.cccontent.features.sukima_dungeon.mobs.MobManager
import jp.awabi2048.cccontent.features.sukima_dungeon.items.ItemManager
import jp.awabi2048.cccontent.features.sukima_dungeon.listeners.*
import jp.awabi2048.cccontent.features.sukima_dungeon.tasks.SpecialTileTask
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.event.Listener
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.io.File

class CCContent : JavaPlugin(), Listener {
    
    companion object {
        lateinit var instance: CCContent
            private set
    }
    
    private var playTimeTrackerTaskId: Int = -1
    
    // SukimaDungeon マネージャー (GitHub版)
    private lateinit var structureLoader: StructureLoader
    private lateinit var mobManager: MobManager
    private lateinit var itemManager: ItemManager
    private lateinit var markerManager: MarkerManager
    private lateinit var arenaManager: ArenaManager
    
    fun getMarkerManager(): MarkerManager = markerManager
    fun getItemManager(): ItemManager = itemManager
     
    override fun onEnable() {
        instance = this
        
        // ロガーをCustomItemManagerに設定
        CustomItemManager.setLogger(logger)
        
        // GulliverLight設定の初期化
        GulliverConfig.initialize(this)
        
        // アイテム登録（スキマダンジョン以外）
        registerCustomItems()
        
        // ランクシステムの初期化
        initializeRankSystem()

        // Arena 初期化
        arenaManager = ArenaManager(this)
        arenaManager.initialize()
        
        // コマンド登録
        val giveCommand = GiveCommand()
        val ccCommand = CCCommand(giveCommand) { reloadConfiguration() }
        
        getCommand("cc-content")?.setExecutor(ccCommand)
        getCommand("cc-content")?.tabCompleter = ccCommand
        // 旧エイリアスも対応
        getCommand("cc")?.setExecutor(ccCommand)
        getCommand("cc")?.tabCompleter = ccCommand

        val arenaCommand = ArenaCommand(arenaManager)
        getCommand("arenaa")?.setExecutor(arenaCommand)
        getCommand("arenaa")?.tabCompleter = arenaCommand
        
        // SukimaDungeon 初期化（GitHub版）
        initializeSukimaDungeon()
        
        // リスナー登録（スキマダンジョン以外）
        server.pluginManager.registerEvents(GulliverItemListener(this), this)
        server.pluginManager.registerEvents(ArenaItemListener(), this)
        server.pluginManager.registerEvents(ArenaListener(arenaManager), this)
        
        // ScaleManagerタスクの開始（毎tick実行）
        server.scheduler.runTaskTimer(this, GulliverScaleManager(), 0L, 1L)
        
        logger.info("CC-Content v${description.version} が有効化されました")
        logger.info("作成者: ${description.authors}")
        logger.info("登録されたアイテム数: ${CustomItemManager.getItemCount()}")
        
        // フィーチャー別のアイテム数を表示
        logger.info("  - misc: ${CustomItemManager.getItemCountByFeature("misc")}")
        logger.info("  - arena: ${CustomItemManager.getItemCountByFeature("arena")}")
    }
    
    /**
     * ランクシステムの初期化
     */
    private fun initializeRankSystem() {
        try {
            // ランクストレージとマネージャーを初期化
            val storage = YamlRankStorage(dataFolder)
            val rankManager = RankManagerImpl(storage)
            
            // 言語ファイルを読み込み
            val languageLoader = LanguageLoader(this, "ja_JP")
            val messageProvider = MessageProviderImpl(languageLoader)
            rankManager.setMessageProvider(messageProvider)
            
            // スキルツリーを登録
            registerSkillTrees()

            val ignoreBlockStore = IgnoreBlockStore(File(dataFolder, "job/.ignore_blocks.yml"))
            
            // チュートリアルランク タスクシステムの初期化
            val (taskLoader, taskChecker) = initializeTutorialTaskSystem(rankManager, storage, ignoreBlockStore)
            
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
                logger.info("プレイ時間トラッカーが起動しました（1分ごとに更新）")
            }
            
            // /rank コマンドを登録
            val translator = jp.awabi2048.cccontent.features.rank.tutorial.task.EntityBlockTranslator(messageProvider)
            val rankCommand = RankCommand(rankManager, messageProvider, taskLoader, taskChecker, translator)
            getCommand("rank")?.setExecutor(rankCommand)
            getCommand("rank")?.tabCompleter = rankCommand
            server.pluginManager.registerEvents(rankCommand, this)
            server.pluginManager.registerEvents(ProfessionMinerExpListener(this, rankManager, ignoreBlockStore), this)
            
            logger.info("ランクシステムが初期化されました")
        } catch (e: Exception) {
            logger.warning("ランクシステムの初期化に失敗しました: ${e.message}")
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
            // tutorial-tasks.yml ファイルを確認・抽出
            val tutorialTasksFile = File(dataFolder, "tutorial-tasks.yml")
            if (!tutorialTasksFile.exists()) {
                extractTutorialTasksFile(tutorialTasksFile)
                logger.info("tutorial-tasks.yml ファイルを作成しました")
            }
            
            // TutorialTaskLoader を初期化
            val taskLoader = TutorialTaskLoader()
            taskLoader.loadRequirements(tutorialTasksFile)
            logger.info("チュートリアルランク タスク要件を読み込みました")
            
            // TutorialTaskChecker を作成
            val taskChecker = TutorialTaskCheckerImpl()
            
            // タスク設定から reset_existing_players を読み込み
            val config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(tutorialTasksFile)
            val resetExistingPlayers = config.getBoolean("settings.reset_existing_players", false)
            
            // 7つのイベントリスナーを登録
            server.pluginManager.registerEvents(
                TutorialPlayerJoinListener(rankManager, resetExistingPlayers),
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
            server.pluginManager.registerEvents(
                TutorialRankUpListener(),
                this
            )
            
            logger.info("チュートリアルランク タスクシステムが初期化されました")
            if (resetExistingPlayers) {
                logger.info("既存プレイヤーをNewbieにリセット: ON")
            } else {
                logger.info("既存プレイヤーをNewbieにリセット: OFF")
            }
            
            return Pair(taskLoader, taskChecker)
        } catch (e: Exception) {
            logger.warning("チュートリアルランク タスクシステムの初期化に失敗しました: ${e.message}")
            e.printStackTrace()
            return Pair(null, null)
        }
    }
    
    /**
     * リソースから tutorial-tasks.yml をコピー
     */
    private fun extractTutorialTasksFile(destination: File) {
        val resourceStream = getResource("tutorial-tasks.yml")
            ?: throw IllegalArgumentException("リソースが見つかりません: tutorial-tasks.yml")
        
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
        val jobDir = File(dataFolder, "job").apply { mkdirs() }
        val expFile = File(jobDir, "exp.yml")

        if (!expFile.exists()) {
            try {
                extractJobFile("exp.yml", expFile)
                logger.info("ジョブ経験値設定ファイルを作成しました: exp.yml")
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
                    logger.info("スキルツリーファイルを作成しました: ${profession.id}.yml")
                } catch (e: Exception) {
                    logger.warning("スキルツリーファイルのコピーに失敗しました (${profession.id}): ${e.message}")
                    continue
                }
            }
            
            try {
                val skillTree = ConfigBasedSkillTree(profession.id, ymlFile)
                SkillTreeRegistry.register(profession, skillTree)
                logger.info("スキルツリーを登録しました: ${profession.id}")
            } catch (e: Exception) {
                logger.warning("スキルツリー読み込み失敗 (${profession.id}): ${e.message}")
            }
        }
    }
    
    /**
     * リソースからジョブファイルをコピー
     */
    private fun extractJobFile(resourceName: String, destination: File) {
        val resourceStream = getResource("job/$resourceName")
            ?: throw IllegalArgumentException("リソースが見つかりません: job/$resourceName")
        
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
        
        // Arena アイテム
        CustomItemManager.register(ArenaTicketItem())
        CustomItemManager.register(ArenaMedalItem())
        CustomItemManager.register(ArenaPrizeItem())
        
        // SukimaDungeon アイテムはGitHub版で管理
    }
    
    /**
     * 設定ファイルをリロード
     */
    private fun reloadConfiguration() {
        try {
            logger.info("CC-Content の設定ファイルをリロード中...")

            data class RequiredResource(
                val resourcePath: String,
                val targetPath: String = resourcePath
            )

            // /ccc reload 時に欠損補完する必須リソース
            val requiredResources = listOf(
                RequiredResource("tutorial-tasks.yml"),
                RequiredResource("lang/ja_jp.yml"),
                RequiredResource("lang/en_us.yml"),
                RequiredResource("lang/ja_jp.yml", "lang/ja_JP.yml"),
                RequiredResource("config/gulliverlight.yml"),
                RequiredResource("config/arena/theme.yml"),
                RequiredResource("config/sukima/config.yml"),
                RequiredResource("config/sukima/items.yml"),
                RequiredResource("config/sukima/mobs.yml"),
                RequiredResource("config/sukima/mob_spawn.yml"),
                RequiredResource("config/sukima/theme.yml")
            )
            
            // 必須ディレクトリのリスト
            val requiredDirs = listOf(
                "job",
                "lang",
                "config",
                "data/cccontent/advancement/tutorial"
            )
            
            // 欠損しているファイルを確認してコピー
            for (required in requiredResources) {
                val file = File(dataFolder, required.targetPath)
                if (!file.exists()) {
                    logger.info("欠損しているファイルを検出: ${required.targetPath}")
                    copyResourceFile(required.resourcePath, file)
                }
            }
            
            // 欠損しているディレクトリを作成
            for (dirName in requiredDirs) {
                val dir = File(dataFolder, dirName)
                if (!dir.exists()) {
                    logger.info("欠損しているディレクトリを検出: $dirName")
                    dir.mkdirs()
                    logger.info("ディレクトリを作成: $dirName")
                }
            }
            
            // スキルツリーファイルをチェック
            val jobDir = File(dataFolder, "job")
            if (jobDir.exists()) {
                val requiredJobFiles = listOf("brewer.yml", "lumberjack.yml", "miner.yml", "exp.yml")
                for (jobFile in requiredJobFiles) {
                    val file = File(jobDir, jobFile)
                    if (!file.exists()) {
                        logger.info("欠損しているジョブファイルを検出: job/$jobFile")
                        copyResourceFile("job/$jobFile", file)
                    }
                }
            }
            
            // チュートリアルランク Advancement ファイルをチェック
            val advancementDir = File(dataFolder, "data/cccontent/advancement/tutorial")
            if (advancementDir.exists()) {
                val requiredAdvancementFiles = listOf(
                    "newbie.json", "visitor.json", "pioneer.json", 
                    "adventurer.json", "attainer.json"
                )
                for (advFile in requiredAdvancementFiles) {
                    val file = File(advancementDir, advFile)
                    if (!file.exists()) {
                        logger.info("欠損しているAdvancementファイルを検出: data/cccontent/advancement/tutorial/$advFile")
                        copyResourceFile("data/cccontent/advancement/tutorial/$advFile", file)
                    }
                }
            }
            
            // SukimaDungeonの設定をリロード
            reloadSukimaDungeon()

            if (::arenaManager.isInitialized) {
                arenaManager.reloadThemes()
            }
            
            logger.info("CC-Content の設定ファイルをすべてリロードしました")
        } catch (e: Exception) {
            logger.warning("リロード中にエラーが発生しました: ${e.message}")
            e.printStackTrace()
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
            
            logger.info("ファイルをコピーしました: $resourcePath → ${targetFile.absolutePath}")
        } catch (e: Exception) {
            logger.warning("ファイルのコピーに失敗しました ($resourcePath): ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * SukimaDungeon マネージャーを初期化（GitHub版）
     */
    private fun initializeSukimaDungeon() {
        try {
            logger.info("[SukimaDungeon] 初期化を開始しています...")
            
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
            server.pluginManager.registerEvents(markerManager, this)
            markerManager.startParticleTask()
            
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
                mobManager.tick()
                SpecialTileTask.tick(this@CCContent, mobManager)
            }, 20L, 20L)

            // Load sessions
            DungeonSessionManager.loadSessions(this)
            
            logger.info("[SukimaDungeon] 初期化が完了しました")
        } catch (e: Exception) {
            logger.warning("[SukimaDungeon] 初期化に失敗しました: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * SukimaDungeonの設定をリロード
     */
    fun reloadSukimaDungeon() {
        // SukimaDungeon用config読み込み (config/sukima/config.yml)
        SukimaConfigHelper.reload(this)
        
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
            itemManager = ItemManager(this)
        }
        itemManager.load()

        StructureBuilder.init(structureLoader, mobManager, itemManager)
        BGMManager.loadConfig()
    }
    
    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        PlayerDataManager.load(event.player)
        
        // ダンジョン内にいる場合はBGMを再開
        if (event.player.world.name.startsWith("dungeon_")) {
            BGMManager.play(event.player, "default")
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        BGMManager.stop(event.player)
        PlayerDataManager.unload(event.player)
        MenuCooldownManager.clearCooldown(event.player.uniqueId)
    }
    
    override fun onDisable() {
        // SukimaDungeon クリーンアップ
        try {
            BGMManager.stopAll()
            DungeonSessionManager.saveSessions(this)
            if (::arenaManager.isInitialized) {
                arenaManager.shutdown()
            }
            for (player in server.onlinePlayers) {
                PlayerDataManager.unload(player)
                MenuCooldownManager.clearCooldown(player.uniqueId)
            }
            logger.info("[SukimaDungeon] セッション情報を保存しました")
        } catch (e: Exception) {
            logger.warning("[SukimaDungeon] クリーンアップ中にエラーが発生しました: ${e.message}")
        }
        
        // プレイ時間トラッカータスクを停止
        if (playTimeTrackerTaskId != -1) {
            server.scheduler.cancelTask(playTimeTrackerTaskId)
        }
        
        logger.info("CC-Content v${description.version} が無効化されました")
    }
}
