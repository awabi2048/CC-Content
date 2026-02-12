package jp.awabi2048.cccontent.features.brewery

import jp.awabi2048.cccontent.util.FeatureInitializationLogger
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class BreweryFeature(private val plugin: JavaPlugin) {
    private var controller: BreweryController? = null

    fun initialize(featureInitLogger: FeatureInitializationLogger? = null) {
        try {
            ensureResources()
            controller = BreweryController(plugin).also { it.initialize() }
            featureInitLogger?.apply {
                setStatus("Brewery", FeatureInitializationLogger.Status.SUCCESS)
            }
        } catch (e: Exception) {
            featureInitLogger?.apply {
                setStatus("Brewery", FeatureInitializationLogger.Status.FAILURE)
                addDetailMessage("Brewery", "[Brewery] 初期化失敗: ${e.message}")
            }
            plugin.logger.warning("Brewery初期化失敗: ${e.message}")
            e.printStackTrace()
        }
    }

    fun reload() {
        ensureResources()
        if (controller == null) {
            controller = BreweryController(plugin).also { it.initialize() }
        } else {
            controller?.reload()
        }
    }

    fun shutdown() {
        controller?.shutdown()
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
