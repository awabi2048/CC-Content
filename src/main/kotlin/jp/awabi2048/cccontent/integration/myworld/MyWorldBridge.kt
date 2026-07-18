package jp.awabi2048.cccontent.integration.myworld

import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import java.util.UUID

data class MyWorldRef(
    val uuid: UUID,
    val worldKey: NamespacedKey,
    val owner: UUID,
    val members: Set<UUID>,
    val moderators: Set<UUID>
) {
    fun authorizedPlayers(): Set<UUID> = buildSet {
        add(owner)
        addAll(members)
        addAll(moderators)
    }
}

enum class MyWorldUnavailableReason {
    PLUGIN_MISSING,
    API_UNAVAILABLE,
    CAPABILITY_DISABLED,
    SERVICE_UNAVAILABLE
}

sealed interface WorldPointGrantResult {
    data class Success(val balance: Int) : WorldPointGrantResult
    data class Unavailable(val reason: MyWorldUnavailableReason) : WorldPointGrantResult
    data class Failure(val message: String) : WorldPointGrantResult
}

interface MyWorldBridge {
    fun isAvailable(): Boolean

    fun findMyWorldByWorldKey(worldKey: NamespacedKey): MyWorldRef?

    fun findMyWorldByUuid(worldUuid: UUID): MyWorldRef?

    fun isBedrock(player: Player): Boolean

    fun isWorldPointCapabilityEnabled(): Boolean

    fun grantWorldPoints(playerId: UUID, amount: Int): WorldPointGrantResult
}
