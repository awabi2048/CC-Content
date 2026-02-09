package jp.awabi2048.cccontent.command

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter

/**
 * /cc-content メインコマンド
 * サブコマンド処理の分岐を担当
 */
class CCCommand(
    private val giveCommand: GiveCommand,
    private val onReload: (() -> Unit)? = null,
    private val onClearBlockPlacementData: (() -> Unit)? = null
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
            "reload" -> {
                handleReload(sender)
            }
            "clear_block_placement_data" -> {
                handleClearBlockPlacementData(sender)
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

    private fun handleClearBlockPlacementData(sender: CommandSender): Boolean {
        if (!sender.hasPermission("cc-content.admin")) {
            sender.sendMessage("§c権限がありません")
            return false
        }

        if (onClearBlockPlacementData == null) {
            sender.sendMessage("§cブロック設置データ削除機能が利用できません")
            return false
        }

        try {
            sender.sendMessage("§6ブロック設置データを削除中...")
            onClearBlockPlacementData.invoke()
            sender.sendMessage("§aブロック設置データを削除しました")
            return true
        } catch (e: Exception) {
            sender.sendMessage("§c削除中にエラーが発生しました: ${e.message}")
            e.printStackTrace()
            return false
        }
    }
    
    /**
     * reload コマンドを処理
     */
    private fun handleReload(sender: CommandSender): Boolean {
        // 権限確認
        if (!sender.hasPermission("cc-content.admin")) {
            sender.sendMessage("§c権限がありません")
            return false
        }
        
        if (onReload == null) {
            sender.sendMessage("§cリロード機能が利用できません")
            return false
        }
        
        try {
            sender.sendMessage("§6CC-Content の設定ファイルをリロード中...")
            onReload.invoke()
            sender.sendMessage("§aCC-Content の設定ファイルをリロードしました")
            return true
        } catch (e: Exception) {
            sender.sendMessage("§cリロード中にエラーが発生しました: ${e.message}")
            e.printStackTrace()
            return false
        }
    }
    
    private fun showHelp(sender: CommandSender) {
         sender.sendMessage("""
              §6§l=== CC-Content コマンド一覧 ===
              §f/cc-content give <player> <feature.id> [amount]
              §7  - カスタムアイテムを配布します
              §7  - 例: /cc-content give @s misc.big_light
              §7  - 例: /cc-content give @a arena.soul_bottle 10
              
              §f/cc-content reload
              §7  - 設定ファイルをリロードします

              §f/ccc clear_block_placement_data
              §7  - プレイヤー設置ブロック判定データを削除します
              
              §f/arenaa §7- アリーナ管理コマンド
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
         
          // サブコマンド補完（/cc-content [ここ]）
          if (args.size == 1) {
              val prefix = args[0].lowercase()
               val candidates = mutableListOf("give", "help")
               if (sender.hasPermission("cc-content.admin")) {
                   candidates.add("reload")
                   candidates.add("clear_block_placement_data")
               }
              return candidates.filter { it.startsWith(prefix) }
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
