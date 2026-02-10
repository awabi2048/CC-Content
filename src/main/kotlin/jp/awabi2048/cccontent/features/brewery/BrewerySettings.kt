package jp.awabi2048.cccontent.features.brewery

import jp.awabi2048.cccontent.features.brewery.model.FirePower
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

data class BreweryRecipe(
    val id: String,
    val name: String,
    val fermentationIngredients: Map<Material, Int>,
    val fermentationTime: Int,
    val fermentationIdealFirePower: FirePower,
    val fermentationParticleColor: String?,
    val distillationTime: Int,
    val distillationFilterConsumption: Int,
    val distillationRuns: Int,
    val agingTimeDays: Int,
    val agingBarrelTypes: Set<String>,
    val outputNames: List<String>,
    val outputDescriptions: List<String>,
    val outputAlcohol: Double,
    val outputColor: String?
) {
    fun getIngredientMaterials(): Set<Material> = fermentationIngredients.keys
    
    fun getTotalIngredientCount(): Int = fermentationIngredients.values.sum()
}

data class RecipeMatchResult(
    val recipe: BreweryRecipe,
    val typeMatchCount: Int,
    val countDifferenceScore: Double
) {
    val quality: Double
        get() = (100.0 - countDifferenceScore).coerceIn(0.0, 100.0)
}

data class BrewerySettings(
    val mismatchFirePenaltyPer30Seconds: Double,
    val distillationOverPenalty: Double,
    val fuelSecondsPerItem: Int,
    val filterSpeedBonus: Double,
    val angelSharePercentPerYear: Double,
    val agingRealSecondsPerYear: Long,
    val smallBarrelSpeedMultiplier: Double,
    val allowedMediumFireMaterials: Set<Material>
)

class BrewerySettingsLoader(private val plugin: JavaPlugin) {
    fun loadSettings(): BrewerySettings {
        val file = File(plugin.dataFolder, "brewery/config.yml")
        val yml = YamlConfiguration.loadConfiguration(file)
        val mediumRaw = yml.getStringList("settings.fire.medium_materials")
        val mediumMaterials = mediumRaw.mapNotNull { runCatching { Material.valueOf(it) }.getOrNull() }.toSet()
        return BrewerySettings(
            mismatchFirePenaltyPer30Seconds = yml.getDouble("settings.quality.penalty_per_30s_mismatch_fire", 2.0),
            distillationOverPenalty = yml.getDouble("settings.quality.penalty_per_over_distillation", 5.0),
            fuelSecondsPerItem = yml.getInt("settings.fire.fuel_seconds_per_item", 30).coerceAtLeast(1),
            filterSpeedBonus = yml.getDouble("settings.distillation.filter_speed_bonus", 0.15).coerceIn(0.0, 0.9),
            angelSharePercentPerYear = yml.getDouble("settings.aging.angel_share_percent_per_year", 2.0).coerceAtLeast(0.0),
            agingRealSecondsPerYear = yml.getLong("settings.aging.real_seconds_per_year", 1200L).coerceAtLeast(1L),
            smallBarrelSpeedMultiplier = yml.getDouble("settings.aging.small_barrel_speed_multiplier", 1.25).coerceAtLeast(0.1),
            allowedMediumFireMaterials = mediumMaterials
        )
    }

    fun loadRecipes(): Map<String, BreweryRecipe> {
        val file = File(plugin.dataFolder, "recipe/brewery.yml")
        val yml = YamlConfiguration.loadConfiguration(file)
        val recipes = mutableMapOf<String, BreweryRecipe>()
        for (id in yml.getKeys(false)) {
            val node = yml.getConfigurationSection(id) ?: continue
            val recipe = parseRecipe(id, node)
            if (recipe != null) {
                recipes[id] = recipe
            }
        }
        return recipes
    }

