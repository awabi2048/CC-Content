package jp.awabi2048.cccontent.features.fishing

import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class FishingMiniGameSettings(
    val baseHookWindowTicks: Long,
    val baseFightDurationTicks: Long,
    val fightIntervalTicks: Long,
    val greenWidth: Double,
    val yellowMargin: Double,
    val inputStep: Double,
    val initialEffectiveness: Double,
    val statusMessageTicks: Long,
    val lureReductionPerLevel: Double,
    val resistanceSmoothing: Double,
    val lateralSmoothing: Double,
    val visualForwardRange: Double,
    val visualLateralRange: Double,
    val facingBonusMultiplier: Double,
    val waitTimeMinTicks: Int,
    val waitTimeMaxTicks: Int
)

data class FishingSettings(
    val enabled: Boolean,
    val fishes: List<FishDefinition>,
    val baits: List<BaitDefinition>,
    val rods: List<RodDefinition>,
    val minigame: FishingMiniGameSettings
) {
    companion object {
        fun load(plugin: JavaPlugin): FishingSettings {
            val file = ensureResource(plugin, "config/fishing/fish.yml")
            migrateIfRequired(plugin, file)
            val config = YamlConfiguration.loadConfiguration(file)
            require(config.get("config_version") is Number && config.getInt("config_version") == 3) {
                "config/fishing/fish.yml.config_version must be the integer 3"
            }
            require(config.get("enabled") is Boolean) { "config/fishing/fish.yml.enabled must be a boolean" }
            val fishes = config.getConfigurationSection("fish")?.getKeys(false).orEmpty().map { id ->
                val path = "fish.$id"
                val qualities = config.getConfigurationSection("$path.quality")?.getKeys(false).orEmpty().associate {
                    FishQuality.fromConfigId(it) to positiveInt(config.get("$path.quality.$it"), "$path.quality.$it")
                }
                val fight = FishFightProfile(
                    targetCenter = finiteRange(config.get("$path.fight.target_center"), 0.0, 100.0, "$path.fight.target_center"),
                    driftPerStep = finiteRange(config.get("$path.fight.drift_per_step"), 0.0, 100.0, "$path.fight.drift_per_step"),
                    directionPersistence = finiteRange(config.get("$path.fight.direction_persistence"), 0.0, 1.0, "$path.fight.direction_persistence"),
                    durationMultiplier = positiveDouble(config.get("$path.fight.duration_multiplier"), "$path.fight.duration_multiplier")
                )
                FishDefinition(
                    id = id,
                    material = material(config.getString("$path.material"), "$path.material"),
                    weight = positiveInt(config.get("$path.weight"), "$path.weight"),
                    minLevel = positiveInt(config.get("$path.min_level"), "$path.min_level"),
                    biomes = config.getStringList("$path.biomes").toSet(),
                    weather = config.getStringList("$path.weather").map { FishingWeather.valueOf(it.uppercase()) }.toSet(),
                    times = config.getStringList("$path.times").map { FishingTime.valueOf(it.uppercase()) }.toSet(),
                    water = FishingWaterCondition(
                        type = FishingWaterType.fromConfigId(requireString(config.getString("$path.water.type"), "$path.water.type")),
                        depth = positiveRange(config, "$path.water.depth"),
                        width = positiveRange(config, "$path.water.width")
                    ),
                    sizeCm = positiveRange(config, "$path.size_cm"),
                    weightGrams = positiveRange(config, "$path.weight_grams"),
                    qualities = qualities.also { require(it.isNotEmpty()) { "$path.quality must not be empty" } },
                    rarity = FishRarity.valueOf(requireString(config.getString("$path.rarity"), "$path.rarity").uppercase()),
                    requiredBaitTags = config.getStringList("$path.required_bait_tags").toSet(),
                    fight = fight
                )
            }
            require(fishes.isNotEmpty()) { "config/fishing/fish.yml に魚定義がありません" }
            val baits = config.getConfigurationSection("bait")?.getKeys(false).orEmpty().map { id ->
                val path = "bait.$id"
                BaitDefinition(
                    id,
                    material(config.getString("$path.material"), "$path.material"),
                    positiveDouble(config.get("$path.wait_time_multiplier"), "$path.wait_time_multiplier"),
                    positiveDouble(config.get("$path.rare_catch_multiplier"), "$path.rare_catch_multiplier"),
                    positiveDouble(config.get("$path.quality_multiplier"), "$path.quality_multiplier"),
                    config.getStringList("$path.special_tags").toSet()
                )
            }
            require(baits.isNotEmpty()) { "config/fishing/fish.yml.bait must not be empty" }
            val rods = config.getConfigurationSection("rod")?.getKeys(false).orEmpty().map { id ->
                val path = "rod.$id"
                RodDefinition(
                    id,
                    positiveDouble(config.get("$path.power_multiplier"), "$path.power_multiplier"),
                    positiveDouble(config.get("$path.finesse_multiplier"), "$path.finesse_multiplier"),
                    positiveInt(config.get("$path.max_durability"), "$path.max_durability")
                )
            }
            require(rods.isNotEmpty()) { "config/fishing/fish.yml.rod must not be empty" }
            val minigame = FishingMiniGameSettings(
                positiveLong(config.get("minigame.hook_window_ticks"), "minigame.hook_window_ticks"),
                positiveLong(config.get("minigame.fight_duration_ticks"), "minigame.fight_duration_ticks"),
                positiveLong(config.get("minigame.fight_interval_ticks"), "minigame.fight_interval_ticks"),
                finiteRange(config.get("minigame.green_width"), 1.0, 100.0, "minigame.green_width"),
                finiteRange(config.get("minigame.yellow_margin"), 0.0, 100.0, "minigame.yellow_margin"),
                positiveDouble(config.get("minigame.input_step"), "minigame.input_step"),
                finiteRange(config.get("minigame.initial_effectiveness"), 0.0, 100.0, "minigame.initial_effectiveness"),
                positiveLong(config.get("minigame.status_message_ticks"), "minigame.status_message_ticks"),
                finiteRange(config.get("minigame.lure_reduction_per_level"), 0.0, 0.9, "minigame.lure_reduction_per_level"),
                finiteRange(config.get("minigame.resistance_smoothing"), 0.01, 1.0, "minigame.resistance_smoothing"),
                finiteRange(config.get("minigame.lateral_smoothing"), 0.01, 1.0, "minigame.lateral_smoothing"),
                positiveDouble(config.get("minigame.visual_forward_range"), "minigame.visual_forward_range"),
                positiveDouble(config.get("minigame.visual_lateral_range"), "minigame.visual_lateral_range"),
                positiveDouble(config.get("minigame.facing_bonus_multiplier"), "minigame.facing_bonus_multiplier"),
                positiveInt(config.get("wait_time.min_ticks"), "wait_time.min_ticks"),
                positiveInt(config.get("wait_time.max_ticks"), "wait_time.max_ticks")
            )
            require(minigame.waitTimeMinTicks <= minigame.waitTimeMaxTicks) {
                "wait_time.min_ticks must not exceed wait_time.max_ticks"
            }
            return FishingSettings(config.getBoolean("enabled"), fishes, baits, rods, minigame)
        }

        private fun ensureResource(plugin: JavaPlugin, path: String): File {
            val file = File(plugin.dataFolder, path)
            if (!file.exists()) {
                file.parentFile.mkdirs()
                plugin.saveResource(path, false)
            }
            return file
        }

        private fun migrateIfRequired(plugin: JavaPlugin, file: File) {
            val config = YamlConfiguration.loadConfiguration(file)
            val version = config.getInt("config_version", -1)
            if (version == 3) return
            require(version == 2) { "Unsupported fishing config version: $version" }
            val defaults = plugin.getResource("config/fishing/fish.yml")?.bufferedReader()?.use {
                YamlConfiguration.loadConfiguration(it)
            } ?: error("Bundled fishing config is missing")
            val migrated = YamlConfiguration.loadConfiguration(file)
            FishingConfigMigration.migrateVersion2(migrated, defaults)

            val backup = File(file.parentFile, "${file.name}.bak-v2")
            if (!backup.exists()) Files.copy(file.toPath(), backup.toPath())
            val temporary = File(file.parentFile, "${file.name}.migrating")
            runCatching {
                migrated.save(temporary)
                runCatching {
                    Files.move(
                        temporary.toPath(),
                        file.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                    )
                }.getOrElse {
                    Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            }.onFailure {
                temporary.delete()
                throw IllegalStateException("Fishing config migration failed; original file was preserved", it)
            }
            plugin.logger.info("Fishing config migrated from version 2 to 3; backup=${backup.name}")
        }

        private fun requireString(value: String?, path: String): String =
            requireNotNull(value) { "$path is required" }.also { require(it.isNotBlank()) { "$path must not be blank" } }

        private fun material(value: String?, path: String): Material =
            Material.matchMaterial(requireString(value, path)) ?: error("$path is invalid")

        private fun positiveInt(value: Any?, path: String): Int =
            require(value is Number && value.toDouble() == value.toInt().toDouble() && value.toInt() > 0) {
                "$path must be a positive integer"
            }.let { value.toInt() }

        private fun positiveLong(value: Any?, path: String): Long =
            require(value is Number && value.toDouble() == value.toLong().toDouble() && value.toLong() > 0) {
                "$path must be a positive integer"
            }.let { value.toLong() }

        private fun positiveDouble(value: Any?, path: String): Double =
            require(value is Number && value.toDouble().isFinite() && value.toDouble() > 0.0) {
                "$path must be a positive finite number"
            }.let { value.toDouble() }

        private fun finiteRange(value: Any?, min: Double, max: Double, path: String): Double =
            require(value is Number && value.toDouble().isFinite() && value.toDouble() in min..max) {
                "$path must be between $min and $max"
            }.let { value.toDouble() }

        private fun positiveRange(config: YamlConfiguration, path: String): IntRange {
            val min = positiveInt(config.get("$path.min"), "$path.min")
            val max = positiveInt(config.get("$path.max"), "$path.max")
            require(min <= max) { "$path.min must not exceed $path.max" }
            return min..max
        }
    }
}

object FishingConfigMigration {
    fun migrateVersion2(target: YamlConfiguration, defaults: YamlConfiguration) {
        require(target.getInt("config_version", -1) == 2) { "Fishing config must be version 2" }
        target.getConfigurationSection("fish")?.getKeys(false).orEmpty().forEach { fishId ->
            val path = "fish.$fishId"
            copyRangeIfMissing(target, defaults, "$path.size_cm", 1..100)
            copyRangeIfMissing(target, defaults, "$path.weight_grams", 1..1000)
        }
        target.set("config_version", 3)
    }

    private fun copyRangeIfMissing(
        target: YamlConfiguration,
        defaults: YamlConfiguration,
        path: String,
        fallback: IntRange
    ) {
        if (!target.contains("$path.min")) {
            target.set("$path.min", defaults.get("$path.min") ?: fallback.first)
        }
        if (!target.contains("$path.max")) {
            target.set("$path.max", defaults.get("$path.max") ?: fallback.last)
        }
    }
}
