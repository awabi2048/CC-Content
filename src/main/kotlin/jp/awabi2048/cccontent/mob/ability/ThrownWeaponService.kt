package jp.awabi2048.cccontent.mob.ability

import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.Item
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Vector
import java.util.UUID
import java.util.WeakHashMap

class ThrownWeaponService private constructor(private val plugin: JavaPlugin) {
    data class LaunchSpec(
        val ownerId: UUID,
        val projectile: Projectile,
        val weapon: ItemStack,
        val damage: Double,
        val loaderKey: String
    )

    private data class ActiveThrownWeapon(
        val ownerId: UUID,
        val projectileId: UUID,
        val flyingDisplay: FlyingItemDisplay?,
        val weapon: ItemStack,
        val damage: Double,
        val loaderKey: String
    )

    private data class GroundWeapon(
        val itemId: UUID,
        val weapon: ItemStack,
        val loaderKey: String,
        val stuckDisplayId: UUID?,
        var ttlTicks: Long
    )

    companion object {
        private val instances = WeakHashMap<JavaPlugin, ThrownWeaponService>()
        private const val RELOAD_RANGE = 1.2
        private const val GROUND_TTL_TICKS = 20L * 30L
        private const val PLAYER_PICKUP_DISABLED_KEY = "weapon_throw_player_pickup_disabled"
        private const val DISPLAY_UPDATE_INTERVAL_TICKS = 2L
        private const val GROUND_PICKUP_SEARCH_INTERVAL_TICKS = 5L

        fun getInstance(plugin: JavaPlugin): ThrownWeaponService {
            synchronized(instances) {
                return instances.getOrPut(plugin) { ThrownWeaponService(plugin) }
            }
        }
    }

    private val activeThrows = mutableMapOf<UUID, ActiveThrownWeapon>()
    private val ownerToProjectile = mutableMapOf<UUID, UUID>()
    private val groundWeapons = mutableMapOf<UUID, GroundWeapon>()
    private var task: BukkitTask? = null
    private var tickCounter: Long = 0L

    fun launch(spec: LaunchSpec): Boolean {
        if (ownerToProjectile.containsKey(spec.ownerId)) return false
        val projectile = spec.projectile
        val world = projectile.world

        val flyingDisplay = FlyingItemDisplay.spawn(world, projectile.location, spec.weapon.clone())
        hideInternalProjectile(projectile)

        val active = ActiveThrownWeapon(
            ownerId = spec.ownerId,
            projectileId = projectile.uniqueId,
            flyingDisplay = flyingDisplay,
            weapon = spec.weapon.clone(),
            damage = spec.damage,
            loaderKey = spec.loaderKey
        )
        activeThrows[projectile.uniqueId] = active
        ownerToProjectile[spec.ownerId] = projectile.uniqueId
        ensureTask()
        return true
    }

    fun hasActiveThrow(ownerId: UUID): Boolean = ownerToProjectile.containsKey(ownerId)

    fun findNearestGroundWeapon(loaderKey: String, origin: org.bukkit.Location, searchRange: Double): Item? {
        val key = NamespacedKey(plugin, loaderKey)
        return origin.world
            ?.getNearbyEntities(origin, searchRange, searchRange, searchRange)
            ?.asSequence()
            ?.mapNotNull { it as? Item }
            ?.filter { item ->
                item.isValid &&
                    !item.isDead &&
                    item.persistentDataContainer.get(key, PersistentDataType.BYTE)?.toInt() == 1
            }
            ?.minByOrNull { item -> item.location.distanceSquared(origin) }
    }

    fun handleProjectileDamage(event: EntityDamageByEntityEvent) {
        val projectile = event.damager as? Projectile ?: return
        val active = activeThrows[projectile.uniqueId] ?: return
        val target = event.entity as? LivingEntity ?: return
        if (target !is Player) return

        event.damage = active.damage
        target.world.playSound(target.location, org.bukkit.Sound.ITEM_SHIELD_BREAK, 0.9f, 1.0f)
        toGroundWeapon(active, projectile.location, resolveImpactDirection(projectile))
        projectile.remove()
        removeActive(projectile.uniqueId)
    }

    fun handleProjectileHit(event: ProjectileHitEvent) {
        val active = activeThrows[event.entity.uniqueId] ?: return
        event.entity.world.playSound(event.entity.location, org.bukkit.Sound.ITEM_SHIELD_BREAK, 0.9f, 1.0f)
        toGroundWeapon(active, event.entity.location, resolveImpactDirection(event.entity))
        event.entity.remove()
        removeActive(event.entity.uniqueId)
    }

    fun handleItemPickup(event: EntityPickupItemEvent) {
        val picker = event.entity as? Player ?: return
        val key = NamespacedKey(plugin, PLAYER_PICKUP_DISABLED_KEY)
        if (event.item.persistentDataContainer.get(key, PersistentDataType.BYTE)?.toInt() == 1) {
            event.isCancelled = true
            picker.playSound(picker.location, org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.35f, 0.7f)
        }
    }

    fun shutdown() {
        task?.cancel()
        task = null

        activeThrows.values.forEach { active ->
            active.flyingDisplay?.cleanup()
            Bukkit.getEntity(active.projectileId)?.remove()
        }
        groundWeapons.values.forEach { ground ->
            Bukkit.getEntity(ground.itemId)?.remove()
            removeStuckDisplay(ground.stuckDisplayId)
        }

        activeThrows.clear()
        ownerToProjectile.clear()
        groundWeapons.clear()

        synchronized(instances) { instances.remove(plugin) }
    }

