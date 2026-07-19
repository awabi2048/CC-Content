package jp.awabi2048.cccontent.features.fishing

import com.awabi2048.ccsystem.api.time.Season
import org.bukkit.Location
import org.bukkit.HeightMap
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.data.Waterlogged
import java.util.Random
import kotlin.math.pow

enum class FishQuality(val id: String, val multiplier: Double) {
    COMMON("common", 1.0),
    RARE("rare", 1.5),
    LEGENDARY("legendary", 2.25);

    val stars: String
        get() {
            val filled = ordinal + 1
            val empty = entries.size - filled
            return "§e" + "★".repeat(filled) +
                if (empty > 0) "§7" + "☆".repeat(empty) else ""
        }

    companion object {
        fun fromConfigId(id: String): FishQuality = entries.firstOrNull { it.id.equals(id, true) }
            ?: error("Unknown fish quality: $id")

        fun fromStoredId(id: String): FishQuality = entries.firstOrNull { it.id == id }
            ?: error("Unknown stored fish quality: $id")

        fun normalizeStoredCounts(counts: Map<String, Long>): Map<FishQuality, Long> =
            counts.entries.groupingBy { fromStoredId(it.key) }.fold(0L) { total, entry -> total + entry.value }
    }
}

enum class FishRarity { COMMON, RARE, SPECIAL }

data class FishFightProfile(
    val targetCenter: Double,
    val driftPerStep: Double,
    val directionPersistence: Double,
    val durationMultiplier: Double
)

enum class FishingWaterType(val id: String) {
    ANY("any"),
    SHORE("shore"),
    NARROW("narrow"),
    OPEN("open"),
    DEEP_OPEN("deep_open");

    companion object {
        fun fromConfigId(id: String): FishingWaterType = entries.firstOrNull { it.id.equals(id, true) }
            ?: error("Unknown fishing water type: $id")
    }
}

data class FishingWaterProfile(
    val depth: Int,
    val width: Int,
    val environments: Set<FishingEnvironment> = emptySet()
)

enum class FishingEnvironment(val id: String) {
    ESTUARY("estuary"),
    SAND_BOTTOM("sand_bottom"),
    AQUATIC_VEGETATION("aquatic_vegetation"),
    ROCKY_DEEP("rocky_deep");

    companion object {
        fun fromConfigId(id: String): FishingEnvironment =
            entries.firstOrNull { it.id.equals(id, true) }
                ?: error("Unknown fishing environment: $id")
    }
}

data class FishingWaterCondition(
    val type: FishingWaterType,
    val depth: IntRange,
    val width: IntRange
) {
    fun matches(profile: FishingWaterProfile): Boolean =
        profile.depth in depth && profile.width in width
}

data class FishDefinition(
    val id: String,
    val material: Material,
    val weight: Int,
    val minLevel: Int,
    val biomes: Set<String>,
    val weather: Set<FishingWeather>,
    val times: Set<FishingTime>,
    val preferredSeasons: Set<Season>,
    val excludedSeasons: Set<Season>,
    val preferredEnvironments: Set<FishingEnvironment>,
    val water: FishingWaterCondition,
    val sizeCm: IntRange,
    val weightGrams: IntRange,
    val qualities: Map<FishQuality, Int>,
    val rarity: FishRarity,
    val requiredBaitTags: Set<String>,
    val fight: FishFightProfile
)

data class BaitDefinition(
    val id: String,
    val material: Material,
    val waitTimeMultiplier: Double,
    val rareCatchMultiplier: Double,
    val qualityMultiplier: Double,
    val specialTags: Set<String>
)

data class RodDefinition(
    val id: String,
    val powerMultiplier: Double,
    val finesseMultiplier: Double,
    val maxDurability: Int
)

