package jp.awabi2048.cccontent.arena.item

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import jp.awabi2048.cccontent.arena.config.ArenaDataFile
import jp.awabi2048.cccontent.CCContent
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemFlag
import io.papermc.paper.datacomponent.item.CustomModelData
import io.papermc.paper.datacomponent.DataComponentTypes

/**
 * アリーナで使用されるアイテムの管理を行うオブジェクト。
 * KotaArenaのItemManagerをCC-Content用に移植。
 * アイテムの作成、判定、属性操作などの機能を提供する。
 */
object ArenaItemManager {
    val ARENA_ITEM_KEY = NamespacedKey(CCContent.instance, "arena_item_id")
    val BOTTLE_TYPE_KEY = NamespacedKey(CCContent.instance, "bottle_type")
    val SOUL_CHARGE_KEY = NamespacedKey(CCContent.instance, "soul_charge")
    val GATE_KEY_DURABILITY_KEY = NamespacedKey(CCContent.instance, "gate_key_durability")
    val GATE_KEY_MAX_DURABILITY_KEY = NamespacedKey(CCContent.instance, "gate_key_max_durability")
    val SACK_CONTENTS_KEY = NamespacedKey(CCContent.instance, "sack_contents")
    
    private val gson = Gson()
    private val sackMapType = object : TypeToken<MutableMap<String, Int>>() {}.type

    /**
     * 古いアイテムを新しい形式に更新する。
     */
    fun updateItem(item: ItemStack): ItemStack {
        val id = getArenaItemId(item) ?: return item
        
        // すでに最新（WOODEN_PICKAXE 且つ PDCあり）なら何もしない
        if (item.type == Material.WOODEN_PICKAXE && 
            item.itemMeta?.persistentDataContainer?.has(ARENA_ITEM_KEY, PersistentDataType.STRING) == true) {
            return item
        }
        
        val amount = item.amount
        val newItem = when (id) {
            "ticket_easy", "ticket_normal" -> getItem(ArenaItem.TICKET_NORMAL)
            "ticket_hard", "ticket_extreme" -> getItem(ArenaItem.TICKET_BOSS)
            "ticket_quick" -> getItem(ArenaItem.TICKET_QUICK)
            "soul_fragment" -> getItem(ArenaItem.SOUL_FRAGMENT)
            "quest_ticket" -> getItem(ArenaItem.QUEST_TICKET)
            "booster_50" -> getItem(ArenaItem.BOOSTER_50)
            "booster_100" -> getItem(ArenaItem.BOOSTER_100)
            "booster_150" -> getItem(ArenaItem.BOOSTER_150)
            "gate_key_20" -> getItem(ArenaItem.ARENA_GATE_KEY_20)
            "gate_key_40" -> getItem(ArenaItem.ARENA_GATE_KEY_40)
            "gate_key_60" -> getItem(ArenaItem.ARENA_GATE_KEY_60)
            "mob_drop_sack" -> getItem(ArenaItem.MOB_DROP_SACK)
            "talisman_hunter" -> getItem(ArenaItem.TALISMAN_HUNTER)
            "talisman_golem" -> getItem(ArenaItem.TALISMAN_GOLEM)
            "empty_soul_bottle" -> {
                val charge = getSoulBottleCharge(item)
                val bottle = createEmptySoulBottle()
                addSoulCharge(bottle, charge)
            }
            "filled_soul_bottle" -> createFilledSoulBottle()
            "entrance_ticket" -> getEntranceTicket()
            else -> return item
        }
        newItem.amount = amount
        return newItem
    }
    
    enum class ArenaItem {
        TICKET_NORMAL,
        TICKET_BOSS,
        TICKET_QUICK,
        SOUL_FRAGMENT,
        QUEST_TICKET,
        EMPTY_SOUL_BOTTLE,
        FILLED_SOUL_BOTTLE,
        BOOSTER_50,
        BOOSTER_100,
        BOOSTER_150,
        ARENA_GATE_KEY_20,
        ARENA_GATE_KEY_40,
        ARENA_GATE_KEY_60,
        MOB_DROP_SACK,
        TALISMAN_HUNTER,
        TALISMAN_GOLEM
    }

