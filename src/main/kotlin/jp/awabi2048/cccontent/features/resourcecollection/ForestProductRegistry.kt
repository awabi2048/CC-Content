package jp.awabi2048.cccontent.features.resourcecollection

import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import kotlin.math.abs

enum class TreeSpecies(
    val log: Material,
    val strippedLog: Material,
    val leaves: Material
) {
    OAK(Material.OAK_LOG, Material.STRIPPED_OAK_LOG, Material.OAK_LEAVES),
    SPRUCE(Material.SPRUCE_LOG, Material.STRIPPED_SPRUCE_LOG, Material.SPRUCE_LEAVES),
    BIRCH(Material.BIRCH_LOG, Material.STRIPPED_BIRCH_LOG, Material.BIRCH_LEAVES),
    JUNGLE(Material.JUNGLE_LOG, Material.STRIPPED_JUNGLE_LOG, Material.JUNGLE_LEAVES),
    ACACIA(Material.ACACIA_LOG, Material.STRIPPED_ACACIA_LOG, Material.ACACIA_LEAVES),
    DARK_OAK(Material.DARK_OAK_LOG, Material.STRIPPED_DARK_OAK_LOG, Material.DARK_OAK_LEAVES),
    MANGROVE(Material.MANGROVE_LOG, Material.STRIPPED_MANGROVE_LOG, Material.MANGROVE_LEAVES),
    CHERRY(Material.CHERRY_LOG, Material.STRIPPED_CHERRY_LOG, Material.CHERRY_LEAVES),
    PALE_OAK(Material.PALE_OAK_LOG, Material.STRIPPED_PALE_OAK_LOG, Material.PALE_OAK_LEAVES);

    fun isTrunk(material: Material): Boolean = material == log || material == strippedLog

    companion object {
        fun fromTrunk(material: Material): TreeSpecies? = entries.firstOrNull { it.isTrunk(material) }
    }
}

enum class ForestProductTargetKind {
    LOG,
    LEAF
}

enum class ForestProductType(
    val itemId: String,
    val targetKind: ForestProductTargetKind
) {
    PINE_CONE("resource.pine_cone", ForestProductTargetKind.LEAF),
    TREE_RESIN("resource.tree_resin", ForestProductTargetKind.LOG),
    BIRCH_OUTER_BARK("resource.birch_outer_bark", ForestProductTargetKind.LOG),
    TANNIN_BARK("resource.tannin_bark", ForestProductTargetKind.LOG),
    TINDER_FUNGUS("resource.tinder_fungus", ForestProductTargetKind.LOG),
    ACACIA_GUM("resource.acacia_gum", ForestProductTargetKind.LOG),
    AROMATIC_WOOD_CHIP("resource.aromatic_wood_chip", ForestProductTargetKind.LOG),
    BURL_WOOD("resource.burl_wood", ForestProductTargetKind.LOG);

    val displayNameKey: String
        get() = "custom_items.resource.${itemId.substringAfter('.')}.name"
}

data class ForestProductSettings(
    val enabled: Boolean,
    val baseDiscoveryChance: Double,
    val maximumDiscoveryBonus: Double,
    val burlOverrideChance: Double
)

data class ForestProductResolution(
    val type: ForestProductType,
    val fixedValue: Double
)

