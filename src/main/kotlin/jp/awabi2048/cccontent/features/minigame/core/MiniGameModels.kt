package jp.awabi2048.cccontent.features.minigame.core

import java.util.UUID

/**
 * ユーザー設置型ミニゲームが共通して持つ識別子と進行結果の契約。
 * Bukkitのエンティティやプレイヤー状態を参照しないため、ルール判定を純粋に検証できる。
 */
data class MiniGameId(
    val worldUuid: UUID,
    val gameId: String
) {
    init {
        require(gameId.isNotBlank()) { "gameId must not be blank" }
    }
}

enum class MiniGameType {
    RACE,
    HIDE_AND_SEEK,
    CHASE,
    COLOSSEUM,
    ENDERGOLF
}

enum class MiniGameMarkerType {
    START,
    CHECKPOINT,
    GOAL,
    JAIL,
    CUP,
    WATER_HAZARD,
    BUNKER
}

enum class MiniGameRole {
    HUNTER,
    RUNNER
}

enum class MiniGameSessionState {
    IDLE,
    RUNNING,
    ENDED
}

enum class MiniGameEndReason {
    COMPLETED,
    TIME_LIMIT,
    STOPPED,
    FORCED,
    PLAYER_DEATH,
    PLAYER_LEFT
}

data class MiniGameMarker(
    val markerUuid: UUID,
    val game: MiniGameId,
    val ownerUuid: UUID,
    val type: MiniGameMarkerType,
    val checkpointIndex: Int? = null
) {
    init {
        if (type in setOf(
                MiniGameMarkerType.CHECKPOINT,
                MiniGameMarkerType.CUP,
                MiniGameMarkerType.WATER_HAZARD,
                MiniGameMarkerType.BUNKER
            )) {
            require(checkpointIndex != null && checkpointIndex >= 1) {
                "indexed markers require a positive checkpointIndex"
            }
        } else if (type == MiniGameMarkerType.START) {
            require(checkpointIndex == null || checkpointIndex >= 1) {
                "a start marker index must be null or positive"
            }
        } else {
            require(checkpointIndex == null) { "this marker type must not have an index" }
        }
    }
}

data class MiniGameResultEntry(
    val playerUuid: UUID,
    val completed: Boolean,
    val elapsedMillis: Long?,
    val rank: Int?,
    val score: Int? = null
)

data class MiniGameResult(
    val game: MiniGameId,
    val type: MiniGameType,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val reason: MiniGameEndReason,
    val entries: List<MiniGameResultEntry>
)

interface MiniGameSessionContract {
    val game: MiniGameId
    val type: MiniGameType
    val participantUuids: Set<UUID>
    val state: MiniGameSessionState
    val startedAtMillis: Long?
    val deadlineMillis: Long?

    fun start(nowMillis: Long)
    fun tick(nowMillis: Long): MiniGameResult?
    fun forceEnd(nowMillis: Long, reason: MiniGameEndReason = MiniGameEndReason.FORCED): MiniGameResult
    fun result(nowMillis: Long, reason: MiniGameEndReason): MiniGameResult
}

/**
 * 参加者集合、開始時刻、制限時間、終了理由を管理する共通セッション。
 * 個別ゲームは [recordCompletion] で結果を登録し、結果の並び順を決定する。
 */
