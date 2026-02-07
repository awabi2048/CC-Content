package jp.awabi2048.cccontent.features.sukima_dungeon.gui

import jp.awabi2048.cccontent.features.sukima_dungeon.DungeonTier
import jp.awabi2048.cccontent.features.sukima_dungeon.generator.StructureLoader
import jp.awabi2048.cccontent.features.sukima_dungeon.LangManager
import jp.awabi2048.cccontent.features.sukima_dungeon.PlayerDataManager
import jp.awabi2048.cccontent.features.sukima_dungeon.MessageManager
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
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
        
        val blackGlass = createGuiItem(Material.BLACK_STAINED_GLASS_PANE, " ")
        val blackMeta = blackGlass.itemMeta
        blackMeta?.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
        blackGlass.itemMeta = blackMeta
        
        val grayGlass = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ")
        val grayMeta = grayGlass.itemMeta
        grayMeta?.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
        grayGlass.itemMeta = grayMeta

        // Fill Header & Footer (Row 0 and 4)
        for (i in 0..8) inv.setItem(i, blackGlass)
        for (i in 36..44) inv.setItem(i, blackGlass)

        // Fill Background (Rows 1, 2, 3)
        for (i in 9..35) inv.setItem(i, grayGlass)

        // ---------------------------------------------
        // Center: Entrance Button (Slot 22)
        // ---------------------------------------------
        val entranceItem = createGuiItem(Material.END_CRYSTAL, 
            "§bダンジョンへのポータルを開く", 
            "§7クリックしてダンジョンへのポータルを開きます！")
        inv.setItem(22, entranceItem)

        val bar = "§7§m――――――――――――――――――――――――――――――"

        // ---------------------------------------------
        // Slot 38: Play Style Selector
        // ---------------------------------------------
        val styleLabel = if (isMultiplayer) {
            MessageManager.getMessage(player, "gui_style_multi")
        } else {
            MessageManager.getMessage(player, "gui_style_single")
        }
        val styleItem = createGuiItem(Material.LEATHER_HORSE_ARMOR, 
            "§6プレイスタイルの選択", 
            bar,
            styleLabel,
            "§7クリックして切り替えます",
            bar)
        val styleMeta = styleItem.itemMeta as? org.bukkit.inventory.meta.LeatherArmorMeta
        styleMeta?.setColor(org.bukkit.Color.WHITE)
        styleMeta?.addItemFlags(ItemFlag.HIDE_DYE)
        styleItem.itemMeta = styleMeta
        inv.setItem(37, styleItem)

        val sizeKey = sizes[currentSizeIndex]
        if (sizes.size > 1) {
            val sizeLabel = MessageManager.getMessage(player, "gui_size_$sizeKey")
            val sizeItem = createGuiItem(Material.DIAMOND_PICKAXE, 
                "§eサイズの選択",
                bar,
                sizeLabel,
                "§7クリックして切り替えます",
                bar)
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
            val displaySuffix = if (isRandom) "" else "の世界"
            val fullDisplayName = "$baseDisplayName$displaySuffix"
            
            val themeIcon = if (isRandom) Material.MAP else theme?.icon ?: Material.PAPER

            val themeItem = createGuiItem(themeIcon, 
                "§dポータルの接続先", 
                bar,
                "§f§l| §7現在の設定 §d${fullDisplayName}",
                "§e§nクリックして変更",
                bar)
            inv.setItem(43, themeItem)
            
            // ---------------------------------------------
            // Slot 40: Info
            // ---------------------------------------------
            val sizeName = LangManager.getMessage(PlayerDataManager.getPlayerData(player).lang, "sizes.$sizeKey")
            val infoItem = createGuiItem(Material.SPYGLASS, 
                "§aInfo", 
                bar,
                "§f§l| §7ポータルの接続先 §d${fullDisplayName}", 
                "§f§l| §7サイズ §e${sizeName}",
                "§7${theme?.getDescription(player) ?: ""}",
                bar,
                "§7スキマの世界は非常に不安定です。そのため、ポータルが別の世界に繋がってしまうことがあります。",
                "§7より上位のしおりを使用すると、望んだ傾向の世界に行きやすくなります。",
                bar)

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

    private fun createGuiItem(material: Material, name: String, vararg lore: String): ItemStack {
        val item = ItemStack(material, 1)
        val meta: ItemMeta? = item.itemMeta
        meta?.setDisplayName(name)
        if (lore.isNotEmpty()) {
            meta?.lore = lore.toList()
        }
        item.itemMeta = meta
        return item
    }

}
