package jp.awabi2048.cccontent.features.cooking

import com.awabi2048.ccsystem.api.localization.LocalizationKey
import org.bukkit.Material
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
    val displayName: CookingDisplayName,
    val containerRemainder: CookingContainerRemainder?
)

/** CC catalog と Minecraft クライアント翻訳を混同しない表示名契約です。 */
sealed interface CookingDisplayName {
    data class Localized(val key: LocalizationKey<String>) : CookingDisplayName
    data class MinecraftTranslation(val key: String) : CookingDisplayName
}

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

data class UnifiedLiquidOutput(
    val liquidId: String,
    val customItemId: String,
    val container: CookingLiquidContainerDefinition
)

data class UnifiedLiquidCookingRecipe(
    val id: String,
    val heat: CookingHeat,
    val liquidInputs: Map<String, Int>,
    val durationSeconds: Int,
    val experience: Long,
    val result: UnifiedCookingResult,
    val residualLiquids: Map<String, Int>,
    val liquidOutputs: Map<String, UnifiedLiquidOutput>,
    val processingLevels: List<Int> = emptyList(),
    /** trueの場合、液体を最初に1容器回収する操作へ固形成果物も同梱します。 */
    val collectSolidResultWithLiquid: Boolean = false
) {
    val stationRecipe: CookingRecipeDefinition
        get() = CookingRecipeDefinition(
            id,
            CookingStation.CAULDRON,
            "LIQUID",
            CookingTier.BASIC,
            heat,
            emptyMap(),
            0,
            durationSeconds,
            experience,
            CookingResultKind.ITEM
        )

    val snapshot: CookingRecipeSnapshot
        get() = CookingRecipeSnapshot(
            result.customItemId,
            result.amountPerScale,
            result.customItemId,
            durationSeconds,
            heat,
            0,
            CookingResultKind.ITEM,
            null,
            null,
            experience
        )
}

