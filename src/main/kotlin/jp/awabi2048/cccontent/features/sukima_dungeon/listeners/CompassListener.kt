package jp.awabi2048.cccontent.features.sukima_dungeon.listeners

import jp.awabi2048.cccontent.features.sukima_dungeon.CustomItemManager
import jp.awabi2048.cccontent.features.sukima_dungeon.MessageManager
import jp.awabi2048.cccontent.features.sukima_dungeon.SproutManager
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.entity.ItemDisplay
import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataType
import org.bukkit.inventory.ItemStack
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import java.util.UUID

class CompassListener(private val plugin: JavaPlugin) : Listener {

    private val sneakingPlayers = mutableMapOf<UUID, Long>()
    private val activeSessions = mutableMapOf<UUID, CompassSession>()

    private var maxRadiusParticle: org.bukkit.Particle = org.bukkit.Particle.FLAME
    private var currentRadiusParticle: org.bukkit.Particle = org.bukkit.Particle.SOUL_FIRE_FLAME

    init {
        loadConfig()
        startUpdateTask()
    }

    private fun loadConfig() {
        val sukimaConfig = jp.awabi2048.cccontent.features.sukima_dungeon.SukimaConfigHelper.getConfig(plugin)
        val maxParticleName = sukimaConfig.getString("compass.max_radius_particle", "FLAME")?.uppercase() ?: "FLAME"
        val currentParticleName = sukimaConfig.getString("compass.current_radius_particle", "SOUL_FIRE_FLAME")?.uppercase() ?: "SOUL_FIRE_FLAME"
        
        maxRadiusParticle = try {
            org.bukkit.Particle.valueOf(maxParticleName)
        } catch (e: Exception) {
            org.bukkit.Particle.FLAME
        }
        
        currentRadiusParticle = try {
            org.bukkit.Particle.valueOf(currentParticleName)
        } catch (e: Exception) {
            org.bukkit.Particle.SOUL_FIRE_FLAME
        }
    }

    class CompassSession(
        val player: UUID,
        val maxRadius: Double,
        val maxSeconds: Int,
        val cooldownSeconds: Int,
        var range: Double = 0.0,
        val markerEntities: MutableMap<UUID, ItemDisplay> = mutableMapOf(),
        var tickCount: Int = 0,
        var reachedMaxRange: Boolean = false,
        var stopTime: Long = 0, // 探知終了時刻
        var tier: Int = 1
    )

