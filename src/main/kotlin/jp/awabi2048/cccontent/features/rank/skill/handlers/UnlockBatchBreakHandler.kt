package jp.awabi2048.cccontent.features.rank.skill.handlers

import jp.awabi2048.cccontent.CCContent
import jp.awabi2048.cccontent.features.rank.job.BlockPositionCodec
import jp.awabi2048.cccontent.features.rank.job.IgnoreBlockStore
import jp.awabi2048.cccontent.features.rank.profession.Profession
import jp.awabi2048.cccontent.features.rank.skill.EffectContext
import jp.awabi2048.cccontent.features.rank.skill.EvaluationMode
import jp.awabi2048.cccontent.features.rank.skill.SkillEffect
import jp.awabi2048.cccontent.features.rank.skill.SkillEffectEngine
import jp.awabi2048.cccontent.features.rank.skill.SkillEffectHandler
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.Display
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.ExperienceOrb
import org.bukkit.entity.Player
import org.bukkit.entity.Item
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

class UnlockBatchBreakHandler(
    private val ignoreBlockStore: IgnoreBlockStore
) : SkillEffectHandler {
    init {
        activeInstance = this
    }

    enum class BatchBreakMode(val modeKey: String) {
        CUT_ALL("cut_all"),
        MINE_ALL("mine_all");

        companion object {
            fun fromRaw(raw: String?): BatchBreakMode? {
                val normalized = raw?.lowercase() ?: return null
                return entries.firstOrNull { it.modeKey == normalized }
            }

            fun fromProfession(profession: Profession?): BatchBreakMode? {
                return when (profession) {
                    Profession.LUMBERJACK -> CUT_ALL
                    Profession.MINER -> MINE_ALL
                    else -> null
                }
            }

            fun fromTool(toolType: Material): BatchBreakMode? {
                val toolName = toolType.name
                return when {
                    toolName.contains("PICKAXE") -> MINE_ALL
                    toolName.endsWith("_AXE") -> CUT_ALL
                    else -> null
                }
            }
        }
    }

    companion object {
        const val EFFECT_TYPE = "collect.unlock_batch_break"

        private const val DEFAULT_MAX_CHAIN_COUNT = 16
        private const val DEFAULT_DELAY_TICKS = 2L
        private const val MAX_ALLOWED_CHAIN_COUNT = 512
        private const val MIN_DELAY_TICKS = 0L
        private const val MAX_DELAY_TICKS = 40L
        private const val MAX_VISUALIZE_BLOCKS = 128
        private const val DEFAULT_DOUBLE_CLICK_WINDOW_MILLIS = 350L
        private const val DEFAULT_PREVIEW_TTL_TICKS = 12L
        private const val PREVIEW_COOLDOWN_MILLIS = 120L

        @Volatile
        private var activeInstance: UnlockBatchBreakHandler? = null

        private val activeTasks: MutableMap<UUID, BukkitTask> = ConcurrentHashMap()
        private val activeIndicators: MutableMap<UUID, MutableList<BlockDisplay>> = ConcurrentHashMap()
        private val previewClearTasks: MutableMap<UUID, BukkitTask> = ConcurrentHashMap()
        private val internalBreakPlayers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
        private val modeToggles: MutableMap<UUID, PlayerModeToggleState> = ConcurrentHashMap()
        private val lastSneakClicks: MutableMap<UUID, Long> = ConcurrentHashMap()
        private val lastPreviewAt: MutableMap<UUID, Long> = ConcurrentHashMap()
        private val debugOverrides: MutableMap<UUID, MutableMap<BatchBreakMode, DebugOverride>> = ConcurrentHashMap()

        fun previewForPlayer(player: Player, block: Block) {
            activeInstance?.previewOnLeftClick(player, block)
        }

        data class PlayerModeToggleState(
            var cutAllEnabled: Boolean = true,
            var mineAllEnabled: Boolean = true
        )

        data class DebugOverride(
            val delayTicks: Int,
            val maxChainCount: Int,
            val autoCollect: Boolean
        )

        fun isInternalBreakInProgress(playerUuid: UUID): Boolean {
            return internalBreakPlayers.contains(playerUuid)
        }

        fun isModeEnabled(playerUuid: UUID, mode: BatchBreakMode): Boolean {
            val state = modeToggles[playerUuid] ?: PlayerModeToggleState()
            return when (mode) {
                BatchBreakMode.CUT_ALL -> state.cutAllEnabled
                BatchBreakMode.MINE_ALL -> state.mineAllEnabled
            }
        }

        fun toggleMode(playerUuid: UUID, mode: BatchBreakMode): Boolean {
            val state = modeToggles.getOrPut(playerUuid) { PlayerModeToggleState() }
            return when (mode) {
                BatchBreakMode.CUT_ALL -> {
                    state.cutAllEnabled = !state.cutAllEnabled
                    state.cutAllEnabled
                }
                BatchBreakMode.MINE_ALL -> {
                    state.mineAllEnabled = !state.mineAllEnabled
                    state.mineAllEnabled
                }
            }
        }

        fun markSneakClickAndCheckDoubleClick(playerUuid: UUID, nowMillis: Long = System.currentTimeMillis()): Boolean {
            val last = lastSneakClicks[playerUuid]
            if (last == null || nowMillis - last > DEFAULT_DOUBLE_CLICK_WINDOW_MILLIS) {
                lastSneakClicks[playerUuid] = nowMillis
                return false
            }

            lastSneakClicks.remove(playerUuid)
            return true
        }

        fun canUseMode(playerUuid: UUID, mode: BatchBreakMode): Boolean {
            val debug = debugOverrides[playerUuid]
            if (debug != null && debug.containsKey(mode)) {
                return true
            }

            val compiled = SkillEffectEngine.getCachedEffects(playerUuid) ?: return false
            if (!SkillEffectEngine.hasCachedEffect(playerUuid, EFFECT_TYPE)) {
                return false
            }

            return when (mode) {
                BatchBreakMode.CUT_ALL -> compiled.profession == Profession.LUMBERJACK
                BatchBreakMode.MINE_ALL -> compiled.profession == Profession.MINER
            }
        }

        fun setDebugOverride(playerUuid: UUID, mode: BatchBreakMode, delayTicks: Int, maxChainCount: Int, autoCollect: Boolean) {
            val normalizedDelay = delayTicks.coerceIn(MIN_DELAY_TICKS.toInt(), MAX_DELAY_TICKS.toInt())
            val normalizedChain = maxChainCount.coerceIn(1, MAX_ALLOWED_CHAIN_COUNT)

            debugOverrides
                .getOrPut(playerUuid) { mutableMapOf() }[mode] = DebugOverride(
                delayTicks = normalizedDelay,
                maxChainCount = normalizedChain,
                autoCollect = autoCollect
            )
        }

        fun clearDebugOverride(playerUuid: UUID, mode: BatchBreakMode) {
            val overrides = debugOverrides[playerUuid] ?: return
            overrides.remove(mode)
            if (overrides.isEmpty()) {
                debugOverrides.remove(playerUuid)
            }
        }

        fun getDebugEffect(playerUuid: UUID, mode: BatchBreakMode): SkillEffect? {
            val override = debugOverrides[playerUuid]?.get(mode) ?: return null
            val params = mapOf<String, Any>(
                "mode" to mode.modeKey,
                "delayTicks" to override.delayTicks,
                "maxChainCount" to override.maxChainCount,
                "autoCollect" to override.autoCollect,
                "visualizeTargets" to true,
                "matchLevel" to if (mode == BatchBreakMode.MINE_ALL) "FAMILY" else "EXACT"
            )

            return SkillEffect(
                type = EFFECT_TYPE,
                params = params,
                evaluationMode = EvaluationMode.RUNTIME
            )
        }

        fun stopForPlayer(playerUuid: UUID) {
            activeTasks.remove(playerUuid)?.cancel()
            clearIndicators(playerUuid)
            previewClearTasks.remove(playerUuid)?.cancel()
            internalBreakPlayers.remove(playerUuid)
        }

        fun stopAll() {
            for ((playerUuid, task) in activeTasks.toMap()) {
                task.cancel()
                activeTasks.remove(playerUuid)
                clearIndicators(playerUuid)
                previewClearTasks.remove(playerUuid)?.cancel()
                internalBreakPlayers.remove(playerUuid)
            }
            modeToggles.clear()
            lastSneakClicks.clear()
            lastPreviewAt.clear()
            debugOverrides.clear()
        }

        private fun clearIndicators(playerUuid: UUID) {
            val indicators = activeIndicators.remove(playerUuid) ?: return
            for (indicator in indicators) {
                if (indicator.isValid) {
                    indicator.remove()
                }
            }
        }

        private fun shouldSkipPreviewByCooldown(playerUuid: UUID, nowMillis: Long = System.currentTimeMillis()): Boolean {
            val last = lastPreviewAt[playerUuid]
            if (last != null && nowMillis - last < PREVIEW_COOLDOWN_MILLIS) {
                return true
            }

            lastPreviewAt[playerUuid] = nowMillis
            return false
        }

        private fun materialFamilyKey(material: Material): String {
            val name = material.name
            if (!name.endsWith("_ORE")) {
                return name
            }

            if (name.startsWith("DEEPSLATE_")) {
                return name.removePrefix("DEEPSLATE_")
            }

            return name
        }

        private fun isMaterialMatched(reference: Material, candidate: Material, matchLevel: MatchLevel): Boolean {
            if (reference == candidate) {
                return true
            }

            if (matchLevel == MatchLevel.EXACT) {
                return false
            }

            return materialFamilyKey(reference) == materialFamilyKey(candidate)
        }

        private fun isPlayerPlaced(ignoreBlockStore: IgnoreBlockStore, block: Block): Boolean {
            val packed = BlockPositionCodec.pack(block.x, block.y, block.z)
            return ignoreBlockStore.contains(block.world.uid, packed)
        }

        private fun collectConnectedBlocks(
            origin: Block,
            maxChainCount: Int,
            mode: BatchBreakMode,
            matchLevel: MatchLevel,
            targetBlocks: Set<Material>,
            ignoreBlockStore: IgnoreBlockStore
        ): List<Block> {
            val world = origin.world
            val baseType = origin.type
            val queue = ArrayDeque<Block>()
            val visited = mutableSetOf<Long>()
            val result = mutableListOf<Block>()

            queue.add(origin)
            visited.add(BlockPositionCodec.pack(origin.x, origin.y, origin.z))

            while (queue.isNotEmpty() && result.size <= maxChainCount) {
                val current = queue.removeFirst()
                if (!isMaterialMatched(baseType, current.type, matchLevel)) {
                    continue
                }

                if (!isModeTargetMaterial(mode, current.type)) {
                    continue
                }

                if (targetBlocks.isNotEmpty() && current.type !in targetBlocks) {
                    continue
                }

                if (isPlayerPlaced(ignoreBlockStore, current)) {
                    continue
                }

                result.add(current)
                if (result.size > maxChainCount) {
                    break
                }

                for ((dx, dy, dz) in OFFSETS_26) {
                    val nx = current.x + dx
                    val ny = current.y + dy
                    val nz = current.z + dz
                    val packed = BlockPositionCodec.pack(nx, ny, nz)
                    if (!visited.add(packed)) {
                        continue
                    }

                    val next = world.getBlockAt(nx, ny, nz)
                    if (isMaterialMatched(baseType, next.type, matchLevel)
                        && isModeTargetMaterial(mode, next.type)
                        && (targetBlocks.isEmpty() || next.type in targetBlocks)
                        && !isPlayerPlaced(ignoreBlockStore, next)
                    ) {
                        queue.add(next)
                    }
                }
            }

            return if (result.size <= 1) emptyList() else result.drop(1).take(maxChainCount)
        }

        private fun isModeTargetMaterial(mode: BatchBreakMode, material: Material): Boolean {
            return when (mode) {
                BatchBreakMode.MINE_ALL -> material.name.endsWith("_ORE")
                BatchBreakMode.CUT_ALL -> material.name.endsWith("_LOG")
            }
        }

        private val OFFSETS_26 = buildList {
            for (x in -1..1) {
                for (y in -1..1) {
                    for (z in -1..1) {
                        if (x == 0 && y == 0 && z == 0) {
                            continue
                        }
                        add(Triple(x, y, z))
                    }
                }
            }
        }.toTypedArray()
    }

    private data class BatchBreakRuntimeOptions(
        val mode: BatchBreakMode,
        val maxChainCount: Int,
        val delayTicks: Long,
        val instantBreak: Boolean,
        val autoCollect: Boolean,
        val visualizeTargets: Boolean,
        val matchLevel: MatchLevel,
        val targetBlocks: Set<Material>
    )

    enum class MatchLevel {
        EXACT,
        FAMILY;

        companion object {
            fun fromRaw(raw: String?): MatchLevel {
                val value = raw?.uppercase() ?: return EXACT
                return entries.firstOrNull { it.name == value } ?: EXACT
            }
        }
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

        val options = resolveRuntimeOptions(context.skillEffect, context.profession) ?: return false
        if (!canUseMode(playerUuid, options.mode) || !isModeEnabled(playerUuid, options.mode)) {
            return false
        }

        val heldMode = BatchBreakMode.fromTool(player.inventory.itemInMainHand.type) ?: return false
        if (heldMode != options.mode) {
            return false
        }

        if (!isModeTargetMaterial(options.mode, event.block.type)) {
            return false
        }

        stopForPlayer(playerUuid)

        val connectedTargets = collectConnectedBlocks(
            event.block,
            options.maxChainCount,
            options.mode,
            options.matchLevel,
            options.targetBlocks,
            ignoreBlockStore
        )
        if (connectedTargets.isEmpty()) {
            return false
        }

        showTargetVisuals(player, options, listOf(event.block) + connectedTargets)
        CCContent.instance.server.scheduler.runTask(CCContent.instance, Runnable {
            clearIndicators(playerUuid)
        })

        applyDeferredDurabilityAtStart(player, playerUuid, player.inventory.heldItemSlot, connectedTargets.size)
        scheduleBatchBreak(player, playerUuid, options, connectedTargets, player.inventory.heldItemSlot)
        return true
    }

    private fun previewOnLeftClick(player: Player, block: Block) {
        val playerUuid = player.uniqueId
        if (isInternalBreakInProgress(playerUuid) || activeTasks.containsKey(playerUuid) || shouldSkipPreviewByCooldown(playerUuid)) {
            return
        }

        if (isPlayerPlaced(ignoreBlockStore, block)) {
            clearIndicators(playerUuid)
            return
        }

        val compiledEffects = SkillEffectEngine.getCachedEffects(playerUuid)
        val heldMode = BatchBreakMode.fromTool(player.inventory.itemInMainHand.type) ?: run {
            clearIndicators(playerUuid)
            return
        }

        val debugEffect = getDebugEffect(playerUuid, heldMode)
        val effectAndProfession = if (debugEffect != null) {
            val profession = compiledEffects?.profession ?: when (heldMode) {
                BatchBreakMode.CUT_ALL -> Profession.LUMBERJACK
                BatchBreakMode.MINE_ALL -> Profession.MINER
            }
            debugEffect to profession
        } else {
            if (compiledEffects == null) {
                clearIndicators(playerUuid)
                return
            }

            val entry = SkillEffectEngine.getCachedEffectForBlock(playerUuid, EFFECT_TYPE, block.type.name) ?: run {
                clearIndicators(playerUuid)
                return
            }
            entry.effect to compiledEffects.profession
        }

        val options = resolveRuntimeOptions(effectAndProfession.first, effectAndProfession.second) ?: run {
            clearIndicators(playerUuid)
            return
        }

        if (!canUseMode(playerUuid, options.mode) || !isModeEnabled(playerUuid, options.mode)) {
            clearIndicators(playerUuid)
            return
        }

        if (heldMode != options.mode || !isModeTargetMaterial(options.mode, block.type)) {
            clearIndicators(playerUuid)
            return
        }

        val connectedTargets = collectConnectedBlocks(
            block,
            options.maxChainCount,
            options.mode,
            options.matchLevel,
            options.targetBlocks,
            ignoreBlockStore
        )

        showTargetVisuals(player, options, listOf(block) + connectedTargets)
        schedulePreviewAutoClear(playerUuid, effectAndProfession.first.getIntParam("previewTicks", DEFAULT_PREVIEW_TTL_TICKS.toInt()).toLong())
    }

    private fun schedulePreviewAutoClear(playerUuid: UUID, previewTicks: Long) {
        previewClearTasks.remove(playerUuid)?.cancel()

        val plugin = CCContent.instance
        val ttl = previewTicks.coerceIn(2L, 80L)
        val task = plugin.server.scheduler.runTaskLater(plugin, Runnable {
            clearIndicators(playerUuid)
            previewClearTasks.remove(playerUuid)
        }, ttl)
        previewClearTasks[playerUuid] = task
    }

    private fun resolveRuntimeOptions(skillEffect: SkillEffect, profession: Profession): BatchBreakRuntimeOptions? {
        val mode = resolveMode(skillEffect.getStringParam("mode", "auto"), profession) ?: return null

        val maxChainCount = skillEffect
            .getIntParam("maxChainCount", DEFAULT_MAX_CHAIN_COUNT)
            .coerceIn(1, MAX_ALLOWED_CHAIN_COUNT)

        val requestedDelay = skillEffect
            .getIntParam("delayTicks", DEFAULT_DELAY_TICKS.toInt())
            .coerceIn(MIN_DELAY_TICKS.toInt(), MAX_DELAY_TICKS.toInt())

        val instantBreak = skillEffect.getBooleanParam("instantBreak", false) || requestedDelay <= 0
        val delayTicks = if (instantBreak) 1L else requestedDelay.toLong()

        val autoCollect = skillEffect.getBooleanParam("autoCollect", false)
        val visualizeTargets = skillEffect.getBooleanParam("visualizeTargets", true)
        val matchLevel = MatchLevel.fromRaw(skillEffect.getStringParam("matchLevel", "EXACT"))
        val targetBlocks = skillEffect
            .getStringListParam("targetBlocks")
            .mapNotNull { Material.matchMaterial(it.uppercase()) }
            .toSet()

        return BatchBreakRuntimeOptions(
            mode = mode,
            maxChainCount = maxChainCount,
            delayTicks = delayTicks,
            instantBreak = instantBreak,
            autoCollect = autoCollect,
            visualizeTargets = visualizeTargets,
            matchLevel = matchLevel,
            targetBlocks = targetBlocks
        )
    }

    private fun resolveMode(rawMode: String?, profession: Profession): BatchBreakMode? {
        val parsed = BatchBreakMode.fromRaw(rawMode)
        if (parsed != null) {
            return parsed
        }
        return BatchBreakMode.fromProfession(profession)
    }

    private fun showTargetVisuals(player: Player, options: BatchBreakRuntimeOptions, targets: List<Block>) {
        if (!options.visualizeTargets || targets.isEmpty() || targets.size > MAX_VISUALIZE_BLOCKS) {
            return
        }

        clearIndicators(player.uniqueId)
        val indicators = mutableListOf<BlockDisplay>()
        for (target in targets) {
            val display = target.world.spawn(target.location, BlockDisplay::class.java) { entity ->
                entity.block = target.blockData
                entity.isGlowing = true
            }
            indicators.add(display)
        }

        if (indicators.isNotEmpty()) {
            activeIndicators[player.uniqueId] = indicators
        }
    }

    private fun scheduleBatchBreak(
        player: Player,
        playerUuid: UUID,
        options: BatchBreakRuntimeOptions,
        blocks: List<Block>,
        originHeldSlot: Int
    ) {
        player.sendActionBar(net.kyori.adventure.text.Component.text(
            "${if (options.mode == BatchBreakMode.MINE_ALL) "MineAll" else "CutAll"}: ${blocks.size} blocks"
        ))

        if (options.instantBreak) {
            val plugin = CCContent.instance
            val task = plugin.server.scheduler.runTask(plugin, Runnable {
                internalBreakPlayers.add(playerUuid)
                try {
                    for (target in blocks) {
                        if (!player.isOnline || target.type.isAir || isPlayerPlaced(ignoreBlockStore, target)) {
                            continue
                        }
                        val soundGroup = target.blockSoundGroup
                        val success = breakWithOriginalContext(player, originHeldSlot, target, preserveDurability = true)
                        if (success) {
                            playBreakSound(target, soundGroup)
                            if (options.autoCollect) {
                                collectNearbyLootToPlayer(player, target)
                            }
                        }
                    }
                } finally {
                    internalBreakPlayers.remove(playerUuid)
                    stopForPlayer(playerUuid)
                }
            })
            activeTasks[playerUuid] = task
            return
        }

        val plugin = CCContent.instance
        val iterator = blocks.iterator()
        var finalized = false
        val finalizeBatch = {
            if (!finalized) {
                finalized = true
                stopForPlayer(playerUuid)
            }
        }

        val task = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            if (!player.isOnline) {
                finalizeBatch()
                return@Runnable
            }

            if (!iterator.hasNext()) {
                finalizeBatch()
                return@Runnable
            }

            val target = iterator.next()
            if (target.type.isAir || isPlayerPlaced(ignoreBlockStore, target)) {
                if (!iterator.hasNext()) {
                    finalizeBatch()
                }
                return@Runnable
            }

            val soundGroup = target.blockSoundGroup
            internalBreakPlayers.add(playerUuid)
            try {
                val success = breakWithOriginalContext(player, originHeldSlot, target, preserveDurability = true)
                if (success) {
                    playBreakSound(target, soundGroup)
                    if (options.autoCollect) {
                        collectNearbyLootToPlayer(player, target)
                    }
                }
                if (!iterator.hasNext()) {
                    finalizeBatch()
                }
            } finally {
                internalBreakPlayers.remove(playerUuid)
            }
        }, options.delayTicks, options.delayTicks)

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

    private fun collectNearbyLootToPlayer(player: Player, aroundBlock: Block) {
        val center = aroundBlock.location.clone().add(0.5, 0.5, 0.5)
        val entities = aroundBlock.world.getNearbyEntities(center, 1.5, 1.5, 1.5) { entity ->
            entity is Item || entity is ExperienceOrb
        }
        if (entities.isEmpty()) {
            return
        }

        for (entity in entities) {
            val expOrb = entity as? ExperienceOrb
            if (expOrb != null) {
                player.giveExp(expOrb.experience)
                expOrb.remove()
                continue
            }

            val itemEntity = entity as? Item ?: continue
            val stack = itemEntity.itemStack.clone()
            itemEntity.remove()

            val leftovers = player.inventory.addItem(stack)
            for (leftover in leftovers.values) {
                aroundBlock.world.dropItem(aroundBlock.location.clone().add(0.5, 0.5, 0.5), leftover).apply {
                    pickupDelay = 0
                    owner = player.uniqueId
                }
            }
        }
    }

    private fun playBreakSound(block: Block, soundGroup: org.bukkit.SoundGroup) {
        val location = block.location.clone().add(0.5, 0.5, 0.5)
        block.world.playSound(location, soundGroup.breakSound, soundGroup.volume, soundGroup.pitch)
    }

    private fun applyDeferredDurabilityAtStart(player: Player, playerUuid: UUID, originHeldSlot: Int, plannedChainBreaks: Int) {
        if (plannedChainBreaks <= 0 || originHeldSlot !in 0..8) {
            return
        }

        val item = player.inventory.getItem(originHeldSlot) ?: return
        if (item.type.maxDurability <= 0 || item.type.isAir) {
            return
        }

        val additionalDamage = calculateDeferredDurabilityDamage(playerUuid, item, plannedChainBreaks)
        if (additionalDamage <= 0) {
            return
        }

        val meta = item.itemMeta as? Damageable ?: return
        val maxDurability = item.type.maxDurability.toInt()
        val nextDamage = meta.damage + additionalDamage
        if (nextDamage >= maxDurability) {
            player.inventory.setItem(originHeldSlot, null)
            player.world.playSound(player.location, org.bukkit.Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f)
            return
        }

        meta.damage = nextDamage
        item.itemMeta = meta
    }

    private fun calculateDeferredDurabilityDamage(playerUuid: UUID, toolItem: ItemStack, plannedChainBreaks: Int): Int {
        if (plannedChainBreaks <= 0) {
            return 0
        }

        // バニラの耐久エンチャントレベルを取得
        val vanillaUnbreakingLevel = toolItem.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.UNBREAKING).coerceAtLeast(0)
        
        // スキルによる耐久レベルを取得
        val compiledEffects = SkillEffectEngine.getCachedEffects(playerUuid)
        val skillUnbreakingLevel = if (compiledEffects != null) {
            val entries = compiledEffects.byType["collect.durability_save_chance"] ?: emptyList()
            if (entries.isNotEmpty()) {
                val combinedEffect = SkillEffectEngine.combineEffects(entries, "")
                combinedEffect?.getDoubleParam("unbreaking_level", 0.0)?.toInt() ?: 0
            } else {
                0
            }
        } else {
            0
        }
        
        // 新しい耐久エンチャントレベル（バニラ + スキル）
        val totalUnbreakingLevel = vanillaUnbreakingLevel + skillUnbreakingLevel

        var damage = 0
        repeat(plannedChainBreaks) {
            var consume = true

            if (totalUnbreakingLevel > 0) {
                // バニラの耐久エンチャント計算式: レベルnでn/(n+1)の確率で減少しない
                val chanceToSkipDamage = totalUnbreakingLevel.toDouble() / (totalUnbreakingLevel + 1)
                if (kotlin.random.Random.nextDouble() < chanceToSkipDamage) {
                    consume = false
                }
            }

            if (consume) {
                damage++
            }
        }

        return damage
    }

    override fun calculateStrength(skillEffect: SkillEffect): Double {
        val maxChainCount = skillEffect.getIntParam("maxChainCount", DEFAULT_MAX_CHAIN_COUNT).toDouble()
        val delayTicks = max(1.0, skillEffect.getIntParam("delayTicks", DEFAULT_DELAY_TICKS.toInt()).toDouble())
        return maxChainCount / delayTicks
    }

    override fun supportsProfession(professionId: String): Boolean {
        return professionId in listOf("lumberjack", "miner")
    }

    override fun validateParams(skillEffect: SkillEffect): Boolean {
        val maxChainCount = skillEffect.getIntParam("maxChainCount", DEFAULT_MAX_CHAIN_COUNT)
        val delayTicks = skillEffect.getIntParam("delayTicks", DEFAULT_DELAY_TICKS.toInt())
        return maxChainCount > 0 && delayTicks >= 0
    }
}
