package jp.awabi2048.cccontent.features.fishing

import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

data class FishingSettings(val enabled: Boolean, val fishes: List<FishDefinition>, val clickCount: Int, val timeoutTicks: Long) {
    companion object {
        fun load(plugin: JavaPlugin): FishingSettings {
            val file = File(plugin.dataFolder, "config/fishing/fish.yml")
            if (!file.exists()) {
                file.parentFile.mkdirs()
                plugin.saveResource("config/fishing/fish.yml", false)
            }
            val config = YamlConfiguration.loadConfiguration(file)
            require(config.get("schema_version") is Number && config.getInt("schema_version") == 1) { "config/fishing/fish.yml.schema_version must be the integer 1" }
            require(config.get("enabled") is Boolean) { "config/fishing/fish.yml.enabled must be a boolean" }
            val fishes = config.getConfigurationSection("fish")?.getKeys(false).orEmpty().map { id ->
                val path = "fish.$id"
                val qualities = config.getConfigurationSection("$path.quality")?.getKeys(false).orEmpty().associate {
                    FishQuality.fromId(it) to config.getInt("$path.quality.$it")
                }
                FishDefinition(
                    id = id,
                    material = Material.matchMaterial(requireString(config.getString("$path.material"), "$path.material")) ?: error("$path.material is invalid"),
                    weight = requirePositiveInt(config.get("$path.weight"), "$path.weight"),
                    minLevel = requirePositiveInt(config.get("$path.min_level"), "$path.min_level"),
                    biomes = config.getStringList("$path.biomes").toSet(),
                    weather = config.getStringList("$path.weather").map { FishingWeather.valueOf(it.uppercase()) }.toSet(),
                    times = config.getStringList("$path.times").map { FishingTime.valueOf(it.uppercase()) }.toSet(),
                    bobberY = requireRange(config, "$path.bobber_y"),
                    bobberDistance = requireRange(config, "$path.bobber_distance"),
                    qualities = qualities.also { require(it.isNotEmpty()) { "$path.quality must not be empty" } },
                    exp = requirePositiveLong(config.get("$path.exp"), "$path.exp")
                )
            }
            require(fishes.isNotEmpty()) { "config/fishing/fish.yml に魚定義がありません" }
            return FishingSettings(
                config.get("enabled") as Boolean,
                fishes,
                requirePositiveInt(config.get("minigame.click_count"), "minigame.click_count"),
                requirePositiveLong(config.get("minigame.timeout_ticks"), "minigame.timeout_ticks")
            )
        }

        private fun requireString(value: String?, path: String): String = requireNotNull(value) { "$path is required" }.also { require(it.isNotBlank()) { "$path must not be blank" } }
        private fun requirePositiveInt(value: Any?, path: String): Int = require(value is Number && value.toDouble() == value.toInt().toDouble() && value.toInt() > 0) { "$path must be a positive integer" }.let { value.toInt() }
        private fun requirePositiveLong(value: Any?, path: String): Long = require(value is Number && value.toDouble() == value.toLong().toDouble() && value.toLong() > 0) { "$path must be a positive integer" }.let { value.toLong() }
        private fun requireRange(config: YamlConfiguration, path: String): IntRange {
            val min = requireNonNegativeLong(config.get("$path.min"), "$path.min").toInt()
            val max = requireNonNegativeLong(config.get("$path.max"), "$path.max").toInt()
            require(min <= max) { "$path.min must not exceed $path.max" }
            return min..max
        }
        private fun requireNonNegativeLong(value: Any?, path: String): Long = require(value is Number && value.toDouble() == value.toLong().toDouble() && value.toLong() >= 0) { "$path must be a non-negative integer" }.let { value.toLong() }
    }
}
