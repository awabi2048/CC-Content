@file:Suppress("DEPRECATION")

package jp.awabi2048.cccontent.items.arena

import jp.awabi2048.cccontent.items.CustomItem
import jp.awabi2048.cccontent.items.CustomItemI18n
import jp.awabi2048.cccontent.items.CustomItemManager
import jp.awabi2048.cccontent.util.ItemMetaCompat
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemFlag
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import java.util.Locale
import kotlin.math.ceil

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
        modelData?.let { ItemMetaCompat.setLegacyCustomModelData(meta, it) }
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
                "shulker_mimic", "shulker_warp_sniper" -> "shulker"
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
                "guardian" -> NamespacedKey.minecraft("lightning_rod")
                "elder_guardian" -> NamespacedKey.minecraft("lightning_rod")
                "drowned" -> NamespacedKey.minecraft("moss_block")
                "silverfish" -> NamespacedKey.minecraft("blue_egg")
                "spider" -> NamespacedKey.minecraft("disc_fragment_5")
                "blaze" -> NamespacedKey.minecraft("blaze_powder")
                "magma_cube" -> NamespacedKey.minecraft("fire_charge")
                "spirit" -> NamespacedKey.minecraft("ghast_tear")
                "witch" -> NamespacedKey.minecraft("leather_chestplate")
                "bat" -> NamespacedKey.minecraft("phantom_membrane")
                "enderman" -> NamespacedKey.minecraft("ender_pearl")
                "shulker" -> NamespacedKey.minecraft("shulker_shell")
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
            "enderman",
            "shulker",
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

enum class ArenaEnchantShardEffectType(val id: String) {
    LIMIT_BREAKING("limit_breaking"),
    INCOMPATIBLE_COMBINATION("incompatible_combination"),
    INVALID_TARGET_ATTACH("invalid_target_attach"),
    CUSTOM_ENCHANT_ATTACH("custom_enchant_attach");

    companion object {
        fun fromId(id: String): ArenaEnchantShardEffectType? {
            return entries.firstOrNull { it.id == id }
        }
    }
}

object ArenaEnchantShardData {
    data class Shard(
        val effectType: ArenaEnchantShardEffectType,
        val enchantmentIds: List<String>,
        val overLevel: Int?
    ) {
        val primaryEnchantmentId: String
            get() = enchantmentIds.firstOrNull().orEmpty()

        val setKey: String
            get() = enchantmentIds.joinToString("+")
    }

    private val shardMarkerKey by lazy {
        NamespacedKey(Bukkit.getPluginManager().getPlugin("CC-Content")!!, "enchant_shard_marker")
    }

    private val specKey by lazy {
        NamespacedKey(Bukkit.getPluginManager().getPlugin("CC-Content")!!, "enchant_shard_spec")
    }

    private val effectTypeKey by lazy {
        NamespacedKey(Bukkit.getPluginManager().getPlugin("CC-Content")!!, "enchant_shard_effect_type")
    }

    private val enchantmentIdsKey by lazy {
        NamespacedKey(Bukkit.getPluginManager().getPlugin("CC-Content")!!, "enchant_shard_enchantment_ids")
    }

    private val overLevelKey by lazy {
        NamespacedKey(Bukkit.getPluginManager().getPlugin("CC-Content")!!, "enchant_shard_over_level")
    }

    fun apply(meta: ItemMeta, definition: ArenaEnchantShardDefinition) {
        meta.persistentDataContainer.set(shardMarkerKey, PersistentDataType.BYTE, 1)
        meta.persistentDataContainer.set(specKey, PersistentDataType.STRING, definition.spec)
        meta.persistentDataContainer.set(effectTypeKey, PersistentDataType.STRING, definition.shard.effectType.id)
        meta.persistentDataContainer.set(enchantmentIdsKey, PersistentDataType.STRING, definition.shard.enchantmentIds.joinToString(","))
        if (definition.shard.overLevel != null) {
            meta.persistentDataContainer.set(overLevelKey, PersistentDataType.INTEGER, definition.shard.overLevel)
        } else {
            meta.persistentDataContainer.remove(overLevelKey)
        }
    }