    private fun ensureTask() {
        if (task != null) return
        task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable { tick() }, 1L, 1L)
    }

    private fun tick() {
        tickCounter += 1L
        val shouldUpdateDisplay = tickCounter % DISPLAY_UPDATE_INTERVAL_TICKS == 0L
        val shouldSearchGroundPickup = tickCounter % GROUND_PICKUP_SEARCH_INTERVAL_TICKS == 0L

        val activeIterator = activeThrows.entries.iterator()
        while (activeIterator.hasNext()) {
            val (projectileId, active) = activeIterator.next()
            val projectile = Bukkit.getEntity(projectileId) as? Projectile
            if (projectile == null || !projectile.isValid || projectile.isDead) {
                active.flyingDisplay?.cleanup()
                ownerToProjectile.remove(active.ownerId)
                activeIterator.remove()
                continue
            }

            active.flyingDisplay?.syncFromProjectile(projectile, projectile.ticksLived.toLong())

            if (shouldUpdateDisplay) {
                val vel = projectile.velocity
                active.flyingDisplay?.updatePosition(
                    projectile.location.toVector(),
                    if (vel.lengthSquared() > 0.0001) vel else projectile.location.direction,
                    projectile.ticksLived.toLong()
                )
            }
        }

        val groundIterator = groundWeapons.entries.iterator()
        while (groundIterator.hasNext()) {
            val (_, ground) = groundIterator.next()
            ground.ttlTicks -= 1L
            if (ground.ttlTicks <= 0L) {
                Bukkit.getEntity(ground.itemId)?.remove()
                removeStuckDisplay(ground.stuckDisplayId)
                groundIterator.remove()
                continue
            }

            val item = Bukkit.getEntity(ground.itemId) as? Item
            if (item == null || !item.isValid || item.isDead) {
                item?.remove()
                removeStuckDisplay(ground.stuckDisplayId)
                groundIterator.remove()
                continue
            }

            if (!shouldSearchGroundPickup) continue

            val loaderKey = NamespacedKey(plugin, ground.loaderKey)
            val nearby = item.world.getNearbyEntities(item.location, RELOAD_RANGE, RELOAD_RANGE, RELOAD_RANGE)
            val loader = nearby
                .asSequence()
                .mapNotNull { it as? Mob }
                .firstOrNull { mob ->
                    mob.isValid &&
                        !mob.isDead &&
                        mob.equipment?.itemInMainHand?.type?.isAir == true &&
                        mob.persistentDataContainer.get(loaderKey, PersistentDataType.BYTE)?.toInt() == 1
                }

            if (loader != null) {
                loader.equipment?.setItemInMainHand(ground.weapon.clone())
                loader.world.playSound(loader.location, org.bukkit.Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.05f)
                item.remove()
                removeStuckDisplay(ground.stuckDisplayId)
                groundIterator.remove()
            }
        }

        if (activeThrows.isEmpty() && groundWeapons.isEmpty()) {
            task?.cancel()
            task = null
        }
    }

    private fun toGroundWeapon(active: ActiveThrownWeapon, location: org.bukkit.Location, impactDirection: Vector) {
        val normalizedDirection = if (impactDirection.lengthSquared() > 1.0E-6) {
            impactDirection.clone().normalize()
        } else {
            Vector(0.0, -1.0, 0.0)
        }

        val world = location.world ?: return
        val dropLoc = location.clone().add(0.0, 0.1, 0.0)

        val stuckDisplayId = active.flyingDisplay?.let { display ->
            if (display.isValid()) {
                display.freezeAt(dropLoc.toVector(), normalizedDirection)
                display.id
            } else {
                display.cleanup()
                null
            }
        }

        val droppedItem = world.dropItem(dropLoc, active.weapon.clone())
        droppedItem.pickupDelay = Int.MAX_VALUE
        droppedItem.isUnlimitedLifetime = true
        droppedItem.velocity = Vector(0.0, 0.0, 0.0)
        droppedItem.persistentDataContainer.set(
            NamespacedKey(plugin, PLAYER_PICKUP_DISABLED_KEY),
            PersistentDataType.BYTE,
            1
        )
        droppedItem.persistentDataContainer.set(
            NamespacedKey(plugin, active.loaderKey),
            PersistentDataType.BYTE,
            1
        )

        groundWeapons[droppedItem.uniqueId] = GroundWeapon(
            itemId = droppedItem.uniqueId,
            weapon = active.weapon.clone(),
            loaderKey = active.loaderKey,
            stuckDisplayId = stuckDisplayId,
            ttlTicks = GROUND_TTL_TICKS
        )
    }

    private fun resolveImpactDirection(projectile: Projectile): Vector {
        val velocity = projectile.velocity
        if (velocity.lengthSquared() > 1.0E-6) {
            return velocity
        }
        val fallback = projectile.location.direction
        return if (fallback.lengthSquared() > 1.0E-6) fallback else Vector(0.0, -1.0, 0.0)
    }

    private fun removeStuckDisplay(stuckDisplayId: UUID?) {
        if (stuckDisplayId == null) return
        val entity = Bukkit.getEntity(stuckDisplayId)
        if (entity != null && entity.isValid) {
            entity.remove()
        }
    }

    private fun hideInternalProjectile(projectile: Projectile) {
        runCatching {
            val method = projectile.javaClass.methods.firstOrNull { candidate ->
                candidate.name == "setVisibleByDefault" &&
                    candidate.parameterCount == 1 &&
                    candidate.parameterTypes[0] == Boolean::class.javaPrimitiveType
            }
            method?.invoke(projectile, false)
        }
    }

    private fun removeActive(projectileId: UUID) {
        val active = activeThrows.remove(projectileId) ?: return
        ownerToProjectile.remove(active.ownerId)
    }
}
