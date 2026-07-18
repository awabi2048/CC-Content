package jp.awabi2048.cccontent.features.rank.profession.profile

import kotlin.math.roundToLong

enum class ProfessionActionQuality(val multiplier: Double) {
    LOW(0.80),
    STANDARD(1.00),
    HIGH(1.15),
    HIGHEST(1.25)
}

object ProfessionExperience {
    const val NORMAL_ACTION = 1L
    const val SPECIALIST_ACTION = 4L
    const val HIGH_QUALITY_BONUS = 2L
    const val FIRST_DISCOVERY_BONUS = 5L
    const val BATCH_NORMAL_ACTION_CAP = 16L

    fun batchExperience(targetCount: Int, baseExperience: Long = NORMAL_ACTION): Long {
        if (targetCount <= 0 || baseExperience <= 0L) return 0L
        var weighted = 0.0
        repeat(targetCount) { index ->
            weighted += when (index) {
                0 -> 1.0
                in 1..7 -> 0.70
                else -> 0.40
            }
        }
        return (weighted * baseExperience).roundToLong()
            .coerceAtMost(BATCH_NORMAL_ACTION_CAP * baseExperience)
    }

    fun recipeExperience(
        baseExperience: Long,
        quality: ProfessionActionQuality,
        firstCompletion: Boolean
    ): Long = (baseExperience.coerceAtLeast(0L) * quality.multiplier).roundToLong() +
        if (firstCompletion) FIRST_DISCOVERY_BONUS else 0L
}