    fun read(item: ItemStack): Shard? {
        val meta = item.itemMeta ?: return null
        if (meta.persistentDataContainer.get(shardMarkerKey, PersistentDataType.BYTE)?.toInt() != 1) {
            return null
        }
        val effectTypeId = meta.persistentDataContainer.get(effectTypeKey, PersistentDataType.STRING) ?: return null
        val enchantmentIds = meta.persistentDataContainer.get(enchantmentIdsKey, PersistentDataType.STRING)
            ?.split(',')
            ?.map { it.trim().lowercase(Locale.ROOT) }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            .orEmpty()
        if (enchantmentIds.isEmpty()) {
            return null
        }
        val effectType = ArenaEnchantShardEffectType.fromId(effectTypeId) ?: return null
        val overLevel = meta.persistentDataContainer.get(overLevelKey, PersistentDataType.INTEGER)
        return Shard(effectType = effectType, enchantmentIds = enchantmentIds, overLevel = overLevel)
    }

    fun readSpec(item: ItemStack): String? {
        val meta = item.itemMeta ?: return null
        if (meta.persistentDataContainer.get(shardMarkerKey, PersistentDataType.BYTE)?.toInt() != 1) {
            return null
        }
        return meta.persistentDataContainer.get(specKey, PersistentDataType.STRING)
    }
}

data class ArenaEnchantShardDefinition(
    val key: String,
    val spec: String,
    val displayEnchantmentIds: List<String>,
    val shard: ArenaEnchantShardData.Shard,
    val dropMobDefinitionIds: Set<String> = emptySet(),
    val baseDropChance: Double? = null
)

object ArenaEnchantShardRegistry {
    private val ALL_MOB_DEFINITION_IDS = setOf(
        "zombie_normal", "skeleton_normal", "skeleton_rapid", "skeleton_curve_backstep", "skeleton_heavy_bow_shield",
        "skeleton_throw_close", "zombie_light_leap", "zombie_archer", "zombie_archer_swap", "zombie_heavy_shield",
        "husk_normal", "husk_light_leap", "husk_archer", "husk_archer_swap", "husk_heavy_shield", "husk_weakening_aura",
        "raiden_creeper", "skeleton_plain", "spider_plain", "spider_stealth", "spider_broodmother", "spider_broodling",
        "spider_swift", "spider_venom_frenzy", "spider_ferocious", "silverfish_plain", "silverfish_big_poison",
        "silverfish_stealth_fang", "iron_golem_normal", "iron_golem_magnet", "guardian_normal", "guardian_small",
        "guardian_beam_burst", "guardian_drain", "bogged_plain", "bogged_normal", "bogged_rapid", "bogged_curve_backstep",
        "bogged_heavy_bow_shield", "bogged_throw_close", "bogged_boomerang", "stray_plain", "stray_normal", "stray_rapid",
        "stray_curve_backstep", "stray_heavy_bow_shield", "stray_throw_close", "slime_merge_small", "slime_merge_medium",
        "slime_merge_large", "slime_merge_mini", "slime_poison", "slime_wither", "drowned_unarmed", "drowned_trident_guard",
        "drowned_raider_axe", "water_spirit", "water_spirit_elite", "ashen_spirit", "frog_big", "blaze_normal",
        "blaze_power", "blaze_rapid", "blaze_melee", "blaze_beam", "magma_cube_large", "magma_cube_medium",
        "magma_cube_small", "magma_cube_mini", "wither_skeleton_swap", "wither_skeleton_bow_guard",
        "wither_skeleton_wither_boomerang", "wither_knight", "wither_ghost", "end_crystal_sentinel",
        "end_wither_sentinel", "witch_normal", "witch_elite", "bat_venom", "enderman_mist_delay",
        "enderman_small_backstab", "enderman_void_carrier", "enderman_phase", "enderman_drain", "enderman_rift_carrier",
        "enderman_grave_carrier", "enderman_mirror", "enderman_eye_summoner", "endermite_poison",
        "shulker_mimic", "ender_eye_beam"
    )

