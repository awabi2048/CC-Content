@file:Suppress("DEPRECATION")

package jp.awabi2048.cccontent.features.npc.menu

import com.awabi2048.ccsystem.CCSystem
import jp.awabi2048.cccontent.economy.ContentEconomyBridge
import jp.awabi2048.cccontent.features.arena.event.ArenaSessionEndedEvent
import jp.awabi2048.cccontent.features.npc.request.DeadChestRecoveryService
import jp.awabi2048.cccontent.features.npc.request.DeadChestSnapshot
import jp.awabi2048.cccontent.features.npc.shop.OageShrineShopMenuService
import jp.awabi2048.cccontent.features.npc.shop.OageShrineShopState
import jp.awabi2048.cccontent.features.rank.profession.Profession
import jp.awabi2048.cccontent.gui.GuiMenuItems
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
        const val MENU_ID = "oage_shrine"
        const val MENU_SIZE = 45
        const val TALK_MENU_SIZE = 54
        const val OAGE_BOX_SIZE = 54
        const val BACK_SLOT = 40
        const val TALK_BACK_SLOT = 49
        const val OAGE_BOX_BACK_SLOT = 49
        const val DELIVERY_SLOT = 20
        const val PART_TIME_SLOT = 24
        const val DAILY_HEADER_SLOT = 4
        const val OAGE_BOX_HEADER_SLOT = 4
        const val OAGE_BOX_CONFIRM_PREVIEW_SLOT = 22
        const val OAGE_BOX_CONFIRM_SLOT = 20
        const val OAGE_BOX_CANCEL_SLOT = 24
        const val DEAD_CHEST_RECOVERY_COST = 300.0
        const val DEAD_CHEST_CONFIRM_PREVIEW_SLOT = 22
        const val DEAD_CHEST_CONFIRM_SLOT = 20
        const val DEAD_CHEST_CANCEL_SLOT = 24
        const val DELIVERY_CONFIRM_PREVIEW_SLOT = 22
        const val DELIVERY_CONFIRM_SLOT = 20
        const val DELIVERY_CANCEL_SLOT = 24
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

    fun initialize() {
        ensureConfig()
        ensureState()
        shopMenuService = OageShrineShopMenuService(plugin, shopState) { player ->
            openView(player, NpcMenuView.MAIN)
        }
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

        openView(player, NpcMenuView.MAIN)
        return true
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val holder = event.view.topInventory.holder as? NpcMenuHolder ?: return
        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return
        if (player.uniqueId != holder.ownerId) return
        if (event.rawSlot !in 0 until event.view.topInventory.size) return

        handleClick(player, holder, event.rawSlot)
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        val holder = event.view.topInventory.holder as? NpcMenuHolder ?: return
        if (event.rawSlots.any { it in 0 until event.view.topInventory.size } || holder.ownerId != (event.whoClicked as? Player)?.uniqueId) {
            event.isCancelled = true
        }
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
                19 -> { playClick(player); openView(player, NpcMenuView.DAILY) }
                21 -> { playClick(player); openShopMenu(player) }
                25 -> { playClick(player); openView(player, NpcMenuView.REQUEST) }
            }
            NpcMenuView.DAILY -> when (slot) {
                BACK_SLOT -> { playClick(player); openView(player, NpcMenuView.MAIN) }
                DELIVERY_SLOT -> handleDeliveryClick(player)
                PART_TIME_SLOT -> handlePartTimeClick(player)
            }
            NpcMenuView.TALK -> if (slot == TALK_BACK_SLOT) {
                playClick(player)
                openView(player, NpcMenuView.MAIN)
            }
            NpcMenuView.REQUEST -> if (slot == BACK_SLOT) {
                playClick(player)
                openView(player, NpcMenuView.MAIN)
            } else if (slot == 20) {
                handleDeadChestRecoveryRequest(player)
            }
            NpcMenuView.DEAD_CHEST_CONFIRM -> when (slot) {
                DEAD_CHEST_CONFIRM_SLOT -> holder.selectedDeadChest?.let { confirmDeadChestRecovery(player, it) }
                DEAD_CHEST_CANCEL_SLOT -> {
                    playClick(player)
                    openView(player, NpcMenuView.REQUEST)
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

    private fun openView(player: Player, view: NpcMenuView) {
        if (view == NpcMenuView.OAGE_BOX) {
            openOageBox(player)
            return
        }

        val holder = NpcMenuHolder(player.uniqueId, view)
        val inventory = Bukkit.createInventory(holder, menuSize(view), title(player, view))
        holder.backingInventory = inventory
        render(player, holder, inventory)
        player.openInventory(inventory)
    }

    private fun openShopMenu(player: Player) {
        if (!::shopMenuService.isInitialized) return
        shopMenuService.open(player)
    }

    private fun openOageBox(player: Player) {
        val holder = NpcMenuHolder(player.uniqueId, NpcMenuView.OAGE_BOX)
        val inventory = Bukkit.createInventory(holder, OAGE_BOX_SIZE, title(player, NpcMenuView.OAGE_BOX))
        holder.backingInventory = inventory
        render(player, holder, inventory)
        player.openInventory(inventory)
    }

    private fun openOageBoxConfirm(player: Player, reward: OageBoxReward) {
        val holder = NpcMenuHolder(player.uniqueId, NpcMenuView.OAGE_BOX_CONFIRM)
        holder.selectedOageBoxReward = reward
        val inventory = Bukkit.createInventory(holder, MENU_SIZE, "§8おあげBOX - 確認")
        holder.backingInventory = inventory
        render(player, holder, inventory)
        player.openInventory(inventory)
    }

    private fun render(player: Player, holder: NpcMenuHolder, inventory: Inventory) {
        GuiMenuItems.fillFramed(inventory)
        when (holder.view) {
            NpcMenuView.MAIN -> renderMain(player, inventory)
            NpcMenuView.DAILY -> renderDaily(player, inventory)
            NpcMenuView.TALK -> renderTalk(player, inventory)
            NpcMenuView.REQUEST -> renderRequest(player, inventory)
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

    private fun openDeadChestConfirm(player: Player, snapshot: DeadChestSnapshot) {
        val holder = NpcMenuHolder(player.uniqueId, NpcMenuView.DEAD_CHEST_CONFIRM)
        holder.selectedDeadChest = snapshot
        val inventory = Bukkit.createInventory(holder, MENU_SIZE, "§8失具還術 - 確認")
        holder.backingInventory = inventory
        render(player, holder, inventory)
        player.openInventory(inventory)
    }

    private fun openDeliveryConfirm(player: Player, item: DeliveryItemMatch) {
        val holder = NpcMenuHolder(player.uniqueId, NpcMenuView.DELIVERY_CONFIRM)
        holder.selectedDeliveryItem = item
        val inventory = Bukkit.createInventory(holder, MENU_SIZE, "§8奉納品 - 確認")
        holder.backingInventory = inventory
        render(player, holder, inventory)
        player.openInventory(inventory)
    }

    private fun renderDeadChestConfirm(player: Player, holder: NpcMenuHolder, inventory: Inventory) {
        val snapshot = holder.selectedDeadChest ?: run {
            inventory.setItem(22, GuiMenuItems.icon(Material.BARRIER, "§c対象が見つかりません"))
            inventory.setItem(DEAD_CHEST_CANCEL_SLOT, GuiMenuItems.icon(Material.RED_CONCRETE, "§c戻る", listOf("§7ご奉仕に戻ります。")))
            return
        }
        inventory.setItem(DEAD_CHEST_CONFIRM_PREVIEW_SLOT, GuiMenuItems.hideTooltip(ItemStack(Material.RECOVERY_COMPASS)))
        inventory.setItem(DEAD_CHEST_CONFIRM_SLOT, GuiMenuItems.icon(
            Material.LIME_CONCRETE,
            "§a回収する",
            listOf(
                "§7最新のデスチェストを回収します。",
                "§f❙ §7初穂料 ${formatAcorn(DEAD_CHEST_RECOVERY_COST)}"
            )
        ))
        inventory.setItem(DEAD_CHEST_CANCEL_SLOT, GuiMenuItems.icon(Material.RED_CONCRETE, "§cキャンセル", listOf("§7ご奉仕に戻ります。")))
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
        val current = deadChestRecoveryService.getLatestChest(player)?.takeIf { sameDeadChest(it, snapshot) } ?: run {
            playError(player)
            player.closeInventory()
            player.sendMessage("§c回収対象の最新デスチェストが変わっています。")
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
        val current = deadChestRecoveryService.getLatestChest(player)?.takeIf { sameDeadChest(it, snapshot) } ?: run {
            playError(player)
            player.sendMessage("§c回収対象の最新デスチェストが変わっています。")
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
        val leftLocation = deadChestRecoveryService.chestLocation(left.chest)
        val rightLocation = deadChestRecoveryService.chestLocation(right.chest)
        return leftLocation.world?.name == rightLocation.world?.name &&
            leftLocation.blockX == rightLocation.blockX &&
            leftLocation.blockY == rightLocation.blockY &&
            leftLocation.blockZ == rightLocation.blockZ &&
            deadChestRecoveryService.chestOwnerId(left.chest) == deadChestRecoveryService.chestOwnerId(right.chest)
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

    private fun handleDeadChestRecoveryRequest(player: Player) {
        val check = evaluateDeadChestRecovery(player)
        if (check.snapshot == null || !check.executable) return
        playClick(player)
        openDeadChestConfirm(player, check.snapshot)
    }

    private fun deadChestRecoveryIcon(player: Player): ItemStack {
        val check = evaluateDeadChestRecovery(player)
        val baseLore = listOf(
            "§7デスチェストからアイテムを取り戻すことができます",
            "§7取り戻したアイテムは、直接インベントリに入ります",
            "§7おあげちゃんが疲れてしまうので、連続で行うことはできません",
            "§c【注意】最新のデスチェストのみが回収対象になります！",
            "§cデスチェストが複数ある場合、それ以外のものは取り戻せません"
        )
        val body = buildList {
            addAll(baseLore.take(3))
            add("")
            add(baseLore[3])
            add(baseLore[4])
        }
        val costOrStatusLine = when {
            check.executable -> "§f❙ §7初穂料 ${formatAcorn(DEAD_CHEST_RECOVERY_COST)}"
            check.snapshot == null -> "§7デスチェストが見つかりませんでした"
            else -> check.failureMessage ?: "§c失具還術を行えません"
        }
        val sep = separator(body + costOrStatusLine)
        val lore = buildList {
            add(sep)
            addAll(body)
            add(sep)
            add(costOrStatusLine)
            add(sep)
        }
        return GuiMenuItems.icon(Material.RECOVERY_COMPASS, "§a失具還術", lore).apply {
            itemMeta = itemMeta?.also { meta ->
                meta.setEnchantmentGlintOverride(check.executable)
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
            }
        }
    }

    private fun evaluateDeadChestRecovery(player: Player): DeadChestRecoveryCheck {
        if (!deadChestRecoveryService.isAvailable()) {
            return DeadChestRecoveryCheck(null, null, false, "§cDeadChestが利用できないため、失具還術を行えません。")
        }
        val snapshot = deadChestRecoveryService.getLatestChest(player)
            ?: return DeadChestRecoveryCheck(null, null, false, "§7現在アクティブなデスチェストはありません。")
        if (ContentEconomyBridge.get(plugin) == null) {
            return DeadChestRecoveryCheck(snapshot, null, false, "§c経済機能が利用できません。")
        }
        val balance = ContentEconomyBridge.balance(plugin, player) ?: 0.0
        if (balance < DEAD_CHEST_RECOVERY_COST || !ContentEconomyBridge.has(plugin, player, DEAD_CHEST_RECOVERY_COST)) {
            return DeadChestRecoveryCheck(snapshot, balance, false, insufficientAcornLine(DEAD_CHEST_RECOVERY_COST, balance))
        }
        if (!deadChestRecoveryService.canRecoverToInventory(player, snapshot.chest)) {
            return DeadChestRecoveryCheck(snapshot, balance, false, "§cインベントリに空きがありません。")
        }
        return DeadChestRecoveryCheck(snapshot, balance, true, null)
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
            inventory.setItem(OAGE_BOX_CANCEL_SLOT, GuiMenuItems.icon(Material.RED_CONCRETE, "§c戻る", listOf("§7おあげBOXに戻ります。")))
            return
        }
        inventory.setItem(OAGE_BOX_CONFIRM_PREVIEW_SLOT, reward.icon(player))
        inventory.setItem(OAGE_BOX_CONFIRM_SLOT, GuiMenuItems.icon(Material.LIME_CONCRETE, "§a受け取る", listOf("§7このご褒美を受け取ります。")))
        inventory.setItem(OAGE_BOX_CANCEL_SLOT, GuiMenuItems.icon(Material.RED_CONCRETE, "§cキャンセル", listOf("§7おあげBOXに戻ります。")))
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
                    "§7${selected?.displayName ?: "奉納品"}§7を奉納します。",
                    "§7報酬として§e$DELIVERY_WORLD_POINT_REWARD ワールドポイント§7を受け取ります。"
                )
            )
        )
        inventory.setItem(DELIVERY_CANCEL_SLOT, GuiMenuItems.icon(Material.RED_CONCRETE, "§cキャンセル", listOf("§7日課メニューに戻ります。")))
    }

    private fun deliveryIcon(player: Player): ItemStack {
        val profession = professionProvider(player.uniqueId)
        if (profession != Profession.BREWER) {
            return GuiMenuItems.icon(Material.GRAY_DYE, "§e奉納品", listOf("§7職業に就くと、おあげ神社への奉納が行えるようになります"))
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
        val sep = separator(body)
        val icon = GuiMenuItems.icon(
            Material.PLAYER_HEAD,
            "§e奉納品",
            buildList {
                add(sep)
                addAll(offeringLore)
                add(sep)
                add(body[offeringLore.size])
                add(body[offeringLore.size + 1])
                add(body[offeringLore.size + 2])
                add(sep)
                add(statusLine)
                add(sep)
            }
        )
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
        val taskLine = if (completed) "§7・ §aアリーナミッションを1回完了する" else "§7・ §fアリーナミッションを1回完了する"
        val extraLine = when {
            completed && !opened -> "§a今日のタスクがすべて完了しました！\n§e§nクリックで報酬を受け取る"
            else -> null
        }
        val body = buildList {
            add("§f❙ §a今日のお仕事")
            add(taskLine)
            add("§7おあげちゃんからの依頼をこなして、")
            add("§6ご褒美§7を貰いましょう！")
            if (opened) add("§c今日の分は既に受け取っています")
            if (extraLine != null) addAll(extraLine.lines())
        }
        val sep = separator(body)
        return GuiMenuItems.icon(Material.BARREL, "§6おあげちゃんアルバイト", buildList {
            add(sep)
            add(body[0])
            add(body[1])
            add(sep)
            add(body[2])
            add(body[3])
            if (opened) add("§c今日の分は既に受け取っています")
            add(sep)
            if (extraLine != null) addAll(extraLine.lines())
        })
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

    private fun icon(player: Player, key: String, material: Material): ItemStack = GuiMenuItems.icon(material, text(player, "$key.name"), list(player, "$key.lore"))

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

    private fun backButton(player: Player): ItemStack = GuiMenuItems.backButton(text(player, "back.name"), list(player, "back.lore"))

    private fun menuSize(view: NpcMenuView): Int = when (view) {
        NpcMenuView.TALK -> TALK_MENU_SIZE
        NpcMenuView.OAGE_BOX -> OAGE_BOX_SIZE
        else -> MENU_SIZE
    }

    private fun title(player: Player, view: NpcMenuView): String = text(player, "title.${view.key}")

    private fun text(player: Player, key: String): String {
        val locale = ContentLocaleResolver.resolve(player)
        return jp.awabi2048.cccontent.CCContent.languageManager.let { _ ->
            com.awabi2048.ccsystem.CCSystem.getAPI().getI18nString("CC-Content:rank", locale, "gui.npc.oage_shrine.$key", emptyMap()).replace('&', '§')
        }
    }

    private fun list(player: Player, key: String): List<String> {
        val locale = ContentLocaleResolver.resolve(player)
        return com.awabi2048.ccsystem.CCSystem.getAPI().getI18nStringList("CC-Content:rank", locale, "gui.npc.oage_shrine.$key", emptyMap()).map { it.replace('&', '§') }
    }

    private fun playClick(player: Player) {
        player.playSound(player.location, "minecraft:ui.button.click", 0.7f, 1.6f)
    }

    private fun playError(player: Player) {
        player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.7f)
    }

    private fun separator(lines: Collection<String>): String {
        return GuiMenuItems.separator(lines)
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
            ?: GuiMenuItems.icon(Material.BARRIER, "§c未登録アイテム", listOf("§7$itemId"))
        is OageBoxReward.Acorn -> GuiMenuItems.icon(
            Material.PLAYER_HEAD,
            "§6収穫したてのどんぐり",
            listOf("§7${acornDisplay(amount)}")
        ).apply { applyAcornTexture() }
        is OageBoxReward.WorldPoint -> GuiMenuItems.icon(
            Material.HONEY_BOTTLE,
            "§6世界樹の樹液",
            listOf("§7${worldPointDisplay(amount)}")
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
        DELIVERY_CONFIRM("delivery_confirm"),
        OAGE_BOX("oage_box"),
        OAGE_BOX_CONFIRM("oage_box_confirm")
    }

    private class NpcMenuHolder(
        ownerId: UUID,
        val view: NpcMenuView
    ) : OwnedMenuHolder(ownerId) {
        var oageBoxRewards: Map<Int, OageBoxReward> = emptyMap()
        var selectedOageBoxReward: OageBoxReward? = null
        var selectedDeadChest: DeadChestSnapshot? = null
        var selectedDeliveryItem: DeliveryItemMatch? = null
    }
}
