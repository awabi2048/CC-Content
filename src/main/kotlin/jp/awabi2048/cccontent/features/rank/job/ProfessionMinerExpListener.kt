package jp.awabi2048.cccontent.features.rank.job

import jp.awabi2048.cccontent.features.rank.RankManager
import jp.awabi2048.cccontent.features.rank.profession.Profession
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class ProfessionMinerExpListener(
    private val plugin: JavaPlugin,
    private val rankManager: RankManager,
    private val ignoreBlockStore: IgnoreBlockStore
) : Listener {
    private val minerExpMap: Map<Material, Long>

    init {
        val jobDir = File(plugin.dataFolder, "job").apply { mkdirs() }
        val expFile = File(jobDir, "exp.yml")

        minerExpMap = loadMinerExpMap(expFile)
    }

    @EventHandler(ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        val block = event.blockPlaced
        val packedPosition = BlockPositionCodec.pack(block.x, block.y, block.z)
        ignoreBlockStore.add(block.world.uid, packedPosition)
    }

    @EventHandler(ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val player = event.player
        val uuid = player.uniqueId
        val block = event.block
        val packedPosition = BlockPositionCodec.pack(block.x, block.y, block.z)

        if (!rankManager.isAttainer(uuid)) {
            return
        }

        val playerProfession = rankManager.getPlayerProfession(uuid) ?: return
        if (playerProfession.profession != Profession.MINER) {
            return
        }

        val expAmount = minerExpMap[block.type] ?: return
        if (expAmount <= 0L) {
            return
        }

        if (ignoreBlockStore.contains(block.world.uid, packedPosition)) {
            return
        }

        rankManager.addProfessionExp(uuid, expAmount)
    }

    private fun loadMinerExpMap(expFile: File): Map<Material, Long> {
        if (!expFile.exists()) {
            plugin.logger.warning("job/exp.yml が見つからないため、Miner経験値付与を無効化します")
            return emptyMap()
        }

        val config = YamlConfiguration.loadConfiguration(expFile)
        val section = config.getConfigurationSection("miner") ?: return emptyMap()

        val result = mutableMapOf<Material, Long>()
        for (blockKey in section.getKeys(false)) {
            val material = Material.matchMaterial(blockKey.uppercase()) ?: continue
            val exp = section.getLong(blockKey, 0L)
            if (exp > 0L) {
                result[material] = exp
            }
        }

        return result
    }
}
