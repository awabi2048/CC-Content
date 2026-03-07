package jp.awabi2048.cccontent.features.sukima_dungeon.mobs

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.attribute.Attribute
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import jp.awabi2048.cccontent.features.sukima_dungeon.SukimaConfigHelper
import java.io.File
import java.util.Random

class MobManager(private val plugin: JavaPlugin) {
    private val mobs = mutableMapOf<String, MobDefinition>()
    private val themeSpawns = mutableMapOf<String, List<String>>()
    private val random = Random()
    private var amplificationRatio = 0.0

    data class MobDefinition(
        val id: String,
        val type: EntityType,
        val health: Double,
        val attack: Double,
        val movementSpeed: Double,
        val armor: Double,
        val scale: Double,
        val equipment: Map<EquipmentSlot, Material>,
        val minDist: Double,
        val maxDist: Double,
        val specialBehaviorId: String? = null
    )

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
        val file = File(plugin.dataFolder, "config/sukima_dungeon/mob_definition.yml")
        if (!file.exists()) {
            file.parentFile.mkdirs()
            plugin.saveResource("config/sukima_dungeon/mob_definition.yml", false)
        }
        
        val config = YamlConfiguration.loadConfiguration(file)
        val section = config.getConfigurationSection("mobs") ?: config

        mobs.clear()
        for (key in section.getKeys(false)) {
            val mobSection = section.getConfigurationSection(key)
            if (mobSection == null) {
                plugin.logger.warning("[SukimaDungeon] mob_definition.yml の読み込み失敗: $key")
                continue
            }

            val entityTypeName = mobSection.getString("type") ?: "ZOMBIE"
            val entityType = try {
                EntityType.valueOf(entityTypeName)
            } catch (_: IllegalArgumentException) {
                plugin.logger.warning("[SukimaDungeon] mob_definition.yml の type が不正です: $key type=$entityTypeName")
                continue
            }

            if (!entityType.isAlive) {
                plugin.logger.warning("[SukimaDungeon] mob_definition.yml の type は生物のみ指定可能です: $key type=$entityTypeName")
                continue
            }

            val health = mobSection.getDouble("health", 20.0).coerceAtLeast(1.0)
            val attack = mobSection.getDouble("attack", 5.0).coerceAtLeast(0.0)
            val movementSpeed = mobSection.getDouble("movement_speed", 0.23).coerceAtLeast(0.01)
            val armor = mobSection.getDouble("armor", 0.0).coerceAtLeast(0.0)
            val scale = mobSection.getDouble("scale", 1.0).coerceAtLeast(0.1)

            val equipmentSection = mobSection.getConfigurationSection("equipment")
            val equipment = mutableMapOf<EquipmentSlot, Material>()
            if (equipmentSection != null) {
                for (slotKey in equipmentSection.getKeys(false)) {
                    val slot = parseEquipmentSlot(slotKey)
                    if (slot == null) {
                        plugin.logger.warning("[SukimaDungeon] equipment slot が不正です: $key slot=$slotKey")
                        continue
                    }

                    val materialName = equipmentSection.getString(slotKey) ?: continue
                    val material = try {
                        Material.valueOf(materialName)
                    } catch (_: IllegalArgumentException) {
                        plugin.logger.warning("[SukimaDungeon] equipment material が不正です: $key slot=$slotKey material=$materialName")
                        continue
                    }

                    equipment[slot] = material
                }
            }

            val minDist = mobSection.getDouble("min_dist", 0.0)
            val maxDist = mobSection.getDouble("max_dist", Double.MAX_VALUE)
            val specialBehaviorId = mobSection.getString("special_behavior")

            mobs[key] = MobDefinition(
                id = key,
                type = entityType,
                health = health,
                attack = attack,
                movementSpeed = movementSpeed,
                armor = armor,
                scale = scale,
                equipment = equipment,
                minDist = minDist,
                maxDist = maxDist,
                specialBehaviorId = specialBehaviorId
            )
        }
    }

    private fun loadSpawnSettings() {
        val file = File(plugin.dataFolder, "config/sukima_dungeon/theme.yml")
        if (!file.exists()) {
            file.parentFile.mkdirs()
            plugin.saveResource("config/sukima_dungeon/theme.yml", false)
        }

        val config = YamlConfiguration.loadConfiguration(file)
        val section = config.getConfigurationSection("mob_spawn.themes") ?: return

        themeSpawns.clear()
        for (key in section.getKeys(false)) {
            val list = section.getStringList(key)
            themeSpawns[key] = list
        }
    }

    fun spawnMob(location: Location, themeName: String, center: Location): LivingEntity? {
        val mobList = themeSpawns[themeName] ?: themeSpawns["default"]
        if (mobList.isNullOrEmpty()) {
            plugin.logger.warning("No mob spawn settings found for theme: $themeName")
            return null
        }

        val distance = location.distance(center)
        
        // Filter mobs by distance
        val suitableMobs = mobList.filter { mobId ->
            val def = mobs[mobId]
            if (def != null) {
                distance >= def.minDist && distance <= def.maxDist
            } else {
                false
            }
        }

        // If no suitable mobs found, fallback to any from the list (or return)
        val finalMobId = if (suitableMobs.isNotEmpty()) {
            suitableMobs[random.nextInt(suitableMobs.size)]
        } else {
            // Fallback: just pick random or maybe nothing. 
            // Let's pick random to ensure something spawns if config is messy, 
            // but arguably we should spawn nothing if out of range. 
            // For now, let's respect the range strictly.
            return null
        }

        val definition = mobs[finalMobId] ?: return null
        
        // Calculate scaling based on distance (keep existing logic)
        val multiplier = 1.0 + (distance * amplificationRatio)

        val entity = location.world?.spawnEntity(location, definition.type) as? LivingEntity ?: return null
        entity.removeWhenFarAway = false

        // Apply stats
        val maxHealth = definition.health * multiplier
        val attack = definition.attack * multiplier

        entity.getAttribute(Attribute.MAX_HEALTH)?.baseValue = maxHealth
        entity.health = maxHealth
        entity.getAttribute(Attribute.ATTACK_DAMAGE)?.baseValue = attack
        entity.getAttribute(Attribute.MOVEMENT_SPEED)?.baseValue = definition.movementSpeed
        entity.getAttribute(Attribute.ARMOR)?.baseValue = definition.armor
        entity.getAttribute(Attribute.SCALE)?.baseValue = definition.scale

        // Equip items
        val equip = entity.equipment
        if (equip != null) {
            definition.equipment.forEach { (slot, material) ->
                equip.setItem(slot, ItemStack(material))
            }
        }

        // Apply special behavior if exists
        definition.specialBehaviorId?.let { behaviorId ->
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
            val entity = org.bukkit.Bukkit.getEntity(entry.key) as? LivingEntity
            
            if (entity == null || entity.isDead || !entity.isValid) {
                iterator.remove()
                continue
            }
            
            entry.value.onTick(entity)
        }
    }

    private fun parseEquipmentSlot(slotKey: String): EquipmentSlot? {
        return when (slotKey.lowercase()) {
            "helmet" -> EquipmentSlot.HEAD
            "chestplate" -> EquipmentSlot.CHEST
            "leggings" -> EquipmentSlot.LEGS
            "boots" -> EquipmentSlot.FEET
            "main_hand" -> EquipmentSlot.HAND
            "off_hand" -> EquipmentSlot.OFF_HAND
            else -> null
        }
    }
}
