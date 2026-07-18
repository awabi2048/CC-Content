@file:Suppress("DEPRECATION")

package jp.awabi2048.cccontent.features.sukima_dungeon.gui

import com.awabi2048.ccsystem.api.gui.GuiLoreLine

import jp.awabi2048.cccontent.features.sukima_dungeon.DungeonTier
import jp.awabi2048.cccontent.features.sukima_dungeon.generator.StructureLoader
import jp.awabi2048.cccontent.features.sukima_dungeon.LangManager
import jp.awabi2048.cccontent.features.sukima_dungeon.MessageManager
import jp.awabi2048.cccontent.util.ContentLocaleResolver
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ItemFlag

class DungeonEntranceGui(private val loader: StructureLoader, val tier: DungeonTier) : InventoryHolder {

    private var inventory: Inventory? = null
    var currentThemeIndex = 0
    var isMultiplayer = false
    private val themes = listOf("random") + loader.getThemeNames()
        .mapNotNull { loader.getTheme(it) }
        .filter { it.requiredTier <= tier.tier }
        .map { it.id }

    val sizes = tier.availableSizes
    var currentSizeIndex = 0

    override fun getInventory(): Inventory {
        return inventory ?: Bukkit.createInventory(this, 45, "Dungeon")
    }

    fun open(player: Player) {
        val title = MessageManager.getMessage(player, "gui_entrance_title", mapOf("tier" to MessageManager.getTierName(player, tier.name)))
        inventory = Bukkit.createInventory(this, 45, title)
        update(player)
        player.openInventory(inventory!!)
    }

    fun update(player: Player) {
        val inv = inventory ?: return
        
        jp.awabi2048.cccontent.gui.GuiMenuItems.fillFramed(inv)

        // ---------------------------------------------
        // Center: Entrance Button (Slot 22)
        // ---------------------------------------------
        val entranceItem = createGuiItem(
            Material.END_CRYSTAL,
            MessageManager.getMessage(player, "gui_entrance_open_name"),
            listOf(singleAction(player, MessageManager.getMessage(player, "gui_entrance_open_action")))
        )
        inv.setItem(22, entranceItem)

        // ---------------------------------------------
        // Slot 38: Play Style Selector
        // ---------------------------------------------
        val styleLabel = if (isMultiplayer) {
            MessageManager.getMessage(player, "gui_style_multi")
        } else {
            MessageManager.getMessage(player, "gui_style_single")
        }
        val styleItem = createGuiItem(
            Material.LEATHER_HORSE_ARMOR,
            MessageManager.getMessage(player, "gui_style_name"),
            listOf(
                GuiLoreLine.Data(MessageManager.getMessage(player, "gui_current_setting_label"), styleLabel, "§e"),
                singleAction(player, MessageManager.getMessage(player, "gui_style_action"))
            )
        )
        val styleMeta = styleItem.itemMeta as? org.bukkit.inventory.meta.LeatherArmorMeta
        styleMeta?.setColor(org.bukkit.Color.WHITE)
        styleMeta?.addItemFlags(ItemFlag.HIDE_DYE)
        styleItem.itemMeta = styleMeta
        inv.setItem(37, styleItem)

        val sizeKey = sizes[currentSizeIndex]
        if (sizes.size > 1) {
            val sizeLabel = MessageManager.getMessage(player, "gui_size_$sizeKey")
            val sizeItem = createGuiItem(
                Material.DIAMOND_PICKAXE,
                MessageManager.getMessage(player, "gui_size_name"),
                listOf(
                    GuiLoreLine.Data(MessageManager.getMessage(player, "gui_current_setting_label"), sizeLabel, "§e"),
                    singleAction(player, MessageManager.getMessage(player, "gui_size_action"))
                )
            )
            val sizeMeta = sizeItem.itemMeta
            sizeMeta?.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
            sizeItem.itemMeta = sizeMeta
            inv.setItem(38, sizeItem)
        }

        // ---------------------------------------------
        // Slot 43: Theme Selector
        // ---------------------------------------------
        if (themes.isNotEmpty()) {
            val themeName = themes[currentThemeIndex]
            val theme = loader.getTheme(themeName)
            val isRandom = themeName == "random"
            
            val randomDisplayName = MessageManager.getMessage(player, "gui_theme_random")
            val baseDisplayName = if (isRandom) randomDisplayName else theme?.getDisplayName(player) ?: themeName
            val fullDisplayName = if (isRandom) baseDisplayName else MessageManager.getMessage(
                player,
                "gui_theme_world_value",
                mapOf("theme" to baseDisplayName)
            )
            
            val themeIcon = if (isRandom) Material.MAP else theme?.icon ?: Material.PAPER

            val themeItem = createGuiItem(
                themeIcon,
                MessageManager.getMessage(player, "gui_theme_name"),
                listOf(
                    GuiLoreLine.Data(MessageManager.getMessage(player, "gui_current_setting_label"), fullDisplayName, "§d"),
                    singleAction(player, MessageManager.getMessage(player, "gui_theme_action"))
                )
            )
            inv.setItem(43, themeItem)
            
            // ---------------------------------------------
            // Slot 40: Info
            // ---------------------------------------------
            val sizeName = LangManager.getMessage(ContentLocaleResolver.resolve(player), "sizes.$sizeKey")
            val infoLines = mutableListOf<GuiLoreLine>(
                GuiLoreLine.Data(MessageManager.getMessage(player, "gui_info_theme_label"), fullDisplayName, "§d"),
                GuiLoreLine.Data(MessageManager.getMessage(player, "gui_info_size_label"), sizeName, "§e")
            )
            theme?.getDescription(player)?.takeIf { it.isNotBlank() }?.let { infoLines += GuiLoreLine.Text(it) }
            infoLines += MessageManager.getList(player, "gui_info_warning").map(GuiLoreLine::Warning)
            val infoItem = createGuiItem(
                Material.SPYGLASS,
                MessageManager.getMessage(player, "gui_info_name"),
                infoLines
            )

            inv.setItem(40, infoItem)
        } else {
             val errorItem = createGuiItem(Material.BARRIER, MessageManager.getMessage(player, "gui_theme_error"))
             inv.setItem(43, errorItem)
             inv.setItem(40, errorItem)
        }
    }
    
    fun nextTheme(player: Player) {
        if (themes.isNotEmpty()) {
            currentThemeIndex = (currentThemeIndex + 1) % themes.size
            update(player)
        }
    }

    fun togglePlayStyle(player: Player) {
        isMultiplayer = !isMultiplayer
        update(player)
    }

    fun nextSize(player: Player) {
        currentSizeIndex = (currentSizeIndex + 1) % sizes.size
        update(player)
    }
    
    fun getCurrentTheme(): String? {
        return if (themes.isNotEmpty()) themes[currentThemeIndex] else null
    }

    private fun createGuiItem(material: Material, name: String, lore: List<GuiLoreLine> = emptyList()): ItemStack {
        return SukimaGuiItems.icon(material, name, lore)
    }

    private fun singleAction(player: Player, action: String) = SukimaGuiItems.singleAction(player, action)

}
