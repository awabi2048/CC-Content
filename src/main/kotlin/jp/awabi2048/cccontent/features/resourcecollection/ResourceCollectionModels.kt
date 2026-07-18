package jp.awabi2048.cccontent.features.resourcecollection

import jp.awabi2048.cccontent.features.rank.profession.Profession
import org.bukkit.Material
import org.bukkit.block.data.Ageable
import org.bukkit.block.data.BlockData
import java.util.Random

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
}

object NormalResourceBonusPolicy {
    fun succeeds(chance: Double, natural: Boolean, random: Random): Boolean {
        if (!natural || chance <= 0.0) return false
        require(chance <= 1.0) { "Normal resource bonus chance must not exceed 1.0" }
        return random.nextDouble() < chance
    }
}
