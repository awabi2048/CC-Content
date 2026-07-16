@file:Suppress("DEPRECATION")

package jp.awabi2048.cccontent.features.npc.menu

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiLoreFrame
import com.awabi2048.ccsystem.api.gui.GuiLoreBlock
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.MenuRoute
import jp.awabi2048.cccontent.economy.ContentEconomyBridge
import jp.awabi2048.cccontent.features.arena.event.ArenaSessionEndedEvent
import jp.awabi2048.cccontent.features.npc.request.DeadChestRecoveryService
import jp.awabi2048.cccontent.features.npc.request.DeadChestIdentity
import jp.awabi2048.cccontent.features.npc.request.DeadChestSnapshot
import jp.awabi2048.cccontent.features.npc.shop.OageShrineShopMenuService
import jp.awabi2048.cccontent.features.npc.shop.OageShrineShopState
import jp.awabi2048.cccontent.features.rank.profession.Profession
import jp.awabi2048.cccontent.gui.GuiMenuItems
import jp.awabi2048.cccontent.gui.MenuEventGuards
import jp.awabi2048.cccontent.gui.OwnedMenuHolder
import jp.awabi2048.cccontent.items.CustomItemManager
import jp.awabi2048.cccontent.items.misc.BoxedDaiginjoItem
import jp.awabi2048.cccontent.util.ContentLocaleResolver
import jp.awabi2048.cccontent.util.OageMessageSender
import com.destroystokyo.paper.profile.PlayerProfile
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.random.Random

