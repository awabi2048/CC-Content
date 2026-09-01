package jp.awabi2048.cccontent.features.crops

import org.bukkit.NamespacedKey
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

/**
 * 作物栽培（Crops）の設定。config/crops/crops.yml から読み込む。
 * 作物ごとに成長時間・表示モデル・関連アイテムを外部化する。
 */
data class CropDefinition(
    val id: String,
    val seedItemId: String,
    val harvestItemId: String,
    val maxStage: Int,
    val ticksPerStage: Int,
    val boneMealStages: Int,
    val supportModel: NamespacedKey,
    val stageModels: List<NamespacedKey>
) {
    init {
        require(stageModels.size == maxStage + 1) { "crop $id の stage_models は max_stage+1 件必要です" }
    }
}

data class CropsSettings(
    val crops: List<CropDefinition>,
    val debug: Boolean = false
) {
    fun crop(id: String): CropDefinition? = crops.firstOrNull { it.id == id }

    companion object {
        fun load(plugin: JavaPlugin): CropsSettings {
            val file = File(plugin.dataFolder, "config/crops/crops.yml")
            if (!file.exists()) {
                file.parentFile.mkdirs()
                plugin.saveResource("config/crops/crops.yml", false)
            }
            val config = YamlConfiguration.loadConfiguration(file)
            require(config.getInt("config_version") == 1) {
                "config/crops/crops.yml.config_version は 1 である必要があります"
            }
            val debug = config.getBoolean("debug", false)
            val crops = config.getConfigurationSection("crops")?.getKeys(false).orEmpty().map { id ->
                val path = "crops.$id"
                val stageModels = config.getStringList("$path.stage_models").map {
                    NamespacedKey.fromString(it) ?: error("$path.stage_models の値が不正です: $it")
                }
                CropDefinition(
                    id = id,
                    seedItemId = requireNotNull(config.getString("$path.seed_item")) { "$path.seed_item は必須です" },
                    harvestItemId = requireNotNull(config.getString("$path.harvest_item")) { "$path.harvest_item は必須です" },
                    maxStage = config.getInt("$path.max_stage").coerceAtLeast(1),
                    ticksPerStage = config.getInt("$path.ticks_per_stage").coerceAtLeast(1),
                    boneMealStages = config.getInt("$path.bone_meal_stages").coerceAtLeast(0),
                    supportModel = NamespacedKey.fromString(
                        requireNotNull(config.getString("$path.support_model")) { "$path.support_model は必須です" }
                    ) ?: error("$path.support_model が不正です"),
                    stageModels = stageModels
                )
            }
            require(crops.isNotEmpty()) { "config/crops/crops.yml に作物定義がありません" }
            return CropsSettings(crops, debug)
        }
    }
}
