package jp.awabi2048.cccontent.features.rank.skill.handlers

import jp.awabi2048.cccontent.CCContent
import jp.awabi2048.cccontent.features.rank.profession.Profession
import jp.awabi2048.cccontent.features.rank.skill.ActiveSkillManager
import jp.awabi2048.cccontent.features.rank.skill.EffectContext
import jp.awabi2048.cccontent.features.rank.skill.EvaluationMode
import jp.awabi2048.cccontent.features.rank.skill.SkillEffect
import jp.awabi2048.cccontent.features.rank.skill.SkillEffectEngine
import jp.awabi2048.cccontent.features.rank.skill.SkillEffectHandler
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.Container
import org.bukkit.entity.Player
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

class BlastMineHandler : SkillEffectHandler {
    companion object {
        const val EFFECT_TYPE = "collect.blast_mine"

        private const val DEFAULT_RADIUS = 3.0
        private const val DEFAULT_AUTO_COLLECT = false
        private const val DEFAULT_LOSS_RATE = 0.0
        private const val MAX_RADIUS = 10.0
        private const val MIN_RADIUS = 1.0
        private const val BREAK_SOUND_INTERVAL = 4

        @Volatile
        private var activeInstance: BlastMineHandler? = null

        private val activeTasks: MutableMap<UUID, BukkitTask> = ConcurrentHashMap()
        private val internalBreakPlayers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
        private val debugOverrides: MutableMap<UUID, DebugOverride> = ConcurrentHashMap()

        data class DebugOverride(
            val radius: Double,
            val autoCollect: Boolean,
            val lossRate: Double
        )

        fun isInternalBreakInProgress(playerUuid: UUID): Boolean {
            return internalBreakPlayers.contains(playerUuid)
        }

        fun setDebugOverride(playerUuid: UUID, radius: Double, autoCollect: Boolean, lossRate: Double) {
            val normalizedRadius = radius.coerceIn(0.5, MAX_RADIUS)
            val normalizedLossRate = lossRate.coerceIn(0.0, 1.0)

            debugOverrides[playerUuid] = DebugOverride(
                radius = normalizedRadius,
                autoCollect = autoCollect,
                lossRate = normalizedLossRate
            )
        }

        fun clearDebugOverride(playerUuid: UUID) {
            debugOverrides.remove(playerUuid)
        }

        fun getDebugEffect(playerUuid: UUID): SkillEffect? {
            val override = debugOverrides[playerUuid] ?: return null
            val params = mapOf<String, Any>(
                "radius" to override.radius,
                "autoCollect" to override.autoCollect,
                "lossRate" to override.lossRate
            )

            return SkillEffect(
                type = EFFECT_TYPE,
                params = params,
                evaluationMode = EvaluationMode.RUNTIME
            )
        }

        fun stopForPlayer(playerUuid: UUID) {
            activeTasks.remove(playerUuid)?.cancel()
            internalBreakPlayers.remove(playerUuid)
        }

        fun stopAll() {
            for ((playerUuid, task) in activeTasks.toMap()) {
                task.cancel()
                activeTasks.remove(playerUuid)
                internalBreakPlayers.remove(playerUuid)
            }
            debugOverrides.clear()
        }
    }

    private data class BlastMineRuntimeOptions(
        val radius: Double,
        val autoCollect: Boolean,
        val lossRate: Double
    )

    init {
        activeInstance = this
    }

    override fun getEffectType(): String = EFFECT_TYPE

    override fun getDefaultEvaluationMode(): EvaluationMode = EvaluationMode.RUNTIME

    override fun applyEffect(context: EffectContext): Boolean {
        val event = context.getEvent<BlockBreakEvent>() ?: return false
        val player = event.player
        val playerUuid = player.uniqueId

        if (isInternalBreakInProgress(playerUuid)) {
            return false
        }

        // アクティブスキルチェック：このスキルが現在アクティブでなければ処理しない
        if (!ActiveSkillManager.isActiveSkillById(playerUuid, context.skillId)) {
            return false
        }

        if (!isPickaxe(player.inventory.itemInMainHand.type)) {
            return false
        }

        val options = resolveRuntimeOptions(context.skillEffect)
        stopForPlayer(playerUuid)

        val centerBlock = event.block
        val blocks = collectCuboidBlocks(centerBlock, options.radius)

        if (blocks.isEmpty()) {
            return false
        }

        val originTool = player.inventory.itemInMainHand.clone()

        scheduleBlastBreak(player, playerUuid, options, blocks, originTool)

        return true
    }

    private fun isPickaxe(material: Material): Boolean {
        return material.name.endsWith("_PICKAXE")
    }

    private fun resolveRuntimeOptions(skillEffect: SkillEffect): BlastMineRuntimeOptions {
        val radius = skillEffect.getDoubleParam("radius", DEFAULT_RADIUS).coerceIn(MIN_RADIUS, MAX_RADIUS)
        val autoCollect = skillEffect.getBooleanParam("autoCollect", DEFAULT_AUTO_COLLECT)
        val lossRate = skillEffect.getDoubleParam("lossRate", DEFAULT_LOSS_RATE).coerceIn(0.0, 1.0)

        return BlastMineRuntimeOptions(
            radius = radius,
            autoCollect = autoCollect,
            lossRate = lossRate
        )
    }