    private val UNDEAD_MOB_DEFINITION_IDS = setOf(
        "zombie_normal", "zombie_light_leap", "zombie_archer", "zombie_archer_swap", "zombie_heavy_shield",
        "husk_normal", "husk_light_leap", "husk_archer", "husk_archer_swap", "husk_heavy_shield", "husk_weakening_aura",
        "skeleton_plain", "skeleton_normal", "skeleton_rapid", "skeleton_curve_backstep", "skeleton_heavy_bow_shield",
        "skeleton_throw_close", "bogged_plain", "bogged_normal", "bogged_rapid", "bogged_curve_backstep",
        "bogged_heavy_bow_shield", "bogged_throw_close", "bogged_boomerang", "stray_plain", "stray_normal", "stray_rapid",
        "stray_curve_backstep", "stray_heavy_bow_shield", "stray_throw_close", "drowned_unarmed", "drowned_trident_guard",
        "drowned_raider_axe", "wither_skeleton_swap", "wither_skeleton_bow_guard", "wither_skeleton_wither_boomerang",
        "wither_knight", "wither_ghost", "end_wither_sentinel"
    )

    private val ARTHROPOD_MOB_DEFINITION_IDS = setOf(
        "spider_plain", "spider_stealth", "spider_broodmother", "spider_broodling", "spider_swift", "spider_venom_frenzy",
        "spider_ferocious", "silverfish_plain", "silverfish_big_poison", "silverfish_stealth_fang", "endermite_poison"
    )

    private val BOW_MOB_DEFINITION_IDS = setOf(
        "zombie_archer", "zombie_archer_swap", "husk_archer", "husk_archer_swap", "skeleton_plain", "skeleton_normal",
        "skeleton_rapid", "skeleton_curve_backstep", "skeleton_heavy_bow_shield", "bogged_plain", "bogged_normal",
        "bogged_rapid", "bogged_curve_backstep", "bogged_heavy_bow_shield", "stray_plain", "stray_normal", "stray_rapid",
        "stray_curve_backstep", "stray_heavy_bow_shield", "wither_skeleton_swap", "wither_skeleton_bow_guard"
    )

    private val EQUIPPED_MOB_DEFINITION_IDS = setOf(
        "zombie_light_leap", "zombie_archer", "zombie_archer_swap", "zombie_heavy_shield", "husk_light_leap", "husk_archer",
        "husk_archer_swap", "husk_heavy_shield", "husk_weakening_aura", "skeleton_plain", "skeleton_normal", "skeleton_rapid",
        "skeleton_curve_backstep", "skeleton_heavy_bow_shield", "skeleton_throw_close", "bogged_plain", "bogged_normal",
        "bogged_rapid", "bogged_curve_backstep", "bogged_heavy_bow_shield", "bogged_throw_close", "bogged_boomerang",
        "stray_plain", "stray_normal", "stray_rapid", "stray_curve_backstep", "stray_heavy_bow_shield", "stray_throw_close",
        "iron_golem_normal", "iron_golem_magnet", "drowned_unarmed", "drowned_trident_guard", "drowned_raider_axe",
        "wither_skeleton_swap", "wither_skeleton_bow_guard", "wither_skeleton_wither_boomerang", "wither_knight",
        "wither_ghost", "blaze_melee"
    )

    private val NETHER_EQUIPPED_MOB_DEFINITION_IDS = setOf(
        "wither_skeleton_swap", "wither_skeleton_bow_guard", "wither_skeleton_wither_boomerang", "blaze_melee"
    )

    private val EQUIPPED_BOW_MOB_DEFINITION_IDS = setOf(
        "zombie_archer", "zombie_archer_swap", "husk_archer", "husk_archer_swap", "skeleton_plain", "skeleton_normal",
        "skeleton_rapid", "skeleton_curve_backstep", "skeleton_heavy_bow_shield", "bogged_plain", "bogged_normal",
        "bogged_rapid", "bogged_curve_backstep", "bogged_heavy_bow_shield", "stray_plain", "stray_normal", "stray_rapid",
        "stray_curve_backstep", "stray_heavy_bow_shield", "wither_skeleton_swap", "wither_skeleton_bow_guard"
    )

    private val SWORD_MOB_DEFINITION_IDS = setOf(
        "zombie_light_leap", "zombie_archer_swap", "zombie_heavy_shield", "husk_light_leap", "husk_archer_swap",
        "husk_heavy_shield", "husk_weakening_aura", "wither_skeleton_swap"
    )

