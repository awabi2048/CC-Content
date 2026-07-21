package jp.awabi2048.cccontent.features.cooking

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

internal data class PersistedCookingStation(
    val equipment: CookingStation,
    val session: CookingStationSession? = null,
    val workspaceItems: Map<Int, String> = emptyMap(),
    val experienceAwarded: Boolean = false,
    val starterCatalogAwarded: Boolean = false,
    val collectorIds: Set<String> = emptySet()
)

internal class CookingStateStore(private val file: File) {
    fun load(): MutableMap<CookingStationKey, PersistedCookingStation> {
        if (!file.exists()) return mutableMapOf()
        val root = YamlConfiguration.loadConfiguration(file)
        require(integer(root, "schema_version") == 3) { "${file.path}.schema_version must be 3" }
        val stations = root.getConfigurationSection("stations") ?: return mutableMapOf()
        return stations.getKeys(false).associate { pathKey ->
            val section = section(stations, pathKey)
            val key = CookingStationKey.deserialize(string(section, "station"))
                ?: error("${file.path}.stations.$pathKey.station is invalid")
            key to loadStation(section)
        }.toMutableMap()
    }

    fun save(stations: Map<CookingStationKey, PersistedCookingStation>) {
        val output = YamlConfiguration()
        output.set("schema_version", 3)
        stations.toSortedMap(compareBy({ it.worldId.toString() }, { it.y }, { it.x }, { it.z }))
            .forEach { (key, station) -> saveStation(output.createSection("stations.${key.pathKey()}"), key, station) }
        file.parentFile.mkdirs()
        output.save(file)
    }

    private fun saveStation(
        target: ConfigurationSection,
        key: CookingStationKey,
        persisted: PersistedCookingStation
    ) {
        target.set("station", key.serialize())
        target.set("equipment", persisted.equipment.name)
        target.set("workspace_items", persisted.workspaceItems.toSortedMap().map { (slot, item) ->
            linkedMapOf("slot" to slot, "serialized_item" to item)
        })
        val session = persisted.session
        if (session == null) {
            target.set("status", CookingProcessState.IDLE.name)
            return
        }
        target.set("status", session.state.name)
        target.set("starter_uuid", session.starterId)
        target.set("recipe_or_preparation_id", session.recipeId)
        target.set("scale", session.scale)
        target.set("start_heat", session.startHeat.name)
        target.set("outcome", if (session.failureCommitted) "FAILURE" else "NORMAL")
        target.set("remaining_ticks", session.remainingTicks)
        target.set("total_ticks", session.totalTicks)
        target.set("reserved_water_units", session.reservedWaterUnits)
        target.set("water_consumed", session.consumedWaterUnits)
        target.set("experience_awarded", persisted.experienceAwarded)
        target.set("starter_catalog_awarded", persisted.starterCatalogAwarded)
        target.set("collector_uuids", persisted.collectorIds.sorted())
        saveSnapshot(target.createSection("recipe_snapshot"), session.recipeSnapshot)
        target.set("original_inputs", session.originalInputs.map { input ->
            linkedMapOf(
                "ingredient_id" to input.ingredientId,
                "amount" to input.amount,
                "serialized_item" to input.serializedItem,
                "container_remainder_material" to input.containerRemainderMaterial,
                "container_remainder_amount" to input.containerRemainderAmount
            )
        })
        target.set("ready_items", session.outputStacks.map { item ->
            linkedMapOf("custom_item_id" to item.customItemId, "amount" to item.amount, "failed" to item.failed)
        })
        session.reservoir?.let { reservoir ->
            target.set("liquid_result_id", reservoir.customItemId)
            target.set("liquid_remaining", reservoir.remaining)
            target.set("liquid_maximum", reservoir.maximum)
            target.set("container_type", reservoir.containerMaterial)
            target.set("liquid_failed", reservoir.failed)
        }
    }

    private fun saveSnapshot(target: ConfigurationSection, snapshot: CookingRecipeSnapshot) {
        target.set("result_id", snapshot.normalResultId)
        target.set("result_amount", snapshot.resultAmountPerScale)
        target.set("failure_result", snapshot.failureResultId)
        target.set("duration_seconds", snapshot.durationSeconds)
        target.set("heat", snapshot.expectedHeat.name)
        target.set("water_units", snapshot.waterUnits)
        target.set("result_kind", snapshot.resultKind.name)
        target.set("container", snapshot.containerMaterial)
        target.set("pane", snapshot.liquidPaneMaterial)
        target.set("exp", snapshot.experience)
        target.set("brew_family", snapshot.brewFamilyId)
        target.set("prepared_quality", snapshot.preparedQuality)
    }

