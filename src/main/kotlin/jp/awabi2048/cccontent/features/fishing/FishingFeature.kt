package jp.awabi2048.cccontent.features.fishing

import jp.awabi2048.cccontent.CCContent
import jp.awabi2048.cccontent.features.catalog.CatalogItem
import jp.awabi2048.cccontent.features.catalog.CatalogStore
import jp.awabi2048.cccontent.features.rank.profession.Profession
import jp.awabi2048.cccontent.util.FeatureInitializationLogger
import com.awabi2048.ccsystem.CCSystem
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerFishEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import java.util.Random
import java.util.UUID

class FishingFeature(private val plugin: CCContent, private val catalogStore: CatalogStore) : Listener {
    private lateinit var settings: FishingSettings
    private lateinit var items: FishingItems
    private lateinit var fishdex: FishdexStore
    private val sessions = mutableMapOf<UUID, ActiveSession>()

    private data class ActiveSession(
        val catchData: FishCatch,
        var input: FishingInputState,
        val bedrock: Boolean,
        val bossBar: BossBar,
        val task: BukkitTask
    )

    fun initialize(logger: FeatureInitializationLogger) {
        settings = FishingSettings.load(plugin)
        if (!settings.enabled) return
        items = FishingItems(plugin)
        fishdex = FishdexStore(catalogStore)
        plugin.server.pluginManager.registerEvents(this, plugin)
        logger.setStatus("Fishing", FeatureInitializationLogger.Status.SUCCESS)
        logger.addSummaryMessage("Fishing", "釣りと魚図鑑を登録しました (${settings.fishes.size}種類)")
    }

    fun shutdown() {
        sessions.forEach { (playerId, session) ->
            session.task.cancel()
            Bukkit.getPlayer(playerId)?.let { player ->
                player.hideBossBar(session.bossBar)
                if (player.isOnline) player.sendActionBar(Component.empty())
            }
        }
        sessions.clear()
    }

    @EventHandler
    fun onFish(event: PlayerFishEvent) {
        val player = event.player
        if (player.inventory.itemInMainHand.type != Material.FISHING_ROD) return
        if (event.state == PlayerFishEvent.State.FISHING) {
            items.markRod(player.inventory.itemInMainHand)
            return
        }
        if (event.state != PlayerFishEvent.State.CAUGHT_FISH || sessions.containsKey(player.uniqueId)) return
        event.isCancelled = true
        val level = plugin.getRankManager()?.getCurrentProfessionLevel(player.uniqueId) ?: 1
        val catchData = FishingCatchSelector.select(
            FishingContext(player.world, event.hook.location, level), settings.fishes, Random()
        ) ?: run {
            player.sendMessage(message(player, "fishing.no_catch"))
            return
        }
        startMiniGame(player, catchData)
    }

    private fun startMiniGame(player: Player, catchData: FishCatch) {
        player.sendMessage(message(player, "fishing.hooked", "count" to settings.clickCount))
        val bedrock = isBedrockPlayer(player)
        val input = FishingInputState(0, settings.clickCount, expectedLeft = true)
        val bossBar = BossBar.bossBar(
            Component.text(message(player, if (bedrock) "fishing.input.tap" else "fishing.input.left")),
            0.0f,
            BossBar.Color.BLUE,
            BossBar.Overlay.PROGRESS
        )
        player.showBossBar(bossBar)
        val task = plugin.server.scheduler.runTaskLater(plugin, Runnable { fail(player.uniqueId) }, settings.timeoutTicks)
        sessions[player.uniqueId] = ActiveSession(catchData, input, bedrock, bossBar, task)
        updateDisplay(player, sessions.getValue(player.uniqueId))
    }

    @EventHandler
    fun onRodClick(event: PlayerInteractEvent) {
        val session = sessions[event.player.uniqueId] ?: return
        val action = event.action
        if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK &&
            action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return
        event.isCancelled = true
        val leftClick = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK
        val normalizedLeftClick = FishingInputNormalizer.normalize(
            leftClick,
            session.input.expectedLeft,
            session.bedrock
        )
        val (result, nextInput) = session.input.accept(
            normalizedLeftClick,
            event.player.inventory.itemInMainHand.type == Material.FISHING_ROD
        )
        if (result == FishingInputResult.FAILED) {
            fail(event.player.uniqueId)
            return
        }
        session.input = nextInput
        if (result == FishingInputResult.COMPLETE) complete(event.player) else updateDisplay(event.player, session)
    }

    private fun updateDisplay(player: Player, session: ActiveSession) {
        val inputKey = if (session.bedrock) "fishing.input.tap"
        else if (session.input.expectedLeft) "fishing.input.left" else "fishing.input.right"
        session.bossBar.progress((session.input.progress.toFloat() / session.input.required).coerceIn(0.0f, 1.0f))
        session.bossBar.name(Component.text(message(player, inputKey)))
        player.sendActionBar(Component.text(message(player,
            "fishing.progress", "current" to session.input.progress, "required" to session.input.required
        )))
    }

    private fun complete(player: Player) {
        val session = sessions.remove(player.uniqueId) ?: return
        session.task.cancel()
        player.hideBossBar(session.bossBar)
        player.sendActionBar(Component.empty())
        val catchItem = items.createCatch(player, session.catchData)
        player.inventory.addItem(catchItem).values.forEach { overflow ->
            // 満杯時も釣果を失わず、プレイヤーの位置へ実体として返す。
            player.world.dropItemNaturally(player.location, overflow)
        }
        if (plugin.getRankManager()?.getPlayerProfession(player.uniqueId)?.profession == Profession.FISHER) {
            plugin.getRankManager()?.addProfessionExp(player.uniqueId, session.catchData.exp)
        }
        fishdex.record(player.uniqueId, session.catchData)
        player.sendMessage(message(player,
            "fishing.caught",
            "fish" to message(player, "fishing.catalog.item.${session.catchData.fishId}"),
            "weight" to session.catchData.weightGrams,
            "quality" to message(player, "fishing.quality.${session.catchData.quality.id}")
        ))
    }

    private fun fail(playerId: UUID) {
        val session = sessions.remove(playerId) ?: return
        session.task.cancel()
        Bukkit.getPlayer(playerId)?.let { player ->
            if (player.isOnline) {
                player.hideBossBar(session.bossBar)
                player.sendActionBar(Component.empty())
                player.sendMessage(message(player, "fishing.failed"))
            }
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) = fail(event.player.uniqueId)

    fun catalogItems(): List<CatalogItem> = settings.fishes.map { CatalogItem(it.id, it.material) }

    private fun message(player: Player, key: String, vararg placeholders: Pair<String, Any?>): String =
        CCSystem.getAPI().getI18nString(
            player,
            key,
            placeholders.associate { (name, value) -> name to (value ?: "null") }
        ).replace('&', '§')

    private fun isBedrockPlayer(player: Player): Boolean {
        if (!Bukkit.getPluginManager().isPluginEnabled("MyWorldManager")) return false
        return try {
            MyWorldManagerApi.getBedrockFormService()?.isBedrock(player) == true
        } catch (_: Throwable) {
            false
        }
    }
}

/** 統合版は表示中の左右指示を持たないため、クリックを現在の期待入力へ正規化する。 */
object FishingInputNormalizer {
    @JvmStatic
    fun normalize(leftClick: Boolean, expectedLeft: Boolean, bedrock: Boolean): Boolean =
        if (bedrock) expectedLeft else leftClick
}
