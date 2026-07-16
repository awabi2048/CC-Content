package jp.awabi2048.cccontent.features.resourcecollection

import jp.awabi2048.cccontent.features.rank.profession.Profession
import org.bukkit.Material

enum class CollectionAction {
    HARVEST,
    CRAFT
}

data class CollectionRule(
    val id: String,
    val material: Material,
    val profession: Profession,
    val experience: Long,
    val minimumAge: Int?
)

data class CollectionRewardKey(
    val action: CollectionAction,
    val playerId: String,
    val sourceId: Any
)

/**
 * 1つのゲームイベントから同じ報酬を二重に付与しないための境界です。
 * クラフトはイベント単位、採取はブロック破壊単位で呼び出します。
 */
class CollectionRewardGuard {
    private val granted = HashSet<CollectionRewardKey>()

    fun tryReserve(key: CollectionRewardKey): Boolean = granted.add(key)

    fun tryGrant(key: CollectionRewardKey, grant: () -> Unit): Boolean {
        if (!tryReserve(key)) return false
        grant()
        return true
    }

    fun clear() = granted.clear()
}