    private fun collectCuboidBlocks(center: Block, radius: Double): List<Block> {
        val world = center.world
        val centerX = center.x
        val centerY = center.y
        val centerZ = center.z
        val radiusInt = kotlin.math.ceil(radius).toInt()

        val blocks = mutableListOf<Block>()

        for (dx in -radiusInt..radiusInt) {
            for (dy in -radiusInt..radiusInt) {
                for (dz in -radiusInt..radiusInt) {
                    if (dx == 0 && dy == 0 && dz == 0) continue

                    val block = world.getBlockAt(centerX + dx, centerY + dy, centerZ + dz)

                    if (block.type.isAir) continue
                    if (block.type == Material.WATER || block.type == Material.LAVA) continue

                    if (shouldSkipBlock(block)) continue

                    blocks.add(block)
                }
            }
        }

        return blocks
    }

    private fun shouldSkipBlock(block: Block): Boolean {
        val material = block.type
        val name = material.name

        if (block.state is Container) return true

        if (name.endsWith("_SHULKER_BOX") || name.endsWith("SHULKER_BOX")) return true
        if (material == Material.CHEST || material == Material.TRAPPED_CHEST) return true
        if (material == Material.BARREL) return true
        if (material == Material.ENDER_CHEST) return true

        if (material == Material.FURNACE || material == Material.BLAST_FURNACE || material == Material.SMOKER) return true
        if (material == Material.BREWING_STAND) return true
        if (material == Material.HOPPER || material == Material.DROPPER || material == Material.DISPENSER) return true

        if (name.endsWith("_BED")) return true
        if (material == Material.SPAWNER) return true
        if (material == Material.BEEHIVE || material == Material.BEE_NEST) return true
        if (name.endsWith("_SIGN") || name.endsWith("_HANGING_SIGN")) return true

        return false
    }

    private fun scheduleBlastBreak(
        player: Player,
        playerUuid: UUID,
        options: BlastMineRuntimeOptions,
        blocks: List<Block>,
        originTool: ItemStack
    ) {
        val totalBlocks = blocks.size
        player.sendActionBar(net.kyori.adventure.text.Component.text("BlastMine: $totalBlocks ブロック"))
        val plugin = CCContent.instance

        val task = plugin.server.scheduler.runTask(plugin, Runnable {
            // 爆発音を再生
            player.world.playSound(
                player.location,
                org.bukkit.Sound.ENTITY_GENERIC_EXPLODE,
                1.0f,
                1.0f
            )

            internalBreakPlayers.add(playerUuid)
            try {
                var successCount = 0
                for (target in blocks) {
                    if (!player.isOnline || target.type.isAir) continue
                    val soundGroup = target.blockSoundGroup
                    val simulatedDrops = simulateDrops(target, originTool, player)
                    val success = breakDirectly(target)
                    if (success) {
                        grantSimulatedDrops(player, target, simulatedDrops, options)
                        successCount += 1
                        if (successCount % BREAK_SOUND_INTERVAL == 0) {
                            playBreakSound(target, soundGroup)
                        }
                    }
                }
            } finally {
                internalBreakPlayers.remove(playerUuid)
                stopForPlayer(playerUuid)
            }
        })

        activeTasks[playerUuid] = task
    }

    private fun breakDirectly(target: Block): Boolean {
        if (target.type.isAir) {
            return false
        }
        target.type = Material.AIR
        return true
    }

    private fun simulateDrops(target: Block, tool: ItemStack, player: Player): List<ItemStack> {
        return target.getDrops(tool, player).map { it.clone() }
    }

    private fun grantSimulatedDrops(player: Player, block: Block, drops: List<ItemStack>, options: BlastMineRuntimeOptions) {
        if (drops.isEmpty()) {
            return
        }

        val world = block.world
        val dropLocation = block.location.clone().add(0.5, 0.5, 0.5)

        for (stack in drops) {
            if (options.lossRate > 0.0 && Random.nextDouble() < options.lossRate) {
                continue
            }

            if (options.autoCollect) {
                val leftovers = player.inventory.addItem(stack.clone())
                for (leftover in leftovers.values) {
                    world.dropItem(dropLocation, leftover).apply {
                        pickupDelay = 0
                        owner = player.uniqueId
                    }
                }
            } else {
                world.dropItem(dropLocation, stack.clone())
            }
        }
    }


    private fun playBreakSound(block: Block, soundGroup: org.bukkit.SoundGroup) {
        val location = block.location.clone().add(0.5, 0.5, 0.5)
        block.world.playSound(location, soundGroup.breakSound, soundGroup.volume, soundGroup.pitch)
    }

    override fun calculateStrength(skillEffect: SkillEffect): Double {
        val radius = skillEffect.getDoubleParam("radius", DEFAULT_RADIUS)
        val size = kotlin.math.ceil(radius).toInt()
        val oneSide = size * 2 + 1
        val volume = (oneSide * oneSide * oneSide).toDouble()
        return volume
    }

    override fun supportsProfession(professionId: String): Boolean {
        return professionId == "miner"
    }

    override fun validateParams(skillEffect: SkillEffect): Boolean {
        val radius = skillEffect.getDoubleParam("radius", DEFAULT_RADIUS)
        return radius > 0
    }
}
