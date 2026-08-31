package jp.awabi2048.cccontent.features.brewery

import jp.awabi2048.cccontent.util.FeatureInitializationLogger
import jp.awabi2048.cccontent.features.catalog.CatalogItem
import jp.awabi2048.cccontent.features.catalog.CatalogStore
import jp.awabi2048.cccontent.features.processing.ProcessingEquipmentService
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class BreweryFeature(
    private val plugin: JavaPlugin,
    private val catalogStore: CatalogStore,
    private val processingEquipmentService: ProcessingEquipmentService,
) {
    private var controller: BreweryController? = null

    fun initialize(featureInitLogger: FeatureInitializationLogger? = null) {
        try {
            ensureResources()
            retireGardenLedger()
            // 先に参照を公開してから初期化します。初期化途中で失敗しても catch 節が
            // 共有設備プロバイダーを登録済みコントローラー経由で確実に解除できます。
            val createdController = BreweryController(plugin, catalogStore, processingEquipmentService)
            controller = createdController
            createdController.initialize()
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
            runCatching { controller?.shutdown() }
            controller = null
            throw e
        }
    }

    fun reload() {
        ensureResources()
        if (controller == null) {
            val createdController = BreweryController(plugin, catalogStore, processingEquipmentService)
            controller = createdController
            createdController.initialize()
        } else {
            controller?.reload()
        }
    }

    fun shutdown() {
        controller?.shutdown()
        controller = null
    }

    fun flushDirty() {
        controller?.flushIfDirty()
    }

    fun catalogItems(): List<CatalogItem> = controller?.catalogItems().orEmpty()

    private fun ensureResources() {
        ensureFile("config/brewery/config.yml")
        ensureFile("config/brewery/recipes.yml")
    }

    private fun ensureFile(path: String) {
        val file = File(plugin.dataFolder, path)
        if (file.exists()) return
        file.parentFile?.mkdirs()
        plugin.saveResource(path, false)
    }

    private fun retireGardenLedger() {
        val ledger = File(plugin.dataFolder, "data/brewery/garden.yml")
        if (!ledger.exists()) return
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val backup = File(plugin.dataFolder, "data/migration/$stamp/data/brewery/garden.yml")
        backup.parentFile.mkdirs()
        check(ledger.copyTo(backup, overwrite = false).exists()) { "Garden台帳のバックアップに失敗しました" }
        check(ledger.delete()) { "Garden台帳の削除に失敗しました" }
        plugin.logger.info("[GardenMigration] Garden台帳をバックアップし、管理対象から解除しました: ${backup.path}")
    }
}