data class FishingContext(
    val world: World,
    val bobber: Location,
    val fisherLevel: Int,
    val season: Season,
    val waterProfile: FishingWaterProfile? = FishingWaterAnalyzer.analyze(bobber)
) {
    val biome: String get() = bobber.block.biome.key.key
    val weather: FishingWeather get() = if (FishingWeatherResolver.isRaining(world, bobber)) {
        FishingWeather.RAIN
    } else {
        FishingWeather.CLEAR
    }
    val time: FishingTime get() = FishingTime.fromWorldTime(world.time)
}

object FishingWeatherResolver {
    private val dryBiomes = setOf(
        "desert",
        "savanna",
        "savanna_plateau",
        "windswept_savanna",
        "badlands",
        "eroded_badlands",
        "wooded_badlands"
    )

    fun isRaining(world: World, location: Location): Boolean {
        if (!world.hasStorm()) return false
        if (location.block.biome.key.key in dryBiomes) return false
        val highestY = world.getHighestBlockYAt(location.blockX, location.blockZ, HeightMap.MOTION_BLOCKING)
        return highestY <= location.blockY + 1
    }
}

object FishingWaterAnalyzer {
    private const val MAX_DEPTH = 32
    private const val WIDTH_RADIUS = 16
    private val waterPlants = setOf(
        Material.KELP,
        Material.KELP_PLANT,
        Material.SEAGRASS,
        Material.TALL_SEAGRASS,
        Material.BUBBLE_COLUMN
    )
    private val sandBottoms = setOf(Material.SAND, Material.RED_SAND, Material.SUSPICIOUS_SAND)
    private val rockyBottoms = setOf(
        Material.STONE,
        Material.DEEPSLATE,
        Material.ANDESITE,
        Material.DIORITE,
        Material.GRANITE,
        Material.TUFF,
        Material.GRAVEL
    )
    private val widthAxes = listOf(
        (1 to 0) to (-1 to 0),
        (0 to 1) to (0 to -1),
        (1 to 1) to (-1 to -1),
        (1 to -1) to (-1 to 1)
    )

    fun analyze(location: Location): FishingWaterProfile? {
        val initial = sequenceOf(location.block, location.block.getRelative(0, -1, 0), location.block.getRelative(0, 1, 0))
            .firstOrNull(::isWater) ?: return null
        var surface = initial
        var ascent = 0
        while (ascent < MAX_DEPTH) {
            val above = surface.getRelative(0, 1, 0)
            if (!isWater(above)) break
            surface = above
            ascent++
        }
        var depth = 0
        var cursor = surface
        while (depth < MAX_DEPTH && isWater(cursor)) {
            depth++
            cursor = cursor.getRelative(0, -1, 0)
        }
        val width = widthAxes.minOf { (positive, negative) ->
            1 + runLength(surface, positive.first, positive.second) +
                runLength(surface, negative.first, negative.second)
        }
        return FishingWaterProfile(depth, width, analyzeEnvironment(surface, cursor, depth))
    }

    private fun analyzeEnvironment(surface: Block, bottom: Block, depth: Int): Set<FishingEnvironment> =
        buildSet {
            if (bottom.type in sandBottoms) add(FishingEnvironment.SAND_BOTTOM)
            if (depth >= 8 && bottom.type in rockyBottoms) add(FishingEnvironment.ROCKY_DEEP)
            if (hasAquaticVegetation(surface)) add(FishingEnvironment.AQUATIC_VEGETATION)
            if (isEstuary(surface)) add(FishingEnvironment.ESTUARY)
        }

    private fun hasAquaticVegetation(surface: Block): Boolean =
        (-4..4).any { dx ->
            (-4..4).any { dz ->
                (0 until minOf(6, surface.y - surface.world.minHeight + 1)).any { depth ->
                    surface.getRelative(dx, -depth, dz).type in waterPlants
                }
            }
        }

    private fun isEstuary(surface: Block): Boolean {
        var riverFound = false
        var oceanFound = false
        for (dx in -32..32 step 4) {
            for (dz in -32..32 step 4) {
                val x = surface.x + dx
                val z = surface.z + dz
                if (!surface.world.isChunkLoaded(Math.floorDiv(x, 16), Math.floorDiv(z, 16))) continue
                val biome = surface.world.getBiome(x, surface.y, z).key.key
                riverFound = riverFound || biome == "river" || biome == "frozen_river"
                oceanFound = oceanFound || biome.endsWith("ocean")
                if (riverFound && oceanFound) return true
            }
        }
        return false
    }

