package jp.awabi2048.cccontent.features.arena.mechanic

import jp.awabi2048.cccontent.features.arena.ArenaBounds
import jp.awabi2048.cccontent.features.arena.ArenaMechanicCleanupBounds
import jp.awabi2048.cccontent.features.arena.ArenaSession
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.plugin.java.JavaPlugin

class ArenaMechanicContext(
    val plugin: JavaPlugin,
    val session: ArenaSession
) {
    val world: World?
        get() = Bukkit.getWorld(session.worldName)

    fun markersForWave(wave: Int): List<ArenaMechanicMarker> {
        return session.mechanicMarkersByWave[wave].orEmpty()
    }

    fun markersForWave(wave: Int, tag: String): List<Location> {
        return markersForWave(wave)
            .asSequence()
            .filter { it.tag == tag }
            .map { it.location.clone() }
            .toList()
    }

    fun markersForWaveByPrefix(wave: Int, tagPrefix: String): List<ArenaMechanicMarker> {
        return markersForWave(wave)
            .filter { it.tag.startsWith(tagPrefix) }
            .map { it.clone() }
    }

    fun participantPlayers() = session.participants.mapNotNull { Bukkit.getPlayer(it) }

    fun addCleanupBounds(bounds: ArenaBounds) {
        session.mechanicCleanupBounds += ArenaMechanicCleanupBounds(bounds)
    }
}
