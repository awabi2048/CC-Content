package jp.awabi2048.cccontent.mob.type

import jp.awabi2048.cccontent.mob.CustomMobRuntime
import jp.awabi2048.cccontent.mob.MobSpawnContext
import jp.awabi2048.cccontent.mob.ability.MobAbility
import org.bukkit.Material
import org.bukkit.attribute.Attribute
import org.bukkit.entity.EntityType
import org.bukkit.entity.Slime
import org.bukkit.inventory.ItemStack

open class EquipmentMobType(
    id: String,
    baseEntityType: EntityType,
    abilities: List<MobAbility>,
    private val defaultMainHand: Material? = null,
    private val defaultOffHand: Material? = null,
    private val defaultHelmet: Material? = null,
    private val defaultChestplate: Material? = null,
    private val defaultLeggings: Material? = null,
    private val defaultBoots: Material? = null
) : AbilityMobType(
    id = id,
    baseEntityType = baseEntityType,
    abilities = abilities
) {
    override fun onSpawn(context: MobSpawnContext, runtime: CustomMobRuntime?) {
        applyVisualSpec(context)
        super.onSpawn(context, runtime)
    }

    override fun applyDefaultEquipment(context: MobSpawnContext, runtime: CustomMobRuntime?) {
        val equipment = context.entity.equipment ?: return
        val mainHand = defaultMainHand
        if (mainHand != null) {
            equipment.setItemInMainHand(ItemStack(mainHand))
        }
        val offHand = defaultOffHand
        if (offHand != null) {
            equipment.setItemInOffHand(ItemStack(offHand))
        }
        val helmet = defaultHelmet
        if (helmet != null) {
            equipment.helmet = ItemStack(helmet)
        }
        val chestplate = defaultChestplate
        if (chestplate != null) {
            equipment.chestplate = ItemStack(chestplate)
        }
        val leggings = defaultLeggings
        if (leggings != null) {
            equipment.leggings = ItemStack(leggings)
        }
        val boots = defaultBoots
        if (boots != null) {
            equipment.boots = ItemStack(boots)
        }
    }

    private fun applyVisualSpec(context: MobSpawnContext) {
        val entity = context.entity
        val slimeSize = SLIME_SIZE_BY_MOB_ID[id]
        if (entity is Slime && slimeSize != null) {
            entity.setSize(slimeSize)
            entity.getAttribute(Attribute.MAX_HEALTH)?.baseValue = context.definition.health
            entity.health = context.definition.health
            return
        }

        val scale = SCALE_BY_MOB_ID[id] ?: return
        entity.getAttribute(Attribute.SCALE)?.baseValue = scale
    }

    companion object {
        private val SLIME_SIZE_BY_MOB_ID = mapOf(
            "magma_cube_large" to 4,
            "magma_cube_medium" to 3,
            "magma_cube_small" to 2,
            "magma_cube_mini" to 0,
            "slime_merge_large" to 4,
            "slime_merge_medium" to 3,
            "slime_merge_small" to 2,
            "slime_merge_mini" to 0,
            "slime_poison" to 4,
            "slime_wither" to 4
        )

        private val SCALE_BY_MOB_ID = mapOf(
            "spider_stealth" to 0.8,
            "spider_broodmother" to 1.5,
            "spider_broodling" to 0.5,
            "spider_venom_frenzy" to 1.2,
            "spider_ferocious" to 1.2,
            "silverfish_big_poison" to 1.5,
            "silverfish_stealth_fang" to 2.5,
            "guardian_small" to 0.75,
            "guardian_drain" to 2.5,
            "water_spirit" to 3.0,
            "ashen_spirit" to 3.0,
            "frog_big" to 3.0,
            "blaze_power" to 1.5,
            "blaze_rapid" to 1.2,
            "blaze_melee" to 1.25,
            "blaze_beam" to 1.2,
            "witch_elite" to 1.2,
            "bat_venom" to 3.0
        )
    }
}
