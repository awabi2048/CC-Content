package jp.awabi2048.cccontent.economy

import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.plugin.java.JavaPlugin
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object ContentEconomyBridge {
    private val formatter = DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.ROOT))
    private var provider: Economy? = null

    fun get(plugin: JavaPlugin): Economy? {
        ensure(plugin)
        return provider
    }

    fun has(plugin: JavaPlugin, player: OfflinePlayer, price: Double): Boolean {
        val economy = get(plugin) ?: return false
        return economy.has(player, price)
    }

    fun balance(plugin: JavaPlugin, player: OfflinePlayer): Double? {
        val economy = get(plugin) ?: return null
        return economy.getBalance(player)
    }

    fun withdraw(plugin: JavaPlugin, player: OfflinePlayer, price: Double): Boolean {
        val economy = get(plugin) ?: return false
        return economy.withdrawPlayer(player, price).transactionSuccess()
    }

    fun deposit(plugin: JavaPlugin, player: OfflinePlayer, price: Double): Boolean {
        val economy = get(plugin) ?: return false
        return economy.depositPlayer(player, price).transactionSuccess()
    }

    fun formatPrice(price: Double): String = formatter.format(price)

    private fun ensure(plugin: JavaPlugin) {
        if (provider != null) {
            return
        }
        provider = Bukkit.getServicesManager().getRegistration(Economy::class.java)?.provider
    }
}
