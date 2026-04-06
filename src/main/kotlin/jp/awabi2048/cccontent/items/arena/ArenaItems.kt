package jp.awabi2048.cccontent.items.arena

import jp.awabi2048.cccontent.items.CustomItem
import jp.awabi2048.cccontent.items.CustomItemI18n
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import java.util.Locale

private val arenaItemKey by lazy {
    NamespacedKey(Bukkit.getPluginManager().getPlugin("CC-Content")!!, "arena_item")
}

abstract class ArenaSimpleItem(
    private val material: Material,
    private val modelData: Int?,
    final override val id: String,
    final override val displayName: String,
    final override val lore: List<String>
) : CustomItem {
    final override val feature: String = "arena"

    final override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)

    override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
        val item = ItemStack(material, amount.coerceAtLeast(1))
        val meta = item.itemMeta ?: return item
        val name = CustomItemI18n.text(player, "custom_items.$feature.$id.name", displayName)
        val localizedLore = CustomItemI18n.list(player, "custom_items.$feature.$id.lore", lore)
        meta.displayName(Component.text(name))
        meta.lore(localizedLore.map { Component.text(it) })
        modelData?.let { meta.setCustomModelData(it) }
        meta.persistentDataContainer.set(arenaItemKey, PersistentDataType.STRING, id)
        item.itemMeta = meta
        return item
    }

    final override fun matches(item: ItemStack): Boolean {
        val meta = item.itemMeta ?: return false
        val raw = meta.persistentDataContainer.get(arenaItemKey, PersistentDataType.STRING) ?: return false
        return raw == id
    }
}

class ArenaTicketItem : ArenaSimpleItem(
    material = Material.PAPER,
    modelData = 4001,
    id = "ticket",
    displayName = "§6アリーナチケット",
    lore = listOf("§7アリーナへの参加チケット")
)

class ArenaMedalItem : ArenaSimpleItem(
    material = Material.SUNFLOWER,
    modelData = 4002,
    id = "medal",
    displayName = "§6アリーナメダル",
    lore = listOf("§7アリーナでの戦いの証")
)

class ArenaPrizeItem : ArenaSimpleItem(
    material = Material.CHEST,
    modelData = 4003,
    id = "prize",
    displayName = "§6アリーナ報酬箱",
    lore = listOf("§7アリーナでの勝利報酬")
)

class ArenaDecayedEquipmentItem(
    private val kindId: String,
    kindLabel: String,
    modelKey: String
) : ArenaSimpleItem(
    material = Material.POISONOUS_POTATO,
    modelData = null,
    id = "decayed_$kindId",
    displayName = "§7朽ちた$kindLabel",
    lore = listOf("§8かつての力を失った残骸")
) {
    override val itemModel: NamespacedKey = NamespacedKey.minecraft(modelKey)
}

