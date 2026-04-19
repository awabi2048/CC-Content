package jp.awabi2048.cccontent.features.arena

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

object ArenaPermissions {
    const val ADMIN = "cc-content.arena.admin"

    const val MENU_MISSION = "cc-content.arena.menu.mission"
    const val MENU_BROADCAST = "cc-content.arena.menu.broadcast"
    const val MENU_PEDESTAL = "cc-content.arena.menu.pedestal"

    const val LICENSE_PAPER = "cc-content.arena.license.paper"
    const val LICENSE_BRONZE = "cc-content.arena.license.bronze"
    const val LICENSE_SILVER = "cc-content.arena.license.silver"
    const val LICENSE_GOLD = "cc-content.arena.license.gold"

    fun hasAdminAccess(sender: CommandSender): Boolean {
        return sender !is Player || sender.hasPermission(ADMIN)
    }

    fun hasMissionMenuPermission(player: Player): Boolean = player.hasPermission(MENU_MISSION)

    fun hasBroadcastMenuPermission(player: Player): Boolean = player.hasPermission(MENU_BROADCAST)

    fun hasPedestalMenuPermission(player: Player): Boolean = player.hasPermission(MENU_PEDESTAL)

    fun hasAnyLicensePermission(player: Player): Boolean {
        return player.hasPermission(LICENSE_PAPER) ||
            player.hasPermission(LICENSE_BRONZE) ||
            player.hasPermission(LICENSE_SILVER) ||
            player.hasPermission(LICENSE_GOLD)
    }

    fun maxLicenseStarByPermission(player: Player): Int {
        return when {
            player.hasPermission(LICENSE_GOLD) -> 4
            player.hasPermission(LICENSE_SILVER) -> 3
            player.hasPermission(LICENSE_BRONZE) -> 2
            player.hasPermission(LICENSE_PAPER) -> 1
            else -> 0
        }
    }
}
