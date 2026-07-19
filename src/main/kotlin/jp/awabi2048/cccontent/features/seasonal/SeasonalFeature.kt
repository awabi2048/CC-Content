package jp.awabi2048.cccontent.features.seasonal

import com.awabi2048.ccsystem.CCSystem
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask

class SeasonalFeature(private val plugin: JavaPlugin) {
    private var refreshTask: BukkitTask? = null
    private var loadResult: SeasonalRegistryLoadResult? = null
    private var calendar: SeasonalCalendarService? = null
    private var statuses: List<SeasonalEventStatus> = emptyList()

    fun initialize() {
        reload()
        refreshTask = plugin.server.scheduler.runTaskTimer(
            plugin,
            Runnable(::refresh),
            1_200L,
            1_200L
        )
    }

    fun reload() {
        val loaded = SeasonalEventRegistry.load(plugin)
        loadResult = loaded
        calendar = SeasonalCalendarService(loaded.settings.zoneId, loaded.settings.upcomingWindow)
        refresh()
        plugin.logger.info(
            "[Seasonal] enabled=${loaded.settings.enabled} definitions=${loaded.definitions.size} " +
                "rejected=${loaded.rejectedEntries}"
        )
    }

    fun refresh() {
        val loaded = loadResult ?: return
        statuses = if (loaded.settings.enabled) {
            checkNotNull(calendar).evaluateAll(
                loaded.definitions,
                CCSystem.getAPI().getSharedClockService().now()
            )
        } else {
            loaded.definitions.map { definition ->
                SeasonalEventStatus(definition, SeasonalEventState.DISABLED, null)
            }
        }
    }

    fun statuses(): List<SeasonalEventStatus> = statuses.toList()

    fun shutdown() {
        refreshTask?.cancel()
        refreshTask = null
        statuses = emptyList()
        loadResult = null
        calendar = null
    }
}
