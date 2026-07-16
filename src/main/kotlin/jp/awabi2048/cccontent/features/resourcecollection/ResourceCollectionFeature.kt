package jp.awabi2048.cccontent.features.resourcecollection

import jp.awabi2048.cccontent.features.rank.RankManager
import jp.awabi2048.cccontent.features.rank.profession.Profession
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.plugin.java.JavaPlugin

class ResourceCollectionFeature(
    private val plugin: JavaPlugin,
    private val rankManager: RankManager
) : Listener {
    private lateinit var settings: ResourceCollectionSettings
    private val rewardGuard = CollectionRewardGuard()

    fun initialize() {
        settings = ResourceCollectionSettings.load(plugin)
        if (settings.enabled) plugin.server.pluginManager.registerEvents(this, plugin)
    }

    fun shutdown() = rewardGuard.clear()

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onHarvest(event: BlockBreakEvent) {
        val player = event.player
        if (player.world.name !in settings.worlds) return
        val rule = settings.harvestRules[event.block.type] ?: return
        if (rankManager.getPlayerProfession(player.uniqueId)?.profession != rule.profession) return
        // FarmerのブロックEXPはRank側で付与済みのため、資源収集側では二重付与しない。
        if (rule.profession == Profession.FARMER) return
        val age = event.block.blockData.asAgeableOrNull()?.age
        if (rule.minimumAge != null && age != null && age < rule.minimumAge) return
        grant(CollectionAction.HARVEST, player.uniqueId.toString(), event, rule)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onCraft(event: CraftItemEvent) {
        val player = event.whoClicked as? org.bukkit.entity.Player ?: return
        if (player.world.name !in settings.worlds) return
        val rule = settings.craftRules[event.recipe.result.type] ?: return
        if (rankManager.getPlayerProfession(player.uniqueId)?.profession != rule.profession) return
        grant(CollectionAction.CRAFT, player.uniqueId.toString(), event, rule)
    }

    private fun grant(action: CollectionAction, playerId: String, sourceId: Any, rule: CollectionRule) {
        rewardGuard.tryGrant(CollectionRewardKey(action, playerId, sourceId)) {
            rankManager.addProfessionExp(java.util.UUID.fromString(playerId), rule.experience)
        }
    }
}

private fun org.bukkit.block.data.BlockData.asAgeableOrNull(): org.bukkit.block.data.Ageable? = this as? org.bukkit.block.data.Ageable
