package jp.awabi2048.cccontent.features.sukima_dungeon

import jp.awabi2048.cccontent.features.sukima_dungeon.generator.VoidChunkGenerator
import jp.awabi2048.cccontent.CCContent
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.WorldCreator
import java.util.UUID

object DungeonManager {
    private val worldThemes = mutableMapOf<String, jp.awabi2048.cccontent.features.sukima_dungeon.generator.Theme>()

    fun registerTheme(world: World, theme: jp.awabi2048.cccontent.features.sukima_dungeon.generator.Theme) {
        worldThemes[world.name] = theme
    }

    fun getTheme(world: World): jp.awabi2048.cccontent.features.sukima_dungeon.generator.Theme? {
        return worldThemes[world.name]
    }

    fun createDungeonWorld(themeId: String? = null): World? {
        val uuid = UUID.randomUUID()
        val worldName = "dungeon_$uuid"
        return createOrLoadWorld(worldName, themeId)
    }

    fun loadDungeonWorld(worldName: String, themeId: String? = null): World? {
        if (!worldName.startsWith("dungeon_")) return null
        return createOrLoadWorld(worldName, themeId)
    }

    private fun createOrLoadWorld(worldName: String, themeId: String? = null): World? {
        val creator = WorldCreator(worldName)
        creator.generator(VoidChunkGenerator())
        
        // TODO: バイオーム設定は一時的にコメントアウト
        // if (themeId != null) {
        //     creator.biomeProvider(jp.awabi2048.cccontent.features.sukima_dungeon.generator.ThemeBiomeProvider(themeId))
        // }
        
        val world = creator.createWorld()
        world?.let {
            it.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false)
            it.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false)
            it.setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE, false)
            it.time = 6000
        }
        
        return world
    }

    fun deleteDungeonWorld(world: World) {
        if (!world.name.startsWith("dungeon_")) return

        // Kick any remaining players (just in case)
        for (player in world.players) {
            player.teleport(Bukkit.getWorlds()[0].spawnLocation)
            player.sendMessage("Dungeon is closing...")
        }

        Bukkit.unloadWorld(world, false)
        
        val worldFolder = world.worldFolder
        deleteDirectory(worldFolder)
        Bukkit.getLogger().info("Deleted dungeon world: ${world.name}")
        worldThemes.remove(world.name)
        PortalManager.onDungeonClose(world.name)
    }

    fun deleteDirectory(directory: java.io.File): Boolean {
        if (directory.isDirectory) {
            val children = directory.list()
            if (children != null) {
                for (child in children) {
                    val success = deleteDirectory(java.io.File(directory, child))
                    if (!success) return false
                }
            }
        }
        return directory.delete()
    }

    fun escapeDungeon(world: World, reason: String? = null) {
        if (!world.name.startsWith("dungeon_")) return
        
        val players = world.players.toList()
        val mainWorld = Bukkit.getWorlds()[0]
        val spawnLocation = mainWorld.spawnLocation
        
        for (p in players) {
            // 騾蜃ｺ譎ゅ↓縺翫≠縺偵■繧・ｓ縺ｮ蠕｡譛ｭ繧貞炎髯､
            p.inventory.contents.filterNotNull().forEach { item ->
                if (CustomItemManager.isTalismanItem(item)) {
                    item.amount = 0
                }
            }
            
            p.teleport(spawnLocation)
            DungeonSessionManager.endSession(p)
            DungeonSessionManager.removeSessionFromFile(CCContent.instance, p.uniqueId)
            ScoreboardManager.removeScoreboard(p)
            BGMManager.stop(p)

            // Reset down state
            DungeonSessionManager.getSession(p)?.isDown = false
            p.gameMode = org.bukkit.GameMode.SURVIVAL
            
            if (reason != null) {
                p.sendMessage(reason)
            } else {
                 val msg = MessageManager.getMessage(p, "prefix") + if (PlayerDataManager.getPlayerData(p).lang == "ja_jp") {
                    "§oおあげちゃんの御札の力で、スキマから脱出しました..."
                } else {
                    "§oWith the power of Oage-chan's Talisman, you escaped from the sukima..."
                }
                p.sendMessage(msg)
            }
        }
    }
}
