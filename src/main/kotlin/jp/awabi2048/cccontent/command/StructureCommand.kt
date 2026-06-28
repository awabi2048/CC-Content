package jp.awabi2048.cccontent.command

import jp.awabi2048.cccontent.features.sukima_dungeon.generator.StructureType
import jp.awabi2048.cccontent.structure.CardinalDirection
import jp.awabi2048.cccontent.structure.SavedSchemStructure
import jp.awabi2048.cccontent.structure.SchemStructureService
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class StructureCommand(
    private val structureService: SchemStructureService,
    private val onArenaStructureSaved: (() -> Unit)? = null,
    private val onSukimaStructureSaved: (() -> Unit)? = null
) {
    fun onCommand(sender: CommandSender, args: Array<out String>): Boolean {
        if (!sender.hasPermission("cc-content.admin")) {
            sender.sendMessage("§cPermission denied")
            return true
        }
        val player = sender as? Player
        if (player == null) {
            sender.sendMessage("§cThis command can only be used by a player with a WorldEdit selection")
            return true
        }
        if (args.size < 2 || args[0].lowercase() != "save") {
            sendUsage(sender)
            return true
        }

        return when (args[1].lowercase()) {
            "arena" -> saveArena(player, args)
            "arena-lift" -> saveArenaLift(player, args)
            "sukima" -> saveSukima(player, args)
            else -> {
                sendUsage(sender)
                true
            }
        }
    }

    fun onTabComplete(sender: CommandSender, cmd: Command, args: Array<String>): List<String> {
        if (!sender.hasPermission("cc-content.admin")) return emptyList()
        return when (args.size) {
            1 -> listOf("save").matching(args[0])
            2 -> if (args[0].equals("save", ignoreCase = true)) {
                listOf("arena", "arena-lift", "sukima").matching(args[1])
            } else {
                emptyList()
            }
            4 -> if (args[0].equals("save", ignoreCase = true) && args[1].equals("sukima", ignoreCase = true)) {
                StructureType.values().map { it.keyword }.matching(args[3])
            } else {
                emptyList()
            }
            else -> emptyList()
        }
    }

    private fun saveArena(player: Player, args: Array<out String>): Boolean {
        val parsed = parseOptions(player, args, 4, ARENA_USAGE) ?: return true
        val theme = sanitizePathSegment(parsed.positional[2]) ?: return true.also {
            player.sendMessage("§cInvalid theme: ${parsed.positional[2]}")
        }
        val fileName = sanitizeFileName(parsed.positional[3]) ?: return true.also {
            player.sendMessage("§cInvalid file: ${parsed.positional[3]}")
        }
        val result = trySave(player, "structures/arena/$theme/$fileName", parsed) ?: return true
        onArenaStructureSaved?.invoke()
        sendSaveResult(player, "arena structure", result, parsed)
        return true
    }

    private fun saveArenaLift(player: Player, args: Array<out String>): Boolean {
        val parsed = parseOptions(player, args, 2, ARENA_LIFT_USAGE) ?: return true
        val result = trySave(player, "structures/arena/lift.schem", parsed) ?: return true
        onArenaStructureSaved?.invoke()
        sendSaveResult(player, "arena lift", result, parsed)
        return true
    }

    private fun saveSukima(player: Player, args: Array<out String>): Boolean {
        val parsed = parseOptions(player, args, 5, SUKIMA_USAGE) ?: return true
        val theme = sanitizePathSegment(parsed.positional[2]) ?: return true.also {
            player.sendMessage("§cInvalid theme: ${parsed.positional[2]}")
        }
        val type = StructureType.values().firstOrNull { it.keyword.equals(parsed.positional[3], ignoreCase = true) }
        if (type == null) {
            player.sendMessage("§cInvalid sukima type: ${parsed.positional[3]}")
            return true
        }
        val name = sanitizePathSegment(parsed.positional[4]) ?: return true.also {
            player.sendMessage("§cInvalid name: ${parsed.positional[4]}")
        }
        val result = trySave(player, "structures/sukima_dungeon/$theme/${type.keyword}.$name.schem", parsed)
            ?: return true
        onSukimaStructureSaved?.invoke()
        sendSaveResult(player, "sukima structure", result, parsed)
        return true
    }

    private fun sendSaveResult(player: Player, label: String, result: SavedSchemStructure, options: SaveOptions) {
        val base = "§aSaved $label: ${result.file.name} (${result.size.x}x${result.size.y}x${result.size.z}) facing=${facingLabel(options, result)}"
        player.sendMessage(base)
        val v = result.validation
        if (v != null && v.hasIssues()) {
            if (v.missingMarkers.isNotEmpty())
                player.sendMessage("§e⚠ Missing markers: ${v.missingMarkers.joinToString(", ")}")
            if (v.extraMarkers.isNotEmpty())
                player.sendMessage("§e⚠ Extra markers: ${v.extraMarkers.joinToString(", ")}")
            if (v.warnings.isNotEmpty())
                v.warnings.forEach { player.sendMessage("§e⚠ $it") }
        }
    }

    private fun trySave(player: Player, relativePath: String, options: SaveOptions) = try {
        structureService.saveSelection(player, relativePath, options.overwrite, options.facing, options.force)
    } catch (e: Exception) {
        player.sendMessage("§cFailed to save structure: ${e.message}")
        null
    }

    private fun sendUsage(sender: CommandSender) {
        sender.sendMessage("§e$ARENA_USAGE")
        sender.sendMessage("§e$ARENA_LIFT_USAGE")
        sender.sendMessage("§e$SUKIMA_USAGE")
    }

    private data class SaveOptions(
        val positional: List<String>,
        val overwrite: Boolean,
        val facing: CardinalDirection?,
        val force: Boolean
    )

    private fun facingLabel(options: SaveOptions, result: SavedSchemStructure): String {
        if (options.facing != null) return options.facing.token
        val t = result.transform
        val facing = result.facing.token
        return if (t.mirrorX) "auto($facing, mirrored)" else "auto($facing)"
    }

    private fun parseOptions(
        player: Player,
        args: Array<out String>,
        positionalCount: Int,
        usage: String
    ): SaveOptions? {
        val positional = mutableListOf<String>()
        var overwrite = false
        var facing: CardinalDirection? = null
        var force = false
        var i = 0
        while (i < args.size) {
            val arg = args[i]
            when {
                arg.equals("--overwrite", ignoreCase = true) -> {
                    overwrite = true
                }
                arg.equals("--force", ignoreCase = true) -> {
                    force = true
                }
                arg.equals("--facing", ignoreCase = true) -> {
                    val value = args.getOrNull(i + 1)
                    if (value == null) {
                        player.sendMessage("§c--facing には north/east/south/west のいずれかを指定してください")
                        player.sendMessage("§e$usage")
                        return null
                    }
                    facing = CardinalDirection.fromToken(value)
                    if (facing == null) {
                        player.sendMessage("§c無効な facing 値: $value (north/east/south/west のいずれか)")
                        player.sendMessage("§e$usage")
                        return null
                    }
                    i += 1
                }
                arg.startsWith("--") -> {
                    player.sendMessage("§c不明なオプションです: $arg")
                    player.sendMessage("§e$usage")
                    return null
                }
                else -> positional.add(arg)
            }
            i += 1
        }
        if (positional.size != positionalCount) {
            player.sendMessage("§e$usage")
            return null
        }
        return SaveOptions(positional, overwrite, facing, force)
    }

    private fun sanitizePathSegment(raw: String): String? {
        val value = raw.trim()
        if (value.isBlank() || value == "." || value == "..") return null
        if (!SAFE_SEGMENT.matches(value)) return null
        return value
    }

    private fun sanitizeFileName(raw: String): String? {
        val value = raw.trim().removeSuffix(".schem")
        if (value.isBlank() || value == "." || value == "..") return null
        if (!SAFE_FILE_STEM.matches(value)) return null
        return "$value.schem"
    }

    private fun List<String>.matching(prefix: String): List<String> {
        return filter { it.startsWith(prefix, ignoreCase = true) }
    }

    companion object {
        private const val FACING_FLAG = "[--facing <north|east|south|west>] [--overwrite] [--force]"
        private const val ARENA_USAGE = "/ccc structure save arena <theme> <file> $FACING_FLAG"
        private const val ARENA_LIFT_USAGE = "/ccc structure save arena-lift $FACING_FLAG"
        private const val SUKIMA_USAGE = "/ccc structure save sukima <theme> <type> <name> $FACING_FLAG"
        private val SAFE_SEGMENT = Regex("[A-Za-z0-9_-]+")
        private val SAFE_FILE_STEM = Regex("[A-Za-z0-9_.-]+")
    }
}
