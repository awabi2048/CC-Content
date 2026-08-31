package jp.awabi2048.cccontent.items

import org.bukkit.NamespacedKey

/**
 * CC-Contentとリソースパックの間で共有するitem_model IDを集約します。
 *
 * このクラスが返すIDは「表示モデルそのもの」ではなく、リソースパックの
 * assets/kota_server/items/ 配下にある中継定義を指します。したがって、
 * バニラモデルから独自モデルへの差し替えは、プラグインの挙動コードを変更せず
 * リソースパック側だけで行えます。
 */
object ContentItemModels {
    private const val NAMESPACE = "kota_server"
    private const val PREFIX = "custom_item"

    fun cooking(id: String): NamespacedKey = custom("cooking/$id")
    fun cookingLiquidDisplay(): NamespacedKey = cooking("liquid_display")

    fun brewery(id: String): NamespacedKey = custom("brewery/$id")
    fun breweryPrepared(preparationId: String): NamespacedKey = brewery("prepared/$preparationId")
    fun breweryFermented(recipeId: String): NamespacedKey = brewery("fermented/$recipeId")
    fun breweryFailed(recipeId: String): NamespacedKey = brewery("failed/$recipeId")

    fun resource(id: String): NamespacedKey = custom("resource/$id")

    fun fishingFish(fishId: String): NamespacedKey = custom("fishing/fish/$fishId")
    fun fishingRod(rodId: String): NamespacedKey = custom("fishing/rod/$rodId")
    fun fishingBait(baitId: String): NamespacedKey = custom("fishing/bait/$baitId")
    fun fishingGyotaku(fishId: String): NamespacedKey = custom("fishing/gyotaku/$fishId")
    fun fishingDictionary(): NamespacedKey = custom("fishing/dictionary")

    private fun custom(path: String): NamespacedKey {
        require(path.isNotBlank()) { "item_model path must not be blank" }
        return NamespacedKey(NAMESPACE, "$PREFIX/$path")
    }
}