    private fun startUpdateTask() {
        object : BukkitRunnable() {
            override fun run() {
                val toRemoveSneak = mutableListOf<UUID>()
                for ((uuid, startTime) in sneakingPlayers) {
                    val player = Bukkit.getPlayer(uuid)
                    if (player == null || !player.isSneaking || !CustomItemManager.isCompassItem(player.inventory.itemInMainHand)) {
                        toRemoveSneak.add(uuid)
                        continue
                    }

                    if (System.currentTimeMillis() - startTime >= 1000) {
                        val item = player.inventory.itemInMainHand
                        
                        // Check cooldown
                        if (player.getCooldown(item.type) > 0) {
                            player.sendMessage("§cコンパスは現在クールタイム中です。")
                            player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f)
                            toRemoveSneak.add(uuid)
                            continue
                        }

                        if (!player.world.name.startsWith("dungeon_")) {
                            player.sendMessage(MessageManager.getMessage(player, "compass_cannot_use_here"))
                            player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f)
                            toRemoveSneak.add(uuid)
                            continue
                        }
                        
                        // Condition check: Hunger <= 6 or has negative effects
                        val hasNegativeEffect = player.activePotionEffects.any { 
                            val type = it.type
                            type == org.bukkit.potion.PotionEffectType.BLINDNESS || 
                            type == org.bukkit.potion.PotionEffectType.HUNGER ||
                            type == org.bukkit.potion.PotionEffectType.SLOWNESS ||
                            type == org.bukkit.potion.PotionEffectType.WITHER ||
                            type == org.bukkit.potion.PotionEffectType.POISON
                        }

                        if (player.gameMode != org.bukkit.GameMode.CREATIVE && (player.foodLevel <= 6 || hasNegativeEffect)) {
                             player.sendMessage("§cお腹が空いているか、体調が悪いため、コンパスに集中できない...")
                             player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f)
                             toRemoveSneak.add(uuid)
                             continue
                        }

                        val meta = item.itemMeta
                        val maxRadius = meta?.persistentDataContainer?.get(NamespacedKey("sukimadungeon", "compass_radius"), PersistentDataType.DOUBLE) ?: jp.awabi2048.cccontent.features.sukima_dungeon.SukimaConfigHelper.getConfig(plugin).getDouble("compass.radius", 48.0)
                        val maxTime = meta?.persistentDataContainer?.get(NamespacedKey("sukimadungeon", "compass_time"), PersistentDataType.INTEGER) ?: 30
                        val cooldownSec = meta?.persistentDataContainer?.get(NamespacedKey("sukimadungeon", "compass_cooldown"), PersistentDataType.INTEGER) ?: 60
                        
                        // Get tier from display name or PDC if possible (Here we use regex for fallback)
                        val tierRegex = Regex("Tier (\\d)")
                        val tierMatch = tierRegex.find(meta?.displayName ?: "")
                        val tier = tierMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1

                        activeSessions[uuid] = CompassSession(uuid, maxRadius, maxTime, cooldownSec, tier = tier)
                        toRemoveSneak.add(uuid)
                        player.sendMessage(MessageManager.getMessage(player, "compass_detect_start").replace("[Compass] ", ""))
                        player.playSound(player.location, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.0f)
                    }
                }
                toRemoveSneak.forEach { sneakingPlayers.remove(it) }

