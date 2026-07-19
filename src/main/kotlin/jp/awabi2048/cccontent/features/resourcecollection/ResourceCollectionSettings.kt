package jp.awabi2048.cccontent.features.resourcecollection

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class ResourceCollectionSettings(
    val enabled: Boolean,
    private val professionEnabled: Map<ResourceCollectionKind, Boolean>,
    val normalBonusEnabled: Map<ResourceCollectionKind, Boolean>,
    private val operations: Map<ResourceOperation, Boolean>
) {
    fun isProfessionEnabled(kind: ResourceCollectionKind): Boolean =
        professionEnabled[kind] == true

    fun isOperationEnabled(operation: ResourceOperation): Boolean =
        isProfessionEnabled(operation.kind) && operations[operation] == true

    companion object {
        private const val CONFIG_PATH = "config/resource_collection/config.yml"
        private const val CONFIG_VERSION = 4

        fun load(plugin: JavaPlugin): ResourceCollectionSettings {
            val file = ensureFile(plugin, CONFIG_PATH)
            migrateIfRequired(plugin, file)
            archiveLegacyCollectionRules(plugin, file.parentFile)
            val config = YamlConfiguration.loadConfiguration(file)
            require(config.get("config_version") is Number && config.getInt("config_version") == CONFIG_VERSION) {
                "$CONFIG_PATH.config_version must be the integer $CONFIG_VERSION"
            }
            return ResourceCollectionSettings(
                requireBoolean(config, "enabled"),
                ResourceCollectionKind.entries.associateWith { kind ->
                    requireBoolean(config, "${kind.configSection}.enabled")
                },
                ResourceCollectionKind.entries.associateWith { kind ->
                    requireBoolean(config, "${kind.configSection}.normal_bonus_drop")
                },
                ResourceOperation.entries.associateWith { operation ->
                    requireBoolean(config, operation.configPath)
                }
            )
        }

        private fun migrateIfRequired(plugin: JavaPlugin, file: File) {
            val existing = YamlConfiguration.loadConfiguration(file)
            val version = existing.getInt("config_version", -1)
            if (version == CONFIG_VERSION) return
            require(version == 2 || version == 3) {
                "Unsupported resource collection config version: $version"
            }
            val migrated = YamlConfiguration.loadConfiguration(file)
            migrated.set("config_version", CONFIG_VERSION)
            migrated.set("worlds", null)
            ResourceCollectionKind.entries.forEach { kind ->
                migrated.set("${kind.configSection}.enabled", true)
                migrated.set(
                    "${kind.configSection}.normal_bonus_drop",
                    existing.getBoolean("normal_bonus.${kind.configSection}", true)
                )
            }
            migrated.set("normal_bonus", null)
            ResourceOperation.entries.forEach { operation ->
                migrated.set(operation.configPath, true)
            }
            val backup = File(file.parentFile, "${file.name}.bak-v$version")
            if (!backup.exists()) Files.copy(file.toPath(), backup.toPath())
            val temporary = File(file.parentFile, "${file.name}.migrating")
            runCatching {
                migrated.save(temporary)
                runCatching {
                    Files.move(
                        temporary.toPath(), file.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING
                    )
                }.getOrElse {
                    Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            }.onFailure {
                temporary.delete()
                throw IllegalStateException("Resource collection config migration failed; original was preserved", it)
            }
            plugin.logger.info(
                "Resource collection config migrated from version $version to $CONFIG_VERSION; backup=${backup.name}"
            )
        }

        private fun archiveLegacyCollectionRules(plugin: JavaPlugin, directory: File) {
            val legacy = File(directory, "collection.yml")
            if (!legacy.exists()) return
            var backup = File(directory, "collection.yml.bak-v2")
            var suffix = 1
            while (backup.exists()) {
                backup = File(directory, "collection.yml.bak-v2-$suffix")
                suffix++
            }
            Files.move(legacy.toPath(), backup.toPath())
            plugin.logger.info("Legacy resource collection EXP and craft rules were archived as ${backup.name}")
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

        private fun requireBoolean(config: YamlConfiguration, key: String): Boolean {
            val value = config.get(key)
            require(value is Boolean) { "$CONFIG_PATH.$key must be a boolean" }
            return value
        }
    }
}

private val ResourceCollectionKind.configSection: String
    get() = name.lowercase()

enum class ResourceOperation(
    val kind: ResourceCollectionKind,
    val configPath: String
) {
    MINER_INSPECTION(ResourceCollectionKind.MINERAL, "mineral.inspection"),
    MINER_CHISEL(ResourceCollectionKind.MINERAL, "mineral.chisel_game"),
    MINER_BATCH(ResourceCollectionKind.MINERAL, "mineral.batch_mining"),
    LUMBERJACK_BATCH(ResourceCollectionKind.FOREST, "forest.batch_felling"),
    LUMBERJACK_HEARTWOOD(ResourceCollectionKind.FOREST, "forest.heartwood"),
    LUMBERJACK_BARK(ResourceCollectionKind.FOREST, "forest.bark"),
    LUMBERJACK_TIMBER_PROCESSING(ResourceCollectionKind.FOREST, "forest.timber_processing"),
    LUMBERJACK_FOREST_PRODUCTS(ResourceCollectionKind.FOREST, "forest.forest_products"),
    LUMBERJACK_LEAF_CLEANUP(ResourceCollectionKind.FOREST, "forest.leaf_cleanup"),
    LUMBERJACK_AUTOMATIC_REPLANT(ResourceCollectionKind.FOREST, "forest.automatic_replant"),
    FARMER_WILD_GATHERING(ResourceCollectionKind.CROP, "crop.wild_gathering"),
    FARMER_SURFACE_GATHERING(ResourceCollectionKind.CROP, "crop.surface_gathering"),
    FARMER_AREA_TILLING(ResourceCollectionKind.CROP, "crop.area_tilling"),
    FARMER_AREA_HARVEST(ResourceCollectionKind.CROP, "crop.area_harvest"),
    FARMER_AUTOMATIC_REPLANT(ResourceCollectionKind.CROP, "crop.automatic_replant")
}
