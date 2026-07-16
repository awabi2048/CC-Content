package jp.awabi2048.cccontent.features.fishing

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.math.roundToLong

enum class FishQuality(val id: String, val multiplier: Double) {
    COMMON("common", 1.0),
    UNCOMMON("uncommon", 1.25),
    RARE("rare", 1.6),
    EPIC("epic", 2.2),
    LEGENDARY("legendary", 3.0);

    companion object {
        fun fromId(id: String): FishQuality = entries.firstOrNull { it.id.equals(id, true) }
            ?: error("Unknown fish quality: $id")
    }
}

data class FishDefinition(
    val id: String,
    val material: Material,
    val weight: Int,
    val minLevel: Int,
    val biomes: Set<String>,
    val weather: Set<FishingWeather>,
    val times: Set<FishingTime>,
    val bobberY: IntRange,
    val bobberDistance: IntRange,
    val qualities: Map<FishQuality, Int>,
    val exp: Long
)

data class FishingContext(
    val world: World,
    val bobber: Location,
    val professionLevel: Int,
    val randomSeed: Long = 0L
) {
    val biome: String get() = bobber.block.biome.key.key
    val weather: FishingWeather get() = if (world.hasStorm()) FishingWeather.RAIN else FishingWeather.CLEAR
    val time: FishingTime get() = FishingTime.fromWorldTime(world.time)
    val distanceFromOrigin: Int get() = bobber.distance(bobber.clone().apply { y = 0.0 }).roundToInt()
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
    val sizeCm: Int,
    val exp: Long
)

object FishingCatchSelector {
    fun select(context: FishingContext, definitions: List<FishDefinition>, random: java.util.Random): FishCatch? {
        val candidates = definitions.filter { definition ->
            context.professionLevel >= definition.minLevel &&
                (definition.biomes.isEmpty() || definition.biomes.any { it.equals(context.biome, true) }) &&
                (definition.weather.isEmpty() || context.weather in definition.weather) &&
                (definition.times.isEmpty() || context.time in definition.times) &&
                context.bobber.blockY in definition.bobberY &&
                context.distanceFromOrigin in definition.bobberDistance
        }
        if (candidates.isEmpty()) return null
        val selected = weighted(candidates, random) { it.weight }
        val quality = weighted(selected.qualities.entries.toList(), random) { it.value }.key
        val size = random.nextInt(41) + 10
        val weight = (size * (random.nextInt(91) + 80) / 10.0).roundToInt()
        return FishCatch(selected.id, selected.material, weight, quality, size, (selected.exp * quality.multiplier).roundToLong())
    }

    private fun <T> weighted(items: List<T>, random: java.util.Random, weight: (T) -> Int): T {
        val total = items.sumOf { weight(it).coerceAtLeast(0) }
        require(total > 0) { "Fishing weights must contain a positive value" }
        var cursor = random.nextInt(total)
        for (item in items) {
            cursor -= weight(item).coerceAtLeast(0)
            if (cursor < 0) return item
        }
        return items.last()
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

enum class FishingInputResult { ACCEPTED, COMPLETE, FAILED }

/** 捕獲中の入力判定をUIイベントから分離し、交互入力の境界を固定する。 */
data class FishingInputState(
    val progress: Int,
    val required: Int,
    val expectedLeft: Boolean
) {
    fun accept(leftClick: Boolean, hasRod: Boolean): Pair<FishingInputResult, FishingInputState> {
        if (!hasRod || leftClick != expectedLeft) return FishingInputResult.FAILED to this
        val nextProgress = progress + 1
        val result = if (nextProgress >= required) FishingInputResult.COMPLETE else FishingInputResult.ACCEPTED
        return result to copy(progress = nextProgress, expectedLeft = !expectedLeft)
    }
}