class ArenaMobTokenItem(private val mobTypeId: String = "zombie") : ArenaSimpleItem(
    material = Material.POISONOUS_POTATO,
    modelData = null,
    id = "mob_token",
    displayName = "§6${mobTypeId.lowercase(Locale.ROOT)}の頭",
    lore = listOf("§7アリーナに出現するモンスターのヘッド", "§7アリーナロビーで報酬と交換しよう！")
) {
    override val itemModel: NamespacedKey = resolveItemModel(mobTypeId)

    override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
        val item = ItemStack(Material.POISONOUS_POTATO, amount.coerceAtLeast(1))
        val meta = item.itemMeta ?: return item
        val normalizedTypeId = resolveTokenCategoryTypeId(mobTypeId)
        val tokenName = CustomItemI18n.text(
            player,
            "custom_items.arena.mob_token.token_names.$normalizedTypeId",
            normalizedTypeId
        )
        val localizedLore = CustomItemI18n.list(
            player,
            "custom_items.arena.mob_token.lore",
            listOf("§7アリーナに出現するモンスターが落としたアイテム", "§7アリーナロビーで報酬と交換しよう！")
        )

        meta.displayName(Component.text("§6$tokenName"))
        meta.lore(localizedLore.map { Component.text(it) })
        meta.setItemModel(itemModel)
        meta.persistentDataContainer.set(arenaItemKey, PersistentDataType.STRING, id)
        item.itemMeta = meta
        return item
    }

    companion object {
        private fun sanitizeMobTypeId(typeId: String): String {
            return typeId.trim().lowercase(Locale.ROOT).replace(Regex("[^a-z0-9_]+"), "_")
        }

        fun resolveTokenCategoryTypeId(typeId: String): String {
            val normalized = sanitizeMobTypeId(typeId)
            return when (normalized) {
                "cave_spider" -> "spider"
                "ashen_spirit", "water_spirit", "vex" -> "spirit"
                else -> normalized
            }
        }

        fun supportsTokenCategoryTypeId(typeId: String): Boolean {
            return SUPPORTED_TOKEN_CATEGORY_TYPE_IDS.contains(resolveTokenCategoryTypeId(typeId))
        }

        fun supportedTokenCategoryTypeIds(): Set<String> = SUPPORTED_TOKEN_CATEGORY_TYPE_IDS

        private fun resolveItemModel(typeId: String): NamespacedKey {
            val normalized = resolveTokenCategoryTypeId(typeId)
            return when (normalized) {
                "skeleton" -> NamespacedKey.minecraft("skeleton_skull")
                "zombie" -> NamespacedKey.minecraft("zombie_head")
                "creeper" -> NamespacedKey.minecraft("creeper_head")
                "piglin" -> NamespacedKey.minecraft("piglin_head")
                "wither_skeleton" -> NamespacedKey.minecraft("charcoal")
                "ender_dragon" -> NamespacedKey.minecraft("dragon_head")
                "husk" -> NamespacedKey.minecraft("leather_chestplate")
                "iron_golem" -> NamespacedKey.minecraft("resin_clump")
                "guardian" -> NamespacedKey.minecraft("prismarine_shard")
                "elder_guardian" -> NamespacedKey.minecraft("tide_armor_trim_smithing_template")
                "drowned" -> NamespacedKey.minecraft("moss_block")
                "silverfish" -> NamespacedKey.minecraft("blue_egg")
                "spider" -> NamespacedKey.minecraft("disc_fragment_5")
                "blaze" -> NamespacedKey.minecraft("blaze_powder")
                "magma_cube" -> NamespacedKey.minecraft("fire_charge")
                "spirit" -> NamespacedKey.minecraft("ghast_tear")
                "witch" -> NamespacedKey.minecraft("leather_chestplate")
                "bat" -> NamespacedKey.minecraft("phantom_membrane")
                "bogged" -> NamespacedKey.minecraft("skeleton_skull")
                "stray" -> NamespacedKey.minecraft("skeleton_skull")
                "frog" -> NamespacedKey.minecraft("resin_clump")
                "slime" -> NamespacedKey.minecraft("emerald")
                else -> NamespacedKey.minecraft("poisonous_potato")
            }
        }

        private val SUPPORTED_TOKEN_CATEGORY_TYPE_IDS = setOf(
            "skeleton",
            "zombie",
            "creeper",
            "piglin",
            "wither_skeleton",
            "ender_dragon",
            "husk",
            "iron_golem",
            "guardian",
            "elder_guardian",
            "drowned",
            "silverfish",
            "spider",
            "witch",
            "blaze",
            "magma_cube",
            "slime",
            "bogged",
            "stray",
            "bat",
            "allay",
            "spirit",
            "frog"
        )
    }
}

class BoomerangTokenItem : ArenaSimpleItem(
    material = Material.POISONOUS_POTATO,
    modelData = null,
    id = "boomerang_token",
    displayName = "§6ボーンメラン",
    lore = listOf("§7アリーナに出現するモンスターが落としたアイテム", "§7アリーナロビーで報酬と交換しよう！")
)

enum class ArenaOverEnchanterMode(val id: String) {
    LIMIT_BREAKING("limit_breaking"),
    OVER_STACKING("over_stacking"),
    EXOTIC_ATTACH("exotic_attach");

    companion object {
        fun fromId(id: String): ArenaOverEnchanterMode? {
            return entries.firstOrNull { it.id == id }
        }
    }
}

object ArenaOverEnchanterCatalystData {
    data class Catalyst(
        val mode: ArenaOverEnchanterMode,
        val targetEnchantmentId: String,
        val overLevel: Int?
    )

    private val modeKey by lazy {
        NamespacedKey(Bukkit.getPluginManager().getPlugin("CC-Content")!!, "over_enchanter_mode")
    }

    private val targetEnchantmentKey by lazy {
        NamespacedKey(Bukkit.getPluginManager().getPlugin("CC-Content")!!, "over_enchanter_target_enchantment")
    }

    private val overLevelKey by lazy {
        NamespacedKey(Bukkit.getPluginManager().getPlugin("CC-Content")!!, "over_enchanter_over_level")
    }

    fun apply(meta: ItemMeta, catalyst: Catalyst) {
        meta.persistentDataContainer.set(modeKey, PersistentDataType.STRING, catalyst.mode.id)
        meta.persistentDataContainer.set(targetEnchantmentKey, PersistentDataType.STRING, catalyst.targetEnchantmentId)
        if (catalyst.overLevel != null) {
            meta.persistentDataContainer.set(overLevelKey, PersistentDataType.INTEGER, catalyst.overLevel)
        } else {
            meta.persistentDataContainer.remove(overLevelKey)
        }
    }

    fun read(item: ItemStack): Catalyst? {
        val meta = item.itemMeta ?: return null
        val modeId = meta.persistentDataContainer.get(modeKey, PersistentDataType.STRING) ?: return null
        val targetEnchantment = meta.persistentDataContainer.get(targetEnchantmentKey, PersistentDataType.STRING) ?: return null
        val mode = ArenaOverEnchanterMode.fromId(modeId) ?: return null
        val overLevel = meta.persistentDataContainer.get(overLevelKey, PersistentDataType.INTEGER)
        return Catalyst(mode = mode, targetEnchantmentId = targetEnchantment, overLevel = overLevel)
    }
}

