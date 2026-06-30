package jp.awabi2048.cccontent.features.arena.mechanic.natura

import jp.awabi2048.cccontent.features.arena.mechanic.ArenaMechanicContext
import jp.awabi2048.cccontent.features.arena.mechanic.ArenaMechanicSupport
import jp.awabi2048.cccontent.features.arena.mechanic.ArenaThemeMechanic
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.block.BlockFace
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.block.data.type.PointedDripstone
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.UUID
import kotlin.math.max
import kotlin.random.Random

class NaturaArenaMechanic(private val plugin: JavaPlugin) : ArenaThemeMechanic {
    private val stalactitesByWave = mutableMapOf<Int, MutableList<StalactiteDrop>>()
    private val mistsByWave = mutableMapOf<Int, MutableList<MistCloud>>()

    override fun onStageReady(context: ArenaMechanicContext) {
        stalactitesByWave.clear()
        mistsByWave.clear()

        for (wave in 1..context.session.waves) {
            val stalactites = context.markersForWave(wave, STALACTITE_TAG)
                .map { StalactiteDrop(wave, it.clone()) }
                .toMutableList()
            if (stalactites.isNotEmpty()) {
                stalactitesByWave[wave] = stalactites
                stalactites.forEach { context.addCleanupBounds(ArenaMechanicSupport.boundsAround(it.location, 2, 5, 2)) }
            }

            val mists = context.markersForWave(wave, MIST_TAG)
                .map { MistCloud(wave, it.clone()) }
                .toMutableList()
            if (mists.isNotEmpty()) {
                mistsByWave[wave] = mists
                mists.forEach { context.addCleanupBounds(ArenaMechanicSupport.boundsAround(it.location, MIST_RADIUS.toInt(), 2, MIST_RADIUS.toInt())) }
            }
        }

        val stalactiteCount = stalactitesByWave.values.sumOf { it.size }
        val mistCount = mistsByWave.values.sumOf { it.size }
        if (stalactiteCount > 0 || mistCount > 0) {
            plugin.logger.info("[Arena] Natura mechanic prepared: world=${context.session.worldName} stalactites=$stalactiteCount mists=$mistCount")
        }
    }

    override fun onTick(context: ArenaMechanicContext, currentTick: Long) {
        val activeWaves = ArenaMechanicSupport.activeWaves(context)

        stalactitesByWave.forEach { (wave, drops) ->
            drops.forEach { drop ->
                if (wave in activeWaves) drop.tick(context, currentTick) else drop.reset()
            }
        }
        mistsByWave.forEach { (wave, mists) ->
            mists.forEach { mist ->
                if (wave in activeWaves) mist.tick(context, currentTick) else mist.reset()
            }
        }
    }

    override fun onWaveCleared(context: ArenaMechanicContext, wave: Int) {
        stalactitesByWave[wave]?.forEach { it.reset() }
        mistsByWave[wave]?.forEach { it.reset() }
    }

    override fun onSessionEnded(context: ArenaMechanicContext, success: Boolean) {
        dispose()
    }

    override fun dispose() {
        stalactitesByWave.values.flatten().forEach { it.reset() }
        mistsByWave.values.flatten().forEach { it.reset() }
        stalactitesByWave.clear()
        mistsByWave.clear()
    }

