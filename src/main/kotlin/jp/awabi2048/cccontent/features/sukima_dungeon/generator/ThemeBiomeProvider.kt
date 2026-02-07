package jp.awabi2048.cccontent.features.sukima_dungeon.generator

import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.block.Biome
import org.bukkit.generator.BiomeProvider
import org.bukkit.generator.WorldInfo

class ThemeBiomeProvider(private val themeId: String) : BiomeProvider() {

    override fun getBiome(worldInfo: WorldInfo, x: Int, y: Int, z: Int): Biome {
        val biomeKey = NamespacedKey("kota_server", "sukima_dungeon_$themeId")
        val biome = Registry.BIOME.get(biomeKey)
        
        return if (biome != null) {
            biome
        } else {
            // 該当バイオームが存在しない場合は警告を出して THE_VOID を返す
            Bukkit.getLogger().warning("[SukimaDungeon] Biome '$biomeKey' not found. Falling back to THE_VOID.")
            Biome.THE_VOID
        }
    }

    override fun getBiomes(worldInfo: WorldInfo): List<Biome> {
        val biomeKey = NamespacedKey("kota_server", "sukima_dungeon_$themeId")
        val biome = Registry.BIOME.get(biomeKey) ?: Biome.THE_VOID
        return listOf(biome)
    }
}
