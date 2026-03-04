package jp.awabi2048.cccontent.items.misc

import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.Locale

object CustomHeadConfigRegistry {
    private val variants = linkedMapOf<String, CustomHeadVariant>()

    fun initialize(plugin: JavaPlugin) {
        ensureDefaultFile(plugin)
        reload(plugin)
    }

    fun reload(plugin: JavaPlugin) {
        ensureDefaultFile(plugin)
        variants.clear()

        val configFile = getConfigFile(plugin)
        val yaml = YamlConfiguration.loadConfiguration(configFile)
        val variantsSection = yaml.getConfigurationSection("variants")

        if (variantsSection == null) {
            plugin.logger.warning("[CustomHead] variants セクションが見つかりません: ${configFile.name}")
            return
        }

        for (variantKey in variantsSection.getKeys(false).sorted()) {
            val section = variantsSection.getConfigurationSection(variantKey) ?: continue
            val loaded = runCatching { loadVariant(section, variantKey) }
            loaded.onFailure {
                plugin.logger.warning("[CustomHead] 設定読み込み失敗: $variantKey (${it.message})")
            }
            loaded.getOrNull()?.let { variant ->
                if (variants.containsKey(variant.variantId)) {
                    plugin.logger.warning("[CustomHead] variant_id が重複しています: ${variant.variantId}")
                    return@let
                }
                variants[variant.variantId] = variant
            }
        }

        plugin.logger.info("[CustomHead] バリエーション読込数: ${variants.size}")
    }

    fun getAllVariants(): List<CustomHeadVariant> = variants.values.toList()

    fun isBypassEnabled(plugin: JavaPlugin): Boolean {
        val configFile = getConfigFile(plugin)
        val yaml = YamlConfiguration.loadConfiguration(configFile)
        return yaml.getBoolean("settings.test_mode_without_hdb", false)
    }

    private fun getConfigFile(plugin: JavaPlugin): File {
        val file = File(plugin.dataFolder, "config/custom_item/custom_head.yml")
        if (!file.parentFile.exists()) {
            file.parentFile.mkdirs()
        }
        return file
    }

    private fun ensureDefaultFile(plugin: JavaPlugin) {
        val file = getConfigFile(plugin)
        if (!file.exists()) {
            plugin.saveResource("config/custom_item/custom_head.yml", false)
        }
    }

    private fun loadVariant(section: ConfigurationSection, fallbackId: String): CustomHeadVariant {
        val rawVariantId = section.getString("variant_id")?.trim().orEmpty()
        val variantId = (if (rawVariantId.isNotBlank()) rawVariantId else fallbackId)
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9_]+"), "_")
            .trim('_')
        require(variantId.isNotBlank()) { "variant_id が空です" }

        val itemMaterial = parseMaterial(section.getString("item.material"), Material.NAME_TAG)
        val itemDisplayName = colorize(section.getString("item.name") ?: "&dカスタムヘッド券")
        val itemLore = section.getStringList("item.lore").ifEmpty {
            listOf(
                "&7右クリックでヘッド選択GUIを開く",
                "&7選択後に確認ダイアログで交換"
            )
        }.map(::colorize)
        val itemCustomModelData = if (section.contains("item.custom_model_data")) {
            section.getInt("item.custom_model_data")
        } else {
            null
        }

        val guiTitle = colorize(section.getString("gui.title") ?: "&0&lカスタムヘッド選択")
        val themeIconMaterial = parseMaterial(section.getString("gui.theme_icon_material"), Material.PAPER)
        val themeIconName = colorize(section.getString("gui.theme_icon_name") ?: "&eテーマ")

        val headSection = section.getMapList("heads")
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
