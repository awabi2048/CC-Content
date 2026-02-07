package jp.awabi2048.cccontent.features.sukima_dungeon.generator

import org.bukkit.Location
import org.bukkit.block.structure.StructureRotation
import org.bukkit.structure.Structure
import org.bukkit.scheduler.BukkitRunnable
import java.util.Random

import jp.awabi2048.cccontent.features.sukima_dungeon.mobs.MobManager

object StructureBuilder {
    private var loader: StructureLoader? = null
    private var mobManager: MobManager? = null
    private var itemManager: jp.awabi2048.cccontent.features.sukima_dungeon.items.ItemManager? = null
    private val random = Random()

    fun init(loader: StructureLoader, mobManager: MobManager, itemManager: jp.awabi2048.cccontent.features.sukima_dungeon.items.ItemManager) {
        this.loader = loader
        this.mobManager = mobManager
        this.itemManager = itemManager
    }

    data class BuildResult(
        val minibossMarkers: Map<Pair<Int, Int>, Location>,
        val restCells: Set<Pair<Int, Int>>
    )

    data class MarkerProcessResult(
        val playerSpawns: List<Location>,
        val mobSpawnPoints: List<Location>
    )

    fun buildSpread(
        startLocation: Location, 
        grid: Array<Array<MazeGenerator.Cell>>, 
        theme: Theme, 
        entranceX: Int? = null, 
        entranceZ: Int? = null,
        onComplete: (BuildResult) -> Unit
    ) {
        val plugin = loader?.plugin ?: throw IllegalStateException("StructureBuilder not initialized")
        val tileSize = theme.tileSize
        val width = grid.size
        val length = grid[0].size
        val startX = startLocation.blockX
        val startY = startLocation.blockY
        val startZ = startLocation.blockZ
        val world = startLocation.world ?: return

        val restCells = mutableSetOf<Pair<Int, Int>>()
        val minibossCells = mutableSetOf<Pair<Int, Int>>()

        val cellsToBuild = mutableListOf<Pair<Int, Int>>()
        for (x in grid.indices) {
            for (z in grid[0].indices) {
                cellsToBuild.add(x to z)
            }
        }

        val config = loader?.plugin?.config
        val cellsPerTick = config?.getInt("generator_cells_per_tick", 5) ?: 5

        object : BukkitRunnable() {
            var index = 0

            override fun run() {
                val limit = (index + cellsPerTick).coerceAtMost(cellsToBuild.size)
                
                while (index < limit) {
                    val (x, z) = cellsToBuild[index]
                    val cell = grid[x][z]
                    val cellX = startX + x * tileSize
                    val cellZ = startZ + z * tileSize
                    
                    val isEntrance = x == entranceX && z == entranceZ
                    val location = Location(world, cellX.toDouble(), startY.toDouble(), cellZ.toDouble())
                    val resultType = buildCell(theme, location, cell, isEntrance)
                    
                    when (resultType) {
                        StructureType.REST -> restCells.add(x to z)
                        StructureType.MINIBOSS -> minibossCells.add(x to z)
                        else -> {}
                    }
                    index++
                }

                if (index >= cellsToBuild.size) {
                    this.cancel()
                    val minibossMarkers = processSpecialMarkers(startLocation, width, length, theme, minibossCells)
                    onComplete(BuildResult(minibossMarkers, restCells))
                }
            }
        }.runTaskTimer(plugin, 1L, 1L)
    }

