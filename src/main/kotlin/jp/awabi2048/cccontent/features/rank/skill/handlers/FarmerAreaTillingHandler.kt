package jp.awabi2048.cccontent.features.rank.skill.handlers

import jp.awabi2048.cccontent.features.rank.skill.EffectContext
import jp.awabi2048.cccontent.features.rank.skill.EvaluationMode
import jp.awabi2048.cccontent.features.rank.skill.SkillEffect
import jp.awabi2048.cccontent.features.rank.skill.SkillEffectHandler
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot

class FarmerAreaTillingHandler : SkillEffectHandler {
    companion object {
        const val EFFECT_TYPE = "farmer.area_tilling"
        private const val DEFAULT_RADIUS = 2
        private const val MAX_RADIUS = 6
    }

    override fun getEffectType(): String = EFFECT_TYPE

    override fun getDefaultEvaluationMode(): EvaluationMode = EvaluationMode.RUNTIME

    override fun applyEffect(context: EffectContext): Boolean {
        val event = context.getEvent<PlayerInteractEvent>() ?: return false
        if (event.hand != EquipmentSlot.HAND) {
            return false
        }

        if (event.action != Action.RIGHT_CLICK_BLOCK) {
            return false
        }

        val player = event.player
        if (!FarmerCropSupport.isHoe(player.inventory.itemInMainHand.type)) {
            return false
        }

        val center = event.clickedBlock ?: return false
        if (!FarmerCropSupport.canApplyTillingTo(center, event.blockFace)) {
            return false
        }

        val radius = context.skillEffect.getIntParam("radius", DEFAULT_RADIUS).coerceIn(1, MAX_RADIUS)
        var changed = 0
        var planted = 0

        for (dx in -radius..radius) {
            for (dy in -radius..radius) {
                for (dz in -radius..radius) {
                    val target = center.world.getBlockAt(center.x + dx, center.y + dy, center.z + dz)
                    if (FarmerCropSupport.applyTillingTo(target, event.blockFace)) {
                        changed++
                        if (FarmerCropSupport.tryAutoPlantSeedOnFarmland(player, target)) {
                            planted++
                        }
                    }
                }
            }
        }

        return changed > 0 || planted > 0
    }

    override fun calculateStrength(skillEffect: SkillEffect): Double {
        val radius = skillEffect.getIntParam("radius", DEFAULT_RADIUS).coerceIn(1, MAX_RADIUS)
        val oneSide = radius * 2 + 1
        return (oneSide * oneSide * oneSide).toDouble()
    }

    override fun supportsProfession(professionId: String): Boolean {
        return professionId == "farmer"
    }

    override fun validateParams(skillEffect: SkillEffect): Boolean {
        return skillEffect.getIntParam("radius", DEFAULT_RADIUS) > 0
    }
}
