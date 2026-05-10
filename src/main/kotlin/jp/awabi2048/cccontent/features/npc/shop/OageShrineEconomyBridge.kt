package jp.awabi2048.cccontent.features.npc.shop

import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.plugin.java.JavaPlugin
import java.lang.reflect.Method
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object OageShrineEconomyBridge {
    private val formatter = DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.ROOT))
    private var provider: Any? = null
    private var hasMethod: Method? = null
    private var withdrawMethod: Method? = null
    private var depositMethod: Method? = null

    fun get(plugin: JavaPlugin): Any? {
        ensure(plugin)
        return provider
    }

    fun has(plugin: JavaPlugin, player: OfflinePlayer, price: Double): Boolean {
        ensure(plugin)
        val target = provider ?: return false
        val method = hasMethod ?: return false
        return runCatching { method.invoke(target, player, price) as? Boolean ?: false }.getOrDefault(false)
    }

    fun withdraw(plugin: JavaPlugin, player: OfflinePlayer, price: Double): Boolean {
        ensure(plugin)
        val target = provider ?: return false
        val method = withdrawMethod ?: return false
        return runCatching {
            val result = method.invoke(target, player, price)
            when (result) {
                is Boolean -> result
                else -> true
            }
        }.getOrDefault(false)
    }

    fun deposit(plugin: JavaPlugin, player: OfflinePlayer, price: Double): Boolean {
        ensure(plugin)
        val target = provider ?: return false
        val method = depositMethod ?: return false
        return runCatching {
            val result = method.invoke(target, player, price)
            when (result) {
                is Boolean -> result
                else -> true
            }
        }.getOrDefault(false)
    }

    fun formatPrice(price: Double): String = formatter.format(price)

    private fun ensure(plugin: JavaPlugin) {
        if (provider != null && hasMethod != null && withdrawMethod != null && depositMethod != null) {
            return
        }

        val reg = Bukkit.getServicesManager().getRegistration(Class.forName("net.milkbowl.vault.economy.Economy")) ?: return
        provider = reg.provider
        val clazz = provider?.javaClass ?: return
        hasMethod = clazz.methods.firstOrNull { it.name == "has" && it.parameterCount >= 2 }
        withdrawMethod = clazz.methods.firstOrNull { it.name == "withdrawPlayer" && it.parameterCount >= 2 }
        depositMethod = clazz.methods.firstOrNull { it.name == "depositPlayer" && it.parameterCount >= 2 }
        plugin.logger.info("[OageShrine] Vault 経済プロバイダを検出しました: ${clazz.name}")
    }
}
