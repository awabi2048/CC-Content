package jp.awabi2048.cccontent.features.minigame.race

import jp.awabi2048.cccontent.features.minigame.core.MiniGameEndReason
import jp.awabi2048.cccontent.features.minigame.core.MiniGameId
import jp.awabi2048.cccontent.features.minigame.core.MiniGameMarker
import jp.awabi2048.cccontent.features.minigame.core.MiniGameMarkerType
import jp.awabi2048.cccontent.features.minigame.core.MiniGameResult
import jp.awabi2048.cccontent.features.minigame.core.MiniGameSession
import jp.awabi2048.cccontent.features.minigame.core.MiniGameSessionContract
import jp.awabi2048.cccontent.features.minigame.core.MiniGameType
import java.util.UUID

data class RaceCourse(
    val startMarkerUuid: UUID,
    val checkpointMarkerUuids: List<UUID>,
    val goalMarkerUuid: UUID
) {
    init {
        require(checkpointMarkerUuids.distinct().size == checkpointMarkerUuids.size) {
            "checkpoint marker UUIDs must be unique"
        }
        require(startMarkerUuid !in checkpointMarkerUuids && goalMarkerUuid !in checkpointMarkerUuids) {
            "start and goal must not also be checkpoints"
        }
    }

    companion object {
        fun fromMarkers(markers: Collection<MiniGameMarker>): RaceCourse {
            val start = markers.filter { it.type == MiniGameMarkerType.START }
            val goals = markers.filter { it.type == MiniGameMarkerType.GOAL }
            require(start.size == 1) { "race requires exactly one start marker" }
            require(goals.size == 1) { "race requires exactly one goal marker" }
            val checkpoints = markers
                .filter { it.type == MiniGameMarkerType.CHECKPOINT }
                .sortedBy { requireNotNull(it.checkpointIndex) }
            require(checkpoints.map { it.checkpointIndex }.distinct().size == checkpoints.size) {
                "checkpoint indexes must be unique"
            }
            return RaceCourse(start.single().markerUuid, checkpoints.map { it.markerUuid }, goals.single().markerUuid)
        }
    }
}

enum class RaceVisitStatus {
    STARTED,
    CHECKPOINT_REACHED,
    FINISHED,
    IGNORED,
    WRONG_ORDER
}

data class RaceVisitResult(
    val playerUuid: UUID,
    val status: RaceVisitStatus,
    val expectedMarkerUuid: UUID?,
    val elapsedMillis: Long? = null
)

class RaceSession(
    game: MiniGameId,
    participants: Collection<UUID>,
    private val course: RaceCourse,
    timeLimitMillis: Long
) : MiniGameSessionContract {
    private val session = MiniGameSession(game, MiniGameType.RACE, participants, timeLimitMillis)
    private val nextCheckpointByPlayer = participants.associateWith { 0 }.toMutableMap()

    override val game get() = session.game
    override val type get() = session.type
    override val participantUuids get() = session.participantUuids
    override val state get() = session.state
    override val startedAtMillis get() = session.startedAtMillis
    override val deadlineMillis get() = session.deadlineMillis

    override fun start(nowMillis: Long) = session.start(nowMillis)

    fun visit(playerUuid: UUID, markerUuid: UUID, nowMillis: Long): RaceVisitResult {
        if (playerUuid !in participantUuids || state != jp.awabi2048.cccontent.features.minigame.core.MiniGameSessionState.RUNNING) {
            return RaceVisitResult(playerUuid, RaceVisitStatus.IGNORED, expectedMarker(playerUuid))
        }
        if (session.hasCompleted(playerUuid)) {
            return RaceVisitResult(playerUuid, RaceVisitStatus.IGNORED, null)
        }
        val next = nextCheckpointByPlayer.getValue(playerUuid)
        val expected = when {
            next == 0 -> course.startMarkerUuid
            next <= course.checkpointMarkerUuids.size -> course.checkpointMarkerUuids[next - 1]
            else -> course.goalMarkerUuid
        }
        if (markerUuid != expected) {
            return RaceVisitResult(playerUuid, RaceVisitStatus.WRONG_ORDER, expected)
        }
        return when {
            next == 0 -> {
                nextCheckpointByPlayer[playerUuid] = 1
                RaceVisitResult(playerUuid, RaceVisitStatus.STARTED, expected)
            }
            next <= course.checkpointMarkerUuids.size -> {
                nextCheckpointByPlayer[playerUuid] = next + 1
                RaceVisitResult(playerUuid, RaceVisitStatus.CHECKPOINT_REACHED, expected)
            }
            else -> {
                val elapsed = nowMillis - requireNotNull(startedAtMillis)
                session.recordCompletion(playerUuid, elapsed)
                RaceVisitResult(playerUuid, RaceVisitStatus.FINISHED, expected, elapsed)
            }
        }
    }

    fun expectedMarker(playerUuid: UUID): UUID? {
        val next = nextCheckpointByPlayer[playerUuid] ?: return null
        return when {
            next == 0 -> course.startMarkerUuid
            next <= course.checkpointMarkerUuids.size -> course.checkpointMarkerUuids[next - 1]
            else -> course.goalMarkerUuid
        }
    }

    fun allParticipantsFinished(): Boolean = participantUuids.all { session.hasCompleted(it) }

    fun withdraw(playerUuid: UUID): Boolean = session.withdraw(playerUuid)

    fun allParticipantsResolved(): Boolean = participantUuids.all(session::isResolved)

    override fun tick(nowMillis: Long): MiniGameResult? = session.tick(nowMillis)

    override fun forceEnd(nowMillis: Long, reason: MiniGameEndReason): MiniGameResult = session.forceEnd(nowMillis, reason)

    override fun result(nowMillis: Long, reason: MiniGameEndReason): MiniGameResult = session.result(nowMillis, reason)
}
