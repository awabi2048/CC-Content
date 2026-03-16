package jp.awabi2048.cccontent.mob

import jp.awabi2048.cccontent.mob.type.SparkZombieMobType
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.attribute.Attribute
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.io.File
import java.util.UUID

class MobService(private val plugin: JavaPlugin) {
    private val customMobTypes = mutableMapOf<String, MobType>()
    private val activeMobs = mutableMapOf<UUID, ActiveMob>()
    private val definitions = mutableMapOf<String, MobDefinition>()
    private val mobTypeKey = NamespacedKey(plugin, "mob_type_id")
    private val mobDefinitionKey = NamespacedKey(plugin, "mob_definition_id")
    private val mobFeatureKey = NamespacedKey(plugin, "mob_feature_id")
    private val customMobKey = NamespacedKey(plugin, "is_custom_mob")
    private var tickTask: BukkitTask? = null

    init {
        registerMobType(SparkZombieMobType())
    }

    fun registerMobType(mobType: MobType) {
        customMobTypes[mobType.id.lowercase()] = mobType
    }

    fun resolveMobType(typeId: String): MobType? {
        customMobTypes[typeId.trim().lowercase()]?.let { return it }

        val entityType = try {
            EntityType.valueOf(typeId.trim().uppercase())
        } catch (_: IllegalArgumentException) {
            return null
        }

        if (!entityType.isAlive) {
            return null
        }

        return VanillaMobType(entityType.name, entityType)
    }

    fun reloadDefinitions(): Map<String, MobDefinition> {
        val file = ensureCommonDefinitionFile()
        val loaded = loadDefinitions(file, null, "[MobService]")
        definitions.clear()
        definitions.putAll(loaded)

        if (definitions.isEmpty()) {
            plugin.logger.severe("[MobService] config/mob_definition.yml が空のためモブ定義を利用できません")
        }

        return definitions.toMap()
    }

    fun getDefinitions(): Map<String, MobDefinition> {
        if (definitions.isEmpty()) {
            reloadDefinitions()
        }
        return definitions.toMap()
    }

    fun getDefinition(id: String): MobDefinition? {
        if (definitions.isEmpty()) {
            reloadDefinitions()
        }
        return definitions[id]
    }

    fun getDefinitionIds(): Set<String> {
        if (definitions.isEmpty()) {
            reloadDefinitions()
        }
        return definitions.keys
    }

    fun spawnByDefinitionId(definitionId: String, location: Location, options: MobSpawnOptions): LivingEntity? {
        val definition = getDefinition(definitionId) ?: return null
        return spawn(definition, location, options)
    }

    fun loadDefinitions(file: File, rootPath: String? = null, logPrefix: String): Map<String, MobDefinition> {
        val config = YamlConfiguration.loadConfiguration(file)
        val section = if (rootPath.isNullOrBlank()) {
            config
        } else {
            config.getConfigurationSection(rootPath)
        }

        if (section == null) {
            plugin.logger.severe("$logPrefix mob_definition のルートセクションが見つかりません: ${rootPath ?: "<root>"}")
            return emptyMap()
        }

        val loaded = mutableMapOf<String, MobDefinition>()
        for (mobId in section.getKeys(false)) {
            val mobSection = section.getConfigurationSection(mobId)
            if (mobSection == null) {
                plugin.logger.severe("$logPrefix mob_definition.yml の読み込み失敗: $mobId")
                continue
            }

            val typeId = mobSection.getString("type")?.trim().orEmpty()
            if (typeId.isBlank()) {
                plugin.logger.severe("$logPrefix mob_definition.yml の type が空です: $mobId")
                continue
            }
            val resolvedType = resolveMobType(typeId)
            if (resolvedType == null) {
                plugin.logger.severe("$logPrefix mob_definition.yml の type が不正です: $mobId type=$typeId")
                continue
            }

            val equipment = parseEquipment(mobId, mobSection, logPrefix)
            loaded[mobId] = MobDefinition(
                id = mobId,
                typeId = resolvedType.id,
                health = mobSection.getDouble("health", 20.0).coerceAtLeast(1.0),
                attack = mobSection.getDouble("attack", 1.0).coerceAtLeast(0.0),
                movementSpeed = mobSection.getDouble("movement_speed", 0.23).coerceAtLeast(0.01),
                armor = mobSection.getDouble("armor", 0.0).coerceAtLeast(0.0),
                scale = mobSection.getDouble("scale", 1.0).coerceAtLeast(0.1),
                equipment = equipment
            )
        }
        return loaded
    }

    fun spawn(definition: MobDefinition, location: Location, options: MobSpawnOptions): LivingEntity? {
        val world = location.world ?: return null
        val mobType = resolveMobType(definition.typeId)
        if (mobType == null) {
            plugin.logger.warning("[MobService] 未登録の mob type です: ${definition.typeId}")
            return null
        }

        val entity = world.spawnEntity(location, mobType.baseEntityType) as? LivingEntity ?: return null
        markEntity(entity, definition, mobType, options)
        applyDefinitionStats(entity, definition)

        val spawnContext = MobSpawnContext(plugin, entity, definition, mobType, options)
        val runtime = mobType.createRuntime(spawnContext)
        activeMobs[entity.uniqueId] = ActiveMob(
            entityId = entity.uniqueId,
            definition = definition,
            mobType = mobType,
            featureId = options.featureId,
            combatActiveProvider = options.combatActiveProvider,
            metadata = options.metadata,
            runtime = runtime
        )

        mobType.applyDefaultEquipment(spawnContext, runtime)
        mobType.onSpawn(spawnContext, runtime)
        applyDefinitionEquipment(entity, definition, onlyIfEmpty = true)
        return entity
    }

