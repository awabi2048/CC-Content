package jp.awabi2048.cccontent.features.arena

import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.io.BukkitObjectOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.UUID

class ArenaAuditLogger(private val plugin: JavaPlugin) {
    private val logFile = File(plugin.dataFolder, "data/arena/audit.jsonl")
    private val zoneId = ZoneId.of("Asia/Tokyo")

    fun logMissionUpdate(missions: List<Map<String, Any?>>) {
        append(
            linkedMapOf(
                "ts" to timestamp(),
                "type" to "mission_update",
                "selected" to missions
            )
        )
    }

    fun logPedestalTransform(
        playerId: UUID,
        playerName: String,
        baseItem: ItemStack,
        resultItem: ItemStack,
        catalysts: List<ItemStack>
    ) {
        append(
            linkedMapOf(
                "ts" to timestamp(),
                "type" to "pedestal_transform",
                "playerId" to playerId.toString(),
                "playerName" to playerName,
                "baseItem" to itemToMap(baseItem),
                "resultItem" to itemToMap(resultItem),
                "catalysts" to catalysts.map { itemToMap(it) }
            )
        )
    }

    private fun append(payload: Map<String, Any?>) {
        runCatching {
            logFile.parentFile.mkdirs()
            logFile.appendText(toJson(payload) + "\n", Charsets.UTF_8)
        }.onFailure { error ->
            plugin.logger.warning("[Arena] 監査ログの書き込みに失敗しました: ${error.message}")
        }
    }

    private fun timestamp(): String {
        return OffsetDateTime.now(zoneId).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }

    private fun itemToMap(item: ItemStack): Map<String, Any?> {
        val meta = item.itemMeta
        return linkedMapOf(
            "material" to item.type.name,
            "amount" to item.amount,
            "displayName" to meta?.displayName,
            "lore" to meta?.lore,
            "enchants" to item.enchantments.mapKeys { it.key.key.toString() },
            "customModelData" to meta?.takeIf { it.hasCustomModelData() }?.customModelData,
            "persistentDataKeys" to meta?.persistentDataContainer?.keys?.map { it.toString() }?.sorted(),
            "bukkitSerialized" to item.serialize(),
            "rawBase64" to runCatching { serializeItem(item) }.getOrNull()
        )
    }

    private fun serializeItem(item: ItemStack): String {
        val bytes = ByteArrayOutputStream()
        BukkitObjectOutputStream(bytes).use { stream ->
            stream.writeObject(item)
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray())
    }

    private fun toJson(value: Any?): String {
        return when (value) {
            null -> "null"
            is String -> "\"" + value.flatMap { escapeJsonChar(it) }.joinToString("") + "\""
            is Number, is Boolean -> value.toString()
            is Map<*, *> -> value.entries.joinToString(prefix = "{", postfix = "}") { (key, entryValue) ->
                toJson(key.toString()) + ":" + toJson(entryValue)
            }
            is Iterable<*> -> value.joinToString(prefix = "[", postfix = "]") { toJson(it) }
            is Array<*> -> value.joinToString(prefix = "[", postfix = "]") { toJson(it) }
            else -> toJson(value.toString())
        }
    }

    private fun escapeJsonChar(char: Char): List<Char> {
        return when (char) {
            '"' -> listOf('\\', '"')
            '\\' -> listOf('\\', '\\')
            '\b' -> listOf('\\', 'b')
            '\u000C' -> listOf('\\', 'f')
            '\n' -> listOf('\\', 'n')
            '\r' -> listOf('\\', 'r')
            '\t' -> listOf('\\', 't')
            else -> if (char.code < 0x20) {
                "\\u%04x".format(char.code).toList()
            } else {
                listOf(char)
            }
        }
    }
}
