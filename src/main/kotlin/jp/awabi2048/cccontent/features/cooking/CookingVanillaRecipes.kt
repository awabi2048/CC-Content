package jp.awabi2048.cccontent.features.cooking

import jp.awabi2048.cccontent.features.catalog.CatalogStore
import jp.awabi2048.cccontent.features.catalog.CatalogType
import jp.awabi2048.cccontent.features.rank.RankManager
import jp.awabi2048.cccontent.items.CustomItemManager
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.FurnaceExtractEvent
import org.bukkit.inventory.FurnaceRecipe
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.RecipeChoice
import org.bukkit.inventory.ShapelessRecipe
import org.bukkit.inventory.SmokingRecipe
import org.bukkit.plugin.java.JavaPlugin

internal data class VanillaCookingDefinition(
    val id: String,
    val station: CookingStation,
    val ingredients: Map<String, Int>,
    val outputAmount: Int,
    val cookTicks: Int = 0,
    val experience: Long = 0,
    val tier: CookingTier = CookingTier.BASIC
)

internal object CookingVanillaDefinitions {
    val all = listOf(
        craft("miso", mapOf("resource.soybean" to 2, "resource.rice" to 1, "resource.rock_salt" to 1), 2),
        craft("soy_sauce", mapOf("resource.soybean" to 2, "minecraft:wheat" to 1, "resource.rock_salt" to 1, "minecraft:glass_bottle" to 1), 1),
        craft("curry_roux", mapOf("resource.spice_leaf" to 1, "minecraft:wheat" to 1, "minecraft:milk_bucket" to 1), 2),
        craft("butter", mapOf("minecraft:milk_bucket" to 1, "resource.rock_salt" to 1), 4),
        craft("dough", mapOf("minecraft:wheat" to 3, "minecraft:egg" to 1), 1),
        craft("sweet_dough", mapOf("minecraft:wheat" to 3, "minecraft:egg" to 1, "minecraft:honey_bottle" to 1), 1),
        craft("carrot_cookie_dough", mapOf("minecraft:wheat" to 2, "minecraft:egg" to 1, "cooking.cut_carrot" to 1, "minecraft:sugar" to 1), 1),
        furnace("roasted_coffee", "resource.coffee_bean", 200, 1, 0, CookingTier.BASIC),
        furnace("sweet_bread", "cooking.sweet_dough", 200, 1, 15, CookingTier.INTERMEDIATE),
        furnace("carrot_cookie", "cooking.carrot_cookie_dough", 200, 4, 15, CookingTier.INTERMEDIATE),
        furnace("country_bread", "cooking.dough", 200, 1, 12, CookingTier.BASIC),
        furnace("baked_apple", "minecraft:apple", 200, 1, 10, CookingTier.BASIC),
        smoker("smoked_cod", "cooking.fillet_cod", 100, 1, 12),
        smoker("smoked_salmon", "cooking.fillet_salmon", 100, 1, 14),
        smoker("smoked_mackerel", "cooking.fillet_mackerel", 100, 1, 14)
    )

    private fun craft(id: String, ingredients: Map<String, Int>, amount: Int) =
        VanillaCookingDefinition(id, CookingStation.CRAFTING, ingredients, amount)

    private fun furnace(id: String, input: String, ticks: Int, amount: Int, exp: Long, tier: CookingTier) =
        VanillaCookingDefinition(id, CookingStation.FURNACE, mapOf(input to 1), amount, ticks, exp, tier)

    private fun smoker(id: String, input: String, ticks: Int, amount: Int, exp: Long) =
        VanillaCookingDefinition(id, CookingStation.SMOKER, mapOf(input to 1), amount, ticks, exp, CookingTier.INTERMEDIATE)
}

internal class CookingVanillaRecipeController(
    private val plugin: JavaPlugin,
    private val rankManagerProvider: () -> RankManager?,
    private val catalogStore: CatalogStore
) : Listener {
    private val keys = mutableListOf<NamespacedKey>()

    fun initialize() {
        Bukkit.getPluginManager().registerEvents(this, plugin)
        CookingVanillaDefinitions.all.forEach(::register)
    }

    fun shutdown() {
        keys.forEach(Bukkit::removeRecipe)
        keys.clear()
    }

    private fun register(definition: VanillaCookingDefinition) {
        val key = NamespacedKey(plugin, "cooking_${definition.id}")
        val result = requireNotNull(CustomItemManager.createItem("cooking.${definition.id}", definition.outputAmount))
        val recipe = when (definition.station) {
            CookingStation.CRAFTING -> ShapelessRecipe(key, result).also { recipe ->
                definition.ingredients.forEach { (id, amount) ->
                    repeat(amount) { recipe.addIngredient(choice(id)) }
                }
            }
            CookingStation.FURNACE -> FurnaceRecipe(key, result, choice(definition.ingredients.keys.single()), 0f, definition.cookTicks)
            CookingStation.SMOKER -> SmokingRecipe(key, result, choice(definition.ingredients.keys.single()), 0f, definition.cookTicks)
            else -> error("Unsupported vanilla cooking station: ${definition.station}")
        }
        check(Bukkit.addRecipe(recipe)) { "Cooking recipe registration failed: ${definition.id}" }
        keys += key
    }

    private fun choice(id: String): RecipeChoice = if (id.startsWith("minecraft:")) {
        RecipeChoice.MaterialChoice(Material.matchMaterial(id.removePrefix("minecraft:").uppercase())
            ?: error("Unknown Minecraft ingredient: $id"))
    } else {
        RecipeChoice.ExactChoice(requireNotNull(CustomItemManager.createItem(id)) { "Unknown custom ingredient: $id" })
    }

    @EventHandler(ignoreCancelled = true)
    fun onExtract(event: FurnaceExtractEvent) {
        val id = CustomItemManager.identify(event.itemStack)?.fullId?.removePrefix("cooking.") ?: return
        val definition = CookingVanillaDefinitions.all.firstOrNull { it.experience > 0 && it.id == id } ?: return
        reward(event.player, definition, (event.itemAmount / definition.outputAmount).coerceAtLeast(1))
    }

    private fun reward(player: Player, definition: VanillaCookingDefinition, batches: Int) {
        rankManagerProvider()?.addProfessionExp(player.uniqueId, definition.experience * batches)
        catalogStore.record(player.uniqueId, CatalogType.COOKING, definition.id, obtained = true)
    }
}
