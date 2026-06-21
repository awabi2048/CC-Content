@file:Suppress("DEPRECATION")

package jp.awabi2048.cccontent.features.brewery.item

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiLoreFrame
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import jp.awabi2048.cccontent.features.brewery.BreweryRecipe
import jp.awabi2048.cccontent.features.brewery.model.BrewStage
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import kotlin.math.roundToInt

class BreweryItemCodec(private val plugin: JavaPlugin) {
    private val stageKey = NamespacedKey(plugin, "brewery_stage")
    private val recipeIdKey = NamespacedKey(plugin, "brewery_recipe_id")
    private val qualityKey = NamespacedKey(plugin, "brewery_quality")
    private val alcoholKey = NamespacedKey(plugin, "brewery_alcohol")
    private val distillCountKey = NamespacedKey(plugin, "brewery_distill_count")
    private val muddyKey = NamespacedKey(plugin, "brewery_muddy")
    private val ambiguousKey = NamespacedKey(plugin, "brewery_ambiguous")
    private val historyKey = NamespacedKey(plugin, "brewery_history")
    private val agingStartKey = NamespacedKey(plugin, "brewery_aging_started_at")
    private val finalStarsKey = NamespacedKey(plugin, "brewery_final_stars")
    private val filterKey = NamespacedKey(plugin, "brewery_filter_sample")
    private val filterRemainingUsesKey = NamespacedKey(plugin, "brewery_filter_remaining_uses")

    data class BreweryItemState(
        val stage: BrewStage,
        val recipeId: String,
        val quality: Double,
        val alcohol: Double,
        val distillCount: Int,
        val muddy: Boolean,
        val ambiguous: Boolean,
        val history: String,
        val agingStartedAt: Long,
        val finalStars: Int
    )

    fun createFermentedBottle(recipeId: String, quality: Double, muddy: Boolean, history: String): ItemStack {
        return createFermentedBottle(recipeId, quality, muddy, history, null)
    }

    fun createFermentedBottle(recipeId: String, quality: Double, muddy: Boolean, history: String, recipe: BreweryRecipe?): ItemStack {
        val item = ItemStack(Material.POTION)
        val meta = item.itemMeta ?: return item

        val displayName = if (recipe != null) {
            if (muddy) "§7泥状の${recipe.name}" else "§b${recipe.middleOutputName}"
        } else {
            if (muddy) "§7泥状の醸造物" else "§b発酵醸造物"
        }
        meta.setDisplayName(displayName)

        val loreDesc = if (recipe != null && !muddy) {
            recipe.middleOutputDescription
        } else ""

        meta.lore(
            CCSystem.getAPI().getLoreService().render(GuiLoreSpec.Auto(listOfNotNull(
                "§7レシピ: ${recipe?.name ?: recipeId}",
                "§7段階: 発酵",
                if (loreDesc.isNotEmpty()) "§f$loreDesc" else null
            ), GuiLoreFrame.NONE))
        )

        val pdc = meta.persistentDataContainer
        pdc.set(stageKey, PersistentDataType.STRING, if (muddy) BrewStage.FAILED.name else BrewStage.FERMENTED.name)
        pdc.set(recipeIdKey, PersistentDataType.STRING, recipeId)
        pdc.set(qualityKey, PersistentDataType.DOUBLE, quality)
        pdc.set(alcoholKey, PersistentDataType.DOUBLE, 0.0)
        pdc.set(distillCountKey, PersistentDataType.INTEGER, 0)
        pdc.set(muddyKey, PersistentDataType.BYTE, if (muddy) 1 else 0)
        pdc.set(ambiguousKey, PersistentDataType.BYTE, 0)
        pdc.set(historyKey, PersistentDataType.STRING, history)
        pdc.set(finalStarsKey, PersistentDataType.INTEGER, 0)

        if (recipe != null && recipe.middleOutputColor != null && !muddy) {
            applyPotionColor(meta, recipe.middleOutputColor)
        }

        item.itemMeta = meta
        return item
    }

    private fun getFinalNameByQuality(recipe: BreweryRecipe, quality: Double): String {
        val index = when {
            quality < 34 -> 0
            quality < 67 -> 1
            else -> 2
        }
        return recipe.finalOutputNames.getOrElse(index) { recipe.name }
    }

    private fun getFinalDescriptionByQuality(recipe: BreweryRecipe, quality: Double): String {
        val index = when {
            quality < 34 -> 0
            quality < 67 -> 1
            else -> 2
        }
        return recipe.finalOutputDescriptions.getOrElse(index) { "" }
    }

    private fun applyPotionColor(meta: org.bukkit.inventory.meta.ItemMeta, colorHex: String) {
        val potionMeta = meta as? org.bukkit.inventory.meta.PotionMeta ?: return
        try {
            val color = parseHexColor(colorHex)
            if (color != null) {
                potionMeta.setColor(color)
            }
        } catch (_: Exception) {
        }
    }

