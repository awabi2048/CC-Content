package jp.awabi2048.cccontent.util

import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.meta.ItemMeta

object ItemMetaCompat {
    private val setCustomModelDataMethod = ItemMeta::class.java.methods.firstOrNull {
        it.name == "setCustomModelData" &&
            it.parameterTypes.size == 1 &&
            (it.parameterTypes[0].name == "int" || it.parameterTypes[0].name == "java.lang.Integer")
    }
    private val getCustomModelDataMethod = ItemMeta::class.java.methods.firstOrNull {
        it.name == "getCustomModelData" && it.parameterCount == 0
    }
    private val hasCustomModelDataMethod = ItemMeta::class.java.methods.firstOrNull {
        it.name == "hasCustomModelData" && it.parameterCount == 0
    }
    private val additionalTooltipFlag = runCatching {
        ItemFlag.valueOf("HIDE_ADDITIONAL_TOOLTIP")
    }.getOrNull()

    fun setLegacyCustomModelData(meta: ItemMeta, value: Int) {
        setCustomModelDataMethod?.invoke(meta, value)
    }

    fun getLegacyCustomModelData(meta: ItemMeta): Int? {
        if (!hasLegacyCustomModelData(meta)) return null
        return getCustomModelDataMethod?.invoke(meta) as? Int
    }

    fun hasLegacyCustomModelData(meta: ItemMeta): Boolean {
        return hasCustomModelDataMethod?.invoke(meta) as? Boolean ?: false
    }

    fun hideAdditionalTooltip(meta: ItemMeta) {
        additionalTooltipFlag?.let { meta.addItemFlags(it) }
    }
}
