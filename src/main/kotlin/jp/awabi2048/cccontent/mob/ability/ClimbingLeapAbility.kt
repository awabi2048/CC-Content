package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobRuntimeContext
import org.bukkit.block.BlockFace
import org.bukkit.entity.LivingEntity

class ClimbingLeapAbility(
    override val id: String,
    cooldownTicks: Long = 70L,
    minRangeSquared: Double = 4.0,
    maxRange: Double = 7.0,
    horizontalSpeed: Double = 1.0,
    verticalSpeed: Double = 0.55,
    postLeapFaceTicks: Long = 20L
) : MobAbility {

    private val delegate = LeapAbility(
        id = "${id}_delegate",
        cooldownTicks = cooldownTicks,
        minRangeSquared = minRangeSquared,
        maxRange = maxRange,
        horizontalSpeed = horizontalSpeed,
        verticalSpeed = verticalSpeed,
        postLeapFaceTicks = postLeapFaceTicks
    )

    override fun createRuntime(context: jp.awabi2048.cccontent.mob.MobSpawnContext): MobAbilityRuntime {
        return delegate.createRuntime(context)
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val leapRuntime = runtime as? LeapAbility.Runtime
        val keepFacing = (leapRuntime?.postLeapFaceTicks ?: 0L) > 0L
        if (!keepFacing && !isClimbingState(context.entity)) {
            return
        }
        delegate.onTick(context, runtime)
    }

    private fun isClimbingState(entity: LivingEntity): Boolean {
        if (entity.isOnGround) return false
        val block = entity.location.block
        val adjacentFaces = arrayOf(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)
        return adjacentFaces.any { face -> block.getRelative(face).type.isSolid }
    }
}
