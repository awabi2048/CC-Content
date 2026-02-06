package jp.awabi2048.cccontent.items.misc

import jp.awabi2048.cccontent.CCContent
import org.bukkit.Bukkit
import org.bukkit.attribute.Attribute
import org.bukkit.persistence.PersistentDataType

/**
 * GulliverLightのスケール管理クラス
 * 統合元プラグインのScaleManagerをそのまま移植
 */
class GulliverScaleManager : Runnable {
    
    override fun run() {
        val config = GulliverConfig
        
        val bigLightMax = config.getBigLightMaxScale()
        val bigLightSpeed = config.getBigLightScaleSpeed()
        
        val smallLightMin = config.getSmallLightMinScale()
        val smallLightSpeed = config.getSmallLightScaleSpeed()

        for (player in Bukkit.getOnlinePlayers()) {
            if (!player.isHandRaised) continue
            if (player.isSneaking) continue

            // Check main hand
            val item = player.inventory.itemInMainHand
            val meta = item.itemMeta ?: continue
            val container = meta.persistentDataContainer

            val scaleAttribute = player.getAttribute(Attribute.valueOf("GENERIC_SCALE")) ?: continue
            val currentScale = scaleAttribute.baseValue

            if (container.has(GulliverItems.BIG_LIGHT_KEY, PersistentDataType.BYTE)) {
                // Grow
                val newScale = (currentScale + bigLightSpeed).coerceAtMost(bigLightMax)
                if (newScale != currentScale) {
                    scaleAttribute.baseValue = newScale
                }
            } else if (container.has(GulliverItems.SMALL_LIGHT_KEY, PersistentDataType.BYTE)) {
                // Shrink
                val newScale = (currentScale - smallLightSpeed).coerceAtLeast(smallLightMin)
                if (newScale != currentScale) {
                    scaleAttribute.baseValue = newScale
                }
            }
        }
    }
}