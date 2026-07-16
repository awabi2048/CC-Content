package jp.awabi2048.cccontent.features.arena

import jp.awabi2048.cccontent.items.arena.ArenaEnchantShardDefinition
import jp.awabi2048.cccontent.items.arena.ArenaEnchantShardEffectType
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import java.util.Locale
import kotlin.random.Random

data class ArenaShardRewardEntry(
    val definition: ArenaEnchantShardDefinition,
    val tier: Int,
    val type: ArenaEnchantShardEffectType,
    val weight: Int
)

class ArenaShardRewardService private constructor(
    private val entriesByDifficulty: Map<Int, List<ArenaShardRewardEntry>>
) {
    fun select(difficultyId: Int, mobDefinitionId: String, random: Random): ArenaShardRewardEntry? {
        val candidates = entriesByDifficulty[difficultyId]
            .orEmpty()
            .filter { mobDefinitionId.trim().lowercase(Locale.ROOT) in it.definition.dropMobDefinitionIds }
        if (candidates.isEmpty()) return null
        val totalWeight = candidates.sumOf { it.weight }
        var cursor = random.nextInt(totalWeight)
        return candidates.first { entry ->
            cursor -= entry.weight
            cursor < 0
        }
    }

    fun select(difficultyId: Int, mobDefinitionId: String): ArenaShardRewardEntry? =
        select(difficultyId, mobDefinitionId, Random.Default)

    fun entriesForDifficulty(difficultyId: Int): List<ArenaShardRewardEntry> = entriesByDifficulty[difficultyId].orEmpty()

    companion object {
        private val REQUIRED_FIELDS = setOf("id", "tier", "type", "weight")

        @JvmOverloads
        fun load(
            config: YamlConfiguration,
            definitions: List<ArenaEnchantShardDefinition>,
            knownDifficultyIds: Set<Int> = setOf(1, 2, 3, 4)
        ): ArenaShardRewardService {
            val definitionsByKey = definitions.associateBy { it.key }
            require(definitionsByKey.size == definitions.size) { "Arenaシャード定義に重複IDがあります" }
            val rootKeys = config.getKeys(false)
            require(rootKeys.isNotEmpty()) { "Arenaシャード報酬表が空です" }
            val tables = mutableMapOf<Int, List<ArenaShardRewardEntry>>()
            val assignedDifficultyByShard = mutableMapOf<String, Int>()
            rootKeys.forEach { rawDifficultyId ->
                val difficultyId = rawDifficultyId.toIntOrNull()
                    ?: error("Arenaシャード報酬表の難易度IDが不正です: $rawDifficultyId")
                require(difficultyId in knownDifficultyIds) { "Arenaシャード報酬表に未知の難易度IDがあります: $difficultyId" }
                require(difficultyId !in tables) { "Arenaシャード報酬表に重複した難易度IDがあります: $difficultyId" }
                val section = config.getConfigurationSection(rawDifficultyId)
                    ?: error("Arenaシャード報酬表の難易度表がセクションではありません: $difficultyId")
                val table = parseTable(section, difficultyId, definitionsByKey)
                table.forEach { entry ->
                    val previous = assignedDifficultyByShard.putIfAbsent(entry.definition.key, difficultyId)
                    require(previous == null) {
                        "同じシャードを複数難易度へ設定できません: id=${entry.definition.key} difficulties=$previous,$difficultyId"
                    }
                }
                tables[difficultyId] = table
            }
            val missing = knownDifficultyIds - tables.keys
            require(missing.isEmpty()) { "Arenaシャード報酬表に未定義の難易度IDがあります: ${missing.sorted()}" }
            return ArenaShardRewardService(tables.toMap())
        }

        private fun parseTable(
            section: ConfigurationSection,
            difficultyId: Int,
            definitionsByKey: Map<String, ArenaEnchantShardDefinition>
        ): List<ArenaShardRewardEntry> {
            val rawEntries = section.getMapList("entries")
            require(rawEntries.isNotEmpty()) { "Arenaシャード報酬表が空です: difficulty=$difficultyId" }
            val seenIds = mutableSetOf<String>()
            return rawEntries.mapIndexed { index, raw ->
                val unknownFields = raw.keys.map { it.toString() }.toSet() - REQUIRED_FIELDS
                require(unknownFields.isEmpty()) { "Arenaシャード報酬表に未知の項目があります: difficulty=$difficultyId index=$index fields=$unknownFields" }
                val id = raw["id"]?.toString()?.trim().orEmpty()
                require(id.isNotEmpty()) { "Arenaシャード報酬表のIDが空です: difficulty=$difficultyId index=$index" }
                require(seenIds.add(id)) { "Arenaシャード報酬表に重複IDがあります: difficulty=$difficultyId id=$id" }
                val definition = definitionsByKey[id] ?: error("Arenaシャード報酬表に未知のシャードIDがあります: $id")
                val tier = raw["tier"]?.toString()?.toIntOrNull()
                    ?: error("Arenaシャード報酬表のTierが不正です: id=$id")
                require(tier in 1..3) { "Arenaシャード報酬表のTierは1〜3です: id=$id tier=$tier" }
                require(tier <= difficultyId) { "低難易度に高Tierのシャードを設定できません: difficulty=$difficultyId id=$id tier=$tier" }
                require(tier == definitionTier(definition)) { "シャードIDとTierが一致しません: id=$id tier=$tier" }
                val type = ArenaEnchantShardEffectType.fromId(raw["type"]?.toString()?.trim()?.lowercase(Locale.ROOT).orEmpty())
                    ?: error("Arenaシャード報酬表の種類が不正です: id=$id")
                require(type == definition.shard.effectType) { "シャードIDと種類が一致しません: id=$id type=${type.id}" }
                val weight = raw["weight"]?.toString()?.toIntOrNull()
                    ?: error("Arenaシャード報酬表の重みが不正です: id=$id")
                require(weight > 0) { "Arenaシャード報酬表の重みは1以上です: id=$id" }
                ArenaShardRewardEntry(definition, tier, type, weight)
            }
        }

        private fun definitionTier(definition: ArenaEnchantShardDefinition): Int {
            return if (definition.shard.effectType == ArenaEnchantShardEffectType.LIMIT_BREAKING) {
                definition.shard.overLevel ?: error("限界突破シャードにTier相当のレベルがありません: ${definition.key}")
            } else {
                1
            }
        }
    }
}