    val ALL_ITEM_IDS = listOf(
        "ticket_normal", "ticket_boss", "ticket_quick",
        "soul_fragment", "quest_ticket", "empty_bottle", "filled_bottle",
        "booster_50", "booster_100", "booster_150",
        "gate_key_20", "gate_key_40", "gate_key_60",
        "mob_drop_sack", "talisman_hunter", "talisman_golem", "entrance_ticket"
    )

    /** 魂の瓶の最大チャージ量 */
    const val MAX_SOUL_CHARGE = 100

    /**
     * 指定された種類のアリーナアイテムを取得する。
     *
     * @param item アイテムの種類
     * @return 作成されたItemStack
     * @throws IllegalArgumentException 未対応のアイテムタイプの場合
     */
    fun getItem(item: ArenaItem): ItemStack {
        // 対応アイテムの検証（元のロジックを簡略化）
        if (item !in listOf(
            ArenaItem.TICKET_NORMAL,
            ArenaItem.TICKET_BOSS,
            ArenaItem.TICKET_QUICK,
            ArenaItem.SOUL_FRAGMENT,
            ArenaItem.QUEST_TICKET,
            ArenaItem.BOOSTER_50,
            ArenaItem.BOOSTER_100,
            ArenaItem.BOOSTER_150,
            ArenaItem.ARENA_GATE_KEY_20,
            ArenaItem.ARENA_GATE_KEY_40,
            ArenaItem.ARENA_GATE_KEY_60,
            ArenaItem.MOB_DROP_SACK,
            ArenaItem.TALISMAN_HUNTER,
            ArenaItem.TALISMAN_GOLEM
        )) {
            throw IllegalArgumentException("未対応のアイテムタイプです: $item")
        }

        val itemStack = ItemStack(Material.WOODEN_PICKAXE)
        val meta = itemStack.itemMeta ?: return itemStack

        val (name, id) = when (item) {
            ArenaItem.TICKET_NORMAL -> "§#00ff7fアリーナチケット" to "ticket_normal"
            ArenaItem.TICKET_BOSS -> "§#dc143c上級アリーナチケット" to "ticket_boss"
            ArenaItem.TICKET_QUICK -> "§dクイックアリーナチケット" to "ticket_quick"
            ArenaItem.SOUL_FRAGMENT -> "§bソウルフラグメント" to "soul_fragment"
            ArenaItem.QUEST_TICKET -> "§bアリーナクエスト手形" to "quest_ticket"
            ArenaItem.BOOSTER_50 -> "§b「アリーナ報酬ブースター」" to "booster_50"
            ArenaItem.BOOSTER_100 -> "§d「アリーナ報酬ブースター」" to "booster_100"
            ArenaItem.BOOSTER_150 -> "§d§n「アリーナ報酬ブースター」" to "booster_150"
            ArenaItem.ARENA_GATE_KEY_20 -> "§cぼろぼろのアリーナゲートの鍵" to "gate_key_20"
            ArenaItem.ARENA_GATE_KEY_40 -> "§c錆びついたアリーナゲートの鍵" to "gate_key_40"
            ArenaItem.ARENA_GATE_KEY_60 -> "§c欠けたアリーナゲートの鍵" to "gate_key_60"
            ArenaItem.MOB_DROP_SACK -> "§bモブドロップの入れ物" to "mob_drop_sack"
            ArenaItem.TALISMAN_HUNTER -> "§b狩人のお守り" to "talisman_hunter"
            ArenaItem.TALISMAN_GOLEM -> "§bゴーレムのお守り" to "talisman_golem"
            else -> "" to ""
        }

        // TODO: CCContentの言語システムを使用して翻訳を取得
        meta.displayName(LegacyComponentSerializer.legacySection().deserialize(name).decoration(TextDecoration.ITALIC, false))
        
        // PDC設定
        meta.persistentDataContainer.set(ARENA_ITEM_KEY, PersistentDataType.STRING, id)

        // TODO: item_model設定 - CCContentのリソースパックに合わせて調整
        var cmdString: String? = null
        when (item) {
            ArenaItem.TICKET_NORMAL, ArenaItem.TICKET_BOSS, ArenaItem.TICKET_QUICK, ArenaItem.QUEST_TICKET -> {
                // チケット系
                // meta.setItemModel(NamespacedKey.fromString("cccontent:arena_ticket"))
                cmdString = ArenaDataFile.items.getString("custom_model_data.$id", id)
            }
            ArenaItem.SOUL_FRAGMENT -> {
                // ソウルフラグメント
                // meta.setItemModel(NamespacedKey.fromString("cccontent:arena_entrance_token"))
                cmdString = ArenaDataFile.items.getString("custom_model_data.$id", "soul_fragment")
            }
            ArenaItem.BOOSTER_50, ArenaItem.BOOSTER_100, ArenaItem.BOOSTER_150 -> {
                // ブースター
                // meta.setItemModel(NamespacedKey.fromString("cccontent:arena_misc"))
                cmdString = ArenaDataFile.items.getString("custom_model_data.$id", "arena_$id")
            }
            ArenaItem.ARENA_GATE_KEY_20, ArenaItem.ARENA_GATE_KEY_40, ArenaItem.ARENA_GATE_KEY_60 -> {
                // ゲートキー
                // meta.setItemModel(NamespacedKey.fromString("cccontent:arena_misc"))
                val variation = id.substringAfter("gate_key_")
                cmdString = ArenaDataFile.items.getString("custom_model_data.$id", "arena_gate_key_$variation")
            }
            ArenaItem.MOB_DROP_SACK -> {
                // meta.setItemModel(NamespacedKey.fromString("cccontent:arena_misc"))
                cmdString = ArenaDataFile.items.getString("custom_model_data.mob_drop_sack", "arena_mob_drop_sack")
            }
            ArenaItem.TALISMAN_HUNTER -> {
                // meta.setItemModel(NamespacedKey.fromString("cccontent:arena_misc"))
                cmdString = ArenaDataFile.items.getString("custom_model_data.talisman_hunter", "arena_talisman_hunter")
            }
            ArenaItem.TALISMAN_GOLEM -> {
                // meta.setItemModel(NamespacedKey.fromString("cccontent:arena_misc"))
                cmdString = ArenaDataFile.items.getString("custom_model_data.talisman_golem", "arena_talisman_golem")
            }
            else -> {}
        }

        // 属性・ツールチップ非表示
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
        meta.setAttributeModifiers(null)
        
        // スタックサイズ設定
        meta.setMaxStackSize(64)
        
        // TODO: 各アイテム固有のLore設定を移植
        // 簡易的な実装として空のLoreを設定
        when (item) {
            ArenaItem.QUEST_TICKET -> {
                meta.lore(listOf("§7クエスト達成の証。", "§7集めると良いことがあるかも...？").map { 
                    LegacyComponentSerializer.legacySection().deserialize(it).decoration(TextDecoration.ITALIC, false) 
                })
            }
            ArenaItem.SOUL_FRAGMENT -> {
                meta.lore(listOf("§7アリーナへ入場するための捧げ物。", "§7強力な魂の欠片。").map { 
                    LegacyComponentSerializer.legacySection().deserialize(it).decoration(TextDecoration.ITALIC, false) 
                })
            }
            // TODO: 他のアイテムのLoreも追加
            else -> {}
        }

        // 発光エフェクトを追加（特定のアイテム）
        if (item in listOf(
            ArenaItem.TICKET_BOSS, ArenaItem.TICKET_QUICK, ArenaItem.QUEST_TICKET, ArenaItem.SOUL_FRAGMENT,
            ArenaItem.BOOSTER_50, ArenaItem.BOOSTER_100, ArenaItem.BOOSTER_150,
            ArenaItem.ARENA_GATE_KEY_20, ArenaItem.ARENA_GATE_KEY_40, ArenaItem.ARENA_GATE_KEY_60,
            ArenaItem.MOB_DROP_SACK, ArenaItem.TALISMAN_HUNTER, ArenaItem.TALISMAN_GOLEM
        )) {
            meta.setEnchantmentGlintOverride(true)
        }

        itemStack.itemMeta = meta
        
        // CustomModelData設定 - Paper APIの実装はフェーズ3で
        // if (cmdString != null) {
        //     try {
        //         val customModelDataValue = cmdString?.toIntOrNull() ?: 0
        //         if (customModelDataValue > 0) {
        //             item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, 
        //                 CustomModelData.of(customModelDataValue)
        //             )
        //         }
        //     } catch (e: Exception) {
        //         // エラー時は無視
        //     }
        // }
        // 
        // // tool, attribute_modifiersの削除
        // itemStack.unsetData(DataComponentTypes.TOOL)
        // itemStack.unsetData(DataComponentTypes.ATTRIBUTE_MODIFIERS)
        
        // スタックサイズ設定がPDCだけでは不十分な場合があるので直にたたく
        if (item in listOf(
            ArenaItem.ARENA_GATE_KEY_20, ArenaItem.ARENA_GATE_KEY_40, ArenaItem.ARENA_GATE_KEY_60,
            ArenaItem.MOB_DROP_SACK, ArenaItem.TALISMAN_HUNTER, ArenaItem.TALISMAN_GOLEM
        )) {
            val gateMeta = itemStack.itemMeta
            gateMeta?.setMaxStackSize(1)
            itemStack.itemMeta = gateMeta
        }
        
        return itemStack
    }

