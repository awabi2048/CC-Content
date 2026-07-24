package jp.awabi2048.cccontent.features.rank.impl

import jp.awabi2048.cccontent.features.rank.RankStorage
import jp.awabi2048.cccontent.features.rank.RankReleasePolicy
import jp.awabi2048.cccontent.features.rank.event.PlayerExperienceGainEvent
import jp.awabi2048.cccontent.features.rank.event.PrestigeExecutedEvent
import jp.awabi2048.cccontent.features.rank.event.PrestigeSkillAcquiredEvent
import jp.awabi2048.cccontent.features.rank.event.ProfessionChangedEvent
import jp.awabi2048.cccontent.features.rank.event.ProfessionLevelUpEvent
import jp.awabi2048.cccontent.features.rank.event.ProfessionSelectedEvent
import jp.awabi2048.cccontent.features.rank.event.SkillAcquiredEvent
import jp.awabi2048.cccontent.features.rank.localization.MessageProvider
import jp.awabi2048.cccontent.features.rank.prestige.PrestigeToken
import jp.awabi2048.cccontent.features.rank.profession.PlayerProfession
import jp.awabi2048.cccontent.features.rank.profession.Profession
import jp.awabi2048.cccontent.features.rank.profession.ProfessionManager
import jp.awabi2048.cccontent.features.rank.profession.SkillTreeRegistry
import jp.awabi2048.cccontent.features.rank.profession.BossBarDisplayMode
import jp.awabi2048.cccontent.features.rank.profession.profile.ProfessionFeatureToggles
import jp.awabi2048.cccontent.features.rank.profession.profile.ProfessionSpecialization
import jp.awabi2048.cccontent.features.rank.profession.profile.TypedProfessionLevelCurve
import jp.awabi2048.cccontent.features.rank.profession.profile.TypedProfessionProfile
import jp.awabi2048.cccontent.features.rank.profession.profile.TypedProfessionProfileResolver
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID

