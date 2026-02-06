package jp.awabi2048.cccontent.features.sukima_dungeon.generator

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.Marker
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.structure.StructureManager
import org.bukkit.util.Vector
import java.io.File

/**
 * ストラクチャーを配置してダンジョンを構築するクラス
 */
class StructureBuilder(
    private val plugin: JavaPlugin,
    private val structureLoader: StructureLoader
) {
    
    companion object {
        const val CELLS_PER_TICK = 5
        const val MARKER_SEARCH_RADIUS_Y = 20.0
    }
    
    /**
     * ストラクチャー配置結果
     */
    data class BuildResult(
        val cellCount: Int,
        val startTime: Long,
        val structureCount: Int = 0
    )
    
    /**
     * マーカー処理結果
     */
    data class MarkerProcessResult(
        val sproutMarkers: List<Location> = emptyList(),
        val mobSpawns: List<Location> = emptyList(),
        val npcSpawns: List<Location> = emptyList(),
        val itemSpawns: List<Location> = emptyList(),
        val minibossMarkers: List<Location> = emptyList(),
        val restCells: List<Location> = emptyList()
    )
    
    /**
     * 迷路をスプレッド配置（非同期で複数tick実行）
     * @param maze 迷路グリッド
     * @param world 配置対象ワールド
     * @param theme テーマ
     * @param centerX 中央X座標
     * @param centerZ 中央Z座標
     * @param onComplete 完了時のコールバック
     */
    fun buildSpread(
        maze: Array<Array<MazeGenerator.Cell>>,
        world: World,
        theme: Theme,
        centerX: Int,
        centerZ: Int,
        onComplete: (BuildResult, MarkerProcessResult) -> Unit
    ) {
        val gridWidth = maze.size
        val gridLength = maze[0].size
        val tileSize = theme.tileSize
        
        // セルリストを作成
        val cells = mutableListOf<Pair<MazeGenerator.Cell, Boolean>>()
        for (x in maze.indices) {
            for (z in maze[x].indices) {
                val isEntrance = (x == gridWidth / 2 && z == gridLength / 2)
                cells.add(Pair(maze[x][z], isEntrance))
            }
        }
        
        val startTime = System.currentTimeMillis()
        var processedCells = 0
        var structureCount = 0
        
        // 非同期タスク
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            val markers = mutableMapOf<Location, String>()
            
            // セルを処理（CELLS_PER_TICK個ずつ）
            for ((cell, isEntrance) in cells) {
                val worldX = centerX + (cell.x - gridWidth / 2) * tileSize
                val worldZ = centerZ + (cell.z - gridLength / 2) * tileSize
                val location = Location(world, worldX.toDouble(), 64.0, worldZ.toDouble())
                
                // ストラクチャータイプを判定
                val connectionCount = cell.connections.size
                var structureType = StructureType.fromConnectionCount(connectionCount)
                
                // エントランスは特別扱い
                if (isEntrance) {
                    structureType = StructureType.ENTRANCE
                }
                
                // CROSS セルの一部をミニボスまたはトラップに変更
                if (structureType == StructureType.CROSS) {
                    val rand = kotlin.random.Random.nextDouble()
                    if (rand < 0.1) {  // 10% の確率
                        structureType = StructureType.MINIBOSS
                    } else if (rand < 0.15) {  // 5% の確率
                        structureType = StructureType.TRAP
                    }
                }
                
                // ストラクチャーを配置
                val structureFile = selectStructureFile(theme, structureType)
                if (structureFile != null) {
                    try {
                        pasteStructure(world, structureFile, location)
                        structureCount++
                    } catch (e: Exception) {
                        plugin.logger.warning("[SukimaDungeon] ストラクチャー配置エラー: ${e.message}")
                    }
                }
                
                processedCells++
            }
            
            // マーカー処理をメインスレッドで実行
            Bukkit.getScheduler().runTask(plugin, Runnable {
                val markerResult = processMarkers(world)
                val buildResult = BuildResult(
                    cellCount = processedCells,
                    startTime = startTime,
                    structureCount = structureCount
                )
                onComplete(buildResult, markerResult)
            })
        })
    }
    
    /**
     * ストラクチャーファイルを選択
     */
    private fun selectStructureFile(theme: Theme, type: StructureType): File? {
        val structureName = structureLoader.selectRandomStructure(theme, type) ?: return null
        val file = File(
            plugin.dataFolder,
            "structures/sukima_dungeon/${theme.path}/${type.name.lowercase()}/$structureName"
        )
        
        return if (file.exists()) file else null
    }
    
    /**
     * ストラクチャーを配置
     */
    private fun pasteStructure(world: World, structureFile: File, location: Location) {
        try {
            val structureManager = Bukkit.getStructureManager()
            val structure = structureManager.loadStructure(structureFile) ?: return
            
            // ランダムに回転（0°, 90°, 180°, 270°）
            val rotation = org.bukkit.block.structure.StructureRotation.values().random()
            
            structure.place(
                location,
                true,
                rotation,
                org.bukkit.block.structure.Mirror.NONE,
                0,
                1.0f,
                java.util.Random()
            )
        } catch (e: Exception) {
            plugin.logger.warning("[SukimaDungeon] ストラクチャー配置エラー: ${e.message}")
        }
    }
    
    /**
     * ワールド内のマーカーを処理
     */
    fun processMarkers(world: World): MarkerProcessResult {
        val sproutMarkers = mutableListOf<Location>()
        val mobSpawns = mutableListOf<Location>()
        val npcSpawns = mutableListOf<Location>()
        val itemSpawns = mutableListOf<Location>()
        val minibossMarkers = mutableListOf<Location>()
        val restCells = mutableListOf<Location>()
        
        // ワールド内のすべてのマーカーエンティティを検索
        for (entity in world.entities) {
            if (entity !is Marker) continue
            
            // マーカー名で分類
            val markerName = entity.customName ?: entity.scoreboardTags.firstOrNull() ?: continue
            
            when {
                markerName.contains("sprout") || entity.scoreboardTags.contains("sd.marker.sprout") -> {
                    sproutMarkers.add(entity.location)
                    entity.remove()
                }
                markerName.contains("mob") || entity.scoreboardTags.contains("sd.marker.mob") -> {
                    mobSpawns.add(entity.location)
                    entity.remove()
                }
                markerName.contains("npc") || entity.scoreboardTags.contains("sd.marker.npc") -> {
                    npcSpawns.add(entity.location)
                    entity.remove()
                }
                markerName.contains("item") || entity.scoreboardTags.contains("sd.marker.item") -> {
                    itemSpawns.add(entity.location)
                    entity.remove()
                }
                markerName.contains("miniboss") || entity.scoreboardTags.contains("sd.marker.miniboss") -> {
                    minibossMarkers.add(entity.location)
                    entity.remove()
                }
                markerName.contains("rest") || entity.scoreboardTags.contains("sd.marker.rest") -> {
                    restCells.add(entity.location)
                    entity.remove()
                }
            }
        }
        
        plugin.logger.info(
            "[SukimaDungeon] マーカー処理完了: " +
            "sprout=${sproutMarkers.size}, mob=${mobSpawns.size}, npc=${npcSpawns.size}, " +
            "item=${itemSpawns.size}, miniboss=${minibossMarkers.size}, rest=${restCells.size}"
        )
        
        return MarkerProcessResult(
            sproutMarkers = sproutMarkers,
            mobSpawns = mobSpawns,
            npcSpawns = npcSpawns,
            itemSpawns = itemSpawns,
            minibossMarkers = minibossMarkers,
            restCells = restCells
        )
    }
}