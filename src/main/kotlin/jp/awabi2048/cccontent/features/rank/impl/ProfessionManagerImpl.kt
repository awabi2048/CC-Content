package jp.awabi2048.cccontent.features.rank.impl

import jp.awabi2048.cccontent.features.rank.RankStorage
import jp.awabi2048.cccontent.features.rank.event.PlayerExperienceGainEvent
import jp.awabi2048.cccontent.features.rank.event.ProfessionChangedEvent
import jp.awabi2048.cccontent.features.rank.event.ProfessionLevelUpEvent
import jp.awabi2048.cccontent.features.rank.event.ProfessionSelectedEvent
import jp.awabi2048.cccontent.features.rank.event.SkillAcquiredEvent
import jp.awabi2048.cccontent.features.rank.localization.MessageProvider
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

        val playerProf = PlayerProfession(
            playerUuid = playerUuid,
            profession = profession,
            acquiredSkills = mutableSetOf(startSkillId),
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

        val newProf = PlayerProfession(
            playerUuid = playerUuid,
            profession = profession,
            acquiredSkills = mutableSetOf(startSkillId),
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

        storage.saveProfession(prof)

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
}
