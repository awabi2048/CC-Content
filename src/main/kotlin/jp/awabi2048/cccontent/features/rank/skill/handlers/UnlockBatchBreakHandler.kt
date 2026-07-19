package jp.awabi2048.cccontent.features.rank.skill.handlers

import jp.awabi2048.cccontent.CCContent
import jp.awabi2048.cccontent.features.rank.job.BlockPositionCodec
import jp.awabi2048.cccontent.features.rank.job.IgnoreBlockStore
import jp.awabi2048.cccontent.features.rank.profession.Profession
import jp.awabi2048.cccontent.features.rank.profession.profile.LumberjackSkillProfile
import jp.awabi2048.cccontent.features.rank.profession.profile.MinerSkillProfile
import jp.awabi2048.cccontent.features.rank.profession.profile.TypedProfessionProfile
import jp.awabi2048.cccontent.features.rank.skill.ActiveSkillManager
import jp.awabi2048.cccontent.features.rank.skill.ActiveTriggerType
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
        private const val DEFAULT_PREVIEW_TTL_TICKS = 12L
        private const val PREVIEW_COOLDOWN_MILLIS = 120L

        @Volatile
        private var activeInstance: UnlockBatchBreakHandler? = null

        private val activeTasks: MutableMap<UUID, BukkitTask> = ConcurrentHashMap()
        private val activeIndicators: MutableMap<UUID, MutableList<BlockDisplay>> = ConcurrentHashMap()
        private val previewClearTasks: MutableMap<UUID, BukkitTask> = ConcurrentHashMap()
        private val internalBreakPlayers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
        private val lastPreviewAt: MutableMap<UUID, Long> = ConcurrentHashMap()
        private val debugOverrides: MutableMap<UUID, MutableMap<BatchBreakMode, DebugOverride>> = ConcurrentHashMap()
        private val lowestBreakPositions: MutableMap<UUID, Block?> = ConcurrentHashMap()

        fun previewForPlayer(player: Player, block: Block) {
            activeInstance?.previewOnLeftClick(player, block)
        }

        fun applyTypedProfile(event: BlockBreakEvent, profile: TypedProfessionProfile): Boolean =
            activeInstance?.applyTypedProfileInternal(event, profile) ?: false

        data class DebugOverride(
            val delayTicks: Int,
            val maxChainCount: Int,
            val autoCollect: Boolean
        )

        fun isInternalBreakInProgress(playerUuid: UUID): Boolean {
            return internalBreakPlayers.contains(playerUuid)
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
            lowestBreakPositions.remove(playerUuid)
            ReplantHandler.clearProcessed(playerUuid)
        }

        fun getLowestBreakPosition(playerUuid: UUID): Block? {
            return lowestBreakPositions[playerUuid]
        }

        fun stopAll() {
            for ((playerUuid, task) in activeTasks.toMap()) {
                task.cancel()
                activeTasks.remove(playerUuid)
                clearIndicators(playerUuid)
                previewClearTasks.remove(playerUuid)?.cancel()
                internalBreakPlayers.remove(playerUuid)
                lowestBreakPositions.remove(playerUuid)
                ReplantHandler.clearProcessed(playerUuid)
            }
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
            ignoreBlockStore: IgnoreBlockStore,
            optimizedTraversal: Boolean = false
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

                val neighbors = OFFSETS_26
                    .map { (dx, dy, dz) -> world.getBlockAt(current.x + dx, current.y + dy, current.z + dz) }
                    .let { blocks ->
                        if (optimizedTraversal) blocks.sortedBy { if (it.type == baseType) 0 else 1 } else blocks
                    }
                for (next in neighbors) {
                    val packed = BlockPositionCodec.pack(next.x, next.y, next.z)
                    if (!visited.add(packed)) {
                        continue
                    }

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
                BatchBreakMode.CUT_ALL -> {
                    val name = material.name
                    name.endsWith("_LOG") || name.endsWith("_STEM") || name.endsWith("_HYPHAE")
                }
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
        val targetBlocks: Set<Material>,
        val leafCleanup: Boolean = false,
        val autoReplant: Boolean = false,
        val optimizedTraversal: Boolean = false
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

    override fun isActiveSkill(): Boolean = true

    override fun getTriggerType(): ActiveTriggerType = ActiveTriggerType.AUTO_BREAK

    override fun getDefaultEvaluationMode(): EvaluationMode = EvaluationMode.RUNTIME

    override fun applyEffect(context: EffectContext): Boolean {
        val event = context.getEvent<BlockBreakEvent>() ?: return false
        val player = event.player
        val playerUuid = player.uniqueId
 
        if (isInternalBreakInProgress(playerUuid)) {
            return false
        }
 
        val options = resolveRuntimeOptions(context.skillEffect, context.profession) ?: return false
 
        // AUTO_BREAK系は常時発動対象として扱う（手動発動スキルとの競合回避）
 
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

        val allBlocks = listOf(event.block) + connectedTargets
        val lowestBlock = allBlocks.minByOrNull { it.y }
        if (lowestBlock != null && options.mode == BatchBreakMode.CUT_ALL) {
            lowestBreakPositions[playerUuid] = lowestBlock
        }

        scheduleBatchBreak(
            player, playerUuid, options, event.block, connectedTargets, player.inventory.heldItemSlot
        )
        return true
    }

    private fun applyTypedProfileInternal(
        event: BlockBreakEvent,
        profile: TypedProfessionProfile
    ): Boolean {
        val options = resolveTypedOptions(profile) ?: return false
        return startBatchBreak(event, options)
    }

    private fun resolveTypedOptions(profile: TypedProfessionProfile): BatchBreakRuntimeOptions? =
        when (profile) {
            is MinerSkillProfile -> {
                if (!profile.batchProcessingEnabled) return null
                BatchBreakRuntimeOptions(
                    mode = BatchBreakMode.MINE_ALL,
                    maxChainCount = (profile.maximumBatchSize - 1).coerceAtLeast(1),
                    delayTicks = DEFAULT_DELAY_TICKS,
                    instantBreak = false,
                    autoCollect = profile.automaticCollectionEnabled,
                    visualizeTargets = true,
                    matchLevel = MatchLevel.FAMILY,
                    targetBlocks = emptySet(),
                    optimizedTraversal = profile.optimizedSearchEnabled
                )
            }
            is LumberjackSkillProfile -> {
                if (!profile.batchProcessingEnabled) return null
                BatchBreakRuntimeOptions(
                    mode = BatchBreakMode.CUT_ALL,
                    maxChainCount = (profile.maximumBatchSize - 1).coerceAtLeast(1),
                    delayTicks = DEFAULT_DELAY_TICKS,
                    instantBreak = false,
                    autoCollect = false,
                    visualizeTargets = true,
                    matchLevel = MatchLevel.EXACT,
                    targetBlocks = emptySet(),
                    leafCleanup = profile.leafCleanupEnabled,
                    autoReplant = profile.automaticReplantEnabled
                )
            }
            else -> null
        }

    private fun startBatchBreak(event: BlockBreakEvent, options: BatchBreakRuntimeOptions): Boolean {
        val player = event.player
        val playerUuid = player.uniqueId
        if (isInternalBreakInProgress(playerUuid) ||
            BatchBreakMode.fromTool(player.inventory.itemInMainHand.type) != options.mode ||
            !isModeTargetMaterial(options.mode, event.block.type)) {
            return false
        }

        stopForPlayer(playerUuid)
        val connectedTargets = collectConnectedBlocks(
            event.block,
            options.maxChainCount,
            options.mode,
            options.matchLevel,
            options.targetBlocks,
            ignoreBlockStore,
            options.optimizedTraversal
        )
        if (connectedTargets.isEmpty()) return false

        showTargetVisuals(player, options, listOf(event.block) + connectedTargets)
        CCContent.instance.server.scheduler.runTask(CCContent.instance, Runnable {
            clearIndicators(playerUuid)
        })

        if (options.mode == BatchBreakMode.CUT_ALL) {
            lowestBreakPositions[playerUuid] =
                (listOf(event.block) + connectedTargets).minByOrNull { it.y }
        }
        scheduleBatchBreak(
            player, playerUuid, options, event.block, connectedTargets, player.inventory.heldItemSlot
        )
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

        val typedOptions = CCContent.rankManager.getTypedProfessionProfile(playerUuid)
            ?.let(::resolveTypedOptions)
        if (typedOptions != null) {
            if (heldMode != typedOptions.mode || !isModeTargetMaterial(typedOptions.mode, block.type)) {
                clearIndicators(playerUuid)
                return
            }
            val connectedTargets = collectConnectedBlocks(
                block,
                typedOptions.maxChainCount,
                typedOptions.mode,
                typedOptions.matchLevel,
                typedOptions.targetBlocks,
                ignoreBlockStore,
                typedOptions.optimizedTraversal
            )
            showTargetVisuals(player, typedOptions, listOf(block) + connectedTargets)
            schedulePreviewAutoClear(playerUuid, DEFAULT_PREVIEW_TTL_TICKS)
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

        if (!canUseMode(playerUuid, options.mode)) {
            clearIndicators(playerUuid)
            return
        }

        // skillActivationStates での発動確認（該当effect.typeを持つスキルがOFFの場合はプレビュー非表示）
        val profession = CCContent.rankManager.getPlayerProfession(playerUuid)
        if (profession != null) {
            val activeSkillId = profession.activeSkillId
            if (activeSkillId != null && !profession.isSkillActivationEnabled(activeSkillId)) {
                clearIndicators(playerUuid)
                return
            }
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
        origin: Block,
        blocks: List<Block>,
        originHeldSlot: Int
    ) {
        val originalMaterial = origin.type
        val replantPosition = if (options.mode == BatchBreakMode.CUT_ALL) {
            (listOf(origin) + blocks).minByOrNull { it.y }
        } else {
            null
        }
        val leaves = if (options.leafCleanup) collectNearbyLeaves(origin, blocks) else emptyList()
        val finishLumberjackOptions = {
            if (options.leafCleanup) {
                for (leaf in leaves) {
                    if (!leaf.type.name.endsWith("_LEAVES") || isPlayerPlaced(ignoreBlockStore, leaf)) continue
                    breakWithOriginalContext(player, originHeldSlot, leaf, preserveDurability = true)
                }
            }
            if (options.autoReplant && replantPosition != null) {
                ReplantHandler.replantBatch(player, replantPosition, originalMaterial)
            }
        }

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
                    finishLumberjackOptions()
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
                finishLumberjackOptions()
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
                    finishLumberjackOptions()
                    finalizeBatch()
                }
            } finally {
                internalBreakPlayers.remove(playerUuid)
            }
        }, options.delayTicks, options.delayTicks)

        activeTasks[playerUuid] = task
    }

    private fun collectNearbyLeaves(origin: Block, trunks: List<Block>): List<Block> {
        val queue = ArrayDeque<Block>()
        val visited = mutableSetOf<Long>()
        val result = mutableListOf<Block>()
        (listOf(origin) + trunks).forEach { trunk ->
            for ((dx, dy, dz) in OFFSETS_26) {
                queue.add(trunk.getRelative(dx, dy, dz))
            }
        }
        while (queue.isNotEmpty() && result.size < 256) {
            val block = queue.removeFirst()
            val packed = BlockPositionCodec.pack(block.x, block.y, block.z)
            if (!visited.add(packed) ||
                kotlin.math.abs(block.x - origin.x) > 8 ||
                block.y !in (origin.y - 2)..(origin.y + 18) ||
                kotlin.math.abs(block.z - origin.z) > 8 ||
                !block.type.name.endsWith("_LEAVES")) {
                continue
            }
            result.add(block)
            for ((dx, dy, dz) in OFFSETS_26) {
                queue.add(block.getRelative(dx, dy, dz))
            }
        }
        return result
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
