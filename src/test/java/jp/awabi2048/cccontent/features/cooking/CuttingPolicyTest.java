package jp.awabi2048.cccontent.features.cooking;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CuttingPolicyTest {
    private static final CuttingRecipeDefinition RECIPE = new CuttingRecipeDefinition(
        "cut_pumpkin", "pumpkin", "cooking.cut_pumpkin", 4,
        CuttingFoodClass.TOUGH, 2, CookingIntermediateStage.PRIMARY, 1
    );

    @Test
    void classifiesAllSpecifiedBoards() {
        assertEquals(CuttingBoardClass.SOFT, CuttingPolicy.boardClass(Material.BIRCH_PRESSURE_PLATE));
        assertEquals(CuttingBoardClass.BALANCED, CuttingPolicy.boardClass(Material.MANGROVE_PRESSURE_PLATE));
        assertEquals(CuttingBoardClass.HARD, CuttingPolicy.boardClass(Material.WARPED_PRESSURE_PLATE));
        assertNull(CuttingPolicy.boardClass(Material.STONE_PRESSURE_PLATE));
    }

    @Test
    void appliesBoardMultiplierOnlyToKnifeDamage() {
        CuttingExecution soft = CuttingPolicy.execute(RECIPE, "pumpkin", 3, CuttingBoardClass.SOFT, emptySlots());
        CuttingExecution balanced = CuttingPolicy.execute(RECIPE, "pumpkin", 3, CuttingBoardClass.BALANCED, emptySlots());
        assertNotNull(soft);
        assertNotNull(balanced);
        assertEquals(12, soft.getOutputAmount());
        assertEquals(12, balanced.getOutputAmount());
        assertEquals(12, soft.getKnifeDamage());
        assertEquals(6, balanced.getKnifeDamage());
    }

    @Test
    void fillsSameItemStacksBeforeEmptySlots() {
        List<CuttingSlot> slots = emptySlots();
        slots.set(4, new CuttingSlot("cooking.cut_pumpkin", 62, 64));
        CuttingExecution result = CuttingPolicy.execute(
            RECIPE, "pumpkin", 1, CuttingBoardClass.HARD, slots
        );
        assertNotNull(result);
        assertEquals(64, result.getResultingSlots().get(4).getAmount());
        assertEquals(2, result.getResultingSlots().get(0).getAmount());
    }

    @Test
    void rejectsWhenAllOutputsDoNotFit() {
        List<CuttingSlot> full = new ArrayList<>();
        for (int index = 0; index < 10; index++) full.add(new CuttingSlot("other", 64, 64));
        assertNull(CuttingPolicy.execute(RECIPE, "pumpkin", 1, CuttingBoardClass.HARD, full));
        assertNull(CuttingPolicy.execute(RECIPE, "wrong", 1, CuttingBoardClass.HARD, emptySlots()));
    }

    private static List<CuttingSlot> emptySlots() {
        List<CuttingSlot> slots = new ArrayList<>();
        for (int index = 0; index < 10; index++) slots.add(new CuttingSlot(null, 0, 64));
        return slots;
    }
}
