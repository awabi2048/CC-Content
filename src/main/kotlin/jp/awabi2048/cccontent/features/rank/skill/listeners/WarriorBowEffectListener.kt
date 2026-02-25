package jp.awabi2048.cccontent.features.rank.skill.listeners

import jp.awabi2048.cccontent.CCContent
import jp.awabi2048.cccontent.features.rank.profession.Profession
import jp.awabi2048.cccontent.features.rank.skill.SkillEffectEngine
import jp.awabi2048.cccontent.features.rank.skill.handlers.WarriorBowPowerBoostHandler
import jp.awabi2048.cccontent.features.rank.skill.handlers.WarriorSnipeHandler
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.AbstractArrow
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import java.util.UUID

class WarriorBowEffectListener : Listener {

    companion object {
        private val plugin by lazy { JavaPlugin.getPlugin(CCContent::class.java) }
        private val bowPowerDeltaKey by lazy { NamespacedKey(plugin, "warrior_bow_power_delta") }
        private val snipeDamageFactorKey by lazy { NamespacedKey(plugin, "warrior_snipe_damage_factor") }
        private const val FULL_BOW_CHARGE_TICKS = 20.0
        private const val TICKS_PER_MILLISECOND = 1.0 / 50.0
        private const val ARROW_GRAVITY_PER_TICK = 0.05
        private const val MAX_GRAVITY_COMPENSATION_PER_TICK = 0.049
        private const val MAX_RANGE_COMPENSATION_TICKS = 100
        private const val CHARGE_BAR_SEGMENTS = 15
        private const val EXTRA_CHARGE_RELEASE_GRACE_MILLIS = 750L
    }

    private data class SnipeFlightState(
        val arrow: AbstractArrow,
        val compensationPerTick: Double,
        var remainingTicks: Int
    )

    private data class ExtraChargeState(
        val startedAtMillis: Long,
        val maxChargeTicks: Double,
        var lastHandRaisedAtMillis: Long,
        var completionNotified: Boolean = false
    )

    private val bowDrawStartedAtMillis = mutableMapOf<UUID, Long>()
    private val extraChargeStates = mutableMapOf<UUID, ExtraChargeState>()
    private val snipeFlightStates = mutableMapOf<UUID, SnipeFlightState>()

    init {
        startSnipeFlightTask()
        startExtraChargeHudTask()
    }

    private fun startSnipeFlightTask() {
        object : BukkitRunnable() {
            override fun run() {
                if (snipeFlightStates.isEmpty()) {
                    return
                }

                val iterator = snipeFlightStates.entries.iterator()
                while (iterator.hasNext()) {
                    val state = iterator.next().value
                    val arrow = state.arrow
                    if (!arrow.isValid || arrow.isDead || arrow.isOnGround || state.remainingTicks <= 0) {
                        iterator.remove()
                        continue
                    }

                    val velocity = arrow.velocity
                    if (velocity.lengthSquared() <= 1.0E-8) {
                        iterator.remove()
                        continue
                    }

                    arrow.world.spawnParticle(
                        Particle.ENCHANTED_HIT,
                        arrow.location,
                        1,
                        0.0,
                        0.0,
                        0.0,
                        0.0
                    )

                    if (state.compensationPerTick > 0.0) {
                        velocity.y += state.compensationPerTick
                        arrow.velocity = velocity
                    }
                    state.remainingTicks -= 1
                }
            }
        }.runTaskTimer(plugin, 1L, 1L)
    }

