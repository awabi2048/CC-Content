package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobDeathContext
import jp.awabi2048.cccontent.mob.MobRuntimeContext
import org.bukkit.Location
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Marker
import org.bukkit.entity.Shulker
import kotlin.math.sin
import kotlin.random.Random

class ShulkerCarrierArtilleryAbility(
    override val id: String,
    private val oscillationAmplitude: Double = 0.55,
    private val oscillationFrequency: Double = 0.12,
    private val teleportIntervalTicks: Long = 160L,
    private val teleportRadius: Double = 8.0,
    private val markerSearchRadius: Double = 32.0,
    private val prioritizeBarrierMarkerOnFinalWave: Boolean = false
) : MobAbility {

    data class Runtime(
        var carrierId: java.util.UUID? = null,
        var anchor: Location? = null,
        var elapsedTicks: Long = 0L,
        var teleportCooldownTicks: Long = 0L
    ) : MobAbilityRuntime

    override fun tickIntervalTicks(): Long = 1L

    override fun createRuntime(context: jp.awabi2048.cccontent.mob.MobSpawnContext): MobAbilityRuntime {
        return Runtime(teleportCooldownTicks = teleportIntervalTicks)
    }

    override fun onSpawn(context: jp.awabi2048.cccontent.mob.MobSpawnContext, runtime: MobAbilityRuntime?) {
        val shulker = context.entity as? Shulker ?: return
        val rt = runtime as? Runtime ?: return
        val world = shulker.world

        shulker.setAI(false)

        val carrier = world.spawn(shulker.location, ArmorStand::class.java)
        carrier.isInvisible = true
        carrier.isInvulnerable = true
        carrier.setGravity(false)
        carrier.isMarker = true
        carrier.setAI(false)
        carrier.isSilent = true
        carrier.isCollidable = false
        carrier.isPersistent = true
        carrier.addScoreboardTag("cc.mob.shulker_carrier")
        carrier.addPassenger(shulker)

        rt.carrierId = carrier.uniqueId
        rt.anchor = carrier.location.clone()
        rt.teleportCooldownTicks = teleportIntervalTicks

        shulker.peek = 1.0f
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val shulker = context.entity as? Shulker ?: return
        val rt = runtime as? Runtime ?: return
        val carrier = ensureCarrier(shulker, rt) ?: return

        if (rt.anchor == null) {
            rt.anchor = carrier.location.clone()
        }
        val anchor = rt.anchor ?: return

        rt.elapsedTicks += context.tickDelta
        rt.teleportCooldownTicks = (rt.teleportCooldownTicks - context.tickDelta).coerceAtLeast(0L)

        val nextY = anchor.y + sin(rt.elapsedTicks.toDouble() * oscillationFrequency) * oscillationAmplitude
        val lifted = anchor.clone().apply { y = nextY }
        carrier.teleport(lifted)
        if (!carrier.passengers.any { it.uniqueId == shulker.uniqueId }) {
            carrier.addPassenger(shulker)
        }

        if (!context.isCombatActive()) {
            return
        }

        if (rt.teleportCooldownTicks > 0L) {
            return
        }

        val destination = selectTeleportDestination(context, shulker, anchor)
            ?: EndThemeEffects.findNearbyTeleportLocation(anchor, teleportRadius, attempts = 20)
            ?: return

        rt.anchor = destination.clone()
        carrier.teleport(destination)
        rt.teleportCooldownTicks = teleportIntervalTicks
        shulker.world.playSound(destination, org.bukkit.Sound.ENTITY_SHULKER_TELEPORT, 0.9f, 1.35f)
    }

    private fun ensureCarrier(shulker: Shulker, runtime: Runtime): ArmorStand? {
        val existing = runtime.carrierId
            ?.let { org.bukkit.Bukkit.getEntity(it) as? ArmorStand }
            ?.takeIf { it.isValid && !it.isDead && it.world.uid == shulker.world.uid }
        if (existing != null) {
            return existing
        }

        val spawned = shulker.world.spawn(shulker.location, ArmorStand::class.java)
        spawned.isInvisible = true
        spawned.isInvulnerable = true
        spawned.setGravity(false)
        spawned.isMarker = true
        spawned.setAI(false)
        spawned.isSilent = true
        spawned.isCollidable = false
        spawned.isPersistent = true
        spawned.addScoreboardTag("cc.mob.shulker_carrier")
        spawned.addPassenger(shulker)
        runtime.carrierId = spawned.uniqueId
        runtime.anchor = spawned.location.clone()
        return spawned
    }

    override fun onDeath(context: MobDeathContext, runtime: MobAbilityRuntime?) {
        val rt = runtime as? Runtime ?: return
        val carrier = rt.carrierId?.let { org.bukkit.Bukkit.getEntity(it) as? ArmorStand } ?: return
        if (carrier.isValid) {
            carrier.remove()
        }
    }

    private fun selectTeleportDestination(context: MobRuntimeContext, shulker: Shulker, anchor: Location): Location? {
        if (!prioritizeBarrierMarkerOnFinalWave || !isFinalWave(context)) {
            return null
        }
        val markers = shulker.world.getNearbyEntities(anchor, markerSearchRadius, markerSearchRadius, markerSearchRadius)
            .asSequence()
            .filterIsInstance<Marker>()
            .filter { marker -> marker.scoreboardTags.contains("arena.marker.barrier_point") }
            .map { marker -> marker.location.clone().add(0.0, 0.45, 0.0) }
            .toList()
        if (markers.isEmpty()) {
            return null
        }
        return markers[Random.nextInt(markers.size)]
    }

    private fun isFinalWave(context: MobRuntimeContext): Boolean {
        val wave = context.activeMob.metadata["wave"]?.toIntOrNull() ?: return false
        val total = context.activeMob.metadata["total_waves"]?.toIntOrNull() ?: return false
        return wave >= total
    }
}
