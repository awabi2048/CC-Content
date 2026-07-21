package jp.awabi2048.cccontent.features.cooking

import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import kotlin.math.abs

data class UnifiedCookingSettings(
    val matching: CookingMatchSettings,
    val panMaxScale: Int,
    val cauldronMaxScale: Int,
    val flushIntervalTicks: Long
)

enum class CookingIngredientMatcherType { CUSTOM_ITEM_ID, FISH_ID, RESOURCE_ID, MATERIAL }

data class CookingIngredientMatcher(
    val type: CookingIngredientMatcherType,
    val value: String
)

data class CookingContainerRemainder(val material: Material, val amount: Int)

data class UnifiedCookingIngredient(
    val id: String,
    val matcher: CookingIngredientMatcher,
    val displayNameKey: String,
    val containerRemainder: CookingContainerRemainder?
)

enum class CuttingFoodClass { DELICATE, FIRM, TOUGH }
enum class CookingIntermediateStage { PRIMARY, SECONDARY }

data class CuttingRecipeDefinition(
    val id: String,
    val inputIngredientId: String,
    val outputCustomItemId: String,
    val outputAmount: Int,
    val foodClass: CuttingFoodClass,
    val baseDurability: Int,
    val stage: CookingIntermediateStage,
    val depth: Int
)

data class CookingEffectDefinition(val type: String, val amplifier: Int, val durationSeconds: Int)

data class UnifiedCookingResult(
    val customItemId: String,
    val baseMaterial: Material,
    val itemModel: NamespacedKey,
    val container: Material?,
    val liquidPane: Material?,
    val nutrition: Int,
    val saturationModifier: Float,
    val alwaysEat: Boolean,
    val effects: List<CookingEffectDefinition>,
    val maxStackSize: Int,
    val amountPerScale: Int
)

data class UnifiedCookingRecipe(
    val definition: CookingRecipeDefinition,
    val result: UnifiedCookingResult,
    val failureResult: UnifiedCookingResult
)

data class UnifiedCookingConfiguration(
    val settings: UnifiedCookingSettings,
    val ingredients: Map<String, UnifiedCookingIngredient>,
    val cuttingRecipes: Map<String, CuttingRecipeDefinition>,
    val recipes: Map<String, UnifiedCookingRecipe>
)

object UnifiedCookingConfigurationLoader {
    private val forbiddenRecipeFields = setOf(
        "servings", "servings_per_unit", "completion", "quality", "score",
        "intoxication_reduction"
    )

    @JvmStatic
    fun load(dataFolder: File): UnifiedCookingConfiguration {
        val settings = loadSettings(File(dataFolder, "config/cooking/config.yml"))
        val ingredients = loadIngredients(File(dataFolder, "config/cooking/ingredients.yml"))
        val cutting = loadCutting(File(dataFolder, "config/cooking/cutting.yml"), ingredients)
        val recipes = loadRecipes(File(dataFolder, "config/cooking/recipe.yml"), ingredients)
        validateRecipeConflicts(recipes.values.map(UnifiedCookingRecipe::definition))
        return UnifiedCookingConfiguration(settings, ingredients, cutting, recipes)
    }

    @JvmStatic
    fun loadSettings(file: File): UnifiedCookingSettings {
        val root = yaml(file)
        requireExactInt(root, "config_version", 3, file)
        require(root.get("enabled") is Boolean) { "${file.path}.enabled must be a boolean" }
        val matching = requireSection(root, "matching", file)
        val equipment = requireSection(root, "equipment", file)
        val state = requireSection(root, "state", file)
        return UnifiedCookingSettings(
            CookingMatchSettings(
                requireDouble(matching, "maximum_excess_ratio_per_ingredient", file),
                requireDouble(matching, "maximum_unknown_ratio", file),
                requireDouble(matching, "maximum_total_error", file),
                requireDouble(matching, "ambiguity_margin", file)
            ),
            requireInt(equipment, "pan_max_scale", file).also { require(it == 5) },
            requireInt(equipment, "cauldron_max_scale", file).also { require(it == 3) },
            requireInt(state, "flush_interval_ticks", file).toLong().also { require(it > 0) }
        )
    }

