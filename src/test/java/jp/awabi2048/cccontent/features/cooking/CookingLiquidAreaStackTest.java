package jp.awabi2048.cccontent.features.cooking;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CookingLiquidAreaStackTest {
    @Test
    void popsTheLastInsertedRemovableItem() {
        var first = new CookingLiquidAreaItem("first", true);
        var second = new CookingLiquidAreaItem("second", true);

        var removal = CookingLiquidAreaStack.INSTANCE.pop(List.of(first, second));

        assertNotNull(removal);
        assertEquals(second, removal.getItem());
        assertEquals(List.of(first), removal.getRemaining());
    }

    @Test
    void aNonRemovableTopItemBlocksEarlierItems() {
        var first = new CookingLiquidAreaItem("first", true);
        var fixed = new CookingLiquidAreaItem("fixed", false);

        assertNull(CookingLiquidAreaStack.INSTANCE.pop(List.of(first, fixed)));
    }

    @Test
    void acceptsExactlyFiveMaterialEntriesAndRejectsTheSixth() {
        var entries = new ArrayList<CookingLiquidAreaItem>();
        for (int index = 0; index < CookingLiquidAreaStack.MAX_ENTRIES; index++) {
            entries.add(new CookingLiquidAreaItem("item-" + index, true));
        }

        assertEquals(5, entries.size());
        assertNull(CookingLiquidAreaStack.INSTANCE.push(
            entries,
            new CookingLiquidAreaItem("item-5", true)
        ));
    }
}
