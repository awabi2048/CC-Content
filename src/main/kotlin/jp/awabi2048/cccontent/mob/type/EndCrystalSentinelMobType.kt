package jp.awabi2048.cccontent.mob.type

import jp.awabi2048.cccontent.CCContent
import jp.awabi2048.cccontent.mob.CustomMobRuntime
import jp.awabi2048.cccontent.mob.EntityMobDamagedContext
import jp.awabi2048.cccontent.mob.EntityMobDeathContext
import jp.awabi2048.cccontent.mob.EntityMobRuntimeContext
import jp.awabi2048.cccontent.mob.EntityMobSpawnContext
import jp.awabi2048.cccontent.mob.EntityMobType
import jp.awabi2048.cccontent.mob.MobService
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.EnderCrystal
import org.bukkit.entity.EntityType
import org.bukkit.entity.Mob
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToLong
import kotlin.random.Random

class EndCrystalSentinelMobType : EntityMobType {
    override val id: String = "end_crystal_sentinel"
    override val baseEntityType: EntityType = EntityType.END_CRYSTAL

    private enum class Mode {
        ATTACK,
        DEFENSE
    }

    private data class NoiseProjectile(
        var position: Location,
        var velocity: Vector,
        val targetId: UUID?,
        var remainingTicks: Int
    )

    private data class Runtime(
        var mode: Mode = Mode.ATTACK,
        var modeSwitchTicks: Long = MODE_SWITCH_TICKS,
        var attackCooldownTicks: Long = 0L,
        var burstRemaining: Int = 0,
        var burstIntervalTicks: Long = 0L,
        var defenseDischargeCooldownTicks: Long = 0L,
        var defensePreCastTicks: Long = 0L,
        var defenseFreezeTicks: Long = 0L,
        var defenseTargetId: UUID? = null,
        var teleportCooldownTicks: Long = TELEPORT_COOLDOWN_TICKS,
        var virtualHealth: Double = DEFENSE_MAX_HEALTH,
        var satelliteTicks: Long = 0L,
        val projectiles: MutableList<NoiseProjectile> = mutableListOf()
    ) : CustomMobRuntime

    private data class BoltSegment(
        val start: Vector,
        val end: Vector,
        val depth: Int,
        val delayTicks: Int
    )

    override fun createRuntime(context: EntityMobSpawnContext): CustomMobRuntime {
        return Runtime()
    }

    override fun onSpawn(context: EntityMobSpawnContext, runtime: CustomMobRuntime?) {
        val crystal = context.entity as? EnderCrystal ?: return
        val rt = runtime as? Runtime ?: return
        crystal.isShowingBottom = false
        crystal.isInvulnerable = true
        crystal.isSilent = true
        rt.virtualHealth = DEFENSE_MAX_HEALTH
    }

    override fun onTick(context: EntityMobRuntimeContext, runtime: CustomMobRuntime?) {
        val crystal = context.entity as? EnderCrystal ?: return
        val rt = runtime as? Runtime ?: return
        if (!crystal.isValid || crystal.isDead) return

        updateTimers(context.tickDelta, rt)
        tickProjectiles(context, crystal, rt)
        emitSatellites(crystal, rt)

        if (rt.modeSwitchTicks <= 0L) {
            switchMode(crystal, rt)
        }

        if (rt.teleportCooldownTicks <= 0L) {
            processTeleport(context, crystal, rt)
            rt.teleportCooldownTicks = TELEPORT_COOLDOWN_TICKS
        }

        when (rt.mode) {
            Mode.ATTACK -> processAttackMode(context, crystal, rt)
            Mode.DEFENSE -> processDefenseMode(context, crystal, rt)
        }
    }

