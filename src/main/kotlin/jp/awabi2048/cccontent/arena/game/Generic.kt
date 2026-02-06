package jp.awabi2048.cccontent.arena.game

import jp.awabi2048.cccontent.arena.ArenaMain
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.scoreboard.Scoreboard
import java.util.UUID

/**
 * アリーナセッションの基本クラス
 * 通常アリーナ、ボスアリーナ、クイックアリーナの親クラス
 */
abstract class Generic(
    val arenaType: String,  // "normal", "boss", "quick"
    val sessionUUID: UUID = UUID.randomUUID()
) {
    
    // ====== ゲーム状態管理 ======
    enum class GameState {
        WAITING,      // 待機中
        STARTING,     // 開始準備
        RUNNING,      // 進行中
        FINISHED,     // 終了
        CANCELLED     // キャンセル
    }
    
    var gameState: GameState = GameState.WAITING
        protected set
    
    // ====== プレイヤー管理 ======
    private val players = mutableListOf<UUID>()
    private val playerScores = mutableMapOf<UUID, Int>()  // プレイヤーごとのスコア（キル数など）
    private val playerDeaths = mutableMapOf<UUID, Int>()  // プレイヤーごとのデス数
    
    // ====== ゲーム設定 ======
    abstract val maxPlayers: Int
    abstract val maxWaves: Int
    abstract val timeLimit: Long  // ミリ秒
    
    // ====== タイマー ======
    protected var startTime: Long = 0
    protected var taskId: Int = -1
    
    // ====== セッションワールド ======
    var sessionWorld: World? = null
        protected set
    var lobbyLocation: Location? = null
        protected set
    
    // ====== スコアボード ======
    private val scoreboards = mutableMapOf<UUID, org.bukkit.scoreboard.Objective>()
    
    // ====== ゲーム進行管理 ======
    protected var currentWave: Int = 0
    protected var isRunning: Boolean = false
    
    /**
     * セッションを開始する
     */
    open fun start() {
        if (gameState != GameState.WAITING) return
        
        gameState = GameState.STARTING
        startTime = System.currentTimeMillis()
        isRunning = true
        
        // セッションワールドを準備
        prepareSessionWorld()
        
        // プレイヤーをアリーナワールドにテレポート
        players.forEach { uuid ->
            val player = Bukkit.getPlayer(uuid) ?: return@forEach
            sessionWorld?.let {
                player.teleport(it.spawnLocation)
            }
        }
        
        gameState = GameState.RUNNING
        currentWave = 1
        
        ArenaMain.getPlugin().logger.info("アリーナセッション開始: $sessionUUID (タイプ: $arenaType)")
    }
    
    /**
     * セッションを停止する
     */
    open fun stop() {
        if (!isRunning) return
        
        gameState = GameState.FINISHED
        isRunning = false
        
        // タスクをキャンセル
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId)
        }
        
        // プレイヤーをロビーに帰還
        players.forEach { uuid ->
            val player = Bukkit.getPlayer(uuid) ?: return@forEach
            player.teleport(ArenaMain.lobbyOriginLocation)
        }
        
        // セッションワールドをクリーンアップ
        cleanupSessionWorld()
        
        ArenaMain.getPlugin().logger.info("アリーナセッション終了: $sessionUUID")
    }
    
    /**
     * セッションをキャンセルする（エラー時）
     */
    open fun cancel() {
        gameState = GameState.CANCELLED
        isRunning = false
        
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId)
        }
        
        players.forEach { uuid ->
            val player = Bukkit.getPlayer(uuid) ?: return@forEach
            player.teleport(ArenaMain.lobbyOriginLocation)
        }
        
        cleanupSessionWorld()
    }
    
    // ====== プレイヤー管理 ======
    
    /**
     * プレイヤーをセッションに追加
     */
    fun addPlayer(player: Player): Boolean {
        if (players.size >= maxPlayers) return false
        if (players.contains(player.uniqueId)) return false
        
        players.add(player.uniqueId)
        playerScores[player.uniqueId] = 0
        playerDeaths[player.uniqueId] = 0
        
        ArenaMain.displayScoreboardMap[player] = createScoreboard(player)
        
        return true
    }
    
    /**
     * プレイヤーをセッションから削除
     */
    fun removePlayer(player: Player) {
        players.remove(player.uniqueId)
        playerScores.remove(player.uniqueId)
        playerDeaths.remove(player.uniqueId)
        scoreboards.remove(player.uniqueId)
        ArenaMain.displayScoreboardMap.remove(player)
    }
    
    /**
     * プレイヤーがセッションに参加しているか確認
     */
    fun hasPlayer(player: Player): Boolean = players.contains(player.uniqueId)
    
    /**
     * セッション内のプレイヤー数を取得
     */
    fun getPlayerCount(): Int = players.size
    
    /**
     * セッション内のすべてのプレイヤーを取得
     */
    fun getPlayers(): List<Player> {
        return players.mapNotNull { Bukkit.getPlayer(it) }
    }
    
    // ====== スコア管理 ======
    
    /**
     * プレイヤーのスコアを増加（キル数等）
     */
    fun addScore(player: Player, score: Int) {
        playerScores[player.uniqueId] = (playerScores[player.uniqueId] ?: 0) + score
        updateScoreboard(player)
    }
    
    /**
     * プレイヤーのスコアを取得
     */
    fun getScore(player: Player): Int = playerScores[player.uniqueId] ?: 0
    
    /**
     * プレイヤーのデス数を増加
     */
    fun addDeath(player: Player) {
        playerDeaths[player.uniqueId] = (playerDeaths[player.uniqueId] ?: 0) + 1
        updateScoreboard(player)
    }
    
    /**
     * プレイヤーのデス数を取得
     */
    fun getDeaths(player: Player): Int = playerDeaths[player.uniqueId] ?: 0
    
    // ====== スコアボード管理 ======
    
    /**
     * スコアボードを作成
     */
    private fun createScoreboard(player: Player): org.bukkit.scoreboard.Objective {
        val scoreboardManager = Bukkit.getScoreboardManager() ?: return Bukkit.getScoreboardManager()!!.mainScoreboard.registerNewObjective("arena", "dummy", net.kyori.adventure.text.Component.text("§6§l＝ アリーナ ＝"))
        val scoreboard = scoreboardManager.newScoreboard
        val objective = scoreboard.registerNewObjective("arena_${player.uniqueId}", "dummy", net.kyori.adventure.text.Component.text("§6§l＝ アリーナ ＝"))
        objective.displaySlot = org.bukkit.scoreboard.DisplaySlot.SIDEBAR
        
        scoreboards[player.uniqueId] = objective
        return objective
    }
    
    /**
     * スコアボードを更新
     */
    protected fun updateScoreboard(player: Player) {
        val objective = scoreboards[player.uniqueId] ?: return
        
        // スコア表示を更新（実装例）
        val score = getScore(player)
        val deaths = getDeaths(player)
        val wave = currentWave
        
        objective.getScore("§7キル数: §e$score").score = 10
        objective.getScore("§7デス数: §e$deaths").score = 9
        objective.getScore("§7ウェーブ: §e$wave").score = 8
    }
    
    // ====== セッションワールド管理 ======
    
    /**
     * セッションワールドを準備（抽象メソッド）
     */
    protected abstract fun prepareSessionWorld()
    
    /**
     * セッションワールドをクリーンアップ（抽象メソッド）
     */
    protected abstract fun cleanupSessionWorld()
    
    // ====== ゲーム進行管理 ======
    
    /**
     * ウェーブを開始する（抽象メソッド）
     */
    protected abstract fun startWave(wave: Int)
    
    /**
     * 経過時間を取得（ミリ秒）
     */
    fun getElapsedTime(): Long = System.currentTimeMillis() - startTime
    
    /**
     * 経過時間が制限時間を超えたか確認
     */
    fun isTimeUp(): Boolean = getElapsedTime() > timeLimit
    
    /**
     * セッションIDを取得
     */
    fun getSessionId(): String = sessionUUID.toString()
}
