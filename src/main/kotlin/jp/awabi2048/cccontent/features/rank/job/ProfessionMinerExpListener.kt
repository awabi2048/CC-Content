package jp.awabi2048.cccontent.features.rank.job

import jp.awabi2048.cccontent.features.rank.RankManager
import jp.awabi2048.cccontent.features.rank.profession.Profession
import org.bukkit.Material.BREWING_STAND
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.event.block.Action
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.UUID

class ProfessionMinerExpListener(
    private val plugin: JavaPlugin,
    private val rankManager: RankManager,
    private val ignoreBlockStore: IgnoreBlockStore
) : Listener {
    private val minerExpMap: Map<Material, Long>
    private val lumberjackExpMap: Map<Material, Long>
    private val brewerMockExp: Long
    private val brewerCooldownMillis = 1000L
    private val brewerLastGainAt: MutableMap<UUID, Long> = mutableMapOf()

    init {
        val jobDir = File(plugin.dataFolder, "job").apply { mkdirs() }
        val expFile = File(jobDir, "exp.yml")

        val config = if (expFile.exists()) YamlConfiguration.loadConfiguration(expFile) else YamlConfiguration()
        if (!expFile.exists()) {
            plugin.logger.warning("job/exp.yml が見つからないため、職業経験値付与を無効化します")
        }

        minerExpMap = loadBlockExpMap(config, "miner")
        lumberjackExpMap = loadBlockExpMap(config, "lumberjack")
        brewerMockExp = config.getLong("brewer.mock_exp", 0L)
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

        if (!rankManager.isAttainer(uuid)) {
            return
        }

        val playerProfession = rankManager.getPlayerProfession(uuid) ?: return
        when (playerProfession.profession) {
            Profession.MINER -> addBreakExpIfEligible(uuid, event.block, minerExpMap)
            Profession.LUMBERJACK -> addBreakExpIfEligible(uuid, event.block, lumberjackExpMap)
            else -> return
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onBrewerMockInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) {
            return
        }

        if (event.hand != EquipmentSlot.HAND) {
            return
        }

        val clickedBlock = event.clickedBlock ?: return
        if (clickedBlock.type != BREWING_STAND) {
            return
        }

        val player = event.player
        val uuid = player.uniqueId
        if (!rankManager.isAttainer(uuid)) {
            return
        }

        val profession = rankManager.getPlayerProfession(uuid)?.profession ?: return
        if (profession != Profession.BREWER) {
            return
        }

        if (brewerMockExp <= 0L) {
            return
        }

        val now = System.currentTimeMillis()
        val lastGain = brewerLastGainAt[uuid] ?: 0L
        if (now - lastGain < brewerCooldownMillis) {
            return
        }
        brewerLastGainAt[uuid] = now

        rankManager.addProfessionExp(uuid, brewerMockExp)
    }

    private fun addBreakExpIfEligible(uuid: UUID, block: org.bukkit.block.Block, expMap: Map<Material, Long>) {
        val expAmount = expMap[block.type] ?: return
        if (expAmount <= 0L) {
            return
        }

        val packedPosition = BlockPositionCodec.pack(block.x, block.y, block.z)
        if (ignoreBlockStore.contains(block.world.uid, packedPosition)) {
            return
        }

        // 経験値付与後、破壊した位置を記録（同じ位置での再破壊による経験値獲得を防止）
        ignoreBlockStore.add(block.world.uid, packedPosition)

        rankManager.addProfessionExp(uuid, expAmount)
    }

    private fun loadBlockExpMap(config: YamlConfiguration, path: String): Map<Material, Long> {
        val section = config.getConfigurationSection(path) ?: return emptyMap()

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