    private inner class StalactiteDrop(
        private val wave: Int,
        val location: Location
    ) {
        private var phase = StalactitePhase.IDLE
        private var remainingTicks = randomStalactiteIdleTicks()
        private var displayId: UUID? = null
        private var startY = location.y + 6.0
        private var impactY = location.y + 0.15

        fun tick(context: ArenaMechanicContext, currentTick: Long) {
            remainingTicks--
            when (phase) {
                StalactitePhase.IDLE -> if (remainingTicks <= 0) beginWarning(context)
                StalactitePhase.WARNING -> {
                    renderWarning()
                    if (remainingTicks <= 0) beginFalling()
                }
                StalactitePhase.FALLING -> {
                    updateFallingDisplay()
                    if (remainingTicks <= 0) impact(context)
                }
                StalactitePhase.DEBRIS -> {
                    renderDebris()
                    if (remainingTicks <= 0) reset()
                }
            }
        }

        fun reset() {
            removeDisplay()
            phase = StalactitePhase.IDLE
            remainingTicks = randomStalactiteIdleTicks()
        }

        private fun beginWarning(context: ArenaMechanicContext) {
            val roomTop = context.session.roomBounds[wave]?.maxY ?: (location.blockY + 8)
            startY = max(roomTop - 1.0, location.y + 3.0)
            impactY = location.y + 0.15
            phase = StalactitePhase.WARNING
            remainingTicks = STALACTITE_WARNING_TICKS
            location.world?.playSound(location, Sound.BLOCK_POINTED_DRIPSTONE_DRIP_LAVA_INTO_CAULDRON, 0.7f, 0.65f)
        }

        private fun beginFalling() {
            spawnDisplay()
            phase = StalactitePhase.FALLING
            remainingTicks = STALACTITE_FALL_TICKS
        }

        private fun impact(context: ArenaMechanicContext) {
            removeDisplay()
            val center = location.clone().add(0.5, 0.8, 0.5)
            val damage = if (context.session.promoted) STALACTITE_DAMAGE_PROMOTED else STALACTITE_DAMAGE
            val aoeDamage = if (context.session.promoted) STALACTITE_AOE_DAMAGE_PROMOTED else STALACTITE_AOE_DAMAGE
            ArenaMechanicSupport.targetsNear(context, center, 1.4, 1.2, 1.4).forEach { target ->
                val direct = target.location.distanceSquared(center) <= 0.75 * 0.75
                target.damage(if (direct) damage else aoeDamage)
            }
            location.world?.playSound(location, Sound.BLOCK_POINTED_DRIPSTONE_LAND, 1.0f, 0.75f)
            location.world?.spawnParticle(Particle.CRIT, location.clone().add(0.5, 0.5, 0.5), 18, 0.6, 0.25, 0.6, 0.08)
            phase = StalactitePhase.DEBRIS
            remainingTicks = STALACTITE_DEBRIS_TICKS
        }

        private fun renderWarning() {
            location.world?.spawnParticle(Particle.CRIT, location.clone().add(0.5, 0.2, 0.5), 4, 0.35, 0.05, 0.35, 0.02)
        }

        private fun renderDebris() {
            location.world?.spawnParticle(Particle.CRIT, location.clone().add(0.5, 0.25, 0.5), 3, 0.4, 0.15, 0.4, 0.02)
        }

        private fun spawnDisplay() {
            val world = location.world ?: return
            val display = world.spawn(
                Location(world, location.x + 0.5, startY, location.z + 0.5),
                BlockDisplay::class.java
            )
            val dripstone = Material.POINTED_DRIPSTONE.createBlockData() as PointedDripstone
            dripstone.verticalDirection = BlockFace.DOWN
            display.setBlock(dripstone)
            display.isInvulnerable = true
            display.interpolationDuration = 1
            display.teleportDuration = 1
            display.brightness = Display.Brightness(15, 15)
            display.transformation = Transformation(
                Vector3f(-1.5f, -1.5f, -1.5f),
                Quaternionf(),
                Vector3f(3f, 3f, 3f),
                Quaternionf()
            )
            display.addScoreboardTag("arena.mechanic.natura.stalactite_display")
            displayId = display.uniqueId
        }

        private fun updateFallingDisplay() {
            val display = displayId?.let { org.bukkit.Bukkit.getEntity(it) as? BlockDisplay } ?: return
            if (!display.isValid) return
            val progress = 1.0 - (remainingTicks.toDouble() / STALACTITE_FALL_TICKS.toDouble()).coerceIn(0.0, 1.0)
            val y = startY + (impactY - startY) * progress
            display.teleport(Location(display.world, location.x + 0.5, y, location.z + 0.5))
        }

        private fun removeDisplay() {
            val display = displayId?.let { org.bukkit.Bukkit.getEntity(it) }
            if (display != null && display.isValid) display.remove()
            displayId = null
        }

        override fun toString(): String = "StalactiteDrop(wave=$wave,location=${location.blockX},${location.blockY},${location.blockZ})"
    }

