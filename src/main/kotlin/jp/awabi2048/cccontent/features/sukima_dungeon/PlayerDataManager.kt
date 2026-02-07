package jp.awabi2048.cccontent.features.sukima_dungeon

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.*

object PlayerDataManager {
    private val dataCache = mutableMapOf<UUID, PlayerData>()
    private lateinit var dataDir: File

    data class PlayerData(
        var lang: String = "ja_jp"
    )

    fun init(plugin: JavaPlugin) {
        dataDir = File(plugin.dataFolder, "playerdata")
        if (!dataDir.exists()) {
            dataDir.mkdirs()
        }
    }

    fun load(player: Player): PlayerData {
        val file = File(dataDir, "${player.uniqueId}.yml")
        val data = if (file.exists()) {
            val config = YamlConfiguration.loadConfiguration(file)
            PlayerData(
                lang = config.getString("lang", "ja_jp") ?: "ja_jp"
            )
        } else {
            PlayerData()
        }
        dataCache[player.uniqueId] = data
        return data
    }

    fun save(player: Player) {
        val data = dataCache[player.uniqueId] ?: return
        val file = File(dataDir, "${player.uniqueId}.yml")
        val config = YamlConfiguration()
        config.set("lang", data.lang)
        config.save(file)
    }

    fun getPlayerData(player: Player): PlayerData {
        return dataCache[player.uniqueId] ?: load(player)
    }

    fun unload(player: Player) {
        save(player)
        dataCache.remove(player.uniqueId)
    }
}
