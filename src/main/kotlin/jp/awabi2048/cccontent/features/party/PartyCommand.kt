package jp.awabi2048.cccontent.features.party

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import java.util.UUID

class PartyCommand(private val controller: PartyController) : CommandExecutor, TabCompleter {
    private val subcommands = listOf("menu", "list", "create", "info", "invite", "accept", "join", "leave", "kick", "leader", "disband", "recruit", "chat", "chat-toggle")

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        val player = sender as? Player ?: run { sender.sendMessage("This command can only be used by players."); return true }
        if (args.isEmpty()) { controller.openMenu(player); return true }
        try {
            when (args[0].lowercase()) {
                "create" -> create(player, args)
                "menu" -> controller.openMenu(player)
                "list" -> list(player)
                "info" -> info(player, args)
                "invite" -> invite(player, args)
                "accept" -> accept(player, args)
                "_accept" -> accept(player, args)
                "join", "_join" -> join(player, args)
                "leave" -> {
                    if (controller.service.leave(player.uniqueId) == null) {
                        controller.message(player, "party.not_in_party")
                    }
                }
                "kick" -> kick(player, args)
                "leader" -> leader(player, args)
                "disband" -> disband(player)
                "recruit" -> recruit(player, args)
                "chat" -> chat(player, args)
                "chat-toggle" -> controller.message(player, if (controller.toggleChat(player)) "party.chat_channel.enabled" else "party.chat_channel.disabled")
                else -> return usage(player)
            }
        } catch (_: IllegalArgumentException) {
            controller.message(player, "party.error.invalid_action")
        } catch (_: IllegalStateException) {
            controller.message(player, "party.feature_disabled")
        }
        return true
    }

    private fun create(player: Player, args: Array<String>) {
        require(args.size >= 2)
        var end = args.size
        val recruiting = args.lastOrNull()?.let { it.equals("true", true) || it.equals("false", true) } == true
        val recruitingValue = if (recruiting) args[--end].toBoolean() else false
        val capacity = args.getOrNull(end - 1)?.toIntOrNull()?.also { end-- }
        val name = args[1]
        val description = args.copyOfRange(2, end).joinToString(" ")
        val party = controller.service.create(player.uniqueId, name, description, capacity ?: controller.defaultCapacity, recruitingValue)
        controller.message(player, "party.created", mapOf("party" to party.name, "id" to party.id))
    }

    private fun info(player: Player, args: Array<String>) {
        val party = args.getOrNull(1)?.let { UUID.fromString(it).let(controller.service::get) } ?: controller.service.partyOf(player.uniqueId)
        if (party == null) { controller.message(player, "party.not_in_party"); return }
        controller.message(player, "party.info", mapOf(
            "party" to party.name, "id" to party.id, "leader" to controller.playerName(party.leader),
            "members" to party.members.joinToString(", ") { controller.playerName(it) },
            "count" to party.members.size, "capacity" to party.capacity,
            "recruiting" to party.recruiting
        ))
    }

    private fun invite(player: Player, args: Array<String>) {
        require(args.size == 2)
        val target = controller.resolveOnline(args[1]) ?: run { controller.message(player, "party.player_not_online"); return }
        val party = controller.service.partyOf(player.uniqueId) ?: error("not_in_party")
        controller.service.invite(party.id, player.uniqueId, target.uniqueId)
        controller.message(player, "party.invite_sent", mapOf("player" to target.name))
    }

    private fun accept(player: Player, args: Array<String>) {
        val partyId = args.getOrNull(1)?.let(UUID::fromString)
            ?: controller.service.invitesFor(player.uniqueId).firstOrNull()?.partyId
            ?: error("invite_not_found")
        val party = controller.service.acceptInvite(player.uniqueId, partyId)
        controller.message(player, "party.joined", mapOf("party" to party.name))
    }

    private fun join(player: Player, args: Array<String>) {
        require(args.size == 2)
        val party = controller.service.join(player.uniqueId, UUID.fromString(args[1]))
        controller.message(player, "party.joined", mapOf("party" to party.name))
    }

    private fun list(player: Player) {
        val parties = controller.service.recruitingParties()
        if (parties.isEmpty()) {
            controller.message(player, "party.list.empty")
            return
        }
        controller.message(player, "party.list.header")
        parties.forEach { party ->
            val line = controller.text(player, "party.list.entry", mapOf("party" to party.name, "count" to party.members.size, "capacity" to party.capacity))
            player.sendMessage(Component.text(line).clickEvent(ClickEvent.runCommand("/party _join ${party.id}")))
        }
    }

    private fun kick(player: Player, args: Array<String>) {
        require(args.size == 2)
        val target = controller.resolveOnline(args[1]) ?: run { controller.message(player, "party.player_not_online"); return }
        val party = controller.service.partyOf(player.uniqueId) ?: error("not_in_party")
        controller.service.kick(party.id, player.uniqueId, target.uniqueId)
    }

    private fun leader(player: Player, args: Array<String>) {
        require(args.size == 2)
        val target = controller.resolveOnline(args[1]) ?: run { controller.message(player, "party.player_not_online"); return }
        val party = controller.service.partyOf(player.uniqueId) ?: error("not_in_party")
        controller.service.transferLeadership(party.id, player.uniqueId, target.uniqueId)
    }

    private fun disband(player: Player) {
        val party = controller.service.partyOf(player.uniqueId) ?: error("not_in_party")
        controller.service.disband(party.id, player.uniqueId)
    }

    private fun recruit(player: Player, args: Array<String>) {
        require(args.size == 2)
        val value = args[1].toBooleanStrictOrNull() ?: error("invalid")
        val party = controller.service.partyOf(player.uniqueId) ?: error("not_in_party")
        controller.service.update(party.id, player.uniqueId, recruiting = value)
        controller.message(player, "party.recruiting_updated", mapOf("enabled" to value))
        if (value) controller.broadcastRecruitment(controller.service.get(party.id)!!)
    }

    private fun chat(player: Player, args: Array<String>) {
        require(args.size >= 2)
        val party = controller.service.partyOf(player.uniqueId) ?: error("not_in_party")
        controller.service.sendChat(party.id, player.uniqueId, args.copyOfRange(1, args.size).joinToString(" "))
    }

    private fun usage(player: Player): Boolean { controller.message(player, "party.usage"); return true }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<String>): List<String> =
        if (args.size == 1) subcommands.filter { it.startsWith(args[0], true) } else emptyList()
}
