package jp.awabi2048.cccontent.features.npc.request

import me.crylonz.deadchest.ChestData
import me.crylonz.deadchest.DeadChestAPI
import me.crylonz.deadchest.DeadChestLoader
import me.crylonz.deadchest.DeadChestManager
import org.bukkit.Bukkit
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
            val apiChests = DeadChestAPI.getChests(player)
            val chests = apiChests.ifEmpty {
                DeadChestLoader.getChestDataCache().chestData.filter { it.playerUUID == player.uniqueId }
            }
            chests.mapIndexed { index, chest ->
                DeadChestSnapshot(index, chest, identity(chest), describe(chest))
            }
        }.onFailure {
            plugin.logger.warning("[OageShrine] DeadChest list fetch failed: ${it.message}")
        }.getOrDefault(emptyList())
    }

    fun getLatestChests(player: Player, limit: Int = 5): List<DeadChestSnapshot> {
        return getActiveChests(player)
            .sortedByDescending { it.chest.chestDate?.time ?: Long.MIN_VALUE }
            .take(limit)
    }

    fun timeUntilLost(chest: ChestData): String {
        val remaining = DeadChestManager.getCurrentPhaseRemainingMillis(chest, Date())
        if (remaining < 0) return "無期限"
        if (remaining <= 0) return "§c期限切れ"
        val sec = remaining / 1000
        return when {
            sec >= 3600 -> "あと${sec / 3600}時間${(sec % 3600) / 60}分"
            sec >= 60 -> "あと${sec / 60}分${sec % 60}秒"
            else -> "まもなく"
        }
    }

    fun findArmorItemName(chest: ChestData): String? {
        return chest.inventory.filterNotNull().firstOrNull { item ->
            val name = item.type.name
            name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") ||
            name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS") ||
            name == "ELYTRA" || name == "TURTLE_HELMET"
        }?.let { item ->
            item.type.name.lowercase(Locale.ROOT).replace('_', ' ')
        }
    }

    fun canRecoverToInventory(player: Player, chest: ChestData): Boolean {
        val contents = chest.inventory.filterNotNull().filter { it.type.isItem && it.amount > 0 }
        if (contents.isEmpty()) return true

        val simulated = player.inventory.storageContents.map { it?.clone() }.toMutableList()
        for (item in contents) {
            var remaining = item.amount
            for (index in simulated.indices) {
                val existing = simulated[index] ?: continue
                if (!existing.isSimilar(item)) continue
                val space = existing.maxStackSize - existing.amount
                if (space <= 0) continue
                val moved = remaining.coerceAtMost(space)
                existing.amount += moved
                remaining -= moved
                if (remaining <= 0) break
            }
            while (remaining > 0) {
                val emptyIndex = simulated.indexOfFirst { it == null || it.type.isAir }
                if (emptyIndex < 0) return false
                val moved = remaining.coerceAtMost(item.maxStackSize)
                val clone = item.clone()
                clone.amount = moved
                simulated[emptyIndex] = clone
                remaining -= moved
            }
        }
        return true
    }

    fun recoverToInventory(player: Player, chest: ChestData): Boolean {
        if (!isAvailable() || !player.isOnline) return false
        return runCatching {
            if (!canRecoverToInventory(player, chest)) return false
            val contents = chest.inventory.filterNotNull().map { it.clone() }.toTypedArray()
            if (!DeadChestAPI.removeChest(chest)) return false
            val leftovers = player.inventory.addItem(*contents)
            leftovers.values.forEach { player.world.dropItemNaturally(player.location, it) }
            if (leftovers.isNotEmpty()) {
                plugin.logger.warning("[OageShrine] DeadChest recovery overflowed after capacity check: player=${player.uniqueId}")
            }
            true
        }
            .onFailure { plugin.logger.warning("[OageShrine] DeadChest recovery failed: ${it.message}") }
            .getOrDefault(false)
    }

    private fun identity(chest: ChestData): DeadChestIdentity {
        val location = chest.chestLocation
        val worldName = chest.worldName ?: location.world?.name ?: "unknown"
        return DeadChestIdentity(
            ownerId = chest.playerUUID,
            worldName = worldName,
            x = location.blockX,
            y = location.blockY,
            z = location.blockZ,
            createdAtMillis = chest.chestDate?.time
        )
    }

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
            summary = summarize(inventory),
            timeUntilLost = timeUntilLost(chest),
            armorItemName = findArmorItemName(chest)
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
    val identity: DeadChestIdentity,
    val description: DeadChestDescription
)

data class DeadChestIdentity(
    val ownerId: UUID,
    val worldName: String,
    val x: Int,
    val y: Int,
    val z: Int,
    val createdAtMillis: Long?
)

data class DeadChestDescription(
    val worldName: String,
    val x: Int,
    val y: Int,
    val z: Int,
    val createdAt: String,
    val itemCount: Int,
    val itemKinds: Int,
    val summary: String,
    val timeUntilLost: String,
    val armorItemName: String?
)
