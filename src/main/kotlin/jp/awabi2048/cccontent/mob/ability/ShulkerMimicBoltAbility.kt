package jp.awabi2048.cccontent.mob.ability

import com.destroystokyo.paper.profile.ProfileProperty
import jp.awabi2048.cccontent.mob.MobDamagedContext
import jp.awabi2048.cccontent.mob.MobRuntimeContext
import jp.awabi2048.cccontent.mob.MobService
import jp.awabi2048.cccontent.mob.MobSpawnContext
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.FluidCollisionMode
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Display
import org.bukkit.entity.Guardian
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.random.Random

class ShulkerMimicBoltAbility(
    override val id: String,
    private val headTextureValue: String,
    private val headScale: Float = 1.95f,
    private val headYOffset: Double = 0.95,
    private val projectileSpeedPerTick: Double = 0.34,
    private val projectileDamageMultiplier: Double = 0.95,
    private val homingStrength: Double = 0.18,
    private val projectileHitRadius: Double = 1.1,
    private val cooldownTicks: Long = 58L,
    private val searchRadius: Double = 28.0
) : MobAbility {

    data class Runtime(
        var displayId: UUID? = null,
        var anchorLocation: Location? = null,
        var cooldownRemainingTicks: Long = 0L,
        var searchPhaseOffsetSteps: Int = 0
    ) : MobAbilityRuntime

    override fun tickIntervalTicks(): Long = 1L

    override fun createRuntime(context: MobSpawnContext): MobAbilityRuntime {
        return Runtime(searchPhaseOffsetSteps = Random.nextInt(0, SEARCH_PHASE_VARIANTS))
    }

    override fun onSpawn(context: MobSpawnContext, runtime: MobAbilityRuntime?) {
        val guardian = context.entity as? Guardian ?: return
        val rt = runtime as? Runtime ?: return

        Bukkit.getMobGoals().removeAllGoals(guardian)
        guardian.setAI(false)
        guardian.setGravity(false)
        guardian.isInvisible = true
        guardian.isSilent = true
        guardian.isCollidable = false
        guardian.isPersistent = true
        guardian.velocity = ZERO_VECTOR
        guardian.fallDistance = 0f

        val anchor = guardian.location.clone()
        rt.anchorLocation = anchor

        val display = guardian.world.spawn(anchor.clone().add(0.0, headYOffset, 0.0), ItemDisplay::class.java)
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
        val guardian = context.entity as? Guardian ?: return
        val rt = runtime as? Runtime ?: return
        val display = rt.displayId?.let { Bukkit.getEntity(it) as? ItemDisplay } ?: return
        if (!guardian.isValid || guardian.isDead || !display.isValid) return

        rt.cooldownRemainingTicks = (rt.cooldownRemainingTicks - context.tickDelta).coerceAtLeast(0L)

        val anchor = rt.anchorLocation ?: guardian.location.clone().also { rt.anchorLocation = it }
        val target = resolveTarget(guardian)
        if (target == null) {
            guardian.target = null
            val idleFacing = anchor.clone()
            guardian.teleport(idleFacing)
            display.teleport(idleFacing.clone().add(0.0, headYOffset, 0.0))
            return
        }

        guardian.target = target
        val shouldSearch = MobAbilityUtils.shouldProcessSearchTick(
            context.activeMob.tickCount,
            context.loadSnapshot.searchIntervalMultiplier,
            rt.searchPhaseOffsetSteps,
            baseStepTicks = 1L
        )
        val facing = faceTowards(anchor.clone(), target.eyeLocation.clone())
        guardian.teleport(facing)
        display.teleport(facing.clone().add(0.0, headYOffset, 0.0))

        if (!context.isCombatActive() || !shouldSearch || rt.cooldownRemainingTicks > 0L) {
            return
        }

        launchBolt(context, guardian, target)
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
        rt.displayId?.let { Bukkit.getEntity(it) }?.let { if (it.isValid) it.remove() }
    }

    private fun launchBolt(context: MobRuntimeContext, guardian: Guardian, target: Player) {
        val service = MobService.getInstance(context.plugin)
        val world = guardian.world
        val damage = (context.definition.attack * projectileDamageMultiplier).coerceAtLeast(0.0)
        val origin = guardian.location.clone().add(0.0, headYOffset + 0.15, 0.0)
        var position = origin.clone()
        var velocity = target.eyeLocation.toVector().subtract(position.toVector())
            .takeIf { it.lengthSquared() > 0.0001 }
            ?.normalize()
            ?.multiply(projectileSpeedPerTick)
            ?: guardian.location.direction.clone().normalize().multiply(projectileSpeedPerTick)

        world.playSound(origin, Sound.ENTITY_SHULKER_SHOOT, 1.0f, 1.15f)
        world.playSound(origin, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.0f)

        object : BukkitRunnable() {
            var life = PROJECTILE_LIFETIME_TICKS

            override fun run() {
                if (!guardian.isValid || guardian.isDead || life <= 0L) {
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

                val noisy = velocity
                velocity = if (noisy.lengthSquared() > 0.0001) noisy.normalize().multiply(projectileSpeedPerTick) else ZERO_VECTOR
                if (velocity.lengthSquared() <= 0.0001) {
                    cancel()
                    return
                }

                val blockHit = world.rayTraceBlocks(position, velocity.clone().normalize(), velocity.length(), FluidCollisionMode.NEVER, true)
                if (blockHit != null) {
                    spawnImpact(world, blockHit.hitPosition.toLocation(world))
                    cancel()
                    return
                }

                val next = position.clone().add(velocity)
                val hit = world.getNearbyEntities(next, projectileHitRadius, projectileHitRadius, projectileHitRadius)
                    .asSequence()
                    .filterIsInstance<Player>()
                    .firstOrNull { it.isValid && !it.isDead && it.world.uid == world.uid && it.gameMode != GameMode.SPECTATOR }

                if (hit != null) {
                    if (damage > 0.0 && service != null) {
                        service.issueDirectDamagePermit(guardian.uniqueId, hit.uniqueId)
                        hit.damage(damage, guardian)
                    }
                    spawnImpact(world, hit.location.clone().add(0.0, 1.0, 0.0))
                    world.playSound(hit.location, Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.9f, 1.25f)
                    cancel()
                    return
                }

                position = next
                world.spawnParticle(Particle.END_ROD, position, 1, 0.0, 0.0, 0.0, 0.0)
                world.spawnParticle(Particle.PORTAL, position, 2, 0.08, 0.08, 0.08, 0.02)
                life -= 1L
            }
        }.runTaskTimer(context.plugin, 1L, 1L)
    }

    private fun spawnImpact(world: org.bukkit.World, location: Location) {
        world.spawnParticle(Particle.ENCHANTED_HIT, location, 16, 0.18, 0.18, 0.18, 0.0)
        world.spawnParticle(Particle.PORTAL, location, 20, 0.22, 0.22, 0.22, 0.02)
        world.spawnParticle(Particle.DUST, location, 6, 0.08, 0.08, 0.08, 0.0, Particle.DustOptions(Color.fromRGB(160, 90, 220), 1.15f))
    }

    private fun resolveTarget(guardian: Guardian): Player? {
        val fixed = (guardian.target as? Player)?.takeIf { isValidTarget(guardian, it) }
        if (fixed != null) {
            return fixed
        }

        return guardian.getNearbyEntities(searchRadius, searchRadius, searchRadius)
            .asSequence()
            .mapNotNull { it as? Player }
            .filter { isValidTarget(guardian, it) }
            .minByOrNull { it.location.distanceSquared(guardian.location) }
    }

    private fun isValidTarget(guardian: Guardian, player: Player): Boolean {
        if (!player.isValid || player.isDead) return false
        if (player.gameMode == GameMode.SPECTATOR) return false
        return player.world.uid == guardian.world.uid
    }

    private fun faceTowards(from: Location, to: Location): Location {
        val delta = to.toVector().subtract(from.toVector())
        val yaw = Math.toDegrees(atan2(-delta.x, delta.z)).toFloat()
        val xz = sqrt(delta.x * delta.x + delta.z * delta.z)
        val pitch = (-Math.toDegrees(atan2(delta.y, xz.coerceAtLeast(1.0e-6)))).toFloat()
        return from.clone().apply {
            this.yaw = yaw
            this.pitch = pitch
        }
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
        val ZERO_VECTOR: Vector = Vector(0.0, 0.0, 0.0)
    }
}
