package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobRuntimeContext
import jp.awabi2048.cccontent.mob.MobService
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.entity.Shulker
import org.bukkit.entity.ShulkerBullet
import kotlin.math.roundToLong
import kotlin.random.Random

class ShulkerBurstShotAbility(
    override val id: String,
    private val cooldownTicks: Long = 46L,
    private val burstCount: Int = 3,
    private val burstIntervalTicks: Long = 5L,
    private val bulletDamageMultiplier: Double = 0.7,
    private val levitationAmplifier: Int = 2,
    private val levitationDurationTicks: Int = 10
) : MobAbility {

    data class Runtime(
        var cooldownRemainingTicks: Long = 0L,
        var searchPhaseOffsetSteps: Int = 0
    ) : MobAbilityRuntime

    override fun createRuntime(context: jp.awabi2048.cccontent.mob.MobSpawnContext): MobAbilityRuntime {
        return Runtime(searchPhaseOffsetSteps = Random.nextInt(0, SEARCH_PHASE_VARIANTS))
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val shulker = context.entity as? Shulker ?: return
        val rt = runtime as? Runtime ?: return
        if (rt.cooldownRemainingTicks > 0L) {
            rt.cooldownRemainingTicks = (rt.cooldownRemainingTicks - context.tickDelta).coerceAtLeast(0L)
        }
        if (!context.isCombatActive()) {
            return
        }
        if (rt.cooldownRemainingTicks > 0L) {
            return
        }
        if (!MobAbilityUtils.shouldProcessSearchTick(
                context.activeMob.tickCount,
                context.loadSnapshot.searchIntervalMultiplier,
                rt.searchPhaseOffsetSteps,
                baseStepTicks = 1L
            )) {
            return
        }
        if (Random.nextDouble() < context.loadSnapshot.abilityExecutionSkipChance) {
            return
        }

        val target = MobAbilityUtils.resolveTarget(shulker) as? Player ?: return
        if (!target.isValid || target.isDead || target.world.uid != shulker.world.uid) {
            return
        }
        if (!shulker.hasLineOfSight(target)) {
            return
        }

        val mobService = MobService.getInstance(context.plugin) ?: return
        val finalDamage = (context.definition.attack * bulletDamageMultiplier).coerceAtLeast(0.0)

        repeat(burstCount.coerceAtLeast(1)) { index ->
            val delay = burstIntervalTicks.coerceAtLeast(1L) * index.toLong()
            org.bukkit.Bukkit.getScheduler().runTaskLater(context.plugin, Runnable {
                val shooter = org.bukkit.Bukkit.getEntity(shulker.uniqueId) as? Shulker ?: return@Runnable
                val victim = org.bukkit.Bukkit.getEntity(target.uniqueId) as? Player ?: return@Runnable
                if (!shooter.isValid || shooter.isDead || !victim.isValid || victim.isDead || shooter.world.uid != victim.world.uid) {
                    return@Runnable
                }

                val bullet = shooter.world.spawnEntity(
                    shooter.eyeLocation.clone().add(shooter.location.direction.clone().multiply(0.5)),
                    EntityType.SHULKER_BULLET
                ) as? ShulkerBullet ?: return@Runnable

                bullet.shooter = shooter
                bullet.target = victim
                val launchDirection = victim.eyeLocation.toVector().subtract(shooter.eyeLocation.toVector())
                val normalized = if (launchDirection.lengthSquared() > 0.0001) launchDirection.normalize() else shooter.location.direction
                bullet.velocity = normalized.multiply(0.5)

                mobService.markCustomProjectileDamage(bullet, finalDamage)
                mobService.markShulkerLevitationBullet(bullet, levitationAmplifier, levitationDurationTicks)
            }, delay)
        }

        shulker.world.playSound(shulker.location, org.bukkit.Sound.ENTITY_SHULKER_SHOOT, 1.0f, 1.25f)

        val baseCooldown = cooldownTicks.coerceAtLeast(1L)
        rt.cooldownRemainingTicks = (baseCooldown * context.loadSnapshot.abilityCooldownMultiplier).roundToLong().coerceAtLeast(baseCooldown)
    }

    private companion object {
        const val SEARCH_PHASE_VARIANTS = 16
    }
}
