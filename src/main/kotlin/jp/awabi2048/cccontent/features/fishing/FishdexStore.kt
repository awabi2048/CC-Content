package jp.awabi2048.cccontent.features.fishing

import jp.awabi2048.cccontent.features.catalog.CatalogStore
import jp.awabi2048.cccontent.features.catalog.CatalogType
import java.util.UUID

/** 釣り側の既存APIを共通図鑑ストアへ接続するアダプタ。独自ファイルは持たない。 */
class FishdexStore(private val store: CatalogStore) {
    fun load(playerId: UUID): MutableMap<String, FishdexEntry> =
        store.entries(playerId, CatalogType.FISHING).mapValues { (fishId, entry) ->
            FishdexEntry(fishId, entry.discovered, entry.obtainedCount, entry.maximumWeight, entry.minimumWeight,
                FishQuality.normalizeStoredCounts(entry.qualityCounts))
        }.toMutableMap()

    fun record(playerId: UUID, catch: FishCatch): FishdexEntry {
        val entry = store.record(playerId, CatalogType.FISHING, catch.fishId, catch.quality.id,
            (catch.quality.ordinal + 1) * 100.0 / FishQuality.entries.size, weight = catch.weightGrams)
        return FishdexEntry(catch.fishId, true, entry.obtainedCount, entry.maximumWeight, entry.minimumWeight,
            FishQuality.normalizeStoredCounts(entry.qualityCounts))
    }

    fun save(@Suppress("UNUSED_PARAMETER") playerId: UUID, @Suppress("UNUSED_PARAMETER") entries: Map<String, FishdexEntry>) {
        store.save()
    }
}
