package jp.awabi2048.cccontent.items.misc

import org.bukkit.Material

data class CustomHeadChoice(
    val hdbId: String,
    val displayName: String?,
    val lore: List<String>
)

data class CustomHeadVariant(
    val variantId: String,
    val itemMaterial: Material,
    val itemDisplayName: String,
    val itemLore: List<String>,
    val itemCustomModelData: Int?,
    val guiTitle: String,
    val themeIconMaterial: Material,
    val themeIconName: String,
    val heads: List<CustomHeadChoice>
)