    override fun onDamaged(context: EntityMobDamagedContext, runtime: CustomMobRuntime?) {
        val crystal = context.entity as? EnderCrystal ?: return
        val rt = runtime as? Runtime ?: return

        if (rt.mode != Mode.DEFENSE) {
            context.event.isCancelled = true
            playDamageNullifiedFeedback(crystal.location)
            return
        }

        val attacker = resolvePlayerAttacker(context.event) ?: run {
            context.event.isCancelled = true
            playDamageNullifiedFeedback(crystal.location)
            return
        }
        if (!attacker.isValid || attacker.isDead || attacker.world.uid != crystal.world.uid) {
            context.event.isCancelled = true
            playDamageNullifiedFeedback(crystal.location)
            return
        }

        context.event.isCancelled = true
        val rawDamage = context.event.finalDamage.coerceAtLeast(context.event.damage).coerceAtLeast(0.0)
        if (rawDamage <= 0.0) {
            playDamageNullifiedFeedback(crystal.location)
            return
        }

        rt.virtualHealth = (rt.virtualHealth - rawDamage).coerceAtLeast(0.0)
        crystal.world.spawnParticle(Particle.ENCHANTED_HIT, crystal.location.clone().add(0.0, 0.7, 0.0), 12, 0.24, 0.24, 0.24, 0.0)
        crystal.world.playSound(crystal.location, Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.8f, 1.0f)

        if (rt.virtualHealth <= 0.0) {
            killCrystal(context.plugin, crystal)
        }
    }

    override fun onDeath(context: EntityMobDeathContext, runtime: CustomMobRuntime?) {
        val rt = runtime as? Runtime ?: return
        rt.projectiles.clear()
    }

    private fun updateTimers(tickDelta: Long, rt: Runtime) {
        rt.modeSwitchTicks -= tickDelta
        rt.attackCooldownTicks = (rt.attackCooldownTicks - tickDelta).coerceAtLeast(0L)
        rt.burstIntervalTicks = (rt.burstIntervalTicks - tickDelta).coerceAtLeast(0L)
        rt.defenseDischargeCooldownTicks = (rt.defenseDischargeCooldownTicks - tickDelta).coerceAtLeast(0L)
        rt.teleportCooldownTicks = (rt.teleportCooldownTicks - tickDelta).coerceAtLeast(0L)
        rt.satelliteTicks += tickDelta
    }

    private fun switchMode(crystal: EnderCrystal, rt: Runtime) {
        rt.mode = if (rt.mode == Mode.ATTACK) Mode.DEFENSE else Mode.ATTACK
        rt.modeSwitchTicks = MODE_SWITCH_TICKS
        rt.burstRemaining = 0
        rt.burstIntervalTicks = 0L
        rt.attackCooldownTicks = 0L
        rt.defenseDischargeCooldownTicks = 0L
        rt.defensePreCastTicks = 0L
        rt.defenseFreezeTicks = 0L
        rt.defenseTargetId = null
        rt.virtualHealth = DEFENSE_MAX_HEALTH
        crystal.isInvulnerable = rt.mode != Mode.DEFENSE
        val pitch = if (rt.mode == Mode.ATTACK) 0.7f else 1.3f
        crystal.world.playSound(crystal.location, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.9f, pitch)
    }

    private fun processAttackMode(context: EntityMobRuntimeContext, crystal: EnderCrystal, rt: Runtime) {
        crystal.isInvulnerable = true
        val target = resolveNearestTarget(crystal) ?: return

        if (rt.burstRemaining <= 0 && rt.attackCooldownTicks <= 0L) {
            rt.burstRemaining = BURST_SHOT_COUNT
            rt.burstIntervalTicks = 0L
            rt.attackCooldownTicks = ATTACK_COOLDOWN_TICKS
        }

        if (rt.burstRemaining > 0 && rt.burstIntervalTicks <= 0L) {
            launchNoiseProjectile(context, crystal, rt, target)
            rt.burstRemaining -= 1
            rt.burstIntervalTicks = BURST_SHOT_INTERVAL_TICKS
        }
    }

