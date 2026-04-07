package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobRuntimeContext
import jp.awabi2048.cccontent.mob.MobService
import org.bukkit.FluidCollisionMode
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.entity.Shulker
import org.bukkit.entity.ShulkerBullet
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Vector
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.random.Random

class ShulkerCloseDefenseBarrageAbility(
    override val id: String,
    private val normalCooldownTicks: Long = 54L,
    private val closeDefenseCooldownTicks: Long = 64L,
    private val closeDefenseRange: Double = 3.2,
    private val homingProjectileDamageMultiplier: Double = 0.58,
    private val homingProjectileSpeedPerTick: Double = 0.2,
    private val homingStrength: Double = 0.15,
    private val homingNoiseStrength: Double = 0.09,
    private val curveProjectileDamageMultiplier: Double = 0.45,
    private val curveProjectileSpeedPerTick: Double = 0.19
) : MobAbility {

    data class Runtime(
        var cooldownRemainingTicks: Long = 0L,
        var searchPhaseOffsetSteps: Int = 0
    ) : MobAbilityRuntime

    override fun tickIntervalTicks(): Long = 1L

    override fun createRuntime(context: jp.awabi2048.cccontent.mob.MobSpawnContext): MobAbilityRuntime {
        return Runtime(searchPhaseOffsetSteps = Random.nextInt(0, SEARCH_PHASE_VARIANTS))
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val shulker = context.entity as? Shulker ?: return
        val rt = runtime as? Runtime ?: return
        renderProximityZone(shulker)

        if (rt.cooldownRemainingTicks > 0L) {
            rt.cooldownRemainingTicks = (rt.cooldownRemainingTicks - context.tickDelta).coerceAtLeast(0L)
        }
        if (!context.isCombatActive()) {
            shulker.peek = 1.0f
            return
        }
        if (rt.cooldownRemainingTicks > 0L) return
        if (!MobAbilityUtils.shouldProcessSearchTick(
                context.activeMob.tickCount,
                context.loadSnapshot.searchIntervalMultiplier,
                rt.searchPhaseOffsetSteps,
                baseStepTicks = 1L
            )) return
        if (Random.nextDouble() < context.loadSnapshot.abilityExecutionSkipChance) return

        val target = resolveCombatTarget(shulker) ?: run {
            shulker.peek = 1.0f
            return
        }
        if (!target.isValid || target.isDead || target.world.uid != shulker.world.uid) {
            shulker.peek = 1.0f
            return
        }

        val distance = shulker.location.distance(target.location)
        if (distance <= closeDefenseRange) {
            shulker.peek = 0.0f
            fireCloseDefense(context, shulker)
            rt.cooldownRemainingTicks = adjustedCooldown(closeDefenseCooldownTicks, context)
            return
        }

        shulker.peek = 1.0f
        fireThreeWayHoming(context, shulker, target)
        rt.cooldownRemainingTicks = adjustedCooldown(normalCooldownTicks, context)
    }

    private fun adjustedCooldown(base: Long, context: MobRuntimeContext): Long {
        val safeBase = base.coerceAtLeast(1L)
        return (safeBase * context.loadSnapshot.abilityCooldownMultiplier).roundToLong().coerceAtLeast(safeBase)
    }

    private fun fireThreeWayHoming(context: MobRuntimeContext, shooter: Shulker, target: Player) {
        val service = MobService.getInstance(context.plugin) ?: return
        val damage = (context.definition.attack * homingProjectileDamageMultiplier).coerceAtLeast(0.0)
        val offsets = listOf(-18.0, 0.0, 18.0)
        offsets.forEachIndexed { index, yawOffset ->
            org.bukkit.Bukkit.getScheduler().runTaskLater(context.plugin, Runnable {
                val liveShooter = org.bukkit.Bukkit.getEntity(shooter.uniqueId) as? Shulker ?: return@Runnable
                val liveTarget = org.bukkit.Bukkit.getEntity(target.uniqueId) as? Player ?: return@Runnable
                if (!liveShooter.isValid || liveShooter.isDead || !liveTarget.isValid || liveTarget.isDead || liveShooter.world.uid != liveTarget.world.uid) {
                    return@Runnable
                }
                spawnVisualBullet(liveShooter)
                launchHomingShot(context, service, liveShooter, liveTarget, damage, yawOffset)
            }, index.toLong() * 3L)
        }
        shooter.world.playSound(shooter.location, org.bukkit.Sound.ENTITY_SHULKER_SHOOT, 1.0f, 1.12f)
        shooter.world.playSound(shooter.location, org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.0f)
    }

    private fun launchHomingShot(
        context: MobRuntimeContext,
        service: MobService,
        shooter: Shulker,
        target: Player,
        damage: Double,
        yawOffsetDegrees: Double
    ) {
        val world = shooter.world
        var position = shooter.eyeLocation.clone().add(shooter.location.direction.clone().multiply(0.45))
        val initial = target.eyeLocation.toVector().subtract(position.toVector())
        var velocity = if (initial.lengthSquared() > 0.0001) {
            rotateYaw(initial.normalize(), yawOffsetDegrees).multiply(homingProjectileSpeedPerTick)
        } else {
            rotateYaw(shooter.location.direction.clone().normalize(), yawOffsetDegrees).multiply(homingProjectileSpeedPerTick)
        }

        object : BukkitRunnable() {
            var life = ShulkerProjectileConfig.PROJECTILE_LIFETIME_TICKS

            override fun run() {
                if (!shooter.isValid || shooter.isDead || life <= 0L) {
                    cancel()
                    return
                }

                val liveTarget = org.bukkit.Bukkit.getEntity(target.uniqueId) as? Player
                if (liveTarget != null && liveTarget.isValid && !liveTarget.isDead && liveTarget.world.uid == world.uid) {
                    val desired = liveTarget.eyeLocation.toVector().subtract(position.toVector())
                    if (desired.lengthSquared() > 0.0001) {
                        val wanted = desired.normalize().multiply(homingProjectileSpeedPerTick)
                        velocity = velocity.multiply(1.0 - homingStrength).add(wanted.multiply(homingStrength)).normalize().multiply(homingProjectileSpeedPerTick)
                    }
                }

                val noisy = velocity.add(randomNoiseVector().multiply(homingNoiseStrength))
                velocity = if (noisy.lengthSquared() > 0.0001) noisy.normalize().multiply(homingProjectileSpeedPerTick) else Vector(0.0, 0.0, 0.0)

                val blockHit = world.rayTraceBlocks(position, velocity.clone().normalize(), velocity.length(), FluidCollisionMode.NEVER, true)
                if (blockHit != null) {
                    world.spawnParticle(Particle.ENCHANTED_HIT, blockHit.hitPosition.toLocation(world), 8, 0.08, 0.08, 0.08, 0.0)
                    cancel()
                    return
                }

                val next = position.clone().add(velocity)
                val hit = world.getNearbyEntities(next, 0.52, 0.52, 0.52)
                    .asSequence()
                    .filterIsInstance<Player>()
                    .firstOrNull { it.isValid && !it.isDead && it.world.uid == world.uid }
                if (hit != null) {
                    if (damage > 0.0) {
                        service.issueDirectDamagePermit(shooter.uniqueId, hit.uniqueId)
                        hit.damage(damage, shooter)
                    }
                    world.spawnParticle(Particle.ENCHANTED_HIT, hit.location.clone().add(0.0, 1.0, 0.0), 14, 0.1, 0.1, 0.1, 0.0)
                    cancel()
                    return
                }

                position = next
                world.spawnParticle(Particle.END_ROD, position, 1, 0.01, 0.01, 0.01, 0.0)
                life -= 1L
            }
        }.runTaskTimer(context.plugin, 1L, 1L)
    }

    private fun fireCloseDefense(context: MobRuntimeContext, shooter: Shulker) {
        val service = MobService.getInstance(context.plugin) ?: return
        val damage = (context.definition.attack * curveProjectileDamageMultiplier).coerceAtLeast(0.0)
        val world = shooter.world
        val base = shooter.eyeLocation.clone().add(0.0, -0.15, 0.0)
        repeat(8) { index ->
            val angle = (PI * 2.0 * index.toDouble() / 8.0)
            val horizontal = Vector(cos(angle), 0.0, sin(angle)).normalize().multiply(curveProjectileSpeedPerTick)
            val curveSign = if (index % 2 == 0) 1.0 else -1.0
            launchCurvedShot(context, service, shooter, base.clone(), horizontal, damage, curveSign)
        }
        world.playSound(shooter.location, org.bukkit.Sound.BLOCK_SHULKER_BOX_CLOSE, 0.9f, 1.05f)
        world.playSound(shooter.location, org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.65f, 1.4f)
    }

    private fun launchCurvedShot(
        context: MobRuntimeContext,
        service: MobService,
        shooter: Shulker,
        origin: org.bukkit.Location,
        initialVelocity: Vector,
        damage: Double,
        curveSign: Double
    ) {
        val world = shooter.world
        var position = origin
        var velocity = initialVelocity

        object : BukkitRunnable() {
            var life = ShulkerProjectileConfig.PROJECTILE_LIFETIME_TICKS

            override fun run() {
                if (!shooter.isValid || shooter.isDead || life <= 0L) {
                    cancel()
                    return
                }

                velocity = rotateYaw(velocity, curveSign * 6.0).normalize().multiply(curveProjectileSpeedPerTick)
                val blockHit = world.rayTraceBlocks(position, velocity.clone().normalize(), velocity.length(), FluidCollisionMode.NEVER, true)
                if (blockHit != null) {
                    world.spawnParticle(Particle.ENCHANTED_HIT, blockHit.hitPosition.toLocation(world), 8, 0.07, 0.07, 0.07, 0.0)
                    cancel()
                    return
                }

                val next = position.clone().add(velocity)
                val hit = world.getNearbyEntities(next, 0.48, 0.48, 0.48)
                    .asSequence()
                    .filterIsInstance<Player>()
                    .firstOrNull { it.isValid && !it.isDead && it.world.uid == world.uid }
                if (hit != null) {
                    if (damage > 0.0) {
                        service.issueDirectDamagePermit(shooter.uniqueId, hit.uniqueId)
                        hit.damage(damage, shooter)
                    }
                    world.spawnParticle(Particle.ENCHANTED_HIT, hit.location.clone().add(0.0, 1.0, 0.0), 12, 0.09, 0.09, 0.09, 0.0)
                    cancel()
                    return
                }

                position = next
                world.spawnParticle(Particle.PORTAL, position, 2, 0.02, 0.02, 0.02, 0.01)
                life -= 1L
            }
        }.runTaskTimer(context.plugin, 1L, 1L)
    }

    private fun resolveCombatTarget(shulker: Shulker): Player? {
        val fixed = MobAbilityUtils.resolveTarget(shulker) as? Player
        if (fixed != null && fixed.isValid && !fixed.isDead && fixed.world.uid == shulker.world.uid) return fixed
        return shulker.getNearbyEntities(24.0, 24.0, 24.0)
            .asSequence()
            .mapNotNull { it as? Player }
            .filter { it.isValid && !it.isDead && it.world.uid == shulker.world.uid }
            .minByOrNull { it.location.distanceSquared(shulker.location) }
    }

    private fun spawnVisualBullet(shooter: Shulker) {
        val bullet = shooter.world.spawn(shooter.eyeLocation.clone(), ShulkerBullet::class.java)
        bullet.shooter = shooter
        bullet.addScoreboardTag(VISUAL_SHULKER_BULLET_TAG)
        bullet.target = resolveCombatTarget(shooter)
        org.bukkit.Bukkit.getScheduler().runTaskLater(
            org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(javaClass),
            Runnable {
                if (bullet.isValid && !bullet.isDead) {
                    bullet.remove()
                }
            },
            ShulkerProjectileConfig.PROJECTILE_LIFETIME_TICKS
        )
    }

    private fun renderProximityZone(shulker: Shulker) {
        val world = shulker.world
        val center = shulker.location.clone().add(0.0, 0.8, 0.0)
        val points = 14
        val radius = closeDefenseRange
        repeat(points) { index ->
            val angle = (2.0 * PI * index.toDouble() / points.toDouble()) + (shulker.ticksLived.toDouble() * 0.07)
            val point = center.clone().add(cos(angle) * radius, 0.0, sin(angle) * radius)
            world.spawnParticle(Particle.END_ROD, point, 1, 0.0, 0.0, 0.0, 0.0)
            world.spawnParticle(Particle.PORTAL, point, 1, 0.01, 0.01, 0.01, 0.01)
        }
    }

    private fun rotateYaw(vector: Vector, degrees: Double): Vector {
        if (degrees == 0.0) return vector
        val radians = Math.toRadians(degrees)
        val c = cos(radians)
        val s = sin(radians)
        return Vector(vector.x * c - vector.z * s, vector.y, vector.x * s + vector.z * c)
    }

    private fun randomNoiseVector(): Vector {
        return Vector(
            Random.nextDouble(-1.0, 1.0),
            Random.nextDouble(-0.7, 0.7),
            Random.nextDouble(-1.0, 1.0)
        )
    }

    private companion object {
        const val VISUAL_SHULKER_BULLET_TAG = "cc.mob.visual_shulker_bullet"
        const val SEARCH_PHASE_VARIANTS = 16
    }
}