    @JvmStatic
    fun loadIngredients(file: File): Map<String, UnifiedCookingIngredient> {
        val root = yaml(file)
        requireExactInt(root, "config_version", 2, file)
        val section = requireSection(root, "ingredients", file)
        return section.getKeys(false).associateWith { id ->
            val path = "ingredients.$id"
            val ingredient = requireSection(section, id, file)
            val matcher = requireSection(ingredient, "matcher", file)
            val keys = matcher.getKeys(false)
            require(keys.size == 1) { "${file.path}.$path.matcher must contain exactly one matcher" }
            val matcherKey = keys.single()
            val type = when (matcherKey) {
                "custom_item_id" -> CookingIngredientMatcherType.CUSTOM_ITEM_ID
                "fish_id" -> CookingIngredientMatcherType.FISH_ID
                "resource_id" -> CookingIngredientMatcherType.RESOURCE_ID
                "material" -> CookingIngredientMatcherType.MATERIAL
                else -> error("${file.path}.$path.matcher.$matcherKey is unsupported")
            }
            val value = requireString(matcher, matcherKey, file)
            if (type == CookingIngredientMatcherType.MATERIAL) require(Material.matchMaterial(value) != null)
            val remainder = ingredient.getConfigurationSection("container_remainder")?.let { raw ->
                CookingContainerRemainder(
                    requireMaterial(raw, "material", file),
                    requireInt(raw, "amount", file).also { require(it > 0) }
                )
            }
            UnifiedCookingIngredient(
                id,
                CookingIngredientMatcher(type, value),
                requireString(ingredient, "display_name_key", file),
                remainder
            )
        }.also { require(it.isNotEmpty()) { "${file.path}.ingredients must not be empty" } }
    }

    @JvmStatic
    fun loadCutting(
        file: File,
        ingredients: Map<String, UnifiedCookingIngredient>
    ): Map<String, CuttingRecipeDefinition> {
        val root = yaml(file)
        requireExactInt(root, "config_version", 2, file)
        val section = requireSection(root, "recipes", file)
        return section.getKeys(false).associateWith { id ->
            val recipe = requireSection(section, id, file)
            val input = requireString(recipe, "input", file)
            require(input in ingredients) { "${file.path}.recipes.$id.input is unknown: $input" }
            val output = requireSection(recipe, "output", file)
            val stage = requireSection(recipe, "stage", file)
            CuttingRecipeDefinition(
                id,
                input,
                requireString(output, "custom_item_id", file),
                requireInt(output, "amount", file).also { require(it > 0) },
                enumValue<CuttingFoodClass>(recipe, "food_class", file),
                requireInt(recipe, "base_durability", file).also { require(it > 0) },
                enumValue<CookingIntermediateStage>(stage, "type", file),
                requireInt(stage, "depth", file).also { require(it in 1..2) }
            )
        }
    }

    @JvmStatic
    fun loadRecipes(
        file: File,
        ingredients: Map<String, UnifiedCookingIngredient>
    ): Map<String, UnifiedCookingRecipe> {
        val root = yaml(file)
        requireExactInt(root, "config_version", 3, file)
        val section = requireSection(root, "recipes", file)
        return section.getKeys(false).associateWith { id ->
            val raw = requireSection(section, id, file)
            forbiddenRecipeFields.forEach { field ->
                require(!raw.contains(field)) { "${file.path}.recipes.$id.$field is forbidden" }
            }
            val station = enumValue<CookingStation>(raw, "equipment", file)
            require(station == CookingStation.PAN || station == CookingStation.CAULDRON) {
                "${file.path}.recipes.$id equipment must be PAN or CAULDRON"
            }
            val heat = enumValue<CookingHeat>(raw, "heat", file)
            val ingredientAmounts = requireSection(raw, "ingredients", file).getKeys(false).associateWith { ingredientId ->
                require(ingredientId in ingredients) { "${file.path}.recipes.$id uses unknown ingredient $ingredientId" }
                requireInt(raw, "ingredients.$ingredientId", file).also { require(it > 0) }
            }
            require(ingredientAmounts.isNotEmpty() && ingredientAmounts.size <= 5)
            val water = requireInt(raw, "water_units", file)
            require(if (station == CookingStation.CAULDRON) water in 1..3 else water == 0)
            val result = loadResult(requireSection(raw, "result", file), file, false)
            val failure = loadResult(requireSection(raw, "failure_result", file), file, true)
            val kind = enumValue<CookingResultKind>(requireSection(raw, "result", file), "kind", file)
            UnifiedCookingRecipe(
                CookingRecipeDefinition(
                    id,
                    station,
                    requireString(raw, "group", file),
                    enumValue(raw, "tier", file),
                    heat,
                    ingredientAmounts,
                    water,
                    requireInt(raw, "duration_seconds", file).also { require(it > 0) },
                    requireLong(raw, "exp", file).also { require(it >= 0) },
                    kind
                ),
                result,
                failure
            )
        }.also { require(it.isNotEmpty()) { "${file.path}.recipes must not be empty" } }
    }