    @Deprecated("Use buildSpread for better performance", ReplaceWith("buildSpread(startLocation, grid, theme, entranceX, entranceZ, onComplete)"))
    fun build(startLocation: Location, grid: Array<Array<MazeGenerator.Cell>>, theme: Theme, entranceX: Int? = null, entranceZ: Int? = null): BuildResult {
        val loader = this.loader ?: throw IllegalStateException("StructureBuilder not initialized")
        val mobManager = this.mobManager ?: throw IllegalStateException("StructureBuilder not initialized")
        val itemManager = this.itemManager ?: throw IllegalStateException("StructureBuilder not initialized")
        val world = startLocation.world ?: return BuildResult(emptyMap(), emptySet()) // Fixed return type
        val startX = startLocation.blockX
        val startY = startLocation.blockY
        val startZ = startLocation.blockZ
        
        val tileSize = theme.tileSize
        val width = grid.size
        val length = grid[0].size
        
        val spawnLocations = mutableListOf<Location>()
        
        // Calculate maze center for mob scaling
        val centerX = startLocation.x + (width * tileSize) / 2.0
        val centerZ = startLocation.z + (length * tileSize) / 2.0
        val centerLocation = Location(world, centerX, startY.toDouble(), centerZ)
        
        if (theme.time != null) {
            world.time = theme.time
        }

        val restCells = mutableSetOf<Pair<Int, Int>>()
        val minibossCells = mutableSetOf<Pair<Int, Int>>()

        for (x in grid.indices) {
            for (z in grid[0].indices) {
                val cell = grid[x][z]
                val cellX = startX + x * tileSize
                val cellZ = startZ + z * tileSize
                
                val isEntrance = x == entranceX && z == entranceZ
                val location = Location(world, cellX.toDouble(), startY.toDouble(), cellZ.toDouble())
                val resultType = buildCell(theme, location, cell, isEntrance)
                
                when (resultType) {
                    StructureType.REST -> restCells.add(x to z)
                    StructureType.MINIBOSS -> minibossCells.add(x to z)
                    else -> {}
                }
            }
        }

        val minibossMarkers = processSpecialMarkers(startLocation, width, length, theme, minibossCells)

        return BuildResult(minibossMarkers, restCells)
    }

    private fun processSpecialMarkers(startLocation: Location, width: Int, length: Int, theme: Theme, minibossCells: Set<Pair<Int, Int>>): Map<Pair<Int, Int>, Location> {
        val world = startLocation.world ?: return emptyMap()
        val startX = startLocation.blockX
        val startY = startLocation.blockY
        val startZ = startLocation.blockZ
        val tileSize = theme.tileSize
        val config = loader?.plugin?.config ?: return emptyMap()
        val searchRadiusY = config.getDouble("marker_search_radius_y", 20.0)

        val minibossMarkers = mutableMapOf<Pair<Int, Int>, Location>()

        for (cell in minibossCells) {
            val cellX = startX + cell.first * tileSize
            val cellZ = startZ + cell.second * tileSize

            val minX = cellX.toDouble() - 0.1
            val maxX = (cellX + tileSize).toDouble() + 0.1
            val minZ = cellZ.toDouble() - 0.1
            val maxZ = (cellZ + tileSize).toDouble() + 0.1
            val minY = startY.toDouble() - searchRadiusY
            val maxY = startY.toDouble() + searchRadiusY

            val minChunkX = minX.toInt() shr 4
            val maxChunkX = maxX.toInt() shr 4
            val minChunkZ = minZ.toInt() shr 4
            val maxChunkZ = maxZ.toInt() shr 4

            for (cx in minChunkX..maxChunkX) {
                for (cz in minChunkZ..maxChunkZ) {
                    val chunk = world.getChunkAt(cx, cz)
                    if (!chunk.isLoaded) chunk.load()

                    for (entity in chunk.entities) {
                        val loc = entity.location
                        if (loc.x !in minX..maxX || loc.z !in minZ..maxZ || loc.y !in minY..maxY) continue

                        if (entity is org.bukkit.entity.Marker) {
                            if (entity.scoreboardTags.contains("sd.marker.mob")) {
                                minibossMarkers[cell] = loc.clone()
                                break // Only one miniboss per tile
                            }
                        } else if (entity is org.bukkit.entity.ArmorStand) {
                            if (entity.customName == "MOB") {
                                minibossMarkers[cell] = loc.clone()
                                break
                            }
                        }
                    }
                }
            }
        }
        return minibossMarkers
    }

    // Existing processMarkers handles everything ELSE (spawns, NPCs, etc.)
    // We need to make sure processMarkers DOES NOT handle MOBs in MINIBOSS tiles.
    // However, build() already calls buildCell which places structures.
    // The current flow in EntranceListener is:
    // 1. StructureBuilder.build(...)
    // 2. ScoreboardManager.setupScoreboard(...)
    // 3. SproutManager.generateSprouts(...)
    // 4. StructureBuilder.processMarkers(...)
    
    // If I add minibossCells logic to build(), I should pass it to processMarkers.

