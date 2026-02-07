package jp.awabi2048.cccontent.features.sukima_dungeon

import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.World
import org.bukkit.entity.EntityType
import org.bukkit.entity.Interaction
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.Color
import org.bukkit.Bukkit
import java.util.UUID

object PortalManager {
    private val portals = mutableMapOf<UUID, PortalSession>()
    private lateinit var plugin: JavaPlugin

    fun init(plugin: JavaPlugin) {
        this.plugin = plugin
        startParticleTask()
    }

    fun createPortal(player: Player, tier: DungeonTier, themeName: String, sizeKey: String): PortalSession {
        val loc = player.location.clone()
        val world = loc.world ?: throw IllegalStateException("World cannot be null")
        
        val interaction = world.spawnEntity(loc, EntityType.INTERACTION) as Interaction
        interaction.interactionWidth = 1.0f
        interaction.interactionHeight = 2.0f
        
        val session = PortalSession(
            id = interaction.uniqueId,
            owner = player.uniqueId,
            location = loc,
            tier = tier,
            themeName = themeName,
            sizeKey = sizeKey,
            createdAt = System.currentTimeMillis()
        )
        
        portals[interaction.uniqueId] = session
        
        // Expiry task
        val durationTicks = SukimaConfigHelper.getConfig(plugin).getInt("bookmark_portal_duration", 300) * 20L
        object : BukkitRunnable() {
            override fun run() {
                removePortal(interaction.uniqueId)
            }
        }.runTaskLater(plugin, durationTicks)
        
        return session
    }

    fun createReturnPortal(location: Location): PortalSession {
        val world = location.world ?: throw IllegalStateException("World cannot be null")
        
        val interaction = world.spawnEntity(location, EntityType.INTERACTION) as Interaction
        interaction.interactionWidth = 1.0f
        interaction.interactionHeight = 2.0f
        interaction.addScoreboardTag("sd.portal.return")
        
        // Marker for compass
        val marker = world.spawnEntity(location, EntityType.MARKER) as org.bukkit.entity.Marker
        marker.addScoreboardTag("sd.return_portal_marker")
        
        val session = PortalSession(
            id = interaction.uniqueId,
            owner = UUID(0, 0), // System owned
            location = location,
            tier = DungeonTier.BROKEN,
            themeName = "",
            sizeKey = "",
            createdAt = System.currentTimeMillis(),
            isReady = true,
            isReturn = true
        )
        
        portals[interaction.uniqueId] = session
        return session
    }

    fun getPortal(uuid: UUID): PortalSession? = portals[uuid]

    fun getReturnPortal(world: World): PortalSession? {
        return portals.values.find { it.location.world == world && it.isReturn }
    }

    fun onDungeonClose(worldName: String) {
        val iterator = portals.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.worldName == worldName || entry.value.location.world?.name == worldName) {
                entry.value.interaction.remove()
                iterator.remove()
            }
        }
    }

    fun removePortal(uuid: UUID) {
        val session = portals.remove(uuid)
        session?.let {
            it.interaction.remove()
        }
    }

    private fun startParticleTask() {
        object : BukkitRunnable() {
            override fun run() {
                val iterator = portals.entries.iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    val session = entry.value
                    
                    if (!session.location.world!!.isChunkLoaded(session.location.blockX shr 4, session.location.blockZ shr 4)) continue
                    if (System.currentTimeMillis() - session.createdAt > 30000 && 
                        session.location.world!!.getNearbyEntities(session.location, 16.0, 16.0, 16.0).none { it is Player }) {
                         // Condition 7: 付近にプレイヤーが存在しなくなること
                         session.interaction.remove()
                         iterator.remove()
                         continue
                    }

                    val world = session.location.world!!
                    val loc = session.location.clone().add(0.0, 1.0, 0.0)
                    
                    // Particle: minecraft:dust_color_transition{from_color:[0,0,1],to_color:[0, 1, 0],scale:1}
                    val dustOptions = Particle.DustTransition(Color.BLUE, Color.GREEN, 1.0f)
                    world.spawnParticle(Particle.DUST_COLOR_TRANSITION, loc, 20, 0.3, 0.8, 0.3, 0.05, dustOptions)
                }
            }
        }.runTaskTimer(plugin, 5L, 5L)
    }
}

data class PortalSession(
    val id: UUID,
    val owner: UUID,
    val location: Location,
    val tier: DungeonTier,
    val themeName: String,
    val sizeKey: String,
    val createdAt: Long,
    var isReady: Boolean = false,
    var teleportLocation: Location? = null,
    var worldName: String? = null,
    var sproutCount: Int = 0,
    var duration: Int = 0,
    var tileSize: Int = 16,
    var gridWidth: Int = 0,
    var gridLength: Int = 0,
    var minibossMarkers: Map<Pair<Int, Int>, Location> = emptyMap(),
    var mobSpawnPoints: List<Location> = emptyList(),
    var restCells: Set<Pair<Int, Int>> = emptySet(),
    var dungeonThemeId: String? = null,
    var size: Int = 0,
    var isReturn: Boolean = false
) {
    val interaction: Interaction
        get() = Bukkit.getEntity(id) as Interaction
}
