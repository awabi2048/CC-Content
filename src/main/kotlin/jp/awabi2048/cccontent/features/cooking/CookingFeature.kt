package jp.awabi2048.cccontent.features.cooking

import jp.awabi2048.cccontent.util.FeatureInitializationLogger
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class CookingFeature(private val plugin: JavaPlugin) {
    fun initialize(featureInitLogger: FeatureInitializationLogger? = null) {
        try {
            ensureResources()
            featureInitLogger?.apply {
                setStatus("Cooking", FeatureInitializationLogger.Status.SUCCESS)
            }
        } catch (e: Exception) {
            featureInitLogger?.apply {
                setStatus("Cooking", FeatureInitializationLogger.Status.FAILURE)
                addDetailMessage("Cooking", "[Cooking] 初期化失敗: ${e.message}")
            }
            plugin.logger.warning("Cooking初期化失敗: ${e.message}")
            e.printStackTrace()
        }
    }

    fun reload() {
        ensureResources()
    }

    private fun ensureResources() {
        ensureFile("cooking/config.yml")
        ensureFile("recipe/cooking.yml")
        ensureFile("recipe/ingredient_definition.yml")
    }

    private fun ensureFile(path: String) {
        val file = File(plugin.dataFolder, path)
        if (file.exists()) return
        file.parentFile?.mkdirs()
        plugin.saveResource(path, false)
    }
}
