package jp.awabi2048.cccontent.structure

import kotlin.math.floor

data class DirectionalConnectionSides(
    val inSides: Set<CardinalDirection>,
    val outSides: Set<CardinalDirection>
) {
    val allSides: Set<CardinalDirection> get() = inSides + outSides
}

object StructureMarkerValidator {
    fun validateArena(
        schema: ArenaStructureSchema,
        entities: List<LoadedSchemEntity>,
        size: CcStructureSize
    ): StructureMarkerValidation {
        val sides = collectDirectionalSides(entities, size)
        val expectedSides = schema.connectionSides
        val missing = mutableListOf<String>()
        val extra = mutableListOf<String>()

        val sideCounts = countConnectionMarkersPerSide(entities, size)
        sideCounts.forEach { (side, count) ->
            if (count > 1) {
                extra += "connection(${side.token},count=$count)"
            }
        }

        sides.allSides.forEach { side ->
            if (side !in expectedSides) {
                extra += "connection(${side.token},unexpected)"
            }
        }
        expectedSides.forEach { side ->
            if (side !in sides.allSides) {
                missing += "connection(${side.token})"
            }
        }

        if (schema.inSide != null && schema.inSide !in sides.inSides && schema.inSide in expectedSides) {
            missing += "${StructureSchemas.ARENA_CONNECTION_IN_TAG}(${schema.inSide.token})"
        }
        schema.outSides.forEach { outSide ->
            if (outSide !in sides.outSides && outSide in expectedSides) {
                missing += "${StructureSchemas.ARENA_CONNECTION_OUT_TAG}(${outSide.token})"
            }
        }

        sides.inSides.forEach { side ->
            if (schema.inSide == null) {
                extra += "${StructureSchemas.ARENA_CONNECTION_IN_TAG}(${side.token},unexpected)"
            } else if (side != schema.inSide && side in expectedSides) {
                extra += "${StructureSchemas.ARENA_CONNECTION_IN_TAG}(${side.token},should_be_out)"
            }
        }
        sides.outSides.forEach { side ->
            if (side !in schema.outSides && side in expectedSides) {
                extra += "${StructureSchemas.ARENA_CONNECTION_OUT_TAG}(${side.token},should_be_in)"
            }
        }

        entities.forEach { entity ->
            if (entity.typeId != StructureSchemas.MARKER_ENTITY_TYPE) {
                val hasConnectionTag = StructureSchemas.ARENA_CONNECTION_TAGS.any { it in entity.scoreboardTags }
                if (hasConnectionTag) {
                    extra += "connection(invalid_type=${entity.typeId})"
                }
            }
        }

        return StructureMarkerValidation(
            isValid = missing.isEmpty() && extra.isEmpty(),
            missingMarkers = missing.distinct(),
            extraMarkers = extra.distinct(),
            warnings = emptyList()
        )
    }

    fun collectDirectionalSides(
        entities: List<LoadedSchemEntity>,
        size: CcStructureSize
    ): DirectionalConnectionSides {
        val maxX = size.x - 1
        val maxZ = size.z - 1
        val inSides = mutableSetOf<CardinalDirection>()
        val outSides = mutableSetOf<CardinalDirection>()

        entities.forEach { entity ->
            val isIn = StructureSchemas.ARENA_CONNECTION_IN_TAG in entity.scoreboardTags
            val isOut = StructureSchemas.ARENA_CONNECTION_OUT_TAG in entity.scoreboardTags
            if (!isIn && !isOut) return@forEach
            if (entity.typeId != StructureSchemas.MARKER_ENTITY_TYPE) return@forEach

            val x = floor(entity.x).toInt()
            val z = floor(entity.z).toInt()
            val touchedSides = buildList {
                if (x == 0) add(CardinalDirection.WEST)
                if (x == maxX) add(CardinalDirection.EAST)
                if (z == 0) add(CardinalDirection.NORTH)
                if (z == maxZ) add(CardinalDirection.SOUTH)
            }
            if (touchedSides.size != 1) return@forEach

            val side = touchedSides.single()
            if (isIn) inSides.add(side)
            if (isOut) outSides.add(side)
        }

        return DirectionalConnectionSides(inSides, outSides)
    }

