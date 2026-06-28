package jp.awabi2048.cccontent.features.arena.mechanic.ocean

import jp.awabi2048.cccontent.features.arena.mechanic.ArenaMechanicContext
import jp.awabi2048.cccontent.features.arena.mechanic.ArenaMechanicSupport
import jp.awabi2048.cccontent.features.arena.mechanic.ArenaThemeMechanic
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

class OceanMonumentArenaMechanic(private val plugin: JavaPlugin) : ArenaThemeMechanic {
    private val geysersByWave = mutableMapOf<Int, MutableList<Geyser>>()
    private val whirlpoolsByWave = mutableMapOf<Int, MutableList<Whirlpool>>()
    private val nauseaType = PotionEffectType.NAUSEA

    override fun onStageReady(context: ArenaMechanicContext) {
        geysersByWave.clear()
        whirlpoolsByWave.clear()

        for (wave in 1..context.session.waves) {
            val geysers = context.markersForWave(wave, GEYSER_TAG)
                .map { Geyser(wave, it.clone()) }
                .toMutableList()
            if (geysers.isNotEmpty()) {
                geysersByWave[wave] = geysers
                geysers.forEach { context.addCleanupBounds(ArenaMechanicSupport.boundsAround(it.location, 1, 4, 1)) }
            }

            val whirlpools = context.markersForWave(wave, WHIRLPOOL_TAG)
                .map { Whirlpool(wave, it.clone()) }
                .toMutableList()
            if (whirlpools.isNotEmpty()) {
                whirlpoolsByWave[wave] = whirlpools
                whirlpools.forEach { context.addCleanupBounds(ArenaMechanicSupport.boundsAround(it.location, WHIRLPOOL_RADIUS.toInt(), 2, WHIRLPOOL_RADIUS.toInt())) }
            }
        }

        val geyserCount = geysersByWave.values.sumOf { it.size }
        val whirlpoolCount = whirlpoolsByWave.values.sumOf { it.size }
        if (geyserCount > 0 || whirlpoolCount > 0) {
            plugin.logger.info("[Arena] Ocean Monument mechanic prepared: world=${context.session.worldName} geysers=$geyserCount whirlpools=$whirlpoolCount")
        }
    }

    override fun onTick(context: ArenaMechanicContext, currentTick: Long) {
        val activeWaves = ArenaMechanicSupport.activeWaves(context)

        geysersByWave.forEach { (wave, geysers) ->
            geysers.forEach { geyser ->
                if (wave in activeWaves) geyser.tick(context, currentTick) else geyser.reset()
            }
        }
        whirlpoolsByWave.forEach { (wave, whirlpools) ->
            whirlpools.forEach { whirlpool ->
                if (wave in activeWaves) whirlpool.tick(context, currentTick) else whirlpool.reset()
            }
        }
    }

    override fun onWaveCleared(context: ArenaMechanicContext, wave: Int) {
        geysersByWave[wave]?.forEach { it.reset() }
        whirlpoolsByWave[wave]?.forEach { it.reset() }
    }

    override fun onSessionEnded(context: ArenaMechanicContext, success: Boolean) {
        dispose()
    }

    override fun dispose() {
        geysersByWave.values.flatten().forEach { it.reset() }
        whirlpoolsByWave.values.flatten().forEach { it.reset() }
        geysersByWave.clear()
        whirlpoolsByWave.clear()
    }

