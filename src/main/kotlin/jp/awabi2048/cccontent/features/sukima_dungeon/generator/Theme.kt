package jp.awabi2048.cccontent.features.sukima_dungeon.generator

import org.bukkit.Material

/**
 * ダンジョンテーマを表すデータクラス
 */
data class Theme(
    val id: String,
    val name: String,
    val path: String,
    val icon: Material,
    val tileSize: Int,
    val time: Long?,
    val gravity: Double,
    val voidYLimit: Double?,
    val requiredTier: Int,
    val structures: Map<StructureType, List<String>> = emptyMap()
) {
    companion object {
        fun fromConfigMap(
            id: String,
            map: Map<String, Any>
        ): Theme {
            @Suppress("UNCHECKED_CAST")
            return Theme(
                id = id,
                name = map["name"] as? String ?: id,
                path = map["path"] as? String ?: id,
                icon = runCatching {
                    Material.valueOf((map["icon"] as? String ?: "GRASS_BLOCK").uppercase())
                }.getOrDefault(Material.GRASS_BLOCK),
                tileSize = (map["tile_size"] as? Number)?.toInt() ?: 16,
                time = (map["time"] as? Number)?.toLong(),
                gravity = (map["gravity"] as? Number)?.toDouble() ?: 1.0,
                voidYLimit = (map["void_y_limit"] as? Number)?.toDouble(),
                requiredTier = (map["required_tier"] as? Number)?.toInt() ?: 1
            )
        }
    }
}

/**
 * ストラクチャーの種類を表す列挙型
 */
enum class StructureType {
    ENTRANCE,   // エントランス（開始地点）
    STRAIGHT,   // 直線（2方向接続）
    CORNER,     // コーナー（2方向接続、90度ターン）
    T_SHAPE,    // T字（3方向接続）
    CROSS,      // 十字（4方向接続）
    DEADEND,    // 行き止まり（1方向接続）
    MINIBOSS,   // ミニボス部屋（特殊）
    TRAP;       // トラップ部屋（特殊）
    
    companion object {
        fun fromConnectionCount(count: Int): StructureType = when (count) {
            0 -> DEADEND
            1 -> DEADEND
            2 -> STRAIGHT   // または CORNER（確率で分け可能）
            3 -> T_SHAPE
            4 -> CROSS
            else -> CROSS
        }
    }
}