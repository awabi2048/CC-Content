package jp.awabi2048.cccontent.mob.ability

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.entity.Display
import org.bukkit.entity.EntityType
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.LivingEntity
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.AxisAngle4f
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.UUID

class FlyingItemDisplay private constructor(
    private val display: ItemDisplay,
    private val itemStack: ItemStack
) {
    companion object {
        private const val SPIN_SPEED = 30.0
        private const val DISC_RADIUS = 0.3
        private const val DISC_HALF_HEIGHT = 0.15
        private const val BASE_ROTATION_ANGLE = 1.5708f

        fun spawn(world: World, location: Location, itemStack: ItemStack): FlyingItemDisplay {
            val display = world.spawnEntity(location, EntityType.ITEM_DISPLAY) as ItemDisplay
            display.setItemStack(itemStack.clone())
            display.billboard = Display.Billboard.FIXED
            display.brightness = Display.Brightness(15, 15)
            val baseRot = Quaternionf(AxisAngle4f(BASE_ROTATION_ANGLE, 1f, 0f, 0f))
            display.transformation = Transformation(
                Vector3f(0f, 0f, 0f),
                baseRot,
                Vector3f(0.55f, 0.55f, 0.55f),
                Quaternionf()
            )
            return FlyingItemDisplay(display, itemStack)
        }
    }

    val id: UUID = display.uniqueId

    fun updatePosition(position: Vector, direction: Vector, elapsedTicks: Long) {
        if (!display.isValid) return
        display.teleport(Location(display.world, position.x, position.y, position.z))
        val dir = direction.clone()
        if (dir.lengthSquared() < 0.0001) return
        dir.normalize()
        val yaw = kotlin.math.atan2(-dir.x, dir.z).toFloat()
        val spinAngle = (elapsedTicks.toDouble() * SPIN_SPEED).toFloat()

        val spinRot = Quaternionf(AxisAngle4f(spinAngle, 0f, 0f, 1f))
        val baseRot = Quaternionf(AxisAngle4f(BASE_ROTATION_ANGLE, 1f, 0f, 0f))
        val dirRot = Quaternionf().rotateY(yaw)
        val totalRot = dirRot.mul(baseRot.mul(spinRot))

        display.transformation = Transformation(
            Vector3f(0f, 0f, 0f),
            totalRot,
            Vector3f(0.55f, 0.55f, 0.55f),
            Quaternionf()
        )
    }

    fun checkHitsAlongSegment(
        world: World,
        from: Vector,
        to: Vector,
        ownerId: UUID,
        hitSet: MutableSet<UUID>,
        owner: LivingEntity,
        damage: Double,
        onHitPlayer: ((LivingEntity) -> Unit)? = null
    ): Boolean {
        val minX = minOf(from.x, to.x) - DISC_RADIUS
        val maxX = maxOf(from.x, to.x) + DISC_RADIUS
        val minY = minOf(from.y, to.y) - DISC_HALF_HEIGHT
        val maxY = maxOf(from.y, to.y) + DISC_HALF_HEIGHT
        val minZ = minOf(from.z, to.z) - DISC_RADIUS
        val maxZ = maxOf(from.z, to.z) + DISC_RADIUS

        val nearby = world.getNearbyEntities(
            Location(world, (minX + maxX) * 0.5, (minY + maxY) * 0.5, (minZ + maxZ) * 0.5),
            (maxX - minX) * 0.5,
            (maxY - minY) * 0.5,
            (maxZ - minZ) * 0.5
        )

        for (entity in nearby) {
            if (entity.uniqueId == ownerId) continue
            val living = entity as? LivingEntity ?: continue
            if (living.isDead || !living.isValid) continue
            if (!isPlayer(living)) continue
            if (!hitSet.add(living.uniqueId)) continue
            if (!intersectsDisc(living, from, to)) {
                hitSet.remove(living.uniqueId)
                continue
            }
            living.damage(damage, owner)
            onHitPlayer?.invoke(living)
            world.spawnParticle(Particle.CRIT, living.location.add(0.0, 1.0, 0.0), 4, 0.1, 0.1, 0.1, 0.02)
            world.playSound(living.location, Sound.ITEM_SHIELD_BREAK, 0.9f, 1.0f)
        }
        return false
    }

    fun cleanup() {
        if (display.isValid) display.remove()
    }

    fun isValid(): Boolean = display.isValid

    private fun intersectsDisc(entity: LivingEntity, from: Vector, to: Vector): Boolean {
        val eyePos = entity.eyeLocation.toVector()
        val ab = to.clone().subtract(from)
        val abLenSq = ab.lengthSquared()
        if (abLenSq <= 1e-6) {
            val distSq = eyePos.distanceSquared(from)
            return distSq <= DISC_RADIUS * DISC_RADIUS && kotlin.math.abs(eyePos.y - from.y) <= DISC_HALF_HEIGHT
        }
        val ap = eyePos.clone().subtract(from)
        val t = (ap.dot(ab) / abLenSq).coerceIn(0.0, 1.0)
        val closest = from.clone().add(ab.multiply(t))
        val horizontalDistSq = (eyePos.x - closest.x).let { dx -> dx * dx } + (eyePos.z - closest.z).let { dz -> dz * dz }
        val verticalDist = kotlin.math.abs(eyePos.y - closest.y)
        return horizontalDistSq <= DISC_RADIUS * DISC_RADIUS && verticalDist <= DISC_HALF_HEIGHT
    }

    private fun isPlayer(entity: org.bukkit.entity.Entity): Boolean = entity.type == org.bukkit.entity.EntityType.PLAYER
}
