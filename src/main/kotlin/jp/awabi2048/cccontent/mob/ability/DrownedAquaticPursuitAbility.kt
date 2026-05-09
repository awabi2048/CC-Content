@file:Suppress("DEPRECATION")

package jp.awabi2048.cccontent.mob.ability

import com.destroystokyo.paper.entity.ai.Goal
import com.destroystokyo.paper.entity.ai.GoalKey
import com.destroystokyo.paper.entity.ai.GoalType
import com.destroystokyo.paper.entity.ai.VanillaGoal
import jp.awabi2048.cccontent.mob.MobDeathContext
import jp.awabi2048.cccontent.mob.MobAttackContext
import jp.awabi2048.cccontent.mob.MobRuntimeContext
import jp.awabi2048.cccontent.mob.MobSpawnContext
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.block.data.Waterlogged
import org.bukkit.entity.Drowned
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import java.util.EnumSet
import java.util.UUID

class DrownedAquaticPursuitAbility(
    override val id: String,
    private val waterMovementEfficiencyBonus: Double = DEFAULT_WATER_MOVEMENT_EFFICIENCY_BONUS,
    private val waterLeapPrepareTicks: Long = DEFAULT_WATER_LEAP_PREPARE_TICKS,
    private val waterLeapCooldownTicks: Long = DEFAULT_WATER_LEAP_COOLDOWN_TICKS,
    private val waterLeapHorizontalSpeed: Double = DEFAULT_WATER_LEAP_HORIZONTAL_SPEED,
    private val waterLeapVerticalSpeed: Double = DEFAULT_WATER_LEAP_VERTICAL_SPEED,
    private val meleeReachMultiplier: Double = 1.0
) : MobAbility {

    data class Runtime(
        var groundAttackCooldownTicks: Long = 0L,
        var lastNaturalMeleeTick: Long = Long.MIN_VALUE,
        var waterStreakTicks: Long = 0L,
        var leapCooldownTicks: Long = 0L,
        var wasLowerBodyInWater: Boolean = false
    ) : MobAbilityRuntime

    override fun createRuntime(context: MobSpawnContext): MobAbilityRuntime {
        return Runtime(
            groundAttackCooldownTicks = 0L,
            lastNaturalMeleeTick = Long.MIN_VALUE
        )
    }

    override fun onSpawn(context: MobSpawnContext, runtime: MobAbilityRuntime?) {
        val entity = context.entity
        removeWaterSpeedModifier(entity)
        applyWaterSpeedModifier(entity)
        configureAttackGoals(context)
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val rt = runtime as? Runtime ?: return
        val entity = context.entity

        if (rt.groundAttackCooldownTicks > 0L) {
            rt.groundAttackCooldownTicks = (rt.groundAttackCooldownTicks - context.tickDelta).coerceAtLeast(0L)
        }
        if (rt.leapCooldownTicks > 0L) {
            rt.leapCooldownTicks = (rt.leapCooldownTicks - context.tickDelta).coerceAtLeast(0L)
        }

        val lowerBodyInWater = isLowerBodyInWater(entity)
        if (lowerBodyInWater && !rt.wasLowerBodyInWater) {
            rt.leapCooldownTicks = 0L
        }

        if (lowerBodyInWater) {
            rt.waterStreakTicks = (rt.waterStreakTicks + context.tickDelta).coerceAtMost(1000L)
            tryWaterExitLeap(context, rt)
        } else {
            rt.waterStreakTicks = 0L
        }

        if (!lowerBodyInWater) {
            tryGroundFallbackMeleeAttack(context, rt)
        }

        rt.wasLowerBodyInWater = lowerBodyInWater
    }

    override fun onDeath(context: MobDeathContext, runtime: MobAbilityRuntime?) {
        removeWaterSpeedModifier(context.entity)
    }

    override fun onAttack(context: MobAttackContext, runtime: MobAbilityRuntime?) {
        val rt = runtime as? Runtime ?: return
        val damager = context.event.damager as? LivingEntity ?: return
        if (damager.uniqueId != context.entity.uniqueId) {
            return
        }
        rt.lastNaturalMeleeTick = context.activeMob.tickCount
    }

    private fun applyWaterSpeedModifier(entity: LivingEntity) {
        val attribute = entity.getAttribute(Attribute.WATER_MOVEMENT_EFFICIENCY) ?: return
        val exists = attribute.modifiers.any { it.uniqueId == WATER_SPEED_MODIFIER_UUID }
        if (exists) {
            return
        }

        attribute.addModifier(
            AttributeModifier(
                WATER_SPEED_MODIFIER_UUID,
                WATER_SPEED_MODIFIER_NAME,
                waterMovementEfficiencyBonus,
                AttributeModifier.Operation.ADD_NUMBER
            )
        )
    }

    private fun removeWaterSpeedModifier(entity: LivingEntity) {
        val attribute = entity.getAttribute(Attribute.WATER_MOVEMENT_EFFICIENCY) ?: return
        val modifier = attribute.modifiers.firstOrNull { it.uniqueId == WATER_SPEED_MODIFIER_UUID } ?: return
        attribute.removeModifier(modifier)
    }

    private fun isLowerBodyInWater(entity: LivingEntity): Boolean {
        val feetBlock = entity.location.clone().add(0.0, 0.1, 0.0).block
        val waistBlock = entity.location.clone().add(0.0, 0.6, 0.0).block
        return isWaterBlock(feetBlock.type, feetBlock.blockData is Waterlogged && (feetBlock.blockData as Waterlogged).isWaterlogged) ||
            isWaterBlock(waistBlock.type, waistBlock.blockData is Waterlogged && (waistBlock.blockData as Waterlogged).isWaterlogged)
    }

    private fun isWaterBlock(type: Material, isWaterlogged: Boolean): Boolean {
        return type == Material.WATER || type == Material.BUBBLE_COLUMN || isWaterlogged
    }

    private fun configureAttackGoals(context: MobSpawnContext) {
        val drowned = context.entity as? Drowned ?: return
        val mobGoals = Bukkit.getMobGoals()

        mobGoals.removeGoal(drowned, VanillaGoal.DROWNED_TRIDENT_ATTACK)

        if (mobGoals.hasGoal(drowned, VanillaGoal.DROWNED_ATTACK)) {
            return
        }

        val meleeKey = GoalKey.of(Drowned::class.java, NamespacedKey(context.plugin, "drowned_force_melee_attack"))
        if (mobGoals.hasGoal(drowned, meleeKey)) {
            return
        }

        val reachSquared = DEFAULT_ATTACK_REACH_SQUARED * meleeReachMultiplier.coerceAtLeast(0.1).let { it * it }
        mobGoals.addGoal(drowned, 2, DrownedForceMeleeAttackGoal(drowned, meleeKey, reachSquared))
    }

    private fun tryGroundFallbackMeleeAttack(context: MobRuntimeContext, runtime: Runtime) {
        val drowned = context.entity as? Drowned ?: return
        if (drowned.isInWater || drowned.isInRain || drowned.isInBubbleColumn) {
            return
        }
        if (runtime.groundAttackCooldownTicks > 0L) {
            return
        }
        if (context.activeMob.tickCount - runtime.lastNaturalMeleeTick <= NATURAL_ATTACK_SUPPRESS_TICKS) {
            return
        }

        val target = drowned.target as? Player ?: return
        if (!target.isValid || target.isDead || target.gameMode == GameMode.SPECTATOR) {
            return
        }
        if (target.world.uid != drowned.world.uid) {
            return
        }
        if (!drowned.hasLineOfSight(target)) {
            return
        }
        val distanceSquared = drowned.location.distanceSquared(target.location)
        val groundReachSquared = GROUND_ATTACK_REACH_SQUARED * meleeReachMultiplier.coerceAtLeast(0.1).let { it * it }
        if (distanceSquared > groundReachSquared) {
            return
        }

        target.damage(context.definition.attack.coerceAtLeast(0.0), drowned)
        runtime.groundAttackCooldownTicks = GROUND_ATTACK_COOLDOWN_TICKS
    }

    private fun tryWaterExitLeap(context: MobRuntimeContext, runtime: Runtime) {
        val drowned = context.entity as? Drowned ?: return
        if (runtime.leapCooldownTicks > 0L) return
        if (runtime.waterStreakTicks < waterLeapPrepareTicks.coerceAtLeast(1L)) return

        val target = drowned.target as? Player ?: return
        if (!target.isValid || target.isDead || target.gameMode == GameMode.SPECTATOR) {
            return
        }
        if (target.world.uid != drowned.world.uid) {
            return
        }
        if (!drowned.hasLineOfSight(target)) {
            return
        }

        val distanceSquared = drowned.location.distanceSquared(target.location)
        if (distanceSquared > WATER_LEAP_MAX_RANGE_SQUARED || distanceSquared < WATER_LEAP_MIN_RANGE_SQUARED) {
            return
        }

        val horizontal = target.location.toVector().subtract(drowned.location.toVector()).setY(0.0)
        if (horizontal.lengthSquared() < 0.0001) {
            return
        }

        drowned.velocity = horizontal.normalize().multiply(waterLeapHorizontalSpeed).setY(waterLeapVerticalSpeed)
        drowned.world.spawnParticle(Particle.BUBBLE, drowned.location.clone().add(0.0, 0.4, 0.0), 28, 0.35, 0.2, 0.35, 0.04)
        drowned.world.spawnParticle(Particle.SPLASH, drowned.location.clone().add(0.0, 0.2, 0.0), 24, 0.35, 0.15, 0.35, 0.08)
        drowned.world.playSound(drowned.location, Sound.ENTITY_DOLPHIN_JUMP, 0.8f, 0.85f)
        drowned.world.playSound(drowned.location, Sound.ENTITY_DROWNED_SWIM, 0.75f, 1.2f)

        runtime.waterStreakTicks = 0L
        runtime.leapCooldownTicks = waterLeapCooldownTicks.coerceAtLeast(1L)
    }

    private class DrownedForceMeleeAttackGoal(
        private val drowned: Drowned,
        private val key: GoalKey<Drowned>,
        private val attackReachSquared: Double
    ) : Goal<Drowned> {

        private var cooldownTicks: Int = 0

        override fun shouldActivate(): Boolean {
            return resolveTarget() != null
        }

        override fun shouldStayActive(): Boolean {
            return resolveTarget() != null
        }

        override fun stop() {
            cooldownTicks = 0
        }

        override fun tick() {
            if (cooldownTicks > 0) {
                cooldownTicks -= 1
            }

            val target = resolveTarget() ?: return
            val distanceSquared = drowned.location.distanceSquared(target.location)
            if (distanceSquared > attackReachSquared) {
                drowned.pathfinder.moveTo(target.location, CHASE_SPEED)
                return
            }

            if (cooldownTicks <= 0) {
                drowned.attack(target)
                cooldownTicks = ATTACK_COOLDOWN_TICKS
            }
        }

        override fun getKey(): GoalKey<Drowned> {
            return key
        }

        override fun getTypes(): EnumSet<GoalType> {
            return EnumSet.of(GoalType.MOVE, GoalType.LOOK)
        }

        private fun resolveTarget(): Player? {
            val target = drowned.target as? Player ?: return null
            if (!target.isValid || target.isDead) return null
            if (target.world.uid != drowned.world.uid) return null
            return target
        }

        companion object {
            private const val CHASE_SPEED = 1.15
            private const val ATTACK_COOLDOWN_TICKS = 20
        }
    }

    companion object {
        const val DEFAULT_WATER_MOVEMENT_EFFICIENCY_BONUS = 0.1
        const val DEFAULT_WATER_LEAP_PREPARE_TICKS = 30L
        const val DEFAULT_WATER_LEAP_COOLDOWN_TICKS = 80L
        const val DEFAULT_WATER_LEAP_HORIZONTAL_SPEED = 1.08
        const val DEFAULT_WATER_LEAP_VERTICAL_SPEED = 0.58

        private val WATER_SPEED_MODIFIER_UUID: UUID = UUID.fromString("5eabbe49-d690-4c85-a7de-4ce9a64f8b67")
        private const val WATER_SPEED_MODIFIER_NAME = "drowned_aquatic_pursuit_water_speed"
        private const val DEFAULT_ATTACK_REACH_SQUARED = 4.84
        private const val GROUND_ATTACK_REACH_SQUARED = 5.76
        private const val GROUND_ATTACK_COOLDOWN_TICKS = 20L
        private const val NATURAL_ATTACK_SUPPRESS_TICKS = 12L
        private const val WATER_LEAP_MIN_RANGE_SQUARED = 64.0
        private const val WATER_LEAP_MAX_RANGE_SQUARED = 196.0
    }
}
