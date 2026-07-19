package jp.awabi2048.cccontent.features.seasonal

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.MonthDay
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Locale

data class SeasonalRegistrySettings(
    val enabled: Boolean,
    val zoneId: ZoneId,
    val upcomingWindow: Duration
)

data class SeasonalRegistryLoadResult(
    val settings: SeasonalRegistrySettings,
    val definitions: List<SeasonalEventDefinition>,
    val rejectedEntries: Int
)

object SeasonalEventRegistry {
    private const val CONFIG_PATH = "config/seasonal/events.yml"

    fun load(plugin: JavaPlugin): SeasonalRegistryLoadResult {
        val file = ensureFile(plugin)
        val config = YamlConfiguration.loadConfiguration(file)
        require(config.getInt("config_version", -1) == 1) { "$CONFIG_PATH.config_version must be 1" }
        val settings = SeasonalRegistrySettings(
            enabled = requiredBoolean(config, "enabled"),
            zoneId = ZoneId.of(requiredString(config, "timezone")),
            upcomingWindow = Duration.ofSeconds(config.getLong("upcoming_window_seconds").also { require(it >= 0) })
        )
        var rejected = 0
        val definitions = config.getMapList("definitions").mapIndexedNotNull { index, raw ->
            runCatching { parseDefinition(raw, index) }
                .onFailure { error ->
                    rejected++
                    plugin.logger.warning("[Seasonal] definitions[$index]を無効化しました: ${error.message}")
                }
                .getOrNull()
        }
        val duplicateIds = definitions.groupingBy(SeasonalEventDefinition::id).eachCount()
            .filterValues { it > 1 }.keys
        require(duplicateIds.isEmpty()) { "$CONFIG_PATH contains duplicate ids: ${duplicateIds.sorted()}" }
        return SeasonalRegistryLoadResult(settings, definitions, rejected)
    }

    private fun parseDefinition(raw: Map<*, *>, index: Int): SeasonalEventDefinition {
        val path = "$CONFIG_PATH.definitions[$index]"
        val id = requiredString(raw, "event_id", path)
        val schedule = requiredMap(raw, "schedule", path)
        val cycleMap = raw["cycle"] as? Map<*, *>
        return SeasonalEventDefinition(
            id = id,
            enabled = requiredBoolean(raw, "enabled", path),
            displayNameKey = requiredString(raw, "display_name_key", path),
            schedule = parseSchedule(schedule, "$path.schedule"),
            gracePeriod = Duration.ofSeconds(optionalLong(raw, "grace_seconds", 0L, path)),
            cycle = cycleMap?.let { parseCycle(it, "$path.cycle") },
            worldScopes = optionalStringList(raw, "world_scopes", path).toSet(),
            requiredFeatures = optionalStringList(raw, "required_features", path).toSet()
        )
    }

    private fun parseSchedule(raw: Map<*, *>, path: String): SeasonalSchedule =
        when (requiredString(raw, "type", path).uppercase(Locale.ROOT)) {
            "ANNUAL_RANGE" -> AnnualRangeSchedule(
                MonthDay.parse("--${requiredString(raw, "start_month_day", path)}"),
                LocalTime.parse(requiredString(raw, "start_time", path)),
                MonthDay.parse("--${requiredString(raw, "end_month_day", path)}"),
                LocalTime.parse(requiredString(raw, "end_time", path))
            )
            "FIXED_RANGE" -> FixedRangeSchedule(
                OffsetDateTime.parse(requiredString(raw, "start", path)),
                OffsetDateTime.parse(requiredString(raw, "end", path))
            )
            "NTH_WEEKDAY" -> NthWeekdaySchedule(
                requiredInt(raw, "month", path),
                requiredInt(raw, "ordinal", path),
                DayOfWeek.valueOf(requiredString(raw, "day_of_week", path).uppercase(Locale.ROOT)),
                LocalTime.parse(requiredString(raw, "start_time", path)),
                Duration.ofSeconds(requiredLong(raw, "duration_seconds", path))
            )
            else -> throw IllegalArgumentException("$path.type is unsupported")
        }

    private fun parseCycle(raw: Map<*, *>, path: String): SeasonalCycle =
        SeasonalCycle(
            requiredInt(raw, "base_year", path),
            requiredInt(raw, "cycle_length", path),
            requiredNumberList(raw, "allowed_phases", path).map(Number::toInt).toSet()
        )

    private fun ensureFile(plugin: JavaPlugin): File {
        val file = File(plugin.dataFolder, CONFIG_PATH)
        if (!file.exists()) {
            file.parentFile.mkdirs()
            plugin.saveResource(CONFIG_PATH, false)
        }
        return file
    }

    private fun requiredString(config: YamlConfiguration, key: String): String =
        config.getString(key)?.takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("$CONFIG_PATH.$key must be a non-empty string")

    private fun requiredBoolean(config: YamlConfiguration, key: String): Boolean =
        (config.get(key) as? Boolean)
            ?: throw IllegalArgumentException("$CONFIG_PATH.$key must be a boolean")

    private fun requiredString(raw: Map<*, *>, key: String, path: String): String =
        (raw[key] as? String)?.takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("$path.$key must be a non-empty string")

    private fun requiredBoolean(raw: Map<*, *>, key: String, path: String): Boolean =
        (raw[key] as? Boolean) ?: throw IllegalArgumentException("$path.$key must be a boolean")

    private fun requiredInt(raw: Map<*, *>, key: String, path: String): Int =
        (raw[key] as? Number)?.toInt() ?: throw IllegalArgumentException("$path.$key must be an integer")

    private fun requiredLong(raw: Map<*, *>, key: String, path: String): Long =
        (raw[key] as? Number)?.toLong() ?: throw IllegalArgumentException("$path.$key must be an integer")

    private fun optionalLong(raw: Map<*, *>, key: String, fallback: Long, path: String): Long {
        val result = raw[key]?.let { value ->
            (value as? Number)?.toLong() ?: throw IllegalArgumentException("$path.$key must be an integer")
        } ?: fallback
        require(result >= 0) { "$path.$key must not be negative" }
        return result
    }

    private fun requiredMap(raw: Map<*, *>, key: String, path: String): Map<*, *> =
        raw[key] as? Map<*, *> ?: throw IllegalArgumentException("$path.$key must be a section")

    private fun optionalStringList(raw: Map<*, *>, key: String, path: String): List<String> {
        val list = raw[key] ?: return emptyList()
        require(list is List<*> && list.all { it is String && it.isNotBlank() }) {
            "$path.$key must be a string list"
        }
        return list.filterIsInstance<String>()
    }

    private fun requiredNumberList(raw: Map<*, *>, key: String, path: String): List<Number> {
        val list = raw[key]
        require(list is List<*> && list.all { it is Number }) { "$path.$key must be an integer list" }
        return list.filterIsInstance<Number>()
    }
}
