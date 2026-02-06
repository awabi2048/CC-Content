package jp.awabi2048.cccontent.arena

import jp.awabi2048.cccontent.CCContent
import jp.awabi2048.cccontent.arena.config.ArenaDataFile
// import jp.awabi2048.cccontent.arena.game.Generic
// import jp.awabi2048.cccontent.arena.gui.GuiListener
// import jp.awabi2048.cccontent.arena.command.ArenaCommand
// import jp.awabi2048.cccontent.arena.command.ArenaAdminCommand
// import jp.awabi2048.cccontent.arena.command.PartyCommand
// import jp.awabi2048.cccontent.arena.game.GameListener
// import jp.awabi2048.cccontent.arena.item.ItemListener
// import jp.awabi2048.cccontent.arena.item.SoulBottleListener
// import jp.awabi2048.cccontent.arena.item.BoosterListener
// import jp.awabi2048.cccontent.arena.item.MobDropSackListener
// import jp.awabi2048.cccontent.arena.item.HunterTalismanListener
// import jp.awabi2048.cccontent.arena.item.GolemTalismanListener
// import jp.awabi2048.cccontent.arena.listener.ItemUpdateListener
// import jp.awabi2048.cccontent.arena.player.PlayerListener
// import jp.awabi2048.cccontent.arena.party.Party
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

/**
 * アリーナ機能のメイン管理クラス。
 * KotaArenaの機能をCC-Content内で管理する。
 */
object ArenaMain {
    val prefix = "§7«§cArena§7»"
    val displayScoreboardMap = mutableMapOf<Player, org.bukkit.scoreboard.Objective>()
    val spawnSessionKillCount = mutableMapOf<String, Int>()
    
    /** セッションごとのプレイヤー別キル数 (sessionUUID -> (playerUUID -> killCount)) */
    val playerSessionKillCount = mutableMapOf<String, MutableMap<UUID, Int>>()
    
    /** セッションごとのプレイヤー別デス数 (sessionUUID -> (playerUUID -> deathCount)) */
    val playerSessionDeathCount = mutableMapOf<String, MutableMap<UUID, Int>>()
    val activeSession = mutableListOf<Any>() // Generic は後で追加
    val activeParty = mutableListOf<Any>() // Party は後で追加
    lateinit var lobbyOriginLocation: Location
    // lateinit var guiListener: GuiListener
    
    private lateinit var plugin: JavaPlugin
    
    /**
     * アリーナシステムを初期化する。
     * CCContent.onEnable()から呼び出される。
     */
    fun initialize(plugin: JavaPlugin) {
        this.plugin = plugin
        
        // 設定ファイルを読み込み
        try {
            ArenaDataFile.loadAll()
            plugin.logger.info("アリーナ設定ファイルの読み込みに成功しました")
        } catch (e: Exception) {
            plugin.logger.severe("アリーナ設定ファイルの読み込みに失敗: ${e.message}")
            e.printStackTrace()
            return
        }
        
        // デフォルトのロビー位置を設定
        lobbyOriginLocation = Location(Bukkit.getWorlds()[0], 0.0, 100.0, 0.0)
        
        // 言語ファイルはCCContentの既存システムを使用
        
        // コマンドを登録（plugin.ymlで定義済み）- フェーズ3で実装
        // plugin.getCommand("arena")?.setExecutor(ArenaCommand())
        // val arenaAdminCommand = ArenaAdminCommand()
        // plugin.getCommand("arenaa")?.setExecutor(arenaAdminCommand)
        // plugin.getCommand("arenaa")?.tabCompleter = arenaAdminCommand
        // plugin.getCommand("party")?.setExecutor(PartyCommand())
        
        // リスナーを登録 - フェーズ3で実装
        // guiListener = GuiListener()
        // plugin.server.pluginManager.registerEvents(GameListener(), plugin)
        // plugin.server.pluginManager.registerEvents(guiListener, plugin)
        // plugin.server.pluginManager.registerEvents(ItemListener(), plugin)
        // 
        // plugin.server.pluginManager.registerEvents(PlayerListener(), plugin)
        // plugin.server.pluginManager.registerEvents(SoulBottleListener(), plugin)
        // plugin.server.pluginManager.registerEvents(BoosterListener(), plugin)
        // plugin.server.pluginManager.registerEvents(MobDropSackListener(), plugin)
        // plugin.server.pluginManager.registerEvents(HunterTalismanListener(), plugin)
        // plugin.server.pluginManager.registerEvents(GolemTalismanListener(), plugin)
        // plugin.server.pluginManager.registerEvents(ItemUpdateListener(), plugin)
        
        // 前回実行時の古いセッションワールドをクリーンアップ
        cleanupOldSessionWorlds()
        
        plugin.logger.info("アリーナ機能が初期化されました")
    }
    
    /**
     * 前回実行時に残った古いセッションワールドを削除する。
     */
    private fun cleanupOldSessionWorlds() {
        try {
            val worldContainer = Bukkit.getWorldContainer()
            val sessionWorlds = worldContainer.listFiles { file ->
                file.isDirectory && file.name.startsWith("arena_session.")
            } ?: return
            
            sessionWorlds.forEach { worldFolder ->
                try {
                    val worldName = worldFolder.name
                    
                    // ロード済みの場合はアンロード
                    val world = Bukkit.getWorld(worldName)
                    if (world != null) {
                        Bukkit.unloadWorld(world, false)
                    }
                    
                    // フォルダを削除
                    worldFolder.deleteRecursively()
                    plugin.logger.info("古いセッションワールドを削除しました: $worldName")
                } catch (e: Exception) {
                    plugin.logger.warning("ワールド ${worldFolder.name} のクリーンアップに失敗: ${e.message}")
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("古いセッションワールドのクリーンアップに失敗: ${e.message}")
        }
    }
    
    /**
     * プラグイン無効化時のクリーンアップ処理。
     * CCContent.onDisable()から呼び出される。
     */
    fun cleanup() {
        // アクティブなセッションをクリーンアップ
        // activeSession.forEach { it.stop() }
        activeSession.clear()
        activeParty.clear()
        
        plugin.logger.info("アリーナ機能のクリーンアップが完了しました")
    }
    
    /**
     * プラグインインスタンスを取得する。
     */
    fun getPlugin(): JavaPlugin = plugin
}