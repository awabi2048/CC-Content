package jp.awabi2048.cccontent.features.arena.generator

/**
 * 難易度starと参加人数上限の中央対応表。
 * theme yml側の max_participants 個別指定は廃止し、全themeでこの対応に従う。
 * （旧データ実績：★1=2人、★2=3人、★3=4人、★4=6人）
 */
object ArenaThemeDifficulty {
    fun maxParticipantsForStar(difficultyStar: Int): Int {
        return when {
            difficultyStar <= 1 -> 2
            difficultyStar == 2 -> 3
            difficultyStar == 3 -> 4
            else -> 6
        }
    }
}
