package jp.awabi2048.cccontent.features.cooking

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiLoreBlock
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.GuiMenuIconAction
import com.awabi2048.ccsystem.api.gui.GuiMenuIconData
import com.awabi2048.ccsystem.api.gui.GuiMenuIconSpec
import com.awabi2048.ccsystem.api.gui.GuiNameSpec
import jp.awabi2048.cccontent.features.rank.RankManager
import jp.awabi2048.cccontent.features.catalog.CatalogItem
import jp.awabi2048.cccontent.features.catalog.CatalogStore
import jp.awabi2048.cccontent.features.catalog.CatalogType
import jp.awabi2048.cccontent.gui.GuiMenuItems
import jp.awabi2048.cccontent.util.FeatureInitializationLogger
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockBurnEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.block.BlockPistonExtendEvent
import org.bukkit.event.block.BlockPistonRetractEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

/** 料理の設定、GUI、調理セッション、図鑑を一つの機能境界で管理する。 */
class CookingFeature(
    private val plugin: JavaPlugin,
    private val rankManagerProvider: () -> RankManager?,
    private val catalogStore: CatalogStore
) {
    private var controller: CookingController? = null

    fun initialize(featureInitLogger: FeatureInitializationLogger? = null) {
        try {
            ensureResources()
            val config = YamlConfiguration.loadConfiguration(File(plugin.dataFolder, "config/cooking/config.yml"))
            require(config.get("enabled") is Boolean) { "config/cooking/config.yml.enabled must be a boolean" }
            if (!config.getBoolean("enabled")) {
                featureInitLogger?.setStatus("Cooking", FeatureInitializationLogger.Status.SUCCESS)
                return
            }
            controller = CookingController(plugin, rankManagerProvider, catalogStore).also { it.initialize() }
            featureInitLogger?.setStatus("Cooking", FeatureInitializationLogger.Status.SUCCESS)
        } catch (e: Exception) {
            featureInitLogger?.apply {
                setStatus("Cooking", FeatureInitializationLogger.Status.FAILURE)
                addDetailMessage("Cooking", "[Cooking] 初期化失敗: ${e.message}")
            }
            plugin.logger.warning("Cooking初期化失敗: ${e.message}")
        }
    }

    fun reload() {
        ensureResources()
        val config = YamlConfiguration.loadConfiguration(File(plugin.dataFolder, "config/cooking/config.yml"))
        require(config.get("enabled") is Boolean) { "config/cooking/config.yml.enabled must be a boolean" }
        if (!config.getBoolean("enabled")) {
            controller?.shutdown()
            controller = null
            return
        }
        controller?.reload() ?: run { controller = CookingController(plugin, rankManagerProvider, catalogStore).also { it.initialize() } }
    }

    fun shutdown() { controller?.shutdown() }

    fun catalogItems(): List<CatalogItem> = controller?.catalogItems().orEmpty()

    private fun ensureResources() {
        ensureFile("config/cooking/config.yml")
        ensureFile("config/cooking/recipe.yml")
        ensureFile("config/ingredient_definition.yml")
    }

    private fun ensureFile(path: String) {
        val file = File(plugin.dataFolder, path)
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            plugin.saveResource(path, false)
        }
    }
}

private data class CookingIngredient(val id: String, val material: Material, val pdcId: String?)
private enum class CookingEquipment {
    PAN,
    CAULDRON
}

private data class CookingRecipe(
    val id: String,
    val equipment: CookingEquipment,
    val ingredients: Map<String, Int>,
    val seasonings: Map<String, Int>,
    val resultMaterial: Material,
    val resultModel: NamespacedKey,
    val exp: Long,
    val completionTicks: Long,
    val weight: Double
)
private data class CookingMatch(val recipe: CookingRecipe, val score: Double)

internal fun cookingStartClickAllowed(click: ClickType): Boolean = click == ClickType.LEFT || click == ClickType.RIGHT

internal fun cookingInputRemainders(inputAmounts: Map<String, Int>, required: Map<String, Int>): Map<String, Int>? {
    val remaining = inputAmounts.toMutableMap()
    required.forEach { (id, amount) ->
        val available = remaining[id] ?: return null
        if (available < amount) return null
        remaining[id] = available - amount
    }
    return remaining.filterValues { it > 0 }
}

