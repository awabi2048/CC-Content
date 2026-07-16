package jp.awabi2048.cccontent.features.arena

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.exp
import kotlin.math.ln
import kotlin.random.Random

data class ArenaHistoryRecord(
    val playerId: UUID,
    val date: LocalDate,
    val difficultyStar: Int,
    val durationSeconds: Long
)

data class ArenaDemandConfig(
    val halfLifeDays: Double = 30.0,
    val maxAgeDays: Int = 90,
    val clearCountWeight: Double = 1.0,
    val difficultyWeight: Double = 1.0,
    val durationWeight: Double = 0.25,
    val influenceStrength: Double = 1.0
) {
    init {
        require(halfLifeDays.isFinite() && halfLifeDays > 0.0) { "halfLifeDays must be a finite positive number" }
        require(maxAgeDays in 1..90) { "maxAgeDays must be between 1 and 90" }
        require(clearCountWeight.isFinite() && clearCountWeight >= 0.0) {
            "clearCountWeight must be a finite non-negative number"
        }
        require(difficultyWeight.isFinite() && difficultyWeight >= 0.0) {
            "difficultyWeight must be a finite non-negative number"
        }
        require(durationWeight.isFinite() && durationWeight >= 0.0) {
            "durationWeight must be a finite non-negative number"
        }
        require((clearCountWeight + durationWeight).isFinite() && clearCountWeight + durationWeight > 0.0) {
            "clearCountWeight or durationWeight must be positive"
        }
        require(influenceStrength.isFinite() && influenceStrength >= 0.0) {
            "influenceStrength must be a finite non-negative number"
        }
    }
}

class ArenaDemandModel(private val config: ArenaDemandConfig) {
    @JvmOverloads
    fun selectDifficulty(
        candidates: List<Int>,
        history: Collection<ArenaHistoryRecord>,
        today: LocalDate,
        random: Random = Random.Default
    ): Int {
        require(candidates.isNotEmpty()) { "candidates must not be empty" }
        val uniqueCandidates = candidates.distinct()
        val target = estimateTargetDifficulty(uniqueCandidates, history, today)
        val scores = uniqueCandidates.map { candidate ->
            selectionWeight(candidate, target)
        }
        val total = scores.sum()
        var roll = random.nextDouble() * total
        uniqueCandidates.forEachIndexed { index, candidate ->
            roll -= scores[index]
            if (roll <= 0.0) return candidate
        }
        return uniqueCandidates.last()
    }

    /**
     * 各プレイヤーの減衰済み活動量と難易度平均を個別に集約し、活動量で全員を統合した目標を算出する。
     * 保持期間内の履歴をプレイヤー単位で一度だけコホートへ加えるため、候補別の人気回数は使わない。
     */
    fun estimateTargetDifficulty(
        candidates: List<Int>,
        history: Collection<ArenaHistoryRecord>,
        today: LocalDate
    ): Double {
        require(candidates.isNotEmpty()) { "candidates must not be empty" }
        val uniqueCandidates = candidates.distinct()
        val neutralTarget = uniqueCandidates.average()
        val playerStats = history.asSequence()
            .mapNotNull { record ->
                val age = ChronoUnit.DAYS.between(record.date, today)
                if (age !in 0 until config.maxAgeDays) return@mapNotNull null
                val decay = decayFactor(age)
                val durationHours = record.durationSeconds.coerceAtLeast(0L).toDouble() / 3600.0
                val activity = decay * (config.clearCountWeight + config.durationWeight * durationHours)
                if (activity <= 0.0) return@mapNotNull null
                record.playerId to (activity to record.difficultyStar.toDouble())
            }
            .groupBy({ it.first }, { it.second })
            .values
            .mapNotNull { records ->
                val activity = records.sumOf { it.first }
                if (activity <= 0.0) return@mapNotNull null
                val difficulty = records.sumOf { it.first * it.second } / activity
                activity to difficulty
            }

        val cohortActivity = playerStats.sumOf { it.first }
        if (cohortActivity <= 0.0 || config.difficultyWeight == 0.0) return neutralTarget

        val observedTarget = playerStats.sumOf { it.first * it.second } / cohortActivity
        val difficultyPull = config.difficultyWeight / (1.0 + config.difficultyWeight)
        return neutralTarget + (observedTarget - neutralTarget) * difficultyPull
    }

    /** 候補ごとの信号は目標からの距離だけであり、履歴件数は使わない。 */
    fun selectionWeight(candidate: Int, targetDifficulty: Double): Double {
        require(targetDifficulty.isFinite()) { "targetDifficulty must be finite" }
        return exp(-kotlin.math.abs(candidate - targetDifficulty) * config.influenceStrength)
    }

    fun decayFactor(ageDays: Long): Double {
        if (ageDays < 0L || ageDays >= config.maxAgeDays) return 0.0
        return exp(-ln(2.0) * ageDays / config.halfLifeDays)
    }
}
