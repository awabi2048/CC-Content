package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobDamagedContext
import jp.awabi2048.cccontent.mob.MobRuntimeContext
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Allay
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Vex
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityCombustEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class WaterSpiritAbility(
    override val id: String,
    private val approachDistance: Double = DEFAULT_APPROACH_DISTANCE,
    private val closeRangeDamage: Double = DEFAULT_CLOSE_RANGE_DAMAGE,
    private val farRangeOrbDamage: Double = DEFAULT_FAR_RANGE_ORB_DAMAGE,
    private val sharedCooldownTicks: Long = DEFAULT_SHARED_COOLDOWN_TICKS,
    private val teleportCooldownTicks: Long = DEFAULT_TELEPORT_COOLDOWN_TICKS,
    private val freezeTicks: Long = DEFAULT_FREEZE_TICKS,
    private val orbSpeed: Double = DEFAULT_ORB_SPEED,
    private val orbCount: Int = DEFAULT_ORB_COUNT,
    private val orbitRadius: Double = DEFAULT_ORBIT_RADIUS,
    private val orbitSpeed: Double = DEFAULT_ORBIT_SPEED
) : MobAbility {

    data class Runtime(
        var vexUuid: UUID? = null,
        var sharedCooldownTicks: Long = 0L,
        var teleportCooldownTicks: Long = 0L,
        var frozenTicks: Long = 0L,
        var searchPhaseOffsetSteps: Int = 0,
        var ambientParticleTicks: Long = 0L,
        var syncTask: BukkitTask? = null,
        var damageListener: Listener? = null,
        var attackListener: Listener? = null,
        var combustListener: Listener? = null,
        var interactListener: Listener? = null,
        var plugin: JavaPlugin? = null
    ) : MobAbilityRuntime

    override fun createRuntime(context: jp.awabi2048.cccontent.mob.MobSpawnContext): MobAbilityRuntime {
        return Runtime(
            sharedCooldownTicks = sharedCooldownTicks,
            searchPhaseOffsetSteps = Random.nextInt(0, SEARCH_PHASE_VARIANTS)
        )
    }

    override fun onSpawn(context: jp.awabi2048.cccontent.mob.MobSpawnContext, runtime: MobAbilityRuntime?) {
        val allay = context.entity as? Allay ?: return
        Bukkit.getMobGoals().removeAllGoals(allay)
        allay.isSilent = true

        val loc = allay.location
        val vex = loc.world!!.spawn(loc, Vex::class.java)
        vex.isInvisible = true
        vex.isSilent = true
        vex.isInvulnerable = true
        vex.isGlowing = false
        vex.addPotionEffect(PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false, false))
        vex.isAware = true
        vex.setLimitedLifetime(false)
        vex.equipment?.setItemInMainHand(ItemStack(Material.AIR))
        vex.getAttribute(Attribute.SCALE)?.baseValue = 0.1

        val rt = runtime as? Runtime ?: return
        rt.vexUuid = vex.uniqueId
        rt.plugin = context.plugin

        loc.world!!.playSound(loc, Sound.ENTITY_WITHER_SHOOT, 0.6f, 1.2f)

        val damageListener = object : Listener {
            @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
            fun onDamage(event: EntityDamageEvent) {
                if (event.entity.uniqueId == allay.uniqueId) {
                    when (event.cause) {
                        EntityDamageEvent.DamageCause.SUFFOCATION,
                        EntityDamageEvent.DamageCause.FIRE,
                        EntityDamageEvent.DamageCause.FIRE_TICK,
                        EntityDamageEvent.DamageCause.LAVA -> event.isCancelled = true
                        else -> {}
                    }
                }
            }
        }
        Bukkit.getPluginManager().registerEvents(damageListener, context.plugin)
        rt.damageListener = damageListener

        val attackListener = object : Listener {
            @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
            fun onVexAttack(event: EntityDamageByEntityEvent) {
                val vexId = rt.vexUuid ?: return
                if (event.damager.uniqueId == vexId && event.entity is Player) {
                    event.isCancelled = true
                }
            }
        }
        Bukkit.getPluginManager().registerEvents(attackListener, context.plugin)
        rt.attackListener = attackListener

        val combustListener = object : Listener {
            @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
            fun onCombust(event: EntityCombustEvent) {
                if (event.entity.uniqueId == allay.uniqueId) {
                    event.isCancelled = true
                }
            }
        }
        Bukkit.getPluginManager().registerEvents(combustListener, context.plugin)
        rt.combustListener = combustListener

        val interactListener = object : Listener {
            @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
            fun onInteract(event: PlayerInteractEntityEvent) {
                if (event.rightClicked.uniqueId == allay.uniqueId) {
                    event.isCancelled = true
                }
            }
        }
        Bukkit.getPluginManager().registerEvents(interactListener, context.plugin)
        rt.interactListener = interactListener

        val vexUuid = vex.uniqueId
        rt.syncTask = Bukkit.getScheduler().runTaskTimer(context.plugin, Runnable {
            if (!allay.isValid || allay.isDead) {
                rt.syncTask?.cancel()
                rt.syncTask = null
                return@Runnable
            }

            if (rt.frozenTicks > 0L) {
                rt.frozenTicks -= 1L
                allay.fireTicks = 0
                val targetPlayer = allay.target as? Player
                if (targetPlayer != null && targetPlayer.isValid && !targetPlayer.isDead) {
                    val from = allay.location.clone().add(0.0, 1.0, 0.0)
                    val to = targetPlayer.location.clone().add(0.0, 1.0, 0.0)
                    val dir = to.toVector().subtract(from.toVector())
                    val yaw = Math.toDegrees(atan2(-dir.x, dir.z)).toFloat()
                    val pitch = (-Math.toDegrees(atan2(dir.y, sqrt(dir.x * dir.x + dir.z * dir.z)))).toFloat()
                    val newLoc = allay.location.clone()
                    newLoc.yaw = yaw
                    newLoc.pitch = pitch
                    allay.teleport(newLoc)
                }
                if (rt.frozenTicks == 0L) {
                    val vexEntity = Bukkit.getEntity(vexUuid) as? Vex
                    if (vexEntity != null && vexEntity.isValid && !vexEntity.isDead) {
                        vexEntity.teleport(allay.location)
                    }
                }
                return@Runnable
            }

            val vexEntity = Bukkit.getEntity(vexUuid) as? Vex
            if (vexEntity == null || !vexEntity.isValid || vexEntity.isDead) {
                val newVex = allay.world!!.spawn(allay.location, Vex::class.java)
                newVex.isInvisible = true
                newVex.isSilent = true
                newVex.isInvulnerable = true
                newVex.addPotionEffect(PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false, false))
                newVex.isAware = true
                newVex.setLimitedLifetime(false)
                newVex.equipment?.setItemInMainHand(ItemStack(Material.AIR))
                newVex.getAttribute(Attribute.SCALE)?.baseValue = 0.1
                rt.vexUuid = newVex.uniqueId
                return@Runnable
            }
            val target = vexEntity.target
            if (target != null && target.isValid && !target.isDead) {
                allay.target = target
            }
            allay.teleport(vexEntity.location)
            allay.fireTicks = 0

            val targetPlayer = target as? Player
            if (targetPlayer != null && targetPlayer.isValid && !targetPlayer.isDead) {
                val from = allay.location.clone().add(0.0, 1.0, 0.0)
                val to = targetPlayer.location.clone().add(0.0, 1.0, 0.0)
                val dir = to.toVector().subtract(from.toVector())
                val yaw = Math.toDegrees(atan2(-dir.x, dir.z)).toFloat()
                val pitch = (-Math.toDegrees(atan2(dir.y, sqrt(dir.x * dir.x + dir.z * dir.z)))).toFloat()
                val newLoc = allay.location.clone()
                newLoc.yaw = yaw
                newLoc.pitch = pitch
                allay.teleport(newLoc)
            }
        }, 1L, 1L)
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val rt = runtime as? Runtime ?: return

        if (rt.sharedCooldownTicks > 0L) {
            rt.sharedCooldownTicks -= 10L
        }
        if (rt.teleportCooldownTicks > 0L) {
            rt.teleportCooldownTicks -= 10L
        }
        rt.ambientParticleTicks += 10L

        val entity = context.entity
        val vexUuid = rt.vexUuid
        val vex = if (vexUuid != null) Bukkit.getEntity(vexUuid) as? Vex else null

        if (vex != null && vex.isValid && !vex.isDead) {
            updateVexTarget(vex, entity)
        }

        spawnAmbientParticles(entity, rt)

        if (entity.location.block.type != Material.AIR && rt.teleportCooldownTicks <= 0L) {
            val safeLoc = findSafeLocation(entity)
            if (safeLoc != null) {
                performTeleportEffect(entity.location, safeLoc)
                val vexEntity = vex
                if (vexEntity != null && vexEntity.isValid && !vexEntity.isDead) {
                    vexEntity.teleport(safeLoc)
                }
                entity.teleport(safeLoc)
                rt.frozenTicks = freezeTicks
                rt.teleportCooldownTicks = teleportCooldownTicks
            }
        }

        if (!context.isCombatActive()) return

        val target = findNearestPlayer(entity) ?: return
        if (!target.isValid || target.isDead) return

        val distance = entity.location.distance(target.location)

        if (rt.sharedCooldownTicks <= 0L) {
            if (distance <= approachDistance) {
                executeMeleeAttack(entity, target)
                rt.sharedCooldownTicks = sharedCooldownTicks
                scheduleTeleport(entity, rt, freeFarAttack = true)
            } else if (entity.hasLineOfSight(target)) {
                executeFarAttack(context.plugin, entity, target)
                rt.sharedCooldownTicks = (sharedCooldownTicks * context.loadSnapshot.abilityCooldownMultiplier).roundToLong().coerceAtLeast(sharedCooldownTicks)
            }
        }
    }

    override fun onDamaged(context: MobDamagedContext, runtime: MobAbilityRuntime?) {
        val entity = context.entity
        val world = entity.world ?: return
        world.playSound(entity.location, Sound.ENTITY_BLAZE_HURT, 0.8f, 0.75f)
    }

    override fun onDeath(context: jp.awabi2048.cccontent.mob.MobDeathContext, runtime: MobAbilityRuntime?) {
        val rt = runtime as? Runtime ?: return
        val entity = context.entity
        entity.world?.playSound(entity.location, Sound.ENTITY_BLAZE_DEATH, 1.0f, 0.75f)
        rt.syncTask?.cancel()
        rt.syncTask = null
        rt.damageListener?.let { HandlerList.unregisterAll(it) }
        rt.damageListener = null
        rt.attackListener?.let { HandlerList.unregisterAll(it) }
        rt.attackListener = null
        rt.combustListener?.let { HandlerList.unregisterAll(it) }
        rt.combustListener = null
        rt.interactListener?.let { HandlerList.unregisterAll(it) }
        rt.interactListener = null
        rt.vexUuid?.let { uuid ->
            val vex = Bukkit.getEntity(uuid) as? Vex
            if (vex != null && vex.isValid && !vex.isDead) {
                vex.remove()
            }
        }
    }

    private fun updateVexTarget(vex: Vex, allay: LivingEntity) {
        val target = findNearestPlayer(allay)
        if (target != null && target.isValid && !target.isDead) {
            vex.target = target
        } else {
            vex.target = null
        }
    }

    private fun findNearestPlayer(entity: LivingEntity): Player? {
        return entity.getNearbyEntities(SEARCH_RANGE, SEARCH_RANGE, SEARCH_RANGE)
            .asSequence()
            .filterIsInstance<Player>()
            .filter { it.isValid && !it.isDead }
            .minByOrNull { it.location.distanceSquared(entity.location) }
    }

    private fun spawnAmbientParticles(entity: LivingEntity, rt: Runtime) {
        if (rt.ambientParticleTicks % 5L != 0L) return
        val loc = entity.location.clone().add(0.0, 1.0, 0.0)
        val world = loc.world ?: return
        for (i in 0 until 20) {
            val angle = Random.nextDouble(0.0, Math.PI * 2)
            val radius = 0.9 + Random.nextDouble(0.0, 0.6)
            val x = cos(angle) * radius
            val z = sin(angle) * radius
            val y = Random.nextDouble(-0.5, 0.5)
            world.spawnParticle(Particle.DOLPHIN, loc.clone().add(x, y, z), 1, 0.0, 0.0, 0.0, 0.0)
        }
    }

    private fun findSafeLocation(entity: LivingEntity): Location? {
        val world = entity.world
        val baseX = entity.location.blockX
        val baseY = entity.location.blockY
        val baseZ = entity.location.blockZ
        for (dy in 0..10) {
            val block = world.getBlockAt(baseX, baseY + dy, baseZ)
            if (block.type == Material.AIR) {
                return Location(world, baseX + 0.5, baseY + dy.toDouble(), baseZ + 0.5, entity.location.yaw, entity.location.pitch)
            }
        }
        for (attempt in 0 until 50) {
            val angle = Random.nextDouble(0.0, Math.PI * 2)
            val hDist = Random.nextDouble(1.0, 10.0)
            val yOff = Random.nextDouble(-5.0, 10.0)
            val tx = baseX + cos(angle) * hDist
            val ty = baseY + yOff
            val tz = baseZ + sin(angle) * hDist
            val block = world.getBlockAt(tx.toInt(), ty.toInt(), tz.toInt())
            if (block.type == Material.AIR) {
                return Location(world, tx + 0.5, ty.toDouble(), tz + 0.5, entity.location.yaw, entity.location.pitch)
            }
        }
        return null
    }

    private fun performTeleportEffect(from: Location, to: Location) {
        val world = from.world ?: return
        world.playSound(from, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.8f, 1.5f)
        world.spawnParticle(Particle.DRIPPING_DRIPSTONE_WATER, from.clone().add(0.0, 1.0, 0.0), 120, 1.0, 1.0, 1.0, 0.0)
    }

    private fun executeMeleeAttack(entity: LivingEntity, target: LivingEntity) {
        val loc = target.location.clone().add(0.0, 1.0, 0.0)
        val world = loc.world ?: return
        world.spawnParticle(Particle.SPLASH, loc, 20, 0.3, 0.5, 0.3, 0.1)
        world.spawnParticle(Particle.BUBBLE_COLUMN_UP, loc, 10, 0.2, 0.8, 0.2, 0.05)
        world.playSound(loc, Sound.ENTITY_ELDER_GUARDIAN_DEATH, 0.8f, 2.0f)
        target.damage(closeRangeDamage, entity)
    }

    private fun scheduleTeleport(entity: LivingEntity, rt: Runtime, freeFarAttack: Boolean = false) {
        val plugin = rt.plugin ?: return
        val entityId = entity.uniqueId
        val vexId = rt.vexUuid
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            val alive = Bukkit.getEntity(entityId) as? LivingEntity
            if (alive == null || !alive.isValid || alive.isDead) return@Runnable
            val world = alive.world ?: return@Runnable
            val currentLoc = alive.location

            val theta = Random.nextDouble(0.0, Math.PI * 2)
            val phi = Random.nextDouble(0.0, Math.PI)
            val r = 8.0
            val newX = currentLoc.x + r * sin(phi) * cos(theta)
            val newY = currentLoc.y + r * cos(phi)
            val newZ = currentLoc.z + r * sin(phi) * sin(theta)
            val newLoc = Location(world, newX, newY, newZ, currentLoc.yaw, currentLoc.pitch)

            performTeleportEffect(currentLoc, newLoc)

            val vexEntity = if (vexId != null) Bukkit.getEntity(vexId) as? Vex else null
            if (vexEntity != null && vexEntity.isValid && !vexEntity.isDead) {
                vexEntity.teleport(newLoc)
            }
            alive.teleport(newLoc)

            rt.frozenTicks = freezeTicks
            rt.teleportCooldownTicks = teleportCooldownTicks

            if (freeFarAttack) {
                val target = findNearestPlayer(alive)
                if (target != null && target.isValid && !target.isDead && alive.hasLineOfSight(target)) {
                    executeFarAttack(plugin, alive, target)
                }
            }
        }, 5L)
    }

    private fun executeFarAttack(plugin: JavaPlugin, entity: LivingEntity, target: LivingEntity) {
        val world = entity.world ?: return
        val origin = entity.location.clone().add(0.0, 1.0, 0.0)

        for (i in 0 until orbCount) {
            val angleOffset = (i.toDouble() / orbCount) * Math.PI * 2
            launchRotatingOrb(plugin, entity, target, origin, angleOffset, farRangeOrbDamage)
        }

        world.playSound(entity.location, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 0.6f, 1.4f)
    }

    private fun launchRotatingOrb(
        plugin: JavaPlugin,
        entity: LivingEntity,
        target: LivingEntity,
        origin: Location,
        angleOffset: Double,
        damage: Double
    ) {
        val world = origin.world ?: return
        var currentPos = origin.clone()
        var traveledDistance = 0.0
        val maxDistance = 30.0
        val stepDistance = orbSpeed / 20.0
        var tickCount = 0L

        object : BukkitRunnable() {
            override fun run() {
                if (!entity.isValid || entity.isDead || !target.isValid || target.isDead) {
                    cancel()
                    return
                }

                if (traveledDistance >= maxDistance) {
                    cancel()
                    return
                }

                tickCount++
                val orbitAngle = angleOffset + tickCount * orbitSpeed
                val orbitOffsetX = cos(orbitAngle) * orbitRadius
                val orbitOffsetZ = sin(orbitAngle) * orbitRadius
                val orbitOffsetY = sin(orbitAngle * 0.7) * orbitRadius * 0.5

                val targetPos = target.location.clone().add(0.0, 1.0, 0.0)
                val toTarget = targetPos.toVector().subtract(currentPos.toVector())
                val distToTarget = toTarget.length()

                if (distToTarget < 1.2) {
                    target.damage(damage, entity)
                    world.spawnParticle(Particle.SPLASH, currentPos, 8, 0.2, 0.2, 0.2, 0.05)
                    world.playSound(currentPos, Sound.ENTITY_PLAYER_SPLASH, 0.5f, 1.2f)
                    cancel()
                    return
                }

                val moveDir = toTarget.normalize().multiply(stepDistance)
                currentPos.add(moveDir.x + orbitOffsetX * 0.1, moveDir.y + orbitOffsetY * 0.05, moveDir.z + orbitOffsetZ * 0.1)
                traveledDistance += stepDistance

                val dustColor = Color.fromRGB(100, 180, 255)
                world.spawnParticle(
                    Particle.DUST,
                    currentPos.clone(),
                    2,
                    0.1, 0.1, 0.1,
                    0.0,
                    Particle.DustOptions(dustColor, 0.6f)
                )
                world.spawnParticle(Particle.BUBBLE, currentPos.clone(), 1, 0.05, 0.05, 0.05, 0.0)
            }
        }.runTaskTimer(plugin, 0L, 1L)
    }

    companion object {
        const val DEFAULT_APPROACH_DISTANCE = 5.0
        const val DEFAULT_CLOSE_RANGE_DAMAGE = 5.0
        const val DEFAULT_FAR_RANGE_ORB_DAMAGE = 3.5
        const val DEFAULT_SHARED_COOLDOWN_TICKS = 60L
        const val DEFAULT_TELEPORT_COOLDOWN_TICKS = 20L
        const val DEFAULT_FREEZE_TICKS = 20L
        const val DEFAULT_ORB_SPEED = 16.0
        const val DEFAULT_ORB_COUNT = 3
        const val DEFAULT_ORBIT_RADIUS = 0.4
        const val DEFAULT_ORBIT_SPEED = 0.15
        private const val SEARCH_PHASE_VARIANTS = 16
        private const val SEARCH_RANGE = 40.0
    }
}
