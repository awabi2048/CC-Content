package jp.awabi2048.cccontent.features.resourcecollection

import jp.awabi2048.cccontent.features.rank.profession.Profession
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

data class ResourceCollectionSettings(
    val enabled: Boolean,
    val worlds: Set<String>,
    val harvestRules: Map<Material, CollectionRule>,
    val craftRules: Map<Material, CollectionRule>
) {
    companion object {
        private const val CONFIG_PATH = "config/resource_collection/config.yml"
        private const val COLLECTION_PATH = "config/resource_collection/collection.yml"

        fun load(plugin: JavaPlugin): ResourceCollectionSettings {
            val configFile = ensureFile(plugin, CONFIG_PATH)
            val collectionFile = ensureFile(plugin, COLLECTION_PATH)
            val config = YamlConfiguration.loadConfiguration(configFile)
            val collection = YamlConfiguration.loadConfiguration(collectionFile)

            require(config.get("config_version") is Number && config.getInt("config_version") == 2) {
                "$CONFIG_PATH.config_version must be the integer 2"
            }
            val enabled = requireBoolean(config, "enabled", CONFIG_PATH)
            val worlds = requireStringList(config, "worlds", CONFIG_PATH).toSet()
            require(worlds.isNotEmpty()) { "$CONFIG_PATH.worlds must not be empty" }

            require(collection.get("config_version") is Number && collection.getInt("config_version") == 2) {
                "$COLLECTION_PATH.config_version must be the integer 2"
            }
            return ResourceCollectionSettings(
                enabled = enabled,
                worlds = worlds,
                harvestRules = loadRules(collection, "harvest", COLLECTION_PATH),
                craftRules = loadRules(collection, "craft", COLLECTION_PATH)
            ).also {
                require(it.harvestRules.isNotEmpty()) { "$COLLECTION_PATH.harvest must not be empty" }
                require(it.craftRules.isNotEmpty()) { "$COLLECTION_PATH.craft must not be empty" }
            }
        }

        private fun loadRules(config: YamlConfiguration, section: String, path: String): Map<Material, CollectionRule> {
            val root = config.getConfigurationSection(section)
                ?: error("$path.$section is required and must be a section")
            require(root.getKeys(false).isNotEmpty()) { "$path.$section must not be empty" }
            return root.getKeys(false).associate { id ->
                val entry = root.getConfigurationSection(id)
                    ?: error("$path.$section.$id must be a section")
                val materialName = requireString(entry, "material", "$path.$section.$id")
                val material = Material.matchMaterial(materialName.uppercase())
                    ?: error("$path.$section.$id.material is not a valid Bukkit material: $materialName")
                val professionId = requireString(entry, "profession", "$path.$section.$id")
                val profession = Profession.fromId(professionId)
                    ?: error("$path.$section.$id.profession is not a valid profession: $professionId")
                val experienceValue = entry.get("experience")
                require(experienceValue is Number && experienceValue.toLong() > 0 && experienceValue.toDouble() == experienceValue.toLong().toDouble()) {
                    "$path.$section.$id.experience must be a positive integer"
                }
                val minimumAge = entry.get("minimum_age")?.let { value ->
                    require(value is Number && value.toInt() in 0..7 && value.toDouble() == value.toInt().toDouble()) {
                        "$path.$section.$id.minimum_age must be an integer from 0 to 7"
                    }
                    value.toInt()
                }
                material to CollectionRule(id, material, profession, experienceValue.toLong(), minimumAge)
            }
        }

        private fun ensureFile(plugin: JavaPlugin, path: String): File {
            val file = File(plugin.dataFolder, path)
            if (!file.exists()) {
                file.parentFile.mkdirs()
                check(plugin.getResource(path) != null) { "Bundled resource is missing: $path" }
                plugin.saveResource(path, false)
            }
            return file
        }

        private fun requireBoolean(config: YamlConfiguration, key: String, path: String): Boolean {
            val value = config.get(key)
            require(value is Boolean) { "$path.$key must be a boolean" }
            return value
        }

        private fun requireString(config: org.bukkit.configuration.ConfigurationSection, key: String, path: String): String {
            val value = config.get(key)
            require(value is String && value.isNotBlank()) { "$path.$key must be a non-blank string" }
            return value
        }

        private fun requireStringList(config: YamlConfiguration, key: String, path: String): List<String> {
            val value = config.get(key)
            require(value is List<*>) { "$path.$key must be a list of strings" }
            require(value.all { it is String && it.isNotBlank() }) { "$path.$key must be a list of non-blank strings" }
            return value.filterIsInstance<String>()
        }
    }
}