    private fun countConnectionMarkersPerSide(
        entities: List<LoadedSchemEntity>,
        size: CcStructureSize
    ): Map<CardinalDirection, Int> {
        val maxX = size.x - 1
        val maxZ = size.z - 1
        val counts = mutableMapOf<CardinalDirection, Int>()

        entities.forEach { entity ->
            val hasTag = StructureSchemas.ARENA_CONNECTION_TAGS.any { it in entity.scoreboardTags }
            if (!hasTag) return@forEach
            if (entity.typeId != StructureSchemas.MARKER_ENTITY_TYPE) return@forEach

            val x = floor(entity.x).toInt()
            val z = floor(entity.z).toInt()
            val touchedSides = buildList {
                if (x == 0) add(CardinalDirection.WEST)
                if (x == maxX) add(CardinalDirection.EAST)
                if (z == 0) add(CardinalDirection.NORTH)
                if (z == maxZ) add(CardinalDirection.SOUTH)
            }
            if (touchedSides.size != 1) return@forEach

            val side = touchedSides.single()
            counts[side] = (counts[side] ?: 0) + 1
        }

        return counts
    }

    fun detectArenaFacing(
        schema: ArenaStructureSchema,
        sides: DirectionalConnectionSides,
        yawFacing: CardinalDirection
    ): CardinalDirection? {
        val expectedSides = schema.connectionSides

        val candidates = CardinalDirection.entries.filter { facing ->
            val undoQuarter = (4 - facing.ordinal) % 4
            val rotated = sides.allSides.map { it.rotateClockwise(undoQuarter) }.toSet()
            rotated == expectedSides
        }

        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates.first()

        if (schema.inSide != null && sides.inSides.isNotEmpty()) {
            val disambiguated = candidates.filter { facing ->
                val undoQuarter = (4 - facing.ordinal) % 4
                sides.inSides.any { it.rotateClockwise(undoQuarter) == schema.inSide }
            }
            if (disambiguated.size == 1) return disambiguated.first()
            if (disambiguated.isNotEmpty()) {
                return disambiguated.minByOrNull { angularDistance(it, yawFacing) }
            }
        }

        return candidates.minByOrNull { angularDistance(it, yawFacing) }
    }

    private fun angularDistance(a: CardinalDirection, b: CardinalDirection): Int {
        val diff = (a.ordinal - b.ordinal).mod(4)
        return minOf(diff, 4 - diff)
    }

    fun validateSukima(
        schema: SukimaStructureSchema,
        entities: List<LoadedSchemEntity>
    ): StructureMarkerValidation {
        val missing = mutableListOf<String>()
        val extra = mutableListOf<String>()
        schema.markerRequirements.forEach { requirement ->
            val tagged = entities.filter { requirement.tag in it.scoreboardTags }
            val matching = tagged.count { it.typeId == requirement.entityType }
            if (matching < requirement.minimum) {
                missing += "${requirement.tag}(minimum=${requirement.minimum}, actual=$matching)"
            }
            requirement.maximum?.let { maximum ->
                if (matching > maximum) {
                    extra += "${requirement.tag}(maximum=$maximum, actual=$matching)"
                }
            }
            tagged.filter { it.typeId != requirement.entityType }.forEach { entity ->
                extra += "${requirement.tag}(invalid_type=${entity.typeId})"
            }
        }
        return StructureMarkerValidation(
            isValid = missing.isEmpty() && extra.isEmpty(),
            missingMarkers = missing,
            extraMarkers = extra.distinct(),
            warnings = emptyList()
        )
    }
}
