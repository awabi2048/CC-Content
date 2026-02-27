package jp.awabi2048.cccontent.items.misc

import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.Locale

object CustomHeadConfigRegistry {
    private val variants = linkedMapOf<String, CustomHeadVariant>()

    fun initialize(plugin: JavaPlugin) {
        ensureDefaultFiles(plugin)
        reload(plugin)
    }

    fun reload(plugin: JavaPlugin) {
        ensureDefaultFiles(plugin)
        variants.clear()

        val baseDir = getConfigDir(plugin)
        val files = baseDir.listFiles { file -> file.isFile && file.extension.equals("yml", ignoreCase = true) }
            ?.sortedBy { it.name.lowercase(Locale.ROOT) }
            ?: emptyList()

        for (file in files) {
            val loaded = runCatching { loadVariant(file) }
            loaded.onFailure {
                plugin.logger.warning("[CustomHead] 設定読み込み失敗: ${file.name} (${it.message})")
            }
            loaded.getOrNull()?.let { variant ->
                if (variants.containsKey(variant.variantId)) {
                    plugin.logger.warning("[CustomHead] variant_id が重複しています: ${variant.variantId} (${file.name})")
                    return@let
                }
                variants[variant.variantId] = variant
            }
        }

        plugin.logger.info("[CustomHead] バリエーション読込数: ${variants.size}")
    }

    fun getAllVariants(): List<CustomHeadVariant> = variants.values.toList()

    private fun getConfigDir(plugin: JavaPlugin): File {
        val dir = File(plugin.dataFolder, "misc/custom_heads")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun ensureDefaultFiles(plugin: JavaPlugin) {
        val dir = getConfigDir(plugin)
        val defaults = listOf(
            "misc/custom_heads/sakura.yml",
            "misc/custom_heads/halloween.yml"
        )
        for (resourcePath in defaults) {
            val outFile = File(plugin.dataFolder, resourcePath)
            if (!outFile.exists()) {
                plugin.saveResource(resourcePath, false)
            }
        }
        if (!dir.exists()) {
            dir.mkdirs()
        }
    }

    private fun loadVariant(file: File): CustomHeadVariant {
        val yaml = YamlConfiguration.loadConfiguration(file)

        val fallbackId = file.nameWithoutExtension.lowercase(Locale.ROOT)
        val rawVariantId = yaml.getString("variant_id")?.trim().orEmpty()
        val variantId = (if (rawVariantId.isNotBlank()) rawVariantId else fallbackId)
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9_]+"), "_")
            .trim('_')
        require(variantId.isNotBlank()) { "variant_id が空です" }

        val itemMaterial = parseMaterial(yaml.getString("item.material"), Material.NAME_TAG)
        val itemDisplayName = colorize(yaml.getString("item.name") ?: "&dカスタムヘッド券")
        val itemLore = yaml.getStringList("item.lore").ifEmpty {
            listOf(
                "&7右クリックでヘッド選択GUIを開く",
                "&7選択後に確認ダイアログで交換"
            )
        }.map(::colorize)
        val itemCustomModelData = if (yaml.contains("item.custom_model_data")) {
            yaml.getInt("item.custom_model_data")
        } else {
            null
        }

        val guiTitle = colorize(yaml.getString("gui.title") ?: "&0&lカスタムヘッド選択")
        val themeIconMaterial = parseMaterial(yaml.getString("gui.theme_icon_material"), Material.PAPER)
        val themeIconName = colorize(yaml.getString("gui.theme_icon_name") ?: "&eテーマ")

        val headSection = yaml.getMapList("heads")
        val heads = headSection.mapNotNull { row ->
            val id = (row["hdb_id"] ?: row["id"])?.toString()?.trim().orEmpty()
            if (id.isBlank()) return@mapNotNull null
            val displayName = row["name"]?.toString()?.let(::colorize)
            val lore = when (val rawLore = row["lore"]) {
                is List<*> -> rawLore.mapNotNull { it?.toString() }.map(::colorize)
                else -> emptyList()
            }
            CustomHeadChoice(
                hdbId = id,
                displayName = displayName,
                lore = lore
            )
        }

        require(heads.isNotEmpty()) { "heads が1件も定義されていません" }

        return CustomHeadVariant(
            variantId = variantId,
            itemMaterial = itemMaterial,
            itemDisplayName = itemDisplayName,
            itemLore = itemLore,
            itemCustomModelData = itemCustomModelData,
            guiTitle = guiTitle,
            themeIconMaterial = themeIconMaterial,
            themeIconName = themeIconName,
            heads = heads
        )
    }

    private fun parseMaterial(raw: String?, default: Material): Material {
        val name = raw?.trim().orEmpty()
        return if (name.isBlank()) {
            default
        } else {
            Material.matchMaterial(name.uppercase(Locale.ROOT)) ?: default
        }
    }

    private fun colorize(input: String): String = input.replace('&', '§')
}