class ArenaOverEnchanterCatalystItem(
    id: String,
    displayName: String,
    lore: List<String>,
    private val catalyst: ArenaOverEnchanterCatalystData.Catalyst,
    private val fixedDisplayName: String,
    private val fixedLore: List<String>,
    material: Material
) : ArenaSimpleItem(
    material = material,
    modelData = null,
    id = id,
    displayName = displayName,
    lore = lore
) {
    override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
        val item = super.createItemForPlayer(player, amount)
        val meta = item.itemMeta ?: return item
        item.amount = 1
        meta.setMaxStackSize(1)
        meta.displayName(Component.text(fixedDisplayName))
        meta.lore(fixedLore.map { Component.text(it) })
        ArenaOverEnchanterCatalystData.apply(meta, catalyst)
        item.itemMeta = meta
        return item
    }
}

private fun tierDecoration(tier: Int): String {
    val normalized = tier.coerceIn(1, 4)
    val active = "§d" + "❖".repeat(normalized)
    val inactive = "§7" + "❖".repeat(4 - normalized)
    return "§8【$active$inactive§8】"
}

private fun catalystDisplayName(enchantLabel: String, tier: Int): String {
    return "§b${enchantLabel}のかけら ${tierDecoration(tier)}"
}

fun arenaOverEnchanterCatalystItems(): List<ArenaOverEnchanterCatalystItem> {
    val limitBreaking = listOf(
        ArenaOverEnchanterCatalystItem(
            id = "limit_breaking_sharpness_t1",
            displayName = catalystDisplayName("ダメージ増加", 1),
            lore = listOf("§7限界突破用だよ"),
            catalyst = ArenaOverEnchanterCatalystData.Catalyst(ArenaOverEnchanterMode.LIMIT_BREAKING, "sharpness", 1),
            fixedDisplayName = catalystDisplayName("ダメージ増加", 1),
            fixedLore = listOf("§7限界突破用だよ"),
            material = Material.BLAZE_POWDER
        ),
        ArenaOverEnchanterCatalystItem(
            id = "limit_breaking_sharpness_t2",
            displayName = catalystDisplayName("ダメージ増加", 2),
            lore = listOf("§7限界突破用だよ"),
            catalyst = ArenaOverEnchanterCatalystData.Catalyst(ArenaOverEnchanterMode.LIMIT_BREAKING, "sharpness", 2),
            fixedDisplayName = catalystDisplayName("ダメージ増加", 2),
            fixedLore = listOf("§7限界突破用だよ"),
            material = Material.BLAZE_POWDER
        ),
        ArenaOverEnchanterCatalystItem(
            id = "limit_breaking_sharpness_t3",
            displayName = catalystDisplayName("ダメージ増加", 3),
            lore = listOf("§7限界突破用だよ"),
            catalyst = ArenaOverEnchanterCatalystData.Catalyst(ArenaOverEnchanterMode.LIMIT_BREAKING, "sharpness", 3),
            fixedDisplayName = catalystDisplayName("ダメージ増加", 3),
            fixedLore = listOf("§7限界突破用だよ"),
            material = Material.BLAZE_POWDER
        )
    )

    val overStackingTargets = listOf(
        "infinity" to "無限",
        "mending" to "修繕",
        "sharpness" to "ダメージ増加",
        "smite" to "アンデッド特攻",
        "bane_of_arthropods" to "虫特攻",
        "protection" to "防護",
        "fire_protection" to "火炎耐性",
        "blast_protection" to "爆発耐性",
        "projectile_protection" to "飛び道具耐性",
        "multishot" to "拡散",
        "piercing" to "貫通"
    ).map { (id, label) ->
        ArenaOverEnchanterCatalystItem(
            id = "over_stacking_$id",
            displayName = catalystDisplayName(label, 1),
            lore = listOf("§7競合エンチャント用だよ"),
            catalyst = ArenaOverEnchanterCatalystData.Catalyst(ArenaOverEnchanterMode.OVER_STACKING, id, null),
            fixedDisplayName = catalystDisplayName(label, 1),
            fixedLore = listOf("§7競合エンチャント用だよ"),
            material = Material.FERMENTED_SPIDER_EYE
        )
    }

    val exoticAttach = listOf(
        "infinity" to "無限",
        "sharpness" to "ダメージ増加",
        "breach" to "防具貫通"
    ).map { (id, label) ->
        ArenaOverEnchanterCatalystItem(
            id = "exotic_attach_$id",
            displayName = catalystDisplayName(label, 1),
            lore = listOf("§7強制付加用だよ"),
            catalyst = ArenaOverEnchanterCatalystData.Catalyst(ArenaOverEnchanterMode.EXOTIC_ATTACH, id, null),
            fixedDisplayName = catalystDisplayName(label, 1),
            fixedLore = listOf("§7強制付加用だよ"),
            material = Material.NETHER_STAR
        )
    }

    return buildList {
        addAll(limitBreaking)
        addAll(overStackingTargets)
        addAll(exoticAttach)
    }
}
