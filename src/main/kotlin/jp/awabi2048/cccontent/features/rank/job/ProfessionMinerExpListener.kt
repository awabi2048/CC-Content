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

        plugin.logger.fine("[ProfessionMinerExpListener] onBlockBreak: ${player.name} broke ${event.block.type}")

        if (!rankManager.isAttainer(uuid)) {
            plugin.logger.fine("[ProfessionMinerExpListener] ${player.name} is not an attainer")
            return
        }

        val playerProfession = rankManager.getPlayerProfession(uuid) ?: run {
            plugin.logger.fine("[ProfessionMinerExpListener] ${player.name} has no profession")
            return
        }

        plugin.logger.fine("[ProfessionMinerExpListener] ${player.name} profession: ${playerProfession.profession}")
        
        when (playerProfession.profession) {
            Profession.MINER -> {
                plugin.logger.fine("[ProfessionMinerExpListener] Processing MINER: ${event.block.type}")
                addBreakExpIfEligible(uuid, event.block, minerExpMap)
            }
            Profession.LUMBERJACK -> {
                plugin.logger.fine("[ProfessionMinerExpListener] Processing LUMBERJACK: ${event.block.type}")
                addBreakExpIfEligible(uuid, event.block, lumberjackExpMap)
            }
            else -> {
                plugin.logger.fine("[ProfessionMinerExpListener] Profession ${playerProfession.profession} is not miner/lumberjack")
                return
            }
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
        val expAmount = expMap[block.type]
        plugin.logger.fine("[ProfessionMinerExpListener] Block ${block.type} exp amount: $expAmount (map size: ${expMap.size})")

        if (expAmount == null || expAmount <= 0L) {
            plugin.logger.fine("[ProfessionMinerExpListener] No exp mapped for ${block.type} or exp <= 0")
            return
        }

        val packedPosition = BlockPositionCodec.pack(block.x, block.y, block.z)
        if (ignoreBlockStore.contains(block.world.uid, packedPosition)) {
            plugin.logger.fine("[ProfessionMinerExpListener] Block position already broken (in ignore store)")
            return
        }

        // 経験値付与後、破壊した位置を記録（同じ位置での再破壊による経験値獲得を防止）
        ignoreBlockStore.add(block.world.uid, packedPosition)

        plugin.logger.fine("[ProfessionMinerExpListener] Adding $expAmount exp to player $uuid for ${block.type}")
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