    private fun runLength(origin: Block, dx: Int, dz: Int): Int {
        for (distance in 1..WIDTH_RADIUS) {
            if (!isWater(origin.getRelative(dx * distance, 0, dz * distance))) return distance - 1
        }
        return WIDTH_RADIUS
    }

    fun isWater(block: Block): Boolean =
        block.type == Material.WATER ||
            block.type in waterPlants ||
            (block.blockData as? Waterlogged)?.isWaterlogged == true
}

enum class FishingWeather { CLEAR, RAIN }

enum class FishingTime {
    DAWN, DAY, DUSK, NIGHT;

    companion object {
        fun fromWorldTime(time: Long): FishingTime = when (time % 24000L) {
            in 0..999 -> DAWN
            in 1000..11999 -> DAY
            in 12000..13999 -> DUSK
            else -> NIGHT
        }
    }
}

data class FishCatch(
    val fishId: String,
    val material: Material,
    val weightGrams: Int,
    val quality: FishQuality,
    val sizeCm: Int
)

object FishingCatchSelector {
    fun candidates(
        context: FishingContext,
        definitions: List<FishDefinition>,
        bait: BaitDefinition?,
        checkWater: Boolean = true
    ): List<FishDefinition> = definitions.filter { definition ->
        context.fisherLevel >= definition.minLevel &&
            (definition.biomes.isEmpty() || definition.biomes.any { it.equals(context.biome, true) }) &&
            context.season !in definition.excludedSeasons &&
            (!checkWater || context.waterProfile?.let(definition.water::matches) == true) &&
            (definition.requiredBaitTags.isEmpty() ||
                bait?.specialTags?.containsAll(definition.requiredBaitTags) == true)
    }

    fun select(
        context: FishingContext,
        definitions: List<FishDefinition>,
        bait: BaitDefinition?,
        rod: RodDefinition?,
        random: Random
    ): Pair<FishDefinition, FishCatch>? {
        val candidates = candidates(context, definitions, bait)
        if (candidates.isEmpty()) return null
        val rareMultiplier = bait?.rareCatchMultiplier ?: 1.0
        val selected = weighted(candidates, random) { definition ->
            definition.weight * preferenceMultiplier(definition, context) * when (definition.rarity) {
                FishRarity.COMMON -> 1.0
                FishRarity.RARE, FishRarity.SPECIAL -> rareMultiplier
            }
        }
        val qualityMultiplier = (bait?.qualityMultiplier ?: 1.0) * (rod?.finesseMultiplier ?: 1.0)
        val quality = weighted(selected.qualities.entries.toList(), random) { entry ->
            entry.value * (1.0 + (qualityMultiplier - 1.0) * entry.key.ordinal)
        }.key
        val size = selected.sizeCm.random(random)
        val weight = selected.weightGrams.random(random)
        val catch = FishCatch(
            selected.id,
            selected.material,
            weight,
            quality,
            size
        )
        return selected to catch
    }

    @JvmStatic
    fun preferenceMultiplier(
        definition: FishDefinition,
        context: FishingContext
    ): Double {
        val conditionMultiplier = preferenceMultiplier(
            definition.weather,
            definition.times,
            definition.preferredSeasons,
            definition.excludedSeasons,
            context.weather,
            context.time,
            context.season
        )
        val environmentMultiplier = environmentPreferenceMultiplier(
            definition.preferredEnvironments,
            context.waterProfile?.environments.orEmpty()
        )
        return conditionMultiplier * environmentMultiplier
    }

    @JvmStatic
    fun environmentPreferenceMultiplier(
        preferredEnvironments: Set<FishingEnvironment>,
        actualEnvironments: Set<FishingEnvironment>
    ): Double =
        if (preferredEnvironments.isEmpty() || preferredEnvironments.any(actualEnvironments::contains)) 1.0 else 0.8

