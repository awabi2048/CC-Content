package jp.awabi2048.cccontent.features.brewery

import jp.awabi2048.cccontent.features.brewery.model.FirePower
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import kotlin.math.abs

data class IngredientDefinition(
    val key: String,
    val materials: Set<Material> = emptySet(),
    val itemName: String? = null,
    val itemNameContains: String? = null
) {
    fun matches(item: ItemStack): Boolean {
        if (item.type.isAir) return false
        val name = item.itemMeta?.displayName ?: ""
        val conditions = mutableListOf<Boolean>()
        if (materials.isNotEmpty()) conditions += item.type in materials
        if (!itemName.isNullOrBlank()) conditions += name == itemName
        if (!itemNameContains.isNullOrBlank()) conditions += name.contains(itemNameContains)
        return conditions.any { it }
    }
}

data class BreweryRecipe(
    val id: String,
    val name: String,
    val requiredSkillLevel: Int,
    val requiredSkills: Set<String>,
    val fermentationIngredients: Map<String, Int>,
    val fermentationTime: Int,
    val fermentationIdealFirePower: FirePower,
    val fermentationParticleColor: String?,
    val distillationTime: Int,
    val distillationFilterConsumption: Int,
    val distillationRuns: Int,
    val agingTimeDays: Int,
    val agingBarrelTypes: Set<String>,
    val middleOutputName: String,
    val middleOutputDescription: String,
    val middleOutputColor: String?,
    val finalOutputNames: List<String>,
    val finalOutputDescriptions: List<String>,
    val finalOutputAlcohol: Double,
    val finalOutputColor: String?
)

data class RecipeMatchResult(
    val recipe: BreweryRecipe,
    val typeMatchCount: Int,
    val countDifferenceScore: Double,
    val unmatchedItemAmount: Int,
    val matchedIngredientCounts: Map<String, Int>
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
    val allowedMediumFireMaterials: Set<Material>,
    val qualityDebugLog: Boolean
)

class BrewerySettingsLoader(private val plugin: JavaPlugin) {
    private val ingredientDefinitions: Map<String, IngredientDefinition> by lazy { loadIngredientDefinitions() }

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
            allowedMediumFireMaterials = mediumMaterials,
            qualityDebugLog = yml.getBoolean("settings.debug.quality_log", true)
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

    fun getIngredientDefinition(key: String): IngredientDefinition? = ingredientDefinitions[key]

    private fun parseRecipe(id: String, node: ConfigurationSection): BreweryRecipe? {
        val name = node.getString("name", id) ?: id
        val requiredSkillLevel = node.getInt("required_skill_level", 1).coerceAtLeast(1)
        val requiredSkills = node.getStringList("required_skills").map { it.trim() }.filter { it.isNotBlank() }.toSet()

        val fermentationSection = node.getConfigurationSection("fermentation") ?: return null
        val ingredientsSection = fermentationSection.getConfigurationSection("ingredients") ?: return null
        val ingredients = linkedMapOf<String, Int>()
        for (ingredientKey in ingredientsSection.getKeys(false)) {
            val count = ingredientsSection.getInt(ingredientKey, 1).coerceAtLeast(1)
            ingredients[ingredientKey] = count
        }
        if (ingredients.isEmpty()) return null

        val fermentationTime = fermentationSection.getInt("time", 180).coerceAtLeast(1)
        val heatStr = fermentationSection.getString("heat", "medium")?.uppercase() ?: "MEDIUM"
        val firePower = runCatching { FirePower.valueOf(heatStr) }.getOrDefault(FirePower.MEDIUM)
        val particleColor = fermentationSection.getString("particle_color")

        val distillationSection = node.getConfigurationSection("distillation")
        val distillationTime = distillationSection?.getInt("time", 45)?.coerceAtLeast(1) ?: 45
        val distillationFilterConsumption = distillationSection?.getInt("filter_consumption", 1)?.coerceAtLeast(1) ?: 1
        val distillationRuns = distillationSection?.getInt("runs", 3)?.coerceAtLeast(1) ?: 3

        val agingSection = node.getConfigurationSection("aging")
        val agingTimeDays = agingSection?.getInt("time", 1)?.coerceAtLeast(1) ?: 1
        val barrelTypes = parseBarrelTypes(agingSection)

        val middleOutputSection = node.getConfigurationSection("middle_output") ?: return null
        val middleOutputName = middleOutputSection.getString("name", "中間生成物") ?: "中間生成物"
        val middleOutputDescription = middleOutputSection.getString("description", "") ?: ""
        val middleOutputColor = middleOutputSection.getString("color")

        val finalOutputSection = node.getConfigurationSection("final_output") ?: return null
        val names = finalOutputSection.getStringList("name")
        val descriptions = finalOutputSection.getStringList("description")
        val alcohol = finalOutputSection.getDouble("alchol", 40.0).coerceIn(0.0, 100.0)
        val color = finalOutputSection.getString("color")

        return BreweryRecipe(
            id = id,
            name = name,
            requiredSkillLevel = requiredSkillLevel,
            requiredSkills = requiredSkills,
            fermentationIngredients = ingredients,
            fermentationTime = fermentationTime,
            fermentationIdealFirePower = firePower,
            fermentationParticleColor = particleColor,
            distillationTime = distillationTime,
            distillationFilterConsumption = distillationFilterConsumption,
            distillationRuns = distillationRuns,
            agingTimeDays = agingTimeDays,
            agingBarrelTypes = barrelTypes,
            middleOutputName = middleOutputName,
            middleOutputDescription = middleOutputDescription,
            middleOutputColor = middleOutputColor,
            finalOutputNames = if (names.size >= 3) names else listOf("${name}（低品質）", name, "${name}（高品質）"),
            finalOutputDescriptions = if (descriptions.size >= 3) descriptions else listOf("", "", ""),
            finalOutputAlcohol = alcohol,
            finalOutputColor = color
        )
    }

