package jp.awabi2048.cccontent.features.sukima_dungeon

import jp.awabi2048.cccontent.CCContent
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin

class MarkerManager(private val plugin: JavaPlugin) : Listener {
    fun getMarkerTool(player: Player? = null): ItemStack {
        return if (plugin is CCContent) {
            plugin.getAdminMarkerToolService().createTool("sukima_dungeon.marker_tool", player)
        } else {
            throw IllegalStateException("CCContent 以外では利用できません")
        }
    }

    fun isMarkerTool(item: ItemStack?): Boolean {
        return plugin is CCContent && plugin.getAdminMarkerToolService().isTool(item, "sukima_dungeon.marker_tool")
    }

    fun startParticleTask() {
    }

    companion object {
        fun cleanupMarkers(world: World) {
            val ccContent = JavaPlugin.getPlugin(CCContent::class.java)
            ccContent.getAdminMarkerToolService().cleanupMarkers(world, "sd.marker.")
        }
    }
}
