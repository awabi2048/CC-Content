package jp.awabi2048.cccontent.features.cooking

import kotlin.math.ceil

enum class CookingStation { CUTTING, PAN, CAULDRON, FURNACE, SMOKER, CRAFTING }
enum class CookingHeat { NORMAL, HIGH }
enum class CookingTier(val minimumCookLevel: Int) {
    BASIC(0), INTERMEDIATE(15), ADVANCED(35), TOP(50)
}
enum class CookingResultKind { ITEM, BOWL, BOTTLE }
enum class CookingProcessState {
    IDLE,
    PROCESSING_NORMAL,
    PROCESSING_FAILURE,
    PAUSED_NO_HEAT,
    PAUSED_WRONG_HEAT,
    READY_ITEM,
    READY_LIQUID,
    CANCELLED_RETURN
}

data class CookingRecipeDefinition(
    val id: String,
    val station: CookingStation,
    val group: String,
    val tier: CookingTier,
    val heat: CookingHeat?,
    val ingredients: Map<String, Int>,
    val waterUnits: Int,
    val durationSeconds: Int,
    val experience: Long,
    val resultKind: CookingResultKind
)

data class CookingMatchSettings(
    val maximumExcessRatioPerIngredient: Double = 0.50,
    val maximumUnknownRatio: Double = 0.20,
    val maximumTotalError: Double = 0.30,
    val ambiguityMargin: Double = 0.10
) {
    init {
        require(maximumExcessRatioPerIngredient >= 0.0)
        require(maximumUnknownRatio >= 0.0)
        require(maximumTotalError >= 0.0)
        require(ambiguityMargin >= 0.0)
    }
}

data class CookingRecipeMatch(
    val recipe: CookingRecipeDefinition,
    val scale: Int,
    val rawError: Double,
    val effectiveError: Double,
    val exact: Boolean,
    val heatMatches: Boolean
) {
    val score: Double get() = 1.0 - effectiveError
}

sealed interface CookingMatchResult {
    data object NoMatch : CookingMatchResult
    data class Ambiguous(val first: CookingRecipeMatch, val second: CookingRecipeMatch) : CookingMatchResult
    data class Selected(val match: CookingRecipeMatch) : CookingMatchResult
}

/** 第14章の候補評価と優先順位を副作用なしで実行する。 */
object CookingRecipeMatcher {
    fun select(
        recipes: Collection<CookingRecipeDefinition>,
        station: CookingStation,
        actualIngredients: Map<String, Int>,
        currentHeat: CookingHeat?,
        currentWater: Int,
        mismatchPenaltyReduction: Double,
        settings: CookingMatchSettings = CookingMatchSettings()
    ): CookingMatchResult {
        require(actualIngredients.values.all { it > 0 })
        require(mismatchPenaltyReduction in 0.0..1.0)
        val matches = recipes.asSequence()
            .filter { it.station == station }
            .flatMap { recipe -> scales(recipe, station, currentWater).asSequence().map { recipe to it } }
            .mapNotNull { (recipe, scale) ->
                evaluate(recipe, scale, actualIngredients, currentHeat, mismatchPenaltyReduction, settings)
            }
            .sortedWith(
                compareBy<CookingRecipeMatch> { priority(it) }
                    .thenByDescending(CookingRecipeMatch::score)
                    .thenBy { it.recipe.id }
            )
            .toList()
        if (matches.isEmpty()) return CookingMatchResult.NoMatch
        val first = matches.first()
        val second = matches.drop(1).firstOrNull { priority(it) == priority(first) }
        if (second != null && first.score - second.score < settings.ambiguityMargin) {
            return CookingMatchResult.Ambiguous(first, second)
        }
        return CookingMatchResult.Selected(first)
    }

    private fun scales(recipe: CookingRecipeDefinition, station: CookingStation, currentWater: Int): IntRange = when (station) {
        CookingStation.PAN -> 1..5
        CookingStation.CAULDRON -> {
            if (recipe.waterUnits !in 1..3 || currentWater !in 1..3 || currentWater % recipe.waterUnits != 0) IntRange.EMPTY
            else (currentWater / recipe.waterUnits).let { if (it in 1..3) it..it else IntRange.EMPTY }
        }
        CookingStation.CUTTING -> 1..64
        else -> 1..1
    }

    private fun evaluate(
        recipe: CookingRecipeDefinition,
        scale: Int,
        actual: Map<String, Int>,
        currentHeat: CookingHeat?,
        mismatchPenaltyReduction: Double,
        settings: CookingMatchSettings
    ): CookingRecipeMatch? {
        val required = recipe.ingredients.mapValues { (_, amount) -> amount * scale }
        if (required.isEmpty()) return null
        val exact = actual == required
        if (recipe.station == CookingStation.CUTTING) {
            if (!exact) return null
            return CookingRecipeMatch(recipe, scale, 0.0, 0.0, true, true)
        }
        required.forEach { (id, amount) ->
            val supplied = actual[id] ?: return null
            if (supplied < amount || supplied > ceil(amount * (1.0 + settings.maximumExcessRatioPerIngredient)).toInt()) {
                return null
            }
        }
        val requiredTotal = required.values.sum()
        val excess = required.entries.sumOf { (id, amount) -> (actual[id] ?: 0) - amount }
        val unknown = actual.filterKeys { it !in required }.values.sum()
        if (unknown.toDouble() / requiredTotal > settings.maximumUnknownRatio) return null
        val rawError = (excess + 2.0 * unknown) / requiredTotal
        val effectiveError = rawError * (1.0 - mismatchPenaltyReduction)
        if (effectiveError > settings.maximumTotalError) return null
        return CookingRecipeMatch(
            recipe,
            scale,
            rawError,
            effectiveError,
            exact,
            recipe.heat == null || recipe.heat == currentHeat
        )
    }

    private fun priority(match: CookingRecipeMatch): Int = when {
        match.exact && match.heatMatches -> 0
        match.exact -> 1
        match.heatMatches -> 2
        else -> 3
    }
}
