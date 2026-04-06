package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobRuntimeContext
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.block.data.Waterlogged
import org.bukkit.entity.Guardian
import org.bukkit.entity.Player
import kotlin.math.roundToLong
import kotlin.random.Random

class GuardianWaterDiveSplashAbility(
    override val id: String,
    private val jumpCooldownTicks: Long = 120L,
    private val horizontalSpeed: Double = 1.05,
    private val verticalSpeed: Double = 0.66,
    private val splashRadius: Double = 3.6,
    private val splashDamage: Double = 4.0,
    private val splashKnockback: Double = 0.9,
    private val splashVerticalBoost: Double = 0.28
) : MobAbility {

    data class Runtime(
        var cooldownTicks: Long = 0L,
        var splashArmed: Boolean = false,
        var hasLeftGroundAfterJump: Boolean = false,
        var searchPhaseOffsetSteps: Int = 0
    ) : MobAbilityRuntime

    override fun createRuntime(context: jp.awabi2048.cccontent.mob.MobSpawnContext): MobAbilityRuntime {
        return Runtime(searchPhaseOffsetSteps = Random.nextInt(0, SEARCH_PHASE_VARIANTS))
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val rt = runtime as? Runtime ?: return
        val guardian = context.entity as? Guardian ?: return

        if (rt.cooldownTicks > 0L) {
            rt.cooldownTicks = (rt.cooldownTicks - context.tickDelta).coerceAtLeast(0L)
        }

        if (rt.splashArmed) {
            if (!guardian.isOnGround) {
                rt.hasLeftGroundAfterJump = true
            }
            if (rt.hasLeftGroundAfterJump && (guardian.isOnGround || isLowerBodyInWater(guardian))) {
                triggerSplash(guardian)
                rt.splashArmed = false
                rt.hasLeftGroundAfterJump = false
                rt.cooldownTicks = jumpCooldownTicks
                return
            }
            if (guardian.isOnGround && !rt.hasLeftGroundAfterJump) {
                rt.splashArmed = false
                rt.hasLeftGroundAfterJump = false
            }
        }

        if (!context.isCombatActive()) return
        if (rt.cooldownTicks > 0L) return
        if (rt.splashArmed) return
        if (!guardian.isOnGround) return

        if (!MobAbilityUtils.shouldProcessSearchTick(context.activeMob.tickCount, context.loadSnapshot.searchIntervalMultiplier, rt.searchPhaseOffsetSteps)) {
            return
        }
        if (Random.nextDouble() < context.loadSnapshot.abilityExecutionSkipChance) {
            return
        }

        val target = MobAbilityUtils.resolveTarget(guardian) as? Player ?: return
        if (target.gameMode == GameMode.SPECTATOR || !target.isValid || target.isDead) return
        if (target.world.uid != guardian.world.uid) return

        val horizontal = target.location.toVector().subtract(guardian.location.toVector()).setY(0.0)
        if (horizontal.lengthSquared() < 0.0001) return

        guardian.target = target
        guardian.velocity = horizontal.normalize().multiply(horizontalSpeed).setY(verticalSpeed)
        guardian.world.playSound(guardian.location, Sound.ENTITY_DOLPHIN_JUMP, 0.95f, 0.9f)
        rt.splashArmed = true
        rt.hasLeftGroundAfterJump = false
        rt.cooldownTicks = (jumpCooldownTicks * context.loadSnapshot.abilityCooldownMultiplier).roundToLong().coerceAtLeast(jumpCooldownTicks)
    }

    private fun triggerSplash(guardian: Guardian) {
        val center = guardian.location.clone().add(0.0, 0.4, 0.0)
        val world = center.world ?: return
        val radiusSquared = splashRadius * splashRadius
        val landedInWater = isLowerBodyInWater(guardian)

        if (landedInWater) {
            world.spawnParticle(Particle.SPLASH, center, 60, 1.2, 0.3, 1.2, 0.12)
            world.spawnParticle(Particle.BUBBLE, center, 45, 1.1, 0.5, 1.1, 0.04)
            world.playSound(center, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1.0f, 0.85f)
        } else {
            world.spawnParticle(Particle.CLOUD, center, 26, 0.9, 0.15, 0.9, 0.03)
            world.playSound(center, Sound.ENTITY_GUARDIAN_HURT_LAND, 0.9f, 0.95f)
        }

        world.getNearbyEntities(center, splashRadius, splashRadius, splashRadius)
            .asSequence()
            .filterIsInstance<Player>()
            .filter { it.isValid && !it.isDead && it.gameMode != GameMode.SPECTATOR }
            .forEach { player ->
                if (player.location.distanceSquared(center) > radiusSquared) return@forEach
                player.damage(splashDamage, guardian)

                val horizontal = player.location.toVector().subtract(center.toVector()).setY(0.0)
                if (horizontal.lengthSquared() > 1.0e-6) {
                    player.velocity = player.velocity.add(horizontal.normalize().multiply(splashKnockback).setY(splashVerticalBoost))
                }
            }
    }

    private fun isLowerBodyInWater(guardian: Guardian): Boolean {
        val feetBlock = guardian.location.clone().add(0.0, 0.1, 0.0).block
        val waistBlock = guardian.location.clone().add(0.0, 0.6, 0.0).block
        return isWaterBlock(feetBlock.type, feetBlock.blockData is Waterlogged && (feetBlock.blockData as Waterlogged).isWaterlogged) ||
            isWaterBlock(waistBlock.type, waistBlock.blockData is Waterlogged && (waistBlock.blockData as Waterlogged).isWaterlogged)
    }

    private fun isWaterBlock(type: Material, isWaterlogged: Boolean): Boolean {
        return type == Material.WATER || type == Material.BUBBLE_COLUMN || isWaterlogged
    }

    private companion object {
        const val SEARCH_PHASE_VARIANTS = 16
    }
}
