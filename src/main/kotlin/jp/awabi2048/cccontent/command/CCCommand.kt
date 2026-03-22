package jp.awabi2048.cccontent.command

import org.bukkit.Location
import org.bukkit.command.Command
import org.bukkit.command.BlockCommandSender
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.util.Vector

/**
 * /cc-content メインコマンド
 * サブコマンド処理の分岐を担当
 */
class CCCommand(
    private val giveCommand: GiveCommand,
    private val onReload: (() -> Unit)? = null,
    private val onClearBlockPlacementData: (() -> Unit)? = null,
    private val mobDefinitionIdsProvider: (() -> Collection<String>)? = null,
    private val onSummonMob: ((String, Location) -> LivingEntity?)? = null,
    private val onBatchBreakDebug: ((Player, String, Int, Int, Boolean) -> Boolean)? = null,
    private val onBlastMineDebug: ((Player, Double, Int, Boolean, Double) -> Boolean)? = null,
    private val onUpdateDay: ((String?) -> Boolean)? = null
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
            "summon" -> {
                handleSummon(sender, args)
            }
            "debug" -> {
                handleDebug(sender, args)
            }
            "update_day" -> {
                handleUpdateDay(sender, args)
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

    private fun handleSummon(sender: CommandSender, args: Array<String>): Boolean {
        if (!sender.hasPermission("cc-content.admin")) {
            sender.sendMessage("§c権限がありません")
            return false
        }

        if (onSummonMob == null || mobDefinitionIdsProvider == null) {
            sender.sendMessage("§cモブ召喚機能が利用できません")
            return false
        }

        if (args.size != 5) {
            sender.sendMessage("§c使用法: /ccc summon <mob_definition_id> <x> <y> <z>")
            return false
        }

        val definitionId = args[1]
        if (!mobDefinitionIdsProvider.invoke().contains(definitionId)) {
            sender.sendMessage("§c未定義の mob_definition です: $definitionId")
            return false
        }

        val location = try {
            parseLocation(sender, args[2], args[3], args[4])
        } catch (e: IllegalArgumentException) {
            sender.sendMessage("§c${e.message}")
            return false
        }

        val entity = onSummonMob.invoke(definitionId, location)
        if (entity == null) {
            sender.sendMessage("§cモブの召喚に失敗しました: $definitionId")
            return false
        }

        sender.sendMessage(
            "§aモブを召喚しました: id=${definitionId}, type=${entity.type}, world=${location.world?.name}, x=${formatCoord(location.x)}, y=${formatCoord(location.y)}, z=${formatCoord(location.z)}"
        )
        return true
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

    private fun handleBatchBreakDebug(sender: CommandSender, player: Player, args: Array<String>): Boolean {
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

    private fun handleBlastMineDebug(sender: CommandSender, player: Player, args: Array<String>): Boolean {
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

    private fun handleUpdateDay(sender: CommandSender, args: Array<String>): Boolean {
        if (!sender.hasPermission("cc-content.admin")) {
            sender.sendMessage("§c権限がありません")
            return false
        }

        if (onUpdateDay == null) {
            sender.sendMessage("§c日付更新機能が利用できません")
            return false
        }

        if (args.size > 2) {
            sender.sendMessage("§c使用法: /ccc update_day [arena]")
            return false
        }

        val target = args.getOrNull(1)?.lowercase()
        if (target != null && target != "arena") {
            sender.sendMessage("§c不明な更新対象です: $target")
            sender.sendMessage("§7現在指定できるのは arena のみです")
            return false
        }

        if (target == null) {
            sender.sendMessage("§6日付更新を実行中...")
        } else {
            sender.sendMessage("§6日付更新を実行中: $target")
        }

        val result = onUpdateDay.invoke(target)
        if (!result) {
            sender.sendMessage("§c日付更新に失敗しました")
            return false
        }

        sender.sendMessage(if (target == null) "§a日付更新を完了しました" else "§a日付更新を完了しました: $target")
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
            sender.sendMessage("§6CC-Content を再起動中...")
            onReload.invoke()
            sender.sendMessage("§aCC-Content を再起動しました")
            return true
        } catch (e: Exception) {
            sender.sendMessage("§c再起動中にエラーが発生しました: ${e.message}")
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
              §7  - 例: /cc-content give @a arena.prize 10
              §7  - 例: /cc-content give @s sukima_dungeon.talisman
              
                §f/cc-content reload
                §7  - プラグインを再起動相当に再初期化します

               §f/ccc summon <mob_definition_id> <x> <y> <z>
               §7  - 共通 mob_definition からモブを召喚します
               §7  - 例: /ccc summon spark_zombie_basic ~ ~ ~

               §f/ccc clear_block_placement_data
               §7  - プレイヤー設置ブロック判定データを削除します

              §f/ccc debug <mine_all|cut_all> <delay> <max_chain> <auto_collect>
              §7  - MineAll/CutAll のデバッグ設定を適用します

               §f/ccc debug blast_mine <radius> <delay> <auto_collect> <loss_rate>
               §7  - BlastMine のデバッグ設定を適用します

               §f/ccc update_day [arena]
               §7  - 日付更新処理を実行します
               
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
                    candidates.add("summon")
                    candidates.add("clear_block_placement_data")
                    candidates.add("debug")
                    candidates.add("update_day")
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
               "update_day" -> {
                   if (args.size == 2) {
                       listOf("arena").filter { it.startsWith(args[1], ignoreCase = true) }
                   } else {
                       emptyList()
                   }
               }
               "summon" -> {
                   when (args.size) {
                      2 -> {
                          val ids = mobDefinitionIdsProvider?.invoke().orEmpty().sorted()
                          ids.filter { it.startsWith(args[1], ignoreCase = true) }
                      }
                      3, 4, 5 -> listOf("~", "~1", "~-1").filter { it.startsWith(args[args.lastIndex]) }
                      else -> emptyList()
                  }
              }
              else -> emptyList()
          }
      }

    private fun parseLocation(sender: CommandSender, xArg: String, yArg: String, zArg: String): Location {
        val baseLocation = senderLocation(sender)
        val args = listOf(xArg, yArg, zArg)

        return if (args.any { it.startsWith("^") }) {
            if (args.any { !it.startsWith("^") }) {
                throw IllegalArgumentException("ローカル座標(^)と通常座標は混在できません")
            }
            val anchor = baseLocation ?: throw IllegalArgumentException("ローカル座標(^)は位置を持つ実行者のみ使用できます")
            parseLocalLocation(anchor, xArg, yArg, zArg)
        } else {
            val anchor = baseLocation ?: throw IllegalArgumentException("このコマンドは位置を持つ実行者のみ使用できます")
            Location(
                anchor.world,
                parseWorldCoordinate(anchor.x, xArg),
                parseWorldCoordinate(anchor.y, yArg),
                parseWorldCoordinate(anchor.z, zArg),
                anchor.yaw,
                anchor.pitch
            )
        }
    }

    private fun parseWorldCoordinate(base: Double, raw: String): Double {
        return if (raw.startsWith("~")) {
            if (raw == "~") {
                base
            } else {
                base + (raw.substring(1).toDoubleOrNull()
                    ?: throw IllegalArgumentException("座標の指定が不正です: $raw"))
            }
        } else {
            raw.toDoubleOrNull() ?: throw IllegalArgumentException("座標の指定が不正です: $raw")
        }
    }

    private fun parseLocalLocation(anchor: Location, xArg: String, yArg: String, zArg: String): Location {
        val x = parseLocalComponent(xArg)
        val y = parseLocalComponent(yArg)
        val z = parseLocalComponent(zArg)

        val forward = anchor.direction.normalize()
        var left = Vector(0, 1, 0).crossProduct(forward).normalize()
        if (left.lengthSquared() == 0.0) {
            left = Vector(1, 0, 0)
        }
        val up = forward.clone().crossProduct(left).normalize()
        val offset = left.multiply(x).add(up.multiply(y)).add(forward.multiply(z))

        return anchor.clone().add(offset)
    }

    private fun parseLocalComponent(raw: String): Double {
        if (!raw.startsWith("^")) {
            throw IllegalArgumentException("ローカル座標は ^ を使用してください: $raw")
        }
        return if (raw == "^") {
            0.0
        } else {
            raw.substring(1).toDoubleOrNull() ?: throw IllegalArgumentException("座標の指定が不正です: $raw")
        }
    }

    private fun senderLocation(sender: CommandSender): Location? {
        return when (sender) {
            is Entity -> sender.location
            is BlockCommandSender -> sender.block.location.add(0.5, 0.0, 0.5)
            else -> null
        }
    }

    private fun formatCoord(value: Double): String {
        return String.format(java.util.Locale.ROOT, "%.2f", value)
    }
}
