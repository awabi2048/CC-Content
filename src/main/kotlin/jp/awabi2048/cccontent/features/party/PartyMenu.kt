package jp.awabi2048.cccontent.features.party

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiMenuIconAction
import com.awabi2048.ccsystem.api.gui.GuiMenuIconData
import com.awabi2048.ccsystem.api.gui.GuiMenuIconSpec
import com.awabi2048.ccsystem.api.gui.GuiNameSpec
import com.awabi2048.ccsystem.api.gui.GuiNameStyle
import io.papermc.paper.dialog.Dialog
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.input.DialogInput
import io.papermc.paper.registry.data.dialog.type.DialogType
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickCallback
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta

class PartyMenu(private val controller: PartyController) {
    private val elements get() = CCSystem.getAPI().getGuiElementService()

    fun open(player: Player) {
        val party = controller.service.partyOf(player.uniqueId) ?: controller.service.create(
            player.uniqueId,
            controller.text(player, "party.default_name", mapOf("player" to player.name))
        )
        val holder = PartyMenuHolder(party.id)
        val inventory = Bukkit.createInventory(
            holder,
            45,
            elements.title(GuiNameSpec.Text(controller.text(player, "party.menu.title"), GuiNameStyle.DEFAULT))
        )
        holder.attach(inventory)
        (0..8).forEach { inventory.setItem(it, elements.decoration(Material.BLACK_STAINED_GLASS_PANE)) }
        (9..35).forEach { inventory.setItem(it, elements.decoration(Material.GRAY_STAINED_GLASS_PANE)) }
        (36..44).forEach { inventory.setItem(it, elements.decoration(Material.BLACK_STAINED_GLASS_PANE)) }

        val leader = party.leader == player.uniqueId
        val full = party.members.size >= party.capacity
        val chatActive = controller.isPartyChatActive(player, party)
        inventory.setItem(20, settingsItem(player, party, leader))
        inventory.setItem(22, recruitingItem(player, party, leader))
        inventory.setItem(24, disbandItem(player, leader))
        inventory.setItem(38, chatItem(player, chatActive))
        inventory.setItem(40, informationItem(player, party))
        inventory.setItem(42, inviteItem(player, full))
        player.openInventory(inventory)
    }

    private fun settingsItem(player: Player, party: PartySnapshot, enabled: Boolean) = icon(
        Material.NAME_TAG,
        player,
        "party.menu.settings.name",
        role = if (enabled) GuiElementRole.ACTION else GuiElementRole.CONTENT,
        data = listOf(
            data(player, "party.menu.settings.party_name", party.name),
            data(player, "party.menu.settings.description", party.description.ifBlank { controller.text(player, "party.menu.value.none") })
        ),
        warnings = leaderWarning(player, enabled),
        actions = singleAction(player, "lore.click.any", "party.menu.settings.action", enabled)
    )

    private fun recruitingItem(player: Player, party: PartySnapshot, enabled: Boolean) = icon(
        if (party.recruiting) Material.BOOKSHELF else Material.CHISELED_BOOKSHELF,
        player,
        "party.menu.recruiting.name",
        role = if (enabled) GuiElementRole.ACTION else GuiElementRole.CONTENT,
        data = listOf(data(player, "party.menu.state", controller.text(player, if (party.recruiting) "party.menu.recruiting.enabled" else "party.menu.recruiting.disabled"), if (party.recruiting) "§a" else "§7")),
        warnings = leaderWarning(player, enabled),
        actions = singleAction(player, "lore.click.any", "party.menu.recruiting.action", enabled),
        glint = party.recruiting
    )

    private fun disbandItem(player: Player, enabled: Boolean) = icon(
        Material.REDSTONE,
        player,
        "party.menu.disband.name",
        style = GuiNameStyle.DANGER,
        role = if (enabled) GuiElementRole.ACTION else GuiElementRole.CONTENT,
        warnings = leaderWarning(player, enabled),
        dangers = if (enabled) listOf(controller.text(player, "party.menu.disband.warning")) else emptyList(),
        actions = singleAction(player, "lore.click.any", "party.menu.disband.action", enabled)
    )

    private fun chatItem(player: Player, active: Boolean) = icon(
        if (active) Material.LIME_DYE else Material.GRAY_DYE,
        player,
        "party.menu.chat.name",
        data = listOf(data(player, "party.menu.state", controller.text(player, if (active) "party.menu.chat.enabled" else "party.menu.chat.disabled"), if (active) "§a" else "§7")),
        actions = singleAction(player, "lore.click.any", "party.menu.chat.action", true),
        glint = active
    )

    private fun informationItem(player: Player, party: PartySnapshot): ItemStack {
        val memberData = party.members.map { member ->
            data(
                player,
                if (member == party.leader) "party.menu.info.leader_label" else "party.menu.info.member_label",
                controller.playerName(member),
                if (member == party.leader) "§e" else "§f"
            )
        }
        val item = icon(
            Material.PLAYER_HEAD,
            player,
            "party.menu.info.name",
            role = GuiElementRole.CONTENT,
            data = listOf(
                data(player, "party.menu.info.party_name", party.name),
                data(player, "party.menu.info.description", party.description.ifBlank { controller.text(player, "party.menu.value.none") }),
                data(player, "party.menu.info.capacity_label", "${party.members.size}/${party.capacity}")
            ) + memberData,
            amount = party.members.size
        )
        val meta = item.itemMeta as? SkullMeta ?: return item
        meta.owningPlayer = Bukkit.getOfflinePlayer(party.leader)
        item.itemMeta = meta
        return item
    }

