package jp.awabi2048.cccontent.features.rank.profession.skilltree

import jp.awabi2048.cccontent.features.rank.profession.Profession
import jp.awabi2048.cccontent.features.rank.profession.SkillNode
import jp.awabi2048.cccontent.features.rank.profession.SkillTree
import jp.awabi2048.cccontent.features.rank.skill.CombineRule
import jp.awabi2048.cccontent.features.rank.skill.EvaluationMode
import jp.awabi2048.cccontent.features.rank.skill.SkillEffect
import kotlin.math.roundToLong

class CodeDefinedSkillTree private constructor(
    private val profession: Profession,
    private val definition: Definition
) : SkillTree {
    data class Definition(
        val initialExp: Long,
        val base: Double,
        val maxLevel: Int,
        val overviewIcon: String,
        val bossBarColor: String = "GREEN",
        val nodes: List<SkillNode>
    )

    private val skills = definition.nodes.associateBy(SkillNode::skillId)
    private val childrenByParent = skills.mapValues { (_, node) -> node.children.distinct() }
    private val parentsByChild = buildMap<String, List<String>> {
        skills.keys.forEach { put(it, emptyList()) }
        skills.values.forEach { parent ->
            parent.children.forEach { child ->
                put(child, getValue(child) + parent.skillId)
            }
        }
    }.mapValues { (_, parents) -> parents.distinct().sorted() }
    private val startSkillId = skills.keys.single { parentsByChild.getValue(it).isEmpty() }
    private val requiredTotalExpByLevel = buildLevelThresholds()

    init {
        validate()
    }

    override fun getProfessionId(): String = profession.id

    override fun getSkill(skillId: String): SkillNode? = skills[skillId]

    override fun getAllSkills(): Map<String, SkillNode> = skills

    override fun getStartSkillId(): String = startSkillId

    override fun getChildren(skillId: String): List<String> = childrenByParent[skillId].orEmpty()

    override fun getParents(skillId: String): List<String> = parentsByChild[skillId].orEmpty()

    override fun getAvailableSkills(acquiredSkills: Set<String>, currentLevel: Int): List<String> =
        skills.keys.sorted().filter { canAcquire(it, acquiredSkills, currentLevel) }

    override fun getLevelInitialExp(): Long = definition.initialExp

    override fun getLevelBase(): Double = definition.base

    override fun getMaxLevel(): Int = definition.maxLevel

    override fun getOverviewIcon(): String = definition.overviewIcon

    override fun getBossBarColor(): String = definition.bossBarColor

    override fun getRequiredTotalExpForLevel(level: Int): Long =
        requiredTotalExpByLevel[level.coerceIn(1, definition.maxLevel)]

    override fun calculateLevelByExp(totalExp: Long): Int {
        val exp = totalExp.coerceAtLeast(0L)
        var low = 1
        var high = definition.maxLevel
        var result = 1
        while (low <= high) {
            val middle = (low + high) ushr 1
            if (exp >= requiredTotalExpByLevel[middle]) {
                result = middle
                low = middle + 1
            } else {
                high = middle - 1
            }
        }
        return result
    }

    override fun getExpToNextLevel(level: Int): Long {
        val safeLevel = level.coerceAtLeast(1)
        return (definition.initialExp * Math.pow(definition.base, (safeLevel - 1).toDouble()))
            .roundToLong()
            .coerceAtLeast(1L)
    }

    private fun validate() {
        require(definition.initialExp > 0) { "${profession.id}: initialExp must be positive" }
        require(definition.base >= 1.0) { "${profession.id}: base must be at least 1.0" }
        require(definition.maxLevel > 0) { "${profession.id}: maxLevel must be positive" }
        require(skills.size == definition.nodes.size) { "${profession.id}: duplicate skill id" }

        skills.values.forEach { node ->
            require(node.requiredLevel in 0..definition.maxLevel) {
                "${profession.id}/${node.skillId}: required level is outside the profession level range"
            }
            require(node.children.size <= 2) {
                "${profession.id}/${node.skillId}: a skill may have at most two children"
            }
            node.children.forEach { child ->
                require(child in skills) { "${profession.id}/${node.skillId}: unknown child $child" }
            }
        }
        require(parentsByChild.values.all { it.size <= 2 }) {
            "${profession.id}: a skill may have at most two parents"
        }
        require(skills.keys.count { parentsByChild.getValue(it).isEmpty() } == 1) {
            "${profession.id}: exactly one root skill is required"
        }

        val visited = mutableSetOf<String>()
        val visiting = mutableSetOf<String>()
        fun visit(skillId: String) {
            require(visiting.add(skillId)) { "${profession.id}: cycle detected at $skillId" }
            childrenByParent.getValue(skillId).forEach { child ->
                if (child !in visited) visit(child)
            }
            visiting.remove(skillId)
            visited.add(skillId)
        }
        visit(startSkillId)
        require(visited == skills.keys) { "${profession.id}: unreachable skill nodes exist" }
    }

    private fun buildLevelThresholds(): List<Long> {
        val thresholds = MutableList(definition.maxLevel + 1) { 0L }
        var cumulative = 0L
        for (level in 2..definition.maxLevel) {
            val required = getExpToNextLevel(level - 1)
            cumulative = if (Long.MAX_VALUE - cumulative < required) Long.MAX_VALUE else cumulative + required
            thresholds[level] = cumulative
        }
        return thresholds
    }

    companion object {
        fun create(profession: Profession): CodeDefinedSkillTree =
            CodeDefinedSkillTree(profession, ProfessionSkillTreeDefinitions.get(profession))
    }
}

