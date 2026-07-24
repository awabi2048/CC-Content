package jp.awabi2048.cccontent.features.rank.gui

import jp.awabi2048.cccontent.features.rank.profession.Profession

/**
 * 職業ギルドメニューで共通利用する職業アイコンの配置です。
 *
 * 描画とクリック判定の双方でこの定義を利用し、表示と選択結果のずれを防ぎます。
 */
object ProfessionGuildLayout {
    @JvmField
    val PROFESSION_SLOTS: Map<Int, Profession> = linkedMapOf(
        20 to Profession.LUMBERJACK,
        21 to Profession.MINER,
        22 to Profession.FARMER,
        23 to Profession.FISHER,
        24 to Profession.BREWER,
        29 to Profession.COOK,
        30 to Profession.CARPENTER,
        31 to Profession.GARDENER,
        32 to Profession.SWORDSMAN,
        33 to Profession.WARRIOR
    )
}
