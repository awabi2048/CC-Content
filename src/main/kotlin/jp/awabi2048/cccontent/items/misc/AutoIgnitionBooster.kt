package jp.awabi2048.cccontent.items.misc

import jp.awabi2048.cccontent.items.CustomItem
import jp.awabi2048.cccontent.items.CustomItemI18n
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.UUID
import net.kyori.adventure.text.Component

object AutoIgnitionBoosterKeys {
    val BOOSTER_KEY = NamespacedKey("cccontent", "auto_ignition_booster")
}

object AutoIgnitionBoosterConfig {
    private lateinit var config: YamlConfiguration
    private lateinit var configFile: File

    fun initialize(plugin: JavaPlugin) {
        val miscDir = File(plugin.dataFolder, "misc")
        if (!miscDir.exists()) {
            miscDir.mkdirs()
        }

        configFile = File(miscDir, "auto_ignition_booster.yml")
        if (!configFile.exists()) {
            plugin.saveResource("misc/auto_ignition_booster.yml", false)
        }

        reload()
    }

    fun reload() {
        config = YamlConfiguration.loadConfiguration(configFile)
    }

    fun glideBoostPower(): Double = config.getDouble("glide.boost_power", 1.0)

    fun holdIntervalTicks(): Long = config.getLong("glide.hold_interval_ticks", 10L).coerceAtLeast(1L)

    fun upwardBoostPower(): Double = config.getDouble("upward.boost_power", glideBoostPower())

    fun upwardVelocityCap(): Double = config.getDouble("upward.velocity_cap", 1.8)
}

class AutoIgnitionBoosterItem : CustomItem {
    override val feature = "misc"
    override val id = "auto_ignition_booster"
    override val displayName = "§6自動点火ブースター"
    override val lore = listOf(
        "§7足装備に着用中、§eShift§7で起動します。",
        "§7滑空中は花火を1個消費して前方へ加速します。",
        "§7非滑空時は同等速度を§a上方向§7へ変換して跳躍します。"
    )

    override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)

    override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
        val item = ItemStack(Material.CHAINMAIL_BOOTS, amount)
        val meta = item.itemMeta ?: return item

        val name = CustomItemI18n.text(player, "custom_items.$feature.$id.name", displayName)
        val localizedLore = CustomItemI18n.list(player, "custom_items.$feature.$id.lore", lore)

        meta.displayName(Component.text(name))
        meta.lore(localizedLore.map { Component.text(it) })
        meta.persistentDataContainer.set(AutoIgnitionBoosterKeys.BOOSTER_KEY, PersistentDataType.BYTE, 1)
        item.itemMeta = meta
        return item
    }

    override fun matches(item: ItemStack): Boolean {
        if (item.type != Material.CHAINMAIL_BOOTS) return false
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(AutoIgnitionBoosterKeys.BOOSTER_KEY, PersistentDataType.BYTE)
    }
}

class AutoIgnitionBoosterListener(private val plugin: JavaPlugin) : Listener {
    private val rocketConsumeSoundVolume = 4.0f

    private var tickCounter: Long = 0L
    private val lastActivationTick = mutableMapOf<UUID, Long>()

    init {
        startTask()
    }

    private fun startTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            tickCounter++

            for (player in Bukkit.getOnlinePlayers()) {
                if (!isBoosterEquipped(player)) {
                    lastActivationTick.remove(player.uniqueId)
                    continue
                }
                if (!player.isSneaking) {
                    continue
                }

                val lastTick = lastActivationTick[player.uniqueId] ?: Long.MIN_VALUE / 2
                if (tickCounter - lastTick < AutoIgnitionBoosterConfig.holdIntervalTicks()) {
                    continue
                }

                val activated = if (player.isGliding) {
                    activateDuringGlide(player)
                } else {
                    activateUpwardBoost(player)
                }

                if (activated) {
                    lastActivationTick[player.uniqueId] = tickCounter
                }
            }
        }, 1L, 1L)
    }

    private fun isBoosterEquipped(player: Player): Boolean {
        val boots = player.inventory.boots ?: return false
        val identified = jp.awabi2048.cccontent.items.CustomItemManager.identify(boots) ?: return false
        return identified.fullId == "misc.auto_ignition_booster"
    }

    private fun activateDuringGlide(player: Player): Boolean {
        if (!consumeFireworkRocket(player)) {
            player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.7f)
            return false
        }

        val direction = player.location.direction.clone().normalize()
        val boostVector = direction.multiply(AutoIgnitionBoosterConfig.glideBoostPower())
        player.velocity = player.velocity.add(boostVector)
        player.playSound(player.location, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, rocketConsumeSoundVolume, 1.0f)
        return true
    }

    private fun activateUpwardBoost(player: Player): Boolean {
        if (!player.isOnGround) {
            return false
        }

        val current = player.velocity
        val boostedY = (current.y + AutoIgnitionBoosterConfig.upwardBoostPower())
            .coerceAtMost(AutoIgnitionBoosterConfig.upwardVelocityCap())
        player.velocity = current.setY(boostedY)
        player.playSound(player.location, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.9f, 1.2f)
        return true
    }

    private fun consumeFireworkRocket(player: Player): Boolean {
        if (player.gameMode == org.bukkit.GameMode.CREATIVE) {
            return true
        }

        val inventory = player.inventory

        val mainHand = inventory.itemInMainHand
        if (mainHand.type == Material.FIREWORK_ROCKET && mainHand.amount > 0) {
            mainHand.amount -= 1
            if (mainHand.amount <= 0) {
                inventory.setItemInMainHand(ItemStack(Material.AIR))
            }
            return true
        }

        val offHand = inventory.itemInOffHand
        if (offHand.type == Material.FIREWORK_ROCKET && offHand.amount > 0) {
            offHand.amount -= 1
            if (offHand.amount <= 0) {
                inventory.setItemInOffHand(ItemStack(Material.AIR))
            }
            return true
        }

        for (slot in 0 until 36) {
            val stack = inventory.getItem(slot) ?: continue
            if (stack.type != Material.FIREWORK_ROCKET || stack.amount <= 0) continue

            stack.amount -= 1
            if (stack.amount <= 0) {
                inventory.setItem(slot, ItemStack(Material.AIR))
            }
            return true
        }

        return false
    }
}
