package jp.awabi2048.cccontent.features.cooking

import jp.awabi2048.cccontent.features.catalog.CatalogItem
import jp.awabi2048.cccontent.features.catalog.CatalogStore
import jp.awabi2048.cccontent.features.rank.RankManager
import jp.awabi2048.cccontent.util.FeatureInitializationLogger
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class CookingFeature(
    private val plugin: JavaPlugin,
    private val rankManagerProvider: () -> RankManager?,
    private val catalogStore: CatalogStore
) {
    private var controller: UnifiedCookingController? = null
    private var vanillaRecipes: CookingVanillaRecipeController? = null
    private var items: CookingItems? = null
    private var configuration: UnifiedCookingConfiguration? = null

    fun initialize(featureInitLogger: FeatureInitializationLogger? = null) {
        try {
            ensureResources()
            if (!enabled()) {
                featureInitLogger?.setStatus("Cooking", FeatureInitializationLogger.Status.SUCCESS)
                return
            }
            start()
            featureInitLogger?.setStatus("Cooking", FeatureInitializationLogger.Status.SUCCESS)
        } catch (exception: Exception) {
            featureInitLogger?.apply {
                setStatus("Cooking", FeatureInitializationLogger.Status.FAILURE)
                addDetailMessage("Cooking", "[Cooking] 初期化失敗: ${exception.message}")
            }
            plugin.logger.severe("Cooking初期化失敗: ${exception.message}")
            throw exception
        }
    }

    fun reload() {
        shutdown()
        ensureResources()
        if (enabled()) start()
    }

    fun shutdown() {
        controller?.shutdown()
        controller = null
        vanillaRecipes?.shutdown()
        vanillaRecipes = null
        items?.unregister()
        items = null
        configuration = null
    }

    fun catalogItems(): List<CatalogItem> = configuration?.let { loaded ->
        loaded.recipes.values
            .filter { it.definition.experience > 0 }
            .map { CatalogItem(it.definition.id, it.result.baseMaterial) } +
            CookingVanillaDefinitions.all.filter { it.experience > 0 }
                .map { CatalogItem(it.id, org.bukkit.Material.POISONOUS_POTATO) }
    }.orEmpty()

    private fun start() {
        val loaded = UnifiedCookingConfigurationLoader.load(plugin.dataFolder)
        val preparations = BreweryPreparationConfigurationLoader.load(plugin.dataFolder, loaded.ingredients)
        configuration = loaded
        items = CookingItems(loaded).also(CookingItems::register)
        controller = UnifiedCookingController(plugin, rankManagerProvider, catalogStore, loaded, preparations)
            .also(UnifiedCookingController::initialize)
        vanillaRecipes = CookingVanillaRecipeController(plugin, rankManagerProvider, catalogStore)
            .also(CookingVanillaRecipeController::initialize)
    }

    private fun enabled(): Boolean {
        val file = File(plugin.dataFolder, "config/cooking/config.yml")
        val root = YamlConfiguration.loadConfiguration(file)
        require(root.get("enabled") is Boolean) { "${file.path}.enabled must be a boolean" }
        return root.getBoolean("enabled")
    }

    private fun ensureResources() {
        listOf(
            "config/cooking/config.yml",
            "config/cooking/ingredients.yml",
            "config/cooking/cutting.yml",
            "config/cooking/recipe.yml"
        ).forEach(::ensureFile)
    }

    private fun ensureFile(path: String) {
        val file = File(plugin.dataFolder, path)
        if (file.exists()) return
        file.parentFile.mkdirs()
        check(plugin.getResource(path) != null) { "Bundled resource is missing: $path" }
        plugin.saveResource(path, false)
    }
}
