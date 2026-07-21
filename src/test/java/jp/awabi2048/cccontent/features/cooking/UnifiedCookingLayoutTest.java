package jp.awabi2048.cccontent.features.cooking;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class UnifiedCookingLayoutTest {
    @Test
    void matchesSpecifiedFortyFiveSlotContracts() {
        assertEquals(List.of(11, 12, 13, 14, 15, 20, 21, 22, 23, 24), UnifiedCookingLayout.INSTANCE.getCUTTING_WORK());
        assertEquals(List.of(20, 21, 22, 23, 24), UnifiedCookingLayout.INSTANCE.getTIMED_WORK());
        assertEquals(List.of(22, 21, 23, 20, 24), UnifiedCookingLayout.INSTANCE.getTIMED_OUTPUT_ORDER());
        assertEquals(4, UnifiedCookingLayout.INFO);
        assertEquals(36, UnifiedCookingLayout.CLOSE);
        assertEquals(38, UnifiedCookingLayout.TOOL);
        assertEquals(40, UnifiedCookingLayout.START);
        assertEquals(42, UnifiedCookingLayout.STATE);
        assertEquals(44, UnifiedCookingLayout.HEAT);
        assertEquals(10, Set.copyOf(UnifiedCookingLayout.INSTANCE.getCUTTING_WORK()).size());
    }
}
