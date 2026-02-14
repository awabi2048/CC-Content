package jp.awabi2048.cccontent.features.rank.profession

/**
 * 経験値獲得時に表示するボスバーの表示モード
 */
enum class BossBarDisplayMode(
    val id: String,
    val displayName: String,
    val durationTicks: Long,
    val visible: Boolean
) {
    LONG("long", "表示する（長め）", 200L, true),
    SHORT("short", "表示する（短め）", 60L, true),
    HIDDEN("hidden", "表示しない", 0L, false);

    fun next(): BossBarDisplayMode {
        val modes = entries
        val nextIndex = (ordinal + 1) % modes.size
        return modes[nextIndex]
    }

    companion object {
        fun fromId(id: String): BossBarDisplayMode? {
            return entries.firstOrNull { it.id == id }
        }
    }
}
