package jp.awabi2048.cccontent.features.cooking

import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

internal data class BreweryPreparationDefinition(
    val preparationId: String,
    val familyId: String,
    val group: String,
    val recipe: CookingRecipeDefinition,
    val liquidPane: Material,
    val failureResultId: String
)

internal object BreweryPreparationConfigurationLoader {
    fun load(dataFolder: File, ingredients: Map<String, UnifiedCookingIngredient>): Map<String, BreweryPreparationDefinition> {
        val file = File(dataFolder, "config/brewery/recipes.yml")
        require(file.isFile) { "Missing brewery configuration: ${file.path}" }
        val root = YamlConfiguration().also {
            it.options().pathSeparator('/')
            it.load(file)
        }
        require(root.getInt("config_version", -1) == 3)
        val section = root.getConfigurationSection("preparations")
            ?: error("${file.path}.preparations is required")
        val ingredientIds = ingredients.values.associateBy { ingredient ->
            when (ingredient.matcher.type) {
                CookingIngredientMatcherType.MATERIAL -> "minecraft:${ingredient.matcher.value.lowercase()}"
                CookingIngredientMatcherType.RESOURCE_ID -> "resource.${ingredient.matcher.value}"
                CookingIngredientMatcherType.CUSTOM_ITEM_ID -> ingredient.matcher.value
                CookingIngredientMatcherType.FISH_ID -> "fishing.fish_${ingredient.matcher.value}"
            }
        }
        return section.getKeys(false).associateWith { preparationId ->
            val raw = requireNotNull(section.getConfigurationSection(preparationId))
            val familyId = requireNotNull(raw.getString("family"))
            val group = requireNotNull(raw.getString("group"))
            val ingredientSection = requireNotNull(raw.getConfigurationSection("ingredients"))
            val configuredIngredients = ingredientSection.getKeys(false).associate { canonical ->
                    val ingredient = ingredientIds[canonical]
                        ?: error("${file.path}.preparations.$preparationId uses unknown ingredient $canonical")
                    ingredient.id to ingredientSection.getInt(canonical).also { require(it > 0) }
                }
            val heat = CookingHeat.valueOf(requireNotNull(raw.getString("heat")))
            val recipe = CookingRecipeDefinition(
                "brewery:$preparationId", CookingStation.CAULDRON, group, tier(group), heat,
                configuredIngredients, 3, raw.getInt("duration_seconds").also { require(it > 0) },
                0, CookingResultKind.BOTTLE
            )
            BreweryPreparationDefinition(
                preparationId, familyId, group, recipe,
                Material.valueOf(requireNotNull(raw.getString("liquid_pane"))),
                requireNotNull(raw.getString("failure_result"))
            )
        }.also { require(it.size == 26) }
    }

    private fun tier(group: String): CookingTier = when (group) {
        "BASIC" -> CookingTier.BASIC
        "INTERMEDIATE", "HERBAL", "WILD_AND_FUNGI" -> CookingTier.INTERMEDIATE
        "ADVANCED" -> CookingTier.ADVANCED
        "TOP" -> CookingTier.TOP
        else -> error("Unknown brewery group: $group")
    }
}
