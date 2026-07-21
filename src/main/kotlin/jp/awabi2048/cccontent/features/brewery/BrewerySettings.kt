package jp.awabi2048.cccontent.features.brewery

import jp.awabi2048.cccontent.features.brewery.model.FirePower
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffectType
import java.io.File
import kotlin.math.roundToInt

data class BreweryRecipe(
    val id: String,
    val primaryOutputId: String,
    val requiredSkillLevel: Int,
    val requiredSkills: Set<String>,
    val fermentationIngredients: Map<String, Int>,
    val fermentationTime: Int,
    val fermentationIdealFirePower: FirePower,
    val fermentationParticleColor: String?,
    val distillationTime: Int,
    val distillationFilterConsumption: Int,
    val distillationRuns: Int,
    val agingTimeDays: Int,
    val agingBarrelTypes: Set<String>,
    val middleOutputColor: String?,
    val finalOutputAlcohol: Double,
    val finalOutputColor: String?,
    val finalOutputEffects: List<BreweryEffectDefinition>,
    val finalOutputGlint: Boolean,
    val finalOutputCustomModelData: List<Int>,
    val agingVariants: List<BreweryAgingVariant>,
    val outputs: Map<String, BreweryOutputDefinition>
)

data class BreweryAgingVariant(val outputId: String, val targetUnits: Int, val barrelTypes: Set<String>)

data class BreweryOutputDefinition(
    val id: String,
    val glint: Boolean,
    val effects: List<BreweryEffectDefinition>,
    val itemModel: String,
    val alcoholPercent: Double,
    val intoxicationPoints: Double,
    val soberingPoints: Double
)

fun breweryQualityIndex(quality: Double): Int = when {
    quality < 34 -> 0
    quality < 67 -> 1
    else -> 2
}

fun breweryQualityTier(quality: Double): String = when (breweryQualityIndex(quality)) {
    0 -> "low"
    1 -> "standard"
    else -> "high"
}

data class BreweryEffectDefinition(
    val type: PotionEffectType,
    val worstAmplifier: Int,
    val bestAmplifier: Int,
    val worstDurationSeconds: Int,
    val bestDurationSeconds: Int
) {
    fun resolve(quality: Double): ResolvedBreweryEffect {
        val ratio = quality.coerceIn(0.0, 100.0) / 100.0
        return ResolvedBreweryEffect(
            type,
            interpolate(worstAmplifier, bestAmplifier, ratio),
            interpolate(worstDurationSeconds, bestDurationSeconds, ratio).coerceAtLeast(1)
        )
    }

    private fun interpolate(worst: Int, best: Int, ratio: Double): Int =
        (worst + (best - worst) * ratio).roundToInt().coerceAtLeast(0)
}

data class ResolvedBreweryEffect(
    val type: PotionEffectType,
    val amplifier: Int,
    val durationSeconds: Int
)

data class BrewerySettings(
    val configVersion: Int,
    val distillationOverPenalty: Double,
    val filterSpeedBonus: Double,
    val angelSharePercentPerYear: Double,
    val agingRealSecondsPerYear: Long,
    val smallBarrelSpeedMultiplier: Double,
    val fermentationSignKeyword: String,
    val agingSignKeyword: String,
    val fermentationCapacity: Int,
    val openLargeBarrelEverywhere: Boolean,
    val barrelInventoryRowsLarge: Int,
    val barrelInventoryRowsSmall: Int,
    val nauseaThreshold: Double,
    val stumbleThreshold: Double,
    val faintThreshold: Double,
    val intoxicationDecayPerSecond: Double,
    val faintDurationSeconds: Long,
    val stateRetentionSeconds: Long,
    val fermentationExp: Long,
    val distillationExp: Long,
    val agingExp: Long,
    val flushIntervalTicks: Long
)

