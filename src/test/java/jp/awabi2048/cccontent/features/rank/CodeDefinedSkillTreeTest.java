package jp.awabi2048.cccontent.features.rank;

import jp.awabi2048.cccontent.features.rank.profession.Profession;
import jp.awabi2048.cccontent.features.rank.profession.ProfessionType;
import jp.awabi2048.cccontent.features.rank.profession.SkillTree;
import jp.awabi2048.cccontent.features.rank.profession.skilltree.CodeDefinedSkillTree;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeDefinedSkillTreeTest {
    @Test
    void allProfessionTreesAreConnectedAndWithinTheirLevelCap() {
        for (Profession profession : java.util.Arrays.asList(Profession.values())) {
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
        for (Profession profession : java.util.Arrays.asList(Profession.values())) {
            int maxLevel = CodeDefinedSkillTree.Companion.create(profession).getMaxLevel();
            if (profession.getType() == ProfessionType.CREATIVE) {
                assertEquals(25, maxLevel, profession.getId());
            } else {
                assertTrue(maxLevel > 25, profession.getId());
            }
        }
    }

    @Test
    void everyProfessionHasACodeDefinedSkillTree() {
        for (Profession profession : Profession.values()) {
            SkillTree tree = CodeDefinedSkillTree.Companion.create(profession);
            assertEquals(profession.getId(), tree.getProfessionId());
        }
    }

    private static void visit(SkillTree tree, String skillId, Set<String> visited) {
        if (!visited.add(skillId)) return;
        tree.getChildren(skillId).forEach(child -> visit(tree, child, visited));
    }
}
