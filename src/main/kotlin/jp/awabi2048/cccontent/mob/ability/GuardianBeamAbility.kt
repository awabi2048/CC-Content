package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobRuntimeContext
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Guardian
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

class GuardianBeamAbility(
    override val id: String,
    private val cooldownTicks: Long = 80L,
    private val chargeTicks: Long = 30L,
    private val minRange: Double = 2.5,
    private val maxRange: Double = 16.0,
    private val directDamageMultiplier: Double = 1.0,
    private val directBonusDamage: Double = 0.0,
    private val explosionRadius: Double = 0.0,
    private val directKnockback: Double = 1.6,
    private val splashKnockback: Double = 0.9,
    private val splashVerticalBoost: Double = 0.2,
    private val chargePulseIntervalTicks: Long = 0L,
    private val chargePulseDamage: Double = 0.0,
    private val chargePulseSelfHeal: Double = 0.0,
    private val chargeDebuffType: PotionEffectType? = null,
    private val chargeDebuffDurationTicks: Int = 0,
    private val chargeDebuffAmplifier: Int = 0,
    private val chargePulseSound: Sound? = null,
    private val chargePulseSoundVolume: Float = 1.0f,
    private val chargePulseSoundPitch: Float = 1.0f,
    private val chargeParticle: Particle = Particle.ELECTRIC_SPARK,
    private val impactParticle: Particle? = null,
    private val impactParticleCount: Int = 0,
    private val decorationIntervalTicks: Long = 0L,
    private val decorationEffect: ((Guardian) -> Unit)? = null,
    private val activationSound: Sound? = null,
    private val activationSoundVolume: Float = 1.0f,
    private val activationSoundPitch: Float = 1.0f,
    private val activationSoundOnlyFirst: Boolean = false,
    private val activationTargetParticles: ((Location) -> Unit)? = null,
    private val impactTargetParticles: ((Location) -> Unit)? = null
) : MobAbility {

    data class Runtime(
        var cooldownTicks: Long = 0L,
        var pulseCooldownTicks: Long = 0L,
        var currentTargetId: UUID? = null,
        var startedLaserTicks: Int = Int.MIN_VALUE,
        var chargeParticleTask: BukkitTask? = null,
        var decorationTask: BukkitTask? = null,
        var activationSoundPlayed: Boolean = false
    ) : MobAbilityRuntime

    override fun createRuntime(context: jp.awabi2048.cccontent.mob.MobSpawnContext): MobAbilityRuntime {
        val runtime = Runtime()
        val guardian = context.entity as? Guardian
        if (guardian != null && decorationIntervalTicks > 0L && decorationEffect != null) {
            startDecorationTask(context.plugin, guardian, runtime)
        }
        return runtime
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val abilityRuntime = runtime as? Runtime ?: return
        if (abilityRuntime.cooldownTicks > 0L) {
            abilityRuntime.cooldownTicks -= 10L
        }
        if (abilityRuntime.pulseCooldownTicks > 0L) {
            abilityRuntime.pulseCooldownTicks -= 10L
        }

        val guardian = context.entity as? Guardian ?: return
        if (!context.isCombatActive()) {
            reset(guardian, abilityRuntime)
            return
        }

        val target = MobAbilityUtils.resolveTarget(guardian) ?: run {
            if (guardian.hasLaser()) {
                reset(guardian, abilityRuntime)
            }
            return
        }

        if (!isTargetInRange(guardian, target)) {
            if (guardian.hasLaser()) {
                reset(guardian, abilityRuntime)
            }
            return
        }

        guardian.target = target

        if (!guardian.hasLaser()) {
            if (abilityRuntime.cooldownTicks > 0L) {
                return
            }
            startLaser(context.plugin, guardian, target, abilityRuntime)
            return
        }

        abilityRuntime.currentTargetId = target.uniqueId

        if (guardian.hasLaser()) {
            applyPulseEffects(guardian, target, abilityRuntime)
        }
    }

    override fun onAttack(context: jp.awabi2048.cccontent.mob.MobAttackContext, runtime: MobAbilityRuntime?) {
        val abilityRuntime = runtime as? Runtime ?: return
        val guardian = context.entity as? Guardian ?: return
        val target = context.target ?: return
        if (!guardian.hasLaser()) {
            return
        }
        if (guardian.laserTicks < guardian.laserDuration) {
            return
        }

        val blocked = target as? Player != null && isShieldBlockingSource(target as Player, guardian.location)
        if (blocked) {
            context.event.isCancelled = true
            context.event.damage = 0.0
            guardian.world.playSound(target.location, Sound.ITEM_SHIELD_BLOCK, 1.0f, 0.9f)
            finishBeam(guardian, abilityRuntime)
            return
        }

        context.event.damage = (context.event.damage * directDamageMultiplier + directBonusDamage).coerceAtLeast(0.0)

        val targetLocation = target.location.clone().add(0.0, 1.0, 0.0)
        val direction = target.eyeLocation.toVector().subtract(guardian.eyeLocation.toVector())
            .setY(0.0)
            .takeIf { it.lengthSquared() > 0.0001 }
            ?.normalize()
            ?: guardian.location.direction.clone().setY(0.0).normalize()
        if (directKnockback > 0.0) {
            target.velocity = target.velocity.add(direction.multiply(directKnockback).setY(splashVerticalBoost))
        }

        if (impactParticle != null && impactParticleCount > 0) {
            guardian.world.spawnParticle(impactParticle, targetLocation, impactParticleCount, 0.3, 0.3, 0.3, 0.02)
        }
        impactTargetParticles?.invoke(targetLocation)
        if (explosionRadius > 0.0) {
            guardian.world.playSound(targetLocation, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f)
            applySplashKnockback(guardian, target, targetLocation)
        }

        finishBeam(guardian, abilityRuntime)
    }

    private fun startLaser(plugin: Plugin, guardian: Guardian, target: LivingEntity, runtime: Runtime) {
        guardian.target = target
        guardian.setLaser(true)
        val duration = guardian.laserDuration
        val startTicks = (duration - chargeTicks.coerceAtLeast(1L).toInt()).coerceAtLeast(-10)
        guardian.laserTicks = startTicks
        runtime.currentTargetId = target.uniqueId
        runtime.startedLaserTicks = startTicks
        runtime.pulseCooldownTicks = chargePulseIntervalTicks.coerceAtLeast(10L)
        startChargeParticleTask(plugin, guardian, runtime)
        activationSound?.let { sound ->
            if (!activationSoundOnlyFirst || !runtime.activationSoundPlayed) {
                guardian.world.playSound(target.location, sound, activationSoundVolume, activationSoundPitch)
                runtime.activationSoundPlayed = true
            }
        }
        activationTargetParticles?.invoke(target.location.clone().add(0.0, 0.9, 0.0))
    }

    private fun applyPulseEffects(guardian: Guardian, target: LivingEntity, runtime: Runtime) {
        if (chargePulseIntervalTicks <= 0L && chargeDebuffType == null) {
            return
        }

        if (runtime.pulseCooldownTicks > 0L) {
            return
        }

        runtime.pulseCooldownTicks = chargePulseIntervalTicks.coerceAtLeast(10L)

        if (target is Player && isShieldBlockingSource(target, guardian.location)) {
            guardian.world.playSound(target.location, Sound.ITEM_SHIELD_BLOCK, 0.9f, 1.0f)
            return
        }

        var appliedEffect = false
        if (chargePulseDamage > 0.0) {
            target.damage(chargePulseDamage, guardian)
            appliedEffect = true
        }

        if (chargeDebuffType != null && chargeDebuffDurationTicks > 0) {
            target.addPotionEffect(
                PotionEffect(
                    chargeDebuffType,
                    chargeDebuffDurationTicks,
                    chargeDebuffAmplifier.coerceAtLeast(0),
                    false,
                    true,
                    true
                )
            )
            appliedEffect = true
        }

        if (chargePulseSelfHeal > 0.0) {
            val maxHealth = guardian.getAttribute(Attribute.MAX_HEALTH)?.value ?: guardian.maxHealth
            guardian.health = (guardian.health + chargePulseSelfHeal).coerceAtMost(maxHealth)
        }

        if (appliedEffect) {
            chargePulseSound?.let { sound ->
                guardian.world.playSound(target.location, sound, chargePulseSoundVolume, chargePulseSoundPitch)
            }
        }

    }

    private fun applySplashKnockback(guardian: Guardian, directTarget: LivingEntity, center: Location) {
        val radiusSquared = explosionRadius * explosionRadius
        center.world?.getNearbyEntities(center, explosionRadius, explosionRadius, explosionRadius)
            ?.asSequence()
            ?.filterIsInstance<LivingEntity>()
            ?.filter { it.isValid && !it.isDead && it.uniqueId != guardian.uniqueId && it.uniqueId != directTarget.uniqueId }
            ?.forEach { entity ->
                if (entity.location.distanceSquared(center) > radiusSquared) {
                    return@forEach
                }
                val radial = entity.location.toVector().subtract(center.toVector()).setY(0.0)
                if (radial.lengthSquared() < 0.0001) {
                    return@forEach
                }
                entity.velocity = entity.velocity.add(radial.normalize().multiply(splashKnockback).setY(splashVerticalBoost))
            }
    }

    private fun finishBeam(guardian: Guardian, runtime: Runtime) {
        guardian.setLaser(false)
        guardian.target = null
        runtime.currentTargetId = null
        runtime.startedLaserTicks = Int.MIN_VALUE
        runtime.pulseCooldownTicks = 0L
        runtime.chargeParticleTask?.cancel()
        runtime.chargeParticleTask = null
        runtime.cooldownTicks = cooldownTicks
    }

    private fun reset(guardian: Guardian, runtime: Runtime) {
        guardian.setLaser(false)
        guardian.target = null
        runtime.currentTargetId = null
        runtime.startedLaserTicks = Int.MIN_VALUE
        runtime.pulseCooldownTicks = 0L
        runtime.chargeParticleTask?.cancel()
        runtime.chargeParticleTask = null
    }

    private fun startChargeParticleTask(plugin: Plugin, guardian: Guardian, runtime: Runtime) {
        runtime.chargeParticleTask?.cancel()
        runtime.chargeParticleTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            if (!guardian.isValid || guardian.isDead || !guardian.hasLaser() || guardian.laserTicks >= guardian.laserDuration) {
                runtime.chargeParticleTask?.cancel()
                runtime.chargeParticleTask = null
                return@Runnable
            }
            guardian.world.spawnParticle(
                chargeParticle,
                guardian.location.clone().add(0.0, 1.0, 0.0),
                2,
                0.15,
                0.15,
                0.15,
                0.01
            )
        }, 0L, 10L)
    }

    private fun startDecorationTask(plugin: Plugin, guardian: Guardian, runtime: Runtime) {
        runtime.decorationTask?.cancel()
        runtime.decorationTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            if (!guardian.isValid || guardian.isDead) {
                runtime.decorationTask?.cancel()
                runtime.decorationTask = null
                return@Runnable
            }
            decorationEffect?.invoke(guardian)
        }, 0L, decorationIntervalTicks.coerceAtLeast(10L))
    }

    private fun isChargingPhase(guardian: Guardian): Boolean {
        return guardian.hasLaser() && guardian.laserTicks < guardian.laserDuration
    }

    private fun isTargetInRange(guardian: Guardian, target: LivingEntity): Boolean {
        val distance = guardian.location.distance(target.location)
        return distance in minRange..maxRange
    }

    private fun isShieldBlockingSource(player: Player, sourceLocation: Location): Boolean {
        if (!player.isBlocking) {
            return false
        }

        val facing = player.location.direction.clone().setY(0.0)
        if (facing.lengthSquared() < 0.0001) {
            return true
        }

        val toSource = sourceLocation.toVector().subtract(player.location.toVector()).setY(0.0)
        if (toSource.lengthSquared() < 0.0001) {
            return true
        }

        return facing.normalize().dot(toSource.normalize()) >= 0.2
    }

    companion object {
        fun dustTransitionEffect(inner: Color, outer: Color, size: Float = 1.2f): (Location) -> Unit = { location ->
            val options = Particle.DustTransition(inner, outer, size)
            location.world.spawnParticle(Particle.DUST_COLOR_TRANSITION, location, 18, 0.45, 0.45, 0.45, 0.0, options)
        }

        fun warpedSporeEffect(): (Location) -> Unit = { location ->
            location.world.spawnParticle(Particle.WARPED_SPORE, location, 100, 0.55, 0.45, 0.55, 0.02)
        }

        fun centeredBoxDecorationEffect(
            inner: Color,
            outer: Color,
            baseRadiusX: Double,
            baseRadiusY: Double,
            baseRadiusZ: Double,
            particleCount: Int = 10,
            size: Float = 1.15f
        ): (Guardian) -> Unit = { guardian ->
            val scale = guardian.getAttribute(Attribute.SCALE)?.value ?: 1.0
            val center = guardian.location.clone().add(0.0, guardian.height * 0.5, 0.0)
            val options = Particle.DustTransition(inner, outer, size)
            guardian.world.spawnParticle(
                Particle.DUST_COLOR_TRANSITION,
                center,
                particleCount,
                baseRadiusX * scale,
                baseRadiusY * scale,
                baseRadiusZ * scale,
                0.0,
                options
            )
        }
    }
}
