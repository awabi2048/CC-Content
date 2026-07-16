package jp.awabi2048.cccontent.features.minigame.endergolf

import jp.awabi2048.cccontent.features.minigame.core.MiniGameEndReason
import jp.awabi2048.cccontent.features.minigame.core.MiniGameId
import jp.awabi2048.cccontent.features.minigame.core.MiniGameMarker
import jp.awabi2048.cccontent.features.minigame.core.MiniGameMarkerType
import jp.awabi2048.cccontent.features.minigame.core.MiniGameResult
import jp.awabi2048.cccontent.features.minigame.core.MiniGameSession
import jp.awabi2048.cccontent.features.minigame.core.MiniGameSessionContract
import jp.awabi2048.cccontent.features.minigame.core.MiniGameSessionState
import jp.awabi2048.cccontent.features.minigame.core.MiniGameType
import java.util.UUID

data class EnderGolfHole(
    val number: Int,
    val startMarkerUuid: UUID,
    val cupMarkerUuid: UUID,
    val waterHazardUuids: Set<UUID>,
    val bunkerUuids: Set<UUID>
)

class EnderGolfCourse(val holes: List<EnderGolfHole>) {
    init { require(holes.isNotEmpty()) { "ender golf requires at least one hole" } }

    companion object {
        fun fromMarkers(markers: Collection<MiniGameMarker>): EnderGolfCourse {
            val starts = markers.filter { it.type == MiniGameMarkerType.START }.groupBy { it.checkpointIndex }
            val cups = markers.filter { it.type == MiniGameMarkerType.CUP }.groupBy { it.checkpointIndex }
            require(starts.isNotEmpty() && starts.keys == cups.keys) { "each hole requires one start and one cup" }
            val holes = starts.keys.filterNotNull().sorted().map { number ->
                val start = starts[number].orEmpty()
                val cup = cups[number].orEmpty()
                require(start.size == 1 && cup.size == 1) { "hole $number requires exactly one start and cup" }
                EnderGolfHole(
                    number,
                    start.single().markerUuid,
                    cup.single().markerUuid,
                    markers.filter { it.type == MiniGameMarkerType.WATER_HAZARD && it.checkpointIndex == number }
                        .map { it.markerUuid }.toSet(),
                    markers.filter { it.type == MiniGameMarkerType.BUNKER && it.checkpointIndex == number }
                        .map { it.markerUuid }.toSet()
                )
            }
            return EnderGolfCourse(holes)
        }
    }
}

enum class EnderGolfLanding { IGNORED, STARTED, LANDING, WATER_HAZARD, BUNKER, CUP }

/** エンダーパールの投擲回数とホール進行をプレイヤー単位で保持する。 */
class EnderGolfSession(
    game: MiniGameId,
    participants: Collection<UUID>,
    private val course: EnderGolfCourse,
    timeLimitMillis: Long
) : MiniGameSessionContract {
    private val session = MiniGameSession(game, MiniGameType.ENDERGOLF, participants, timeLimitMillis)
    private val holeByPlayer = participants.associateWith { 0 }.toMutableMap()
    private val started = participants.associateWith { false }.toMutableMap()
    private val strokes = participants.associateWith { 0 }.toMutableMap()

    override val game get() = session.game
    override val type get() = session.type
    override val participantUuids get() = session.participantUuids
    override val state get() = session.state
    override val startedAtMillis get() = session.startedAtMillis
    override val deadlineMillis get() = session.deadlineMillis
    val scores: Map<UUID, Int> get() = strokes.toMap()

    override fun start(nowMillis: Long) = session.start(nowMillis)

    fun beginHole(player: UUID, markerUuid: UUID): EnderGolfLanding {
        if (state != MiniGameSessionState.RUNNING || player !in participantUuids) return EnderGolfLanding.IGNORED
        val hole = course.holes.getOrNull(holeByPlayer.getValue(player)) ?: return EnderGolfLanding.IGNORED
        if (markerUuid != hole.startMarkerUuid) return EnderGolfLanding.IGNORED
        started[player] = true
        return EnderGolfLanding.STARTED
    }

    fun registerThrow(player: UUID): Boolean {
        if (state != MiniGameSessionState.RUNNING || player !in participantUuids || !started.getValue(player)) return false
        strokes[player] = strokes.getValue(player) + 1
        return true
    }

    fun recordLanding(player: UUID, markerUuid: UUID, markerType: MiniGameMarkerType, nowMillis: Long): EnderGolfLanding {
        if (state != MiniGameSessionState.RUNNING || player !in participantUuids || !started.getValue(player)) {
            return EnderGolfLanding.IGNORED
        }
        val hole = course.holes.getOrNull(holeByPlayer.getValue(player)) ?: return EnderGolfLanding.IGNORED
        return when {
            markerType == MiniGameMarkerType.CUP && markerUuid == hole.cupMarkerUuid -> {
                val nextHole = holeByPlayer.getValue(player) + 1
                if (nextHole == course.holes.size) {
                    session.recordCompletion(player, nowMillis - requireNotNull(startedAtMillis), strokes.getValue(player))
                } else {
                    holeByPlayer[player] = nextHole
                    started[player] = false
                }
                EnderGolfLanding.CUP
            }
            markerType == MiniGameMarkerType.WATER_HAZARD && markerUuid in hole.waterHazardUuids -> {
                strokes[player] = strokes.getValue(player) + 1
                EnderGolfLanding.WATER_HAZARD
            }
            markerType == MiniGameMarkerType.BUNKER && markerUuid in hole.bunkerUuids -> {
                strokes[player] = strokes.getValue(player) + 1
                EnderGolfLanding.BUNKER
            }
            else -> EnderGolfLanding.LANDING
        }
    }

    fun recordOrdinaryLanding(player: UUID): EnderGolfLanding {
        if (state != MiniGameSessionState.RUNNING || player !in participantUuids ||
            !started.getValue(player) || session.hasWithdrawn(player)) return EnderGolfLanding.IGNORED
        return EnderGolfLanding.LANDING
    }

    fun allPlayersFinished(): Boolean = participantUuids.all(session::hasCompleted)
    fun withdraw(playerUuid: UUID): Boolean = session.withdraw(playerUuid)
    fun allPlayersResolved(): Boolean = participantUuids.all(session::isResolved)
    fun currentHole(player: UUID): EnderGolfHole? = course.holes.getOrNull(holeByPlayer[player] ?: return null)

    override fun tick(nowMillis: Long): MiniGameResult? = session.tick(nowMillis)
    override fun forceEnd(nowMillis: Long, reason: MiniGameEndReason): MiniGameResult = session.forceEnd(nowMillis, reason)
    override fun result(nowMillis: Long, reason: MiniGameEndReason): MiniGameResult = session.result(nowMillis, reason)
}
