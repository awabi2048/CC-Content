package jp.awabi2048.cccontent.features.npc.request

import me.crylonz.deadchest.ChestData
import me.crylonz.deadchest.DeadChestAPI
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class DeadChestRecoveryService(private val plugin: JavaPlugin) {
    fun isAvailable(): Boolean = Bukkit.getPluginManager().isPluginEnabled("DeadChest")

    fun getActiveChests(player: Player): List<DeadChestSnapshot> {
        if (!isAvailable()) return emptyList()
        return runCatching {
            DeadChestAPI.getChests(player).mapIndexed { index, chest ->
                DeadChestSnapshot(index, chest, describe(chest))
            }
        }.onFailure {
            plugin.logger.warning("[OageShrine] DeadChest list fetch failed: ${it.message}")
        }.getOrDefault(emptyList())
    }

    fun recover(player: Player, chest: ChestData): Boolean {
        if (!isAvailable() || !player.isOnline) return false
        return runCatching { DeadChestAPI.giveBackChest(player, chest) }
            .onFailure { plugin.logger.warning("[OageShrine] DeadChest recovery failed: ${it.message}") }
            .getOrDefault(false)
    }

    fun chestLocation(chest: ChestData): Location = chest.chestLocation

    fun chestOwnerId(chest: ChestData): UUID = chest.playerUUID

    private fun describe(chest: ChestData): DeadChestDescription {
        val location = chest.chestLocation
        val worldName = chest.worldName ?: location.world?.name ?: "unknown"
        val inventory = chest.inventory
        val itemCount = inventory.filterNotNull().sumOf { it.amount }
        val itemKinds = inventory.filterNotNull().count { it.type.isItem }
        return DeadChestDescription(
            worldName = worldName,
            x = location.blockX,
            y = location.blockY,
            z = location.blockZ,
            createdAt = formatDate(chest.chestDate),
            itemCount = itemCount,
            itemKinds = itemKinds,
            summary = summarize(inventory)
        )
    }

    private fun summarize(items: List<ItemStack?>): String {
        val groups = items.filterNotNull()
            .filter { it.type.isItem && it.amount > 0 }
            .groupBy { it.type }
            .mapValues { (_, values) -> values.sumOf { it.amount } }
            .entries
            .sortedByDescending { it.value }
            .take(3)
            .map { "${it.key.name.lowercase(Locale.ROOT)} x${it.value}" }
        return if (groups.isEmpty()) "empty" else groups.joinToString(", ")
    }

    private fun formatDate(date: Date?): String {
        if (date == null) return "unknown"
        return SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN).format(date)
    }
}

data class DeadChestSnapshot(
    val index: Int,
    val chest: ChestData,
    val description: DeadChestDescription
)

data class DeadChestDescription(
    val worldName: String,
    val x: Int,
    val y: Int,
    val z: Int,
    val createdAt: String,
    val itemCount: Int,
    val itemKinds: Int,
    val summary: String
)
