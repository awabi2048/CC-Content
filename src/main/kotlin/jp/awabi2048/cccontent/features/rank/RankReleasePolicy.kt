package jp.awabi2048.cccontent.features.rank

import jp.awabi2048.cccontent.features.rank.profession.Profession
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID

/** 職業・スキルを一般公開する時期をコードで管理する。 */
object RankReleasePolicy {
    const val ADMIN_PERMISSION = "cc-content.admin"

    private val publicProfessions: Set<Profession> = emptySet()
    private const val skillSystemPublic = false

    fun isProfessionPublic(profession: Profession): Boolean = profession in publicProfessions

    fun isProfessionAccessible(isAdmin: Boolean, profession: Profession): Boolean =
        isAdmin || isProfessionPublic(profession)

    fun isSkillSystemAccessible(isAdmin: Boolean): Boolean = isAdmin || skillSystemPublic

    fun canAccessProfession(player: Player, profession: Profession): Boolean =
        isProfessionAccessible(player.hasPermission(ADMIN_PERMISSION), profession)

    fun canAccessProfession(playerUuid: UUID, profession: Profession): Boolean {
        val player = Bukkit.getPlayer(playerUuid) ?: return false
        return canAccessProfession(player, profession)
    }

    fun canUseSkills(player: Player): Boolean =
        isSkillSystemAccessible(player.hasPermission(ADMIN_PERMISSION))

    fun canUseSkills(playerUuid: UUID): Boolean {
        val player = Bukkit.getPlayer(playerUuid) ?: return false
        return canUseSkills(player)
    }
}
