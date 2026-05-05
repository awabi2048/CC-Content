package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobDamagedContext
import jp.awabi2048.cccontent.mob.MobRuntimeContext
import jp.awabi2048.cccontent.mob.MobService
import org.bukkit.Color
import org.bukkit.FluidCollisionMode
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.block.BlockFace
import org.bukkit.entity.AbstractArrow
import org.bukkit.entity.EntityType
import org.bukkit.entity.Interaction
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Shulker
import org.bukkit.persistence.PersistentDataType
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Vector
import java.util.UUID
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class ShulkerWarpSniperAbility(
    override val id: String,
    private val attackIntervalTicks: Long = 60L,
    private val warpIntervalTicks: Long = 100L,
    private val warpChance: Double = 0.3,
    private val projectileDamage: Double = 12.0,
    private val projectileSpeedPerTick: Double = 0.36,
    private val homingStrength: Double = 0.14,
    private val noiseStrength: Double = 0.025,
    private val hitRadius: Double = 0.6,
    private val arrowDeflectRadius: Double = 0.8,
    private val explosionRadius: Double = 2.0,
    private val explosionDamage: Double = 8.0,
    private val allySearchRadius: Double = 72.0
) : MobAbility {

    data class Runtime(
        var attackCooldownTicks: Long = 0L,
        var warpCooldownTicks: Long = 100L,
        var searchPhaseOffsetSteps: Int = 0
    ) : MobAbilityRuntime

    private data class ProjectileState(
        val shooterId: UUID,
        val targetId: UUID,
        val interactionId: UUID,
        val damage: Double,
        var previousPosition: Location,
        var position: Location,
        var velocity: Vector,
        var life: Long
    )

    override fun tickIntervalTicks(): Long = 1L

    override fun createRuntime(context: jp.awabi2048.cccontent.mob.MobSpawnContext): MobAbilityRuntime {
        return Runtime(
            attackCooldownTicks = Random.nextLong(0L, attackIntervalTicks.coerceAtLeast(1L)),
            warpCooldownTicks = warpIntervalTicks.coerceAtLeast(1L),
            searchPhaseOffsetSteps = Random.nextInt(0, SEARCH_PHASE_VARIANTS)
        )
    }

    override fun onSpawn(context: jp.awabi2048.cccontent.mob.MobSpawnContext, runtime: MobAbilityRuntime?) {
        val shulker = context.entity as? Shulker ?: return
        shulker.attachedFace = BlockFace.DOWN
        shulker.setAI(true)
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val shulker = context.entity as? Shulker ?: return
        val rt = runtime as? Runtime ?: return

        rt.attackCooldownTicks = (rt.attackCooldownTicks - context.tickDelta).coerceAtLeast(0L)
        rt.warpCooldownTicks = (rt.warpCooldownTicks - context.tickDelta).coerceAtLeast(0L)

        if (!context.isCombatActive()) return

        if (rt.warpCooldownTicks <= 0L) {
            rt.warpCooldownTicks = warpIntervalTicks.coerceAtLeast(1L)
            tryWarp(context, shulker)
        }

        if (rt.attackCooldownTicks > 0L) return
        if (shulker.peek < OPEN_PEEK_THRESHOLD) return
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
        fireThreeWay(context, service, shulker, target, projectileDamage)
        shulker.world.playSound(shulker.location, Sound.ENTITY_SHULKER_SHOOT, 1.0f, 0.75f)
        shulker.world.playSound(shulker.location, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.75f, 0.75f)
        shulker.world.playSound(shulker.location, "minecraft:entity.blaze.shoot", 1.0f, 1.2f)
        rt.attackCooldownTicks = attackIntervalTicks.coerceAtLeast(1L)
    }

    override fun onDamaged(context: MobDamagedContext, runtime: MobAbilityRuntime?) {
        if (context.event.isCancelled) return
        val shulker = context.entity as? Shulker ?: return
        shulker.world.playSound(shulker.location, "minecraft:entity.blaze.hurt", 1.0f, 0.8f)
        tryWarp(context, shulker)
    }

    private fun tryWarp(context: MobRuntimeContext, shulker: Shulker) {
        if (Random.nextDouble() >= warpChance) return
        val destination = resolveAllyDestination(context, shulker) ?: return
        warp(shulker, destination)
    }

    private fun tryWarp(context: MobDamagedContext, shulker: Shulker) {
        if (Random.nextDouble() >= warpChance) return
        val destination = resolveAllyDestination(context, shulker) ?: return
        warp(shulker, destination)
    }

    private fun resolveAllyDestination(context: MobRuntimeContext, shulker: Shulker): Location? {
        return resolveAllyDestination(
            shulker = shulker,
            service = MobService.getInstance(context.plugin) ?: return null,
            sessionKey = context.sessionKey,
            wave = context.activeMob.metadata["wave"]
        )
    }

    private fun resolveAllyDestination(context: MobDamagedContext, shulker: Shulker): Location? {
        return resolveAllyDestination(
            shulker = shulker,
            service = MobService.getInstance(context.plugin) ?: return null,
            sessionKey = context.sessionKey,
            wave = context.activeMob.metadata["wave"]
        )
    }

    private fun resolveAllyDestination(
        shulker: Shulker,
        service: MobService,
        sessionKey: String,
        wave: String?
    ): Location? {
        val allies = shulker.world.getNearbyEntities(shulker.location, allySearchRadius, allySearchRadius, allySearchRadius)
            .asSequence()
            .filterIsInstance<LivingEntity>()
            .filter { it.uniqueId != shulker.uniqueId && it.isValid && !it.isDead && it.world.uid == shulker.world.uid }
            .mapNotNull { candidate ->
                val active = service.getActiveMob(candidate.uniqueId) ?: return@mapNotNull null
                if (active.sessionKey != sessionKey) return@mapNotNull null
                if (wave != null && active.metadata["wave"] != wave) return@mapNotNull null
                candidate
            }
            .toList()
        if (allies.isEmpty()) return null

        val picked = allies[Random.nextInt(allies.size)]
        return EndThemeEffects.findNearbyTeleportLocation(picked.location, 2.2, attempts = 16)
            ?: picked.location.clone().add(0.0, 0.3, 0.0)
    }

    private fun warp(shulker: Shulker, destination: Location) {
        val target = destination.clone()
        target.yaw = shulker.location.yaw
        target.pitch = 0.0f

        playTeleportSound(shulker.location)
        shulker.world.spawnParticle(Particle.PORTAL, shulker.location.clone().add(0.0, 1.0, 0.0), 26, 0.5, 0.5, 0.5, 0.02)
        shulker.attachedFace = BlockFace.DOWN
        shulker.addScoreboardTag(ALLOW_CUSTOM_TELEPORT_TAG)
        shulker.teleport(target)
        shulker.removeScoreboardTag(ALLOW_CUSTOM_TELEPORT_TAG)
        shulker.attachedFace = BlockFace.DOWN
        playTeleportSound(target)
        shulker.world.spawnParticle(Particle.PORTAL, target.clone().add(0.0, 1.0, 0.0), 26, 0.5, 0.5, 0.5, 0.02)
    }

    private fun playTeleportSound(location: Location) {
        location.world?.playSound(location, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.75f)
    }

    private fun fireThreeWay(
        context: MobRuntimeContext,
        service: MobService,
        shooter: Shulker,
        target: Player,
        damage: Double
    ) {
        val origin = shooter.eyeLocation.clone().add(shooter.location.direction.clone().multiply(0.5))
        val baseDirection = target.eyeLocation.toVector().subtract(origin.toVector())
            .takeIf { it.lengthSquared() > 0.0001 }
            ?.normalize()
            ?: shooter.location.direction.clone().normalize()

        listOf(-60.0, 0.0, 60.0).forEach { yawOffset ->
            val velocity = rotateYaw(baseDirection.clone(), yawOffset).normalize().multiply(projectileSpeedPerTick)
            launchShot(context, service, shooter, target, damage, origin.clone(), velocity)
        }
    }

    private fun launchShot(
        context: MobRuntimeContext,
        service: MobService,
        shooter: Shulker,
        target: Player,
        damage: Double,
        origin: Location,
        initialVelocity: Vector
    ) {
        val interaction = spawnInteraction(context, origin) ?: return
        val state = ProjectileState(
            shooterId = shooter.uniqueId,
            targetId = target.uniqueId,
            interactionId = interaction.uniqueId,
            damage = damage,
            previousPosition = origin.clone(),
            position = origin.clone(),
            velocity = initialVelocity,
            life = PROJECTILE_LIFETIME_TICKS
        )
        activeProjectiles[interaction.uniqueId] = state

        object : BukkitRunnable() {
            override fun run() {
                if (!shooter.isValid || shooter.isDead) {
                    cleanup(state)
                    cancel()
                    return
                }

                val world = state.position.world ?: run {
                    cleanup(state)
                    cancel()
                    return
                }

                if (state.life <= 0L) {
                    explodeOnBlockHit(context, service, shooter, state.position)
                    cleanup(state)
                    cancel()
                    return
                }

                val liveTarget = org.bukkit.Bukkit.getEntity(state.targetId) as? Player
                if (state.life > HOMING_END_LIFE_TICKS && liveTarget != null && liveTarget.isValid && !liveTarget.isDead && liveTarget.world.uid == world.uid) {
                    val desired = liveTarget.eyeLocation.toVector().subtract(state.position.toVector())
                    if (desired.lengthSquared() > 0.0001) {
                        val wanted = desired.normalize().multiply(projectileSpeedPerTick)
                        state.velocity = state.velocity.multiply((1.0 - homingStrength).coerceIn(0.0, 1.0))
                            .add(wanted.multiply(homingStrength.coerceIn(0.0, 1.0)))
                    }
                }

                val noisy = state.velocity.add(randomNoiseVector().multiply(noiseStrength))
                state.velocity = if (noisy.lengthSquared() > 0.0001) {
                    noisy.normalize().multiply(projectileSpeedPerTick)
                } else {
                    Vector(0.0, 0.0, 0.0)
                }

                val direction = state.velocity.clone().normalize()
                val blockHit = world.rayTraceBlocks(state.position, direction, state.velocity.length(), FluidCollisionMode.NEVER, true)
                if (blockHit != null) {
                    explodeOnBlockHit(context, service, shooter, blockHit.hitPosition.toLocation(world))
                    cleanup(state)
                    cancel()
                    return
                }

                state.previousPosition = state.position.clone()
                state.position = state.position.clone().add(state.velocity)
                renderProjectile(state.position)
                moveInteraction(state)

                if (tryDeflectByArrow(state)) {
                    world.spawnParticle(Particle.ENCHANTED_HIT, state.position, 18, 0.15, 0.15, 0.15, 0.0)
                    world.playSound(state.position, Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.9f, 0.75f)
                    cleanup(state)
                    cancel()
                    return
                }

                val hit = findHitPlayer(world, state.previousPosition.toVector(), state.position.toVector())
                if (hit != null) {
                    hitPlayer(service, shooter, hit, state.damage)
                    world.spawnParticle(Particle.ENCHANTED_HIT, hit.location.clone().add(0.0, 1.0, 0.0), 24, 0.16, 0.16, 0.16, 0.0)
                    world.playSound(hit.location, Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.9f, 0.75f)
                    cleanup(state)
                    cancel()
                    return
                }

                state.life -= 1L
            }
        }.runTaskTimer(context.plugin, 1L, 1L)
    }

    private fun spawnInteraction(context: MobRuntimeContext, location: Location): Interaction? {
        val world = location.world ?: return null
        val interaction = world.spawnEntity(location, EntityType.INTERACTION) as Interaction
        interaction.interactionWidth = 0.7f
        interaction.interactionHeight = 0.7f
        interaction.isPersistent = false
        interaction.addScoreboardTag(PROJECTILE_INTERACTION_TAG)
        interaction.persistentDataContainer.set(
            NamespacedKey(context.plugin, PROJECTILE_INTERACTION_KEY),
            PersistentDataType.BYTE,
            1
        )
        return interaction
    }

    private fun moveInteraction(state: ProjectileState) {
        val interaction = org.bukkit.Bukkit.getEntity(state.interactionId) as? Interaction ?: return
        if (interaction.isValid && !interaction.isDead) {
            interaction.teleport(state.position)
        }
    }

    private fun tryDeflectByArrow(state: ProjectileState): Boolean {
        val world = state.position.world ?: return false
        val center = midpoint(state.previousPosition.toVector(), state.position.toVector()).toLocation(world)
        val arrows = world.getNearbyEntities(center, 1.5, 1.5, 1.5).asSequence()
            .filterIsInstance<AbstractArrow>()
            .filter { it.isValid && !it.isDead && it.world.uid == world.uid }
            .toList()
        for (arrow in arrows) {
            val arrowTo = arrow.location.toVector()
            val arrowFrom = arrowTo.clone().subtract(arrow.velocity)
            if (segmentsClose(state.previousPosition.toVector(), state.position.toVector(), arrowFrom, arrowTo, arrowDeflectRadius)) {
                arrow.remove()
                return true
            }
        }
        return false
    }

    private fun findHitPlayer(world: org.bukkit.World, from: Vector, to: Vector): Player? {
        val center = midpoint(from, to).toLocation(world)
        return world.getNearbyEntities(center, 1.6, 1.6, 1.6)
            .asSequence()
            .filterIsInstance<Player>()
            .filter { it.isValid && !it.isDead && it.world.uid == world.uid }
            .firstOrNull { player -> intersectsPlayer(player, from, to) }
    }

    private fun intersectsPlayer(player: Player, from: Vector, to: Vector): Boolean {
        val eye = player.eyeLocation.toVector()
        val body = player.location.clone().add(0.0, 0.9, 0.0).toVector()
        return pointDistanceToSegment(eye, from, to) <= hitRadius || pointDistanceToSegment(body, from, to) <= hitRadius
    }

    private fun hitPlayer(service: MobService, shooter: Shulker, hit: Player, damage: Double) {
        if (damage > 0.0) {
            service.issueDirectDamagePermit(shooter.uniqueId, hit.uniqueId)
            hit.damage(damage, shooter)
        }
        val velocity = hit.velocity.clone()
        velocity.y = maxOf(velocity.y, KNOCK_UP_VELOCITY)
        hit.velocity = velocity
    }

    private fun explodeOnBlockHit(context: MobRuntimeContext, service: MobService, shooter: Shulker, location: Location) {
        val world = location.world ?: return
        world.spawnParticle(Particle.EXPLOSION, location, 1, 0.0, 0.0, 0.0, 0.0)
        world.spawnParticle(Particle.ENCHANTED_HIT, location, 34, 0.7, 0.35, 0.7, 0.0)
        world.spawnParticle(Particle.DUST_COLOR_TRANSITION, location, 18, 0.55, 0.25, 0.55, 0.0, PROJECTILE_DUST)
        world.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 0.65f, 0.75f)

        val damage = explosionDamage.coerceAtLeast(0.0)
        if (damage <= 0.0) return
        world.getNearbyEntities(location, explosionRadius, explosionRadius, explosionRadius)
            .asSequence()
            .filterIsInstance<Player>()
            .filter { it.isValid && !it.isDead && it.world.uid == world.uid }
            .filter { isPlayerInExplosionRadius(it, location, explosionRadius) }
            .forEach { target ->
                service.issueDirectDamagePermit(shooter.uniqueId, target.uniqueId)
                target.damage(damage, shooter)
            }
    }

    private fun isPlayerInExplosionRadius(player: Player, center: Location, radius: Double): Boolean {
        val radiusSquared = radius * radius
        return player.location.distanceSquared(center) <= radiusSquared ||
            player.location.clone().add(0.0, 0.9, 0.0).distanceSquared(center) <= radiusSquared ||
            player.eyeLocation.distanceSquared(center) <= radiusSquared
    }

    private fun renderProjectile(position: Location) {
        val world = position.world ?: return
        world.spawnParticle(Particle.DUST_COLOR_TRANSITION, position, 5, 0.05, 0.05, 0.05, 0.0, PROJECTILE_DUST)
    }

    private fun cleanup(state: ProjectileState) {
        activeProjectiles.remove(state.interactionId)
        val interaction = org.bukkit.Bukkit.getEntity(state.interactionId)
        if (interaction != null && interaction.isValid) {
            interaction.remove()
        }
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
            Random.nextDouble(-0.65, 0.65),
            Random.nextDouble(-1.0, 1.0)
        )
    }

    companion object {
        const val OPEN_PEEK_THRESHOLD = 0.5f
        const val KNOCK_UP_VELOCITY = 0.6
        const val PROJECTILE_LIFETIME_TICKS = 40L
        const val HOMING_END_LIFE_TICKS = PROJECTILE_LIFETIME_TICKS - 20L
        const val ALLOW_CUSTOM_TELEPORT_TAG = "cc.mob.allow_shulker_warp_sniper_teleport"
        const val PROJECTILE_INTERACTION_TAG = "cc.mob.shulker_warp_sniper_projectile"
        const val PROJECTILE_INTERACTION_KEY = "shulker_warp_sniper_projectile"
        const val SEARCH_PHASE_VARIANTS = 16
        val PROJECTILE_DUST = Particle.DustTransition(Color.fromRGB(26, 0, 26), Color.fromRGB(230, 204, 230), 1.0f)
        private val activeProjectiles = mutableMapOf<UUID, ProjectileState>()

        fun handleProjectileInteraction(interaction: Interaction): Boolean {
            val state = activeProjectiles.remove(interaction.uniqueId) ?: return false
            val world = state.position.world
            if (world != null) {
                world.spawnParticle(Particle.ENCHANTED_HIT, state.position, 20, 0.18, 0.18, 0.18, 0.0)
                world.playSound(state.position, Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.9f, 0.75f)
            }
            if (interaction.isValid) {
                interaction.remove()
            }
            return true
        }

        private fun midpoint(a: Vector, b: Vector): Vector {
            return Vector((a.x + b.x) * 0.5, (a.y + b.y) * 0.5, (a.z + b.z) * 0.5)
        }

        private fun pointDistanceToSegment(point: Vector, from: Vector, to: Vector): Double {
            val segment = to.clone().subtract(from)
            val lengthSquared = segment.lengthSquared()
            if (lengthSquared <= 1.0e-6) return point.distance(from)
            val t = point.clone().subtract(from).dot(segment) / lengthSquared
            val closest = from.clone().add(segment.multiply(t.coerceIn(0.0, 1.0)))
            return point.distance(closest)
        }

        private fun segmentsClose(a1: Vector, a2: Vector, b1: Vector, b2: Vector, radius: Double): Boolean {
            return pointDistanceToSegment(a1, b1, b2) <= radius ||
                pointDistanceToSegment(a2, b1, b2) <= radius ||
                pointDistanceToSegment(b1, a1, a2) <= radius ||
                pointDistanceToSegment(b2, a1, a2) <= radius ||
                abs(a1.distance(a2) + b1.distance(b2)) <= 1.0e-6
        }
    }
}
