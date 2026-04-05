package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobDeathContext
import jp.awabi2048.cccontent.mob.MobService
import jp.awabi2048.cccontent.mob.MobSpawnOptions
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.data.Levelled
import kotlin.random.Random

class MagmaStageDeathAbility(
    override val id: String,
    private val explosionPower: Float,
    private val lavaRadius: Int,
    private val lavaLevels: List<Int>,
    private val childDefinitionId: String? = null
) : MobAbility {

    override fun onDeath(context: MobDeathContext, runtime: MobAbilityRuntime?) {
        val source = context.entity
        val world = source.world ?: return
        val deathLocation = source.location.clone()

        if (explosionPower > 0f) {
            world.createExplosion(deathLocation, explosionPower, false, false, source)
        }
        placeLavaLayers(world, deathLocation)
        spawnChild(context, deathLocation)
    }

    private fun placeLavaLayers(world: org.bukkit.World, center: org.bukkit.Location) {
        if (lavaLevels.isEmpty()) return
        for (dx in -lavaRadius..lavaRadius) {
            for (dz in -lavaRadius..lavaRadius) {
                val x = center.blockX + dx
                val z = center.blockZ + dz
                val baseY = center.blockY
                for (y in baseY downTo (baseY - 3)) {
                    val floor = world.getBlockAt(x, y, z)
                    val above = world.getBlockAt(x, y + 1, z)
                    if (!floor.type.isSolid || !above.isPassable) {
                        continue
                    }

                    above.type = Material.LAVA
                    val data = above.blockData as? Levelled ?: break
                    val requested = lavaLevels.random(Random.Default)
                    val clamped = requested.coerceIn(data.minimumLevel, data.maximumLevel)
                    data.level = clamped
                    above.blockData = data
                    break
                }
            }
        }
    }

    private fun spawnChild(context: MobDeathContext, deathLocation: org.bukkit.Location) {
        val childId = childDefinitionId ?: return
        val mobService = MobService.getInstance(context.plugin) ?: return
        val childDefinition = mobService.getDefinition(childId) ?: return

        val options = MobSpawnOptions(
            featureId = context.activeMob.featureId,
            sessionKey = context.sessionKey,
            combatActiveProvider = context.activeMob.combatActiveProvider,
            metadata = context.activeMob.metadata + ("magma_split_child" to "true")
        )
        val child = mobService.spawn(childDefinition, deathLocation.clone().add(0.0, 0.1, 0.0), options)
        if (child != null) {
            return
        }

        Bukkit.getScheduler().runTask(context.plugin, Runnable {
            mobService.spawn(childDefinition, deathLocation.clone().add(0.0, 0.1, 0.0), options)
        })
    }
}
