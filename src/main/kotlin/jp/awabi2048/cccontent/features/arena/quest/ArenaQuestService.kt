package jp.awabi2048.cccontent.features.arena.quest

import jp.awabi2048.cccontent.CCContent
import jp.awabi2048.cccontent.features.arena.ArenaI18n
import jp.awabi2048.cccontent.features.arena.ArenaManager
import jp.awabi2048.cccontent.features.arena.ArenaStartResult
import jp.awabi2048.cccontent.features.arena.event.ArenaDailyQuestGeneratedEvent
import jp.awabi2048.cccontent.features.arena.event.ArenaQuestStartRequestEvent
import jp.awabi2048.cccontent.features.arena.event.ArenaSessionEndedEvent
import jp.awabi2048.cccontent.util.OageMessageSender
import org.bukkit.Bukkit
import org.bukkit.Material
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
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.random.Random

class ArenaQuestService(
    private val plugin: JavaPlugin,
    private val arenaManager: ArenaManager
) : Listener {
    private companion object {
        val TOKYO_ZONE: ZoneId = ZoneId.of("Asia/Tokyo")
        val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
        const val QUEST_SLOT_LIMIT = 14
    }

    private val baseDir = File(plugin.dataFolder, "data/arena")
    private val questDir = File(baseDir, "quests")
    private val playerDir = File(baseDir, "players")
    private val questCache = mutableMapOf<String, ArenaDailyQuestSet>()
    private val playerCache = mutableMapOf<UUID, ArenaPlayerQuestData>()
    private val activeQuests = mutableMapOf<UUID, ArenaActiveQuestRecord>()
    private var difficultyDefinitions: List<ArenaDifficultyDefinition> = emptyList()
    private var difficultyDisplayMap: Map<String, ArenaDifficultyDefinition> = emptyMap()
    private var generateCount: Int = QUEST_SLOT_LIMIT

    fun initialize() {
        ensureDirectories()
        refreshDefinitions()
    }

    fun shutdown() {
        questCache.clear()
        playerCache.clear()
        activeQuests.clear()
    }

    fun updateToday(): Boolean {
        val dateKey = currentDateKey()
        return try {
            refreshDefinitions()
            val questSet = generateAndSave(dateKey)
            questCache[dateKey] = questSet
            true
        } catch (e: Exception) {
            plugin.logger.warning("[Arena] 日付更新に失敗しました: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    fun openMenu(player: Player): Boolean {
        return openMenu(player, currentDateKey())
    }

    fun recordMobKill(playerId: UUID, amount: Int = 1) {
        if (amount <= 0) return
        val playerData = getPlayerData(playerId)
        playerData.addMobKillCount(amount)
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

    private fun openMenu(player: Player, dateKey: String): Boolean {
        return try {
            val questSet = ensureQuestSet(dateKey)
            val holder = ArenaQuestMenuHolder(player.uniqueId, dateKey)
            val inventory = Bukkit.createInventory(holder, ArenaQuestLayout.MENU_SIZE, ArenaQuestLayout.MENU_TITLE)
            holder.backingInventory = inventory
            renderMenu(player, inventory, questSet)
            player.openInventory(inventory)
            true
        } catch (e: Exception) {
            plugin.logger.warning("[Arena] アリーナメニューの表示に失敗しました: date=$dateKey message=${e.message}")
            e.printStackTrace()
            player.sendMessage(ArenaI18n.text(player, "arena.messages.menu.open_failed_detail", "§cアリーナメニューを開けませんでした: {message}", "message" to (e.message ?: "unknown")))
            false
        }
    }

    fun handleMenuClick(player: Player, holder: ArenaQuestMenuHolder, rawSlot: Int): Boolean {
        if (player.uniqueId != holder.ownerId) {
            return false
        }

        if (rawSlot == ArenaQuestLayout.MENU_REFRESH_SLOT) {
            return true
        }

        val questIndex = ArenaQuestLayout.questIndexForSlot(rawSlot) ?: return true
        val questSet = getQuestSet(holder.dateKey) ?: run {
            player.sendMessage(ArenaI18n.text(player, "arena.messages.menu.open_failed", "§cアリーナクエストを開けませんでした"))
            return true
        }
        val quest = questSet.quests.getOrNull(questIndex) ?: return true

        playUiClick(player)

        if (isQuestCompleted(player.uniqueId, holder.dateKey, quest.index)) {
            player.sendMessage(ArenaI18n.text(player, "arena.messages.quest.already_completed", "§eこのクエストはすでに完了済みです"))
            return true
        }

        if (!openConfirmMenu(player, holder.dateKey, quest.index)) {
            player.sendMessage(ArenaI18n.text(player, "arena.messages.menu.open_failed", "§cアリーナクエストを開けませんでした"))
        }
        return true
    }

    fun handleConfirmClick(player: Player, holder: ArenaQuestConfirmHolder, rawSlot: Int): Boolean {
        if (player.uniqueId != holder.ownerId) {
            return false
        }

        return when (rawSlot) {
            ArenaQuestLayout.CONFIRM_OK_SLOT -> {
                playUiClick(player)
                startQuest(player, holder.dateKey, holder.questIndex)
                true
            }
            ArenaQuestLayout.CONFIRM_CANCEL_SLOT -> {
                playUiClick(player)
                if (!openMenu(player, holder.dateKey)) {
                    player.sendMessage(ArenaI18n.text(player, "arena.messages.menu.open_failed", "§cアリーナメニューを開けませんでした"))
                }
                true
            }
            else -> true
        }
    }

    @EventHandler
    fun onArenaSessionEnded(event: ArenaSessionEndedEvent) {
        val activeRecord = activeQuests.remove(event.ownerPlayerId) ?: return
        if (!event.success) {
            return
        }

        val playerData = getPlayerData(event.ownerPlayerId)
        if (playerData.markCompleted(activeRecord.dateKey, activeRecord.questIndex)) {
            savePlayerData(event.ownerPlayerId)
            plugin.logger.info("[Arena] クエスト完了を記録しました: player=${event.ownerPlayerId}, date=${activeRecord.dateKey}, quest=${activeRecord.questIndex}")
        }
    }

    @EventHandler
    fun onQuestMenuDrag(event: InventoryDragEvent) {
        val holder = event.view.topInventory.holder
        if (holder is ArenaQuestMenuHolder || holder is ArenaQuestConfirmHolder) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onQuestMenuClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val holder = event.view.topInventory.holder
        when (holder) {
            is ArenaQuestMenuHolder -> {
                event.isCancelled = true
                handleMenuClick(player, holder, event.rawSlot)
            }
            is ArenaQuestConfirmHolder -> {
                event.isCancelled = true
                handleConfirmClick(player, holder, event.rawSlot)
            }
        }
    }

    private fun startQuest(player: Player, dateKey: String, questIndex: Int) {
        val questSet = getQuestSet(dateKey) ?: run {
            player.sendMessage(ArenaI18n.text(player, "arena.messages.quest.not_found", "§cアリーナクエストが見つかりません"))
            return
        }
        val quest = questSet.quests.getOrNull(questIndex) ?: run {
            player.sendMessage(ArenaI18n.text(player, "arena.messages.quest.not_found", "§cアリーナクエストが見つかりません"))
            return
        }

        if (isQuestCompleted(player.uniqueId, dateKey, quest.index)) {
            player.sendMessage(ArenaI18n.text(player, "arena.messages.quest.already_completed", "§eこのクエストはすでに完了済みです"))
            return
        }

        val requestEvent = ArenaQuestStartRequestEvent(player, dateKey, quest)
        Bukkit.getPluginManager().callEvent(requestEvent)
        if (requestEvent.isCancelled) {
            player.sendMessage(ArenaI18n.text(player, "arena.messages.quest.start_cancelled", "§cクエスト開始はキャンセルされました"))
            return
        }

        player.closeInventory()

        Bukkit.getScheduler().runTask(plugin, Runnable {
            if (!player.isOnline) {
                return@Runnable
            }

            val inviteQuestTitle = "${missionDisplayName(quest.missionTypeId)}＠${themeDisplayName(player, quest.themeId)}"
            val inviteQuestLore = buildQuestConfirmLore(player, quest)

            when (val result = arenaManager.startSession(
                player,
                quest.difficultyId,
                quest.themeId,
                difficultyScore = quest.difficultyScore,
                enableMultiplayerJoin = true,
                inviteQuestTitle = inviteQuestTitle,
                inviteQuestLore = inviteQuestLore,
                maxParticipants = quest.maxParticipants,
                showSessionStartedMessage = false
            )) {
                is ArenaStartResult.Success -> {
                    activeQuests[player.uniqueId] = ArenaActiveQuestRecord(dateKey, quest.index, quest)
                    Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                        if (player.isOnline) {
                            OageMessageSender.send(
                                player,
                                ArenaI18n.text(
                                    player,
                                    "arena.messages.oage.lift_ready",
                                    "§f「リフトの準備ができました！搭乗してお待ちください～」"
                                ),
                                plugin
                            )
                        }
                    }, 40L)
                }
                is ArenaStartResult.Error -> {
                    if (result.messageKey == "arena.messages.command.start_error.stage_build_failed") {
                        player.sendMessage(ArenaI18n.text(player, "arena.messages.quest.stage_build_failed_internal", "§cステージの生成に失敗しました。スタッフに報告してください。(STRUCTURE_ERROR)"))
                    } else if (result.messageKey == "arena.messages.command.start_error.lift_occupied") {
                        OageMessageSender.send(
                            player,
                            ArenaI18n.text(
                                player,
                                "arena.messages.multiplayer.lift_occupied_oage",
                                "§f「リフトが空くまでちょっとまってね！」"
                            ),
                            plugin
                        )
                    } else if (result.messageKey == "arena.messages.command.start_error.lift_not_ready") {
                        OageMessageSender.send(
                            player,
                            ArenaI18n.text(
                                player,
                                "arena.messages.multiplayer.lift_not_ready_oage",
                                "§f「リフトの準備が出来ていないみたいです！ちょっとまってね！」"
                            ),
                            plugin
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
            throw IllegalStateException("theme が0件のためアリーナクエストを生成できません")
        }

        if (ArenaQuestMissionType.entries.isEmpty()) {
            throw IllegalStateException("ミッション定義が0件のためアリーナクエストを生成できません")
        }

        if (generateCount > QUEST_SLOT_LIMIT) {
            throw IllegalStateException("アリーナクエスト生成数がメニュー枠を超えています: $generateCount > $QUEST_SLOT_LIMIT")
        }

        if (generateCount <= 0) {
            throw IllegalStateException("アリーナクエスト生成数が不正です: $generateCount")
        }

    }

    private fun refreshDefinitions() {
        loadGenerationConfig()
        loadDifficultyDefinitions()
        validateArenaSources()
    }

    private fun loadGenerationConfig() {
        val coreConfig = currentCoreConfig()
        if (!coreConfig.contains("arena.quest.generate_count")) {
            throw IllegalStateException("arena.quest.generate_count が見つかりません")
        }
        val configured = coreConfig.getInt("arena.quest.generate_count", QUEST_SLOT_LIMIT)
        if (configured <= 0) {
            throw IllegalStateException("arena.quest.generate_count が不正です: $configured")
        }
        if (configured > QUEST_SLOT_LIMIT) {
            throw IllegalStateException("arena.quest.generate_count がメニュー枠を超えています: $configured > $QUEST_SLOT_LIMIT")
        }
        generateCount = configured
    }

    private fun loadDifficultyDefinitions() {
        val difficultyFile = ensureArenaConfig("config/arena/difficulty.yml")
        val config = YamlConfiguration.loadConfiguration(difficultyFile)
        val parsed = mutableListOf<ArenaDifficultyDefinition>()

        for (difficultyId in config.getKeys(false)) {
            val section = config.getConfigurationSection(difficultyId) ?: continue
            val range = parseDifficultyRange(section.getString("difficulty_range"))
                ?: throw IllegalStateException("difficulty_range が不正です: $difficultyId")
            val display = section.getString("display", difficultyId) ?: difficultyId
            parsed.add(
                ArenaDifficultyDefinition(
                    id = difficultyId,
                    difficultyRange = range,
                    display = display
                )
            )
        }

        if (parsed.isEmpty()) {
            throw IllegalStateException("difficulty.yml が空のためアリーナクエストを生成できません")
        }

        val sortedByRange = parsed.sortedWith(
            compareBy<ArenaDifficultyDefinition> { it.difficultyRange.start }
                .thenBy { it.difficultyRange.endInclusive }
                .thenBy { it.id }
        )

        for (index in 1 until sortedByRange.size) {
            val previous = sortedByRange[index - 1]
            val current = sortedByRange[index]
            if (current.difficultyRange.start <= previous.difficultyRange.endInclusive) {
                throw IllegalStateException(
                    "difficulty_range が重複しています: ${previous.id}=${previous.difficultyRange} / ${current.id}=${current.difficultyRange}"
                )
            }
        }

        difficultyDefinitions = parsed.sortedBy { it.id }
        difficultyDisplayMap = difficultyDefinitions.associateBy { it.id }
    }

    private fun ensureDirectories() {
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
        if (!questDir.exists()) {
            questDir.mkdirs()
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

    private fun generateAndSave(dateKey: String): ArenaDailyQuestSet {
        val questSet = generateQuestSet(dateKey)
        saveQuestSet(questSet)
        Bukkit.getPluginManager().callEvent(ArenaDailyQuestGeneratedEvent(dateKey, questSet))
        return questSet
    }

    private fun generateQuestSet(dateKey: String): ArenaDailyQuestSet {
        val themeIds = arenaManager.getThemeIds().map { it.trim() }.filter { it.isNotBlank() }.sorted()
        val random = Random(dateKey.toEpochSeed())

        val quests = (0 until generateCount).map { index ->
            val themeId = themeIds.random(random)
            val missionType = ArenaQuestMissionType.BARRIER_RESTART
            val difficulty = difficultyDefinitions.random(random)
            val score = randomDifficultyScore(difficulty, random)
            val resolvedDifficultyId = resolveDifficultyId(score) ?: difficulty.id

            ArenaDailyQuestEntry(
                index = index,
                missionTypeId = missionType.id,
                difficultyScore = score,
                difficultyId = resolvedDifficultyId,
                themeId = themeId,
                maxParticipants = 6
            )
        }

        return ArenaDailyQuestSet(
            dateKey = dateKey,
            generatedAtMillis = dateKey.toEpochSeed().toLong(),
            quests = quests
        )
    }

    private fun randomDifficultyScore(definition: ArenaDifficultyDefinition, random: Random): Double {
        val start = definition.difficultyRange.start
        val end = definition.difficultyRange.endInclusive
        if (end <= start) {
            return start
        }
        return start + (end - start) * random.nextDouble()
    }

    private fun resolveDifficultyId(score: Double): String? {
        return difficultyDefinitions.firstOrNull { it.difficultyRange.contains(score) }?.id
    }

    private fun saveQuestSet(questSet: ArenaDailyQuestSet) {
        val file = File(questDir, "${questSet.dateKey}.yml")
        val config = YamlConfiguration()
        config.set("date", questSet.dateKey)
        config.set("generated_at", questSet.generatedAtMillis)
        config.set(
            "quests",
            questSet.quests.map { quest ->
                linkedMapOf(
                    "index" to quest.index,
                    "mission_type_id" to quest.missionTypeId,
                    "difficulty_score" to quest.difficultyScore,
                    "difficulty_id" to quest.difficultyId,
                    "theme_id" to quest.themeId,
                    "max_participants" to quest.maxParticipants
                )
            }
        )
        config.save(file)
    }

    private fun loadQuestSet(dateKey: String): ArenaDailyQuestSet {
        val file = File(questDir, "$dateKey.yml")
        if (!file.exists()) {
            throw IllegalStateException("アリーナクエストデータが見つかりません: $dateKey")
        }

        val config = YamlConfiguration.loadConfiguration(file)
        val storedDate = config.getString("date")
            ?: throw IllegalStateException("アリーナクエストのdateがありません: $dateKey")
        if (storedDate != dateKey) {
            throw IllegalStateException("アリーナクエストのdateが不正です: expected=$dateKey actual=$storedDate")
        }

        val generatedAt = config.getLong("generated_at", 0L)
        val questMaps = config.getMapList("quests")
        if (questMaps.isEmpty()) {
            throw IllegalStateException("アリーナクエストが空です: $dateKey")
        }
        if (questMaps.size > QUEST_SLOT_LIMIT) {
            throw IllegalStateException("アリーナクエスト数がメニュー枠を超えています: ${questMaps.size}")
        }

        val quests = questMaps.mapNotNull { map ->
            val index = map["index"]?.toString()?.toIntOrNull() ?: return@mapNotNull null
            val missionTypeId = map["mission_type_id"]?.toString()?.trim().orEmpty()
            val difficultyScore = map["difficulty_score"]?.toString()?.toDoubleOrNull() ?: return@mapNotNull null
            val difficultyId = map["difficulty_id"]?.toString()?.trim().orEmpty()
            val themeId = map["theme_id"]?.toString()?.trim().orEmpty()
            val maxParticipants = map["max_participants"]?.toString()?.toIntOrNull() ?: 6

            validateStoredQuest(index, missionTypeId, difficultyScore, difficultyId, themeId, maxParticipants)

            ArenaDailyQuestEntry(
                index = index,
                missionTypeId = missionTypeId,
                difficultyScore = difficultyScore,
                difficultyId = difficultyId,
                themeId = themeId,
                maxParticipants = maxParticipants
            )
        }.sortedBy { it.index }

        if (quests.size != questMaps.size) {
            throw IllegalStateException("アリーナクエストデータの読み込みに失敗しました: $dateKey")
        }

        quests.forEachIndexed { expectedIndex, quest ->
            if (quest.index != expectedIndex) {
                throw IllegalStateException("アリーナクエストのindexが連番ではありません: $dateKey")
            }
        }

        return ArenaDailyQuestSet(dateKey = dateKey, generatedAtMillis = generatedAt, quests = quests)
    }

    private fun validateStoredQuest(
        index: Int,
        missionTypeId: String,
        difficultyScore: Double,
        difficultyId: String,
        themeId: String,
        maxParticipants: Int
    ) {
        if (index < 0) {
            throw IllegalStateException("クエストindexが不正です: $index")
        }

        if (missionTypeId.isBlank() || ArenaQuestMissionType.fromId(missionTypeId) == null) {
            throw IllegalStateException("mission_type_id が不正です: $missionTypeId")
        }

        if (themeId.isBlank() || !arenaManager.getThemeIds().contains(themeId)) {
            throw IllegalStateException("theme_id が不正です: $themeId")
        }

        val difficulty = difficultyDefinitions.firstOrNull { it.id == difficultyId }
            ?: throw IllegalStateException("difficulty_id が不正です: $difficultyId")

        if (!difficulty.difficultyRange.contains(difficultyScore)) {
            throw IllegalStateException("difficulty_score が範囲外です: id=$difficultyId score=$difficultyScore range=${difficulty.difficultyRange}")
        }

        if (maxParticipants !in 1..6) {
            throw IllegalStateException("max_participants が不正です: $maxParticipants")
        }
    }

    private fun ensureQuestSet(dateKey: String): ArenaDailyQuestSet {
        questCache[dateKey]?.let { return it }
        val file = File(questDir, "$dateKey.yml")
        val set = if (file.exists()) {
            try {
                loadQuestSet(dateKey)
            } catch (e: Exception) {
                plugin.logger.warning("[Arena] 旧形式または不正なクエストを再生成します: date=$dateKey message=${e.message}")
                generateAndSave(dateKey)
            }
        } else {
            generateAndSave(dateKey)
        }
        questCache[dateKey] = set
        return set
    }

    private fun getQuestSet(dateKey: String): ArenaDailyQuestSet? {
        questCache[dateKey]?.let { return it }
        val file = File(questDir, "$dateKey.yml")
        return try {
            if (file.exists()) {
                loadQuestSet(dateKey).also { questCache[dateKey] = it }
            } else {
                null
            }
        } catch (e: Exception) {
            plugin.logger.warning("[Arena] アリーナクエストの読み込みに失敗しました: date=$dateKey message=${e.message}")
            e.printStackTrace()
            null
        }
    }

    private fun renderMenu(player: Player, inventory: Inventory, questSet: ArenaDailyQuestSet) {
        fillMenuBackground(inventory)

        val playerData = getPlayerData(player.uniqueId)

        renderQuestSlots(player, inventory, player.uniqueId, questSet)
        inventory.setItem(ArenaQuestLayout.MENU_PLAYER_SLOT, createPlayerHead(player, playerData))
        inventory.setItem(ArenaQuestLayout.MENU_INFO_SLOT, createInfoItem())
        inventory.setItem(ArenaQuestLayout.MENU_REFRESH_SLOT, createRefreshItem())
    }

    private fun renderQuestSlots(player: Player, inventory: Inventory, playerId: UUID, questSet: ArenaDailyQuestSet) {
        for ((position, slot) in ArenaQuestLayout.MENU_QUEST_SLOTS.withIndex()) {
            val quest = questSet.quests.getOrNull(position)
            if (quest == null) {
                inventory.setItem(slot, createBackgroundPane(Material.GRAY_STAINED_GLASS_PANE))
                continue
            }

            inventory.setItem(slot, createQuestItem(player, quest, isQuestCompleted(playerId, questSet.dateKey, quest.index)))
        }
    }

    private fun renderConfirmMenu(player: Player, inventory: Inventory, quest: ArenaDailyQuestEntry) {
        fillConfirmBackground(inventory)
        inventory.setItem(ArenaQuestLayout.CONFIRM_OK_SLOT, createActionItem(Material.LIME_WOOL, ArenaI18n.text(player, "arena.ui.confirm.ok_name", "§aOK"), ArenaI18n.stringList(player, "arena.ui.confirm.ok_lore", listOf("§7このクエストを開始します"))))
        inventory.setItem(ArenaQuestLayout.CONFIRM_QUEST_SLOT, createQuestSummaryItem(player, quest))
        inventory.setItem(ArenaQuestLayout.CONFIRM_CANCEL_SLOT, createActionItem(Material.RED_WOOL, ArenaI18n.text(player, "arena.ui.confirm.cancel_name", "§cキャンセル"), ArenaI18n.stringList(player, "arena.ui.confirm.cancel_lore", listOf("§7クエスト開始を取り消します"))))
    }

    private fun openConfirmMenu(player: Player, dateKey: String, questIndex: Int): Boolean {
        val questSet = getQuestSet(dateKey) ?: return false
        val quest = questSet.quests.getOrNull(questIndex) ?: return false

        val holder = ArenaQuestConfirmHolder(player.uniqueId, dateKey, questIndex)
        val inventory = Bukkit.createInventory(holder, ArenaQuestLayout.CONFIRM_SIZE, ArenaQuestLayout.CONFIRM_TITLE)
        holder.backingInventory = inventory
        renderConfirmMenu(player, inventory, quest)
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

    private fun createQuestItem(player: Player, quest: ArenaDailyQuestEntry, isCompleted: Boolean): ItemStack {
        val item = ItemStack(themeIconMaterial(quest.themeId))
        val meta = item.itemMeta ?: return item
        val title = "${missionDisplayName(quest.missionTypeId)}＠${themeDisplayName(player, quest.themeId)}"
        meta.setDisplayName(
            if (isCompleted) {
                ArenaI18n.text(player, "arena.ui.quest.completed_item_name", "§a§m{quest}", "quest" to title)
            } else {
                ArenaI18n.text(player, "arena.ui.quest.item_name", "§a{quest}", "quest" to title)
            }
        )
        meta.lore = buildQuestLore(player, quest)
        item.itemMeta = meta
        return item
    }

    private fun createQuestSummaryItem(player: Player, quest: ArenaDailyQuestEntry): ItemStack {
        return createActionItem(
            themeIconMaterial(quest.themeId),
            ArenaI18n.text(player, "arena.ui.quest.item_name", "§a{quest}", "quest" to "${missionDisplayName(quest.missionTypeId)}＠${themeDisplayName(player, quest.themeId)}"),
            buildQuestConfirmLore(player, quest)
        )
    }

    private fun themeIconMaterial(themeId: String): Material {
        return arenaManager.getTheme(themeId)?.iconMaterial ?: Material.ROTTEN_FLESH
    }

    private fun createPlayerHead(player: Player, playerData: ArenaPlayerQuestData): ItemStack {
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

    private fun createRefreshItem(): ItemStack {
        return createActionItem(
            Material.BEDROCK,
            ArenaI18n.text(null, "arena.ui.coming_soon", "§7Coming Soon!"),
            emptyList()
        )
    }

    private fun buildQuestLore(player: Player, quest: ArenaDailyQuestEntry): List<String> {
        val mission = ArenaQuestMissionType.fromId(quest.missionTypeId) ?: ArenaQuestMissionType.BARRIER_RESTART
        val lore = mutableListOf<String>()
        lore += ArenaI18n.text(player, "arena.ui.separator", "§8§m――――――――――――――――――――")
        lore += ArenaI18n.text(player, "arena.ui.quest.mission_title", "§f❙ §7ミッション内容")
        lore += missionGuideHints(mission, player)
        lore += ""
        lore += ArenaI18n.text(player, "arena.ui.quest.difficulty_inline", "§f❙ §7難易度 §f{difficulty}", "difficulty" to difficultyDisplay(quest.difficultyId))
        lore += ArenaI18n.text(player, "arena.ui.separator", "§8§m――――――――――――――――――――")
        return lore
    }

    private fun buildQuestConfirmLore(player: Player, quest: ArenaDailyQuestEntry): List<String> {
        val mission = ArenaQuestMissionType.fromId(quest.missionTypeId) ?: ArenaQuestMissionType.BARRIER_RESTART
        val memo = randomQuestMemo(player)
        val lore = mutableListOf<String>()
        lore += ArenaI18n.text(player, "arena.ui.separator", "§8§m――――――――――――――――――――")
        lore += ArenaI18n.text(player, "arena.ui.quest.mission_title", "§f❙ §7ミッション内容")
        lore += missionGuideHints(mission, player)
        lore += ""
        lore += ArenaI18n.text(player, "arena.ui.quest.difficulty_inline", "§f❙ §7難易度 §f{difficulty}", "difficulty" to difficultyDisplay(quest.difficultyId))
        lore += ArenaI18n.text(player, "arena.ui.quest.memo_inline", "§f❙ §7メモ §f{memo}", "memo" to memo)
        lore += ArenaI18n.text(player, "arena.ui.separator", "§8§m――――――――――――――――――――")
        return lore
    }

    private fun buildInfoLore(): List<String> {
        val lines = ArenaI18n.stringList(null, "arena.ui.info.lines", listOf("§71日1回、§e24時§7にクエストが更新されます"))
        return listOf(
            ArenaI18n.text(null, "arena.ui.separator", "§8§m――――――――――――――――――――"),
            *lines.toTypedArray(),
            ArenaI18n.text(null, "arena.ui.separator", "§8§m――――――――――――――――――――")
        )
    }

    private fun playUiClick(player: Player) {
        player.playSound(player.location, "minecraft:ui.button.click", 0.8f, 2.0f)
    }

    private fun randomQuestMemo(player: Player): String {
        val memos = ArenaI18n.stringList(player, "arena.quest.memo_choices", listOf("§eがんばってください！"))
        return memos.randomOrNull() ?: "§eがんばってください！"
    }

    private fun difficultyDisplay(difficultyId: String): String {
        return difficultyDisplayMap[difficultyId]?.display ?: difficultyId
    }

    private fun missionDisplayName(missionTypeId: String): String {
        val mission = ArenaQuestMissionType.fromId(missionTypeId) ?: return missionTypeId
        return ArenaI18n.text(null, mission.displayNameKey, mission.id)
    }

    private fun missionGuideHints(mission: ArenaQuestMissionType, player: Player? = null): List<String> {
        return ArenaI18n.stringList(player, mission.missionGuideHintsKey, listOf("§7${mission.id}"))
    }

    private fun themeDisplayName(player: Player, themeId: String): String {
        return ArenaI18n.text(player, "arena.theme.$themeId.name", themeId)
    }

    private fun isQuestCompleted(playerId: UUID, dateKey: String, questIndex: Int): Boolean {
        return getPlayerData(playerId).isCompleted(dateKey, questIndex)
    }

    private fun getPlayerData(playerId: UUID): ArenaPlayerQuestData {
        playerCache[playerId]?.let { return it }

        val file = File(playerDir, "$playerId.yml")
        val data = if (file.exists()) {
            loadPlayerData(file)
        } else {
            ArenaPlayerQuestData()
        }
        playerCache[playerId] = data
        return data
    }

    private fun savePlayerData(playerId: UUID) {
        val data = playerCache[playerId] ?: return
        val file = File(playerDir, "$playerId.yml")
        val config = YamlConfiguration()
        config.set("arena.total_clear_count", data.totalClearCount)
        config.set("arena.total_mob_kill_count", data.totalMobKillCount)
        config.set("arena.barrier_restart_count", data.barrierRestartCount)
        val completedSection = linkedMapOf<String, List<Int>>()
        data.completedByDate.toSortedMap().forEach { (dateKey, indices) ->
            completedSection[dateKey] = indices.toList().sorted()
        }
        config.set("arena.completed", completedSection)
        config.save(file)
    }

    private fun loadPlayerData(file: File): ArenaPlayerQuestData {
        val config = YamlConfiguration.loadConfiguration(file)
        val totalClearCount = config.getInt("arena.total_clear_count", 0).coerceAtLeast(0)
        val totalMobKillCount = config.getInt("arena.total_mob_kill_count", 0).coerceAtLeast(0)
        val barrierRestartCount = config.getInt("arena.barrier_restart_count", 0).coerceAtLeast(0)
        val completedByDate = mutableMapOf<String, MutableSet<Int>>()
        val rawCompleted = config.getConfigurationSection("arena.completed")
        rawCompleted?.getKeys(false)?.forEach { dateKey ->
            val list = config.getIntegerList("arena.completed.$dateKey")
            completedByDate[dateKey] = list.toMutableSet()
        }

        return ArenaPlayerQuestData(
            totalClearCount = totalClearCount,
            totalMobKillCount = totalMobKillCount,
            barrierRestartCount = barrierRestartCount,
            completedByDate = completedByDate
        )
    }

    private fun currentDateKey(): String {
        return LocalDate.now(TOKYO_ZONE).format(DATE_FORMATTER)
    }

    private fun String.toEpochSeed(): Int {
        return LocalDate.parse(this, DATE_FORMATTER).toEpochDay().toInt()
    }

    private fun parseDifficultyRange(text: String?): ClosedFloatingPointRange<Double>? {
        val raw = text?.trim().orEmpty()
        val matched = Regex("^([0-9]+(?:\\.[0-9]+)?)\\.\\.([0-9]+(?:\\.[0-9]+)?)$").matchEntire(raw) ?: return null
        val min = matched.groupValues[1].toDoubleOrNull() ?: return null
        val max = matched.groupValues[2].toDoubleOrNull() ?: return null
        if (min <= 0.0) return null
        if (max < min) return null
        return min..max
    }

}
