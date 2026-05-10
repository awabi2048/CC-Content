package jp.awabi2048.cccontent.features.npc.shop

import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.Locale

object OageShrineShopConfig {
    private const val CONFIG_PATH = "config/npc/oage_shrine.yml"

    data class ConfigData(
        val tabs: List<OageShrineShopTabDefinition>
    )

    fun ensureDefaultFile(plugin: JavaPlugin) {
        val file = getConfigFile(plugin)
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            plugin.saveResource(CONFIG_PATH, false)
        }
    }

    fun load(plugin: JavaPlugin): ConfigData {
        ensureDefaultFile(plugin)
        val yaml = YamlConfiguration.loadConfiguration(getConfigFile(plugin))
        val tabsSection = yaml.getConfigurationSection("tabs")
            ?: throw IllegalStateException("tabs 設定が見つかりません")

        val tabs = tabsSection.getKeys(false).sorted().mapNotNull { key ->
            val section = tabsSection.getConfigurationSection(key) ?: return@mapNotNull null
            loadTab(key, section)
        }

        if (tabs.isEmpty()) {
            throw IllegalStateException("ショップタブが1件も定義されていません")
        }

        return ConfigData(tabs)
    }

    private fun loadTab(tabId: String, section: ConfigurationSection): OageShrineShopTabDefinition? {
        val normalizedTabId = normalizeId(section.getString("tab_id") ?: tabId)
        val itemsSection = section.getConfigurationSection("items") ?: return null
        val items = itemsSection.getKeys(false).sorted().mapNotNull { goodsId ->
            val itemSection = itemsSection.getConfigurationSection(goodsId) ?: return@mapNotNull null
            loadItem(goodsId, itemSection)
        }.take(14)

        if (items.isEmpty()) {
            return null
        }

        return OageShrineShopTabDefinition(
            tabId = normalizedTabId,
            purchaseLimitDaily = section.getIntOrNull("purchase_limit_daily"),
            purchaseLimitWeekly = section.getIntOrNull("purchase_limit_weekly"),
            items = items
        )
    }

    private fun loadItem(goodsId: String, section: ConfigurationSection): OageShrineShopItemDefinition? {
        val price = section.getDoubleOrNull("price") ?: return null
        val itemId = section.getString("item_id")?.trim().orEmpty()
        if (itemId.isBlank()) {
            return null
        }

        return OageShrineShopItemDefinition(
            goodsId = normalizeId(goodsId),
            price = price,
            purchaseLimitDaily = section.getIntOrNull("purchase_limit_daily"),
            purchaseLimitWeekly = section.getIntOrNull("purchase_limit_weekly"),
            itemId = itemId,
            displayNameOverride = section.getString("display_name")?.trim().orEmpty().takeIf { it.isNotBlank() }
                ?: section.getString("name")?.trim().orEmpty().takeIf { it.isNotBlank() },
            source = parseSource(itemId)
        )
    }

    private fun parseSource(itemId: String): OageShrineShopItemSource {
        val trimmed = itemId.trim()
        val headId = when {
            trimmed.startsWith("head_database:", ignoreCase = true) -> trimmed.substringAfter(':').trim()
            trimmed.startsWith("hdb=", ignoreCase = true) -> trimmed.substringAfter('=').trim()
            trimmed.startsWith("hdb:", ignoreCase = true) -> trimmed.substringAfter(':').trim()
            else -> null
        }
        if (headId != null) {
            return HeadDatabaseShopItemSource(headId)
        }

        val customItemId = when {
            trimmed.startsWith("custom_item:", ignoreCase = true) -> trimmed.substringAfter(':').trim()
            else -> trimmed
        }
        return CustomItemShopItemSource(customItemId)
    }

    private fun getConfigFile(plugin: JavaPlugin): File {
        val file = File(plugin.dataFolder, CONFIG_PATH)
        file.parentFile?.mkdirs()
        return file
    }

    private fun normalizeId(raw: String): String {
        return raw.trim().lowercase(Locale.ROOT).replace(Regex("[^a-z0-9_]+"), "_").trim('_')
    }

    private fun ConfigurationSection.getIntOrNull(path: String): Int? {
        if (!contains(path)) return null
        return get(path).let { value ->
            when (value) {
                is Number -> value.toInt()
                is String -> value.toIntOrNull()
                else -> null
            }
        }
    }

    private fun ConfigurationSection.getDoubleOrNull(path: String): Double? {
        if (!contains(path)) return null
        return get(path).let { value ->
            when (value) {
                is Number -> value.toDouble()
                is String -> value.toDoubleOrNull()
                else -> null
            }
        }
    }
}
