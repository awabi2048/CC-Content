package jp.awabi2048.cccontent.structure

import com.sk89q.worldedit.WorldEdit
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard
import com.sk89q.worldedit.extent.clipboard.Clipboard
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats
import com.sk89q.worldedit.function.operation.ForwardExtentCopy
import com.sk89q.worldedit.function.operation.Operations
import com.sk89q.worldedit.IncompleteRegionException
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldedit.math.transform.AffineTransform
import com.sk89q.worldedit.session.ClipboardHolder
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.enginehub.linbus.tree.LinTagType
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

data class CcStructureSize(
    val x: Int,
    val y: Int,
    val z: Int
)

data class StructurePasteOptions(
    val rotationQuarter: Int = 0,
    val mirrorX: Boolean = false,
    val pasteAir: Boolean = true,
    val copyEntities: Boolean = true,
    val copyBiomes: Boolean = true
)

data class LoadedSchemStructure(
    val name: String,
    val file: File,
    val size: CcStructureSize,
    val entities: List<LoadedSchemEntity>,
    private val clipboard: Clipboard
) {
    fun paste(location: Location, options: StructurePasteOptions = StructurePasteOptions()) {
        val world = location.world ?: throw IllegalArgumentException("Location world is null: $location")
        val editSession = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))
        editSession.use { session ->
            val holder = ClipboardHolder(clipboard)
            holder.setTransform(transform(options.rotationQuarter, options.mirrorX))
            val operation = holder
                .createPaste(session)
                .to(BlockVector3.at(location.blockX, location.blockY, location.blockZ))
                .ignoreAirBlocks(!options.pasteAir)
                .copyEntities(options.copyEntities)
                .copyBiomes(options.copyBiomes)
                .build()
            Operations.complete(operation)
        }
    }

    private fun transform(rotationQuarter: Int, mirrorX: Boolean): AffineTransform {
        var transform = AffineTransform()
        if (mirrorX) {
            transform = transform.scale(-1.0, 1.0, 1.0)
        }
        val rotation = rotationQuarter.mod(4)
        if (rotation != 0) {
            transform = transform.rotateY(rotation * 90.0)
        }
        return transform
    }
}

data class LoadedSchemEntity(
    val typeId: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val scoreboardTags: Set<String>
)

data class SavedSchemStructure(
    val file: File,
    val size: CcStructureSize
)

class SchemStructureService(private val plugin: JavaPlugin) {
    fun load(relativePath: String): LoadedSchemStructure? {
        return load(File(plugin.dataFolder, relativePath))
    }

    fun load(file: File): LoadedSchemStructure? {
        if (!file.isFile || !file.name.endsWith(SCHEM_EXTENSION, ignoreCase = true)) return null
        val format = ClipboardFormats.findByFile(file)
        if (format == null) {
            plugin.logger.warning("Unsupported schematic format: ${file.path}")
            return null
        }

        return try {
            FileInputStream(file).use { input ->
                format.getReader(input).use { reader ->
                    val clipboard = reader.read()
                    val minimum = clipboard.region.minimumPoint
                    clipboard.setOrigin(minimum)
                    val dimensions = clipboard.dimensions
                    if (dimensions.x() <= 0 || dimensions.y() <= 0 || dimensions.z() <= 0) {
                        plugin.logger.warning("Invalid schematic size: ${file.path}")
                        return null
                    }
                    LoadedSchemStructure(
                        name = file.name,
                        file = file,
                        size = CcStructureSize(dimensions.x(), dimensions.y(), dimensions.z()),
                        entities = readEntities(clipboard),
                        clipboard = clipboard
                    )
                }
            }
        } catch (e: IOException) {
            plugin.logger.warning("Failed to load schematic: ${file.path} (${e.message})")
            null
        } catch (e: Exception) {
            plugin.logger.warning("Unexpected schematic load error: ${file.path} (${e.message})")
            null
        }
    }

    fun listSchemFiles(folder: File): List<File> {
        return folder.listFiles()
            ?.filter { it.isFile && it.name.endsWith(SCHEM_EXTENSION, ignoreCase = true) }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()
    }

    fun saveSelection(player: Player, relativePath: String, overwrite: Boolean = false): SavedSchemStructure {
        val targetFile = resolveDataFile(relativePath)
        if (!targetFile.name.endsWith(SCHEM_EXTENSION, ignoreCase = true)) {
            throw IllegalArgumentException("Target file must end with $SCHEM_EXTENSION: $relativePath")
        }
        if (targetFile.exists() && !overwrite) {
            throw IllegalStateException("Structure already exists: ${plugin.dataFolder.toPath().relativize(targetFile.toPath())}")
        }

        val world = BukkitAdapter.adapt(player.world)
        val actor = BukkitAdapter.adapt(player)
        val session = WorldEdit.getInstance().sessionManager.get(actor)
        val region = try {
            session.getSelection(world)
        } catch (_: IncompleteRegionException) {
            throw IllegalStateException("WorldEdit selection is incomplete")
        }

        val clipboard = BlockArrayClipboard(region)
        clipboard.setOrigin(region.minimumPoint)
        val copy = ForwardExtentCopy(world, region, clipboard, region.minimumPoint)
        copy.setCopyingEntities(true)
        copy.setCopyingBiomes(true)
        Operations.complete(copy)

        targetFile.parentFile?.mkdirs()
        val format = ClipboardFormats.findByAlias("schem")
            ?: throw IllegalStateException("WorldEdit schem format is not available")
        FileOutputStream(targetFile).use { output ->
            format.getWriter(output).use { writer ->
                writer.write(clipboard)
            }
        }

        val dimensions = clipboard.dimensions
        return SavedSchemStructure(
            file = targetFile,
            size = CcStructureSize(dimensions.x(), dimensions.y(), dimensions.z())
        )
    }

    private fun readEntities(clipboard: Clipboard): List<LoadedSchemEntity> {
        val minimum = clipboard.region.minimumPoint
        return clipboard.getEntities(clipboard.region).mapNotNull { entity ->
            val location = entity.location
            val state = entity.state ?: return@mapNotNull null
            val nbt = state.nbtReference?.value
            val tags = nbt
                ?.findListTag("Tags", LinTagType.stringTag())
                ?.value()
                ?.map { it.value() }
                ?.toSet()
                .orEmpty()
            LoadedSchemEntity(
                typeId = state.type.id(),
                x = location.x - minimum.x().toDouble(),
                y = location.y - minimum.y().toDouble(),
                z = location.z - minimum.z().toDouble(),
                scoreboardTags = tags
            )
        }
    }

    private fun resolveDataFile(relativePath: String): File {
        val normalized = relativePath.replace('\\', '/').trim().trimStart('/')
        if (normalized.isBlank() || normalized.split('/').any { it == ".." || it.isBlank() }) {
            throw IllegalArgumentException("Invalid structure path: $relativePath")
        }
        val base = plugin.dataFolder.canonicalFile
        val target = File(base, normalized).canonicalFile
        if (!target.path.startsWith(base.path + File.separator)) {
            throw IllegalArgumentException("Structure path escapes plugin data folder: $relativePath")
        }
        return target
    }

    companion object {
        const val SCHEM_EXTENSION = ".schem"
    }
}