    @JvmStatic
    fun preferenceMultiplier(
        preferredWeather: Set<FishingWeather>,
        preferredTimes: Set<FishingTime>,
        preferredSeasons: Set<Season>,
        excludedSeasons: Set<Season>,
        actualWeather: FishingWeather,
        actualTime: FishingTime,
        actualSeason: Season
    ): Double {
        val weatherMultiplier =
            if (preferredWeather.isEmpty() || actualWeather in preferredWeather) 1.0 else 0.75
        val timeMultiplier =
            if (preferredTimes.isEmpty() || actualTime in preferredTimes) 1.0 else 0.8
        val seasonMultiplier = when {
            preferredSeasons.isEmpty() || actualSeason in preferredSeasons -> 1.0
            excludedSeasons.isNotEmpty() -> 0.35
            else -> 0.8
        }
        return weatherMultiplier * timeMultiplier * seasonMultiplier
    }

    private fun <T> weighted(items: List<T>, random: Random, weight: (T) -> Double): T {
        val weights = items.map { weight(it).coerceAtLeast(0.0) }
        val total = weights.sum()
        require(total > 0.0) { "Fishing weights must contain a positive value" }
        var cursor = random.nextDouble() * total
        items.forEachIndexed { index, item ->
            cursor -= weights[index]
            if (cursor < 0.0) return item
        }
        return items.last()
    }

    private fun IntRange.random(random: Random): Int =
        first + random.nextInt(last - first + 1)
}

enum class FishingEffectivenessZone {
    GREEN,
    YELLOW,
    ORANGE
}

data class FishingFightScore(
    val greenTicks: Long = 0L,
    val yellowTicks: Long = 0L,
    val orangeTicks: Long = 0L,
    val dangerTicks: Long = 0L
) {
    fun record(zone: FishingEffectivenessZone, effectiveness: Double, ticks: Long): FishingFightScore {
        require(ticks > 0L) { "Fishing fight score ticks must be positive" }
        val danger = effectiveness <= 5.0 || effectiveness >= 95.0
        if (danger) return copy(dangerTicks = dangerTicks + ticks)
        return when (zone) {
            FishingEffectivenessZone.GREEN -> copy(greenTicks = greenTicks + ticks)
            FishingEffectivenessZone.YELLOW -> copy(yellowTicks = yellowTicks + ticks)
            FishingEffectivenessZone.ORANGE -> copy(orangeTicks = orangeTicks + ticks)
        }
    }

    fun normalizedScore(): Double {
        val total = greenTicks + yellowTicks + orangeTicks + dangerTicks
        if (total <= 0L) return 0.0
        return (greenTicks + yellowTicks * 0.6 + orangeTicks * 0.25) / total.toDouble()
    }

    fun successProbability(rarity: FishRarity): Double =
        (normalizedScore() * when (rarity) {
            FishRarity.COMMON -> 1.0
            FishRarity.RARE -> 0.9
            FishRarity.SPECIAL -> 0.75
        }).coerceIn(0.0, 1.0)
}

enum class FishingFightStatus(val messageId: String) {
    HOOKED("hooked"),
    STEADY("steady"),
    CONTROL("control"),
    RESISTING("resisting"),
    FOCUS("focus"),
    DANGER("danger"),
    RECOVER("recover");

    companion object {
        fun candidates(zone: FishingEffectivenessZone): List<FishingFightStatus> = when (zone) {
            FishingEffectivenessZone.GREEN -> listOf(STEADY, CONTROL)
            FishingEffectivenessZone.YELLOW -> listOf(RESISTING, FOCUS)
            FishingEffectivenessZone.ORANGE -> listOf(DANGER, RECOVER)
        }
    }
}

