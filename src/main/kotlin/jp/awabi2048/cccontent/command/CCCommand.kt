package jp.awabi2048.cccontent.command

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter

/**
 * /cc メインコマンド
 * サブコマンド処理の分岐を担当
 */
class CCCommand(
    private val giveCommand: GiveCommand
) : CommandExecutor, TabCompleter {
    
    override fun onCommand(
        sender: CommandSender,
        cmd: Command,
        label: String,
        args: Array<String>
    ): Boolean {
        // コマンド引数がない場合
        if (args.isEmpty()) {
            showHelp(sender)
            return true
        }
        
        // サブコマンド処理
        return when (args[0].lowercase()) {
            "give" -> {
                val subArgs = args.drop(1).toTypedArray()
                giveCommand.onCommand(sender, cmd, "give", subArgs)
            }
            "help" -> {
                showHelp(sender)
                true
            }
            else -> {
                sender.sendMessage("§c不明なサブコマンド: ${args[0]}")
                showHelp(sender)
                true
            }
        }
    }
    
    private fun showHelp(sender: CommandSender) {
         sender.sendMessage("""
             §6§l=== CC-Content コマンド一覧 ===
             §f/cc give <player> <feature.id> [amount]
             §7  - カスタムアイテムを配布します
             §7  - 例: /cc give @s misc.big_light
             §7  - 例: /cc give @a arena.soul_bottle 10
             
             §f/arena §7- アリーナコマンド
             §f/arenaa §7- アリーナ管理コマンド
             §f/party §7- パーティコマンド
             §f/sukima_dungeon §7- スキマダンジョンコマンド
         """.trimIndent())
     }
     
     override fun onTabComplete(
         sender: CommandSender,
         cmd: Command,
         alias: String,
         args: Array<String>
     ): List<String> {
         if (args.isEmpty()) return emptyList()
         
         // サブコマンド補完（/cc [ここ]）
         if (args.size == 1) {
             val prefix = args[0].lowercase()
             return listOf("give", "help").filter { it.startsWith(prefix) }
         }
         
         // サブコマンドの引数補完
         return when (args[0].lowercase()) {
             "give" -> {
                 val subArgs = args.drop(1).toTypedArray()
                 giveCommand.onTabComplete(sender, cmd, "give", subArgs)
             }
             else -> emptyList()
         }
     }
}
