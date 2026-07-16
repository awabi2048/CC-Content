package jp.awabi2048.cccontent.features.fishing

import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.UUID

class FishingSearchStore(private val file: File) {
    private val targets = mutableMapOf<UUID, String>()

    init {
        file.parentFile?.mkdirs()
        load()
    }

    @Synchronized
    fun get(playerId: UUID): String? = targets[playerId]

    @Synchronized
    fun set(playerId: UUID, fishId: String?) {
        if (fishId == null) targets.remove(playerId) else targets[playerId] = fishId
        save()
    }

    @Synchronized
    fun save() {
        val output = YamlConfiguration()
        output.set("schema_version", 1)
        targets.forEach { (playerId, fishId) ->
            output.set("players.$playerId.target", fishId)
        }
        output.save(file)
    }

    private fun load() {
        if (!file.exists()) return
        val input = YamlConfiguration.loadConfiguration(file)
        require(input.getInt("schema_version") == 1) {
            "${file.path}.schema_version must be the integer 1"
        }
        input.getConfigurationSection("players")?.getKeys(false)?.forEach { rawId ->
            val playerId = runCatching { UUID.fromString(rawId) }.getOrNull() ?: return@forEach
            val target = input.getString("players.$rawId.target")?.takeIf { it.isNotBlank() } ?: return@forEach
            targets[playerId] = target
        }
    }
}