class BrewerySettingsLoader(private val plugin: JavaPlugin) {
    fun loadSettings(): BrewerySettings {
        val file = File(plugin.dataFolder, "config/brewery/config.yml")
        val yml = YamlConfiguration.loadConfiguration(file)
        val configVersion = yml.getInt("config_version", -1)
        require(configVersion == 3) { "Brewery設定のconfig_versionが3ではありません: $configVersion" }
        val root = yml.getConfigurationSection("brewery")
            ?: error("Brewery設定にbreweryセクションがありません")
        return BrewerySettings(
            configVersion,
            root.getDouble("quality.over_distillation_penalty", 5.0),
            root.getDouble("distillation.filter_speed_bonus", 0.15).coerceIn(0.0, 0.9),
            root.getDouble("aging.small_barrel_loss_percent_per_target_unit", 1.0).coerceAtLeast(0.0),
            root.getLong("aging.real_seconds_per_unit", 1200L).coerceAtLeast(1L),
            root.getDouble("aging.small_barrel_speed_multiplier", 1.25).coerceAtLeast(0.1),
            requiredString(root, "barrel.fermentation_sign_keyword"),
            requiredString(root, "barrel.aging_sign_keyword"),
            root.getInt("barrel.fermentation_capacity", 3).also {
                require(it in 1..5) { "brewery.barrel.fermentation_capacity must be between 1 and 5" }
            },
            root.getBoolean("barrel.open_large_everywhere", true),
            3,
            1,
            root.getDouble("intoxication.nausea_threshold", 25.0).coerceAtLeast(0.0),
            root.getDouble("intoxication.stumble_threshold", 50.0).coerceAtLeast(0.0),
            root.getDouble("intoxication.faint_threshold", 90.0).coerceAtLeast(0.0),
            root.getDouble("intoxication.decay_per_second", 0.05).coerceAtLeast(0.0),
            root.getLong("intoxication.faint_duration_seconds", 8L).coerceAtLeast(1L),
            root.getLong("intoxication.state_retention_seconds", 604800L).coerceAtLeast(1L),
            root.getLong("rank_exp.fermentation_batch", 10L).coerceAtLeast(0L),
            root.getLong("rank_exp.distillation_batch", 20L).coerceAtLeast(0L),
            root.getLong("rank_exp.aging_per_bottle", 10L).coerceAtLeast(0L),
            root.getLong("state.flush_interval_ticks", 100L).also { require(it > 0) }
        )
    }

    fun loadRecipes(): Map<String, BreweryRecipe> {
        val file = File(plugin.dataFolder, "config/brewery/recipes.yml")
        val yml = YamlConfiguration().also {
            it.options().pathSeparator('/')
            it.load(file)
        }
        require(yml.getInt("config_version", -1) == 3) { "Breweryレシピ設定のconfig_versionが3ではありません" }
        val preparations = yml.getConfigurationSection("preparations")
            ?: error("Breweryレシピ設定にpreparationsセクションがありません")
        val families = yml.getConfigurationSection("brew_families")
            ?: error("Breweryレシピ設定にbrew_familiesセクションがありません")
        require(preparations.getKeys(false).size == 26) { "preparations must contain exactly 26 entries" }
        return families.getKeys(false).associateWith { familyId ->
            parseFamily(familyId, requireNotNull(families.getConfigurationSection(familyId)), preparations)
        }.also { require(it.size == 26) { "brew_families must contain exactly 26 families" } }
    }

