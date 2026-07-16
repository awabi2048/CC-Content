package jp.awabi2048.cccontent.features.minigame.colosseum

import jp.awabi2048.cccontent.features.minigame.core.MiniGameEndReason
import jp.awabi2048.cccontent.features.minigame.core.MiniGameId
import jp.awabi2048.cccontent.features.minigame.core.MiniGameResult
import jp.awabi2048.cccontent.features.minigame.core.MiniGameSession
import jp.awabi2048.cccontent.features.minigame.core.MiniGameSessionContract
import jp.awabi2048.cccontent.features.minigame.core.MiniGameSessionState
import jp.awabi2048.cccontent.features.minigame.core.MiniGameType
import java.util.UUID

enum class ColosseumRoundStatus { ROUND_WON, MATCH_WON, IGNORED }

/** 1対1のラウンド制を担当する純粋なルールモデル。 */
class ColosseumSession(
    game: MiniGameId,
    participants: Collection<UUID>,
    timeLimitMillis: Long,
    private val firstTo: Int
) : MiniGameSessionContract {
    private val session = MiniGameSession(game, MiniGameType.COLOSSEUM, participants, timeLimitMillis)
    private val players = session.participantUuids.toList()
    private val wins = players.associateWith { 0 }.toMutableMap()

    init {
        require(players.size == 2) { "colosseum requires exactly two participants" }
        require(firstTo > 0) { "firstTo must be positive" }
    }

    override val game get() = session.game
    override val type get() = session.type
    override val participantUuids get() = session.participantUuids
    override val state get() = session.state
    override val startedAtMillis get() = session.startedAtMillis
    override val deadlineMillis get() = session.deadlineMillis
    val roundWins: Map<UUID, Int> get() = wins.toMap()

    override fun start(nowMillis: Long) = session.start(nowMillis)

    fun recordRoundWin(winner: UUID, nowMillis: Long): ColosseumRoundStatus {
        if (state != MiniGameSessionState.RUNNING || winner !in participantUuids) return ColosseumRoundStatus.IGNORED
        val next = wins.getValue(winner) + 1
        wins[winner] = next
        if (next >= firstTo) {
            session.recordCompletion(winner, nowMillis - requireNotNull(startedAtMillis), next)
            return ColosseumRoundStatus.MATCH_WON
        }
        return ColosseumRoundStatus.ROUND_WON
    }

    fun recordDeath(loser: UUID, nowMillis: Long): ColosseumRoundStatus {
        val winner = participantUuids.firstOrNull { it != loser } ?: return ColosseumRoundStatus.IGNORED
        return recordRoundWin(winner, nowMillis)
    }

    fun forfeit(leaver: UUID, nowMillis: Long): ColosseumRoundStatus {
        val winner = participantUuids.firstOrNull { it != leaver } ?: return ColosseumRoundStatus.IGNORED
        var outcome = ColosseumRoundStatus.ROUND_WON
        while (wins.getValue(winner) < firstTo && state == MiniGameSessionState.RUNNING) {
            outcome = recordRoundWin(winner, nowMillis)
        }
        return outcome
    }

    fun winnerByScore(nowMillis: Long): UUID? {
        val winner = wins.maxByOrNull { it.value }?.key ?: return null
        if (wins.values.distinct().size == 1) return null
        if (state == MiniGameSessionState.RUNNING) {
            session.recordCompletion(winner, nowMillis - requireNotNull(startedAtMillis), wins.getValue(winner))
        }
        return winner
    }

    override fun tick(nowMillis: Long): MiniGameResult? {
        if (state == MiniGameSessionState.RUNNING && deadlineMillis?.let { nowMillis >= it } == true) {
            winnerByScore(nowMillis)
        }
        return session.tick(nowMillis)
    }
    override fun forceEnd(nowMillis: Long, reason: MiniGameEndReason): MiniGameResult = session.forceEnd(nowMillis, reason)
    override fun result(nowMillis: Long, reason: MiniGameEndReason): MiniGameResult = session.result(nowMillis, reason)
}
