package jp.awabi2048.cccontent.mob.ability

import com.destroystokyo.paper.profile.ProfileProperty
import jp.awabi2048.cccontent.mob.MobDamagedContext
import jp.awabi2048.cccontent.mob.MobDeathContext
import jp.awabi2048.cccontent.mob.MobRuntimeContext
import jp.awabi2048.cccontent.mob.MobService
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.FluidCollisionMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Display
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Vex
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.atan2
import kotlin.math.roundToLong
import kotlin.math.sqrt
import kotlin.random.Random

class EnderEyeHunterAbility(
    override val id: String,
    private val orbitRadius: Double = 6.0,
    private val orbitHeightOffset: Double = 2.2,
    private val shotCooldownTicks: Long = 46L,
    private val projectileSpeedPerTick: Double = 0.275,
    private val projectileDamageMultiplier: Double = 0.8,
    private val homingStrength: Double = 0.2,
    private val maxLifeTicks: Long = 70L,
    private val teleportDistanceThreshold: Double = 18.0,
    private val shotWayCount: Int = 1,
    private val shotSpreadDegrees: Double = 0.0,
    private val headTextureValue: String = DEFAULT_EYE_HEAD_TEXTURE_VALUE,
    private val ambientParticle: Particle = Particle.PORTAL,
    private val ambientParticleCount: Int = 10,
    private val ambientParticleRadius: Double = 0.5
) : MobAbility {

    data class Runtime(
        var displayId: UUID? = null,
        var summonerId: UUID? = null,
        var cooldownRemainingTicks: Long = 0L,
        var teleportCooldownRemainingTicks: Long = 0L,
        var searchPhaseOffsetSteps: Int = 0,
        var movementState: EnderEyeGuidedState = EnderEyeGuidedState(ANGULAR_SPEED / 3.0)
    ) : MobAbilityRuntime

    override fun tickIntervalTicks(): Long = 1L

    override fun createRuntime(context: jp.awabi2048.cccontent.mob.MobSpawnContext): MobAbilityRuntime {
        val slot = context.options.metadata["summon_slot"]?.toIntOrNull()
        val state = EnderEyeGuidedState(ANGULAR_SPEED / 3.0)
        if (slot != null) {
            state.orbitalAngle = (Math.PI * 2.0 * slot.toDouble() / 3.0)
        }
        return Runtime(
            searchPhaseOffsetSteps = Random.nextInt(0, SEARCH_PHASE_VARIANTS),
            movementState = state
        )
    }

    override fun onSpawn(context: jp.awabi2048.cccontent.mob.MobSpawnContext, runtime: MobAbilityRuntime?) {
        val vex = context.entity as? Vex ?: return
        val rt = runtime as? Runtime ?: return

        Bukkit.getMobGoals().removeAllGoals(vex)
        vex.setAI(false)
        vex.isSilent = true
        vex.setGravity(false)
        vex.isInvulnerable = false
        vex.isInvisible = true
        vex.isCollidable = false

        rt.summonerId = context.options.metadata["summoner_id"]?.let { raw ->
            runCatching { UUID.fromString(raw) }.getOrNull()
        }

        val display = vex.world.spawn(vex.location.clone().add(0.0, 0.85, 0.0), ItemDisplay::class.java)
        display.setItemStack(createEyeHeadItem())
        display.billboard = Display.Billboard.FIXED
        display.isInvulnerable = true
        display.interpolationDuration = 1
        display.teleportDuration = 1
        display.brightness = Display.Brightness(15, 15)
        display.transformation = Transformation(
            Vector3f(0f, 0f, 0f),
            Quaternionf(),
            Vector3f(1.6f, 1.6f, 1.6f),
            Quaternionf()
        )
        display.addScoreboardTag("cc.mob.ender_eye_display")
        rt.displayId = display.uniqueId
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val vex = context.entity as? Vex ?: return
        val rt = runtime as? Runtime ?: return
        val display = rt.displayId?.let { Bukkit.getEntity(it) as? ItemDisplay }

        if (display == null || !display.isValid) {
            return
        }

        if (rt.cooldownRemainingTicks > 0L) {
            rt.cooldownRemainingTicks = (rt.cooldownRemainingTicks - context.tickDelta).coerceAtLeast(0L)
        }
        if (rt.teleportCooldownRemainingTicks > 0L) {
            rt.teleportCooldownRemainingTicks = (rt.teleportCooldownRemainingTicks - context.tickDelta).coerceAtLeast(0L)
        }

        val summoner = rt.summonerId?.let { Bukkit.getEntity(it) as? LivingEntity }
            ?.takeIf { it.isValid && !it.isDead && it.world.uid == vex.world.uid }
        val target = resolvePriorityTarget(vex, summoner)
        if (target == null) {
            EnderEyeGuidedMovementController.clearTarget(rt.movementState)
            display.teleport(vex.location.clone().add(0.0, 0.85, 0.0))
            vex.velocity = Vector(0.0, 0.0, 0.0)
            return
        }

        val distanceToTarget = display.location.distance(target.location)
        if (distanceToTarget > teleportDistanceThreshold && rt.teleportCooldownRemainingTicks <= 0L) {
            teleportNearTarget(vex, display, target)
            rt.teleportCooldownRemainingTicks = TELEPORT_COOLDOWN_TICKS
            rt.cooldownRemainingTicks = (rt.cooldownRemainingTicks + 5L).coerceAtLeast(5L)
            return
        }

        val center = resolveOrbitCenter(vex, summoner, target)
        val move = EnderEyeGuidedMovementController.update(
            state = rt.movementState,
            config = orbitConfig(),
            targetUuid = target.uniqueId,
            followerX = display.location.x,
            followerZ = display.location.z,
            targetX = center.x,
            targetY = center.y,
            targetZ = center.z
        )

        val moveLoc = Location(vex.world, move.x, move.y, move.z)
        val facing = facingLocation(moveLoc, target)

        display.teleport(facing)
        vex.teleport(facing.clone().add(0.0, -0.85, 0.0))
        vex.velocity = Vector(0.0, 0.0, 0.0)

        spawnWideAmbient(display, target)

        if (!context.isCombatActive()) return
        if (rt.cooldownRemainingTicks > 0L) return
        if (!MobAbilityUtils.shouldProcessSearchTick(
                context.activeMob.tickCount,
                context.loadSnapshot.searchIntervalMultiplier,
                rt.searchPhaseOffsetSteps,
                baseStepTicks = 1L
            )) return
        if (Random.nextDouble() < context.loadSnapshot.abilityExecutionSkipChance) return

        fireHomingOrb(context, display.location, vex, target)
        val baseCooldown = shotCooldownTicks.coerceAtLeast(1L)
        rt.cooldownRemainingTicks = (baseCooldown * context.loadSnapshot.abilityCooldownMultiplier).roundToLong().coerceAtLeast(baseCooldown)
    }

    override fun onDeath(context: MobDeathContext, runtime: MobAbilityRuntime?) {
        val rt = runtime as? Runtime ?: return
        val display = rt.displayId?.let { Bukkit.getEntity(it) as? ItemDisplay } ?: return
        if (display.isValid) {
            display.remove()
        }
    }

    override fun onDamaged(context: MobDamagedContext, runtime: MobAbilityRuntime?) {
        if (context.event.isCancelled) return
        if (context.event.finalDamage <= 0.0) return

        val entity = context.entity
        val vex = entity as? Vex ?: return
        val rt = runtime as? Runtime ?: return
        val world = entity.world
        val hit = entity.location.clone().add(0.0, 0.9, 0.0)
        world.playSound(hit, Sound.BLOCK_GLASS_BREAK, 0.9f, 1.5f)
        world.spawnParticle(Particle.ENCHANTED_HIT, hit, 16, 0.22, 0.28, 0.22, 0.0)
        world.spawnParticle(Particle.PORTAL, hit, 8, 0.2, 0.3, 0.2, 0.03)

        if (rt.teleportCooldownRemainingTicks > 0L) {
            return
        }
        val display = rt.displayId?.let { Bukkit.getEntity(it) as? ItemDisplay }
            ?.takeIf { it.isValid }
            ?: return
        teleportToRandomOrbitPosition(vex, display, rt)
        rt.teleportCooldownRemainingTicks = TELEPORT_COOLDOWN_TICKS
    }

    private fun resolvePriorityTarget(vex: Vex, summoner: LivingEntity?): Player? {
        if (summoner != null) {
            val summonerTarget = MobAbilityUtils.resolveTarget(summoner) as? Player
            if (summonerTarget != null && summonerTarget.isValid && !summonerTarget.isDead && summonerTarget.world.uid == vex.world.uid) {
                return summonerTarget
            }
        }

        val ownTarget = MobAbilityUtils.resolveTarget(vex) as? Player
        if (ownTarget != null && ownTarget.isValid && !ownTarget.isDead && ownTarget.world.uid == vex.world.uid) {
            return ownTarget
        }

        return vex.getNearbyEntities(24.0, 24.0, 24.0)
            .asSequence()
            .mapNotNull { it as? Player }
            .filter { it.isValid && !it.isDead && it.world.uid == vex.world.uid }
            .minByOrNull { it.location.distanceSquared(vex.location) }
    }

    private fun resolveOrbitCenter(vex: Vex, summoner: LivingEntity?, target: Player): Location {
        val base = summoner?.location ?: target.location
        return base.clone().add(0.0, 0.8, 0.0)
    }

    private fun createEyeHeadItem(): ItemStack {
        val item = ItemStack(Material.PLAYER_HEAD)
        val skullMeta = item.itemMeta as? SkullMeta ?: return item
        val profile = Bukkit.createProfile(UUID.randomUUID(), "ender_eye_head")
        profile.setProperty(ProfileProperty("textures", headTextureValue))
        skullMeta.playerProfile = profile
        item.itemMeta = skullMeta
        return item
    }

    private fun spawnWideAmbient(display: ItemDisplay, target: Player) {
        val world = display.world
        val center = display.location.clone()
        world.spawnParticle(ambientParticle, center, ambientParticleCount, ambientParticleRadius, ambientParticleRadius, ambientParticleRadius, 0.0)
        world.spawnParticle(Particle.PORTAL, center, 4, 0.35, 0.25, 0.35, 0.02)
        world.spawnParticle(
            Particle.DUST_COLOR_TRANSITION,
            center,
            3,
            0.45,
            0.35,
            0.45,
            0.0,
            Particle.DustTransition(Color.fromRGB(168, 95, 224), Color.fromRGB(66, 24, 118), 1.0f)
        )
        world.spawnParticle(Particle.PORTAL, target.location.clone().add(0.0, 1.0, 0.0), 2, 0.25, 0.25, 0.25, 0.02)
    }

    private fun fireHomingOrb(context: MobRuntimeContext, origin: Location, owner: LivingEntity, target: Player) {
        val world = owner.world
        val baseDamage = (context.definition.attack * projectileDamageMultiplier).coerceAtLeast(0.0)

        world.playSound(origin, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.7f, 1.8f)

        val shotCount = shotWayCount.coerceAtLeast(1)
        repeat(shotCount) { index ->
            val offset = if (shotCount == 1) 0.0 else (index - (shotCount - 1) / 2.0) * shotSpreadDegrees
            launchSingleOrb(context, origin, owner, target, baseDamage, offset)
        }
    }

    private fun launchSingleOrb(
        context: MobRuntimeContext,
        origin: Location,
        owner: LivingEntity,
        target: Player,
        damage: Double,
        yawOffsetDegrees: Double
    ) {
        val world = owner.world
        var position = origin.clone()
        val targetVector = target.eyeLocation.toVector().subtract(position.toVector())
        var velocity = if (targetVector.lengthSquared() > 0.0001) {
            rotateYaw(targetVector.normalize(), yawOffsetDegrees).multiply(projectileSpeedPerTick)
        } else {
            owner.location.direction.clone().normalize().multiply(projectileSpeedPerTick)
        }
        val hitSet = mutableSetOf<UUID>()

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
                val blockHit = world.rayTraceBlocks(position, velocity.clone().normalize(), velocity.length(), FluidCollisionMode.NEVER, true)
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
                    if (damage > 0.0) {
                        MobService.getInstance(context.plugin)?.issueDirectDamagePermit(owner.uniqueId, hit.uniqueId)
                        hit.damage(damage, owner)
                    }
                    world.spawnParticle(Particle.ENCHANTED_HIT, hit.location.clone().add(0.0, 1.0, 0.0), 16, 0.12, 0.12, 0.12, 0.0)
                    world.playSound(hit.location, Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.8f, 1.8f)
                    cancel()
                    return
                }

                position = next
                world.spawnParticle(Particle.ENCHANTED_HIT, position, 3, 0.04, 0.04, 0.04, 0.0)
                world.spawnParticle(
                    Particle.DUST_COLOR_TRANSITION,
                    position,
                    1,
                    0.03,
                    0.03,
                    0.03,
                    0.0,
                    Particle.DustTransition(Color.fromRGB(146, 86, 229), Color.fromRGB(255, 255, 255), 0.9f)
                )
                life -= 1L
            }
        }.runTaskTimer(context.plugin, 1L, 1L)
    }

    private fun teleportToRandomOrbitPosition(vex: Vex, display: ItemDisplay, runtime: Runtime) {
        val summoner = runtime.summonerId?.let { Bukkit.getEntity(it) as? LivingEntity }
            ?.takeIf { it.isValid && !it.isDead && it.world.uid == vex.world.uid }
        val target = resolvePriorityTarget(vex, summoner)
        val center = if (target != null) {
            resolveOrbitCenter(vex, summoner, target)
        } else {
            vex.location.clone().add(0.0, 0.8, 0.0)
        }
        val angle = Random.nextDouble(0.0, Math.PI * 2.0)
        val destination = center.clone().add(
            cos(angle) * orbitRadius,
            orbitHeightOffset + Random.nextDouble(-0.2, 0.2),
            sin(angle) * orbitRadius
        )

        val facing = if (target != null) {
            facingLocation(destination.clone(), target)
        } else {
            destination.clone()
        }

        vex.world.spawnParticle(Particle.PORTAL, display.location.clone(), 16, 0.3, 0.3, 0.3, 0.02)
        display.teleport(facing)
        vex.teleport(facing.clone().add(0.0, -0.85, 0.0))
        vex.world.spawnParticle(Particle.PORTAL, facing.clone(), 16, 0.3, 0.3, 0.3, 0.02)
    }

    private fun rotateYaw(vector: Vector, degrees: Double): Vector {
        if (degrees == 0.0) {
            return vector
        }
        val radians = Math.toRadians(degrees)
        val cos = kotlin.math.cos(radians)
        val sin = kotlin.math.sin(radians)
        return Vector(
            vector.x * cos - vector.z * sin,
            vector.y,
            vector.x * sin + vector.z * cos
        ).normalize()
    }

    private fun teleportNearTarget(vex: Vex, display: ItemDisplay, target: Player) {
        val world = vex.world
        val destination = EndThemeEffects.findNearbyTeleportLocation(target.location, 6.0, attempts = 18)
            ?: target.location.clone().add(1.5, 0.5, 0.0)
        world.playSound(vex.location, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.8f)
        world.spawnParticle(Particle.PORTAL, vex.location.clone().add(0.0, 0.8, 0.0), 24, 0.5, 0.5, 0.5, 0.02)
        vex.teleport(destination.clone().add(0.0, -0.85, 0.0))
        display.teleport(destination.clone().add(0.0, 0.85, 0.0))
        world.playSound(destination, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.8f)
    }

    private fun facingLocation(from: Location, target: LivingEntity): Location {
        val to = target.location.clone().add(0.0, 1.0, 0.0)
        val dir = from.toVector().subtract(to.toVector())
        val yaw = Math.toDegrees(atan2(-dir.x, dir.z)).toFloat()
        val pitch = (-Math.toDegrees(atan2(dir.y, sqrt((dir.x * dir.x + dir.z * dir.z).coerceAtLeast(1.0e-6))))).toFloat()
        return from.clone().apply {
            this.yaw = yaw
            this.pitch = pitch
        }
    }

    private fun orbitConfig(): EnderEyeGuidedConfig {
        return EnderEyeGuidedConfig(
            orbitRadius = orbitRadius,
            orbitHeightOffset = orbitHeightOffset,
            targetPositionDelayTicks = 20,
            directionChangeIntervalTicks = 20,
            directionTransitionTicks = 10,
            baseAngularSpeed = ANGULAR_SPEED / 3.0,
            yFollowLerpFactor = 0.08
        )
    }

    private companion object {
        const val SEARCH_PHASE_VARIANTS = 16
        const val TELEPORT_COOLDOWN_TICKS = 20L
        const val DEFAULT_EYE_HEAD_TEXTURE_VALUE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzRiZTY3YWVkZWRlMzE2ZTVmMDBhN2FkM2ZiZTMwYTgxY2VmNjdkNGRmN2NlZDEzMGZiZTAyYmUwMTU0OGJjZSJ9fX0="
        const val ANGULAR_SPEED = 0.019634954084936207
    }
}