    // TODO: 以下のメソッドを移植
    // createEmptySoulBottle()
    // createFilledSoulBottle()
    // getEntranceTicket()
    // getSoulBottleCharge()
    // addSoulCharge()
    // getSackContents()
    // updateSackContents()
    // isAllowedInSack()
    // updateGateKeyDurability()
    // isSoulFragment()
    // isEmptySoulBottle()
    // isFilledSoulBottle()
    // isArenaGateKey()
    // isMobDropSack()
    // isTalisman()
    // getArenaItemId()
    // getItemById()
    
    /**
     * ソウルフラグメントを取得する。
     */
    fun getSoulFragment(): ItemStack = getItem(ArenaItem.SOUL_FRAGMENT)

    /**
     * アイテムがソウルフラグメントかどうか判定する。
     */
    fun isSoulFragment(item: ItemStack?): Boolean {
        if (item == null) return false
        val meta = item.itemMeta ?: return false
        
        // PDC優先
        if (meta.persistentDataContainer.get(ARENA_ITEM_KEY, PersistentDataType.STRING) == "soul_fragment") return true
        
        // 旧判定 (フォールバック)
        if (item.type != Material.GHAST_TEAR) return false
        val displayName = meta.displayName() ?: return false
        val serializedName = LegacyComponentSerializer.legacySection().serialize(displayName)
        return serializedName == "§bソウルフラグメント"
    }

