package jp.awabi2048.cccontent.items.sukima_dungeon

/**
 * ダンジョンティア定義
 * コンパスの性能やしおり（ブックマーク）の成功率を決定する
 */
enum class DungeonTier(
    /** 内部名 */
    val internalName: String,
    /** ティアレベル */
    val tier: Int,
    /** ティア名（表示用） */
    val displayName: String
) {
    /** ぼろぼろの */
    BROKEN("BROKEN", 1, "ぼろぼろの"),
    
    /** 擦り切れた */
    WORN("WORN", 2, "擦り切れた"),
    
    /** 色褪せた */
    FADED("FADED", 3, "色褪せた"),
    
    /** 新品の */
    NEW("NEW", 4, "新品の");
    
    companion object {
        fun fromInternalName(name: String): DungeonTier? {
            return values().find { it.internalName == name }
        }
    }
}
