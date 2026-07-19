package jp.awabi2048.cccontent.features.resourcecollection

import jp.awabi2048.cccontent.features.rank.profession.Profession
import org.bukkit.Material
import org.bukkit.block.data.Ageable
import org.bukkit.block.data.BlockData
import java.util.Random
import org.bukkit.World
import kotlin.math.hypot
import kotlin.math.round

enum class ResourceCollectionKind(val profession: Profession) {
    MINERAL(Profession.MINER),
    FOREST(Profession.LUMBERJACK),
    CROP(Profession.FARMER)
}

object ResourceMaterialPolicy {
    private val ores = setOf(
        Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE, Material.COPPER_ORE,
        Material.DEEPSLATE_COPPER_ORE, Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
        Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE, Material.REDSTONE_ORE,
        Material.DEEPSLATE_REDSTONE_ORE, Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
        Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE, Material.EMERALD_ORE,
        Material.DEEPSLATE_EMERALD_ORE, Material.NETHER_QUARTZ_ORE, Material.NETHER_GOLD_ORE,
        Material.ANCIENT_DEBRIS
    )
    private val logs = setOf(
        Material.OAK_LOG, Material.BIRCH_LOG, Material.SPRUCE_LOG, Material.JUNGLE_LOG,
        Material.ACACIA_LOG, Material.DARK_OAK_LOG, Material.MANGROVE_LOG, Material.CHERRY_LOG,
        Material.PALE_OAK_LOG,
        Material.STRIPPED_OAK_LOG, Material.STRIPPED_BIRCH_LOG, Material.STRIPPED_SPRUCE_LOG,
        Material.STRIPPED_JUNGLE_LOG, Material.STRIPPED_ACACIA_LOG, Material.STRIPPED_DARK_OAK_LOG,
        Material.STRIPPED_MANGROVE_LOG, Material.STRIPPED_CHERRY_LOG, Material.STRIPPED_PALE_OAK_LOG,
        Material.CRIMSON_STEM, Material.WARPED_STEM,
        Material.STRIPPED_CRIMSON_STEM, Material.STRIPPED_WARPED_STEM
    )
    private val ageableCrops = setOf(
        Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS,
        Material.COCOA, Material.NETHER_WART, Material.SWEET_BERRY_BUSH
    )
    private val naturalPlants = setOf(
        Material.MELON, Material.PUMPKIN, Material.SUGAR_CANE, Material.CACTUS,
        Material.BAMBOO, Material.KELP, Material.KELP_PLANT
    )
    private val wildVegetation = setOf(
        Material.SHORT_GRASS,
        Material.TALL_GRASS,
        Material.FERN,
        Material.LARGE_FERN,
        Material.DANDELION,
        Material.POPPY,
        Material.BLUE_ORCHID,
        Material.ALLIUM,
        Material.AZURE_BLUET,
        Material.RED_TULIP,
        Material.ORANGE_TULIP,
        Material.WHITE_TULIP,
        Material.PINK_TULIP,
        Material.OXEYE_DAISY,
        Material.CORNFLOWER,
        Material.LILY_OF_THE_VALLEY,
        Material.BROWN_MUSHROOM,
        Material.RED_MUSHROOM,
        Material.MOSS_BLOCK,
        Material.VINE,
        Material.SEAGRASS,
        Material.TALL_SEAGRASS,
        Material.KELP,
        Material.KELP_PLANT
    )

    fun classify(material: Material, blockData: BlockData): ResourceCollectionKind? = when {
        material in ores -> ResourceCollectionKind.MINERAL
        material in logs -> ResourceCollectionKind.FOREST
        material in ageableCrops && isFullyGrown(blockData) -> ResourceCollectionKind.CROP
        material in naturalPlants -> ResourceCollectionKind.CROP
        else -> null
    }

    private fun isFullyGrown(blockData: BlockData): Boolean {
        val ageable = blockData as? Ageable ?: return false
        return ageable.age >= ageable.maximumAge
    }

    fun isWildVegetation(material: Material): Boolean = material in wildVegetation

    fun isVegetationBase(material: Material): Boolean = material == Material.GRASS_BLOCK