private object ProfessionSkillTreeDefinitions {
    private fun node(
        profession: Profession,
        id: String,
        level: Int,
        icon: String?,
        children: List<String>,
        effect: SkillEffect? = null,
        exclusiveBranch: Boolean = true,
        activationToggleable: Boolean = true
    ) = SkillNode(
        skillId = id,
        nameKey = "skill.${profession.id}.$id.name",
        descriptionKey = "skill.${profession.id}.$id.description",
        requiredLevel = level,
        icon = icon,
        children = children,
        effect = effect,
        exclusiveBranch = exclusiveBranch,
        activationToggleable = activationToggleable
    )

    private fun effect(
        type: String,
        params: Map<String, Any> = emptyMap(),
        evaluationMode: EvaluationMode = EvaluationMode.CACHED,
        combineRule: CombineRule = CombineRule.ADD
    ) = SkillEffect(type, params, evaluationMode, combineRule)

    fun get(profession: Profession): CodeDefinedSkillTree.Definition = when (profession) {
        Profession.BREWER -> definition(
            profession, 100, 1.25, 50, "BREWING_STAND",
            node(profession, "initial", 0, "BREWING_STAND", listOf("skill1")),
            node(profession, "skill1", 5, "POTION", listOf("skill2", "skill3"), effect("craft.unlock_system")),
            node(profession, "skill2", 10, "HONEY_BOTTLE", listOf("skill4"), effect("craft.unlock_recipe", mapOf("recipes" to listOf("potion_water", "potion_mundane")))),
            node(profession, "skill3", 10, "SPLASH_POTION", listOf("skill5"), effect("craft.unlock_recipe", mapOf("recipes" to listOf("potion_strength", "potion_weakness")))),
            node(profession, "skill4", 15, "BLAZE_POWDER", listOf("skill6")),
            node(profession, "skill5", 20, "FERMENTED_SPIDER_EYE", listOf("skill7", "skill9"), effect("craft.work_speed_bonus_mock", mapOf("bonus" to 0.1))),
            node(profession, "skill6", 25, "CLOCK", listOf("skill8")),
            node(profession, "skill7", 30, "CAULDRON", listOf("skill10")),
            node(profession, "skill8", 35, "GLOWSTONE_DUST", listOf("skill11"), effect("craft.success_rate_bonus_mock", mapOf("bonus" to 0.1))),
            node(profession, "skill9", 30, "GUNPOWDER", listOf("skill10"), effect("craft.success_rate_bonus_mock", mapOf("bonus" to 0.15))),
            node(profession, "skill10", 35, "DRAGON_BREATH", listOf("skill11"), effect("craft.work_speed_bonus_mock", mapOf("bonus" to 0.2))),
            node(profession, "skill11", 40, "ENCHANTED_GOLDEN_APPLE", listOf("skill12")),
            node(profession, "skill12", 50, "ENCHANTED_BOOK", emptyList(), effect("general.unlock_item_token", mapOf("items" to listOf("brewer_token"))))
        )

        Profession.CARPENTER -> definition(
            profession, 100, 1.25, 25, "CRAFTING_TABLE",
            node(profession, "initial", 0, "OAK_PLANKS", listOf("skill1")),
            node(profession, "skill1", 5, "ITEM_FRAME", listOf("skill2")),
            node(profession, "skill2", 15, "STONECUTTER", listOf("skill3")),
            node(profession, "skill3", 25, "ENCHANTED_BOOK", emptyList(), effect("general.unlock_item_token", mapOf("items" to listOf("carpenter_hammer_token"))))
        )

        Profession.COOK -> definition(
            profession, 100, 1.25, 50, "FURNACE",
            node(profession, "initial", 1, "SMOKER", listOf("skill1")),
            node(profession, "skill1", 2, "BOWL", listOf("skill2", "skill4"), effect("craft.unlock_system")),
            node(profession, "skill2", 3, "BREAD", listOf("skill3"), effect("craft.unlock_recipe", mapOf("recipes" to listOf("craft_bread", "craft_stick")))),
            node(profession, "skill3", 4, "COOKED_BEEF", listOf("skill8"), effect("craft.work_speed_bonus_mock", mapOf("bonus" to 0.1))),
            node(profession, "skill4", 3, "MUSHROOM_STEW", listOf("skill5", "skill6"), effect("craft.unlock_recipe", mapOf("recipes" to listOf("craft_mushroom_stew", "craft_beetroot_soup")))),
            node(profession, "skill5", 4, "RABBIT_STEW", listOf("skill8"), effect("craft.success_rate_bonus_mock", mapOf("bonus" to 0.15))),
            node(profession, "skill6", 4, "BEETROOT_SOUP", listOf("skill7"), effect("craft.unlock_recipe", mapOf("recipes" to listOf("craft_rabbit_stew", "craft_suspicious_stew")))),
            node(profession, "skill7", 5, "SUSPICIOUS_STEW", listOf("skill9")),
            node(profession, "skill8", 6, "GOLDEN_CARROT", listOf("skill9")),
            node(profession, "skill9", 7, "ENCHANTED_GOLDEN_APPLE", emptyList(), effect("general.unlock_item_token", mapOf("items" to listOf("cook_token"))))
        )

        Profession.FARMER -> definition(
            profession, 10, 1.2, 50, "DIAMOND_HOE",
            node(profession, "initial", 0, "WOODEN_HOE", listOf("harvest_1")),
            node(profession, "harvest_1", 5, "STONE_HOE", listOf("area_tilling"), effect("collect.drop_bonus", mapOf("fortune_level" to 1))),
            node(profession, "area_tilling", 10, "FARMLAND", listOf("area_harvesting", "auto_replanting"), effect("farmer.area_tilling", mapOf("radius" to 2))),
            node(profession, "area_harvesting", 20, "DIAMOND_HOE", listOf("harvest_2"), effect("farmer.area_harvesting", mapOf("radius" to 2))),
            node(profession, "auto_replanting", 20, "GOLDEN_HOE", listOf("harvest_2"), effect("farmer.auto_replanting")),
            node(profession, "harvest_2", 30, "WHEAT", listOf("tool", "harvest_3"), effect("collect.drop_bonus", mapOf("fortune_level" to 1))),
            node(profession, "tool", 35, "WILD_ARMOR_TRIM_SMITHING_TEMPLATE", listOf("special_loot_table_1")),
            node(profession, "harvest_3", 35, "COMPOSTER", listOf("special_loot_table_1"), effect("collect.drop_bonus", mapOf("fortune_level" to 1))),
            node(profession, "special_loot_table_1", 40, "PRISMARINE_CRYSTALS", listOf("special_loot_table_2")),
            node(profession, "special_loot_table_2", 50, "RESIN_CLUMP", emptyList(), effect("general.unlock_item_token", mapOf("items" to listOf("farmer_scythe_token"))))
        )

        Profession.FISHER -> definition(
            profession, 100, 1.25, 50, "FISHING_ROD", "AQUA",
            node(profession, "initial", 0, "FISHING_ROD", listOf("patient_cast")),
            node(profession, "patient_cast", 5, "LILY_PAD", listOf("deep_water"), effect("fisher.hook_window_bonus", mapOf("multiplier" to 1.2)), activationToggleable = false),
            node(profession, "deep_water", 15, "NAUTILUS_SHELL", listOf("master_angler"), effect("fisher.stability_bonus", mapOf("bonus" to 0.15)), activationToggleable = false),
            node(profession, "master_angler", 30, "HEART_OF_THE_SEA", emptyList(), effect("fisher.duration_multiplier", mapOf("multiplier" to 0.85)), activationToggleable = false)
        )

        Profession.GARDENER -> definition(
            profession, 100, 1.25, 25, "FLOWER_POT",
            node(profession, "initial", 0, "OAK_SAPLING", listOf("skill1")),
            node(profession, "skill1", 5, "FEATHER", listOf("skill2")),
            node(profession, "skill2", 10, "SHULKER_BOX", listOf("skill3")),
            node(profession, "skill3", 15, "BRUSH", listOf("skill4")),
            node(profession, "skill4", 20, "STICK", listOf("skill5")),
            node(profession, "skill5", 25, "ENCHANTED_BOOK", emptyList(), effect("general.unlock_item_token", mapOf("items" to listOf("gardener_shears_token"))))
        )

        Profession.LUMBERJACK -> definition(
            profession, 10, 1.25, 50, "DIAMOND_AXE",
            node(profession, "initial", 0, "WOODEN_AXE", listOf("speed_1")),
            node(profession, "speed_1", 5, "STONE_AXE", listOf("harvest_1"), effect("collect.break_speed_boost", mapOf("efficiency_level" to 1, "targetBlocks" to listOf("AnyLog")))),
            node(profession, "harvest_1", 10, "GOLDEN_AXE", listOf("speed_2", "harvest_2"), effect("collect.drop_bonus", mapOf("fortune_level" to 1))),
            node(profession, "speed_2", 15, "DIAMOND_AXE", listOf("cut_all_1"), effect("collect.break_speed_boost", mapOf("efficiency_level" to 1, "targetBlocks" to listOf("AnyLog")))),
            node(profession, "harvest_2", 15, "GOLDEN_AXE", listOf("cut_all_1"), effect("collect.drop_bonus", mapOf("fortune_level" to 1))),
            node(profession, "cut_all_1", 25, "INK_SAC", listOf("cut_all_2"), batchBreak(8, 2, false, false)),
            node(profession, "cut_all_2", 30, "LEATHER_BOOTS", listOf("wind_gust", "harvest_3"), batchBreak(16, 0, true, false)),
            node(profession, "wind_gust", 40, "WIND_CHARGE", listOf("replant"), effect("lumberjack.wind_gust", mapOf("max_distance" to 12, "delay_per_block" to 1))),
            node(profession, "replant", 45, "OAK_SAPLING", listOf("cut_all_3"), effect("lumberjack.replant")),
            node(profession, "harvest_3", 40, "GOLDEN_AXE", listOf("cut_all_3"), effect("collect.drop_bonus", mapOf("fortune_level" to 1))),
            node(profession, "cut_all_3", 50, "NETHER_STAR", emptyList(), batchBreak(24, 0, true, true))
        )

        Profession.MINER -> definition(
            profession, 10, 1.2, 50, "DIAMOND_PICKAXE",
            node(profession, "initial", 0, "WOODEN_PICKAXE", listOf("speed_1")),
            node(profession, "speed_1", 5, "IRON_PICKAXE", listOf("fortune_1"), effect("collect.break_speed_boost", mapOf("efficiency_level" to 1, "targetTools" to listOf("PICKAXE")))),
            node(profession, "fortune_1", 10, "GOLDEN_PICKAXE", listOf("speed_2", "fortune_2"), effect("collect.drop_bonus", mapOf("fortune_level" to 1))),
            node(profession, "speed_2", 15, "IRON_PICKAXE", listOf("durability_1"), effect("collect.break_speed_boost", mapOf("efficiency_level" to 1, "targetBlocks" to listOf("STONE", "COBBLESTONE", "DEEPSLATE"), "targetTools" to listOf("PICKAXE")))),
            node(profession, "fortune_2", 15, "GOLDEN_PICKAXE", listOf("fortune_3"), effect("collect.drop_bonus", mapOf("fortune_level" to 1))),
            node(profession, "durability_1", 20, "DIAMOND_PICKAXE", listOf("mineall_1"), effect("collect.durability_save_chance", mapOf("unbreaking_level" to 1))),
            node(profession, "fortune_3", 20, "GOLDEN_PICKAXE", listOf("mineall_1"), effect("collect.drop_bonus", mapOf("fortune_level" to 1))),
            node(profession, "mineall_1", 30, "TEST_BLOCK", listOf("mineall_2", "blastmine_1"), mineAll(8, 3, false, false)),
            node(profession, "mineall_2", 40, "TEST_BLOCK", listOf("mineall_3"), mineAll(16, 2, false, true)),
            node(profession, "mineall_3", 50, "TEST_BLOCK", emptyList(), mineAll(24, 3, true, true)),
            node(profession, "blastmine_1", 40, "TNT", listOf("blastmine_2"), effect("collect.blast_mine", mapOf("radius" to 1, "autoCollect" to false, "lossRate" to 0.5, "targetTools" to listOf("PICKAXE")))),
            node(profession, "blastmine_2", 50, "TNT_MINECART", emptyList(), effect("collect.blast_mine", mapOf("radius" to 2, "autoCollect" to true, "lossRate" to 0.3, "targetTools" to listOf("PICKAXE"))))
        )

        Profession.SWORDSMAN -> definition(
            profession, 100, 1.25, 50, "DIAMOND_SWORD",
            node(profession, "initial", 0, "WOODEN_SWORD", listOf("power_1")),
            node(profession, "power_1", 5, "STONE_SWORD", listOf("sweep_boost_1", "power_2"), effect("swordsman.sword_damage_boost", mapOf("sharpness" to 1))),
            node(profession, "sweep_boost_1", 10, "IRON_SWORD", listOf("buff_1"), effect("swordsman.sweep_damage_boost", mapOf("sweeping_edge" to 1))),
            node(profession, "power_2", 10, "DIAMOND_SWORD", listOf("buff_1"), effect("swordsman.sword_damage_boost", mapOf("sharpness" to 1))),
            node(profession, "buff_1", 20, "GOLDEN_SWORD", listOf("sweep_boost_2", "drain_1"), effect("swordsman.underdog_buff", mapOf("radius" to 12, "ratio" to 2.0, "buff" to "REGENERATION_1"))),
            node(profession, "sweep_boost_2", 30, "NETHERITE_SWORD", listOf("power_3"), effect("swordsman.sweep_damage_boost", mapOf("sweeping_edge" to 1))),
            node(profession, "drain_1", 30, "SHIELD", listOf("buff_2"), effect("swordsman.drain", mapOf("ratio" to 0.3, "max_heal" to 4.0, "chance" to 0.25))),
            node(profession, "power_3", 40, "TOTEM_OF_UNDYING", listOf("reach_1"), effect("swordsman.sword_damage_boost", mapOf("sharpness" to 1.0))),
            node(profession, "buff_2", 40, "TARGET", listOf("reach_1"), effect("swordsman.underdog_buff", mapOf("radius" to 16, "ratio" to 3.0, "buff" to "REGENERATION_2"))),
            node(profession, "reach_1", 50, "ENCHANTED_BOOK", emptyList(), effect("combat.attack_reach_boost", mapOf("reach_blocks" to 1.5)))
        )

        Profession.WARRIOR -> definition(
            profession, 10, 1.2, 50, "NETHERITE_AXE",
            node(profession, "initial", 0, "WOODEN_AXE", listOf("power_1")),
            node(profession, "power_1", 5, "STONE_AXE", listOf("axe_power_1", "bow_power_1"), effect("warrior.axe_damage_boost", mapOf("sharpness" to 1))),
            node(profession, "axe_power_1", 10, "IRON_AXE", listOf("shock_wave_1", "self_buff_1"), effect("warrior.axe_damage_boost", mapOf("sharpness" to 0.5))),
            node(profession, "shock_wave_1", 20, "IRON_AXE", listOf("shock_wave_2"), effect("warrior.axe_damage_boost", mapOf("sharpness" to 0.5))),
            node(profession, "self_buff_1", 20, "SHIELD", listOf("attack_speed_1"), effect("combat.attack_reach_boost", mapOf("reach_blocks" to 0.5))),
            node(profession, "shock_wave_2", 30, "DIAMOND_AXE", listOf("axe_power_2"), effect("warrior.axe_damage_boost", mapOf("sharpness" to 1.0))),
            node(profession, "attack_speed_1", 30, "GOLDEN_AXE", listOf("axe_power_2"), effect("warrior.axe_damage_boost", mapOf("sharpness" to 0.5))),
            node(profession, "axe_power_2", 35, "NETHERITE_AXE", listOf("magnet_1", "critical_1"), effect("warrior.axe_damage_boost", mapOf("sharpness" to 1.0))),
            node(profession, "magnet_1", 40, "SHIELD", listOf("axe_power_3"), effect("combat.attack_reach_boost", mapOf("reach_blocks" to 1.0))),
            node(profession, "critical_1", 40, "TOTEM_OF_UNDYING", listOf("axe_power_3"), effect("warrior.axe_damage_boost", mapOf("sharpness" to 1.0))),
            node(profession, "axe_power_3", 50, "IRON_CHESTPLATE", emptyList(), effect("warrior.axe_damage_boost", mapOf("sharpness" to 1.5))),
            node(profession, "bow_power_1", 10, "BOW", listOf("snipe_1", "3way_1"), effect("warrior.bow_power_boost", mapOf("power" to 0.5))),
            node(profession, "snipe_1", 20, "BOW", listOf("piercing_1"), effect("warrior.snipe", mapOf("max_charge_time" to 20.0, "damage_multiplier" to 1.2, "range_multiplier" to 1.25))),
            node(profession, "3way_1", 20, "BOW", listOf("3way_2"), effect("warrior.3way", mapOf("arrow_consumption" to 3, "side_damage_multiplier" to 1.0), combineRule = CombineRule.REPLACE)),
            node(profession, "piercing_1", 25, "BOW", listOf("bow_power_2"), effect("warrior.piercing", mapOf("max_pierce_count" to 1))),
            node(profession, "3way_2", 25, "BOW", listOf("bow_power_2"), effect("warrior.3way", mapOf("arrow_consumption" to 2, "side_damage_multiplier" to 1.0), combineRule = CombineRule.REPLACE)),
            node(profession, "bow_power_2", 30, "BOW", listOf("arrow_saving_1", "range_1"), effect("warrior.bow_power_boost", mapOf("power" to 1.0))),
            node(profession, "arrow_saving_1", 40, "BOW", listOf("aiming_1"), effect("warrior.arrow_saving", mapOf("chance" to 0.25))),
            node(profession, "range_1", 40, "BOW", listOf("aiming_1"), effect("warrior.snipe", mapOf("max_charge_time" to 20.0, "damage_multiplier" to 1.0, "range_multiplier" to 1.5))),
            node(profession, "aiming_1", 50, "BOW", emptyList(), effect("warrior.aiming", mapOf("homing_strength" to 6.0, "duration" to 40)))
        )
    }