    private val RAPID_SKELETON_MOB_DEFINITION_IDS = setOf("skeleton_rapid", "bogged_rapid", "stray_rapid")
    private val GOLEM_MOB_DEFINITION_IDS = setOf("iron_golem_normal", "iron_golem_magnet")
    private val WATER_SPIRIT_MOB_DEFINITION_IDS = setOf("water_spirit", "water_spirit_elite")
    private val GUARDIAN_MOB_DEFINITION_IDS = setOf("guardian_normal", "guardian_small", "guardian_beam_burst", "guardian_drain")
    private val SLIME_MOB_DEFINITION_IDS = setOf("slime_merge_small", "slime_merge_medium", "slime_merge_large", "slime_merge_mini", "slime_poison", "slime_wither")
    private val BLAZE_MOB_DEFINITION_IDS = setOf("blaze_normal", "blaze_power", "blaze_rapid", "blaze_melee", "blaze_beam")
    private val SHULKER_MOB_DEFINITION_IDS = setOf("shulker_mimic")
    private val CREEPER_MOB_DEFINITION_IDS = setOf("raiden_creeper")

    private fun lb(key: String, enchantId: String, label: String, overLevel: Int, mobIds: Set<String>, chance: Double) =
        ArenaEnchantShardDefinition(
            key = key,
            spec = "limit_breaking:$enchantId:$overLevel",
            displayEnchantmentIds = listOf(enchantId),
            shard = ArenaEnchantShardData.Shard(
                effectType = ArenaEnchantShardEffectType.LIMIT_BREAKING,
                enchantmentIds = listOf(enchantId),
                overLevel = overLevel
            ),
            dropMobDefinitionIds = mobIds,
            baseDropChance = chance
        )

    private fun incompatibleCombination(
        setId: String,
        enchantmentIds: List<String>,
        mobIds: Set<String>,
        chance: Double
    ) = ArenaEnchantShardDefinition(
        key = "incompatible_combination_$setId",
        spec = "incompatible_combination:$setId",
        displayEnchantmentIds = enchantmentIds,
        shard = ArenaEnchantShardData.Shard(
            effectType = ArenaEnchantShardEffectType.INCOMPATIBLE_COMBINATION,
            enchantmentIds = enchantmentIds,
            overLevel = null
        ),
        dropMobDefinitionIds = mobIds,
        baseDropChance = chance
    )

    private fun invalidTargetAttach(enchantId: String, mobIds: Set<String>, chance: Double) = ArenaEnchantShardDefinition(
        key = "invalid_target_attach_$enchantId",
        spec = "invalid_target_attach:$enchantId",
        displayEnchantmentIds = listOf(enchantId),
        shard = ArenaEnchantShardData.Shard(
            effectType = ArenaEnchantShardEffectType.INVALID_TARGET_ATTACH,
            enchantmentIds = listOf(enchantId),
            overLevel = null
        ),
        dropMobDefinitionIds = mobIds,
        baseDropChance = chance
    )