    fun bonusItemId(kind: ResourceCollectionKind, sourceMaterial: Material): String? = when (kind) {
        ResourceCollectionKind.MINERAL -> "mica_flake"
        ResourceCollectionKind.FOREST -> "tree_resin"
        ResourceCollectionKind.CROP -> when (sourceMaterial) {
            Material.WHEAT -> "straw"
            Material.POTATOES -> "sprouted_potato"
            Material.CARROTS, Material.BEETROOTS -> "vegetable_leaves"
            Material.COCOA -> "cocoa_pulp"
            Material.NETHER_WART -> "wart_fiber"
            else -> null
        }
    }

    fun isLeaf(material: Material): Boolean = material.name.endsWith("_LEAVES") ||
        material == Material.NETHER_WART_BLOCK || material == Material.WARPED_WART_BLOCK

    fun treeReplantMaterial(material: Material): Material? = when (material) {
        Material.OAK_LOG, Material.STRIPPED_OAK_LOG -> Material.OAK_SAPLING
        Material.BIRCH_LOG, Material.STRIPPED_BIRCH_LOG -> Material.BIRCH_SAPLING
        Material.SPRUCE_LOG, Material.STRIPPED_SPRUCE_LOG -> Material.SPRUCE_SAPLING
        Material.JUNGLE_LOG, Material.STRIPPED_JUNGLE_LOG -> Material.JUNGLE_SAPLING
        Material.ACACIA_LOG, Material.STRIPPED_ACACIA_LOG -> Material.ACACIA_SAPLING
        Material.DARK_OAK_LOG, Material.STRIPPED_DARK_OAK_LOG -> Material.DARK_OAK_SAPLING
        Material.MANGROVE_LOG, Material.STRIPPED_MANGROVE_LOG -> Material.MANGROVE_PROPAGULE
        Material.CHERRY_LOG, Material.STRIPPED_CHERRY_LOG -> Material.CHERRY_SAPLING
        Material.PALE_OAK_LOG, Material.STRIPPED_PALE_OAK_LOG -> Material.PALE_OAK_SAPLING
        Material.CRIMSON_STEM, Material.STRIPPED_CRIMSON_STEM -> Material.CRIMSON_FUNGUS
        Material.WARPED_STEM, Material.STRIPPED_WARPED_STEM -> Material.WARPED_FUNGUS
        else -> null
    }

    fun canPlantTreeOn(material: Material): Boolean = material in setOf(
        Material.DIRT,
        Material.GRASS_BLOCK,
        Material.PODZOL,
        Material.COARSE_DIRT,
        Material.ROOTED_DIRT,
        Material.MOSS_BLOCK,
        Material.MUD,
        Material.CLAY,
        Material.CRIMSON_NYLIUM,
        Material.WARPED_NYLIUM,
        Material.SOUL_SOIL
    )
}

object NormalResourceBonusPolicy {
    fun succeeds(chance: Double, natural: Boolean, random: Random): Boolean {
        if (!natural || chance <= 0.0) return false
        require(chance <= 1.0) { "Normal resource bonus chance must not exceed 1.0" }
        return random.nextDouble() < chance
    }
}

object ChiselRewardPolicy {
    @JvmOverloads
    fun specialMaterialCount(
        averageAccuracy: Double,
        minimumStandardEnabled: Boolean,
        topEvaluationExtra: Int,
        topEvaluationThreshold: Double = 0.90
    ): Int {
        require(averageAccuracy in 0.0..1.0) { "Chisel accuracy must be between 0 and 1" }
        require(topEvaluationExtra >= 0) { "Chisel top-evaluation bonus must not be negative" }
        require(topEvaluationThreshold in 0.0..1.0) {
            "Chisel top-evaluation threshold must be between 0 and 1"
        }
        val base = when {
            averageAccuracy >= topEvaluationThreshold -> 3
            averageAccuracy >= 0.70 -> 2
            averageAccuracy >= 0.40 -> 1
            minimumStandardEnabled -> 1
            else -> 0
        }
        return base + if (averageAccuracy >= topEvaluationThreshold) topEvaluationExtra else 0
    }
}