    private fun processDefenseMode(context: EntityMobRuntimeContext, crystal: EnderCrystal, rt: Runtime) {
        crystal.isInvulnerable = false
        if (rt.defenseFreezeTicks > 0L) {
            rt.defenseFreezeTicks = (rt.defenseFreezeTicks - context.tickDelta).coerceAtLeast(0L)
        }

        if (rt.defensePreCastTicks > 0L) {
            renderDefenseChargeEffect(crystal)
            rt.defensePreCastTicks = (rt.defensePreCastTicks - context.tickDelta).coerceAtLeast(0L)
            if (rt.defensePreCastTicks <= 0L) {
                fireDefenseLightning(context, crystal, rt)
            }
            return
        }

        val nearbyPlayers = crystal.getNearbyEntities(DEFENSE_TRIGGER_RADIUS, DEFENSE_TRIGGER_RADIUS, DEFENSE_TRIGGER_RADIUS)
            .asSequence()
            .filterIsInstance<Player>()
            .filter { isValidArenaTarget(crystal, it) }
            .toList()

        if (nearbyPlayers.isEmpty()) return
        if (rt.defenseDischargeCooldownTicks > 0L) return

        rt.defenseTargetId = nearbyPlayers.minByOrNull { it.location.distanceSquared(crystal.location) }?.uniqueId
        rt.defensePreCastTicks = DEFENSE_PRE_CAST_TICKS
        rt.defenseFreezeTicks = DEFENSE_PRE_CAST_TICKS + DEFENSE_POST_CAST_FREEZE_TICKS
    }

    private fun fireDefenseLightning(context: EntityMobRuntimeContext, crystal: EnderCrystal, rt: Runtime) {
        val target = rt.defenseTargetId?.let { Bukkit.getPlayer(it) }
            ?.takeIf { isValidArenaTarget(crystal, it) && it.location.distanceSquared(crystal.location) <= DEFENSE_TRIGGER_RADIUS * DEFENSE_TRIGGER_RADIUS }
            ?: return

        val origin = crystal.location.clone().add(0.0, 0.5, 0.0)
        val segments = mutableListOf<BoltSegment>()
        repeat(DEFENSE_BOLT_COUNT) {
            val yaw = Random.nextDouble(0.0, PI * 2.0)
            val pitch = Random.nextDouble(-0.3, 0.3)
            val direction = Vector(
                cos(yaw) * cos(pitch),
                sin(pitch),
                sin(yaw) * cos(pitch)
            ).normalize()
            generateBranch(origin.toVector().clone(), direction, DEFENSE_MAX_BOLT_LENGTH, 0, 0, segments)
        }

        crystal.world.playSound(crystal.location, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 2.0f)
        crystal.world.spawnParticle(
            Particle.DUST_COLOR_TRANSITION,
            origin,
            1,
            0.2,
            0.2,
            0.2,
            0.0,
            Particle.DustTransition(
                Color.fromRGB(110, 210, 255),
                Color.fromRGB(240, 250, 255),
                1.3f
            )
        )

        applyDefenseLightningDamage(context.plugin, crystal, context.definition.attack, segments)
        startDefenseRenderTask(context.plugin, crystal, segments)
        rt.defenseTargetId = null
        rt.defenseDischargeCooldownTicks = (DEFENSE_DISCHARGE_COOLDOWN_TICKS * context.loadSnapshot.abilityCooldownMultiplier)
            .roundToLong().coerceAtLeast(DEFENSE_DISCHARGE_COOLDOWN_TICKS)
    }

    private fun processTeleport(context: EntityMobRuntimeContext, crystal: EnderCrystal, rt: Runtime) {
        val toBarrier = resolveNearbyUnactivatedBarrierPoint(crystal)
        if (toBarrier != null) {
            performTeleport(crystal, toBarrier)
            return
        }

        val mobTarget = resolveNearbyMobTeleportTarget(crystal) ?: return
        performTeleport(crystal, mobTarget)
        emitTeleportPulse(context, crystal)
    }

