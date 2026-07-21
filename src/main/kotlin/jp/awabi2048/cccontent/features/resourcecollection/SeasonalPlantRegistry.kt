package jp.awabi2048.cccontent.features.resourcecollection

import com.awabi2048.ccsystem.api.time.Season
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.Locale
import java.util.Random

data class SeasonalPlantDefinition(
    val id: String,
    val customItemId: String,
    val useNameKey: String,
    val vegetationGroupNameKey: String,
    val weightsBySeason: Map<Season, Int>,
    val sourceMaterials: Set<Material>,
    val biomeKeys: Set<String>,
    val minimumY: Int,
    val maximumY: Int,
    val itemModel: String
) {
    fun matches(season: Season, material: Material, biomeKey: String, y: Int): Boolean =
        weight(season) > 0 &&
            material in sourceMaterials &&
            y in minimumY..maximumY &&
            (biomeKeys.isEmpty() || biomeKey in biomeKeys)

    fun weight(season: Season): Int = weightsBySeason[season] ?: 0
}

class SeasonalPlantRegistry private constructor(
    private val enabled: Boolean,
    private val definitions: List<SeasonalPlantDefinition>,
) {
    fun select(
        season: Season,
        material: Material,
        biomeKey: String,
        y: Int,
        random: Random
    ): SeasonalPlantDefinition? {
        if (!enabled) return null
        val candidates = definitions.filter { it.matches(season, material, biomeKey, y) }
        val totalWeight = candidates.sumOf { it.weight(season) }
        if (totalWeight <= 0) return null
        var cursor = random.nextInt(totalWeight)
        for (candidate in candidates) {
            cursor -= candidate.weight(season)
            if (cursor < 0) return candidate
        }
        return null
    }

    fun selectStable(
        season: Season,
        material: Material,
        biomeKey: String,
        y: Int,
        seed: Long
    ): SeasonalPlantDefinition? {
        if (!enabled) return null
        val candidates = definitions.filter { it.matches(season, material, biomeKey, y) }
        val selected = GatheringPatchModel.weightedIndex(candidates.map { it.weight(season) }, seed) ?: return null
        return candidates[selected]
    }

    fun size(): Int = definitions.size

    companion object {
        private const val CONFIG_PATH = "config/resource_collection/seasonal_plants.yml"

        fun load(plugin: JavaPlugin): SeasonalPlantRegistry {
            val file = ensureFile(plugin)
            val config = YamlConfiguration.loadConfiguration(file)
            require(config.get("schema_version") is Number && config.getInt("schema_version") == 2) {
                "$CONFIG_PATH.schema_version must be the integer 2"
            }
            val enabled = requireBoolean(config, "enabled")
            require(!config.contains("surface_recovery_seconds")) { "$CONFIG_PATH.surface_recovery_seconds is forbidden" }
            val rawDefinitions = config.getMapList("definitions")
            val definitions = rawDefinitions.mapIndexed { index, raw -> parseDefinition(raw, index) }
            val duplicateIds = definitions.groupingBy(SeasonalPlantDefinition::id).eachCount()
                .filterValues { it > 1 }.keys
            require(duplicateIds.isEmpty()) { "$CONFIG_PATH contains duplicate ids: ${duplicateIds.sorted()}" }
            plugin.logger.info(
                "Resource Collection: seasonal plant registry enabled=$enabled definitions=${definitions.size}"
            )
            return SeasonalPlantRegistry(enabled, definitions)
        }

        fun of(enabled: Boolean, definitions: List<SeasonalPlantDefinition>): SeasonalPlantRegistry =
            SeasonalPlantRegistry(enabled, definitions)

        private fun parseDefinition(raw: Map<*, *>, index: Int): SeasonalPlantDefinition {
            val path = "$CONFIG_PATH.definitions[$index]"
            val id = requireString(raw, "id", path)
            require(id.matches(Regex("[a-z0-9_]+"))) { "$path.id must use lowercase snake_case" }
            val customItemId = requireString(raw, "custom_item_id", path)
            require(customItemId.matches(Regex("[a-z0-9_.-]+"))) { "$path.custom_item_id is invalid" }
            val useNameKey = requireLanguageKey(raw, "use_name_key", path)
            val vegetationGroupNameKey = requireLanguageKey(raw, "vegetation_group_name_key", path)
            val rawWeights = raw["weights_by_season"] as? Map<*, *>
                ?: throw IllegalArgumentException("$path.weights_by_season must be a map")
            val weights = Season::class.java.enumConstants.associateWith { season ->
                requireInt(rawWeights, season.name, "$path.weights_by_season").also {
                    require(it >= 0) { "$path.weights_by_season.${season.name} must not be negative" }
                }
            }
            require(weights.values.any { it > 0 }) { "$path.weights_by_season must contain a positive weight" }
            val sourceMaterials = requireStringList(raw, "source_materials", path).map { rawMaterial ->
                runCatching { Material.valueOf(rawMaterial.uppercase(Locale.ROOT)) }
                    .getOrElse { throw IllegalArgumentException("$path.source_materials contains invalid material: $rawMaterial") }
            }.toSet()
            require(sourceMaterials.isNotEmpty()) { "$path.source_materials must not be empty" }
            val biomeKeys = optionalStringList(raw, "biome_keys", path).map { it.lowercase(Locale.ROOT) }.toSet()
            biomeKeys.forEach { key ->
                require(key.matches(Regex("[a-z0-9_.-]+:[a-z0-9_./-]+"))) { "$path.biome_keys contains invalid key: $key" }
            }
            val minimumY = requireInt(raw, "minimum_y", path)
            val maximumY = requireInt(raw, "maximum_y", path)
            require(minimumY <= maximumY) { "$path.minimum_y must not exceed maximum_y" }
            val itemModel = requireString(raw, "item_model", path)
            require(itemModel.matches(Regex("[a-z0-9_.-]+:[a-z0-9_./-]+"))) { "$path.item_model is invalid" }
            return SeasonalPlantDefinition(
                id,
                customItemId,
                useNameKey,
                vegetationGroupNameKey,
                weights,
                sourceMaterials,
                biomeKeys,
                minimumY,
                maximumY,
                itemModel
            )
        }

        private fun requireLanguageKey(raw: Map<*, *>, key: String, path: String): String {
            val value = requireString(raw, key, path)
            require(value.matches(Regex("[a-z0-9_.-]+"))) { "$path.$key is invalid" }
            return value
        }

        private fun requireString(raw: Map<*, *>, key: String, path: String): String =
            (raw[key] as? String)?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("$path.$key must be a non-empty string")

        private fun requireStringList(raw: Map<*, *>, key: String, path: String): List<String> {
            val list = raw[key] as? List<*> ?: throw IllegalArgumentException("$path.$key must be a list")
            require(list.all { it is String && it.isNotBlank() }) { "$path.$key must contain strings" }
            return list.filterIsInstance<String>()
        }

        private fun optionalStringList(raw: Map<*, *>, key: String, path: String): List<String> {
            if (key !in raw) return emptyList()
            return requireStringList(raw, key, path)
        }

        private fun requireInt(raw: Map<*, *>, key: String, path: String): Int =
            (raw[key] as? Number)?.toInt()
                ?: throw IllegalArgumentException("$path.$key must be an integer")

        private fun requireBoolean(config: YamlConfiguration, key: String): Boolean {
            val value = config.get(key)
            require(value is Boolean) { "$CONFIG_PATH.$key must be a boolean" }
            return value
        }

        private fun ensureFile(plugin: JavaPlugin): File {
            val file = File(plugin.dataFolder, CONFIG_PATH)
            if (!file.exists()) {
                file.parentFile.mkdirs()
                check(plugin.getResource(CONFIG_PATH) != null) { "Bundled resource is missing: $CONFIG_PATH" }
                plugin.saveResource(CONFIG_PATH, false)
            }
            return file
        }
    }
}