    private fun parseBarrelTypes(agingSection: ConfigurationSection?): Set<String> {
        val raw = agingSection?.get("barrel_type")
        return when (raw) {
            null -> setOf("any")
            is String -> if (raw.equals("any", ignoreCase = true)) setOf("any") else setOf(raw.lowercase())
            is List<*> -> {
                val list = raw.mapNotNull { it?.toString()?.lowercase() }
                if (list.any { it == "any" }) setOf("any") else list.toSet()
            }
            else -> setOf("any")
        }
    }

    private fun loadIngredientDefinitions(): Map<String, IngredientDefinition> {
        val file = File(plugin.dataFolder, "recipe/ingredient_definition.yml")
        val yml = YamlConfiguration.loadConfiguration(file)
        val section = yml.getConfigurationSection("ingredients") ?: return emptyMap()
        val map = mutableMapOf<String, IngredientDefinition>()

        for (key in section.getKeys(false)) {
            val raw = section.get(key)
            val definition = when (raw) {
                is String -> IngredientDefinition(
                    key = key,
                    materials = setOfNotNull(parseMaterial(raw))
                )
                is List<*> -> IngredientDefinition(
                    key = key,
                    materials = raw.mapNotNull { parseMaterial(it?.toString() ?: "") }.toSet()
                )
                is ConfigurationSection -> {
                    val materials = mutableSetOf<Material>()
                    val single = raw.getString("material")
                    if (!single.isNullOrBlank()) {
                        parseMaterial(single)?.let { materials += it }
                    }
                    materials += raw.getStringList("materials").mapNotNull { parseMaterial(it) }
                    IngredientDefinition(
                        key = key,
                        materials = materials,
                        itemName = raw.getString("item_name"),
                        itemNameContains = raw.getString("item_name_contains")
                    )
                }
                else -> IngredientDefinition(key = key)
            }
            map[key] = definition
        }

        return map
    }

    private fun parseMaterial(raw: String): Material? {
        if (raw.isBlank()) return null
        return runCatching { Material.valueOf(raw.uppercase()) }.getOrNull()
    }

    fun findBestRecipe(recipes: Map<String, BreweryRecipe>, inputItems: List<ItemStack>): RecipeMatchResult? {
        if (inputItems.isEmpty()) return null

        var bestMatch: RecipeMatchResult? = null
        for (recipe in recipes.values) {
            val match = matchRecipe(recipe, inputItems) ?: continue
            if (bestMatch == null) {
                bestMatch = match
                continue
            }
            if (match.typeMatchCount > bestMatch.typeMatchCount) {
                bestMatch = match
            } else if (match.typeMatchCount == bestMatch.typeMatchCount && match.countDifferenceScore < bestMatch.countDifferenceScore) {
                bestMatch = match
            }
        }
        return bestMatch
    }

    private fun matchRecipe(recipe: BreweryRecipe, inputItems: List<ItemStack>): RecipeMatchResult? {
        val counts = recipe.fermentationIngredients.keys.associateWith { 0 }.toMutableMap()
        var unmatchedAmount = 0

        for (item in inputItems) {
            val matchedKey = recipe.fermentationIngredients.keys.firstOrNull { key -> matchesIngredientKey(key, item) }
            if (matchedKey == null) {
                unmatchedAmount += item.amount
                continue
            }
            counts[matchedKey] = (counts[matchedKey] ?: 0) + item.amount
        }

        val typeMatchCount = recipe.fermentationIngredients.keys.count { (counts[it] ?: 0) > 0 }
        if (typeMatchCount <= 0) return null

        var countDiffScore = 0.0
        for ((ingredientKey, requiredCount) in recipe.fermentationIngredients) {
            val actual = counts[ingredientKey] ?: 0
            countDiffScore += abs(actual - requiredCount) * 5.0
        }
        countDiffScore += unmatchedAmount * 10.0

        return RecipeMatchResult(
            recipe = recipe,
            typeMatchCount = typeMatchCount,
            countDifferenceScore = countDiffScore,
            unmatchedItemAmount = unmatchedAmount,
            matchedIngredientCounts = counts
        )
    }

    fun matchesIngredientKey(ingredientKey: String, item: ItemStack): Boolean {
        val definition = ingredientDefinitions[ingredientKey]
        if (definition != null) {
            return definition.matches(item)
        }
        val material = parseMaterial(ingredientKey) ?: return false
        return item.type == material
    }

    fun getFinalOutputNameByQuality(recipe: BreweryRecipe, quality: Double): String {
        val index = when {
            quality < 34 -> 0
            quality < 67 -> 1
            else -> 2
        }
        return recipe.finalOutputNames.getOrElse(index) { recipe.name }
    }

    fun getFinalOutputDescriptionByQuality(recipe: BreweryRecipe, quality: Double): String {
        val index = when {
            quality < 34 -> 0
            quality < 67 -> 1
            else -> 2
        }
        return recipe.finalOutputDescriptions.getOrElse(index) { "" }
    }
}