    private fun resolveNearbyUnactivatedBarrierPoint(crystal: EnderCrystal): Location? {
        val manager = CCContent.instance.getArenaManagerOrNull() ?: return null
        val points = manager.getUnactivatedBarrierPointLocations(crystal.world.name)
        return points
            .asSequence()
            .filter { it.world?.uid == crystal.world.uid }
            .filter { it.distanceSquared(crystal.location) <= BARRIER_POINT_SEARCH_RADIUS_SQUARED }
            .minByOrNull { it.distanceSquared(crystal.location) }
            ?.clone()
            ?.add(0.0, 0.4, 0.0)
    }

    private fun resolveNearbyMobTeleportTarget(crystal: EnderCrystal): Location? {
        return crystal.getNearbyEntities(TELEPORT_TARGET_SEARCH_RADIUS, TELEPORT_TARGET_SEARCH_RADIUS, TELEPORT_TARGET_SEARCH_RADIUS)
            .asSequence()
            .filterIsInstance<Mob>()
            .filter { it.isValid && !it.isDead && it.uniqueId != crystal.uniqueId }
            .minByOrNull { it.location.distanceSquared(crystal.location) }
            ?.location
            ?.clone()
            ?.add(0.0, 0.6, 0.0)
    }

    private fun emitTeleportPulse(context: EntityMobRuntimeContext, crystal: EnderCrystal) {
        val center = crystal.location.clone().add(0.0, 0.5, 0.0)
        crystal.world.spawnParticle(Particle.TRIAL_SPAWNER_DETECTION_OMINOUS, center, 18, 0.8, 0.8, 0.8, 0.0)
        crystal.world.playSound(center, Sound.BLOCK_END_PORTAL_SPAWN, 0.8f, 1.1f)

        crystal.getNearbyEntities(TELEPORT_PULSE_RADIUS, TELEPORT_PULSE_RADIUS, TELEPORT_PULSE_RADIUS)
            .forEach { entity ->
                when (entity) {
                    is Mob -> {
                        if (!entity.isValid || entity.isDead) return@forEach
                        val delta = entity.location.toVector().subtract(center.toVector()).setY(0.0)
                        val outward = if (delta.lengthSquared() <= 1.0e-6) {
                            Vector(Random.nextDouble(-0.2, 0.2), 0.0, Random.nextDouble(-0.2, 0.2))
                        } else {
                            delta.normalize()
                        }
                        entity.velocity = entity.velocity.add(outward.multiply(TELEPORT_PULSE_KNOCKBACK).setY(TELEPORT_PULSE_VERTICAL))
                        entity.addPotionEffect(PotionEffect(PotionEffectType.SPEED, TELEPORT_BUFF_TICKS, 1, false, true, true))
                        entity.addPotionEffect(PotionEffect(PotionEffectType.STRENGTH, TELEPORT_BUFF_TICKS, 0, false, true, true))
                    }

                    is Player -> {
                        if (!isValidArenaTarget(crystal, entity)) return@forEach
                        val damage = (context.definition.attack * 0.9).coerceAtLeast(0.0)
                        if (damage > 0.0) {
                            entity.damage(damage, crystal)
                        }
                    }
                }
            }
    }

    private fun performTeleport(crystal: EnderCrystal, destination: Location) {
        val from = crystal.location.clone().add(0.0, 0.5, 0.0)
        val to = destination.clone().add(0.0, 0.5, 0.0)
        val world = crystal.world
        world.spawnParticle(Particle.PORTAL, from, 18, 0.4, 0.4, 0.4, 0.05)
        crystal.teleport(destination)
        world.spawnParticle(Particle.PORTAL, to, 24, 0.5, 0.5, 0.5, 0.06)
    }

    private fun launchNoiseProjectile(context: EntityMobRuntimeContext, crystal: EnderCrystal, rt: Runtime, target: Player) {
        val origin = crystal.location.clone().add(0.0, 0.6, 0.0)
        val direction = target.eyeLocation.toVector().subtract(origin.toVector())
            .takeIf { it.lengthSquared() > 1.0e-6 }
            ?.normalize()
            ?: Vector(0.0, 0.0, 1.0)

        rt.projectiles += NoiseProjectile(
            position = origin,
            velocity = direction.multiply(NOISE_PROJECTILE_SPEED_PER_TICK),
            targetId = target.uniqueId,
            remainingTicks = NOISE_PROJECTILE_MAX_TICKS
        )
        crystal.world.playSound(origin, Sound.BLOCK_AMETHYST_CLUSTER_HIT, 0.7f, 1.5f)
        crystal.world.spawnParticle(Particle.ENCHANTED_HIT, origin, 8, 0.08, 0.08, 0.08, 0.0)
    }

