package jp.awabi2048.cccontent.features.sukima_dungeon

import org.bukkit.World

const val SUKIMA_DUNGEON_WORLD_PREFIX = "sukima_dungeon."

fun isSukimaDungeonWorldName(worldName: String): Boolean {
    return worldName.startsWith(SUKIMA_DUNGEON_WORLD_PREFIX)
}

fun isSukimaDungeonWorld(world: World): Boolean {
    return isSukimaDungeonWorldName(world.name)
}
