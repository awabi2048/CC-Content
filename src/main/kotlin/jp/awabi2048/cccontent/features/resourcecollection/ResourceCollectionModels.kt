package jp.awabi2048.cccontent.features.resourcecollection

import jp.awabi2048.cccontent.features.rank.profession.Profession
import org.bukkit.Material
import org.bukkit.block.data.Ageable
import org.bukkit.block.data.BlockData
import java.util.Random
import org.bukkit.World

enum class ResourceCollectionKind(val profession: Profession, val bonusItemId: String) {
    MINERAL(Profession.MINER, "mica_flake"),
    FOREST(Profession.LUMBERJACK, "resin"),
    CROP(Profession.FARMER, "straw")
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
        Material.PALE_OAK_LOG, Material.CRIMSON_STEM, Material.WARPED_STEM
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

    fun isLeaf(material: Material): Boolean = material.name.endsWith("_LEAVES") ||
        material == Material.NETHER_WART_BLOCK || material == Material.WARPED_WART_BLOCK

    fun treeReplantMaterial(material: Material): Material? = when (material) {
        Material.OAK_LOG -> Material.OAK_SAPLING
        Material.BIRCH_LOG -> Material.BIRCH_SAPLING
        Material.SPRUCE_LOG -> Material.SPRUCE_SAPLING
        Material.JUNGLE_LOG -> Material.JUNGLE_SAPLING
        Material.ACACIA_LOG -> Material.ACACIA_SAPLING
        Material.DARK_OAK_LOG -> Material.DARK_OAK_SAPLING
        Material.MANGROVE_LOG -> Material.MANGROVE_PROPAGULE
        Material.CHERRY_LOG -> Material.CHERRY_SAPLING
        Material.PALE_OAK_LOG -> Material.PALE_OAK_SAPLING
        Material.CRIMSON_STEM -> Material.CRIMSON_FUNGUS
        Material.WARPED_STEM -> Material.WARPED_FUNGUS
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
    fun specialMaterialCount(
        averageAccuracy: Double,
        minimumStandardEnabled: Boolean,
        topEvaluationExtra: Int
    ): Int {
        require(averageAccuracy in 0.0..1.0) { "Chisel accuracy must be between 0 and 1" }
        require(topEvaluationExtra >= 0) { "Chisel top-evaluation bonus must not be negative" }
        val base = when {
            averageAccuracy >= 0.90 -> 3
            averageAccuracy >= 0.70 -> 2
            averageAccuracy >= 0.40 -> 1
            minimumStandardEnabled -> 1
            else -> 0
        }
        return base + if (averageAccuracy >= 0.90) topEvaluationExtra else 0
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
