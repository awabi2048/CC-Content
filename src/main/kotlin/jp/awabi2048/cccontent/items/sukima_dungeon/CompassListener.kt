package jp.awabi2048.cccontent.items.sukima_dungeon

import jp.awabi2048.cccontent.items.sukima_dungeon.common.MessageManager
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.*

/**
 * コンパスのShift長押し検知リスナー
 * 元の SukimaDungeon の CompassListener を再現
 */
class CompassListener(
    private val plugin: JavaPlugin,
    private val messageManager: MessageManager
) : Listener {
    
    // アクティブなコンパス使用を追跡
    private val activeCompassUsers: MutableMap<UUID, BukkitTask> = mutableMapOf()
    private val compassCooldowns: MutableMap<UUID, Long> = mutableMapOf()
    
    // コンパス検知タスク
    private val detectionTasks: MutableMap<UUID, BukkitTask> = mutableMapOf()
    
    @EventHandler
    fun onPlayerToggleSneak(event: PlayerToggleSneakEvent) {
        val player = event.player
        val itemInHand = player.inventory.itemInMainHand
        
        // アイテムがコンパスか確認
        if (!isCompassItem(itemInHand)) return
        
        // ダンジョン内チェック（仮実装）
        if (!isInDungeon(player)) {
            messageManager.sendError(player, "error.not_in_dungeon")
            return
        }
        
        if (event.isSneaking) {
            // Shiftを押し始めた
            startCompassActivation(player, itemInHand)
        } else {
            // Shiftを離した
            stopCompassActivation(player)
        }
    }
    
    /**
     * コンパスアイテムかどうかを判定
     */
    private fun isCompassItem(item: org.bukkit.inventory.ItemStack): Boolean {
        if (item.type != org.bukkit.Material.POISONOUS_POTATO) return false
        
        val meta = item.itemMeta ?: return false
        val pdc = meta.persistentDataContainer
        
        val itemType = pdc.get(
            NamespacedKey(plugin, "sukima_item"),
            PersistentDataType.STRING
        )
        
        return itemType == "compass"
    }
    
    /**
     * ダンジョン内にいるかどうか（仮実装）
     * TODO: 実際のダンジョン判定ロジックを実装
     */
    private fun isInDungeon(player: Player): Boolean {
        // 仮実装：常にtrueを返す
        // 実際の実装では、ワールドや領域をチェックする必要がある
        return true
    }
    
    /**
     * コンパス起動を開始
     */
    private fun startCompassActivation(player: Player, compassItem: org.bukkit.inventory.ItemStack) {
        val playerId = player.uniqueId
        
        // クールダウン確認
        val cooldownEnd = compassCooldowns[playerId] ?: 0
        if (cooldownEnd > System.currentTimeMillis()) {
            val remaining = ((cooldownEnd - System.currentTimeMillis()) / 1000).toInt()
            messageManager.sendCompassCooldown(player, remaining)
            return
        }
        
        // 既にアクティブな場合はキャンセル
        activeCompassUsers[playerId]?.cancel()
        
        // コンパスのパラメータを取得
        val meta = compassItem.itemMeta ?: return
        val pdc = meta.persistentDataContainer
        
        val tier = pdc.get(
            NamespacedKey(plugin, "compass_tier"),
            PersistentDataType.INTEGER
        ) ?: 1
        
        val duration = pdc.get(
            NamespacedKey(plugin, "compass_time"),
            PersistentDataType.INTEGER
        ) ?: 30
        
        val radius = pdc.get(
            NamespacedKey(plugin, "compass_radius"),
            PersistentDataType.DOUBLE
        ) ?: 48.0
        
        val cooldown = pdc.get(
            NamespacedKey(plugin, "compass_cooldown"),
            PersistentDataType.INTEGER
        ) ?: 60
        
        // 起動メッセージ
        messageManager.sendCompassActivate(player, tier, duration)
        
        // アクティベーションタスクを開始（長押し検知）
        val activationTask = Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (player.isSneaking && player.inventory.itemInMainHand == compassItem) {
                // 長押し成功：検知を開始
                startDetection(player, tier, duration, radius, cooldown)
            } else {
                // 長押し失敗
                stopCompassActivation(player)
            }
        }, 20L) // 1秒後にチェック
        
        activeCompassUsers[playerId] = activationTask
    }
    
    /**
     * コンパス起動を停止
     */
    private fun stopCompassActivation(player: Player) {
        val playerId = player.uniqueId
        
        // アクティベーションタスクをキャンセル
        activeCompassUsers[playerId]?.cancel()
        activeCompassUsers.remove(playerId)
        
        // 検知タスクを停止
        detectionTasks[playerId]?.cancel()
        detectionTasks.remove(playerId)
        
        messageManager.sendCompassDeactivate(player)
    }
    
    /**
     * ワールドの芽検知を開始
     */
    private fun startDetection(
        player: Player,
        tier: Int,
        duration: Int,
        radius: Double,
        cooldown: Int
    ) {
        val playerId = player.uniqueId
        
        // 検知タスクを作成
        val detectionTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            if (!player.isOnline || !player.isSneaking) {
                stopCompassActivation(player)
                return@Runnable
            }
            
            // ワールドの芽を検索
            val sproutLocation = findNearestSprout(player, radius)
            
            if (sproutLocation != null) {
                val distance = sproutLocation.distance(player.location)
                val direction = getDirection(player.location.yaw, sproutLocation)
                
                messageManager.sendCompassDetected(player, distance, direction)
                
                // デバフ効果を適用（Tierに応じて）
                applyDebuffs(player, tier)
            } else {
                messageManager.send(player, "compass.no_sprouts")
            }
        }, 0L, 20L) // 1秒ごとに検知
        
        detectionTasks[playerId] = detectionTask
        
        // 継続時間後に自動停止
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            stopCompassActivation(player)
            setCooldown(playerId, cooldown)
        }, (duration * 20).toLong())
    }
    
    /**
     * 最も近いワールドの芽を検索
     * TODO: 実際のワールドの芽エンティティ/ブロックを検索するロジックを実装
     */
    private fun findNearestSprout(player: Player, radius: Double): org.bukkit.Location? {
        // 仮実装：常にnullを返す
        // 実際の実装では、MANGROVE_PROPAGULE ブロックやエンティティを検索する必要がある
        return null
    }
    
    /**
     * 方角を文字列で取得
     */
    private fun getDirection(playerYaw: Float, targetLocation: org.bukkit.Location): String {
        // 簡易実装
        return "北"
    }
    
    /**
     * デバフ効果を適用
     */
    private fun applyDebuffs(player: Player, tier: Int) {
        // Tierに応じたデバフを適用
        // 実際の実装では、ポーション効果を適用する
        when (tier) {
            1 -> {
                // 飢餓 I
                player.addPotionEffect(org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.HUNGER,
                    100, // 5秒
                    0
                ))
            }
            2 -> {
                // 飢餓 I + 盲目 I
                player.addPotionEffect(org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.HUNGER,
                    100,
                    0
                ))
                player.addPotionEffect(org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.BLINDNESS,
                    100,
                    0
                ))
            }
            3 -> {
                // 飢餓 I + 盲目 I + 鈍化 II
                player.addPotionEffect(org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.HUNGER,
                    100,
                    0
                ))
                player.addPotionEffect(org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.BLINDNESS,
                    100,
                    0
                ))
                player.addPotionEffect(org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.SLOWNESS,
                    100,
                    1
                ))
            }
        }
    }
    
    /**
     * クールダウンを設定
     */
    private fun setCooldown(playerId: UUID, cooldownSeconds: Int) {
        val cooldownEnd = System.currentTimeMillis() + (cooldownSeconds * 1000L)
        compassCooldowns[playerId] = cooldownEnd
    }
    
    /**
     * クリーンアップ（プラグイン無効化時など）
     */
    fun cleanup() {
        activeCompassUsers.values.forEach { it.cancel() }
        detectionTasks.values.forEach { it.cancel() }
        activeCompassUsers.clear()
        detectionTasks.clear()
        compassCooldowns.clear()
    }
}