class NpcMenuService(
    private val plugin: JavaPlugin,
    private val professionProvider: (UUID) -> Profession? = { null }
) : Listener {
    private companion object {
        const val CONFIG_PATH = "config/npc/menu.yml"
        const val MENU_OWNER = "cc-content:npc:oage_shrine"
        const val MENU_ID = "oage_shrine"
        val MENU_SIZE: Int get() = CCSystem.getAPI().getGuiLayoutService().size45()
        val TALK_MENU_SIZE: Int get() = CCSystem.getAPI().getGuiLayoutService().settings54().size
        val OAGE_BOX_SIZE: Int get() = CCSystem.getAPI().getGuiLayoutService().settings54().size
        val BACK_SLOT: Int get() = CCSystem.getAPI().getGuiLayoutService().backSlot45()
        val DEAD_CHEST_SELECTION_LAYOUT get() = CCSystem.getAPI().getGuiLayoutService().free45()
        val DEAD_CHEST_SELECTION_SLOTS = (20..24).toList()
        val TALK_BACK_SLOT: Int get() = CCSystem.getAPI().getGuiLayoutService().settings54().infoSlot
        val OAGE_BOX_BACK_SLOT: Int get() = CCSystem.getAPI().getGuiLayoutService().settings54().infoSlot
        const val DELIVERY_SLOT = 20
        const val PART_TIME_SLOT = 24
        const val DAILY_HEADER_SLOT = 4
        const val OAGE_BOX_HEADER_SLOT = 4
        val OAGE_BOX_CONFIRM_PREVIEW_SLOT: Int get() = CCSystem.getAPI().getGuiLayoutService().confirmation45().previewSlot
        val OAGE_BOX_CONFIRM_SLOT: Int get() = CCSystem.getAPI().getGuiLayoutService().confirmation45().confirmSlot
        val OAGE_BOX_CANCEL_SLOT: Int get() = CCSystem.getAPI().getGuiLayoutService().confirmation45().cancelSlot
        const val DEAD_CHEST_RECOVERY_COST = 300.0
        val DEAD_CHEST_CONFIRM_PREVIEW_SLOT: Int get() = CCSystem.getAPI().getGuiLayoutService().confirmation45().previewSlot
        val DEAD_CHEST_CONFIRM_SLOT: Int get() = CCSystem.getAPI().getGuiLayoutService().confirmation45().confirmSlot
        val DEAD_CHEST_CANCEL_SLOT: Int get() = CCSystem.getAPI().getGuiLayoutService().confirmation45().cancelSlot
        val DELIVERY_CONFIRM_PREVIEW_SLOT: Int get() = CCSystem.getAPI().getGuiLayoutService().confirmation45().previewSlot
        val DELIVERY_CONFIRM_SLOT: Int get() = CCSystem.getAPI().getGuiLayoutService().confirmation45().confirmSlot
        val DELIVERY_CANCEL_SLOT: Int get() = CCSystem.getAPI().getGuiLayoutService().confirmation45().cancelSlot
        const val PART_TIME_TASK_ARENA = "arena_mission_clear"
        const val DELIVERY_WORLD_POINT_REWARD = 100
        val OAGE_BOX_REWARD_SLOTS = listOf(20, 21, 22, 23, 24, 29, 30, 31, 32, 33)
    }

    private val configFile = File(plugin.dataFolder, CONFIG_PATH)
    private val stateFile = File(plugin.dataFolder, "data/npc/oage_shrine.yml")
    private val shopState = OageShrineShopState(File(plugin.dataFolder, "data/npc/oage_shrine_shop.yml"))
    private var oageBoxRewards = OageBoxRewardDefinitions(emptyList(), emptyList(), emptyList())
    private var state = YamlConfiguration()
    private var oageChanProfile: PlayerProfile? = null
    private lateinit var shopMenuService: OageShrineShopMenuService
    private val deadChestRecoveryService = DeadChestRecoveryService(plugin)
    private val navigation get() = CCSystem.getAPI().getMenuNavigationService()

    fun initialize() {
        ensureConfig()
        ensureState()
        shopMenuService = OageShrineShopMenuService(plugin, shopState) { player ->
            openRootView(player, NpcMenuView.MAIN)
        }
        registerNavigationRoutes()
        loadOageChanProfile()
        reload()
        plugin.server.pluginManager.registerEvents(shopMenuService, plugin)
    }

    fun reload() {
        ensureConfig()
        ensureState()
        val config = loadMenuConfig()
        oageBoxRewards = loadOageBoxRewardDefinitions(config)
        state = YamlConfiguration.loadConfiguration(stateFile)
        shopState.reload()
        if (::shopMenuService.isInitialized) {
            shopMenuService.reload()
        }
    }

    fun getMenuIds(): Set<String> = setOf(MENU_ID)

    fun resetDelivery(): Boolean {
        state.set("delivery.completed", null)
        saveState()
        return true
    }

    fun resetPartTime(): Boolean {
        state.set("part_time.completed_tasks", null)
        state.set("part_time.opened", null)
        saveState()
        return true
    }

    fun completeOageDaily(playerId: UUID): Boolean {
        markPartTimeCompleted(playerId, PART_TIME_TASK_ARENA)
        return true
    }

    fun resetShopDaily(): Boolean {
        shopState.clearDaily()
        return true
    }

    fun resetShopWeekly(): Boolean {
        shopState.clearWeekly()
        return true
    }

    fun open(menuId: String, player: Player): Boolean {
        if (menuId != MENU_ID) {
            player.sendMessage("§cNPCメニューが見つかりません: $menuId")
            return false
        }

        openRootView(player, NpcMenuView.MAIN)
        return true
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val holder = event.view.topInventory.holder as? NpcMenuHolder ?: return
        val player = MenuEventGuards.ownedTopClick(event, holder, "NpcMenuService.onInventoryClick: menu_click") ?: return

        handleClick(player, holder, event.rawSlot)
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        val holder = event.view.topInventory.holder as? NpcMenuHolder ?: return
        MenuEventGuards.cancelOwnedTopDrag(event, holder, "NpcMenuService.onInventoryDrag: drag_cancelled")
    }

    @EventHandler
    fun onArenaSessionEnded(event: ArenaSessionEndedEvent) {
        if (event.success) {
            markPartTimeCompleted(event.ownerPlayerId, PART_TIME_TASK_ARENA)
        }
    }

    private fun handleClick(player: Player, holder: NpcMenuHolder, slot: Int) {
        when (holder.view) {
            NpcMenuView.MAIN -> when (slot) {
                19 -> { playClick(player); navigate(player, holder.route, route(NpcMenuView.DAILY)) }
                21 -> { playClick(player); openShopMenu(player) }
                25 -> { playClick(player); navigate(player, holder.route, route(NpcMenuView.REQUEST)) }
            }
            NpcMenuView.DAILY -> when (slot) {
                BACK_SLOT -> { playClick(player); openPreviousOr(player, NpcMenuView.MAIN) }
                DELIVERY_SLOT -> handleDeliveryClick(player)
                PART_TIME_SLOT -> handlePartTimeClick(player)
            }
            NpcMenuView.TALK -> if (slot == TALK_BACK_SLOT) {
                playClick(player)
                openPreviousOr(player, NpcMenuView.MAIN)
            }
            NpcMenuView.REQUEST -> if (slot == BACK_SLOT) {
                playClick(player)
                openPreviousOr(player, NpcMenuView.MAIN)
            } else if (slot == 20) {
                handleDeadChestRecoveryRequest(player, holder.route)
            }
            NpcMenuView.DEAD_CHEST_SELECTION -> {
                when (slot) {
                    in DEAD_CHEST_SELECTION_SLOTS -> holder.deadChestList.getOrNull(DEAD_CHEST_SELECTION_SLOTS.indexOf(slot))?.let { chest ->
                        playClick(player)
                        navigate(player, holder.route, deadChestConfirmRoute(chest.identity))
                    }
                    BACK_SLOT -> {
                        playClick(player)
                        openPreviousOr(player, NpcMenuView.REQUEST)
                    }
                }
            }
            NpcMenuView.DEAD_CHEST_CONFIRM -> when (slot) {
                DEAD_CHEST_CONFIRM_SLOT -> holder.selectedDeadChest?.let { confirmDeadChestRecovery(player, it) }
                DEAD_CHEST_CANCEL_SLOT -> {
                    playClick(player)
                    openPreviousOr(player, NpcMenuView.REQUEST)
                }
            }
            NpcMenuView.DELIVERY_CONFIRM -> when (slot) {
                DELIVERY_CONFIRM_SLOT -> holder.selectedDeliveryItem?.let { confirmDelivery(player, it) }
                DELIVERY_CANCEL_SLOT -> {
                    playClick(player)
                    openView(player, NpcMenuView.DAILY)
                }
            }
            NpcMenuView.OAGE_BOX -> {
                if (slot == OAGE_BOX_BACK_SLOT) {
                    playClick(player)
                    openView(player, NpcMenuView.DAILY)
                } else if (slot in OAGE_BOX_REWARD_SLOTS) {
                    handleOageBoxRewardClick(player, holder, slot)
                }
            }
            NpcMenuView.OAGE_BOX_CONFIRM -> when (slot) {
                OAGE_BOX_CONFIRM_SLOT -> holder.selectedOageBoxReward?.let { confirmOageBoxReward(player, it) }
                OAGE_BOX_CANCEL_SLOT -> {
                    playClick(player)
                    openOageBox(player)
                }
            }
        }
    }

    private fun registerNavigationRoutes() {
        NpcMenuView.values().forEach { view ->
            navigation.registerOpener(MENU_OWNER, view.key) { player, route ->
                openRoute(player, route)
            }
        }
        navigation.registerMenuMatcher(MENU_OWNER) { inventory -> inventory.holder is NpcMenuHolder }
    }

    private fun route(view: NpcMenuView, payload: Map<String, String> = emptyMap()): MenuRoute {
        return MenuRoute(MENU_OWNER, view.key, payload)
    }

    private fun openRootView(player: Player, view: NpcMenuView) {
        if (!navigation.openRoot(player, route(view))) {
            openViewDirect(player, route(view))
        }
    }

    private fun navigate(player: Player, currentRoute: MenuRoute, targetRoute: MenuRoute) {
        if (!navigation.pushAndOpen(player, currentRoute, targetRoute)) {
            openRoute(player, targetRoute)
        }
    }

    private fun openPreviousOr(player: Player, fallbackView: NpcMenuView) {
        if (!navigation.openPrevious(player)) {
            openViewDirect(player, route(fallbackView))
        }
    }

    private fun openRoute(player: Player, route: MenuRoute): Boolean {
        val view = NpcMenuView.fromKey(route.id) ?: return false
        return when (view) {
            NpcMenuView.DEAD_CHEST_SELECTION -> openDeadChestSelectionRoute(player, route)
            NpcMenuView.DEAD_CHEST_CONFIRM -> openDeadChestConfirmRoute(player, route)
            else -> {
                openViewDirect(player, route(view))
                true
            }
        }
    }

    private fun openView(player: Player, view: NpcMenuView) {
        openRootView(player, view)
    }

    private fun openViewDirect(player: Player, route: MenuRoute) {
        val view = NpcMenuView.fromKey(route.id) ?: return
        if (view == NpcMenuView.OAGE_BOX) {
            openOageBox(player)
            return
        }

        val holder = NpcMenuHolder(player.uniqueId, view, route)
        val inventory = Bukkit.createInventory(holder, menuSize(view), title(player, view))
        holder.backingInventory = inventory
        render(player, holder, inventory)
        player.openInventory(inventory)
        playMenuOpen(player)
    }

    private fun openShopMenu(player: Player) {
        if (!::shopMenuService.isInitialized) return
        shopMenuService.open(player)
    }

    private fun openOageBox(player: Player) {
        val holder = NpcMenuHolder(player.uniqueId, NpcMenuView.OAGE_BOX, route(NpcMenuView.OAGE_BOX))
        val inventory = Bukkit.createInventory(holder, OAGE_BOX_SIZE, title(player, NpcMenuView.OAGE_BOX))
        holder.backingInventory = inventory
        render(player, holder, inventory)
        player.openInventory(inventory)
        playMenuOpen(player)
    }

    private fun openOageBoxConfirm(player: Player, reward: OageBoxReward) {
        val holder = NpcMenuHolder(player.uniqueId, NpcMenuView.OAGE_BOX_CONFIRM, route(NpcMenuView.OAGE_BOX_CONFIRM))
        holder.selectedOageBoxReward = reward
        val inventory = Bukkit.createInventory(holder, MENU_SIZE, "§8おあげBOX - 確認")
        holder.backingInventory = inventory
        render(player, holder, inventory)
        player.openInventory(inventory)
        playMenuOpen(player)
    }

    private fun render(player: Player, holder: NpcMenuHolder, inventory: Inventory) {
        GuiMenuItems.fillFramed(inventory)
        when (holder.view) {
            NpcMenuView.MAIN -> renderMain(player, inventory)
            NpcMenuView.DAILY -> renderDaily(player, inventory)
            NpcMenuView.TALK -> renderTalk(player, inventory)
            NpcMenuView.REQUEST -> renderRequest(player, inventory)
            NpcMenuView.DEAD_CHEST_SELECTION -> renderDeadChestSelection(player, holder, inventory)
            NpcMenuView.DEAD_CHEST_CONFIRM -> renderDeadChestConfirm(player, holder, inventory)
            NpcMenuView.DELIVERY_CONFIRM -> renderDeliveryConfirm(player, holder, inventory)
            NpcMenuView.OAGE_BOX -> renderOageBox(player, holder, inventory)
            NpcMenuView.OAGE_BOX_CONFIRM -> renderOageBoxConfirm(player, holder, inventory)
        }
    }

    private fun renderMain(player: Player, inventory: Inventory) {
        inventory.setItem(19, icon(player, "main.daily", Material.BAMBOO_HANGING_SIGN))
        inventory.setItem(21, icon(player, "main.shop", Material.CHEST))
        inventory.setItem(23, comingSoonIcon())
        inventory.setItem(25, icon(player, "main.request", Material.FLOWER_BANNER_PATTERN))
        inventory.setItem(40, oageChanHead())
    }

    private fun renderDaily(player: Player, inventory: Inventory) {
        inventory.setItem(DAILY_HEADER_SLOT, icon(player, "main.daily", Material.BAMBOO_HANGING_SIGN))
        inventory.setItem(DELIVERY_SLOT, deliveryIcon(player))
        inventory.setItem(PART_TIME_SLOT, partTimeIcon(player))
        inventory.setItem(BACK_SLOT, backButton(player))
    }

    private fun openDeadChestSelectionRoute(player: Player, route: MenuRoute): Boolean {
        if (!deadChestRecoveryService.isAvailable()) {
            playError(player)
            player.sendMessage("§cDeadChestが利用できないため、失具還術を行えません。")
            return false
        }
        val chests = deadChestRecoveryService.getLatestChests(player)
        if (chests.isEmpty()) {
            playError(player)
            player.sendMessage("§7現在アクティブなデスチェストはありません。")
            return false
        }
        val holder = NpcMenuHolder(player.uniqueId, NpcMenuView.DEAD_CHEST_SELECTION, route)
        holder.deadChestList = chests
        val inventory = Bukkit.createInventory(holder, DEAD_CHEST_SELECTION_LAYOUT.size, "§8失具還術 - 選択")
        holder.backingInventory = inventory
        render(player, holder, inventory)
        player.openInventory(inventory)
        playMenuOpen(player)
        return true
    }

    private fun openDeadChestConfirmRoute(player: Player, route: MenuRoute): Boolean {
        val identity = route.toDeadChestIdentity()
        val snapshot = identity?.let { target ->
            deadChestRecoveryService.getActiveChests(player).firstOrNull { it.identity == target }
        }
        val holder = NpcMenuHolder(player.uniqueId, NpcMenuView.DEAD_CHEST_CONFIRM, route)
        holder.selectedDeadChest = snapshot
        val inventory = Bukkit.createInventory(holder, MENU_SIZE, "§8失具還術 - 確認")
        holder.backingInventory = inventory
        render(player, holder, inventory)
        player.openInventory(inventory)
        playMenuOpen(player)
        return true
    }

    private fun openDeliveryConfirm(player: Player, item: DeliveryItemMatch) {
        val holder = NpcMenuHolder(player.uniqueId, NpcMenuView.DELIVERY_CONFIRM, route(NpcMenuView.DELIVERY_CONFIRM))
        holder.selectedDeliveryItem = item
        val inventory = Bukkit.createInventory(holder, MENU_SIZE, "§8奉納品 - 確認")
        holder.backingInventory = inventory
        render(player, holder, inventory)
        player.openInventory(inventory)
        playMenuOpen(player)
    }

    private fun renderDeadChestSelection(player: Player, holder: NpcMenuHolder, inventory: Inventory) {
        val whitePane = GuiMenuItems.backgroundPane(Material.WHITE_STAINED_GLASS_PANE)
        // FREE_45: 最新5件だけを中央行に並べる失具還術専用レイアウト。本文スロットは機能側で固定管理する。
        for (slot in DEAD_CHEST_SELECTION_SLOTS) {
            inventory.setItem(slot, whitePane)
        }
        val chests = holder.deadChestList
        chests.forEachIndexed { index, snapshot ->
            val slot = DEAD_CHEST_SELECTION_SLOTS.getOrNull(index) ?: return@forEachIndexed
            val desc = snapshot.description
            val loreBlocks = mutableListOf<GuiLoreBlock>()
            val infoLines = buildList {
                add(GuiLoreLine.Data("内容", "${desc.itemCount}個 (${desc.itemKinds}種類)", "§e"))
                if (desc.armorItemName != null) add(GuiLoreLine.Data("装備", desc.armorItemName, "§e"))
                add(GuiLoreLine.Data("ロストまで", desc.timeUntilLost, "§e"))
                add(GuiLoreLine.Data("死亡時刻", desc.createdAt, "§e"))
            }
            loreBlocks.add(GuiLoreBlock(infoLines))
            val operation = CCSystem.getAPI().getI18nString(player, "lore.click.any")
            val action = CCSystem.getAPI().getI18nString(player, "gui.npc.oage_shrine.request.restore.action")
            val resolvedAction = CCSystem.getAPI().getI18nString(
                player,
                "lore.action_single_with_operation",
                mapOf("operation" to operation, "action" to action)
            )
            loreBlocks.add(GuiLoreBlock(listOf(
                GuiLoreLine.SingleAction(operation, action, resolvedAction),
                GuiLoreLine.Data("初穂料", "🐿 ${ContentEconomyBridge.formatPrice(DEAD_CHEST_RECOVERY_COST)}", "§e")
            )))
            val renderedLore = CCSystem.getAPI().getLoreService().render(GuiLoreSpec.Blocks(loreBlocks))
            val icon = ItemStack(Material.CHEST).apply {
                itemMeta = itemMeta?.also { meta ->
                    meta.displayName(LegacyComponentSerializer.legacySection().deserialize("§eデスチェスト #${index + 1}"))
                    meta.lore(renderedLore)
                }
            }
            inventory.setItem(slot, icon)
        }
        inventory.setItem(BACK_SLOT, backButton(player))
    }

    private fun renderDeadChestConfirm(player: Player, holder: NpcMenuHolder, inventory: Inventory) {
        val snapshot = holder.selectedDeadChest ?: run {
            inventory.setItem(DEAD_CHEST_CONFIRM_PREVIEW_SLOT, GuiMenuItems.icon(Material.BARRIER, "§c対象が見つかりません"))
            inventory.setItem(DEAD_CHEST_CANCEL_SLOT, GuiMenuItems.icon(Material.RED_CONCRETE, "§c戻る", listOf(GuiLoreLine.Text("ご奉仕に戻る"))))
            return
        }
        val desc = snapshot.description
        val infoLines = buildList {
            add(GuiLoreLine.Data("内容", "${desc.itemCount}個 (${desc.itemKinds}種類)", "§e"))
            if (desc.armorItemName != null) add(GuiLoreLine.Data("装備", desc.armorItemName, "§e"))
            add(GuiLoreLine.Data("ロストまで", desc.timeUntilLost, "§e"))
            add(GuiLoreLine.Data("死亡時刻", desc.createdAt, "§e"))
        }
        val renderedLore = CCSystem.getAPI().getLoreService().render(
            GuiLoreSpec.Blocks(listOf(GuiLoreBlock(infoLines)))
        )
        val previewItem = ItemStack(Material.CHEST).apply {
            itemMeta = itemMeta?.also { meta ->
                meta.displayName(LegacyComponentSerializer.legacySection().deserialize(desc.summary))
                meta.lore(renderedLore)
            }
        }
        inventory.setItem(DEAD_CHEST_CONFIRM_PREVIEW_SLOT, previewItem)
        inventory.setItem(DEAD_CHEST_CONFIRM_SLOT, GuiMenuItems.icon(
            Material.LIME_CONCRETE,
            "§a回収する",
            listOf(
                GuiLoreLine.Text("このデスチェストを回収"),
                GuiLoreLine.Data("初穂料", formatAcorn(DEAD_CHEST_RECOVERY_COST), "§e")
            )
        ))
        inventory.setItem(DEAD_CHEST_CANCEL_SLOT, GuiMenuItems.icon(Material.RED_CONCRETE, "§cキャンセル", listOf(GuiLoreLine.Text("ご奉仕に戻る"))))
    }

    private fun confirmDeadChestRecovery(player: Player, snapshot: DeadChestSnapshot) {
        if (!prepareDeadChestRecovery(player, snapshot)) return

        player.closeInventory()
        player.playSound(player.location, "minecraft:entity.fox.ambient", 1.0f, 1.0f)
        player.sendMessage(OageMessageSender.getPrefix(plugin) + "ほにゃらら～ ⋯")
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            executeDeadChestRecovery(player, snapshot)
        }, 20L)
    }

    private fun prepareDeadChestRecovery(player: Player, snapshot: DeadChestSnapshot): Boolean {
        if (!deadChestRecoveryService.isAvailable()) {
            playError(player)
            player.closeInventory()
            player.sendMessage("§cDeadChestが利用できないため、失具還術を行えません。")
            return false
        }
        if (ContentEconomyBridge.get(plugin) == null) {
            playError(player)
            player.sendMessage("§c経済機能が利用できません。")
            return false
        }
        val current = deadChestRecoveryService.getActiveChests(player).firstOrNull { sameDeadChest(it, snapshot) } ?: run {
            playError(player)
            player.closeInventory()
            player.sendMessage("§c回収対象のデスチェストが見つかりませんでした。")
            return false
        }
        val balance = ContentEconomyBridge.balance(plugin, player) ?: 0.0
        if (balance < DEAD_CHEST_RECOVERY_COST || !ContentEconomyBridge.has(plugin, player, DEAD_CHEST_RECOVERY_COST)) {
            playError(player)
            player.sendMessage(insufficientAcornLine(DEAD_CHEST_RECOVERY_COST, balance))
            return false
        }
        if (!deadChestRecoveryService.canRecoverToInventory(player, current.chest)) {
            playError(player)
            player.sendMessage("§cインベントリに空きがありません。")
            return false
        }
        return true
    }

    private fun executeDeadChestRecovery(player: Player, snapshot: DeadChestSnapshot) {
        if (!player.isOnline) return
        if (!deadChestRecoveryService.isAvailable() || ContentEconomyBridge.get(plugin) == null) {
            playError(player)
            player.sendMessage("§c失具還術を行えませんでした。")
            return
        }
        val current = deadChestRecoveryService.getActiveChests(player).firstOrNull { sameDeadChest(it, snapshot) } ?: run {
            playError(player)
            player.sendMessage("§c回収対象のデスチェストが見つかりませんでした。")
            return
        }
        val balance = ContentEconomyBridge.balance(plugin, player) ?: 0.0
        if (balance < DEAD_CHEST_RECOVERY_COST || !ContentEconomyBridge.has(plugin, player, DEAD_CHEST_RECOVERY_COST)) {
            playError(player)
            player.sendMessage(insufficientAcornLine(DEAD_CHEST_RECOVERY_COST, balance))
            return
        }
        if (!deadChestRecoveryService.canRecoverToInventory(player, current.chest)) {
            playError(player)
            player.sendMessage("§cインベントリに空きがありません。")
            return
        }
        if (!ContentEconomyBridge.withdraw(plugin, player, DEAD_CHEST_RECOVERY_COST)) {
            playError(player)
            player.sendMessage("§c初穂料を納められませんでした。")
            return
        }

        val recovered = deadChestRecoveryService.recoverToInventory(player, current.chest)
        if (!recovered) {
            ContentEconomyBridge.deposit(plugin, player, DEAD_CHEST_RECOVERY_COST)
            playError(player)
            player.closeInventory()
            player.sendMessage("§c回収に失敗したため、初穂料を返しました。")
            return
        }

        player.sendMessage(OageMessageSender.getPrefix(plugin) + "ほい！")
        player.playSound(player.location, "minecraft:block.note_block.pling", 1.0f, 1.75f)
        player.playSound(player.location, "minecraft:item.trident.thunder", 1.0f, 2.0f)
        player.sendMessage("§bおあげちゃんの力で、失くしたものを呼び出しました！")
    }

    private fun sameDeadChest(left: DeadChestSnapshot, right: DeadChestSnapshot): Boolean {
        return left.identity == right.identity
    }

    private fun deadChestConfirmRoute(identity: DeadChestIdentity): MenuRoute {
        return route(
            NpcMenuView.DEAD_CHEST_CONFIRM,
            mapOf(
                "ownerId" to identity.ownerId.toString(),
                "worldName" to identity.worldName,
                "x" to identity.x.toString(),
                "y" to identity.y.toString(),
                "z" to identity.z.toString(),
                "createdAtMillis" to (identity.createdAtMillis?.toString() ?: "")
            )
        )
    }

    private fun MenuRoute.toDeadChestIdentity(): DeadChestIdentity? {
        val ownerId = payload["ownerId"]?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return null
        val worldName = payload["worldName"] ?: return null
        val x = payload["x"]?.toIntOrNull() ?: return null
        val y = payload["y"]?.toIntOrNull() ?: return null
        val z = payload["z"]?.toIntOrNull() ?: return null
        val createdAtMillis = payload["createdAtMillis"]?.takeIf { it.isNotBlank() }?.toLongOrNull()
        return DeadChestIdentity(ownerId, worldName, x, y, z, createdAtMillis)
    }

    private fun renderTalk(player: Player, inventory: Inventory) {
        inventory.setItem(4, comingSoonIcon())
        inventory.setItem(20, icon(player, "talk.topic.arena", Material.DIAMOND_SWORD))
        listOf(21, 22, 23, 24, 29, 30, 31, 32, 33).forEach {
            inventory.setItem(it, icon(player, "talk.topic.locked", Material.GRAY_DYE))
        }
        inventory.setItem(TALK_BACK_SLOT, backButton(player))
    }

    private fun renderRequest(player: Player, inventory: Inventory) {
        inventory.setItem(4, icon(player, "main.request", Material.FLOWER_BANNER_PATTERN))
        inventory.setItem(20, deadChestRecoveryIcon(player))
        inventory.setItem(24, comingSoonIcon())
        inventory.setItem(BACK_SLOT, backButton(player))
    }

    private fun handleDeadChestRecoveryRequest(player: Player, currentRoute: MenuRoute) {
        if (!deadChestRecoveryService.isAvailable()) {
            playError(player)
            player.sendMessage("§cDeadChestが利用できないため、失具還術を行えません。")
            return
        }
        val chests = deadChestRecoveryService.getLatestChests(player)
        if (chests.isEmpty()) {
            playError(player)
            player.sendMessage("§7現在アクティブなデスチェストはありません。")
            return
        }
        playClick(player)
        navigate(player, currentRoute, route(NpcMenuView.DEAD_CHEST_SELECTION))
    }

    private fun deadChestRecoveryIcon(player: Player): ItemStack {
        val check = evaluateDeadChestRecovery(player)
        val body = listOf(
            "デスチェストからアイテムを回収",
            "回収したアイテムはインベントリへ格納",
            "連続実行不可",
            "",
            "一覧から回収対象を選択"
        )
        val renderedLore = CCSystem.getAPI().getLoreService().render(
            GuiLoreSpec.Blocks(listOf(
                GuiLoreBlock(body.filter(String::isNotBlank).map { GuiLoreLine.Text(it) }),
                GuiLoreBlock(listOf(if (check.executable) {
                    GuiLoreLine.Data("初穂料", formatAcorn(DEAD_CHEST_RECOVERY_COST), "§e")
                } else {
                    GuiLoreLine.Warning(check.failureMessage ?: "失具還術を実行不可")
                }))
            ))
        )
        return GuiMenuItems.icon(Material.RECOVERY_COMPASS, "§a失具還術").apply {
            itemMeta = itemMeta?.also { meta ->
                meta.lore(renderedLore)
                meta.setEnchantmentGlintOverride(check.executable)
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
            }
        }
    }

    private fun evaluateDeadChestRecovery(player: Player): DeadChestRecoveryCheck {
        if (!deadChestRecoveryService.isAvailable()) {
            return DeadChestRecoveryCheck(null, null, false, "DeadChestが利用できないため実行不可")
        }
        val hasChests = deadChestRecoveryService.getLatestChests(player).isNotEmpty()
        if (!hasChests) {
            return DeadChestRecoveryCheck(null, null, false, "現在アクティブなデスチェストなし")
        }
        if (ContentEconomyBridge.get(plugin) == null) {
            return DeadChestRecoveryCheck(null, null, false, "経済機能を利用不可")
        }
        val balance = ContentEconomyBridge.balance(plugin, player) ?: 0.0
        if (balance < DEAD_CHEST_RECOVERY_COST || !ContentEconomyBridge.has(plugin, player, DEAD_CHEST_RECOVERY_COST)) {
            return DeadChestRecoveryCheck(
                null,
                balance,
                false,
                "どんぐりが不足（必要 ${ContentEconomyBridge.formatPrice(DEAD_CHEST_RECOVERY_COST)} / 所持 ${ContentEconomyBridge.formatPrice(balance)}）"
            )
        }
        return DeadChestRecoveryCheck(null, balance, true, null)
    }

    private fun renderOageBox(player: Player, holder: NpcMenuHolder, inventory: Inventory) {
        inventory.setItem(OAGE_BOX_HEADER_SLOT, partTimeIcon(player))
        inventory.setItem(OAGE_BOX_BACK_SLOT, backButton(player))
        val rewards = drawOageBoxRewards()
        holder.oageBoxRewards = OAGE_BOX_REWARD_SLOTS.zip(rewards).toMap()
        OAGE_BOX_REWARD_SLOTS.forEach { slot ->
            inventory.setItem(slot, holder.oageBoxRewards[slot]?.icon(player) ?: GuiMenuItems.icon(Material.GRAY_DYE, "§7空き"))
        }
    }

    private fun renderOageBoxConfirm(player: Player, holder: NpcMenuHolder, inventory: Inventory) {
        val reward = holder.selectedOageBoxReward ?: run {
            inventory.setItem(OAGE_BOX_CONFIRM_PREVIEW_SLOT, GuiMenuItems.icon(Material.BARRIER, "§c報酬が見つかりません"))
            inventory.setItem(OAGE_BOX_CANCEL_SLOT, GuiMenuItems.icon(Material.RED_CONCRETE, "§c戻る", listOf(GuiLoreLine.Text("おあげBOXに戻る"))))
            return
        }
        inventory.setItem(OAGE_BOX_CONFIRM_PREVIEW_SLOT, reward.icon(player))
        inventory.setItem(OAGE_BOX_CONFIRM_SLOT, GuiMenuItems.icon(Material.LIME_CONCRETE, "§a受け取る", listOf(GuiLoreLine.Text("このご褒美を受け取る"))))
        inventory.setItem(OAGE_BOX_CANCEL_SLOT, GuiMenuItems.icon(Material.RED_CONCRETE, "§cキャンセル", listOf(GuiLoreLine.Text("おあげBOXに戻る"))))
    }

    private fun renderDeliveryConfirm(player: Player, holder: NpcMenuHolder, inventory: Inventory) {
        val selected = holder.selectedDeliveryItem
        inventory.setItem(DELIVERY_CONFIRM_PREVIEW_SLOT, boxedDaiginjoItem())
        inventory.setItem(
            DELIVERY_CONFIRM_SLOT,
            GuiMenuItems.icon(
                Material.LIME_CONCRETE,
                "§a奉納する",
                listOf(
                    GuiLoreLine.Data("奉納品", selected?.displayName ?: "奉納品", "§f"),
                    GuiLoreLine.Data("報酬", "$DELIVERY_WORLD_POINT_REWARD ワールドポイント", "§e")
                )
            )
        )
        inventory.setItem(DELIVERY_CANCEL_SLOT, GuiMenuItems.icon(Material.RED_CONCRETE, "§cキャンセル", listOf(GuiLoreLine.Text("日課メニューに戻る"))))
    }

    private fun deliveryIcon(player: Player): ItemStack {
        val profession = professionProvider(player.uniqueId)
        if (profession != Profession.BREWER) {
            return GuiMenuItems.icon(Material.GRAY_DYE, "§e奉納品", listOf(GuiLoreLine.Warning("職業に就くと奉納可能")))
        }

        val offering = boxedDaiginjoItem()
        val offeringName = legacyDisplayName(offering).ifBlank { "§f箱入り大吟醸" }
        val offeringLore = legacyLore(offering)
        val statusLine = deliveryStatusLine(player.uniqueId)
        val body = buildList {
            addAll(offeringLore)
            add("§7おあげ神社に${offeringName}§7を奉納することで、")
            add("§7お礼として§6ワールドポイント§7と§6🐿 §eどんぐり§7を貰えます")
            add("§c※ 奉納は1週間に1回まで行えます")
            add(statusLine)
        }
        val renderedLore = CCSystem.getAPI().getLoreService().render(
            GuiLoreSpec.Blocks(listOf(
                GuiLoreBlock(offeringLore.map { GuiLoreLine.Text(it) }),
                GuiLoreBlock(listOf(
                    GuiLoreLine.Text("おあげ神社に奉納"),
                    GuiLoreLine.Data("お礼", "ワールドポイント・どんぐり", "§6"),
                    GuiLoreLine.Warning("奉納は1週間に1回まで")
                )),
                GuiLoreBlock(listOf(GuiLoreLine.Text(statusLine)))
            ))
        )
        val icon = GuiMenuItems.icon(Material.PLAYER_HEAD, "§e奉納品").apply {
            itemMeta = itemMeta?.also { it.lore(renderedLore) }
        }
        val skullMeta = icon.itemMeta as? SkullMeta
        val offeringMeta = offering.itemMeta as? SkullMeta
        if (skullMeta != null && offeringMeta != null) {
            skullMeta.playerProfile = offeringMeta.playerProfile
            icon.itemMeta = skullMeta
        }
        return icon
    }

    private fun partTimeIcon(player: Player): ItemStack {
        val completed = isPartTimeTaskCompleted(player.uniqueId, PART_TIME_TASK_ARENA)
        val opened = isPartTimeOpened(player.uniqueId)
        val canClaim = completed && !opened
        val renderedLore = CCSystem.getAPI().getLoreService().render(
            GuiLoreSpec.Blocks(buildList {
                add(GuiLoreBlock(listOf(
                    GuiLoreLine.Text("今日のお仕事"),
                    GuiLoreLine.StyledText("アリーナミッションを1回完了", if (completed) "§a" else "§f", false)
                )))
                add(GuiLoreBlock(buildList {
                    add(GuiLoreLine.Text("おあげちゃんからの依頼を達成"))
                    add(GuiLoreLine.Text("ご褒美を獲得"))
                    if (opened) add(GuiLoreLine.Warning("今日の分は受け取り済み"))
                }))
                if (canClaim) add(GuiLoreBlock(listOf(GuiLoreLine.Text("今日のタスクがすべて完了"), GuiLoreLine.SingleAction("クリック", "報酬を受け取る", "クリックで報酬を受け取る"))))
            })
        )
        return GuiMenuItems.icon(Material.BARREL, "§6おあげちゃんアルバイト").apply {
            itemMeta = itemMeta?.also { it.lore(renderedLore) }
        }
    }

    private fun comingSoonIcon(): ItemStack = GuiMenuItems.icon(Material.BEDROCK, "§7Coming Soon")

    private fun boxedDaiginjoItem(amount: Int = 1): ItemStack = requireNotNull(CustomItemManager.createItem(BoxedDaiginjoItem.fullId, amount))

    private fun handleDeliveryClick(player: Player) {
        if (professionProvider(player.uniqueId) != Profession.BREWER || isDeliveryCompleted(player.uniqueId)) {
            return
        }

        val target = findDeliveryItem(player) ?: run {
            player.sendMessage("§cインベントリ内に箱入り大吟醸がありません。")
            return
        }

        playClick(player)
        openDeliveryConfirm(player, target)
    }

    private fun confirmDelivery(player: Player, selected: DeliveryItemMatch) {
        if (professionProvider(player.uniqueId) != Profession.BREWER || isDeliveryCompleted(player.uniqueId)) {
            player.closeInventory()
            return
        }

        val target = player.inventory.getItem(selected.slot)
            ?.takeIf { it.amount > 0 && isDeliveryItem(it) }
            ?.let { DeliveryItemMatch(selected.slot, legacyDisplayName(it).ifBlank { selected.displayName }) }
            ?: findDeliveryItem(player)
            ?: run {
                playError(player)
                player.sendMessage("§cインベントリ内に箱入り大吟醸がありません。")
                openView(player, NpcMenuView.DAILY)
                return
            }

        grantWorldPoint(player.uniqueId, DELIVERY_WORLD_POINT_REWARD) ?: return
        consumeOneDeliveryItem(player, target.slot)
        markDeliveryCompleted(player.uniqueId)
        player.closeInventory()
        player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f)
        player.playSound(player.location, Sound.ENTITY_CHICKEN_AMBIENT, 0.8f, 1.5f)
        player.sendMessage("${target.displayName}§7を奉納して、§6🛖 §e$DELIVERY_WORLD_POINT_REWARD ワールドポイント§7を手に入れました！")
        OageMessageSender.send(player, "§fありがとうございます！", plugin, sound = null, volume = 1f, pitch = 1.5f)
    }

    private fun findDeliveryItem(player: Player): DeliveryItemMatch? {
        player.inventory.storageContents.forEachIndexed { slot, item ->
            if (item != null && item.amount > 0 && isDeliveryItem(item)) {
                return DeliveryItemMatch(slot, legacyDisplayName(item).ifBlank { "§f箱入り大吟醸" })
            }
        }
        return null
    }

    private fun isDeliveryItem(item: ItemStack): Boolean = CustomItemManager.identify(item)?.fullId == BoxedDaiginjoItem.fullId

    private fun consumeOneDeliveryItem(player: Player, slot: Int) {
        val item = player.inventory.getItem(slot) ?: return
        if (item.amount <= 1) player.inventory.setItem(slot, null) else {
            item.amount -= 1
            player.inventory.setItem(slot, item)
        }
    }

    private fun grantWorldPoint(playerId: UUID, amount: Int): Int? {
        return runCatching {
            val myWorldManager = Bukkit.getPluginManager().getPlugin("MyWorldManager") ?: return null
            val apiClass = Class.forName("me.awabi2048.myworldmanager.api.MyWorldManagerApi", true, myWorldManager.javaClass.classLoader)
            val available = apiClass.getMethod("isWorldPointServiceAvailable").invoke(null) as? Boolean ?: false
            if (!available) null else apiClass.getMethod("addWorldPoint", UUID::class.java, Int::class.javaPrimitiveType).invoke(null, playerId, amount) as? Int
        }.getOrNull()
    }

    private fun isWorldPointAvailable(): Boolean {
        return runCatching {
            val myWorldManager = Bukkit.getPluginManager().getPlugin("MyWorldManager") ?: return false
            val apiClass = Class.forName("me.awabi2048.myworldmanager.api.MyWorldManagerApi", true, myWorldManager.javaClass.classLoader)
            apiClass.getMethod("isWorldPointServiceAvailable").invoke(null) as? Boolean ?: false
        }.getOrDefault(false)
    }

    private fun handlePartTimeClick(player: Player) {
        if (isPartTimeOpened(player.uniqueId) || !isPartTimeTaskCompleted(player.uniqueId, PART_TIME_TASK_ARENA)) return
        playClick(player)
        player.closeInventory()
        OageMessageSender.send(player, "§f今日もありがとうございます！好きなものを持っていってね", plugin, sound = null, volume = 1f, pitch = 1.5f)
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (player.isOnline && !isPartTimeOpened(player.uniqueId)) {
                openOageBox(player)
            }
        }, 30L)
    }

    private fun handleOageBoxRewardClick(player: Player, holder: NpcMenuHolder, slot: Int) {
        if (isPartTimeOpened(player.uniqueId)) {
            playError(player)
            player.closeInventory()
            player.sendMessage("§7今日のご褒美は受け取り済みです。")
            return
        }
        val reward = holder.oageBoxRewards[slot] ?: return
        playClick(player)
        openOageBoxConfirm(player, reward)
    }

    private fun confirmOageBoxReward(player: Player, reward: OageBoxReward) {
        if (isPartTimeOpened(player.uniqueId)) {
            playError(player)
            player.closeInventory()
            player.sendMessage("§7今日のご褒美は受け取り済みです。")
            return
        }
        val failure = validateOageBoxRewardGrant(player, reward)
        if (failure != null) {
            playError(player)
            player.sendMessage(failure)
            return
        }
        if (!grantOageBoxReward(player, reward)) {
            playError(player)
            player.sendMessage("§cご褒美を受け取れませんでした。")
            return
        }
        markPartTimeOpened(player.uniqueId)
        playClick(player)
        player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 2.0f)
        player.sendMessage("§aおあげBOXから ${reward.receivedDisplayName(player)} を入手しました！")
        player.closeInventory()
    }

    private fun validateOageBoxRewardGrant(player: Player, reward: OageBoxReward): String? = when (reward) {
        is OageBoxReward.CustomItem -> {
            val item = CustomItemManager.createItemForPlayer(reward.itemId, player, reward.amount)
                ?: return "§c未登録のカスタムアイテムです: ${reward.itemId}"
            if (canAcceptReward(player, item)) null else "§cインベントリに空きがありません。"
        }
        is OageBoxReward.Acorn -> if (ContentEconomyBridge.get(plugin) != null) null else "§c経済機能が利用できません。"
        is OageBoxReward.WorldPoint -> if (isWorldPointAvailable()) null else "§cワールドポイント機能が利用できません。"
    }

    private fun grantOageBoxReward(player: Player, reward: OageBoxReward): Boolean = when (reward) {
        is OageBoxReward.CustomItem -> {
            val item = CustomItemManager.createItemForPlayer(reward.itemId, player, reward.amount) ?: return false
            player.inventory.addItem(item).isEmpty()
        }
        is OageBoxReward.Acorn -> ContentEconomyBridge.deposit(plugin, player, reward.amount.toDouble())
        is OageBoxReward.WorldPoint -> grantWorldPoint(player.uniqueId, reward.amount) != null
    }

    private fun canAcceptReward(player: Player, item: ItemStack): Boolean {
        if (player.inventory.firstEmpty() >= 0) return true
        return player.inventory.storageContents.filterNotNull().any { existing -> existing.isSimilar(item) && existing.amount + item.amount <= existing.maxStackSize }
    }

    private fun icon(player: Player, key: String, material: Material): ItemStack = GuiMenuItems.icon(material, text(player, "$key.name"), list(player, "$key.lore").map { GuiLoreLine.Text(it) })

    private fun oageChanHead(): ItemStack {
        val item = GuiMenuItems.icon(Material.PLAYER_HEAD, "§aおあげ神社の授与所")
        val meta = item.itemMeta as? SkullMeta ?: return item
        val profile = oageChanProfile
        if (profile != null) {
            meta.playerProfile = profile
        } else {
            meta.owningPlayer = Bukkit.getOfflinePlayer("OageChan_")
        }
        item.itemMeta = meta
        return item
    }

    private fun loadOageChanProfile() {
        Bukkit.createProfile("OageChan_").update().thenAccept { profile ->
            Bukkit.getScheduler().runTask(plugin, Runnable {
                oageChanProfile = profile
            })
        }.exceptionally { error ->
            plugin.logger.warning("[Oage Shrine] OageChan_ のヘッドプロフィール取得に失敗しました: ${error.message}")
            null
        }
    }

    private fun backButton(player: Player): ItemStack = GuiMenuItems.backButton(text(player, "back.name"), list(player, "back.lore").map { GuiLoreLine.Text(it) })

    private fun menuSize(view: NpcMenuView): Int = when (view) {
        NpcMenuView.TALK -> TALK_MENU_SIZE
        NpcMenuView.DEAD_CHEST_SELECTION -> DEAD_CHEST_SELECTION_LAYOUT.size
        NpcMenuView.OAGE_BOX -> OAGE_BOX_SIZE
        else -> MENU_SIZE
    }

    private fun title(player: Player, view: NpcMenuView): String = text(player, "title.${view.key}")

    private fun text(player: Player, key: String): String {
        val locale = ContentLocaleResolver.resolve(player)
        return com.awabi2048.ccsystem.CCSystem.getAPI().getI18nString(locale, "gui.npc.oage_shrine.$key", emptyMap()).replace('&', '§')
    }

    private fun list(player: Player, key: String): List<String> {
        val locale = ContentLocaleResolver.resolve(player)
        return com.awabi2048.ccsystem.CCSystem.getAPI().getI18nStringList(locale, "gui.npc.oage_shrine.$key", emptyMap()).map { it.replace('&', '§') }
    }

    private fun playClick(player: Player) {
        CCSystem.getAPI().getMenuSoundService().onMenuClick(player, MENU_ID)
    }

    private fun playError(player: Player) {
        CCSystem.getAPI().getMenuSoundService().onMenuClick(player, MENU_ID, com.awabi2048.ccsystem.api.gui.MenuClickType.CANCEL)
    }

    private fun playMenuOpen(player: Player) {
        CCSystem.getAPI().getMenuSoundService().onMenuOpen(player, MENU_ID)
    }

    private fun formatAcorn(amount: Double, color: String = "§e"): String = ContentEconomyBridge.formatAcorn(amount, color)

    private fun insufficientAcornLine(required: Double, balance: Double): String =
        "§6🐿 §c§n${ContentEconomyBridge.formatPrice(required)}§r §cどんぐりが足りません §7(手持ちの個数 ${formatAcorn(balance)}§7)"

    private fun legacy(text: String): Component = LegacyComponentSerializer.legacySection().deserialize(text).decoration(TextDecoration.ITALIC, false)

    private fun legacyDisplayName(item: ItemStack): String = item.itemMeta?.displayName()?.let { LegacyComponentSerializer.legacySection().serialize(it) }.orEmpty()

    private fun legacyLore(item: ItemStack): List<String> = item.itemMeta?.lore()?.map { LegacyComponentSerializer.legacySection().serialize(it) }.orEmpty()

    private fun markPartTimeCompleted(playerId: UUID, taskId: String) {
        state.set("part_time.completed_tasks.$playerId.$taskId", true)
        saveState()
    }

    private fun isPartTimeTaskCompleted(playerId: UUID, taskId: String): Boolean = state.getBoolean("part_time.completed_tasks.$playerId.$taskId", false)

    private fun markPartTimeOpened(playerId: UUID) {
        state.set("part_time.opened.$playerId", true)
        saveState()
    }

    private fun isPartTimeOpened(playerId: UUID): Boolean = state.getBoolean("part_time.opened.$playerId", false)

    private fun isDeliveryCompleted(playerId: UUID): Boolean = state.getBoolean("delivery.completed.$playerId", false)

    private fun markDeliveryCompleted(playerId: UUID) {
        state.set("delivery.completed.$playerId", true)
        saveState()
    }

    private fun deliveryStatusLine(playerId: UUID): String = if (isDeliveryCompleted(playerId)) "§b今週は奉納済みです" else "§e今週はまだ奉納できます"

    private fun saveState() {
        stateFile.parentFile?.mkdirs()
        state.save(stateFile)
    }

    private fun ensureConfig() {
        if (!configFile.exists()) {
            configFile.parentFile?.mkdirs()
            plugin.saveResource(CONFIG_PATH, false)
        }
    }

    private fun ensureState() {
        if (!stateFile.exists()) {
            stateFile.parentFile?.mkdirs()
            stateFile.createNewFile()
        }
    }

    private fun loadMenuConfig(): YamlConfiguration {
        return YamlConfiguration().also { config ->
            config.options().pathSeparator('/')
            config.load(configFile)
        }
    }

    private fun loadOageBoxRewardDefinitions(config: YamlConfiguration): OageBoxRewardDefinitions {
        val itemSection = config.getConfigurationSection("oage_box/rewards/item")
            ?: throw IllegalStateException("config/npc/menu.yml: oage_box.rewards.item がありません")
        val itemRewards = itemSection.getKeys(false).map { itemId ->
            val amount = itemSection.getInt("$itemId/amount", 1).coerceAtLeast(1)
            val weight = itemSection.getInt("$itemId/weight", 1).coerceAtLeast(1)
            if (CustomItemManager.getItem(itemId) == null) {
                throw IllegalStateException("config/npc/menu.yml: 未登録のカスタムアイテムIDです: $itemId")
            }
            OageBoxItemRewardDefinition(itemId, amount, weight)
        }
        val acornRewards = loadWeightedAmountDefinitions(config, "oage_box/rewards/dg")
        val worldPointRewards = loadWeightedAmountDefinitions(config, "oage_box/rewards/world_point")
        require(itemRewards.isNotEmpty()) { "config/npc/menu.yml: oage_box.rewards.item は1件以上必要です" }
        require(acornRewards.isNotEmpty()) { "config/npc/menu.yml: oage_box.rewards.dg は1件以上必要です" }
        require(worldPointRewards.isNotEmpty()) { "config/npc/menu.yml: oage_box.rewards.world_point は1件以上必要です" }
        return OageBoxRewardDefinitions(itemRewards, acornRewards, worldPointRewards)
    }

    private fun loadWeightedAmountDefinitions(config: YamlConfiguration, path: String): List<WeightedAmountRewardDefinition> {
        return config.getMapList(path).mapIndexed { index, raw ->
            val amount = (raw["amount"] as? Number)?.toInt() ?: raw["amount"]?.toString()?.toIntOrNull()
                ?: throw IllegalStateException("config/npc/menu.yml: $path[$index].amount が不正です")
            val weight = (raw["weight"] as? Number)?.toInt() ?: raw["weight"]?.toString()?.toIntOrNull()
                ?: throw IllegalStateException("config/npc/menu.yml: $path[$index].weight が不正です")
            WeightedAmountRewardDefinition(amount.coerceAtLeast(1), weight.coerceAtLeast(1))
        }
    }

    private fun drawOageBoxRewards(): List<OageBoxReward> {
        val random = Random(oageBoxDailySeed())
        val rewards = mutableListOf<OageBoxReward>()
        val acorn = weightedAmountDefinition(oageBoxRewards.acornRewards, random)
        val worldPoint = weightedAmountDefinition(oageBoxRewards.worldPointRewards, random)
        rewards.add(OageBoxReward.Acorn(acorn.amount))
        rewards.add(OageBoxReward.WorldPoint(worldPoint.amount))
        rewards.addAll(drawOageBoxItemRewards(random))
        return rewards.shuffled(random)
    }

    private fun drawOageBoxItemRewards(random: Random): List<OageBoxReward.CustomItem> {
        val rewards = mutableListOf<OageBoxReward.CustomItem>()
        var pool = oageBoxRewards.itemRewards.toMutableList()
        repeat(OAGE_BOX_REWARD_SLOTS.size - 2) {
            if (pool.isEmpty()) {
                pool = oageBoxRewards.itemRewards.toMutableList()
            }
            val selected = weightedItemDefinition(pool, random)
            rewards.add(OageBoxReward.CustomItem(selected.itemId, selected.amount))
            pool.remove(selected)
        }
        return rewards
    }

    private fun weightedItemDefinition(definitions: List<OageBoxItemRewardDefinition>, random: Random): OageBoxItemRewardDefinition {
        val total = definitions.sumOf { it.weight }.coerceAtLeast(1)
        var cursor = random.nextInt(total)
        for (definition in definitions) {
            cursor -= definition.weight
            if (cursor < 0) return definition
        }
        return definitions.first()
    }

    private fun weightedAmountDefinition(definitions: List<WeightedAmountRewardDefinition>, random: Random): WeightedAmountRewardDefinition {
        val total = definitions.sumOf { it.weight }.coerceAtLeast(1)
        var cursor = random.nextInt(total)
        for (definition in definitions) {
            cursor -= definition.weight
            if (cursor < 0) return definition
        }
        return definitions.first()
    }

    private fun oageBoxDailySeed(): Long = LocalDate.now(ZoneId.systemDefault()).toEpochDay()

    private fun OageBoxReward.icon(player: Player): ItemStack = when (this) {
        is OageBoxReward.CustomItem -> CustomItemManager.createItemForPlayer(itemId, player, amount)
            ?: GuiMenuItems.icon(Material.BARRIER, "§c未登録アイテム", listOf(GuiLoreLine.Data("ID", itemId, "§7")))
        is OageBoxReward.Acorn -> GuiMenuItems.icon(
            Material.PLAYER_HEAD,
            "§6収穫したてのどんぐり",
            listOf(GuiLoreLine.Text(acornDisplay(amount)))
        ).apply { applyAcornTexture() }
        is OageBoxReward.WorldPoint -> GuiMenuItems.icon(
            Material.HONEY_BOTTLE,
            "§6世界樹の樹液",
            listOf(GuiLoreLine.Text(worldPointDisplay(amount)))
        )
    }

    private fun ItemStack.applyAcornTexture(): ItemStack {
        val meta = itemMeta as? SkullMeta ?: return this
        val profile = Bukkit.createProfile(UUID.randomUUID(), "oage_box_acorn")
        profile.setProperty(com.destroystokyo.paper.profile.ProfileProperty("textures", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDNhOWEwNzFiNDI4M2M3NTYyNjg3NWM3YmFmZDBlZWYxM2IzZGZmNThhZDk2ODBhMTY1Mjg4YTcxNzFjNTYzNSJ9fX0="))
        meta.playerProfile = profile
        itemMeta = meta
        return this
    }

    private fun OageBoxReward.receivedDisplayName(player: Player): String = when (this) {
        is OageBoxReward.CustomItem -> CustomItemManager.createItemForPlayer(itemId, player, amount)
            ?.let { legacyDisplayName(it) }
            ?.ifBlank { "§f$itemId" }
            ?: "§f$itemId"
        is OageBoxReward.Acorn -> acornDisplay(amount)
        is OageBoxReward.WorldPoint -> worldPointDisplay(amount)
    }

    private fun acornDisplay(amount: Int): String = "🐿 ${ContentEconomyBridge.formatPrice(amount.toDouble())}"

    private fun worldPointDisplay(amount: Int): String = "🛖 $amount"

    private data class OageBoxRewardDefinitions(
        val itemRewards: List<OageBoxItemRewardDefinition>,
        val acornRewards: List<WeightedAmountRewardDefinition>,
        val worldPointRewards: List<WeightedAmountRewardDefinition>
    )
    private data class OageBoxItemRewardDefinition(val itemId: String, val amount: Int, val weight: Int)
    private data class WeightedAmountRewardDefinition(val amount: Int, val weight: Int)
    private sealed class OageBoxReward {
        data class CustomItem(val itemId: String, val amount: Int) : OageBoxReward()
        data class Acorn(val amount: Int) : OageBoxReward()
        data class WorldPoint(val amount: Int) : OageBoxReward()
    }
    private data class DeliveryItemMatch(val slot: Int, val displayName: String)
    private data class DeadChestRecoveryCheck(
        val snapshot: DeadChestSnapshot?,
        val balance: Double?,
        val executable: Boolean,
        val failureMessage: String?
    )

    private enum class NpcMenuView(val key: String) {
        MAIN("main"),
        DAILY("daily"),
        TALK("talk"),
        REQUEST("request"),
        DEAD_CHEST_CONFIRM("dead_chest_confirm"),
        DEAD_CHEST_SELECTION("dead_chest_selection"),
        DELIVERY_CONFIRM("delivery_confirm"),
        OAGE_BOX("oage_box"),
        OAGE_BOX_CONFIRM("oage_box_confirm");

        companion object {
            private val byKey = values().associateBy { it.key }

            fun fromKey(key: String): NpcMenuView? = byKey[key]
        }
    }

    private class NpcMenuHolder(
        ownerId: UUID,
        val view: NpcMenuView,
        val route: MenuRoute
    ) : OwnedMenuHolder(ownerId) {
        var oageBoxRewards: Map<Int, OageBoxReward> = emptyMap()
        var selectedOageBoxReward: OageBoxReward? = null
        var selectedDeadChest: DeadChestSnapshot? = null
        var selectedDeliveryItem: DeliveryItemMatch? = null
        var deadChestList: List<DeadChestSnapshot> = emptyList()
    }
}