    private fun startExtraChargeHudTask() {
        object : BukkitRunnable() {
            override fun run() {
                if (extraChargeStates.isEmpty()) {
                    return
                }

                val now = System.currentTimeMillis()
                val iterator = extraChargeStates.entries.iterator()
                while (iterator.hasNext()) {
                    val (playerUuid, state) = iterator.next()
                    val player = plugin.server.getPlayer(playerUuid)
                    if (player == null || !player.isOnline) {
                        iterator.remove()
                        continue
                    }

                    if (!player.isSneaking || !player.isHandRaised) {
                        if (!player.isSneaking) {
                            clearChargeSubtitle(player)
                            iterator.remove()
                            continue
                        }

                        clearChargeSubtitle(player)
                        if (now - state.lastHandRaisedAtMillis > EXTRA_CHARGE_RELEASE_GRACE_MILLIS) {
                            iterator.remove()
                        }
                        continue
                    }

                    state.lastHandRaisedAtMillis = now

                    val elapsedTicks = (now - state.startedAtMillis) * TICKS_PER_MILLISECOND
                    val ratio = (elapsedTicks / state.maxChargeTicks).coerceIn(0.0, 1.0)
                    val completed = ratio >= 1.0
                    if (completed && !state.completionNotified) {
                        player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.6f)
                        state.completionNotified = true
                    }

                    val subtitle = buildChargeSubtitle(ratio, completed)
                    player.sendTitle("", subtitle, 0, 5, 0)
                }
            }
        }.runTaskTimer(plugin, 1L, 1L)
    }

    private fun buildChargeSubtitle(ratio: Double, completed: Boolean): String {
        if (completed) {
            return "§b" + "|".repeat(CHARGE_BAR_SEGMENTS)
        }

        val filled = (ratio.coerceIn(0.0, 1.0) * CHARGE_BAR_SEGMENTS).toInt().coerceIn(0, CHARGE_BAR_SEGMENTS)
        val empty = CHARGE_BAR_SEGMENTS - filled
        return "§a" + "|".repeat(filled) + "§7" + "|".repeat(empty)
    }

    private fun clearChargeSubtitle(player: Player) {
        player.sendTitle("", "", 0, 0, 0)
    }

    private fun calculateEffectiveRangeMultiplier(baseRangeMultiplier: Double, ratio: Double): Double {
        val clampedBase = baseRangeMultiplier.coerceAtLeast(1.0)
        val clampedRatio = ratio.coerceIn(0.0, 1.0)
        return 1.0 + (clampedBase - 1.0) * clampedRatio
    }

    private fun calculateGravityCompensationPerTick(rangeMultiplier: Double): Double {
        if (rangeMultiplier <= 1.0) {
            return 0.0
        }

        val compensation = ARROW_GRAVITY_PER_TICK * (1.0 - 1.0 / rangeMultiplier)
        return compensation.coerceIn(0.0, MAX_GRAVITY_COMPENSATION_PER_TICK)
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onBowInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) {
            return
        }

        val player = event.player
        val hand = event.hand ?: EquipmentSlot.HAND
        val itemInUsedHand = when (hand) {
            EquipmentSlot.HAND -> player.inventory.itemInMainHand
            EquipmentSlot.OFF_HAND -> player.inventory.itemInOffHand
            else -> null
        } ?: return

        if (!itemInUsedHand.type.name.endsWith("BOW")) {
            bowDrawStartedAtMillis.remove(player.uniqueId)
            if (extraChargeStates.remove(player.uniqueId) != null) {
                clearChargeSubtitle(player)
            }
            return
        }

        bowDrawStartedAtMillis[player.uniqueId] = System.currentTimeMillis()
        if (extraChargeStates.remove(player.uniqueId) != null) {
            clearChargeSubtitle(player)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onSneak(event: PlayerToggleSneakEvent) {
        val player = event.player
        val playerUuid = player.uniqueId

        if (!event.isSneaking) {
            if (extraChargeStates.remove(playerUuid) != null) {
                clearChargeSubtitle(player)
            }
            return
        }

        val compiledEffects = SkillEffectEngine.getCachedEffects(playerUuid) ?: return
        if (compiledEffects.profession != Profession.WARRIOR) {
            return
        }

        val snipeEntries = compiledEffects.byType[WarriorSnipeHandler.EFFECT_TYPE]
        if (snipeEntries.isNullOrEmpty()) {
            return
        }

        val maxChargeTime = snipeEntries
            .sumOf { it.effect.getDoubleParam("max_charge_time", 0.0).coerceAtLeast(0.0) }
        if (maxChargeTime <= 0.0) {
            return
        }

        if (!player.isHandRaised) {
            return
        }

        val drawStartedAt = bowDrawStartedAtMillis[playerUuid] ?: return
        val drawTicks = (System.currentTimeMillis() - drawStartedAt) * TICKS_PER_MILLISECOND
        if (drawTicks >= FULL_BOW_CHARGE_TICKS) {
            extraChargeStates[playerUuid] = ExtraChargeState(
                startedAtMillis = System.currentTimeMillis(),
                maxChargeTicks = maxChargeTime,
                lastHandRaisedAtMillis = System.currentTimeMillis()
            )
            player.playSound(player.location, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.9f, 1.4f)
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onShootBow(event: EntityShootBowEvent) {
        val shooter = event.entity as? Player ?: return
        val arrow = event.projectile as? AbstractArrow ?: return
        val bow = event.bow ?: return

        val compiledEffects = SkillEffectEngine.getCachedEffects(shooter.uniqueId) ?: return
        if (compiledEffects.profession != Profession.WARRIOR) {
            return
        }
        if (!bow.type.name.contains("BOW")) {
            return
        }

        val shooterUuid = shooter.uniqueId
        bowDrawStartedAtMillis.remove(shooterUuid)

        val fullChargeReached = event.force >= 1.0f
        val extraChargeState = extraChargeStates.remove(shooterUuid)
        if (extraChargeState != null) {
            clearChargeSubtitle(shooter)
        }
        if (fullChargeReached && shooter.isSneaking && extraChargeState != null) {
            val snipeEntries = compiledEffects.byType[WarriorSnipeHandler.EFFECT_TYPE]
            if (!snipeEntries.isNullOrEmpty()) {
                val damageMultiplier = snipeEntries
                    .sumOf { it.effect.getDoubleParam("damage_multiplier", 0.0).coerceAtLeast(0.0) }
                val rangeMultiplier = snipeEntries
                    .maxOfOrNull { it.effect.getDoubleParam("range_multiplier", 1.0).coerceAtLeast(1.0) }
                    ?: 1.0

                if (extraChargeState.maxChargeTicks > 0.0) {
                    val additionalChargeTicks = (System.currentTimeMillis() - extraChargeState.startedAtMillis) * TICKS_PER_MILLISECOND
                    val ratio = (additionalChargeTicks / extraChargeState.maxChargeTicks).coerceIn(0.0, 1.0)

                    val damageFactor = (1.0 + ratio) * damageMultiplier
                    if (damageFactor > 0.0) {
                        arrow.persistentDataContainer.set(snipeDamageFactorKey, PersistentDataType.DOUBLE, damageFactor)
                    }

                    val effectiveRangeMultiplier = calculateEffectiveRangeMultiplier(rangeMultiplier, ratio)
                    val compensationPerTick = calculateGravityCompensationPerTick(effectiveRangeMultiplier)
                    snipeFlightStates[arrow.uniqueId] = SnipeFlightState(
                        arrow = arrow,
                        compensationPerTick = compensationPerTick,
                        remainingTicks = MAX_RANGE_COMPENSATION_TICKS
                    )

                    shooter.playSound(shooter.location, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.8f, 1.5f)
                }
            }
        }

        val bonusPower = compiledEffects.byType[WarriorBowPowerBoostHandler.EFFECT_TYPE]
            ?.sumOf { it.effect.getDoubleParam("power", 0.0).coerceAtLeast(0.0) }
            ?: 0.0
        if (bonusPower <= 0.0) {
            return
        }

        val actualPower = bow.getEnchantmentLevel(Enchantment.POWER).toDouble().coerceAtLeast(0.0)
        val effectivePower = actualPower + bonusPower
        val deltaDamage = enchantLevelBonusDamage(effectivePower) - enchantLevelBonusDamage(actualPower)
        if (deltaDamage <= 0.0) {
            return
        }

        arrow.persistentDataContainer.set(bowPowerDeltaKey, PersistentDataType.DOUBLE, deltaDamage)
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onArrowDamage(event: EntityDamageByEntityEvent) {
        val arrow = event.damager as? AbstractArrow ?: return
        val deltaDamage = arrow.persistentDataContainer.get(bowPowerDeltaKey, PersistentDataType.DOUBLE)
        if (deltaDamage != null && deltaDamage > 0.0) {
            event.damage += deltaDamage
        }

        val damageFactor = arrow.persistentDataContainer.get(snipeDamageFactorKey, PersistentDataType.DOUBLE)
        if (damageFactor != null && damageFactor > 0.0) {
            event.damage *= damageFactor
        }
    }

    @EventHandler
    fun onProjectileHit(event: ProjectileHitEvent) {
        val arrow = event.entity as? AbstractArrow ?: return
        snipeFlightStates.remove(arrow.uniqueId)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val playerUuid = event.player.uniqueId
        bowDrawStartedAtMillis.remove(playerUuid)
        extraChargeStates.remove(playerUuid)
    }

    private fun enchantLevelBonusDamage(level: Double): Double {
        if (level <= 0.0) {
            return 0.0
        }
        return level * 0.5 + 0.5
    }
}
