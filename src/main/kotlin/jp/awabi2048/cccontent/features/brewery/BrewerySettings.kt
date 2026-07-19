@file:Suppress("DEPRECATION")

package jp.awabi2048.cccontent.features.brewery

import jp.awabi2048.cccontent.features.brewery.model.FirePower
import jp.awabi2048.cccontent.items.CustomItemManager
import org.bukkit.Material
import org.bukkit.potion.PotionEffectType
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

data class IngredientDefinition(
    val key: String,
    val materials: Set<Material> = emptySet(),
    val customItemIds: Set<String> = emptySet(),
    val itemName: String? = null,
    val itemNameContains: String? = null
) {
    fun matches(item: ItemStack): Boolean {
        if (item.type.isAir) return false
        val name = item.itemMeta?.displayName ?: ""
        val conditions = mutableListOf<Boolean>()
        if (materials.isNotEmpty()) conditions += item.type in materials
        if (customItemIds.isNotEmpty()) conditions += CustomItemManager.identify(item)?.fullId in customItemIds
        if (!itemName.isNullOrBlank()) conditions += name == itemName
        if (!itemNameContains.isNullOrBlank()) conditions += name.contains(itemNameContains)
        return conditions.any { it }
    }
}

data class BreweryRecipe(
    val id: String,
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
    val middleOutputColor: String?,
    val finalOutputAlcohol: Double,
    val finalOutputColor: String?,
    val finalOutputEffects: List<BreweryEffectDefinition>,
    val finalOutputGlint: Boolean,
    val finalOutputCustomModelData: List<Int>
)

fun breweryQualityIndex(quality: Double): Int = when {
    quality < 34 -> 0
    quality < 67 -> 1
    else -> 2
}

fun breweryQualityTier(quality: Double): String = when (breweryQualityIndex(quality)) {
    0 -> "low"
    1 -> "standard"
    else -> "high"
}

data class BreweryEffectDefinition(
    val type: PotionEffectType,
    val worstAmplifier: Int,
    val bestAmplifier: Int,
    val worstDurationSeconds: Int,
    val bestDurationSeconds: Int
) {
    fun resolve(quality: Double): ResolvedBreweryEffect {
        val ratio = quality.coerceIn(0.0, 100.0) / 100.0
        return ResolvedBreweryEffect(
            type = type,
            amplifier = interpolate(worstAmplifier, bestAmplifier, ratio),
            durationSeconds = interpolate(worstDurationSeconds, bestDurationSeconds, ratio).coerceAtLeast(1)
        )
    }

    private fun interpolate(worst: Int, best: Int, ratio: Double): Int {
        return (worst + (best - worst) * ratio).roundToInt().coerceAtLeast(0)
    }
}

data class ResolvedBreweryEffect(
    val type: PotionEffectType,
    val amplifier: Int,
    val durationSeconds: Int
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
    val configVersion: Int,
    val mismatchFirePenaltyPer30Seconds: Double,
    val distillationOverPenalty: Double,
    val fuelSecondsPerItem: Int,
    val filterSpeedBonus: Double,
    val angelSharePercentPerYear: Double,
    val agingRealSecondsPerYear: Long,
    val smallBarrelSpeedMultiplier: Double,
    val fermentationSignKeyword: String,
    val agingSignKeyword: String,
    val fermentationCapacity: Int,
    val openLargeBarrelEverywhere: Boolean,
    val barrelInventoryRowsLarge: Int,
    val barrelInventoryRowsSmall: Int,
    val countDifferencePenalty: Double,
    val unmatchedItemPenalty: Double,
    val allowedMediumFireMaterials: Set<Material>,
    val nauseaThreshold: Double,
    val stumbleThreshold: Double,
    val faintThreshold: Double,
    val intoxicationDecayPerSecond: Double,
    val faintDurationSeconds: Long,
    val stateRetentionSeconds: Long,
    val fermentationExp: Long,
    val distillationExp: Long,
    val agingExp: Long
)

class BrewerySettingsLoader(private val plugin: JavaPlugin) {
    private val ingredientDefinitions: Map<String, IngredientDefinition> by lazy { loadIngredientDefinitions() }

