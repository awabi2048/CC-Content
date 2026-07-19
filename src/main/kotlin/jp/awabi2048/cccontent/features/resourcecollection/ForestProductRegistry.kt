package jp.awabi2048.cccontent.features.resourcecollection

import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.Locale
import java.util.Random

data class ForestProductDefinition(
    val id: String,
    val customItemId: String,
    val displayNameKey: String,
    val treeMaterials: Set<Material>,
    val targetMaterials: Set<Material>,
    val biomeKeys: Set<String>,
    val discoveryChance: Double,
    val weight: Int
) {
    fun matches(tree: Material, target: Material, biomeKey: String): Boolean =
        tree in treeMaterials && target in targetMaterials &&
            (biomeKeys.isEmpty() || biomeKey in biomeKeys)
}

class ForestProductRegistry private constructor(
    private val enabled: Boolean,
    private val definitions: List<ForestProductDefinition>
) {
    fun select(
        tree: Material,
        target: Material,
        biomeKey: String,
        chanceBonus: Double,
        random: Random
    ): ForestProductDefinition? {
        if (!enabled) return null
        val candidates = definitions.filter { it.matches(tree, target, biomeKey) }
        val totalWeight = candidates.sumOf(ForestProductDefinition::weight)
        if (totalWeight <= 0) return null
        var cursor = random.nextInt(totalWeight)
        val selected = candidates.firstOrNull { candidate ->
            cursor -= candidate.weight
            cursor < 0
        } ?: return null
        return selected.takeIf {
            random.nextDouble() < (selected.discoveryChance + chanceBonus).coerceIn(0.0, 1.0)
        }
    }

    companion object {
        private const val CONFIG_PATH = "config/resource_collection/forest_products.yml"

        fun load(plugin: JavaPlugin): ForestProductRegistry {
            val file = ensureFile(plugin)
            val config = YamlConfiguration.loadConfiguration(file)
            require(config.get("schema_version") is Number && config.getInt("schema_version") == 1) {
                "$CONFIG_PATH.schema_version must be the integer 1"
            }
            val enabled = config.get("enabled") as? Boolean
                ?: throw IllegalArgumentException("$CONFIG_PATH.enabled must be a boolean")
            val definitions = config.getMapList("definitions").mapIndexed(::parseDefinition)
            val duplicates = definitions.groupingBy(ForestProductDefinition::id).eachCount()
                .filterValues { it > 1 }.keys
            require(duplicates.isEmpty()) { "$CONFIG_PATH contains duplicate ids: ${duplicates.sorted()}" }
            plugin.logger.info(
                "Resource Collection: forest product registry enabled=$enabled definitions=${definitions.size}"
            )
            return ForestProductRegistry(enabled, definitions)
        }

        fun of(enabled: Boolean, definitions: List<ForestProductDefinition>): ForestProductRegistry =
            ForestProductRegistry(enabled, definitions)

        private fun parseDefinition(index: Int, raw: Map<*, *>): ForestProductDefinition {
            val path = "$CONFIG_PATH.definitions[$index]"
            val id = string(raw, "id", path)
            require(id.matches(Regex("[a-z0-9_]+"))) { "$path.id must use lowercase snake_case" }
            val customItemId = string(raw, "custom_item_id", path)
            val displayNameKey = string(raw, "display_name_key", path)
            val treeMaterials = materials(raw, "tree_materials", path)
            val targetMaterials = materials(raw, "target_materials", path)
            val biomeKeys = stringList(raw, "biome_keys", path, optional = true)
                .map { it.lowercase(Locale.ROOT) }.toSet()
            biomeKeys.forEach { key ->
                require(key.matches(Regex("[a-z0-9_.-]+:[a-z0-9_./-]+"))) { "$path.biome_keys contains invalid key: $key" }
            }
            val chance = (raw["discovery_chance"] as? Number)?.toDouble()
                ?: throw IllegalArgumentException("$path.discovery_chance must be a number")
            require(chance in 0.0..1.0) { "$path.discovery_chance must be between 0 and 1" }
            val weight = (raw["weight"] as? Number)?.toInt()
                ?: throw IllegalArgumentException("$path.weight must be an integer")
            require(weight > 0) { "$path.weight must be positive" }
            return ForestProductDefinition(
                id, customItemId, displayNameKey, treeMaterials, targetMaterials, biomeKeys, chance, weight
            )
        }

        private fun materials(raw: Map<*, *>, key: String, path: String): Set<Material> =
            stringList(raw, key, path).map { value ->
                runCatching { Material.valueOf(value.uppercase(Locale.ROOT)) }
                    .getOrElse { throw IllegalArgumentException("$path.$key contains invalid material: $value") }
            }.toSet().also { require(it.isNotEmpty()) { "$path.$key must not be empty" } }

        private fun string(raw: Map<*, *>, key: String, path: String): String =
            (raw[key] as? String)?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("$path.$key must be a non-empty string")

        private fun stringList(raw: Map<*, *>, key: String, path: String, optional: Boolean = false): List<String> {
            if (optional && key !in raw) return emptyList()
            val list = raw[key] as? List<*> ?: throw IllegalArgumentException("$path.$key must be a list")
            require(list.all { it is String && it.isNotBlank() }) { "$path.$key must contain strings" }
            return list.filterIsInstance<String>()
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
