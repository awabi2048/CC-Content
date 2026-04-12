package jp.awabi2048.cccontent.mob.ability

import com.destroystokyo.paper.profile.ProfileProperty
import jp.awabi2048.cccontent.mob.MobDamagedContext
import jp.awabi2048.cccontent.mob.MobRuntimeContext
import jp.awabi2048.cccontent.mob.MobService
import jp.awabi2048.cccontent.mob.MobSpawnContext
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Display
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.entity.Slime
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Vector
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

class ShulkerMimicBoltAbility(
    override val id: String,
    private val headTextureValue: String,
    private val headScale: Float = 2.0f,
    private val headYOffset: Double = 8.6 / 16.0 + 0.5,
    private val projectileSpeedPerTick: Double = 0.34,
    private val projectileDamageMultiplier: Double = 0.95,
    private val homingStrength: Double = 0.18,
    private val projectileHitPadding: Double = 0.08,
    private val hitKnockUpVelocity: Double = 0.38,
    private val cooldownTicks: Long = 58L,
    private val searchRadius: Double = 28.0
) : MobAbility {

    data class Runtime(
        var displayId: UUID? = null,
        var anchorLocation: Location? = null,
        var cooldownRemainingTicks: Long = 0L,
        var searchPhaseOffsetSteps: Int = 0,
        var facingYaw: Float = 0.0f
    ) : MobAbilityRuntime

    override fun tickIntervalTicks(): Long = 1L

    override fun createRuntime(context: MobSpawnContext): MobAbilityRuntime {
        return Runtime(
            cooldownRemainingTicks = cooldownTicks,
            searchPhaseOffsetSteps = Random.nextInt(0, SEARCH_PHASE_VARIANTS)
        )
    }

    override fun onSpawn(context: MobSpawnContext, runtime: MobAbilityRuntime?) {
        val slime = context.entity as? Slime ?: return
        val rt = runtime as? Runtime ?: return

        Bukkit.getMobGoals().removeAllGoals(slime)
        slime.setAI(false)
        slime.setGravity(false)
        slime.setSize(1)
        slime.getAttribute(Attribute.MAX_HEALTH)?.baseValue = context.definition.health
        slime.health = context.definition.health.coerceAtMost(slime.getAttribute(Attribute.MAX_HEALTH)?.value ?: context.definition.health)
        slime.isInvisible = true
        slime.isSilent = true
        slime.isCollidable = false
        slime.isPersistent = true
        slime.velocity = ZERO_VECTOR
        slime.fallDistance = 0f

        val anchor = slime.location.clone()
        rt.anchorLocation = anchor

        val display = slime.world.spawn(anchor.clone().add(0.0, headYOffset, 0.0), ItemDisplay::class.java)
        display.setItemStack(createHeadItem())
        display.billboard = Display.Billboard.FIXED
        display.isInvulnerable = true
        display.interpolationDuration = 2
        display.teleportDuration = 2
        display.brightness = Display.Brightness(15, 15)
        display.transformation = headDisplayTransformation(headScale)
        display.addScoreboardTag("cc.mob.shulker_mimic_display")
        rt.displayId = display.uniqueId
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val slime = context.entity as? Slime ?: return
        val rt = runtime as? Runtime ?: return
        val display = rt.displayId?.let { Bukkit.getEntity(it) as? ItemDisplay } ?: return
        if (!slime.isValid || slime.isDead || !display.isValid) return

        rt.cooldownRemainingTicks = (rt.cooldownRemainingTicks - context.tickDelta).coerceAtLeast(0L)

        val anchor = rt.anchorLocation ?: slime.location.clone().also { rt.anchorLocation = it }
        val target = resolveTarget(slime)
        if (target == null) {
            slime.target = null
            val idleFacing = anchor.clone()
            idleFacing.yaw = rt.facingYaw
            idleFacing.pitch = 0f
            slime.teleport(idleFacing)
            display.teleport(idleFacing.clone().add(0.0, headYOffset, 0.0))
            return
        }

        slime.target = target
        val shouldSearch = MobAbilityUtils.shouldProcessSearchTick(
            context.activeMob.tickCount,
            context.loadSnapshot.searchIntervalMultiplier,
            rt.searchPhaseOffsetSteps,
            baseStepTicks = 1L
        )
        if (rt.cooldownRemainingTicks <= FACING_LEAD_TICKS) {
            rt.facingYaw = snapYawToCardinal(target.location, slime.location)
        }

        val facing = anchor.clone().apply {
            yaw = rt.facingYaw
            pitch = 0f
        }
        slime.teleport(facing)
        display.teleport(facing.clone().add(0.0, headYOffset, 0.0))

        if (!context.isCombatActive() || !shouldSearch || rt.cooldownRemainingTicks > 0L) {
            return
        }

        launchBolt(context, slime, display.location.clone(), target, rt.facingYaw)
        rt.cooldownRemainingTicks = cooldownTicks
    }

    override fun onDamaged(context: MobDamagedContext, runtime: MobAbilityRuntime?) {
        if (context.event.isCancelled) return
        if (context.event.finalDamage <= 0.0) return
        val loc = context.entity.location.clone().add(0.0, 1.0, 0.0)
        context.entity.world.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.8f, 1.3f)
        context.entity.world.spawnParticle(Particle.ENCHANTED_HIT, loc, 12, 0.22, 0.22, 0.22, 0.0)
    }

    override fun onDeath(context: jp.awabi2048.cccontent.mob.MobDeathContext, runtime: MobAbilityRuntime?) {
        val rt = runtime as? Runtime ?: return
        context.entity.world.playSound(context.entity.location.clone().add(0.0, 0.5, 0.0), Sound.ENTITY_SHULKER_DEATH, 1.0f, 1.0f)
        rt.displayId?.let { Bukkit.getEntity(it) }?.let { if (it.isValid) it.remove() }
    }

    private fun launchBolt(context: MobRuntimeContext, slime: Slime, originBase: Location, target: Player, facingYaw: Float) {
        val service = MobService.getInstance(context.plugin)
        val world = slime.world
        val damage = (context.definition.attack * projectileDamageMultiplier).coerceAtLeast(0.0)
        val origin = originBase
        var position = origin.clone()
        var velocity = directionFromYaw(facingYaw)
            .takeIf { it.lengthSquared() > 0.0001 }
            ?.normalize()
            ?.multiply(projectileSpeedPerTick)
            ?: Vector(0.0, 0.0, 1.0).multiply(projectileSpeedPerTick)
        val noiseSeed = Random.nextDouble(0.0, Math.PI * 2.0)

        world.playSound(origin, Sound.ENTITY_SHULKER_SHOOT, 1.0f, 1.15f)
        world.playSound(origin, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.0f)

        object : BukkitRunnable() {
            var life = PROJECTILE_LIFETIME_TICKS
            var tickIndex = 0L

            override fun run() {
                if (!slime.isValid || slime.isDead || life <= 0L) {
                    cancel()
                    return
                }

                val liveTarget = Bukkit.getEntity(target.uniqueId) as? Player
                if (liveTarget != null && liveTarget.isValid && !liveTarget.isDead && liveTarget.world.uid == world.uid) {
                    val desired = liveTarget.eyeLocation.toVector().subtract(position.toVector())
                    if (desired.lengthSquared() > 0.0001) {
                        val wanted = desired.normalize().multiply(projectileSpeedPerTick)
                        velocity = velocity.multiply((1.0 - homingStrength).coerceIn(0.0, 1.0))
                            .add(wanted.multiply(homingStrength.coerceIn(0.0, 1.0)))
                    }
                }

                velocity = applyProjectileNoise(velocity, tickIndex, noiseSeed)
                if (velocity.lengthSquared() <= 0.0001) {
                    cancel()
                    return
                }

                val next = position.clone().add(velocity)
                val blockHit = world.rayTraceBlocks(position, velocity.clone().normalize(), velocity.length(), org.bukkit.FluidCollisionMode.NEVER, true)
                val blockHitDistance = blockHit?.hitPosition?.distance(position.toVector()) ?: Double.POSITIVE_INFINITY
                val hit = findHitTarget(world, slime, position, next, blockHitDistance)
                if (hit != null) {
                    if (damage > 0.0 && service != null) {
                        service.issueDirectDamagePermit(slime.uniqueId, hit.uniqueId)
                        hit.damage(damage, slime)
                    }
                    hit.velocity = hit.velocity.clone().add(Vector(0.0, hitKnockUpVelocity, 0.0))
                    spawnImpact(world, hit.location.clone().add(0.0, 1.0, 0.0))
                    world.playSound(hit.location, Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.9f, 1.25f)
                    cancel()
                    return
                }

                if (blockHit != null) {
                    spawnImpact(world, blockHit.hitPosition.toLocation(world))
                    cancel()
                    return
                }

                position = next
                world.spawnParticle(Particle.END_ROD, position, 1, 0.0, 0.0, 0.0, 0.0)
                world.spawnParticle(Particle.ENCHANTED_HIT, position, 2, 0.08, 0.08, 0.08, 0.0)
                world.spawnParticle(Particle.PORTAL, position, 2, 0.08, 0.08, 0.08, 0.02)
                world.spawnParticle(Particle.DUST, position, 1, 0.03, 0.03, 0.03, 0.0, Particle.DustOptions(Color.fromRGB(174, 94, 230), 1.0f))
                life -= 1L
                tickIndex += 1L
            }
        }.runTaskTimer(context.plugin, 1L, 1L)
    }

    private fun spawnImpact(world: org.bukkit.World, location: Location) {
        world.spawnParticle(Particle.ENCHANTED_HIT, location, 18, 0.18, 0.18, 0.18, 0.0)
        world.spawnParticle(Particle.PORTAL, location, 20, 0.22, 0.22, 0.22, 0.02)
        world.spawnParticle(Particle.DUST, location, 8, 0.08, 0.08, 0.08, 0.0, Particle.DustOptions(Color.fromRGB(160, 90, 220), 1.15f))
    }

    private fun resolveTarget(slime: Slime): Player? {
        val fixed = (slime.target as? Player)?.takeIf { isValidTarget(slime, it) }
        if (fixed != null) {
            return fixed
        }

        return slime.getNearbyEntities(searchRadius, searchRadius, searchRadius)
            .asSequence()
            .mapNotNull { it as? Player }
            .filter { isValidTarget(slime, it) }
            .minByOrNull { it.location.distanceSquared(slime.location) }
    }

    private fun isValidTarget(slime: Slime, player: Player): Boolean {
        if (!player.isValid || player.isDead) return false
        if (player.gameMode == GameMode.SPECTATOR) return false
        return player.world.uid == slime.world.uid
    }

    private fun findHitTarget(world: org.bukkit.World, slime: Slime, from: Location, to: Location, obstacleDistance: Double): Player? {
        val delta = to.toVector().subtract(from.toVector())
        if (delta.lengthSquared() <= 0.0001) return null

        val maxDistance = delta.length()
        val direction = delta.clone().normalize()
        val searchCenter = from.clone().add(to).multiply(0.5)
        val searchRadiusX = abs(to.x - from.x) * 0.5 + 1.2
        val searchRadiusY = abs(to.y - from.y) * 0.5 + 1.2
        val searchRadiusZ = abs(to.z - from.z) * 0.5 + 1.2

        return world.getNearbyEntities(searchCenter, searchRadiusX, searchRadiusY, searchRadiusZ)
            .asSequence()
            .mapNotNull { it as? Player }
            .mapNotNull { player ->
                if (!isValidTarget(slime, player)) return@mapNotNull null
                val box = player.boundingBox.expand(projectileHitPadding, projectileHitPadding, projectileHitPadding)
                val candidateDistance = when {
                    box.contains(from.toVector()) || box.contains(to.toVector()) -> 0.0
                    else -> {
                        val trace = box.rayTrace(from.toVector(), direction, maxDistance) ?: return@mapNotNull null
                        trace.hitPosition.distance(from.toVector())
                    }
                }
                if (candidateDistance <= obstacleDistance) HitCandidate(player, candidateDistance) else null
            }
            .minByOrNull { it.distance }
            ?.player
    }

    private fun applyProjectileNoise(velocity: Vector, tickIndex: Long, noiseSeed: Double): Vector {
        if (velocity.lengthSquared() <= 0.0001) return ZERO_VECTOR

        val forward = velocity.clone().normalize()
        val right = if (abs(forward.y) < 0.92) {
            forward.clone().crossProduct(Vector(0.0, 1.0, 0.0)).normalize()
        } else {
            forward.clone().crossProduct(Vector(1.0, 0.0, 0.0)).normalize()
        }
        val up = right.clone().crossProduct(forward).normalize()
        val amplitude = 0.135
        val phaseA = tickIndex.toDouble() * 0.42 + noiseSeed
        val phaseB = tickIndex.toDouble() * 0.29 + noiseSeed * 0.7
        val jitter = right.multiply(sin(phaseA) * amplitude)
            .add(up.multiply(cos(phaseB) * amplitude * 0.55))

        return forward.add(jitter).normalize().multiply(projectileSpeedPerTick)
    }

    private fun directionFromYaw(yaw: Float): Vector {
        val radians = Math.toRadians(yaw.toDouble())
        return Vector(-sin(radians), 0.0, cos(radians))
    }

    private fun snapYawToCardinal(target: Location, origin: Location): Float {
        val delta = target.toVector().subtract(origin.toVector())
        val yaw = Math.toDegrees(atan2(-delta.x, delta.z))
        val normalized = ((yaw % 360.0) + 360.0) % 360.0
        val snapped = ((normalized / 90.0).roundToInt() * 90) % 360
        return snapped.toFloat()
    }

    private fun createHeadItem(): ItemStack {
        val item = ItemStack(Material.PLAYER_HEAD)
        val skullMeta = item.itemMeta as? SkullMeta ?: return item
        val profile = Bukkit.createProfile(UUID.randomUUID(), "shulker_mimic")
        profile.setProperty(ProfileProperty("textures", headTextureValue))
        skullMeta.playerProfile = profile
        item.itemMeta = skullMeta
        return item
    }

    private companion object {
        const val SEARCH_PHASE_VARIANTS = 16
        const val PROJECTILE_LIFETIME_TICKS = 70L
        const val FACING_LEAD_TICKS = 10L
        val ZERO_VECTOR: Vector = Vector(0.0, 0.0, 0.0)
    }
}

private data class HitCandidate(
    val player: Player,
    val distance: Double
)