    private inner class Geyser(
        private val wave: Int,
        val location: Location
    ) {
        private var phase = GeyserPhase.IDLE
        private var remainingTicks = randomIdleTicks()
        private var nextDamageTick = 0L

        fun tick(context: ArenaMechanicContext, currentTick: Long) {
            remainingTicks--
            when (phase) {
                GeyserPhase.IDLE -> if (remainingTicks <= 0) beginWarning()
                GeyserPhase.WARNING -> {
                    renderWarning()
                    if (remainingTicks <= 0) beginErupting(context, currentTick)
                }
                GeyserPhase.ERUPTING -> {
                    renderColumn()
                    applyLift(context)
                    if (currentTick >= nextDamageTick) {
                        nextDamageTick = currentTick + GEYSER_DAMAGE_INTERVAL_TICKS
                        applyHeatDamage(context)
                    }
                    if (remainingTicks <= 0) beginFade()
                }
                GeyserPhase.FADING -> {
                    renderFade()
                    if (remainingTicks <= 0) reset()
                }
            }
        }

        fun reset() {
            phase = GeyserPhase.IDLE
            remainingTicks = randomIdleTicks()
            nextDamageTick = 0L
        }

        private fun beginWarning() {
            phase = GeyserPhase.WARNING
            remainingTicks = GEYSER_WARNING_TICKS
            location.world?.playSound(location, Sound.BLOCK_BUBBLE_COLUMN_UPWARDS_AMBIENT, 0.9f, 1.2f)
        }

        private fun beginErupting(context: ArenaMechanicContext, currentTick: Long) {
            phase = GeyserPhase.ERUPTING
            remainingTicks = GEYSER_ERUPTION_TICKS
            nextDamageTick = currentTick
            location.world?.playSound(location, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1.0f, 0.8f)
            applyHeatDamage(context)
        }

        private fun beginFade() {
            phase = GeyserPhase.FADING
            remainingTicks = GEYSER_FADE_TICKS
        }

        private fun renderWarning() {
            location.world?.spawnParticle(Particle.BUBBLE_COLUMN_UP, location.clone().add(0.5, 0.25, 0.5), 8, 0.25, 0.1, 0.25, 0.02)
        }

        private fun renderColumn() {
            val world = location.world ?: return
            for (height in 0..4) {
                world.spawnParticle(Particle.BUBBLE_COLUMN_UP, location.clone().add(0.5, height + 0.4, 0.5), 5, 0.22, 0.1, 0.22, 0.03)
            }
        }

        private fun renderFade() {
            location.world?.spawnParticle(Particle.BUBBLE, location.clone().add(0.5, 1.0, 0.5), 10, 0.35, 0.45, 0.35, 0.01)
        }

        private fun applyLift(context: ArenaMechanicContext) {
            val center = location.clone().add(0.5, 1.0, 0.5)
            ArenaMechanicSupport.targetsNear(context, center, 0.8, 3.5, 0.8).forEach { target ->
                val velocity = target.velocity
                velocity.y = max(velocity.y, GEYSER_LIFT_Y)
                target.velocity = velocity
            }
        }

        private fun applyHeatDamage(context: ArenaMechanicContext) {
            val center = location.clone().add(0.5, 1.0, 0.5)
            val damage = if (context.session.promoted) GEYSER_DAMAGE_PROMOTED else GEYSER_DAMAGE
            val fireTicks = if (context.session.promoted) GEYSER_FIRE_TICKS_PROMOTED else GEYSER_FIRE_TICKS
            ArenaMechanicSupport.targetsNear(context, center, 0.8, 3.5, 0.8).forEach { target ->
                target.damage(damage)
                target.fireTicks = max(target.fireTicks, fireTicks)
            }
        }

        override fun toString(): String = "Geyser(wave=$wave,location=${location.blockX},${location.blockY},${location.blockZ})"
    }

    private inner class Whirlpool(
        private val wave: Int,
        val location: Location
    ) {
        private var phase = WhirlpoolPhase.IDLE
        private var remainingTicks = randomWhirlpoolIdleTicks()
        private val trappedTicksByEntity = mutableMapOf<UUID, Int>()
        private var nextDamageTick = 0L

        fun tick(context: ArenaMechanicContext, currentTick: Long) {
            remainingTicks--
            when (phase) {
                WhirlpoolPhase.IDLE -> if (remainingTicks <= 0) beginWarning()
                WhirlpoolPhase.WARNING -> {
                    renderWarning()
                    if (remainingTicks <= 0) beginActive(currentTick)
                }
                WhirlpoolPhase.ACTIVE -> {
                    renderWhirlpool()
                    applyPull(context, currentTick)
                    if (remainingTicks <= 0) beginFade()
                }
                WhirlpoolPhase.FADING -> {
                    renderFade()
                    if (remainingTicks <= 0) reset()
                }
            }
        }

        fun reset() {
            phase = WhirlpoolPhase.IDLE
            remainingTicks = randomWhirlpoolIdleTicks()
            trappedTicksByEntity.clear()
            nextDamageTick = 0L
        }

        private fun beginWarning() {
            phase = WhirlpoolPhase.WARNING
            remainingTicks = WHIRLPOOL_WARNING_TICKS
            location.world?.playSound(location, Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_AMBIENT, 0.8f, 1.1f)
        }

        private fun beginActive(currentTick: Long) {
            phase = WhirlpoolPhase.ACTIVE
            remainingTicks = WHIRLPOOL_ACTIVE_TICKS
            nextDamageTick = currentTick + WHIRLPOOL_DAMAGE_INTERVAL_TICKS
            location.world?.playSound(location, Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 1.0f, 0.8f)
        }

        private fun beginFade() {
            phase = WhirlpoolPhase.FADING
            remainingTicks = WHIRLPOOL_FADE_TICKS
            trappedTicksByEntity.clear()
        }

        private fun renderWarning() {
            renderRing(WHIRLPOOL_RADIUS, Particle.BUBBLE_COLUMN_UP, 12)
        }

        private fun renderWhirlpool() {
            renderRing(WHIRLPOOL_RADIUS, Particle.BUBBLE, 18)
            renderRing(WHIRLPOOL_RADIUS * 0.55, Particle.BUBBLE_COLUMN_UP, 10)
        }

        private fun renderFade() {
            renderRing(WHIRLPOOL_RADIUS * 0.6, Particle.BUBBLE, 8)
        }

        private fun renderRing(radius: Double, particle: Particle, count: Int) {
            val world = location.world ?: return
            val tickOffset = remainingTicks.toDouble() * 0.2
            repeat(count) { index ->
                val angle = (index.toDouble() / count.toDouble()) * PI * 2.0 + tickOffset
                world.spawnParticle(
                    particle,
                    location.clone().add(0.5 + cos(angle) * radius, 0.35, 0.5 + sin(angle) * radius),
                    1,
                    0.02,
                    0.02,
                    0.02,
                    0.0
                )
            }
        }

        private fun applyPull(context: ArenaMechanicContext, currentTick: Long) {
            val center = location.clone().add(0.5, 0.5, 0.5)
            val targets = ArenaMechanicSupport.targetsNear(context, center, WHIRLPOOL_RADIUS, 2.0, WHIRLPOOL_RADIUS)
                .filter { it.location.distanceSquared(center) <= WHIRLPOOL_RADIUS * WHIRLPOOL_RADIUS + 2.0 }
            val activeIds = targets.mapTo(mutableSetOf()) { it.uniqueId }
            trappedTicksByEntity.keys.removeIf { it !in activeIds }

            targets.forEach { target ->
                val towardCenter = center.toVector().subtract(target.location.toVector()).setY(0)
                val distance = towardCenter.length().coerceAtLeast(0.2)
                val radial = towardCenter.normalize().multiply(if (context.session.promoted) WHIRLPOOL_PULL_PROMOTED else WHIRLPOOL_PULL)
                val tangent = Vector(-radial.z, 0.0, radial.x).normalize().multiply(WHIRLPOOL_SPIN)
                target.velocity = target.velocity.add(radial.multiply(1.0 + (WHIRLPOOL_RADIUS - distance).coerceAtLeast(0.0) * 0.08)).add(tangent)
                trappedTicksByEntity[target.uniqueId] = (trappedTicksByEntity[target.uniqueId] ?: 0) + 1
                applyPlayerPenalty(context, target, currentTick)
            }
        }

        private fun applyPlayerPenalty(context: ArenaMechanicContext, target: LivingEntity, currentTick: Long) {
            val player = target as? Player ?: return
            val trappedTicks = trappedTicksByEntity[player.uniqueId] ?: return
            if (trappedTicks >= WHIRLPOOL_NAUSEA_TICKS) {
                player.addPotionEffect(PotionEffect(nauseaType, 25, 0, false, false, false))
            }
            if (trappedTicks >= WHIRLPOOL_DAMAGE_START_TICKS && currentTick >= nextDamageTick) {
                nextDamageTick = currentTick + WHIRLPOOL_DAMAGE_INTERVAL_TICKS
                val damage = if (context.session.promoted) WHIRLPOOL_DAMAGE_PROMOTED else WHIRLPOOL_DAMAGE
                player.damage(damage)
            }
        }

        override fun toString(): String = "Whirlpool(wave=$wave,location=${location.blockX},${location.blockY},${location.blockZ})"
    }

