package jp.awabi2048.cccontent.features.sukima_dungeon

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * プレイヤーデータ管理クラス
 * プレイヤーのダンジョン関連データを永続化する
 */
class PlayerDataManager(private val plugin: JavaPlugin) {
    
    /**
     * プレイヤーデータクラス
     */
    data class PlayerData(
        val uuid: UUID,
        var language: String = "ja_jp",
        var isDown: Boolean = false,
        var currentWorldName: String? = null
    )
    
    private val playerDataCache: MutableMap<UUID, PlayerData> = ConcurrentHashMap()
    private val dataFolder = File(plugin.dataFolder, "playerdata")
    
    init {
        if (!dataFolder.exists()) {
            dataFolder.mkdirs()
        }
    }
    
    /**
     * プレイヤーデータを取得（キャッシュから、なければディスクから読み込み）
     */
    fun getPlayerData(player: Player): PlayerData {
        val uuid = player.uniqueId
        
        // キャッシュに存在すればそれを返す
        playerDataCache[uuid]?.let { return it }
        
        // ディスクから読み込み
        return loadPlayerData(uuid)
    }
    
    /**
     * ディスクからプレイヤーデータを読み込み
     */
    fun loadPlayerData(uuid: UUID): PlayerData {
        val file = File(dataFolder, "$uuid.yml")
        
        val data = if (file.exists()) {
            try {
                val config = YamlConfiguration.loadConfiguration(file)
                PlayerData(
                    uuid = uuid,
                    language = config.getString("language", "ja_jp") ?: "ja_jp",
                    isDown = config.getBoolean("is_down", false),
                    currentWorldName = config.getString("current_world_name")
                )
            } catch (e: Exception) {
                plugin.logger.warning("[SukimaDungeon] プレイヤーデータ読み込みエラー ($uuid): ${e.message}")
                PlayerData(uuid = uuid)
            }
        } else {
            PlayerData(uuid = uuid)
        }
        
        // キャッシュに保存
        playerDataCache[uuid] = data
        return data
    }
    
    /**
     * プレイヤーデータをディスクに保存
     */
    fun savePlayerData(uuid: UUID) {
        val data = playerDataCache[uuid] ?: return
        
        val file = File(dataFolder, "$uuid.yml")
        val config = YamlConfiguration()
        
        config.set("language", data.language)
        config.set("is_down", data.isDown)
        config.set("current_world_name", data.currentWorldName)
        
        try {
            config.save(file)
        } catch (e: Exception) {
            plugin.logger.warning("[SukimaDungeon] プレイヤーデータ保存エラー ($uuid): ${e.message}")
        }
    }
    
    /**
     * すべてのプレイヤーデータを保存
     */
    fun saveAllPlayerData() {
        for ((uuid, _) in playerDataCache) {
            savePlayerData(uuid)
        }
    }
    
    /**
     * プレイヤーデータをアンロード（キャッシュと共に保存）
     */
    fun unloadPlayerData(uuid: UUID) {
        savePlayerData(uuid)
        playerDataCache.remove(uuid)
    }
    
    /**
     * プレイヤーデータをクリア（キャッシュのみ）
     */
    fun clearPlayerDataCache(uuid: UUID) {
        playerDataCache.remove(uuid)
    }
    
    /**
     * すべてのプレイヤーデータをクリア
     */
    fun clearAllPlayerDataCache() {
        playerDataCache.clear()
    }
    
    /**
     * キャッシュに保存されているデータ数を取得
     */
    fun getCacheSize(): Int {
        return playerDataCache.size
    }
}