    private fun parseHexColor(hex: String): Color? {
        val cleanHex = hex.removePrefix("#")
        if (cleanHex.length != 6) return null
        return try {
            val r = cleanHex.substring(0, 2).toInt(16)
            val g = cleanHex.substring(2, 4).toInt(16)
            val b = cleanHex.substring(4, 6).toInt(16)
            Color.fromRGB(r, g, b)
        } catch (_: Exception) {
            null
        }
    }

    fun parse(item: ItemStack?): BreweryItemState? {
        if (item == null || item.type.isAir) return null
        val meta = item.itemMeta ?: return null
        val pdc = meta.persistentDataContainer
        val stageRaw = pdc.get(stageKey, PersistentDataType.STRING) ?: return null
        val stage = runCatching { BrewStage.valueOf(stageRaw) }.getOrNull() ?: return null
        return BreweryItemState(
            stage = stage,
            recipeId = pdc.get(recipeIdKey, PersistentDataType.STRING) ?: "unknown",
            quality = pdc.get(qualityKey, PersistentDataType.DOUBLE) ?: 0.0,
            alcohol = pdc.get(alcoholKey, PersistentDataType.DOUBLE) ?: 0.0,
            distillCount = pdc.get(distillCountKey, PersistentDataType.INTEGER) ?: 0,
            muddy = (pdc.get(muddyKey, PersistentDataType.BYTE)?.toInt() ?: 0) == 1,
            ambiguous = (pdc.get(ambiguousKey, PersistentDataType.BYTE)?.toInt() ?: 0) == 1,
            history = pdc.get(historyKey, PersistentDataType.STRING) ?: "",
            agingStartedAt = pdc.get(agingStartKey, PersistentDataType.LONG) ?: 0L,
            finalStars = pdc.get(finalStarsKey, PersistentDataType.INTEGER) ?: 0
        )
    }

    fun markDistilled(item: ItemStack, state: BreweryItemState, targetDistillCount: Int, overPenalty: Double) {
        markDistilled(item, state, targetDistillCount, overPenalty, null)
    }

    fun markDistilled(item: ItemStack, state: BreweryItemState, targetDistillCount: Int, overPenalty: Double, recipe: BreweryRecipe?) {
        val meta = item.itemMeta ?: return
        val pdc = meta.persistentDataContainer
        val over = (state.distillCount - targetDistillCount).coerceAtLeast(0)
        val quality = (state.quality - over * overPenalty).coerceIn(0.0, 100.0)
        val ambiguous = state.distillCount != targetDistillCount
        pdc.set(stageKey, PersistentDataType.STRING, BrewStage.DISTILLED.name)
        pdc.set(qualityKey, PersistentDataType.DOUBLE, quality)
        pdc.set(ambiguousKey, PersistentDataType.BYTE, if (ambiguous) 1 else 0)

        val displayName = if (recipe != null) {
            val name = getFinalNameByQuality(recipe, quality)
            if (ambiguous) "§e$name（品質低下）" else "§e$name"
        } else {
            if (ambiguous) "§e蒸留済み醸造物（品質低下）" else "§e蒸留済み醸造物"
        }
        meta.setDisplayName(displayName)
        val description = if (recipe != null) getFinalDescriptionByQuality(recipe, quality) else ""
        meta.lore(
            CCSystem.getAPI().getLoreService().render(GuiLoreSpec.Auto(listOfNotNull(
                "§7レシピ: ${recipe?.name ?: state.recipeId}",
                "§7品質: ${"%.1f".format(quality)}",
                "§7蒸留回数: ${state.distillCount}/$targetDistillCount",
                "§7アルコール: ${"%.1f".format(state.alcohol)}%",
                if (description.isNotBlank()) "§f$description" else null
            ), GuiLoreFrame.NONE))
        )

        if (recipe != null && recipe.finalOutputColor != null) {
            applyPotionColor(meta, recipe.finalOutputColor)
        }

        item.itemMeta = meta
    }

    fun incrementDistillation(item: ItemStack, targetDistillCount: Int, targetAlcohol: Double) {
        val meta = item.itemMeta ?: return
        val pdc = meta.persistentDataContainer
        val currentCount = (pdc.get(distillCountKey, PersistentDataType.INTEGER) ?: 0) + 1
        val currentAlcohol = pdc.get(alcoholKey, PersistentDataType.DOUBLE) ?: 0.0
        val nextAlcohol = if (currentCount <= targetDistillCount && targetDistillCount > 0) {
            val gain = targetAlcohol / targetDistillCount.toDouble()
            currentAlcohol + gain
        } else {
            currentAlcohol + 5.0
        }.coerceIn(0.0, 100.0)
        pdc.set(distillCountKey, PersistentDataType.INTEGER, currentCount)
        pdc.set(alcoholKey, PersistentDataType.DOUBLE, nextAlcohol)
        item.itemMeta = meta
    }

    fun setAgingStart(item: ItemStack, startedAt: Long) {
        val meta = item.itemMeta ?: return
        meta.persistentDataContainer.set(agingStartKey, PersistentDataType.LONG, startedAt)
        item.itemMeta = meta
    }

