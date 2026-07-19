package jp.awabi2048.cccontent.features.fishing;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FishingWorldBoundarySourceContractTest {
    private static final Path SOURCE = Path.of(
        "src/main/kotlin/jp/awabi2048/cccontent/features/fishing/FishingFeature.kt"
    );

    @Test
    void nonReadyResourceWorldLeavesVanillaFishingUntouched() throws Exception {
        String source = readSource();

        assertTrue(source.contains(
            "if (!isReadyResourceWorld(player)) {\n" +
                "            return\n" +
                "        }"
        ));
        assertTrue(source.contains("getResourceWorldLifecycleService().isReady(player.world.key)"));
        assertFalse(source.contains("fishing.error.resource_world_only"));
    }

    @Test
    void vanillaCatchIsCancelledOnlyForContentFishingSession() throws Exception {
        String source = readSource();

        assertTrue(source.contains("val session = sessions[player.uniqueId]"));
        assertTrue(source.contains("if (session != null) {\n                    event.isCancelled = true"));
    }

    @Test
    void fightOwnsBobberAndRodMissingUsesGracePeriod() throws Exception {
        String source = readSource();

        assertTrue(source.contains("session.hook.teleport(location)"));
        assertTrue(source.contains("session.hook.velocity = Vector()"));
        assertTrue(source.contains("if (!isValidFishingWater(next)) return"));
        assertTrue(source.contains("session.rodMissingTicks >= 15L"));
        assertTrue(source.contains("Phase.HOOK_WINDOW -> {"));
        assertFalse(source.contains("fail(event.player.uniqueId, \"fishing.failed.no_rod\")"));
    }

    @Test
    void hookWindowAcceptsAllRightClickFishingEventRoutes() throws Exception {
        String source = readSource();

        assertTrue(source.contains("Phase.HOOK_WINDOW -> startFight(player, session)"));
        assertTrue(source.contains(
            "Phase.HOOK_WINDOW -> if (right) {\n" +
                "                event.isCancelled = true\n" +
                "                startFight(event.player, session)"
        ));
    }

    @Test
    void dictionaryAndSearchUseTheSameTenBlockWaterSurvey() throws Exception {
        String source = readSource();

        assertTrue(source.contains("player.rayTraceBlocks(10.0, FluidCollisionMode.ALWAYS)"));
        assertTrue(source.contains("val block = surveyWaterBlock(player)"));
        assertTrue(source.contains("val surveyBlock = surveyWaterBlock(player)"));
        assertTrue(source.contains("candidateNames.takeIf(List<String>::isNotEmpty)"));
        assertTrue(source.contains("message(player, \"fishing.dictionary.hint.none\")"));
    }

    @Test
    void baitIsConsumedOnlyAfterAValidCandidateIsSelected() throws Exception {
        String source = readSource();
        String startCast = source.substring(source.indexOf("private fun startCast"), source.indexOf("private fun onBite"));
        String onBite = source.substring(source.indexOf("private fun onBite"), source.indexOf("@EventHandler\n    fun onRodClick"));

        assertTrue(startCast.contains("items.resolveBait(player.inventory.itemInOffHand)"));
        assertFalse(startCast.contains("consumeBait"));
        assertTrue(onBite.indexOf("if (selected == null)") < onBite.indexOf("items.consumeBait"));
    }

    @Test
    void fightUsesCumulativeScoreAndLocalizedHitTitle() throws Exception {
        String source = readSource();

        assertTrue(source.contains("session.fightScore = session.fightScore.record("));
        assertTrue(source.contains("session.fightScore.successProbability(definition.rarity)"));
        assertTrue(source.contains("message(player, \"fishing.fight.hit_title\")"));
        assertFalse(source.contains("Component.text(\"§6HIT!\")"));
        assertFalse(source.contains("zone.successChance"));
    }

    private static String readSource() throws Exception {
        return Files.readString(SOURCE, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
