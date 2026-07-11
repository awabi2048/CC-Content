package jp.awabi2048.cccontent.features.rank.tutorial.task

/**
 * チュートリアル要件の参照口。
 *
 * 旧実装はYAMLを読んでいたが、ランク経路を固定仕様にしたため現在は
 * TutorialRankRequirementRegistry を唯一の正として返す。
 */
class TutorialTaskLoader {
    fun getRequirement(rankId: String): TaskRequirement {
        return TutorialRankRequirementRegistry.getRequirement(rankId)
    }

    fun getRankIcon(rankId: String): String? {
        return TutorialRankRequirementRegistry.getRankIcon(rankId)
    }

    fun getLowestDefinedRankId(): String? {
        return TutorialRankRequirementRegistry.getLowestDefinedRankId()
    }
}
