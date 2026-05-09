package jp.awabi2048.cccontent.features.npc.menu

import com.awabi2048.ccsystem.CCSystem
import jp.awabi2048.cccontent.features.arena.event.ArenaSessionEndedEvent
import jp.awabi2048.cccontent.features.rank.profession.Profession
import jp.awabi2048.cccontent.gui.GuiMenuItems
import jp.awabi2048.cccontent.gui.OwnedMenuHolder
import jp.awabi2048.cccontent.items.CustomItemManager
import jp.awabi2048.cccontent.items.misc.BoxedDaiginjoItem
import jp.awabi2048.cccontent.util.ContentLocaleResolver
import jp.awabi2048.cccontent.util.OageMessageSender
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
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.UUID
import kotlin.random.Random

class NpcMenuService(
    private val plugin: JavaPlugin,
    private val professionProvider: (UUID) -> Profession? = { null }
) : Listener {
    private companion object {
        const val CONFIG_PATH = "config/npc/menu.yml"
        const val MENU_ID = "oage_jinja_offering"
        const val TITLE = "§8おあげ神社 - 授与所"
        const val MENU_SIZE = 45
        const val SHOP_MENU_SIZE = 54
        const val TALK_MENU_SIZE = 54
        const val GARBAGE_BOX_SIZE = 54
        const val BACK_SLOT = 40
        const val SHOP_BACK_SLOT = 45
        const val TALK_BACK_SLOT = 49
        const val GARBAGE_BOX_BACK_SLOT = 49
        const val DELIVERY_SLOT = 20
        const val PART_TIME_SLOT = 24
        const val DAILY_HEADER_SLOT = 4
        const val GARBAGE_BOX_HEADER_SLOT = 4
        const val PART_TIME_TASK_ARENA = "arena_mission_clear"
        const val DELIVERY_WORLD_POINT_REWARD = 100

        val MAIN_SLOTS = mapOf(
            19 to NpcMenuView.DAILY,
            21 to NpcMenuView.SHOP,
            23 to NpcMenuView.TALK,
            25 to NpcMenuView.REQUEST
        )
        val TALK_TOPIC_SLOTS = setOf(20, 21, 22, 23, 24, 29, 30, 31, 32, 33)
        val REQUEST_ACTION_SLOTS = setOf(20, 24)
        val SHOP_TAB_SLOTS = listOf(47, 48)
        val SHOP_CONTENT_SLOTS = listOf(19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34)
        val GARBAGE_BOX_REWARD_SLOTS = listOf(20, 21, 22, 23, 24, 29, 30, 31, 32, 33)
    }

    private val configFile = File(plugin.dataFolder, CONFIG_PATH)
    private val stateFile = File(plugin.dataFolder, "data/npc/oage_jinja_offering.yml")
    private var configuredMenuIds: Set<String> = setOf(MENU_ID)
    private var rewardDefinitions: List<GarbageBoxRewardDefinition> = emptyList()
    private var state = YamlConfiguration()

    fun initialize() {
        ensureConfig()
        ensureState()
        reload()
    }

    fun reload() {
        ensureConfig()
        ensureState()
        val config = YamlConfiguration.loadConfiguration(configFile)
        configuredMenuIds = config.getConfigurationSection("menus")
            ?.getKeys(false)
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
            ?: setOf(MENU_ID)
        rewardDefinitions = loadRewardDefinitions(config)
        state = YamlConfiguration.loadConfiguration(stateFile)
    }

    fun getMenuIds(): Set<String> = configuredMenuIds

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

    fun open(menuId: String, player: Player): Boolean {
        if (menuId != MENU_ID || !configuredMenuIds.contains(menuId)) {
            player.sendMessage("ﾂｧcNPC繝｡繝九Η繝ｼ縺瑚ｦ九▽縺九ｊ縺ｾ縺帙ｓ: $menuId")
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

        val rawSlot = event.rawSlot
        if (rawSlot !in 0 until event.view.topInventory.size) return

        handleClick(player, holder, rawSlot)
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
            NpcMenuView.MAIN -> {
                val next = MAIN_SLOTS[slot] ?: return
                playClick(player)
                openView(player, next)
            }
            NpcMenuView.DAILY -> {
                when (slot) {
                    BACK_SLOT -> {
                        playClick(player)
                        openView(player, NpcMenuView.MAIN)
                    }
                    DELIVERY_SLOT -> handleDeliveryClick(player)
                    PART_TIME_SLOT -> handlePartTimeClick(player)
                }
            }
            NpcMenuView.SHOP -> {
                if (slot == SHOP_BACK_SLOT) {
                    playClick(player)
                    openView(player, NpcMenuView.MAIN)
                    return
                }
                val tabIndex = SHOP_TAB_SLOTS.indexOf(slot)
                if (tabIndex >= 0) {
                    playClick(player)
                    openShop(player, ShopTab.entries[tabIndex.coerceAtMost(ShopTab.entries.lastIndex)])
                } else if (slot in SHOP_CONTENT_SLOTS) {
                    notifyPreparing(player)
                }
            }
            NpcMenuView.TALK -> {
                if (slot == TALK_BACK_SLOT) {
                    playClick(player)
                    openView(player, NpcMenuView.MAIN)
                } else if (slot in TALK_TOPIC_SLOTS) {
                    notifyPreparing(player)
                }
            }
            NpcMenuView.REQUEST -> {
                if (slot == BACK_SLOT) {
                    playClick(player)
                    openView(player, NpcMenuView.MAIN)
                } else if (slot in REQUEST_ACTION_SLOTS) {
                    notifyPreparing(player)
                }
            }
            NpcMenuView.GARBAGE_BOX -> {
                if (slot == GARBAGE_BOX_BACK_SLOT) {
                    playClick(player)
                    openView(player, NpcMenuView.DAILY)
                } else if (slot in GARBAGE_BOX_REWARD_SLOTS) {
                    handleGarbageBoxRewardClick(player, holder, slot)
                }
            }
        }
    }

    private fun openView(player: Player, view: NpcMenuView) {
        if (view == NpcMenuView.SHOP) {
            openShop(player, ShopTab.OTHER)
            return
        }
        if (view == NpcMenuView.GARBAGE_BOX) {
            openGarbageBox(player)
            return
        }

        val holder = NpcMenuHolder(player.uniqueId, view)
        val inventory = Bukkit.createInventory(holder, menuSize(view), TITLE)
        holder.backingInventory = inventory
        render(player, holder, inventory)
        player.openInventory(inventory)
    }

    private fun openShop(player: Player, tab: ShopTab) {
        val holder = NpcMenuHolder(player.uniqueId, NpcMenuView.SHOP, tab)
        val inventory = Bukkit.createInventory(holder, SHOP_MENU_SIZE, TITLE)
        holder.backingInventory = inventory
        render(player, holder, inventory)
        player.openInventory(inventory)
    }

    private fun openGarbageBox(player: Player) {
        val holder = NpcMenuHolder(player.uniqueId, NpcMenuView.GARBAGE_BOX)
        val inventory = Bukkit.createInventory(holder, GARBAGE_BOX_SIZE, TITLE)
        holder.backingInventory = inventory
        render(player, holder, inventory)
        player.openInventory(inventory)
    }

    private fun render(player: Player, holder: NpcMenuHolder, inventory: Inventory) {
        GuiMenuItems.fillFramed(inventory)
        when (holder.view) {
            NpcMenuView.MAIN -> renderMain(player, inventory)
            NpcMenuView.DAILY -> renderDaily(player, inventory)
            NpcMenuView.SHOP -> renderShop(player, holder.shopTab, inventory)
            NpcMenuView.TALK -> renderTalk(player, inventory)
            NpcMenuView.REQUEST -> renderRequest(player, inventory)
            NpcMenuView.GARBAGE_BOX -> renderGarbageBox(player, holder, inventory)
        }
    }

    private fun renderMain(player: Player, inventory: Inventory) {
        inventory.setItem(19, icon(player, "main.daily", Material.CHEST))
        inventory.setItem(21, icon(player, "main.shop", Material.CRAFTING_TABLE))
        inventory.setItem(23, icon(player, "main.talk", Material.WRITABLE_BOOK))
        inventory.setItem(25, icon(player, "main.request", Material.CLOCK))
    }

    private fun renderDaily(player: Player, inventory: Inventory) {
        inventory.setItem(DAILY_HEADER_SLOT, icon(player, "daily.header", Material.WRITABLE_BOOK))
        inventory.setItem(DELIVERY_SLOT, deliveryIcon(player))
        inventory.setItem(PART_TIME_SLOT, partTimeIcon(player))
        inventory.setItem(BACK_SLOT, backButton(player))
    }

    private fun renderShop(player: Player, tab: ShopTab, inventory: Inventory) {
        inventory.setItem(4, icon(player, "shop.header", Material.CHEST))
        inventory.setItem(SHOP_BACK_SLOT, backButton(player))
        renderShopTabs(player, tab, inventory)

        val contentMaterial = when (tab) {
            ShopTab.OTHER -> Material.EMERALD
            ShopTab.HEAD -> Material.PLAYER_HEAD
        }
        SHOP_CONTENT_SLOTS.forEachIndexed { index, slot ->
            inventory.setItem(slot, icon(player, "shop.placeholder", contentMaterial, "index" to (index + 1)))
        }
    }

    private fun renderShopTabs(player: Player, activeTab: ShopTab, inventory: Inventory) {
        ShopTab.entries.forEachIndexed { index, tab ->
            val key = if (tab == activeTab) "shop.tab.${tab.key}.active" else "shop.tab.${tab.key}"
            inventory.setItem(SHOP_TAB_SLOTS[index], icon(player, key, tab.material))
        }
    }

    private fun renderTalk(player: Player, inventory: Inventory) {
        inventory.setItem(4, icon(player, "talk.header", Material.WRITABLE_BOOK))
        inventory.setItem(20, icon(player, "talk.topic.arena", Material.DIAMOND_SWORD))
        listOf(21, 22, 23, 24, 29, 30, 31, 32, 33).forEach {
            inventory.setItem(it, icon(player, "talk.topic.locked", Material.GRAY_DYE))
        }
        inventory.setItem(TALK_BACK_SLOT, backButton(player))
    }

    private fun renderRequest(player: Player, inventory: Inventory) {
        inventory.setItem(4, icon(player, "request.header", Material.CLOCK))
        inventory.setItem(20, icon(player, "request.restore", Material.ENDER_EYE))
        inventory.setItem(24, icon(player, "request.ritual", Material.NETHER_STAR))
        inventory.setItem(BACK_SLOT, backButton(player))
    }

    private fun renderGarbageBox(player: Player, holder: NpcMenuHolder, inventory: Inventory) {
        inventory.setItem(
            GARBAGE_BOX_HEADER_SLOT,
            GuiMenuItems.icon(Material.BRICKS, "ﾂｧ6縺翫≠縺偵■繧・ｓ縺ｮ縺後ｉ縺上◆邂ｱ", listOf("ﾂｧ7縺碑､堤ｾ弱ｒ縺ｲ縺ｨ縺､驕ｸ繧薙〒縺上□縺輔＞"))
        )
        inventory.setItem(GARBAGE_BOX_BACK_SLOT, backButton(player))
        val rewards = drawGarbageBoxRewards()
        holder.garbageBoxRewards = GARBAGE_BOX_REWARD_SLOTS.zip(rewards).toMap()
        GARBAGE_BOX_REWARD_SLOTS.forEach { slot ->
            inventory.setItem(slot, holder.garbageBoxRewards[slot]?.item ?: GuiMenuItems.icon(Material.GRAY_DYE, "ﾂｧ7遨ｺ"))
        }
    }

    private fun deliveryIcon(player: Player): ItemStack {
        val profession = professionProvider(player.uniqueId)
        if (profession != Profession.BREWER) {
            val lines = listOf(
                "§e奉納品",
                "§7職業に就くと、おあげ神社への奉納が行えるようになります",
                "§7お礼として、§6ワールドポイント§7と§6どんぐり§7に引き換えて貰えますよ！"
            )
            val sep = separator(lines)
            return GuiMenuItems.icon(
                material = Material.GRAY_DYE,
                name = "§e奉納品",
                lore = listOf(
                    sep,
                    "§7職業に就くと、おあげ神社への奉納が行えるようになります",
                    "§7お礼として、§6ワールドポイント§7と§6どんぐり§7に引き換えて貰えますよ！",
                    sep
                )
            )
        }

        val offering = boxedDaiginjoItem()
        val offeringName = legacyDisplayName(offering).ifBlank { "ﾂｧf邂ｱ蜈･繧雁､ｧ蜷滄・" }
        val offeringLore = legacyLore(offering)
        val statusLine = deliveryStatusLine(player.uniqueId)
        val sep = separator(
            listOf(
                "§e奉納品",
                offeringName,
                "§7おあげ神社に${offeringName}§7を奉納することで、",
                "§7お礼として§6ワールドポイント§7と§6どんぐり§7を貰えます",
                "§c※ 奉納は1週間に1回まで行えます",
                statusLine
            ) + offeringLore
        )
        val icon = GuiMenuItems.icon(
            material = Material.PLAYER_HEAD,
            name = "§e奉納品",
            lore = buildList {
                add(sep)
                addAll(offeringLore)
                add(sep)
                add("§7おあげ神社に${offeringName}§7を奉納することで、")
                add("§7お礼として§6ワールドポイント§7と§6どんぐり§7を貰えます")
                add("§c※ 奉納は1週間に1回まで行えます")
                add(sep)
                add(statusLine)
                add(sep)
            }
        )
        val skullMeta = icon.itemMeta as? org.bukkit.inventory.meta.SkullMeta
        val offeringMeta = offering.itemMeta as? org.bukkit.inventory.meta.SkullMeta
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
            completed && !opened -> "§eお仕事が全て終わったので、おあげちゃんがご褒美を用意してくれています (クリック)"
            opened -> "§7今日のご褒美は受け取り済みです"
            else -> null
        }
        val sep = separator(
            listOfNotNull(
                "§6おあげちゃんアルバイト",
                "§f❙ §a今日のお仕事",
                taskLine,
                "§7おあげちゃんからの依頼をこなして、",
                "§6ご褒美§7を貰いましょう！",
                extraLine
            )
        )
        return GuiMenuItems.icon(
            material = Material.BARREL,
            name = "§6おあげちゃんアルバイト",
            lore = buildList {
                add(sep)
                add("§f❙ §a今日のお仕事")
                add(taskLine)
                add(sep)
                add("§7おあげちゃんからの依頼をこなして、")
                add("§6ご褒美§7を貰いましょう！")
                add(sep)
                if (extraLine != null) add(extraLine)
            }
        )
    }

    private fun boxedDaiginjoItem(amount: Int = 1): ItemStack {
        return requireNotNull(CustomItemManager.createItem(BoxedDaiginjoItem.fullId, amount)) {
            "Custom item is not registered: ${BoxedDaiginjoItem.fullId}"
        }
    }

    private fun handleDeliveryClick(player: Player) {
        if (professionProvider(player.uniqueId) != Profession.BREWER || isDeliveryCompleted(player.uniqueId)) {
            return
        }

        val target = findDeliveryItem(player) ?: run {
            player.sendMessage("§cインベントリ内に箱入り大吟醸がありません。")
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
                return DeliveryItemMatch(slot, legacyDisplayName(item).ifBlank { "ﾂｧf邂ｱ蜈･繧雁､ｧ蜷滄・" })
            }
        }
        return null
    }

    private fun isDeliveryItem(item: ItemStack): Boolean {
        return CustomItemManager.identify(item)?.fullId == BoxedDaiginjoItem.fullId
    }

    private fun consumeOneDeliveryItem(player: Player, slot: Int) {
        val item = player.inventory.getItem(slot) ?: return
        if (item.amount <= 1) {
            player.inventory.setItem(slot, null)
        } else {
            item.amount -= 1
            player.inventory.setItem(slot, item)
        }
    }

    private fun grantWorldPoint(playerId: UUID, amount: Int): Int? {
        return runCatching {
            val myWorldManager = Bukkit.getPluginManager().getPlugin("MyWorldManager") ?: return null
            val apiClass = Class.forName(
                "me.awabi2048.myworldmanager.api.MyWorldManagerApi",
                true,
                myWorldManager.javaClass.classLoader
            )
            val available = apiClass.getMethod("isWorldPointServiceAvailable").invoke(null) as? Boolean ?: false
            if (!available) {
                null
            } else {
                apiClass.getMethod("addWorldPoint", UUID::class.java, Int::class.javaPrimitiveType)
                    .invoke(null, playerId, amount) as? Int
            }
        }.getOrNull()
    }

    private fun handlePartTimeClick(player: Player) {
        if (isPartTimeOpened(player.uniqueId) || !isPartTimeTaskCompleted(player.uniqueId, PART_TIME_TASK_ARENA)) {
            return
        }
        playClick(player)
        openGarbageBox(player)
    }

    private fun handleGarbageBoxRewardClick(player: Player, holder: NpcMenuHolder, slot: Int) {
        if (isPartTimeOpened(player.uniqueId)) {
            playError(player)
            player.closeInventory()
            player.sendMessage("§7今日のご褒美は受け取り済みです。")
            return
        }
        val reward = holder.garbageBoxRewards[slot] ?: return
        if (!canAcceptReward(player, reward.item)) {
            playError(player)
            player.sendMessage("§cインベントリに空きがありません。")
            return
        }
        val leftovers = player.inventory.addItem(reward.item.clone())
        if (leftovers.isNotEmpty()) {
            playError(player)
            player.sendMessage("§cインベントリに空きがありません。")
            return
        }
        markPartTimeOpened(player.uniqueId)
        playClick(player)
        player.sendMessage("§aおあげちゃんのがらくた箱からご褒美を受け取りました。")
        player.closeInventory()
    }

    private fun canAcceptReward(player: Player, item: ItemStack): Boolean {
        if (player.inventory.firstEmpty() >= 0) return true
        return player.inventory.storageContents.filterNotNull().any { existing ->
            existing.isSimilar(item) && existing.amount + item.amount <= existing.maxStackSize
        }
    }

    private fun icon(player: Player, key: String, material: Material, vararg placeholders: Pair<String, Any?>) =
        GuiMenuItems.icon(
            material = material,
            name = text(player, "$key.name", *placeholders),
            lore = list(player, "$key.lore", *placeholders)
        )

    private fun backButton(player: Player) =
        GuiMenuItems.backButton(text(player, "back.name"), list(player, "back.lore"))

    private fun menuSize(view: NpcMenuView): Int {
        return when (view) {
            NpcMenuView.SHOP -> SHOP_MENU_SIZE
            NpcMenuView.TALK -> TALK_MENU_SIZE
            NpcMenuView.GARBAGE_BOX -> GARBAGE_BOX_SIZE
            else -> MENU_SIZE
        }
    }

    private fun notifyPreparing(player: Player) {
        playClick(player)
        player.sendMessage(text(player, "messages.preparing"))
    }

    private fun playClick(player: Player) {
        player.playSound(player.location, "minecraft:ui.button.click", 0.7f, 1.6f)
    }

    private fun playError(player: Player) {
        player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.7f)
    }

    private fun text(player: Player, key: String, vararg placeholders: Pair<String, Any?>): String {
        val locale = ContentLocaleResolver.resolve(player)
        return CCSystem.getAPI()
            .getI18nString("CC-Content:rank", locale, "gui.npc.oage_jinja_offering.$key", placeholdersMap(*placeholders))
            .replace('&', '§')
    }

    private fun list(player: Player, key: String, vararg placeholders: Pair<String, Any?>): List<String> {
        val locale = ContentLocaleResolver.resolve(player)
        return CCSystem.getAPI()
            .getI18nStringList("CC-Content:rank", locale, "gui.npc.oage_jinja_offering.$key", placeholdersMap(*placeholders))
            .map { it.replace('&', '§') }
    }

    private fun placeholdersMap(vararg placeholders: Pair<String, Any?>): Map<String, Any> {
        return placeholders.associate { (key, value) -> key to (value ?: "null") }
    }

    private fun ensureConfig() {
        if (!configFile.exists()) {
            configFile.parentFile?.mkdirs()
            plugin.saveResource(CONFIG_PATH, false)
        }
        val config = YamlConfiguration.loadConfiguration(configFile)
        if (!config.isList("garbage_box.rewards")) {
            config.set(
                "garbage_box.rewards",
                listOf(
                    mapOf("material" to "BREAD", "amount" to 3, "weight" to 30, "name" to "§fおさがりのパン", "lore" to listOf("§7おあげちゃんのがらくた箱から出てきたもの")),
                    mapOf("material" to "GOLD_NUGGET", "amount" to 8, "weight" to 20, "name" to "§6小さなどんぐり袋", "lore" to listOf("§7少しだけ得をした気分になります")),
                    mapOf("material" to "EXPERIENCE_BOTTLE", "amount" to 4, "weight" to 15, "name" to "§a古い経験瓶", "lore" to listOf("§7神社の倉庫に眠っていました")),
                    mapOf("material" to "AMETHYST_SHARD", "amount" to 2, "weight" to 10, "name" to "§dきらきらした欠片", "lore" to listOf("§7用途はあとで考えましょう")),
                    mapOf("material" to "COOKIE", "amount" to 6, "weight" to 25, "name" to "§eおやつ", "lore" to listOf("§7休憩用です"))
                )
            )
            config.save(configFile)
        }
    }

    private fun ensureState() {
        if (!stateFile.exists()) {
            stateFile.parentFile?.mkdirs()
            stateFile.createNewFile()
        }
    }

    private fun saveState() {
        stateFile.parentFile?.mkdirs()
        state.save(stateFile)
    }

    private fun loadRewardDefinitions(config: YamlConfiguration): List<GarbageBoxRewardDefinition> {
        return config.getMapList("garbage_box.rewards").mapNotNull { raw ->
            val materialName = raw["material"]?.toString()?.uppercase() ?: return@mapNotNull null
            val material = Material.matchMaterial(materialName) ?: return@mapNotNull null
            val amount = (raw["amount"] as? Number)?.toInt() ?: raw["amount"]?.toString()?.toIntOrNull() ?: 1
            val weight = (raw["weight"] as? Number)?.toInt() ?: raw["weight"]?.toString()?.toIntOrNull() ?: 1
            val name = raw["name"]?.toString()?.replace('&', '§')
            val lore = (raw["lore"] as? List<*>)?.map { it.toString().replace('&', '§') }.orEmpty()
            GarbageBoxRewardDefinition(material, amount.coerceIn(1, material.maxStackSize), weight.coerceAtLeast(1), name, lore)
        }.ifEmpty {
            listOf(GarbageBoxRewardDefinition(Material.BREAD, 1, 1, "§fおさがりのパン", listOf("§7おあげちゃんのがらくた箱から出てきたもの")))
        }
    }

    private fun drawGarbageBoxRewards(): List<GarbageBoxReward> {
        return List(GARBAGE_BOX_REWARD_SLOTS.size) {
            GarbageBoxReward(weightedRewardDefinition().toItem())
        }
    }

    private fun weightedRewardDefinition(): GarbageBoxRewardDefinition {
        val total = rewardDefinitions.sumOf { it.weight }.coerceAtLeast(1)
        var cursor = Random.nextInt(total)
        for (definition in rewardDefinitions) {
            cursor -= definition.weight
            if (cursor < 0) return definition
        }
        return rewardDefinitions.first()
    }

    private fun GarbageBoxRewardDefinition.toItem(): ItemStack {
        val item = ItemStack(material, amount)
        val meta = item.itemMeta ?: return item
        if (!name.isNullOrBlank()) {
            meta.displayName(legacy(name))
        }
        if (lore.isNotEmpty()) {
            meta.lore(lore.map { legacy(it) })
        }
        item.itemMeta = meta
        return item
    }

    private fun markPartTimeCompleted(playerId: UUID, taskId: String) {
        state.set("part_time.completed_tasks.$playerId.$taskId", true)
        saveState()
    }

    private fun isPartTimeTaskCompleted(playerId: UUID, taskId: String): Boolean {
        return state.getBoolean("part_time.completed_tasks.$playerId.$taskId", false)
    }

    private fun markPartTimeOpened(playerId: UUID) {
        state.set("part_time.opened.$playerId", true)
        saveState()
    }

    private fun isPartTimeOpened(playerId: UUID): Boolean {
        return state.getBoolean("part_time.opened.$playerId", false)
    }

    private fun isDeliveryCompleted(playerId: UUID): Boolean {
        return state.getBoolean("delivery.completed.$playerId", false)
    }

    private fun markDeliveryCompleted(playerId: UUID) {
        state.set("delivery.completed.$playerId", true)
        saveState()
    }

    private fun deliveryStatusLine(playerId: UUID): String {
        return if (isDeliveryCompleted(playerId)) {
            "§b今週は奉納済みです"
        } else {
            "§e今週はまだ奉納できます"
        }
    }

    private fun separator(lines: Collection<String>): String {
        val maxLength = lines.maxOfOrNull { stripColor(it).length } ?: 0
        return "§8" + "―".repeat(maxLength.coerceAtMost(10).coerceAtLeast(1))
    }

    private fun stripColor(text: String): String {
        return text.replace(Regex("[§&][0-9A-FK-ORa-fk-or]"), "")
    }


    private fun legacyDisplayName(item: ItemStack): String {
        return item.itemMeta?.displayName()?.let { LegacyComponentSerializer.legacySection().serialize(it) }.orEmpty()
    }

    private fun legacyLore(item: ItemStack): List<String> {
        return item.itemMeta?.lore()?.map { LegacyComponentSerializer.legacySection().serialize(it) }.orEmpty()
    }

    private fun legacy(text: String): Component {
        return LegacyComponentSerializer.legacySection().deserialize(text).decoration(TextDecoration.ITALIC, false)
    }
}

private class NpcMenuHolder(
    ownerId: UUID,
    val view: NpcMenuView,
    val shopTab: ShopTab = ShopTab.OTHER
) : OwnedMenuHolder(ownerId) {
    var garbageBoxRewards: Map<Int, GarbageBoxReward> = emptyMap()
}

private enum class NpcMenuView(val key: String) {
    MAIN("main"),
    DAILY("daily"),
    SHOP("shop"),
    TALK("talk"),
    REQUEST("request"),
    GARBAGE_BOX("garbage_box")
}

private enum class ShopTab(val key: String, val material: Material) {
    OTHER("other", Material.EMERALD),
    HEAD("head", Material.PLAYER_HEAD)
}

private data class GarbageBoxRewardDefinition(
    val material: Material,
    val amount: Int,
    val weight: Int,
    val name: String?,
    val lore: List<String>
)

private data class GarbageBoxReward(
    val item: ItemStack
)

private data class DeliveryItemMatch(
    val slot: Int,
    val displayName: String
)
