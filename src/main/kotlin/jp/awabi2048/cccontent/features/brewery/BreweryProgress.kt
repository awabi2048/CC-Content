package jp.awabi2048.cccontent.features.brewery

import kotlin.math.max

/** 酔いと図鑑をUUID単位で保存するための版付き進行モデル。 */
data class BreweryIntoxicationState(
    var alcohol: Double = 0.0,
    var updatedAtMillis: Long = 0L,
    var faintUntilMillis: Long = 0L
)

object BreweryIntoxicationMath {
    @JvmStatic
    fun decay(
        state: BreweryIntoxicationState,
        nowMillis: Long,
        decayPerSecond: Double,
        retentionSeconds: Long
    ): Boolean {
        if (state.updatedAtMillis <= 0L) {
            state.updatedAtMillis = nowMillis
            return false
        }
        val elapsedMillis = max(0L, nowMillis - state.updatedAtMillis)
        val elapsedSeconds = elapsedMillis / 1000.0
        if (elapsedSeconds <= 0.0) return false
        val previous = state.alcohol
        state.alcohol = (state.alcohol - elapsedSeconds * decayPerSecond).coerceAtLeast(0.0)
        state.updatedAtMillis = nowMillis
        if (state.alcohol <= 0.0 && elapsedMillis > retentionSeconds * 1000L) {
            state.faintUntilMillis = 0L
        }
        return previous != state.alcohol
    }

    @JvmStatic
    fun qualityCorrectedAlcohol(baseAlcohol: Double, quality: Double): Double {
        return (baseAlcohol * quality.coerceIn(0.0, 100.0) / 100.0).coerceIn(-100.0, 100.0)
    }
}
