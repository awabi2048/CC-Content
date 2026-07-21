package jp.awabi2048.cccontent.features.cooking

import jp.awabi2048.cccontent.persistence.ContentPdcKeys
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

class CookingIngredientResolver(
    ingredients: Collection<UnifiedCookingIngredient>
) {
    private val byCustomItem = index(ingredients, CookingIngredientMatcherType.CUSTOM_ITEM_ID)
    private val byFish = index(ingredients, CookingIngredientMatcherType.FISH_ID)
    private val byResource = index(ingredients, CookingIngredientMatcherType.RESOURCE_ID)
    private val byMaterial = index(ingredients, CookingIngredientMatcherType.MATERIAL)

    fun resolve(item: ItemStack): UnifiedCookingIngredient? {
        val meta = item.itemMeta
        val pdc = meta?.persistentDataContainer
        val customItemId = pdc?.get(ContentPdcKeys.customItemId, PersistentDataType.STRING)
        val fishId = pdc?.get(ContentPdcKeys.fishId, PersistentDataType.STRING)
        val resourceId = pdc?.get(ContentPdcKeys.resourceId, PersistentDataType.STRING)
        return resolveIds(customItemId, fishId, resourceId, item.type.name)
    }

    fun resolveIds(
        customItemId: String?,
        fishId: String?,
        resourceId: String?,
        materialName: String
    ): UnifiedCookingIngredient? = customItemId?.let(byCustomItem::get)
        ?: fishId?.let(byFish::get)
        ?: resourceId?.let(byResource::get)
        ?: byMaterial[materialName.uppercase()]

    fun aggregate(items: Collection<ItemStack>): Map<String, Int> = items
        .filter { !it.type.isAir && it.amount > 0 }
        .mapNotNull { item -> resolve(item)?.let { ingredient -> ingredient.id to item.amount } }
        .groupingBy(Pair<String, Int>::first)
        .fold(0) { amount, pair -> amount + pair.second }

    fun containsUnknown(items: Collection<ItemStack>): Boolean =
        items.any { !it.type.isAir && it.amount > 0 && resolve(it) == null }

    private fun index(
        ingredients: Collection<UnifiedCookingIngredient>,
        type: CookingIngredientMatcherType
    ): Map<String, UnifiedCookingIngredient> = ingredients
        .filter { it.matcher.type == type }
        .associateBy { ingredient ->
            if (type == CookingIngredientMatcherType.MATERIAL) ingredient.matcher.value.uppercase()
            else ingredient.matcher.value
        }
        .also { indexed ->
            val count = ingredients.count { it.matcher.type == type }
            require(indexed.size == count) { "Duplicate cooking ingredient matcher: $type" }
        }
}
