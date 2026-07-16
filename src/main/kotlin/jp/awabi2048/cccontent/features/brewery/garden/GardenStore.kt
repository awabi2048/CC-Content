package jp.awabi2048.cccontent.features.brewery.garden

import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

data class GardenLocation(val worldId: UUID, val x: Int, val y: Int, val z: Int) {
    val key: String get() = "$worldId,$x,$y,$z"
    companion object { fun parse(raw: String): GardenLocation = raw.split(',').takeIf { it.size == 4 }?.let { GardenLocation(UUID.fromString(it[0]), it[1].toInt(), it[2].toInt(), it[3].toInt()) } ?: error("Garden座標キーが不正です: $raw") }
}

class GardenStore(private val file: File) {
    private val states = linkedMapOf<GardenLocation, GardenPlantState>()
    init { file.parentFile?.mkdirs(); load() }
    @Synchronized fun snapshot(): Map<GardenLocation, GardenPlantState> = states.mapValues { it.value.copy() }
    @Synchronized fun get(location: GardenLocation): GardenPlantState? = states[location]?.copy()
    @Synchronized fun put(location: GardenLocation, state: GardenPlantState) { states[location] = state.copy(); save() }
    @Synchronized fun remove(location: GardenLocation): GardenPlantState? = states.remove(location)?.also { save() }
    @Synchronized fun save() {
        val yaml = YamlConfiguration(); yaml.set("schema_version", 1)
        states.forEach { (location, state) ->
            val path = "plants.${location.key}"
            yaml.set("$path.plant_id", state.plantId)
            yaml.set("$path.plant_stage", state.stage)
            yaml.set("$path.planted_at", state.plantedAtMillis)
            yaml.set("$path.next_growth_at", state.nextGrowthAtMillis)
        }
        val tmp = File(file.parentFile, "${file.name}.tmp")
        yaml.save(tmp); Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
    private fun load() {
        if (!file.isFile) return
        val yaml = YamlConfiguration.loadConfiguration(file)
        require(yaml.getInt("schema_version", -1) == 1) { "Garden台帳のschema_versionが不正です" }
        yaml.getConfigurationSection("plants")?.getKeys(false)?.forEach { raw ->
            val node = yaml.getConfigurationSection("plants.$raw") ?: error("Garden台帳の${raw}がセクションではありません")
            val plantId = node.get("plant_id") as? String
                ?: error("Garden台帳のplants.${raw}.plant_idは文字列が必要です")
            val stage = requiredLong(node.get("plant_stage"), "plants.${raw}.plant_stage").toInt()
            val plantedAt = requiredLong(node.get("planted_at"), "plants.${raw}.planted_at")
            val nextGrowthAt = requiredLong(node.get("next_growth_at"), "plants.${raw}.next_growth_at")
            states[GardenLocation.parse(raw)] = GardenPlantState(plantId, stage, plantedAt, nextGrowthAt)
        }
    }

    private fun requiredLong(value: Any?, path: String): Long {
        val number = value as? Number ?: error("Garden台帳の${path}は整数が必要です")
        require(number.toDouble() == number.toLong().toDouble()) { "Garden台帳の${path}は整数が必要です" }
        return number.toLong()
    }
}
