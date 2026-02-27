package jp.awabi2048.cccontent.features.sukima_dungeon

import jp.awabi2048.cccontent.CCContent
import jp.awabi2048.cccontent.features.sukima_dungeon.generator.MazeGenerator
import jp.awabi2048.cccontent.features.sukima_dungeon.generator.StructureBuilder
import jp.awabi2048.cccontent.features.sukima_dungeon.generator.StructureLoader
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

import org.bukkit.command.TabCompleter

class MazeCommand(private val plugin: JavaPlugin, private val loader: StructureLoader) : CommandExecutor, TabCompleter {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(MessageManager.getMessage(null as Player?, "command_player_only"))
            return true
        }

        if (args.isEmpty()) {
            sender.sendMessage(MessageManager.getMessage(sender, "command_usage"))
            return true
        }

        when (args[0].lowercase()) {
            "reload" -> {
                if (!sender.hasPermission("sukimadungeon.admin")) {
                    sender.sendMessage(MessageManager.getMessage(sender, "no_permission"))
                    return true
                }
                CCContent.instance.reloadSukimaDungeon()
                sender.sendMessage(MessageManager.getMessage(sender, "command_reload_success"))
            }
            "add_item" -> {
                if (!sender.hasPermission("sukimadungeon.admin")) {
                    sender.sendMessage(MessageManager.getMessage(sender, "no_permission"))
                    return true
                }
                if (args.size < 3) {
                    sender.sendMessage("§c使用法: /sd add_item <ID> <weight>")
                    return true
                }
                val id = args[1].lowercase()
                val weight = args[2].toIntOrNull()
                if (weight == null || weight <= 0) {
                    sender.sendMessage("§c重みは正の整数で指定してください。")
                    return true
                }
                val item = sender.inventory.itemInMainHand
                if (item.type.isAir) {
                    sender.sendMessage("§cアイテムを手に持ってください。")
                    return true
                }
                
                try {
                    val itemManager = CCContent.instance.getItemManager()
                    itemManager.saveItem(id, item, weight)
                    sender.sendMessage("§aアイテムを items.yml に登録しました。")
                } catch (e: Exception) {
                    sender.sendMessage("§cアイテムの登録に失敗しました: ${e.message}")
                    e.printStackTrace()
                }
            }
            else -> {
                sender.sendMessage(MessageManager.getMessage(sender, "command_unknown"))
            }
        }

        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String>? {
        if (args.size == 1) {
            return listOf("reload", "add_item").filter { it.startsWith(args[0], true) }
        }

        if (args[0].equals("add_item", ignoreCase = true)) {
            when (args.size) {
                2 -> return listOf("<ID>")
                3 -> return listOf("<weight>")
            }
        }

        return null
    }
}
