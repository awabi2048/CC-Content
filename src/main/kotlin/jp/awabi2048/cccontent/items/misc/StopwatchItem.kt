package jp.awabi2048.cccontent.items.misc

import jp.awabi2048.cccontent.items.CustomItem
import jp.awabi2048.cccontent.items.CustomItemI18n
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import kotlin.math.max

class StopwatchItem(private val plugin: JavaPlugin) : CustomItem, Listener {

    override val feature = "misc"
    override val id = "stopwatch"
    override val displayName = "Stopwatch"
    override val itemModel = NamespacedKey.minecraft("clock")
    override val canStack = false

    private val stopwatchKey = NamespacedKey("cccontent", "stopwatch")
    private val runningSince = mutableMapOf<UUID, Long>()

    init {
        plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            updateActionBars()
        }, 0L, 5L)
    }

    override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)

    override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
        val item = ItemStack(Material.POISONOUS_POTATO, amount)
        val meta = item.itemMeta ?: return item

        val name = CustomItemI18n.text(player, "custom_items.$feature.$id.name", displayName)
        val lore = CustomItemI18n.lore(
            player,
            "custom_items.$feature.$id.lore",
            listOf(
                "Right click to start or stop timing.",
                "Press F while holding it to reset."
            )
        )

        meta.displayName(Component.text(name))
        meta.lore(lore)
        meta.persistentDataContainer.set(stopwatchKey, PersistentDataType.BYTE, 1)
        item.itemMeta = meta
        return item
    }

    override fun matches(item: ItemStack): Boolean {
        if (item.type != Material.POISONOUS_POTATO) return false
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(stopwatchKey, PersistentDataType.BYTE)
    }

    override fun onRightClick(player: Player, event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return
        if (event.hand != EquipmentSlot.HAND) return

        event.isCancelled = true
        val playerId = player.uniqueId
        val startedAt = runningSince[playerId]
        if (startedAt == null) {
            runningSince[playerId] = System.currentTimeMillis()
            player.sendActionBar(statusComponent("started"))
            player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.8f, 1.6f)
        } else {
            runningSince.remove(playerId)
            player.sendActionBar(statusComponent("stopped: ", formatElapsed(System.currentTimeMillis() - startedAt)))
            player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.8f, 0.9f)
        }
    }

    @EventHandler
    fun onPlayerSwapHandItems(event: PlayerSwapHandItemsEvent) {
        val player = event.player
        if (!matches(player.inventory.itemInMainHand) && !matches(player.inventory.itemInOffHand)) return

        event.isCancelled = true
        if (runningSince.containsKey(player.uniqueId)) {
            runningSince[player.uniqueId] = System.currentTimeMillis()
        }
        player.sendActionBar(statusComponent("reset: ", "00:00.000"))
        player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 1.8f)
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        runningSince.remove(event.player.uniqueId)
    }

    private fun updateActionBars() {
        if (runningSince.isEmpty()) return
        val now = System.currentTimeMillis()
        val iterator = runningSince.iterator()
        while (iterator.hasNext()) {
            val (playerId, startedAt) = iterator.next()
            val player = plugin.server.getPlayer(playerId)
            if (player == null || !player.isOnline) {
                iterator.remove()
                continue
            }
            player.sendActionBar(
                Component.text("Stopwatch ", NamedTextColor.YELLOW)
                    .append(Component.text(formatElapsed(now - startedAt), NamedTextColor.WHITE))
            )
        }
    }

    private fun statusComponent(label: String, value: String? = null): Component {
        var component = Component.text("Stopwatch ", NamedTextColor.YELLOW)
            .append(Component.text(label, NamedTextColor.GRAY))
        if (value != null) {
            component = component.append(Component.text(value, NamedTextColor.WHITE))
        }
        return component
    }

    private fun formatElapsed(elapsedMillis: Long): String {
        val safeMillis = max(0L, elapsedMillis)
        val minutes = safeMillis / 60_000L
        val seconds = (safeMillis / 1_000L) % 60L
        val millis = safeMillis % 1_000L
        return "%02d:%02d.%03d".format(minutes, seconds, millis)
    }
}
