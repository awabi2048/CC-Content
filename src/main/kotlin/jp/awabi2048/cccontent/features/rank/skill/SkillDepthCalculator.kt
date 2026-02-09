package jp.awabi2048.cccontent.features.rank.skill

import jp.awabi2048.cccontent.features.rank.profession.SkillTree

object SkillDepthCalculator {
    fun calculateDepth(skillId: String, skillTree: SkillTree, maxDepth: Int = 100): Int {
        val visited = mutableSetOf<String>()
        return calculateDepthInternal(skillId, skillTree, visited, 0, maxDepth)
    }

    private fun calculateDepthInternal(
        skillId: String,
        skillTree: SkillTree,
        visited: MutableSet<String>,
        currentDepth: Int,
        maxDepth: Int
    ): Int {
        if (currentDepth > maxDepth) {
            return maxDepth
        }
        if (skillId in visited) {
            return maxDepth
        }
        visited.add(skillId)

        val parents = skillTree.getParents(skillId)
        if (parents.isEmpty()) {
            return 0
        }

        var maxParentDepth = 0
        for (parentId in parents) {
            val parentDepth = calculateDepthInternal(parentId, skillTree, visited, currentDepth + 1, maxDepth)
            if (parentDepth > maxParentDepth) {
                maxParentDepth = parentDepth
            }
        }

        return maxParentDepth + 1
    }
}