    fun clearAgingStart(item: ItemStack) {
        val meta = item.itemMeta ?: return
        meta.persistentDataContainer.remove(agingStartKey)
        item.itemMeta = meta
    }

    fun markAged(item: ItemStack, state: BreweryItemState, finalQuality: Double) {
        markAged(item, state, finalQuality, null)
    }

    fun markAged(item: ItemStack, state: BreweryItemState, finalQuality: Double, recipe: BreweryRecipe?) {
        val rounded = (finalQuality / 20.0).roundToInt().coerceIn(0, 5) * 20
        val stars = (rounded / 20).coerceIn(1, 5)
        val meta = item.itemMeta ?: return
        val pdc = meta.persistentDataContainer
        pdc.set(stageKey, PersistentDataType.STRING, BrewStage.AGED.name)
        pdc.set(qualityKey, PersistentDataType.DOUBLE, finalQuality.coerceIn(0.0, 100.0))
        pdc.set(finalStarsKey, PersistentDataType.INTEGER, stars)
        
        val displayName = if (recipe != null) {
            val name = getFinalNameByQuality(recipe, finalQuality)
            "§6$name ${"★".repeat(stars)}"
        } else {
            "§6熟成酒 ${"★".repeat(stars)}"
        }
        meta.setDisplayName(displayName)

        val desc = if (recipe != null) {
            getFinalDescriptionByQuality(recipe, finalQuality)
        } else ""
        
        meta.lore(
            CCSystem.getAPI().getLoreService().render(GuiLoreSpec.Auto(listOfNotNull(
                "§7レシピ: ${recipe?.name ?: state.recipeId}",
                "§7最終品質: ${"%.1f".format(finalQuality.coerceIn(0.0, 100.0))}",
                "§7評価: ${"★".repeat(stars)}",
                "§7アルコール: ${"%.1f".format(state.alcohol)}%",
                if (desc.isNotEmpty()) "§f$desc" else null
            ), GuiLoreFrame.NONE))
        )
        
        if (recipe != null && recipe.finalOutputColor != null) {
            applyPotionColor(meta, recipe.finalOutputColor)
        }
        
        item.itemMeta = meta
    }

    fun isSampleFilter(item: ItemStack?): Boolean {
        if (item == null || item.type.isAir) return false
        if (item.type != Material.POISONOUS_POTATO) return false
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(filterKey, PersistentDataType.BYTE)
    }

    fun buildSampleFilterItem(): ItemStack {
        val item = ItemStack(Material.POISONOUS_POTATO)
        val meta = item.itemMeta ?: return item
        meta.setItemModel(NamespacedKey.minecraft("shears"))
        meta.setDisplayName("§e蒸留フィルター（試作）")
        meta.lore(
            CCSystem.getAPI().getLoreService().render(GuiLoreSpec.Auto(listOf(
                "§7蒸留時間 -15%",
                "§7蒸留1回ごとに耐久を1消費"
            ), GuiLoreFrame.NONE))
        )
        meta.persistentDataContainer.set(filterKey, PersistentDataType.BYTE, 1)
        meta.persistentDataContainer.set(filterRemainingUsesKey, PersistentDataType.INTEGER, FILTER_MAX_USES)
        item.itemMeta = meta
        return item
    }

    fun damageFilter(item: ItemStack): Boolean {
        return damageFilter(item, 1)
    }

    fun damageFilter(item: ItemStack, amount: Int): Boolean {
        val meta = item.itemMeta ?: return true
        if (item.type == Material.POISONOUS_POTATO) {
            val remaining = meta.persistentDataContainer.get(filterRemainingUsesKey, PersistentDataType.INTEGER)
                ?: FILTER_MAX_USES
            val nextRemaining = remaining - amount.coerceAtLeast(1)
            if (nextRemaining <= 0) {
                return true
            }
            meta.persistentDataContainer.set(filterRemainingUsesKey, PersistentDataType.INTEGER, nextRemaining)
            item.itemMeta = meta
            return false
        }
        val damageable = meta as? org.bukkit.inventory.meta.Damageable ?: return true
        damageable.damage = damageable.damage + amount
        if (damageable.damage >= item.type.maxDurability) {
            return true
        }
        item.itemMeta = damageable
        return false
    }

    fun applyFailureDisplay(item: ItemStack, name: String) {
        val meta = item.itemMeta ?: return
        meta.setDisplayName(name)
        item.itemMeta = meta
    }

    fun writeHistory(item: ItemStack, history: String) {
        val meta = item.itemMeta ?: return
        meta.persistentDataContainer.set(historyKey, PersistentDataType.STRING, history)
        item.itemMeta = meta
    }

    fun getHistory(container: PersistentDataContainer): String {
        return container.get(historyKey, PersistentDataType.STRING) ?: ""
    }

    companion object {
        private const val FILTER_MAX_USES = 238
    }
}