    fun processMarkers(startLocation: Location, width: Int, length: Int, theme: Theme, targetNpcCount: Int, minibossCells: Set<Pair<Int, Int>> = emptySet()): MarkerProcessResult {
        val loader = this.loader ?: throw IllegalStateException("StructureBuilder not initialized")
        val mobManager = this.mobManager ?: throw IllegalStateException("StructureBuilder not initialized")
        val itemManager = this.itemManager ?: throw IllegalStateException("StructureBuilder not initialized")
        val world = startLocation.world ?: return MarkerProcessResult(emptyList(), emptyList())
        val startX = startLocation.blockX
        val startY = startLocation.blockY
        val startZ = startLocation.blockZ
        val tileSize = theme.tileSize

        val spawnLocations = mutableListOf<Location>()
        val mobSpawnPoints = mutableListOf<Location>()
        val config = loader.plugin.config
        val searchRadiusY = config.getDouble("marker_search_radius_y", 20.0)
        
        val minX = startX.toDouble() - 1.0
        val maxX = (startX + width * tileSize).toDouble() + 1.0
        val minZ = startZ.toDouble() - 1.0
        val maxZ = (startZ + length * tileSize).toDouble() + 1.0
        val minY = startY.toDouble() - searchRadiusY
        val maxY = startY.toDouble() + searchRadiusY

        val mobRate = config.getDouble("spawn_rates.MOB", 0.5)
        val itemRate = config.getDouble("spawn_rates.ITEM", 0.4)

        val npcMarkers = mutableListOf<Location>()

        // Calculate maze center for mob scaling
        val centerX = startLocation.x + (width * tileSize) / 2.0
        val centerZ = startLocation.z + (length * tileSize) / 2.0
        val centerLocation = Location(world, centerX, startY.toDouble(), centerZ)

        // Iterate through chunks to ensure they are loaded
        val minChunkX = (minX.toInt()) shr 4
        val maxChunkX = (maxX.toInt()) shr 4
        val minChunkZ = (minZ.toInt()) shr 4
        val maxChunkZ = (maxZ.toInt()) shr 4

        for (cx in minChunkX..maxChunkX) {
            for (cz in minChunkZ..maxChunkZ) {
                val chunk = world.getChunkAt(cx, cz)
                if (!chunk.isLoaded) {
                    chunk.load()
                }

                for (entity in chunk.entities) {
                    val loc = entity.location
                    if (loc.x !in minX..maxX || loc.z !in minZ..maxZ || loc.y !in minY..maxY) continue

                    // Check if this entity is in a miniboss cell
                    val relX = (loc.x - startX).toInt() / tileSize
                    val relZ = (loc.z - startZ).toInt() / tileSize
                    val isInMinibossCell = minibossCells.contains(relX to relZ)

                    if (entity is org.bukkit.entity.Marker) {
                        val tags = entity.scoreboardTags
                        
                        when {
                            tags.contains("sd.marker.spawn") -> {
                                spawnLocations.add(loc.clone())
                            }
                            tags.contains("sd.marker.mob") -> {
                                if (!isInMinibossCell && random.nextDouble() <= mobRate) {
                                    mobSpawnPoints.add(loc.clone())
                                }
                            }
                            tags.contains("sd.marker.npc") -> {
                                npcMarkers.add(loc.clone())
                            }
                            tags.contains("sd.marker.item") -> {
                                if (random.nextDouble() <= itemRate) {
                                    itemManager.spawnItem(loc)
                                }
                            }
                            // Sprouts are handled by SproutManager later, they shouldn't be removed here.
                        }
                    }
                    else if (entity is org.bukkit.entity.ArmorStand) {
                        val name = entity.customName ?: continue
                        
                        when (name) {
                            "SPAWN" -> {
                                spawnLocations.add(loc.clone())
                            }
                            "MOB" -> {
                                if (!isInMinibossCell && random.nextDouble() <= mobRate) {
                                    mobSpawnPoints.add(loc.clone())
                                }
                            }
                            "NPC" -> {
                                npcMarkers.add(loc.clone())
                            }
                            "ITEM" -> {
                                if (random.nextDouble() <= itemRate) {
                                    itemManager.spawnItem(loc)
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Randomly select and spawn NPCs
        npcMarkers.shuffled().take(targetNpcCount).forEach { loc ->
            val villager = world.spawn(loc, org.bukkit.entity.Villager::class.java)
            villager.setAI(false)
            villager.isInvulnerable = true
            villager.setGravity(true)
            villager.isSilent = true
        }
        
        return MarkerProcessResult(spawnLocations, mobSpawnPoints)
    }

    private fun buildCell(theme: Theme, location: Location, cell: MazeGenerator.Cell, isEntrance: Boolean): StructureType {
        var (type, rotation) = if (isEntrance) {
            StructureType.ENTRANCE to 0
        } else {
            determineStructureTypeAndRotation(cell)
        }
        
        // Trap replacement logic for CROSS sections
        if (!isEntrance && type == StructureType.CROSS) {
            val config = loader?.plugin?.config
            val minibossChance = config?.getDouble("miniboss_chance", 0.1) ?: 0.1
            val restChance = config?.getDouble("rest_chance", 0.1) ?: 0.1

            val rand = random.nextDouble()
            when {
                rand < minibossChance -> {
                    type = StructureType.MINIBOSS
                }
                rand < minibossChance + restChance -> {
                    type = StructureType.REST
                }
            }
        }

        var structures = theme.structures[type]
        
        // Fallback for optional structures (MINIBOSS, REST)
        if (structures.isNullOrEmpty() && (type == StructureType.MINIBOSS || type == StructureType.REST)) {
            loader?.plugin?.logger?.info("Missing optional structure $type, falling back to CROSS")
            type = StructureType.CROSS
            structures = theme.structures[type]
        }
        
        if (structures.isNullOrEmpty()) {
            // Fallback: Place a single block to prevent void holes and indicate error
            location.block.type = org.bukkit.Material.BEDROCK
            loader?.plugin?.logger?.warning("Missing structure for type $type in theme ${theme.id}")
            return type
        }
        
        val structure = structures[random.nextInt(structures.size)]
        
        val structureRotation = when (rotation) {
            0 -> StructureRotation.NONE
            1 -> StructureRotation.CLOCKWISE_90
            2 -> StructureRotation.CLOCKWISE_180
            3 -> StructureRotation.COUNTERCLOCKWISE_90
            else -> StructureRotation.NONE
        }

        // Adjust location based on rotation to keep structure inside the grid cell
        // Assuming structure rotates around (0,0,0)
        var offsetX = 0.0
        var offsetZ = 0.0
        // We need to shift by size - 1 because indices are 0-based.
        // E.g. Size 2 (0,1) -> Rotated becomes (0, -1). To get back to (0,1) range we shift by 1.
        val shift = (theme.tileSize - 1).toDouble()

        when (structureRotation) {
            StructureRotation.CLOCKWISE_90 -> {
                offsetX = shift
            }
            StructureRotation.CLOCKWISE_180 -> {
                offsetX = shift
                offsetZ = shift
            }
            StructureRotation.COUNTERCLOCKWISE_90 -> {
                offsetZ = shift
            }
            else -> {}
        }

        val placeLocation = location.clone().add(offsetX, 0.0, offsetZ)

        // Place structure
        structure.place(placeLocation, true, structureRotation, org.bukkit.block.structure.Mirror.NONE, 0, 1.0f, random)
        
        return type
    }

    private fun determineStructureTypeAndRotation(cell: MazeGenerator.Cell): Pair<StructureType, Int> {
        val n = cell.connections.contains(MazeGenerator.Direction.NORTH)
        val s = cell.connections.contains(MazeGenerator.Direction.SOUTH)
        val e = cell.connections.contains(MazeGenerator.Direction.EAST)
        val w = cell.connections.contains(MazeGenerator.Direction.WEST)
        
        val connections = listOf(n, s, e, w).count { it }

        return when (connections) {
            1 -> {
                // Dead End
                when {
                    n -> StructureType.DEAD_END to 2 
                    s -> StructureType.DEAD_END to 0
                    e -> StructureType.DEAD_END to 3 
                    w -> StructureType.DEAD_END to 1 
                    else -> StructureType.DEAD_END to 0
                }
            }
            2 -> {
                if ((n && s) || (e && w)) {
                    // Straight
                    if (n && s) StructureType.STRAIGHT to 0
                    else StructureType.STRAIGHT to 1 
                } else {
                    // Corner
                    when {
                        n && e -> StructureType.CORNER to 2 
                        e && s -> StructureType.CORNER to 3 
                        s && w -> StructureType.CORNER to 0 
                        w && n -> StructureType.CORNER to 1 
                        else -> StructureType.CORNER to 0
                    }
                }
            }
            3 -> {
                // T-Shape
                when {
                    !n -> StructureType.T_SHAPE to 2 
                    !s -> StructureType.T_SHAPE to 0 
                    !e -> StructureType.T_SHAPE to 3 
                    !w -> StructureType.T_SHAPE to 1 
                    else -> StructureType.T_SHAPE to 0
                }
            }
            4 -> StructureType.CROSS to 0
            else -> StructureType.STRAIGHT to 0 
        }
    }
}
