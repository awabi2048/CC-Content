package jp.awabi2048.cccontent

import jp.awabi2048.cccontent.command.CCCommand
import jp.awabi2048.cccontent.command.GiveCommand
import jp.awabi2048.cccontent.items.CustomItemManager
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
        
        // コマンド登録
        val giveCommand = GiveCommand()
        val ccCommand = CCCommand(giveCommand)
        
        getCommand("cc")?.setExecutor(ccCommand)
        getCommand("cc")?.tabCompleter = giveCommand
        
        logger.info("CC-Content v${description.version} が有効化されました")
        logger.info("作成者: ${description.authors}")
    }
    
    override fun onDisable() {
        logger.info("CC-Content v${description.version} が無効化されました")
    }
}
