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
import jp.awabi2048.cccontent.util.SystemEntityMarker
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.block.BlockFace
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockFadeEvent
import org.bukkit.event.entity.EntityInteractEvent
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f
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
        logger.addSummaryMessage("Crops", "作物定義: ${settings.crops.size}件, debug=${settings.debug}")
        if (settings.debug) {
            plugin.logger.info("[Crops][Debug] 初期化完了: debug=true, crops=${settings.crops.map { it.id }}")
            settings.crops.forEach { def ->
                plugin.logger.info(
                    "[Crops][Debug] 定義 ${def.id}: maxStage=${def.maxStage}, ticksPerStage=${def.ticksPerStage}, " +
                        "boneMealStages=${def.boneMealStages}, seed=${def.seedItemId}, harvest=${def.harvestItemId}"
                )
            }
        }
    }

    fun shutdown() {
        runCatching { growthTask?.cancel() }
        growthTask = null
    }

    // ---- 設置（木の棒を右クリック） ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    fun onPlace(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) {
            debugLog("onPlace: hand=${event.hand} != HAND のためスキップ player=${event.player.name}")
            return
        }
        if (event.action != Action.RIGHT_CLICK_BLOCK) {
            debugLog("onPlace: action=${event.action} != RIGHT_CLICK_BLOCK のためスキップ player=${event.player.name}")
            return
        }
        val item = event.item
        if (item == null) {
            debugLog("onPlace: item=null のためスキップ player=${event.player.name} face=${event.blockFace}")
            return
        }
        if (item.type != Material.STICK) {
            debugLog("onPlace: item=${item.type} != STICK のためスキップ player=${event.player.name} face=${event.blockFace}")
            return
        }
        val block = event.clickedBlock
        if (block == null) {
            debugLog("onPlace: clickedBlock=null のためスキップ player=${event.player.name}")
            return
        }
        debugLog(
            "onPlace: 試行 player=${event.player.name} block=${block.type} at=${block.x},${block.y},${block.z} " +
                "face=${event.blockFace} above=${block.getRelative(BlockFace.UP).type} item=${item.type} hand=${event.hand}"
        )
        // 上面以外（側面/下面）への誤設置と、隣接ブロックへの誤判定を防ぐため、上面ヒットのみを許可する。
        if (event.blockFace != BlockFace.UP) {
            debugLog("onPlace: face=${event.blockFace} != UP のためスキップ（上面以外は設置不可）")
            return
        }
        if (block.type != Material.FARMLAND && block.type != Material.SOUL_SAND) {
            debugLog("onPlace: block.type=${block.type} は FARMLAND/SOUL_SAND ではないためスキップ")
            return
        }
        val aboveType = block.getRelative(BlockFace.UP).type
        if (!aboveType.isAir) {
            debugLog("onPlace: 上が空気ではないためスキップ above=$aboveType at=${block.x},${block.y + 1},${block.z}")
            return
        }

        val loc = block.location.add(0.5, 1.0, 0.5)
        if (hasSupportNearby(loc)) {
            debugLog("onPlace: hasSupportNearby=true のため設置をブロック loc=$loc")
            event.isCancelled = true
            event.setUseInteractedBlock(Event.Result.DENY)
            event.setUseItemInHand(Event.Result.DENY)
            return
        }

        event.isCancelled = true
        event.setUseInteractedBlock(Event.Result.DENY)
        event.setUseItemInHand(Event.Result.DENY)

        spawnSupport(loc)
        debugLog("onPlace: 支柱を設置 loc=$loc uuids stored in Interaction")

        val player = event.player
        if (player.gameMode != GameMode.CREATIVE) {
            item.amount -= 1
            if (item.amount <= 0) player.inventory.setItemInMainHand(null)
        }
        player.sendMessage(localized(player, ContentCropsKeys.CROPS_SUPPORT_PLACE))
        player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.4f)
    }

    private fun hasSupportNearby(loc: Location): Boolean {
        val world = loc.world ?: return false
        // 旧実装では 0.6x1.5x0.6 のAABBで隣接マスの支柱まで誤ヒットしていたため、
        // 同ブロック厳密一致（distanceSquared < 0.25、半径0.5）のみを占有とみなす。
        val candidates = world.getNearbyEntities(loc, 0.6, 0.6, 0.6).filter { supportOf(it) != null }
        val filtered = candidates.filter { it.location.distanceSquared(loc) < 0.25 }
        if (settings.debug) {
            if (filtered.isNotEmpty()) {
                filtered.forEach { entity ->
                    debugLog(
                        "hasSupportNearby hit reqLoc=${loc.blockX},${loc.blockY},${loc.blockZ} " +
                            "exact=${entity.location} distSq=${entity.location.distanceSquared(loc)} valid=${entity.isValid}"
                    )
                }
            } else if (candidates.isNotEmpty()) {
                candidates.forEach { entity ->
                    debugLog(
                        "hasSupportNearby ignored neighbor reqLoc=${loc.blockX},${loc.blockY},${loc.blockZ} " +
                            "neighbor=${entity.location} distSq=${entity.location.distanceSquared(loc)}"
                    )
                }
            }
        }
        return filtered.isNotEmpty()
    }

    // ---- 農地破壊時の孤児除去 ----

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onFarmBlockBreak(event: BlockBreakEvent) {
        val block = event.block
        if (block.type != Material.FARMLAND && block.type != Material.SOUL_SAND) return
        val loc = block.location.add(0.5, 1.0, 0.5)
        removeOrphanSupportsAt(loc, "BlockBreak")
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onFarmBlockFade(event: BlockFadeEvent) {
        val block = event.block
        if (block.type != Material.FARMLAND) return
        val loc = block.location.add(0.5, 1.0, 0.5)
        removeOrphanSupportsAt(loc, "BlockFade")
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityTrample(event: EntityInteractEvent) {
        val block = event.block
        if (block.type != Material.FARMLAND) return
        val loc = block.location.add(0.5, 1.0, 0.5)
        removeOrphanSupportsAt(loc, "Trample")
    }

    private fun removeOrphanSupportsAt(loc: Location, reason: String) {
        val world = loc.world ?: return
        val orphans = world.getNearbyEntities(loc, 0.5, 0.5, 0.5).mapNotNull { supportOf(it) }
            .filter { it.location.distanceSquared(loc) < 0.25 }
        if (orphans.isEmpty()) return
        orphans.forEach { interaction ->
            val pdc = interaction.persistentDataContainer
            val cropType = pdc.get(ContentPdcKeys.cropsCropType, PersistentDataType.STRING)
            debugLog("removeOrphanSupportsAt[$reason]: 除去 loc=$loc orphan=${interaction.location} cropType=$cropType")
            findDisplay(pdc, ContentPdcKeys.cropsSupportDisplay)?.remove()
            findDisplay(pdc, ContentPdcKeys.cropsCropDisplay)?.remove()
            interaction.remove()
        }
    }

    private fun spawnSupport(loc: Location) {
        val world = loc.world ?: return
        // 農地の見た目表面（FARMLAND 0.9375 / SOUL_SAND 0.875）にディスプレイ底面を合わせる。
        // ItemDisplay は中心原点のため、translation.y = surface - loc.y + 0.5*scale で補正する。
        val farmBlock = world.getBlockAt(loc.blockX, loc.blockY - 1, loc.blockZ)
        val baseTy = when (farmBlock.type) {
            Material.SOUL_SAND -> 0.375f
            else -> 0.4375f // FARMLAND 想定
        }
        val ty = baseTy + 0.015f // 微小な浮かせでZファイティング回避
        val transformation = Transformation(Vector3f(0f, ty, 0f), Quaternionf(), Vector3f(1f, 1f, 1f), Quaternionf())
        val supportDisplay = world.spawnEntity(loc, EntityType.ITEM_DISPLAY) as ItemDisplay
        SystemEntityMarker.mark(supportDisplay, plugin)
        supportDisplay.setItemStack(supportItemStack())
        supportDisplay.billboard = Display.Billboard.FIXED
        supportDisplay.transformation = transformation

        val cropDisplay = world.spawnEntity(loc, EntityType.ITEM_DISPLAY) as ItemDisplay
        SystemEntityMarker.mark(cropDisplay, plugin)
        cropDisplay.setItemStack(ItemStack(Material.AIR))
        cropDisplay.billboard = Display.Billboard.FIXED
        cropDisplay.transformation = transformation

        val interaction = world.spawnEntity(loc, EntityType.INTERACTION) as Interaction
        SystemEntityMarker.mark(interaction, plugin)
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
        if (event.hand != EquipmentSlot.HAND) {
            debugLog("onEntityInteract: hand=${event.hand} != HAND のためスキップ player=${event.player.name}")
            return
        }
        val interaction = supportOf(event.rightClicked)
        if (interaction == null) {
            debugLog("onEntityInteract: supportOf=null（Crops支柱ではない） entity=${event.rightClicked.type} player=${event.player.name}")
            return
        }
        event.isCancelled = true

        val player = event.player
        val pdc = interaction.persistentDataContainer
        val cropType = pdc.get(ContentPdcKeys.cropsCropType, PersistentDataType.STRING)
        val stage = pdc.get(ContentPdcKeys.cropsStage, PersistentDataType.INTEGER) ?: 0
        val progress = pdc.get(ContentPdcKeys.cropsProgress, PersistentDataType.INTEGER) ?: 0
        val mainHand = player.inventory.itemInMainHand
        val seedCustom = CustomItemManager.identify(mainHand)
        debugLog(
            "onEntityInteract: player=${player.name} cropType=$cropType stage=$stage progress=$progress " +
                "hand=${mainHand.type} seedId=${seedCustom?.fullId} at=${interaction.location.blockX},${interaction.location.blockY},${interaction.location.blockZ}"
        )

        // 骨粉：未作付け・成熟済みでなければ1段階（設定値）進める
        if (mainHand.type == Material.BONE_MEAL && cropType != null) {
            val def = settings.crop(cropType)
            if (def == null) {
                debugLog("onEntityInteract: 骨粉だが cropType=$cropType の定義が見つからない")
                return
            }
            if (stage < def.maxStage) {
                val before = stage
                val after = (stage + def.boneMealStages).coerceAtMost(def.maxStage)
                consumeIfSurvival(player, mainHand)
                setStage(interaction, def, after)
                debugLog("onEntityInteract: 骨粉で成長 $before -> $after (max=${def.maxStage})")
                player.playSound(player.location, Sound.ITEM_BONE_MEAL_USE, 1.0f, 1.0f)
            } else {
                debugLog("onEntityInteract: 骨粉だが既に成熟 stage=$stage")
            }
            return
        }

        // 未作付け：種を持っていれば作付け
        if (cropType == null) {
            val def = settings.crops.firstOrNull { it.seedItemId == seedCustom?.fullId }
            if (def != null) {
                debugLog("onEntityInteract: 作付け seed=${seedCustom?.fullId} -> ${def.id} stage 0")
                consumeIfSurvival(player, mainHand)
                pdc.set(ContentPdcKeys.cropsCropType, PersistentDataType.STRING, def.id)
                setStage(interaction, def, 0)
                player.sendMessage(localized(player, ContentCropsKeys.CROPS_PLANT))
            } else {
                debugLog("onEntityInteract: 未作付けだが手持ちは種ではない hand=${mainHand.type} seedId=${seedCustom?.fullId}")
            }
            return
        }

        // すでに作付け済み
        val def = settings.crop(cropType)
        if (def == null) {
            debugLog("onEntityInteract: 作付け済みだが cropType=$cropType の定義が見つからない")
            return
        }
        val isSeed = settings.crops.any { it.seedItemId == seedCustom?.fullId }
        debugLog("onEntityInteract: 作付け済み判定 isSeed=$isSeed stage=$stage max=${def.maxStage} hand=${mainHand.type}")
        if (stage >= def.maxStage) {
            if (!isSeed && mainHand.type != Material.BONE_MEAL) {
                debugLog("onEntityInteract: 収穫実行 harvest=${def.harvestItemId} stage $stage -> 0")
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
                debugLog("onEntityInteract: 成熟だが手に種を持っているため収穫せず ALREADY_PLANTED")
                player.sendMessage(localized(player, ContentCropsKeys.CROPS_ALREADY_PLANTED))
            }
        } else if (mainHand.type == Material.AIR) {
            debugLog("onEntityInteract: 未成熟で空手のため NOT_READY stage=$stage max=${def.maxStage}")
            player.sendMessage(localized(player, ContentCropsKeys.CROPS_NOT_READY))
        } else {
            debugLog("onEntityInteract: 未成熟で種/骨粉以外のため何もしない hand=${mainHand.type}")
        }
    }

    // ---- 左クリック（支柱ごと回収） ----

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    fun onAttack(event: PrePlayerAttackEntityEvent) {
        val interaction = supportOf(event.attacked)
        if (interaction == null) {
            debugLog("onAttack: supportOf=null entity=${event.attacked.type} player=${event.player.name}")
            return
        }
        debugLog("onAttack: 左クリック検知 player=${event.player.name} at=${interaction.location.blockX},${interaction.location.blockY},${interaction.location.blockZ}")
        event.isCancelled = true
        breakSupport(interaction, event.player)
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    fun onLegacyAttack(event: EntityDamageByEntityEvent) {
        val entity = event.entity
        if (entity !is Interaction) {
            debugLog("onLegacyAttack: entity not Interaction type=${entity.type}")
            return
        }
        val interaction = supportOf(entity)
        if (interaction == null) {
            debugLog("onLegacyAttack: supportOf=null")
            return
        }
        debugLog("onLegacyAttack: 左クリック(legacy) player=${(event.damager as? Player)?.name} at=${interaction.location.blockX},${interaction.location.blockY},${interaction.location.blockZ}")
        event.isCancelled = true
        breakSupport(interaction, event.damager as? Player)
    }

    private fun breakSupport(interaction: Interaction, player: Player?) {
        if (!interaction.isValid) {
            debugLog("breakSupport: interaction already invalid")
            return
        }
        val pdc = interaction.persistentDataContainer
        val cropType = pdc.get(ContentPdcKeys.cropsCropType, PersistentDataType.STRING)
        val stage = pdc.get(ContentPdcKeys.cropsStage, PersistentDataType.INTEGER)
        debugLog("breakSupport: 実行 player=${player?.name} cropType=$cropType stage=$stage at=${interaction.location.blockX},${interaction.location.blockY},${interaction.location.blockZ}")
        findDisplay(pdc, ContentPdcKeys.cropsSupportDisplay)?.remove()
        findDisplay(pdc, ContentPdcKeys.cropsCropDisplay)?.remove()
        val loc = interaction.location.clone()
        interaction.remove()
        debugLog("breakSupport: エンティティ除去完了")
        if (player != null && player.gameMode != GameMode.CREATIVE) {
            player.world.dropItemNaturally(loc, ItemStack(Material.STICK))
        }
        player?.sendMessage(localized(player, ContentCropsKeys.CROPS_SUPPORT_BREAK))
    }

    // ---- 成長 ticker（読込中のみ進行） ----

    private fun tickGrowth() {
        var grownCount = 0
        var totalPlanted = 0
        for (world in plugin.server.worlds) {
            for (entity in world.getEntitiesByClass(Interaction::class.java)) {
                val pdc = entity.persistentDataContainer
                if (!pdc.has(ContentPdcKeys.cropsSupport, PersistentDataType.BYTE)) continue
                // 下部ブロックが農地でなくなった孤児は自動除去（破壊イベントを取りこぼした場合の自己修復）
                val farmBlock = world.getBlockAt(entity.location.blockX, entity.location.blockY - 1, entity.location.blockZ)
                if (farmBlock.type != Material.FARMLAND && farmBlock.type != Material.SOUL_SAND) {
                    debugLog(
                        "tickGrowth: 孤児検知（下部ブロックが農地でなくなった） at=${entity.location.blockX},${entity.location.blockY},${entity.location.blockZ} " +
                            "farm=${farmBlock.type} -> 除去"
                    )
                    findDisplay(pdc, ContentPdcKeys.cropsSupportDisplay)?.remove()
                    findDisplay(pdc, ContentPdcKeys.cropsCropDisplay)?.remove()
                    entity.remove()
                    continue
                }
                val cropType = pdc.get(ContentPdcKeys.cropsCropType, PersistentDataType.STRING) ?: continue
                totalPlanted++
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
                    debugLog("tickGrowth: 成長 ${entity.location.blockX},${entity.location.blockY},${entity.location.blockZ} $stage -> $newStage (crop=$cropType)")
                    grownCount++
                } else {
                    pdc.set(ContentPdcKeys.cropsProgress, PersistentDataType.INTEGER, progress)
                }
            }
        }
        if (settings.debug && grownCount > 0) {
            plugin.logger.info("[Crops][Debug] tickGrowth: $grownCount 件が成長（作付け総数 $totalPlanted）")
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

    private fun debugLog(message: String) {
        if (!::settings.isInitialized || !settings.debug) return
        plugin.logger.info("[Crops][Debug] $message")
    }
}