    private enum class GeyserPhase {
        IDLE,
        WARNING,
        ERUPTING,
        FADING
    }

    private enum class WhirlpoolPhase {
        IDLE,
        WARNING,
        ACTIVE,
        FADING
    }

    companion object {
        private const val GEYSER_TAG = "arena.marker.mechanic.ocean_monument.geyser"
        private const val WHIRLPOOL_TAG = "arena.marker.mechanic.ocean_monument.whirlpool"
        private const val GEYSER_IDLE_MIN_TICKS = 60L
        private const val GEYSER_IDLE_MAX_TICKS = 100L
        private const val GEYSER_WARNING_TICKS = 20L
        private const val GEYSER_ERUPTION_TICKS = 40L
        private const val GEYSER_FADE_TICKS = 10L
        private const val GEYSER_DAMAGE_INTERVAL_TICKS = 20L
        private const val GEYSER_DAMAGE = 1.5
        private const val GEYSER_DAMAGE_PROMOTED = 2.0
        private const val GEYSER_FIRE_TICKS = 40
        private const val GEYSER_FIRE_TICKS_PROMOTED = 60
        private const val GEYSER_LIFT_Y = 0.9
        private const val WHIRLPOOL_IDLE_MIN_TICKS = 120L
        private const val WHIRLPOOL_IDLE_MAX_TICKS = 160L
        private const val WHIRLPOOL_WARNING_TICKS = 30L
        private const val WHIRLPOOL_ACTIVE_TICKS = 100L
        private const val WHIRLPOOL_FADE_TICKS = 20L
        private const val WHIRLPOOL_RADIUS = 3.0
        private const val WHIRLPOOL_PULL = 0.08
        private const val WHIRLPOOL_PULL_PROMOTED = 0.12
        private const val WHIRLPOOL_SPIN = 0.06
        private const val WHIRLPOOL_NAUSEA_TICKS = 40
        private const val WHIRLPOOL_DAMAGE_START_TICKS = 80
        private const val WHIRLPOOL_DAMAGE_INTERVAL_TICKS = 20L
        private const val WHIRLPOOL_DAMAGE = 0.5
        private const val WHIRLPOOL_DAMAGE_PROMOTED = 1.0

        private fun randomIdleTicks(): Long = Random.nextLong(GEYSER_IDLE_MIN_TICKS, GEYSER_IDLE_MAX_TICKS + 1)
        private fun randomWhirlpoolIdleTicks(): Long = Random.nextLong(WHIRLPOOL_IDLE_MIN_TICKS, WHIRLPOOL_IDLE_MAX_TICKS + 1)
    }
}
