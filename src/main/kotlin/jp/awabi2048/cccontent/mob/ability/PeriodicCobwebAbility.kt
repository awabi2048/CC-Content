package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobRuntimeContext
import org.bukkit.Location
import org.bukkit.Material
import kotlin.random.Random

class PeriodicCobwebAbility(
    override val id: String,
    private val cycleTicks: Long = 100L,
    private val webLifetimeTicks: Long = 100L,
    private val radius: Int = 3,
    private val maxWebsPerCycle: Int = 8
) : MobAbility {

    data class Runtime(
        var cycleCooldownTicks: Long = 0L,
        val activeWebs: MutableMap<BlockPos, Long> = mutableMapOf()
    ) : MobAbilityRuntime

    data class BlockPos(
        val worldName: String,
        val x: Int,
        val y: Int,
        val z: Int
    )

    override fun createRuntime(context: jp.awabi2048.cccontent.mob.MobSpawnContext): MobAbilityRuntime {
        return Runtime(cycleCooldownTicks = cycleTicks.coerceAtLeast(1L))
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val abilityRuntime = runtime as? Runtime ?: return
        val world = context.entity.world
        val nowTick = world.fullTime

        restoreExpiredWebs(world, abilityRuntime, nowTick)

        if (!context.isCombatActive()) return

        if (abilityRuntime.cycleCooldownTicks > 0L) {
            abilityRuntime.cycleCooldownTicks -= 10L
            return
        }

        abilityRuntime.cycleCooldownTicks = cycleTicks.coerceAtLeast(1L)
        placeCobwebs(context.entity.location, abilityRuntime, nowTick)
    }

    private fun restoreExpiredWebs(
        world: org.bukkit.World,
        runtime: Runtime,
        nowTick: Long
    ) {
        val iterator = runtime.activeWebs.entries.iterator()
        while (iterator.hasNext()) {
            val (pos, expiresAt) = iterator.next()
            if (expiresAt > nowTick) continue

            if (world.name == pos.worldName) {
                val block = world.getBlockAt(pos.x, pos.y, pos.z)
                if (block.type == Material.COBWEB) {
                    block.type = Material.AIR
                }
            }
            iterator.remove()
        }
    }

    private fun placeCobwebs(origin: Location, runtime: Runtime, nowTick: Long) {
        val world = origin.world ?: return
        val centerX = origin.blockX
        val centerY = origin.blockY
        val centerZ = origin.blockZ

        val candidates = mutableListOf<BlockPos>()
        for (dx in -radius..radius) {
            for (dy in -1..1) {
                for (dz in -radius..radius) {
                    if (dx == 0 && dy == 0 && dz == 0) continue
                    val x = centerX + dx
                    val y = centerY + dy
                    val z = centerZ + dz
                    val block = world.getBlockAt(x, y, z)
                    if (block.type != Material.AIR) continue
                    candidates.add(BlockPos(world.name, x, y, z))
                }
            }
        }

        if (candidates.isEmpty()) return

        val placeCount = maxWebsPerCycle.coerceAtLeast(1).coerceAtMost(candidates.size)
        repeat(placeCount) {
            val index = Random.nextInt(candidates.size)
            val pos = candidates.removeAt(index)
            val block = world.getBlockAt(pos.x, pos.y, pos.z)
            if (block.type != Material.AIR) return@repeat
            block.type = Material.COBWEB
            runtime.activeWebs[pos] = nowTick + webLifetimeTicks.coerceAtLeast(1L)
        }
    }
}
