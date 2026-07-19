package jp.awabi2048.cccontent.gui

import com.awabi2048.ccsystem.CCSystem

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

data class PendingVirtualItem(
    val id: String,
    val feature: String,
    val playerId: UUID,
    val playerName: String,
    val createdAt: String,
    val viewId: String,
    val virtualSlot: Int,
    val source: String,
    val sourceSlot: Int?,
    val item: ItemStack
)

class VirtualInventoryEscrowService(private val plugin: JavaPlugin) {
    private val file = File(plugin.dataFolder, "data/virtual_inventory/pending_returns.yml")
    private val records = linkedMapOf<String, PendingVirtualItem>()
    private val zoneId get() = CCSystem.getAPI().getSharedClockService().zoneId

    fun initialize() {
        records.clear()
        if (!file.exists()) return
        val config = YamlConfiguration.loadConfiguration(file)
        val section = config.getConfigurationSection("transactions") ?: return
        for (id in section.getKeys(false)) {
            val path = "transactions.$id"
            val playerId = section.getString("$id.player_uuid")?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: continue
            val item = section.getItemStack("$id.item") ?: continue
            records[id] = PendingVirtualItem(
                id = id,
                feature = section.getString("$id.feature").orEmpty(),
                playerId = playerId,
                playerName = section.getString("$id.player_name").orEmpty(),
                createdAt = section.getString("$id.created_at").orEmpty(),
                viewId = section.getString("$id.view_id").orEmpty(),
                virtualSlot = section.getInt("$id.virtual_slot"),
                source = section.getString("$id.source").orEmpty(),
                sourceSlot = section.get("$id.source_slot")?.let { section.getInt("$id.source_slot") },
                item = item
            )
        }
    }

    @Synchronized
    fun reserve(
        feature: String,
        playerId: UUID,
        playerName: String,
        viewId: String,
        virtualSlot: Int,
        source: String,
        sourceSlot: Int?,
        item: ItemStack
    ): PendingVirtualItem {
        val record = PendingVirtualItem(
            id = UUID.randomUUID().toString(),
            feature = feature,
            playerId = playerId,
            playerName = playerName,
            createdAt = OffsetDateTime.now(zoneId).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            viewId = viewId,
            virtualSlot = virtualSlot,
            source = source,
            sourceSlot = sourceSlot,
            item = item.clone()
        )
        records[record.id] = record
        runCatching { save() }.onFailure {
            records.remove(record.id)
            throw it
        }
        return record
    }

    @Synchronized
    fun updateItem(id: String, item: ItemStack) {
        val current = records[id] ?: return
        records[id] = current.copy(item = item.clone())
        save()
    }

    @Synchronized
    fun resolve(id: String) {
        val removed = records.remove(id) ?: return
        runCatching { save() }.onFailure {
            records[id] = removed
            throw it
        }
    }

    @Synchronized
    fun pendingFor(playerId: UUID, feature: String): List<PendingVirtualItem> {
        return records.values
            .filter { it.playerId == playerId && it.feature == feature }
            .map { it.copy(item = it.item.clone()) }
    }

    private fun save() {
        file.parentFile.mkdirs()
        val config = YamlConfiguration()
        records.forEach { (id, record) ->
            val path = "transactions.$id"
            config.set("$path.feature", record.feature)
            config.set("$path.player_uuid", record.playerId.toString())
            config.set("$path.player_name", record.playerName)
            config.set("$path.created_at", record.createdAt)
            config.set("$path.view_id", record.viewId)
            config.set("$path.virtual_slot", record.virtualSlot)
            config.set("$path.source", record.source)
            config.set("$path.source_slot", record.sourceSlot)
            config.set("$path.item", record.item)
        }
        val temporary = File(file.parentFile, "${file.name}.tmp")
        config.save(temporary)
        try {
            Files.move(
                temporary.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: Exception) {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