    /**
     * 空の魔法の瓶を作成する。
     */
    fun createEmptySoulBottle(): ItemStack {
        // TODO: 完全な実装を移植
        val item = ItemStack(Material.WOODEN_PICKAXE)
        val meta = item.itemMeta ?: return item
        
        meta.displayName(LegacyComponentSerializer.legacySection().deserialize("§d空の魔法の瓶").decoration(TextDecoration.ITALIC, false))
        
        val lore = listOf(
            "§8§m                    ",
            "§7モブを倒して魂を集めよう。",
            "§7満タンになると§b魂入りの瓶§7に変化する。",
            "§8§m                    ",
            "",
            "§7チャージ: §e0§7/§e$MAX_SOUL_CHARGE"
        )
        meta.lore(lore.map { LegacyComponentSerializer.legacySection().deserialize(it).decoration(TextDecoration.ITALIC, false) })
        
        // PDC
        meta.persistentDataContainer.set(ARENA_ITEM_KEY, PersistentDataType.STRING, "empty_soul_bottle")
        meta.persistentDataContainer.set(SOUL_CHARGE_KEY, PersistentDataType.INTEGER, 0)
        meta.persistentDataContainer.set(BOTTLE_TYPE_KEY, PersistentDataType.STRING, "empty_soul_bottle")

        // 属性・ツールチップ非表示
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
        meta.setAttributeModifiers(null)

        // スタックサイズ設定
        meta.setMaxStackSize(1)
        
        item.itemMeta = meta
        
        // CustomModelData設定 - Paper APIの実装はフェーズ3で
        // val cmdString = ArenaDataFile.items.getString("custom_model_data.empty_soul_bottle", "empty_soul_bottle")
        // try {
        //     val customModelDataValue = cmdString?.toIntOrNull() ?: 0
        //     if (customModelDataValue > 0) {
        //         item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, 
        //             CustomModelData.of(customModelDataValue)
        //         )
        //     }
        // } catch (e: Exception) {
        //     // エラー時は無視
        // }
        // 
        // // tool, attribute_modifiersの削除
        // item.unsetData(DataComponentTypes.TOOL)
        // item.unsetData(DataComponentTypes.ATTRIBUTE_MODIFIERS)
        
        return item
    }

