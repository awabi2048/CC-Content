package jp.awabi2048.cccontent.features.cooking

import kotlin.math.roundToInt

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
 * 液体量の正準単位です。
 *
 * 釜の状態とメニュー表示は5単位を満容量とし、1単位を200mBとして扱います。
 * バニラ釜の水位は3段階しかないため、そこへの反映だけは別の視覚変換を通します。
 */
object CookingLiquidVolume {
    const val UNITS_PER_CAULDRON = 5
    const val MILLIBUCKETS_PER_UNIT = 200
    const val CAULDRON_CAPACITY_MILLIBUCKETS = UNITS_PER_CAULDRON * MILLIBUCKETS_PER_UNIT
    const val VANILLA_CAULDRON_LEVELS = 3

    @JvmStatic
    fun toMillibuckets(units: Int): Int {
        require(units in 0..UNITS_PER_CAULDRON)
        return units * MILLIBUCKETS_PER_UNIT
    }

    /** 論理量を、プレイヤーに見えるバニラ釜の水位へ変換します。 */
    @JvmStatic
    fun toVanillaLevel(units: Int): Int {
        require(units in 0..UNITS_PER_CAULDRON)
        if (units == 0) return 0
        return ((units * VANILLA_CAULDRON_LEVELS) + UNITS_PER_CAULDRON - 1) /
            UNITS_PER_CAULDRON
    }

    /** 外部操作で変化したバニラ水位を、論理量へ戻します。 */
    @JvmStatic
    fun fromVanillaLevel(level: Int): Int {
        require(level in 0..VANILLA_CAULDRON_LEVELS)
        if (level == 0) return 0
        return (level * UNITS_PER_CAULDRON.toDouble() / VANILLA_CAULDRON_LEVELS)
            .roundToInt()
            .coerceIn(1, UNITS_PER_CAULDRON)
    }
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

    fun canMinus(amountsToRemove: Map<String, Int>): Boolean =
        amountsToRemove.isNotEmpty() && amountsToRemove.values.all { it > 0 } &&
            amountsToRemove.all { (liquidId, amount) -> this.amount(liquidId) >= amount }

    /** 混合液の回収で、定義された構成を一度に減らします。 */
    fun minusAll(amountsToRemove: Map<String, Int>): CookingLiquidContents {
        require(canMinus(amountsToRemove)) { "Cooking liquid contents cannot subtract the requested composition" }
        val next = amounts.toMutableMap()
        amountsToRemove.forEach { (liquidId, amount) ->
            val remaining = (next[liquidId] ?: 0) - amount
            if (remaining <= 0) next.remove(liquidId) else next[liquidId] = remaining
        }
        return of(next)
    }

    fun isPotentialInputFor(recipes: Collection<UnifiedLiquidCookingRecipe>): Boolean = recipes.any { recipe ->
        amounts.all { (liquidId, amount) -> amount <= (recipe.liquidInputs[liquidId] ?: 0) } &&
            recipe.liquidInputs.values.sum() <= MAX_CAPACITY
    }

    companion object {
        const val MAX_CAPACITY = CookingLiquidVolume.UNITS_PER_CAULDRON

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
