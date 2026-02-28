package jp.awabi2048.cccontent.items.misc

import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.Locale

data class CassetteDefinition(
    val id: String,
    val songName: String,
    val description: List<String>,
    val durationSeconds: Int,
    val soundKey: String,
    val material: Material
)

object RadioCassetteConfig {
    private lateinit var plugin: JavaPlugin
    private lateinit var configFile: File
    private val cassettes = linkedMapOf<String, CassetteDefinition>()

    fun initialize(plugin: JavaPlugin) {
        this.plugin = plugin
        val miscDir = File(plugin.dataFolder, "misc")
        if (!miscDir.exists()) {
            miscDir.mkdirs()
        }

        configFile = File(miscDir, "radio_cassette.yml")
        if (!configFile.exists()) {
            plugin.saveResource("misc/radio_cassette.yml", false)
        }

        reload()
    }

    fun reload() {
        if (!::configFile.isInitialized) {
            return
        }

        cassettes.clear()
        val yaml = YamlConfiguration.loadConfiguration(configFile)
        val root = yaml.getConfigurationSection("cassettes")
        if (root == null) {
            if (::plugin.isInitialized) {
                plugin.logger.warning("[RadioCassette] 設定セクション cassettes が見つかりません")
            }
            return
        }

        for (cassetteId in root.getKeys(false).sorted()) {
            val basePath = "cassettes.$cassetteId"
            val songName = yaml.getString("$basePath.曲名")
                ?: yaml.getString("$basePath.title")
                ?: cassetteId

            val description = when {
                yaml.isList("$basePath.説明") -> yaml.getStringList("$basePath.説明")
                yaml.isString("$basePath.説明") -> listOfNotNull(yaml.getString("$basePath.説明"))
                yaml.isList("$basePath.description") -> yaml.getStringList("$basePath.description")
                yaml.isString("$basePath.description") -> listOfNotNull(yaml.getString("$basePath.description"))
                else -> emptyList()
            }

            val duration = if (yaml.contains("$basePath.再生時間")) {
                yaml.getInt("$basePath.再生時間", 0)
            } else {
                yaml.getInt("$basePath.duration_seconds", 0)
            }

            if (duration <= 0) {
                plugin.logger.warning("[RadioCassette] 再生時間が不正のためスキップ: id=$cassetteId")
                continue
            }

            val soundKey = yaml.getString("$basePath.再生キー")
                ?: yaml.getString("$basePath.sound")
                ?: yaml.getString("$basePath.サウンド")
                ?: ""

            if (soundKey.isBlank()) {
                plugin.logger.warning("[RadioCassette] sound が未設定のためスキップ: id=$cassetteId")
                continue
            }

            val material = resolveDiscMaterial(cassetteId)
            cassettes[cassetteId] = CassetteDefinition(
                id = cassetteId,
                songName = songName,
                description = description,
                durationSeconds = duration,
                soundKey = soundKey,
                material = material
            )
        }

        plugin.logger.info("[RadioCassette] カセット定義を読み込みました: ${cassettes.size}件")
    }

    fun getAll(): List<CassetteDefinition> = cassettes.values.toList()

    fun getById(id: String): CassetteDefinition? = cassettes[id]

    private fun resolveDiscMaterial(cassetteId: String): Material {
        val normalized = cassetteId
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')

        val materialName = "MUSIC_DISC_${normalized.uppercase(Locale.ROOT)}"
        return Material.matchMaterial(materialName) ?: Material.MUSIC_DISC_13
    }
}
