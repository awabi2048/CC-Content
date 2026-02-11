package jp.awabi2048.cccontent.features.rank.command

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * /rankmenu コマンド - 職業メインメニューを開く
 */
class RankMenuCommand(
    private val rankCommand: RankCommand
) : CommandExecutor {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (sender !is Player) {
            sender.sendMessage("§cこのコマンドはプレイヤーのみ実行できます")
            return false
        }

        return rankCommand.openProfessionMainMenu(sender)
    }
}
