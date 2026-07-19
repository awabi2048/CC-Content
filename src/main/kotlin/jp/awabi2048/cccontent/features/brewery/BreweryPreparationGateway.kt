package jp.awabi2048.cccontent.features.brewery

import jp.awabi2048.cccontent.features.brewery.item.BreweryItemCodec
import jp.awabi2048.cccontent.features.brewery.model.FirePower
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

data class BreweryPreparation(
    val recipeId: String,
    val quality: Double,
    val batches: Int,
    val requiredFirePower: FirePower,
    val processingSeconds: Int
)

interface BreweryPreparationGateway {
    fun match(inputItems: List<ItemStack>, maximumBatches: Int): BreweryPreparation?
    fun createWort(preparation: BreweryPreparation, player: Player?, failed: Boolean): ItemStack
}

internal class DefaultBreweryPreparationGateway(
    private val loader: BrewerySettingsLoader,
    private val recipes: Map<String, BreweryRecipe>,
    private val codec: BreweryItemCodec
) : BreweryPreparationGateway {
    override fun match(inputItems: List<ItemStack>, maximumBatches: Int): BreweryPreparation? {
        val match = loader.findBestRecipe(recipes, inputItems) ?: return null
        val batches = match.recipe.fermentationIngredients.entries
            .minOf { (id, required) -> (match.matchedIngredientCounts[id] ?: 0) / required }
            .coerceIn(1, maximumBatches)
        return BreweryPreparation(
            recipeId = match.recipe.id,
            quality = match.quality,
            batches = batches,
            requiredFirePower = match.recipe.fermentationIdealFirePower,
            processingSeconds = match.recipe.fermentationTime
        )
    }

    override fun createWort(preparation: BreweryPreparation, player: Player?, failed: Boolean): ItemStack {
        val recipe = requireNotNull(recipes[preparation.recipeId])
        return codec.createWortBottle(
            preparation.recipeId,
            preparation.quality,
            1,
            recipe,
            player,
            failed
        )
    }
}
