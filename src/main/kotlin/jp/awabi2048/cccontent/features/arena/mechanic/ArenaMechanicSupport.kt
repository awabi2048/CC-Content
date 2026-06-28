package jp.awabi2048.cccontent.features.arena.mechanic

import jp.awabi2048.cccontent.features.arena.ArenaBounds
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import kotlin.math.max
import kotlin.math.min

/**
 * テーマギミックから繰り返し使う薄い補助。
 * ギミックの抽選・周期・効果量は各テーマが持ち、ここでは Arena の参加者/モブ境界だけを扱う。
 */
object ArenaMechanicSupport {
    fun isWaveNearParticipant(context: ArenaMechanicContext, wave: Int): Boolean {
        val world = context.world ?: return false
        val watchedRooms = listOf(wave - 1, wave, wave + 1)
            .mapNotNull { context.session.roomBounds[it] }
        if (watchedRooms.isEmpty()) return false

        return context.participantPlayers().any { player ->
            player.world.uid == world.uid && isActivePlayer(player) && watchedRooms.any { it.contains(player.location) }
        }
    }

    fun activeWaves(context: ArenaMechanicContext): Set<Int> {
        return (1..context.session.waves)
            .filter { wave ->
                wave in context.session.startedWaves &&
                    wave !in context.session.clearedWaves &&
                    isWaveNearParticipant(context, wave)
            }
            .toSet()
    }

    fun targetsNear(
        context: ArenaMechanicContext,
        center: Location,
        radiusX: Double,
        radiusY: Double,
        radiusZ: Double,
        playersOnly: Boolean = false
    ): List<LivingEntity> {
        val world = context.world ?: return emptyList()
        if (center.world?.uid != world.uid) return emptyList()

        return world.getNearbyEntities(center, radiusX, radiusY, radiusZ)
            .asSequence()
            .mapNotNull { it as? LivingEntity }
            .filter { it.isValid && !it.isDead }
            .filter { entity ->
                val player = entity as? Player
                if (player != null) {
                    isActivePlayer(player) && entity.uniqueId in context.session.participants
                } else {
                    !playersOnly && entity.uniqueId in context.session.activeMobs
                }
            }
            .toList()
    }

    fun participantScale(context: ArenaMechanicContext): MechanicParticipantScale {
        return when (context.session.participants.size.coerceIn(1, 6)) {
            1 -> MechanicParticipantScale(slotMultiplier = 1.0, frequencyMultiplier = 1.0)
            2 -> MechanicParticipantScale(slotMultiplier = 1.3, frequencyMultiplier = 1.05)
            3 -> MechanicParticipantScale(slotMultiplier = 1.6, frequencyMultiplier = 1.1)
            4, 5 -> MechanicParticipantScale(slotMultiplier = 1.9, frequencyMultiplier = 1.15)
            else -> MechanicParticipantScale(slotMultiplier = 2.5, frequencyMultiplier = 1.2)
        }
    }

    fun boundsAround(center: Location, radiusX: Int, radiusY: Int, radiusZ: Int): ArenaBounds {
        return ArenaBounds(
            minX = center.blockX - radiusX,
            maxX = center.blockX + radiusX,
            minY = center.blockY - radiusY,
            maxY = center.blockY + radiusY,
            minZ = center.blockZ - radiusZ,
            maxZ = center.blockZ + radiusZ
        )
    }

    fun boundsFromCorners(first: Location, second: Location, margin: Int = 0): ArenaBounds {
        return ArenaBounds(
            minX = min(first.blockX, second.blockX) - margin,
            maxX = max(first.blockX, second.blockX) + margin,
            minY = min(first.blockY, second.blockY) - margin,
            maxY = max(first.blockY, second.blockY) + margin,
            minZ = min(first.blockZ, second.blockZ) - margin,
            maxZ = max(first.blockZ, second.blockZ) + margin
        )
    }

    fun ArenaBounds.contains(location: Location): Boolean {
        return contains(location.x, location.y, location.z)
    }

    fun horizontalDirection(from: Location, to: Location): Vector {
        val vector = to.toVector().subtract(from.toVector()).setY(0)
        return if (vector.lengthSquared() <= 1.0e-6) Vector(0.0, 0.0, 1.0) else vector.normalize()
    }

    private fun isActivePlayer(player: Player): Boolean {
        return player.isValid && !player.isDead && player.gameMode != GameMode.SPECTATOR
    }
}

data class MechanicParticipantScale(
    val slotMultiplier: Double,
    val frequencyMultiplier: Double
)
