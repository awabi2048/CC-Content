package jp.awabi2048.cccontent.features.sukima_dungeon

import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.entity.EntityType
import org.bukkit.entity.Marker
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable

class MarkerManager(private val plugin: JavaPlugin) : Listener {
    private val MODE_KEY = NamespacedKey(plugin, "marker_mode")
    private val TOOL_KEY = NamespacedKey(plugin, "marker_tool")
    private val lastSwitchTime = mutableMapOf<java.util.UUID, Long>()

    enum class MarkerMode(val tag: String, val displayName: String, val particle: Particle) {
        MOB("sd.marker.mob", "§cMOB", Particle.FLAME),
        NPC("sd.marker.npc", "§aNPC", Particle.HAPPY_VILLAGER),
        ITEM("sd.marker.item", "§bITEM", Particle.SOUL_FIRE_FLAME),
        SPROUT("sd.marker.sprout", "§dSPROUT", Particle.GLOW),
        SPAWN("sd.marker.spawn", "§fSPAWN", Particle.CLOUD)
    }

    fun getMarkerTool(player: Player): ItemStack {
        val item = ItemStack(Material.BLAZE_ROD)
        val meta = item.itemMeta ?: return item
        
        meta.setDisplayName("§dマーカー設置ツール")
        val mode = getMode(item)
        updateLore(meta, mode)
        
        meta.persistentDataContainer.set(TOOL_KEY, PersistentDataType.BYTE, 1)
        meta.persistentDataContainer.set(MODE_KEY, PersistentDataType.STRING, mode.name)
        
        item.itemMeta = meta
        return item
    }

    private fun getMode(item: ItemStack): MarkerMode {
        val meta = item.itemMeta ?: return MarkerMode.MOB
        val modeName = meta.persistentDataContainer.get(MODE_KEY, PersistentDataType.STRING)
        return try {
            MarkerMode.valueOf(modeName ?: "MOB")
        } catch (e: Exception) {
            MarkerMode.MOB
        }
    }

    private fun updateLore(meta: org.bukkit.inventory.meta.ItemMeta, mode: MarkerMode) {
        val bar = MessageManager.getMessage(null, "common_bar")
        meta.lore = listOf(
            bar,
            "§f§l| §7現在のモード §a${mode.displayName}",
            "",
            "§e右クリック§7 マーカーを設置する",
            "§eShift + スクロール§7 モードを変更する",
            "§eShift + 右クリック§7 最寄りのマーカーを削除",
            bar
        )
    }

    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        val item = event.item ?: return
        val meta = item.itemMeta ?: return
        if (!meta.persistentDataContainer.has(TOOL_KEY, PersistentDataType.BYTE)) return
        
        event.isCancelled = true
        val player = event.player
        var mode = getMode(item)

        if (event.action == Action.LEFT_CLICK_AIR || event.action == Action.LEFT_CLICK_BLOCK) {
            return
        }

        if (isSukimaDungeonWorld(player.world)) {
            player.sendMessage("§c[Marker] §fダンジョン内ではマーカー設置ツールを使用できません。")
            return
        }

