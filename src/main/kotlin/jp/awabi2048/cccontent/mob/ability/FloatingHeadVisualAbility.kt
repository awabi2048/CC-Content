@file:Suppress("REDUNDANT_CALL_OF_CONVERSION_METHOD")

package jp.awabi2048.cccontent.mob.ability

import com.destroystokyo.paper.profile.ProfileProperty
import jp.awabi2048.cccontent.mob.MobDeathContext
import jp.awabi2048.cccontent.mob.MobDamagedContext
import jp.awabi2048.cccontent.mob.MobGenericDamagedContext
import jp.awabi2048.cccontent.mob.MobRuntimeContext
import jp.awabi2048.cccontent.mob.MobSpawnContext
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Display
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.entity.Vex
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.UUID
import kotlin.math.sin
import kotlin.random.Random

class FloatingHeadVisualAbility(
    override val id: String,
    private val headTextureValue: String,
    private val approachSpeed: Double = 0.15,
    private val preferredDistance: Double = 5.0,
    private val oscillationAmplitude: Double = 0.4,
    private val oscillationFrequency: Double = 0.05,
    private val noiseIntervalTicks: Long = 10L,
    private val noiseMagnitude: Double = 0.3,
    private val displayScale: Float = 1.4f,
    private val searchRadius: Double = 32.0
) : MobAbility {

    data class Runtime(
        var displayId: UUID? = null,
        var noiseOffsetX: Double = 0.0,
        var noiseOffsetZ: Double = 0.0,
        var noiseCooldownTicks: Long = 0L,
        var elapsedTicks: Long = 0L
    ) : MobAbilityRuntime

    override fun tickIntervalTicks(): Long = 1L

    override fun createRuntime(context: MobSpawnContext): MobAbilityRuntime {
        return Runtime()
    }

    override fun onSpawn(context: MobSpawnContext, runtime: MobAbilityRuntime?) {
        val vex = context.entity as? Vex ?: return
        val rt = runtime as? Runtime ?: return

        Bukkit.getMobGoals().removeAllGoals(vex)
        vex.setAI(false)
        vex.setGravity(false)
        vex.isInvisible = true
        vex.isSilent = true
        vex.isCollidable = false
        vex.velocity = ZERO_VECTOR
        vex.fallDistance = 0f

        val display = vex.world.spawn(
            vex.location.clone().add(0.0, DISPLAY_Y_OFFSET, 0.0),
            ItemDisplay::class.java
        )
        display.setItemStack(createHeadItem())
        display.billboard = Display.Billboard.FIXED
        display.isInvulnerable = true
        display.interpolationDuration = 2
        display.teleportDuration = 2
        display.brightness = Display.Brightness(15, 15)
        display.transformation = headDisplayTransformation(displayScale)
        display.addScoreboardTag("cc.mob.ender_ghost_display")
        rt.displayId = display.uniqueId
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val vex = context.entity as? Vex ?: return
        val rt = runtime as? Runtime ?: return
        val display = rt.displayId?.let { Bukkit.getEntity(it) as? ItemDisplay }
        if (display == null || !display.isValid) return

        rt.elapsedTicks += context.tickDelta.toLong()
        vex.velocity = ZERO_VECTOR
        vex.fallDistance = 0f

        val target = resolveTarget(vex)
        if (target == null) {
            vex.target = null
            val anchor = vex.location.clone().add(0.0, DISPLAY_Y_OFFSET, 0.0)
            display.teleport(anchor)
            vex.teleport(anchor.clone().add(0.0, -DISPLAY_Y_OFFSET, 0.0))
            return
        }
        vex.target = target

        rt.noiseCooldownTicks = (rt.noiseCooldownTicks - context.tickDelta.toLong()).coerceAtLeast(0L)
        if (rt.noiseCooldownTicks <= 0L) {
            rt.noiseOffsetX = Random.nextDouble(-noiseMagnitude, noiseMagnitude)
            rt.noiseOffsetZ = Random.nextDouble(-noiseMagnitude, noiseMagnitude)
            rt.noiseCooldownTicks = noiseIntervalTicks
        }

        val baseLocation = computeBaseLocation(vex, target, context.tickDelta)
        val oscillationY = sin(rt.elapsedTicks.toDouble() * oscillationFrequency) * oscillationAmplitude
        val dest = baseLocation.add(rt.noiseOffsetX, oscillationY, rt.noiseOffsetZ)
        val facing = faceTowards(dest, target.eyeLocation.clone())

        vex.teleport(facing.clone().add(0.0, -DISPLAY_Y_OFFSET, 0.0))
        display.teleport(facing)

        spawnAmbient(display)
    }

    override fun onDamaged(context: MobDamagedContext, runtime: MobAbilityRuntime?) {
        if (context.event.isCancelled) return
        if (context.event.finalDamage <= 0.0) return
        val loc = context.entity.location.clone().add(0.0, DISPLAY_Y_OFFSET, 0.0)
        context.entity.world.playSound(loc, Sound.BLOCK_GLASS_BREAK, 0.9f, 1.5f)
        context.entity.world.spawnParticle(Particle.ENCHANTED_HIT, loc, 12, 0.2, 0.2, 0.2, 0.0)
    }

    override fun onGenericDamaged(context: MobGenericDamagedContext, runtime: MobAbilityRuntime?) {
        if (context.event.cause in IGNORED_DAMAGE_CAUSES) {
            context.event.isCancelled = true
        }
    }

    override fun onDeath(context: jp.awabi2048.cccontent.mob.MobDeathContext, runtime: MobAbilityRuntime?) {
        val rt = runtime as? Runtime ?: return
        val display = rt.displayId?.let { Bukkit.getEntity(it) as? ItemDisplay } ?: return
        if (display.isValid) {
            display.remove()
        }
    }

    private fun computeBaseLocation(
        vex: Vex,
        target: Player,
        tickDelta: Long
    ): Location {
        val toVex = vex.location.toVector().subtract(target.location.toVector())
        val dist = toVex.length()
        val delta = tickDelta.toDouble()

        if (dist > preferredDistance + 2.0) {
            val dir = target.eyeLocation.toVector().subtract(vex.location.toVector())
            if (dir.lengthSquared() > 0.01) {
                val step = approachSpeed * delta
                return vex.location.add(dir.normalize().multiply(step))
            }
        } else if (dist < preferredDistance - 2.0) {
            val dir = vex.location.toVector().subtract(target.eyeLocation.toVector())
            if (dir.lengthSquared() > 0.01) {
                val step = approachSpeed * 0.5 * delta
                return vex.location.add(dir.normalize().multiply(step))
            }
        }

        return vex.location
    }

    private fun spawnAmbient(display: ItemDisplay) {
        val loc = display.location
        loc.world.spawnParticle(Particle.PORTAL, loc, 3, 0.12, 0.12, 0.12, 0.01)
    }

    private fun resolveTarget(vex: Vex): Player? {
        val fixed = (vex.target as? Player)?.takeIf { isValidTarget(vex, it) }
        if (fixed != null && fixed.isValid && !fixed.isDead && fixed.world.uid == vex.world.uid) {
            return fixed
        }
        return vex.getNearbyEntities(searchRadius, 24.0, searchRadius)
            .asSequence()
            .mapNotNull { it as? Player }
            .filter { isValidTarget(vex, it) }
            .minByOrNull { it.location.distanceSquared(vex.location) }
    }

    private fun isValidTarget(vex: Vex, player: Player): Boolean {
        if (!player.isValid || player.isDead) {
            return false
        }
        if (player.gameMode == GameMode.SPECTATOR) {
            return false
        }
        return player.world.uid == vex.world.uid
    }

    private fun faceTowards(from: Location, to: Location): Location {
        val delta = to.toVector().subtract(from.toVector())
        val yaw = Math.toDegrees(kotlin.math.atan2(-delta.x, delta.z)).toFloat()
        val xz = kotlin.math.sqrt(delta.x * delta.x + delta.z * delta.z)
        val pitch = (-Math.toDegrees(kotlin.math.atan2(delta.y, xz.coerceAtLeast(1.0e-6)))).toFloat()
        return from.clone().apply {
            this.yaw = yaw
            this.pitch = pitch
        }
    }

    private fun createHeadItem(): ItemStack {
        val item = ItemStack(Material.PLAYER_HEAD)
        val skullMeta = item.itemMeta as? SkullMeta ?: return item
        val profile = Bukkit.createProfile(UUID.randomUUID(), "ender_ghost")
        profile.setProperty(ProfileProperty("textures", headTextureValue))
        skullMeta.playerProfile = profile
        item.itemMeta = skullMeta
        return item
    }

    private companion object {
        // Vex 本体とヘッド display の基準位置差。実機確認しながら微調整前提。
        const val DISPLAY_Y_OFFSET = 0.35
        val ZERO_VECTOR: Vector = Vector(0, 0, 0)
        val IGNORED_DAMAGE_CAUSES = setOf(
            EntityDamageEvent.DamageCause.FALL,
            EntityDamageEvent.DamageCause.SUFFOCATION,
            EntityDamageEvent.DamageCause.FIRE,
            EntityDamageEvent.DamageCause.FIRE_TICK,
            EntityDamageEvent.DamageCause.LAVA
        )
    }
}
