package jp.awabi2048.cccontent;

import jp.awabi2048.cccontent.features.brewery.BreweryIntoxicationMath;
import jp.awabi2048.cccontent.features.brewery.BreweryIntoxicationState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BreweryProgressTest {
    @Test
    void qualityCorrectedAlcoholUsesFinishedQuality() {
        assertEquals(35.0, BreweryIntoxicationMath.qualityCorrectedAlcohol(70.0, 50.0), 0.0001);
        assertEquals(0.0, BreweryIntoxicationMath.qualityCorrectedAlcohol(70.0, -1.0), 0.0001);
        assertEquals(70.0, BreweryIntoxicationMath.qualityCorrectedAlcohol(70.0, 101.0), 0.0001);
        assertEquals(-20.0, BreweryIntoxicationMath.qualityCorrectedAlcohol(-40.0, 50.0), 0.0001);
    }

    @Test
    void intoxicationDecaysFromPersistedTimestampAfterRestart() {
        var state = new BreweryIntoxicationState(70.0, 1_000L, 0L);

        BreweryIntoxicationMath.decay(state, 11_000L, 0.5, 604_800L);

        assertEquals(65.0, state.getAlcohol(), 0.0001);
        assertEquals(11_000L, state.getUpdatedAtMillis());
    }

}
