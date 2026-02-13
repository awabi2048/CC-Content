package jp.awabi2048.cccontent.features.rank.skill.handlers

import jp.awabi2048.cccontent.CCContent
import jp.awabi2048.cccontent.features.rank.job.BlockPositionCodec
import jp.awabi2048.cccontent.features.rank.job.IgnoreBlockStore
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
import org.bukkit.entity.ExperienceOrb
import org.bukkit.entity.Player
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockDropItemEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class BlastMineHandler(
    private val ignoreBlockStore: IgnoreBlockStore
) : SkillEffectHandler {
    companion object {
        const val EFFECT_TYPE = "collect.blast_mine"

        private const val DEFAULT_RADIUS = 3.0
        private const val DEFAULT_AUTO_COLLECT = false
        private const val DEFAULT_LOSS_RATE = 0.0
        private const val MAX_RADIUS = 10.0
        private const val MIN_RADIUS = 1.0

        @Volatile
        private var activeInstance: BlastMineHandler? = null

        private val activeTasks: MutableMap<UUID, BukkitTask> = ConcurrentHashMap()
        private val internalBreakPlayers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
        private val debugOverrides: MutableMap<UUID, DebugOverride> = ConcurrentHashMap()
        private val pendingDropModifiers: MutableMap<UUID, MutableList<DropModifier>> = ConcurrentHashMap()

        data class DebugOverride(
            val radius: Double,
            val delayTicksPerLayer: Int,
            val autoCollect: Boolean,
            val lossRate: Double
        )

        data class DropModifier(
            val blockX: Int,
            val blockY: Int,
            val blockZ: Int,
            val lossRate: Double,
            val autoCollect: Boolean,
            val originTool: ItemStack?
        )

        fun isInternalBreakInProgress(playerUuid: UUID): Boolean {
            return internalBreakPlayers.contains(playerUuid)
        }

        fun setDebugOverride(playerUuid: UUID, radius: Double, delayTicksPerLayer: Int, autoCollect: Boolean, lossRate: Double) {
            val normalizedRadius = radius.coerceIn(0.5, MAX_RADIUS)
            val normalizedLossRate = lossRate.coerceIn(0.0, 1.0)

            debugOverrides[playerUuid] = DebugOverride(
                radius = normalizedRadius,
                delayTicksPerLayer = 0,
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
                "delayTicksPerLayer" to override.delayTicksPerLayer,
                "autoCollect" to override.autoCollect,
                "lossRate" to override.lossRate
            )

            return SkillEffect(
                type = EFFECT_TYPE,
                params = params,
                evaluationMode = EvaluationMode.RUNTIME
            )
        }

        fun getDropModifier(playerUuid: UUID, block: Block): DropModifier? {
            val modifiers = pendingDropModifiers[playerUuid] ?: return null
            return modifiers.find { it.blockX == block.x && it.blockY == block.y && it.blockZ == block.z }
        }

        fun removeDropModifier(playerUuid: UUID, block: Block) {
            val modifiers = pendingDropModifiers[playerUuid] ?: return
            modifiers.removeAll { it.blockX == block.x && it.blockY == block.y && it.blockZ == block.z }
            if (modifiers.isEmpty()) {
                pendingDropModifiers.remove(playerUuid)
            }
        }

        fun stopForPlayer(playerUuid: UUID) {
            activeTasks.remove(playerUuid)?.cancel()
            internalBreakPlayers.remove(playerUuid)
            pendingDropModifiers.remove(playerUuid)
        }

        fun stopAll() {
            for ((playerUuid, task) in activeTasks.toMap()) {
                task.cancel()
                activeTasks.remove(playerUuid)
                internalBreakPlayers.remove(playerUuid)
            }
            pendingDropModifiers.clear()
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

        if (isPlayerPlacedBlock(event.block)) {
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

        val originHeldSlot = player.inventory.heldItemSlot
        val originTool = player.inventory.itemInMainHand.clone()

        registerDropModifiers(playerUuid, blocks, options, originTool)

        scheduleBlastBreak(player, playerUuid, options, blocks, originHeldSlot, originTool)

        return true
    }

    private fun isPickaxe(material: Material): Boolean {
        return material.name.endsWith("_PICKAXE")
    }

    private fun isPlayerPlacedBlock(block: Block): Boolean {
        val packedPosition = BlockPositionCodec.pack(block.x, block.y, block.z)
        return ignoreBlockStore.contains(block.world.uid, packedPosition)
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
                    if (isPlayerPlacedBlock(block)) continue

                    blocks.add(block)
                }
            }
        }

        return blocks
    }

    private fun collectSphericalBlocks(center: Block, radius: Double): Map<Int, MutableList<Block>> {
        val world = center.world
        val centerX = center.x
        val centerY = center.y
        val centerZ = center.z
        val radiusSquared = radius * radius
        val radiusInt = kotlin.math.ceil(radius).toInt()

        val blocksByDistance = mutableMapOf<Int, MutableList<Block>>()

        for (dx in -radiusInt..radiusInt) {
            for (dy in -radiusInt..radiusInt) {
                for (dz in -radiusInt..radiusInt) {
                    if (dx == 0 && dy == 0 && dz == 0) continue

                    val distanceSquared = dx * dx + dy * dy + dz * dz
                    if (distanceSquared > radiusSquared) continue

                    val distance = kotlin.math.sqrt(distanceSquared.toDouble()).toInt()
                    if (distance == 0) continue

                    val block = world.getBlockAt(centerX + dx, centerY + dy, centerZ + dz)

                    if (block.type.isAir) continue
                    if (block.type == Material.WATER || block.type == Material.LAVA) continue

                    if (shouldSkipBlock(block)) continue

                    if (isPlayerPlacedBlock(block)) continue

                    blocksByDistance.getOrPut(distance) { mutableListOf() }.add(block)
                }
            }
        }

        return blocksByDistance.toSortedMap()
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

    private fun registerDropModifiers(
        playerUuid: UUID,
        blocks: List<Block>,
        options: BlastMineRuntimeOptions,
        originTool: ItemStack
    ) {
        val modifiers = mutableListOf<DropModifier>()
        for (block in blocks) {
            modifiers.add(DropModifier(
                blockX = block.x,
                blockY = block.y,
                blockZ = block.z,
                lossRate = options.lossRate,
                autoCollect = options.autoCollect,
                originTool = originTool.clone()
            ))
        }
        pendingDropModifiers[playerUuid] = modifiers
    }

    private fun scheduleBlastBreak(
        player: Player,
        playerUuid: UUID,
        options: BlastMineRuntimeOptions,
        blocks: List<Block>,
        originHeldSlot: Int,
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
                for (target in blocks) {
                    if (!player.isOnline || target.type.isAir || isPlayerPlacedBlock(target)) continue
                    val soundGroup = target.blockSoundGroup
                    val success = breakWithOriginalContext(player, originHeldSlot, target, preserveDurability = true)
                    if (success) {
                        playBreakSound(target, soundGroup)
                    }
                }
            } finally {
                internalBreakPlayers.remove(playerUuid)
                stopForPlayer(playerUuid)
            }
        })

        activeTasks[playerUuid] = task
    }

    private fun breakWithOriginalContext(player: Player, originHeldSlot: Int, target: Block, preserveDurability: Boolean): Boolean {
        val inventory = player.inventory
        val previousSlot = inventory.heldItemSlot
        val beforeTool = if (preserveDurability && originHeldSlot in 0..8) inventory.getItem(originHeldSlot)?.clone() else null
        if (originHeldSlot in 0..8 && previousSlot != originHeldSlot) {
            inventory.heldItemSlot = originHeldSlot
        }

        return try {
            player.breakBlock(target)
        } finally {
            if (beforeTool != null && originHeldSlot in 0..8) {
                inventory.setItem(originHeldSlot, beforeTool)
            }
            if (inventory.heldItemSlot != previousSlot) {
                inventory.heldItemSlot = previousSlot
            }
        }
    }

    fun applyDropModification(player: Player, event: BlockDropItemEvent) {
        val modifier = getDropModifier(player.uniqueId, event.block) ?: return

        if (modifier.lossRate > 0.0) {
            val iterator = event.items.iterator()
            while (iterator.hasNext()) {
                val itemEntity = iterator.next()
                if (kotlin.random.Random.nextDouble() < modifier.lossRate) {
                    iterator.remove()
                    itemEntity.remove()
                }
            }
        }

        if (modifier.autoCollect && event.items.isNotEmpty()) {
            for (itemEntity in event.items.toList()) {
                val stack = itemEntity.itemStack.clone()
                itemEntity.remove()
                val leftovers = player.inventory.addItem(stack)
                for (leftover in leftovers.values) {
                    event.block.world.dropItem(
                        event.block.location.clone().add(0.5, 0.5, 0.5),
                        leftover
                    ).apply {
                        pickupDelay = 0
                        owner = player.uniqueId
                    }
                }
            }
            event.items.clear()
        }

        removeDropModifier(player.uniqueId, event.block)
    }

    fun applyExpCollection(player: Player, block: Block) {
        val modifier = getDropModifier(player.uniqueId, block) ?: return
        if (!modifier.autoCollect) return

        val center = block.location.clone().add(0.5, 0.5, 0.5)
        val entities = block.world.getNearbyEntities(center, 1.5, 1.5, 1.5) { entity ->
            entity is ExperienceOrb
        }

        for (entity in entities) {
            val expOrb = entity as? ExperienceOrb ?: continue
            player.giveExp(expOrb.experience)
            expOrb.remove()
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
