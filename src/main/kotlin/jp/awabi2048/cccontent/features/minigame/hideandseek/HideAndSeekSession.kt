package jp.awabi2048.cccontent.features.minigame.hideandseek

import jp.awabi2048.cccontent.features.minigame.core.MiniGameEndReason
import jp.awabi2048.cccontent.features.minigame.core.MiniGameId
import jp.awabi2048.cccontent.features.minigame.core.MiniGameResult
import jp.awabi2048.cccontent.features.minigame.core.MiniGameRole
import jp.awabi2048.cccontent.features.minigame.core.MiniGameSession
import jp.awabi2048.cccontent.features.minigame.core.MiniGameSessionContract
import jp.awabi2048.cccontent.features.minigame.core.MiniGameSessionState
import jp.awabi2048.cccontent.features.minigame.core.MiniGameType
import java.util.UUID

enum class CaptureStatus { CAPTURED, IGNORED, ALREADY_CAPTURED }

class HideAndSeekSession(
    game: MiniGameId,
    participants: Collection<UUID>,
    timeLimitMillis: Long,
    hunters: Set<UUID>,
    preparationMillis: Long = 0L
) : MiniGameSessionContract {
    private val session = MiniGameSession(
        game,
        MiniGameType.HIDE_AND_SEEK,
        participants,
        timeLimitMillis,
        preparationMillis
    )
    val roles: Map<UUID, MiniGameRole>
    private val captured = linkedSetOf<UUID>()

    init {
        val distinctParticipants = participants.distinct()
        require(hunters.isNotEmpty() && hunters.size < distinctParticipants.size && hunters.all { it in distinctParticipants }) {
            "hide-and-seek requires selected hunters and runners"
        }
        roles = distinctParticipants.associateWith { uuid ->
            if (uuid in hunters) MiniGameRole.HUNTER else MiniGameRole.RUNNER
        }.toMap()
    }

    override val game get() = session.game
    override val type get() = session.type
    override val participantUuids get() = session.participantUuids
    override val state get() = session.state
    override val startedAtMillis get() = session.startedAtMillis
    override val deadlineMillis get() = session.deadlineMillis
    val runners: Set<UUID> get() = roles.filterValues { it == MiniGameRole.RUNNER }.keys

    override fun start(nowMillis: Long) = session.start(nowMillis)

    fun capture(hunter: UUID, runner: UUID, nowMillis: Long): CaptureStatus {
        if (state != MiniGameSessionState.RUNNING || nowMillis < requireNotNull(startedAtMillis) ||
            roles[hunter] != MiniGameRole.HUNTER ||
            roles[runner] != MiniGameRole.RUNNER || session.hasWithdrawn(hunter) || session.hasWithdrawn(runner)) {
            return CaptureStatus.IGNORED
        }
        if (!captured.add(runner)) return CaptureStatus.ALREADY_CAPTURED
        session.recordCompletion(runner, nowMillis - requireNotNull(startedAtMillis))
        return CaptureStatus.CAPTURED
    }

    fun allRunnersCaptured(): Boolean = runners.all(captured::contains)
    fun isCaptured(player: UUID): Boolean = player in captured
    fun withdraw(player: UUID): Boolean {
        if (!session.withdraw(player)) return false
        return activeHunters().isEmpty() || activeRunners().isEmpty()
    }
    fun activeHunters(): Set<UUID> = roles.filterValues { it == MiniGameRole.HUNTER }.keys
        .filterNot(session::hasWithdrawn).toSet()
    fun activeRunners(): Set<UUID> = runners.filterNot { it in captured || session.hasWithdrawn(it) }.toSet()
    override fun tick(nowMillis: Long): MiniGameResult? = session.tick(nowMillis)
    override fun forceEnd(nowMillis: Long, reason: MiniGameEndReason) = session.forceEnd(nowMillis, reason)
    override fun result(nowMillis: Long, reason: MiniGameEndReason) = session.result(nowMillis, reason)
}