    private fun inviteItem(player: Player, full: Boolean) = icon(
        Material.LEATHER_HELMET,
        player,
        "party.menu.invite.name",
        role = if (full) GuiElementRole.CONTENT else GuiElementRole.ACTION,
        warnings = if (full) listOf(controller.text(player, "party.menu.invite.full")) else emptyList(),
        actions = listOf(
            action(player, "lore.click.left", "party.menu.invite.select", !full),
            action(player, "lore.click.right", "party.menu.invite.input", !full)
        )
    )

    private fun leaderWarning(player: Player, enabled: Boolean): List<String> =
        if (enabled) emptyList() else listOf(controller.text(player, "party.menu.leader_only"))

    private fun data(player: Player, key: String, value: Any?, color: String = "§f") =
        GuiMenuIconData(controller.text(player, key), value, color)

    private fun singleAction(player: Player, operationKey: String, actionKey: String, enabled: Boolean): List<GuiMenuIconAction> =
        listOf(action(player, operationKey, actionKey, enabled, true))

    private fun action(player: Player, operationKey: String, actionKey: String, enabled: Boolean, single: Boolean = false): GuiMenuIconAction {
        val operation = controller.text(player, operationKey)
        val action = controller.text(player, actionKey)
        val resolved = if (single) controller.text(player, "lore.action_single_with_operation", mapOf("operation" to operation, "action" to action)) else null
        return GuiMenuIconAction(operation, action, resolved, enabled)
    }

    private fun icon(
        material: Material,
        player: Player,
        nameKey: String,
        style: GuiNameStyle = GuiNameStyle.DEFAULT,
        role: GuiElementRole = GuiElementRole.ACTION,
        amount: Int = 1,
        data: List<GuiMenuIconData> = emptyList(),
        warnings: List<String> = emptyList(),
        dangers: List<String> = emptyList(),
        actions: List<GuiMenuIconAction> = emptyList(),
        glint: Boolean? = null
    ): ItemStack = elements.menuIcon(
        GuiMenuIconSpec(
            material,
            GuiNameSpec.Text(controller.text(player, nameKey), style),
            role,
            amount,
            emptyList(),
            data,
            emptyList(),
            warnings,
            dangers,
            actions,
            glint
        )
    )

    fun showSettingsDialog(player: Player, party: PartySnapshot) {
        showTextDialog(player, "party.dialog.settings.title", listOf(
            DialogInput.text("party_name", Component.text(controller.text(player, "party.dialog.settings.name"))).initial(party.name).maxLength(32).build(),
            DialogInput.text("party_description", Component.text(controller.text(player, "party.dialog.settings.description"))).initial(party.description).maxLength(100).build()
        )) { view ->
            val name = view.getText("party_name").orEmpty().trim()
            val description = view.getText("party_description").orEmpty().trim()
            controller.service.update(party.id, player.uniqueId, name = name, description = description)
            open(player)
        }
    }

    fun showInviteDialog(player: Player) {
        showTextDialog(player, "party.dialog.invite.title", listOf(
            DialogInput.text("party_target", Component.text(controller.text(player, "party.dialog.invite.target"))).maxLength(32).build()
        )) { view ->
            val targetName = view.getText("party_target").orEmpty().trim()
            val target = controller.resolveOnline(targetName) ?: run {
                controller.message(player, "party.player_not_online")
                return@showTextDialog
            }
            val party = controller.service.partyOf(player.uniqueId) ?: return@showTextDialog
            controller.service.invite(party.id, player.uniqueId, target.uniqueId)
            controller.message(player, "party.invite_sent", mapOf("player" to target.name))
            open(player)
        }
    }

    private fun showTextDialog(player: Player, titleKey: String, inputs: List<DialogInput>, submit: (io.papermc.paper.dialog.DialogResponseView) -> Unit) {
        val callback = DialogAction.customClick(DialogActionCallback { view, audience ->
            if (audience !is Player) return@DialogActionCallback
            runCatching { submit(view) }.onFailure { controller.message(audience, "party.error.invalid_action") }
        }, ClickCallback.Options.builder().uses(1).build())
        val dialog = Dialog.create { factory ->
            factory.empty().base(
                DialogBase.builder(Component.text(controller.text(player, titleKey)))
                    .body(listOf(DialogBody.plainMessage(Component.text(controller.text(player, "party.dialog.body")), 280)))
                    .inputs(inputs).canCloseWithEscape(true).build()
            ).type(DialogType.confirmation(
                ActionButton.builder(Component.text(controller.text(player, "party.dialog.submit"))).action(callback).build(),
                ActionButton.builder(Component.text(controller.text(player, "party.dialog.cancel"))).build()
            ))
        }
        player.showDialog(dialog)
    }
}

class PartyMenuHolder(val partyId: java.util.UUID) : InventoryHolder {
    private lateinit var attachedInventory: Inventory
    fun attach(inventory: Inventory) {
        attachedInventory = inventory
    }
    override fun getInventory(): Inventory = attachedInventory
}