    private fun parseRecipe(id: String, node: org.bukkit.configuration.ConfigurationSection): BreweryRecipe? {
        val name = node.getString("name", id) ?: id
        
        val fermentationSection = node.getConfigurationSection("fermentation") ?: return null
        val ingredientsSection = fermentationSection.getConfigurationSection("ingredients") ?: return null
        val ingredients = mutableMapOf<Material, Int>()
        for (materialName in ingredientsSection.getKeys(false)) {
            val material = runCatching { Material.valueOf(materialName.uppercase()) }.getOrNull() ?: continue
            val count = ingredientsSection.getInt(materialName, 1).coerceAtLeast(1)
            ingredients[material] = count
        }
        if (ingredients.isEmpty()) return null
        
        val fermentationTime = fermentationSection.getInt("time", 180).coerceAtLeast(1)
        val heatStr = fermentationSection.getString("heat", "medium")?.uppercase() ?: "MEDIUM"
        val firePower = runCatching { FirePower.valueOf(heatStr) }.getOrDefault(FirePower.MEDIUM)
        val particleColor = fermentationSection.getString("particle_color")
        
        val distillationSection = node.getConfigurationSection("distillation")
        val distillationTime = distillationSection?.getInt("time", 45) ?: 45
        val distillationFilterConsumption = distillationSection?.getInt("filter_consumption", 1) ?: 1
        val distillationRuns = distillationSection?.getInt("runs", 3) ?: 3
        
        val agingSection = node.getConfigurationSection("aging")
        val agingTimeDays = agingSection?.getInt("time", 1) ?: 1
        val barrelTypesRaw = agingSection?.getStringList("barrel_type") ?: listOf("any")
        val barrelTypes = if (barrelTypesRaw.contains("any")) {
            setOf("any")
        } else {
            barrelTypesRaw.map { it.lowercase() }.toSet()
        }
        
        val outputSection = node.getConfigurationSection("output") ?: return null
        val names = outputSection.getStringList("name")
        val descriptions = outputSection.getStringList("description")
        val alcohol = outputSection.getDouble("alchol", 40.0).coerceIn(0.0, 100.0)
        val color = outputSection.getString("color")
        
        return BreweryRecipe(
            id = id,
            name = name,
            fermentationIngredients = ingredients,
            fermentationTime = fermentationTime,
            fermentationIdealFirePower = firePower,
            fermentationParticleColor = particleColor,
            distillationTime = distillationTime,
            distillationFilterConsumption = distillationFilterConsumption,
            distillationRuns = distillationRuns,
            agingTimeDays = agingTimeDays,
            agingBarrelTypes = barrelTypes,
            outputNames = if (names.size >= 3) names else listOf("${name}（低品質）", name, "${name}（高品質）"),
            outputDescriptions = if (descriptions.size >= 3) descriptions else listOf("", "", ""),
            outputAlcohol = alcohol,
            outputColor = color
        )
    }
    
    fun findBestRecipe(
        recipes: Map<String, BreweryRecipe>,
        inputItems: Map<Material, Int>
    ): RecipeMatchResult? {
        if (inputItems.isEmpty()) return null
        
        val inputMaterials = inputItems.keys
        val inputTotalCount = inputItems.values.sum()
        
        var bestMatch: RecipeMatchResult? = null
        
        for (recipe in recipes.values) {
            val recipeMaterials = recipe.getIngredientMaterials()
            val typeMatchCount = inputMaterials.intersect(recipeMaterials).size
            
            if (typeMatchCount == 0) continue
            
            var countDiffScore = 0.0
            for ((material, requiredCount) in recipe.fermentationIngredients) {
                val actualCount = inputItems[material] ?: 0
                val diff = kotlin.math.abs(actualCount - requiredCount)
                countDiffScore += diff * 5.0
            }
            for ((material, actualCount) in inputItems) {
                if (material !in recipeMaterials) {
                    countDiffScore += actualCount * 10.0
                }
            }
            
            val match = RecipeMatchResult(recipe, typeMatchCount, countDiffScore)
            
            if (bestMatch == null) {
                bestMatch = match
                continue
            }
            
            if (typeMatchCount > bestMatch.typeMatchCount) {
                bestMatch = match
            } else if (typeMatchCount == bestMatch.typeMatchCount && countDiffScore < bestMatch.countDifferenceScore) {
                bestMatch = match
            }
        }
        
        return bestMatch
    }
    
    fun getOutputNameByQuality(recipe: BreweryRecipe, quality: Double): String {
        val index = when {
            quality < 34 -> 0
            quality < 67 -> 1
            else -> 2
        }
        return recipe.outputNames.getOrElse(index) { recipe.name }
    }
    
    fun getOutputDescriptionByQuality(recipe: BreweryRecipe, quality: Double): String {
        val index = when {
            quality < 34 -> 0
            quality < 67 -> 1
            else -> 2
        }
        return recipe.outputDescriptions.getOrElse(index) { "" }
    }
}
