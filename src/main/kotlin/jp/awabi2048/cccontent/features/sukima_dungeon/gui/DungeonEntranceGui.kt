package jp.awabi2048.cccontent.features.sukima_dungeon.gui

import jp.awabi2048.cccontent.items.sukima_dungeon.common.ConfigManager
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.plugin.java.JavaPlugin
import java.util.*

/**
 * ダンジョン進入GUI管理クラス
 * 3段階のGUI（テーマ選択→サイズ選択→確認）をハンドリング
 */
class DungeonEntranceGui(
    private val plugin: JavaPlugin,
    private val guiManager: GuiManager,
    private val configManager: ConfigManager
) {
    // プレイヤーの選択を一時保存
    private val playerSelections: MutableMap<UUID, GuiSelection> = mutableMapOf()
    
    /**
     * プレイヤーの選択情報
     */
    data class GuiSelection(
        var selectedTier: String = "",
        var selectedTheme: String? = null,
        var selectedSize: String? = null,
        var currentStage: GuiStage = GuiStage.THEME
    )
    
    /**
     * GUI ステージの定義
     */
    enum class GuiStage {
        THEME,      // テーマ選択
        SIZE,       // サイズ選択
        CONFIRM     // 最終確認
    }
    
    /**
     * ダンジョン進入 GUI を表示（テーマ選択から開始）
     * @param player プレイヤー
     * @param tier ブックマークのティア（BROKEN, WORN, FADED, NEW）
     */
    fun openEntranceGui(player: Player, tier: String) {
        val selection = GuiSelection(
            selectedTier = tier,
            currentStage = GuiStage.THEME
        )
        playerSelections[player.uniqueId] = selection
        
        showThemeSelectionGui(player, tier)
    }
    
    /**
     * テーマ選択 GUI を表示
     */
    private fun showThemeSelectionGui(player: Player, tier: String) {
        val inventory = guiManager.createInventory("§b§lテーマを選択", 6)
        guiManager.drawBorder(inventory)
        
        // ティア設定からこのティアで使用可能なテーマを取得
        val tierConfig = configManager.getDungeonTierConfig(tier)
        val availableThemes = tierConfig?.availableThemes ?: listOf()
        
        if (availableThemes.isEmpty()) {
            player.closeInventory()
            player.sendMessage("§c利用可能なテーマがありません")
            return
        }
        
        var slot = 10
        for (themeName in availableThemes) {
            val themeConfig = configManager.getDungeonThemeConfig(themeName)
            if (themeConfig != null) {
                val material = try {
                    Material.valueOf(themeConfig.icon)
                } catch (e: IllegalArgumentException) {
                    Material.BOOK
                }
                
                val item = guiManager.createItem(
                    material,
                    "§a${themeConfig.displayName}",
                    listOf("§7このテーマでダンジョンに進入します")
                )
                
                inventory.setItem(slot, item)
                slot += 2
                
                if (slot >= 16) {
                    slot = 19
                }
            }
        }
        
        // キャンセルボタン
        inventory.setItem(guiManager.getSlot(5, 4), guiManager.createCancelButton())
        
        guiManager.showGui(player, inventory)
        playerSelections[player.uniqueId]?.currentStage = GuiStage.THEME
    }
    
    /**
     * サイズ選択 GUI を表示
     */
    private fun showSizeSelectionGui(player: Player) {
        val selection = playerSelections[player.uniqueId] ?: return
        val tier = selection.selectedTier
        val theme = selection.selectedTheme ?: return
        
        val inventory = guiManager.createInventory("§b§lサイズを選択", 6)
        guiManager.drawBorder(inventory)
        
        // ティア設定からこのティアで使用可能なサイズを取得
        val tierConfig = configManager.getDungeonTierConfig(tier)
        val availableSizes = tierConfig?.availableSizes ?: listOf()
        
        if (availableSizes.isEmpty()) {
            player.closeInventory()
            player.sendMessage("§c利用可能なサイズがありません")
            return
        }
        
        var slot = 10
        for (sizeName in availableSizes) {
            val sizeConfig = configManager.getDungeonSizeConfig(sizeName)
            if (sizeConfig != null) {
                val duration = sizeConfig.duration / 60 // 秒を分に変換
                val sproutCount = sizeConfig.sproutBaseCount
                val material = when (sizeName) {
                    "small" -> Material.OAK_WOOD
                    "medium" -> Material.BIRCH_WOOD
                    "large" -> Material.JUNGLE_WOOD
                    "huge" -> Material.DARK_OAK_WOOD
                    else -> Material.OAK_WOOD
                }
                
                val item = guiManager.createItem(
                    material,
                    "§a${sizeName.uppercase()}",
                    listOf(
                        "§7制限時間: §f${duration}分",
                        "§7ワールドの芽: §f${sproutCount}個"
                    )
                )
                
                inventory.setItem(slot, item)
                slot += 2
                
                if (slot >= 16) {
                    slot = 19
                }
            }
        }
        
        // 戻るボタン
        inventory.setItem(guiManager.getSlot(5, 0), guiManager.createPreviousButton())
        
        // キャンセルボタン
        inventory.setItem(guiManager.getSlot(5, 4), guiManager.createCancelButton())
        
        guiManager.showGui(player, inventory)
        playerSelections[player.uniqueId]?.currentStage = GuiStage.SIZE
    }
    
    /**
     * 最終確認 GUI を表示
     */
    private fun showConfirmGui(player: Player) {
        val selection = playerSelections[player.uniqueId] ?: return
        val theme = selection.selectedTheme ?: return
        val size = selection.selectedSize ?: return
        
        val inventory = guiManager.createInventory("§b§l確認", 5)
        guiManager.drawBorder(inventory)
        
        // テーマ情報
        val themeConfig = configManager.getDungeonThemeConfig(theme)
        if (themeConfig != null) {
            val themeMaterial = try {
                Material.valueOf(themeConfig.icon)
            } catch (e: IllegalArgumentException) {
                Material.BOOK
            }
            
            val themeItem = guiManager.createItem(
                themeMaterial,
                "§eテーマ",
                listOf("§f${themeConfig.displayName}")
            )
            inventory.setItem(guiManager.getSlot(1, 2), themeItem)
        }
        
        // サイズ情報
        val sizeConfig = configManager.getDungeonSizeConfig(size)
        if (sizeConfig != null) {
            val sizeMaterial = when (size) {
                "small" -> Material.OAK_WOOD
                "medium" -> Material.BIRCH_WOOD
                "large" -> Material.JUNGLE_WOOD
                "huge" -> Material.DARK_OAK_WOOD
                else -> Material.OAK_WOOD
            }
            
            val duration = sizeConfig.duration / 60
            val sizeItem = guiManager.createItem(
                sizeMaterial,
                "§eサイズ",
                listOf(
                    "§f${size.uppercase()}",
                    "§7制限時間: §f${duration}分"
                )
            )
            inventory.setItem(guiManager.getSlot(1, 6), sizeItem)
        }
        
        // 確認ボタン
        inventory.setItem(guiManager.getSlot(3, 3), guiManager.createConfirmButton())
        
        // 戻るボタン
        inventory.setItem(guiManager.getSlot(3, 5), guiManager.createPreviousButton())
        
        // キャンセルボタン
        inventory.setItem(guiManager.getSlot(3, 7), guiManager.createCancelButton())
        
        guiManager.showGui(player, inventory)
        playerSelections[player.uniqueId]?.currentStage = GuiStage.CONFIRM
    }
    
    /**
     * GUI のアイテムクリックを処理
     * @param player プレイヤー
     * @param slot クリックされたスロット
     * @return true: イベントを消費, false: 処理しない
     */
    fun handleGuiClick(player: Player, slot: Int): Boolean {
        val selection = playerSelections[player.uniqueId] ?: return false
        
        return when (selection.currentStage) {
            GuiStage.THEME -> handleThemeSelection(player, slot)
            GuiStage.SIZE -> handleSizeSelection(player, slot)
            GuiStage.CONFIRM -> handleConfirmation(player, slot)
        }
    }
    
    /**
     * テーマ選択処理
     */
    private fun handleThemeSelection(player: Player, slot: Int): Boolean {
        val selection = playerSelections[player.uniqueId] ?: return false
        
        // キャンセルボタンチェック
        if (slot == guiManager.getSlot(5, 4)) {
            player.closeInventory()
            playerSelections.remove(player.uniqueId)
            return true
        }
        
        // テーマ選択処理
        val tierConfig = configManager.getDungeonTierConfig(selection.selectedTier)
        val availableThemes = tierConfig?.availableThemes ?: listOf()
        
        var expectedSlot = 10
        for (themeName in availableThemes) {
            if (slot == expectedSlot) {
                selection.selectedTheme = themeName
                showSizeSelectionGui(player)
                return true
            }
            expectedSlot += 2
            if (expectedSlot >= 16) {
                expectedSlot = 19
            }
        }
        
        return false
    }
    
    /**
     * サイズ選択処理
     */
    private fun handleSizeSelection(player: Player, slot: Int): Boolean {
        val selection = playerSelections[player.uniqueId] ?: return false
        
        // 戻るボタンチェック
        if (slot == guiManager.getSlot(5, 0)) {
            showThemeSelectionGui(player, selection.selectedTier)
            return true
        }
        
        // キャンセルボタンチェック
        if (slot == guiManager.getSlot(5, 4)) {
            player.closeInventory()
            playerSelections.remove(player.uniqueId)
            return true
        }
        
        // サイズ選択処理
        val tierConfig = configManager.getDungeonTierConfig(selection.selectedTier)
        val availableSizes = tierConfig?.availableSizes ?: listOf()
        
        var expectedSlot = 10
        for (sizeName in availableSizes) {
            if (slot == expectedSlot) {
                selection.selectedSize = sizeName
                showConfirmGui(player)
                return true
            }
            expectedSlot += 2
            if (expectedSlot >= 16) {
                expectedSlot = 19
            }
        }
        
        return false
    }
    
    /**
     * 確認処理
     */
    private fun handleConfirmation(player: Player, slot: Int): Boolean {
        val selection = playerSelections[player.uniqueId] ?: return false
        
        // 確認ボタンチェック
        if (slot == guiManager.getSlot(3, 3)) {
            // ダンジョン開始
            val theme = selection.selectedTheme ?: return false
            val size = selection.selectedSize ?: return false
            
            player.closeInventory()
            
            // ここからダンジョン開始処理（別途実装）
            player.sendMessage("§aダンジョンを開始します...")
            player.sendMessage("§fテーマ: §e${configManager.getDungeonThemeConfig(theme)?.displayName}")
            player.sendMessage("§fサイズ: §e${size.uppercase()}")
            
            // TODO: startDungeon(player, theme, size)
            
            playerSelections.remove(player.uniqueId)
            return true
        }
        
        // 戻るボタンチェック
        if (slot == guiManager.getSlot(3, 5)) {
            showSizeSelectionGui(player)
            return true
        }
        
        // キャンセルボタンチェック
        if (slot == guiManager.getSlot(3, 7)) {
            player.closeInventory()
            playerSelections.remove(player.uniqueId)
            return true
        }
        
        return false
    }
    
    /**
     * プレイヤーの選択情報を取得
     */
    fun getPlayerSelection(player: Player): GuiSelection? {
        return playerSelections[player.uniqueId]
    }
    
    /**
     * プレイヤーの選択情報をクリア
     */
    fun clearPlayerSelection(player: Player) {
        playerSelections.remove(player.uniqueId)
    }
    
    /**
     * 全選択情報をクリア（プラグイン無効化時など）
     */
    fun cleanup() {
        playerSelections.clear()
    }
}