    /**
     * 魂入りの瓶を作成する。
     */
    fun createFilledSoulBottle(): ItemStack {
        // TODO: 完全な実装を移植
        val item = ItemStack(Material.WOODEN_PICKAXE)
        val meta = item.itemMeta ?: return item
        
        meta.displayName(LegacyComponentSerializer.legacySection().deserialize("§b魂入りの瓶").decoration(TextDecoration.ITALIC, false))
        
        val lore = listOf(
            "§8§m                    ",
            "§7モブの魂が詰まった瓶。",
            "§7アリーナ入場時に§6捧げ物§7として使用できる。",
            "§8§m                    "
        )
        meta.lore(lore.map { LegacyComponentSerializer.legacySection().deserialize(it).decoration(TextDecoration.ITALIC, false) })
        
        meta.setEnchantmentGlintOverride(true)
        
        // PDC
        meta.persistentDataContainer.set(ARENA_ITEM_KEY, PersistentDataType.STRING, "filled_soul_bottle")
        meta.persistentDataContainer.set(BOTTLE_TYPE_KEY, PersistentDataType.STRING, "filled_soul_bottle")

        // 属性・ツールチップ非表示
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
        meta.setAttributeModifiers(null)
        
        // スタックサイズ設定
        meta.setMaxStackSize(1)
        
        item.itemMeta = meta
        
        // CustomModelData設定 - Paper APIの実装はフェーズ3で
        // val cmdString = ArenaDataFile.items.getString("custom_model_data.filled_soul_bottle", "filled_soul_bottle")
        // try {
        //     val customModelDataValue = cmdString?.toIntOrNull() ?: 0
        //     if (customModelDataValue > 0) {
        //         item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, 
        //             CustomModelData.customModelData().addInt(customModelDataValue).build()
        //         )
        //     }
        // } catch (e: Exception) {
        //     // エラー時は無視
        // }
        // 
        // // tool, attribute_modifiersの削除
        // item.unsetData(DataComponentTypes.TOOL)
        // item.unsetData(DataComponentTypes.ATTRIBUTE_MODIFIERS)
        
        return item
    }

