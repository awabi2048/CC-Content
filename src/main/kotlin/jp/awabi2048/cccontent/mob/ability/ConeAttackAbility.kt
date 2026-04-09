package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobRuntimeContext
import jp.awabi2048.cccontent.mob.MobService
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.tan
import kotlin.random.Random

class ConeAttackAbility(
    override val id: String,
    private val range: Double = 4.0,
    private val coneHalfAngleDegrees: Double = 30.0,
    private val cooldownTicks: Long = 20L,
    private val damageMultiplier: Double = 1.0,
    private val bonusDamage: Double = 0.0,
    private val knockback: Double = 0.6,
    private val verticalBoost: Double = 0.15,
    private val chargeUpTicks: Long = 0L
) : MobAbility {

    data class Runtime(
        var cooldownRemainingTicks: Long = 0L,
        var chargeRemainingTicks: Long = 0L,
        var searchPhaseOffsetSteps: Int = 0
    ) : MobAbilityRuntime

    override fun tickIntervalTicks(): Long = 1L

    override fun createRuntime(context: jp.awabi2048.cccontent.mob.MobSpawnContext): MobAbilityRuntime {
        return Runtime(searchPhaseOffsetSteps = Random.nextInt(0, SEARCH_PHASE_VARIANTS))
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val rt = runtime as? Runtime ?: return
        val attacker = context.entity

        if (rt.cooldownRemainingTicks > 0L) {
            rt.cooldownRemainingTicks = (rt.cooldownRemainingTicks - context.tickDelta).coerceAtLeast(0L)
        }

        if (!context.isCombatActive()) {
            rt.chargeRemainingTicks = 0L
            return
        }

        if (rt.chargeRemainingTicks > 0L) {
            rt.chargeRemainingTicks = (rt.chargeRemainingTicks - context.tickDelta).coerceAtLeast(0L)
            if (rt.chargeRemainingTicks <= 0L) {
                executeAttack(context, rt)
            }
            return
        }

        if (rt.cooldownRemainingTicks > 0L) return

        if (!MobAbilityUtils.shouldProcessSearchTick(
                context.activeMob.tickCount,
                context.loadSnapshot.searchIntervalMultiplier,
                rt.searchPhaseOffsetSteps,
                baseStepTicks = 1L
            )) return
        if (Random.nextDouble() < context.loadSnapshot.abilityExecutionSkipChance) return

        val target = MobAbilityUtils.resolveTarget(attacker) as? Player ?: return
        if (target.gameMode == GameMode.SPECTATOR) return
        val distance = attacker.location.distance(target.location)
        if (distance > range + 1.0) return

        MobAbilityUtils.faceTowards(attacker, target)

        if (chargeUpTicks > 0L) {
            rt.chargeRemainingTicks = chargeUpTicks
            attacker.world.playSound(attacker.location, Sound.ENTITY_WARDEN_ANGRY, 0.5f, 1.8f)
        } else {
            executeAttack(context, rt)
        }
    }

    private fun executeAttack(context: MobRuntimeContext, rt: Runtime) {
        val attacker = context.entity
        val forward = attacker.location.direction.clone().normalize()
        val origin = attacker.eyeLocation
        val halfAngleRad = Math.toRadians(coneHalfAngleDegrees)
        val hitEntities = mutableListOf<LivingEntity>()

        attacker.world.getNearbyEntities(
            attacker.location,
            range + 1.0, range + 1.0, range + 1.0
        ).asSequence()
            .filterIsInstance<Player>()
            .filter { it.isValid && !it.isDead && it.gameMode != GameMode.SPECTATOR }
            .forEach { candidate ->
                val toCandidate = candidate.location.toVector().subtract(origin.toVector())
                val dist = toCandidate.length()
                if (dist < 0.5 || dist > range) return@forEach

                val dir = toCandidate.clone().normalize()
                val dot = forward.dot(dir)
                val angle = kotlin.math.acos(dot.coerceIn(-1.0, 1.0))
                if (angle <= halfAngleRad) {
                    hitEntities.add(candidate)
                }
            }

        renderConeEffect(attacker, forward, halfAngleRad)

        val damage = (context.definition.attack * damageMultiplier + bonusDamage).coerceAtLeast(0.0)
        hitEntities.forEach { target ->
            if (target is Player && isShieldBlocking(target, attacker.location)) {
                target.world.playSound(target.location, Sound.ITEM_SHIELD_BLOCK, 1.0f, 0.9f)
                return@forEach
            }

            if (damage > 0.0) {
                MobService.getInstance(context.plugin)?.issueDirectDamagePermit(attacker.uniqueId, target.uniqueId)
                target.damage(damage, attacker)
            }

            if (knockback > 0.0) {
                val push = target.location.toVector().subtract(attacker.location.toVector()).setY(0.0)
                if (push.lengthSquared() > 0.0001) {
                    target.velocity = target.velocity.add(push.normalize().multiply(knockback).setY(verticalBoost))
                }
            }

            target.world.playSound(target.location, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.2f)
        }

        if (hitEntities.isNotEmpty()) {
            attacker.world.playSound(attacker.location, Sound.ENTITY_WARDEN_ATTACK_IMPACT, 0.7f, 1.5f)
        }

        val baseCooldown = cooldownTicks.coerceAtLeast(1L)
        rt.cooldownRemainingTicks =
            (baseCooldown * context.loadSnapshot.abilityCooldownMultiplier).roundToLong().coerceAtLeast(baseCooldown)
    }

    private fun renderConeEffect(attacker: LivingEntity, forward: Vector, halfAngleRad: Double) {
        val origin = attacker.eyeLocation
        val world = attacker.world
        val normalizedForward = forward.clone().normalize()
        val worldUp = Vector(0.0, 1.0, 0.0)
        val right = normalizedForward.clone().crossProduct(worldUp).let {
            if (it.lengthSquared() > 0.0001) it.normalize() else Vector(1.0, 0.0, 0.0)
        }
        val up = right.clone().crossProduct(normalizedForward).normalize()
        // 円錐内部の粒子密度は見た目確認しながら再調整前提。
        val depthSteps = 8
        val samplesPerStep = 10

        for (depthIndex in 1..depthSteps) {
            val dist = range * depthIndex.toDouble() / depthSteps.toDouble()
            val center = origin.clone().add(normalizedForward.clone().multiply(dist))
            val coneRadius = tan(halfAngleRad) * dist

            repeat(samplesPerStep) {
                val radiusFactor = kotlin.math.sqrt(Random.nextDouble()) * coneRadius
                val theta = Random.nextDouble(0.0, Math.PI * 2.0)
                val offset = right.clone().multiply(cos(theta) * radiusFactor)
                    .add(up.clone().multiply(sin(theta) * radiusFactor))
                val point = center.clone().add(offset)
                world.spawnParticle(Particle.DRAGON_BREATH, point, 1, 0.0, 0.0, 0.0, 0.0)
            }
        }
    }

    private fun isShieldBlocking(player: Player, sourceLocation: Location): Boolean {
        if (!player.isBlocking) return false
        val facing = player.location.direction.clone().setY(0.0)
        if (facing.lengthSquared() < 0.0001) return true
        val toSource = sourceLocation.toVector().subtract(player.location.toVector()).setY(0.0)
        if (toSource.lengthSquared() < 0.0001) return true
        return facing.normalize().dot(toSource.normalize()) >= 0.2
    }

    private companion object {
        const val SEARCH_PHASE_VARIANTS = 16
    }
}
