package jp.awabi2048.cccontent.features.minigame.core

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.UUID

class MiniGameSettings(private val plugin: JavaPlugin) {
    private val configPath = "config/minigame/settings.yml"
    private val stateFile = File(plugin.dataFolder, "data/minigame/games.yml")
    private lateinit var validated: MiniGameValidatedSettings
    private lateinit var state: YamlConfiguration

    fun initialize() {
        if (!File(plugin.dataFolder, configPath).exists()) plugin.saveResource(configPath, false)
        listOf("hideandseek", "chase", "colosseum", "endergolf").forEach { gameId ->
            val path = "config/minigame/$gameId.yml"
            if (!File(plugin.dataFolder, path).exists()) plugin.saveResource(path, false)
        }
        val settingsFile = File(plugin.dataFolder, configPath)
        val settings = YamlConfiguration.loadConfiguration(settingsFile)
        val settingsValues = sectionValues(settings, configPath)
        val gameValues = linkedMapOf<String, Map<String, Any?>>()
        listOf("hideandseek", "chase", "colosseum", "endergolf").forEach { gameId ->
            val file = File(plugin.dataFolder, "config/minigame/$gameId.yml")
            val config = YamlConfiguration.loadConfiguration(file)
            gameValues[gameId] = sectionValues(config, file.path)
        }
        validated = MiniGameSettingsValidator.validateSettings(settingsValues, gameValues)
        stateFile.parentFile.mkdirs()
        state = YamlConfiguration.loadConfiguration(stateFile)
        MiniGameSettingsValidator.validateState(sectionValues(state, "data/minigame/games.yml"))
    }

    fun defaultGameId(): String = validated.defaultGameId

    fun markerRadius(): Double = validated.markerRadius

    fun timeLimitSeconds(game: MiniGameId): Int {
        require(game.gameId in MiniGameSupportedGames.ids) { "unsupported mini-game id: ${game.gameId}" }
        val configured = validated.gameConfigs[game.gameId]?.timeLimitSeconds ?: validated.defaultTimeLimitSeconds
        val override = state.get(path(game))
        return if (override == null) configured else {
            require(override is Int && override in 30..3600) { "invalid time limit override: ${path(game)}" }
            override
        }
    }

    fun hunterCount(game: MiniGameId): Int = requireNotNull(validated.gameConfigs[game.gameId]?.hunterCount) {
        "hunter_count is not configured for ${game.gameId}"
    }

    fun firstTo(game: MiniGameId): Int = requireNotNull(validated.gameConfigs[game.gameId]?.firstTo) {
        "first_to is not configured for ${game.gameId}"
    }

    fun preparationSeconds(game: MiniGameId): Int {
        val configured = requireNotNull(validated.gameConfigs[game.gameId]?.preparationSeconds) {
            "preparation_seconds is not configured for ${game.gameId}"
        }
        val override = state.get(preparationPath(game))
        return if (override == null) configured else {
            require(override is Int && override in 0..600) { "invalid preparation override: ${preparationPath(game)}" }
            override
        }
    }

    fun adjustTimeLimit(game: MiniGameId, deltaSeconds: Int): Int {
        val next = (timeLimitSeconds(game) + deltaSeconds).coerceIn(30, 3600)
        state.set(path(game), next)
        state.save(stateFile)
        return next
    }

    fun adjustPreparation(game: MiniGameId, deltaSeconds: Int): Int {
        val next = (preparationSeconds(game) + deltaSeconds).coerceIn(0, 600)
        state.set(preparationPath(game), next)
        state.save(stateFile)
        return next
    }

    private fun path(game: MiniGameId): String = "games.${game.worldUuid}.${game.gameId}.time_limit_seconds"
    private fun preparationPath(game: MiniGameId): String = "games.${game.worldUuid}.${game.gameId}.preparation_seconds"

    private fun sectionValues(section: org.bukkit.configuration.ConfigurationSection, source: String): Map<String, Any?> {
        return section.getValues(false).mapValues { (key, value) ->
            when (value) {
                is org.bukkit.configuration.ConfigurationSection -> sectionValues(value, "$source.$key")
                else -> value
            }
        }
    }
}

data class MiniGameValidatedSettings(
    val defaultGameId: String,
    val defaultTimeLimitSeconds: Int,
    val markerRadius: Double,
    val gameConfigs: Map<String, MiniGameGameConfig>
)

data class MiniGameGameConfig(
    val timeLimitSeconds: Int,
    val hunterCount: Int? = null,
    val firstTo: Int? = null,
    val preparationSeconds: Int? = null
)

object MiniGameSettingsValidator {
    private val settingsKeys = setOf("default_game_id", "defaults")
    private val defaultKeys = setOf("time_limit_seconds", "marker_radius")
    private val hideAndSeekKeys = setOf("time_limit_seconds", "hunter_count", "preparation_seconds")
    private val hunterKeys = setOf("time_limit_seconds", "hunter_count")
    private val colosseumKeys = setOf("time_limit_seconds", "first_to")
    private val enderGolfKeys = setOf("time_limit_seconds")

