package jp.awabi2048.cccontent.features.minigame.core

import org.bukkit.entity.Player
import jp.awabi2048.cccontent.integration.myworld.MyWorldRef
import java.util.UUID

/**
 * MWMが稼働している場合だけ、そのワールドの所有者・メンバー情報を参照する。
 * 連携はprovided APIで行い、クラス名やメソッド名の反射解決は行わない。
 */
class MiniGameAccessPolicy(
    private val worldProvider: (UUID) -> MyWorldRef?
) {
    fun canEdit(player: Player, worldUuid: UUID, itemOwnerUuid: UUID): Boolean =
        canEdit(player.uniqueId, worldUuid, itemOwnerUuid)

    fun canView(player: Player, worldUuid: UUID): Boolean = canView(player.uniqueId, worldUuid)

    fun canView(playerUuid: UUID, worldUuid: UUID): Boolean = runCatching {
        val worldData = worldProvider(worldUuid) ?: return@runCatching false
        playerUuid in worldData.authorizedPlayers()
    }.getOrDefault(false)

    /** PDC所有者と操作者の双方を、稼働中のMWM権限情報で検証する。 */
    fun canEdit(playerUuid: UUID, worldUuid: UUID, itemOwnerUuid: UUID): Boolean {
        if (itemOwnerUuid == UUID_ZERO) return false
        return runCatching { resolveMwmMembership(playerUuid, worldUuid, itemOwnerUuid) }.getOrDefault(false)
    }

    private fun resolveMwmMembership(playerUuid: UUID, worldUuid: UUID, itemOwnerUuid: UUID): Boolean {
        // MWM不在時はPDC所有者へのフォールバックを許可しない。
        val worldData = worldProvider(worldUuid) ?: return false
        val authorized = worldData.authorizedPlayers()
        return itemOwnerUuid in authorized && playerUuid in authorized
    }

    private companion object {
        val UUID_ZERO: UUID = UUID(0L, 0L)
    }
}
