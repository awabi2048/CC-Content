package jp.awabi2048.cccontent.features.environment

import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.Locale

/**
 * 収集要素が共有する気候・地形・水域・高度の分類です。
 *
 * 「温帯」「海系統」のような条件を各機能が個別に文字列判定すると、同じ場所が
 * 機能ごとに別の環境として扱われます。そのため、判定の入口をこの resolver の
 * `position -> conditions` に固定し、収集・釣り・将来の採取要素が同じ結果を参照します。
 */
enum class ClimateRegion {
    TEMPERATE,
    COLD,
    WARM,
    DRY,
    TROPICAL,
    WET,
    MOUNTAIN,
    NETHER,
    UNKNOWN
}

enum class TerrainRegion {
    FIELD,
    FOREST,
    JUNGLE,
    TAIGA,
    WETLAND,
    COAST,
    OCEAN,
    MOUNTAIN,
    BADLANDS,
    NETHER,
    UNKNOWN
}

enum class WaterRegion {
    LAND,
    RIVER,
    OCEAN,
    NONE,
    UNKNOWN
}

data class EnvironmentPosition(
    val worldKey: String,
    val x: Int,
    val y: Int,
    val z: Int
)

data class EnvironmentConditions(
    val position: EnvironmentPosition,
    val biomeKey: String,
    val worldEnvironment: World.Environment,
    val climate: ClimateRegion,
    val terrain: TerrainRegion,
    val water: WaterRegion,
    val verticalRegions: Set<String>
) {
    fun hasVerticalRegion(id: String): Boolean = id.lowercase(Locale.ROOT) in verticalRegions
}

data class EnvironmentBiomeProfile(
    val climate: ClimateRegion,
    val terrain: TerrainRegion,
    val water: WaterRegion
)

data class EnvironmentVerticalRegion(
    val minimumY: Int,
    val maximumY: Int
) {
    init {
        require(minimumY <= maximumY) { "Environment vertical region minimumY must not exceed maximumY" }
    }

    fun contains(y: Int): Boolean = y in minimumY..maximumY
}

/**
 * 環境分類を一元的に解決するレジストリです。
 *
 * biome group は選択条件の集合、biome profile は表示・判定用の直交した属性です。
 * そのため「温帯であり森林」のような条件を、排他的な一つの階層へ押し込めません。
 */
