package jp.awabi2048.cccontent.features.party

import jp.awabi2048.cccontent.gui.ManagedMenuPresenter

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiMenuEntryAction
import com.awabi2048.ccsystem.api.gui.GuiMenuEntryData
import com.awabi2048.ccsystem.api.gui.GuiMenuEntrySpec
import com.awabi2048.ccsystem.api.gui.GuiNameSpec
import com.awabi2048.ccsystem.api.gui.GuiNameStyle
import com.awabi2048.ccsystem.api.gui.GuiValueTone
import com.awabi2048.ccsystem.api.gui.InventoryMenuDefinition
import com.awabi2048.ccsystem.api.gui.InventoryMenuView
import com.awabi2048.ccsystem.api.gui.MenuActionHandler
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuDialogButton
import com.awabi2048.ccsystem.api.gui.MenuDialogHandler
import com.awabi2048.ccsystem.api.gui.MenuDialogInput
import com.awabi2048.ccsystem.api.gui.MenuDialogRequest
import com.awabi2048.ccsystem.api.gui.MenuElement
import com.awabi2048.ccsystem.api.gui.MenuAcceptedClicks
import com.awabi2048.ccsystem.api.gui.MenuRoute
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

class PartyMenu(private val controller: PartyController) {
    private val elements get() = CCSystem.getAPI().getGuiElementService()
    private val runtime get() = CCSystem.getAPI().getMenuRuntimeService()

    init {
        runtime.register(
            InventoryMenuDefinition(
                owner = OWNER,
                id = MENU_ID,
                renderer = { context -> render(context.player, context.route) },
                actions = mapOf(
                    "settings" to MenuActionHandler { context -> openSettings(context.player, context.route) },
                    "recruiting" to MenuActionHandler { context -> toggleRecruiting(context.player, context.route) },
                    "disband" to MenuActionHandler { context -> confirmDisband(context.player, context.route) },
                    "chat" to MenuActionHandler { context -> toggleChat(context.player) },
                    "invite" to MenuActionHandler { context -> invite(context.player, context.route, context.click.isRightClick) }
                )
            )
        )
    }

    fun open(player: Player) {
        val party = controller.service.partyOf(player.uniqueId) ?: controller.service.create(
            player.uniqueId,
            controller.text(player, "party.default_name", mapOf("player" to player.name))
        )
        runtime.open(player, MenuRoute(OWNER, MENU_ID, mapOf("partyId" to party.id.toString())))
    }

    private fun render(player: Player, route: MenuRoute): InventoryMenuView {
        val partyId = route.payload["partyId"]?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }
        val party = partyId?.let(controller.service::get) ?: controller.service.partyOf(player.uniqueId)
            ?: controller.service.create(player.uniqueId, controller.text(player, "party.default_name", mapOf("player" to player.name)))
        val inventory = Bukkit.createInventory(
            null,
            45,
            elements.title(GuiNameSpec.Text(controller.text(player, "party.menu.title"), GuiNameStyle.DEFAULT))
        )
        (0..8).forEach { inventory.setItem(it, elements.decoration(Material.BLACK_STAINED_GLASS_PANE)) }
        (9..35).forEach { inventory.setItem(it, elements.decoration(Material.GRAY_STAINED_GLASS_PANE)) }
        (36..44).forEach { inventory.setItem(it, elements.decoration(Material.BLACK_STAINED_GLASS_PANE)) }

