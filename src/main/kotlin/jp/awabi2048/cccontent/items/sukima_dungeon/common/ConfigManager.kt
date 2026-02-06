package jp.awabi2048.cccontent.items.sukima_dungeon.common

import org.bukkit.Location
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

/**
 * SukimaDungeon 設定管理クラス
 * config.yml の設定を読み込み、管理する
 */
class ConfigManager(private val plugin: JavaPlugin) {
    private var config: YamlConfiguration? = null
    private val configFile: File
    
    // 設定キャッシュ
    var language: String = "ja_jp"
        private set
    
    var dungeonWorlds: List<String> = listOf("world")
        private set
    
    var escapeLocation: Location? = null
        private set
    
    var defaultCompassRadius: Double = 48.0
        private set
    
    var debugEnabled: Boolean = false
        private set
    
    var verboseLogging: Boolean = false
        private set
    
    // コンパスTier設定
    data class CompassTierConfig(
        val name: String,
        val duration: Int,
        val cooldown: Int,
        val debuffs: List<String>
    )
    
    val compassTiers: MutableMap<Int, CompassTierConfig> = mutableMapOf()
    
    // タリスマン設定
    var talismanEscapeDelay: Int = 3
        private set
    
     // ワールドの芽設定
     var sproutDropChance: Double = 1.0
         private set
     
     var sproutRespawnTime: Int = 300
         private set
     
     // ダンジョンティア設定
     data class DungeonTierConfig(
         val availableSizes: List<String>,
         val availableThemes: List<String>
     )
     
     val dungeonTiers: MutableMap<String, DungeonTierConfig> = mutableMapOf()
     
     // ダンジョンサイズ設定
     data class DungeonSizeConfig(
         val tiles: Int,
         val duration: Int,
         val sproutBaseCount: Int
     )
     
     val dungeonSizes: MutableMap<String, DungeonSizeConfig> = mutableMapOf()
     
     // ダンジョンテーマ設定
     data class DungeonThemeConfig(
         val displayName: String,
         val icon: String = "BOOK"
     )
     
     val dungeonThemes: MutableMap<String, DungeonThemeConfig> = mutableMapOf()
     
     init {
        val configDir = File(plugin.dataFolder, "config/sukima").apply { mkdirs() }
        configFile = File(configDir, "config.yml")
        
        // デフォルト設定ファイルを抽出
        if (!configFile.exists()) {
            extractDefaultConfig()
        }
        
        loadConfig()
    }
    
