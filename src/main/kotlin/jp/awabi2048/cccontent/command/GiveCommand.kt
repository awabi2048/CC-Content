package jp.awabi2048.cccontent.command

import jp.awabi2048.cccontent.items.CustomItemManager
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

/**
 * /cc give コマンド
 * 形式: /cc give <player> <feature.id> [amount]
 * 例: /cc give @s misc.big_light
 */
class GiveCommand : CommandExecutor, TabCompleter {
    
    override fun onCommand(
        sender: CommandSender,
        cmd: Command,
        label: String,
        args: Array<String>
    ): Boolean {
        // 構文チェック
         if (args.size < 2) {
             sender.sendMessage("§c使用方法: /cc give <player> <feature.id> [amount]")
             sender.sendMessage("§c例: /cc give @s misc.big_light")
             return true
         }
         
         // プレイヤー取得
         val playerArg = args[0]
        val targetPlayers = if (playerArg == "@s") {
            if (sender is Player) listOf(sender) else {
                sender.sendMessage("§cこのコマンドはプレイヤーによってのみ実行できます")
                return true
            }
        } else if (playerArg == "@a") {
            Bukkit.getOnlinePlayers().toList()
        } else {
            val player = Bukkit.getPlayer(playerArg)
            if (player == null) {
                sender.sendMessage("§cプレイヤー '§f$playerArg§c' が見つかりません")
                return true
            }
            listOf(player)
        }
        
        if (targetPlayers.isEmpty()) {
            sender.sendMessage("§c対象プレイヤーが見つかりません")
            return true
        }
        
        // アイテムID取得
         val fullId = args[1]
         
         // 数量取得
         val amount = args.getOrNull(2)?.toIntOrNull() ?: 1
        if (amount <= 0) {
            sender.sendMessage("§c数量は1以上である必要があります")
            return true
        }
        
        if (CustomItemManager.getItem(fullId) == null) {
            sender.sendMessage("§cアイテムID '§f$fullId§c' が見つかりません")
            return true
        }
        
        // プレイヤーに配布
        targetPlayers.forEach { player ->
            val item = CustomItemManager.createItemForPlayer(fullId, player, amount)
            if (item != null) {
                player.inventory.addItem(item)
            }
        }
        
        // 通知
        if (targetPlayers.size == 1) {
            val player = targetPlayers[0]
            sender.sendMessage("§a${player.name} に §f$fullId§a を §f$amount§a 個配布しました")
        } else {
            sender.sendMessage("§a${targetPlayers.size} 人のプレイヤーに §f$fullId§a を §f$amount§a 個配布しました")
        }
        
        return true
    }
    
    override fun onTabComplete(
         sender: CommandSender,
         cmd: Command,
         alias: String,
         args: Array<String>
     ): List<String> {
         return when (args.size) {
             // プレイヤー名補完（/cc give [ここ]）
             1 -> {
                 val playerPrefix = args[0].lowercase()
                 listOf("@s", "@a") + Bukkit.getOnlinePlayers()
                     .map { it.name }
                     .filter { it.lowercase().startsWith(playerPrefix) }
             }
             // アイテムID補完（/cc give <player> [ここ]）
             2 -> {
                 val itemPrefix = args[1].lowercase()
                 CustomItemManager.getAllItemIds()
                     .filter { it.lowercase().startsWith(itemPrefix) }
                     .toList()
             }
             else -> emptyList()
         }
     }
}
