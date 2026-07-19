package jp.awabi2048.cccontent.features.processing

import kotlin.math.abs

enum class ProcessingFirePower {
    NORMAL,
    HIGH
}

data class ProcessingRecipe(
    val id: String,
    val process: String,
    val firePower: ProcessingFirePower,
    val ingredients: Map<String, Int>
) {
    init {
        require(id.isNotBlank()) { "recipe id must not be blank" }
        require(process.isNotBlank()) { "recipe process must not be blank" }
        require(ingredients.isNotEmpty()) { "recipe ingredients must not be empty" }
        require(ingredients.keys.none(String::isBlank)) { "ingredient id must not be blank" }
        require(ingredients.values.all { it > 0 }) { "ingredient amount must be positive" }
    }
}

data class RecipeMatchPolicy(
    val maximumBatchSize: Int,
    val allowedExcessRatio: Double,
    val maximumUnmatchedAmount: Int,
    val ambiguityThreshold: Double
) {
    init {
        require(maximumBatchSize > 0) { "maximum batch size must be positive" }
        require(allowedExcessRatio >= 0.0) { "allowed excess ratio must not be negative" }
        require(maximumUnmatchedAmount >= 0) { "maximum unmatched amount must not be negative" }
        require(ambiguityThreshold >= 0.0) { "ambiguity threshold must not be negative" }
    }
}

data class RecipeCandidate(
    val recipe: ProcessingRecipe,
    val batches: Int,
    val ratioDistance: Double,
    val unmatchedAmount: Int,
    val consumedAmounts: Map<String, Int>
) {
    val score: Double = ratioDistance + unmatchedAmount.toDouble()
}

sealed interface RecipeMatchResult {
    data class Matched(val candidate: RecipeCandidate) : RecipeMatchResult
    data object NoMatch : RecipeMatchResult
    data class Ambiguous(
        val best: RecipeCandidate,
        val second: RecipeCandidate
    ) : RecipeMatchResult
}

object ProcessingRecipeMatcher {
    fun validateNoConflicts(recipes: Collection<ProcessingRecipe>) {
        val duplicate = recipes.groupBy(::conflictKey).entries.firstOrNull { it.value.size > 1 } ?: return
        val ids = duplicate.value.map(ProcessingRecipe::id).sorted().joinToString(", ")
        error("conflicting processing recipes: $ids")
    }

    fun match(
        recipes: Collection<ProcessingRecipe>,
        process: String,
        actualFirePower: ProcessingFirePower,
        inputAmounts: Map<String, Int>,
        policy: RecipeMatchPolicy
    ): RecipeMatchResult {
        if (inputAmounts.isEmpty() || inputAmounts.values.any { it <= 0 }) {
            return RecipeMatchResult.NoMatch
        }
        val candidates = recipes.asSequence()
            .filter { it.process == process && it.firePower == actualFirePower }
            .mapNotNull { candidate(it, inputAmounts, policy) }
            .sortedWith(
                compareBy<RecipeCandidate>(
                    { it.recipe.ingredients.keys != inputAmounts.keys },
                    RecipeCandidate::ratioDistance,
                    RecipeCandidate::unmatchedAmount,
                    { it.recipe.id }
                )
            )
            .toList()
        val best = candidates.firstOrNull() ?: return RecipeMatchResult.NoMatch
        val second = candidates.getOrNull(1)
        if (second != null && abs(second.score - best.score) < policy.ambiguityThreshold) {
            return RecipeMatchResult.Ambiguous(best, second)
        }
        return RecipeMatchResult.Matched(best)
    }

    private fun candidate(
        recipe: ProcessingRecipe,
        inputAmounts: Map<String, Int>,
        policy: RecipeMatchPolicy
    ): RecipeCandidate? {
        val safeBatches = recipe.ingredients.entries
            .minOf { (id, required) -> (inputAmounts[id] ?: 0) / required }
            .coerceAtMost(policy.maximumBatchSize)
        if (safeBatches <= 0) return null

        val expected = recipe.ingredients.mapValues { (_, amount) -> amount * safeBatches }
        val unmatchedAmount = inputAmounts.entries
            .filter { it.key !in recipe.ingredients }
            .sumOf(Map.Entry<String, Int>::value)
        if (unmatchedAmount > policy.maximumUnmatchedAmount) return null

        val expectedTotal = expected.values.sum().coerceAtLeast(1)
        val definedActualTotal = inputAmounts.entries
            .filter { it.key in recipe.ingredients }
            .sumOf(Map.Entry<String, Int>::value)
        val excess = (definedActualTotal - expectedTotal).coerceAtLeast(0)
        if (excess.toDouble() / expectedTotal > policy.allowedExcessRatio) return null

        val expectedRatios = normalizedRatios(expected)
        val actualDefined = recipe.ingredients.keys.associateWith { inputAmounts[it] ?: 0 }
        val actualRatios = normalizedRatios(actualDefined)
        val ratioDistance = recipe.ingredients.keys.sumOf { id ->
            abs((actualRatios[id] ?: 0.0) - (expectedRatios[id] ?: 0.0))
        }
        return RecipeCandidate(
            recipe = recipe,
            batches = safeBatches,
            ratioDistance = ratioDistance,
            unmatchedAmount = unmatchedAmount,
            consumedAmounts = inputAmounts.toMap()
        )
    }

    private fun conflictKey(recipe: ProcessingRecipe): String {
        val divisor = recipe.ingredients.values.reduce(::greatestCommonDivisor)
        val ratio = recipe.ingredients.toSortedMap()
            .entries
            .joinToString("|") { (id, amount) -> "$id:${amount / divisor}" }
        return "${recipe.process};${recipe.firePower};$ratio"
    }

    private fun normalizedRatios(amounts: Map<String, Int>): Map<String, Double> {
        val total = amounts.values.sum().coerceAtLeast(1).toDouble()
        return amounts.mapValues { (_, amount) -> amount / total }
    }

    private fun greatestCommonDivisor(left: Int, right: Int): Int {
        var a = abs(left)
        var b = abs(right)
        while (b != 0) {
            val next = a % b
            a = b
            b = next
        }
        return a.coerceAtLeast(1)
    }
}