    private fun definition(
        profession: Profession,
        initialExp: Long,
        base: Double,
        maxLevel: Int,
        overviewIcon: String,
        vararg nodes: SkillNode
    ) = definition(profession, initialExp, base, maxLevel, overviewIcon, "GREEN", *nodes)

    private fun definition(
        profession: Profession,
        initialExp: Long,
        base: Double,
        maxLevel: Int,
        overviewIcon: String,
        bossBarColor: String,
        vararg nodes: SkillNode
    ) = CodeDefinedSkillTree.Definition(
        initialExp = initialExp,
        base = base,
        maxLevel = maxLevel,
        overviewIcon = overviewIcon,
        bossBarColor = bossBarColor,
        nodes = nodes.toList().map { node ->
            node.copy(prerequisites = nodes.filter { node.skillId in it.children }.map(SkillNode::skillId).sorted())
        }
    )

    private fun batchBreak(maxCount: Int, delay: Int, instant: Boolean, autoCollect: Boolean) =
        effect(
            "collect.unlock_batch_break",
            mapOf(
                "mode" to "cut_all",
                "maxChainCount" to maxCount,
                "delayTicks" to delay,
                "instantBreak" to instant,
                "autoCollect" to autoCollect,
                "visualizeTargets" to true,
                "matchLevel" to "EXACT",
                "targetTools" to listOf("AXE")
            )
        )

    private fun mineAll(maxCount: Int, delay: Int, instant: Boolean, autoCollect: Boolean) =
        effect(
            "collect.unlock_batch_break",
            mapOf(
                "mode" to "mine_all",
                "maxChainCount" to maxCount,
                "delayTicks" to delay,
                "instantBreak" to instant,
                "autoCollect" to autoCollect,
                "visualizeTargets" to true,
                "matchLevel" to "FAMILY"
            )
        )
}