class CollectionEnvironmentResolver private constructor(
    private val profiles: Map<String, EnvironmentBiomeProfile>,
    private val groups: Map<String, Set<String>>,
    private val verticalRegions: Map<String, EnvironmentVerticalRegion>
) {
    fun at(block: Block): EnvironmentConditions = at(block.world, block.x, block.y, block.z)

    fun at(world: World, x: Int, y: Int, z: Int): EnvironmentConditions = conditions(
        world.getBiome(x, y, z).key.toString(),
        world.environment,
        EnvironmentPosition(world.key.toString(), x, y, z)
    )

    /** テストや設定読込時にも、同じ位置条件関数を副作用なしで利用します。 */
    fun conditions(
        biomeKey: String,
        worldEnvironment: World.Environment,
        position: EnvironmentPosition = EnvironmentPosition("unknown", 0, 0, 0)
    ): EnvironmentConditions {
        val normalizedBiome = normalizeBiomeKey(biomeKey)
        if (worldEnvironment == World.Environment.NETHER) {
            return EnvironmentConditions(
                position,
                normalizedBiome,
                worldEnvironment,
                ClimateRegion.NETHER,
                TerrainRegion.NETHER,
                WaterRegion.NONE,
                verticalRegionsFor(position.y)
            )
        }
        val profile = profiles[normalizedBiome] ?: fallbackProfile(normalizedBiome)
        return EnvironmentConditions(
            position,
            normalizedBiome,
            worldEnvironment,
            profile.climate,
            profile.terrain,
            profile.water,
            verticalRegionsFor(position.y)
        )
    }

    fun conditions(
        biomeKey: String,
        worldEnvironment: World.Environment,
        y: Int
    ): EnvironmentConditions = conditions(
        biomeKey,
        worldEnvironment,
        EnvironmentPosition("unknown", 0, y, 0)
    )

    fun isInBiomeGroup(biomeKey: String, groupId: String): Boolean =
        normalizeBiomeKey(biomeKey) in groups[groupId.lowercase(Locale.ROOT)].orEmpty()

    fun biomesInGroups(groupIds: Collection<String>): Set<String> =
        groupIds.flatMap { groups[it.lowercase(Locale.ROOT)].orEmpty() }.toSet()

    fun verticalRegion(id: String): EnvironmentVerticalRegion? =
        verticalRegions[id.lowercase(Locale.ROOT)]

    fun isOceanFamily(conditions: EnvironmentConditions): Boolean =
        conditions.water == WaterRegion.OCEAN ||
            conditions.biomeKey.substringAfter(':').let { it == "ocean" || it.endsWith("_ocean") }

    fun isOceanFamily(biomeKey: String, worldEnvironment: World.Environment = World.Environment.NORMAL): Boolean =
        isOceanFamily(conditions(biomeKey, worldEnvironment))

    private fun verticalRegionsFor(y: Int): Set<String> = verticalRegions
        .filterValues { it.contains(y) }
        .keys

    private fun fallbackProfile(biomeKey: String): EnvironmentBiomeProfile {
        val path = biomeKey.substringAfter(':')
        return when {
            path == "river" || path == "frozen_river" ->
                EnvironmentBiomeProfile(ClimateRegion.WET, TerrainRegion.WETLAND, WaterRegion.RIVER)
            path == "ocean" || path.endsWith("_ocean") ->
                EnvironmentBiomeProfile(ClimateRegion.WET, TerrainRegion.OCEAN, WaterRegion.OCEAN)
            listOf("frozen", "snow", "ice", "cold", "grove").any(path::contains) ->
                EnvironmentBiomeProfile(ClimateRegion.COLD, TerrainRegion.FIELD, WaterRegion.LAND)
            listOf("desert", "badlands", "savanna").any(path::contains) ->
                EnvironmentBiomeProfile(ClimateRegion.DRY, TerrainRegion.BADLANDS, WaterRegion.LAND)
            listOf("swamp", "mangrove").any(path::contains) ->
                EnvironmentBiomeProfile(ClimateRegion.WET, TerrainRegion.WETLAND, WaterRegion.LAND)
            path.contains("jungle") ->
                EnvironmentBiomeProfile(ClimateRegion.TROPICAL, TerrainRegion.JUNGLE, WaterRegion.LAND)
            path.contains("taiga") ->
                EnvironmentBiomeProfile(ClimateRegion.COLD, TerrainRegion.TAIGA, WaterRegion.LAND)
            listOf("mountain", "peak", "slope", "windswept", "stony").any(path::contains) ->
                EnvironmentBiomeProfile(ClimateRegion.TEMPERATE, TerrainRegion.MOUNTAIN, WaterRegion.LAND)
            else ->
                EnvironmentBiomeProfile(ClimateRegion.TEMPERATE, TerrainRegion.FIELD, WaterRegion.LAND)
        }
    }

    companion object {
        const val CONFIG_PATH = "config/environment/regions.yml"

        fun load(plugin: JavaPlugin): CollectionEnvironmentResolver {
            val file = ensureFile(plugin)
            val config = YamlConfiguration.loadConfiguration(file)
            require(config.get("config_version") is Number && config.getInt("config_version") == 1) {
                "$CONFIG_PATH.config_version must be the integer 1"
            }
            val groups = loadGroups(config.getConfigurationSection("biome_groups"), file)
            val profiles = loadProfiles(config.getConfigurationSection("biome_profiles"), file)
            val vertical = loadVerticalRegions(config.getConfigurationSection("vertical_regions"), file)
            require(groups.isNotEmpty()) { "$CONFIG_PATH.biome_groups must not be empty" }
            require(profiles.isNotEmpty()) { "$CONFIG_PATH.biome_profiles must not be empty" }
            require(vertical.isNotEmpty()) { "$CONFIG_PATH.vertical_regions must not be empty" }
            return CollectionEnvironmentResolver(profiles, groups, vertical)
        }

        /** 設定を必要としないポリシーのテスト・静的API向けの同一既定値です。 */
        fun defaults(): CollectionEnvironmentResolver = CollectionEnvironmentResolver(
            defaultProfiles(),
            defaultGroups(),
            defaultVerticalRegions()
        )

        private fun loadGroups(
            section: ConfigurationSection?,
            file: File
        ): Map<String, Set<String>> {
            requireNotNull(section) { "$file.biome_groups must be a section" }
            return section.getKeys(false).associate { rawId ->
                val id = normalizeId(rawId, "$file.biome_groups")
                val values = section.getStringList(rawId).map(::normalizeBiomeKey).toSet()
                require(values.isNotEmpty()) { "$file.biome_groups.$rawId must not be empty" }
                id to values
            }
        }

        private fun loadProfiles(
            section: ConfigurationSection?,
            file: File
        ): Map<String, EnvironmentBiomeProfile> {
            requireNotNull(section) { "$file.biome_profiles must be a section" }
            return section.getKeys(false).associate { rawBiome ->
                val biome = normalizeBiomeKey(rawBiome)
                val raw = section.getConfigurationSection(rawBiome)
                    ?: error("$file.biome_profiles.$rawBiome must be a section")
                biome to EnvironmentBiomeProfile(
                    enumValue(raw, "climate", file),
                    enumValue(raw, "terrain", file),
                    enumValue(raw, "water", file)
                )
            }
        }

        private fun loadVerticalRegions(
            section: ConfigurationSection?,
            file: File
        ): Map<String, EnvironmentVerticalRegion> {
            requireNotNull(section) { "$file.vertical_regions must be a section" }
            return section.getKeys(false).associate { rawId ->
                val id = normalizeId(rawId, "$file.vertical_regions")
                val raw = section.getConfigurationSection(rawId)
                    ?: error("$file.vertical_regions.$rawId must be a section")
                id to EnvironmentVerticalRegion(
                    requireInt(raw, "minimum_y", file),
                    requireInt(raw, "maximum_y", file)
                )
            }
        }

        private fun normalizeBiomeKey(raw: String): String = raw.lowercase(Locale.ROOT).let {
            if (':' in it) it else "minecraft:$it"
        }

        private fun normalizeId(raw: String, path: String): String {
            require(raw.matches(Regex("[a-z0-9_.-]+"))) { "$path.$raw is invalid" }
            return raw.lowercase(Locale.ROOT)
        }

        private inline fun <reified T : Enum<T>> enumValue(
            section: ConfigurationSection,
            key: String,
            file: File
        ): T = runCatching { enumValueOf<T>(section.getString(key).orEmpty().uppercase(Locale.ROOT)) }
            .getOrElse { error("$file.$key is invalid") }

        private fun requireInt(section: ConfigurationSection, key: String, file: File): Int {
            val value = section.get(key)
            require(value is Number && value.toDouble() == value.toInt().toDouble()) {
                "$file.$key must be an integer"
            }
            return value.toInt()
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

        private fun defaultVerticalRegions(): Map<String, EnvironmentVerticalRegion> = mapOf(
            "wild_gathering" to EnvironmentVerticalRegion(35, 200),
            "mineral_high" to EnvironmentVerticalRegion(96, 320),
            "mineral_shallow" to EnvironmentVerticalRegion(32, 95),
            "mineral_middle" to EnvironmentVerticalRegion(0, 31),
            "mineral_deep" to EnvironmentVerticalRegion(Int.MIN_VALUE, -1)
        )

        private fun defaultGroups(): Map<String, Set<String>> {
            fun minecraft(vararg ids: String) = ids.map { "minecraft:$it" }.toSet()
            return mapOf(
                "temperate" to minecraft(
                    "plains", "sunflower_plains", "forest", "flower_forest", "birch_forest",
                    "old_growth_birch_forest", "dark_forest", "meadow", "cherry_grove", "pale_garden",
                    "windswept_forest", "windswept_hills", "windswept_gravelly_hills"
                ),
                "temperate_field" to minecraft(
                    "plains", "sunflower_plains", "meadow", "forest", "birch_forest",
                    "old_growth_birch_forest"
                ),
                "wetland" to minecraft("river", "swamp", "mangrove_swamp"),
                "warm_field" to minecraft("plains", "sunflower_plains", "savanna", "savanna_plateau", "windswept_savanna"),
                "jungle" to minecraft("jungle", "sparse_jungle", "bamboo_jungle"),
                "dry_field" to minecraft("savanna", "savanna_plateau", "windswept_savanna", "wooded_badlands"),
                "forest" to minecraft("forest", "flower_forest", "birch_forest", "old_growth_birch_forest", "bamboo_jungle"),
                "tea_source" to minecraft(
                    "forest", "birch_forest", "old_growth_birch_forest", "taiga",
                    "old_growth_pine_taiga", "old_growth_spruce_taiga", "bamboo_jungle"
                ),
                "taiga" to minecraft("taiga", "old_growth_pine_taiga", "old_growth_spruce_taiga"),
                "cold_field" to minecraft("plains", "meadow", "snowy_plains", "grove"),
                "flower_field" to minecraft("plains", "sunflower_plains", "meadow", "flower_forest"),
                "green_onion_field" to minecraft("plains", "meadow", "river", "forest"),
                "grape_forest" to minecraft("forest", "flower_forest", "birch_forest", "jungle", "sparse_jungle"),
                "blueberry_taiga" to minecraft(
                    "taiga", "snowy_taiga", "grove", "old_growth_pine_taiga", "old_growth_spruce_taiga"
                ),
                "ocean_family" to minecraft(
                    "ocean", "deep_ocean", "cold_ocean", "deep_cold_ocean", "frozen_ocean",
                    "lukewarm_ocean", "deep_lukewarm_ocean", "warm_ocean", "deep_warm_ocean"
                ),
                "forest_product_old_growth_pine_taiga" to minecraft("old_growth_pine_taiga", "old_growth_spruce_taiga"),
                "forest_product_old_growth_birch_forest" to minecraft("old_growth_birch_forest"),
                "forest_product_oak_swamp" to minecraft("swamp"),
                "forest_product_pale_garden" to minecraft("pale_garden"),
                "forest_product_dark_forest" to minecraft("dark_forest"),
                "forest_product_jungle" to minecraft("jungle", "sparse_jungle", "bamboo_jungle"),
                "forest_product_eroded_savanna" to minecraft("eroded_savanna"),
                "forest_product_cherry_grove" to minecraft("cherry_grove"),
                "forest_product_mangrove_swamp" to minecraft("mangrove_swamp")
            )
        }

        private fun defaultProfiles(): Map<String, EnvironmentBiomeProfile> {
            val groups = defaultGroups()
            val profiles = mutableMapOf<String, EnvironmentBiomeProfile>()
            fun assign(group: String, climate: ClimateRegion, terrain: TerrainRegion, water: WaterRegion) {
                groups[group].orEmpty().forEach { biome ->
                    profiles[biome] = EnvironmentBiomeProfile(climate, terrain, water)
                }
            }
            assign("temperate", ClimateRegion.TEMPERATE, TerrainRegion.FIELD, WaterRegion.LAND)
            assign("forest", ClimateRegion.TEMPERATE, TerrainRegion.FOREST, WaterRegion.LAND)
            assign("wetland", ClimateRegion.WET, TerrainRegion.WETLAND, WaterRegion.LAND)
            assign("jungle", ClimateRegion.TROPICAL, TerrainRegion.JUNGLE, WaterRegion.LAND)
            assign("taiga", ClimateRegion.COLD, TerrainRegion.TAIGA, WaterRegion.LAND)
            assign("cold_field", ClimateRegion.COLD, TerrainRegion.FIELD, WaterRegion.LAND)
            assign("dry_field", ClimateRegion.DRY, TerrainRegion.BADLANDS, WaterRegion.LAND)
            assign("warm_field", ClimateRegion.WARM, TerrainRegion.FIELD, WaterRegion.LAND)
            groups["ocean_family"].orEmpty().forEach { biome ->
                profiles[biome] = EnvironmentBiomeProfile(ClimateRegion.WET, TerrainRegion.OCEAN, WaterRegion.OCEAN)
            }
            profiles["minecraft:river"] = EnvironmentBiomeProfile(ClimateRegion.WET, TerrainRegion.WETLAND, WaterRegion.RIVER)
            profiles["minecraft:frozen_river"] = EnvironmentBiomeProfile(ClimateRegion.COLD, TerrainRegion.WETLAND, WaterRegion.RIVER)
            return profiles
        }
    }
}
