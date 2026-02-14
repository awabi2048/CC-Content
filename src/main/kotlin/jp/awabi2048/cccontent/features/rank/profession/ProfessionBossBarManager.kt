package jp.awabi2048.cccontent.features.rank.profession

import jp.awabi2048.cccontent.features.rank.localization.MessageProvider
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ProfessionBossBarManager(
    private val plugin: JavaPlugin,
    private val professionManager: ProfessionManager,
    private val messageProvider: MessageProvider
) {
    private val activeBossBars = ConcurrentHashMap<UUID, BossBarData>()
    private val lastExpAmounts = ConcurrentHashMap<UUID, Long>()

    private data class BossBarData(
        val bossBar: BossBar,
        var taskId: Int
    )

    fun showExpGain(player: Player, gainedAmount: Long = 0L) {
        val uuid = player.uniqueId
        
        if (!professionManager.isBossBarEnabled(uuid)) {
            return
        }

        val prof = professionManager.getPlayerProfession(uuid) ?: return
        val displayMode = prof.bossBarDisplayMode
        if (!displayMode.visible) {
            return
        }
        val skillTree = SkillTreeRegistry.getSkillTree(prof.profession) ?: return
        
        val currentLevel = skillTree.calculateLevelByExp(prof.currentExp)
        val currentExp = prof.currentExp
        val requiredExp = skillTree.getRequiredTotalExpForLevel(currentLevel + 1)
        val previousLevelExp = skillTree.getRequiredTotalExpForLevel(currentLevel)
        
        val levelExp = if (currentLevel >= skillTree.getMaxLevel()) {
            currentExp - previousLevelExp
        } else {
            requiredExp - previousLevelExp
        }
        val currentLevelExp = currentExp - previousLevelExp
        val progress = if (levelExp > 0) {
            (currentLevelExp.toDouble() / levelExp.toDouble()).coerceIn(0.0, 1.0)
        } else {
            1.0
        }

        val professionName = messageProvider.getProfessionName(prof.profession)
        val formattedCurrentExp = formatNumber(currentLevelExp)
        val formattedLevelExp = formatNumber(levelExp)
        
        // 書式: §6【<職業名>】 §7Lv. §e§l<レベル> §7(§a<現在の経験値>§7/<必要経験値> +§a<差分>§7)
        val gainedText = if (gainedAmount > 0) " +§a${formatNumber(gainedAmount)}" else ""
        val text = Component.text(
            "§6【$professionName】 §7Lv. §e§l$currentLevel §7(§a$formattedCurrentExp§7/$formattedLevelExp$gainedText§7)"
        )

        // 職業設定からボスバー色を取得
        val bossBarColor = try {
            BossBar.Color.valueOf(skillTree.getBossBarColor().uppercase())
        } catch (e: IllegalArgumentException) {
            BossBar.Color.GREEN
        }

        val existingData = activeBossBars[uuid]
        val bossBar = existingData?.bossBar ?: BossBar.bossBar(
            text,
            progress.toFloat(),
            bossBarColor,
            BossBar.Overlay.PROGRESS
        )

        bossBar.name(text)
        bossBar.progress(progress.toFloat())

        if (existingData != null) {
            Bukkit.getScheduler().cancelTask(existingData.taskId)
        } else {
            player.showBossBar(bossBar)
        }

        val taskId = Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            hideBossBar(uuid)
        }, displayMode.durationTicks).taskId

        activeBossBars[uuid] = BossBarData(bossBar, taskId)
        lastExpAmounts[uuid] = currentExp
    }

    fun hideBossBar(uuid: UUID) {
        val data = activeBossBars.remove(uuid) ?: return
        val player = Bukkit.getPlayer(uuid) ?: return
        player.hideBossBar(data.bossBar)
    }

    fun hideAll() {
        activeBossBars.forEach { (uuid, data) ->
            val player = Bukkit.getPlayer(uuid) ?: return@forEach
            player.hideBossBar(data.bossBar)
            Bukkit.getScheduler().cancelTask(data.taskId)
        }
        activeBossBars.clear()
    }

    private fun formatNumber(num: Long): String {
        return String.format("%,d", num)
    }
}
