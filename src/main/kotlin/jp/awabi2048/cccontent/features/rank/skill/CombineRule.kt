package jp.awabi2048.cccontent.features.rank.skill

/**
 * スキルエフェクトの合成方法を定義する enum
 *
 * - ADD: 効果値を加算する（深い順に適用）
 * - MULTIPLY: 効果値を乗算する（深い順に適用）
 * - REPLACE: 最深スキルの値で置き換え（浅いスキルは無視）
 * - MAX: 複数スキルの中で最大値を使用
 * - MAX_BY_DEPTH: ツリー深度による最大値（レガシー）
 */
enum class CombineRule {
    ADD,
    MULTIPLY,
    REPLACE,
    MAX,
    MAX_BY_DEPTH
}
