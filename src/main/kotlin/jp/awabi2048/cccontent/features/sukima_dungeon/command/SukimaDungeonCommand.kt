package jp.awabi2048.cccontent.features.sukima_dungeon.command

import jp.awabi2048.cccontent.CCContent
import jp.awabi2048.cccontent.features.sukima_dungeon.DungeonSessionManager
import jp.awabi2048.cccontent.items.sukima_dungeon.DungeonTier
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

/**
 * スキマダンジョンコマンド
 * 形式:
 * - /sukima_dungeon help
 * - /sukima_dungeon tier <tier>
 * - /sukima_dungeon status
 * - /sukima_dungeon info
 * - /sukima_dungeon stop <player>
 */
class SukimaDungeonCommand : CommandExecutor, TabCompleter {

    override fun onCommand(
        sender: CommandSender,
        cmd: Command,
        label: String,
        args: Array<String>
    ): Boolean {
        if (args.isEmpty()) {
            sendUsage(sender)
            return true
        }

        return when (args[0].lowercase()) {
            "help" -> handleHelp(sender)
            "tier" -> handleTier(sender, args)
            "status" -> handleStatus(sender, args)
            "info" -> handleInfo(sender, args)
            "stop" -> handleStop(sender, args)
            else -> {
                sendUsage(sender)
                true
            }
        }
    }

    /**
     * ティア別ダンジョン開始
     */
    private fun handleTier(sender: CommandSender, args: Array<String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("§cこのコマンドはプレイヤーによってのみ実行できます")
            return true
        }

        if (args.size < 2) {
            sender.sendMessage("§c使用法: /sukima_dungeon tier <tier>")
            sender.sendMessage("§c利用可能なティア: ${DungeonTier.entries.joinToString(", ") { it.internalName }}")
            return true
        }

        val tierName = args[1].uppercase()
        val tier = try {
            DungeonTier.valueOf(tierName)
        } catch (e: IllegalArgumentException) {
            sender.sendMessage("§cティア '§f$tierName§c' が見つかりません")
            return true
        }

        // ブックマークアイテムを対応するティアに設定してコンパスを使用する処理を開始
        sender.sendMessage("§aティア §f${tier.displayName}§a のダンジョンを開始します")
        // 実装はブックマークアイテムのリスナーで処理される
        return true
    }

    /**
     * ダンジョンセッションステータス表示
     */
    private fun handleStatus(sender: CommandSender, args: Array<String>): Boolean {
        val sessions = DungeonSessionManager.getAllSessionsAsCollection()

        if (sessions.isEmpty()) {
            sender.sendMessage("§e現在アクティブなダンジョンセッションはありません")
            return true
        }

        sender.sendMessage("§6=== アクティブなダンジョンセッション ===")
        sessions.forEach { session ->
            val player = Bukkit.getPlayer(session.playerUUID)
            val playerName = player?.name ?: "§7(オフライン)"
            val floorInfo = "進捗 ${session.collectedSprouts}/${session.totalSprouts}"
            sender.sendMessage("§e  - プレイヤー: §f$playerName§e | ティア: §f${session.tier.displayName}§e | $floorInfo")
        }
        sender.sendMessage("§6合計: §f${sessions.size}§6 セッション")
        return true
    }

    /**
     * ダンジョン情報表示
     */
    private fun handleInfo(sender: CommandSender, args: Array<String>): Boolean {
        sender.sendMessage("§6=== スキマダンジョン情報 ===")
        sender.sendMessage("§eティア一覧:")
        DungeonTier.entries.forEach { tier ->
            sender.sendMessage("§f  - ${tier.internalName}§e: §f${tier.displayName} (Lv${tier.tier})")
        }
        sender.sendMessage("§e")
        sender.sendMessage("§eコマンド:")
        sender.sendMessage("§f  - /sukima_dungeon tier <tier>§e: ティアを指定してダンジョンを開始")
        sender.sendMessage("§f  - /sukima_dungeon status§e: セッション一覧を表示")
        sender.sendMessage("§f  - /sukima_dungeon info§e: このメッセージを表示")
        if (sender.hasPermission("cc-content.admin")) {
            sender.sendMessage("§f  - /sukima_dungeon stop <player>§e: セッションを停止（管理者用）")
        }
        return true
    }

    /**
     * ダンジョンセッション停止
     */
    private fun handleStop(sender: CommandSender, args: Array<String>): Boolean {
        if (!sender.hasPermission("cc-content.admin")) {
            sender.sendMessage("§c権限がありません")
            return true
        }

        if (args.size < 2) {
            sender.sendMessage("§c使用法: /sukima_dungeon stop <player>")
            return true
        }

        val playerName = args[1]
        val player = Bukkit.getPlayer(playerName)

        if (player == null) {
            sender.sendMessage("§cプレイヤー '§f$playerName§c' が見つかりません")
            return true
        }

        val session = DungeonSessionManager.getSession(player)
        if (session == null) {
            sender.sendMessage("§c§f${player.name}§c はダンジョンセッション内にいません")
            return true
        }

        DungeonSessionManager.endSession(player.uniqueId)
        sender.sendMessage("§a§f${player.name}§a のダンジョンセッションを停止しました")
        player.sendMessage("§eダンジョンセッションが管理者によって停止されました")

        return true
    }

    /**
     * ヘルプメッセージ表示
     */
    private fun handleHelp(sender: CommandSender): Boolean {
        sender.sendMessage("§6=== スキマダンジョン コマンド ===")
        sender.sendMessage("§e/sukima_dungeon tier <tier>§f: ティアを指定してダンジョンを開始")
        sender.sendMessage("§e/sukima_dungeon status§f: アクティブなセッション一覧を表示")
        sender.sendMessage("§e/sukima_dungeon info§f: ダンジョン情報を表示")
        if (sender.hasPermission("cc-content.admin")) {
            sender.sendMessage("§e/sukima_dungeon stop <player>§f: プレイヤーのセッションを停止（管理者用）")
        }
        sender.sendMessage("§e/sukima_dungeon help§f: このメッセージを表示")
        return true
    }

    /**
     * 使用方法メッセージ表示
     */
    private fun sendUsage(sender: CommandSender) {
        sender.sendMessage("§c使用法: /sukima_dungeon <tier|status|info|help>")
        sender.sendMessage("§c詳細は /sukima_dungeon help を実行してください")
    }

    /**
     * TAB補完実装
     */
    override fun onTabComplete(
        sender: CommandSender,
        cmd: Command,
        alias: String,
        args: Array<String>
    ): List<String> {
        return when {
            // サブコマンド補完（/sukima_dungeon [ここ]）
            args.size == 1 -> {
                val subCommands = mutableListOf("help", "tier", "status", "info")
                if (sender.hasPermission("cc-content.admin")) {
                    subCommands.add("stop")
                }
                subCommands
                    .filter { it.startsWith(args[0].lowercase()) }
            }

            // ティア補完（/sukima_dungeon tier [ここ]）
            args.size == 2 && args[0].equals("tier", ignoreCase = true) -> {
                DungeonTier.entries
                    .map { it.internalName }
                    .filter { it.startsWith(args[1].uppercase()) }
            }

            // プレイヤー補完（/sukima_dungeon stop [ここ]）
            args.size == 2 && args[0].equals("stop", ignoreCase = true) && sender.hasPermission("cc-content.admin") -> {
                Bukkit.getOnlinePlayers()
                    .map { it.name }
                    .filter { it.startsWith(args[1], ignoreCase = true) }
            }

            else -> emptyList()
        }
    }
}
