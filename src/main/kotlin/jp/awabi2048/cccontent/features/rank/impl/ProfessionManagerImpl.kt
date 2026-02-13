package jp.awabi2048.cccontent.features.rank.impl

import jp.awabi2048.cccontent.features.rank.RankStorage
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
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID

class ProfessionManagerImpl(
    private val storage: RankStorage,
    private val messageProvider: MessageProvider
) : ProfessionManager {

    private val professionCache: MutableMap<UUID, PlayerProfession> = mutableMapOf()

    init {
        storage.init()
    }

    override fun getPlayerProfession(playerUuid: UUID): PlayerProfession? {
        professionCache[playerUuid]?.let { return it }
        val loaded = storage.loadProfession(playerUuid) ?: return null
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

        val skillTree = SkillTreeRegistry.getSkillTree(profession) ?: return false
        val startSkillId = skillTree.getStartSkillId()

        // 開始スキルと requiredLevel = 0 のスキルを自動習得
        val acquiredSkills = mutableSetOf(startSkillId)
        acquiredSkills.addAll(collectLevel0Skills(skillTree, acquiredSkills))

        val playerProf = PlayerProfession(
            playerUuid = playerUuid,
            profession = profession,
            acquiredSkills = acquiredSkills,
            currentExp = 0L
        )

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

        val skillTree = SkillTreeRegistry.getSkillTree(profession) ?: return false
        val startSkillId = skillTree.getStartSkillId()

        // 開始スキルと requiredLevel = 0 のスキルを自動習得
        val acquiredSkills = mutableSetOf(startSkillId)
        acquiredSkills.addAll(collectLevel0Skills(skillTree, acquiredSkills))

        val newProf = PlayerProfession(
            playerUuid = playerUuid,
            profession = profession,
            acquiredSkills = acquiredSkills,
            currentExp = 0L
        )

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
        val skillTree = SkillTreeRegistry.getSkillTree(prof.profession) ?: return false
        val oldLevel = skillTree.calculateLevelByExp(prof.currentExp)

        prof.addExperience(amount)
        val newLevel = skillTree.calculateLevelByExp(prof.currentExp)

        storage.saveProfession(prof)

        val player = Bukkit.getPlayer(playerUuid)
        if (player != null) {
            val expEvent = PlayerExperienceGainEvent(player, prof.profession, amount, prof.currentExp)
            Bukkit.getPluginManager().callEvent(expEvent)

            if (newLevel > oldLevel) {
                val gainedLevel = newLevel - oldLevel
                player.sendMessage(
                    messageProvider.getMessage(
                        "rank.profession.level_up",
                        "profession" to messageProvider.getProfessionName(prof.profession),
                        "old" to oldLevel,
                        "new" to newLevel,
                        "gained" to gainedLevel
                    )
                )
                player.playSound(player.location, "minecraft:entity.player.levelup", 1.0f, 1.2f)

                val levelUpEvent = ProfessionLevelUpEvent(player, prof.profession, oldLevel, newLevel)
                Bukkit.getPluginManager().callEvent(levelUpEvent)
            }
        }

        return true
    }

    override fun acquireSkill(playerUuid: UUID, skillId: String): Boolean {
        val prof = getPlayerProfession(playerUuid) ?: return false
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
        val prof = getPlayerProfession(playerUuid) ?: return emptyList()
        val skillTree = SkillTreeRegistry.getSkillTree(prof.profession) ?: return emptyList()
        val currentLevel = skillTree.calculateLevelByExp(prof.currentExp)
        return skillTree.getAvailableSkills(prof.acquiredSkills, currentLevel)
    }

    override fun getAcquiredSkills(playerUuid: UUID): Set<String> {
        return getPlayerProfession(playerUuid)?.acquiredSkills?.toSet() ?: emptySet()
    }

    override fun getCurrentExp(playerUuid: UUID): Long {
        return getPlayerProfession(playerUuid)?.currentExp ?: 0L
    }

    override fun getCurrentLevel(playerUuid: UUID): Int {
        val prof = getPlayerProfession(playerUuid) ?: return 1
        val skillTree = SkillTreeRegistry.getSkillTree(prof.profession) ?: return 1
        return skillTree.calculateLevelByExp(prof.currentExp)
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

    override fun resetProfession(playerUuid: UUID): Boolean {
        if (getPlayerProfession(playerUuid) == null) {
            return false
        }
        professionCache.remove(playerUuid)
        storage.deleteProfession(playerUuid)
        return true
    }

    override fun isBossBarEnabled(playerUuid: UUID): Boolean {
        return getPlayerProfession(playerUuid)?.bossBarEnabled ?: true
    }

    override fun setBossBarEnabled(playerUuid: UUID, enabled: Boolean) {
        val prof = getPlayerProfession(playerUuid) ?: return
        prof.bossBarEnabled = enabled
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
        return getPlayerProfession(playerUuid)?.prestigeSkills?.toSet() ?: emptySet()
    }

    override fun hasPrestigeSkill(playerUuid: UUID, skillId: String): Boolean {
        return getPlayerProfession(playerUuid)?.hasPrestigeSkillUnlocked(skillId) ?: false
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
                    "rank.profession.prestige.executed",
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
    }

    fun loadData() {
        professionCache.clear()
    }

    private fun resolveSkillId(knownSkillIds: Set<String>, skillId: String): String {
        val trimmed = skillId.trim()
        return knownSkillIds.firstOrNull { it.equals(trimmed, ignoreCase = true) } ?: trimmed
    }

    private fun playSkillAcquireSounds(player: Player) {
        player.playSound(player.location, "minecraft:block.note_block.pling", 1.0f, 2.0f)
        player.playSound(player.location, "minecraft:ui.button.click", 0.8f, 1.0f)
        player.playSound(player.location, "minecraft:entity.player.levelup", 1.0f, 2.0f)
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
