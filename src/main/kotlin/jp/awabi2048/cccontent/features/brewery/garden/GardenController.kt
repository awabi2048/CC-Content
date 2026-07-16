package jp.awabi2048.cccontent.features.brewery.garden

import com.awabi2048.ccsystem.CCSystem
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.block.BlockPistonExtendEvent
import org.bukkit.event.block.BlockPistonRetractEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.io.File
import java.util.concurrent.ThreadLocalRandom

class GardenController(private val plugin: JavaPlugin) : Listener, AutoCloseable {
    private val settings = GardenSettingsLoader.load(plugin)
    private val store = GardenStore(File(plugin.dataFolder, "data/brewery/garden.yml"))
    private var growthTask: BukkitTask? = null

    fun initialize() {
        validateStoredStates()
        GardenItems.register(settings.plants.values)
        plugin.server.pluginManager.registerEvents(this, plugin)
        val period = settings.growthCheckIntervalSeconds * 20L
        growthTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable(::advanceLoadedPlants), period, period)
    }

    fun flush() = store.save()

    override fun close() {
        growthTask?.cancel()
        growthTask = null
        HandlerList.unregisterAll(this)
        store.save()
        GardenItems.unregister()
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInteract(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND || event.action != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return
        val clicked = event.clickedBlock ?: return
        if (clicked.type != Material.GRASS_BLOCK) return

        val held = event.player.inventory.itemInMainHand
        val customId = GardenItems.customId(held) ?: return
        val plant = settings.plants.values.firstOrNull { it.itemSeedId == customId } ?: return
        val target = clicked.getRelative(0, 1, 0)
        if (!target.type.isAir || store.get(target.gardenLocation()) != null) return

        val now = System.currentTimeMillis()
        val state = GardenPlantState(
            plantId = plant.id,
            stage = 0,
            plantedAtMillis = now,
            nextGrowthAtMillis = now + plant.stages.first().growthSeconds * 1000L
        )
        target.setBlockData(plant.stages.first().createBlockData(), false)
        store.put(target.gardenLocation(), state)
        held.amount -= 1
        event.isCancelled = true
        message(event.player, "brewery.garden.planted", "plant" to plantName(event.player, plant))
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBreak(event: BlockBreakEvent) {
        val location = event.block.gardenLocation()
        val state = store.get(location)
        if (state != null) {
            val plant = settings.plants.getValue(state.plantId)
            if (event.player.inventory.itemInMainHand.type == Material.SHEARS) {
                event.isCancelled = true
                if (state.stage != plant.matureStage.id) {
                    message(event.player, "brewery.garden.not_mature")
                    return
                }
                harvest(event.player, event.block, plant)
                return
            }
            store.remove(location)
        }

        store.remove(event.block.getRelative(0, 1, 0).gardenLocation())
        if (event.block.type in settings.seedSourceMaterials &&
            ThreadLocalRandom.current().nextDouble() < settings.seedDropChance
        ) {
            val plant = settings.plants.values.random()
            event.block.world.dropItemNaturally(event.block.location, GardenItems.seed(plant, event.player))
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onBlockExplode(event: BlockExplodeEvent) = removeDestroyed(event.blockList())

    @EventHandler(ignoreCancelled = true)
    fun onEntityExplode(event: EntityExplodeEvent) = removeDestroyed(event.blockList())

    @EventHandler(ignoreCancelled = true)
    fun onPistonExtend(event: BlockPistonExtendEvent) {
        if (event.blocks.any(::isPlantOrSupport)) event.isCancelled = true
    }

    @EventHandler(ignoreCancelled = true)
    fun onPistonRetract(event: BlockPistonRetractEvent) {
        if (event.blocks.any(::isPlantOrSupport)) event.isCancelled = true
    }

    private fun harvest(player: Player, block: Block, plant: GardenPlant) {
        val fruit = GardenItems.fruit(plant, player)
        player.inventory.addItem(fruit).values.forEach { block.world.dropItemNaturally(block.location, it) }
        val regrown = GardenGrowth.regrow(plant, System.currentTimeMillis())
        block.setBlockData(plant.stages[regrown.stage].createBlockData(), false)
        store.put(block.gardenLocation(), regrown)
        Bukkit.getPluginManager().callEvent(GardenHarvestEvent(player, block.location, plant.id, plant.itemFruitId))
        message(player, "brewery.garden.harvested", "plant" to plantName(player, plant))
    }

    private fun advanceLoadedPlants() {
        val now = System.currentTimeMillis()
        store.snapshot().forEach { (location, state) ->
            val world = Bukkit.getWorld(location.worldId) ?: return@forEach
            if (!world.isChunkLoaded(location.x shr 4, location.z shr 4)) return@forEach
            val plant = settings.plants[state.plantId] ?: error("Garden台帳の植物IDが不正です: ${state.plantId}")
            if (state.stage !in plant.stages.indices) error("Garden台帳の段階が不正です: ${state.stage}")
            val block = world.getBlockAt(location.x, location.y, location.z)
            if (block.blockData != plant.stages[state.stage].createBlockData()) {
                store.remove(location)
                return@forEach
            }
            if (GardenGrowth.advance(plant, state, now)) {
                block.setBlockData(plant.stages[state.stage].createBlockData(), false)
                store.put(location, state)
            }
        }
    }

    private fun validateStoredStates() {
        store.snapshot().forEach { (_, state) ->
            val plant = settings.plants[state.plantId]
                ?: error("Garden台帳に未知の植物IDがあります: ${state.plantId}")
            require(state.stage in plant.stages.indices) { "Garden台帳の段階が範囲外です: ${state.stage}" }
            require(state.plantedAtMillis >= 0 && state.nextGrowthAtMillis >= 0) { "Garden台帳の時刻が不正です" }
        }
    }

    private fun removeDestroyed(blocks: List<Block>) {
        blocks.forEach { block ->
            store.remove(block.gardenLocation())
            store.remove(block.getRelative(0, 1, 0).gardenLocation())
        }
    }

    private fun isPlantOrSupport(block: Block): Boolean =
        store.get(block.gardenLocation()) != null || store.get(block.getRelative(0, 1, 0).gardenLocation()) != null

    private fun message(player: Player, key: String, vararg placeholders: Pair<String, Any>) {
        player.sendMessage(CCSystem.getAPI().getI18nString(player, key, placeholders.toMap()))
    }

    private fun plantName(player: Player, plant: GardenPlant): String =
        CCSystem.getAPI().getI18nString(player, plant.fruitLanguageKey)

    private fun Block.gardenLocation() = GardenLocation(world.uid, x, y, z)
}
