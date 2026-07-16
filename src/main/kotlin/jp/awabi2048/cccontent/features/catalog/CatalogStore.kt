package jp.awabi2048.cccontent.features.catalog

import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.UUID

enum class CatalogType(val id: String) {
    FISHING("fishing"),
    COOKING("cooking"),
    BREWERY("brewery")
}

data class CatalogEntry(
    val itemId: String,
    var discoveredAtMillis: Long = 0L,
    var obtainedCount: Long = 0L,
    var drunkCount: Long = 0L,
    var bestQuality: Double = 0.0,
    var bestCompletion: Int = 0,
    var maximumWeight: Int? = null,
    var minimumWeight: Int? = null,
    val qualityCounts: MutableMap<String, Long> = mutableMapOf()
) {
    val discovered: Boolean get() = discoveredAtMillis > 0L
}

/** 3図鑑が同じファイルとキャッシュを共有する唯一の永続ストア。 */
class CatalogStore(private val file: File) {
    private val data = mutableMapOf<UUID, MutableMap<CatalogType, MutableMap<String, CatalogEntry>>>()

    init {
        file.parentFile?.mkdirs()
        load()
    }

    @Synchronized
    fun entries(playerId: UUID, type: CatalogType): Map<String, CatalogEntry> =
        data[playerId]?.get(type)?.mapValues { (_, entry) -> entry.copy(qualityCounts = entry.qualityCounts.toMutableMap()) }
            ?: emptyMap()

    @Synchronized
    fun record(
        playerId: UUID,
        type: CatalogType,
        itemId: String,
        qualityId: String? = null,
        qualityValue: Double? = null,
        completion: Int? = null,
        weight: Int? = null,
        obtained: Boolean = true,
        drunk: Boolean = false
    ): CatalogEntry {
        val entry = data.getOrPut(playerId) { mutableMapOf() }
            .getOrPut(type) { mutableMapOf() }
            .getOrPut(itemId) { CatalogEntry(itemId) }
        if (!entry.discovered) entry.discoveredAtMillis = System.currentTimeMillis()
        if (obtained) entry.obtainedCount++
        if (drunk) entry.drunkCount++
        qualityId?.let { entry.qualityCounts[it] = (entry.qualityCounts[it] ?: 0L) + 1L }
        qualityValue?.let { entry.bestQuality = maxOf(entry.bestQuality, it.coerceIn(0.0, 100.0)) }
        completion?.let { entry.bestCompletion = maxOf(entry.bestCompletion, it.coerceIn(0, 100)) }
        weight?.let {
            entry.maximumWeight = maxOf(entry.maximumWeight ?: it, it)
            entry.minimumWeight = minOf(entry.minimumWeight ?: it, it)
        }
        save()
        return entry
    }

    @Synchronized
    fun save() {
        val output = YamlConfiguration()
        output.set("schema_version", 2)
        data.forEach { (uuid, categories) ->
            categories.forEach { (type, entries) ->
                entries.forEach { (itemId, entry) ->
                    val base = "players.$uuid.${type.id}.$itemId"
                    output.set("$base.discovered_at", entry.discoveredAtMillis)
                    output.set("$base.obtained_count", entry.obtainedCount)
                    output.set("$base.drunk_count", entry.drunkCount)
                    output.set("$base.best_quality", entry.bestQuality)
                    output.set("$base.best_completion", entry.bestCompletion)
                    output.set("$base.maximum_weight", entry.maximumWeight)
                    output.set("$base.minimum_weight", entry.minimumWeight)
                    entry.qualityCounts.forEach { (quality, count) -> output.set("$base.quality.$quality", count) }
                }
            }
        }
        output.save(file)
    }

    private fun load() {
        if (!file.exists()) return
        val input = YamlConfiguration.loadConfiguration(file)
        input.getConfigurationSection("players")?.getKeys(false)?.forEach { rawUuid ->
            val uuid = runCatching { UUID.fromString(rawUuid) }.getOrNull() ?: return@forEach
            val categories = data.getOrPut(uuid) { mutableMapOf() }
            CatalogType.entries.forEach { type ->
                val section = input.getConfigurationSection("players.$rawUuid.${type.id}") ?: return@forEach
                val entries = categories.getOrPut(type) { mutableMapOf() }
                section.getKeys(false).forEach { itemId ->
                    val base = "players.$rawUuid.${type.id}.$itemId"
                    val quality = mutableMapOf<String, Long>()
                    input.getConfigurationSection("$base.quality")?.getKeys(false)?.forEach { id ->
                        quality[id] = input.getLong("$base.quality.$id")
                    }
                    entries[itemId] = CatalogEntry(
                        itemId,
                        input.getLong("$base.discovered_at"),
                        input.getLong("$base.obtained_count"),
                        input.getLong("$base.drunk_count"),
                        input.getDouble("$base.best_quality"),
                        input.getInt("$base.best_completion"),
                        input.getObject("$base.maximum_weight", Int::class.java),
                        input.getObject("$base.minimum_weight", Int::class.java),
                        quality
                    )
                }
            }
        }
    }
}