    val definitions: List<ArenaEnchantShardDefinition> = buildList {
        add(lb("sharpness_1", "sharpness", "ダメージ増加", 1, ALL_MOB_DEFINITION_IDS, 0.005))
        add(lb("sharpness_2", "sharpness", "ダメージ増加", 2, ALL_MOB_DEFINITION_IDS, 0.002))
        add(lb("smite_1", "smite", "アンデッド特攻", 1, UNDEAD_MOB_DEFINITION_IDS, 0.005))
        add(lb("smite_2", "smite", "アンデッド特攻", 2, UNDEAD_MOB_DEFINITION_IDS, 0.002))
        add(lb("bane_of_arthropods_1", "bane_of_arthropods", "虫特攻", 1, ARTHROPOD_MOB_DEFINITION_IDS, 0.005))
        add(lb("bane_of_arthropods_2", "bane_of_arthropods", "虫特攻", 2, ARTHROPOD_MOB_DEFINITION_IDS, 0.002))
        add(lb("power_1", "power", "射撃ダメージ", 1, BOW_MOB_DEFINITION_IDS, 0.005))
        add(lb("power_2", "power", "射撃ダメージ", 2, BOW_MOB_DEFINITION_IDS, 0.002))
        add(lb("protection_1", "protection", "ダメージ軽減", 1, EQUIPPED_MOB_DEFINITION_IDS, 0.005))
        add(lb("protection_2", "protection", "ダメージ軽減", 2, EQUIPPED_MOB_DEFINITION_IDS, 0.002))
        add(lb("fire_protection_1", "fire_protection", "火炎耐性", 1, NETHER_EQUIPPED_MOB_DEFINITION_IDS, 0.005))
        add(lb("fire_protection_2", "fire_protection", "火炎耐性", 2, NETHER_EQUIPPED_MOB_DEFINITION_IDS, 0.002))
        add(lb("projectile_protection_1", "projectile_protection", "飛び道具耐性", 1, EQUIPPED_BOW_MOB_DEFINITION_IDS, 0.005))
        add(lb("projectile_protection_2", "projectile_protection", "飛び道具耐性", 2, EQUIPPED_BOW_MOB_DEFINITION_IDS, 0.002))
        add(lb("sweeping_edge_1", "sweeping_edge", "範囲ダメージ", 1, SWORD_MOB_DEFINITION_IDS, 0.005))
        add(lb("sweeping_edge_2", "sweeping_edge", "範囲ダメージ", 2, SWORD_MOB_DEFINITION_IDS, 0.002))
        add(lb("quick_charge_1", "quick_charge", "高速装填", 1, RAPID_SKELETON_MOB_DEFINITION_IDS, 0.008))
        add(lb("quick_charge_2", "quick_charge", "高速装填", 2, RAPID_SKELETON_MOB_DEFINITION_IDS, 0.003))
        add(lb("unbreaking_1", "unbreaking", "耐久", 1, GOLEM_MOB_DEFINITION_IDS, 0.01))
        add(lb("unbreaking_2", "unbreaking", "耐久", 2, GOLEM_MOB_DEFINITION_IDS, 0.004))
        add(lb("looting_1", "looting", "ドロップ増加", 1, WATER_SPIRIT_MOB_DEFINITION_IDS, 0.01))
        add(lb("looting_2", "looting", "ドロップ増加", 2, WATER_SPIRIT_MOB_DEFINITION_IDS, 0.004))
        add(lb("impaling_1", "impaling", "水棲特攻", 1, GUARDIAN_MOB_DEFINITION_IDS, 0.01))
        add(lb("impaling_2", "impaling", "水棲特攻", 2, GUARDIAN_MOB_DEFINITION_IDS, 0.004))
        add(lb("knockback_1", "knockback", "ノックバック", 1, SLIME_MOB_DEFINITION_IDS, 0.01))
        add(lb("knockback_2", "knockback", "ノックバック", 2, SLIME_MOB_DEFINITION_IDS, 0.004))
        add(lb("knockback_3", "knockback", "ノックバック", 3, SLIME_MOB_DEFINITION_IDS, 0.001))
        add(lb("fire_aspect_1", "fire_aspect", "火属性", 1, BLAZE_MOB_DEFINITION_IDS, 0.01))
        add(lb("fire_aspect_2", "fire_aspect", "火属性", 2, BLAZE_MOB_DEFINITION_IDS, 0.004))
        add(lb("fire_aspect_3", "fire_aspect", "火属性", 3, BLAZE_MOB_DEFINITION_IDS, 0.001))
        add(lb("feather_falling_1", "feather_falling", "落下耐性", 1, SHULKER_MOB_DEFINITION_IDS, 0.01))
        add(lb("feather_falling_2", "feather_falling", "落下耐性", 2, SHULKER_MOB_DEFINITION_IDS, 0.004))
        add(lb("blast_protection_1", "blast_protection", "爆発耐性", 1, CREEPER_MOB_DEFINITION_IDS, 0.01))
        add(lb("blast_protection_2", "blast_protection", "爆発耐性", 2, CREEPER_MOB_DEFINITION_IDS, 0.004))

        add(incompatibleCombination("infinity_mending", listOf("infinity", "mending"), BOW_MOB_DEFINITION_IDS, 0.002))
        add(incompatibleCombination("multishot_piercing", listOf("multishot", "piercing"), BOW_MOB_DEFINITION_IDS, 0.002))
        add(incompatibleCombination("damage_smite", listOf("sharpness", "smite"), UNDEAD_MOB_DEFINITION_IDS, 0.002))
        add(incompatibleCombination("damage_bane_of_arthropods", listOf("sharpness", "bane_of_arthropods"), ARTHROPOD_MOB_DEFINITION_IDS, 0.002))
        add(incompatibleCombination("protection_fire_protection", listOf("protection", "fire_protection"), NETHER_EQUIPPED_MOB_DEFINITION_IDS, 0.002))
        add(incompatibleCombination("protection_blast_protection", listOf("protection", "blast_protection"), CREEPER_MOB_DEFINITION_IDS, 0.002))
        add(incompatibleCombination("protection_projectile_protection", listOf("protection", "projectile_protection"), EQUIPPED_BOW_MOB_DEFINITION_IDS, 0.002))
        add(invalidTargetAttach("infinity", BOW_MOB_DEFINITION_IDS, 0.002))
        add(invalidTargetAttach("power", BOW_MOB_DEFINITION_IDS, 0.002))
        add(invalidTargetAttach("breach", EQUIPPED_MOB_DEFINITION_IDS, 0.002))
    }

