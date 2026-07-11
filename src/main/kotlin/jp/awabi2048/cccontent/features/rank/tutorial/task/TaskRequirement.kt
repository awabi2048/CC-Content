package jp.awabi2048.cccontent.features.rank.tutorial.task

/**
 * チュートリアルランクの固定要件。
 *
 * ランク経路は運用で変更しない前提のため、要件はYAMLではなく型付きの進捗項目で保持する。
 */
data class TaskRequirement(
    val playTimeMin: Int = 0,
    val activeOverworldMin: Int = 0,
    val activeNetherResourceMin: Int = 0,
    val diamondOreMines: Int = 0,
    val enderEyeCrafts: Int = 0,
    val requiresMyWorldCreated: Boolean = false,
    val requiresNetherPortalIgnited: Boolean = false,
    val requiresEndPortalOpened: Boolean = false,
    val mobKills: Map<String, Int> = emptyMap(),
    val blockMines: Map<String, Int> = emptyMap(),
    val vanillaExp: Long = 0L,
    val itemsRequired: Map<String, Int> = emptyMap(),
    val bossKills: Map<String, Int> = emptyMap()
) {
    fun isEmpty(): Boolean {
        return playTimeMin == 0 &&
                activeOverworldMin == 0 &&
                activeNetherResourceMin == 0 &&
                diamondOreMines == 0 &&
                enderEyeCrafts == 0 &&
                !requiresMyWorldCreated &&
                !requiresNetherPortalIgnited &&
                !requiresEndPortalOpened &&
                mobKills.isEmpty() &&
                blockMines.isEmpty() &&
                vanillaExp == 0L &&
                itemsRequired.isEmpty() &&
                bossKills.isEmpty()
    }

    fun getRequiredMobKills(mobType: String): Int = mobKills[mobType] ?: 0

    fun getRequiredBlockMines(blockType: String): Int = blockMines[blockType] ?: 0

    fun getRequiredBossKills(bossType: String): Int = bossKills[bossType] ?: 0

    fun getRequiredItemCount(material: String): Int = itemsRequired[material] ?: 0
}
