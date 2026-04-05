package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobRuntimeContext
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import org.bukkit.entity.Player
import java.util.UUID
import kotlin.math.roundToLong
import kotlin.random.Random

class LeapAbility(
    override val id: String,
    private val cooldownTicks: Long = 80L,
    private val minRangeSquared: Double = 16.0,
    private val maxRange: Double = 8.0,
    private val horizontalSpeed: Double = 0.9,
    private val verticalSpeed: Double = 0.45,
    private val farJumpStartRangeSquared: Double? = null,
    private val farHorizontalSpeed: Double = horizontalSpeed,
    private val farVerticalSpeed: Double = verticalSpeed,
    private val postLeapFaceTicks: Long = 20L
) : MobAbility {
    data class Runtime(
        var leapCooldownTicks: Long = 0L,
        var searchPhaseOffsetSteps: Int = 0,
        var postLeapFaceTicks: Long = 0L,
        var postLeapTargetId: UUID? = null
    ) : MobAbilityRuntime

    override fun createRuntime(context: jp.awabi2048.cccontent.mob.MobSpawnContext): MobAbilityRuntime {
        return Runtime(searchPhaseOffsetSteps = Random.nextInt(0, SEARCH_PHASE_VARIANTS))
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val abilityRuntime = runtime as? Runtime ?: return
        continuePostLeapFacing(context, abilityRuntime)

        if (abilityRuntime.leapCooldownTicks > 0L) {
            abilityRuntime.leapCooldownTicks = (abilityRuntime.leapCooldownTicks - context.tickDelta).coerceAtLeast(0L)
        }

        if (!context.isCombatActive()) return
        if (abilityRuntime.leapCooldownTicks > 0L) return

        val loadSnapshot = context.loadSnapshot
        if (!MobAbilityUtils.shouldProcessSearchTick(context.activeMob.tickCount, loadSnapshot.searchIntervalMultiplier, abilityRuntime.searchPhaseOffsetSteps)) {
            return
        }
        if (Random.nextDouble() < loadSnapshot.abilityExecutionSkipChance) {
            return
        }

        val entity = context.entity
        if (!entity.isOnGround) return

        val target = MobAbilityUtils.resolveTarget(entity) ?: return
        val distanceSquared = entity.location.distanceSquared(target.location)
        val maxRangeSquared = maxRange * maxRange
        if (distanceSquared < minRangeSquared || distanceSquared > maxRangeSquared) return

        MobAbilityUtils.faceTowards(entity, target)

        val delta = target.location.toVector().subtract(entity.location.toVector())
        val horizontal = delta.clone().setY(0.0)
        if (horizontal.lengthSquared() < 0.0001) return
        val useFarJump = farJumpStartRangeSquared != null && distanceSquared >= farJumpStartRangeSquared
        val selectedHorizontalSpeed = if (useFarJump) farHorizontalSpeed else horizontalSpeed
        val selectedVerticalSpeed = if (useFarJump) farVerticalSpeed else verticalSpeed

        entity.world.playSound(entity.location, Sound.ENTITY_IRON_GOLEM_STEP, 1.0f, 0.8f)
        entity.velocity = horizontal.normalize().multiply(selectedHorizontalSpeed).setY(selectedVerticalSpeed)
        abilityRuntime.postLeapFaceTicks = postLeapFaceTicks
        abilityRuntime.postLeapTargetId = target.uniqueId

        abilityRuntime.leapCooldownTicks =
            (cooldownTicks * loadSnapshot.abilityCooldownMultiplier).roundToLong().coerceAtLeast(cooldownTicks)
    }

    private fun continuePostLeapFacing(context: MobRuntimeContext, runtime: Runtime) {
        if (runtime.postLeapFaceTicks <= 0L) return

        val entity = context.entity
        if (entity.isOnGround) {
            val landingTarget = runtime.postLeapTargetId?.let { Bukkit.getEntity(it) as? LivingEntity }
            redirectToPlayerOnLanding(entity, landingTarget)
            runtime.postLeapFaceTicks = 0L
            runtime.postLeapTargetId = null
            return
        }

        val targetId = runtime.postLeapTargetId
        val target = targetId?.let { Bukkit.getEntity(it) as? LivingEntity }
        if (target == null || !target.isValid || target.isDead || target.world.uid != entity.world.uid) {
            runtime.postLeapFaceTicks = 0L
            runtime.postLeapTargetId = null
            return
        }

        MobAbilityUtils.faceTowards(entity, target)
        runtime.postLeapFaceTicks = (runtime.postLeapFaceTicks - context.tickDelta).coerceAtLeast(0L)
    }

    private fun redirectToPlayerOnLanding(entity: LivingEntity, target: LivingEntity?) {
        val mob = entity as? Mob ?: return
        val playerTarget = target as? Player ?: return
        if (!playerTarget.isValid || playerTarget.isDead || playerTarget.world.uid != mob.world.uid) {
            return
        }

        mob.target = playerTarget
        mob.pathfinder.moveTo(playerTarget.location, LANDING_CHASE_SPEED)
    }

    private companion object {
        const val SEARCH_PHASE_VARIANTS = 16
        const val LANDING_CHASE_SPEED = 1.2
    }
}