    private val bySpec: Map<String, ArenaEnchantShardDefinition> = definitions.associateBy { it.spec }
    private val byMobDefinitionId: Map<String, List<ArenaEnchantShardDefinition>> = buildMap {
        definitions.filter { it.baseDropChance != null }.forEach { definition ->
            definition.dropMobDefinitionIds.forEach { mobDefinitionId ->
                put(mobDefinitionId, (get(mobDefinitionId).orEmpty() + definition))
            }
        }
    }

    fun findBySpec(spec: String): ArenaEnchantShardDefinition? {
        return bySpec[normalizeSpec(spec)]
    }

    fun findByShard(shard: ArenaEnchantShardData.Shard): ArenaEnchantShardDefinition? {
        return definitions.firstOrNull {
            it.shard.effectType == shard.effectType &&
                it.shard.enchantmentIds == shard.enchantmentIds &&
                it.shard.overLevel == shard.overLevel
        }
    }

    fun supportedSpecs(): List<String> = definitions.map { it.spec }.sorted()

    fun getDropDefinitionsForMob(mobDefinitionId: String): List<ArenaEnchantShardDefinition> {
        return byMobDefinitionId[mobDefinitionId.trim().lowercase(Locale.ROOT)].orEmpty()
    }

    fun calculateDropChance(baseChance: Double, attemptCount: Int): Double {
        val normalizedBaseChance = baseChance.coerceIn(0.0, 1.0)
        if (normalizedBaseChance <= 0.0) return 0.0
        if (normalizedBaseChance >= 1.0) return 1.0
        val threshold = ceil(1.0 / normalizedBaseChance).toInt().coerceAtLeast(1)
        return when {
            attemptCount < threshold -> normalizedBaseChance
            attemptCount >= threshold * 2 -> 1.0
            else -> {
                val progress = (attemptCount - threshold).toDouble() / threshold.toDouble()
                normalizedBaseChance + progress * (1.0 - normalizedBaseChance)
            }
        }.coerceIn(0.0, 1.0)
    }

    private fun normalizeSpec(spec: String): String {
        return spec.trim().lowercase(Locale.ROOT)
    }
}

