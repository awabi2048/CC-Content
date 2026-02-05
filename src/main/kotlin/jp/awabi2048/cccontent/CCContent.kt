package jp.awabi2048.cccontent

import jp.awabi2048.cccontent.command.CCCommand
import jp.awabi2048.cccontent.command.GiveCommand
import jp.awabi2048.cccontent.items.CustomItemManager
import jp.awabi2048.cccontent.items.misc.BigLight
import jp.awabi2048.cccontent.items.misc.SmallLight
import jp.awabi2048.cccontent.items.misc.GulliverItemListener
import org.bukkit.plugin.java.JavaPlugin

class CCContent : JavaPlugin() {
    
    companion object {
        lateinit var instance: CCContent
            private set
    }
    
    override fun onEnable() {
        instance = this
        
        // ロガーをCustomItemManagerに設定
        CustomItemManager.setLogger(logger)
        
        // アイテム登録
        registerCustomItems()
        
        // コマンド登録
        val giveCommand = GiveCommand()
        val ccCommand = CCCommand(giveCommand)
        
        getCommand("cc")?.setExecutor(ccCommand)
        getCommand("cc")?.tabCompleter = giveCommand
        
        // リスナー登録
        server.pluginManager.registerEvents(GulliverItemListener(), this)
        
        logger.info("CC-Content v${description.version} が有効化されました")
        logger.info("作成者: ${description.authors}")
        logger.info("登録されたアイテム数: ${CustomItemManager.getItemCount()}")
    }
    
    private fun registerCustomItems() {
        // GulliverLight アイテム
        CustomItemManager.register(BigLight())
        CustomItemManager.register(SmallLight())
    }
    
    override fun onDisable() {
        logger.info("CC-Content v${description.version} が無効化されました")
    }
}
