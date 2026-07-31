package jp.awabi2048.cccontent.features.rank;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfessionGuildRuntimeContractTest {
    private static final Path RANK_COMMAND = Path.of(
        "src/main/kotlin/jp/awabi2048/cccontent/features/rank/command/RankCommand.kt"
    );

    @Test
    void guildAndConfirmationStayOnTheSharedInventoryRuntime() throws IOException {
        String source = Files.readString(RANK_COMMAND, StandardCharsets.UTF_8);

        assertTrue(source.contains("id = PROFESSION_GUILD_MENU_ID"));
        assertTrue(source.contains("id = PROFESSION_CONFIRM_MENU_ID"));
        assertTrue(source.contains("getGuiLayoutService().confirmation45()"));
        assertTrue(source.contains("gui.menuEntry("));
        assertTrue(source.contains("GuiMenuEntrySpec("));
        assertTrue(source.contains("displayElement("));
        assertTrue(source.contains(").lines().map { line ->"));
        assertFalse(source.contains("MenuElement("));
        assertFalse(source.contains("ProfessionSelectionGuiHolder"));
        assertFalse(source.contains("showProfessionConfirmDialog"));
    }

    @Test
    void playerHeadNameUsesTheSharedNameRenderer() throws IOException {
        String source = Files.readString(RANK_COMMAND, StandardCharsets.UTF_8);

        assertTrue(source.contains("private fun createPlayerHeadDisplay(player: Player): DisplayItemSpec"));
        assertTrue(source.contains("toComponent(\"§a§l${player.name}\")"));
        assertTrue(source.contains("player.uniqueId,"));
    }
}