    private fun loadStation(section: ConfigurationSection): PersistedCookingStation {
        val equipment = enum<CookingStation>(section, "equipment")
        val workspace = section.getMapList("workspace_items").associate { raw ->
            mapInt(raw, "slot") to mapString(raw, "serialized_item")
        }
        val status = enum<CookingProcessState>(section, "status")
        if (status == CookingProcessState.IDLE) return PersistedCookingStation(equipment, null, workspace)
        val snapshot = loadSnapshot(section(section, "recipe_snapshot"))
        val inputs = section.getMapList("original_inputs").map { raw ->
            CookingStoredInput(
                mapString(raw, "ingredient_id"), mapInt(raw, "amount"), mapString(raw, "serialized_item"),
                raw["container_remainder_material"] as? String,
                mapInt(raw, "container_remainder_amount")
            )
        }
        val outputs = section.getMapList("ready_items").map { raw ->
            CookingOutputStack(mapString(raw, "custom_item_id"), mapInt(raw, "amount"), mapBoolean(raw, "failed"))
        }
        val liquidId = section.getString("liquid_result_id")
        val reservoir = liquidId?.let {
            CookingReservoir(
                it, integer(section, "liquid_remaining"), integer(section, "liquid_maximum"),
                string(section, "container_type"), boolean(section, "liquid_failed")
            )
        }
        val session = CookingStationSession(
            string(section, "recipe_or_preparation_id"), snapshot, string(section, "starter_uuid"),
            integer(section, "scale"), enum(section, "start_heat"), string(section, "outcome") == "FAILURE",
            inputs, integer(section, "reserved_water_units"), long(section, "total_ticks"),
            long(section, "remaining_ticks"), status, outputs, reservoir,
            integer(section, "water_consumed")
        )
        return PersistedCookingStation(
            equipment, session, workspace,
            boolean(section, "experience_awarded"), boolean(section, "starter_catalog_awarded"),
            section.getStringList("collector_uuids").toSet()
        )
    }

    private fun loadSnapshot(section: ConfigurationSection): CookingRecipeSnapshot = CookingRecipeSnapshot(
        string(section, "result_id"), integer(section, "result_amount"), string(section, "failure_result"),
        integer(section, "duration_seconds"), enum(section, "heat"), integer(section, "water_units"),
        enum(section, "result_kind"), section.getString("container"), section.getString("pane"),
        long(section, "exp"), section.getString("brew_family"), section.get("prepared_quality")?.let { integer(section, "prepared_quality") }
    )

    private fun section(parent: ConfigurationSection, path: String): ConfigurationSection =
        parent.getConfigurationSection(path) ?: error("${file.path}.$path must be a section")
    private fun string(parent: ConfigurationSection, path: String): String =
        (parent.get(path) as? String)?.takeIf { it.isNotBlank() } ?: error("${file.path}.$path must be a string")
    private fun integer(parent: ConfigurationSection, path: String): Int =
        (parent.get(path) as? Number)?.toInt() ?: error("${file.path}.$path must be an integer")
    private fun long(parent: ConfigurationSection, path: String): Long =
        (parent.get(path) as? Number)?.toLong() ?: error("${file.path}.$path must be an integer")
    private fun boolean(parent: ConfigurationSection, path: String): Boolean =
        parent.get(path) as? Boolean ?: error("${file.path}.$path must be a boolean")
    private inline fun <reified T : Enum<T>> enum(parent: ConfigurationSection, path: String): T =
        runCatching { enumValueOf<T>(string(parent, path)) }.getOrElse { error("${file.path}.$path is invalid") }
    private fun mapString(map: Map<*, *>, key: String): String =
        map[key] as? String ?: error("${file.path} map.$key must be a string")
    private fun mapInt(map: Map<*, *>, key: String): Int =
        (map[key] as? Number)?.toInt() ?: error("${file.path} map.$key must be an integer")
    private fun mapBoolean(map: Map<*, *>, key: String): Boolean =
        map[key] as? Boolean ?: error("${file.path} map.$key must be a boolean")
}
