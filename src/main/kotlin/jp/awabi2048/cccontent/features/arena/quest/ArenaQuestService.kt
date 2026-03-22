package jp.awabi2048.cccontent.features.arena.quest

import jp.awabi2048.cccontent.CCContent
import jp.awabi2048.cccontent.features.arena.ArenaI18n
import jp.awabi2048.cccontent.features.arena.ArenaManager
import jp.awabi2048.cccontent.features.arena.ArenaStartResult
import jp.awabi2048.cccontent.features.arena.event.ArenaDailyQuestGeneratedEvent
import jp.awabi2048.cccontent.features.arena.event.ArenaQuestStartRequestEvent
import jp.awabi2048.cccontent.features.arena.event.ArenaSessionEndedEvent
import jp.awabi2048.cccontent.features.arena.quest.ArenaQuestModifiers
import jp.awabi2048.cccontent.features.arena.quest.ArenaQuestCharactorDefinition
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
    private var charactorDefinitions: Map<String, ArenaQuestCharactorDefinition> = emptyMap()
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
            player.sendMessage("§cアリーナメニューを開けませんでした: ${e.message ?: "unknown"}")
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
            player.sendMessage("§cアリーナクエストを開けませんでした")
            return true
        }
        val quest = questSet.quests.getOrNull(questIndex) ?: return true

        if (isQuestCompleted(player.uniqueId, holder.dateKey, quest.index)) {
            player.sendMessage("§eこのクエストはすでに完了済みです")
            return true
        }

        if (!openConfirmMenu(player, holder.dateKey, quest.index)) {
            player.sendMessage("§cアリーナクエストを開けませんでした")
        }
        return true
    }

    fun handleConfirmClick(player: Player, holder: ArenaQuestConfirmHolder, rawSlot: Int): Boolean {
        if (player.uniqueId != holder.ownerId) {
            return false
        }

        return when (rawSlot) {
            ArenaQuestLayout.CONFIRM_OK_SLOT -> {
                startQuest(player, holder.dateKey, holder.questIndex)
                true
            }
            ArenaQuestLayout.CONFIRM_CANCEL_SLOT -> {
                if (!openMenu(player, holder.dateKey)) {
                    player.sendMessage("§cアリーナメニューを開けませんでした")
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
            player.sendMessage("§cアリーナクエストが見つかりません")
            return
        }
        val quest = questSet.quests.getOrNull(questIndex) ?: run {
            player.sendMessage("§cアリーナクエストが見つかりません")
            return
        }

        if (isQuestCompleted(player.uniqueId, dateKey, quest.index)) {
            player.sendMessage("§eこのクエストはすでに完了済みです")
            return
        }

        val requestEvent = ArenaQuestStartRequestEvent(player, dateKey, quest)
        Bukkit.getPluginManager().callEvent(requestEvent)
        if (requestEvent.isCancelled) {
            player.sendMessage("§cクエスト開始はキャンセルされました")
            return
        }

        val charactor = charactorDefinition(quest.charactorId)

        player.closeInventory()
        player.sendMessage(
            ArenaI18n.text(
                player,
                "arena.messages.session.starting",
                "&6[Arena] アリーナを開始します..."
            )
        )

        Bukkit.getScheduler().runTask(plugin, Runnable {
            if (!player.isOnline) {
                return@Runnable
            }

            when (val result = arenaManager.startSession(
                player,
                quest.mobTypeId,
                quest.difficultyId,
                quest.themeId,
                charactor.toModifiers(),
                quest.difficultyScore
            )) {
                is ArenaStartResult.Success -> {
                    activeQuests[player.uniqueId] = ArenaActiveQuestRecord(dateKey, quest.index, quest)
                }
                is ArenaStartResult.Error -> {
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
        })
    }

    private fun validateArenaSources() {
        val mobTypeIds = arenaManager.getMobTypeIds().map { it.trim() }.filter { it.isNotBlank() }.sorted()
        if (mobTypeIds.isEmpty()) {
            throw IllegalStateException("mob_type が0件のためアリーナクエストを生成できません")
        }

        val themeIds = arenaManager.getThemeIds().map { it.trim() }.filter { it.isNotBlank() }.sorted()
        if (themeIds.isEmpty()) {
            throw IllegalStateException("theme が0件のためアリーナクエストを生成できません")
        }

        if (generateCount > QUEST_SLOT_LIMIT) {
            throw IllegalStateException("アリーナクエスト生成数がメニュー枠を超えています: $generateCount > $QUEST_SLOT_LIMIT")
        }

        if (generateCount <= 0) {
            throw IllegalStateException("アリーナクエスト生成数が不正です: $generateCount")
        }

        if (charactorDefinitions.size != 3) {
            throw IllegalStateException("quest_charactor_config が不完全です")
        }

        val totalWeight = charactorDefinitions.values.sumOf { it.weight.coerceAtLeast(0) }
        if (totalWeight <= 0) {
            throw IllegalStateException("quest_charactor_config の重み合計が不正です")
        }
    }

    private fun refreshDefinitions() {
        loadGenerationConfig()
        loadDifficultyDefinitions()
        loadCharactorDefinitions()
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

    private fun loadCharactorDefinitions() {
        val coreConfig = currentCoreConfig()
        val section = coreConfig.getConfigurationSection("arena.quest_charactor_config")
            ?: throw IllegalStateException("arena.quest_charactor_config が見つかりません")

        val supported = listOf(
            createCharactorDefinition(coreConfig, "none", "特になし"),
            createCharactorDefinition(coreConfig, "legion", "大群"),
            createCharactorDefinition(coreConfig, "elite", "少数精鋭")
        )

        val names = supported.associateBy { it.id }
        if (names.size != supported.size) {
            throw IllegalStateException("quest_charactor_config に重複IDがあります")
        }

        supported.forEach { definition ->
            if (!section.contains(definition.id)) {
                throw IllegalStateException("quest_charactor_config.${definition.id} が見つかりません")
            }
            if (definition.weight <= 0) {
                throw IllegalStateException("quest_charactor_config.${definition.id}.weight が不正です: ${definition.weight}")
            }
            validatePositiveMultiplier("quest_charactor_config.${definition.id}.spawn_interval_multiplier", definition.spawnIntervalMultiplier)
            validatePositiveMultiplier("quest_charactor_config.${definition.id}.max_summon_count_multiplier", definition.maxSummonCountMultiplier)
            validatePositiveMultiplier("quest_charactor_config.${definition.id}.clear_mob_count_multiplier", definition.clearMobCountMultiplier)
            validatePositiveMultiplier("quest_charactor_config.${definition.id}.mob_stats_multiplier.health", definition.mobHealthMultiplier)
            validatePositiveMultiplier("quest_charactor_config.${definition.id}.mob_stats_multiplier.attack", definition.mobAttackMultiplier)
        }

        charactorDefinitions = names
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

    private fun createCharactorDefinition(
        coreConfig: FileConfiguration,
        id: String,
        displayName: String
    ): ArenaQuestCharactorDefinition {
        val basePath = "arena.quest_charactor_config.$id"
        val requiredKeys = listOf(
            "$basePath.weight",
            "$basePath.spawn_interval_multiplier",
            "$basePath.max_summon_count_multiplier",
            "$basePath.clear_mob_count_multiplier",
            "$basePath.mob_stats_multiplier.health",
            "$basePath.mob_stats_multiplier.attack"
        )
        requiredKeys.forEach { path ->
            if (!coreConfig.contains(path)) {
                throw IllegalStateException("$path が見つかりません")
            }
        }
        return ArenaQuestCharactorDefinition(
            id = id,
            displayName = displayName,
            weight = coreConfig.getInt("$basePath.weight"),
            spawnIntervalMultiplier = coreConfig.getDouble("$basePath.spawn_interval_multiplier"),
            maxSummonCountMultiplier = coreConfig.getDouble("$basePath.max_summon_count_multiplier"),
            clearMobCountMultiplier = coreConfig.getDouble("$basePath.clear_mob_count_multiplier"),
            mobHealthMultiplier = coreConfig.getDouble("$basePath.mob_stats_multiplier.health"),
            mobAttackMultiplier = coreConfig.getDouble("$basePath.mob_stats_multiplier.attack")
        )
    }

    private fun validatePositiveMultiplier(path: String, value: Double) {
        if (value <= 0.0) {
            throw IllegalStateException("$path が不正です: $value")
        }
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
        val mobTypeIds = arenaManager.getMobTypeIds().map { it.trim() }.filter { it.isNotBlank() }.sorted()
        val themeIds = arenaManager.getThemeIds().map { it.trim() }.filter { it.isNotBlank() }.sorted()
        val random = Random(dateKey.toEpochSeed())

        val quests = (0 until generateCount).map { index ->
            val mobTypeId = mobTypeIds.random(random)
            val themeId = themeIds.random(random)
            val difficulty = difficultyDefinitions.random(random)
            val charactor = selectCharactor(random)
            val score = randomDifficultyScore(difficulty, random)
            val resolvedDifficultyId = resolveDifficultyId(score) ?: difficulty.id

            ArenaDailyQuestEntry(
                index = index,
                mobTypeId = mobTypeId,
                difficultyScore = score,
                difficultyId = resolvedDifficultyId,
                themeId = themeId,
                charactorId = charactor.id
            )
        }

        return ArenaDailyQuestSet(
            dateKey = dateKey,
            generatedAtMillis = dateKey.toEpochSeed().toLong(),
            quests = quests
        )
    }

    private fun selectCharactor(random: Random): ArenaQuestCharactorDefinition {
        val candidates = charactorDefinitions.values.sortedBy { it.id }
        val totalWeight = candidates.sumOf { it.weight.coerceAtLeast(0) }
        if (totalWeight <= 0) {
            return ArenaQuestCharactorDefinition(
                id = "none",
                displayName = "特になし",
                weight = 1,
                spawnIntervalMultiplier = 1.0,
                maxSummonCountMultiplier = 1.0,
                clearMobCountMultiplier = 1.0,
                mobHealthMultiplier = 1.0,
                mobAttackMultiplier = 1.0
            )
        }

        var roll = random.nextInt(totalWeight)
        for (candidate in candidates) {
            roll -= candidate.weight.coerceAtLeast(0)
            if (roll < 0) {
                return candidate
            }
        }
        return candidates.lastOrNull() ?: charactorDefinitions["none"] ?: ArenaQuestCharactorDefinition(
            id = "none",
            displayName = "特になし",
            weight = 1,
            spawnIntervalMultiplier = 1.0,
            maxSummonCountMultiplier = 1.0,
            clearMobCountMultiplier = 1.0,
            mobHealthMultiplier = 1.0,
            mobAttackMultiplier = 1.0
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

    private fun charactorDefinition(charactorId: String): ArenaQuestCharactorDefinition {
        return charactorDefinitions[charactorId]
            ?: throw IllegalStateException("charactor_id が不正です: $charactorId")
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
                    "mob_type_id" to quest.mobTypeId,
                    "difficulty_score" to quest.difficultyScore,
                    "difficulty_id" to quest.difficultyId,
                    "theme_id" to quest.themeId,
                    "charactor_id" to quest.charactorId
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
            val mobTypeId = map["mob_type_id"]?.toString()?.trim().orEmpty()
            val difficultyScore = map["difficulty_score"]?.toString()?.toDoubleOrNull() ?: return@mapNotNull null
            val difficultyId = map["difficulty_id"]?.toString()?.trim().orEmpty()
            val themeId = map["theme_id"]?.toString()?.trim().orEmpty()
            val charactorId = map["charactor_id"]?.toString()?.trim().orEmpty()

            validateStoredQuest(index, mobTypeId, difficultyScore, difficultyId, themeId, charactorId)

            ArenaDailyQuestEntry(
                index = index,
                mobTypeId = mobTypeId,
                difficultyScore = difficultyScore,
                difficultyId = difficultyId,
                themeId = themeId,
                charactorId = charactorId
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
        mobTypeId: String,
        difficultyScore: Double,
        difficultyId: String,
        themeId: String,
        charactorId: String
    ) {
        if (index < 0) {
            throw IllegalStateException("クエストindexが不正です: $index")
        }

        if (mobTypeId.isBlank() || !arenaManager.getMobTypeIds().contains(mobTypeId)) {
            throw IllegalStateException("mob_type_id が不正です: $mobTypeId")
        }

        if (themeId.isBlank() || !arenaManager.getThemeIds().contains(themeId)) {
            throw IllegalStateException("theme_id が不正です: $themeId")
        }

        val difficulty = difficultyDefinitions.firstOrNull { it.id == difficultyId }
            ?: throw IllegalStateException("difficulty_id が不正です: $difficultyId")

        if (!difficulty.difficultyRange.contains(difficultyScore)) {
            throw IllegalStateException("difficulty_score が範囲外です: id=$difficultyId score=$difficultyScore range=${difficulty.difficultyRange}")
        }

        if (charactorId.isBlank() || !charactorDefinitions.containsKey(charactorId)) {
            throw IllegalStateException("charactor_id が不正です: $charactorId")
        }
    }

    private fun ensureQuestSet(dateKey: String): ArenaDailyQuestSet {
        questCache[dateKey]?.let { return it }
        val file = File(questDir, "$dateKey.yml")
        val set = if (file.exists()) {
            loadQuestSet(dateKey)
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
        val completedCount = questSet.quests.count { playerData.isCompleted(questSet.dateKey, it.index) }

        renderQuestSlots(inventory, questSet)
        inventory.setItem(ArenaQuestLayout.MENU_PLAYER_SLOT, createPlayerHead(player, playerData, completedCount, questSet.quests.size))
        inventory.setItem(ArenaQuestLayout.MENU_INFO_SLOT, createInfoItem())
        inventory.setItem(ArenaQuestLayout.MENU_REFRESH_SLOT, createRefreshItem())
    }

    private fun renderQuestSlots(inventory: Inventory, questSet: ArenaDailyQuestSet) {
        for ((position, slot) in ArenaQuestLayout.MENU_QUEST_SLOTS.withIndex()) {
            val quest = questSet.quests.getOrNull(position)
            if (quest == null) {
                inventory.setItem(slot, createBackgroundPane(Material.GRAY_STAINED_GLASS_PANE))
                continue
            }

            inventory.setItem(slot, createQuestItem(quest))
        }
    }

    private fun renderConfirmMenu(inventory: Inventory, quest: ArenaDailyQuestEntry) {
        fillConfirmBackground(inventory)
        inventory.setItem(ArenaQuestLayout.CONFIRM_OK_SLOT, createActionItem(Material.LIME_STAINED_GLASS_PANE, "§a§lOK", listOf("§7このクエストを開始します")))
        inventory.setItem(ArenaQuestLayout.CONFIRM_QUEST_SLOT, createQuestSummaryItem(quest))
        inventory.setItem(ArenaQuestLayout.CONFIRM_CANCEL_SLOT, createActionItem(Material.RED_STAINED_GLASS_PANE, "§c§lキャンセル", listOf("§7クエスト開始を取り消します")))
    }

    private fun openConfirmMenu(player: Player, dateKey: String, questIndex: Int): Boolean {
        val questSet = getQuestSet(dateKey) ?: return false
        val quest = questSet.quests.getOrNull(questIndex) ?: return false

        val holder = ArenaQuestConfirmHolder(player.uniqueId, dateKey, questIndex)
        val inventory = Bukkit.createInventory(holder, ArenaQuestLayout.CONFIRM_SIZE, ArenaQuestLayout.CONFIRM_TITLE)
        holder.backingInventory = inventory
        renderConfirmMenu(inventory, quest)
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

    private fun createQuestItem(quest: ArenaDailyQuestEntry): ItemStack {
        val item = ItemStack(Material.ROTTEN_FLESH)
        val meta = item.itemMeta ?: return item
        meta.setDisplayName("§aクエスト: ${formatQuestMobName(quest.mobTypeId)}")
        meta.lore = buildQuestLore(quest)
        item.itemMeta = meta
        return item
    }

    private fun createQuestSummaryItem(quest: ArenaDailyQuestEntry): ItemStack {
        return createActionItem(
            Material.ROTTEN_FLESH,
            "§aクエスト: ${formatQuestMobName(quest.mobTypeId)}",
            buildQuestLore(quest)
        )
    }

    private fun createPlayerHead(player: Player, playerData: ArenaPlayerQuestData, completedCount: Int, questCount: Int): ItemStack {
        val item = ItemStack(Material.PLAYER_HEAD)
        val meta = item.itemMeta as? SkullMeta ?: return item
        meta.owningPlayer = player
        meta.setDisplayName("§6§l${player.name}")
        meta.lore = listOf(
            "§7累計クリア数: §f${playerData.totalClearCount}",
            "§7今日の完了数: §f$completedCount/$questCount"
        )
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
        item.itemMeta = meta
        return item
    }

    private fun createInfoItem(): ItemStack {
        return createActionItem(
            Material.BOOK,
            "§b§lInfo",
            listOf(
                "§7ここにはダミー説明を表示します",
                "§7アリーナクエストの一覧を確認できます",
                "§7更新は /ccc update_day で実行します"
            )
        )
    }

    private fun createRefreshItem(): ItemStack {
        return createActionItem(
            Material.CLOCK,
            "§a§l再生成",
            listOf("§7モック実装です", "§7現在は何もしません")
        )
    }

    private fun buildQuestLore(quest: ArenaDailyQuestEntry): List<String> {
        val charactor = charactorDefinition(quest.charactorId)
        return listOf(
            "§8§m――――――――――――――――――――",
            "§f│ §7出現モブ §e${formatQuestMobName(quest.mobTypeId)}",
            "§f│ §7特徴 ${charactorColor(quest.charactorId)}${charactor.displayName}",
            "§f│ §7難易度 ${difficultyDisplay(quest.difficultyId)}",
            "§8§m――――――――――――――――――――",
            "",
            "§e§nクリックしてこのクエストを受注する"
        )
    }

    private fun difficultyDisplay(difficultyId: String): String {
        return difficultyDisplayMap[difficultyId]?.display ?: difficultyId
    }

    private fun formatQuestMobName(mobTypeId: String): String {
        val normalized = mobTypeId.lowercase()
        return when (normalized) {
            "zombie" -> "ゾンビ"
            "skeleton" -> "スケルトン"
            "creeper" -> "クリーパー"
            "spider" -> "スパイダー"
            "enderman" -> "エンダーマン"
            "slime" -> "スライム"
            "witch" -> "ウィッチ"
            "blaze" -> "ブレイズ"
            "husk" -> "ハスク"
            "drowned" -> "ドラウンド"
            "piglin" -> "ピグリン"
            "wither_skeleton" -> "ウィザースケルトン"
            "magma_cube" -> "マグマキューブ"
            "guardian" -> "ガーディアン"
            else -> mobTypeId.replace('_', ' ')
        }
    }

    private fun charactorColor(charactorId: String): String {
        return when (charactorId) {
            "legion" -> "§a"
            "elite" -> "§b"
            else -> "§7"
        }
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
        val completedByDate = mutableMapOf<String, MutableSet<Int>>()
        val rawCompleted = config.getConfigurationSection("arena.completed")
        rawCompleted?.getKeys(false)?.forEach { dateKey ->
            val list = config.getIntegerList("arena.completed.$dateKey")
            completedByDate[dateKey] = list.toMutableSet()
        }

        return ArenaPlayerQuestData(
            totalClearCount = totalClearCount,
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

    private fun formatDifficultyScore(score: Double): String {
        return String.format(java.util.Locale.ROOT, "%.2f", score)
    }
}
