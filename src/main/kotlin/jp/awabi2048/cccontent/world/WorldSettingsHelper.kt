package jp.awabi2048.cccontent.world

import org.bukkit.World
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.plugin.java.JavaPlugin

object WorldSettingsHelper {
    fun applyDistanceSettings(plugin: JavaPlugin, world: World, pathPrefix: String) {
        val config = plugin.config
        applyDistance(plugin, world, config, "$pathPrefix.view_distance") { value ->
            world.viewDistance = value
        }
        applyDistance(plugin, world, config, "$pathPrefix.simulation_distance") { value ->
            world.simulationDistance = value
        }
        applyDistance(plugin, world, config, "$pathPrefix.send_view_distance") { value ->
            world.sendViewDistance = value
        }
    }

    private fun applyDistance(
        plugin: JavaPlugin,
        world: World,
        config: FileConfiguration,
        path: String,
        setter: (Int) -> Unit
    ) {
        val value = config.getInt(path, -1)
        if (value < 0) {
            return
        }
        runCatching { setter(value) }
            .onFailure {
                plugin.logger.warning("距離設定の適用に失敗しました: world=${world.name} path=$path value=$value")
            }
    }
}