                val toRemoveSession = mutableListOf<UUID>()
                for ((uuid, session) in activeSessions) {
                    val player = Bukkit.getPlayer(uuid)
                    if (player == null) {
                        toRemoveSession.add(uuid)
                        continue
                    }

                    // Handle markers during grace period (15s) after max range reached
                    if (session.reachedMaxRange && session.stopTime != 0L) {
                        if (System.currentTimeMillis() - session.stopTime >= 15000) {
                            toRemoveSession.add(uuid)
                        } else {
                            // Maintain markers but stop showing foot particles after max range reached
                            // spawnExpansionParticles(player, session) 
                            updateMarkers(player, session) // Keep updating for deletions
                        }
                        continue
                    }

                    if (!player.isSneaking || !CustomItemManager.isCompassItem(player.inventory.itemInMainHand)) {
                        toRemoveSession.add(uuid)
                        continue
                    }

                    // Expand range
                    val expansionPerTick = session.maxRadius / (session.maxSeconds * 20.0)
                    if (session.range < session.maxRadius) {
                        session.range += expansionPerTick
                        if (session.range >= session.maxRadius) {
                            session.range = session.maxRadius
                            if (!session.reachedMaxRange) {
                                session.reachedMaxRange = true
                                session.stopTime = System.currentTimeMillis()
                                player.playSound(player.location, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.0f, 1.0f)
                                
                                if (session.markerEntities.isEmpty()) {
                                    player.sendMessage(MessageManager.getMessage(player, "compass_detect_fail"))
                                    toRemoveSession.add(uuid)
                                } else {
                                    player.sendMessage("§b§o最大範囲まで探知しました。15秒間マーカーを維持します。")
                                }
                            }
                        }
                    }

                    // Effects based on Tier (Creative mode immune)
                    if (player.gameMode != org.bukkit.GameMode.CREATIVE) {
                        if (session.tier >= 1) {
                            player.addPotionEffect(org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.HUNGER, 40, 0, false, false))
                        }
                        if (session.tier >= 2) {
                            player.addPotionEffect(org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS, 40, 0, false, false))
                        }
                        if (session.tier >= 3) {
                            player.addPotionEffect(org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS, 40, 1, false, false))
                        }
                    }

                    // Particles for expansion and indicator
                    spawnExpansionParticles(player, session)

                    // Hunger decrease (0.5 units / 3 sec = 1 point / 60 ticks) - skip if creative
                    session.tickCount++
                    if (player.gameMode != org.bukkit.GameMode.CREATIVE && session.tickCount % 60 == 0) {
                        player.foodLevel = (player.foodLevel - 1).coerceAtLeast(0)
                        if (player.foodLevel <= 0) {
                            player.sendMessage(MessageManager.getMessage(player, "compass_detect_interrupted_hunger").replace("[Compass] ", ""))
                            toRemoveSession.add(uuid)
                            continue
                        }
                    }

                    // Sound interval (1 sec = 20 ticks)
                    if (session.tickCount % 20 == 0) {
                        player.playSound(player.location, Sound.BLOCK_AMETHYST_BLOCK_STEP, 0.5f, 1.5f)
                    }

                    // Detect sprouts
                    updateMarkers(player, session)
                }

                for (uuid in toRemoveSession) {
                    val session = activeSessions.remove(uuid)
                    session?.markerEntities?.values?.forEach { it.remove() }
                    val player = Bukkit.getPlayer(uuid)
                    
                    if (player != null && session != null) {
                        player.sendMessage(MessageManager.getMessage(player, "compass_detect_stop").replace("[Compass] ", ""))
                        player.playSound(player.location, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 0.5f)
                        
                        // Apply cooldown
                        val ticks = session.cooldownSeconds * 20
                        player.setCooldown(org.bukkit.Material.STONE_PICKAXE, ticks)
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L)
    }

    private fun spawnExpansionParticles(player: Player, session: CompassSession) {
        val loc = player.location
        
        // Final scale range is constant 2.0 blocks
        val maxIndicatorRange = 2.0
        val currentIndicatorRange = (session.range / session.maxRadius) * 2.0
        
        // 1. Max radius indicator (Static circle)
        val maxPoints = 32
        for (i in 0 until maxPoints) {
            val angle = 2.0 * Math.PI * i / maxPoints
            val x = Math.cos(angle) * maxIndicatorRange
            val z = Math.sin(angle) * maxIndicatorRange
            val particleLoc = loc.clone().add(x, 0.05, z)
            player.spawnParticle(maxRadiusParticle, particleLoc, 1, 0.0, 0.0, 0.0, 0.0)
        }

        // 2. Current range indicator (Rotating particles)
        val currentPoints = 4
        val time = System.currentTimeMillis() / 100.0 // Rotation speed
        for (i in 0 until currentPoints) {
            val angle = (2.0 * Math.PI * i / currentPoints) + time
            val x = Math.cos(angle) * currentIndicatorRange
            val z = Math.sin(angle) * currentIndicatorRange
            val particleLoc = loc.clone().add(x, 0.1, z)
            player.spawnParticle(currentRadiusParticle, particleLoc, 1, 0.0, 0.0, 0.0, 0.0)
        }

        // 3. Radar radar: Projection of detected sprouts onto the foot indicator
        // Relative position within maxRadius is projected to 2.0 blocks radius
        for (display in session.markerEntities.values) {
            val sproutLoc = display.location
            val relX = sproutLoc.x - loc.x
            val relZ = sproutLoc.z - loc.z
            val dist = Math.sqrt(relX * relX + relZ * relZ)

            // Normalize distance to 0.0 - 2.0 range
            val radarDist = (dist / session.maxRadius) * 2.0
            val ratio = if (dist > 0) radarDist / dist else 0.0
            
            val projX = relX * ratio
            val projZ = relZ * ratio
            
            val particleLoc = loc.clone().add(projX, 0.15, projZ)
            // Use blue/cyan particles for the "dot" on the radar
            player.spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, particleLoc, 1, 0.0, 0.0, 0.0, 0.0)
        }
    }

    private fun updateMarkers(player: Player, session: CompassSession) {
        val world = player.world
        if (!world.name.startsWith("dungeon_")) return

        val sprouts = world.entities.filter { it.scoreboardTags.contains("world_sprout_marker") }
        val returnPortals = world.entities.filter { it.scoreboardTags.contains("sd.return_portal_marker") }
        val playerLoc = player.location

        // Detect sprouts
        for (sprout in sprouts) {
            val dist = sprout.location.distance(playerLoc)
            val sproutId = sprout.uniqueId

            if (dist <= session.range) {
                if (!session.markerEntities.containsKey(sproutId)) {
                    val display = world.spawn(sprout.location.clone().add(0.0, 0.5, 0.0), ItemDisplay::class.java) { d ->
                        d.setItemStack(org.bukkit.inventory.ItemStack(org.bukkit.Material.GLASS))
                        d.isGlowing = true
                        d.glowColorOverride = org.bukkit.Color.fromRGB(8453761)
                        d.brightness = org.bukkit.entity.Display.Brightness(15, 15)
                    }
                    session.markerEntities[sproutId] = display
                    
                    // discovery feedback
                    player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.2f)
                    player.sendMessage("§b新しいワールドの芽を検知しました！")
                }
            } else {
                session.markerEntities.remove(sproutId)?.remove()
            }
        }

        // Detect return portals
        for (portal in returnPortals) {
            val dist = portal.location.distance(playerLoc)
            val portalId = portal.uniqueId

            if (dist <= session.range) {
                if (!session.markerEntities.containsKey(portalId)) {
                    val display = world.spawn(portal.location.clone().add(0.0, 0.5, 0.0), ItemDisplay::class.java) { d ->
                        d.setItemStack(org.bukkit.inventory.ItemStack(org.bukkit.Material.BEACON))
                        d.isGlowing = true
                        d.glowColorOverride = org.bukkit.Color.AQUA
                        d.brightness = org.bukkit.entity.Display.Brightness(15, 15)
                    }
                    session.markerEntities[portalId] = display
                    
                    // discovery feedback
                    player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.5f)
                    player.sendMessage("§b帰還ポータルの反応を検知しました！")
                }
            } else {
                session.markerEntities.remove(portalId)?.remove()
            }
        }

        // Clean up markers for entities that no longer exist
        val currentTargetIds = (sprouts + returnPortals).map { it.uniqueId }.toSet()
        val toRemoveMarkers = session.markerEntities.keys.filter { it !in currentTargetIds }
        toRemoveMarkers.forEach {
            session.markerEntities.remove(it)?.remove()
        }
    }

    @EventHandler
    fun onSneak(event: PlayerToggleSneakEvent) {
        val player = event.player
        
        if (event.isSneaking) {
            if (CustomItemManager.isCompassItem(player.inventory.itemInMainHand)) {
                sneakingPlayers[player.uniqueId] = System.currentTimeMillis()
            }
        } else {
            sneakingPlayers.remove(player.uniqueId)
            // session removal is handled in the update task
        }
    }

    @EventHandler
    fun onHeld(event: PlayerItemHeldEvent) {
        val player = event.player
        val newItem = player.inventory.getItem(event.newSlot)
        if (!CustomItemManager.isCompassItem(newItem)) {
            sneakingPlayers.remove(player.uniqueId)
            // session removal is handled in the update task
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        sneakingPlayers.remove(event.player.uniqueId)
        val session = activeSessions.remove(event.player.uniqueId)
        session?.markerEntities?.values?.forEach { it.remove() }
    }

    @EventHandler
    fun onSproutBreak(event: jp.awabi2048.cccontent.features.sukima_dungeon.events.SproutBreakEvent) {
        val uuid = event.trackerUuid
        for (session in activeSessions.values) {
            session.markerEntities.remove(uuid)?.remove()
        }
    }
}
