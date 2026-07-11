package jp.awabi2048.cccontent.features.rank.tutorial.task

import org.bukkit.Material
import org.bukkit.event.world.PortalCreateEvent

/** PortalCreateEvent が実際のネザーポータル成立を表すかを判定する。 */
object NetherPortalCreationPredicate {
    fun isEstablished(reason: PortalCreateEvent.CreateReason, materials: Iterable<Material>): Boolean {
        return reason == PortalCreateEvent.CreateReason.FIRE && materials.any { it == Material.NETHER_PORTAL }
    }
}
