package jp.awabi2048.cccontent;

import com.awabi2048.ccsystem.api.gui.MenuActionSafety;
import jp.awabi2048.cccontent.gui.ContentMenuActionSafety;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContentMenuActionSafetyTest {
    @Test
    void everyMigratedActionHasAnExplicitSafetyClassification() {
        List.of(
            "open_journal", "open_detail", "set_search", "clear_search", "brewery_action",
            "start", "stop", "participants", "toggle_participant", "adjust_time_increase",
            "adjust_time_decrease", "adjust_preparation_increase", "adjust_preparation_decrease",
            "history_recent", "history_top", "close", "profession_select", "profession_confirm",
            "profession_cancel", "settings", "recruiting", "disband", "chat", "invite"
        ).forEach(action -> assertNotEquals(
            MenuActionSafety.UNSPECIFIED,
            ContentMenuActionSafety.INSTANCE.safetyFor(action),
            action
        ));
    }

    @Test
    void classifiesStateChangingActionsConservatively() {
        assertEquals(MenuActionSafety.IRREVERSIBLE, ContentMenuActionSafety.INSTANCE.safetyFor("brewery_action"));
        assertEquals(MenuActionSafety.IRREVERSIBLE, ContentMenuActionSafety.INSTANCE.safetyFor("adjust_time_increase"));
        assertEquals(MenuActionSafety.EXTERNAL_SIDE_EFFECT, ContentMenuActionSafety.INSTANCE.safetyFor("recruiting"));
        assertEquals(MenuActionSafety.CONFIRM_ENTRY, ContentMenuActionSafety.INSTANCE.safetyFor("profession_select"));
        assertEquals(MenuActionSafety.IRREVERSIBLE, ContentMenuActionSafety.INSTANCE.safetyFor("profession_confirm"));
    }

    @Test
    void rejectsUnclassifiedActionsInsteadOfFallingBackToUnspecified() {
        assertThrows(IllegalStateException.class, () -> ContentMenuActionSafety.INSTANCE.safetyFor("unknown"));
    }
}
