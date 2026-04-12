package jp.awabi2048.cccontent.mob

import org.bukkit.entity.EntityType
import java.util.Locale

object MobRewardCategory {
    fun resolveEntityCategoryId(baseEntityType: EntityType): String {
        return when (baseEntityType) {
            EntityType.CAVE_SPIDER -> "spider"
            EntityType.END_CRYSTAL -> "spirit"
            EntityType.WITHER -> "wither_skeleton"
            EntityType.VEX -> "spirit"
            else -> sanitizeTypeId(baseEntityType.name)
        }
    }

    fun sanitizeTypeId(typeId: String): String {
        return typeId.trim().lowercase(Locale.ROOT).replace(Regex("[^a-z0-9_]+"), "_")
    }
}