        val leader = party.leader == player.uniqueId
        val full = party.members.size >= party.capacity
        val chatActive = controller.isPartyChatActive(player, party)
        val semanticElements = listOf(
            settingsItem(player, party, leader),
            recruitingItem(player, party, leader),
            disbandItem(player, leader),
            chatItem(player, chatActive),
            informationItem(player, party),
            inviteItem(player, full),
        )
        semanticElements.forEach { inventory.setItem(it.slot, it.item) }
        val semanticBySlot = semanticElements.associateBy(MenuElement::slot)
        return InventoryMenuView(
            size = 45,
            title = elements.title(GuiNameSpec.Text(controller.text(player, "party.menu.title"), GuiNameStyle.DEFAULT)),
            elements = (0 until inventory.size).mapNotNull { slot ->
                semanticBySlot[slot]?.let { return@mapNotNull it }
                val item = inventory.getItem(slot) ?: return@mapNotNull null
                MenuElement(slot, item, GuiElementRole.DECORATION)
            },
            standardFrame = false
        )
    }

    private fun settingsItem(player: Player, party: PartySnapshot, enabled: Boolean) = icon(
        20,
        Material.NAME_TAG,
        player,
        "party.menu.settings.name",
        role = if (enabled) GuiElementRole.ACTION else GuiElementRole.CONTENT,
        data = listOf(
            data(player, "party.menu.settings.party_name", party.name),
            data(player, "party.menu.settings.description", party.description.ifBlank { controller.text(player, "party.menu.value.none") })
        ),
        warnings = leaderWarning(player, enabled),
        actions = singleAction("settings", player, "party.menu.settings.action", enabled)
    )

    private fun recruitingItem(player: Player, party: PartySnapshot, enabled: Boolean) = icon(
        22,
        if (party.recruiting) Material.BOOKSHELF else Material.CHISELED_BOOKSHELF,
        player,
        "party.menu.recruiting.name",
        role = if (enabled) GuiElementRole.ACTION else GuiElementRole.CONTENT,
        data = listOf(data(player, "party.menu.state", controller.text(player, if (party.recruiting) "party.menu.recruiting.enabled" else "party.menu.recruiting.disabled"), if (party.recruiting) "§a" else "§7")),
        warnings = leaderWarning(player, enabled),
        actions = singleAction("recruiting", player, "party.menu.recruiting.action", enabled),
        glint = party.recruiting
    )

    private fun disbandItem(player: Player, enabled: Boolean) = icon(
        24,
        Material.REDSTONE,
        player,
        "party.menu.disband.name",
        style = GuiNameStyle.DANGER,
        role = if (enabled) GuiElementRole.ACTION else GuiElementRole.CONTENT,
        warnings = leaderWarning(player, enabled),
        dangers = if (enabled) listOf(controller.text(player, "party.menu.disband.warning")) else emptyList(),
        actions = singleAction("disband", player, "party.menu.disband.action", enabled)
    )

    private fun chatItem(player: Player, active: Boolean) = icon(
        38,
        if (active) Material.LIME_DYE else Material.GRAY_DYE,
        player,
        "party.menu.chat.name",
        data = listOf(data(player, "party.menu.state", controller.text(player, if (active) "party.menu.chat.enabled" else "party.menu.chat.disabled"), if (active) "§a" else "§7")),
        actions = singleAction("chat", player, "party.menu.chat.action", true),
        glint = active
    )

    private fun informationItem(player: Player, party: PartySnapshot): MenuElement {
        val memberData = party.members.map { member ->
            data(
                player,
                if (member == party.leader) "party.menu.info.leader_label" else "party.menu.info.member_label",
                controller.playerName(member),
                if (member == party.leader) "§e" else "§f"
            )
        }
        val item = icon(
            40,
            Material.PLAYER_HEAD,
            player,
            "party.menu.info.name",
            role = GuiElementRole.CONTENT,
            data = listOf(
                data(player, "party.menu.info.party_name", party.name),
                data(player, "party.menu.info.description", party.description.ifBlank { controller.text(player, "party.menu.value.none") }),
                data(player, "party.menu.info.capacity_label", "${party.members.size}/${party.capacity}")
            ) + memberData,
            amount = party.members.size,
            playerHeadOwner = party.leader,
        )
        return item
    }

    private fun inviteItem(player: Player, full: Boolean) = icon(
        42,
        Material.LEATHER_HELMET,
        player,
        "party.menu.invite.name",
        role = if (full) GuiElementRole.CONTENT else GuiElementRole.ACTION,
        warnings = if (full) listOf(controller.text(player, "party.menu.invite.full")) else emptyList(),
        actions = listOf(
            action("invite", player, "party.menu.invite.select", MenuAcceptedClicks.LEFT, !full),
            action("invite", player, "party.menu.invite.input", MenuAcceptedClicks.RIGHT, !full)
        )
    )

    private fun leaderWarning(player: Player, enabled: Boolean): List<String> =
        if (enabled) emptyList() else listOf(controller.text(player, "party.menu.leader_only"))

    private fun data(player: Player, key: String, value: Any?, color: String = "§f") =
        GuiMenuEntryData(controller.text(player, key), value, tone(color))

    private fun singleAction(actionId: String, player: Player, actionKey: String, enabled: Boolean): List<GuiMenuEntryAction> =
        listOf(action(actionId, player, actionKey, MenuAcceptedClicks.LEFT_RIGHT, enabled))

    private fun action(
        actionId: String,
        player: Player,
        actionKey: String,
        acceptedClicks: Set<org.bukkit.event.inventory.ClickType>,
        enabled: Boolean,
    ) = GuiMenuEntryAction(actionId, acceptedClicks, controller.text(player, actionKey), enabled = enabled)

    private fun icon(
        slot: Int,
        material: Material,
        player: Player,
        nameKey: String,
        style: GuiNameStyle = GuiNameStyle.DEFAULT,
        role: GuiElementRole = GuiElementRole.ACTION,
        amount: Int = 1,
        data: List<GuiMenuEntryData> = emptyList(),
        warnings: List<String> = emptyList(),
        dangers: List<String> = emptyList(),
        actions: List<GuiMenuEntryAction> = emptyList(),
        glint: Boolean? = null,
        playerHeadOwner: java.util.UUID? = null,
    ): MenuElement = elements.menuEntry(
        player,
        GuiMenuEntrySpec(
            slot = slot,
            material = material,
            name = GuiNameSpec.Text(controller.text(player, nameKey), style),
            role = role,
            amount = amount,
            data = data,
            warnings = warnings,
            dangers = dangers,
            actions = actions,
            glint = glint,
            playerHeadOwner = playerHeadOwner,
        )
    )

    private fun tone(color: String): GuiValueTone = when (color) {
        "§a" -> GuiValueTone.SUCCESS
        "§b" -> GuiValueTone.INFO
        "§c" -> GuiValueTone.DANGER
        "§6" -> GuiValueTone.WARNING
        "§7" -> GuiValueTone.MUTED
        "§e" -> GuiValueTone.PRIMARY
        else -> GuiValueTone.DEFAULT
    }

    private fun party(player: Player, route: MenuRoute): PartySnapshot? {
        val id = route.payload["partyId"]?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }
        return id?.let(controller.service::get) ?: controller.service.partyOf(player.uniqueId)
    }

    private fun openSettings(player: Player, route: MenuRoute): MenuActionResult {
        val party = party(player, route) ?: return MenuActionResult.Rejected()
        if (party.leader != player.uniqueId) return MenuActionResult.Rejected()
        showSettingsDialog(player, party)
        return MenuActionResult.Success(MenuUpdate.None)
    }

    private fun toggleRecruiting(player: Player, route: MenuRoute): MenuActionResult {
        val party = party(player, route) ?: return MenuActionResult.Rejected()
        if (party.leader != player.uniqueId) return MenuActionResult.Rejected()
        val updated = controller.service.update(party.id, player.uniqueId, recruiting = !party.recruiting)
        if (updated.recruiting) controller.broadcastRecruitment(updated)
        return MenuActionResult.Success(MenuUpdate.Refresh)
    }

    private fun confirmDisband(player: Player, route: MenuRoute): MenuActionResult {
        val party = party(player, route) ?: return MenuActionResult.Rejected()
        if (party.leader != player.uniqueId) return MenuActionResult.Rejected()
        jp.awabi2048.cccontent.gui.SimpleConfirmationDialog.show(
            player,
            Component.text(controller.text(player, "party.dialog.disband.title")),
            Component.text(controller.text(player, "party.dialog.disband.body")),
            Component.text(controller.text(player, "party.dialog.submit")),
            Component.text(controller.text(player, "party.dialog.cancel")),
            { target -> controller.service.disband(party.id, target.uniqueId); ManagedMenuPresenter.close(target) }
        )
        return MenuActionResult.Success(MenuUpdate.None)
    }

    private fun toggleChat(player: Player): MenuActionResult {
        controller.message(
            player,
            if (controller.toggleChat(player)) "party.chat_channel.enabled" else "party.chat_channel.disabled"
        )
        return MenuActionResult.Success(MenuUpdate.Refresh)
    }

    private fun invite(player: Player, route: MenuRoute, typedInput: Boolean): MenuActionResult {
        val party = party(player, route) ?: return MenuActionResult.Rejected()
        if (party.members.size >= party.capacity) return MenuActionResult.Rejected()
        if (typedInput) {
            showInviteDialog(player)
            return MenuActionResult.Success(MenuUpdate.None)
        }
        if (!controller.interactionClaims.tryClaim(player.uniqueId, PartyMenuListener.CLAIM_OWNER)) {
            controller.message(player, "party.interaction.busy")
            return MenuActionResult.Rejected()
        }
        ManagedMenuPresenter.close(player)
        controller.message(player, "party.interaction.invite_waiting")
        return MenuActionResult.Success(MenuUpdate.Close)
    }

    fun showSettingsDialog(player: Player, party: PartySnapshot) {
        showTextDialog(player, "party.dialog.settings.title", listOf(
            MenuDialogInput.Text("party_name", Component.text(controller.text(player, "party.dialog.settings.name")), party.name, maxLength = 32),
            MenuDialogInput.Text("party_description", Component.text(controller.text(player, "party.dialog.settings.description")), party.description, maxLength = 100)
        )) { view ->
            val name = view.textValue("party_name").trim()
            val description = view.textValue("party_description").trim()
            controller.service.update(party.id, player.uniqueId, name = name, description = description)
            open(player)
        }
    }

    fun showInviteDialog(player: Player) {
        showTextDialog(player, "party.dialog.invite.title", listOf(
            MenuDialogInput.Text("party_target", Component.text(controller.text(player, "party.dialog.invite.target")), maxLength = 32)
        )) { view ->
            val targetName = view.textValue("party_target").trim()
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

    private fun showTextDialog(
        player: Player,
        titleKey: String,
        inputs: List<MenuDialogInput>,
        submit: (com.awabi2048.ccsystem.api.gui.MenuDialogResponse) -> Unit
    ) {
        CCSystem.getAPI().getMenuDialogService().show(
            player,
            MenuDialogRequest(
                owner = OWNER,
                id = titleKey,
                title = Component.text(controller.text(player, titleKey)),
                body = listOf(Component.text(controller.text(player, "party.dialog.body"))),
                inputs = inputs,
                confirm = MenuDialogButton(
                    Component.text(controller.text(player, "party.dialog.submit")),
                    MenuDialogHandler { target, response ->
                        runCatching { submit(response) }
                            .onFailure { controller.message(target, "party.error.invalid_action") }
                            .fold(
                                onSuccess = { MenuActionResult.Success(MenuUpdate.None) },
                                onFailure = { MenuActionResult.Rejected() }
                            )
                    }
                ),
                cancel = MenuDialogButton(
                    Component.text(controller.text(player, "party.dialog.cancel")),
                    MenuDialogHandler { _, _ -> MenuActionResult.Success(MenuUpdate.Close) }
                )
            )
        )
    }

    companion object {
        private const val OWNER = "cc-content"
        private const val MENU_ID = "party"
    }
}