private class CookingController(
    private val plugin: JavaPlugin,
    private val rankManagerProvider: () -> RankManager?,
    private val catalogStore: CatalogStore
) : Listener {
    private val configFile get() = File(plugin.dataFolder, "config/cooking/config.yml")
    private val recipeFile get() = File(plugin.dataFolder, "config/cooking/recipe.yml")
    private val stateFile get() = File(plugin.dataFolder, "data/cooking/state.yml")
    private val itemIdKey = NamespacedKey("cccontent", "cooking_item_id")
    private val recipeKey = NamespacedKey("cccontent", "cooking_recipe_id")
    private val completionKey = NamespacedKey("cccontent", "cooking_completion")
    private val customItemIdKey = NamespacedKey("cccontent", "custom_item_id")
    private val recipes = mutableListOf<CookingRecipe>()
    private val ingredients = mutableMapOf<String, CookingIngredient>()
    private var minimumSimilarity = 0.5
    private var ingredientSlotsByLevel: Map<Int, Int> = emptyMap()
    private val pending = mutableMapOf<CookingStationKey, List<ItemStack>>()
    private val active = mutableMapOf<CookingStationKey, ActiveCooking>()
    private val stationLocks = mutableMapOf<CookingStationKey, UUID>()
    private var progressTask: BukkitTask? = null
    private var tickCounter = 0L
    private var state = YamlConfiguration()

    private data class ActiveCooking(
        val recipe: CookingRecipe,
        var remainingTicks: Long,
        val score: Int,
        val seasoningIds: List<String>,
        val starterId: UUID,
        var settled: Boolean = false
    )

    fun initialize() {
        stateFile.parentFile.mkdirs()
        loadDefinitions()
        loadState()
        Bukkit.getPluginManager().registerEvents(this, plugin)
        startProgressTask()
    }

    fun reload() {
        loadDefinitions()
        loadState()
        startProgressTask()
    }

    fun shutdown() {
        Bukkit.getOnlinePlayers().forEach { player ->
            val holder = player.openInventory.topInventory.holder as? CookingHolder ?: return@forEach
            returnInputs(player, player.openInventory.topInventory, holder)
            player.closeInventory()
        }
        progressTask?.cancel()
        progressTask = null
        stationLocks.clear()
        saveState()
    }

    private fun loadDefinitions() {
        val settings = YamlConfiguration.loadConfiguration(configFile).getConfigurationSection("settings")
            ?: error("config/cooking/config.yml.settings is required")
        require(settings.get("minimum_similarity") is Number) { "config/cooking/config.yml.settings.minimum_similarity is required" }
        minimumSimilarity = settings.getDouble("minimum_similarity").also { require(it in 0.0..1.0) }
        ingredientSlotsByLevel = loadIngredientSlots(settings)
        val recipeConfig = YamlConfiguration.loadConfiguration(recipeFile)
        ingredients.clear()
        recipeConfig.getConfigurationSection("ingredients")?.getKeys(false)?.forEach { id ->
            val section = recipeConfig.getConfigurationSection("ingredients.$id") ?: return@forEach
            val material = Material.matchMaterial(section.getString("material", "AIR") ?: "AIR")
                ?: error("cooking ingredient $id has invalid material")
            ingredients[id] = CookingIngredient(id, material, section.getString("pdc_id"))
        }
        recipes.clear()
        recipeConfig.getConfigurationSection("recipes")?.getKeys(false)?.forEach { id ->
            val section = recipeConfig.getConfigurationSection("recipes.$id") ?: return@forEach
            val ingredientMap = section.getConfigurationSection("ingredients")?.getKeys(false)
                ?.associateWith { positiveInt(section.get("ingredients.$it"), "cooking recipe $id ingredient $it") } ?: emptyMap()
            val seasoningMap = section.getConfigurationSection("seasonings")?.getKeys(false)
                ?.associateWith { positiveInt(section.get("seasonings.$it"), "cooking recipe $id seasoning $it") } ?: emptyMap()
            val material = Material.matchMaterial(requireNotNull(section.getString("result.material")) { "cooking recipe $id result.material is required" })
                ?: error("cooking recipe $id has invalid result material")
            val model = NamespacedKey.fromString(requireNotNull(section.getString("result.item_model")) { "cooking recipe $id result.item_model is required" })
                ?: error("cooking recipe $id has invalid item_model")
            val equipment = section.getString("equipment")
                ?.uppercase()
                ?.let { runCatching { CookingEquipment.valueOf(it) }.getOrNull() }
                ?: error("cooking recipe $id has invalid equipment")
            require(ingredientMap.isNotEmpty()) { "cooking recipe $id has no ingredients" }
            recipes += CookingRecipe(
                id, equipment, ingredientMap, seasoningMap, material, model,
                positiveLong(section.get("exp"), "cooking recipe $id exp"),
                positiveLong(section.get("completion_seconds"), "cooking recipe $id completion_seconds") * 20L,
                section.getDouble("weight", 1.0).also { require(it >= 0.0) { "cooking recipe $id weight must be non-negative" } }
            )
        }
        require(recipes.isNotEmpty()) { "cooking recipes are empty" }
    }

    private fun loadIngredientSlots(settings: ConfigurationSection): Map<Int, Int> {
        val section = settings.getConfigurationSection("ingredient_slots_by_level")
            ?: error("config/cooking/config.yml.settings.ingredient_slots_by_level is required")
        require(section.getKeys(false).isNotEmpty()) {
            "config/cooking/config.yml.settings.ingredient_slots_by_level must not be empty"
        }
        return section.getKeys(false).associate { rawLevel ->
            val level = rawLevel.toIntOrNull()
                ?: error("config/cooking/config.yml.settings.ingredient_slots_by_level key must be an integer: $rawLevel")
            require(level >= 1) {
                "config/cooking/config.yml.settings.ingredient_slots_by_level key must be positive: $rawLevel"
            }
            val slots = section.get("$rawLevel")
            require(slots is Number && slots.toDouble() == slots.toInt().toDouble() && slots.toInt() in 1..5) {
                "config/cooking/config.yml.settings.ingredient_slots_by_level.$rawLevel must be an integer from 1 to 5"
            }
            level to slots.toInt()
        }.toSortedMap()
    }

    private fun positiveInt(value: Any?, path: String): Int = require(value is Number && value.toDouble() == value.toInt().toDouble() && value.toInt() > 0) { "$path must be a positive integer" }.let { value.toInt() }
    private fun positiveLong(value: Any?, path: String): Long = require(value is Number && value.toDouble() == value.toLong().toDouble() && value.toLong() > 0) { "$path must be a positive integer" }.let { value.toLong() }

    private fun loadState() {
        state = if (stateFile.exists()) YamlConfiguration.loadConfiguration(stateFile) else YamlConfiguration()
        pending.clear()
        active.clear()
        state.getConfigurationSection("pending")?.getKeys(false)?.forEach { pathKey ->
            val serialized = state.getString("pending.$pathKey.station") ?: return@forEach
            val station = CookingStationKey.deserialize(serialized) ?: return@forEach
            pending[station] = loadItems(state.getList("pending.$pathKey.items").orEmpty())
        }
        state.getConfigurationSection("active")?.getKeys(false)?.forEach { pathKey ->
            val path = "active.$pathKey"
            val station = CookingStationKey.deserialize(state.getString("$path.station") ?: return@forEach) ?: return@forEach
            val recipe = recipes.firstOrNull { it.id == state.getString("$path.recipe") } ?: return@forEach
            val starterId = state.getString("$path.starter")?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@forEach
            active[station] = ActiveCooking(
                recipe,
                state.getLong("$path.remaining_ticks").coerceAtLeast(1L),
                state.getInt("$path.score").coerceIn(0, 100),
                state.getStringList("$path.seasonings"),
                starterId
            )
        }
    }

    private fun saveState() {
        val output = YamlConfiguration()
        pending.forEach { (station, items) ->
            val path = "pending.${station.pathKey()}"
            output.set("$path.station", station.serialize())
            output.set("$path.items", items.map { it.serialize() })
        }
        active.forEach { (station, session) ->
            val path = "active.${station.pathKey()}"
            output.set("$path.station", station.serialize())
            output.set("$path.recipe", session.recipe.id)
            output.set("$path.remaining_ticks", session.remainingTicks)
            output.set("$path.score", session.score)
            output.set("$path.seasonings", session.seasoningIds)
            output.set("$path.starter", session.starterId.toString())
        }
        output.save(stateFile)
    }

    private fun loadItems(values: List<Any?>): List<ItemStack> = values.filterNotNull().mapNotNull {
        @Suppress("UNCHECKED_CAST")
        (it as? Map<String, Any>)?.let(ItemStack::deserialize)
    }

    @EventHandler
    fun onStationClick(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK || !event.player.isSneaking) return
        val block = event.clickedBlock ?: return
        val equipment = equipmentAt(block) ?: return
        event.isCancelled = true
        open(event.player, CookingStationKey.from(block), equipment)
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val holder = event.view.topInventory.holder as? CookingHolder ?: return
        if (holder.owner != event.whoClicked.uniqueId) { event.isCancelled = true; return }
        val player = event.whoClicked as? Player ?: return
        val slot = event.rawSlot
        if (event.click == ClickType.SHIFT_LEFT || event.click == ClickType.SHIFT_RIGHT ||
            event.action == InventoryAction.MOVE_TO_OTHER_INVENTORY || event.action == InventoryAction.HOTBAR_MOVE_AND_READD ||
            event.action == InventoryAction.HOTBAR_SWAP || event.action == InventoryAction.COLLECT_TO_CURSOR) {
            event.isCancelled = true
            return
        }
        if (slot == CookingHolder.START) {
            event.isCancelled = true
            if (cookingStartClickAllowed(event.click)) start(player, event.view.topInventory, holder.station, holder.equipment)
            return
        }
        if (slot == CookingHolder.CANCEL) {
            event.isCancelled = true
            player.closeInventory()
            return
        }
        if (slot in CookingHolder.INGREDIENT_SLOTS && slot !in unlockedSlots(player)) event.isCancelled = true
        if (slot in 0 until event.view.topInventory.size && slot !in CookingHolder.INPUT_SLOTS) event.isCancelled = true
    }

    @EventHandler
    fun onDrag(event: InventoryDragEvent) {
        val holder = event.view.topInventory.holder as? CookingHolder ?: return
        val player = event.whoClicked as? Player ?: return
        if (event.rawSlots.any { it !in unlockedSlots(player) && it !in CookingHolder.SEASONING_SLOTS }) event.isCancelled = true
    }

    @EventHandler
    fun onClose(event: InventoryCloseEvent) {
        val holder = event.inventory.holder as? CookingHolder ?: return
        val player = event.player as? Player ?: return
        stationLocks.remove(holder.station, player.uniqueId)
        if (active.containsKey(holder.station)) return
        returnInputs(player, event.inventory, holder)
    }

    private fun open(player: Player, station: CookingStationKey, equipment: CookingEquipment) {
        if (active.containsKey(station)) {
            player.sendMessage(message(player, "cooking.error.in_progress")); return
        }
        val lockOwner = stationLocks[station]
        if (lockOwner != null && lockOwner != player.uniqueId) {
            player.sendMessage(message(player, "cooking.error.in_use"))
            return
        }
        stationLocks[station] = player.uniqueId
        pending.remove(station)?.let { results ->
            results.forEach { item ->
                localizeResult(item, player)
                player.inventory.addItem(item).values.forEach { player.world.dropItem(player.location, it) }
                item.itemMeta?.persistentDataContainer?.get(recipeKey, PersistentDataType.STRING)?.let { recipeId ->
                    val completion = item.itemMeta?.persistentDataContainer?.get(completionKey, PersistentDataType.INTEGER)
                    catalogStore.record(player.uniqueId, CatalogType.COOKING, recipeId, completion = completion)
                    player.sendMessage(message(player, "cooking.process.collected", mapOf("recipe" to message(player, "cooking.recipe.$recipeId"))))
                }
            }
            saveState()
        }
        val title = message(player, "cooking.ui.title")
        val holder = CookingHolder(player.uniqueId, station, equipment)
        val inventory = Bukkit.createInventory(holder, 54, Component.text(title))
        holder.backingInventory = inventory
        render(inventory, player)
        player.openInventory(inventory)
    }

    private fun render(inventory: Inventory, player: Player) {
        for (slot in 0 until inventory.size) inventory.setItem(slot, GuiMenuItems.backgroundPane(Material.GRAY_STAINED_GLASS_PANE))
        for (slot in CookingHolder.FRAME_SLOTS) inventory.setItem(slot, GuiMenuItems.backgroundPane(Material.BLACK_STAINED_GLASS_PANE))
        val unlocked = unlockedSlots(player)
        CookingHolder.INGREDIENT_SLOTS.filterNot(unlocked::contains).forEach { slot ->
            inventory.setItem(slot, lockedItem(player))
        }
        unlocked.forEach { inventory.setItem(it, null) }
        CookingHolder.SEASONING_SLOTS.forEach { inventory.setItem(it, null) }
        inventory.setItem(CookingHolder.START, startItem(player))
        inventory.setItem(CookingHolder.CANCEL, CCSystem.getAPI().getGuiElementService().backItem(message(player, "gui.common.back")))
        inventory.setItem(CookingHolder.INFO, infoItem(player))
    }

    private fun start(
        player: Player,
        inventory: Inventory,
        station: CookingStationKey,
        equipment: CookingEquipment
    ) {
        val stationBlock = station.blockIfLoaded()
        if (stationBlock == null || !hasActiveHeat(stationBlock, equipment)) {
            player.sendMessage(message(player, "cooking.error.no_heat"))
            return
        }
        val ingredientItems = CookingHolder.INGREDIENT_SLOTS.mapNotNull(inventory::getItem).filter(::isRealItem)
        val seasoningItems = CookingHolder.SEASONING_SLOTS.mapNotNull(inventory::getItem).filter(::isRealItem)
        if (ingredientItems.isEmpty()) { player.sendMessage(message(player, "cooking.error.no_ingredients")); return }
        val match = findBestMatch(ingredientItems, seasoningItems, equipment)
        if (match == null || match.score < minimumSimilarity) { player.sendMessage(message(player, "cooking.error.recipe_not_found")); return }
        if (!consumeInputs(inventory, match.recipe)) {
            player.sendMessage(message(player, "cooking.error.recipe_not_found")); return
        }
        CookingHolder.INPUT_SLOTS.forEach { inventory.setItem(it, null) }
        val seasoningIds = seasoningItems.flatMap { item -> ingredients.keys.filter { it == itemId(item) } }
        val score = (match.score * 100.0).roundToInt().coerceIn(0, 100)
        active[station] = ActiveCooking(
            match.recipe,
            match.recipe.completionTicks,
            score,
            seasoningIds,
            player.uniqueId
        )
        returnInputs(player, inventory, inventory.holder as? CookingHolder)
        saveState()
        player.closeInventory()
        player.sendMessage(message(player, "cooking.process.started", mapOf("recipe" to message(player, "cooking.recipe.${match.recipe.id}"))))
    }

    private fun complete(station: CookingStationKey) {
        val session = active[station] ?: return
        if (session.settled || session.remainingTicks > 0) return
        session.settled = true
        val result = ItemStack(session.recipe.resultMaterial)
        result.amount = 1
        result.editMeta { meta ->
            meta.setItemModel(session.recipe.resultModel)
            meta.persistentDataContainer.set(itemIdKey, PersistentDataType.STRING, "dish_${session.recipe.id}")
            meta.persistentDataContainer.set(customItemIdKey, PersistentDataType.STRING, "cooking.dish_${session.recipe.id}")
            meta.persistentDataContainer.set(recipeKey, PersistentDataType.STRING, session.recipe.id)
            meta.persistentDataContainer.set(completionKey, PersistentDataType.INTEGER, session.score)
        }
        pending[station] = listOf(result)
        if (rankManagerProvider()?.getPlayerProfession(session.starterId)?.profession == jp.awabi2048.cccontent.features.rank.profession.Profession.COOK) {
            rankManagerProvider()?.addProfessionExp(session.starterId, session.recipe.exp)
        }
        catalogStore.record(session.starterId, CatalogType.COOKING, session.recipe.id, completion = session.score, obtained = false)
        Bukkit.getPlayer(session.starterId)?.takeIf(Player::isOnline)?.let { player ->
            player.sendMessage(message(player, "cooking.process.completed", mapOf("recipe" to message(player, "cooking.recipe.${session.recipe.id}"), "score" to session.score)))
            player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f)
        }
        active.remove(station)
        saveState()
    }

    private fun startProgressTask() {
        progressTask?.cancel()
        progressTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            tickCounter++
            active.entries.toList().forEach { (station, session) ->
                val block = station.blockIfLoaded() ?: return@forEach
                if (equipmentAt(block) != session.recipe.equipment) {
                    invalidateStation(station)
                    return@forEach
                }
                if (!hasActiveHeat(block, session.recipe.equipment)) return@forEach
                session.remainingTicks--
                if (session.remainingTicks <= 0L) complete(station)
            }
            if (tickCounter % 100L == 0L && active.isNotEmpty()) saveState()
        }, 1L, 1L)
    }

    private fun localizeResult(item: ItemStack, player: Player) {
        val recipeId = item.itemMeta?.persistentDataContainer?.get(recipeKey, PersistentDataType.STRING) ?: return
        val completion = item.itemMeta?.persistentDataContainer?.get(completionKey, PersistentDataType.INTEGER) ?: 0
        item.editMeta {
            it.displayName(Component.text(message(player, "cooking.recipe.$recipeId")))
            it.lore(
                CCSystem.getAPI().getLoreService().render(
                    GuiLoreSpec.Blocks(
                        listOf(
                            GuiLoreBlock(listOf(GuiLoreLine.Text(message(player, "cooking.recipe_description.$recipeId")))),
                            GuiLoreBlock(
                                listOf(
                                    GuiLoreLine.Data(
                                        message(player, "cooking.item.data.completion"),
                                        "$completion%",
                                        "§f"
                                    )
                                )
                            )
                        )
                    )
                )
            )
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onStationBreak(event: BlockBreakEvent) {
        invalidateStation(CookingStationKey.from(event.block))
    }

    @EventHandler(ignoreCancelled = true)
    fun onStationBurn(event: BlockBurnEvent) {
        invalidateStation(CookingStationKey.from(event.block))
    }

    @EventHandler(ignoreCancelled = true)
    fun onStationExplode(event: BlockExplodeEvent) {
        event.blockList().forEach { invalidateStation(CookingStationKey.from(it)) }
    }

    @EventHandler(ignoreCancelled = true)
    fun onStationExplode(event: EntityExplodeEvent) {
        event.blockList().forEach { invalidateStation(CookingStationKey.from(it)) }
    }

    @EventHandler(ignoreCancelled = true)
    fun onStationMove(event: BlockPistonExtendEvent) {
        event.blocks.forEach { invalidateStation(CookingStationKey.from(it)) }
    }

    @EventHandler(ignoreCancelled = true)
    fun onStationMove(event: BlockPistonRetractEvent) {
        event.blocks.forEach { invalidateStation(CookingStationKey.from(it)) }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        closeCookingInventory(event.player)
    }

    @EventHandler
    fun onPlayerTeleport(event: PlayerTeleportEvent) {
        closeCookingInventory(event.player)
    }

    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        closeCookingInventory(event.player)
    }

    private fun closeCookingInventory(player: Player) {
        if (player.openInventory.topInventory.holder is CookingHolder) player.closeInventory()
    }

    private fun invalidateStation(station: CookingStationKey) {
        val changed = active.remove(station) != null || pending.remove(station) != null
        stationLocks.remove(station)?.let { Bukkit.getPlayer(it)?.closeInventory() }
        if (changed) saveState()
    }

    private fun consumeInputs(inventory: Inventory, recipe: CookingRecipe): Boolean {
        val required = recipe.ingredients + recipe.seasonings
        val available = CookingHolder.INPUT_SLOTS.mapNotNull { inventory.getItem(it)?.takeIf(::isRealItem) }
            .groupingBy(::itemId).fold(0) { total, item -> total + item.amount }
        val remainderById = cookingInputRemainders(available, required)?.toMutableMap() ?: return false
        CookingHolder.INPUT_SLOTS.forEach { slot ->
            val item = inventory.getItem(slot) ?: return@forEach
            if (!isRealItem(item)) return@forEach
            val id = itemId(item)
            val keep = minOf(item.amount, remainderById[id] ?: 0)
            if (keep == 0) inventory.setItem(slot, null)
            else item.amount = keep
            remainderById[id] = (remainderById[id] ?: 0) - keep
        }
        return true
    }

    fun catalogItems(): List<CatalogItem> = recipes.map { CatalogItem(it.id, it.resultMaterial) }

    private fun findBestMatch(
        items: List<ItemStack>,
        seasonings: List<ItemStack>,
        equipment: CookingEquipment
    ): CookingMatch? {
        val actual = items.groupingBy(::itemId).fold(0) { total, item -> total + item.amount }
        val actualSeasonings = seasonings.groupingBy(::itemId).fold(0) { total, item -> total + item.amount }
        return recipes.asSequence().filter { it.equipment == equipment }.map { recipe ->
            val expectedWeight = recipe.ingredients.values.sum().coerceAtLeast(1).toDouble()
            val matched = recipe.ingredients.entries.sumOf { (id, amount) -> minOf(actual[id] ?: 0, amount).toDouble() / amount * amount }
            val missing = (expectedWeight - matched) / expectedWeight
            val extra = (actual.values.sum() - recipe.ingredients.values.sum()).coerceAtLeast(0).toDouble() / expectedWeight
            val seasoningExpected = recipe.seasonings.values.sum().coerceAtLeast(1).toDouble()
            val seasoningMatched = recipe.seasonings.entries.sumOf { (id, amount) -> minOf(actualSeasonings[id] ?: 0, amount).toDouble() / amount * amount }
            val seasoningScore = if (recipe.seasonings.isEmpty()) 1.0 else seasoningMatched / seasoningExpected
            CookingMatch(recipe, (matched / expectedWeight * 0.8 + seasoningScore * 0.2 - missing * 0.15 - extra * 0.2).coerceIn(0.0, 1.0))
        }.maxByOrNull { it.score }
    }

    private fun equipmentAt(block: org.bukkit.block.Block): CookingEquipment? = when {
        block.type in setOf(Material.CAMPFIRE, Material.SOUL_CAMPFIRE) -> CookingEquipment.PAN
        block.type == Material.WATER_CAULDRON -> CookingEquipment.CAULDRON
        else -> null
    }

    private fun hasActiveHeat(block: org.bukkit.block.Block, equipment: CookingEquipment): Boolean {
        val heatBlock = when (equipment) {
            CookingEquipment.PAN -> block
            CookingEquipment.CAULDRON -> block.getRelative(org.bukkit.block.BlockFace.DOWN)
        }
        if (heatBlock.type !in setOf(Material.CAMPFIRE, Material.SOUL_CAMPFIRE)) return false
        return (heatBlock.blockData as? org.bukkit.block.data.type.Campfire)?.isLit == true
    }

    private fun itemId(item: ItemStack): String {
        val meta = item.itemMeta
        val explicit = meta?.persistentDataContainer?.get(itemIdKey, PersistentDataType.STRING)
        if (explicit != null) return explicit
        return ingredients.values.firstOrNull { ingredient ->
            ingredient.material == item.type && (ingredient.pdcId == null || meta?.persistentDataContainer?.get(itemIdKey, PersistentDataType.STRING) == ingredient.pdcId)
        }?.id ?: item.type.key.key
    }

    private fun unlockedSlots(player: Player): Set<Int> = CookingHolder.INGREDIENT_SLOTS.take(configuredIngredientSlotCount(player)).toSet()
    private fun configuredIngredientSlotCount(player: Player): Int {
        val currentLevel = level(player)
        return ingredientSlotsByLevel.entries.filter { it.key <= currentLevel }.maxByOrNull { it.key }?.value
            ?: error("No cooking ingredient slot setting applies to profession level $currentLevel")
    }
    private fun level(player: Player): Int = rankManagerProvider()?.getCurrentProfessionLevel(player.uniqueId) ?: 1
    private fun isRealItem(item: ItemStack): Boolean = item.type != Material.AIR && item.type != Material.GRAY_STAINED_GLASS_PANE && item.type != Material.WHITE_STAINED_GLASS_PANE && item.type != Material.BARRIER
    private fun returnInputs(player: Player, inventory: Inventory, holder: CookingHolder?, excludedSlots: Set<Int> = emptySet()) {
        if (holder?.returned == true) return
        holder?.returned = true
        val items = CookingHolder.INPUT_SLOTS.filterNot(excludedSlots::contains).mapNotNull(inventory::getItem).filter(::isRealItem).map(ItemStack::clone)
        CookingHolder.INPUT_SLOTS.forEach { inventory.setItem(it, null) }
        items.forEach { item -> player.inventory.addItem(item).values.forEach { player.world.dropItem(player.location, it) } }
        saveState()
    }
    private fun lockedItem(player: Player): ItemStack = CCSystem.getAPI().getGuiElementService().menuIcon(GuiMenuIconSpec(
        Material.BARRIER, GuiNameSpec.Text(message(player, "cooking.ui.locked"), com.awabi2048.ccsystem.api.gui.GuiNameStyle.DEFAULT), GuiElementRole.DECORATION, 1,
        emptyList(), emptyList(), emptyList(), listOf(message(player, "cooking.ui.locked_reason")), emptyList(), emptyList(), null
    ))
    private fun infoItem(player: Player): ItemStack = CCSystem.getAPI().getGuiElementService().menuIcon(GuiMenuIconSpec(
        Material.BOOK, GuiNameSpec.Text(message(player, "cooking.ui.info"), com.awabi2048.ccsystem.api.gui.GuiNameStyle.DEFAULT), GuiElementRole.CONTENT, 1,
        emptyList(), listOf(GuiMenuIconData(message(player, "cooking.ui.level"), level(player), "§f"), GuiMenuIconData(message(player, "cooking.ui.ingredient_slots"), configuredIngredientSlotCount(player), "§f")), emptyList(), emptyList(), emptyList(), emptyList(), null
    ))
    private fun startItem(player: Player): ItemStack = CCSystem.getAPI().getGuiElementService().menuIcon(GuiMenuIconSpec(
        Material.CAMPFIRE, GuiNameSpec.Text(message(player, "cooking.ui.start"), com.awabi2048.ccsystem.api.gui.GuiNameStyle.SUCCESS), GuiElementRole.ACTION, 1,
        emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), listOf(
            GuiMenuIconAction(
                message(player, "lore.click.any"),
                message(player, "cooking.ui.start_action"),
                message(player, "lore.action_single_with_operation", mapOf(
                    "operation" to message(player, "lore.click.any"),
                    "action" to message(player, "cooking.ui.start_action")
                )),
                true
            )
        ), null
    ))
    private fun message(player: Player, key: String, placeholders: Map<String, Any> = emptyMap()): String = CCSystem.getAPI().getI18nString(player, key, placeholders)
}

private class CookingHolder(
    val owner: UUID,
    val station: CookingStationKey,
    val equipment: CookingEquipment
) : InventoryHolder {
    lateinit var backingInventory: Inventory
    var returned: Boolean = false
    override fun getInventory(): Inventory = backingInventory
    companion object {
        const val START = 49
        const val CANCEL = 45
        const val INFO = 4
        val INGREDIENT_SLOTS = listOf(20, 21, 22, 23, 24)
        val SEASONING_SLOTS = listOf(30, 31, 32)
        val INPUT_SLOTS = INGREDIENT_SLOTS + SEASONING_SLOTS
        val FRAME_SLOTS = (0..8).toList() + (45..53).toList()
    }
}
