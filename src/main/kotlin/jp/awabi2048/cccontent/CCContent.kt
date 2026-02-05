package jp.awabi2048.cccontent

import org.bukkit.plugin.java.JavaPlugin

class CCContent : JavaPlugin() {
    
    override fun onEnable() {
        logger.info("CC-Content v${description.version} が有効化されました")
        logger.info("作成者: ${description.authors}")
    }
    
    override fun onDisable() {
        logger.info("CC-Content v${description.version} が無効化されました")
    }
}