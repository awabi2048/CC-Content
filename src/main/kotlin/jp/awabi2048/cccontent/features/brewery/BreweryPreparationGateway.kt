package jp.awabi2048.cccontent.features.brewery

import jp.awabi2048.cccontent.features.brewery.item.BreweryItemCodec
import jp.awabi2048.cccontent.features.brewery.model.FirePower
import jp.awabi2048.cccontent.features.processing.ProcessingFirePower
import jp.awabi2048.cccontent.features.processing.ProcessingRecipe
import jp.awabi2048.cccontent.features.processing.ProcessingRecipeMatcher
import jp.awabi2048.cccontent.features.processing.RecipeMatchPolicy
import jp.awabi2048.cccontent.features.processing.RecipeMatchResult
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
    fun match(inputItems: List<ItemStack>, maximumBatches: Int, actualFirePower: FirePower): BreweryPreparation?
    fun createWort(preparation: BreweryPreparation, player: Player?, failed: Boolean): ItemStack
}

internal class DefaultBreweryPreparationGateway(
    private val loader: BrewerySettingsLoader,
    private val recipes: Map<String, BreweryRecipe>,
    private val codec: BreweryItemCodec
) : BreweryPreparationGateway {
    override fun match(inputItems: List<ItemStack>, maximumBatches: Int, actualFirePower: FirePower): BreweryPreparation? {
        val processingRecipes = recipes.values.map { recipe ->
            ProcessingRecipe(
                recipe.id,
                "cauldron",
                recipe.fermentationIdealFirePower.toProcessingFirePower(),
                recipe.fermentationIngredients
            )
        }
        ProcessingRecipeMatcher.validateNoConflicts(processingRecipes)
        val inputAmounts = loader.resolveIngredientAmounts(
            recipes.values.flatMap { it.fermentationIngredients.keys }.toSet(),
            inputItems
        )
        val result = ProcessingRecipeMatcher.matchAllowingFireMismatch(
            processingRecipes,
            "cauldron",
            actualFirePower.toProcessingFirePower(),
            inputAmounts,
            RecipeMatchPolicy(maximumBatches, 0.5, 1, 0.01)
        )
        val candidate = (result as? RecipeMatchResult.Matched)?.candidate ?: return null
        val recipe = recipes[candidate.recipe.id] ?: return null
        val quality = (100.0 - candidate.ratioDistance * 50.0 - candidate.unmatchedAmount * 10.0)
            .coerceIn(0.0, 100.0)
        return BreweryPreparation(
            recipeId = recipe.id,
            quality = quality,
            batches = candidate.batches,
            requiredFirePower = recipe.fermentationIdealFirePower,
            processingSeconds = recipe.fermentationTime
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

    private fun FirePower.toProcessingFirePower(): ProcessingFirePower = when (this) {
        FirePower.HIGH -> ProcessingFirePower.HIGH
        FirePower.LOW,
        FirePower.MEDIUM -> ProcessingFirePower.NORMAL
    }
}
