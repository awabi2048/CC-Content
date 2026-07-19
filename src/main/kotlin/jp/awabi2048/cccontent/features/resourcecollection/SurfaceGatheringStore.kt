package jp.awabi2048.cccontent.features.resourcecollection

import org.bukkit.block.Block
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.UUID

class SurfaceGatheringStore(private val file: File) {
    private val unavailableUntil = mutableMapOf<AreaKey, Instant>()

    init {
        load()
    }

    @Synchronized
    fun isAvailable(block: Block, now: Instant): Boolean {
        val key = AreaKey(block.world.uid, block.chunk.x, block.chunk.z)
        val until = unavailableUntil[key] ?: return true
        if (!until.isAfter(now)) {
            unavailableUntil.remove(key)
            return true
        }
        return false
    }

    @Synchronized
    fun claim(block: Block, now: Instant, recoverySeconds: Long): Boolean {
        require(recoverySeconds > 0L) { "Surface recovery seconds must be positive" }
        if (!isAvailable(block, now)) return false
        unavailableUntil[AreaKey(block.world.uid, block.chunk.x, block.chunk.z)] =
            now.plusSeconds(recoverySeconds)
        save()
        return true
    }

    @Synchronized
    fun save() {
        file.parentFile.mkdirs()
        val yaml = YamlConfiguration()
        yaml.set("schema_version", 1)
        unavailableUntil.entries.sortedBy { it.key.encoded() }.forEach { (key, until) ->
            yaml.set("areas.${key.encoded()}", until.toString())
        }
        val temporary = File(file.parentFile, "${file.name}.tmp")
        yaml.save(temporary)
        try {
            Files.move(
                temporary.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun load() {
        if (!file.isFile) return
        val yaml = YamlConfiguration.loadConfiguration(file)
        require(yaml.getInt("schema_version", -1) == 1) {
            "Unsupported surface gathering schema version"
        }
        val section = yaml.getConfigurationSection("areas") ?: return
        section.getKeys(false).forEach { encoded ->
            val key = AreaKey.decode(encoded) ?: return@forEach
            val until = yaml.getString("areas.$encoded")
                ?.let { runCatching { Instant.parse(it) }.getOrNull() }
                ?: return@forEach
            unavailableUntil[key] = until
        }
    }

    data class AreaKey(val worldId: UUID, val chunkX: Int, val chunkZ: Int) {
        fun encoded(): String = "${worldId}_${chunkX}_${chunkZ}"

        companion object {
            fun decode(value: String): AreaKey? {
                val parts = value.split('_')
                if (parts.size != 3) return null
                val worldId = runCatching { UUID.fromString(parts[0]) }.getOrNull() ?: return null
                val chunkX = parts[1].toIntOrNull() ?: return null
                val chunkZ = parts[2].toIntOrNull() ?: return null
                return AreaKey(worldId, chunkX, chunkZ)
            }
        }
    }
}
