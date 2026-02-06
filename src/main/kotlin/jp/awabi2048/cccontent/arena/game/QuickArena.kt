package jp.awabi2048.cccontent.arena.game

import jp.awabi2048.cccontent.arena.ArenaMain
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.WorldCreator
import java.util.UUID

/**
 * クイック難度のアリーナゲーム（短時間）
 */
class QuickArena(
    sessionUUID: UUID = UUID.randomUUID()
) : Generic("quick", sessionUUID) {
    
    override val maxPlayers: Int = 2
    override val maxWaves: Int = 5
    override val timeLimit: Long = 900000  // 15分
    
    // ゲーム固有設定
    private val mobsPerWave = 8
    private val waveInterval = 20000  // 20秒（通常より短い）
    
    private var waveStartTime: Long = 0
    
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
                // ワールド設定
                world.pvp = true
                world.difficulty = org.bukkit.Difficulty.NORMAL
                world.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false)
                world.setGameRule(org.bukkit.GameRule.DO_FIRE_TICK, false)
            }
            
            ArenaMain.getPlugin().logger.info("セッションワールド作成: $worldName")
        } catch (e: Exception) {
            ArenaMain.getPlugin().logger.severe("セッションワールド作成失敗: ${e.message}")
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
                
                ArenaMain.getPlugin().logger.info("セッションワールド削除: ${world.name}")
            } catch (e: Exception) {
                ArenaMain.getPlugin().logger.warning("セッションワールド削除失敗: ${e.message}")
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
        
        ArenaMain.getPlugin().logger.info("クイックウェーブ開始: $wave (セッション: $sessionUUID)")
        
        // モブをスポーン（簡易実装、通常より少ない）
        spawnMobs(wave)
        
        // 次のウェーブを予約
        scheduleNextWave(wave)
    }
    
    /**
     * モブをスポーン（フェーズ4で詳細実装）
     */
    private fun spawnMobs(wave: Int) {
        sessionWorld ?: return
        
        val mobCount = mobsPerWave + (wave - 1) * 1  // ウェーブごとに緩やかに増加
        
        // TODO: モブスポーン処理の実装
        // ArenaDataFile.mobDefinition から設定を読み込む
        // sessionWorld にモブをスポーン
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
        ArenaMain.getPlugin().logger.info("クイックアリーナ終了: $sessionUUID")
        
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
            
            // TODO: スコアからドロップ計算（通常より少ない）
            // TODO: 報酬アイテムをプレイヤーのインベントリに追加
        }
    }
}