    /**
     * アリーナ入場チケットを取得する。
     */
    fun getEntranceTicket(): ItemStack {
        // TODO: 完全な実装を移植
        val item = ItemStack(Material.WOODEN_PICKAXE)
        val meta = item.itemMeta ?: return item

        meta.displayName(LegacyComponentSerializer.legacySection().deserialize("§6アリーナ入場チケット").decoration(TextDecoration.ITALIC, false))
        meta.lore(
            listOf(
                "§8§m                    ",
                "§7アリーナに入場するために必要なチケット",
                "§7このチケットを消費してアリーナに挑戦できる。",
                "§8§m                    "
            ).map { LegacyComponentSerializer.legacySection().deserialize(it).decoration(TextDecoration.ITALIC, false) }
        )
        meta.setEnchantmentGlintOverride(true)

        // PDC
        meta.persistentDataContainer.set(ARENA_ITEM_KEY, PersistentDataType.STRING, "entrance_ticket")

        // 属性・ツールチップ非表示
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
        meta.setAttributeModifiers(null)

        // スタックサイズ設定
        meta.setMaxStackSize(64)
        
        item.itemMeta = meta
        
        // CustomModelData設定 - Paper APIの実装はフェーズ3で
        // val cmdString = ArenaDataFile.items.getString("custom_model_data.entrance_ticket", "entrance_ticket")
        // try {
        //     val customModelDataValue = cmdString?.toIntOrNull() ?: 0
        //     if (customModelDataValue > 0) {
        //         item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, 
        //             CustomModelData.of(customModelDataValue)
        //         )
        //     }
        // } catch (e: Exception) {
        //     // エラー時は無視
        // }
        // 
        // // tool, attribute_modifiersの削除
        // item.unsetData(DataComponentTypes.TOOL)
        // item.unsetData(DataComponentTypes.ATTRIBUTE_MODIFIERS)
        
        return item
    }

    /**
     * アイテムが空の魔法の瓶かどうか判定する。
     */
    fun isEmptySoulBottle(item: ItemStack?): Boolean {
        if (item == null) return false
        val meta = item.itemMeta ?: return false
        
        // PDC優先
        if (meta.persistentDataContainer.get(ARENA_ITEM_KEY, PersistentDataType.STRING) == "empty_soul_bottle") return true
        
        // 旧判定
        return false
    }
    
    fun isFilledSoulBottle(item: ItemStack?): Boolean {
        if (item == null) return false
        val meta = item.itemMeta ?: return false

        // PDC優先
        if (meta.persistentDataContainer.get(ARENA_ITEM_KEY, PersistentDataType.STRING) == "filled_soul_bottle") return true

        // 旧判定
        return false
    }

    fun isArenaGateKey(item: ItemStack?): Boolean {
        if (item == null) return false
        val meta = item.itemMeta ?: return false
        val id = meta.persistentDataContainer.get(ARENA_ITEM_KEY, PersistentDataType.STRING) ?: return false
        return id.startsWith("gate_key_")
    }

    fun isMobDropSack(item: ItemStack?): Boolean {
        if (item == null) return false
        val meta = item.itemMeta ?: return false
        val id = meta.persistentDataContainer.get(ARENA_ITEM_KEY, PersistentDataType.STRING) ?: return false
        return id == "mob_drop_sack"
    }

    fun isTalisman(item: ItemStack?): Boolean {
        if (item == null) return false
        val meta = item.itemMeta ?: return false
        val id = meta.persistentDataContainer.get(ARENA_ITEM_KEY, PersistentDataType.STRING) ?: return false
        return id.startsWith("talisman_")
    }
    
    /**
     * 空の魔法の瓶の現在のチャージ量を取得する。
     */
    fun getSoulBottleCharge(item: ItemStack): Int {
        if (!isEmptySoulBottle(item)) return 0
        val meta = item.itemMeta ?: return 0
        return meta.persistentDataContainer.get(SOUL_CHARGE_KEY, PersistentDataType.INTEGER) ?: 0
    }
    
    /**
     * 空の魔法の瓶にチャージを追加し、満タンなら魂入りの瓶に変換する。
     *
     * @param item 対象のアイテム
     * @param amount 追加するチャージ量
     * @return 変換された場合は魂入りの瓶、そうでなければ更新された空の瓶
     */
    fun addSoulCharge(item: ItemStack, amount: Int): ItemStack {
        if (!isEmptySoulBottle(item)) return item
        
        val currentCharge = getSoulBottleCharge(item)
        val newCharge = currentCharge + amount
        
        // 満タンになったら魂入りの瓶に変換
        if (newCharge >= MAX_SOUL_CHARGE) {
            return createFilledSoulBottle()
        }
        
        // チャージ量を更新
        val meta = item.itemMeta ?: return item
        meta.persistentDataContainer.set(SOUL_CHARGE_KEY, PersistentDataType.INTEGER, newCharge)
        
        // Loreを更新
        val lore = listOf(
            "§8§m                    ",
            "§7モブを倒して魂を集めよう。",
            "§7満タンになると§b魂入りの瓶§7に変化する。",
            "§8§m                    ",
            "",
            "§7チャージ: §e$newCharge§7/§e$MAX_SOUL_CHARGE"
        )
        meta.lore(lore.map { LegacyComponentSerializer.legacySection().deserialize(it).decoration(TextDecoration.ITALIC, false) })
        
        item.itemMeta = meta
        return item
    }