        if (event.action == Action.RIGHT_CLICK_BLOCK || event.action == Action.RIGHT_CLICK_AIR) {
            if (player.isSneaking) {
                // Remove nearest marker
                val markers = player.getNearbyEntities(5.0, 5.0, 5.0).filterIsInstance<Marker>()
                    .filter { marker -> MarkerMode.values().any { marker.scoreboardTags.contains(it.tag) } }
                
                val nearest = markers.minByOrNull { it.location.distanceSquared(player.location) }
                
                if (nearest != null) {
                    val loc = nearest.location
                    nearest.remove()
                    player.sendMessage("§b[Marker] §f最寄りのマーカーを削除しました。")
                    player.playSound(player.location, org.bukkit.Sound.BLOCK_ANVIL_BREAK, 0.5f, 2.0f)
                    loc.world?.spawnParticle(Particle.SMOKE, loc, 10, 0.1, 0.1, 0.1, 0.02)
                } else {
                    player.sendMessage("§c[Marker] §f近くに自身の管理するマーカーが見つかりませんでした。")
                }
                return
            }

            if (event.action == Action.RIGHT_CLICK_BLOCK) {
                val block = event.clickedBlock ?: return
                val loc = block.location.add(0.5, 1.1, 0.5) // Slightly above center of block
                
                val world = loc.world ?: return
                val marker = world.spawnEntity(loc, EntityType.MARKER) as Marker
                marker.addScoreboardTag(mode.tag)
                
                player.sendMessage("§b[Marker] ${mode.displayName} §fマーカーを設置しました。")
                player.playSound(player.location, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f)
                
                // Show particle immediately
                world.spawnParticle(mode.particle, loc, 10, 0.1, 0.1, 0.1, 0.05)
            }
        }
    }

    @EventHandler
    fun onItemHeld(event: PlayerItemHeldEvent) {
        val player = event.player
        val item = player.inventory.getItem(event.previousSlot) ?: return
        val meta = item.itemMeta ?: return
        if (!meta.persistentDataContainer.has(TOOL_KEY, PersistentDataType.BYTE)) return

        if (!player.isSneaking) return

        if (isSukimaDungeonWorld(player.world)) {
            player.sendMessage("§c[Marker] §fダンジョン内ではマーカー設置ツールを使用できません。")
            return
        }

        // Apply short cooldown to prevent double switches in a single tick
        val now = System.currentTimeMillis()
        val last = lastSwitchTime.getOrDefault(player.uniqueId, 0L)
        if (now - last < 50) {
            event.player.inventory.heldItemSlot = event.previousSlot
            return
        }
        lastSwitchTime[player.uniqueId] = now

        // Cancel slot change
        event.player.inventory.heldItemSlot = event.previousSlot

        val currentMode = getMode(item)
        val values = MarkerMode.values()
        
        val diff = event.newSlot - event.previousSlot
        // Handle wraparound (from 8 to 0 or 0 to 8)
        val isNext = if (diff == -8) true else if (diff == 8) false else diff > 0
        
        val newMode = if (isNext) {
            values[(currentMode.ordinal + 1) % values.size]
        } else {
            values[(currentMode.ordinal - 1 + values.size) % values.size]
        }

        meta.persistentDataContainer.set(MODE_KEY, PersistentDataType.STRING, newMode.name)
        updateLore(meta, newMode)
        item.itemMeta = meta

        player.sendActionBar(net.kyori.adventure.text.Component.text("§7設置モードを ${newMode.displayName} §7に変更しました。"))
        player.playSound(player.location, org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 2.0f)
    }

    fun startParticleTask() {
        object : BukkitRunnable() {
            override fun run() {
                for (world in plugin.server.worlds) {
                    // Marker particles are for development, so maybe only in certain worlds or if players are nearby
                    val markers = world.entities.filterIsInstance<Marker>()
                    if (markers.isEmpty()) continue
                    
                    for (marker in markers) {
                        val tags = marker.scoreboardTags
                        val mode = MarkerMode.values().find { tags.contains(it.tag) } ?: continue
                        
                        // Only show if a player with a tool is nearby
                        val hasToolNearby = marker.getNearbyEntities(16.0, 16.0, 16.0).any { 
                            it is Player && it.inventory.itemInMainHand.let { item ->
                                item.itemMeta?.persistentDataContainer?.has(TOOL_KEY, PersistentDataType.BYTE) == true
                            }
                        }
                        
                        if (hasToolNearby) {
                            world.spawnParticle(mode.particle, marker.location, 3, 0.05, 0.05, 0.05, 0.02)
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 10L, 10L)
    }

    companion object {
        fun cleanupMarkers(world: org.bukkit.World) {
            world.entities.forEach { entity ->
                val hasTag = entity.scoreboardTags.any { it.startsWith("sd.marker.") }
                val isOldArmorStand = entity is org.bukkit.entity.ArmorStand && 
                        listOf("MOB", "NPC", "ITEM", "SPROUT", "SPAWN").contains(entity.customName)
                val isMarkerEntity = entity is org.bukkit.entity.Marker
                
                if (hasTag || isOldArmorStand || isMarkerEntity) {
                    entity.remove()
                }
            }
        }
    }
}