    private fun tickProjectiles(context: EntityMobRuntimeContext, crystal: EnderCrystal, rt: Runtime) {
        val world = crystal.world
        val iterator = rt.projectiles.iterator()
        while (iterator.hasNext()) {
            val projectile = iterator.next()
            if (projectile.remainingTicks <= 0) {
                iterator.remove()
                continue
            }

            val target = projectile.targetId?.let { Bukkit.getPlayer(it) }
                ?.takeIf { it.isValid && !it.isDead && it.world.uid == world.uid }
            if (target != null) {
                val desired = target.eyeLocation.toVector().subtract(projectile.position.toVector())
                if (desired.lengthSquared() > 1.0e-6) {
                    val steer = desired.normalize().multiply(NOISE_PROJECTILE_SPEED_PER_TICK)
                    projectile.velocity = projectile.velocity.multiply(1.0 - NOISE_HOMING_STEER).add(steer.multiply(NOISE_HOMING_STEER))
                }
            }

            val jitter = Vector(
                Random.nextDouble(-NOISE_JITTER, NOISE_JITTER),
                Random.nextDouble(-NOISE_JITTER, NOISE_JITTER),
                Random.nextDouble(-NOISE_JITTER, NOISE_JITTER)
            )
            projectile.velocity = projectile.velocity.add(jitter)
            if (projectile.velocity.lengthSquared() > 1.0e-6) {
                projectile.velocity = projectile.velocity.normalize().multiply(NOISE_PROJECTILE_SPEED_PER_TICK)
            }

            val from = projectile.position.clone()
            val velocity = projectile.velocity.clone()
            val distance = velocity.length()
            if (distance <= 1.0e-6) {
                iterator.remove()
                continue
            }
            val direction = velocity.clone().normalize()
            val blockHit = world.rayTraceBlocks(from, direction, distance, org.bukkit.FluidCollisionMode.NEVER, true)
            if (blockHit != null) {
                world.spawnParticle(Particle.ENCHANTED_HIT, from, 8, 0.1, 0.1, 0.1, 0.0)
                iterator.remove()
                continue
            }

            val hitPlayer = world.rayTraceEntities(from, direction, distance, NOISE_HIT_RADIUS) {
                it is Player && isValidArenaTarget(crystal, it)
            }?.hitEntity as? Player

            if (hitPlayer != null) {
                val damage = (context.definition.attack * 0.65).coerceAtLeast(0.0)
                if (damage > 0.0) {
                    hitPlayer.damage(damage, crystal)
                }
                val push = hitPlayer.location.toVector().subtract(from.toVector()).setY(0.0)
                if (push.lengthSquared() > 1.0e-6) {
                    hitPlayer.velocity = hitPlayer.velocity.add(push.normalize().multiply(0.35).setY(0.2))
                }
                world.spawnParticle(Particle.ENCHANTED_HIT, hitPlayer.location.clone().add(0.0, 1.0, 0.0), 14, 0.12, 0.12, 0.12, 0.0)
                world.playSound(hitPlayer.location, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 0.7f, 1.3f)
                iterator.remove()
                continue
            }

            projectile.position = from.add(velocity)
            projectile.remainingTicks -= 1

            world.spawnParticle(Particle.ENCHANTED_HIT, projectile.position, 2, 0.03, 0.03, 0.03, 0.0)
            world.spawnParticle(
                Particle.DUST,
                projectile.position,
                1,
                0.0,
                0.0,
                0.0,
                0.0,
                Particle.DustOptions(Color.fromRGB(86, 24, 128), 1.2f)
            )
        }
    }

