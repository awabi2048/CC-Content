package jp.awabi2048.cccontent.features.sukima_dungeon.mobs

import jp.awabi2048.cccontent.mob.MobDefinition
import jp.awabi2048.cccontent.mob.MobService
import jp.awabi2048.cccontent.mob.MobSpawnOptions
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.attribute.Attribute
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.LivingEntity
import org.bukkit.plugin.java.JavaPlugin
import jp.awabi2048.cccontent.features.sukima_dungeon.SukimaConfigHelper
import java.io.File
import java.util.Random

private data class ThemeMobSpawnEntry(
    val id: String,
    val weight: Int,
    val minDist: Double,
    val maxDist: Double,
    val specialBehaviorId: String?
)

class MobManager(
    private val plugin: JavaPlugin,
    private val mobService: MobService = MobService(plugin)
) {
    private val mobs = mutableMapOf<String, MobDefinition>()
    private val themeSpawns = mutableMapOf<String, List<ThemeMobSpawnEntry>>()
    private val random = Random()
    private var amplificationRatio = 0.0

    private val behaviors = mutableMapOf<String, SpecialBehavior>()
    private val activeEntities = mutableMapOf<java.util.UUID, SpecialBehavior>()

    fun registerBehavior(behavior: SpecialBehavior) {
        behaviors[behavior.id] = behavior
    }

    fun load() {
        loadMobs()
        loadSpawnSettings()
        amplificationRatio = SukimaConfigHelper.getConfig(plugin).getDouble("mob_scaling.amplification_ratio", 0.0)
    }

    private fun loadMobs() {
        val loaded = mobService.reloadDefinitions()
        mobs.clear()
        mobs.putAll(loaded)
    }

    private fun loadSpawnSettings() {
        val file = File(plugin.dataFolder, "config/sukima_dungeon/theme.yml")
        if (!file.exists()) {
            file.parentFile.mkdirs()
            plugin.saveResource("config/sukima_dungeon/theme.yml", false)
        }

        val config = YamlConfiguration.loadConfiguration(file)
        val section = config.getConfigurationSection("themes") ?: return

        themeSpawns.clear()
        for (themeId in section.getKeys(false)) {
            val themeSection = section.getConfigurationSection(themeId) ?: continue
            val mobSectionList = themeSection.getMapList("mobs")
            if (mobSectionList.isEmpty()) {
                continue
            }

            val entries = mobSectionList.mapNotNull { raw ->
                val mobId = raw["id"]?.toString()?.trim().orEmpty()
                if (mobId.isBlank()) {
                    plugin.logger.warning("[SukimaDungeon] theme.yml の mobs.id が空です: theme=$themeId")
                    return@mapNotNull null
                }
                if (!mobs.containsKey(mobId)) {
                    plugin.logger.warning("[SukimaDungeon] theme.yml が未定義 mob_definition を参照しています: theme=$themeId mob=$mobId")
                    return@mapNotNull null
                }

                val weight = when (val rawWeight = raw["weight"]) {
                    is Number -> rawWeight.toInt()
                    is String -> rawWeight.toIntOrNull()
                    else -> null
                } ?: 1
                if (weight <= 0) {
                    plugin.logger.warning("[SukimaDungeon] theme.yml の weight は1以上である必要があります: theme=$themeId mob=$mobId")
                    return@mapNotNull null
                }

                val minDist = when (val value = raw["min_dist"]) {
                    is Number -> value.toDouble()
                    is String -> value.toDoubleOrNull()
                    else -> null
                } ?: 0.0
                val maxDist = when (val value = raw["max_dist"]) {
                    is Number -> value.toDouble()
                    is String -> value.toDoubleOrNull()
                    else -> null
                } ?: Double.MAX_VALUE

                ThemeMobSpawnEntry(
                    id = mobId,
                    weight = weight,
                    minDist = minDist,
                    maxDist = maxDist,
                    specialBehaviorId = raw["special_behavior"]?.toString()?.takeIf { it.isNotBlank() }
                )
            }

            if (entries.isNotEmpty()) {
                themeSpawns[themeId] = entries
            }
        }
    }

    fun spawnMob(location: Location, themeName: String, center: Location): LivingEntity? {
        val worldName = location.world?.name ?: return null
        val sessionKey = "sukima:$worldName"
        val spawnThrottle = mobService.getSpawnThrottle(sessionKey)
        val intervalChance = (1.0 / spawnThrottle.intervalMultiplier).coerceIn(0.0, 1.0)
        if (random.nextDouble() < spawnThrottle.skipChance) {
            return null
        }
        if (random.nextDouble() > intervalChance) {
            return null
        }

        val mobList = themeSpawns[themeName]
        if (mobList.isNullOrEmpty()) {
            plugin.logger.warning("No mob spawn settings found for theme: $themeName")
            return null
        }

        val distance = location.distance(center)

        val suitableMobs = mobList.filter { entry ->
            distance >= entry.minDist && distance <= entry.maxDist
        }
        if (suitableMobs.isEmpty()) {
            return null
        }

        val finalEntry = selectWeightedEntry(suitableMobs) ?: return null
        val definition = mobs[finalEntry.id] ?: return null
        
        val multiplier = 1.0 + (distance * amplificationRatio)

        val entity = mobService.spawn(
            definition,
            location,
            MobSpawnOptions(
                featureId = "sukima_dungeon",
                sessionKey = sessionKey,
                metadata = mapOf("theme" to themeName)
            )
        ) ?: return null
        entity.removeWhenFarAway = false

        val maxHealth = definition.health * multiplier
        val attack = definition.attack * multiplier

        entity.getAttribute(Attribute.MAX_HEALTH)?.baseValue = maxHealth
        entity.health = maxHealth
        entity.getAttribute(Attribute.ATTACK_DAMAGE)?.baseValue = attack
        entity.getAttribute(Attribute.MOVEMENT_SPEED)?.baseValue = definition.movementSpeed
        entity.getAttribute(Attribute.ARMOR)?.baseValue = definition.armor

        finalEntry.specialBehaviorId?.let { behaviorId ->
            val behavior = behaviors[behaviorId]
            if (behavior != null) {
                behavior.onApply(entity)
                activeEntities[entity.uniqueId] = behavior
            }
        }
        return entity
    }

    fun tick() {
        val iterator = activeEntities.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val entity = Bukkit.getEntity(entry.key) as? LivingEntity
            
            if (entity == null || entity.isDead || !entity.isValid) {
                iterator.remove()
                continue
            }
            
            entry.value.onTick(entity)
        }
    }

    private fun selectWeightedEntry(entries: List<ThemeMobSpawnEntry>): ThemeMobSpawnEntry? {
        val totalWeight = entries.sumOf { it.weight }
        if (totalWeight <= 0) {
            return null
        }

        var roll = random.nextInt(totalWeight)
        for (entry in entries) {
            roll -= entry.weight
            if (roll < 0) {
                return entry
            }
        }

        return entries.lastOrNull()
    }
}
