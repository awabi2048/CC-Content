package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobDeathContext
import jp.awabi2048.cccontent.mob.MobRuntimeContext
import jp.awabi2048.cccontent.mob.MobService
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Allay
import org.bukkit.entity.Display
import org.bukkit.entity.EntityType
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.Material
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.math.roundToLong
import kotlin.random.Random

class EnderEyeHunterAbility(
    override val id: String,
    private val orbitRadius: Double = 3.8,
    private val orbitHeightOffset: Double = 1.6,
    private val shotCooldownTicks: Long = 46L,
    private val projectileSpeedPerTick: Double = 0.55,
    private val projectileDamageMultiplier: Double = 0.8,
    private val homingStrength: Double = 0.2,
    private val maxLifeTicks: Long = 70L
) : MobAbility {

    data class Runtime(
        var eyeDisplayId: java.util.UUID? = null,
        var orbitAngle: Double = Random.nextDouble(0.0, Math.PI * 2.0),
        var cooldownRemainingTicks: Long = 0L,
        var searchPhaseOffsetSteps: Int = 0
    ) : MobAbilityRuntime

    override fun tickIntervalTicks(): Long = 1L

    override fun createRuntime(context: jp.awabi2048.cccontent.mob.MobSpawnContext): MobAbilityRuntime {
        return Runtime(searchPhaseOffsetSteps = Random.nextInt(0, SEARCH_PHASE_VARIANTS))
    }

    override fun onSpawn(context: jp.awabi2048.cccontent.mob.MobSpawnContext, runtime: MobAbilityRuntime?) {
        val allay = context.entity as? Allay ?: return
        val rt = runtime as? Runtime ?: return
        Bukkit.getMobGoals().removeAllGoals(allay)
        allay.isSilent = true
        allay.setGravity(false)

        val display = allay.world.spawnEntity(allay.location.clone().add(0.0, 0.8, 0.0), EntityType.ITEM_DISPLAY) as ItemDisplay
        display.setItemStack(ItemStack(Material.ENDER_EYE))
        display.billboard = Display.Billboard.CENTER
        display.brightness = Display.Brightness(15, 15)
        display.transformation = Transformation(
            Vector3f(0f, 0f, 0f),
            Quaternionf(),
            Vector3f(1.6f, 1.6f, 1.6f),
            Quaternionf()
        )
        rt.eyeDisplayId = display.uniqueId
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val allay = context.entity as? Allay ?: return
        val rt = runtime as? Runtime ?: return
        val display = rt.eyeDisplayId?.let { Bukkit.getEntity(it) as? ItemDisplay }

        if (rt.cooldownRemainingTicks > 0L) {
            rt.cooldownRemainingTicks = (rt.cooldownRemainingTicks - context.tickDelta).coerceAtLeast(0L)
        }

        val target = MobAbilityUtils.resolveTarget(allay) as? Player
        if (target == null || !target.isValid || target.isDead || target.world.uid != allay.world.uid) {
            return
        }

        rt.orbitAngle += 0.12
        val orbitOffset = Vector(
            kotlin.math.cos(rt.orbitAngle) * orbitRadius,
            orbitHeightOffset,
            kotlin.math.sin(rt.orbitAngle) * orbitRadius
        )
        val destination = target.location.toVector().add(orbitOffset)
        val nextLoc = Location(allay.world, destination.x, destination.y, destination.z)
        allay.teleport(nextLoc)
        updateFacing(allay, target)
        display?.teleport(nextLoc.clone().add(0.0, 0.6, 0.0))

        allay.world.spawnParticle(Particle.PORTAL, nextLoc.clone().add(0.0, 0.7, 0.0), 2, 0.08, 0.06, 0.08, 0.0)

        if (!context.isCombatActive()) return
        if (rt.cooldownRemainingTicks > 0L) return
        if (!MobAbilityUtils.shouldProcessSearchTick(
                context.activeMob.tickCount,
                context.loadSnapshot.searchIntervalMultiplier,
                rt.searchPhaseOffsetSteps,
                baseStepTicks = 1L
            )) return
        if (Random.nextDouble() < context.loadSnapshot.abilityExecutionSkipChance) return

        fireHomingOrb(context, allay, target)
        val baseCooldown = shotCooldownTicks.coerceAtLeast(1L)
        rt.cooldownRemainingTicks = (baseCooldown * context.loadSnapshot.abilityCooldownMultiplier).roundToLong().coerceAtLeast(baseCooldown)
    }

    override fun onDeath(context: MobDeathContext, runtime: MobAbilityRuntime?) {
        val rt = runtime as? Runtime ?: return
        val display = rt.eyeDisplayId?.let { Bukkit.getEntity(it) as? ItemDisplay } ?: return
        if (display.isValid) {
            display.remove()
        }
    }

    private fun fireHomingOrb(context: MobRuntimeContext, owner: LivingEntity, target: Player) {
        val world = owner.world
        var position = owner.eyeLocation.clone()
        var velocity = target.eyeLocation.toVector().subtract(position.toVector()).normalize().multiply(projectileSpeedPerTick)
        val hitSet = mutableSetOf<java.util.UUID>()
        val baseDamage = (context.definition.attack * projectileDamageMultiplier).coerceAtLeast(0.0)

        world.playSound(owner.location, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.7f, 1.8f)

        object : BukkitRunnable() {
            var life = maxLifeTicks

            override fun run() {
                if (!owner.isValid || owner.isDead || life <= 0L) {
                    cancel()
                    return
                }

                val liveTarget = Bukkit.getEntity(target.uniqueId) as? Player
                if (liveTarget != null && liveTarget.isValid && !liveTarget.isDead && liveTarget.world.uid == world.uid) {
                    val desired = liveTarget.eyeLocation.toVector().subtract(position.toVector())
                    if (desired.lengthSquared() > 0.0001) {
                        val wanted = desired.normalize().multiply(projectileSpeedPerTick)
                        velocity = velocity.multiply(1.0 - homingStrength).add(wanted.multiply(homingStrength)).normalize().multiply(projectileSpeedPerTick)
                    }
                }

                val next = position.clone().add(velocity)
                val blockHit = world.rayTraceBlocks(position, velocity.clone().normalize(), velocity.length(), org.bukkit.FluidCollisionMode.NEVER, true)
                if (blockHit != null) {
                    world.spawnParticle(Particle.ENCHANTED_HIT, blockHit.hitPosition.toLocation(world), 10, 0.08, 0.08, 0.08, 0.0)
                    cancel()
                    return
                }

                val hit = world.getNearbyEntities(next, 0.65, 0.65, 0.65)
                    .asSequence()
                    .filterIsInstance<Player>()
                    .firstOrNull { it.uniqueId != owner.uniqueId && it.isValid && !it.isDead && hitSet.add(it.uniqueId) }

                if (hit != null) {
                    if (baseDamage > 0.0) {
                        MobService.getInstance(context.plugin)?.issueDirectDamagePermit(owner.uniqueId, hit.uniqueId)
                        hit.damage(baseDamage, owner)
                    }
                    world.spawnParticle(Particle.ENCHANTED_HIT, hit.location.clone().add(0.0, 1.0, 0.0), 16, 0.12, 0.12, 0.12, 0.0)
                    world.playSound(hit.location, Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.8f, 1.8f)
                    cancel()
                    return
                }

                position = next
                world.spawnParticle(Particle.ENCHANTED_HIT, position, 3, 0.04, 0.04, 0.04, 0.0)
                life -= 1L
            }
        }.runTaskTimer(context.plugin, 1L, 1L)
    }

    private fun updateFacing(entity: LivingEntity, target: LivingEntity) {
        val from = entity.location.clone().add(0.0, 0.8, 0.0)
        val to = target.location.clone().add(0.0, 1.0, 0.0)
        val dir = to.toVector().subtract(from.toVector())
        val yaw = Math.toDegrees(atan2(-dir.x, dir.z)).toFloat()
        val pitch = (-Math.toDegrees(atan2(dir.y, sqrt((dir.x * dir.x + dir.z * dir.z).coerceAtLeast(1.0e-6))))).toFloat()
        val face = entity.location.clone().apply {
            this.yaw = yaw
            this.pitch = pitch
        }
        entity.teleport(face)
    }

    private companion object {
        const val SEARCH_PHASE_VARIANTS = 16
    }
}
