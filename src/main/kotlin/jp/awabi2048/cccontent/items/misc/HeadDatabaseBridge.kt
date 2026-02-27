package jp.awabi2048.cccontent.items.misc

import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import java.lang.reflect.Method

object HeadDatabaseBridge {
    private var initialized = false
    private var apiInstance: Any? = null
    private var getItemHeadMethod: Method? = null

    fun isAvailable(plugin: JavaPlugin): Boolean {
        if (isBypassEnabled(plugin)) return true
        ensureInitialized(plugin)
        return apiInstance != null && getItemHeadMethod != null
    }

    fun getHead(plugin: JavaPlugin, hdbId: String): ItemStack? {
        if (isBypassEnabled(plugin)) {
            return createBypassHead(hdbId)
        }
        ensureInitialized(plugin)
        val instance = apiInstance ?: return null
        val method = getItemHeadMethod ?: return null
        return runCatching {
            method.invoke(instance, hdbId) as? ItemStack
        }.getOrNull()?.clone()?.apply { amount = 1 }
    }

    fun reset() {
        initialized = false
        apiInstance = null
        getItemHeadMethod = null
    }

    private fun ensureInitialized(plugin: JavaPlugin) {
        if (initialized) return
        initialized = true

        if (isBypassEnabled(plugin)) {
            plugin.logger.info("[CustomHead] HeadDatabaseバイパスモードで動作します")
            return
        }

        val hdbPlugin = plugin.server.pluginManager.getPlugin("HeadDatabase")
        if (hdbPlugin == null || !hdbPlugin.isEnabled) {
            return
        }

        val candidates = listOf(
            "me.arcaniax.hdb.api.HeadDatabaseAPI",
            "me.arcaniax.hdb.api.DatabaseAPI"
        )

        for (className in candidates) {
            val clazz = runCatching { Class.forName(className) }.getOrNull() ?: continue
            val method = clazz.methods.firstOrNull {
                it.name == "getItemHead" &&
                    it.parameterCount == 1 &&
                    it.parameterTypes[0] == String::class.java
            } ?: continue

            val instance = createInstance(clazz, hdbPlugin)
            if (instance != null) {
                apiInstance = instance
                getItemHeadMethod = method
                plugin.logger.info("[CustomHead] HeadDatabase API を検出しました: $className")
                return
            }
        }

        plugin.logger.warning("[CustomHead] HeadDatabase API の検出に失敗しました")
    }

    private fun createInstance(clazz: Class<*>, hdbPlugin: Plugin): Any? {
        val noArg = runCatching { clazz.getDeclaredConstructor() }.getOrNull()
        if (noArg != null) {
            return runCatching { noArg.newInstance() }.getOrNull()
        }

        val pluginCtor = clazz.declaredConstructors.firstOrNull { ctor ->
            ctor.parameterCount == 1 && Plugin::class.java.isAssignableFrom(ctor.parameterTypes[0])
        }
        if (pluginCtor != null) {
            return runCatching { pluginCtor.newInstance(hdbPlugin) }.getOrNull()
        }

        return null
    }

    private fun isBypassEnabled(plugin: JavaPlugin): Boolean {
        return plugin.config.getBoolean("misc.custom_head.test_mode_without_hdb", false)
    }

    private fun createBypassHead(hdbId: String): ItemStack {
        val item = ItemStack(org.bukkit.Material.PLAYER_HEAD)
        val meta: ItemMeta? = item.itemMeta
        meta?.setDisplayName("§fテストヘッド §7(HDB:$hdbId)")
        meta?.lore = mutableListOf(
            "§7HeadDatabase バイパスモード",
            "§7実ID: $hdbId"
        )
        item.itemMeta = meta
        return item
    }
}
