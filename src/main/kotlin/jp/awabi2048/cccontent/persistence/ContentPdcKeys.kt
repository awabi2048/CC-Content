package jp.awabi2048.cccontent.persistence

import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType

/** CC-Contentが外部保存へ使用するPDCキーを一元管理する。 */
object ContentPdcKeys {
    const val NEW_NAMESPACE = "cccontent"
    const val LEGACY_NAMESPACE = "cc-content"

    fun current(key: String): NamespacedKey = NamespacedKey(NEW_NAMESPACE, key)
    fun legacy(key: String): NamespacedKey = NamespacedKey(LEGACY_NAMESPACE, key)

    val customItemId = current("custom_item_id")
    val resourceId = current("resource_id")
    val resourceTags = current("resource_tags")
    val fishId = current("fish_id")
    val fishItemSchema = current("fish_item_schema")
    val cookingRecipeId = current("cooking_recipe_id")
    val cookingStage = current("cooking_stage")
    val brewFamilyId = current("brew_family_id")
    val brewStage = current("brew_stage")
    val brewQuality = current("brew_quality")
    val distillationCount = current("distillation_count")
    val gyotakuRecordId = current("catch_record_id")
    val itemSchemaVersion = current("item_schema_version")

    /**
     * 正規キーを優先して読み、旧名前空間しかない場合は同値を正規キーへ移す。
     * 戻り値のchangedは呼出側がItemMetaをItemStackへ再設定する必要があることを示す。
     */
    fun <P : Any, C : Any> readAndMigrate(
        container: PersistentDataContainer,
        key: String,
        type: PersistentDataType<P, C>,
        onConflict: ((String) -> Unit)? = null
    ): MigratedValue<C> {
        val currentKey = current(key)
        val legacyKey = legacy(key)
        val currentValue = container.get(currentKey, type)
        val legacyValue = container.get(legacyKey, type)

        if (currentValue != null) {
            if (legacyValue != null) {
                if (currentValue != legacyValue) onConflict?.invoke(key)
                container.remove(legacyKey)
                return MigratedValue(currentValue, true)
            }
            return MigratedValue(currentValue, false)
        }
        if (legacyValue == null) return MigratedValue(null, false)

        container.set(currentKey, type, legacyValue)
        container.remove(legacyKey)
        return MigratedValue(legacyValue, true)
    }

    data class MigratedValue<T>(val value: T?, val changed: Boolean)
}
