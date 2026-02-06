package jp.awabi2048.cccontent.arena.config

import jp.awabi2048.cccontent.arena.ArenaMain
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

/**
 * アリーナ機能の設定ファイルを管理するオブジェクト。
 * 設定ファイルは `arena/` サブディレクトリに配置される。
 */
object ArenaDataFile {
    lateinit var config: YamlConfiguration
    lateinit var mobDifficulty: YamlConfiguration
    lateinit var mobType: YamlConfiguration
    lateinit var mobDefinition: YamlConfiguration
    lateinit var items: YamlConfiguration

    lateinit var playerData: YamlConfiguration
    lateinit var ongoingQuestData: YamlConfiguration
    lateinit var playerQuestData: YamlConfiguration
    lateinit var stats: YamlConfiguration

    /**
     * プラグインデータフォルダからすべての設定ファイルを読み込む
     */
    fun loadAll() {
        val plugin = ArenaMain.getPlugin()
        
        // アリーナ設定ディレクトリが存在しない場合は作成
        val arenaDataDir = File(plugin.dataFolder, "arena")
        if (!arenaDataDir.exists()) {
            arenaDataDir.mkdirs()
        }

        // リソースからデフォルトの設定ファイルを保存
        saveDefaultIfNotExists("config.yml")
        saveDefaultIfNotExists("mob_difficulty.yml")
        saveDefaultIfNotExists("mob_type.yml")
        saveDefaultIfNotExists("mob_definition.yml")
        saveDefaultIfNotExists("items.yml")

        
        // 設定ファイルを読み込む
        config = loadYaml("config.yml")
        mobDifficulty = loadYaml("mob_difficulty.yml")
        mobType = loadYaml("mob_type.yml")
        mobDefinition = loadYaml("mob_definition.yml")
        items = loadYaml("items.yml")
        
        // プレイヤーデータファイルを初期化（これらにはデフォルトがない）
        playerData = loadYaml("player_data.yml", createIfMissing = true)
        ongoingQuestData = loadYaml("ongoing_quest_data.yml", createIfMissing = true)
        playerQuestData = loadYaml("player_quest_data.yml", createIfMissing = true)
        stats = loadYaml("stats.yml", createIfMissing = true)
        
        plugin.logger.info("アリーナ設定ファイルの読み込みが完了しました")
    }
    
    /**
     * YAML設定ファイルを読み込む
     */
    private fun loadYaml(fileName: String, createIfMissing: Boolean = false): YamlConfiguration {
        val file = File(ArenaMain.getPlugin().dataFolder, "arena/$fileName")
        
        if (!file.exists() && createIfMissing) {
            file.createNewFile()
        }
        
        return YamlConfiguration.loadConfiguration(file)
    }
    
    /**
     * リソースからデフォルトの設定ファイルを（存在しない場合のみ）保存する
     */
    private fun saveDefaultIfNotExists(fileName: String) {
        val file = File(ArenaMain.getPlugin().dataFolder, "arena/$fileName")
        
        if (!file.exists()) {
            ArenaMain.getPlugin().saveResource("arena/$fileName", false)
            ArenaMain.getPlugin().logger.info("デフォルト設定ファイルを作成しました: arena/$fileName")
        }
    }
    
    /**
     * すべての設定ファイルを再読み込みする
     */
    fun reloadAll() {
        loadAll()
        ArenaMain.getPlugin().logger.info("アリーナ設定ファイルを再読み込みしました")
    }
    
    /**
     * 設定ファイルを保存する
     */
    fun save(config: YamlConfiguration, fileName: String) {
        val file = File(ArenaMain.getPlugin().dataFolder, "arena/$fileName")
        try {
            config.save(file)
        } catch (e: Exception) {
            ArenaMain.getPlugin().logger.severe("設定ファイルの保存に失敗 ($fileName): ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * プレイヤーの個別のデータファイルを取得する
     * ファイルが存在しない場合は作成する
     */
    fun getPlayerDataFile(playerUUID: String): YamlConfiguration {
        val playerDataDir = File(ArenaMain.getPlugin().dataFolder, "arena/playerdata")
        if (!playerDataDir.exists()) {
            playerDataDir.mkdirs()
        }
        
        val playerFile = File(playerDataDir, "$playerUUID.yml")
        if (!playerFile.exists()) {
            playerFile.createNewFile()
        }
        
        return YamlConfiguration.loadConfiguration(playerFile)
    }
    
    /**
     * プレイヤーの個別のデータファイルを保存する
     */
    fun savePlayerDataFile(playerUUID: String, config: YamlConfiguration) {
        val playerDataDir = File(ArenaMain.getPlugin().dataFolder, "arena/playerdata")
        if (!playerDataDir.exists()) {
            playerDataDir.mkdirs()
        }
        
        val playerFile = File(playerDataDir, "$playerUUID.yml")
        try {
            config.save(playerFile)
        } catch (e: Exception) {
            ArenaMain.getPlugin().logger.severe("プレイヤーデータファイルの保存に失敗 ($playerUUID): ${e.message}")
            e.printStackTrace()
        }
    }
}