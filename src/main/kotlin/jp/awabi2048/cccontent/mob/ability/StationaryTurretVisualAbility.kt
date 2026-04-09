package jp.awabi2048.cccontent.mob.ability

import com.destroystokyo.paper.profile.ProfileProperty
import jp.awabi2048.cccontent.mob.MobDeathContext
import jp.awabi2048.cccontent.mob.MobRuntimeContext
import jp.awabi2048.cccontent.mob.MobSpawnContext
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.entity.Guardian
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.sqrt

class StationaryTurretVisualAbility(
    override val id: String,
    private val headTextureValue: String,
    private val headScale: Float = 1.3f,
    private val frameScale: Float = 0.65f,
    private val headYOffset: Double = 0.9,
    private val frameYOffset: Double = -0.1
) : MobAbility {

    data class Runtime(
        var headDisplayId: UUID? = null,
        var frameDisplayId: UUID? = null,
        var anchorLocation: Location? = null
    ) : MobAbilityRuntime

    override fun tickIntervalTicks(): Long = 1L

    override fun createRuntime(context: MobSpawnContext): MobAbilityRuntime {
        return Runtime()
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

        val anchor = guardian.location.clone()
        rt.anchorLocation = anchor

        val headDisplay = guardian.world.spawn(
            anchor.clone().add(0.0, headYOffset, 0.0),
            ItemDisplay::class.java
        )
        headDisplay.setItemStack(createHeadItem())
        headDisplay.billboard = Display.Billboard.FIXED
        headDisplay.isInvulnerable = true
        headDisplay.interpolationDuration = 2
        headDisplay.teleportDuration = 2
        headDisplay.brightness = Display.Brightness(15, 15)
        headDisplay.transformation = Transformation(
            Vector3f(0f, 0f, 0f),
            Quaternionf(),
            Vector3f(headScale, headScale, headScale),
            Quaternionf()
        )
        headDisplay.addScoreboardTag("cc.mob.ender_shooter_head")
        rt.headDisplayId = headDisplay.uniqueId

        val frameDisplay = guardian.world.spawn(
            anchor.clone().add(0.0, frameYOffset, 0.0),
            BlockDisplay::class.java
        )
        frameDisplay.setBlock(Material.END_PORTAL_FRAME.createBlockData())
        frameDisplay.isInvulnerable = true
        frameDisplay.interpolationDuration = 2
        frameDisplay.teleportDuration = 2
        frameDisplay.brightness = Display.Brightness(15, 15)
        frameDisplay.transformation = Transformation(
            Vector3f(0f, -0.2f, 0f),
            Quaternionf(),
            Vector3f(frameScale, frameScale, frameScale),
            Quaternionf()
        )
        frameDisplay.addScoreboardTag("cc.mob.ender_shooter_frame")
        rt.frameDisplayId = frameDisplay.uniqueId
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val guardian = context.entity as? Guardian ?: return
        val rt = runtime as? Runtime ?: return

        val anchor = rt.anchorLocation ?: return
        val target = resolveTarget(guardian)
        guardian.target = target

        val headDisplay = rt.headDisplayId?.let { Bukkit.getEntity(it) as? ItemDisplay }
        val frameDisplay = rt.frameDisplayId?.let { Bukkit.getEntity(it) as? BlockDisplay }

        if (headDisplay != null && headDisplay.isValid) {
            val headLocation = anchor.clone().add(0.0, headYOffset, 0.0)
            headDisplay.teleport(target?.let { faceTowards(headLocation, it.eyeLocation.clone()) } ?: headLocation)
        }
        if (frameDisplay != null && frameDisplay.isValid) {
            frameDisplay.teleport(anchor.clone().add(0.0, frameYOffset, 0.0))
        }

        guardian.teleport(target?.let { faceTowards(anchor, it.eyeLocation.clone()) } ?: anchor)
    }

    override fun onDeath(context: MobDeathContext, runtime: MobAbilityRuntime?) {
        val rt = runtime as? Runtime ?: return
        rt.headDisplayId?.let { Bukkit.getEntity(it) }?.let { if (it.isValid) it.remove() }
        rt.frameDisplayId?.let { Bukkit.getEntity(it) }?.let { if (it.isValid) it.remove() }
    }

    private fun createHeadItem(): ItemStack {
        val item = ItemStack(Material.PLAYER_HEAD)
        val skullMeta = item.itemMeta as? SkullMeta ?: return item
        val profile = Bukkit.createProfile(UUID.randomUUID(), "ender_shooter")
        profile.setProperty(ProfileProperty("textures", headTextureValue))
        skullMeta.playerProfile = profile
        item.itemMeta = skullMeta
        return item
    }

    private fun resolveTarget(guardian: Guardian): Player? {
        val fixed = (guardian.target as? Player)?.takeIf { isValidTarget(guardian, it) }
        if (fixed != null) {
            return fixed
        }

        return guardian.getNearbyEntities(24.0, 18.0, 24.0)
            .asSequence()
            .mapNotNull { it as? Player }
            .filter { isValidTarget(guardian, it) }
            .minByOrNull { it.location.distanceSquared(guardian.location) }
    }

    private fun isValidTarget(guardian: Guardian, player: Player): Boolean {
        if (!player.isValid || player.isDead) {
            return false
        }
        if (player.gameMode == GameMode.SPECTATOR) {
            return false
        }
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
}
