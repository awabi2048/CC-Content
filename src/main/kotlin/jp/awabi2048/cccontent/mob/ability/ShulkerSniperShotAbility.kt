package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobDamagedContext
import jp.awabi2048.cccontent.mob.MobRuntimeContext
import jp.awabi2048.cccontent.mob.MobService
import org.bukkit.FluidCollisionMode
import org.bukkit.Particle
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Shulker
import org.bukkit.entity.ShulkerBullet
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Vector
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToLong
import kotlin.random.Random

class ShulkerSniperShotAbility(
    override val id: String,
    private val cooldownTicks: Long = 52L,
    private val projectileDamageMultiplier: Double = 0.95,
    private val projectileSpeedPerTick: Double = 0.36,
    private val homingStrength: Double = 0.2,
    private val noiseStrength: Double = 0.025,
    private val maxLifeTicks: Long = 60L,
    private val hitRadius: Double = 2.25,
    private val allyTeleportSearchRadius: Double = 72.0
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
        if (!context.isCombatActive()) return
        if (rt.cooldownRemainingTicks > 0L) return
        if (!MobAbilityUtils.shouldProcessSearchTick(
                context.activeMob.tickCount,
                context.loadSnapshot.searchIntervalMultiplier,
                rt.searchPhaseOffsetSteps,
                baseStepTicks = 1L
            )) return
        if (Random.nextDouble() < context.loadSnapshot.abilityExecutionSkipChance) return

        val target = resolveCombatTarget(shulker) ?: return
        if (!target.isValid || target.isDead || target.world.uid != shulker.world.uid) return
        if (!shulker.hasLineOfSight(target)) return

        val service = MobService.getInstance(context.plugin) ?: return
        val damage = (context.definition.attack * projectileDamageMultiplier).coerceAtLeast(0.0)
        launchShot(context, service, shulker, target, damage)
        shulker.world.playSound(shulker.location, org.bukkit.Sound.ENTITY_SHULKER_SHOOT, 1.0f, 1.5f)
        shulker.world.playSound(shulker.location, org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.75f, 1.15f)

        val baseCooldown = cooldownTicks.coerceAtLeast(1L)
        rt.cooldownRemainingTicks = (baseCooldown * context.loadSnapshot.abilityCooldownMultiplier).roundToLong().coerceAtLeast(baseCooldown)
    }

    override fun onDamaged(context: MobDamagedContext, runtime: MobAbilityRuntime?) {
        if (context.event.isCancelled) return
        val service = MobService.getInstance(context.plugin) ?: return
        val self = context.entity
        val wave = context.activeMob.metadata["wave"]
        val allies = self.world.getNearbyEntities(self.location, allyTeleportSearchRadius, allyTeleportSearchRadius, allyTeleportSearchRadius)
            .asSequence()
            .filterIsInstance<LivingEntity>()
            .filter { it.uniqueId != self.uniqueId && it.isValid && !it.isDead }
            .mapNotNull { candidate ->
                val active = service.getActiveMob(candidate.uniqueId) ?: return@mapNotNull null
                if (active.sessionKey != context.sessionKey) return@mapNotNull null
                if (wave != null && active.metadata["wave"] != wave) return@mapNotNull null
                candidate
            }
            .toList()
        if (allies.isEmpty()) return

        val picked = allies[Random.nextInt(allies.size)]
        val destination = EndThemeEffects.findNearbyTeleportLocation(picked.location, 2.2, attempts = 16)
            ?: picked.location.clone().add(0.0, 0.3, 0.0)
        EndThemeEffects.playTeleportSound(self.world, self.location)
        self.world.spawnParticle(Particle.PORTAL, self.location.clone().add(0.0, 1.0, 0.0), 26, 0.5, 0.5, 0.5, 0.02)
        self.teleport(destination)
        EndThemeEffects.playTeleportSound(self.world, destination)
    }

    private fun launchShot(
        context: MobRuntimeContext,
        service: MobService,
        shooter: Shulker,
        target: Player,
        damage: Double
    ) {
        val world = shooter.world
        spawnVisualBullet(world, shooter)
        var position = shooter.eyeLocation.clone().add(shooter.location.direction.clone().multiply(0.5))
        var velocity = target.eyeLocation.toVector().subtract(position.toVector())
            .takeIf { it.lengthSquared() > 0.0001 }
            ?.normalize()
            ?.multiply(projectileSpeedPerTick)
            ?: shooter.location.direction.clone().normalize().multiply(projectileSpeedPerTick)

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
                        val wanted = desired.normalize().multiply(projectileSpeedPerTick)
                        velocity = velocity.multiply((1.0 - homingStrength).coerceIn(0.0, 1.0))
                            .add(wanted.multiply(homingStrength.coerceIn(0.0, 1.0)))
                    }
                }

                val noisy = velocity.add(randomNoiseVector().multiply(noiseStrength))
                velocity = if (noisy.lengthSquared() > 0.0001) noisy.normalize().multiply(projectileSpeedPerTick) else Vector(0.0, 0.0, 0.0)

                val blockHit = world.rayTraceBlocks(position, velocity.clone().normalize(), velocity.length(), FluidCollisionMode.NEVER, true)
                if (blockHit != null) {
                    world.spawnParticle(Particle.ENCHANTED_HIT, blockHit.hitPosition.toLocation(world), 10, 0.08, 0.08, 0.08, 0.0)
                    cancel()
                    return
                }

                val next = position.clone().add(velocity)
                val hit = world.getNearbyEntities(next, hitRadius, hitRadius, hitRadius)
                    .asSequence()
                    .filterIsInstance<Player>()
                    .firstOrNull { it.isValid && !it.isDead && it.world.uid == world.uid }

                if (hit != null) {
                    if (damage > 0.0) {
                        service.issueDirectDamagePermit(shooter.uniqueId, hit.uniqueId)
                        hit.damage(damage, shooter)
                    }
                    world.spawnParticle(Particle.ENCHANTED_HIT, hit.location.clone().add(0.0, 1.0, 0.0), 24, 0.16, 0.16, 0.16, 0.0)
                    world.playSound(hit.location, org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.9f, 1.5f)
                    cancel()
                    return
                }

                position = next
                world.spawnParticle(Particle.END_ROD, position, 2, 0.03, 0.03, 0.03, 0.0)
                world.spawnParticle(Particle.ENCHANTED_HIT, position, 3, 0.03, 0.03, 0.03, 0.0)
                life -= 1L
            }
        }.runTaskTimer(context.plugin, 1L, 1L)
    }

    private fun resolveCombatTarget(shulker: Shulker): Player? {
        val fixed = MobAbilityUtils.resolveTarget(shulker) as? Player
        if (fixed != null && fixed.isValid && !fixed.isDead && fixed.world.uid == shulker.world.uid) return fixed
        return shulker.getNearbyEntities(28.0, 28.0, 28.0)
            .asSequence()
            .mapNotNull { it as? Player }
            .filter { it.isValid && !it.isDead && it.world.uid == shulker.world.uid }
            .minByOrNull { it.location.distanceSquared(shulker.location) }
    }

    private fun spawnVisualBullet(world: org.bukkit.World, shooter: Shulker) {
        val bullet = world.spawn(shooter.eyeLocation.clone(), ShulkerBullet::class.java)
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
        val radius = 3.2
        repeat(points) { index ->
            val angle = (2.0 * PI * index.toDouble() / points.toDouble()) + (shulker.ticksLived.toDouble() * 0.07)
            val point = center.clone().add(cos(angle) * radius, 0.0, sin(angle) * radius)
            world.spawnParticle(Particle.END_ROD, point, 1, 0.0, 0.0, 0.0, 0.0)
            world.spawnParticle(Particle.PORTAL, point, 1, 0.01, 0.01, 0.01, 0.01)
        }
    }

    private fun randomNoiseVector(): Vector {
        return Vector(
            Random.nextDouble(-1.0, 1.0),
            Random.nextDouble(-0.65, 0.65),
            Random.nextDouble(-1.0, 1.0)
        )
    }

    private companion object {
        const val VISUAL_SHULKER_BULLET_TAG = "cc.mob.visual_shulker_bullet"
        const val SEARCH_PHASE_VARIANTS = 16
    }
}
