package jp.awabi2048.cccontent;

import jp.awabi2048.cccontent.features.fishing.FishCatch;
import jp.awabi2048.cccontent.features.fishing.FishQuality;
import jp.awabi2048.cccontent.features.fishing.FishdexEntry;
import jp.awabi2048.cccontent.features.fishing.FishdexPage;
import jp.awabi2048.cccontent.features.fishing.FishFightProfile;
import jp.awabi2048.cccontent.features.fishing.FishingEffectivenessZone;
import jp.awabi2048.cccontent.features.fishing.FishingFightStatus;
import jp.awabi2048.cccontent.features.fishing.FishingFightScore;
import jp.awabi2048.cccontent.features.fishing.FishingFightState;
import jp.awabi2048.cccontent.features.fishing.FishingCatchSelector;
import jp.awabi2048.cccontent.features.fishing.FishingTime;
import jp.awabi2048.cccontent.features.fishing.FishingWeather;
import com.awabi2048.ccsystem.api.time.Season;
import jp.awabi2048.cccontent.features.fishing.FishRarity;
import jp.awabi2048.cccontent.features.fishing.FishingWaterCondition;
import jp.awabi2048.cccontent.features.fishing.FishingWaterProfile;
import jp.awabi2048.cccontent.features.fishing.FishingWaterType;
import jp.awabi2048.cccontent.features.fishing.FishingEnvironment;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FishingModelTest {
    @Test
    void waterConditionsUseDepthAndWidth() {
        var offshore = new FishingWaterCondition(
            FishingWaterType.DEEP_OPEN,
            new kotlin.ranges.IntRange(8, 32),
            new kotlin.ranges.IntRange(21, 33)
        );

        assertTrue(offshore.matches(new FishingWaterProfile(12, 25, Set.of(FishingEnvironment.ROCKY_DEEP))));
        assertTrue(!offshore.matches(new FishingWaterProfile(4, 25, Set.of())));
        assertTrue(!offshore.matches(new FishingWaterProfile(12, 10, Set.of())));
    }

    @Test
    void fishdexStatsAreRecordedPerFish() {
        var first = new FishCatch("cod", Material.COD, 120, FishQuality.COMMON, 20);
        var second = new FishCatch("cod", Material.COD, 80, FishQuality.RARE, 15);
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
    void fishingEffectivenessUsesGreenYellowAndOrangeBands() {
        var profile = new FishFightProfile(50.0, 4.0, 1.0, 1.0);
        assertEquals(FishingEffectivenessZone.GREEN,
            new FishingFightState(50.0, 20, 1).zone(profile, 10.0, 20.0));
        assertEquals(FishingEffectivenessZone.YELLOW,
            new FishingFightState(70.0, 20, 1).zone(profile, 10.0, 20.0));
        assertEquals(FishingEffectivenessZone.ORANGE,
            new FishingFightState(90.0, 20, 1).zone(profile, 10.0, 20.0));
    }

    @Test
    void fishingInputRaisesAndLowersEffectivenessWithinBounds() {
        var state = new FishingFightState(50.0, 20, 1);
        assertEquals(60.0, state.applyInput(10.0).getEffectiveness());
        assertEquals(0.0, state.applyInput(-100.0).getEffectiveness());
        assertEquals(100.0, state.applyInput(100.0).getEffectiveness());
    }

    @Test
    void fishDriftRespectsStabilityAndConsumesTime() {
        var profile = new FishFightProfile(50.0, 10.0, 1.0, 1.0);
        var state = new FishingFightState(50.0, 20, 1);
        var unstable = state.advance(profile, 5, 0.0, 0.25, 0.18, new Random(1));
        var stable = state.advance(profile, 5, 0.5, 0.25, 0.18, new Random(1));
        assertEquals(15, unstable.getRemainingTicks());
        assertTrue(Math.abs(unstable.getEffectiveness() - 50.0) >
            Math.abs(stable.getEffectiveness() - 50.0));
    }

    @Test
    void fishResistanceReversesWhenDirectionPersistenceEnds() {
        var profile = new FishFightProfile(50.0, 10.0, 0.5, 1.0);
        var state = new FishingFightState(50.0, 20, 1);
        var reversed = state.advance(profile, 5, 0.0, 0.25, 0.18, new Random() {
            @Override
            public double nextDouble() {
                return 0.9;
            }
        });

        assertEquals(-1, reversed.getDriftDirection());
        assertEquals(47.5, reversed.getEffectiveness());
        assertEquals(-2.5, reversed.getDriftVelocity());
    }

    @Test
    void fightStatusHasTwoMessagesForEveryEffectivenessZone() {
        for (var zone : FishingEffectivenessZone.values()) {
            assertEquals(2, FishingFightStatus.Companion.candidates(zone).size());
        }
    }

    @Test
    void fishQualityUsesOnlyCanonicalStoredIds() {
        assertEquals(3, FishQuality.values().length);
        assertEquals("§e★§7☆☆", FishQuality.COMMON.getStars());
        assertEquals("§e★★§7☆", FishQuality.RARE.getStars());
        assertEquals("§e★★★", FishQuality.LEGENDARY.getStars());
        assertEquals(FishQuality.COMMON, FishQuality.Companion.fromStoredId("common"));
        assertEquals(FishQuality.RARE, FishQuality.Companion.fromStoredId("rare"));
        assertEquals(FishQuality.LEGENDARY, FishQuality.Companion.fromStoredId("legendary"));
        assertThrows(IllegalStateException.class, () -> FishQuality.Companion.fromStoredId("uncommon"));
        assertThrows(IllegalStateException.class, () -> FishQuality.Companion.fromStoredId("epic"));
    }

    @Test
    void weatherTimeAndSeasonPreferencesAdjustWeightWithoutExcludingTheFish() {
        assertEquals(1.0, FishingCatchSelector.preferenceMultiplier(
            Set.of(FishingWeather.RAIN),
            Set.of(FishingTime.NIGHT),
            Set.of(Season.SUMMER),
            Set.of(),
            FishingWeather.RAIN,
            FishingTime.NIGHT,
            Season.SUMMER
        ));
        assertEquals(0.75 * 0.8 * 0.8, FishingCatchSelector.preferenceMultiplier(
            Set.of(FishingWeather.RAIN),
            Set.of(FishingTime.NIGHT),
            Set.of(Season.SUMMER),
            Set.of(),
            FishingWeather.CLEAR,
            FishingTime.DAY,
            Season.WINTER
        ), 0.000001);
        assertTrue(FishingCatchSelector.preferenceMultiplier(
            Set.of(FishingWeather.RAIN),
            Set.of(FishingTime.NIGHT),
            Set.of(Season.SUMMER),
            Set.of(Season.WINTER),
            FishingWeather.CLEAR,
            FishingTime.DAY,
            Season.SPRING
        ) > 0.0);
    }

    @Test
    void localEnvironmentPreferencesAdjustWeightWithoutExcludingTheFish() {
        assertEquals(1.0, FishingCatchSelector.environmentPreferenceMultiplier(
            Set.of(FishingEnvironment.ESTUARY),
            Set.of(FishingEnvironment.ESTUARY, FishingEnvironment.SAND_BOTTOM)
        ));
        assertEquals(0.8, FishingCatchSelector.environmentPreferenceMultiplier(
            Set.of(FishingEnvironment.ESTUARY),
            Set.of(FishingEnvironment.ROCKY_DEEP)
        ));
    }

    @Test
    void fightScoreUsesTheWholeFightInsteadOfTheLastTick() {
        var mostlyGreen = new FishingFightScore();
        for (int i = 0; i < 99; i++) {
            mostlyGreen = mostlyGreen.record(FishingEffectivenessZone.GREEN, 50.0, 1);
        }
        mostlyGreen = mostlyGreen.record(FishingEffectivenessZone.ORANGE, 0.0, 1);

        var mostlyDanger = new FishingFightScore();
        for (int i = 0; i < 99; i++) {
            mostlyDanger = mostlyDanger.record(FishingEffectivenessZone.ORANGE, 0.0, 1);
        }
        mostlyDanger = mostlyDanger.record(FishingEffectivenessZone.GREEN, 50.0, 1);

        assertTrue(mostlyGreen.normalizedScore() > 0.98);
        assertTrue(mostlyDanger.normalizedScore() < 0.02);
        assertTrue(mostlyGreen.successProbability(FishRarity.COMMON) >
            mostlyDanger.successProbability(FishRarity.COMMON));
    }

    @Test
    void fightScoreIsNormalizedAcrossDifferentFightDurations() {
        var shortFight = new FishingFightScore()
            .record(FishingEffectivenessZone.GREEN, 50.0, 10)
            .record(FishingEffectivenessZone.YELLOW, 70.0, 10);
        var longFight = new FishingFightScore()
            .record(FishingEffectivenessZone.GREEN, 50.0, 100)
            .record(FishingEffectivenessZone.YELLOW, 70.0, 100);

        assertEquals(shortFight.normalizedScore(), longFight.normalizedScore(), 0.000001);
        assertTrue(shortFight.successProbability(FishRarity.COMMON) >
            shortFight.successProbability(FishRarity.SPECIAL));
    }
}
