package jp.awabi2048.cccontent.features.sukima_dungeon.items

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Display.Billboard
import org.bukkit.entity.EntityType
import org.bukkit.entity.Interaction
import org.bukkit.entity.ItemDisplay
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.Transformation
import org.joml.AxisAngle4f
import org.joml.Vector3f
import java.io.File
import java.util.Random
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.inventory.EquipmentSlotGroup

class ItemManager(private val plugin: JavaPlugin) {
    private val random = Random()
    private val items = mutableListOf<DungeonItem>()
    private var totalWeight = 0

    data class DungeonItem(
        val material: Material,
        val name: String,
        val weight: Int,
        val lore: List<String>,
        val enchantments: Map<String, Int>? = null,
        val attributeModifiers: List<AttributeData>? = null,
        val itemModel: String? = null,
        val customModelData: Int? = null
    )

    data class AttributeData(
        val attribute: String,
        val name: String,
        val amount: Double,
        val operation: String,
        val slot: String? = null
    )

    companion object {
        val KEY_ITEM_TYPE = "dungeon_item_type"
        val KEY_ITEM_NAME = "dungeon_item_name"
    }

    fun load() {
        val file = File(plugin.dataFolder, "sukima/items.yml")
        if (!file.exists()) {
            file.parentFile.mkdirs()
            plugin.saveResource("sukima/items.yml", false)
        }
        
        val config = YamlConfiguration.loadConfiguration(file)
        val section = config.getConfigurationSection("items") ?: return
        
        items.clear()
        totalWeight = 0
        
        for (key in section.getKeys(false)) {
            val matName = section.getString("$key.material") ?: continue
            val material = try { Material.valueOf(matName) } catch (e: Exception) { continue }
            val name = section.getString("$key.name") ?: material.name
            val weight = section.getInt("$key.weight", 1)
            val lore = section.getStringList("$key.lore").map { org.bukkit.ChatColor.translateAlternateColorCodes('&', it) }
            
            val enchantSection = section.getConfigurationSection("$key.enchantments")
            val enchantments = mutableMapOf<String, Int>()
            enchantSection?.getKeys(false)?.forEach { enchantKey ->
                enchantments[enchantKey] = enchantSection.getInt(enchantKey)
            }

            val attributeList = mutableListOf<AttributeData>()
            val attributeSection = section.getConfigurationSection("$key.attribute_modifiers")
            attributeSection?.getKeys(false)?.forEach { attrKey ->
                val attrSubSection = attributeSection.getConfigurationSection(attrKey) ?: return@forEach
                val attribute = attrSubSection.getString("attribute") ?: return@forEach
                val attrName = attrSubSection.getString("name") ?: attrKey
                val amount = attrSubSection.getDouble("amount")
                val operation = attrSubSection.getString("operation") ?: "ADD_NUMBER"
                val slot = attrSubSection.getString("slot")
                attributeList.add(AttributeData(attribute, attrName, amount, operation, slot))
            }
            
            val itemModel = section.getString("$key.item_model")
            val customModelData = if (section.contains("$key.custom_model_data")) section.getInt("$key.custom_model_data") else null
            
            items.add(DungeonItem(material, name, weight, lore, 
                if (enchantments.isEmpty()) null else enchantments, 
                if (attributeList.isEmpty()) null else attributeList,
                itemModel, customModelData))
            totalWeight += weight
        }
    }

    private fun getRandomItem(): DungeonItem? {
        if (items.isEmpty()) return null
        var r = random.nextInt(totalWeight)
        for (item in items) {
            r -= item.weight
            if (r < 0) return item
        }
        return items.lastOrNull()
    }

