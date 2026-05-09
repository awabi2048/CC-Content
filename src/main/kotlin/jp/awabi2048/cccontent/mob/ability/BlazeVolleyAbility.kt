@file:Suppress("USELESS_IS_CHECK")

package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobRuntimeContext
import jp.awabi2048.cccontent.mob.MobService
import org.bukkit.Bukkit
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Fireball
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Projectile
import org.bukkit.entity.SmallFireball
import org.bukkit.util.Vector
import kotlin.math.roundToLong
import kotlin.random.Random

class BlazeVolleyAbility(
    override val id: String,
    private val cooldownTicks: Long = 70L,
    private val minRange: Double = 2.0,
    private val maxRange: Double = 24.0,
    private val projectileKind: ProjectileKind = ProjectileKind.SMALL_FIREBALL,
    private val projectileSpeed: Double = 0.95,
    private val inaccuracy: Double = 0.02,
    private val burstCount: Int = 1,
    private val burstIntervalTicks: Long = 4L,
    private val recoilPerShot: Double = 0.0,
    private val damageMultiplier: Double = 1.0,
    private val requireLineOfSight: Boolean = true
) : MobAbility {

    override fun tickIntervalTicks(): Long = 1L

    enum class ProjectileKind {
        SMALL_FIREBALL,
        FIREBALL
    }

    data class Runtime(
        var cooldownTicks: Long = 0L,
        var searchPhaseOffsetSteps: Int = 0,
        var burstRemainingShots: Int = 0,
        var nextBurstShotTick: Long = 0L,
        var burstTargetId: java.util.UUID? = null
    ) : MobAbilityRuntime

    override fun createRuntime(context: jp.awabi2048.cccontent.mob.MobSpawnContext): MobAbilityRuntime {
        return Runtime(searchPhaseOffsetSteps = Random.nextInt(0, SEARCH_PHASE_VARIANTS))
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val rt = runtime as? Runtime ?: return
        if (rt.cooldownTicks > 0L) {
            rt.cooldownTicks -= 1L
        }

        if (!context.isCombatActive()) {
            rt.burstRemainingShots = 0
            rt.burstTargetId = null
            return
        }

        if (rt.burstRemainingShots > 0) {
            processBurst(context, rt)
            return
        }

        if (rt.cooldownTicks > 0L) {
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

        val shooter = context.entity
        val target = MobAbilityUtils.resolveTarget(shooter) ?: return
        val distance = shooter.location.distance(target.location)
        if (distance < minRange || distance > maxRange) {
            return
        }
        if (requireLineOfSight && !shooter.hasLineOfSight(target)) {
            return
        }

        rt.burstRemainingShots = burstCount.coerceAtLeast(1)
        rt.nextBurstShotTick = Bukkit.getCurrentTick().toLong()
        rt.burstTargetId = target.uniqueId
        processBurst(context, rt)
    }

    private fun processBurst(context: MobRuntimeContext, runtime: Runtime) {
        val nowTick = Bukkit.getCurrentTick().toLong()
        if (nowTick < runtime.nextBurstShotTick) {
            return
        }

        val shooter = context.entity
        val target = resolveBurstTarget(shooter, runtime)
        if (target == null || (requireLineOfSight && !shooter.hasLineOfSight(target))) {
            finishBurst(context, runtime)
            return
        }

        MobAbilityUtils.faceTowards(shooter, target)
        launchProjectile(context, shooter, target)
        applyRecoil(shooter, target)

        runtime.burstRemainingShots -= 1
        if (runtime.burstRemainingShots <= 0) {
            finishBurst(context, runtime)
            return
        }

        runtime.nextBurstShotTick = nowTick + burstIntervalTicks.coerceAtLeast(1L)
    }

    private fun resolveBurstTarget(shooter: LivingEntity, runtime: Runtime): LivingEntity? {
        val targetId = runtime.burstTargetId
        if (targetId != null) {
            val found = Bukkit.getEntity(targetId) as? LivingEntity
            if (found != null && found.isValid && !found.isDead && found.world.uid == shooter.world.uid) {
                return found
            }
        }
        return MobAbilityUtils.resolveTarget(shooter)
    }

    private fun finishBurst(context: MobRuntimeContext, runtime: Runtime) {
        runtime.burstRemainingShots = 0
        runtime.burstTargetId = null
        val baseCooldown = cooldownTicks.coerceAtLeast(1L)
        runtime.cooldownTicks =
            (baseCooldown * context.loadSnapshot.abilityCooldownMultiplier).roundToLong().coerceAtLeast(baseCooldown)
    }

    private fun launchProjectile(context: MobRuntimeContext, shooter: LivingEntity, target: LivingEntity) {
        val source = shooter.eyeLocation
        val predictedTarget = target.eyeLocation.add(target.velocity.clone().multiply(0.32))
        val direction = predictedTarget.toVector().subtract(source.toVector())
        if (direction.lengthSquared() < 0.0001) {
            return
        }
        direction.x += Random.nextDouble(-inaccuracy, inaccuracy)
        direction.y += Random.nextDouble(-inaccuracy * 0.7, inaccuracy * 0.7)
        direction.z += Random.nextDouble(-inaccuracy, inaccuracy)

        MobService.getInstance(context.plugin)?.issueManagedProjectilePermit(shooter.uniqueId)
        val projectile = when (projectileKind) {
            ProjectileKind.SMALL_FIREBALL -> shooter.launchProjectile(SmallFireball::class.java)
            ProjectileKind.FIREBALL -> shooter.launchProjectile(Fireball::class.java)
        }
        projectile.shooter = shooter
        projectile.velocity = direction.normalize().multiply(projectileSpeed)
        if (projectile is Fireball) {
            projectile.yield = 0f
            projectile.setIsIncendiary(false)
        }

        val baseDamage = context.definition.attack.coerceAtLeast(0.0)
        val finalDamage = (baseDamage * damageMultiplier).coerceAtLeast(0.0)
        MobService.getInstance(context.plugin)?.markCustomProjectileDamage(projectile, finalDamage)
        MobService.getInstance(context.plugin)?.recordProjectileLaunch(context.sessionKey)

        shooter.world.spawnParticle(Particle.FLAME, source, 12, 0.18, 0.12, 0.18, 0.0)
        shooter.world.playSound(shooter.location, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 1.0f)
    }

    private fun applyRecoil(shooter: LivingEntity, target: LivingEntity) {
        if (recoilPerShot <= 0.0) {
            return
        }
        val recoilDirection = shooter.location.toVector().subtract(target.location.toVector()).setY(0.0)
        if (recoilDirection.lengthSquared() < 0.0001) {
            return
        }
        val recoilVelocity = recoilDirection.normalize().multiply(recoilPerShot)
        shooter.velocity = shooter.velocity.add(Vector(recoilVelocity.x, 0.0, recoilVelocity.z))
    }

    private companion object {
        const val SEARCH_PHASE_VARIANTS = 16
    }
}
