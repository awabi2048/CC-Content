package jp.awabi2048.cccontent.features.cooking;

import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CookingInventoryInteractionPolicyTest {
    @Test
    void 入力スロットでは通常のインベントリ操作を許可する() {
        assertTrue(CookingInventoryInteractionPolicy.INSTANCE.allowsInputClick(ClickType.LEFT));
        assertTrue(CookingInventoryInteractionPolicy.INSTANCE.allowsInputClick(ClickType.RIGHT));
        assertTrue(CookingInventoryInteractionPolicy.INSTANCE.allowsInputClick(ClickType.SHIFT_LEFT));
        assertTrue(CookingInventoryInteractionPolicy.INSTANCE.allowsInputClick(ClickType.NUMBER_KEY));
        assertTrue(CookingInventoryInteractionPolicy.INSTANCE.allowsInputClick(ClickType.SWAP_OFFHAND));
        assertTrue(CookingInventoryInteractionPolicy.INSTANCE.allowsInputClick(ClickType.DOUBLE_CLICK));
        assertFalse(CookingInventoryInteractionPolicy.INSTANCE.allowsInputClick(ClickType.CREATIVE));
        assertFalse(CookingInventoryInteractionPolicy.INSTANCE.allowsInputClick(ClickType.UNKNOWN));
    }

    @Test
    void 入力ワークスペースはアイドル中と処理中に利用できる() {
        assertTrue(CookingInventoryInteractionPolicy.INSTANCE.allowsInputWorkspace(null));
        assertTrue(CookingInventoryInteractionPolicy.INSTANCE.allowsInputWorkspace(CookingProcessState.PROCESSING_NORMAL));
        assertTrue(CookingInventoryInteractionPolicy.INSTANCE.allowsInputWorkspace(CookingProcessState.PAUSED_WRONG_HEAT));
        assertFalse(CookingInventoryInteractionPolicy.INSTANCE.allowsInputWorkspace(CookingProcessState.READY_ITEM));
        assertFalse(CookingInventoryInteractionPolicy.INSTANCE.allowsInputWorkspace(CookingProcessState.READY_LIQUID));
    }

    @Test
    void シフト移送は利用可能な通常入力スロットだけへ限定する() {
        assertEquals(
            List.of(20, 22),
            CookingInventoryInteractionPolicy.INSTANCE.transferableInputSlots(
                List.of(20, 21, 22), Set.of(21), Set.of(), true
            )
        );
        assertEquals(
            List.of(),
            CookingInventoryInteractionPolicy.INSTANCE.transferableInputSlots(
                List.of(20, 21, 22), Set.of(), Set.of(), false
            )
        );
    }
}
