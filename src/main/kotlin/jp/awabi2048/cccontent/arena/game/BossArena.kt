package jp.awabi2048.cccontent.arena.game

import jp.awabi2048.cccontent.arena.ArenaMain
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.WorldCreator
import org.bukkit.entity.LivingEntity
import java.util.UUID

/**
 * 上級難度のアリーナゲーム（ボスアリーナ）
 */
class BossArena(
    sessionUUID: UUID = UUID.randomUUID()
) : Generic("boss", sessionUUID) {
    
    override val maxPlayers: Int = 4
    override val maxWaves: Int = 3
    override val timeLimit: Long = 2700000  // 45分
    
    // ゲーム固有設定
    private val mobsPerWave = 15
    private val waveInterval = 45000  // 45秒
    
    private var waveStartTime: Long = 0
    private var bossEntity: LivingEntity? = null
    
    override fun start() {
        super.start()
        
        // 最初のウェーブを開始
        startWave(1)
    }
    
    override fun prepareSessionWorld() {
        try {
            // セッションワールドを作成
            val worldName = "arena_session.${sessionUUID}"
            
            // 既に存在するなら削除
            val existingWorld = Bukkit.getWorld(worldName)
            if (existingWorld != null) {
                Bukkit.unloadWorld(existingWorld, false)
            }
            
            // ワールドを作成（ボイドワールド）
            val worldCreator = WorldCreator(worldName)
            worldCreator.environment(World.Environment.NORMAL)
            worldCreator.type(org.bukkit.WorldType.FLAT)
            
            sessionWorld = worldCreator.createWorld()
            
            sessionWorld?.let { world ->
                // ワールド設定（上級難度）
                world.pvp = true
                world.difficulty = org.bukkit.Difficulty.HARD
                world.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false)
                world.setGameRule(org.bukkit.GameRule.DO_FIRE_TICK, true)  // ボスアリーナは火ダメージあり
            }
            
            ArenaMain.getPlugin().logger.info("ボスアリーナワールド作成: $worldName")
        } catch (e: Exception) {
            ArenaMain.getPlugin().logger.severe("ボスアリーナワールド作成失敗: ${e.message}")
            cancel()
        }
    }
    
    override fun cleanupSessionWorld() {
        sessionWorld?.let { world ->
            try {
                Bukkit.unloadWorld(world, false)
                
                // ワールドフォルダを削除
                val worldFolder = world.worldFolder
                worldFolder.deleteRecursively()
                
                ArenaMain.getPlugin().logger.info("ボスアリーナワールド削除: ${world.name}")
            } catch (e: Exception) {
                ArenaMain.getPlugin().logger.warning("ボスアリーナワールド削除失敗: ${e.message}")
            }
        }
    }
    
    override fun startWave(wave: Int) {
        if (wave > maxWaves) {
            // ゲーム終了
            finishGame()
            return
        }
        
        if (!isRunning) return
        
        currentWave = wave
        waveStartTime = System.currentTimeMillis()
        
        // スコアボード更新
        getPlayers().forEach { player ->
            updateScoreboard(player)
        }
        
        when (wave) {
            1, 2 -> {
                ArenaMain.getPlugin().logger.info("ボスアリーナウェーブ開始: $wave (セッション: $sessionUUID)")
                spawnMobs(wave)
            }
            3 -> {
                // 最終ウェーブ: ボス戦
                ArenaMain.getPlugin().logger.info("ボス戦開始: (セッション: $sessionUUID)")
                spawnBoss()
            }
        }
        
        // 次のウェーブを予約
        scheduleNextWave(wave)
    }
    
    /**
     * モブをスポーン（フェーズ4で詳細実装）
     */
    private fun spawnMobs(wave: Int) {
        sessionWorld ?: return
        
        val mobCount = mobsPerWave + (wave - 1) * 3
        
        // TODO: モブスポーン処理の実装（通常より強い）
        // ArenaDataFile.mobDefinition から難易度を上げて設定を読み込む
        // sessionWorld にモブをスポーン
    }
    
    /**
     * ボスをスポーン（フェーズ4で詳細実装）
     */
    private fun spawnBoss() {
        sessionWorld ?: return
        
        // TODO: ボスエンティティのスポーン処理
        // TODO: カスタムボス設定の読み込み
        // TODO: ボスのHP、攻撃力、スキル設定
    }
    
    /**
     * 次のウェーブをスケジュール
     */
    private fun scheduleNextWave(currentWave: Int) {
        val plugin = ArenaMain.getPlugin()
        val ticks = (waveInterval / 50).toLong()
        
        taskId = Bukkit.getScheduler().scheduleSyncDelayedTask(
            plugin,
            Runnable {
                // ウェーブが終了したかチェック
                if (isRunning && currentWave < maxWaves) {
                    startWave(currentWave + 1)
                }
            },
            ticks
        )
    }
    
    /**
     * ゲームを終了
     */
    private fun finishGame() {
        ArenaMain.getPlugin().logger.info("ボスアリーナ終了: $sessionUUID")
        
        // 報酬計算（フェーズ4で詳細実装）
        calculateRewards()
        
        // セッション終了
        stop()
    }
    
    /**
     * 報酬を計算（フェーズ4で詳細実装）
     */
    private fun calculateRewards() {
        getPlayers().forEach { player ->
            val score = getScore(player)
            val deaths = getDeaths(player)
            
            // TODO: スコアからドロップ計算（通常より多い高級アイテム）
            // TODO: ボス討伐ボーナス
            // TODO: 報酬アイテムをプレイヤーのインベントリに追加
        }
    }
}