    @JvmStatic
    fun validateRecipeConflicts(recipes: Collection<CookingRecipeDefinition>) {
        val signatures = mutableMapOf<String, String>()
        recipes.forEach { recipe ->
            val values = recipe.ingredients.values + recipe.waterUnits
            val divisor = values.filter { it > 0 }.reduce(::gcd)
            val normalized = recipe.ingredients.toSortedMap().entries.joinToString(",") { (id, amount) ->
                "$id=${amount / divisor}"
            }
            val signature = "${recipe.station}:${recipe.heat}:$normalized:water=${recipe.waterUnits / divisor}"
            val previous = signatures.putIfAbsent(signature, recipe.id)
            require(previous == null) { "Cooking recipe conflict: $previous and ${recipe.id}" }
        }
    }

    private fun loadResult(section: ConfigurationSection, file: File, failure: Boolean): UnifiedCookingResult {
        val customId = requireString(section, "custom_item_id", file)
        val base = requireMaterial(section, "base_material", file)
        val model = NamespacedKey.fromString(requireString(section, "item_model", file))
            ?: error("${file.path} result item_model is invalid")
        val container = section.getString("container")?.let { Material.matchMaterial(it) ?: error("invalid container $it") }
        val pane = section.getString("liquid_pane")?.let { Material.matchMaterial(it) ?: error("invalid liquid_pane $it") }
        val effects = section.getStringList("effects").map { encoded ->
            val parts = encoded.split('/')
            require(parts.size == 3) { "${file.path} effect must be POTION/AMPLIFIER/DURATION_SECONDS" }
            CookingEffectDefinition(parts[0], parts[1].toInt(), parts[2].toInt().also { require(it > 0) })
        }
        return UnifiedCookingResult(
            customId,
            base,
            model,
            container,
            pane,
            if (failure) 0 else section.getInt("nutrition", 0),
            if (failure) 0f else section.getDouble("saturation_modifier", 0.0).toFloat(),
            !failure && section.getBoolean("always_eat", false),
            if (failure) emptyList() else effects,
            section.getInt("max_stack_size", if (container == null) 16 else 1).also { require(it in 1..64) },
            section.getInt("amount_per_scale", 1).also { require(it > 0) }
        )
    }

    private fun gcd(a: Int, b: Int): Int {
        var x = abs(a)
        var y = abs(b)
        while (y != 0) { val next = x % y; x = y; y = next }
        return x.coerceAtLeast(1)
    }

    private fun yaml(file: File): YamlConfiguration {
        require(file.isFile) { "Missing cooking configuration: ${file.path}" }
        return YamlConfiguration.loadConfiguration(file)
    }

    private fun requireSection(parent: ConfigurationSection, path: String, file: File): ConfigurationSection =
        parent.getConfigurationSection(path) ?: error("${file.path}.$path must be a section")

    private fun requireString(parent: ConfigurationSection, path: String, file: File): String =
        (parent.get(path) as? String)?.takeIf(String::isNotBlank)
            ?: error("${file.path}.$path must be a non-empty string")

    private fun requireInt(parent: ConfigurationSection, path: String, file: File): Int {
        val value = parent.get(path)
        require(value is Number && value.toDouble() == value.toInt().toDouble()) { "${file.path}.$path must be an integer" }
        return value.toInt()
    }

    private fun requireLong(parent: ConfigurationSection, path: String, file: File): Long {
        val value = parent.get(path)
        require(value is Number && value.toDouble() == value.toLong().toDouble()) { "${file.path}.$path must be an integer" }
        return value.toLong()
    }

    private fun requireDouble(parent: ConfigurationSection, path: String, file: File): Double {
        val value = parent.get(path)
        require(value is Number) { "${file.path}.$path must be a number" }
        return value.toDouble()
    }

    private fun requireExactInt(parent: ConfigurationSection, path: String, expected: Int, file: File) {
        require(requireInt(parent, path, file) == expected) { "${file.path}.$path must be $expected" }
    }

    private fun requireMaterial(parent: ConfigurationSection, path: String, file: File): Material =
        Material.matchMaterial(requireString(parent, path, file)) ?: error("${file.path}.$path is not a material")

    private inline fun <reified T : Enum<T>> enumValue(parent: ConfigurationSection, path: String, file: File): T =
        runCatching { enumValueOf<T>(requireString(parent, path, file).uppercase()) }
            .getOrElse { error("${file.path}.$path is invalid") }
}