    fun loadSettings(): BrewerySettings {
        val file = File(plugin.dataFolder, "config/brewery/config.yml")
        val yml = YamlConfiguration.loadConfiguration(file)
        val configVersion = yml.getInt("config_version", -1)
        require(configVersion == 2) { "Brewery設定のconfig_versionが2ではありません: $configVersion" }
        val root = yml.getConfigurationSection("brewery")
            ?: error("Brewery設定にbreweryセクションがありません")
        val mediumRaw = root.getStringList("fire.medium_materials")
        val mediumMaterials = mediumRaw.mapNotNull { runCatching { Material.valueOf(it) }.getOrNull() }.toSet()
        return BrewerySettings(
            configVersion = configVersion,
            mismatchFirePenaltyPer30Seconds = root.getDouble("quality.penalty_per_30s_mismatch_fire", 2.0),
            distillationOverPenalty = root.getDouble("quality.penalty_per_over_distillation", 5.0),
            fuelSecondsPerItem = root.getInt("fire.fuel_seconds_per_item", 30).coerceAtLeast(1),
            filterSpeedBonus = root.getDouble("distillation.filter_speed_bonus", 0.15).coerceIn(0.0, 0.9),
            angelSharePercentPerYear = root.getDouble("aging.angel_share_percent_per_year", 2.0).coerceAtLeast(0.0),
            agingRealSecondsPerYear = root.getLong("aging.real_seconds_per_year", 1200L).coerceAtLeast(1L),
            smallBarrelSpeedMultiplier = root.getDouble("aging.small_barrel_speed_multiplier", 1.25).coerceAtLeast(0.1),
            fermentationSignKeyword = root.getString("barrel.fermentation_sign_keyword")
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: error("brewery.barrel.fermentation_sign_keyword is required"),
            agingSignKeyword = root.getString("barrel.aging_sign_keyword")
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: error("brewery.barrel.aging_sign_keyword is required"),
            fermentationCapacity = root.getInt("barrel.fermentation_capacity", 3).also {
                require(it in 1..5) { "brewery.barrel.fermentation_capacity must be between 1 and 5" }
            },
            openLargeBarrelEverywhere = root.getBoolean("barrel.open_large_everywhere", true),
            barrelInventoryRowsLarge = root.getInt("barrel.inventory_rows_large", 3).also {
                require(it in 1..6) { "barrel.inventory_rows_large must be between 1 and 6" }
            },
            barrelInventoryRowsSmall = root.getInt("barrel.inventory_rows_small", 1).also {
                require(it in 1..6) { "barrel.inventory_rows_small must be between 1 and 6" }
            },
            countDifferencePenalty = root.getDouble("quality.count_difference_penalty", 5.0).coerceAtLeast(0.0),
            unmatchedItemPenalty = root.getDouble("quality.unmatched_item_penalty", 10.0).coerceAtLeast(0.0),
            allowedMediumFireMaterials = mediumMaterials,
            nauseaThreshold = root.getDouble("intoxication.nausea_threshold", 25.0).coerceAtLeast(0.0),
            stumbleThreshold = root.getDouble("intoxication.stumble_threshold", 50.0).coerceAtLeast(0.0),
            faintThreshold = root.getDouble("intoxication.faint_threshold", 90.0).coerceAtLeast(0.0),
            intoxicationDecayPerSecond = root.getDouble("intoxication.decay_per_second", 0.05).coerceAtLeast(0.0),
            faintDurationSeconds = root.getLong("intoxication.faint_duration_seconds", 8L).coerceAtLeast(1L),
            stateRetentionSeconds = root.getLong("state.retention_seconds", 604800L).coerceAtLeast(1L),
            fermentationExp = root.getLong("rank_exp.fermentation", 10L).coerceAtLeast(0L),
            distillationExp = root.getLong("rank_exp.distillation", 20L).coerceAtLeast(0L),
            agingExp = root.getLong("rank_exp.aging", 30L).coerceAtLeast(0L)
        )
    }

    fun loadRecipes(): Map<String, BreweryRecipe> {
        val file = File(plugin.dataFolder, "config/brewery/recipe.yml")
        val yml = YamlConfiguration.loadConfiguration(file)
        require(yml.getInt("config_version", -1) == 2) { "Breweryレシピ設定のconfig_versionが2ではありません" }
        val recipeSection = yml.getConfigurationSection("recipes")
            ?: error("Breweryレシピ設定にrecipesセクションがありません")
        val recipes = mutableMapOf<String, BreweryRecipe>()
        for (id in recipeSection.getKeys(false)) {
            val node = recipeSection.getConfigurationSection(id) ?: continue
            val recipe = parseRecipe(id, node)
            if (recipe != null) {
                recipes[id] = recipe
            }
        }
        return recipes
    }

    fun getIngredientDefinition(key: String): IngredientDefinition? = ingredientDefinitions[key]