    fun spawnItem(location: Location) {
        val item = getRandomItem() ?: return
        val world = location.world ?: return

        // Create Item Display
        val display = world.spawnEntity(location, EntityType.ITEM_DISPLAY) as ItemDisplay
        val itemStack = ItemStack(item.material)
        val meta = itemStack.itemMeta
        if (meta != null) {
            meta.setDisplayName(org.bukkit.ChatColor.translateAlternateColorCodes('&', item.name))
            
            // Apply lore if provided
            if (item.lore.isNotEmpty()) {
                meta.lore = item.lore
            }
            
            // Apply enchantments if provided (clears existing before applying new ones if specified)
            item.enchantments?.let { enchants ->
                meta.enchants.keys.forEach { meta.removeEnchant(it) }
                enchants.forEach { (enchKey, level) ->
                    val enchantment = org.bukkit.enchantments.Enchantment.getByKey(NamespacedKey.minecraft(enchKey.lowercase()))
                    if (enchantment != null) {
                        meta.addEnchant(enchantment, level, true)
                    }
                }
            }
            
            // Apply attribute modifiers if provided (clears existing before applying new ones if specified)
            item.attributeModifiers?.let { attrs ->
                meta.attributeModifiers?.asMap()?.keys?.forEach { meta.removeAttributeModifier(it) }
                attrs.forEach { attrData ->
                    val attribute = try { org.bukkit.Registry.ATTRIBUTE.get(NamespacedKey.minecraft(attrData.attribute.lowercase())) } catch (e: Exception) { 
                        try { org.bukkit.Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic." + attrData.attribute.lowercase())) } catch(e2: Exception) { null }
                    }
                    if (attribute != null) {
                        val operation = try { AttributeModifier.Operation.valueOf(attrData.operation.uppercase()) } catch (e: Exception) { AttributeModifier.Operation.ADD_NUMBER }
                        val slot = if (attrData.slot != null) {
                            try { EquipmentSlotGroup.getByName(attrData.slot.uppercase()) } catch (e: Exception) { EquipmentSlotGroup.ANY }
                        } else {
                            EquipmentSlotGroup.ANY
                        }
                        val modifier = AttributeModifier(NamespacedKey(plugin, attrData.name.lowercase()), attrData.amount, operation, slot!!)
                        meta.addAttributeModifier(attribute, modifier)
                    }
                }
            }
            
            // Apply models if provided
            item.itemModel?.let { meta.setItemModel(NamespacedKey.fromString(it)) }
            item.customModelData?.let { meta.setCustomModelData(it) }
            
            itemStack.itemMeta = meta
        }
        display.setItemStack(itemStack)
        display.billboard = Billboard.FIXED
        
        val randomYRot = (random.nextFloat() * 360.0).toFloat()
        display.setRotation(randomYRot, 0f)
        
        val oldTrans = display.transformation
        val axisAngle = AxisAngle4f(1.5708f, 1f, 0f, 0f)
        val leftRot = org.joml.Quaternionf(axisAngle)
        
        val newTrans = Transformation(
            Vector3f(0f, 0.025f, 0f),
            leftRot,
            Vector3f(0.6f, 0.6f, 0.6f),
            oldTrans.rightRotation
        )
        display.transformation = newTrans

        // Create Interaction Entity
        val interaction = world.spawnEntity(location, EntityType.INTERACTION) as Interaction
        interaction.interactionWidth = 0.6f
        interaction.interactionHeight = 0.1f
        
        // Link data
        val pdc = interaction.persistentDataContainer
        val ns = NamespacedKey(plugin, KEY_ITEM_TYPE)
        val nsName = NamespacedKey(plugin, KEY_ITEM_NAME)
        
        pdc.set(ns, PersistentDataType.STRING, item.material.name)
        pdc.set(nsName, PersistentDataType.STRING, item.name)
        
        val displayKey = NamespacedKey(plugin, "display_uuid")
        pdc.set(displayKey, PersistentDataType.STRING, display.uniqueId.toString())
    }

    fun saveItem(id: String, item: ItemStack, weight: Int) {
        val file = File(plugin.dataFolder, "sukima/items.yml")
        val config = YamlConfiguration.loadConfiguration(file)
        val section = config.getConfigurationSection("items") ?: config.createSection("items")
        
        val meta = item.itemMeta
        var finalId = id.lowercase().replace(" ", "_")
        var count = 1
        val baseId = finalId
        while (section.contains(finalId)) {
            finalId = "${baseId}_$count"
            count++
        }
        
        val itemPath = "items.$finalId"
        config.set("$itemPath.material", item.type.name)
        if (meta?.hasDisplayName() == true) {
            config.set("$itemPath.name", meta.displayName.replace("§", "&"))
        }
        config.set("$itemPath.weight", weight)
        if (meta?.hasLore() == true) {
            config.set("$itemPath.lore", meta.lore?.map { it.replace("§", "&") })
        }
        
        if (meta?.hasEnchants() == true) {
            meta.enchants.forEach { (enchant, level) ->
                config.set("$itemPath.enchantments.${enchant.key.key}", level)
            }
        }

        if (meta?.hasAttributeModifiers() == true) {
            var attrIdx = 0
            meta.attributeModifiers?.asMap()?.forEach { (attribute, modifiers) ->
                modifiers.forEach { modifier ->
                    val attrPath = "$itemPath.attribute_modifiers.attr_$attrIdx"
                    config.set("$attrPath.attribute", attribute.key.key)
                    config.set("$attrPath.name", modifier.name)
                    config.set("$attrPath.amount", modifier.amount)
                    config.set("$attrPath.operation", modifier.operation.name)
                    if (modifier.slotGroup != EquipmentSlotGroup.ANY) {
                        config.set("$attrPath.slot", modifier.slotGroup.toString())
                    }
                    attrIdx++
                }
            }
        }
        
        if (meta != null) {
            if (meta.hasItemModel()) {
                config.set("$itemPath.item_model", meta.itemModel?.toString())
            }
            if (meta.hasCustomModelData()) {
                config.set("$itemPath.custom_model_data", meta.customModelData)
            }
        }
        
        config.save(file)
        load() // Reload after saving
    }
}
