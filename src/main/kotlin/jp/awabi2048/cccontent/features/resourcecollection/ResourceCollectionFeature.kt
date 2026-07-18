package jp.awabi2048.cccontent.features.resourcecollection

import com.awabi2048.ccsystem.CCSystem
import jp.awabi2048.cccontent.features.rank.RankManager
import jp.awabi2048.cccontent.features.rank.profession.profile.FarmerSkillProfile
import jp.awabi2048.cccontent.features.rank.profession.profile.LumberjackSkillProfile
import jp.awabi2048.cccontent.features.rank.profession.profile.MinerSkillProfile
import jp.awabi2048.cccontent.items.CustomItemManager
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockDropItemEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.Random

class ResourceCollectionFeature(
    private val plugin: JavaPlugin,
    private val rankManager: RankManager,
    private val random: Random = Random()
) : Listener {
    private lateinit var settings: ResourceCollectionSettings
    private lateinit var items: ResourceCollectionItems

    fun initialize() {
        settings = ResourceCollectionSettings.load(plugin)
        if (!settings.enabled) return
        items = ResourceCollectionItems(plugin)
        items.register()
        plugin.server.pluginManager.registerEvents(this, plugin)
        plugin.logger.info("Resource Collection: normal bonus resources enabled; legacy EXP and craft rules disabled")
    }

    fun shutdown() {
        if (::items.isInitialized) items.unregister()
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockDrops(event: BlockDropItemEvent) {
        val kind = ResourceMaterialPolicy.classify(event.blockState.type, event.blockState.blockData) ?: return
        if (settings.normalBonusEnabled[kind] != true) return
        val player = event.player
        if (rankManager.getPlayerProfession(player.uniqueId)?.profession != kind.profession) return
        val chance = when (val profile = rankManager.getTypedProfessionProfile(player.uniqueId)) {
            is MinerSkillProfile -> profile.ordinaryExtraDropChance
            is LumberjackSkillProfile -> profile.ordinaryExtraDropChance
            is FarmerSkillProfile -> profile.byproductChance
            else -> return
        }
        val natural = CCSystem.getAPI().getNaturalOriginRegistry().isNatural(
            event.block.world.key, event.block.x, event.block.y, event.block.z
        )
        if (!NormalResourceBonusPolicy.succeeds(chance, natural, random)) return
        val bonus = CustomItemManager.createItemForPlayer("resource.${kind.bonusItemId}", player, 1) ?: return
        event.block.world.dropItemNaturally(event.block.location.add(0.5, 0.5, 0.5), bonus)
    }
}
