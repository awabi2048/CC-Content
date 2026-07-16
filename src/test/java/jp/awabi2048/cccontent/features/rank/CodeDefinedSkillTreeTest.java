package jp.awabi2048.cccontent.features.rank;

import jp.awabi2048.cccontent.features.rank.profession.Profession;
import jp.awabi2048.cccontent.features.rank.profession.ProfessionType;
import jp.awabi2048.cccontent.features.rank.profession.SkillNode;
import jp.awabi2048.cccontent.features.rank.profession.SkillTree;
import jp.awabi2048.cccontent.features.rank.profession.skilltree.CodeDefinedSkillTree;
import jp.awabi2048.cccontent.features.rank.skill.handlers.FisherBonusHandler;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeDefinedSkillTreeTest {
    @Test
    void allProfessionTreesAreConnectedAndWithinTheirLevelCap() {
        for (Profession profession : Profession.values()) {
            SkillTree tree = CodeDefinedSkillTree.Companion.create(profession);
            Set<String> visited = new HashSet<>();
            visit(tree, tree.getStartSkillId(), visited);

            assertEquals(tree.getAllSkills().keySet(), visited, profession.getId());
            assertTrue(tree.getAllSkills().values().stream()
                .allMatch(skill -> skill.getRequiredLevel() <= tree.getMaxLevel()), profession.getId());
        }
    }

    @Test
    void onlyCreativeProfessionsUseTheLevel25Cap() {
        for (Profession profession : Profession.values()) {
            int maxLevel = CodeDefinedSkillTree.Companion.create(profession).getMaxLevel();
            if (profession.getType() == ProfessionType.CREATIVE) {
                assertEquals(25, maxLevel, profession.getId());
            } else {
                assertTrue(maxLevel > 25, profession.getId());
            }
        }
    }

    @Test
    void fisherTreeUsesFishingSystemEffects() {
        SkillTree tree = CodeDefinedSkillTree.Companion.create(Profession.FISHER);
        assertEquals(50, tree.getMaxLevel());
        assertEffect(tree, "patient_cast", FisherBonusHandler.HOOK_WINDOW_EFFECT);
        assertEffect(tree, "deep_water", FisherBonusHandler.STABILITY_EFFECT);
        assertEffect(tree, "master_angler", FisherBonusHandler.DURATION_EFFECT);
    }

    private static void assertEffect(SkillTree tree, String skillId, String expectedType) {
        SkillNode skill = tree.getSkill(skillId);
        assertNotNull(skill);
        assertNotNull(skill.getEffect());
        assertEquals(expectedType, skill.getEffect().getType());
    }

    private static void visit(SkillTree tree, String skillId, Set<String> visited) {
        if (!visited.add(skillId)) return;
        tree.getChildren(skillId).forEach(child -> visit(tree, child, visited));
    }
}