class ForestProductRegistry private constructor(
    val settings: ForestProductSettings
) {
    fun resolve(
        species: TreeSpecies,
        biomeKey: String,
        worldSeed: Long,
        rootX: Int,
        rootY: Int,
        rootZ: Int,
        discoveryBonus: Double
    ): ForestProductResolution? {
        if (!settings.enabled) return null
        val base = resolveBaseProduct(species, biomeKey) ?: return null
        val fixedValue = stableValue(worldSeed, rootX, rootY, rootZ, species, 0x4650524f44554354L)
        val threshold = settings.baseDiscoveryChance +
            discoveryBonus.coerceIn(0.0, settings.maximumDiscoveryBonus)
        if (fixedValue >= threshold) return null
        val product = if (
            isBurlEnvironment(species, biomeKey) &&
            stableValue(worldSeed, rootX, rootY, rootZ, species, 0x4255524c574f4f44L) <
            settings.burlOverrideChance
        ) {
            ForestProductType.BURL_WOOD
        } else {
            base
        }
        return ForestProductResolution(product, fixedValue)
    }

    companion object {
        private const val CONFIG_PATH = "config/resource_collection/forest_products.yml"

        fun load(plugin: JavaPlugin): ForestProductRegistry {
            val file = ensureFile(plugin)
            val config = YamlConfiguration.loadConfiguration(file)
            require(config.get("schema_version") is Number && config.getInt("schema_version") == 2) {
                "$CONFIG_PATH.schema_version must be the integer 2"
            }
            val settings = ForestProductSettings(
                enabled = config.boolean("enabled"),
                baseDiscoveryChance = config.chance("base_discovery_chance"),
                maximumDiscoveryBonus = config.chance("maximum_discovery_bonus"),
                burlOverrideChance = config.chance("burl_override_chance")
            )
            plugin.logger.info(
                "Resource Collection: forest products enabled=${settings.enabled} " +
                    "base=${settings.baseDiscoveryChance} bonus=${settings.maximumDiscoveryBonus} " +
                    "burl=${settings.burlOverrideChance}"
            )
            return ForestProductRegistry(settings)
        }

        fun of(settings: ForestProductSettings): ForestProductRegistry = ForestProductRegistry(settings)

        fun resolveBaseProduct(species: TreeSpecies, biomeKey: String): ForestProductType? {
            val biome = biomeKey.substringAfter(':').lowercase()
            return when (species) {
                TreeSpecies.SPRUCE -> when (biome) {
                    "old_growth_pine_taiga", "old_growth_spruce_taiga" -> ForestProductType.TREE_RESIN
                    else -> ForestProductType.PINE_CONE
                }
                TreeSpecies.BIRCH -> when (biome) {
                    "old_growth_birch_forest" -> ForestProductType.TINDER_FUNGUS
                    else -> ForestProductType.BIRCH_OUTER_BARK
                }
                TreeSpecies.OAK -> when (biome) {
                    "swamp" -> ForestProductType.TINDER_FUNGUS
                    else -> ForestProductType.TANNIN_BARK
                }
                TreeSpecies.DARK_OAK -> ForestProductType.TANNIN_BARK
                TreeSpecies.PALE_OAK -> when (biome) {
                    "pale_garden" -> ForestProductType.TINDER_FUNGUS
                    else -> null
                }
                TreeSpecies.JUNGLE -> ForestProductType.AROMATIC_WOOD_CHIP
                TreeSpecies.ACACIA -> ForestProductType.ACACIA_GUM
                TreeSpecies.MANGROVE -> when (biome) {
                    "mangrove_swamp" -> ForestProductType.TANNIN_BARK
                    else -> null
                }
                TreeSpecies.CHERRY -> when (biome) {
                    "cherry_grove" -> ForestProductType.AROMATIC_WOOD_CHIP
                    else -> null
                }
            }
        }

        fun isBurlEnvironment(species: TreeSpecies, biomeKey: String): Boolean {
            val biome = biomeKey.substringAfter(':').lowercase()
            return when (biome) {
                "old_growth_pine_taiga", "old_growth_spruce_taiga" -> species == TreeSpecies.SPRUCE
                "old_growth_birch_forest" -> species == TreeSpecies.BIRCH
                "dark_forest" -> species == TreeSpecies.DARK_OAK || species == TreeSpecies.OAK
                "jungle", "sparse_jungle", "bamboo_jungle" -> species == TreeSpecies.JUNGLE
                "eroded_savanna" -> species == TreeSpecies.ACACIA
                "cherry_grove" -> species == TreeSpecies.CHERRY
                else -> false
            }
        }

        private fun stableValue(
            worldSeed: Long,
            x: Int,
            y: Int,
            z: Int,
            species: TreeSpecies,
            salt: Long
        ): Double {
            var value = worldSeed xor salt
            value = mix(value xor x.toLong() * -7046029254386353131L)
            value = mix(value xor y.toLong() * -4417276706812531889L)
            value = mix(value xor z.toLong() * 1609587929392839161L)
            value = mix(value xor species.ordinal.toLong() * -7723592293110705685L)
            return (abs(value ushr 11).toDouble() / (1L shl 53).toDouble()).coerceIn(0.0, 1.0)
        }

        private fun mix(input: Long): Long {
            var value = input
            value = (value xor (value ushr 30)) * -4658895280553007687L
            value = (value xor (value ushr 27)) * -7723592293110705685L
            return value xor (value ushr 31)
        }

        private fun YamlConfiguration.boolean(path: String): Boolean =
            get(path) as? Boolean
                ?: throw IllegalArgumentException("$CONFIG_PATH.$path must be a boolean")

        private fun YamlConfiguration.chance(path: String): Double =
            ((get(path) as? Number)?.toDouble()
                ?: throw IllegalArgumentException("$CONFIG_PATH.$path must be a number"))
                .also { require(it in 0.0..1.0) { "$CONFIG_PATH.$path must be between 0 and 1" } }

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
