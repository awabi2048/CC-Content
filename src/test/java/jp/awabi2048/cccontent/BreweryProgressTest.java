package jp.awabi2048.cccontent;

import jp.awabi2048.cccontent.features.brewery.BreweryIntoxicationMath;
import jp.awabi2048.cccontent.features.brewery.BreweryIntoxicationState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BreweryProgressTest {
    @Test
    void intoxicationDecaysFromPersistedTimestampAfterRestart() {
        var state = new BreweryIntoxicationState(70.0, 1_000L, 0L);

        BreweryIntoxicationMath.decay(state, 11_000L, 0.5, 604_800L);

        assertEquals(65.0, state.getAlcohol(), 0.0001);
        assertEquals(11_000L, state.getUpdatedAtMillis());
    }

}
