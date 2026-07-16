package jp.awabi2048.cccontent.featurestate

import java.util.Locale

/** コンテンツ管理コマンドが扱う既知featureの定義を保持する純粋なレジストリ。 */
data class ContentFeature(
    val id: String,
    val displayName: String,
    val dependencies: List<String> = emptyList()
)

object ContentFeatureCatalog {
    val features: List<ContentFeature> = listOf(
        ContentFeature("arena", "Arena"),
        ContentFeature("rank", "Rank"),
        ContentFeature("brewery", "Brewery"),
        ContentFeature("cooking", "Cooking"),
        ContentFeature("fishing", "Fishing", listOf("rank")),
        ContentFeature("resource_collection", "Resource Collection", listOf("rank")),
        ContentFeature("sukima_dungeon", "Sukima Dungeon"),
        ContentFeature("party", "Party"),
        ContentFeature("minigame", "Minigame", listOf("party"))
    )

    private val byId = features.associateBy(ContentFeature::id)

    /** 大文字入力だけを許可し、別名・区切り文字変換は行わない。 */
    fun resolve(rawId: String): ContentFeature? = byId[rawId.lowercase(Locale.ROOT)]
}