    fun startTickTask(intervalTicks: Long = 10L) {
        if (tickTask != null) {
            return
        }

        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            val iterator = activeMobs.entries.iterator()
            while (iterator.hasNext()) {
                val (_, activeMob) = iterator.next()
                val entity = Bukkit.getEntity(activeMob.entityId) as? LivingEntity
                if (entity == null || !entity.isValid || entity.isDead) {
                    iterator.remove()
                    continue
                }

                activeMob.tickCount += intervalTicks
                activeMob.mobType.onTick(
                    MobRuntimeContext(plugin, entity, activeMob),
                    activeMob.runtime
                )
            }
        }, intervalTicks, intervalTicks)
    }

    fun shutdown() {
        tickTask?.cancel()
        tickTask = null
        activeMobs.clear()
    }

    fun untrack(entityId: UUID) {
        activeMobs.remove(entityId)
    }

    fun handleAttack(event: EntityDamageByEntityEvent, attacker: LivingEntity) {
        val activeMob = activeMobs[attacker.uniqueId] ?: return
        activeMob.mobType.onAttack(
            MobAttackContext(
                plugin = plugin,
                entity = attacker,
                activeMob = activeMob,
                event = event,
                target = event.entity as? LivingEntity
            ),
            activeMob.runtime
        )
    }

    fun handleDamaged(event: EntityDamageByEntityEvent, damaged: LivingEntity) {
        val activeMob = activeMobs[damaged.uniqueId] ?: return
        activeMob.mobType.onDamaged(
            MobDamagedContext(
                plugin = plugin,
                entity = damaged,
                activeMob = activeMob,
                event = event,
                attacker = event.damager as? LivingEntity
            ),
            activeMob.runtime
        )
    }

    fun handleDeath(event: EntityDeathEvent) {
        val entity = event.entity
        val activeMob = activeMobs.remove(entity.uniqueId) ?: return
        activeMob.mobType.onDeath(
            MobDeathContext(plugin, entity, activeMob, event),
            activeMob.runtime
        )
    }

    private fun markEntity(entity: LivingEntity, definition: MobDefinition, mobType: MobType, options: MobSpawnOptions) {
        val container = entity.persistentDataContainer
        container.set(mobTypeKey, PersistentDataType.STRING, mobType.id)
        container.set(mobDefinitionKey, PersistentDataType.STRING, definition.id)
        container.set(mobFeatureKey, PersistentDataType.STRING, options.featureId)
        container.set(customMobKey, PersistentDataType.BYTE, if (mobType.isCustom) 1 else 0)
    }

    private fun applyDefinitionStats(entity: LivingEntity, definition: MobDefinition) {
        entity.getAttribute(Attribute.MAX_HEALTH)?.baseValue = definition.health
        entity.getAttribute(Attribute.ATTACK_DAMAGE)?.baseValue = definition.attack
        entity.getAttribute(Attribute.MOVEMENT_SPEED)?.baseValue = definition.movementSpeed
        entity.getAttribute(Attribute.ARMOR)?.baseValue = definition.armor
        entity.getAttribute(Attribute.SCALE)?.baseValue = definition.scale
        entity.health = definition.health
    }

    private fun applyDefinitionEquipment(entity: LivingEntity, definition: MobDefinition, onlyIfEmpty: Boolean) {
        val equipment = entity.equipment ?: return
        definition.equipment.forEach { (slot, material) ->
            if (onlyIfEmpty && !equipment.getItem(slot).isNullOrAir()) {
                return@forEach
            }
            equipment.setItem(slot, ItemStack(material))
        }
    }

    private fun parseEquipment(mobId: String, section: ConfigurationSection, logPrefix: String): Map<EquipmentSlot, Material> {
        val equipment = mutableMapOf<EquipmentSlot, Material>()
        val equipmentSection = section.getConfigurationSection("equipment") ?: return equipment
        for (slotKey in equipmentSection.getKeys(false)) {
            val slot = parseEquipmentSlot(slotKey)
            if (slot == null) {
                plugin.logger.severe("$logPrefix equipment slot が不正です: $mobId slot=$slotKey")
                continue
            }

            val materialName = equipmentSection.getString(slotKey) ?: continue
            val material = try {
                Material.valueOf(materialName)
            } catch (_: IllegalArgumentException) {
                plugin.logger.severe("$logPrefix equipment material が不正です: $mobId slot=$slotKey material=$materialName")
                continue
            }
            equipment[slot] = material
        }
        return equipment
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

    private fun ItemStack?.isNullOrAir(): Boolean {
        return this == null || this.type.isAir
    }

    private fun ensureCommonDefinitionFile(): File {
        val file = File(plugin.dataFolder, "config/mob_definition.yml")
        if (!file.exists()) {
            file.parentFile.mkdirs()
            plugin.saveResource("config/mob_definition.yml", false)
        }
        return file
    }
}
