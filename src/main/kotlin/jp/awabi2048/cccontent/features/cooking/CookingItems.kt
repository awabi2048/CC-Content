package jp.awabi2048.cccontent.features.cooking

import com.awabi2048.ccsystem.CCSystem
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.Consumable
import io.papermc.paper.datacomponent.item.FoodProperties
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation
import jp.awabi2048.cccontent.items.CustomItem
import jp.awabi2048.cccontent.items.CustomItemManager
import jp.awabi2048.cccontent.persistence.ContentPdcKeys
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

class CookingItems(private val configuration: UnifiedCookingConfiguration) {
    private val registered = mutableListOf<String>()

    fun register() {
        unregister()
        register(ToolItem("knife", 256))
        register(ToolItem("frying_pan", 384))
        configuration.cuttingRecipes.values
            .distinctBy(CuttingRecipeDefinition::outputCustomItemId)
            .forEach { recipe -> register(IntermediateItem(recipe.outputCustomItemId)) }
        listOf(
            "miso" to 16, "soy_sauce" to 16, "curry_roux" to 64, "butter" to 64,
            "dough" to 64, "sweet_dough" to 64, "carrot_cookie_dough" to 64, "roasted_coffee" to 64
        ).forEach { (id, stack) -> register(IntermediateItem("cooking.$id", stack)) }
        configuration.recipes.values
            .flatMap { listOf(it.result, it.failureResult) }
            .distinctBy(UnifiedCookingResult::customItemId)
            .forEach { result -> register(ResultItem(result)) }
        CookingVanillaDefinitions.all.filter { it.experience > 0 }.forEach { definition ->
            val nutrition = when (definition.tier) {
                CookingTier.BASIC -> 5
                CookingTier.INTERMEDIATE -> 7
                CookingTier.ADVANCED -> 9
                CookingTier.TOP -> 12
            }
            val saturation = when (definition.tier) {
                CookingTier.BASIC -> 0.6f
                CookingTier.INTERMEDIATE -> 0.8f
                CookingTier.ADVANCED -> 1.0f
                CookingTier.TOP -> 1.2f
            }
            register(ResultItem(UnifiedCookingResult(
                "cooking.${definition.id}", Material.POISONOUS_POTATO,
                NamespacedKey("kota_server", "custom_item/cooking/${definition.id}"), null, null,
                nutrition, saturation, false, emptyList(), 16, definition.outputAmount
            )))
        }
    }

    fun unregister() {
        CustomItemManager.unregisterByPrefix("cooking.")
        registered.clear()
    }

    private fun register(item: CustomItem) {
        CustomItemManager.register(item)
        registered += item.fullId
    }

    private abstract class BaseCookingItem(final override val id: String) : CustomItem {
        final override val feature = "cooking"
        final override val canPlace = false
        override val displayName: String = id

        protected fun localizedMeta(item: ItemStack, player: Player?, maxStack: Int, model: NamespacedKey) {
            item.editMeta { meta ->
                meta.displayName(Component.text(text(player, "custom_items.cooking.$id.name"))
                    .decoration(TextDecoration.ITALIC, false))
                val descriptionKey = "custom_items.cooking.$id.description"
                if (CCSystem.getAPI().hasI18nKey(descriptionKey)) {
                    meta.lore(listOf(Component.text(text(player, descriptionKey), NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)))
                }
                meta.setItemModel(model)
                meta.setMaxStackSize(maxStack)
                meta.persistentDataContainer.set(ContentPdcKeys.customItemId, PersistentDataType.STRING, fullId)
                meta.persistentDataContainer.set(ContentPdcKeys.itemSchemaVersion, PersistentDataType.INTEGER, 4)
            }
        }

        override fun matches(item: ItemStack): Boolean =
            item.itemMeta?.persistentDataContainer?.get(ContentPdcKeys.customItemId, PersistentDataType.STRING) == fullId

        protected fun text(player: Player?, key: String): String =
            CCSystem.getAPI().getI18nString(player, key).replace('&', '§')
    }

    private class ToolItem(id: String, private val durability: Int) : BaseCookingItem(id) {
        override val canStack = false
        override val itemModel = NamespacedKey("kota_server", "custom_item/cooking/$id")
        override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)
        override fun createItemForPlayer(player: Player?, amount: Int): ItemStack =
            ItemStack(Material.POISONOUS_POTATO).also { item ->
                localizedMeta(item, player, 1, requireNotNull(itemModel))
                item.setData(DataComponentTypes.MAX_DAMAGE, durability)
                item.unsetData(DataComponentTypes.FOOD)
                item.unsetData(DataComponentTypes.CONSUMABLE)
            }
    }

    private class IntermediateItem(fullId: String, private val maximumStack: Int = 64) : BaseCookingItem(fullId.removePrefix("cooking.")) {
        override val itemModel = NamespacedKey("kota_server", "custom_item/cooking/$id")
        override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)
        override fun createItemForPlayer(player: Player?, amount: Int): ItemStack =
            ItemStack(Material.POISONOUS_POTATO, amount.coerceAtLeast(1)).also { item ->
                localizedMeta(item, player, maximumStack, requireNotNull(itemModel))
                item.unsetData(DataComponentTypes.FOOD)
                item.unsetData(DataComponentTypes.CONSUMABLE)
                item.editMeta { meta ->
                    meta.persistentDataContainer.set(ContentPdcKeys.cookingStage, PersistentDataType.STRING, "INTERMEDIATE")
                }
            }
    }

    private class ResultItem(private val result: UnifiedCookingResult) :
        BaseCookingItem(result.customItemId.removePrefix("cooking.")) {
        override val itemModel: NamespacedKey = result.itemModel
        override val keepConsumableComponent: Boolean = result.nutrition > 0 || result.alwaysEat
        override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)
        override fun createItemForPlayer(player: Player?, amount: Int): ItemStack =
            ItemStack(result.baseMaterial, amount.coerceAtLeast(1)).also { item ->
                localizedMeta(item, player, result.maxStackSize, itemModel)
                item.editMeta { meta ->
                    meta.persistentDataContainer.set(ContentPdcKeys.cookingRecipeId, PersistentDataType.STRING, id)
                    meta.persistentDataContainer.set(ContentPdcKeys.cookingStage, PersistentDataType.STRING, "RESULT")
                }
                if (keepConsumableComponent) {
                    item.setData(
                        DataComponentTypes.FOOD,
                        FoodProperties.food()
                            .nutrition(result.nutrition)
                            .saturation(result.saturationModifier)
                            .canAlwaysEat(result.alwaysEat)
                    )
                    item.setData(
                        DataComponentTypes.CONSUMABLE,
                        Consumable.consumable().consumeSeconds(1.6f)
                            .animation(if (result.container == Material.GLASS_BOTTLE) ItemUseAnimation.DRINK else ItemUseAnimation.EAT)
                            .hasConsumeParticles(true)
                    )
                } else {
                    item.unsetData(DataComponentTypes.FOOD)
                    item.unsetData(DataComponentTypes.CONSUMABLE)
                }
            }
    }
}
