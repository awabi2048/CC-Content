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
import jp.awabi2048.cccontent.items.sukima_dungeon.*
import jp.awabi2048.cccontent.features.rank.RankManager
import jp.awabi2048.cccontent.features.rank.impl.RankManagerImpl
import jp.awabi2048.cccontent.features.rank.impl.YamlRankStorage
import jp.awabi2048.cccontent.features.rank.localization.LanguageLoader
import jp.awabi2048.cccontent.features.rank.localization.MessageProviderImpl
import jp.awabi2048.cccontent.features.rank.command.RankCommand
import jp.awabi2048.cccontent.features.rank.profession.Profession
import jp.awabi2048.cccontent.features.rank.profession.SkillTreeRegistry
import jp.awabi2048.cccontent.features.rank.profession.skilltree.ConfigBasedSkillTree
import jp.awabi2048.cccontent.features.rank.tutorial.task.TutorialTaskLoader
import jp.awabi2048.cccontent.features.rank.tutorial.task.TutorialTaskCheckerImpl
import jp.awabi2048.cccontent.features.rank.listener.*
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class CCContent : JavaPlugin() {
    
    companion object {
        lateinit var instance: CCContent
            private set
    }
    
    override fun onEnable() {
        instance = this
        
        // ロガーをCustomItemManagerに設定
        CustomItemManager.setLogger(logger)
        
        // GulliverLight設定の初期化
        GulliverConfig.initialize(this)
        
        // アイテム登録
        registerCustomItems()
        
        // ランクシステムの初期化
        initializeRankSystem()
        
        // コマンド登録
        val giveCommand = GiveCommand()
        val ccCommand = CCCommand(giveCommand)
        
         getCommand("cc")?.setExecutor(ccCommand)
         getCommand("cc")?.tabCompleter = ccCommand
        
        // リスナー登録
        server.pluginManager.registerEvents(GulliverItemListener(this), this)
        server.pluginManager.registerEvents(ArenaItemListener(), this)
        server.pluginManager.registerEvents(SukimaItemListener(), this)
        
        // ScaleManagerタスクの開始（毎tick実行）
        server.scheduler.runTaskTimer(this, GulliverScaleManager(), 0L, 1L)
        
        logger.info("CC-Content v${description.version} が有効化されました")
        logger.info("作成者: ${description.authors}")
        logger.info("登録されたアイテム数: ${CustomItemManager.getItemCount()}")
        
        // フィーチャー別のアイテム数を表示
        logger.info("  - misc: ${CustomItemManager.getItemCountByFeature("misc")}")
        logger.info("  - arena: ${CustomItemManager.getItemCountByFeature("arena")}")
        logger.info("  - sukima_dungeon: ${CustomItemManager.getItemCountByFeature("sukima_dungeon")}")
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
            
            // チュートリアルランク タスクシステムの初期化
            initializeTutorialTaskSystem(rankManager, storage)
            
            // /rank コマンドを登録
            val rankCommand = RankCommand(rankManager, messageProvider)
            getCommand("rank")?.setExecutor(rankCommand)
            getCommand("rank")?.tabCompleter = rankCommand
            
            logger.info("ランクシステムが初期化されました")
        } catch (e: Exception) {
            logger.warning("ランクシステムの初期化に失敗しました: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * チュートリアルランク タスクシステムの初期化
     */
    private fun initializeTutorialTaskSystem(rankManager: RankManager, storage: YamlRankStorage) {
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
                TutorialBlockBreakListener(rankManager, taskChecker, taskLoader, storage),
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
        } catch (e: Exception) {
            logger.warning("チュートリアルランク タスクシステムの初期化に失敗しました: ${e.message}")
            e.printStackTrace()
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
        
        // KotaArena アイテム
        CustomItemManager.register(SoulBottleItem())
        CustomItemManager.register(BoosterItem())
        CustomItemManager.register(MobDropSackItem())
        CustomItemManager.register(HunterTalismanItem())
        CustomItemManager.register(GolemTalismanItem())
        
        // SukimaDungeon アイテム
        CustomItemManager.register(SproutItem())
        CustomItemManager.register(CompassItem())
        CustomItemManager.register(TalismanItem())
    }
    
    override fun onDisable() {
        logger.info("CC-Content v${description.version} が無効化されました")
    }
}
