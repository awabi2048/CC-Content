package jp.awabi2048.cccontent;

import jp.awabi2048.cccontent.features.fishing.FishCatch;
import jp.awabi2048.cccontent.features.fishing.FishQuality;
import jp.awabi2048.cccontent.features.fishing.FishdexEntry;
import jp.awabi2048.cccontent.features.fishing.FishdexPage;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FishingModelTest {
    @Test
    void fishdexStatsAreRecordedPerFish() {
        var first = new FishCatch("cod", Material.COD, 120, FishQuality.COMMON, 20, 5);
        var second = new FishCatch("cod", Material.COD, 80, FishQuality.RARE, 15, 8);
        var entry = new FishdexEntry("cod", false, 0L, null, null, Map.of()).record(first).record(second);

        assertTrue(entry.getDiscovered());
        assertEquals(2L, entry.getTotal());
        assertEquals(120, entry.getMaximumWeight());
        assertEquals(80, entry.getMinimumWeight());
        assertEquals(1L, entry.getQualityCounts().get(FishQuality.RARE));
    }

    @Test
    void fishdexPageBoundariesClampWithoutOverflow() {
        var entries = java.util.stream.IntStream.range(0, 91)
            .mapToObj(i -> new FishdexEntry("fish-" + i, false, 0L, null, null, Map.of()))
            .toList();

        assertEquals(5, FishdexPage.pageCount(entries.size(), 21));
        assertEquals(21, FishdexPage.slice(entries, 0, 21).size());
        assertEquals(7, FishdexPage.slice(entries, 4, 21).size());
        assertEquals(7, FishdexPage.slice(entries, 99, 21).size());
        assertEquals(21, FishdexPage.slice(entries, -99, 21).size());
    }

    @Test
    void fishingInputRequiresRodAndAlternatesLeftAndRight() {
        var state = new jp.awabi2048.cccontent.features.fishing.FishingInputState(0, 2, true);
        var first = state.accept(true, true);
        assertEquals(jp.awabi2048.cccontent.features.fishing.FishingInputResult.ACCEPTED, first.getFirst());
        assertEquals(false, first.getSecond().getExpectedLeft());
        assertEquals(jp.awabi2048.cccontent.features.fishing.FishingInputResult.FAILED, first.getSecond().accept(true, true).getFirst());
        assertEquals(jp.awabi2048.cccontent.features.fishing.FishingInputResult.FAILED, first.getSecond().accept(false, false).getFirst());
        assertEquals(jp.awabi2048.cccontent.features.fishing.FishingInputResult.COMPLETE, first.getSecond().accept(false, true).getFirst());
    }

    @Test
    void fishingInputNormalizationPreservesJavaAlternation() {
        assertTrue(jp.awabi2048.cccontent.features.fishing.FishingInputNormalizer.normalize(true, true, false));
        var state = new jp.awabi2048.cccontent.features.fishing.FishingInputState(0, 2, false);
        assertEquals(jp.awabi2048.cccontent.features.fishing.FishingInputResult.FAILED,
            state.accept(jp.awabi2048.cccontent.features.fishing.FishingInputNormalizer.normalize(true, false, false), true).getFirst());
    }

    @Test
    void fishingInputNormalizationAcceptsEitherBedrockClick() {
        var state = new jp.awabi2048.cccontent.features.fishing.FishingInputState(0, 2, true);
        var first = state.accept(jp.awabi2048.cccontent.features.fishing.FishingInputNormalizer.normalize(false, true, true), true);
        var second = first.getSecond().accept(
            jp.awabi2048.cccontent.features.fishing.FishingInputNormalizer.normalize(true, first.getSecond().getExpectedLeft(), true),
            true
        );

        assertEquals(jp.awabi2048.cccontent.features.fishing.FishingInputResult.ACCEPTED, first.getFirst());
        assertEquals(false, first.getSecond().getExpectedLeft());
        assertEquals(jp.awabi2048.cccontent.features.fishing.FishingInputResult.COMPLETE, second.getFirst());
    }
}
