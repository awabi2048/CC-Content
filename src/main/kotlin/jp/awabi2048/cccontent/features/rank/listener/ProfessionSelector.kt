package jp.awabi2048.cccontent.features.rank.listener

import org.bukkit.entity.Player

/**
 * 職業選択GUIを開くためのインターフェース
 */
interface ProfessionSelector {
    /**
     * 職業選択GUIを開く
     */
    fun openProfessionSelectionGui(player: Player): Boolean
}
