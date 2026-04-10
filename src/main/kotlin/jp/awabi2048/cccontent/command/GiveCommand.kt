package jp.awabi2048.cccontent.command

import jp.awabi2048.cccontent.items.CustomItemManager
import jp.awabi2048.cccontent.items.arena.ArenaEnchantShardItem
import jp.awabi2048.cccontent.items.arena.ArenaEnchantShardRegistry
import jp.awabi2048.cccontent.items.arena.ArenaMobTokenItem
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
        
         val arenaTokenCategory = parseArenaTokenCategory(fullId)
         val arenaEnchantShard = parseArenaEnchantShard(fullId)
         if (arenaEnchantShard == null && fullId == "arena.enchant_shard") {
             sender.sendMessage("§cエンチャントシャードは 'arena.enchant_shard#limit_breaking:sharpness:1' の形式で指定してください")
             return true
         }
         if (arenaTokenCategory == null && arenaEnchantShard == null && CustomItemManager.getItem(fullId) == null) {
             sender.sendMessage("§cアイテムID '§f$fullId§c' が見つかりません")
             return true
         }

        // プレイヤーに配布
        targetPlayers.forEach { player ->
            if (arenaEnchantShard != null) {
                repeat(amount) {
                    player.inventory.addItem(ArenaEnchantShardItem.createShard(player, arenaEnchantShard, 1))
                }
            } else {
                val item = if (arenaTokenCategory != null) {
                    CustomItemManager.createItemForPlayer(ArenaMobTokenItem(arenaTokenCategory), player, amount)
                } else {
                    CustomItemManager.createItemForPlayer(fullId, player, amount)
                }
                if (item != null) {
                    player.inventory.addItem(item)
                }
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
                   val baseCandidates = CustomItemManager.getAllItemIds()
                       .filterNot { it == "arena.enchant_shard" }
                       .filter { it.lowercase().startsWith(itemPrefix) }
                   val tokenCandidates = when {
                       itemPrefix.startsWith("arena.mob_token#") -> {
                          val raw = itemPrefix.substringAfter("#", "")
                          ArenaMobTokenItem.supportedTokenCategoryTypeIds()
                              .sorted()
                              .map { "arena.mob_token#$it" }
                              .filter { it.startsWith("arena.mob_token#$raw") }
                      }
                       "arena.mob_token#".startsWith(itemPrefix) -> listOf("arena.mob_token#")
                       else -> emptyList()
                   }
                   val shardCandidates = when {
                       itemPrefix.startsWith("arena.enchant_shard#") -> {
                           val raw = itemPrefix.substringAfter("#", "")
                           ArenaEnchantShardRegistry.supportedSpecs()
                               .map { "arena.enchant_shard#$it" }
                               .filter { it.startsWith("arena.enchant_shard#$raw") }
                       }
                       "arena.enchant_shard#".startsWith(itemPrefix) -> listOf("arena.enchant_shard#")
                       else -> emptyList()
                   }
                   (baseCandidates + tokenCandidates + shardCandidates).distinct().toList()
               }
              else -> emptyList()
          }
      }

    private fun parseArenaTokenCategory(fullId: String): String? {
        if (!fullId.startsWith("arena.mob_token#")) {
            return null
        }
        val rawCategory = fullId.substringAfter('#', "").trim()
        if (rawCategory.isBlank()) {
            return null
        }
        if (!ArenaMobTokenItem.supportsTokenCategoryTypeId(rawCategory)) {
            return null
        }
        return ArenaMobTokenItem.resolveTokenCategoryTypeId(rawCategory)
    }

    private fun parseArenaEnchantShard(fullId: String) = when {
        !fullId.startsWith("arena.enchant_shard#") -> null
        else -> ArenaEnchantShardRegistry.findBySpec(fullId.substringAfter('#', ""))
    }
 }
