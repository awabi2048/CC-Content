package jp.awabi2048.cccontent.features.brewery

import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class BreweryFeature(private val plugin: JavaPlugin) {
    fun initialize() {
        ensureResources()
    }

    fun reload() {
        ensureResources()
    }

    private fun ensureResources() {
        ensureFile("brewery/config.yml")
        ensureFile("recipe/brewery.yml")
        ensureFile("recipe/ingredient_definition.yml")
    }

    private fun ensureFile(path: String) {
        val file = File(plugin.dataFolder, path)
        if (file.exists()) return
        file.parentFile?.mkdirs()
        plugin.saveResource(path, false)
    }
}