    private fun parseRecipe(id: String, node: ConfigurationSection): BreweryRecipe? {
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

        val fermentationTime = fermentationSection.getInt("time_seconds", 180).coerceAtLeast(1)
        val heatStr = fermentationSection.getString("heat", "medium")?.uppercase() ?: "MEDIUM"
        val firePower = runCatching { FirePower.valueOf(heatStr) }.getOrDefault(FirePower.MEDIUM)
        val particleColor = fermentationSection.getString("particle_color")

        val distillationSection = node.getConfigurationSection("distillation")
        val distillationTime = distillationSection?.getInt("time_seconds", 45)?.coerceAtLeast(1) ?: 45
        val distillationFilterConsumption = distillationSection?.getInt("filter_consumption", 1)?.coerceAtLeast(1) ?: 1
        val distillationRuns = if (distillationSection?.contains("runs") == true) {
            distillationSection.getInt("runs").coerceAtLeast(0)
        } else {
            0
        }

        val agingSection = node.getConfigurationSection("aging")
        val agingTimeDays = if (agingSection?.contains("time_days") == true) {
            agingSection.getInt("time_days").coerceAtLeast(0)
        } else {
            0
        }
        val barrelTypes = parseBarrelTypes(agingSection)

        val middleOutputSection = node.getConfigurationSection("middle_output") ?: return null
        val middleOutputColor = middleOutputSection.getString("color")

        val finalOutputSection = node.getConfigurationSection("final_output") ?: return null
        require(finalOutputSection.contains("alcohol")) { "レシピ$id のfinal_output.alcoholがありません" }
        val alcohol = finalOutputSection.getDouble("alcohol").coerceIn(-100.0, 100.0)
        val color = finalOutputSection.getString("color")
        val effects = finalOutputSection.getStringList("effects").map { parseEffect(id, it) }
        val customModelData = finalOutputSection.getIntegerList("custom_model_data")

        return BreweryRecipe(
            id = id,
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
            middleOutputColor = middleOutputColor,
            finalOutputAlcohol = alcohol,
            finalOutputColor = color,
            finalOutputEffects = effects,
            finalOutputGlint = finalOutputSection.getBoolean("glint", false),
            finalOutputCustomModelData = customModelData
        )
    }

    private fun parseEffect(recipeId: String, raw: String): BreweryEffectDefinition {
        val parts = raw.split('/')
        require(parts.size == 3) { "レシピ$recipeId の効果は TYPE/amplifier/duration_seconds 形式が必要です: $raw" }
        val type = PotionEffectType.getByName(parts[0].trim().uppercase())
            ?: error("レシピ$recipeId のPotionEffectTypeが不正です: ${parts[0]}")
        val amplifier = parseRange(parts[1], recipeId, raw)
        val duration = parseRange(parts[2], recipeId, raw)
        return BreweryEffectDefinition(type, amplifier.first, amplifier.second, duration.first, duration.second)
    }

    private fun parseRange(raw: String, recipeId: String, whole: String): Pair<Int, Int> {
        val values = raw.split('-').map { it.toIntOrNull() }
        require(values.size in 1..2 && values.all { it != null && it >= 0 }) {
            "レシピ$recipeId の効果範囲が不正です: $whole"
        }
        val first = values[0]!!
        val second = values.getOrNull(1) ?: first
        return first to second
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
        val file = File(plugin.dataFolder, "config/ingredient_definition.yml")
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
                        customItemIds = buildSet {
                            raw.getString("custom_item_id")?.takeIf { it.isNotBlank() }?.let(::add)
                            addAll(raw.getStringList("custom_item_ids").onEach {
                                require(it.isNotBlank()) { "材料定義${key}のcustom_item_idsに空文字があります" }
                            })
                        },
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

    fun findBestRecipe(
        recipes: Map<String, BreweryRecipe>,
        inputItems: List<ItemStack>,
        countDifferencePenalty: Double = 5.0,
        unmatchedItemPenalty: Double = 10.0
    ): RecipeMatchResult? {
        if (inputItems.isEmpty()) return null

        var bestMatch: RecipeMatchResult? = null
        for (recipe in recipes.values) {
            val match = matchRecipe(
                recipe,
                inputItems,
                countDifferencePenalty.coerceAtLeast(0.0),
                unmatchedItemPenalty.coerceAtLeast(0.0)
            ) ?: continue
            if (bestMatch == null) {
                bestMatch = match
                continue
            }
            if (match.typeMatchCount > bestMatch.typeMatchCount) {
                bestMatch = match
            } else if (match.typeMatchCount == bestMatch.typeMatchCount &&
                (match.countDifferenceScore < bestMatch.countDifferenceScore ||
                    (match.countDifferenceScore == bestMatch.countDifferenceScore && match.recipe.id < bestMatch.recipe.id))
            ) {
                bestMatch = match
            }
        }
        return bestMatch
    }

    private fun matchRecipe(
        recipe: BreweryRecipe,
        inputItems: List<ItemStack>,
        countDifferencePenalty: Double,
        unmatchedItemPenalty: Double
    ): RecipeMatchResult? {
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
        if (typeMatchCount != recipe.fermentationIngredients.size) return null

        var countDiffScore = 0.0
        for ((ingredientKey, requiredCount) in recipe.fermentationIngredients) {
            val actual = counts[ingredientKey] ?: 0
            countDiffScore += abs(actual - requiredCount) * countDifferencePenalty
        }
        countDiffScore += unmatchedAmount * unmatchedItemPenalty

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

    fun resolveIngredientAmounts(
        ingredientKeys: Set<String>,
        inputItems: List<ItemStack>
    ): Map<String, Int> {
        val amounts = mutableMapOf<String, Int>()
        inputItems.forEach { item ->
            val key = ingredientKeys.sorted().firstOrNull { matchesIngredientKey(it, item) }
                ?: "__unmatched__:${CustomItemManager.identify(item)?.fullId ?: item.type.key}"
            amounts[key] = (amounts[key] ?: 0) + item.amount
        }
        return amounts
    }

    fun getFinalQualityIndex(quality: Double): Int = breweryQualityIndex(quality)
}
