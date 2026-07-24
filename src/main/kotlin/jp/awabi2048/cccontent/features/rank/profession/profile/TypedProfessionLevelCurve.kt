package jp.awabi2048.cccontent.features.rank.profession.profile

import jp.awabi2048.cccontent.features.rank.profession.Profession
import kotlin.math.pow
import kotlin.math.roundToLong

object TypedProfessionLevelCurve {
    const val MAX_LEVEL = 50

    private data class Definition(val initialExp: Long, val base: Double)

    private val definitions = mapOf(
        Profession.MINER to Definition(10L, 1.20),
        Profession.LUMBERJACK to Definition(10L, 1.25),
        Profession.FARMER to Definition(10L, 1.20),
        Profession.FISHER to Definition(100L, 1.25),
        Profession.BREWER to Definition(100L, 1.25),
        Profession.COOK to Definition(100L, 1.25)
    )

    fun requiredTotalExp(profession: Profession, level: Int): Long {
        require(profession.usesTypedAbilityAdapter) { "${profession.id} does not use a typed ability adapter" }
        val target = level.coerceIn(0, MAX_LEVEL)
        if (target == 0) return 0L
        val definition = definitions.getValue(profession)
        var total = 0L
        for (nextLevel in 1..target) {
            val required = (definition.initialExp * definition.base.pow((nextLevel - 1).toDouble()))
                .roundToLong()
                .coerceAtLeast(1L)
            total = if (Long.MAX_VALUE - total < required) Long.MAX_VALUE else total + required
        }
        return total
    }

    fun calculateLevel(profession: Profession, totalExp: Long): Int {
        require(profession.usesTypedAbilityAdapter) { "${profession.id} does not use a typed ability adapter" }
        val exp = totalExp.coerceAtLeast(0L)
        var low = 1
        var high = MAX_LEVEL
        var result = 0
        while (low <= high) {
            val middle = (low + high) ushr 1
            if (exp >= requiredTotalExp(profession, middle)) {
                result = middle
                low = middle + 1
            } else {
                high = middle - 1
            }
        }
        return result
    }

    fun expToNextLevel(profession: Profession, totalExp: Long): Long {
        val level = calculateLevel(profession, totalExp)
        if (level >= MAX_LEVEL) return 0L
        return (requiredTotalExp(profession, level + 1) - totalExp.coerceAtLeast(0L)).coerceAtLeast(0L)
    }
}
