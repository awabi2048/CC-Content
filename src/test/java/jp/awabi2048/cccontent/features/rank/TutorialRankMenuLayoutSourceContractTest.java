package jp.awabi2048.cccontent.features.rank;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TutorialRankMenuLayoutSourceContractTest {
    @Test
    void allTutorialRanksShareOneRouteWithoutChangingRankLoreBuilder() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/kotlin/jp/awabi2048/cccontent/features/rank/command/RankCommand.kt"
        )).replace("\r\n", "\n");

        assertTrue(source.contains("TutorialRank.NEWBIE to 18"));
        assertTrue(source.contains("TutorialRank.VISITOR to 20"));
        assertTrue(source.contains("TutorialRank.PIONEER to 22"));
        assertTrue(source.contains("TutorialRank.ADVENTURER to 24"));
        assertTrue(source.contains("TutorialRank.ATTAINER to 26"));
        assertTrue(source.contains("createTutorialRankItem(viewer, tutorial, rank, currentRank)"));
        assertFalse(source.contains("TUTORIAL_MENU_PAGE_FIRST"));
        assertFalse(source.contains("createTutorialPageArrowItem"));
    }
}