class ProfessionManagerImpl(
    private val storage: RankStorage,
    private val messageProvider: MessageProvider
) : ProfessionManager {
    companion object {
        private const val EXP_SAVE_INTERVAL_MS = 5_000L
        private const val EXP_SAVE_EVENT_THRESHOLD = 32
    }

    private val professionCache: MutableMap<UUID, PlayerProfession> = mutableMapOf()
    private val pendingExpSaveCounts: MutableMap<UUID, Int> = mutableMapOf()
    private val lastExpSaveAt: MutableMap<UUID, Long> = mutableMapOf()

    init {
        storage.init()
    }

    override fun getPlayerProfession(playerUuid: UUID): PlayerProfession? {
        professionCache[playerUuid]?.let { return it }
        val loaded = storage.loadProfession(playerUuid) ?: return null
        if (loaded.acquiredSkills.isEmpty()) {
            SkillTreeRegistry.getSkillTree(loaded.profession)?.let { skillTree ->
                loaded.acquiredSkills += skillTree.getStartSkillId()
                loaded.acquiredSkills += collectLevel0Skills(skillTree, loaded.acquiredSkills)
                storage.saveProfession(loaded)
            }
        }
        professionCache[playerUuid] = loaded
        return loaded
    }

    override fun hasProfession(playerUuid: UUID): Boolean {
        return getPlayerProfession(playerUuid) != null
    }

    override fun selectProfession(playerUuid: UUID, profession: Profession): Boolean {
        if (hasProfession(playerUuid)) {
            return false
        }

        val playerProf = createInitialProfession(playerUuid, profession) ?: return false

        professionCache[playerUuid] = playerProf
        storage.saveProfession(playerProf)

        val player = Bukkit.getPlayer(playerUuid)
        if (player != null) {
            val event = ProfessionSelectedEvent(player, profession)
            Bukkit.getPluginManager().callEvent(event)
        }

        return true
    }

    override fun changeProfession(playerUuid: UUID, profession: Profession): Boolean {
        val oldProf = getPlayerProfession(playerUuid) ?: return false
        val oldProfession = oldProf.profession
        if (oldProfession == profession) {
            return false
        }

        val newProf = createInitialProfession(playerUuid, profession) ?: return false

        professionCache[playerUuid] = newProf
        storage.saveProfession(newProf)

        val player = Bukkit.getPlayer(playerUuid)
        if (player != null) {
            val event = ProfessionChangedEvent(player, oldProfession, profession)
            Bukkit.getPluginManager().callEvent(event)
        }

        return true
    }

    override fun addExperience(playerUuid: UUID, amount: Long): Boolean {
        if (amount <= 0L) {
            return false
        }

        val prof = getPlayerProfession(playerUuid) ?: return false
        if (!RankReleasePolicy.canAccessProfession(playerUuid, prof.profession)) {
            return false
        }
        val skillTree = SkillTreeRegistry.getSkillTree(prof.profession) ?: return false
        val oldLevel = calculateLevel(prof)

        prof.addExperience(amount)
        val newLevel = calculateLevel(prof)
        val levelUp = newLevel > oldLevel
        saveProfessionByExpPolicy(playerUuid, prof, force = levelUp)

        val player = Bukkit.getPlayer(playerUuid)
        if (player != null) {
            val expEvent = PlayerExperienceGainEvent(player, prof.profession, amount, prof.currentExp)
            Bukkit.getPluginManager().callEvent(expEvent)

            if (levelUp) {
                val gainedLevel = newLevel - oldLevel
                val availableBefore = skillTree.getAvailableSkills(prof.acquiredSkills, oldLevel).toSet()
                val availableAfter = skillTree.getAvailableSkills(prof.acquiredSkills, newLevel).toSet()
                val newlyUnlockedSkills = (availableAfter - availableBefore).mapNotNull { skillTree.getSkill(it) }

                if (prof.levelUpNotificationEnabled) {
                    player.sendMessage(
                        messageProvider.getMessage(
                            "profession.level_up",
                            "profession" to messageProvider.getProfessionName(prof.profession),
                            "old" to oldLevel,
                            "new" to newLevel,
                            "gained" to gainedLevel
                        )
                    )
                    player.playSound(player.location, "minecraft:entity.player.levelup", 1.0f, 1.2f)

                    if (newlyUnlockedSkills.isNotEmpty()) {
                        val unlockedNames = newlyUnlockedSkills.joinToString("、") {
                            messageProvider.getSkillName(prof.profession, it.skillId)
                        }
                        val message = Component.text(
                            messageProvider.getMessage(
                                "profession.new_unlock",
                                "count" to newlyUnlockedSkills.size,
                                "skills" to unlockedNames
                            )
                        )
                            .color(NamedTextColor.LIGHT_PURPLE)
                            .decoration(TextDecoration.ITALIC, false)
                            .clickEvent(ClickEvent.runCommand("/rankmenu skill"))
                        player.sendMessage(message)
                    }
                }

                val levelUpEvent = ProfessionLevelUpEvent(player, prof.profession, oldLevel, newLevel)
                Bukkit.getPluginManager().callEvent(levelUpEvent)
            }
        }

        return true
    }

    override fun acquireSkill(playerUuid: UUID, skillId: String): Boolean {
        val prof = getPlayerProfession(playerUuid) ?: return false
        if (!RankReleasePolicy.canUseSkills(playerUuid)) {
            return false
        }
        val skillTree = SkillTreeRegistry.getSkillTree(prof.profession) ?: return false
        val normalizedSkillId = resolveSkillId(skillTree.getAllSkills().keys, skillId)
        val skill = skillTree.getSkill(normalizedSkillId) ?: return false
        val currentLevel = skillTree.calculateLevelByExp(prof.currentExp)

        if (!skillTree.canAcquire(skill.skillId, prof.acquiredSkills, currentLevel)) {
            return false
        }

        if (!prof.acquireSkill(skill.skillId)) {
            return false
        }

        // スキル習得後、requiredLevel = 0 の子スキルを自動習得
        prof.acquiredSkills.addAll(collectLevel0Skills(skillTree, prof.acquiredSkills))

        storage.saveProfession(prof)
        // professionCache を明示的に更新（リスナーが最新情報を取得するため）
        professionCache[playerUuid] = prof

        val player = Bukkit.getPlayer(playerUuid)
        if (player != null) {
            val skillName = messageProvider.getSkillName(prof.profession, skill.skillId)
            val event = SkillAcquiredEvent(player, prof.profession, skill.skillId, skillName)
            Bukkit.getPluginManager().callEvent(event)
            playSkillAcquireSounds(player)
        }

        return true
    }

    override fun getAvailableSkills(playerUuid: UUID): List<String> {
        if (!RankReleasePolicy.canUseSkills(playerUuid)) return emptyList()
        val prof = getPlayerProfession(playerUuid) ?: return emptyList()
        val skillTree = SkillTreeRegistry.getSkillTree(prof.profession) ?: return emptyList()
        val currentLevel = skillTree.calculateLevelByExp(prof.currentExp)
        return skillTree.getAvailableSkills(prof.acquiredSkills, currentLevel)
    }

    override fun getAcquiredSkills(playerUuid: UUID): Set<String> {
        if (!RankReleasePolicy.canUseSkills(playerUuid)) return emptySet()
        val profession = getPlayerProfession(playerUuid) ?: return emptySet()
        return profession.acquiredSkills.toSet()
    }

    override fun getCurrentExp(playerUuid: UUID): Long {
        return getPlayerProfession(playerUuid)?.currentExp ?: 0L
    }

    override fun getCurrentLevel(playerUuid: UUID): Int {
        val prof = getPlayerProfession(playerUuid) ?: return 0
        return calculateLevel(prof)
    }

    override fun setLevel(playerUuid: UUID, level: Int): Boolean {
        val prof = getPlayerProfession(playerUuid) ?: return false
        val skillTree = SkillTreeRegistry.getSkillTree(prof.profession) ?: return false
        val clampedLevel = level.coerceIn(1, skillTree.getMaxLevel())
        prof.currentExp = skillTree.getRequiredTotalExpForLevel(clampedLevel)
        prof.lastUpdated = System.currentTimeMillis()
        storage.saveProfession(prof)
        return true
    }

    override fun getTypedProfile(playerUuid: UUID): TypedProfessionProfile? {
        val prof = getPlayerProfession(playerUuid) ?: return null
        if (!prof.profession.usesTypedAbilityAdapter) return null
        return TypedProfessionProfileResolver.resolve(
            prof.profession,
            calculateLevel(prof),
            prof.acquiredSkills,
            prof.featureToggles
        )
    }

    override fun getFeatureToggles(playerUuid: UUID): ProfessionFeatureToggles? =
        getPlayerProfession(playerUuid)?.takeIf { it.profession.usesTypedAbilityAdapter }?.featureToggles?.copy()

    override fun updateFeatureToggles(playerUuid: UUID, toggles: ProfessionFeatureToggles): Boolean {
        val prof = getPlayerProfession(playerUuid) ?: return false
        if (!prof.profession.usesTypedAbilityAdapter) return false
        prof.featureToggles = toggles.copy()
        prof.lastUpdated = System.currentTimeMillis()
        storage.saveProfession(prof)
        return true
    }

    override fun recordCycleAction(
        playerUuid: UUID,
        specialist: Boolean,
        highQuality: Boolean,
        firstDiscovery: Boolean
    ): Boolean {
        val prof = getPlayerProfession(playerUuid) ?: return false
        if (!prof.profession.usesTypedAbilityAdapter) return false
        prof.cycleStatistics.validActions++
        if (specialist) prof.cycleStatistics.specialistActions++
        if (highQuality) prof.cycleStatistics.highQualityActions++
        if (firstDiscovery) prof.cycleStatistics.firstDiscoveries++
        prof.lastUpdated = System.currentTimeMillis()
        storage.saveProfession(prof)
        return true
    }

    override fun resetProfession(playerUuid: UUID): Boolean {
        if (getPlayerProfession(playerUuid) == null) {
            return false
        }
        professionCache.remove(playerUuid)
        pendingExpSaveCounts.remove(playerUuid)
        lastExpSaveAt.remove(playerUuid)
        storage.deleteProfession(playerUuid)
        return true
    }

    override fun isBossBarEnabled(playerUuid: UUID): Boolean {
        return getPlayerProfession(playerUuid)?.bossBarDisplayMode?.visible ?: true
    }

    override fun setBossBarEnabled(playerUuid: UUID, enabled: Boolean) {
        val prof = getPlayerProfession(playerUuid) ?: return
        prof.bossBarDisplayMode = if (enabled) {
            if (prof.bossBarDisplayMode == BossBarDisplayMode.HIDDEN) BossBarDisplayMode.SHORT else prof.bossBarDisplayMode
        } else {
            BossBarDisplayMode.HIDDEN
        }
        prof.bossBarEnabled = prof.bossBarDisplayMode.visible
        prof.lastUpdated = System.currentTimeMillis()
        storage.saveProfession(prof)
    }

    override fun getBossBarDisplayMode(playerUuid: UUID): BossBarDisplayMode {
        val prof = getPlayerProfession(playerUuid) ?: return BossBarDisplayMode.SHORT
        return prof.bossBarDisplayMode
    }

    override fun setBossBarDisplayMode(playerUuid: UUID, mode: BossBarDisplayMode) {
        val prof = getPlayerProfession(playerUuid) ?: return
        prof.bossBarDisplayMode = mode
        prof.bossBarEnabled = mode.visible
        prof.lastUpdated = System.currentTimeMillis()
        storage.saveProfession(prof)
    }

    override fun isLevelUpNotificationEnabled(playerUuid: UUID): Boolean {
        return getPlayerProfession(playerUuid)?.levelUpNotificationEnabled ?: true
    }

    override fun setLevelUpNotificationEnabled(playerUuid: UUID, enabled: Boolean) {
        val prof = getPlayerProfession(playerUuid) ?: return
        prof.levelUpNotificationEnabled = enabled
        prof.lastUpdated = System.currentTimeMillis()
        storage.saveProfession(prof)
    }

    override fun getPrestigeLevel(playerUuid: UUID): Int {
        val prof = getPlayerProfession(playerUuid) ?: return 0
        val skillTree = SkillTreeRegistry.getSkillTree(prof.profession) ?: return 0
        return prof.getPrestigeLevel(skillTree)
    }

    override fun canPrestige(playerUuid: UUID): Boolean {
        val prof = getPlayerProfession(playerUuid) ?: return false
        val skillTree = SkillTreeRegistry.getSkillTree(prof.profession) ?: return false
        return prof.canPrestige(skillTree)
    }

    override fun acquirePrestigeSkill(playerUuid: UUID, skillId: String): Boolean {
        val prof = getPlayerProfession(playerUuid) ?: return false
        if (!RankReleasePolicy.canUseSkills(playerUuid)) {
            return false
        }
        val skillTree = SkillTreeRegistry.getSkillTree(prof.profession) ?: return false
        val normalizedSkillId = resolveSkillId(skillTree.getAllSkills().keys, skillId)
        val skill = skillTree.getSkill(normalizedSkillId) ?: return false

        if (!prof.canUnlockPrestigeSkill(skillTree, skill.skillId)) {
            return false
        }

        if (!prof.acquirePrestigeSkill(skill.skillId)) {
            return false
        }

        storage.saveProfession(prof)

        val player = Bukkit.getPlayer(playerUuid)
        if (player != null) {
            val skillName = messageProvider.getSkillName(prof.profession, skill.skillId)
            val event = PrestigeSkillAcquiredEvent(player, prof.profession, skill.skillId, skillName)
            Bukkit.getPluginManager().callEvent(event)
            playSkillAcquireSounds(player)
        }

        return true
    }

    override fun getAvailablePrestigeSkills(playerUuid: UUID): List<String> {
        val prof = getPlayerProfession(playerUuid) ?: return emptyList()
        val skillTree = SkillTreeRegistry.getSkillTree(prof.profession) ?: return emptyList()
        return prof.getNextPrestigeSkillOptions(skillTree)
    }

    override fun getAcquiredPrestigeSkills(playerUuid: UUID): Set<String> {
        val prof = getPlayerProfession(playerUuid) ?: return emptySet()
        return prof.prestigeSkills.toSet()
    }

    override fun hasPrestigeSkill(playerUuid: UUID, skillId: String): Boolean {
        val prof = getPlayerProfession(playerUuid) ?: return false
        return prof.hasPrestigeSkillUnlocked(skillId)
    }

    override fun executePrestige(playerUuid: UUID): Boolean {
        val prof = getPlayerProfession(playerUuid) ?: return false
        val skillTree = SkillTreeRegistry.getSkillTree(prof.profession) ?: return false

        if (!prof.canPrestige(skillTree)) {
            return false
        }

        val player = Bukkit.getPlayer(playerUuid)
        val prestigeLevel = prof.getPrestigeLevel(skillTree)

        // プレイヤーがオンラインの場合のみアイテム処理を実行
        if (player != null) {
            // 再プレステージの場合、古い思念アイテムを削除
            val existingToken = PrestigeToken.findToken(player, prof.profession)
            val isRePrestige = existingToken != null
            if (isRePrestige) {
                PrestigeToken.removeToken(player, prof.profession)
            }

            // 新しい思念アイテムを付与
            val token = PrestigeToken.create(prof.profession, prestigeLevel, player, messageProvider)
            val leftover = player.inventory.addItem(token)
            if (leftover.isNotEmpty()) {
                // インベントリがいっぱいの場合はドロップ
                leftover.values.forEach { item ->
                    player.world.dropItem(player.location, item)
                }
            }

            // 職業を削除してリセット（新しい職業を選択できるようにする）
            professionCache.remove(playerUuid)
            storage.deleteProfession(playerUuid)

            val event = PrestigeExecutedEvent(player, prof.profession, prestigeLevel, isRePrestige)
            Bukkit.getPluginManager().callEvent(event)

            player.sendMessage(
                messageProvider.getMessage(
                    "profession.prestige.executed",
                    "profession" to messageProvider.getProfessionName(prof.profession),
                    "level" to prestigeLevel
                )
            )
            player.playSound(player.location, "minecraft:ui.toast.challenge_complete", 1.0f, 1.0f)
        } else {
            // オフラインの場合はデータのみリセット
            professionCache.remove(playerUuid)
            storage.deleteProfession(playerUuid)
        }

        return true
    }

    fun saveData() {
        professionCache.values.forEach { storage.saveProfession(it) }
        pendingExpSaveCounts.clear()
        lastExpSaveAt.clear()
    }

    fun loadData() {
        professionCache.clear()
    }

    private fun resolveSkillId(knownSkillIds: Set<String>, skillId: String): String {
        val trimmed = skillId.trim()
        return knownSkillIds.firstOrNull { it.equals(trimmed, ignoreCase = true) } ?: trimmed
    }

    private fun createInitialProfession(playerUuid: UUID, profession: Profession): PlayerProfession? {
        val skillTree = SkillTreeRegistry.getSkillTree(profession) ?: return null
        val acquiredSkills = mutableSetOf(skillTree.getStartSkillId()).also {
            it.addAll(collectLevel0Skills(skillTree, it))
        }
        return PlayerProfession(
            playerUuid = playerUuid,
            profession = profession,
            acquiredSkills = acquiredSkills,
            currentExp = 0L,
            featureToggles = ProfessionFeatureToggles.defaultsFor(profession)
        )
    }

    private fun calculateLevel(profession: PlayerProfession): Int =
        SkillTreeRegistry.getSkillTree(profession.profession)?.calculateLevelByExp(profession.currentExp) ?: 1

    private fun executeTypedPrestige(playerUuid: UUID, profession: PlayerProfession): Boolean {
        if (calculateLevel(profession) < TypedProfessionLevelCurve.MAX_LEVEL) return false
        val player = Bukkit.getPlayer(playerUuid)
        val cycleNumber = profession.prestigeRecords.count { it.professionId == profession.profession.id } + 1
        val representativeStatistic = maxOf(
            profession.cycleStatistics.validActions,
            profession.cycleStatistics.specialistActions,
            profession.cycleStatistics.highQualityActions,
            profession.cycleStatistics.firstDiscoveries
        )
        val completedAt = System.currentTimeMillis()
        val specializationId = getTypedProfile(playerUuid)?.specialization?.id
        profession.prestigeRecords += jp.awabi2048.cccontent.features.rank.profession.profile.ProfessionPrestigeRecord(
            professionId = profession.profession.id,
            specializationId = specializationId,
            completedAtEpochMillis = completedAt,
            cycleNumber = cycleNumber,
            representativeStatistic = representativeStatistic
        )
        storage.saveProfession(profession)

        if (player != null) {
            val token = PrestigeToken.create(
                profession = profession.profession,
                cycleNumber = cycleNumber,
                owner = player,
                messageProvider = messageProvider,
                specializationId = specializationId,
                completedAtEpochMillis = completedAt,
                representativeStatistic = representativeStatistic
            )
            val leftover = player.inventory.addItem(token)
            leftover.values.forEach { player.world.dropItem(player.location, it) }
        }

        professionCache.remove(playerUuid)
        pendingExpSaveCounts.remove(playerUuid)
        lastExpSaveAt.remove(playerUuid)
        storage.deleteProfession(playerUuid)

        if (player != null) {
            Bukkit.getPluginManager().callEvent(
                PrestigeExecutedEvent(player, profession.profession, cycleNumber, false)
            )
            player.sendMessage(
                messageProvider.getMessage(
                    "profession.prestige.executed",
                    "profession" to messageProvider.getProfessionName(profession.profession),
                    "level" to cycleNumber
                )
            )
            player.playSound(player.location, "minecraft:ui.toast.challenge_complete", 1.0f, 1.0f)
        }
        return true
    }

    private fun playSkillAcquireSounds(player: Player) {
        player.playSound(player.location, "minecraft:block.note_block.pling", 1.0f, 2.0f)
        player.playSound(player.location, "minecraft:ui.button.click", 0.8f, 1.0f)
        player.playSound(player.location, "minecraft:entity.player.levelup", 1.0f, 2.0f)
    }

    private fun saveProfessionByExpPolicy(playerUuid: UUID, profession: PlayerProfession, force: Boolean = false) {
        val now = System.currentTimeMillis()
        val nextCount = (pendingExpSaveCounts[playerUuid] ?: 0) + 1
        pendingExpSaveCounts[playerUuid] = nextCount

        val elapsed = now - (lastExpSaveAt[playerUuid] ?: 0L)
        val intervalReached = elapsed >= EXP_SAVE_INTERVAL_MS
        val thresholdReached = nextCount >= EXP_SAVE_EVENT_THRESHOLD

        if (force || intervalReached || thresholdReached) {
            storage.saveProfession(profession)
            pendingExpSaveCounts[playerUuid] = 0
            lastExpSaveAt[playerUuid] = now
        }
    }

    /**
     * 指定されたスキルツリーから、requiredLevel = 0 のスキルをすべて収集する。
     * 既に習得済みのスキルは除外される。
     *
     * アルゴリズム:
     * 1. 習得済みスキルから見える子スキルをキュー追加
     * 2. キューを処理し、requiredLevel = 0 なら習得スキルに追加
     * 3. 新たに習得したスキルの子スキルもキューに追加（BFS）
     *
     * @param skillTree スキルツリー
     * @param acquiredSkills 現在の習得済みスキル
     * @return 新規習得可能な requiredLevel = 0 スキルのセット
     */
    private fun collectLevel0Skills(
        skillTree: jp.awabi2048.cccontent.features.rank.profession.SkillTree,
        acquiredSkills: Set<String>
    ): Set<String> {
        val newAcquired = mutableSetOf<String>()
        val toCheck = mutableListOf<String>()

        // 初期キューに習得済みスキルの子スキルを追加
        acquiredSkills.forEach { skillId ->
            val skill = skillTree.getSkill(skillId) ?: return@forEach
            toCheck.addAll(skill.children)
        }

        // BFS で requiredLevel = 0 スキルを探索
        while (toCheck.isNotEmpty()) {
            val currentSkillId = toCheck.removeAt(0)

            // 既に習得済みまたは新規習得済みならスキップ
            if (currentSkillId in acquiredSkills || currentSkillId in newAcquired) {
                continue
            }

            val skill = skillTree.getSkill(currentSkillId) ?: continue

            // requiredLevel = 0 なら新規習得対象に追加
            if (skill.requiredLevel == 0) {
                newAcquired.add(currentSkillId)
                // この子スキルもキューに追加（連鎖自動習得対応）
                toCheck.addAll(skill.children)
            }
        }

        return newAcquired
    }
}