    /**
     * IDからアイテムを取得する。
     */
    fun getItemById(id: String): ItemStack? {
        return try {
            when (id.lowercase()) {
                "ticket_normal" -> getItem(ArenaItem.TICKET_NORMAL)
                "ticket_boss" -> getItem(ArenaItem.TICKET_BOSS)
                "ticket_quick" -> getItem(ArenaItem.TICKET_QUICK)
                "soul_fragment" -> getItem(ArenaItem.SOUL_FRAGMENT)
                "quest_ticket" -> getItem(ArenaItem.QUEST_TICKET)
                "empty_bottle" -> createEmptySoulBottle()
                "filled_bottle" -> createFilledSoulBottle()
                "booster_50" -> getItem(ArenaItem.BOOSTER_50)
                "booster_100" -> getItem(ArenaItem.BOOSTER_100)
                "booster_150" -> getItem(ArenaItem.BOOSTER_150)
                "gate_key_20" -> getItem(ArenaItem.ARENA_GATE_KEY_20)
                "gate_key_40" -> getItem(ArenaItem.ARENA_GATE_KEY_40)
                "gate_key_60" -> getItem(ArenaItem.ARENA_GATE_KEY_60)
                "mob_drop_sack" -> getItem(ArenaItem.MOB_DROP_SACK)
                "talisman_hunter" -> getItem(ArenaItem.TALISMAN_HUNTER)
                "talisman_golem" -> getItem(ArenaItem.TALISMAN_GOLEM)
                "entrance_ticket" -> getEntranceTicket()
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 個別の判定ロジック。
     */
    fun getArenaItemId(item: ItemStack?): String? {
        if (item == null) return null
        val meta = item.itemMeta ?: return null
        
        // PDCから取得
        val pdcId = meta.persistentDataContainer.get(ARENA_ITEM_KEY, PersistentDataType.STRING)
        if (pdcId != null) return pdcId
        
        // 従来の判定
        if (isSoulFragment(item)) return "soul_fragment"
        if (isEmptySoulBottle(item)) return "empty_soul_bottle"
        if (isFilledSoulBottle(item)) return "filled_soul_bottle"
        if (isEntranceTicket(item)) return "entrance_ticket"
        if (isArenaGateKey(item)) {
            // For old items that might not have PDC but match the name pattern
            val displayName = meta.displayName() ?: return null
            val name = LegacyComponentSerializer.legacySection().serialize(displayName)
            return when {
                name.contains("§cぼろぼろのアリーナゲートの鍵") -> "gate_key_20"
                name.contains("§c錆びついたアリーナゲートの鍵") -> "gate_key_40"
                name.contains("§c欠けたアリーナゲートの鍵") -> "gate_key_60"
                else -> null
            }
        }
        if (isMobDropSack(item)) return "mob_drop_sack"
        if (isTalisman(item)) {
            val id = meta.persistentDataContainer.get(ARENA_ITEM_KEY, PersistentDataType.STRING)
            if (id != null) return id
        }
        
        // チケット系 (名前で判定)
        val displayName = meta.displayName() ?: return null
        val name = LegacyComponentSerializer.legacySection().serialize(displayName)
        return when {
            name.contains("§a【初級】") -> "ticket_easy"
            name.contains("§e【中級】") -> "ticket_normal"
            name.contains("§c【上級】") -> "ticket_hard"
            name.contains("§d【最上級】") -> "ticket_extreme"
            name.contains("§dクイックアリーナチケット") -> "ticket_quick"
            name.contains("§bアリーナクエスト手形") -> "quest_ticket"
            name.contains("§b「アリーナ報酬ブースター」") -> "booster_50"
            name.contains("§d「アリーナ報酬ブースター」") -> "booster_100"
            name.contains("§d§n「アリーナ報酬ブースター」") -> "booster_150"
            name.contains("§cぼろぼろのアリーナゲートの鍵") -> "gate_key_20"
            name.contains("§c錆びついたアリーナゲートの鍵") -> "gate_key_40"
            name.contains("§c欠けたアリーナゲートの鍵") -> "gate_key_60"
            name.contains("§bモブドロップの入れ物") -> "mob_drop_sack"
            name.contains("§b狩人のお守り") -> "talisman_hunter"
            name.contains("§bゴーレムのお守り") -> "talisman_golem"
            else -> null
        }
    }

    /**
     * アイテムがアリーナ入場チケットかどうか判定する。
     */
    fun isEntranceTicket(item: ItemStack?): Boolean {
        if (item == null) return false
        val meta = item.itemMeta ?: return false

        // PDC優先
        if (meta.persistentDataContainer.get(ARENA_ITEM_KEY, PersistentDataType.STRING) == "entrance_ticket") return true

        // 旧判定
        return false
    }

    /**
     * モブドロップサックの内容を取得する。
     */
    fun getSackContents(item: ItemStack): MutableMap<String, Int> {
        if (!isMobDropSack(item)) return mutableMapOf()
        val meta = item.itemMeta ?: return mutableMapOf()
        val json = meta.persistentDataContainer.get(SACK_CONTENTS_KEY, PersistentDataType.STRING) ?: return mutableMapOf()
        return try {
            gson.fromJson(json, sackMapType)
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    /**
     * モブドロップサックの内容を更新する。
     */
    fun updateSackContents(item: ItemStack, contents: Map<String, Int>): ItemStack {
        if (!isMobDropSack(item)) return item
        val meta = item.itemMeta ?: return item
        val json = gson.toJson(contents)
        meta.persistentDataContainer.set(SACK_CONTENTS_KEY, PersistentDataType.STRING, json)
        item.itemMeta = meta
        return item
    }

    /**
     * アイテムがサックに許可されているかどうかを判定する。
     */
    fun isAllowedInSack(material: org.bukkit.Material): Boolean {
        val allowed = ArenaDataFile.items.getStringList("mob_drop_sack.allowed_items")
        return allowed.contains(material.name)
    }

    /**
     * ゲートキーの耐久値を更新する。
     */
    fun updateGateKeyDurability(item: ItemStack, durability: Int): ItemStack {
        if (!isArenaGateKey(item)) return item
        val meta = item.itemMeta ?: return item
        meta.persistentDataContainer.set(GATE_KEY_DURABILITY_KEY, PersistentDataType.INTEGER, durability)
        item.itemMeta = meta
        return item
    }

    /**
     * ゲートキーの現在の耐久値を取得する。
     */
    fun getGateKeyDurability(item: ItemStack): Int {
        if (!isArenaGateKey(item)) return 0
        val meta = item.itemMeta ?: return 0
        return meta.persistentDataContainer.get(GATE_KEY_DURABILITY_KEY, PersistentDataType.INTEGER) ?: 0
    }

    /**
     * ゲートキーの最大耐久値を取得する。
     */
    fun getGateKeyMaxDurability(item: ItemStack): Int {
        if (!isArenaGateKey(item)) return 0
        val meta = item.itemMeta ?: return 0
        return meta.persistentDataContainer.get(GATE_KEY_MAX_DURABILITY_KEY, PersistentDataType.INTEGER) ?: when {
            getArenaItemId(item) == "gate_key_20" -> 20
            getArenaItemId(item) == "gate_key_40" -> 40
            getArenaItemId(item) == "gate_key_60" -> 60
            else -> 0
        }
    }
}