    fun validateSettings(
        root: Map<String, Any?>,
        gameRoots: Map<String, Map<String, Any?>> = emptyMap()
    ): MiniGameValidatedSettings {
        requireKeys(root, settingsKeys, "settings")
        val defaultGameId = requireString(root, "default_game_id", "settings")
        require(defaultGameId in MiniGameSupportedGames.ids) { "unsupported default_game_id: $defaultGameId" }
        val defaults = requireMap(root, "defaults", "settings")
        requireKeys(defaults, defaultKeys, "settings.defaults")
        val time = requireIntRange(defaults, "time_limit_seconds", 30..3600, "settings.defaults")
        val radius = requireFiniteNumber(defaults, "marker_radius", "settings.defaults")
        require(radius in 0.5..4.0) { "marker_radius is out of range" }
        val configs = linkedMapOf<String, MiniGameGameConfig>()
        for (gameId in listOf("hideandseek", "chase", "colosseum", "endergolf")) {
            configs[gameId] = validateGame(gameId, requireNotNull(gameRoots[gameId]) {
                "missing game config: $gameId"
            })
        }
        return MiniGameValidatedSettings(defaultGameId, time, radius, configs)
    }

    fun validateGame(gameId: String, root: Map<String, Any?>): MiniGameGameConfig = when (gameId) {
        "hideandseek" -> {
            requireKeys(root, hideAndSeekKeys, gameId)
            MiniGameGameConfig(
                requireIntRange(root, "time_limit_seconds", 30..3600, gameId),
                hunterCount = requireIntRange(root, "hunter_count", 1..99, gameId),
                preparationSeconds = requireIntRange(root, "preparation_seconds", 0..600, gameId)
            )
        }
        "chase" -> {
            requireKeys(root, hunterKeys, gameId)
            MiniGameGameConfig(
                requireIntRange(root, "time_limit_seconds", 30..3600, gameId),
                hunterCount = requireIntRange(root, "hunter_count", 1..99, gameId)
            )
        }
        "colosseum" -> {
            requireKeys(root, colosseumKeys, gameId)
            MiniGameGameConfig(
                requireIntRange(root, "time_limit_seconds", 30..3600, gameId),
                firstTo = requireIntRange(root, "first_to", 1..99, gameId)
            )
        }
        "endergolf" -> {
            requireKeys(root, enderGolfKeys, gameId)
            MiniGameGameConfig(requireIntRange(root, "time_limit_seconds", 30..3600, gameId))
        }
        "race" -> error("race.yml must not be configured")
        else -> error("unsupported mini-game id: $gameId")
    }

    fun validateState(root: Map<String, Any?>) {
        if (root.isEmpty()) return
        requireKeys(root, setOf("games"), "games.yml")
        val games = requireMap(root, "games", "games.yml")
        games.forEach { (world, worldValue) ->
            require(runCatching { UUID.fromString(world) }.isSuccess) { "invalid world UUID in games.yml: $world" }
            val gameMap = requireMapValue(worldValue, "games.$world")
            gameMap.forEach { (gameId, value) ->
                require(gameId in MiniGameSupportedGames.ids) { "unsupported game id in games.yml: $gameId" }
                val config = requireMapValue(value, "games.$world.$gameId")
                val allowed = if (gameId == "hideandseek") {
                    setOf("time_limit_seconds", "preparation_seconds")
                } else {
                    setOf("time_limit_seconds")
                }
                requireKeys(config, allowed, "games.$world.$gameId")
                requireIntRange(config, "time_limit_seconds", 30..3600, "games.$world.$gameId")
                config["preparation_seconds"]?.let {
                    requireIntRange(config, "preparation_seconds", 0..600, "games.$world.$gameId")
                }
            }
        }
    }

    private fun requireKeys(root: Map<String, Any?>, allowed: Set<String>, source: String) {
        require(root.keys.all { it in allowed }) { "unknown key in $source" }
    }

    private fun requireMap(root: Map<String, Any?>, key: String, source: String): Map<String, Any?> =
        requireMapValue(root[key], "$source.$key")

    @Suppress("UNCHECKED_CAST")
    private fun requireMapValue(value: Any?, source: String): Map<String, Any?> =
        (value as? Map<String, Any?>) ?: error("map is required: $source")

    private fun requireString(root: Map<String, Any?>, key: String, source: String): String =
        (root[key] as? String)?.takeIf { it.isNotBlank() } ?: error("non-blank string is required: $source.$key")

    private fun requireIntRange(root: Map<String, Any?>, key: String, range: IntRange, source: String): Int {
        val value = root[key]
        require(value is Int && value in range) { "integer $key is out of range or has an invalid type: $source.$key" }
        return value
    }

    private fun requireFiniteNumber(root: Map<String, Any?>, key: String, source: String): Double {
        val value = root[key]
        require(value is Number && value.toDouble().isFinite()) { "finite number is required: $source.$key" }
        return value.toDouble()
    }
}
