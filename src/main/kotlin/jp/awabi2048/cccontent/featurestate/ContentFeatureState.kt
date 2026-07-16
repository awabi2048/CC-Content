package jp.awabi2048.cccontent.featurestate

import java.util.Locale

enum class FeatureState {
    ENABLED,
    DISABLED
}

enum class FeatureStateOperation {
    ENABLE,
    DISABLE
}

enum class FeatureStateResultType {
    CHANGED,
    NO_CHANGE,
    UNKNOWN_FEATURE,
    MISSING_DEPENDENCY,
    ENABLED_DEPENDENCY
}

/** コマンド層が表示内容を組み立てられるよう、判定結果をデータだけで返す。 */
data class FeatureStateResult(
    val operation: FeatureStateOperation,
    val requestedId: String,
    val featureId: String?,
    val resultType: FeatureStateResultType,
    val relatedFeatureIds: List<String> = emptyList()
)

data class FeatureStatus(
    val feature: ContentFeature,
    val state: FeatureState
)

class ContentFeatureState @JvmOverloads constructor(
    initiallyEnabled: Set<String> = emptySet(),
    private val catalog: List<ContentFeature> = ContentFeatureCatalog.features
) {
    private val featuresById = catalog.associateBy(ContentFeature::id)
    private val enabledIds = initiallyEnabled
        .map { it.lowercase(Locale.ROOT) }
        .filter { it in featuresById }
        .toMutableSet()

    init {
        require(catalog.map(ContentFeature::id).distinct().size == catalog.size) {
            "Feature IDs must be unique"
        }
        require(catalog.all { feature -> feature.dependencies.all(featuresById::containsKey) }) {
            "Feature dependencies must reference known features"
        }
    }

    fun statuses(): List<FeatureStatus> = catalog.map { feature ->
        FeatureStatus(feature, if (feature.id in enabledIds) FeatureState.ENABLED else FeatureState.DISABLED)
    }

    fun enabledFeatureIds(): List<String> = catalog.map(ContentFeature::id).filter(enabledIds::contains)

    fun stateOf(rawId: String): FeatureState? = resolve(rawId)?.let {
        if (it.id in enabledIds) FeatureState.ENABLED else FeatureState.DISABLED
    }

    fun enable(rawId: String): FeatureStateResult {
        val feature = resolve(rawId) ?: return FeatureStateResult(
            FeatureStateOperation.ENABLE, rawId, null, FeatureStateResultType.UNKNOWN_FEATURE
        )
        if (feature.id in enabledIds) {
            return result(FeatureStateOperation.ENABLE, rawId, feature, FeatureStateResultType.NO_CHANGE)
        }
        val missing = feature.dependencies.filterNot(enabledIds::contains)
        if (missing.isNotEmpty()) {
            return result(FeatureStateOperation.ENABLE, rawId, feature, FeatureStateResultType.MISSING_DEPENDENCY, missing)
        }
        enabledIds += feature.id
        return result(FeatureStateOperation.ENABLE, rawId, feature, FeatureStateResultType.CHANGED)
    }

    fun disable(rawId: String): FeatureStateResult {
        val feature = resolve(rawId) ?: return FeatureStateResult(
            FeatureStateOperation.DISABLE, rawId, null, FeatureStateResultType.UNKNOWN_FEATURE
        )
        if (feature.id !in enabledIds) {
            return result(FeatureStateOperation.DISABLE, rawId, feature, FeatureStateResultType.NO_CHANGE)
        }
        val enabledDependents = catalog
            .filter { feature.id in it.dependencies && it.id in enabledIds }
            .map(ContentFeature::id)
        if (enabledDependents.isNotEmpty()) {
            return result(
                FeatureStateOperation.DISABLE,
                rawId,
                feature,
                FeatureStateResultType.ENABLED_DEPENDENCY,
                enabledDependents
            )
        }
        enabledIds -= feature.id
        return result(FeatureStateOperation.DISABLE, rawId, feature, FeatureStateResultType.CHANGED)
    }

    private fun resolve(rawId: String): ContentFeature? = featuresById[rawId.lowercase(Locale.ROOT)]

    private fun result(
        operation: FeatureStateOperation,
        requestedId: String,
        feature: ContentFeature,
        resultType: FeatureStateResultType,
        relatedFeatureIds: List<String> = emptyList()
    ) = FeatureStateResult(operation, requestedId, feature.id, resultType, relatedFeatureIds)
}
