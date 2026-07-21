package jp.awabi2048.cccontent.features.cooking

import org.bukkit.Material
import kotlin.math.ceil

enum class CuttingBoardClass { SOFT, BALANCED, HARD }

data class CuttingSlot(val customItemId: String?, val amount: Int, val maximum: Int = 64) {
    init {
        require(amount in 0..maximum)
        require((customItemId == null) == (amount == 0))
    }
}

data class CuttingExecution(
    val scale: Int,
    val knifeDamage: Int,
    val outputAmount: Int,
    val resultingSlots: List<CuttingSlot>
)

object CuttingPolicy {
    private val boardMaterials = mapOf(
        Material.BIRCH_PRESSURE_PLATE to CuttingBoardClass.SOFT,
        Material.CHERRY_PRESSURE_PLATE to CuttingBoardClass.SOFT,
        Material.BAMBOO_PRESSURE_PLATE to CuttingBoardClass.SOFT,
        Material.OAK_PRESSURE_PLATE to CuttingBoardClass.BALANCED,
        Material.SPRUCE_PRESSURE_PLATE to CuttingBoardClass.BALANCED,
        Material.JUNGLE_PRESSURE_PLATE to CuttingBoardClass.BALANCED,
        Material.ACACIA_PRESSURE_PLATE to CuttingBoardClass.BALANCED,
        Material.MANGROVE_PRESSURE_PLATE to CuttingBoardClass.BALANCED,
        Material.DARK_OAK_PRESSURE_PLATE to CuttingBoardClass.HARD,
        Material.CRIMSON_PRESSURE_PLATE to CuttingBoardClass.HARD,
        Material.WARPED_PRESSURE_PLATE to CuttingBoardClass.HARD
    )

    @JvmStatic
    fun boardClass(material: Material): CuttingBoardClass? = boardMaterials[material]

    @JvmStatic
    fun durabilityMultiplier(food: CuttingFoodClass, board: CuttingBoardClass): Double = when {
        food == CuttingFoodClass.DELICATE && board == CuttingBoardClass.HARD -> 2.0
        food == CuttingFoodClass.TOUGH && board == CuttingBoardClass.SOFT -> 2.0
        else -> 1.0
    }

    @JvmStatic
    fun execute(
        recipe: CuttingRecipeDefinition,
        inputIngredientId: String,
        inputAmount: Int,
        board: CuttingBoardClass,
        slots: List<CuttingSlot>
    ): CuttingExecution? {
        require(slots.size == 10)
        if (inputIngredientId != recipe.inputIngredientId || inputAmount <= 0) return null
        val scale = inputAmount
        val outputAmount = recipe.outputAmount * scale
        val placed = placeOutput(slots, recipe.outputCustomItemId, outputAmount) ?: return null
        val damage = ceil(recipe.baseDurability * durabilityMultiplier(recipe.foodClass, board) * scale)
            .toInt().coerceAtLeast(1)
        return CuttingExecution(scale, damage, outputAmount, placed)
    }

    @JvmStatic
    fun placeOutput(
        slots: List<CuttingSlot>,
        outputCustomItemId: String,
        outputAmount: Int
    ): List<CuttingSlot>? {
        require(slots.size == 10)
        require(outputCustomItemId.isNotBlank())
        require(outputAmount > 0)
        val result = slots.toMutableList()
        var remaining = outputAmount
        result.indices.filter { result[it].customItemId == outputCustomItemId }.forEach { index ->
            if (remaining <= 0) return@forEach
            val slot = result[index]
            val moved = minOf(remaining, slot.maximum - slot.amount)
            result[index] = slot.copy(amount = slot.amount + moved)
            remaining -= moved
        }
        result.indices.filter { result[it].customItemId == null }.forEach { index ->
            if (remaining <= 0) return@forEach
            val maximum = result[index].maximum
            val moved = minOf(remaining, maximum)
            result[index] = CuttingSlot(outputCustomItemId, moved, maximum)
            remaining -= moved
        }
        return result.takeIf { remaining == 0 }
    }
}
