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
import com.sk89q.worldedit.regions.CuboidRegion
import com.sk89q.worldedit.util.concurrency.LazyReference
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.enginehub.linbus.tree.LinCompoundTag
import org.enginehub.linbus.tree.LinListTag
import org.enginehub.linbus.tree.LinStringTag
import org.enginehub.linbus.tree.LinTagType
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlin.math.floor

data class CcStructureSize(
    val x: Int,
    val y: Int,
    val z: Int
)

data class StructureMarkerValidation(
    val isValid: Boolean,
    val missingMarkers: List<String>,
    val extraMarkers: List<String>,
    val warnings: List<String>
) {
    fun hasIssues(): Boolean = !missingMarkers.isEmpty() || !extraMarkers.isEmpty() || !warnings.isEmpty()
}

enum class StructureCategory {
    ARENA,
    ARENA_LIFT,
    SUKIMA_DUNGEON
}

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
            val structureTransform = StructureTransform(options.rotationQuarter, options.mirrorX)
            val bounds = structureTransform.bounds(size.x, size.z)
            val holder = ClipboardHolder(clipboard)
            holder.setTransform(structureTransform.toWorldEditTransform())
            val operation = holder
                .createPaste(session)
                .to(
                    BlockVector3.at(
                        location.blockX - bounds.minX,
                        location.blockY,
                        location.blockZ - bounds.minZ
                    )
                )
                .ignoreAirBlocks(!options.pasteAir)
                .copyEntities(options.copyEntities)
                .copyBiomes(options.copyBiomes)
                .build()
            Operations.complete(operation)
        }
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
    val size: CcStructureSize,
    val transform: StructureTransform,
    val validation: StructureMarkerValidation? = null
) {
    val facing: CardinalDirection get() = CardinalDirection.entries[transform.normalizedQuarter]
}

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
                    // 既存schemには保存元ワールドの Pos / UUID / Paper 系NBTを持つMarkerが混在するため、
                    // 読み込み時にも正規化して、貼り付け時の実体座標をトップレベルのローカルPosに揃える。
                    normalizeMarkerEntityNbtForSave(clipboard)
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

    fun saveSelection(
        player: Player,
        relativePath: String,
        overwrite: Boolean = false,
        facing: CardinalDirection? = null,
        force: Boolean = false
    ): SavedSchemStructure {
        val targetFile = resolveDataFile(relativePath)
        if (!targetFile.name.endsWith(SCHEM_EXTENSION, ignoreCase = true)) {
            throw IllegalArgumentException("Target file must end with $SCHEM_EXTENSION: $relativePath")
        }
        if (targetFile.exists() && !overwrite) {
            throw IllegalStateException("Structure already exists: $relativePath")
        }

        val world = BukkitAdapter.adapt(player.world)
        val actor = BukkitAdapter.adapt(player)
        val session = WorldEdit.getInstance().sessionManager.get(actor)
        val region = try {
            session.getSelection(world)
        } catch (_: IncompleteRegionException) {
            throw IllegalStateException("WorldEdit selection is incomplete")
        }

        val sourceClipboard = BlockArrayClipboard(region)
        sourceClipboard.setOrigin(region.minimumPoint)
        val copy = ForwardExtentCopy(world, region, sourceClipboard, region.minimumPoint)
        copy.setCopyingEntities(true)
        copy.setCopyingBiomes(true)
        Operations.complete(copy)

        val effectiveTransform = determineEffectiveTransform(relativePath, sourceClipboard, facing, player)
        val savedClipboard = normalizeStructure(sourceClipboard, effectiveTransform)

        relabelConnectionMarkers(relativePath, savedClipboard)
        normalizeMarkerEntityNbtForSave(savedClipboard)

        val validation = validateClipboardMarkers(relativePath, savedClipboard)
        if (validation.hasIssues()) {
            val issues = mutableListOf<String>()
            if (validation.missingMarkers.isNotEmpty())
                issues.add("missing:${validation.missingMarkers.joinToString(",")}")
            if (validation.extraMarkers.isNotEmpty())
                issues.add("extra:${validation.extraMarkers.joinToString(",")}")
            if (validation.warnings.isNotEmpty())
                issues.add("warn:${validation.warnings.joinToString(",")}")
            plugin.logger.warning("[Structure] Marker validation for $relativePath: ${issues.joinToString("; ")}")
        }
        if (!validation.isValid && !force) {
            val details = (validation.missingMarkers + validation.extraMarkers).joinToString(", ")
            throw IllegalStateException("Structure validation failed: $details (use --force to save anyway)")
        }

        targetFile.parentFile?.mkdirs()
        val format = ClipboardFormats.findByAlias("schem")
            ?: throw IllegalStateException("WorldEdit schem format is not available")
        val temporaryFile = File(targetFile.parentFile, ".${targetFile.name}.${UUID.randomUUID()}.tmp")
        try {
            FileOutputStream(temporaryFile).use { output ->
                format.getWriter(output).use { writer ->
                    writer.write(savedClipboard)
                }
            }
            replaceAtomically(temporaryFile, targetFile, overwrite)
        } finally {
            temporaryFile.delete()
        }

        val dimensions = savedClipboard.dimensions
        return SavedSchemStructure(
            file = targetFile,
            size = CcStructureSize(dimensions.x(), dimensions.y(), dimensions.z()),
            transform = effectiveTransform,
            validation = validation
        )
    }

    /**
     * [source] に [transform] を適用し、正準形 (in=NORTH, out=EAST) に正規化した
     * 新しい Clipboard を返す。
     * transform が恒等変換の場合は [source] をそのまま返す。
     */
    private fun normalizeStructure(source: Clipboard, transform: StructureTransform): Clipboard {
        if (transform.normalizedQuarter == 0 && !transform.mirrorX) return source
        return rotateClipboard(source, transform)
    }

    /**
     * 回転後（NORTH 正規形）クリップボード上の Arena コネクションマーカーの in/out タグを、
     * schema 定義に基づいて位置から再設定する。
     *
     * normalizeToNorth でクリップボード全体が回転されるが、Marker エンティティの
     * scoreboardTags は回転されない。そのため、回転後のマーカー位置と schema.inSide/outSides を
     * 照合し、正しいタグを付与し直す必要がある。
     *
     * これにより、ユーザーが schema と異なる向きで in/out タグを付けて保存した場合でも、
     * 自動的に正規形のタグ付けに直される。
     */
    private fun relabelConnectionMarkers(relativePath: String, clipboard: Clipboard) {
        val path = relativePath.replace('\\', '/').lowercase()
        if (!path.contains("structures/arena/")) return

        val fileName = path.substringAfterLast('/').removeSuffix(".schem")
        val typeKeyword = fileName.substringBefore('.')
        val schema = StructureSchemas.arena(typeKeyword) ?: return
        if (!StructureSchemas.arenaRequiresConnectionMarkers(fileName)) return

        val dims = clipboard.dimensions
        val size = CcStructureSize(dims.x(), dims.y(), dims.z())
        val maxX = size.x - 1
        val maxZ = size.z - 1
        val minPoint = clipboard.region.minimumPoint
        val region = clipboard.region
        for (entity in clipboard.getEntities(region)) {
            val state = entity.state ?: continue
            if (state.type.id() != StructureSchemas.MARKER_ENTITY_TYPE) continue

            val nbt = state.nbtReference?.value
            val currentTags = nbt
                ?.findListTag("Tags", LinTagType.stringTag())
                ?.value()
                ?.map { it.value() }
                ?.toSet()
                .orEmpty()
            val isConnectionMarker = StructureSchemas.ARENA_CONNECTION_TAGS.any { it in currentTags }
            if (!isConnectionMarker) continue

            val location = entity.location
            val blockX = floor(location.x - minPoint.x().toDouble()).toInt()
            val blockZ = floor(location.z - minPoint.z().toDouble()).toInt()
            val touchedSides = buildList {
                if (blockX == 0) add(CardinalDirection.WEST)
                if (blockX == maxX) add(CardinalDirection.EAST)
                if (blockZ == 0) add(CardinalDirection.NORTH)
                if (blockZ == maxZ) add(CardinalDirection.SOUTH)
            }
            if (touchedSides.size != 1) continue
            val side = touchedSides.single()

            val expectedRole = when (side) {
                schema.inSide -> StructureSchemas.ARENA_CONNECTION_IN_TAG
                in schema.outSides -> StructureSchemas.ARENA_CONNECTION_OUT_TAG
                else -> null
            }
            val newTags = currentTags
                .filterNot { it in StructureSchemas.ARENA_CONNECTION_TAGS }
                .toMutableSet()
            if (expectedRole != null) {
                newTags.add(expectedRole)
            }

            if (newTags == currentTags) continue

            val newList = LinListTag.builder(LinTagType.stringTag())
                .addAll(newTags.map { LinStringTag.of(it) })
                .build()
            val newNbt = (nbt?.toBuilder() ?: LinCompoundTag.builder())
                .put("Tags", newList)
                .build()

            state.nbtReference = LazyReference.computed(newNbt)
        }
    }

    private fun normalizeMarkerEntityNbtForSave(clipboard: Clipboard) {
        val region = clipboard.region
        for (entity in clipboard.getEntities(region)) {
            val state = entity.state ?: continue
            if (state.type.id() != StructureSchemas.MARKER_ENTITY_TYPE) continue

            val nbt = state.nbtReference?.value
            val tags = nbt
                ?.findListTag("Tags", LinTagType.stringTag())
                ?.value()
                ?.map { it.value() }
                ?.toSet()
                .orEmpty()
            val tagList = LinListTag.builder(LinTagType.stringTag())
                .addAll(tags.map { LinStringTag.of(it) })
                .build()

            // Marker は構造上の制御点なので、保存時はワールド依存NBTを持ち込まずタグだけを永続化する。
            val normalizedNbt = LinCompoundTag.builder()
                .put("Tags", tagList)
                .build()
            state.nbtReference = LazyReference.computed(normalizedNbt)
        }
    }

    private fun rotateClipboard(source: Clipboard, transform: StructureTransform): Clipboard {
        val sourceDims = source.dimensions
        val bounds = transform.bounds(sourceDims.x(), sourceDims.z())
        val targetX = bounds.width
        val targetZ = bounds.depth
        val targetY = sourceDims.y()
        val targetRegion = CuboidRegion(
            null,
            BlockVector3.at(0, 0, 0),
            BlockVector3.at(targetX - 1, targetY - 1, targetZ - 1)
        )
        val target = BlockArrayClipboard(targetRegion)
        target.setOrigin(BlockVector3.at(0, 0, 0))

        if (transform.normalizedQuarter == 0 && !transform.mirrorX) {
            val copy = ForwardExtentCopy(source, source.region, target, BlockVector3.at(0, 0, 0))
            copy.setCopyingEntities(true)
            copy.setCopyingBiomes(true)
            Operations.complete(copy)
            return target
        }

        val holder = ClipboardHolder(source)
        holder.setTransform(transform.toWorldEditTransform())
        val operation = holder
            .createPaste(target)
            .to(BlockVector3.at(-bounds.minX, 0, -bounds.minZ))
            .copyEntities(true)
            .copyBiomes(true)
            .build()
        Operations.complete(operation)
        return target
    }

    private fun replaceAtomically(source: File, target: File, overwrite: Boolean) {
        val options = if (overwrite) {
            arrayOf(StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } else {
            arrayOf(StandardCopyOption.ATOMIC_MOVE)
        }
        try {
            Files.move(source.toPath(), target.toPath(), *options)
        } catch (_: AtomicMoveNotSupportedException) {
            if (overwrite) {
                Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } else {
                Files.move(source.toPath(), target.toPath())
            }
        }
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

    private fun determineEffectiveTransform(
        relativePath: String,
        sourceClipboard: Clipboard,
        manualFacing: CardinalDirection?,
        player: Player
    ): StructureTransform {
        if (manualFacing != null) {
            val quarter = StructureTransform.rotationBetween(manualFacing, CardinalDirection.NORTH)
            return StructureTransform(quarter)
        }

        val path = relativePath.replace('\\', '/').lowercase()
        val yawFacing = CardinalDirection.fromPlayerYaw(player.location.yaw)

        if (path.contains("structures/arena/")) {
            val detected = detectArenaTransformFromPath(path, sourceClipboard, yawFacing)
            if (detected != null) return detected
        }

        return StructureTransform(yawFacing.ordinal)
    }

    private fun detectArenaTransformFromPath(
        path: String,
        clipboard: Clipboard,
        yawFacing: CardinalDirection
    ): StructureTransform? {
        val fileName = path.substringAfterLast('/').removeSuffix(".schem")
        if (!StructureSchemas.arenaRequiresConnectionMarkers(fileName)) return null

        val typeKeyword = fileName.substringBefore('.')
        val schema = StructureSchemas.arena(typeKeyword) ?: return null

        val entities = readEntities(clipboard)
        val dims = clipboard.dimensions
        val size = CcStructureSize(dims.x(), dims.y(), dims.z())
        val sides = StructureMarkerValidator.collectDirectionalSides(entities, size)

        if (sides.allSides.isEmpty()) return null

        return StructureMarkerValidator.detectArenaTransform(schema, sides, yawFacing)
    }

    /**
     * 保存する .schem ファイルの相対パスから構造物タイプを推定し、必須マーカーを検証する。
     */
    private fun validateClipboardMarkers(relativePath: String, clipboard: Clipboard): StructureMarkerValidation {
        val path = relativePath.replace('\\', '/').lowercase()

        val category = detectCategory(path)
        if (category == null) return StructureMarkerValidation(true, emptyList(), emptyList(), emptyList())

        val entities = readEntities(clipboard)

        return when (category) {
            StructureCategory.SUKIMA_DUNGEON -> validateSukimaMarkers(path, entities)
            StructureCategory.ARENA -> validateArenaMarkers(path, entities, clipboard)
            StructureCategory.ARENA_LIFT -> validateArenaLiftMarkers(entities, clipboard)
        }
    }

    private fun detectCategory(path: String): StructureCategory? {
        if (path.contains("structures/sukima_dungeon/")) return StructureCategory.SUKIMA_DUNGEON
        if (path.contains("structures/arena/lift.schem")) return StructureCategory.ARENA_LIFT
        if (path.contains("structures/arena/")) return StructureCategory.ARENA
        return null
    }

    private fun validateSukimaMarkers(
        path: String,
        entities: List<LoadedSchemEntity>
    ): StructureMarkerValidation {
        val fileName = path.substringAfterLast('/').removeSuffix(".schem")
        val typeKeyword = fileName.substringBefore('.')
        val schema = StructureSchemas.sukima(typeKeyword)
            ?: return StructureMarkerValidation(true, emptyList(), emptyList(), emptyList())
        return StructureMarkerValidator.validateSukima(schema, entities)
    }

    private fun validateArenaMarkers(
        path: String,
        entities: List<LoadedSchemEntity>,
        clipboard: Clipboard
    ): StructureMarkerValidation {
        val fileName = path.substringAfterLast('/').removeSuffix(".schem")
        val typeKeyword = fileName.substringBefore('.')
        val schema = StructureSchemas.arena(typeKeyword)
            ?: return StructureMarkerValidation(true, emptyList(), emptyList(), emptyList())
        if (!StructureSchemas.arenaRequiresConnectionMarkers(fileName)) {
            return StructureMarkerValidation(true, emptyList(), emptyList(), emptyList())
        }

        return StructureMarkerValidator.validateArena(schema, entities, clipboard.structureSize())
    }

    private fun validateArenaLiftMarkers(
        entities: List<LoadedSchemEntity>,
        clipboard: Clipboard
    ): StructureMarkerValidation {
        return StructureMarkerValidator.validateArena(
            StructureSchemas.arena("lift") ?: error("Arena lift schema is missing"),
            entities,
            clipboard.structureSize()
        )
    }

    private fun Clipboard.structureSize(): CcStructureSize {
        val dimensions = this.dimensions
        return CcStructureSize(dimensions.x(), dimensions.y(), dimensions.z())
    }

    companion object {
        const val SCHEM_EXTENSION = ".schem"
    }
}

private fun StructureTransform.toWorldEditTransform(): AffineTransform {
    var affine = AffineTransform()
    if (mirrorX) {
        affine = affine.scale(-1.0, 1.0, 1.0)
    }
    if (normalizedQuarter != 0) {
        affine = affine.rotateY(normalizedQuarter * 90.0)
    }
    return affine
}