    private fun emitSatellites(crystal: EnderCrystal, rt: Runtime) {
        val center = crystal.location.clone().add(0.0, 0.6, 0.0)
        val color = if (rt.mode == Mode.ATTACK) {
            Color.fromRGB(62, 18, 98)
        } else {
            Color.fromRGB(86, 120, 190)
        }
        val baseAngle = rt.satelliteTicks.toDouble() * SATELLITE_ANGULAR_SPEED_PER_TICK

        repeat(5) { index ->
            val angle = baseAngle + (Math.PI * 2.0) * index.toDouble() / 5.0
            val x = cos(angle) * SATELLITE_RADIUS
            val z = sin(angle) * SATELLITE_RADIUS
            val y = sin(angle * 1.6) * SATELLITE_HEIGHT_WAVE
            val point = center.clone().add(x, y, z)
            crystal.world.spawnParticle(
                Particle.DUST,
                point,
                2,
                0.02,
                0.02,
                0.02,
                0.0,
                Particle.DustOptions(color, 1.15f)
            )
            crystal.world.spawnParticle(Particle.ENCHANTED_HIT, point, 1, 0.01, 0.01, 0.01, 0.0)
        }
    }

    private fun resolveNearestTarget(crystal: EnderCrystal): Player? {
        return crystal.world.players
            .asSequence()
            .filter { isValidArenaTarget(crystal, it) }
            .filter { it.location.distanceSquared(crystal.location) <= TARGET_SEARCH_RADIUS_SQUARED }
            .minByOrNull { it.location.distanceSquared(crystal.location) }
    }

