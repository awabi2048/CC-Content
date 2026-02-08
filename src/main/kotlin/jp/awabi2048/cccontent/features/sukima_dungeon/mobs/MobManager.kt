package jp.awabi2048.cccontent.features.sukima_dungeon.mobs

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.attribute.Attribute
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
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
        val type: EntityType,
        val health: Double,
        val attack: Double,
        val scale: Double,
        val equipment: Map<String, Material>,
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
        val file = File(plugin.dataFolder, "sukima/mobs.yml")
        if (!file.exists()) {
            file.parentFile.mkdirs()
            plugin.saveResource("sukima/mobs.yml", false)
        }
        
        val config = YamlConfiguration.loadConfiguration(file)
        val section = config.getConfigurationSection("mobs") ?: return

        mobs.clear()
        for (key in section.getKeys(false)) {
            try {
                val type = EntityType.valueOf(section.getString("$key.type") ?: "ZOMBIE")
                val health = section.getDouble("$key.health", 20.0)
                val attack = section.getDouble("$key.attack", 5.0)
                val scale = section.getDouble("$key.scale", 1.0)
                
                val equipmentSection = section.getConfigurationSection("$key.equipment")
                val equipment = mutableMapOf<String, Material>()
                if (equipmentSection != null) {
                    for (slot in equipmentSection.getKeys(false)) {
                        val matName = equipmentSection.getString(slot)
                        if (matName != null) {
                            equipment[slot] = Material.valueOf(matName)
                        }
                    }
                }

                val minDist = section.getDouble("$key.min_dist", 0.0)
                val maxDist = section.getDouble("$key.max_dist", Double.MAX_VALUE)
                val specialBehaviorId = section.getString("$key.special_behavior")

                mobs[key] = MobDefinition(type, health, attack, scale, equipment, minDist, maxDist, specialBehaviorId)
            } catch (e: Exception) {
                plugin.logger.warning("Failed to load mob definition: $key")
                e.printStackTrace()
            }
        }
    }

    private fun loadSpawnSettings() {
        val file = File(plugin.dataFolder, "sukima/mob_spawn.yml")
        if (!file.exists()) {
            file.parentFile.mkdirs()
            plugin.saveResource("sukima/mob_spawn.yml", false)
        }

        val config = YamlConfiguration.loadConfiguration(file)
        val section = config.getConfigurationSection("themes") ?: return

        themeSpawns.clear()
        for (key in section.getKeys(false)) {
            val list = section.getStringList(key)
            themeSpawns[key] = list
        }
    }

    fun spawnMob(location: Location, themeName: String, center: Location): LivingEntity? {
        val mobList = themeSpawns[themeName]
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
        
        // Apply stats
        val maxHealth = definition.health * multiplier
        val attack = definition.attack * multiplier

        entity.getAttribute(Attribute.MAX_HEALTH)?.baseValue = maxHealth
        entity.health = maxHealth
        entity.getAttribute(Attribute.ATTACK_DAMAGE)?.baseValue = attack
        entity.getAttribute(Attribute.SCALE)?.baseValue = definition.scale

        // Equip items
        val equip = entity.equipment
        if (equip != null) {
            definition.equipment["helmet"]?.let { equip.helmet = ItemStack(it) }
            definition.equipment["chestplate"]?.let { equip.chestplate = ItemStack(it) }
            definition.equipment["leggings"]?.let { equip.leggings = ItemStack(it) }
            definition.equipment["boots"]?.let { equip.boots = ItemStack(it) }
            definition.equipment["main_hand"]?.let { equip.setItemInMainHand(ItemStack(it)) }
            definition.equipment["off_hand"]?.let { equip.setItemInOffHand(ItemStack(it)) }
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
}
