package jp.awabi2048.cccontent.features.sukima_dungeon.generator

import org.bukkit.World
import org.bukkit.generator.ChunkGenerator
import java.util.Random

class VoidChunkGenerator : ChunkGenerator() {
    override fun generateChunkData(world: World, random: Random, x: Int, z: Int, biome: BiomeGrid): ChunkData {
        return createChunkData(world)
    }
}
