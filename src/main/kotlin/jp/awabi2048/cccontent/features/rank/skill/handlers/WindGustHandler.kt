package jp.awabi2048.cccontent.features.rank.skill.handlers

import jp.awabi2048.cccontent.CCContent
import jp.awabi2048.cccontent.features.rank.skill.ActiveSkillManager
import jp.awabi2048.cccontent.features.rank.skill.ActiveTriggerType
import jp.awabi2048.cccontent.features.rank.skill.EffectContext
import jp.awabi2048.cccontent.features.rank.skill.SkillEffect
import jp.awabi2048.cccontent.features.rank.skill.SkillEffectHandler
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class WindGustHandler : SkillEffectHandler {
    companion object {
        const val EFFECT_TYPE = "lumberjack.wind_gust"
        
        private const val MAX_ANGLE_DEGREES = 30.0
        
        private const val DEFAULT_MAX_DISTANCE = 8
        private const val DEFAULT_DELAY_PER_BLOCK = 2L
        
        private val activeTasks: MutableMap<UUID, MutableList<BukkitTask>> = ConcurrentHashMap()
    }

    override fun getEffectType(): String = EFFECT_TYPE

    override fun isActiveSkill(): Boolean = true

    override fun getTriggerType(): ActiveTriggerType = ActiveTriggerType.MANUAL_SHIFT_RIGHT_CLICK

    override fun applyEffect(context: EffectContext): Boolean {
        val player = context.player
        if (!ActiveSkillManager.isActiveSkillById(player.uniqueId, context.skillId)) {
            return false
        }

        val maxDistance = context.skillEffect.getDoubleParam("max_distance", DEFAULT_MAX_DISTANCE.toDouble()).toInt()
        val delayPerBlock = context.skillEffect.getDoubleParam("delay_per_block", DEFAULT_DELAY_PER_BLOCK.toDouble()).toLong()

        cancelActiveTasks(player.uniqueId)

        player.world.playSound(player.location, Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.0f, 1.0f)

        val leafBlocks = collectLeafBlocks(player, maxDistance)
        val tasks = mutableListOf<BukkitTask>()

        for ((index, block) in leafBlocks.withIndex()) {
            val distance = block.location.distance(player.eyeLocation)
            val delay = (distance * delayPerBlock).toLong()

            val task = CCContent.instance?.server?.scheduler?.runTaskLater(
                CCContent.instance,
                Runnable {
                    if (player.isOnline) {
                        breakBlockWithDrops(player, block)
                    }
                },
                delay
            )
            task?.let { tasks.add(it) }
        }

        activeTasks[player.uniqueId] = tasks
        return true
    }

    private fun collectLeafBlocks(player: Player, maxDistance: Int): List<Block> {
        val eyeLocation = player.eyeLocation
        val lookDirection = eyeLocation.direction
        val world = player.world
        val radiusSquared = maxDistance.toDouble() * maxDistance.toDouble()
        val radiusInt = maxDistance

        val blocks = mutableListOf<Pair<Block, Double>>()
        val centerVector = eyeLocation.toVector()

        for (dx in -radiusInt..radiusInt) {
            for (dy in -radiusInt..radiusInt) {
                for (dz in -radiusInt..radiusInt) {
                    val distanceSquared = dx.toDouble() * dx.toDouble() + 
                                      dy.toDouble() * dy.toDouble() + 
                                      dz.toDouble() * dz.toDouble()

                    if (distanceSquared > radiusSquared) continue

                    val block = world.getBlockAt(
                        eyeLocation.blockX + dx,
                        eyeLocation.blockY + dy,
                        eyeLocation.blockZ + dz
                    )

                    if (!isLeafBlock(block)) continue

                    val blockCenter = block.location.toVector().add(org.bukkit.util.Vector(0.5, 0.5, 0.5))
                    val toBlockVector = blockCenter.subtract(centerVector)
                    val angleRadians = lookDirection.angle(toBlockVector)
                    val angleDegrees = Math.toDegrees(angleRadians.toDouble())

                    if (angleDegrees <= MAX_ANGLE_DEGREES) {
                        blocks.add(block to distanceSquared)
                    }
                }
            }
        }

        return blocks.sortedBy { it.second }.map { it.first }
    }

    private fun isLeafBlock(block: Block): Boolean {
        return block.type.name.endsWith("_LEAVES")
    }

    private fun breakBlockWithDrops(player: Player, block: Block) {
        val world = block.world
        val drops = block.getDrops(player.inventory.itemInMainHand, player)

        block.type = Material.AIR

        val dropLocation = block.location.clone().add(0.5, 0.5, 0.5)
        for (drop in drops) {
            world.dropItemNaturally(dropLocation, drop)
        }
    }

    private fun cancelActiveTasks(playerUuid: UUID) {
        activeTasks[playerUuid]?.forEach { it.cancel() }
        activeTasks.remove(playerUuid)
    }

    override fun calculateStrength(skillEffect: SkillEffect): Double {
        val maxDistance = skillEffect.getDoubleParam("max_distance", DEFAULT_MAX_DISTANCE.toDouble())
        val delayPerBlock = skillEffect.getDoubleParam("delay_per_block", DEFAULT_DELAY_PER_BLOCK.toDouble())
        return (maxDistance * 10.0) - (delayPerBlock * 2.0)
    }

    override fun supportsProfession(professionId: String): Boolean {
        return professionId == "lumberjack"
    }

    override fun validateParams(skillEffect: SkillEffect): Boolean {
        val maxDistance = skillEffect.getDoubleParam("max_distance", DEFAULT_MAX_DISTANCE.toDouble()).toInt()
        val delayPerBlock = skillEffect.getDoubleParam("delay_per_block", DEFAULT_DELAY_PER_BLOCK.toDouble()).toLong()

        if (maxDistance < 1 || maxDistance > 32) return false
        if (delayPerBlock < 0 || delayPerBlock > 20) return false

        return true
    }
}
