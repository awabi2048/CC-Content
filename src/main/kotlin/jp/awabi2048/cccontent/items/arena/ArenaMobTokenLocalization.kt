package jp.awabi2048.cccontent.items.arena

import com.awabi2048.ccsystem.api.localization.LocalizationKey
import com.awabi2048.ccsystem.api.localization.generated.ContentCustomItemsKeys

/**
 * アリーナmob tokenの有限カテゴリと生成済みローカライズキーを一対一で管理します。
 *
 * 設定由来のmob種別を表示時に文字列連結すると、カタログとの不一致が起動後まで残ります。
 * そのため、設定読込時にこの表へ解決できることを必須とし、以後は型付きキーだけを渡します。
 */
object ArenaMobTokenLocalization {
    val loreKey: LocalizationKey<List<String>> = ContentCustomItemsKeys.CUSTOM_ITEMS_ARENA_MOB_TOKEN_LORE

    private val nameKeys: Map<String, LocalizationKey<String>> = linkedMapOf(
        "skeleton" to ContentCustomItemsKeys.CUSTOM_ITEMS_ARENA_MOB_TOKEN_TOKEN_NAMES_SKELETON,
        "zombie" to ContentCustomItemsKeys.CUSTOM_ITEMS_ARENA_MOB_TOKEN_TOKEN_NAMES_ZOMBIE,
        "creeper" to ContentCustomItemsKeys.CUSTOM_ITEMS_ARENA_MOB_TOKEN_TOKEN_NAMES_CREEPER,
        "piglin" to ContentCustomItemsKeys.CUSTOM_ITEMS_ARENA_MOB_TOKEN_TOKEN_NAMES_PIGLIN,
        "wither_skeleton" to ContentCustomItemsKeys.CUSTOM_ITEMS_ARENA_MOB_TOKEN_TOKEN_NAMES_WITHER_SKELETON,
        "ender_dragon" to ContentCustomItemsKeys.CUSTOM_ITEMS_ARENA_MOB_TOKEN_TOKEN_NAMES_ENDER_DRAGON,
        "husk" to ContentCustomItemsKeys.CUSTOM_ITEMS_ARENA_MOB_TOKEN_TOKEN_NAMES_HUSK,
        "iron_golem" to ContentCustomItemsKeys.CUSTOM_ITEMS_ARENA_MOB_TOKEN_TOKEN_NAMES_IRON_GOLEM,
        "guardian" to ContentCustomItemsKeys.CUSTOM_ITEMS_ARENA_MOB_TOKEN_TOKEN_NAMES_GUARDIAN,
        "elder_guardian" to ContentCustomItemsKeys.CUSTOM_ITEMS_ARENA_MOB_TOKEN_TOKEN_NAMES_ELDER_GUARDIAN,
        "drowned" to ContentCustomItemsKeys.CUSTOM_ITEMS_ARENA_MOB_TOKEN_TOKEN_NAMES_DROWNED,
        "silverfish" to ContentCustomItemsKeys.CUSTOM_ITEMS_ARENA_MOB_TOKEN_TOKEN_NAMES_SILVERFISH,
        "spider" to ContentCustomItemsKeys.CUSTOM_ITEMS_ARENA_MOB_TOKEN_TOKEN_NAMES_SPIDER,
        "witch" to ContentCustomItemsKeys.CUSTOM_ITEMS_ARENA_MOB_TOKEN_TOKEN_NAMES_WITCH,
        "blaze" to ContentCustomItemsKeys.CUSTOM_ITEMS_ARENA_MOB_TOKEN_TOKEN_NAMES_BLAZE,
        "magma_cube" to ContentCustomItemsKeys.CUSTOM_ITEMS_ARENA_MOB_TOKEN_TOKEN_NAMES_MAGMA_CUBE,
        "slime" to ContentCustomItemsKeys.CUSTOM_ITEMS_ARENA_MOB_TOKEN_TOKEN_NAMES_SLIME,
        "bogged" to ContentCustomItemsKeys.CUSTOM_ITEMS_ARENA_MOB_TOKEN_TOKEN_NAMES_BOGGED,
        "stray" to ContentCustomItemsKeys.CUSTOM_ITEMS_ARENA_MOB_TOKEN_TOKEN_NAMES_STRAY,
        "bat" to ContentCustomItemsKeys.CUSTOM_ITEMS_ARENA_MOB_TOKEN_TOKEN_NAMES_BAT,
        "enderman" to ContentCustomItemsKeys.CUSTOM_ITEMS_ARENA_MOB_TOKEN_TOKEN_NAMES_ENDERMAN,
        "endermite" to ContentCustomItemsKeys.CUSTOM_ITEMS_ARENA_MOB_TOKEN_TOKEN_NAMES_ENDERMITE,
        "shulker" to ContentCustomItemsKeys.CUSTOM_ITEMS_ARENA_MOB_TOKEN_TOKEN_NAMES_SHULKER,
        "spirit" to ContentCustomItemsKeys.CUSTOM_ITEMS_ARENA_MOB_TOKEN_TOKEN_NAMES_SPIRIT,
        "frog" to ContentCustomItemsKeys.CUSTOM_ITEMS_ARENA_MOB_TOKEN_TOKEN_NAMES_FROG,
        "boomerang" to ContentCustomItemsKeys.CUSTOM_ITEMS_ARENA_MOB_TOKEN_TOKEN_NAMES_BOOMERANG,
    )

    fun nameKey(canonicalCategoryId: String): LocalizationKey<String> =
        requireNotNull(nameKeys[canonicalCategoryId]) {
            "mob tokenカテゴリに対応する生成済み言語キーがありません: category=$canonicalCategoryId"
        }

    fun supports(canonicalCategoryId: String): Boolean = nameKeys.containsKey(canonicalCategoryId)

    fun supportedCategoryIds(): Set<String> = nameKeys.keys
}