class ArenaEnchantShardItem : ArenaSimpleItem(
    material = Material.POISONOUS_POTATO,
    modelData = null,
    id = "enchant_shard",
    displayName = "§bエンチャントシャード",
    lore = listOf("§7特殊なエンチャントの欠片")
) {
    override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
        return super.createItemForPlayer(player, amount.coerceAtLeast(1))
    }

    override fun updateLocalization(item: ItemStack, player: Player?) {
        val definition = ArenaEnchantShardData.readSpec(item)?.let { ArenaEnchantShardRegistry.findBySpec(it) }
            ?: ArenaEnchantShardData.read(item)?.let { ArenaEnchantShardRegistry.findByShard(it) }
            ?: return
        val localized = createShard(player, definition, item.amount.coerceAtLeast(1))
        item.itemMeta = localized.itemMeta
    }

    companion object {
        fun createShard(player: Player?, definition: ArenaEnchantShardDefinition, amount: Int = 1): ItemStack {
            val item = CustomItemManager.createItemForPlayer(ArenaEnchantShardItem(), player, amount.coerceAtLeast(1))
            val meta = item.itemMeta ?: return item
            applyShardPresentation(meta, definition)
            applyShardData(meta, definition)
            item.itemMeta = meta
            return item
        }

        private fun applyShardPresentation(meta: ItemMeta, definition: ArenaEnchantShardDefinition) {
            meta.displayName(
                Component.text("エンチャントシャード", NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("【", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false))
                    .append(displayEnchantmentSet(definition).color(NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false))
                    .append(Component.text("】", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false))
            )
            meta.lore(buildLore(definition))
            meta.setItemModel(itemModelForShard(definition))
            meta.setMaxStackSize(1)
            meta.addEnchant(Enchantment.UNBREAKING, 1, true)
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
        }

        private fun applyShardData(meta: ItemMeta, definition: ArenaEnchantShardDefinition) {
            meta.persistentDataContainer.set(arenaItemKey, PersistentDataType.STRING, "enchant_shard")
            ArenaEnchantShardData.apply(meta, definition)
        }

        private fun buildLore(definition: ArenaEnchantShardDefinition): List<Component> {
            val tier = when (definition.shard.effectType) {
                ArenaEnchantShardEffectType.LIMIT_BREAKING -> roman(definition.shard.overLevel ?: 1)
                ArenaEnchantShardEffectType.INCOMPATIBLE_COMBINATION,
                ArenaEnchantShardEffectType.INVALID_TARGET_ATTACH,
                ArenaEnchantShardEffectType.CUSTOM_ENCHANT_ATTACH -> "I"
            }
            val description = when (definition.shard.effectType) {
                ArenaEnchantShardEffectType.LIMIT_BREAKING -> "§f§o限界を超える力を秘めている"
                ArenaEnchantShardEffectType.INCOMPATIBLE_COMBINATION,
                ArenaEnchantShardEffectType.INVALID_TARGET_ATTACH,
                ArenaEnchantShardEffectType.CUSTOM_ENCHANT_ATTACH -> "§f§o調和をもたらす力を秘めている"
            }
            return listOf(
                Component.text("Tier $tier", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text(description).decoration(TextDecoration.ITALIC, false),
            )
        }

        private fun displayEnchantmentSet(definition: ArenaEnchantShardDefinition): Component {
            return definition.displayEnchantmentIds
                .map { enchantmentName(it) }
                .reduceOrNull { acc, component ->
                    acc.append(Component.text(" ┃ ", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false))
                        .append(component)
                }
                ?: Component.text(definition.key)
        }

        private fun enchantmentName(enchantmentId: String): Component {
            return Component.translatable("enchantment.minecraft.${enchantmentId.trim().lowercase(Locale.ROOT)}")
        }

        private fun roman(value: Int): String {
            return when (value.coerceAtLeast(1)) {
                1 -> "I"
                2 -> "II"
                3 -> "III"
                4 -> "IV"
                5 -> "V"
                else -> value.toString()
            }
        }

        private fun itemModelForShard(definition: ArenaEnchantShardDefinition): NamespacedKey {
            return when (definition.shard.effectType) {
                ArenaEnchantShardEffectType.LIMIT_BREAKING -> when ((definition.shard.overLevel ?: 1).coerceAtLeast(1)) {
                    1 -> NamespacedKey.minecraft("resin_clump")
                    2 -> NamespacedKey.minecraft("amethyst_shard")
                    else -> NamespacedKey.minecraft("echo_shard")
                }
                ArenaEnchantShardEffectType.INCOMPATIBLE_COMBINATION,
                ArenaEnchantShardEffectType.INVALID_TARGET_ATTACH,
                ArenaEnchantShardEffectType.CUSTOM_ENCHANT_ATTACH -> NamespacedKey.minecraft("nether_star")
            }
        }
    }
}
