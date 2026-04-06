package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobRuntimeContext
import jp.awabi2048.cccontent.mob.MobService
import org.bukkit.FluidCollisionMode
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.entity.Shulker
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Vector
import kotlin.math.roundToLong
import kotlin.random.Random

class ShulkerBurstShotAbility(
    override val id: String,
    private val cooldownTicks: Long = 46L,
    private val burstCount: Int = 3,
    private val burstIntervalTicks: Long = 5L,
    private val projectileDamageMultiplier: Double = 0.7,
    private val projectileSpeedPerTick: Double = 0.25,
    private val homingStrength: Double = 0.15,
    private val noiseStrength: Double = 0.15,
    private val maxLifeTicks: Long = 70L,
    private val hitRadius: Double = 0.58,
    private val launchVelocityY: Double = 0.70
) : MobAbility {

    data class Runtime(
        var cooldownRemainingTicks: Long = 0L,
        var searchPhaseOffsetSteps: Int = 0
    ) : MobAbilityRuntime

    override fun createRuntime(context: jp.awabi2048.cccontent.mob.MobSpawnContext): MobAbilityRuntime {
        return Runtime(searchPhaseOffsetSteps = Random.nextInt(0, SEARCH_PHASE_VARIANTS))
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val shulker = context.entity as? Shulker ?: return
        val rt = runtime as? Runtime ?: return
        if (rt.cooldownRemainingTicks > 0L) {
            rt.cooldownRemainingTicks = (rt.cooldownRemainingTicks - context.tickDelta).coerceAtLeast(0L)
        }
        if (!context.isCombatActive()) {
            return
        }
        if (rt.cooldownRemainingTicks > 0L) {
            return
        }
        if (!MobAbilityUtils.shouldProcessSearchTick(
                context.activeMob.tickCount,
                context.loadSnapshot.searchIntervalMultiplier,
                rt.searchPhaseOffsetSteps,
                baseStepTicks = 1L
            )) {
            return
        }
        if (Random.nextDouble() < context.loadSnapshot.abilityExecutionSkipChance) {
            return
        }

        val target = resolveCombatTarget(shulker) ?: return
        if (!target.isValid || target.isDead || target.world.uid != shulker.world.uid) {
            return
        }
        if (!shulker.hasLineOfSight(target)) {
            return
        }

        val mobService = MobService.getInstance(context.plugin) ?: return
        val finalDamage = (context.definition.attack * projectileDamageMultiplier).coerceAtLeast(0.0)

        repeat(burstCount.coerceAtLeast(1)) { index ->
            val delay = burstIntervalTicks.coerceAtLeast(1L) * index.toLong()
            org.bukkit.Bukkit.getScheduler().runTaskLater(context.plugin, Runnable {
                val shooter = org.bukkit.Bukkit.getEntity(shulker.uniqueId) as? Shulker ?: return@Runnable
                val victim = org.bukkit.Bukkit.getEntity(target.uniqueId) as? Player ?: return@Runnable
                if (!shooter.isValid || shooter.isDead || !victim.isValid || victim.isDead || shooter.world.uid != victim.world.uid) {
                    return@Runnable
                }
                launchCustomHomingShot(context, mobService, shooter, victim, finalDamage)
            }, delay)
        }

        shulker.world.playSound(shulker.location, org.bukkit.Sound.ENTITY_SHULKER_SHOOT, 1.0f, 1.25f)

        val baseCooldown = cooldownTicks.coerceAtLeast(1L)
        rt.cooldownRemainingTicks = (baseCooldown * context.loadSnapshot.abilityCooldownMultiplier).roundToLong().coerceAtLeast(baseCooldown)
    }

    private companion object {
        const val SEARCH_PHASE_VARIANTS = 16
    }

    private fun launchCustomHomingShot(
        context: MobRuntimeContext,
        mobService: MobService,
        shooter: Shulker,
        target: Player,
        damage: Double
    ) {
        val world = shooter.world
        var position = shooter.eyeLocation.clone().add(shooter.location.direction.clone().multiply(0.45))
        var velocity = target.eyeLocation.toVector().subtract(position.toVector())
            .takeIf { it.lengthSquared() > 0.0001 }
            ?.normalize()
            ?.multiply(projectileSpeedPerTick)
            ?: shooter.location.direction.clone().normalize().multiply(projectileSpeedPerTick)

        world.playSound(position, org.bukkit.Sound.ENTITY_SHULKER_BULLET_HURT, 0.65f, 1.55f)

        object : BukkitRunnable() {
            var life = maxLifeTicks

            override fun run() {
                if (!shooter.isValid || shooter.isDead || life <= 0L) {
                    cancel()
                    return
                }

                val liveTarget = org.bukkit.Bukkit.getEntity(target.uniqueId) as? Player
                if (liveTarget != null && liveTarget.isValid && !liveTarget.isDead && liveTarget.world.uid == world.uid) {
                    val desired = liveTarget.eyeLocation.toVector().subtract(position.toVector())
                    if (desired.lengthSquared() > 0.0001) {
                        val wanted = desired.normalize().multiply(projectileSpeedPerTick)
                        velocity = velocity.multiply((1.0 - homingStrength).coerceIn(0.0, 1.0))
                            .add(wanted.multiply(homingStrength.coerceIn(0.0, 1.0)))
                    }
                }

                val noisy = velocity.add(randomNoiseVector().multiply(noiseStrength))
                velocity = if (noisy.lengthSquared() > 0.0001) {
                    noisy.normalize().multiply(projectileSpeedPerTick)
                } else {
                    Vector(0.0, 0.0, 0.0)
                }

                val blockHit = world.rayTraceBlocks(
                    position,
                    velocity.clone().normalize(),
                    velocity.length(),
                    FluidCollisionMode.NEVER,
                    true
                )
                if (blockHit != null) {
                    world.spawnParticle(Particle.ENCHANTED_HIT, blockHit.hitPosition.toLocation(world), 8, 0.08, 0.08, 0.08, 0.0)
                    world.playSound(blockHit.hitPosition.toLocation(world), org.bukkit.Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.5f, 2.0f)
                    cancel()
                    return
                }

                val next = position.clone().add(velocity)
                val hit = world.getNearbyEntities(next, hitRadius, hitRadius, hitRadius)
                    .asSequence()
                    .filterIsInstance<Player>()
                    .firstOrNull { candidate ->
                        candidate.isValid && !candidate.isDead && candidate.world.uid == world.uid
                    }

                if (hit != null) {
                    if (damage > 0.0) {
                        mobService.issueDirectDamagePermit(shooter.uniqueId, hit.uniqueId)
                        hit.damage(damage, shooter)
                    }
                    val launched = hit.velocity.clone().apply {
                        y = launchVelocityY
                    }
                    hit.velocity = launched
                    world.spawnParticle(Particle.ENCHANTED_HIT, hit.location.clone().add(0.0, 1.0, 0.0), 16, 0.12, 0.12, 0.12, 0.0)
                    world.playSound(hit.location, org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.75f, 1.9f)
                    cancel()
                    return
                }

                position = next
                world.spawnParticle(Particle.END_ROD, position, 1, 0.01, 0.01, 0.01, 0.0)
                world.spawnParticle(Particle.ENCHANTED_HIT, position, 2, 0.03, 0.03, 0.03, 0.0)
                life -= 1L
            }
        }.runTaskTimer(context.plugin, 1L, 1L)
    }

    private fun randomNoiseVector(): Vector {
        return Vector(
            Random.nextDouble(-1.0, 1.0),
            Random.nextDouble(-0.75, 0.75),
            Random.nextDouble(-1.0, 1.0)
        )
    }

    private fun resolveCombatTarget(shulker: Shulker): Player? {
        val fixedTarget = MobAbilityUtils.resolveTarget(shulker) as? Player
        if (fixedTarget != null && fixedTarget.isValid && !fixedTarget.isDead && fixedTarget.world.uid == shulker.world.uid) {
            return fixedTarget
        }
        return shulker.getNearbyEntities(24.0, 24.0, 24.0)
            .asSequence()
            .mapNotNull { it as? Player }
            .filter { it.isValid && !it.isDead && it.world.uid == shulker.world.uid }
            .minByOrNull { it.location.distanceSquared(shulker.location) }
    }
}