    /**
     * デフォルト設定ファイルをリソースから抽出
     */
    private fun extractDefaultConfig() {
        val resourcePath = "config/sukima/config.yml"
        val resourceStream = plugin.getResource(resourcePath)
        
        if (resourceStream != null) {
            configFile.parentFile?.mkdirs()
            resourceStream.use { input ->
                configFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            plugin.logger.info("[SukimaDungeon] デフォルト設定ファイルを作成しました")
        } else {
            plugin.logger.warning("[SukimaDungeon] リソースファイルが見つかりません: $resourcePath")
        }
    }
    
    /**
     * 設定ファイルを読み込む
     */
    fun loadConfig() {
        try {
            config = YamlConfiguration.loadConfiguration(configFile)
            
            // 基本設定
            language = config?.getString("general.language") ?: "ja_jp"
            dungeonWorlds = config?.getStringList("general.dungeon_worlds") ?: listOf("world")
            
            // 脱出地点
            val world = config?.getString("general.escape_location.world")
            val x = config?.getDouble("general.escape_location.x") ?: 0.0
            val y = config?.getDouble("general.escape_location.y") ?: 64.0
            val z = config?.getDouble("general.escape_location.z") ?: 0.0
            val yaw = config?.getDouble("general.escape_location.yaw")?.toFloat() ?: 0f
            val pitch = config?.getDouble("general.escape_location.pitch")?.toFloat() ?: 0f
            
            if (world != null) {
                val bukkitWorld = plugin.server.getWorld(world)
                if (bukkitWorld != null) {
                    escapeLocation = Location(bukkitWorld, x, y, z, yaw, pitch)
                } else {
                    plugin.logger.warning("[SukimaDungeon] 脱出地点のワールドが見つかりません: $world")
                }
            }
            
            // コンパス設定
            defaultCompassRadius = config?.getDouble("compass.default_radius") ?: 48.0
            
            // コンパスTier設定を読み込む
            compassTiers.clear()
            for (tier in 1..3) {
                val tierSection = config?.getConfigurationSection("compass.tiers.tier$tier")
                if (tierSection != null) {
                    val tierConfig = CompassTierConfig(
                        name = tierSection.getString("name") ?: "Tier $tier",
                        duration = tierSection.getInt("duration", 30),
                        cooldown = tierSection.getInt("cooldown", 60),
                        debuffs = tierSection.getStringList("debuffs")
                    )
                    compassTiers[tier] = tierConfig
                }
            }
            
            // タリスマン設定
            talismanEscapeDelay = config?.getInt("talisman.escape_delay") ?: 3
            
             // ワールドの芽設定
             sproutDropChance = config?.getDouble("sprout.drop_chance") ?: 1.0
             sproutRespawnTime = config?.getInt("sprout.respawn_time") ?: 300
             
             // ダンジョンティア設定を読み込む
             dungeonTiers.clear()
             val tiersSection = config?.getConfigurationSection("tiers")
             if (tiersSection != null) {
                 for (tierKey in tiersSection.getKeys(false)) {
                     val tierSection = tiersSection.getConfigurationSection(tierKey)
                     if (tierSection != null) {
                         val tierConfig = DungeonTierConfig(
                             availableSizes = tierSection.getStringList("available_sizes"),
                             availableThemes = tierSection.getStringList("available_themes")
                         )
                         dungeonTiers[tierKey] = tierConfig
                     }
                 }
             }
             
             // ダンジョンサイズ設定を読み込む
             dungeonSizes.clear()
             val sizesSection = config?.getConfigurationSection("sizes")
             if (sizesSection != null) {
                 for (sizeKey in sizesSection.getKeys(false)) {
                     val sizeSection = sizesSection.getConfigurationSection(sizeKey)
                     if (sizeSection != null) {
                         val sizeConfig = DungeonSizeConfig(
                             tiles = sizeSection.getInt("tiles", 5),
                             duration = sizeSection.getInt("duration", 600),
                             sproutBaseCount = sizeSection.getInt("sprout_base_count", 3)
                         )
                         dungeonSizes[sizeKey] = sizeConfig
                     }
                 }
             }
             
             // ダンジョンテーマ設定を読み込む（デフォルト）
             dungeonThemes.clear()
             dungeonThemes["default"] = DungeonThemeConfig("デフォルト", "BOOK")
             dungeonThemes["nether"] = DungeonThemeConfig("ネザー", "NETHERITE_BLOCK")
             dungeonThemes["end"] = DungeonThemeConfig("エンド", "DRAGON_BREATH")
             
             // デバッグ設定
             debugEnabled = config?.getBoolean("debug.enabled") ?: false
             verboseLogging = config?.getBoolean("debug.verbose_logging") ?: false
             
             plugin.logger.info("[SukimaDungeon] 設定ファイルを読み込みました")
             if (debugEnabled) {
                 plugin.logger.info("[SukimaDungeon] デバッグモードが有効です")
             }
         } catch (e: Exception) {
             plugin.logger.warning("[SukimaDungeon] 設定ファイルの読み込みに失敗しました: ${e.message}")
             e.printStackTrace()
         }
     }
    
    /**
     * 設定をリロード
     */
    fun reloadConfig() {
        loadConfig()
    }
    
     /**
      * 指定されたTierの設定を取得
      */
     fun getCompassTierConfig(tier: Int): CompassTierConfig? {
         return compassTiers[tier]
     }
     
     /**
      * 指定されたダンジョンティアの設定を取得
      */
     fun getDungeonTierConfig(tier: String): DungeonTierConfig? {
         return dungeonTiers[tier]
     }
     
     /**
      * 指定されたダンジョンサイズの設定を取得
      */
     fun getDungeonSizeConfig(size: String): DungeonSizeConfig? {
         return dungeonSizes[size]
     }
     
     /**
      * 指定されたダンジョンテーマの設定を取得
      */
     fun getDungeonThemeConfig(theme: String): DungeonThemeConfig? {
         return dungeonThemes[theme]
     }
     
     /**
      * 全ダンジョンティア名を取得
      */
     fun getAllDungeonTiers(): List<String> {
         return dungeonTiers.keys.toList()
     }
     
     /**
      * 全ダンジョンサイズ名を取得
      */
     fun getAllDungeonSizes(): List<String> {
         return dungeonSizes.keys.toList()
     }
     
     /**
      * 全ダンジョンテーマ名を取得
      */
     fun getAllDungeonThemes(): List<String> {
         return dungeonThemes.keys.toList()
     }
     
     /**
      * デバッグログを出力
      */
     fun debug(message: String) {
         if (debugEnabled && verboseLogging) {
             plugin.logger.info("[SukimaDungeon-DEBUG] $message")
         }
     }
 }