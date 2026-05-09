package jp.awabi2048.cccontent.command

import org.bukkit.Location
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.BlockCommandSender
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.util.Vector

/**
 * /cc-content メインコマンド
 * サブコマンド処理の分岐を担当
 */
class CCCommand(
    private val giveCommand: GiveCommand,
    private val onReload: (() -> Unit)? = null,
    private val onRestart: (() -> Unit)? = null,
    private val onClearBlockPlacementData: (() -> Unit)? = null,
    private val mobDefinitionIdsProvider: (() -> Collection<String>)? = null,
    private val onSummonMob: ((String, Location) -> Entity?)? = null,
    private val onUpdateDay: ((String?) -> Boolean)? = null,
    private val npcMenuIdsProvider: (() -> Collection<String>)? = null,
    private val onOpenNpcMenu: ((String, Player) -> Boolean)? = null,
    private val onNpcMenuMaintenance: ((String, String) -> Boolean)? = null
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
            "restart" -> {
                handleRestart(sender)
            }
            "summon" -> {
                handleSummon(sender, args)
            }
            "debug" -> {
                handleDebug(sender, args)
            }
            "npc-menu" -> {
                handleNpcMenu(sender, args)
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

        if (args.size != 2 && args.size != 5) {
            sender.sendMessage("§c使用法: /ccc summon <mob_definition_id> [<x> <y> <z>]")
            return false
        }

        val definitionId = args[1]
        if (!mobDefinitionIdsProvider.invoke().contains(definitionId)) {
            sender.sendMessage("§c未定義の mob_definition です: $definitionId")
            return false
        }

        val location = if (args.size == 2) {
            senderLocation(sender)?.clone() ?: run {
                sender.sendMessage("§c座標省略は位置を持つ実行者のみ使用できます")
                return false
            }
        } else {
            try {
                parseLocation(sender, args[2], args[3], args[4])
            } catch (e: IllegalArgumentException) {
                sender.sendMessage("§c${e.message}")
                return false
            }
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

        if (args.size < 2) {
            sender.sendMessage("§c使用法: /ccc debug <clear_block_placement_data|update_day> ...")
            return false
        }

        val mode = args[1].lowercase()

        return when (mode) {
            "clear_block_placement_data" -> handleClearBlockPlacementData(sender, args)
            "update_day" -> handleUpdateDay(sender, (listOf("update_day") + args.drop(2)).toTypedArray())
            else -> {
                sender.sendMessage("§cmodeは clear_block_placement_data または update_day を指定してください")
                false
            }
        }
    }

    private fun handleNpcMenu(sender: CommandSender, args: Array<String>): Boolean {
        if (!sender.hasPermission("cc-content.npc.menu.open")) {
            sender.sendMessage("§c権限がありません")
            return false
        }

        if (onOpenNpcMenu == null) {
            sender.sendMessage("§cNPCメニュー機能が利用できません")
            return false
        }

        if (args.size !in 2..3) {
            sender.sendMessage("§c使用法: /ccc npc-menu <menu_id> [player]")
            return false
        }

        val menuId = args[1]
        val maintenanceAction = args.getOrNull(2)?.lowercase()
        if (maintenanceAction == "reset-delivery" || maintenanceAction == "reset-part-time") {
            if (onNpcMenuMaintenance == null || !onNpcMenuMaintenance.invoke(menuId, maintenanceAction)) {
                sender.sendMessage("§cNPCメニューのリセットに失敗しました: $maintenanceAction")
                return false
            }
            sender.sendMessage("§aNPCメニューの状態をリセットしました: $maintenanceAction")
            return true
        }

        val target = if (args.size == 3) {
            Bukkit.getPlayerExact(args[2]) ?: run {
                sender.sendMessage("§cプレイヤーが見つかりません: ${args[2]}")
                return false
            }
        } else {
            sender as? Player ?: run {
                sender.sendMessage("§cプレイヤー名を指定してください")
                return false
            }
        }

        return onOpenNpcMenu.invoke(menuId, target)
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
            sender.sendMessage("§c使用法: /ccc debug update_day [arena]")
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

    private fun handleClearBlockPlacementData(sender: CommandSender, args: Array<String>): Boolean {
        if (!sender.hasPermission("cc-content.admin")) {
            sender.sendMessage("§c権限がありません")
            return false
        }

        if (args.size != 2) {
            sender.sendMessage("§c使用法: /ccc debug clear_block_placement_data")
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
            sender.sendMessage("§6config を再読込中...")
            onReload.invoke()
            sender.sendMessage("§aconfig を再読込しました")
            return true
        } catch (e: Exception) {
            sender.sendMessage("§c再読込中にエラーが発生しました: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    private fun handleRestart(sender: CommandSender): Boolean {
        if (!sender.hasPermission("cc-content.admin")) {
            sender.sendMessage("§c権限がありません")
            return false
        }

        if (onRestart == null) {
            sender.sendMessage("§c再起動機能が利用できません")
            return false
        }

        try {
            sender.sendMessage("§6CC-Content を再起動中...")
            onRestart.invoke()
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
              
                 §f/ccc reload
                 §7  - config 配下の設定を再読込します

                 §f/ccc restart
                 §7  - プラグインを再起動相当に再初期化します

               §f/ccc summon <mob_definition_id> [<x> <y> <z>]
               §7  - 共通 mob_definition からモブを召喚します
               §7  - 座標省略時は実行者の現在地で召喚します
               §7  - 例: /ccc summon zombie_leap_only ~ ~ ~
               §7  - 例: /ccc summon zombie_leap_only

               §f/ccc debug clear_block_placement_data
               §7  - プレイヤー設置ブロック判定データを削除します

              §f/ccc debug update_day [arena]
              §7  - 日付更新処理を実行します

              §f/ccc npc-menu <menu_id> [player]
              §7  - NPCメニューを開きます
               
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
                     candidates.add("restart")
                     candidates.add("summon")
                     candidates.add("debug")
                }
                if (sender.hasPermission("cc-content.npc.menu.open")) {
                    candidates.add("npc-menu")
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
                      2 -> listOf("clear_block_placement_data", "update_day").filter { it.startsWith(args[1].lowercase()) }
                     3 -> {
                         when (args[1].lowercase()) {
                             "update_day" -> listOf("arena").filter { it.startsWith(args[2], ignoreCase = true) }
                             else -> emptyList()
                         }
                     }
                      else -> emptyList()
                   }
               }
               "summon" -> {
                   when (args.size) {
                      2 -> mobDefinitionIdsProvider?.invoke().orEmpty().sorted().filter { it.startsWith(args[1], ignoreCase = true) }
                      3, 4, 5 -> listOf("~", "~1", "~-1").filter { it.startsWith(args[args.lastIndex]) }
                      else -> emptyList()
                  }
              }
              "npc-menu" -> {
                  when (args.size) {
                      2 -> npcMenuIdsProvider?.invoke().orEmpty().sorted().filter { it.startsWith(args[1], ignoreCase = true) }
                      3 -> (listOf("reset-delivery", "reset-part-time") + Bukkit.getOnlinePlayers().map { it.name }).filter { it.startsWith(args[2], ignoreCase = true) }
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