    private inner class MistCloud(
        private val wave: Int,
        val location: Location
    ) {
        private var phase = MistPhase.IDLE
        private var remainingTicks = randomMistIdleTicks()
        private var nextPoisonTick = 0L

        fun tick(context: ArenaMechanicContext, currentTick: Long) {
            remainingTicks--
            when (phase) {
                MistPhase.IDLE -> if (remainingTicks <= 0) beginGrowing()
                MistPhase.GROWING -> {
                    renderMist(intensity = 0.5)
                    if (remainingTicks <= 0) beginActive(currentTick)
                }
                MistPhase.ACTIVE -> {
                    renderMist(intensity = 1.0)
                    if (currentTick >= nextPoisonTick) {
                        nextPoisonTick = currentTick + MIST_POISON_INTERVAL_TICKS
                        applyPoison(context)
                    }
                    if (remainingTicks <= 0) beginFading()
                }
                MistPhase.FADING -> {
                    renderMist(intensity = 0.35)
                    if (remainingTicks <= 0) reset()
                }
            }
        }

        fun reset() {
            phase = MistPhase.IDLE
            remainingTicks = randomMistIdleTicks()
            nextPoisonTick = 0L
        }

        private fun beginGrowing() {
            phase = MistPhase.GROWING
            remainingTicks = MIST_GROW_TICKS
            location.world?.playSound(location, Sound.AMBIENT_UNDERWATER_EXIT, 0.6f, 0.65f)
        }

        private fun beginActive(currentTick: Long) {
            phase = MistPhase.ACTIVE
            remainingTicks = MIST_ACTIVE_TICKS
            nextPoisonTick = currentTick
        }

        private fun beginFading() {
            phase = MistPhase.FADING
            remainingTicks = MIST_FADE_TICKS
        }

        private fun renderMist(intensity: Double) {
            val world = location.world ?: return
            val count = (MIST_PARTICLE_COUNT * intensity).toInt().coerceAtLeast(3)
            val dust = Particle.DustOptions(Color.fromRGB(120, 200, 80), 1.4f)
            val center = mistCenter()
            world.spawnParticle(
                Particle.DUST,
                center,
                count,
                MIST_RADIUS,
                0.65,
                MIST_RADIUS,
                0.02,
                dust
            )
            world.spawnParticle(Particle.SPORE_BLOSSOM_AIR, center, count / 2, MIST_RADIUS, 0.6, MIST_RADIUS, 0.01)
        }

        private fun applyPoison(context: ArenaMechanicContext) {
            val center = mistCenter()
            ArenaMechanicSupport.targetsNear(context, center, MIST_RADIUS, 1.5, MIST_RADIUS)
                .filter { it.location.distanceSquared(center) <= MIST_RADIUS * MIST_RADIUS + 2.25 }
                .forEach { target ->
                    target.addPotionEffect(PotionEffect(PotionEffectType.POISON, MIST_POISON_TICKS, 1, false, true, true))
                }
        }

        private fun mistCenter(): Location {
            return location.clone().add(0.5, MIST_CENTER_Y_OFFSET, 0.5)
        }

        override fun toString(): String = "MistCloud(wave=$wave,location=${location.blockX},${location.blockY},${location.blockZ})"
    }

    private enum class StalactitePhase {
        IDLE,
        WARNING,
        FALLING,
        DEBRIS
    }

    private enum class MistPhase {
        IDLE,
        GROWING,
        ACTIVE,
        FADING
    }

    companion object {
        private const val STALACTITE_TAG = "arena.marker.mechanic.natura.stalactite"
        private const val MIST_TAG = "arena.marker.mechanic.natura.mist"
        private const val STALACTITE_IDLE_MIN_TICKS = 50L
        private const val STALACTITE_IDLE_MAX_TICKS = 90L
        private const val STALACTITE_WARNING_TICKS = 20L
        private const val STALACTITE_FALL_TICKS = 12L
        private const val STALACTITE_DEBRIS_TICKS = 40L
        private const val STALACTITE_DAMAGE = 3.0
        private const val STALACTITE_DAMAGE_PROMOTED = 5.0
        private const val STALACTITE_AOE_DAMAGE = 1.5
        private const val STALACTITE_AOE_DAMAGE_PROMOTED = 2.5
        private const val MIST_IDLE_MIN_TICKS = 80L
        private const val MIST_IDLE_MAX_TICKS = 120L
        private const val MIST_GROW_TICKS = 30L
        private const val MIST_ACTIVE_TICKS = 160L
        private const val MIST_FADE_TICKS = 30L
        private const val MIST_RADIUS = 3.0
        private const val MIST_CENTER_Y_OFFSET = -1.0
        private const val MIST_PARTICLE_COUNT = 18
        private const val MIST_POISON_INTERVAL_TICKS = 60L
        private const val MIST_POISON_TICKS = 60

        private fun randomStalactiteIdleTicks(): Long = Random.nextLong(STALACTITE_IDLE_MIN_TICKS, STALACTITE_IDLE_MAX_TICKS + 1)
        private fun randomMistIdleTicks(): Long = Random.nextLong(MIST_IDLE_MIN_TICKS, MIST_IDLE_MAX_TICKS + 1)
    }
}
