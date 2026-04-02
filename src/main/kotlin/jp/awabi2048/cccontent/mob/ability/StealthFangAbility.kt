package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobAttackContext
import jp.awabi2048.cccontent.mob.MobDeathContext
import jp.awabi2048.cccontent.mob.MobRuntimeContext
import jp.awabi2048.cccontent.mob.MobService
import jp.awabi2048.cccontent.mob.MobSpawnContext
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.attribute.Attribute
import org.bukkit.entity.EvokerFangs
import org.bukkit.entity.LivingEntity
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import kotlin.math.roundToLong

class StealthFangAbility(
    override val id: String,
    private val stealthCycleTicks: Long = 160L,
    private val stealthDurationTicks: Int = 60
) : MobAbility {

    data class Runtime(
        var cycleCooldownTicks: Long = 0L,
        var isStealthed: Boolean = false,
        var stealthRemainingTicks: Long = 0L,
        var targetId: java.util.UUID? = null,
        var originalMovementSpeed: Double? = null
    ) : MobAbilityRuntime

    override fun tickIntervalTicks(): Long = 1L

    override fun createRuntime(context: MobSpawnContext): MobAbilityRuntime {
        return Runtime(cycleCooldownTicks = stealthCycleTicks.coerceAtLeast(1L))
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val rt = runtime as? Runtime ?: return
        val entity = context.entity

        if (rt.isStealthed) {
            rt.stealthRemainingTicks = (rt.stealthRemainingTicks - context.tickDelta).coerceAtLeast(0L)
            if (rt.stealthRemainingTicks <= 0L) {
                reveal(context.plugin, entity, rt, null)
            }
            return
        }

        if (!context.isCombatActive()) return

        if (rt.cycleCooldownTicks > 0L) {
            rt.cycleCooldownTicks = (rt.cycleCooldownTicks - context.tickDelta).coerceAtLeast(0L)
            return
        }

        val target = resolveTarget(entity) ?: return
        if (target.world.uid != entity.world.uid) return
        if (entity.location.distanceSquared(target.location) < MIN_TRIGGER_DISTANCE_SQUARED) return

        applyStealth(entity, rt, context, target)
    }

    override fun onAttack(context: MobAttackContext, runtime: MobAbilityRuntime?) {
        val rt = runtime as? Runtime ?: return
        if (!rt.isStealthed) return
        if (!isDirectMeleeAttack(context)) return

        context.event.isCancelled = true
        context.event.damage = 0.0

        val target = context.target
            ?: rt.targetId?.let { id ->
                context.entity.world.players.firstOrNull { player -> player.uniqueId == id }
            }
            ?: return
        reveal(context.plugin, context.entity, rt, target)
    }

    override fun onDeath(context: MobDeathContext, runtime: MobAbilityRuntime?) {
        val rt = runtime as? Runtime ?: return
        boostMovementSpeed(context.entity, rt, false)
    }

    private fun applyStealth(entity: LivingEntity, rt: Runtime, context: MobRuntimeContext, target: LivingEntity) {
        val ticks = stealthDurationTicks.coerceAtLeast(1)
        entity.addPotionEffect(
            PotionEffect(PotionEffectType.INVISIBILITY, ticks, 0, false, false, true)
        )
        boostMovementSpeed(entity, rt, true)
        rt.isStealthed = true
        rt.stealthRemainingTicks = ticks.toLong()
        rt.targetId = target.uniqueId

        showSandstoneBurst(entity.location)
        entity.world.playSound(entity.location, "minecraft:block.sand.break", 0.8f, 0.5f)

        val baseCooldown = stealthCycleTicks.coerceAtLeast(1L)
        rt.cycleCooldownTicks =
            (baseCooldown * context.loadSnapshot.abilityCooldownMultiplier).roundToLong().coerceAtLeast(baseCooldown)
    }

    private fun reveal(
        plugin: org.bukkit.plugin.java.JavaPlugin,
        entity: LivingEntity,
        rt: Runtime,
        attackTarget: LivingEntity?
    ) {
        entity.removePotionEffect(PotionEffectType.INVISIBILITY)
        boostMovementSpeed(entity, rt, false)

        val target = attackTarget ?: resolveTargetByRuntime(entity, rt)
        spawnFang(plugin, entity, target)

        rt.isStealthed = false
        rt.stealthRemainingTicks = 0L
        rt.targetId = null

        entity.velocity = entity.velocity.add(org.bukkit.util.Vector(0.0, SELF_KNOCKUP, 0.0))

        showSandstoneBurst(entity.location)
        entity.world.playSound(entity.location, "minecraft:block.sand.break", 0.8f, 0.5f)
    }

    private fun boostMovementSpeed(entity: LivingEntity, rt: Runtime, enable: Boolean) {
        val attribute = entity.getAttribute(Attribute.MOVEMENT_SPEED) ?: return
        if (enable) {
            if (rt.originalMovementSpeed == null) {
                rt.originalMovementSpeed = attribute.baseValue
                attribute.baseValue = attribute.baseValue * 2.0
            }
            return
        }

        val original = rt.originalMovementSpeed ?: return
        attribute.baseValue = original
        rt.originalMovementSpeed = null
    }

    private fun spawnFang(
        plugin: org.bukkit.plugin.java.JavaPlugin,
        caster: LivingEntity,
        target: LivingEntity?
    ) {
        val world = caster.world
        val spawnLocation = if (target != null && caster.location.distanceSquared(target.location) <= CATCH_UP_DISTANCE_SQUARED) {
            target.location.clone()
        } else {
            caster.location.clone()
        }
        val fangs = world.spawn(spawnLocation, EvokerFangs::class.java)
        fangs.isSilent = true
        MobService.getInstance(plugin)?.markStealthFang(fangs)
    }

    private fun resolveTarget(entity: LivingEntity): LivingEntity? {
        return (entity as? org.bukkit.entity.Mob)?.target as? LivingEntity
    }

    private fun resolveTargetByRuntime(entity: LivingEntity, runtime: Runtime): LivingEntity? {
        val targetId = runtime.targetId ?: return null
        val player = entity.world.players.firstOrNull { it.uniqueId == targetId }
        if (player != null && player.isValid && !player.isDead) {
            return player
        }
        return null
    }

    private fun showSandstoneBurst(location: Location) {
        val world = location.world ?: return
        world.spawnParticle(
            Particle.BLOCK,
            location.clone().add(0.0, 0.5, 0.0),
            100,
            0.5,
            0.5,
            0.5,
            0.0,
            Material.SANDSTONE.createBlockData()
        )
    }

    private fun isDirectMeleeAttack(context: MobAttackContext): Boolean {
        val damager = context.event.damager as? LivingEntity ?: return false
        return damager.uniqueId == context.entity.uniqueId
    }

    companion object {
        private const val MIN_TRIGGER_DISTANCE_SQUARED = 16.0
        private const val CATCH_UP_DISTANCE_SQUARED = 4.0
        private const val SELF_KNOCKUP = 0.55
    }
}
