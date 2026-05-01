package jp.awabi2048.cccontent.features.arena.mission

import jp.awabi2048.cccontent.CCContent
import jp.awabi2048.cccontent.features.arena.ArenaAuditLogger
import jp.awabi2048.cccontent.features.arena.ArenaI18n
import jp.awabi2048.cccontent.features.arena.generator.ArenaThemeVariant
import jp.awabi2048.cccontent.features.arena.ArenaManager
import jp.awabi2048.cccontent.features.arena.ArenaStartResult
import jp.awabi2048.cccontent.features.arena.event.ArenaMissionGeneratedEvent
import jp.awabi2048.cccontent.features.arena.event.ArenaMissionStartRequestEvent
import jp.awabi2048.cccontent.features.arena.event.ArenaSessionEndedEvent
import jp.awabi2048.cccontent.features.arena.generator.ArenaTheme
import jp.awabi2048.cccontent.util.OageMessageSender
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.random.Random

class ArenaMissionService(
    private val plugin: JavaPlugin,
    private val arenaManager: ArenaManager
) : Listener {
    private companion object {
        const val MISSION_SLOT_LIMIT = 14
        const val CURRENT_MISSION_FILE_NAME = "current.yml"
        val DEFAULT_STRONG_ENEMY_MOB_TYPE_IDS = setOf(
            "witch_elite",
            "water_spirit",
            "ashen_spirit",
            "frog_big",
            "spider_broodmother",
            "guardian_beam_burst"
        )
        val LICENSE_REQUIREMENTS = mapOf(
            ArenaLicenseTier.BRONZE to ArenaLicenseRequirement(
                targetTier = ArenaLicenseTier.BRONZE,
                requiredMissionClearCount = 15,
                requiredMobKillCount = 0,
                requiredStrongEnemyKillCount = 0
            ),
            ArenaLicenseTier.SILVER to ArenaLicenseRequirement(
                targetTier = ArenaLicenseTier.SILVER,
                requiredMissionClearCount = 40,
                requiredMobKillCount = 500,
                requiredStrongEnemyKillCount = 0
            ),
            ArenaLicenseTier.GOLD to ArenaLicenseRequirement(
                targetTier = ArenaLicenseTier.GOLD,
                requiredMissionClearCount = 100,
                requiredMobKillCount = 5000,
                requiredStrongEnemyKillCount = 500
            )
        )
    }

    private val baseDir = File(plugin.dataFolder, "data/arena")
    private val missionDir = File(baseDir, "missions")
    private val playerDir = File(baseDir, "players")
    private val auditLogger = ArenaAuditLogger(plugin)
    private var currentMissionSet: ArenaMissionSet? = null
    private val playerCache = mutableMapOf<UUID, ArenaPlayerMissionData>()
    private val activeMissions = mutableMapOf<UUID, ArenaActiveMissionRecord>()
    private var strongEnemyMobTypeIds: Set<String> = DEFAULT_STRONG_ENEMY_MOB_TYPE_IDS
    private var generateCount: Int = MISSION_SLOT_LIMIT

    fun initialize() {
        ensureDirectories()
        refreshDefinitions()
    }

    fun shutdown() {
        currentMissionSet = null
        playerCache.clear()
        activeMissions.clear()
    }

    fun getLobbyProgress(playerId: UUID): Pair<Boolean, Boolean> {
        val data = getPlayerData(playerId)
        return data.lobbyVisited to data.lobbyTutorialCompleted
    }

    fun markLobbyVisited(playerId: UUID) {
        val data = getPlayerData(playerId)
        if (data.lobbyVisited) {
            return
        }
        data.setLobbyVisited()
        savePlayerData(playerId)
    }

    fun markLobbyTutorialCompleted(playerId: UUID) {
        val data = getPlayerData(playerId)
        if (data.lobbyVisited && data.lobbyTutorialCompleted) {
            return
        }
        data.setLobbyTutorialCompleted()
        savePlayerData(playerId)
    }

    fun getStatusSnapshot(): ArenaStatusSnapshot {
        val playerFiles = playerDir.listFiles { file -> file.isFile && file.extension.equals("yml", ignoreCase = true) }?.toList().orEmpty()
        val lobbyProgressCount = playerFiles.count { loadPlayerData(it).lobbyVisited }
        val lobbyTutorialCompletedCount = playerFiles.count { loadPlayerData(it).lobbyTutorialCompleted }
        val missionSet = currentMissionSet ?: loadCurrentMissionSetOrNull()?.also { currentMissionSet = it }
        return ArenaStatusSnapshot(
            currentMissionGeneratedAtMillis = missionSet?.generatedAtMillis,
            hasCurrentMissionSet = missionSet != null,
            loadedPlayerRecords = if (playerFiles.isNotEmpty()) playerFiles.size else playerCache.size,
            lobbyProgressCount = lobbyProgressCount,
            lobbyTutorialCompletedCount = lobbyTutorialCompletedCount,
            activeMissionCount = activeMissions.size,
            strongEnemyMobTypeCount = strongEnemyMobTypeIds.size,
            generateCount = generateCount,
            themeCount = arenaManager.getThemeIds().size
        )
    }

    fun updateToday(): Boolean {
        return try {
            refreshDefinitions()
            val missionSet = generateAndSave()
            currentMissionSet = missionSet
            clearAllCurrentMissionCompletions()
            true
        } catch (e: Exception) {
            plugin.logger.warning("[Arena] ミッション更新に失敗しました: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    fun openMenu(player: Player): Boolean {
        return try {
            val missionSet = ensureCurrentMissionSet()
            val holder = ArenaMissionMenuHolder(player.uniqueId)
            val inventory = Bukkit.createInventory(holder, ArenaMissionLayout.MENU_SIZE, ArenaMissionLayout.MENU_TITLE)
            holder.backingInventory = inventory
            renderMenu(player, inventory, missionSet)
            player.openInventory(inventory)
            true
        } catch (e: Exception) {
            plugin.logger.warning("[Arena] アリーナメニューの表示に失敗しました: message=${e.message}")
            e.printStackTrace()
            player.sendMessage(ArenaI18n.text(player, "arena.messages.menu.open_failed_detail", "§cアリーナメニューを開けませんでした: {message}", "message" to (e.message ?: "unknown")))
            false
        }
    }

    fun recordMobKill(playerId: UUID, mobTypeId: String, amount: Int = 1) {
        if (amount <= 0) return
        val playerData = getPlayerData(playerId)
        playerData.addMobKillCount(amount)
        if (isStrongEnemyMobType(mobTypeId)) {
            playerData.addStrongEnemyKillCount(amount)
        }
        evaluateLicensePromotion(playerId, playerData)
        savePlayerData(playerId)
    }

    fun recordBarrierRestart(playerIds: Collection<UUID>, amount: Int = 1) {
        if (amount <= 0) return
        playerIds.forEach { playerId ->
            val playerData = getPlayerData(playerId)
            playerData.addBarrierRestartCount(amount)
            savePlayerData(playerId)
        }
    }

    fun recordOverEnchantSuccess(playerId: UUID, amount: Int = 1) {
        if (amount <= 0) return
        val playerData = getPlayerData(playerId)
        playerData.addOverEnchantSuccessCount(amount)
        savePlayerData(playerId)
    }

    fun getOverEnchantSuccessCount(playerId: UUID): Int {
        return getPlayerData(playerId).totalOverEnchantSuccessCount.coerceAtLeast(0)
    }

    fun getEnchantShardKillCount(playerId: UUID, shardKey: String, mobDefinitionId: String): Int {
        return getPlayerData(playerId).getEnchantShardKillCount(shardKey, mobDefinitionId)
    }

    fun recordEnchantShardAttempts(playerId: UUID, mobDefinitionId: String, attemptCounts: Map<String, Int>, droppedShardKey: String?) {
        if (attemptCounts.isEmpty()) return
        val playerData = getPlayerData(playerId)
        attemptCounts.forEach { (shardKey, attemptCount) ->
            if (shardKey == droppedShardKey) {
                playerData.resetEnchantShardCounter(shardKey, mobDefinitionId)
            } else {
                playerData.recordEnchantShardFailure(shardKey, mobDefinitionId, attemptCount)
            }
        }
        savePlayerData(playerId)
    }

    fun setLicenseTier(playerId: UUID, licenseTier: ArenaLicenseTier): ArenaLicenseTier {
        val playerData = getPlayerData(playerId)
        playerData.licenseTier = licenseTier
        savePlayerData(playerId)
        return playerData.licenseTier
    }

    fun handleMenuClick(player: Player, holder: ArenaMissionMenuHolder, rawSlot: Int): Boolean {
        if (player.uniqueId != holder.ownerId) {
            return false
        }

        if (rawSlot == ArenaMissionLayout.MENU_REFRESH_SLOT) {
            return true
        }

        val missionIndex = ArenaMissionLayout.missionIndexForSlot(rawSlot) ?: return true
        val missionSet = getCurrentMissionSetOrNull() ?: run {
            player.sendMessage(ArenaI18n.text(player, "arena.messages.menu.open_failed", "§cアリーナミッションを開けませんでした"))
            return true
        }
        val mission = missionSet.missions.getOrNull(missionIndex) ?: return true

        playUiClick(player)

        if (isMissionCompleted(player.uniqueId, mission.index)) {
            player.sendMessage(ArenaI18n.text(player, "arena.messages.mission.already_completed", "§eこのミッションはすでに完了済みです"))
            return true
        }

        if (arenaManager.isPlayerInvitedToSession(player.uniqueId)) {
            player.sendMessage(ArenaI18n.text(player, "arena.messages.mission.already_in_mission", "§cすでに進行中のミッションがあります"))
            return true
        }

        if (!validateMissionLicense(player, mission.difficultyStar)) {
            return true
        }

        if (!openMissionConfirmMenu(player, mission.index)) {
            player.sendMessage(ArenaI18n.text(player, "arena.messages.menu.open_failed", "§cアリーナミッションを開けませんでした"))
        }
        return true
    }

    fun handleConfirmClick(player: Player, holder: ArenaMissionConfirmHolder, rawSlot: Int): Boolean {
        if (player.uniqueId != holder.ownerId) {
            return false
        }

        return when (rawSlot) {
            ArenaMissionLayout.CONFIRM_OK_SLOT -> {
                playUiClick(player)
                startMission(player, holder.missionIndex)
                true
            }
            ArenaMissionLayout.CONFIRM_CANCEL_SLOT -> {
                playUiClick(player)
                if (!openMenu(player)) {
                    player.sendMessage(ArenaI18n.text(player, "arena.messages.menu.open_failed", "§cアリーナメニューを開けませんでした"))
                }
                true
            }
            else -> true
        }
    }

    @EventHandler
    fun onArenaSessionEnded(event: ArenaSessionEndedEvent) {
        val activeRecord = activeMissions.remove(event.ownerPlayerId) ?: return
        if (!event.success) {
            return
        }

        val playerData = getPlayerData(event.ownerPlayerId)
        if (playerData.markCompleted(activeRecord.missionIndex)) {
            evaluateLicensePromotion(event.ownerPlayerId, playerData)
            savePlayerData(event.ownerPlayerId)
            Bukkit.getPlayer(event.ownerPlayerId)?.let { player ->
                player.sendMessage(ArenaI18n.text(player, "arena.messages.mission.completed", "§aミッションクリア！"))
            }
            plugin.logger.info("[Arena] ミッション完了を記録しました: player=${event.ownerPlayerId}, mission=${activeRecord.missionIndex}")
        }
    }

    @EventHandler
    fun onMissionMenuDrag(event: InventoryDragEvent) {
        val holder = event.view.topInventory.holder
        if (holder is ArenaMissionMenuHolder || holder is ArenaMissionConfirmHolder) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onMissionMenuClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val holder = event.view.topInventory.holder
        when (holder) {
            is ArenaMissionMenuHolder -> {
                event.isCancelled = true
                handleMenuClick(player, holder, event.rawSlot)
            }
            is ArenaMissionConfirmHolder -> {
                event.isCancelled = true
                handleConfirmClick(player, holder, event.rawSlot)
            }
        }
    }

    private fun startMission(player: Player, missionIndex: Int) {
        val missionSet = getCurrentMissionSetOrNull() ?: run {
            player.sendMessage(ArenaI18n.text(player, "arena.messages.mission.not_found", "§cアリーナミッションが見つかりません"))
            return
        }
        val mission = missionSet.missions.getOrNull(missionIndex) ?: run {
            player.sendMessage(ArenaI18n.text(player, "arena.messages.mission.not_found", "§cアリーナミッションが見つかりません"))
            return
        }

        if (isMissionCompleted(player.uniqueId, mission.index)) {
            player.sendMessage(ArenaI18n.text(player, "arena.messages.mission.already_completed", "§eこのミッションはすでに完了済みです"))
            return
        }

        if (!validateMissionLicense(player, mission.difficultyStar)) {
            return
        }

        val requestEvent = ArenaMissionStartRequestEvent(player, mission)
        Bukkit.getPluginManager().callEvent(requestEvent)
        if (requestEvent.isCancelled) {
            player.sendMessage(ArenaI18n.text(player, "arena.messages.mission.start_cancelled", "§cミッション開始はキャンセルされました"))
            return
        }

        player.closeInventory()

        Bukkit.getScheduler().runTask(plugin, Runnable {
            if (!player.isOnline) {
                return@Runnable
            }

            val inviteMissionTitle = "${missionDisplayName(mission.missionTypeId)}＠${themeDisplayName(player, mission.themeId)}"
            val inviteMissionLore = buildMissionConfirmLore(player, mission)

            // ミッションタイプに基づいてミッション修飾子を決定
            val missionType = ArenaMissionType.fromId(mission.missionTypeId) ?: ArenaMissionType.BARRIER_RESTART
            val missionModifiers = when (missionType) {
                ArenaMissionType.CLEARING -> ArenaMissionModifiers.CLEARING
                else -> ArenaMissionModifiers.NONE
            }

            when (val result = arenaManager.startSession(
                player,
                mission.themeId,
                promoted = mission.promoted,
                enableMultiplayerJoin = true,
                inviteMissionTitle = inviteMissionTitle,
                inviteMissionLore = inviteMissionLore,
                maxParticipants = mission.maxParticipants,
                showSessionStartedMessage = false,
                missionModifiers = missionModifiers,
                missionTypeId = missionType
            )) {
                is ArenaStartResult.Success -> {
                    activeMissions[player.uniqueId] = ArenaActiveMissionRecord(mission.index, mission)
                    Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                        if (player.isOnline) {
                            OageMessageSender.send(
                                player,
                                ArenaI18n.text(
                                    player,
                                    "arena.messages.oage.lift_ready",
                                    "§f「おねがいします！準備ができるまでリフトに乗ってお待ちください～」"
                                ),
                                plugin,
                                sound = Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM,
                                volume = 1.0f,
                                pitch = 1.15f
                            )
                        }
                    }, 40L)
                }
                is ArenaStartResult.Error -> {
                    if (result.messageKey == "arena.messages.command.start_error.stage_build_failed") {
                        player.sendMessage(ArenaI18n.text(player, "arena.messages.mission.stage_build_failed_internal", "§cステージの生成に失敗しました。スタッフに報告してください。(STRUCTURE_ERROR)"))
                    } else if (result.messageKey == "arena.messages.command.start_error.lift_occupied") {
                        OageMessageSender.send(
                            player,
                            ArenaI18n.text(
                                player,
                                "arena.messages.multiplayer.lift_occupied_oage",
                                "§f「リフトが空くまでちょっとまってね！」"
                            ),
                            plugin,
                            sound = Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM,
                            volume = 1.0f,
                            pitch = 1.15f
                        )
                    } else if (result.messageKey == "arena.messages.command.start_error.lift_not_ready") {
                        OageMessageSender.send(
                            player,
                            ArenaI18n.text(
                                player,
                                "arena.messages.multiplayer.lift_not_ready_oage",
                                "§f「リフトの準備が出来ていないみたいです！ちょっとまってね！」"
                            ),
                            plugin,
                            sound = Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM,
                            volume = 1.0f,
                            pitch = 1.15f
                        )
                    } else {
                        player.sendMessage(
                            ArenaI18n.text(
                                player,
                                result.messageKey,
                                result.fallback,
                                *result.placeholders
                            )
                        )
                    }
                }
            }
        })
    }

    private fun validateArenaSources() {
        val themeIds = arenaManager.getThemeIds().map { it.trim() }.filter { it.isNotBlank() }.sorted()
        if (themeIds.isEmpty()) {
            throw IllegalStateException("theme が0件のためアリーナミッションを生成できません")
        }

        if (ArenaMissionType.entries.isEmpty()) {
            throw IllegalStateException("ミッション定義が0件のためアリーナミッションを生成できません")
        }

        if (generateCount > MISSION_SLOT_LIMIT) {
            throw IllegalStateException("アリーナミッション生成数がメニュー枠を超えています: $generateCount > $MISSION_SLOT_LIMIT")
        }

        if (generateCount <= 0) {
            throw IllegalStateException("アリーナミッション生成数が不正です: $generateCount")
        }

    }

    private fun refreshDefinitions() {
        loadGenerationConfig()
        loadStrongEnemyDefinitions()
        validateArenaSources()
    }

    private fun loadGenerationConfig() {
        val coreConfig = currentCoreConfig()
        if (!coreConfig.contains("arena.mission.generate_count")) {
            throw IllegalStateException("arena.mission.generate_count が見つかりません")
        }
        val configured = coreConfig.getInt("arena.mission.generate_count", MISSION_SLOT_LIMIT)
        if (configured <= 0) {
            throw IllegalStateException("arena.mission.generate_count が不正です: $configured")
        }
        if (configured > MISSION_SLOT_LIMIT) {
            throw IllegalStateException("arena.mission.generate_count がメニュー枠を超えています: $configured > $MISSION_SLOT_LIMIT")
        }
        generateCount = configured
    }

    private fun loadStrongEnemyDefinitions() {
        val coreConfig = currentCoreConfig()
        val configured = coreConfig.getStringList("arena.license.strong_enemy_mob_type_ids")
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotBlank() }
            .toSet()
        strongEnemyMobTypeIds = configured.ifEmpty { DEFAULT_STRONG_ENEMY_MOB_TYPE_IDS }
    }

    private fun ensureDirectories() {
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
        if (!missionDir.exists()) {
            missionDir.mkdirs()
        }
        if (!playerDir.exists()) {
            playerDir.mkdirs()
        }
    }

    private fun ensureArenaConfig(resourcePath: String): File {
        val file = File(plugin.dataFolder, resourcePath)
        if (!file.exists()) {
            file.parentFile.mkdirs()
            plugin.saveResource(resourcePath, false)
        }
        return file
    }

    private fun currentCoreConfig(): FileConfiguration {
        return (plugin as? CCContent)?.getCoreConfig()
            ?: throw IllegalStateException("CCContent core config が利用できません")
    }

    private fun generateAndSave(): ArenaMissionSet {
        val missionSet = generateMissionSet()
        saveMissionSet(missionSet)
        auditLogger.logMissionUpdate(missionSet.missions.map { mission ->
            linkedMapOf(
                "index" to mission.index,
                "missionTypeId" to mission.missionTypeId,
                "themeId" to mission.themeId,
                "promoted" to mission.promoted,
                "maxParticipants" to mission.maxParticipants
            )
        })
        Bukkit.getPluginManager().callEvent(ArenaMissionGeneratedEvent(missionSet))
        return missionSet
    }

    private fun generateMissionSet(): ArenaMissionSet {
        val weightedThemes = arenaManager.getThemeIds()
            .mapNotNull { themeId ->
                val theme = arenaManager.getTheme(themeId) ?: return@mapNotNull null
                if (theme.weight <= 0) return@mapNotNull null
                theme
            }
        if (weightedThemes.isEmpty()) {
            throw IllegalStateException("有効なテーマが0件のためミッションを生成できません")
        }
        val random = Random.Default
        val promotionProbability = loadPromotionProbability()

        val missions = (0 until generateCount).map { index ->
            val theme = selectWeightedTheme(weightedThemes, random)
            val themeId = theme.id
            val missionType = ArenaMissionType.BARRIER_RESTART
            val promoted = theme.promotedVariant != null && random.nextDouble() < promotionProbability
            val variant = theme.variant(promoted)

            ArenaMissionEntry(
                index = index,
                missionTypeId = missionType.id,
                themeId = themeId,
                promoted = promoted,
                difficultyStar = variant.difficultyStar,
                difficultyDisplay = variant.display,
                maxParticipants = variant.maxParticipants
            )
        }

        return ArenaMissionSet(
            generatedAtMillis = System.currentTimeMillis(),
            missions = missions
        )
    }

    private fun loadPromotionProbability(): Double {
        val coreConfig = currentCoreConfig()
        val value = coreConfig.getDouble("arena.mission.promotion_probability", -1.0)
        if (value < 0.0 || value > 1.0) {
            throw IllegalStateException("arena.mission.promotion_probability は0.0〜1.0である必要があります: $value")
        }
        return value
    }

    private fun saveMissionSet(missionSet: ArenaMissionSet) {
        val file = File(missionDir, CURRENT_MISSION_FILE_NAME)
        val config = YamlConfiguration()
        config.set("generated_at", missionSet.generatedAtMillis)
        config.set(
            "missions",
            missionSet.missions.map { mission ->
                linkedMapOf(
                    "index" to mission.index,
                    "mission_type_id" to mission.missionTypeId,
                    "theme_id" to mission.themeId,
                    "promoted" to mission.promoted,
                    "max_participants" to mission.maxParticipants
                )
            }
        )
        config.save(file)
    }

    private fun loadCurrentMissionSet(): ArenaMissionSet {
        val file = File(missionDir, CURRENT_MISSION_FILE_NAME)
        if (!file.exists()) {
            throw IllegalStateException("現在のアリーナミッションデータが見つかりません")
        }

        val config = YamlConfiguration.loadConfiguration(file)
        val generatedAt = config.getLong("generated_at", 0L)
        val missionMaps = config.getMapList("missions")
        if (missionMaps.isEmpty()) {
            throw IllegalStateException("現在のアリーナミッションが空です")
        }
        if (missionMaps.size > MISSION_SLOT_LIMIT) {
            throw IllegalStateException("アリーナミッション数がメニュー枠を超えています: ${missionMaps.size}")
        }

        val missions = missionMaps.mapNotNull { map ->
            val index = map["index"]?.toString()?.toIntOrNull() ?: return@mapNotNull null
            val missionTypeId = map["mission_type_id"]?.toString()?.trim().orEmpty()
            if (map.containsKey("difficulty_id")) {
                throw IllegalStateException("旧形式の difficulty_id を含むミッションデータは使用できません。ミッションを再生成してください")
            }
            val themeId = map["theme_id"]?.toString()?.trim().orEmpty()
            val promoted = map["promoted"]?.toString()?.toBooleanStrictOrNull() ?: false
            val maxParticipants = map["max_participants"]?.toString()?.toIntOrNull() ?: 6

            val variant = validateStoredMission(index, missionTypeId, themeId, promoted, maxParticipants)

            ArenaMissionEntry(
                index = index,
                missionTypeId = missionTypeId,
                themeId = themeId,
                promoted = promoted,
                difficultyStar = variant.difficultyStar,
                difficultyDisplay = variant.display,
                maxParticipants = maxParticipants
            )
        }.sortedBy { it.index }

        if (missions.size != missionMaps.size) {
            throw IllegalStateException("現在のアリーナミッションデータの読み込みに失敗しました")
        }

        missions.forEachIndexed { expectedIndex, mission ->
            if (mission.index != expectedIndex) {
                throw IllegalStateException("現在のアリーナミッションのindexが連番ではありません")
            }
        }

        return ArenaMissionSet(generatedAtMillis = generatedAt, missions = missions)
    }

    private fun validateStoredMission(
        index: Int,
        missionTypeId: String,
        themeId: String,
        promoted: Boolean,
        maxParticipants: Int
    ): ArenaThemeVariant {
        if (index < 0) {
            throw IllegalStateException("ミッションindexが不正です: $index")
        }

        if (missionTypeId.isBlank() || ArenaMissionType.fromId(missionTypeId) == null) {
            throw IllegalStateException("mission_type_id が不正です: $missionTypeId")
        }

        if (themeId.isBlank() || !arenaManager.getThemeIds().contains(themeId)) {
            throw IllegalStateException("theme_id が不正です: $themeId")
        }

        val theme = arenaManager.getTheme(themeId)
            ?: throw IllegalStateException("theme_id が不正です: $themeId")
        if (promoted && theme.promotedVariant == null) {
            throw IllegalStateException("promoted が指定されていますが promoted 設定がありません: theme=$themeId")
        }
        val variant = theme.variant(promoted)
        if (maxParticipants !in 1..variant.maxParticipants) {
            throw IllegalStateException("max_participants が不正です: $maxParticipants (theme=$themeId promoted=$promoted max=${variant.maxParticipants})")
        }
        return variant
    }

    private fun selectWeightedTheme(themes: List<ArenaTheme>, random: Random): ArenaTheme {
        val totalWeight = themes.sumOf { it.weight.coerceAtLeast(0) }
        if (totalWeight <= 0) {
            throw IllegalStateException("有効なテーマweightがありません")
        }

        var roll = random.nextInt(totalWeight)
        for (theme in themes) {
            roll -= theme.weight
            if (roll < 0) {
                return theme
            }
        }
        return themes.last()
    }

    private fun ensureCurrentMissionSet(): ArenaMissionSet {
        currentMissionSet?.let { return it }
        val file = File(missionDir, CURRENT_MISSION_FILE_NAME)
        val set = if (file.exists()) {
            try {
                loadCurrentMissionSet()
            } catch (e: Exception) {
                plugin.logger.warning("[Arena] 旧形式または不正な現在ミッションを再生成します: message=${e.message}")
                generateAndSave()
            }
        } else {
            generateAndSave()
        }
        currentMissionSet = set
        return set
    }

    private fun getCurrentMissionSetOrNull(): ArenaMissionSet? {
        currentMissionSet?.let { return it }
        return loadCurrentMissionSetOrNull()?.also { currentMissionSet = it }
    }

    private fun loadCurrentMissionSetOrNull(): ArenaMissionSet? {
        val file = File(missionDir, CURRENT_MISSION_FILE_NAME)
        return try {
            if (file.exists()) {
                loadCurrentMissionSet()
            } else {
                null
            }
        } catch (e: Exception) {
            plugin.logger.warning("[Arena] 現在のアリーナミッションの読み込みに失敗しました: message=${e.message}")
            e.printStackTrace()
            null
        }
    }

    private fun renderMenu(player: Player, inventory: Inventory, missionSet: ArenaMissionSet) {
        fillMenuBackground(inventory)

        val playerData = getPlayerData(player.uniqueId)

        renderMissionSlots(player, inventory, player.uniqueId, missionSet)
        inventory.setItem(ArenaMissionLayout.MENU_PLAYER_SLOT, createPlayerHead(player, playerData))
        inventory.setItem(ArenaMissionLayout.MENU_INFO_SLOT, createInfoItem())
        inventory.setItem(ArenaMissionLayout.MENU_REFRESH_SLOT, createLicenseCardItem(player, playerData))
    }

    private fun renderMissionSlots(player: Player, inventory: Inventory, playerId: UUID, missionSet: ArenaMissionSet) {
        for ((position, slot) in ArenaMissionLayout.MENU_MISSION_SLOTS.withIndex()) {
            val mission = missionSet.missions.getOrNull(position)
            if (mission == null) {
                inventory.setItem(slot, createBackgroundPane(Material.GRAY_STAINED_GLASS_PANE))
                continue
            }

            inventory.setItem(slot, createMissionItem(player, mission, isMissionCompleted(playerId, mission.index)))
        }
    }

    private fun renderConfirmMenu(player: Player, inventory: Inventory, mission: ArenaMissionEntry) {
        fillConfirmBackground(inventory)
        inventory.setItem(ArenaMissionLayout.CONFIRM_OK_SLOT, createActionItem(Material.LIME_WOOL, ArenaI18n.text(player, "arena.ui.confirm.ok_name", "§aOK"), ArenaI18n.stringList(player, "arena.ui.confirm.ok_lore", listOf("§7このミッションを開始します"))))
        inventory.setItem(ArenaMissionLayout.CONFIRM_MISSION_SLOT, createMissionSummaryItem(player, mission))
        inventory.setItem(ArenaMissionLayout.CONFIRM_CANCEL_SLOT, createActionItem(Material.RED_WOOL, ArenaI18n.text(player, "arena.ui.confirm.cancel_name", "§cキャンセル"), ArenaI18n.stringList(player, "arena.ui.confirm.cancel_lore", listOf("§7ミッション開始を取り消します"))))
    }

    private fun openMissionConfirmMenu(player: Player, missionIndex: Int): Boolean {
        val missionSet = getCurrentMissionSetOrNull() ?: return false
        val mission = missionSet.missions.getOrNull(missionIndex) ?: return false

        val holder = ArenaMissionConfirmHolder(player.uniqueId, missionIndex)
        val inventory = Bukkit.createInventory(holder, ArenaMissionLayout.CONFIRM_SIZE, ArenaMissionLayout.CONFIRM_TITLE)
        holder.backingInventory = inventory
        renderConfirmMenu(player, inventory, mission)
        player.openInventory(inventory)
        return true
    }

    private fun fillMenuBackground(inventory: Inventory) {
        for (slot in 0 until 9) {
            inventory.setItem(slot, createBackgroundPane(Material.BLACK_STAINED_GLASS_PANE))
        }
        for (slot in 9 until 45) {
            inventory.setItem(slot, createBackgroundPane(Material.GRAY_STAINED_GLASS_PANE))
        }
        for (slot in 45 until 54) {
            inventory.setItem(slot, createBackgroundPane(Material.BLACK_STAINED_GLASS_PANE))
        }
    }

    private fun fillConfirmBackground(inventory: Inventory) {
        for (slot in 0 until 9) {
            inventory.setItem(slot, createBackgroundPane(Material.BLACK_STAINED_GLASS_PANE))
        }
        for (slot in 9 until 36) {
            inventory.setItem(slot, createBackgroundPane(Material.GRAY_STAINED_GLASS_PANE))
        }
        for (slot in 36 until 45) {
            inventory.setItem(slot, createBackgroundPane(Material.BLACK_STAINED_GLASS_PANE))
        }
    }

    private fun createBackgroundPane(material: Material): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item
        meta.setDisplayName(" ")
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
        item.itemMeta = meta
        return item
    }

    private fun createActionItem(material: Material, name: String, lore: List<String>): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item
        meta.setDisplayName(name)
        if (lore.isNotEmpty()) {
            meta.lore = lore
        }
        item.itemMeta = meta
        return item
    }

    private fun createMissionItem(player: Player, mission: ArenaMissionEntry, isCompleted: Boolean): ItemStack {
        val item = ItemStack(themeIconMaterial(mission.themeId, mission.promoted))
        val meta = item.itemMeta ?: return item
        val title = "${missionDisplayName(mission.missionTypeId)}＠${themeDisplayName(player, mission.themeId)}"
        meta.setDisplayName(
            if (isCompleted) {
                ArenaI18n.text(player, "arena.ui.mission.completed_item_name", "§a§m{mission}", "mission" to title)
            } else {
                ArenaI18n.text(player, "arena.ui.mission.item_name", "§a{mission}", "mission" to title)
            }
        )
        meta.lore = buildMissionLore(player, mission)
        item.itemMeta = meta
        return item
    }

    private fun createMissionSummaryItem(player: Player, mission: ArenaMissionEntry): ItemStack {
        return createActionItem(
            themeIconMaterial(mission.themeId, mission.promoted),
            ArenaI18n.text(player, "arena.ui.mission.item_name", "§a{mission}", "mission" to "${missionDisplayName(mission.missionTypeId)}＠${themeDisplayName(player, mission.themeId)}"),
            buildMissionConfirmLore(player, mission)
        )
    }

    private fun themeIconMaterial(themeId: String, promoted: Boolean): Material {
        return arenaManager.getTheme(themeId)?.config(promoted)?.iconMaterial ?: Material.ROTTEN_FLESH
    }

    private fun createPlayerHead(player: Player, playerData: ArenaPlayerMissionData): ItemStack {
        val item = ItemStack(Material.PLAYER_HEAD)
        val meta = item.itemMeta as? SkullMeta ?: return item
        meta.owningPlayer = player
        meta.setDisplayName(ArenaI18n.text(player, "arena.ui.player.name_format", "§6{player}", "player" to player.name))
        meta.lore = listOf(
            ArenaI18n.text(player, "arena.ui.separator", "§8§m――――――――――――――――――――"),
            ArenaI18n.text(player, "arena.ui.player.mob_kills", "§f❙ §7倒したモブ §e{count} 体", "count" to playerData.totalMobKillCount),
            ArenaI18n.text(player, "arena.ui.separator", "§8§m――――――――――――――――――――"),
            ArenaI18n.text(player, "arena.ui.player.barrier_restarts", "§f❙ §7結界石を再起動した回数 §e{count} 回", "count" to playerData.barrierRestartCount),
            ArenaI18n.text(player, "arena.ui.separator", "§8§m――――――――――――――――――――")
        )
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
        item.itemMeta = meta
        return item
    }

    private fun createInfoItem(): ItemStack {
        return createActionItem(
            Material.BOOK,
            ArenaI18n.text(null, "arena.ui.info_title", "§bInfo"),
            buildInfoLore()
        )
    }

    private fun createLicenseCardItem(player: Player, playerData: ArenaPlayerMissionData): ItemStack {
        val currentTier = playerData.licenseTier
        val nextTier = currentTier.next()
        val lore = mutableListOf<String>()
        lore += ArenaI18n.text(player, "arena.ui.separator", "§8§m――――――――――――――――――――")
        lore += ArenaI18n.text(
            player,
            "arena.ui.license_card.current_license",
            "§f❙ §7現在のライセンス §e{tier}",
            "tier" to licenseTierDisplayName(player, currentTier)
        )
        lore += ArenaI18n.text(
            player,
            "arena.ui.license_card.allowed_difficulty",
            "§f❙ §7入場許可難易度 §c{difficulty} §7まで",
            "difficulty" to difficultyCapDisplay(currentTier.maxDifficultyStar)
        )
        lore += ArenaI18n.text(player, "arena.ui.separator", "§8§m――――――――――――――――――――")
        lore += ArenaI18n.text(player, "arena.ui.license_card.next_header", "§f❙ §7次のライセンスまで")

        if (nextTier == null) {
            lore += ArenaI18n.text(player, "arena.ui.license_card.max_reached", "  §8- §a全ライセンスを解放済み")
        } else {
            val requirement = LICENSE_REQUIREMENTS[nextTier]
                ?: throw IllegalStateException("ライセンス要件が未定義です: tier=${nextTier.id}")
            val rows = buildLicenseRequirementLines(player, playerData, requirement)
            lore += rows
        }

        lore += ArenaI18n.text(player, "arena.ui.separator", "§8§m――――――――――――――――――――")
        return createActionItem(
            Material.NAME_TAG,
            ArenaI18n.text(player, "arena.ui.license_card.title", "§bライセンスカード"),
            lore
        )
    }

    private fun buildMissionLore(player: Player, mission: ArenaMissionEntry): List<String> {
        val missionType = ArenaMissionType.fromId(mission.missionTypeId) ?: ArenaMissionType.BARRIER_RESTART
        val lore = mutableListOf<String>()
        lore += ArenaI18n.text(player, "arena.ui.separator", "§8§m――――――――――――――――――――")
        lore += ArenaI18n.text(player, "arena.ui.mission.mission_title", "§f❙ §7ミッション内容")
        lore += missionGuideHints(missionType, player)
        lore += ""
        lore += ArenaI18n.text(player, "arena.ui.mission.difficulty_inline", "§f❙ §7難易度 §f{difficulty}", "difficulty" to mission.difficultyDisplay)
        lore += ArenaI18n.text(player, "arena.ui.separator", "§8§m――――――――――――――――――――")
        return lore
    }

    private fun buildMissionConfirmLore(player: Player, mission: ArenaMissionEntry): List<String> {
        val missionType = ArenaMissionType.fromId(mission.missionTypeId) ?: ArenaMissionType.BARRIER_RESTART
        val memo = randomMissionMemo(player)
        val lore = mutableListOf<String>()
        lore += ArenaI18n.text(player, "arena.ui.separator", "§8§m――――――――――――――――――――")
        lore += ArenaI18n.text(player, "arena.ui.mission.mission_title", "§f❙ §7ミッション内容")
        lore += missionGuideHints(missionType, player)
        lore += ""
        lore += ArenaI18n.text(player, "arena.ui.mission.difficulty_inline", "§f❙ §7難易度 §f{difficulty}", "difficulty" to mission.difficultyDisplay)
        lore += ArenaI18n.text(player, "arena.ui.mission.memo_inline", "§f❙ §7メモ §f{memo}", "memo" to memo)
        lore += ArenaI18n.text(player, "arena.ui.separator", "§8§m――――――――――――――――――――")
        return lore
    }

    private fun buildInfoLore(): List<String> {
        val lines = ArenaI18n.stringList(null, "arena.ui.info.lines", listOf("§71日1回、§e24時§7にミッションが更新されます"))
        return listOf(
            ArenaI18n.text(null, "arena.ui.separator", "§8§m――――――――――――――――――――"),
            *lines.toTypedArray(),
            ArenaI18n.text(null, "arena.ui.separator", "§8§m――――――――――――――――――――")
        )
    }

    private fun playUiClick(player: Player) {
        player.playSound(player.location, "minecraft:ui.button.click", 0.8f, 2.0f)
    }

    private fun randomMissionMemo(player: Player): String {
        val memos = ArenaI18n.stringList(player, "arena.mission.memo_choices", listOf("§eがんばってください！"))
        return memos.randomOrNull() ?: "§eがんばってください！"
    }

    private fun buildLicenseRequirementLines(
        player: Player,
        playerData: ArenaPlayerMissionData,
        requirement: ArenaLicenseRequirement
    ): List<String> {
        val lines = mutableListOf<String>()

        lines += ArenaI18n.text(
            player,
            "arena.ui.license_card.requirement.mission_clear",
            "  §8- §fミッションを完了する {progress}",
            "progress" to progressText(playerData.totalMissionClearCount, requirement.requiredMissionClearCount)
        )

        if (requirement.requiredMobKillCount > 0) {
            lines += ArenaI18n.text(
                player,
                "arena.ui.license_card.requirement.mob_kill",
                "  §8- §fモンスターを討伐する（累計） {progress}",
                "progress" to progressText(playerData.totalMobKillCount, requirement.requiredMobKillCount)
            )
        }

        if (requirement.requiredStrongEnemyKillCount > 0) {
            lines += ArenaI18n.text(
                player,
                "arena.ui.license_card.requirement.strong_enemy_kill",
                "  §8- §f強敵を討伐する（累計） {progress}",
                "progress" to progressText(playerData.totalStrongEnemyKillCount, requirement.requiredStrongEnemyKillCount)
            )
        }

        return lines
    }

    private fun progressText(current: Int, required: Int): String {
        val cappedCurrent = current.coerceAtLeast(0)
        val color = if (cappedCurrent >= required) "§a" else "§e"
        return "$color$cappedCurrent§7/$required"
    }

    private fun difficultyCapDisplay(starCount: Int): String {
        val effective = starCount.coerceIn(1, 4)
        return "★".repeat(effective).padEnd(3, '☆')
    }

    private fun licenseTierDisplayName(player: Player?, tier: ArenaLicenseTier): String {
        return ArenaI18n.text(player, tier.displayNameKey, tier.name.lowercase(Locale.ROOT))
    }

    private fun missionDisplayName(missionTypeId: String): String {
        val mission = ArenaMissionType.fromId(missionTypeId) ?: return missionTypeId
        return ArenaI18n.text(null, mission.displayNameKey, mission.id)
    }

    private fun missionGuideHints(mission: ArenaMissionType, player: Player? = null): List<String> {
        return ArenaI18n.stringList(player, mission.missionGuideHintsKey, listOf("§7${mission.id}"))
    }

    private fun themeDisplayName(player: Player, themeId: String): String {
        return ArenaI18n.text(player, "arena.theme.$themeId.name", themeId)
    }

    private fun validateMissionLicense(player: Player, missionDifficultyStar: Int): Boolean {
        val playerData = getPlayerData(player.uniqueId)
        if (missionDifficultyStar <= playerData.licenseTier.maxDifficultyStar) {
            return true
        }
        player.sendMessage(
            ArenaI18n.text(
                player,
                "arena.messages.mission.license_insufficient",
                "&cあなたのライセンスでは、このミッションはまだ受けられません"
            )
        )
        return false
    }

    private fun isMissionCompleted(playerId: UUID, missionIndex: Int): Boolean {
        return getPlayerData(playerId).isCompleted(missionIndex)
    }

    private fun getPlayerData(playerId: UUID): ArenaPlayerMissionData {
        playerCache[playerId]?.let { return it }

        val file = File(playerDir, "$playerId.yml")
        val data = if (file.exists()) {
            loadPlayerData(file)
        } else {
            ArenaPlayerMissionData()
        }
        val beforeTier = data.licenseTier
        evaluateLicensePromotion(playerId, data)
        playerCache[playerId] = data
        if (beforeTier != data.licenseTier) {
            savePlayerData(playerId)
        }
        return data
    }

    private fun savePlayerData(playerId: UUID) {
        val data = playerCache[playerId] ?: return
        val file = File(playerDir, "$playerId.yml")
        val config = YamlConfiguration()
        config.set("arena.total_clear_count", data.totalMissionClearCount)
        config.set("arena.total_mob_kill_count", data.totalMobKillCount)
        config.set("arena.total_strong_enemy_kill_count", data.totalStrongEnemyKillCount)
        config.set("arena.total_over_enchant_success_count", data.totalOverEnchantSuccessCount)
        config.set("arena.barrier_restart_count", data.barrierRestartCount)
        config.set("arena.lobby.visited", data.lobbyVisited)
        config.set("arena.lobby.tutorial_completed", data.lobbyTutorialCompleted)
        config.set("arena.license_tier", data.licenseTier.id)
        config.set("arena.completed", data.completedMissionIndices.toList().sorted())
        val shardCounterSection = linkedMapOf<String, Map<String, Int>>()
        data.enchantShardKillCounters.toSortedMap().forEach { (shardKey, countsByMob) ->
            shardCounterSection[shardKey] = countsByMob
                .filterValues { it > 0 }
                .toSortedMap()
        }
        config.set("arena.enchant_shard_kill_counters", shardCounterSection.filterValues { it.isNotEmpty() })
        config.save(file)
    }

    private fun loadPlayerData(file: File): ArenaPlayerMissionData {
        val config = YamlConfiguration.loadConfiguration(file)
        val totalMissionClearCount = config.getInt("arena.total_clear_count", 0).coerceAtLeast(0)
        val totalMobKillCount = config.getInt("arena.total_mob_kill_count", 0).coerceAtLeast(0)
        val totalStrongEnemyKillCount = config.getInt("arena.total_strong_enemy_kill_count", 0).coerceAtLeast(0)
        val totalOverEnchantSuccessCount = config.getInt("arena.total_over_enchant_success_count", 0).coerceAtLeast(0)
        val barrierRestartCount = config.getInt("arena.barrier_restart_count", 0).coerceAtLeast(0)
        val lobbyVisited = config.getBoolean("arena.lobby.visited", false)
        val lobbyTutorialCompleted = config.getBoolean("arena.lobby.tutorial_completed", false)
        val licenseTier = ArenaLicenseTier.fromId(config.getString("arena.license_tier", ArenaLicenseTier.PAPER.id).orEmpty())
            ?: ArenaLicenseTier.PAPER
        val completedMissionIndices = config.getIntegerList("arena.completed")
            .filter { it >= 0 }
            .toMutableSet()
        val enchantShardKillCounters = mutableMapOf<String, MutableMap<String, Int>>()
        val rawCounters = config.getConfigurationSection("arena.enchant_shard_kill_counters")
        rawCounters?.getKeys(false)?.forEach { shardKey ->
            val byMob = rawCounters.getConfigurationSection(shardKey) ?: return@forEach
            val counters = mutableMapOf<String, Int>()
            byMob.getKeys(false).forEach { mobDefinitionId ->
                val count = byMob.getInt(mobDefinitionId, 0).coerceAtLeast(0)
                if (count > 0) {
                    counters[mobDefinitionId] = count
                }
            }
            if (counters.isNotEmpty()) {
                enchantShardKillCounters[shardKey] = counters
            }
        }

        return ArenaPlayerMissionData(
            totalMissionClearCount = totalMissionClearCount,
            totalMobKillCount = totalMobKillCount,
            totalStrongEnemyKillCount = totalStrongEnemyKillCount,
            totalOverEnchantSuccessCount = totalOverEnchantSuccessCount,
            barrierRestartCount = barrierRestartCount,
            lobbyVisited = lobbyVisited,
            lobbyTutorialCompleted = lobbyTutorialCompleted,
            licenseTier = licenseTier,
            completedMissionIndices = completedMissionIndices,
            enchantShardKillCounters = enchantShardKillCounters
        )
    }

    private fun clearAllCurrentMissionCompletions() {
        val playerIds = mutableSetOf<UUID>()
        playerIds += playerCache.keys
        val files = playerDir.listFiles { file -> file.isFile && file.extension.equals("yml", ignoreCase = true) }.orEmpty()
        files.forEach { file ->
            file.nameWithoutExtension.let { runCatching { UUID.fromString(it) }.getOrNull() }?.let { playerIds += it }
        }
        playerIds.forEach { playerId ->
            val data = getPlayerData(playerId)
            if (data.completedMissionIndices.isNotEmpty()) {
                data.clearCurrentMissionCompletions()
                savePlayerData(playerId)
            }
        }
    }

    private fun evaluateLicensePromotion(playerId: UUID, playerData: ArenaPlayerMissionData) {
        while (true) {
            val nextTier = playerData.licenseTier.next() ?: break
            val requirement = LICENSE_REQUIREMENTS[nextTier] ?: break
            if (!meetsLicenseRequirement(playerData, requirement)) {
                break
            }
            playerData.licenseTier = nextTier
            val onlinePlayer = Bukkit.getPlayer(playerId)
            if (onlinePlayer != null && onlinePlayer.isOnline) {
                onlinePlayer.sendMessage(
                    ArenaI18n.text(
                        onlinePlayer,
                        "arena.messages.license.promoted",
                        "&aライセンスが昇格しました！ &f→ {tier}",
                        "tier" to licenseTierDisplayName(onlinePlayer, nextTier)
                    )
                )
            }
        }
    }

    private fun meetsLicenseRequirement(playerData: ArenaPlayerMissionData, requirement: ArenaLicenseRequirement): Boolean {
        if (playerData.totalMissionClearCount < requirement.requiredMissionClearCount) {
            return false
        }
        if (playerData.totalMobKillCount < requirement.requiredMobKillCount) {
            return false
        }
        if (playerData.totalStrongEnemyKillCount < requirement.requiredStrongEnemyKillCount) {
            return false
        }
        return true
    }

    private fun isStrongEnemyMobType(mobTypeId: String): Boolean {
        val normalized = mobTypeId.trim().lowercase(Locale.ROOT)
        if (normalized.isBlank()) return false
        return strongEnemyMobTypeIds.contains(normalized)
    }

}
