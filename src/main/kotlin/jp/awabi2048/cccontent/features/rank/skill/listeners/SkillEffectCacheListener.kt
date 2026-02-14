package jp.awabi2048.cccontent.features.rank.skill.listeners

import jp.awabi2048.cccontent.features.rank.RankManager
import jp.awabi2048.cccontent.features.rank.event.PrestigeExecutedEvent
import jp.awabi2048.cccontent.features.rank.event.PrestigeSkillAcquiredEvent
import jp.awabi2048.cccontent.features.rank.event.ProfessionChangedEvent
import jp.awabi2048.cccontent.features.rank.event.ProfessionLevelUpEvent
import jp.awabi2048.cccontent.features.rank.event.SkillAcquiredEvent
import jp.awabi2048.cccontent.features.rank.skill.SkillEffectEngine
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.Plugin

class SkillEffectCacheListener(
    private val rankManager: RankManager,
    private val plugin: Plugin
) : Listener {

    @EventHandler
    fun onSkillAcquired(event: SkillAcquiredEvent) {
        val playerProf = rankManager.getPlayerProfession(event.player.uniqueId) ?: return
        SkillEffectEngine.rebuildCache(
            event.player.uniqueId,
            playerProf.acquiredSkills,
            event.profession,
            playerProf.prestigeSkills,
            playerProf.skillActivationStates
        )
    }

    @EventHandler
    fun onProfessionChanged(event: ProfessionChangedEvent) {
        val playerProf = rankManager.getPlayerProfession(event.player.uniqueId) ?: return
        SkillEffectEngine.rebuildCache(
            event.player.uniqueId,
            playerProf.acquiredSkills,
            event.newProfession,
            playerProf.prestigeSkills,
            playerProf.skillActivationStates
        )
    }

    @EventHandler
    fun onProfessionLevelUp(event: ProfessionLevelUpEvent) {
        val playerProf = rankManager.getPlayerProfession(event.player.uniqueId) ?: return
        SkillEffectEngine.rebuildCache(
            event.player.uniqueId,
            playerProf.acquiredSkills,
            event.profession,
            playerProf.prestigeSkills,
            playerProf.skillActivationStates
        )
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val playerProf = rankManager.getPlayerProfession(event.player.uniqueId)
        if (playerProf != null) {
            SkillEffectEngine.rebuildCache(
                event.player.uniqueId,
                playerProf.acquiredSkills,
                playerProf.profession,
                playerProf.prestigeSkills,
                playerProf.skillActivationStates
            )
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        SkillEffectEngine.clearCache(event.player.uniqueId)
    }

    @EventHandler
    fun onPrestigeSkillAcquired(event: PrestigeSkillAcquiredEvent) {
        val playerProf = rankManager.getPlayerProfession(event.player.uniqueId) ?: return
        SkillEffectEngine.rebuildCache(
            event.player.uniqueId,
            playerProf.acquiredSkills,
            event.profession,
            playerProf.prestigeSkills,
            playerProf.skillActivationStates
        )
    }

    @EventHandler
    fun onPrestigeExecuted(event: PrestigeExecutedEvent) {
        val playerProf = rankManager.getPlayerProfession(event.player.uniqueId) ?: return
        SkillEffectEngine.rebuildCache(
            event.player.uniqueId,
            playerProf.acquiredSkills,
            event.profession,
            playerProf.prestigeSkills,
            playerProf.skillActivationStates
        )
    }
}
