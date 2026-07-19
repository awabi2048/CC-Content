package jp.awabi2048.cccontent.features.resourcecollection

import org.bukkit.block.Block
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class ForestProductHarvestStore(private val file: File) {
    private val harvestedTrees = mutableSetOf<TreeKey>()

    init {
        load()
    }

    @Synchronized
    fun isHarvested(root: Block, species: TreeSpecies): Boolean =
        key(root, species) in harvestedTrees

    @Synchronized
    fun claim(root: Block, species: TreeSpecies): Boolean {
        if (!harvestedTrees.add(key(root, species))) return false
        save()
        return true
    }

    @Synchronized
    fun save() {
        file.parentFile.mkdirs()
        val yaml = YamlConfiguration()
        yaml.set("schema_version", 1)
        yaml.set("harvested_trees", harvestedTrees.sortedBy(TreeKey::encoded).map(TreeKey::encoded))
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
            "Unsupported forest product harvest schema version"
        }
        yaml.getStringList("harvested_trees").mapNotNullTo(harvestedTrees, TreeKey::decode)
    }

    private fun key(root: Block, species: TreeSpecies): TreeKey =
        TreeKey(root.world.uid.toString(), root.world.seed, root.x, root.y, root.z, species)

    data class TreeKey(
        val worldId: String,
        val worldSeed: Long,
        val x: Int,
        val y: Int,
        val z: Int,
        val species: TreeSpecies
    ) {
        fun encoded(): String = "$worldId,$worldSeed,$x,$y,$z,${species.name}"

        companion object {
            fun decode(value: String): TreeKey? {
                val parts = value.split(',')
                if (parts.size != 6) return null
                val seed = parts[1].toLongOrNull() ?: return null
                val x = parts[2].toIntOrNull() ?: return null
                val y = parts[3].toIntOrNull() ?: return null
                val z = parts[4].toIntOrNull() ?: return null
                val species = runCatching { TreeSpecies.valueOf(parts[5]) }.getOrNull() ?: return null
                return TreeKey(parts[0], seed, x, y, z, species)
            }
        }
    }
}
