package jp.awabi2048.cccontent.features.crops

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.localization.LocalizationKey
import com.awabi2048.ccsystem.api.localization.generated.ContentCropsKeys
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent
import jp.awabi2048.cccontent.CCContent
import jp.awabi2048.cccontent.items.ContentItemModels
import jp.awabi2048.cccontent.items.CustomItemManager
import jp.awabi2048.cccontent.persistence.ContentPdcKeys
import jp.awabi2048.cccontent.util.FeatureInitializationLogger
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.block.BlockFace
import org.bukkit.entity.Display
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.Interaction
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

/**
 * 作物栽培（Crops）機能。
 *
 * - 耕した農地/ソウルサンドの上に木の棒を右クリックで支柱を設置する。
 * - 支柱は Interaction（当たり判定）＋ 支柱ItemDisplay ＋ 作物ItemDisplay の3エンティティで構成し、
 *   状態は Interaction のPDCに集約して保存する（エンティティNBTで再起動後も復元される）。
 * - 作物は読込中のみtickで成長し、骨粉で段階的に進む。成熟支柱を右クリックで収穫すると
 *   作付け状態を維持したまま1段階目へ戻る。左クリックで支柱ごと回収する。
 */
class CropsFeature(private val plugin: CCContent) : Listener {

    private lateinit var settings: CropsSettings
    private var growthTask: BukkitTask? = null

