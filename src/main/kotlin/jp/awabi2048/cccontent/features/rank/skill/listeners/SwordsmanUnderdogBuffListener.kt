package jp.awabi2048.cccontent.features.rank.skill.listeners

import jp.awabi2048.cccontent.features.rank.profession.Profession
import jp.awabi2048.cccontent.features.rank.skill.SkillEffectEngine
import jp.awabi2048.cccontent.features.rank.skill.handlers.SwordsmanUnderdogBuffHandler
import org.bukkit.entity.Enemy
import org.bukkit.entity.Monster
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.plugin.Plugin
import org.bukkit.potion.PotionEffect
import org.bukkit.scheduler.BukkitRunnable

class SwordsmanUnderdogBuffListener(
    private val plugin: Plugin
) : Listener {
    companion object {
        private const val EFFECT_DURATION_TICKS = 40
    }

    init {
        object : BukkitRunnable() {
            override fun run() {
                tick()
            }
        }.runTaskTimer(plugin, 20L, 20L)
    }

    private fun tick() {
        for (player in plugin.server.onlinePlayers) {
            applyUnderdogBuff(player)
        }
    }

    private fun applyUnderdogBuff(player: Player) {
        if (player.isDead || !player.isOnline) {
            return
        }

        val compiledEffects = SkillEffectEngine.getCachedEffects(player.uniqueId) ?: return
        if (compiledEffects.profession != Profession.SWORDSMAN) {
            return
        }

        val entries = compiledEffects.byType[SwordsmanUnderdogBuffHandler.EFFECT_TYPE] ?: return
        if (entries.isEmpty()) {
            return
        }

        for (entry in entries) {
            val radius = entry.effect.getIntParam("radius", 0)
            if (radius <= 0) {
                continue
            }

            val ratioThreshold = entry.effect.getDoubleParam("ratio", -1.0)
            if (ratioThreshold < 0.0) {
                continue
            }

            val parsedBuff = SwordsmanUnderdogBuffHandler.parseBuffNotation(
                entry.effect.getStringParam("buff", "")
            ) ?: continue

            val currentRatio = calculateNearbyHostileRatio(player, radius)
            if (currentRatio >= ratioThreshold) {
                player.addPotionEffect(
                    PotionEffect(parsedBuff.type, EFFECT_DURATION_TICKS, parsedBuff.amplifier, false, false, true)
                )
            }
        }
    }

    private fun calculateNearbyHostileRatio(player: Player, radius: Int): Double {
        val radiusDouble = radius.toDouble()
        val radiusSquared = radiusDouble * radiusDouble
        val center = player.location
        val nearbyEntities = player.world.getNearbyEntities(center, radiusDouble, radiusDouble, radiusDouble)

        var players = if (!player.isDead) 1 else 0
        var hostiles = 0

        for (entity in nearbyEntities) {
            val distanceSquared = entity.location.distanceSquared(center)
            if (distanceSquared > radiusSquared) {
                continue
            }

            when (entity) {
                is Player -> {
                    if (entity.uniqueId != player.uniqueId && entity.isOnline && !entity.isDead) {
                        players++
                    }
                }

                is Monster, is Enemy -> {
                    if (!entity.isDead && entity.isValid) {
                        hostiles++
                    }
                }
            }
        }

        if (players <= 0) {
            return 0.0
        }

        return hostiles.toDouble() / players.toDouble()
    }
}
