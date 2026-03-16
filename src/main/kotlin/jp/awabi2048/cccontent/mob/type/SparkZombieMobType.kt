package jp.awabi2048.cccontent.mob.type

import jp.awabi2048.cccontent.mob.CustomMobRuntime
import jp.awabi2048.cccontent.mob.MobAttackContext
import jp.awabi2048.cccontent.mob.MobDamagedContext
import jp.awabi2048.cccontent.mob.MobDeathContext
import jp.awabi2048.cccontent.mob.MobRuntimeContext
import jp.awabi2048.cccontent.mob.MobSpawnContext
import jp.awabi2048.cccontent.mob.MobType
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

class SparkZombieMobType : MobType {
    override val id: String = "spark_zombie"
    override val baseEntityType: EntityType = EntityType.ZOMBIE

    data class Runtime(
        var pulseCooldownTicks: Long = 0L
    ) : CustomMobRuntime

    override fun createRuntime(context: MobSpawnContext): CustomMobRuntime {
        return Runtime()
    }

    override fun applyDefaultEquipment(context: MobSpawnContext, runtime: CustomMobRuntime?) {
        val equipment = context.entity.equipment ?: return
        if (equipment.itemInMainHand.type.isAir) {
            equipment.setItemInMainHand(ItemStack(Material.GOLDEN_SWORD))
        }
        if (equipment.helmet.type.isAir) {
            equipment.helmet = ItemStack(Material.CHAINMAIL_HELMET)
        }
    }

    override fun onSpawn(context: MobSpawnContext, runtime: CustomMobRuntime?) {
        context.entity.world.spawnParticle(
            Particle.ELECTRIC_SPARK,
            context.entity.location.add(0.0, 1.0, 0.0),
            20,
            0.4,
            0.5,
            0.4,
            0.02
        )
    }

    override fun onTick(context: MobRuntimeContext, runtime: CustomMobRuntime?) {
        val mobRuntime = runtime as? Runtime ?: return
        val entity = context.entity
        entity.world.spawnParticle(
            Particle.DUST,
            entity.location.add(0.0, 1.0, 0.0),
            4,
            0.25,
            0.35,
            0.25,
            Particle.DustOptions(Color.fromRGB(255, 196, 64), 1.0f)
        )

        if (!context.isCombatActive()) {
            return
        }

        if (mobRuntime.pulseCooldownTicks > 0L) {
            mobRuntime.pulseCooldownTicks -= 10L
            return
        }

        val target = entity.getNearbyEntities(2.5, 1.5, 2.5)
            .filterIsInstance<LivingEntity>()
            .filter { it.uniqueId != entity.uniqueId }
            .minByOrNull { it.location.distanceSquared(entity.location) }
            ?: return

        target.addPotionEffect(PotionEffect(PotionEffectType.GLOWING, 40, 0, false, true, true))
        entity.world.spawnParticle(
            Particle.ELECTRIC_SPARK,
            target.location.add(0.0, 1.0, 0.0),
            8,
            0.3,
            0.4,
            0.3,
            0.02
        )
        mobRuntime.pulseCooldownTicks = 40L
    }

    override fun onAttack(context: MobAttackContext, runtime: CustomMobRuntime?) {
        val target = context.target ?: return
        target.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 40, 0, false, true, true))
        context.entity.world.spawnParticle(
            Particle.ELECTRIC_SPARK,
            target.location.add(0.0, 1.0, 0.0),
            12,
            0.35,
            0.4,
            0.35,
            0.02
        )
    }

    override fun onDamaged(context: MobDamagedContext, runtime: CustomMobRuntime?) {
        context.entity.world.spawnParticle(
            Particle.CRIT,
            context.entity.location.add(0.0, 1.0, 0.0),
            8,
            0.3,
            0.4,
            0.3,
            0.02
        )
    }

    override fun onDeath(context: MobDeathContext, runtime: CustomMobRuntime?) {
        context.entity.world.spawnParticle(
            Particle.ELECTRIC_SPARK,
            context.entity.location.add(0.0, 1.0, 0.0),
            30,
            0.5,
            0.6,
            0.5,
            0.05
        )
    }
}