    private fun playDamageNullifiedFeedback(location: Location) {
        val world = location.world ?: return
        val center = location.clone().add(0.0, 0.7, 0.0)
        world.spawnParticle(Particle.ENCHANTED_HIT, center, 8, 0.18, 0.18, 0.18, 0.0)
        world.spawnParticle(Particle.ELECTRIC_SPARK, center, 6, 0.12, 0.12, 0.12, 0.0)
        world.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 1.65f)
    }

    private fun renderDefenseChargeEffect(crystal: EnderCrystal) {
        val center = crystal.location.clone().add(0.0, 0.5, 0.0)
        crystal.world.spawnParticle(
            Particle.DUST_COLOR_TRANSITION,
            center,
            1,
            0.3,
            0.3,
            0.3,
            0.0,
            Particle.DustTransition(
                Color.fromRGB(80, 160, 255),
                Color.fromRGB(200, 230, 255),
                1.3f
            )
        )
    }

    private fun generateBranch(
        start: Vector,
        direction: Vector,
        remainingLength: Double,
        depth: Int,
        delayTicks: Int,
        result: MutableList<BoltSegment>
    ) {
        var current = start.clone()
        var currentDir = direction.clone()
        var remaining = remainingLength

        while (remaining > 0.0) {
            currentDir = Vector(
                currentDir.x + Random.nextDouble(-DEFENSE_JITTER_AMOUNT, DEFENSE_JITTER_AMOUNT),
                currentDir.y + Random.nextDouble(-DEFENSE_JITTER_AMOUNT, DEFENSE_JITTER_AMOUNT),
                currentDir.z + Random.nextDouble(-DEFENSE_JITTER_AMOUNT, DEFENSE_JITTER_AMOUNT)
            ).let { v ->
                val len = v.length()
                if (len < 0.001) currentDir.clone() else v.multiply(1.0 / len)
            }

            val step = DEFENSE_SEGMENT_LENGTH.coerceAtMost(remaining)
            val next = current.clone().add(currentDir.clone().multiply(step))
            result.add(BoltSegment(current.clone(), next.clone(), depth, delayTicks))

            if (depth < DEFENSE_MAX_BRANCH_DEPTH && Random.nextDouble() < DEFENSE_BRANCH_PROBABILITY) {
                val branchDir = Vector(
                    currentDir.x + Random.nextDouble(-DEFENSE_JITTER_AMOUNT, DEFENSE_JITTER_AMOUNT),
                    currentDir.y + Random.nextDouble(-DEFENSE_JITTER_AMOUNT, DEFENSE_JITTER_AMOUNT),
                    currentDir.z + Random.nextDouble(-DEFENSE_JITTER_AMOUNT, DEFENSE_JITTER_AMOUNT)
                ).let { v ->
                    val len = v.length()
                    if (len < 0.001) currentDir.clone() else v.multiply(1.0 / len)
                }
                generateBranch(next.clone(), branchDir, remaining * DEFENSE_BRANCH_LENGTH_DECAY, depth + 1, delayTicks + 1, result)
            }

            current = next
            remaining -= step
        }
    }

    private fun applyDefenseLightningDamage(plugin: JavaPlugin, crystal: EnderCrystal, attack: Double, segments: List<BoltSegment>) {
        val origin = crystal.location
        val searchRadius = DEFENSE_MAX_BOLT_LENGTH + DEFENSE_DAMAGE_RADIUS
        val candidates = crystal.world.getNearbyPlayers(origin, searchRadius, searchRadius, searchRadius)
        val damaged = mutableSetOf<UUID>()
        val finalDamage = (attack * DEFENSE_DAMAGE_MULTIPLIER).coerceAtLeast(0.0)
        if (finalDamage <= 0.0) return

        for (player in candidates) {
            if (!isValidArenaTarget(crystal, player) || !damaged.add(player.uniqueId)) continue
            val playerVec = player.location.toVector()
            for (segment in segments) {
                if (pointToSegmentDistance(playerVec, segment.start, segment.end) > DEFENSE_DAMAGE_RADIUS) continue
                MobService.getInstance(plugin)?.issueDirectDamagePermit(crystal.uniqueId, player.uniqueId)
                player.damage(finalDamage, crystal)
                cancelKnockback(plugin, player)
                break
            }
        }
    }

    private fun cancelKnockback(plugin: JavaPlugin, player: Player) {
        player.velocity = Vector(0.0, 0.0, 0.0)
        Bukkit.getScheduler().runTask(plugin, Runnable {
            if (player.isValid && !player.isDead) {
                player.velocity = Vector(0.0, 0.0, 0.0)
            }
        })
    }

    private fun startDefenseRenderTask(plugin: JavaPlugin, crystal: EnderCrystal, segments: List<BoltSegment>) {
        val world = crystal.world
        val byDelay = segments.groupBy { it.delayTicks }.toSortedMap()
        byDelay.forEach { (delayTicks, delayedSegments) ->
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                if (!crystal.isValid || crystal.isDead) return@Runnable
                delayedSegments.forEach { renderDefenseSegment(world, it) }
            }, delayTicks.toLong().coerceAtLeast(0L))
        }
    }

    private fun renderDefenseSegment(world: org.bukkit.World, segment: BoltSegment) {
        val segVec = segment.end.clone().subtract(segment.start)
        val segLen = segVec.length()
        if (segLen < 0.01) return

        val stepVec = segVec.normalize().multiply(DEFENSE_RENDER_STEP)
        val steps = (segLen / DEFENSE_RENDER_STEP).toInt().coerceAtLeast(1)
        var cursor = segment.start.clone()
        repeat(steps) {
            val loc = Location(world, cursor.x, cursor.y, cursor.z)
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 1, 0.05, 0.05, 0.05, 0.02)
            world.spawnParticle(
                Particle.DUST_COLOR_TRANSITION,
                loc,
                1,
                0.06,
                0.06,
                0.06,
                0.0,
                if (segment.depth == 0) {
                    Particle.DustTransition(Color.fromRGB(100, 200, 255), Color.fromRGB(220, 240, 255), 1.3f)
                } else {
                    Particle.DustTransition(Color.fromRGB(140, 210, 255), Color.fromRGB(230, 245, 255), 1.3f)
                }
            )
            cursor.add(stepVec)
        }
    }

    private fun pointToSegmentDistance(point: Vector, a: Vector, b: Vector): Double {
        val ab = b.clone().subtract(a)
        val ap = point.clone().subtract(a)
        val abLenSq = ab.lengthSquared()
        if (abLenSq < 0.0001) return ap.length()
        val t = (ap.dot(ab) / abLenSq).coerceIn(0.0, 1.0)
        val closest = a.clone().add(ab.multiply(t))
        return point.distance(closest)
    }

    private fun isValidArenaTarget(crystal: EnderCrystal, player: Player): Boolean {
        if (!player.isValid || player.isDead) return false
        if (player.gameMode == org.bukkit.GameMode.SPECTATOR) return false
        return player.world.uid == crystal.world.uid
    }

    private fun resolvePlayerAttacker(event: org.bukkit.event.entity.EntityDamageByEntityEvent): Player? {
        val direct = event.damager as? Player
        if (direct != null) return direct
        val projectile = event.damager as? Projectile ?: return null
        return projectile.shooter as? Player
    }

    private fun killCrystal(plugin: org.bukkit.plugin.java.JavaPlugin, crystal: EnderCrystal) {
        val world = crystal.world
        val center = crystal.location.clone().add(0.0, 0.5, 0.0)
        world.spawnParticle(Particle.EXPLOSION, center, 1, 0.0, 0.0, 0.0, 0.0)
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.85f)

        CCContent.instance.getArenaManagerOrNull()?.handleEntityDeath(crystal)
        MobService.getInstance(plugin)?.handleEntityDeath(crystal)
        crystal.remove()
    }

    private companion object {
        const val MODE_SWITCH_TICKS = 200L
        const val ATTACK_COOLDOWN_TICKS = 60L
        const val BURST_SHOT_COUNT = 3
        const val BURST_SHOT_INTERVAL_TICKS = 8L

        const val TARGET_SEARCH_RADIUS_SQUARED = 30.0 * 30.0
        const val NOISE_PROJECTILE_SPEED_PER_TICK = 0.55
        const val NOISE_PROJECTILE_MAX_TICKS = 55
        const val NOISE_HIT_RADIUS = 0.55
        const val NOISE_HOMING_STEER = 0.16
        const val NOISE_JITTER = 0.025

        const val DEFENSE_MAX_HEALTH = 100.0
        const val DEFENSE_TRIGGER_RADIUS = 4.0
        const val DEFENSE_DISCHARGE_COOLDOWN_TICKS = 20L
        const val DEFENSE_PRE_CAST_TICKS = 6L
        const val DEFENSE_POST_CAST_FREEZE_TICKS = 6L
        const val DEFENSE_BOLT_COUNT = 4
        const val DEFENSE_MAX_BOLT_LENGTH = 8.0
        const val DEFENSE_SEGMENT_LENGTH = 0.6
        const val DEFENSE_JITTER_AMOUNT = 0.4
        const val DEFENSE_BRANCH_PROBABILITY = 0.35
        const val DEFENSE_BRANCH_LENGTH_DECAY = 0.65
        const val DEFENSE_MAX_BRANCH_DEPTH = 3
        const val DEFENSE_DAMAGE_RADIUS = 1.2
        const val DEFENSE_DAMAGE_MULTIPLIER = 2.0
        const val DEFENSE_RENDER_STEP = 0.6

        const val TELEPORT_COOLDOWN_TICKS = 140L
        const val BARRIER_POINT_SEARCH_RADIUS_SQUARED = 24.0 * 24.0
        const val TELEPORT_TARGET_SEARCH_RADIUS = 22.0
        const val TELEPORT_PULSE_RADIUS = 5.2
        const val TELEPORT_PULSE_KNOCKBACK = 0.95
        const val TELEPORT_PULSE_VERTICAL = 0.28
        const val TELEPORT_BUFF_TICKS = 120

        const val SATELLITE_RADIUS = 1.15
        const val SATELLITE_HEIGHT_WAVE = 0.18
        const val SATELLITE_ANGULAR_SPEED_PER_TICK = 0.12
    }
}
