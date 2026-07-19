package jp.awabi2048.cccontent.features.fishing

import com.awabi2048.ccsystem.CCSystem
import net.kyori.adventure.text.Component
import org.bukkit.HeightMap
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.Random
import java.util.UUID
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

data class NaturalFishingGround(
    val worldId: UUID,
    val center: Location,
    val radius: Double,
    val expiresAtMillis: Long
) {
    fun contains(location: Location): Boolean {
        val dx = location.x - center.x
        val dz = location.z - center.z
        return location.world?.uid == worldId &&
            dx * dx + dz * dz <= radius * radius &&
            FishingWaterAnalyzer.isWater(location.block)
    }
}

class NaturalFishingGroundService(
    private val plugin: JavaPlugin,
    private val settings: NaturalFishingGroundSettings,
    private val isFisher: (Player) -> Boolean,
    private val random: Random = Random()
) {
    private val grounds = mutableListOf<NaturalFishingGround>()
    private val nextRollAt = mutableMapOf<UUID, Long>()
    private var effectTask: BukkitTask? = null
    private var effectCycles = 0

    fun start() {
        if (!settings.enabled || effectTask != null) return
        effectTask = plugin.server.scheduler.runTaskTimer(
            plugin,
            Runnable { renderEffects() },
            80L,
            80L
        )
    }

    fun stop() {
        effectTask?.cancel()
        effectTask = null
        clearAll()
    }

    fun clearAll() {
        grounds.clear()
        nextRollAt.clear()
    }

    fun clearWorld(worldId: UUID) {
        grounds.removeIf { it.worldId == worldId }
    }

    fun contains(location: Location): Boolean {
        purgeExpired()
        return grounds.any { it.contains(location) }
    }

    fun tryGenerate(player: Player): NaturalFishingGround? {
        if (!settings.enabled) return null
        val now = System.currentTimeMillis()
        if ((nextRollAt[player.uniqueId] ?: 0L) > now) return null
        nextRollAt[player.uniqueId] = now + settings.rollCooldownSeconds * 1000L

        val chance = (settings.baseChance * if (isFisher(player)) settings.fisherMultiplier else 1.0)
            .coerceIn(0.0, 1.0)
        if (random.nextDouble() >= chance) return null
        val ground = findCandidate(player, now) ?: return null
        grounds += ground
        nextRollAt[player.uniqueId] = now + settings.successCooldownSeconds * 1000L
        notifyCreation(player, ground)
        return ground
    }

    private fun findCandidate(player: Player, now: Long): NaturalFishingGround? {
        repeat(16) {
            val distanceChunks = random.nextInt(
                settings.maximumDistanceChunks - settings.minimumDistanceChunks + 1
            ) + settings.minimumDistanceChunks
            val angle = random.nextDouble() * PI * 2.0
            val x = player.location.blockX + (cos(angle) * distanceChunks * 16.0).roundToInt()
            val z = player.location.blockZ + (sin(angle) * distanceChunks * 16.0).roundToInt()
            val world = player.world
            if (!world.isChunkLoaded(Math.floorDiv(x, 16), Math.floorDiv(z, 16))) return@repeat
            val radius = random.nextInt(settings.maximumRadius - settings.minimumRadius + 1) +
                settings.minimumRadius
            val center = waterSurface(world, x, z) ?: return@repeat
            if (!validateArea(center, radius)) return@repeat
            val exclusionDistance = settings.exclusionRangeChunks * 16.0
            if (grounds.any {
                    it.worldId == world.uid &&
                        it.center.xzDistanceSquared(center) < exclusionDistance * exclusionDistance
                }
            ) {
                return@repeat
            }
            return NaturalFishingGround(
                world.uid,
                center.clone().add(0.5, 0.5, 0.5),
                radius.toDouble(),
                now + settings.durationSeconds * 1000L
            )
        }
        return null
    }

    private fun validateArea(center: Location, radius: Int): Boolean {
        val centerBlock = center.block
        if (!isFishingBiome(centerBlock.biome.key.key)) return false
        if ((FishingWaterAnalyzer.analyze(center)?.depth ?: 0) < 2) return false

        val marginRadius = (radius * 1.5).roundToInt()
        var coreTotal = 0
        var coreWater = 0
        var coreFishingBiomes = 0
        var marginTotal = 0
        var marginWater = 0
        for (dx in -marginRadius..marginRadius) {
            for (dz in -marginRadius..marginRadius) {
                val distanceSquared = dx * dx + dz * dz
                if (distanceSquared > marginRadius * marginRadius) continue
                val x = center.blockX + dx
                val z = center.blockZ + dz
                val world = center.world
                if (!world.isChunkLoaded(Math.floorDiv(x, 16), Math.floorDiv(z, 16))) return false
                val surface = waterSurface(world, x, z)
                marginTotal++
                if (surface != null) marginWater++
                if (distanceSquared <= radius * radius) {
                    coreTotal++
                    if (surface != null) {
                        coreWater++
                        if (isFishingBiome(surface.block.biome.key.key)) coreFishingBiomes++
                    }
                }
            }
        }
        return coreTotal > 0 &&
            marginTotal > 0 &&
            coreWater.toDouble() / coreTotal >= 0.85 &&
            coreFishingBiomes.toDouble() / coreTotal >= 0.80 &&
            marginWater.toDouble() / marginTotal >= 0.75
    }

    private fun waterSurface(world: World, x: Int, z: Int): Location? {
        val block = world.getHighestBlockAt(x, z, HeightMap.WORLD_SURFACE)
        return block.takeIf(FishingWaterAnalyzer::isWater)?.location
    }

    private fun notifyCreation(generator: Player, ground: NaturalFishingGround) {
        val range = settings.exclusionRangeChunks * 16.0
        generator.world.players
            .filter { it.location.xzDistanceSquared(ground.center) <= range * range }
            .forEach { player ->
                when {
                    player.uniqueId == generator.uniqueId && !isFisher(player) ->
                        player.sendMessage(message(player, "fishing.ground.created_nearby"))
                    isFisher(player) -> player.sendMessage(
                        message(
                            player,
                            "fishing.ground.created_direction",
                            "direction" to message(
                                player,
                                "fishing.ground.direction.${directionId(player.location, ground.center)}"
                            )
                        )
                    )
                }
            }
    }

    private fun renderEffects() {
        purgeExpired()
        effectCycles++
        grounds.forEach { ground ->
            val world = plugin.server.getWorld(ground.worldId) ?: return@forEach
            if (world.players.none { it.location.distanceSquaredSafe(ground.center) <= 48.0 * 48.0 }) {
                return@forEach
            }
            repeat(4) {
                val angle = random.nextDouble() * PI * 2.0
                val distance = random.nextDouble() * (ground.radius + 3.0)
                val location = ground.center.clone().add(cos(angle) * distance, 0.15, sin(angle) * distance)
                world.spawnParticle(Particle.SPLASH, location, 2, 0.15, 0.03, 0.15, 0.02)
                world.spawnParticle(Particle.BUBBLE, location.clone().subtract(0.0, 0.35, 0.0), 3, 0.2, 0.15, 0.2, 0.01)
            }
            if (effectCycles % 3 == 0) {
                world.playSound(
                    ground.center,
                    Sound.ENTITY_FISHING_BOBBER_SPLASH,
                    0.65f,
                    0.85f + random.nextFloat() * 0.35f
                )
            }
        }
    }

    private fun purgeExpired() {
        val now = System.currentTimeMillis()
        grounds.removeIf { it.expiresAtMillis <= now || plugin.server.getWorld(it.worldId) == null }
    }

    private fun message(player: Player, key: String, vararg placeholders: Pair<String, Any>): Component =
        Component.text(
            CCSystem.getAPI().getI18nString(player, key, placeholders.toMap()).replace('&', '§')
        )

    companion object {
        @JvmStatic
        fun directionId(origin: Location, target: Location): String {
            val degrees = Math.toDegrees(atan2(target.x - origin.x, -(target.z - origin.z)))
            val normalized = (degrees + 360.0) % 360.0
            return listOf("north", "north_east", "east", "south_east", "south", "south_west", "west", "north_west")[
                ((normalized + 22.5) / 45.0).toInt() % 8
            ]
        }

        private fun Location.xzDistanceSquared(other: Location): Double {
            val dx = x - other.x
            val dz = z - other.z
            return dx * dx + dz * dz
        }

        private fun Location.distanceSquaredSafe(other: Location): Double =
            if (world?.uid == other.world?.uid) distanceSquared(other) else Double.POSITIVE_INFINITY

        private fun isFishingBiome(id: String): Boolean =
            id == "river" || id == "frozen_river" || id.endsWith("ocean")
    }
}
