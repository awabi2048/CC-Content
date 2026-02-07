package jp.awabi2048.cccontent.features.rank.impl

import jp.awabi2048.cccontent.features.rank.RankStorage
import jp.awabi2048.cccontent.features.rank.profession.Profession
import jp.awabi2048.cccontent.features.rank.profession.PlayerProfession
import jp.awabi2048.cccontent.features.rank.profession.ProfessionManager
import jp.awabi2048.cccontent.features.rank.profession.SkillTree
import jp.awabi2048.cccontent.features.rank.profession.SkillTreeRegistry
import jp.awabi2048.cccontent.features.rank.event.ProfessionSelectedEvent
import jp.awabi2048.cccontent.features.rank.event.ProfessionChangedEvent
import jp.awabi2048.cccontent.features.rank.event.SkillAcquiredEvent
import jp.awabi2048.cccontent.features.rank.event.PlayerExperienceGainEvent
import jp.awabi2048.cccontent.features.rank.localization.MessageProvider
import org.bukkit.Bukkit
import java.util.UUID

/**
 * 職業管理の実装
 */
class ProfessionManagerImpl(
    private val storage: RankStorage,
    private val messageProvider: MessageProvider
) : ProfessionManager {
    
    /** メモリ上にキャッシュされた職業情報 */
    private val professionCache: MutableMap<UUID, PlayerProfession> = mutableMapOf()
    
    init {
        storage.init()
    }
    
    override fun getPlayerProfession(playerUuid: UUID): PlayerProfession? {
        // キャッシュに存在する場合はそれを返す
        if (playerUuid in professionCache) {
            return professionCache[playerUuid]
        }
        
        // ストレージから読み込む
        val loaded = storage.loadProfession(playerUuid)
        
        // 読み込めた場合のみキャッシュに追加
        if (loaded != null) {
            professionCache[playerUuid] = loaded
        }
        
        return loaded
    }
    
    override fun hasProfession(playerUuid: UUID): Boolean {
        return getPlayerProfession(playerUuid) != null
    }
    
    override fun selectProfession(playerUuid: UUID, profession: Profession): Boolean {
        if (hasProfession(playerUuid)) return false  // 既に選択済み
        
        val skillTree = SkillTreeRegistry.getSkillTree(profession) ?: return false
        val startSkillId = skillTree.getStartSkillId()
        
        val playerProf = PlayerProfession(
            playerUuid,
            profession,
            mutableSetOf(startSkillId)  // 開始スキルを習得済みに
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
        
        if (oldProfession == profession) return false  // 同じ職業への変更は無視
        
        val skillTree = SkillTreeRegistry.getSkillTree(profession) ?: return false
        val startSkillId = skillTree.getStartSkillId()
        
        val newProf = PlayerProfession(
            playerUuid,
            profession,
            mutableSetOf(startSkillId)
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
        val prof = getPlayerProfession(playerUuid) ?: return false
        
        prof.addExperience(amount)
        storage.saveProfession(prof)
        
        val player = Bukkit.getPlayer(playerUuid)
        if (player != null) {
            val event = PlayerExperienceGainEvent(player, prof.profession, amount, prof.currentExp)
            Bukkit.getPluginManager().callEvent(event)
        }
        
        return true
    }
    
    override fun acquireSkill(playerUuid: UUID, skillId: String): Boolean {
        val prof = getPlayerProfession(playerUuid) ?: return false
        val skillTree = SkillTreeRegistry.getSkillTree(prof.profession) ?: return false
        val skill = skillTree.getSkill(skillId) ?: return false
        
        // スキル取得可能か確認
        if (!skill.canAcquire(prof.acquiredSkills, prof.currentExp)) {
            return false
        }
        
        // スキルを習得
        if (prof.acquireSkill(skillId, skill.requiredExp)) {
            storage.saveProfession(prof)
            
            val player = Bukkit.getPlayer(playerUuid)
            if (player != null) {
                val skillName = messageProvider.getSkillName(prof.profession, skillId)
                val event = SkillAcquiredEvent(player, prof.profession, skillId, skillName)
                Bukkit.getPluginManager().callEvent(event)
            }
            
            return true
        }
        
        return false
    }
    
    override fun getAvailableSkills(playerUuid: UUID): List<String> {
        val prof = getPlayerProfession(playerUuid) ?: return emptyList()
        val skillTree = SkillTreeRegistry.getSkillTree(prof.profession) ?: return emptyList()
        
        return skillTree.getAvailableSkills(prof.acquiredSkills, prof.currentExp)
    }
    
    override fun getAcquiredSkills(playerUuid: UUID): Set<String> {
        return getPlayerProfession(playerUuid)?.acquiredSkills?.toSet() ?: emptySet()
    }
    
    override fun getCurrentExp(playerUuid: UUID): Long {
        return getPlayerProfession(playerUuid)?.currentExp ?: 0L
    }
    
    override fun resetProfession(playerUuid: UUID): Boolean {
        val prof = getPlayerProfession(playerUuid) ?: return false
        
        professionCache.remove(playerUuid)
        storage.deleteProfession(playerUuid)
        
        return true
    }
    
    fun saveData() {
        professionCache.values.forEach { storage.saveProfession(it) }
    }
    
    fun loadData() {
        professionCache.clear()
    }
}
