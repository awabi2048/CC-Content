package jp.awabi2048.cccontent.features.rank.job

import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.UUID

class IgnoreBlockStore(
    private val file: File
) {
    companion object {
        private const val SAVE_INTERVAL_MS = 5_000L
        private const val PENDING_CHANGES_THRESHOLD = 64
    }

    private val blocksByWorld: MutableMap<UUID, MutableSet<Long>> = mutableMapOf()
    private var dirty: Boolean = false
    private var pendingChanges: Int = 0
    private var lastSavedAt: Long = 0L

    init {
        load()
    }

    fun contains(worldUuid: UUID, packedPosition: Long): Boolean {
        return blocksByWorld[worldUuid]?.contains(packedPosition) == true
    }

    fun add(worldUuid: UUID, packedPosition: Long) {
        val set = blocksByWorld.getOrPut(worldUuid) { mutableSetOf() }
        if (set.add(packedPosition)) {
            markDirtyAndMaybeSave()
        }
    }

    fun clearAll() {
        if (blocksByWorld.isEmpty()) {
            return
        }
        blocksByWorld.clear()
        markDirtyAndMaybeSave(force = true)
    }

    fun getTrackedBlockCount(): Int {
        return blocksByWorld.values.sumOf { it.size }
    }

    fun flush() {
        if (!dirty) {
            return
        }
        saveInternal()
    }

    private fun load() {
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            saveInternal()
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

    private fun markDirtyAndMaybeSave(force: Boolean = false) {
        dirty = true
        pendingChanges += 1

        val now = System.currentTimeMillis()
        val intervalReached = now - lastSavedAt >= SAVE_INTERVAL_MS
        val thresholdReached = pendingChanges >= PENDING_CHANGES_THRESHOLD

        if (force || intervalReached || thresholdReached) {
            saveInternal()
        }
    }

    private fun saveInternal() {
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
        dirty = false
        pendingChanges = 0
        lastSavedAt = System.currentTimeMillis()
    }
}
