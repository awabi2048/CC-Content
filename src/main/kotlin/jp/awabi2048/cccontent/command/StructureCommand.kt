package jp.awabi2048.cccontent.command

import jp.awabi2048.cccontent.features.sukima_dungeon.generator.StructureType
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
        if (args.size !in 4..5) {
            player.sendMessage("§cUsage: /ccc structure save arena <theme> <file> [--overwrite]")
            return true
        }
        val overwrite = hasOverwrite(args, 4)
        val theme = sanitizePathSegment(args[2]) ?: return true.also {
            player.sendMessage("§cInvalid theme: ${args[2]}")
        }
        val fileName = sanitizeFileName(args[3]) ?: return true.also {
            player.sendMessage("§cInvalid file: ${args[3]}")
        }
        val result = trySave(player, "structures/arena/$theme/$fileName", overwrite) ?: return true
        onArenaStructureSaved?.invoke()
        player.sendMessage("§aSaved arena structure: ${result.file.name} (${result.size.x}x${result.size.y}x${result.size.z})")
        return true
    }

    private fun saveArenaLift(player: Player, args: Array<out String>): Boolean {
        if (args.size !in 2..3) {
            player.sendMessage("§cUsage: /ccc structure save arena-lift [--overwrite]")
            return true
        }
        val overwrite = hasOverwrite(args, 2)
        val result = trySave(player, "structures/arena/lift.schem", overwrite) ?: return true
        onArenaStructureSaved?.invoke()
        player.sendMessage("§aSaved arena lift: ${result.size.x}x${result.size.y}x${result.size.z}")
        return true
    }

    private fun saveSukima(player: Player, args: Array<out String>): Boolean {
        if (args.size !in 5..6) {
            player.sendMessage("§cUsage: /ccc structure save sukima <theme> <type> <name> [--overwrite]")
            return true
        }
        val overwrite = hasOverwrite(args, 5)
        val theme = sanitizePathSegment(args[2]) ?: return true.also {
            player.sendMessage("§cInvalid theme: ${args[2]}")
        }
        val type = StructureType.values().firstOrNull { it.keyword.equals(args[3], ignoreCase = true) }
        if (type == null) {
            player.sendMessage("§cInvalid sukima type: ${args[3]}")
            return true
        }
        val name = sanitizePathSegment(args[4]) ?: return true.also {
            player.sendMessage("§cInvalid name: ${args[4]}")
        }
        val result = trySave(player, "structures/sukima_dungeon/$theme/${type.keyword}.$name.schem", overwrite)
            ?: return true
        onSukimaStructureSaved?.invoke()
        player.sendMessage("§aSaved sukima structure: ${result.file.name} (${result.size.x}x${result.size.y}x${result.size.z})")
        return true
    }

    private fun trySave(player: Player, relativePath: String, overwrite: Boolean) = try {
        structureService.saveSelection(player, relativePath, overwrite)
    } catch (e: Exception) {
        player.sendMessage("§cFailed to save structure: ${e.message}")
        null
    }

    private fun sendUsage(sender: CommandSender) {
        sender.sendMessage("§e/ccc structure save arena <theme> <file> [--overwrite]")
        sender.sendMessage("§e/ccc structure save arena-lift [--overwrite]")
        sender.sendMessage("§e/ccc structure save sukima <theme> <type> <name> [--overwrite]")
    }

    private fun hasOverwrite(args: Array<out String>, index: Int): Boolean {
        return args.getOrNull(index)?.equals("--overwrite", ignoreCase = true) == true
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
        private val SAFE_SEGMENT = Regex("[A-Za-z0-9_-]+")
        private val SAFE_FILE_STEM = Regex("[A-Za-z0-9_.-]+")
    }
}
