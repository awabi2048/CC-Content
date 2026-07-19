package jp.awabi2048.cccontent.features.brewery

import jp.awabi2048.cccontent.features.brewery.garden.GardenController
import jp.awabi2048.cccontent.util.FeatureInitializationLogger
import jp.awabi2048.cccontent.features.catalog.CatalogItem
import jp.awabi2048.cccontent.features.catalog.CatalogStore
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import jp.awabi2048.cccontent.features.brewery.item.BreweryItemCodec

class BreweryFeature(private val plugin: JavaPlugin, private val catalogStore: CatalogStore) {
    private var controller: BreweryController? = null
    private var gardenController: GardenController? = null
    private var preparationGateway: BreweryPreparationGateway? = null

    fun initialize(featureInitLogger: FeatureInitializationLogger? = null) {
        try {
            ensureResources()
            val loader = BrewerySettingsLoader(plugin)
            preparationGateway = DefaultBreweryPreparationGateway(
                loader,
                loader.loadRecipes(),
                BreweryItemCodec(plugin)
            )
            controller = BreweryController(plugin, catalogStore).also { it.initialize() }
            gardenController = GardenController(plugin).also { it.initialize() }
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
            runCatching { gardenController?.close() }
            gardenController = null
            runCatching { controller?.shutdown() }
            controller = null
            preparationGateway = null
            throw e
        }
    }

    fun reload() {
        ensureResources()
        gardenController?.close()
        gardenController = null
        if (controller == null) controller = BreweryController(plugin, catalogStore).also { it.initialize() }
        else controller?.reload()
        gardenController = GardenController(plugin).also { it.initialize() }
    }

    fun shutdown() {
        gardenController?.close()
        gardenController = null
        controller?.shutdown()
        controller = null
        preparationGateway = null
    }

    fun flushDirty() {
        gardenController?.flush()
        controller?.flushIfDirty()
    }

    fun catalogItems(): List<CatalogItem> = controller?.catalogItems().orEmpty()

    fun preparationGateway(): BreweryPreparationGateway? = preparationGateway

    private fun ensureResources() {
        ensureFile("config/brewery/config.yml")
        ensureFile("config/brewery/recipe.yml")
        ensureFile("config/brewery/garden.yml")
        ensureFile("config/ingredient_definition.yml")
    }

    private fun ensureFile(path: String) {
        val file = File(plugin.dataFolder, path)
        if (file.exists()) return
        file.parentFile?.mkdirs()
        plugin.saveResource(path, false)
    }
}
