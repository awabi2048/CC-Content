package jp.awabi2048.cccontent.features.resourcecollection

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

data class ChiselSettings(
    val targetCount: Int,
    val targetTimeoutTicks: Long,
    val particleCount: Int
)

data class ResourceCollectionSettings(
    val enabled: Boolean,
    val chisel: ChiselSettings,
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
        private const val CONFIG_VERSION = 7

        fun load(plugin: JavaPlugin): ResourceCollectionSettings {
            val file = ensureFile(plugin, CONFIG_PATH)
            val config = YamlConfiguration.loadConfiguration(file)
            require(config.get("config_version") is Number && config.getInt("config_version") == CONFIG_VERSION) {
                "$CONFIG_PATH.config_version must be the integer $CONFIG_VERSION"
            }
            return ResourceCollectionSettings(
                requireBoolean(config, "enabled"),
                ChiselSettings(
                    requirePositiveInt(config, "chisel.target_count"),
                    requirePositiveLong(config, "chisel.target_timeout_ticks"),
                    requirePositiveInt(config, "chisel.particle_count")
                ),
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

        private fun requirePositiveInt(config: YamlConfiguration, key: String): Int {
            val value = config.get(key)
            require(value is Number && value.toDouble() == value.toInt().toDouble() && value.toInt() > 0) {
                "$CONFIG_PATH.$key must be a positive integer"
            }
            return value.toInt()
        }

        private fun requirePositiveLong(config: YamlConfiguration, key: String): Long {
            val value = config.get(key)
            require(value is Number && value.toDouble() == value.toLong().toDouble() && value.toLong() > 0L) {
                "$CONFIG_PATH.$key must be a positive integer"
            }
            return value.toLong()
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
    MINER_WORK_SPEED(ResourceCollectionKind.MINERAL, "mineral.work_speed"),
    MINER_CHISEL(ResourceCollectionKind.MINERAL, "mineral.chisel_game"),
    LUMBERJACK_WORK_SPEED(ResourceCollectionKind.FOREST, "forest.work_speed"),
    LUMBERJACK_HEARTWOOD(ResourceCollectionKind.FOREST, "forest.heartwood"),
    LUMBERJACK_BARK(ResourceCollectionKind.FOREST, "forest.bark"),
    LUMBERJACK_TIMBER_PROCESSING(ResourceCollectionKind.FOREST, "forest.timber_processing"),
    LUMBERJACK_FOREST_PRODUCTS(ResourceCollectionKind.FOREST, "forest.forest_products"),
    LUMBERJACK_LEAF_CLEANUP(ResourceCollectionKind.FOREST, "forest.leaf_cleanup"),
    LUMBERJACK_AUTOMATIC_REPLANT(ResourceCollectionKind.FOREST, "forest.automatic_replant"),
    FARMER_WILD_GATHERING(ResourceCollectionKind.CROP, "crop.wild_gathering"),
    FARMER_WORK_SPEED(ResourceCollectionKind.CROP, "crop.work_speed"),
    FARMER_SURFACE_GATHERING(ResourceCollectionKind.CROP, "crop.surface_gathering"),
    FARMER_AREA_TILLING(ResourceCollectionKind.CROP, "crop.area_tilling"),
    FARMER_AREA_HARVEST(ResourceCollectionKind.CROP, "crop.area_harvest"),
    FARMER_AUTOMATIC_REPLANT(ResourceCollectionKind.CROP, "crop.automatic_replant")
}
