package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobRuntimeContext
import org.bukkit.Particle
import org.bukkit.attribute.Attribute
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import kotlin.math.roundToLong
import kotlin.random.Random

class GrudgeAuraAbility(
    override val id: String,
    private val radius: Double = DEFAULT_RADIUS,
    private val debuffIntervalTicks: Long = DEFAULT_DEBUFF_INTERVAL_TICKS,
    private val debuffDurationSlownessTicks: Int = DEFAULT_SLOWNESS_DURATION_TICKS,
    private val debuffAmplifierSlowness: Int = DEFAULT_SLOWNESS_AMPLIFIER,
    private val debuffDurationBlindnessTicks: Int = DEFAULT_BLINDNESS_DURATION_TICKS,
    private val damageIntervalTicks: Long = DEFAULT_DAMAGE_INTERVAL_TICKS,
    private val damageAmount: Double = DEFAULT_DAMAGE_AMOUNT,
    private val searchPhaseOffsetSteps: Int = 0
) : MobAbility {

    data class Runtime(
        var debuffCooldownTicks: Long = 0L,
        var damageCooldownTicks: Long = 0L,
        var searchOffset: Int = 0
    ) : MobAbilityRuntime

    override fun createRuntime(context: jp.awabi2048.cccontent.mob.MobSpawnContext): MobAbilityRuntime {
        return Runtime(searchOffset = searchPhaseOffsetSteps.coerceAtLeast(Random.nextInt(0, SEARCH_PHASE_VARIANTS)))
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val rt = runtime as? Runtime ?: return

        if (rt.debuffCooldownTicks > 0L) rt.debuffCooldownTicks -= 10L
        if (rt.damageCooldownTicks > 0L) rt.damageCooldownTicks -= 10L

        if (!context.isCombatActive()) return

        if (!MobAbilityUtils.shouldProcessSearchTick(context.activeMob.tickCount, context.loadSnapshot.searchIntervalMultiplier, rt.searchOffset)) return
        if (Random.nextDouble() < context.loadSnapshot.abilityExecutionSkipChance) return

        val entity = context.entity
        val nearbyPlayers = entity.getNearbyEntities(radius, radius, radius)
            .asSequence()
            .filterIsInstance<Player>()
            .filter { it.isValid && !it.isDead && entity.hasLineOfSight(it) }
            .toList()

        if (nearbyPlayers.isEmpty()) return

        spawnParticles(entity)

        if (rt.debuffCooldownTicks <= 0L) {
            applyDebuffs(nearbyPlayers)
            rt.debuffCooldownTicks = (debuffIntervalTicks * context.loadSnapshot.abilityCooldownMultiplier).roundToLong().coerceAtLeast(debuffIntervalTicks)
        }

        if (rt.damageCooldownTicks <= 0L) {
            applyDamage(entity, nearbyPlayers)
            rt.damageCooldownTicks = (damageIntervalTicks * context.loadSnapshot.abilityCooldownMultiplier).roundToLong().coerceAtLeast(damageIntervalTicks)
        }
    }

    private fun spawnParticles(entity: LivingEntity) {
        val loc = entity.location.clone().add(0.0, 1.0, 0.0)
        val world = loc.world ?: return
        for (i in 0 until 8) {
            val angle = (i / 8.0) * Math.PI * 2
            val x = Math.cos(angle) * radius * 0.9
            val z = Math.sin(angle) * radius * 0.9
            val particleLoc = loc.clone().add(x, 0.0, z)
            world.spawnParticle(Particle.SQUID_INK, particleLoc, 1, 0.15, 0.3, 0.15, 0.0)
            world.spawnParticle(Particle.DRIPPING_OBSIDIAN_TEAR, particleLoc, 1, 0.15, 0.3, 0.15, 0.0)
        }
    }

    private fun applyDebuffs(players: List<Player>) {
        val slowness = PotionEffect(PotionEffectType.SLOWNESS, debuffDurationSlownessTicks, debuffAmplifierSlowness, false, true, true)
        val blindness = PotionEffect(PotionEffectType.BLINDNESS, debuffDurationBlindnessTicks, 0, false, true, true)
        players.forEach { player ->
            player.addPotionEffect(slowness)
            player.addPotionEffect(blindness)
        }
    }

    private fun applyDamage(entity: LivingEntity, players: List<Player>) {
        val dmg = damageAmount.coerceAtLeast(0.0)
        if (dmg <= 0.0) return
        players.forEach { player ->
            player.damage(dmg, entity)
        }
    }

    companion object {
        const val DEFAULT_RADIUS = 5.0
        const val DEFAULT_DEBUFF_INTERVAL_TICKS = 120L
        const val DEFAULT_SLOWNESS_DURATION_TICKS = 100
        const val DEFAULT_SLOWNESS_AMPLIFIER = 1
        const val DEFAULT_BLINDNESS_DURATION_TICKS = 60
        const val DEFAULT_DAMAGE_INTERVAL_TICKS = 160L
        const val DEFAULT_DAMAGE_AMOUNT = 3.0
        private const val SEARCH_PHASE_VARIANTS = 16
    }
}
