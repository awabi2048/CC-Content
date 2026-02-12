package jp.awabi2048.cccontent.util

import java.util.logging.Logger

/**
 * 各featureの初期化状態を管理し、統一フォーマットで出力するロガー
 */
class FeatureInitializationLogger(private val logger: Logger) {
    
    enum class Status {
        SUCCESS,    // ✅
        WARNING,    // ⚠️
        FAILURE     // ❌
    }
    
    data class FeatureInfo(
        val name: String,
        var status: Status = Status.SUCCESS,
        val summaryMessages: MutableList<String> = mutableListOf(),
        val detailMessages: MutableList<String> = mutableListOf()
    )
    
    private val features = mutableMapOf<String, FeatureInfo>()
    
    /**
     * featureの初期化情報を登録
     */
    fun registerFeature(name: String) {
        features[name] = FeatureInfo(name)
    }
    
    /**
     * featureの状態を設定
     */
    fun setStatus(featureName: String, status: Status) {
        features[featureName]?.status = status
    }
    
    /**
     * featureにサマリーメッセージを追加（一覧に表示される）
     * 例: "スキル7種登録"
     */
    fun addSummaryMessage(featureName: String, message: String) {
        features[featureName]?.summaryMessages?.add(message)
    }
    
    /**
     * featureに詳細メッセージを追加（エラー/警告時のみ表示される）
     * 例: "[Arena] テーマ 'corrupted' は以下が不足: straight, corner"
     */
    fun addDetailMessage(featureName: String, message: String) {
        features[featureName]?.detailMessages?.add(message)
    }
    
    /**
     * 統一フォーマットで初期化結果を出力
     */
    fun printSummary() {
        if (features.isEmpty()) {
            return
        }
        
        // サマリーラインを作成
        val summaryLines = features.values.map { feature ->
            val statusIcon = when (feature.status) {
                Status.SUCCESS -> "✅"
                Status.WARNING -> "⚠️"
                Status.FAILURE -> "❌"
            }
            val summary = if (feature.summaryMessages.isNotEmpty()) {
                " (${feature.summaryMessages.joinToString(", ")})"
            } else {
                ""
            }
            "$statusIcon ${feature.name}: ${feature.status.name}$summary"
        }
        
        // 詳細メッセージが存在するかチェック
        val hasDetails = features.any { it.value.detailMessages.isNotEmpty() }
        
        // ヘッダーを出力
        logger.info("==========[CC-Content]===========")
        
        // サマリーを出力
        summaryLines.forEach { logger.info(it) }
        
        // 詳細情報を出力（ある場合のみ）
        if (hasDetails) {
            logger.info("")
            logger.info("==== 詳細情報 ====")
            features.forEach { (_, feature) ->
                feature.detailMessages.forEach { logger.info(it) }
            }
        }
        
        // 結論メッセージ
        logger.info("")
        val hasFailures = features.any { it.value.status == Status.FAILURE }
        val hasWarnings = features.any { it.value.status == Status.WARNING }
        
        val conclusion = when {
            hasFailures -> "読み込みに一部失敗しました"
            hasWarnings -> "読み込みに一部失敗/成功しました"
            else -> "読み込みに成功しました"
        }
        
        logger.info(conclusion)
        logger.info("==================================")
    }
}
