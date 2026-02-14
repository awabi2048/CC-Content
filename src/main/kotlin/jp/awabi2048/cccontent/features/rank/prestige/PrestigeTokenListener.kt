package jp.awabi2048.cccontent.features.rank.prestige

import jp.awabi2048.cccontent.features.rank.profession.ProfessionManager
import jp.awabi2048.cccontent.features.rank.skill.SkillEffectEngine
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerPickupItemEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable

/**
 * 思念アイテムの着脱を検知してスキル効果キャッシュを更新するリスナー
 */
class PrestigeTokenListener(
    private val plugin: JavaPlugin,
    private val professionManager: ProfessionManager
) : Listener {

    // 前回の検知状態を保持（パフォーマンス最適化用）
    private val lastTokenState: MutableMap<Player, Boolean> = mutableMapOf()

    init {
        // 定期的に状態をチェック（念のため）
        object : BukkitRunnable() {
            override fun run() {
                Bukkit.getOnlinePlayers().forEach { player ->
                    checkAndUpdateCache(player)
                }
            }
        }.runTaskTimer(plugin, 20L, 20L) // 1秒ごとにチェック
    }

    @EventHandler
    fun onItemHeld(event: PlayerItemHeldEvent) {
        // ホットバー切り替え時にチェック
        checkAndUpdateCache(event.player)
    }

    @EventHandler
    fun onSwapHandItems(event: PlayerSwapHandItemsEvent) {
        // オフハンド切り替え時にチェック
        checkAndUpdateCache(event.player)
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        // インベントリ操作時にチェック（プレイヤーのみ）
        val player = event.whoClicked as? Player ?: return
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            checkAndUpdateCache(player)
        }, 1L)
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        // ドラッグ操作時にチェック
        val player = event.whoClicked as? Player ?: return
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            checkAndUpdateCache(player)
        }, 1L)
    }

    @EventHandler
    fun onDropItem(event: PlayerDropItemEvent) {
        // アイテムドロップ時にチェック
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            checkAndUpdateCache(event.player)
        }, 1L)
    }

    @EventHandler
    fun onPickupItem(event: PlayerPickupItemEvent) {
        // アイテム拾得時にチェック
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            checkAndUpdateCache(event.player)
        }, 1L)
    }

    /**
     * 思念アイテムの状態をチェックし、変更があればキャッシュを更新
     */
    private fun checkAndUpdateCache(player: Player) {
        val profession = professionManager.getPlayerProfession(player.uniqueId)?.profession ?: return
        val hasToken = PrestigeToken.hasTokenInHotbarOrOffhand(player, profession)
        val previousState = lastTokenState[player]

        // 状態が変化した場合のみキャッシュを更新
        if (previousState != hasToken) {
            lastTokenState[player] = hasToken
            val playerProf = professionManager.getPlayerProfession(player.uniqueId) ?: return
            SkillEffectEngine.rebuildCache(
                player.uniqueId,
                playerProf.acquiredSkills,
                profession,
                playerProf.prestigeSkills,
                playerProf.skillActivationStates
            )
        }
    }
}