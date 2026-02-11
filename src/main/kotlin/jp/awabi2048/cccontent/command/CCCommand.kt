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
    private val onClearBlockPlacementData: (() -> Unit)? = null,
    private val onBatchBreakDebug: ((org.bukkit.entity.Player, String, Int, Int, Boolean) -> Boolean)? = null,
    private val onBlastMineDebug: ((org.bukkit.entity.Player, Double, Int, Boolean, Double) -> Boolean)? = null
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
            "debug" -> {
                handleDebug(sender, args)
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

    private fun handleDebug(sender: CommandSender, args: Array<String>): Boolean {
        if (!sender.hasPermission("cc-content.admin")) {
            sender.sendMessage("§c権限がありません")
            return false
        }

        val player = sender as? org.bukkit.entity.Player
        if (player == null) {
            sender.sendMessage("§cこのコマンドはプレイヤーのみ実行できます")
            return false
        }

        if (args.size < 2) {
            sender.sendMessage("§c使用法: /ccc debug <mine_all|cut_all|blast_mine> ...")
            return false
        }

        val mode = args[1].lowercase()

        return when (mode) {
            "mine_all", "cut_all" -> handleBatchBreakDebug(sender, player, args)
            "blast_mine" -> handleBlastMineDebug(sender, player, args)
            else -> {
                sender.sendMessage("§cmodeは mine_all, cut_all, または blast_mine を指定してください")
                false
            }
        }
    }

    private fun handleBatchBreakDebug(sender: CommandSender, player: org.bukkit.entity.Player, args: Array<String>): Boolean {
        if (onBatchBreakDebug == null) {
            sender.sendMessage("§cデバッグ機能が利用できません")
            return false
        }

        if (args.size != 5) {
            sender.sendMessage("§c使用法: /ccc debug <mine_all|cut_all> <delay> <max_chain> <auto_collect>")
            return false
        }

        val mode = args[1].lowercase()
        val delay = args[2].toIntOrNull()
        val maxChain = args[3].toIntOrNull()
        val autoCollect = args[4].toBooleanStrictOrNull()
        if (delay == null || maxChain == null || autoCollect == null) {
            sender.sendMessage("§cdelay/max_chain/auto_collect の指定が不正です")
            sender.sendMessage("§7例: /ccc debug mine_all 0 32 true")
            return false
        }

        val result = onBatchBreakDebug.invoke(player, mode, delay, maxChain, autoCollect)
        if (!result) {
            sender.sendMessage("§cデバッグ設定の反映に失敗しました")
            return false
        }

        sender.sendMessage("§aデバッグ設定を反映しました: mode=$mode, delay=$delay, max_chain=$maxChain, auto_collect=$autoCollect")
        return true
    }

    private fun handleBlastMineDebug(sender: CommandSender, player: org.bukkit.entity.Player, args: Array<String>): Boolean {
        if (onBlastMineDebug == null) {
            sender.sendMessage("§cデバッグ機能が利用できません")
            return false
        }

        if (args.size != 6) {
            sender.sendMessage("§c使用法: /ccc debug blast_mine <radius> <delay_ticks_per_layer> <auto_collect> <loss_rate>")
            return false
        }

        val radius = args[2].toDoubleOrNull()
        val delayTicksPerLayer = args[3].toIntOrNull()
        val autoCollect = args[4].toBooleanStrictOrNull()
        val lossRate = args[5].toDoubleOrNull()

        if (radius == null || delayTicksPerLayer == null || autoCollect == null || lossRate == null) {
            sender.sendMessage("§cradius/delay_ticks_per_layer/auto_collect/loss_rate の指定が不正です")
            sender.sendMessage("§7例: /ccc debug blast_mine 3.0 2 true 0.1")
            return false
        }

        val result = onBlastMineDebug.invoke(player, radius, delayTicksPerLayer, autoCollect, lossRate)
        if (!result) {
            sender.sendMessage("§cデバッグ設定の反映に失敗しました")
            return false
        }

        sender.sendMessage("§aデバッグ設定を反映しました: radius=$radius, delay=$delayTicksPerLayer, auto_collect=$autoCollect, loss_rate=$lossRate")
        return true
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

              §f/ccc debug <mine_all|cut_all> <delay> <max_chain> <auto_collect>
              §7  - MineAll/CutAll のデバッグ設定を適用します

              §f/ccc debug blast_mine <radius> <delay> <auto_collect> <loss_rate>
              §7  - BlastMine のデバッグ設定を適用します
              
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
                    candidates.add("debug")
                }
               return candidates.filter { it.startsWith(prefix) }
           }
         
         // サブコマンドの引数補完
         return when (args[0].lowercase()) {
             "give" -> {
                  val subArgs = args.drop(1).toTypedArray()
                  giveCommand.onTabComplete(sender, cmd, "give", subArgs)
              }
             "debug" -> {
                 when (args.size) {
                     2 -> listOf("mine_all", "cut_all", "blast_mine").filter { it.startsWith(args[1].lowercase()) }
                     3 -> {
                         when (args[1].lowercase()) {
                             "mine_all", "cut_all" -> listOf("0", "1", "2", "3", "5").filter { it.startsWith(args[2]) }
                             "blast_mine" -> listOf("2.0", "3.0", "4.0", "5.0").filter { it.startsWith(args[2]) }
                             else -> emptyList()
                         }
                     }
                     4 -> {
                         when (args[1].lowercase()) {
                             "mine_all", "cut_all" -> listOf("16", "24", "32", "64").filter { it.startsWith(args[3]) }
                             "blast_mine" -> listOf("0", "1", "2", "3").filter { it.startsWith(args[3]) }
                             else -> emptyList()
                         }
                     }
                     5 -> {
                         when (args[1].lowercase()) {
                             "mine_all", "cut_all" -> listOf("true", "false").filter { it.startsWith(args[4].lowercase()) }
                             "blast_mine" -> listOf("true", "false").filter { it.startsWith(args[4].lowercase()) }
                             else -> emptyList()
                         }
                     }
                     6 -> {
                         when (args[1].lowercase()) {
                             "blast_mine" -> listOf("0.0", "0.1", "0.2", "0.3").filter { it.startsWith(args[5]) }
                             else -> emptyList()
                         }
                     }
                     else -> emptyList()
                 }
             }
             else -> emptyList()
         }
     }
}
