package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobDeathContext
import jp.awabi2048.cccontent.mob.MobRuntimeContext
import jp.awabi2048.cccontent.mob.MobSpawnContext
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

class ArmorMagnetPullAbility(
    override val id: String,
    private val minDistance: Double = 6.0,
    private val maxDistance: Double = 15.0,
    private val pullPerArmorPiece: Double = 0.004,
    private val verticalBoostPerPiece: Double = 0.001
) : MobAbility {

    data class Runtime(
        var ownerId: UUID? = null,
        var task: BukkitTask? = null,
        var tickCounter: Int = 0
    ) : MobAbilityRuntime

    override fun createRuntime(context: MobSpawnContext): MobAbilityRuntime {
        return Runtime(ownerId = context.entity.uniqueId)
    }

    override fun onSpawn(context: MobSpawnContext, runtime: MobAbilityRuntime?) {
        val abilityRuntime = runtime as? Runtime ?: return
        abilityRuntime.ownerId = context.entity.uniqueId
        ensureTask(context.plugin, abilityRuntime)
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val abilityRuntime = runtime as? Runtime ?: return
        abilityRuntime.ownerId = context.entity.uniqueId
        ensureTask(context.plugin, abilityRuntime)
    }

    override fun onDeath(context: MobDeathContext, runtime: MobAbilityRuntime?) {
        val abilityRuntime = runtime as? Runtime ?: return
        stopTask(abilityRuntime)
    }

    private fun ensureTask(plugin: org.bukkit.plugin.java.JavaPlugin, runtime: Runtime) {
        if (runtime.task != null) {
            return
        }
        runtime.task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            processTick(runtime)
        }, 1L, 1L)
    }

    private fun processTick(runtime: Runtime) {
        val ownerId = runtime.ownerId ?: run {
            stopTask(runtime)
            return
        }

        val entity = Bukkit.getEntity(ownerId) as? LivingEntity ?: run {
            stopTask(runtime)
            return
        }
        if (!entity.isValid || entity.isDead) {
            stopTask(runtime)
            return
        }

        runtime.tickCounter += 1
        val shouldRenderLineAndAura = runtime.tickCounter % EFFECT_INTERVAL_TICKS == 0
        if (shouldRenderLineAndAura) {
            spawnMagnetAura(entity)
        }

        val target = MobAbilityUtils.resolveTarget(entity) as? Player ?: return
        if (!target.isValid || target.isDead) return
        if (target.world.uid != entity.world.uid) return

        val distance = entity.location.distance(target.location)
        if (distance < minDistance || distance > maxDistance) {
            return
        }

        val armorPieces = countQualifiedArmorPieces(target)
        if (armorPieces <= 0) {
            return
        }

        if (shouldRenderLineAndAura) {
            drawPullLine(entity.location.clone().add(0.0, 1.0, 0.0), target.location.clone().add(0.0, 1.0, 0.0))
            entity.world.playSound(entity.location, Sound.BLOCK_BEACON_AMBIENT, 1.0f, 0.8f)
        }

        val directionToGolem = entity.location.toVector().subtract(target.location.toVector())
        if (directionToGolem.lengthSquared() < 0.0001) {
            return
        }

        val pull = directionToGolem.normalize().multiply(pullPerArmorPiece * armorPieces.toDouble())
            .setY(verticalBoostPerPiece * armorPieces.toDouble())
        target.velocity = target.velocity.add(pull)
    }

    private fun countQualifiedArmorPieces(player: Player): Int {
        val armor = player.inventory.armorContents
        var count = 0
        for (item in armor) {
            val type = item?.type ?: continue
            if (type in QUALIFIED_ARMOR) {
                count += 1
            }
        }
        return count
    }

    private fun drawPullLine(golemPoint: Location, targetPoint: Location) {
        val world = golemPoint.world ?: return
        if (world.uid != targetPoint.world?.uid) return

        val segment = targetPoint.toVector().subtract(golemPoint.toVector())
        val length = segment.length()
        if (length < 0.0001) return

        val direction = segment.normalize()
        var traveled = 0.0
        while (traveled <= length) {
            val point = golemPoint.toVector().add(direction.clone().multiply(traveled)).toLocation(world)
            world.spawnParticle(
                Particle.DUST,
                point,
                1,
                0.1,
                0.1,
                0.1,
                0.0,
                MAGNET_DUST
            )
            traveled += PARTICLE_STEP
        }
    }

    private fun spawnMagnetAura(entity: LivingEntity) {
        val world = entity.world
        val center = entity.location.clone().add(0.0, 1.0, 0.0)
        world.spawnParticle(
            Particle.DUST,
            center,
            AURA_PARTICLE_COUNT,
            0.1,
            0.1,
            0.1,
            0.0,
            MAGNET_DUST
        )
    }

    private fun stopTask(runtime: Runtime) {
        runtime.task?.cancel()
        runtime.task = null
    }

    private companion object {
        const val EFFECT_INTERVAL_TICKS = 10
        const val PARTICLE_STEP = 0.45
        const val AURA_PARTICLE_COUNT = 12
        val MAGNET_DUST = Particle.DustOptions(Color.fromRGB(130, 130, 130), 1.1f)

        val QUALIFIED_ARMOR = setOf(
            Material.IRON_HELMET,
            Material.IRON_CHESTPLATE,
            Material.IRON_LEGGINGS,
            Material.IRON_BOOTS,
            Material.NETHERITE_HELMET,
            Material.NETHERITE_CHESTPLATE,
            Material.NETHERITE_LEGGINGS,
            Material.NETHERITE_BOOTS
        )
    }
}