    fun initialize(logger: FeatureInitializationLogger) {
        settings = CropsSettings.load(plugin)
        plugin.server.pluginManager.registerEvents(this, plugin)
        growthTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable { tickGrowth() }, 20L, 20L)
        logger.setStatus("Crops", FeatureInitializationLogger.Status.SUCCESS)
        logger.addSummaryMessage("Crops", "作物定義: ${settings.crops.size}件")
    }

    fun shutdown() {
        runCatching { growthTask?.cancel() }
        growthTask = null
    }

    // ---- 設置（木の棒を右クリック） ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    fun onPlace(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        val item = event.item ?: return
        if (item.type != Material.STICK) return
        val block = event.clickedBlock ?: return
        // 上面以外（側面/下面）への誤設置と、隣接ブロックへの誤判定を防ぐため、上面ヒットのみを許可する。
        if (event.blockFace != BlockFace.UP) return
        if (block.type != Material.FARMLAND && block.type != Material.SOUL_SAND) return
        if (!block.getRelative(BlockFace.UP).type.isAir) return

        val loc = block.location.add(0.5, 1.0, 0.5)
        if (hasSupportNearby(loc)) {
            event.isCancelled = true
            event.setUseInteractedBlock(Event.Result.DENY)
            event.setUseItemInHand(Event.Result.DENY)
            return
        }

        event.isCancelled = true
        event.setUseInteractedBlock(Event.Result.DENY)
        event.setUseItemInHand(Event.Result.DENY)

        spawnSupport(loc)

        val player = event.player
        if (player.gameMode != GameMode.CREATIVE) {
            item.amount -= 1
            if (item.amount <= 0) player.inventory.setItemInMainHand(null)
        }
        player.sendMessage(localized(player, ContentCropsKeys.CROPS_SUPPORT_PLACE))
        player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.4f)
    }

    private fun hasSupportNearby(loc: Location): Boolean {
        return loc.world?.getNearbyEntities(loc, 0.6, 1.5, 0.6).orEmpty().any { supportOf(it) != null }
    }

    private fun spawnSupport(loc: Location) {
        val world = loc.world ?: return
        val supportDisplay = world.spawnEntity(loc, EntityType.ITEM_DISPLAY) as ItemDisplay
        supportDisplay.setItemStack(supportItemStack())
        supportDisplay.billboard = Display.Billboard.FIXED

        val cropDisplay = world.spawnEntity(loc, EntityType.ITEM_DISPLAY) as ItemDisplay
        cropDisplay.setItemStack(ItemStack(Material.AIR))
        cropDisplay.billboard = Display.Billboard.FIXED

        val interaction = world.spawnEntity(loc, EntityType.INTERACTION) as Interaction
        interaction.interactionWidth = 0.8f
        interaction.interactionHeight = 1.5f
        interaction.isPersistent = true

        val pdc = interaction.persistentDataContainer
        pdc.set(ContentPdcKeys.cropsSupport, PersistentDataType.BYTE, 1)
        pdc.set(ContentPdcKeys.cropsSupportDisplay, PersistentDataType.STRING, supportDisplay.uniqueId.toString())
        pdc.set(ContentPdcKeys.cropsCropDisplay, PersistentDataType.STRING, cropDisplay.uniqueId.toString())
    }

    private fun supportItemStack(): ItemStack {
        val item = ItemStack(Material.STICK)
        val meta = item.itemMeta ?: return item
        meta.setItemModel(ContentItemModels.crops("support"))
        item.itemMeta = meta
        return item
    }

    private fun cropItemStack(model: NamespacedKey?): ItemStack {
        if (model == null) return ItemStack(Material.AIR)
        val item = ItemStack(Material.STICK)
        val meta = item.itemMeta ?: return item
        meta.setItemModel(model)
        item.itemMeta = meta
        return item
    }

    // ---- 右クリック（骨粉 / 作付け / 収穫） ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    fun onEntityInteract(event: PlayerInteractEntityEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        val interaction = supportOf(event.rightClicked) ?: return
        event.isCancelled = true

        val player = event.player
        val pdc = interaction.persistentDataContainer
        val cropType = pdc.get(ContentPdcKeys.cropsCropType, PersistentDataType.STRING)
        val mainHand = player.inventory.itemInMainHand

        // 骨粉：未作付け・成熟済みでなければ1段階（設定値）進める
        if (mainHand.type == Material.BONE_MEAL && cropType != null) {
            val def = settings.crop(cropType) ?: return
            val stage = pdc.get(ContentPdcKeys.cropsStage, PersistentDataType.INTEGER) ?: 0
            if (stage < def.maxStage) {
                consumeIfSurvival(player, mainHand)
                setStage(interaction, def, (stage + def.boneMealStages).coerceAtMost(def.maxStage))
                player.playSound(player.location, Sound.ITEM_BONE_MEAL_USE, 1.0f, 1.0f)
            }
            return
        }

        // 未作付け：種を持っていれば作付け
        if (cropType == null) {
            val seedCustom = CustomItemManager.identify(mainHand)
            val def = settings.crops.firstOrNull { it.seedItemId == seedCustom?.fullId }
            if (def != null) {
                consumeIfSurvival(player, mainHand)
                pdc.set(ContentPdcKeys.cropsCropType, PersistentDataType.STRING, def.id)
                setStage(interaction, def, 0)
                player.sendMessage(localized(player, ContentCropsKeys.CROPS_PLANT))
            }
            return
        }

        // すでに作付け済み
        val def = settings.crop(cropType) ?: return
        val stage = pdc.get(ContentPdcKeys.cropsStage, PersistentDataType.INTEGER) ?: 0
        val seedCustom = CustomItemManager.identify(mainHand)
        val isSeed = settings.crops.any { it.seedItemId == seedCustom?.fullId }
        if (stage >= def.maxStage) {
            if (!isSeed && mainHand.type != Material.BONE_MEAL) {
                val harvestItem = CustomItemManager.createItem(def.harvestItemId, 1)
                if (harvestItem != null) {
                    player.inventory.addItem(harvestItem).values.forEach {
                        player.world.dropItemNaturally(player.location, it)
                    }
                }
                // 収穫しても作付け状態は維持し、1段階目へ戻す
                setStage(interaction, def, 0)
                player.sendMessage(localized(player, ContentCropsKeys.CROPS_HARVEST))
                player.playSound(player.location, Sound.BLOCK_CROP_BREAK, 1.0f, 1.0f)
            } else if (isSeed) {
                player.sendMessage(localized(player, ContentCropsKeys.CROPS_ALREADY_PLANTED))
            }
        } else if (mainHand.type == Material.AIR) {
            player.sendMessage(localized(player, ContentCropsKeys.CROPS_NOT_READY))
        }
    }

    // ---- 左クリック（支柱ごと回収） ----

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    fun onAttack(event: PrePlayerAttackEntityEvent) {
        val interaction = supportOf(event.attacked) ?: return
        event.isCancelled = true
        breakSupport(interaction, event.player)
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    fun onLegacyAttack(event: EntityDamageByEntityEvent) {
        val entity = event.entity
        if (entity !is Interaction) return
        val interaction = supportOf(entity) ?: return
        event.isCancelled = true
        breakSupport(interaction, event.damager as? Player)
    }

    private fun breakSupport(interaction: Interaction, player: Player?) {
        if (!interaction.isValid) return
        val pdc = interaction.persistentDataContainer
        findDisplay(pdc, ContentPdcKeys.cropsSupportDisplay)?.remove()
        findDisplay(pdc, ContentPdcKeys.cropsCropDisplay)?.remove()
        val loc = interaction.location.clone()
        interaction.remove()
        if (player != null && player.gameMode != GameMode.CREATIVE) {
            player.world.dropItemNaturally(loc, ItemStack(Material.STICK))
        }
        player?.sendMessage(localized(player, ContentCropsKeys.CROPS_SUPPORT_BREAK))
    }

    // ---- 成長 ticker（読込中のみ進行） ----

    private fun tickGrowth() {
        for (world in plugin.server.worlds) {
            for (entity in world.getEntitiesByClass(Interaction::class.java)) {
                val pdc = entity.persistentDataContainer
                if (!pdc.has(ContentPdcKeys.cropsSupport, PersistentDataType.BYTE)) continue
                val cropType = pdc.get(ContentPdcKeys.cropsCropType, PersistentDataType.STRING) ?: continue
                val def = settings.crop(cropType) ?: continue
                val stage = pdc.get(ContentPdcKeys.cropsStage, PersistentDataType.INTEGER) ?: 0
                if (stage >= def.maxStage) continue
                val progress = (pdc.get(ContentPdcKeys.cropsProgress, PersistentDataType.INTEGER) ?: 0) + 20
                if (progress >= def.ticksPerStage) {
                    val newStage = stage + 1
                    pdc.set(ContentPdcKeys.cropsStage, PersistentDataType.INTEGER, newStage)
                    pdc.set(ContentPdcKeys.cropsProgress, PersistentDataType.INTEGER, progress - def.ticksPerStage)
                    findDisplay(pdc, ContentPdcKeys.cropsCropDisplay)
                        ?.setItemStack(cropItemStack(def.stageModels.getOrNull(newStage)))
                } else {
                    pdc.set(ContentPdcKeys.cropsProgress, PersistentDataType.INTEGER, progress)
                }
            }
        }
    }

    // ---- ヘルパ ----

    private fun supportOf(entity: Entity): Interaction? {
        if (entity !is Interaction) return null
        if (!entity.persistentDataContainer.has(ContentPdcKeys.cropsSupport, PersistentDataType.BYTE)) return null
        return entity
    }

    private fun findDisplay(pdc: org.bukkit.persistence.PersistentDataContainer, key: NamespacedKey): ItemDisplay? {
        val uuid = pdc.get(key, PersistentDataType.STRING) ?: return null
        val entity = Bukkit.getEntity(UUID.fromString(uuid)) ?: return null
        return entity as? ItemDisplay
    }

    private fun setStage(interaction: Interaction, def: CropDefinition, stage: Int) {
        val pdc = interaction.persistentDataContainer
        pdc.set(ContentPdcKeys.cropsStage, PersistentDataType.INTEGER, stage)
        pdc.set(ContentPdcKeys.cropsProgress, PersistentDataType.INTEGER, 0)
        findDisplay(pdc, ContentPdcKeys.cropsCropDisplay)
            ?.setItemStack(cropItemStack(def.stageModels.getOrNull(stage)))
    }

    private fun consumeIfSurvival(player: Player, item: ItemStack) {
        if (player.gameMode == GameMode.CREATIVE) return
        item.amount -= 1
        if (item.amount <= 0) player.inventory.setItemInMainHand(null)
    }

    private fun localized(player: Player, key: LocalizationKey<String>): String =
        CCSystem.getAPI().getLocalized(player, key).replace('&', '§')
}
