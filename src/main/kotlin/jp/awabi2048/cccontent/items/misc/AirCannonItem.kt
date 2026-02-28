package jp.awabi2048.cccontent.items.misc

import jp.awabi2048.cccontent.items.CustomItem
import jp.awabi2048.cccontent.items.CustomItemI18n
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.Vector
import java.io.File

object AirCannonConfig {
    private lateinit var config: YamlConfiguration
    private lateinit var configFile: File

    fun initialize(plugin: JavaPlugin) {
        val miscDir = File(plugin.dataFolder, "misc")
        if (!miscDir.exists()) {
            miscDir.mkdirs()
        }

        configFile = File(miscDir, "air_cannon.yml")
        if (!configFile.exists()) {
            plugin.saveResource("misc/air_cannon.yml", false)
        }

        reload()
    }

    fun reload() {
        config = YamlConfiguration.loadConfiguration(configFile)
    }

    fun cooldownTicks(): Int = config.getInt("cooldown_ticks", 10).coerceAtLeast(1)
    fun range(): Double = config.getDouble("range", 6.0).coerceAtLeast(0.1)
    fun radius(): Double = config.getDouble("radius", 1.8).coerceAtLeast(0.1)
    fun minFalloffScale(): Double = config.getDouble("min_falloff_scale", 0.25).coerceIn(0.0, 1.0)

    fun targetHorizontalPower(): Double = config.getDouble("target.horizontal_power", 1.2)
    fun targetUpwardPower(): Double = config.getDouble("target.upward_power", 0.55)
    fun targetScale(): Double = config.getDouble("target.scale", 1.0).coerceAtLeast(0.0)

    fun recoilHorizontalPower(): Double = config.getDouble("recoil.horizontal_power", 0.35)
    fun recoilUpwardPower(): Double = config.getDouble("recoil.upward_power", 0.2)
    fun recoilScale(): Double = config.getDouble("recoil.scale", 1.0).coerceAtLeast(0.0)

    fun useSoundKey(): String = config.getString("sound.use.key", "minecraft:entity.breeze.shoot")!!
    fun useSoundVolume(): Float = config.getDouble("sound.use.volume", 1.0).toFloat().coerceAtLeast(0f)
    fun useSoundPitch(): Float = config.getDouble("sound.use.pitch", 1.1).toFloat().coerceAtLeast(0f)
}

class AirCannonItem : CustomItem {
    override val feature = "misc"
    override val id = "air_cannon"
    override val displayName = "§b空気砲"
    override val itemModel = NamespacedKey.minecraft("wind_charge")
    override val lore = listOf(
        "§7右クリックで前方の空間を圧縮して放出します。",
        "§7前方の対象を§f前方+上方向§7へ吹き飛ばします。",
        "§7使用者も§f後ろ上方§7に小さく反動します。"
    )

    private val airCannonKey = NamespacedKey("cccontent", "air_cannon")

    override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)

    override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
        val item = ItemStack(Material.POISONOUS_POTATO, amount)
        val meta = item.itemMeta ?: return item

        val name = CustomItemI18n.text(player, "custom_items.$feature.$id.name", displayName)
        val localizedLore = CustomItemI18n.list(player, "custom_items.$feature.$id.lore", lore)

        meta.displayName(Component.text(name))
        meta.lore(localizedLore.map { Component.text(it) })
        meta.persistentDataContainer.set(airCannonKey, PersistentDataType.BYTE, 1)
        item.itemMeta = meta
        return item
    }

    override fun matches(item: ItemStack): Boolean {
        if (item.type != Material.POISONOUS_POTATO) return false
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(airCannonKey, PersistentDataType.BYTE)
    }

    override fun onRightClick(player: Player, event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) {
            return
        }
        if (event.hand != EquipmentSlot.HAND) {
            return
        }

        val item = event.item ?: return
        if (player.hasCooldown(item.type)) {
            event.isCancelled = true
            return
        }

        val eye = player.eyeLocation
        val origin = eye.toVector()
        val forward = eye.direction.clone().normalize()
        val horizontalForward = Vector(forward.x, 0.0, forward.z).normalizeOrZero()

        val targets = collectEntitiesInCylinder(player, origin, forward)
        for (target in targets) {
            applyKnockback(target, origin, forward, horizontalForward)
        }

        val recoilScale = AirCannonConfig.recoilScale()
        val recoil = horizontalForward.clone()
            .multiply(-AirCannonConfig.recoilHorizontalPower() * recoilScale)
            .setY(AirCannonConfig.recoilUpwardPower() * recoilScale)
        player.velocity = player.velocity.add(recoil)
        player.playSound(player.location, AirCannonConfig.useSoundKey(), AirCannonConfig.useSoundVolume(), AirCannonConfig.useSoundPitch())
        player.setCooldown(item.type, AirCannonConfig.cooldownTicks())
        event.isCancelled = true
    }

    private fun collectEntitiesInCylinder(player: Player, origin: Vector, axisDirection: Vector): List<Entity> {
        val range = AirCannonConfig.range()
        val radius = AirCannonConfig.radius()
        return player.world.getNearbyEntities(player.location, range, range, range)
            .asSequence()
            .filter { it.isValid && it != player }
            .filter { entity ->
                val toEntity = entity.location.toVector().subtract(origin)
                val axial = toEntity.dot(axisDirection)
                if (axial < 0.0 || axial > range) {
                    return@filter false
                }

                val radial = toEntity.subtract(axisDirection.clone().multiply(axial))
                radial.lengthSquared() <= radius * radius
            }
            .toList()
    }

    private fun applyKnockback(target: Entity, origin: Vector, axisDirection: Vector, horizontalForward: Vector) {
        val range = AirCannonConfig.range()
        val toEntity = target.location.toVector().subtract(origin)
        val axial = toEntity.dot(axisDirection).coerceIn(0.0, range)
        val normalizedDistance = (axial / range).coerceIn(0.0, 1.0)
        val baseScale = (1.0 - normalizedDistance).coerceAtLeast(AirCannonConfig.minFalloffScale())
        val scale = baseScale * AirCannonConfig.targetScale()

        val horizontal = horizontalForward.clone().multiply(AirCannonConfig.targetHorizontalPower() * scale)
        val upward = AirCannonConfig.targetUpwardPower() * scale
        val impulse = horizontal.setY(upward)

        target.velocity = target.velocity.add(impulse)
    }

    private fun Vector.normalizeOrZero(): Vector {
        return if (this.lengthSquared() <= 1.0E-6) {
            Vector(0.0, 0.0, 0.0)
        } else {
            this.normalize()
        }
    }
}
