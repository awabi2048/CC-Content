package jp.awabi2048.cccontent.features.sukima_dungeon

import jp.awabi2048.cccontent.features.playerdata.PlayerDataFiles
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
        // 破損時は隔離のうえ空扱いとなり、言語はデフォルト復帰する。
        val data = if (file.exists()) {
            val config = PlayerDataFiles.load(file)
            PlayerData(
                lang = config.getString("sukima_dungeon.lang", "ja_jp") ?: "ja_jp"
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
        // rank節を持つ同一ファイルを壊さないよう、読み込み＋原子保存を直列化する。
        PlayerDataFiles.update(file) { config ->
            config.set("sukima_dungeon.lang", data.lang)
        }
    }

    fun getPlayerData(player: Player): PlayerData {
        return dataCache[player.uniqueId] ?: load(player)
    }

    fun unload(player: Player) {
        save(player)
        dataCache.remove(player.uniqueId)
    }

    fun clearAll() {
        dataCache.clear()
    }
}
