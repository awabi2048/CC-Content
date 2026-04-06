package jp.awabi2048.cccontent.mob.ability

import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

object EndThemeEffects {
    fun playTeleportSound(world: World, location: Location) {
        world.playSound(location, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 2.0f)
    }

    fun spawnTeleportDebuffField(
        plugin: JavaPlugin,
        owner: LivingEntity,
        center: Location,
        radius: Double = 3.2,
        durationTicks: Long = 60L,
        pulseIntervalTicks: Long = 10L,
        blindnessTicks: Int = 50,
        witherTicks: Int = 50
    ) {
        val world = center.world ?: return
        val fixedCenter = center.clone()
        var remaining = durationTicks.coerceAtLeast(1L)
        var pulseCooldown = 0L

        object : BukkitRunnable() {
            override fun run() {
                if (!plugin.isEnabled || remaining <= 0L || world.uid != fixedCenter.world?.uid) {
                    cancel()
                    return
                }

                renderField(world, fixedCenter, radius)

                if (pulseCooldown <= 0L) {
                    applyDebuffPulse(owner, fixedCenter, radius, blindnessTicks, witherTicks)
                    pulseCooldown = pulseIntervalTicks.coerceAtLeast(1L)
                }

                pulseCooldown -= 1L
                remaining -= 1L
            }
        }.runTaskTimer(plugin, 0L, 1L)
    }

    fun findNearbyTeleportLocation(anchor: Location, radius: Double, attempts: Int = 24): Location? {
        val world = anchor.world ?: return null
        repeat(attempts.coerceAtLeast(1)) {
            val angle = Random.nextDouble(0.0, Math.PI * 2.0)
            val distance = Random.nextDouble(1.2, radius.coerceAtLeast(1.2))
            val x = anchor.x + cos(angle) * distance
            val z = anchor.z + sin(angle) * distance
            val yBase = anchor.y

            for (offset in listOf(2, 1, 0, -1, -2, -3)) {
                val y = yBase + offset
                val feet = Location(world, x, y, z)
                if (isSafe(feet)) {
                    return feet
                }
            }
        }
        return null
    }

    private fun isSafe(location: Location): Boolean {
        val world = location.world ?: return false
        val feet = world.getBlockAt(location.blockX, location.blockY, location.blockZ)
        val head = world.getBlockAt(location.blockX, location.blockY + 1, location.blockZ)
        val below = world.getBlockAt(location.blockX, location.blockY - 1, location.blockZ)
        return feet.isPassable && head.isPassable && below.type.isSolid
    }

    private fun applyDebuffPulse(
        owner: LivingEntity,
        center: Location,
        radius: Double,
        blindnessTicks: Int,
        witherTicks: Int
    ) {
        val world = center.world ?: return
        world.getNearbyEntities(center, radius, radius, radius)
            .asSequence()
            .filterIsInstance<Player>()
            .filter { it.isValid && !it.isDead }
            .forEach { target ->
                target.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, blindnessTicks, 0, false, true, true))
                target.addPotionEffect(PotionEffect(PotionEffectType.WITHER, witherTicks, 0, false, true, true))
            }
    }

    private fun renderField(world: World, center: Location, radius: Double) {
        world.spawnParticle(Particle.PORTAL, center.clone().add(0.0, 0.4, 0.0), 24, radius, 0.25, radius, 0.04)
        world.spawnParticle(Particle.SQUID_INK, center.clone().add(0.0, 0.2, 0.0), 14, radius * 0.65, 0.2, radius * 0.65, 0.0)
    }
}