    private fun parseFamily(
        familyId: String,
        family: ConfigurationSection,
        preparations: ConfigurationSection
    ): BreweryRecipe {
        val preparationId = requiredString(family, "preparation")
        val preparation = preparations.getConfigurationSection(preparationId)
            ?: error("Unknown preparation $preparationId for $familyId")
        require(requiredString(preparation, "family") == familyId)
        require(preparation.getInt("water_units") == 3 && preparation.getInt("max_scale") == 1)
        val ingredientSection = requireNotNull(preparation.getConfigurationSection("ingredients"))
        val ingredients = ingredientSection.getKeys(false)
            .associateWith { ingredientSection.getInt(it).also { amount -> require(amount > 0) } }
        require(ingredients.isNotEmpty())
        val fermentation = requireNotNull(family.getConfigurationSection("fermentation"))
        require(requiredString(fermentation, "yeast") == "brewery.cultured_yeast")
        val distillation = requireNotNull(family.getConfigurationSection("distillation"))
        val aging = requireNotNull(family.getConfigurationSection("aging"))
        val variants = aging.getMapList("variants").map { raw ->
            BreweryAgingVariant(
                raw["output_id"]?.toString()?.takeIf { it.isNotBlank() }
                    ?: error("$familyId aging output_id is required"),
                (raw["target_units"] as? Number)?.toInt()?.also { require(it > 0) }
                    ?: error("$familyId target_units is required"),
                (raw["barrel_types"] as? List<*>)?.map { it.toString().lowercase() }?.toSet()
                    ?.also { require(it.isNotEmpty()) } ?: error("$familyId barrel_types is required")
            )
        }.sortedBy(BreweryAgingVariant::targetUnits)
        require(variants.map { it.targetUnits }.distinct().size == variants.size)
        val outputSection = requireNotNull(family.getConfigurationSection("outputs"))
        val outputs = outputSection.getKeys(false).associateWith { outputId ->
            val output = requireNotNull(outputSection.getConfigurationSection(outputId))
            require(output.isBoolean("glint"))
            require(output.isList("effects"))
            val consumption = requireNotNull(output.getConfigurationSection("consumption"))
            val alcohol = consumption.getDouble("alcohol_percent")
            val intoxication = consumption.getDouble("intoxication_points")
            val sobering = consumption.getDouble("sobering_points")
            require(alcohol in 0.0..100.0 && intoxication in 0.0..100.0 && sobering in 0.0..100.0)
            require(!(intoxication > 0 && sobering > 0))
            require((alcohol == 0.0) == (intoxication == 0.0))
            BreweryOutputDefinition(
                outputId,
                output.getBoolean("glint"),
                output.getStringList("effects").map { parseEffect(outputId, it) },
                requiredString(output, "item_model"),
                alcohol,
                intoxication,
                sobering
            )
        }
        require(outputs.isNotEmpty() && variants.all { it.outputId in outputs })
        require(variants.isNotEmpty() || outputs.size == 1)
        val primary = variants.firstOrNull()?.outputId ?: outputs.keys.single()
        val primaryOutput = outputs.getValue(primary)
        val heat = when (requiredString(preparation, "heat")) {
            "NORMAL" -> FirePower.MEDIUM
            "HIGH" -> FirePower.HIGH
            else -> error("Unknown preparation heat for $familyId")
        }
        return BreweryRecipe(
            familyId, primary, 1, emptySet(), ingredients,
            fermentation.getInt("duration_seconds").also { require(it > 0) }, heat,
            null, distillation.getInt("duration_seconds_per_run").also { require(it > 0) },
            distillation.getInt("filter_consumption_per_run").also { require(it > 0) },
            distillation.getInt("required_runs").also { require(it >= 0) },
            variants.firstOrNull()?.targetUnits ?: 0,
            variants.flatMap { it.barrelTypes }.toSet(), null,
            primaryOutput.alcoholPercent, null, primaryOutput.effects, primaryOutput.glint,
            emptyList(), variants, outputs
        )
    }

    private fun requiredString(section: ConfigurationSection, path: String): String =
        section.getString(path)?.trim()?.takeIf { it.isNotEmpty() }
            ?: error("${section.currentPath}.$path is required")

    private fun parseEffect(outputId: String, raw: String): BreweryEffectDefinition {
        val parts = raw.split('/')
        require(parts.size == 3) { "出力$outputId の効果は TYPE/amplifier/duration_seconds 形式が必要です: $raw" }
        val type = PotionEffectType.getByName(parts[0].trim().uppercase())
            ?: error("出力$outputId のPotionEffectTypeが不正です: ${parts[0]}")
        val amplifier = parseRange(parts[1], outputId, raw)
        val duration = parseRange(parts[2], outputId, raw)
        return BreweryEffectDefinition(type, amplifier.first, amplifier.second, duration.first, duration.second)
    }

    private fun parseRange(raw: String, outputId: String, whole: String): Pair<Int, Int> {
        val values = raw.split('-').map { it.toIntOrNull() }
        require(values.size in 1..2 && values.all { it != null && it >= 0 }) {
            "出力$outputId の効果範囲が不正です: $whole"
        }
        return values[0]!! to (values.getOrNull(1) ?: values[0]!!)
    }

    fun getFinalQualityIndex(quality: Double): Int = breweryQualityIndex(quality)
}