data class FishingFightState(
    val effectiveness: Double,
    val remainingTicks: Long,
    val driftDirection: Int,
    val driftVelocity: Double,
    val lateralOffset: Double,
    val lateralVelocity: Double,
    val lateralTarget: Double
) {
    constructor(effectiveness: Double, remainingTicks: Long, driftDirection: Int) :
        this(effectiveness, remainingTicks, driftDirection, 0.0, 0.0, 0.0, 0.0)

    fun zone(profile: FishFightProfile, greenWidth: Double, yellowMargin: Double): FishingEffectivenessZone {
        val distance = kotlin.math.abs(effectiveness - profile.targetCenter)
        return when {
            distance <= greenWidth / 2.0 -> FishingEffectivenessZone.GREEN
            distance <= greenWidth / 2.0 + yellowMargin -> FishingEffectivenessZone.YELLOW
            else -> FishingEffectivenessZone.ORANGE
        }
    }

    fun applyInput(delta: Double): FishingFightState =
        copy(effectiveness = (effectiveness + delta).coerceIn(0.0, 100.0))

    fun advance(
        profile: FishFightProfile,
        intervalTicks: Long,
        stability: Double,
        resistanceSmoothing: Double,
        lateralSmoothing: Double,
        random: Random
    ): FishingFightState {
        val keepDirection = random.nextDouble() < profile.directionPersistence
        val nextDirection = if (keepDirection) driftDirection else -driftDirection
        val stepScale = intervalTicks.toDouble() / 5.0
        val targetDrift = profile.driftPerStep * stepScale *
            (1.0 - stability.coerceIn(0.0, 0.9)) * nextDirection
        val resistanceBlend = 1.0 - (1.0 - resistanceSmoothing.coerceIn(0.01, 1.0)).pow(stepScale)
        val nextDrift = driftVelocity + (targetDrift - driftVelocity) * resistanceBlend
        val keepLateralTarget = random.nextDouble() < profile.directionPersistence
        val nextLateralTarget = if (keepLateralTarget) lateralTarget else random.nextDouble() * 2.0 - 1.0
        val lateralBlend = 1.0 - (1.0 - lateralSmoothing.coerceIn(0.01, 1.0)).pow(stepScale)
        val nextLateralOffset = lateralOffset + (nextLateralTarget - lateralOffset) * lateralBlend
        return copy(
            effectiveness = (effectiveness + nextDrift).coerceIn(0.0, 100.0),
            remainingTicks = (remainingTicks - intervalTicks).coerceAtLeast(0L),
            driftDirection = nextDirection,
            driftVelocity = nextDrift,
            lateralOffset = nextLateralOffset,
            lateralVelocity = nextLateralOffset - lateralOffset,
            lateralTarget = nextLateralTarget
        )
    }
}

data class FishdexEntry(
    val fishId: String,
    val discovered: Boolean = false,
    val total: Long = 0L,
    val maximumWeight: Int? = null,
    val minimumWeight: Int? = null,
    val qualityCounts: Map<FishQuality, Long> = emptyMap()
) {
    fun record(catch: FishCatch): FishdexEntry = copy(
        discovered = true,
        total = total + 1,
        maximumWeight = maxOf(maximumWeight ?: catch.weightGrams, catch.weightGrams),
        minimumWeight = minOf(minimumWeight ?: catch.weightGrams, catch.weightGrams),
        qualityCounts = qualityCounts + (catch.quality to ((qualityCounts[catch.quality] ?: 0L) + 1L))
    )
}

object FishdexPage {
    @JvmStatic
    fun pageCount(totalEntries: Int, entriesPerPage: Int = 21): Int {
        require(entriesPerPage > 0)
        return maxOf(1, (totalEntries + entriesPerPage - 1) / entriesPerPage)
    }

    @JvmStatic
    fun slice(entries: List<FishdexEntry>, page: Int, entriesPerPage: Int = 21): List<FishdexEntry> {
        require(entriesPerPage > 0)
        val pages = pageCount(entries.size, entriesPerPage)
        val safePage = page.coerceIn(0, pages - 1)
        return entries.drop(safePage * entriesPerPage).take(entriesPerPage)
    }
}
