@file:Suppress("DEPRECATION")

package jp.awabi2048.cccontent.features.arena

import org.bukkit.Bukkit
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.UUID
import kotlin.random.Random

class ConfusionManager(private val plugin: JavaPlugin) {

    private data class ConfusionState(
        val expireAtTick: Long,
        var lastMovementNoiseTick: Long = 0L,
        var lastParticleTick: Long = 0L
    )

    private val states = mutableMapOf<UUID, ConfusionState>()

    fun isConfused(playerId: UUID): Boolean {
        return states.containsKey(playerId)
    }

    fun applyConfusion(playerId: UUID, durationTicks: Long) {
        val currentTick = Bukkit.getCurrentTick().toLong()
        val state = states[playerId]
        val newExpire = currentTick + durationTicks
        val player = Bukkit.getPlayer(playerId)
        if (player != null && player.isOnline) {
            applyNauseaEffect(player)
        }
        if (state != null) {
            states[playerId] = state.copy(expireAtTick = maxOf(state.expireAtTick, newExpire))
        } else {
            states[playerId] = ConfusionState(expireAtTick = newExpire)
            if (player != null && player.isOnline) {
                player.world.spawnParticle(
                    Particle.ANGRY_VILLAGER,
                    player.location.add(0.0, 2.0, 0.0),
                    4, 0.3, 0.2, 0.3, 0.0
                )
            }
        }
    }

    fun removeConfusion(playerId: UUID) {
        states.remove(playerId)
    }

    fun clearAll() {
        states.clear()
    }

    fun tick() {
        val currentTick = Bukkit.getCurrentTick().toLong()
        val iterator = states.entries.iterator()
        while (iterator.hasNext()) {
            val (playerId, state) = iterator.next()
            if (currentTick >= state.expireAtTick) {
                iterator.remove()
                continue
            }

            val player = Bukkit.getPlayer(playerId)
            if (player == null || !player.isOnline) {
                iterator.remove()
                continue
            }

            if (currentTick - state.lastMovementNoiseTick >= MOVEMENT_NOISE_INTERVAL_TICKS) {
                state.lastMovementNoiseTick = currentTick
                if (Random.nextDouble() < MOVEMENT_NOISE_CHANCE) {
                    applyMovementNoise(player)
                }
            }

            if (currentTick - state.lastParticleTick >= PARTICLE_INTERVAL_TICKS) {
                state.lastParticleTick = currentTick
                player.world.spawnParticle(
                    Particle.ANGRY_VILLAGER,
                    player.location.add(0.0, 2.0, 0.0),
                    1, 0.3, 0.2, 0.3, 0.0
                )
            }
        }
    }

    private fun applyMovementNoise(player: Player) {
        val angle = Random.nextDouble(Math.PI * 2.0)
        val strength = Random.nextDouble(MOVEMENT_NOISE_MIN, MOVEMENT_NOISE_MAX)
        val dx = Math.cos(angle) * strength
        val dz = Math.sin(angle) * strength
        player.velocity = player.velocity.add(
            org.bukkit.util.Vector(dx, 0.0, dz)
        )
    }

    private fun applyNauseaEffect(player: Player) {
        val nauseaType = PotionEffectType.getByName("NAUSEA")
            ?: PotionEffectType.getByName("CONFUSION")
            ?: return
        player.addPotionEffect(PotionEffect(nauseaType, CONFUSION_NAUSEA_TICKS, 0, false, false, false), true)
    }

    companion object {
        private const val MOVEMENT_NOISE_INTERVAL_TICKS = 10L
        private const val MOVEMENT_NOISE_CHANCE = 0.5
        private const val MOVEMENT_NOISE_MIN = 0.2
        private const val MOVEMENT_NOISE_MAX = 0.35
        private const val PARTICLE_INTERVAL_TICKS = 20L
        private const val CONFUSION_NAUSEA_TICKS = 20
    }
}
