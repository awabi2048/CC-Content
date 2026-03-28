package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobDeathContext
import jp.awabi2048.cccontent.mob.MobService
import jp.awabi2048.cccontent.mob.MobSpawnOptions
import org.bukkit.attribute.Attribute
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import org.bukkit.util.Vector
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class SplitOnDeathAbility(
    override val id: String,
    private val childDefinitionId: String,
    private val splitCount: Int = 4,
    private val healthMultiplier: Double = 0.5,
    private val attackMultiplier: Double = 0.5,
    private val speedMultiplier: Double = 0.5,
    private val spawnRadius: Double = 1.2
) : MobAbility {

    override fun onDeath(context: MobDeathContext, runtime: MobAbilityRuntime?) {
        if (splitCount <= 0) return

        val source = context.entity
        val world = source.world
        val mobService = MobService.getInstance(context.plugin) ?: return
        val definition = mobService.getDefinition(childDefinitionId) ?: return

        val sourceHealth = source.getAttribute(Attribute.MAX_HEALTH)?.baseValue
        val sourceAttack = source.getAttribute(Attribute.ATTACK_DAMAGE)?.baseValue
        val sourceSpeed = source.getAttribute(Attribute.MOVEMENT_SPEED)?.baseValue

        val options = MobSpawnOptions(
            featureId = context.activeMob.featureId,
            sessionKey = context.sessionKey,
            combatActiveProvider = context.activeMob.combatActiveProvider,
            metadata = context.activeMob.metadata + ("split_child" to "true")
        )

        val sourceTarget = (source as? Mob)?.target
        for (index in 0 until splitCount) {
            val spawnLocation = splitLocation(source.location, index, splitCount)
            val child = mobService.spawn(definition, spawnLocation, options) ?: continue
            applyScaledStats(child, sourceHealth, sourceAttack, sourceSpeed)
            child.velocity = splitImpulse(index, splitCount)
            if (child is Mob && sourceTarget != null && sourceTarget.isValid && !sourceTarget.isDead) {
                child.target = sourceTarget
            }
        }

        world.playSound(source.location, org.bukkit.Sound.ENTITY_SPIDER_DEATH, 1.0f, 0.8f)
    }

    private fun splitLocation(origin: org.bukkit.Location, index: Int, total: Int): org.bukkit.Location {
        val angle = (2.0 * PI * index.toDouble()) / total.coerceAtLeast(1)
        val x = cos(angle) * spawnRadius
        val z = sin(angle) * spawnRadius
        return origin.clone().add(x, 0.1, z)
    }

    private fun splitImpulse(index: Int, total: Int): Vector {
        val angle = (2.0 * PI * index.toDouble()) / total.coerceAtLeast(1)
        return Vector(cos(angle), 0.22, sin(angle)).multiply(0.18)
    }

    private fun applyScaledStats(
        child: LivingEntity,
        sourceHealth: Double?,
        sourceAttack: Double?,
        sourceSpeed: Double?
    ) {
        val health = (sourceHealth ?: child.getAttribute(Attribute.MAX_HEALTH)?.baseValue ?: 20.0) * healthMultiplier
        val attack = (sourceAttack ?: child.getAttribute(Attribute.ATTACK_DAMAGE)?.baseValue ?: 1.0) * attackMultiplier
        val speed = (sourceSpeed ?: child.getAttribute(Attribute.MOVEMENT_SPEED)?.baseValue ?: 0.23) * speedMultiplier

        val clampedHealth = health.coerceAtLeast(1.0)
        val clampedAttack = attack.coerceAtLeast(0.0)
        val clampedSpeed = speed.coerceAtLeast(0.01)

        child.getAttribute(Attribute.MAX_HEALTH)?.baseValue = clampedHealth
        child.getAttribute(Attribute.ATTACK_DAMAGE)?.baseValue = clampedAttack
        child.getAttribute(Attribute.MOVEMENT_SPEED)?.baseValue = clampedSpeed
        child.health = clampedHealth.coerceAtMost(child.getAttribute(Attribute.MAX_HEALTH)?.value ?: clampedHealth)
    }
}
