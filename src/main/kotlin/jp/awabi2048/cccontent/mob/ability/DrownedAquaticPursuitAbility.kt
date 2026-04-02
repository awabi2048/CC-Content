package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobDeathContext
import jp.awabi2048.cccontent.mob.MobRuntimeContext
import jp.awabi2048.cccontent.mob.MobSpawnContext
import org.bukkit.Material
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.block.data.Waterlogged
import org.bukkit.entity.LivingEntity
import java.util.UUID

class DrownedAquaticPursuitAbility(
    override val id: String,
    private val requiredSubmergedTicks: Long = DEFAULT_REQUIRED_SUBMERGED_TICKS,
    private val waterMovementEfficiencyBonus: Double = DEFAULT_WATER_MOVEMENT_EFFICIENCY_BONUS,
    private val leapAbility: LeapAbility = LeapAbility(id = "drowned_aquatic_pursuit_leap")
) : MobAbility {

    data class Runtime(
        var submergedTicks: Long = 0L,
        val leapRuntime: MobAbilityRuntime?
    ) : MobAbilityRuntime

    override fun createRuntime(context: MobSpawnContext): MobAbilityRuntime {
        return Runtime(
            submergedTicks = 0L,
            leapRuntime = leapAbility.createRuntime(context)
        )
    }

    override fun onSpawn(context: MobSpawnContext, runtime: MobAbilityRuntime?) {
        val entity = context.entity
        removeWaterSpeedModifier(entity)
        leapAbility.onSpawn(context, (runtime as? Runtime)?.leapRuntime)
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val rt = runtime as? Runtime ?: return
        val entity = context.entity

        val lowerBodyInWater = isLowerBodyInWater(entity)
        if (lowerBodyInWater) {
            applyWaterSpeedModifier(entity)
            rt.submergedTicks += 10L
        } else {
            removeWaterSpeedModifier(entity)
            rt.submergedTicks = 0L
        }

        if (lowerBodyInWater && rt.submergedTicks >= requiredSubmergedTicks) {
            leapAbility.onTick(context, rt.leapRuntime)
        }
    }

    override fun onDeath(context: MobDeathContext, runtime: MobAbilityRuntime?) {
        removeWaterSpeedModifier(context.entity)
        leapAbility.onDeath(context, (runtime as? Runtime)?.leapRuntime)
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

    companion object {
        const val DEFAULT_REQUIRED_SUBMERGED_TICKS = 60L
        const val DEFAULT_WATER_MOVEMENT_EFFICIENCY_BONUS = 0.75

        private val WATER_SPEED_MODIFIER_UUID: UUID = UUID.fromString("5eabbe49-d690-4c85-a7de-4ce9a64f8b67")
        private const val WATER_SPEED_MODIFIER_NAME = "drowned_aquatic_pursuit_water_speed"
    }
}
