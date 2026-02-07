package jp.awabi2048.cccontent.features.rank.job

import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.UUID

class IgnoreBlockStore(
    private val file: File
) {
    private val blocksByWorld: MutableMap<UUID, MutableSet<Long>> = mutableMapOf()

    init {
        load()
    }

    fun contains(worldUuid: UUID, packedPosition: Long): Boolean {
        return blocksByWorld[worldUuid]?.contains(packedPosition) == true
    }

    fun add(worldUuid: UUID, packedPosition: Long) {
        val set = blocksByWorld.getOrPut(worldUuid) { mutableSetOf() }
        if (set.add(packedPosition)) {
            save()
        }
    }

    private fun load() {
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            save()
            return
        }

        val config = YamlConfiguration.loadConfiguration(file)
        val worldsSection = config.getConfigurationSection("worlds") ?: return

        for (worldKey in worldsSection.getKeys(false)) {
            val worldUuid = runCatching { UUID.fromString(worldKey) }.getOrNull() ?: continue
            val serialized = worldsSection.getString(worldKey).orEmpty()
            if (serialized.isBlank()) {
                continue
            }

            val packedSet = serialized
                .split(',')
                .asSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapNotNull { token -> runCatching { java.lang.Long.parseUnsignedLong(token, 36) }.getOrNull() }
                .toMutableSet()

            if (packedSet.isNotEmpty()) {
                blocksByWorld[worldUuid] = packedSet
            }
        }
    }

    private fun save() {
        val config = if (file.exists()) YamlConfiguration.loadConfiguration(file) else YamlConfiguration()
        config.set("worlds", null)

        for ((worldUuid, packedSet) in blocksByWorld) {
            if (packedSet.isEmpty()) {
                continue
            }

            val serialized = packedSet
                .asSequence()
                .map { packed -> BlockPositionCodec.toBase36(packed) }
                .joinToString(",")

            config.set("worlds.$worldUuid", serialized)
        }

        config.save(file)
    }
}