class MiniGameSession(
    override val game: MiniGameId,
    override val type: MiniGameType,
    participants: Collection<UUID>,
    private val timeLimitMillis: Long,
    private val preparationMillis: Long = 0L,
    private val resultEntries: MutableMap<UUID, MiniGameResultEntry> = linkedMapOf()
) : MiniGameSessionContract {
    override val participantUuids: Set<UUID> = participants.toSet()
    override var state: MiniGameSessionState = MiniGameSessionState.IDLE
        private set
    override var startedAtMillis: Long? = null
        private set
    override var deadlineMillis: Long? = null
        private set
    private var endedAtMillis: Long? = null
    private var endedReason: MiniGameEndReason? = null
    private val withdrawnPlayers = linkedSetOf<UUID>()

    init {
        require(participantUuids.isNotEmpty()) { "a mini-game requires at least one participant" }
        require(timeLimitMillis > 0L) { "timeLimitMillis must be positive" }
        require(preparationMillis >= 0L) { "preparationMillis must not be negative" }
    }

    override fun start(nowMillis: Long) {
        check(state == MiniGameSessionState.IDLE) { "session is not idle" }
        startedAtMillis = nowMillis + preparationMillis
        deadlineMillis = requireNotNull(startedAtMillis) + timeLimitMillis
        state = MiniGameSessionState.RUNNING
    }

    fun recordCompletion(playerUuid: UUID, elapsedMillis: Long, score: Int? = null) {
        check(state == MiniGameSessionState.RUNNING) { "session is not running" }
        require(playerUuid in participantUuids) { "player is not a participant" }
        require(elapsedMillis >= 0L) { "elapsedMillis must not be negative" }
        if (resultEntries[playerUuid]?.completed == true || playerUuid in withdrawnPlayers) return
        resultEntries[playerUuid] = MiniGameResultEntry(playerUuid, true, elapsedMillis, null, score)
    }

    fun hasCompleted(playerUuid: UUID): Boolean = resultEntries[playerUuid]?.completed == true

    fun withdraw(playerUuid: UUID): Boolean {
        if (state != MiniGameSessionState.RUNNING || playerUuid !in participantUuids || hasCompleted(playerUuid)) return false
        return withdrawnPlayers.add(playerUuid)
    }

    fun hasWithdrawn(playerUuid: UUID): Boolean = playerUuid in withdrawnPlayers

    fun isResolved(playerUuid: UUID): Boolean = hasCompleted(playerUuid) || hasWithdrawn(playerUuid)

    override fun tick(nowMillis: Long): MiniGameResult? {
        if (state != MiniGameSessionState.RUNNING || nowMillis < requireNotNull(deadlineMillis)) return null
        return result(nowMillis, MiniGameEndReason.TIME_LIMIT)
    }

    override fun forceEnd(nowMillis: Long, reason: MiniGameEndReason): MiniGameResult {
        check(reason != MiniGameEndReason.COMPLETED && reason != MiniGameEndReason.TIME_LIMIT) {
            "forceEnd requires a non-completion reason"
        }
        return result(nowMillis, reason)
    }

    override fun result(nowMillis: Long, reason: MiniGameEndReason): MiniGameResult {
        if (state == MiniGameSessionState.ENDED) {
            return buildResult()
        }
        check(state == MiniGameSessionState.RUNNING) { "session has not started" }
        val end = nowMillis.coerceAtLeast(requireNotNull(startedAtMillis))
        val completed = resultEntries.values
            .filter { it.completed }
            .sortedWith(compareBy<MiniGameResultEntry> { it.score ?: Int.MAX_VALUE }
                .thenBy { it.elapsedMillis }
                .thenBy { it.playerUuid.toString() })
        val ranked = completed.mapIndexed { index, entry -> entry.copy(rank = index + 1) }
        val rankedByPlayer = ranked.associateBy { it.playerUuid }
        participantUuids.forEach { playerUuid ->
            if (playerUuid !in rankedByPlayer) {
                resultEntries[playerUuid] = MiniGameResultEntry(playerUuid, false, null, null, null)
            }
        }
        ranked.forEach { resultEntries[it.playerUuid] = it }
        endedAtMillis = end
        endedReason = reason
        state = MiniGameSessionState.ENDED
        return buildResult()
    }

    private fun buildResult(): MiniGameResult = MiniGameResult(
        game = game,
        type = type,
        startedAtMillis = requireNotNull(startedAtMillis),
        endedAtMillis = requireNotNull(endedAtMillis),
        reason = requireNotNull(endedReason),
            entries = participantUuids.map { resultEntries.getValue(it) }
            .sortedWith(compareBy<MiniGameResultEntry> { it.rank ?: Int.MAX_VALUE }.thenBy { it.playerUuid.toString() })
    )
}
