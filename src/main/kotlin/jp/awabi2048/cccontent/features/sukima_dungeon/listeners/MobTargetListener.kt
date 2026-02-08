package jp.awabi2048.cccontent.features.sukima_dungeon.listeners

import jp.awabi2048.cccontent.features.sukima_dungeon.isSukimaDungeonWorld
import jp.awabi2048.cccontent.features.sukima_dungeon.SukimaConfigHelper
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityTargetLivingEntityEvent
import org.bukkit.plugin.java.JavaPlugin

class MobTargetListener(private val plugin: JavaPlugin) : Listener {

    @EventHandler
    fun onMobTarget(event: EntityTargetLivingEntityEvent) {
        val target = event.target as? Player ?: return
        
        // ターゲットがダンジョン内にいるかチェック
        if (!isSukimaDungeonWorld(target.world)) return

        val mob = event.entity
        val distance = mob.location.distance(target.location)

        val config = SukimaConfigHelper.getConfig(plugin)
        val normalRange = config.getDouble("mob_detection.normal_range", 16.0)
        val sneakRange = config.getDouble("mob_detection.sneak_range", 8.0)

        if (target.isSneaking) {
            if (distance > sneakRange) {
                event.isCancelled = true
            }
        } else {
            if (distance > normalRange) {
                event.isCancelled = true
            }
        }
    }
}