data class UnifiedCookingConfiguration(
    val settings: UnifiedCookingSettings,
    val ingredients: Map<String, UnifiedCookingIngredient>,
    val cuttingRecipes: Map<String, CuttingRecipeDefinition>,
    val recipes: Map<String, UnifiedCookingRecipe>,
    val liquidRecipes: Map<String, UnifiedLiquidCookingRecipe> = emptyMap()
) {
    constructor(
        settings: UnifiedCookingSettings,
        ingredients: Map<String, UnifiedCookingIngredient>,
        cuttingRecipes: Map<String, CuttingRecipeDefinition>,
        recipes: Map<String, UnifiedCookingRecipe>
    ) : this(settings, ingredients, cuttingRecipes, recipes, emptyMap())
}

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
        val liquidRecipes = loadLiquidRecipes(File(dataFolder, "config/cooking/liquid_recipes.yml"))
        validateRecipeConflicts(recipes.values.map(UnifiedCookingRecipe::definition))
        validateLiquidRecipeConflicts(liquidRecipes.values)
        return UnifiedCookingConfiguration(settings, ingredients, cutting, recipes, liquidRecipes)
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
                parseDisplayName(requireString(ingredient, "display_name_key", file), file, path),
                remainder
            )
        }.also { require(it.isNotEmpty()) { "${file.path}.ingredients must not be empty" } }
    }

    private fun parseDisplayName(raw: String, file: File, path: String): CookingDisplayName = when {
        raw.startsWith("item.minecraft.") -> CookingDisplayName.MinecraftTranslation(raw)
        else -> runCatching {
            CookingDisplayName.Localized(
                jp.awabi2048.cccontent.util.ContentLocalizationKeys.text(
                    raw,
                    "custom_items.cooking.",
                    "custom_items.resource.",
                    "fishing.catalog.item.",
                ),
            )
        }.getOrElse { error ->
            throw IllegalArgumentException("${file.path}.$path.display_name_key is invalid: $raw", error)
        }
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
    fun loadLiquidRecipes(file: File): Map<String, UnifiedLiquidCookingRecipe> {
        val root = yaml(file)
        requireExactInt(root, "config_version", 1, file)
        val section = requireSection(root, "recipes", file)
        return section.getKeys(false).associate { rawId ->
            val raw = requireSection(section, rawId, file)
            val id = "liquid:$rawId"
            val inputSection = requireSection(raw, "liquid_inputs", file)
            val inputs = inputSection.getKeys(false).associateWith { liquidId ->
                requireStringId(liquidId, "${file.path}.recipes.$rawId.liquid_inputs")
                require(liquidId in CookingLiquidPresentation.knownLiquidIds) {
                    "${file.path}.recipes.$rawId.liquid_inputs contains an unknown liquid: $liquidId"
                }
                requireInt(inputSection, liquidId, file).also { require(it > 0) }
            }
            require(inputs.isNotEmpty()) { "${file.path}.recipes.$rawId.liquid_inputs must not be empty" }
            require(inputs.values.sum() in 1..CookingLiquidContents.MAX_CAPACITY) {
                "${file.path}.recipes.$rawId.liquid_inputs exceeds cauldron capacity"
            }
            val result = loadResult(requireSection(raw, "result", file), file, false)
            require(result.container == null) { "${file.path}.recipes.$rawId.result must be a solid item" }
            val residual = optionalAmountMap(raw.getConfigurationSection("residual_liquids"), file, rawId)
            residual.keys.forEach { liquidId ->
                require(liquidId in CookingLiquidPresentation.knownLiquidIds) {
                    "${file.path}.recipes.$rawId.residual_liquids contains an unknown liquid: $liquidId"
                }
            }
            require(residual.size <= 1) {
                "${file.path}.recipes.$rawId supports at most one collectible residual liquid"
            }
            require(residual.values.sum() <= CookingLiquidContents.MAX_CAPACITY) {
                "${file.path}.recipes.$rawId.residual_liquids exceeds cauldron capacity"
            }
            residual.keys.forEach { liquidId ->
                require(
                    CookingLiquidPresentation.isCollectable(
                        CookingLiquidContents.of(mapOf(liquidId to 1))
                    )
                ) {
                    "${file.path}.recipes.$rawId.residual_liquids contains a non-collectable liquid: $liquidId"
                }
            }
            val collectSolidResultWithLiquid = raw.getBoolean("collect_result_with_liquid", false)
            require(!collectSolidResultWithLiquid || residual.values.sum() == 1) {
                "${file.path}.recipes.$rawId.collect_result_with_liquid requires exactly one residual unit"
            }
            val outputSection = raw.getConfigurationSection("liquid_outputs")
            val outputs = outputSection?.getKeys(false)?.associateWith { liquidId ->
                require(liquidId in residual) {
                    "${file.path}.recipes.$rawId.liquid_outputs contains a non-residual liquid: $liquidId"
                }
                val output = requireSection(outputSection, liquidId, file)
                UnifiedLiquidOutput(
                    liquidId,
                    requireString(output, "custom_item_id", file),
                    CookingLiquidContainers.requireMaterial(requireMaterial(output, "container", file)).also { actual ->
                        CookingLiquidPresentation.containerFor(liquidId)?.let { expected ->
                            require(actual == expected) {
                                "${file.path}.recipes.$rawId.liquid_outputs.$liquidId must use ${expected.material.name}"
                            }
                        }
                    },
                )
            }.orEmpty()
            residual.keys.forEach { liquidId ->
                require(liquidId in outputs) {
                    "${file.path}.recipes.$rawId is missing liquid_outputs.$liquidId"
                }
            }
            val levels = raw.getIntegerList("processing_levels")
            require(levels.isEmpty() || levels.size == 3) {
                "${file.path}.recipes.$rawId.processing_levels must contain exactly three levels"
            }
            // processing_levelsはメニュー量ではなく、バニラ釜の視覚演出用の物理水位です。
            require(levels.all { it in 1..CookingLiquidVolume.VANILLA_CAULDRON_LEVELS }) {
                "${file.path}.recipes.$rawId.processing_levels contains an invalid level"
            }
            id to UnifiedLiquidCookingRecipe(
                id,
                enumValue(raw, "heat", file),
                inputs,
                requireInt(raw, "duration_seconds", file).also { require(it > 0) },
                requireLong(raw, "exp", file).also { require(it >= 0) },
                result,
                residual,
                outputs,
                levels,
                collectSolidResultWithLiquid
            )
        }.also { require(it.isNotEmpty()) { "${file.path}.recipes must not be empty" } }
    }

    @JvmStatic
    fun validateLiquidRecipeConflicts(recipes: Collection<UnifiedLiquidCookingRecipe>) {
        val duplicateInputs = recipes.groupBy(UnifiedLiquidCookingRecipe::liquidInputs)
            .filterValues { it.size > 1 }
        require(duplicateInputs.isEmpty()) {
            "Cooking liquid recipe conflict: ${duplicateInputs.values.flatten().map(UnifiedLiquidCookingRecipe::id)}"
        }
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

    private fun optionalAmountMap(
        section: ConfigurationSection?,
        file: File,
        recipeId: String
    ): Map<String, Int> {
        val actual = section ?: return emptyMap()
        return actual.getKeys(false).associateWith { liquidId ->
            requireStringId(liquidId, "${file.path}.recipes.$recipeId.residual_liquids")
            requireInt(actual, liquidId, file).also { require(it > 0) }
        }
    }

    private fun requireStringId(value: String, path: String) {
        require(value.matches(Regex("[a-z0-9_]+"))) { "$path contains invalid liquid id: $value" }
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
