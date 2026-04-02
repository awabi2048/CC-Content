package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobRuntimeContext
import jp.awabi2048.cccontent.mob.MobAttackContext
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.AbstractArrow
import org.bukkit.entity.Arrow
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.Material
import org.bukkit.event.entity.EntityDamageByEntityEvent
import kotlin.math.roundToLong
import kotlin.random.Random

class WeaponThrowAbility(
    override val id: String,
    private val throwCooldownTicks: Long = DEFAULT_THROW_COOLDOWN_TICKS,
    private val triggerMinDistance: Double = DEFAULT_TRIGGER_MIN_DISTANCE,
    private val triggerMaxDistance: Double = DEFAULT_TRIGGER_MAX_DISTANCE,
    private val throwSpeed: Double = DEFAULT_THROW_SPEED,
    private val gravityCompensationPerBlock: Double = DEFAULT_GRAVITY_COMPENSATION_PER_BLOCK,
    private val spread: Double = DEFAULT_SPREAD,
    private val damageMultiplier: Double = DEFAULT_DAMAGE_MULTIPLIER
) : MobAbility {
    data class Runtime(
        var cooldownTicks: Long = 0L,
        var searchPhaseOffsetSteps: Int = 0,
        var lastMeleeDamage: Double? = null,
        var lastMeleeWeaponType: Material? = null
    ) : MobAbilityRuntime

    override fun createRuntime(context: jp.awabi2048.cccontent.mob.MobSpawnContext): MobAbilityRuntime {
        return Runtime(searchPhaseOffsetSteps = Random.nextInt(0, SEARCH_PHASE_VARIANTS))
    }

    override fun onSpawn(context: jp.awabi2048.cccontent.mob.MobSpawnContext, runtime: MobAbilityRuntime?) {
        val key = loaderKey(context.plugin)
        context.entity.persistentDataContainer.set(key, PersistentDataType.BYTE, 1)
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val abilityRuntime = runtime as? Runtime ?: return
        if (abilityRuntime.cooldownTicks > 0L) {
            abilityRuntime.cooldownTicks = (abilityRuntime.cooldownTicks - context.tickDelta).coerceAtLeast(0L)
        }

        val entity = context.entity
        val weapon = entity.equipment?.itemInMainHand
        if (weapon == null || weapon.type.isAir) {
            steerToDroppedWeapon(context, entity)
            return
        }

        if (!context.isCombatActive()) return
        if (abilityRuntime.cooldownTicks > 0L) return
        if (!MobAbilityUtils.shouldProcessSearchTick(context.activeMob.tickCount, context.loadSnapshot.searchIntervalMultiplier, abilityRuntime.searchPhaseOffsetSteps)) {
            return
        }
        if (Random.nextDouble() < context.loadSnapshot.abilityExecutionSkipChance) {
            return
        }

        val target = MobAbilityUtils.resolveTarget(entity) ?: return
        val distance = entity.eyeLocation.distance(target.eyeLocation)
        if (distance < triggerMinDistance || distance > triggerMaxDistance) {
            return
        }

        val service = ThrownWeaponService.getInstance(context.plugin)
        if (service.hasActiveThrow(entity.uniqueId)) {
            return
        }

        val source = entity.eyeLocation
        val predictedTarget = target.eyeLocation.add(target.velocity.clone().multiply(0.35))
        val direction = predictedTarget.toVector().subtract(source.toVector())
        direction.y += distance * gravityCompensationPerBlock
        if (direction.lengthSquared() < 0.0001) {
            return
        }

        direction.x += Random.nextDouble(-spread, spread)
        direction.y += Random.nextDouble(-spread * 0.6, spread * 0.6)
        direction.z += Random.nextDouble(-spread, spread)

        val projectile = entity.launchProjectile(Arrow::class.java)
        projectile.shooter = entity
        projectile.velocity = direction.normalize().multiply(throwSpeed)
        projectile.isSilent = true
        projectile.pickupStatus = AbstractArrow.PickupStatus.DISALLOWED
        projectile.isCritical = false
        projectile.setGravity(false)
        hideInternalProjectile(projectile)

        val itemAttackDamage = resolveThrowBaseDamage(weapon, abilityRuntime, context) ?: return
        val throwDamage = itemAttackDamage * damageMultiplier

        val launched = service.launch(
            ThrownWeaponService.LaunchSpec(
                ownerId = entity.uniqueId,
                projectile = projectile,
                weapon = ItemStack(weapon),
                damage = throwDamage,
                loaderKey = loaderKeyString()
            )
        )
        if (!launched) {
            projectile.remove()
            return
        }

        entity.equipment?.setItemInMainHand(null)
        entity.world.spawnParticle(Particle.SWEEP_ATTACK, entity.location.add(0.0, 1.0, 0.0), 1, 0.0, 0.0, 0.0, 0.0)
        entity.world.playSound(entity.location, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.1f)

        abilityRuntime.cooldownTicks =
            (throwCooldownTicks * context.loadSnapshot.abilityCooldownMultiplier).roundToLong().coerceAtLeast(throwCooldownTicks)
    }

    override fun onAttack(context: MobAttackContext, runtime: MobAbilityRuntime?) {
        val abilityRuntime = runtime as? Runtime ?: return
        if (!isDirectMeleeAttack(context.event, context.entity)) {
            return
        }

        val weaponType = context.entity.equipment?.itemInMainHand?.type ?: return
        if (weaponType.isAir) {
            return
        }
        abilityRuntime.lastMeleeDamage = context.event.damage.coerceAtLeast(0.0)
        abilityRuntime.lastMeleeWeaponType = weaponType
    }

    private fun loaderKey(plugin: org.bukkit.plugin.java.JavaPlugin): NamespacedKey {
        return NamespacedKey(plugin, loaderKeyString())
    }

    private fun loaderKeyString(): String {
        return "weapon_throw_loader_$id"
    }

    private fun resolveThrowBaseDamage(weapon: ItemStack, runtime: Runtime, context: MobRuntimeContext): Double {
        val sampledDamage = runtime.lastMeleeDamage
        if (sampledDamage != null && runtime.lastMeleeWeaponType == weapon.type) {
            return sampledDamage.coerceAtLeast(0.0)
        }
        return context.activeMob.definition.attack.coerceAtLeast(1.0)
    }

    private fun isDirectMeleeAttack(event: EntityDamageByEntityEvent, attacker: LivingEntity): Boolean {
        val damager = event.damager as? LivingEntity ?: return false
        return damager.uniqueId == attacker.uniqueId
    }

    private fun steerToDroppedWeapon(context: MobRuntimeContext, entity: org.bukkit.entity.LivingEntity) {
        val service = ThrownWeaponService.getInstance(context.plugin)
        val dropped = service.findNearestGroundWeapon(loaderKeyString(), entity.location, PICKUP_SEARCH_RANGE) ?: return
        val mob = entity as? Mob ?: return

        mob.pathfinder.stopPathfinding()
        mob.target = null
        mob.pathfinder.moveTo(dropped.location, PICKUP_PATHFIND_SPEED)
    }

    private fun hideInternalProjectile(projectile: Arrow) {
        runCatching {
            val method = projectile.javaClass.methods.firstOrNull { candidate ->
                candidate.name == "setVisibleByDefault" &&
                    candidate.parameterCount == 1 &&
                    candidate.parameterTypes[0] == Boolean::class.javaPrimitiveType
            }
            method?.invoke(projectile, false)
        }
    }

    companion object {
        const val DEFAULT_THROW_COOLDOWN_TICKS = 80L
        const val DEFAULT_TRIGGER_MIN_DISTANCE = 5.0
        const val DEFAULT_TRIGGER_MAX_DISTANCE = 7.0
        const val DEFAULT_THROW_SPEED = 0.6
        const val DEFAULT_GRAVITY_COMPENSATION_PER_BLOCK = 0.018
        const val DEFAULT_SPREAD = 0.02
        const val DEFAULT_DAMAGE_MULTIPLIER = 2.0
        private const val PICKUP_SEARCH_RANGE = 12.0
        private const val PICKUP_PATHFIND_SPEED = 2.3
        private const val SEARCH_PHASE_VARIANTS = 16
    }
}
