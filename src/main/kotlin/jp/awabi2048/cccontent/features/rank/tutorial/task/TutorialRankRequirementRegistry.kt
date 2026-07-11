package jp.awabi2048.cccontent.features.rank.tutorial.task

import jp.awabi2048.cccontent.features.rank.tutorial.TutorialRank

/**
 * チュートリアルランクの進行要件を固定定義する。
 *
 * 各要件は「現在ランクから次ランクへ進むために満たす条件」を表す。
 */
object TutorialRankRequirementRegistry {
    private val requirements = mapOf(
        TutorialRank.NEWBIE to TaskRequirement(
            playTimeMin = 15,
            requiresMyWorldCreated = true
        ),
        TutorialRank.VISITOR to TaskRequirement(
            playTimeMin = 60,
            activeOverworldMin = 60,
            diamondOreMines = 20,
            requiresNetherPortalIgnited = true
        ),
        TutorialRank.PIONEER to TaskRequirement(
            playTimeMin = 120,
            activeNetherResourceMin = 60,
            enderEyeCrafts = 12,
            requiresEndPortalOpened = true
        ),
        TutorialRank.ADVENTURER to TaskRequirement(
            playTimeMin = 1_440,
            bossKills = mapOf("ender_dragon" to 1)
        ),
        TutorialRank.ATTAINER to TaskRequirement()
    )

    private val icons = mapOf(
        TutorialRank.NEWBIE to "CARVED_PUMPKIN",
        TutorialRank.VISITOR to "CARVED_PUMPKIN",
        TutorialRank.PIONEER to "CARVED_PUMPKIN",
        TutorialRank.ADVENTURER to "CARVED_PUMPKIN",
        TutorialRank.ATTAINER to "JACK_O_LANTERN"
    )

    fun getRequirement(rankId: String): TaskRequirement {
        val rank = TutorialRank.entries.firstOrNull { it.name.equals(rankId, ignoreCase = true) }
            ?: throw IllegalArgumentException("Unknown tutorial rank ID: '$rankId'")
        return requirements[rank]
            ?: error("Tutorial rank requirement is not defined: '${rank.name}'")
    }

    fun getRankIcon(rankId: String): String? {
        return TutorialRank.entries.firstOrNull { it.name.equals(rankId, ignoreCase = true) }
            ?.let { icons[it] }
    }

    fun getLowestDefinedRankId(): String = TutorialRank.NEWBIE.name
}
