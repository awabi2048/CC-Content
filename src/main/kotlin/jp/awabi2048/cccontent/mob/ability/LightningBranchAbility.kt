package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobRuntimeContext
import jp.awabi2048.cccontent.mob.MobDamagedContext
import jp.awabi2048.cccontent.mob.MobLoadSnapshot
import jp.awabi2048.cccontent.mob.MobService
import jp.awabi2048.cccontent.mob.MobSpawnContext
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.Material
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.Vector
import org.bukkit.scheduler.BukkitRunnable
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.random.Random

class LightningBranchAbility(
    override val id: String,
    private val boltCount: Int = 4,
    private val damagedBoltCount: Int = 8,
    private val maxBoltLength: Double = 8.0,
    private val segmentLength: Double = 0.6,
    private val jitterAmount: Double = 0.4,
    private val branchProbability: Double = 0.25,
    private val branchLengthDecay: Double = 0.55,
    private val maxBranchDepth: Int = 3,
    private val damageRadius: Double = 1.2,
    private val damageMultiplier: Double = 1.0,
    private val cooldownTicks: Long = 120L,
    private val minRange: Double = 2.0,
    private val maxRange: Double = 14.0,
    private val preCastTicks: Long = 15L,
    private val postCastFreezeTicks: Long = 10L,
    private val triggerOnDamaged: Boolean = true,
    private val requireLineOfSight: Boolean = true,
    private val allowNearestPlayerFallback: Boolean = false,
) : MobAbility {

    override fun tickIntervalTicks(): Long = 1L

    data class Runtime(
        var cooldownRemaining: Long = 0L,
        var freezeUntilTick: Long = 0L,
        var preCastRemainingTicks: Long = 0L,
        var isPreCasting: Boolean = false,
        var targetId: UUID? = null,
        var searchPhaseOffsetSteps: Int = 0,
        var lastDamageTriggerTick: Long = Long.MIN_VALUE
    ) : MobAbilityRuntime

    override fun createRuntime(context: MobSpawnContext): MobAbilityRuntime {
        return Runtime(searchPhaseOffsetSteps = Random.nextInt(0, SEARCH_PHASE_VARIANTS))
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val rt = runtime as? Runtime ?: return
        val entity = context.entity
        val nowTick = Bukkit.getCurrentTick().toLong()

        if (nowTick < rt.freezeUntilTick) {
            entity.velocity = entity.velocity.clone().setX(0.0).setZ(0.0)
        }

        if (rt.cooldownRemaining > 0L) {
            rt.cooldownRemaining = (rt.cooldownRemaining - context.tickDelta).coerceAtLeast(0L)
        }

        if (rt.isPreCasting) {
            renderChargeEffect(entity)
            rt.preCastRemainingTicks = (rt.preCastRemainingTicks - context.tickDelta).coerceAtLeast(0L)
            if (rt.preCastRemainingTicks <= 0L) {
                fire(
                    plugin = context.plugin,
                    entity = entity,
                    attack = context.definition.attack,
                    loadSnapshot = context.loadSnapshot,
                    target = rt.targetId?.let { Bukkit.getEntity(it) as? LivingEntity } ?: MobAbilityUtils.resolveTarget(entity),
                    boltCount = boltCount,
                    applyCooldown = true,
                    runtime = rt
                )
                rt.targetId = null
            }
            return
        }

        if (!context.isCombatActive()) {
            rt.isPreCasting = false
            rt.targetId = null
            return
        }

        if (rt.cooldownRemaining > 0L) return

        if (!MobAbilityUtils.shouldProcessSearchTick(
                context.activeMob.tickCount,
                context.loadSnapshot.searchIntervalMultiplier,
                rt.searchPhaseOffsetSteps,
                baseStepTicks = 1L
        )) return

        if (Random.nextDouble() < context.loadSnapshot.abilityExecutionSkipChance) return

        val target = resolveTarget(entity) ?: return
        val distance = entity.location.distance(target.location)
        if (distance < minRange || distance > maxRange) return
        if (requireLineOfSight && !entity.hasLineOfSight(target)) return

        rt.isPreCasting = true
        rt.targetId = target.uniqueId
        rt.preCastRemainingTicks = preCastTicks.coerceAtLeast(1L)
        rt.freezeUntilTick = maxOf(rt.freezeUntilTick, nowTick + preCastTicks + postCastFreezeTicks)
    }

    override fun onDamaged(context: MobDamagedContext, runtime: MobAbilityRuntime?) {
        if (!triggerOnDamaged) return
        val rt = runtime as? Runtime ?: return
        if (!context.isCombatActive()) return
        if (context.event.isCancelled) return
        if (context.event.finalDamage <= 0.0) return

        val nowTick = Bukkit.getCurrentTick().toLong()
        if (rt.lastDamageTriggerTick == nowTick) return
        rt.lastDamageTriggerTick = nowTick

        fire(
            plugin = context.plugin,
            entity = context.entity,
            attack = context.definition.attack,
            loadSnapshot = context.loadSnapshot,
            target = context.attacker,
            boltCount = damagedBoltCount,
            applyCooldown = false,
            runtime = rt
        )
    }

    private fun fire(
        plugin: JavaPlugin,
        entity: LivingEntity,
        attack: Double,
        loadSnapshot: MobLoadSnapshot,
        target: LivingEntity?,
        boltCount: Int,
        applyCooldown: Boolean,
        runtime: Runtime
    ) {
        runtime.isPreCasting = false

        if (target != null) {
            MobAbilityUtils.faceTowards(entity, target)
        }

        val origin = entity.location.clone().add(0.0, entity.height * 0.5, 0.0)
        val segments = mutableListOf<BoltSegment>()

        repeat(boltCount.coerceAtLeast(1)) {
            val yaw = Random.nextDouble(0.0, PI * 2.0)
            val pitch = Random.nextDouble(-0.3, 0.3)
            val direction = Vector(
                cos(yaw) * cos(pitch),
                sin(pitch),
                sin(yaw) * cos(pitch)
            ).normalize()
            generateBranch(origin.toVector().clone(), direction, maxBoltLength, 0, 0, segments)
        }

        entity.world.playSound(entity.location, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 2.0f)

        entity.world.spawnParticle(
            Particle.DUST_COLOR_TRANSITION,
            origin, 1, 0.2, 0.2, 0.2, 0.0,
            Particle.DustTransition(
                Color.fromRGB(110, 210, 255),
                Color.fromRGB(240, 250, 255),
                1.3f
            )
        )

        applyDamage(plugin, entity, segments, attack)
        startRenderTask(plugin, entity, segments)

        if (applyCooldown) {
            val baseCooldown = cooldownTicks.coerceAtLeast(1L)
            runtime.cooldownRemaining = (baseCooldown * loadSnapshot.abilityCooldownMultiplier)
                .roundToLong().coerceAtLeast(baseCooldown)
        }
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
                currentDir.x + Random.nextDouble(-jitterAmount, jitterAmount),
                currentDir.y + Random.nextDouble(-jitterAmount, jitterAmount),
                currentDir.z + Random.nextDouble(-jitterAmount, jitterAmount)
            ).let { v ->
                val len = v.length()
                if (len < 0.001) currentDir.clone() else v.multiply(1.0 / len)
            }

            val step = segmentLength.coerceAtMost(remaining)
            val next = current.clone().add(currentDir.clone().multiply(step))

            result.add(BoltSegment(current.clone(), next.clone(), depth, delayTicks))

            if (depth < maxBranchDepth && Random.nextDouble() < branchProbability) {
                val branchDir = Vector(
                    currentDir.x + Random.nextDouble(-jitterAmount, jitterAmount),
                    currentDir.y + Random.nextDouble(-jitterAmount, jitterAmount),
                    currentDir.z + Random.nextDouble(-jitterAmount, jitterAmount)
                ).let { v ->
                    val len = v.length()
                    if (len < 0.001) currentDir.clone() else v.multiply(1.0 / len)
                }
                generateBranch(
                    next.clone(),
                    branchDir,
                    remaining * branchLengthDecay,
                    depth + 1,
                    delayTicks + 1,
                    result
                )
            }

            current = next
            remaining -= step
        }
    }

    private fun applyDamage(
        plugin: JavaPlugin,
        entity: LivingEntity,
        segments: List<BoltSegment>,
        attack: Double
    ) {
        val world = entity.world
        val origin = entity.location
        val searchRadius = maxBoltLength + damageRadius
        val candidates = world.getNearbyPlayers(origin, searchRadius, searchRadius, searchRadius)
        val damaged = mutableSetOf<UUID>()
        val finalDamage = (attack * damageMultiplier).coerceAtLeast(0.0)
        if (finalDamage <= 0.0) return

        for (player in candidates) {
            if (player.uniqueId in damaged) continue
            val playerVec = player.location.toVector()
            val armorMultiplier = resolveArmorMultiplier(player)
            val modifiedDamage = (finalDamage * armorMultiplier).coerceAtLeast(0.0)
            if (modifiedDamage <= 0.0) continue

            for (segment in segments) {
                if (pointToSegmentDistance(playerVec, segment.start, segment.end) <= damageRadius) {
                    damaged.add(player.uniqueId)
                    MobService.getInstance(plugin)?.issueDirectDamagePermit(entity.uniqueId, player.uniqueId)
                    player.damage(modifiedDamage, entity)
                    cancelKnockback(plugin, player)
                    break
                }
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

    private fun resolveArmorMultiplier(player: Player): Double {
        val armor = player.inventory.armorContents
        if (armor.isEmpty()) {
            return 1.0
        }
        var total = 0.0
        var count = 0
        for (piece in armor) {
            total += resolveArmorPieceMultiplier(piece?.type)
            count += 1
        }
        if (count <= 0) {
            return 1.0
        }
        return total / count.toDouble()
    }

    private fun resolveArmorPieceMultiplier(type: Material?): Double {
        return when (type) {
            Material.LEATHER_HELMET,
            Material.LEATHER_CHESTPLATE,
            Material.LEATHER_LEGGINGS,
            Material.LEATHER_BOOTS -> 0.25

            Material.IRON_HELMET,
            Material.IRON_CHESTPLATE,
            Material.IRON_LEGGINGS,
            Material.IRON_BOOTS,
            Material.GOLDEN_HELMET,
            Material.GOLDEN_CHESTPLATE,
            Material.GOLDEN_LEGGINGS,
            Material.GOLDEN_BOOTS -> 1.5

            Material.CHAINMAIL_HELMET,
            Material.CHAINMAIL_CHESTPLATE,
            Material.CHAINMAIL_LEGGINGS,
            Material.CHAINMAIL_BOOTS,
            Material.NETHERITE_HELMET,
            Material.NETHERITE_CHESTPLATE,
            Material.NETHERITE_LEGGINGS,
            Material.NETHERITE_BOOTS -> 1.0

            else -> 1.0
        }
    }

    private fun startRenderTask(plugin: JavaPlugin, entity: LivingEntity, segments: List<BoltSegment>) {
        val world = entity.world
        val byDelay = segments.groupBy { it.delayTicks }.toSortedMap()
        byDelay.forEach { (delayTicks, delayedSegments) ->
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                if (!entity.isValid || entity.isDead) {
                    return@Runnable
                }
                delayedSegments.forEach { segment -> renderSegment(world, segment) }
            }, delayTicks.toLong().coerceAtLeast(0L))
        }
    }

    private fun resolveTarget(entity: LivingEntity): LivingEntity? {
        val fixedTarget = MobAbilityUtils.resolveTarget(entity)
        if (fixedTarget != null) {
            return fixedTarget
        }
        if (!allowNearestPlayerFallback) {
            return null
        }
        val horizontalRange = maxRange.coerceAtLeast(1.0)
        val verticalRange = (maxRange * 0.75).coerceAtLeast(1.0)
        return entity.getNearbyEntities(horizontalRange, verticalRange, horizontalRange)
            .asSequence()
            .filterIsInstance<Player>()
            .filter {
                it.isValid &&
                    !it.isDead &&
                    it.world.uid == entity.world.uid &&
                    it.gameMode != org.bukkit.GameMode.SPECTATOR
            }
            .minByOrNull { it.location.distanceSquared(entity.location) }
    }

    private fun renderSegment(world: org.bukkit.World, segment: BoltSegment) {
        val segVec = segment.end.clone().subtract(segment.start)
        val segLen = segVec.length()
        if (segLen < 0.01) return

        val stepVec = segVec.normalize().multiply(RENDER_STEP)
        val steps = (segLen / RENDER_STEP).toInt().coerceAtLeast(1)
        var cursor = segment.start.clone()

        repeat(steps) {
            val loc = Location(world, cursor.x, cursor.y, cursor.z)
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 1, 0.05, 0.05, 0.05, 0.02)
            world.spawnParticle(
                Particle.DUST_COLOR_TRANSITION, loc, 1,
                0.06, 0.06, 0.06, 0.0,
                if (segment.depth == 0) {
                    Particle.DustTransition(
                        Color.fromRGB(100, 200, 255),
                        Color.fromRGB(220, 240, 255),
                        1.3f
                    )
                } else {
                    Particle.DustTransition(
                        Color.fromRGB(140, 210, 255),
                        Color.fromRGB(230, 245, 255),
                        1.3f
                    )
                }
            )
            cursor.add(stepVec)
        }
    }

    private fun renderChargeEffect(entity: LivingEntity) {
        val center = entity.location.clone().add(0.0, entity.height * 0.5, 0.0)
        entity.world.spawnParticle(
            Particle.DUST_COLOR_TRANSITION, center, 1, 0.3, 0.3, 0.3, 0.0,
            Particle.DustTransition(
                Color.fromRGB(80, 160, 255),
                Color.fromRGB(200, 230, 255),
                1.3f
            )
        )
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

    private data class BoltSegment(
        val start: Vector,
        val end: Vector,
        val depth: Int,
        val delayTicks: Int
    )

    companion object {
        private const val SEARCH_PHASE_VARIANTS = 16
        private const val RENDER_STEP = 0.6
    }
}
