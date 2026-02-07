package jp.awabi2048.cccontent.features.sukima_dungeon.generator

import org.bukkit.Material
import org.bukkit.structure.Structure

data class Theme(
    val id: String,
    val icon: Material,
    val tileSize: Int,
    val time: Long?,
    val gravity: Double,
    val voidYLimit: Double?,
    val requiredTier: Int,
    val structures: Map<StructureType, List<Structure>>
) {
    fun getDisplayName(player: org.bukkit.entity.Player? = null): String {
        return jp.awabi2048.cccontent.features.sukima_dungeon.MessageManager.getMessage(player, "sukima_dungeon.theme.$id.name")
    }

    fun getDescription(player: org.bukkit.entity.Player? = null): String {
        return jp.awabi2048.cccontent.features.sukima_dungeon.MessageManager.getMessage(player, "sukima_dungeon.theme.$id.description")
    }

    fun getOageMessages(player: org.bukkit.entity.Player? = null): List<String> {
        return jp.awabi2048.cccontent.features.sukima_dungeon.MessageManager.getList(player, "sukima_dungeon.theme.$id.oage_messages")
    }
}
