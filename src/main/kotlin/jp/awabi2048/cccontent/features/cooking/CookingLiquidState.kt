package jp.awabi2048.cccontent.features.cooking

/** 釜へ投入できる液体の論理IDです。物理ブロックの水位とは分離して保存します。 */
object CookingLiquidIds {
    const val WATER = "water"
    const val SEA_WATER = "sea_water"
    const val SOY_MILK = "soy_milk"
    const val BITTERN = "bittern"

    const val SEA_WATER_BUCKET = "cooking.sea_water_bucket"
    const val SOY_MILK_BOTTLE = "cooking.soy_milk_bottle"
    const val NIGARI_BOTTLE = "cooking.nigari_bottle"
}

/**
 * 釜の容量内にある液体の構成です。
 *
 * Mapをそのまま持ち回らず、ここでID・量・容量を検証することで、保存データや
 * イベント経由の投入が不正な状態を作らないようにします。
 */
data class CookingLiquidContents(
    val amounts: Map<String, Int>
) {
    val total: Int = amounts.values.sum()

    init {
        require(amounts.keys.all { it.matches(Regex("[a-z0-9_]+")) }) {
            "Cooking liquid ids must use lowercase snake_case"
        }
        require(amounts.values.all { it > 0 }) { "Cooking liquid amounts must be positive" }
        require(total in 0..MAX_CAPACITY) { "Cooking liquid capacity must be between 0 and $MAX_CAPACITY" }
    }

    fun isEmpty(): Boolean = amounts.isEmpty()

    fun amount(liquidId: String): Int = amounts[liquidId] ?: 0

    fun containsNonWater(): Boolean = amounts.keys.any { it != CookingLiquidIds.WATER }

    fun plus(liquidId: String, amount: Int): CookingLiquidContents {
        require(amount > 0)
        val next = amounts.toMutableMap()
        next[liquidId] = (next[liquidId] ?: 0) + amount
        return of(next)
    }

    fun minus(liquidId: String, amount: Int): CookingLiquidContents {
        require(amount > 0)
        val current = amounts[liquidId] ?: return this
        val next = amounts.toMutableMap()
        if (current <= amount) next.remove(liquidId) else next[liquidId] = current - amount
        return of(next)
    }

    fun isPotentialInputFor(recipes: Collection<UnifiedLiquidCookingRecipe>): Boolean = recipes.any { recipe ->
        amounts.all { (liquidId, amount) -> amount <= (recipe.liquidInputs[liquidId] ?: 0) } &&
            recipe.liquidInputs.values.sum() <= MAX_CAPACITY
    }

    companion object {
        const val MAX_CAPACITY = 3

        fun empty(): CookingLiquidContents = CookingLiquidContents(emptyMap())

        fun of(amounts: Map<String, Int>): CookingLiquidContents = CookingLiquidContents(
            amounts.filterValues { it > 0 }.toSortedMap()
        )
    }
}

object CookingLiquidRecipeMatcher {
    fun find(
        contents: CookingLiquidContents,
        recipes: Collection<UnifiedLiquidCookingRecipe>
    ): UnifiedLiquidCookingRecipe? = recipes.firstOrNull { it.liquidInputs == contents.amounts }
}
