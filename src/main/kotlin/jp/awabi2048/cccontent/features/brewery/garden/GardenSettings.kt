package jp.awabi2048.cccontent.features.brewery.garden

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.data.BlockData
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

data class GardenStage(val id: Int, val material: Material, val blockData: String, val growthSeconds: Long) {
    fun createBlockData(): BlockData = Bukkit.createBlockData(material, blockData)
}

data class GardenPlant(
    val id: String,
    val seedLanguageKey: String,
    val fruitLanguageKey: String,
    val stages: List<GardenStage>,
    val regrowthStage: Int
) {
    val matureStage: GardenStage get() = stages.last()
    val itemSeedId: String get() = "brewery.garden_seed_$id"
    val itemFruitId: String get() = "brewery.garden_fruit_$id"
}

data class GardenSettings(
    val configVersion: Int,
    val seedDropChance: Double,
    val growthCheckIntervalSeconds: Long,
    val seedSourceMaterials: Set<Material>,
    val plants: Map<String, GardenPlant>
)

object GardenSettingsLoader {
    fun load(plugin: JavaPlugin): GardenSettings {
        val file = File(plugin.dataFolder, "config/brewery/garden.yml")
        require(file.isFile) { "Garden設定ファイルがありません: ${file.path}" }
        return parse(YamlConfiguration.loadConfiguration(file))
    }

    fun parse(yaml: YamlConfiguration): GardenSettings {
        val configVersion = requiredInt(yaml, "config_version")
        require(configVersion == 2) { "Garden設定のconfig_versionが不正です: $configVersion" }
        val root = requiredSection(yaml, "garden")
        val chance = requiredDouble(root, "seed_drop_chance")
        require(chance in 0.0..1.0) { "garden.seed_drop_chanceは0..1で指定してください" }
        val interval = requiredLong(root, "growth_check_interval_seconds")
        require(interval > 0) { "garden.growth_check_interval_secondsは正数が必要です" }
        val source = requiredStringList(root, "seed_source_materials").map { parseMaterial(it, "garden.seed_source_materials") }.toSet()
        require(source.isNotEmpty()) { "garden.seed_source_materialsは空にできません" }
        val plantsSection = requiredSection(root, "plants")
        val plants = linkedMapOf<String, GardenPlant>()
        val itemIds = mutableSetOf<String>()
        for (id in plantsSection.getKeys(false)) {
            require(id.matches(Regex("[a-z0-9_]+"))) { "植物IDが不正です: $id" }
            require(plants.put(id, parsePlant(id, plantsSection.getConfigurationSection(id) ?: error("植物${id}がセクションではありません"))) == null) { "植物IDが重複しています: $id" }
            val plant = plants.getValue(id)
            require(itemIds.add(plant.itemSeedId)) { "カスタムアイテムIDが重複しています: ${plant.itemSeedId}" }
            require(itemIds.add(plant.itemFruitId)) { "カスタムアイテムIDが重複しています: ${plant.itemFruitId}" }
        }
        require(plants.isNotEmpty()) { "garden.plantsは空にできません" }
        return GardenSettings(configVersion, chance, interval, source, plants)
    }

    private fun parsePlant(id: String, section: ConfigurationSection): GardenPlant {
        val seedKey = requiredString(section, "seed_language_key")
        val fruitKey = requiredString(section, "fruit_language_key")
        require(seedKey.isNotBlank() && fruitKey.isNotBlank()) { "植物${id}の言語キーは空にできません" }
        val stagesSection = requiredSection(section, "stages")
        val stages = stagesSection.getKeys(false).map { raw ->
            val stageId = raw.toIntOrNull() ?: error("植物${id}の段階IDが整数ではありません: $raw")
            val node = stagesSection.getConfigurationSection(raw) ?: error("植物${id}の段階${raw}がセクションではありません")
            GardenStage(stageId, parseMaterial(requiredString(node, "material"), "plants.$id.stages.$raw.material"), requiredString(node, "block_data"), requiredLong(node, "growth_seconds").also { require(it > 0) { "植物${id}の段階${raw}のgrowth_secondsは正数が必要です" } }).also { it.createBlockData() }
        }.sortedBy { it.id }
        require(stages.isNotEmpty()) { "植物${id}に段階がありません" }
        stages.forEachIndexed { index, stage -> require(stage.id == index) { "植物${id}の段階IDは0から連続している必要があります" } }
        val regrowth = requiredInt(section, "regrowth_stage")
        require(regrowth in stages.indices) { "植物${id}のregrowth_stageが範囲外です" }
        return GardenPlant(id, seedKey, fruitKey, stages, regrowth)
    }

    private fun parseMaterial(raw: String, path: String): Material = runCatching { Material.valueOf(raw.uppercase()) }.getOrElse { error("${path}のMaterialが不正です: $raw") }
    private fun requiredSection(section: ConfigurationSection, path: String): ConfigurationSection = section.getConfigurationSection(path) ?: error("${path}は必須セクションです")
    private fun requiredString(section: ConfigurationSection, path: String): String = (section.get(path) as? String) ?: error("${section.currentPath}.${path}は文字列が必要です")
    private fun requiredStringList(section: ConfigurationSection, path: String): List<String> = (section.get(path) as? List<*>)?.map { it as? String ?: error("${section.currentPath}.${path}は文字列リストが必要です") } ?: error("${section.currentPath}.${path}は文字列リストが必要です")
    private fun requiredInt(section: ConfigurationSection, path: String): Int = (section.get(path) as? Number)?.let { require(it.toDouble() == it.toInt().toDouble()) { "${section.currentPath}.${path}は整数が必要です" }; it.toInt() } ?: error("${section.currentPath}.${path}は整数が必要です")
    private fun requiredLong(section: ConfigurationSection, path: String): Long = (section.get(path) as? Number)?.let { require(it.toDouble() == it.toLong().toDouble()) { "${section.currentPath}.${path}は整数が必要です" }; it.toLong() } ?: error("${section.currentPath}.${path}は整数が必要です")
    private fun requiredDouble(section: ConfigurationSection, path: String): Double = (section.get(path) as? Number)?.toDouble() ?: error("${section.currentPath}.${path}は数値が必要です")
}