data class ChiselAttemptResult(
    val score: Double,
    val countsAsAttempt: Boolean,
    val consumesIgnoredFailure: Boolean
)

object ChiselAttemptPolicy {
    fun evaluate(distance: Double, tolerance: Double, ignoredFailuresRemaining: Int): ChiselAttemptResult {
        require(distance >= 0.0) { "Chisel distance must not be negative" }
        require(tolerance > 0.0) { "Chisel tolerance must be positive" }
        require(ignoredFailuresRemaining >= 0) { "Ignored chisel failures must not be negative" }
        val score = (1.0 - distance / tolerance).coerceIn(0.0, 1.0)
        val minorFailure = distance > tolerance && distance <= tolerance + 1.0
        return if (minorFailure && ignoredFailuresRemaining > 0) {
            ChiselAttemptResult(0.0, countsAsAttempt = false, consumesIgnoredFailure = true)
        } else {
            ChiselAttemptResult(score, countsAsAttempt = true, consumesIgnoredFailure = false)
        }
    }
}

object ChiselHitPolicy {
    const val PIXEL_SIZE_BLOCKS: Double = 1.0 / 16.0
    const val BASE_TOLERANCE_PIXELS: Double = 2.0
    const val MAX_TOLERANCE_PIXELS: Double = 3.0

    fun tolerancePixels(precisionBonusBlocks: Double): Double {
        require(precisionBonusBlocks >= 0.0) { "Chisel precision bonus must not be negative" }
        return (BASE_TOLERANCE_PIXELS + precisionBonusBlocks / PIXEL_SIZE_BLOCKS)
            .coerceAtMost(MAX_TOLERANCE_PIXELS)
    }

    fun quantizedDistancePixels(firstAxisDeltaBlocks: Double, secondAxisDeltaBlocks: Double): Double {
        val firstPixels = round(firstAxisDeltaBlocks / PIXEL_SIZE_BLOCKS)
        val secondPixels = round(secondAxisDeltaBlocks / PIXEL_SIZE_BLOCKS)
        return hypot(firstPixels, secondPixels)
    }
}

enum class MineralAltitudeBand {
    HIGH,
    SHALLOW,
    MIDDLE,
    DEEP
}

enum class MineralBiomeBand {
    TEMPERATE,
    COLD,
    DRY,
    WET,
    MOUNTAIN,
    NETHER
}

data class MineralInspectionResult(
    val altitude: MineralAltitudeBand,
    val biome: MineralBiomeBand,
    val resourceId: String
)

object MineralCompanionPolicy {
    fun inspect(environment: World.Environment, biomeKey: String, y: Int): MineralInspectionResult {
        val altitude = when {
            y >= 96 -> MineralAltitudeBand.HIGH
            y >= 32 -> MineralAltitudeBand.SHALLOW
            y >= 0 -> MineralAltitudeBand.MIDDLE
            else -> MineralAltitudeBand.DEEP
        }
        val biome = biomeBand(environment, biomeKey)
        val resourceId = when {
            biome == MineralBiomeBand.NETHER -> "sulfur"
            altitude == MineralAltitudeBand.DEEP -> "calcite_fragment"
            biome == MineralBiomeBand.DRY -> "rock_salt"
            else -> "mica_flake"
        }
        return MineralInspectionResult(altitude, biome, resourceId)
    }

    private fun biomeBand(environment: World.Environment, biomeKey: String): MineralBiomeBand {
        if (environment == World.Environment.NETHER) return MineralBiomeBand.NETHER
        val key = biomeKey.lowercase()
        return when {
            listOf("frozen", "snow", "ice", "cold", "grove").any(key::contains) -> MineralBiomeBand.COLD
            listOf("desert", "badlands", "savanna").any(key::contains) -> MineralBiomeBand.DRY
            listOf("swamp", "mangrove", "jungle", "river", "ocean").any(key::contains) -> MineralBiomeBand.WET
            listOf("mountain", "peak", "slope", "windswept", "stony").any(key::contains) -> MineralBiomeBand.MOUNTAIN
            else -> MineralBiomeBand.TEMPERATE
        }
    }
}
