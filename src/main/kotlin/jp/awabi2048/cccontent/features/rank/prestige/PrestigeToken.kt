package jp.awabi2048.cccontent.features.rank.prestige

import jp.awabi2048.cccontent.features.rank.localization.MessageProvider
import jp.awabi2048.cccontent.features.rank.profession.Profession
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import java.util.UUID

/**
 * プレステージ思念アイテムを管理するクラス
 */
class PrestigeToken {

    companion object {
        private const val PRESTIGE_TOKEN_KEY = "prestige_token"
        private const val PROFESSION_KEY = "profession"
        private const val PRESTIGE_LEVEL_KEY = "prestige_level"
        private const val OWNER_KEY = "owner"
        private const val OWNER_NAME_KEY = "owner_name"

        /**
         * 思念アイテムを作成
         * @param profession 職業
         * @param prestigeLevel プレステージレベル
         * @param owner アイテムの所有者（プレイヤー）
         * @param messageProvider メッセージプロバイダー（職業名取得用）
         */
        fun create(profession: Profession, prestigeLevel: Int, owner: Player, messageProvider: MessageProvider): ItemStack {
            val item = ItemStack(Material.PAPER)
            val meta = item.itemMeta
            if (meta == null) {
                org.bukkit.Bukkit.getLogger().warning("[CCContent] Failed to create PrestigeToken: itemMeta is null")
                return item
            }

            // 職業名を翻訳ファイルから取得
            val professionName = messageProvider.getProfessionName(profession)

            // 表示名設定
            meta.setDisplayName("§d§l${professionName}の思念")

            // Lore設定
            val bar = "§8§m                        "
            val lore = mutableListOf(
                bar,
                "§6${professionName}の職を極めた証",
                bar,
                "§f§l| §7プレステージレベル §b$prestigeLevel",
                "§f§l| §7保有者 §6${owner.name}",
                bar
            )
            meta.lore = lore

            // PersistentDataContainerにデータを保存
            val container = meta.persistentDataContainer
            val plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("CC-Content")
            if (plugin == null) {
                org.bukkit.Bukkit.getLogger().warning("[CCContent] Failed to create PrestigeToken: plugin is null")
                return item
            }

             container.set(NamespacedKey(plugin, PRESTIGE_TOKEN_KEY), PersistentDataType.BYTE, 1)
            container.set(NamespacedKey(plugin, PROFESSION_KEY), PersistentDataType.STRING, profession.id)
            container.set(NamespacedKey(plugin, PRESTIGE_LEVEL_KEY), PersistentDataType.INTEGER, prestigeLevel)
            container.set(NamespacedKey(plugin, OWNER_KEY), PersistentDataType.STRING, owner.uniqueId.toString())
            container.set(NamespacedKey(plugin, OWNER_NAME_KEY), PersistentDataType.STRING, owner.name)

            item.itemMeta = meta
            org.bukkit.Bukkit.getLogger().info("[CCContent] PrestigeToken created: ${owner.name}, ${profession.id}, Lvl$prestigeLevel")
            return item
        }

        /**
         * アイテムが思念アイテムかチェック
         */
        fun isPrestigeToken(item: ItemStack?): Boolean {
            if (item == null || item.type == Material.AIR) return false
            val meta = item.itemMeta
            if (meta == null) {
                org.bukkit.Bukkit.getLogger().warning("[CCContent] isPrestigeToken: itemMeta is null")
                return false
            }
            val container = meta.persistentDataContainer
            val plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("CC-Content")
            if (plugin == null) {
                org.bukkit.Bukkit.getLogger().warning("[CCContent] isPrestigeToken: plugin is null")
                return false
            }
            val hasKey = container.has(NamespacedKey(plugin, PRESTIGE_TOKEN_KEY), PersistentDataType.BYTE)
            org.bukkit.Bukkit.getLogger().info("[CCContent] isPrestigeToken check: hasKey=$hasKey, itemType=${item.type}")
            return hasKey
        }

        /**
         * 思念アイテムから職業を取得
         */
        fun getProfession(item: ItemStack?): Profession? {
            if (!isPrestigeToken(item)) return null
            val meta = item!!.itemMeta
            if (meta == null) {
                org.bukkit.Bukkit.getLogger().warning("[CCContent] getProfession: itemMeta is null")
                return null
            }
            val container = meta.persistentDataContainer
            val plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("CC-Content")
            if (plugin == null) {
                org.bukkit.Bukkit.getLogger().warning("[CCContent] getProfession: plugin is null")
                return null
            }
            val professionId = container.get(NamespacedKey(plugin, PROFESSION_KEY), PersistentDataType.STRING)
            if (professionId == null) {
                org.bukkit.Bukkit.getLogger().warning("[CCContent] getProfession: professionId is null")
                return null
            }
            return Profession.fromId(professionId)
        }

        /**
         * 思念アイテムからプレステージレベルを取得
         */
        fun getPrestigeLevel(item: ItemStack?): Int {
            if (!isPrestigeToken(item)) return 0
            val meta = item!!.itemMeta
            if (meta == null) {
                org.bukkit.Bukkit.getLogger().warning("[CCContent] getPrestigeLevel: itemMeta is null")
                return 0
            }
            val container = meta.persistentDataContainer
            val plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("CC-Content")
            if (plugin == null) {
                org.bukkit.Bukkit.getLogger().warning("[CCContent] getPrestigeLevel: plugin is null")
                return 0
            }
            val level = container.get(NamespacedKey(plugin, PRESTIGE_LEVEL_KEY), PersistentDataType.INTEGER)
            if (level == null) {
                org.bukkit.Bukkit.getLogger().warning("[CCContent] getPrestigeLevel: level is null")
                return 0
            }
            return level
        }

        /**
         * 思念アイテムから所有者UUIDを取得
         */
        fun getOwnerUuid(item: ItemStack?): UUID? {
            if (!isPrestigeToken(item)) return null
            val meta = item!!.itemMeta
            if (meta == null) {
                org.bukkit.Bukkit.getLogger().warning("[CCContent] getOwnerUuid: itemMeta is null")
                return null
            }
            val container = meta.persistentDataContainer
            val plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("CC-Content")
            if (plugin == null) {
                org.bukkit.Bukkit.getLogger().warning("[CCContent] getOwnerUuid: plugin is null")
                return null
            }
            val uuidString = container.get(NamespacedKey(plugin, OWNER_KEY), PersistentDataType.STRING)
            if (uuidString == null) {
                org.bukkit.Bukkit.getLogger().warning("[CCContent] getOwnerUuid: uuidString is null")
                return null
            }
            return try {
                UUID.fromString(uuidString)
            } catch (e: IllegalArgumentException) {
                org.bukkit.Bukkit.getLogger().warning("[CCContent] getOwnerUuid: Invalid UUID format: $uuidString")
                null
            }
        }

        /**
         * 思念アイテムから所有者名を取得
         */
        fun getOwnerName(item: ItemStack?): String? {
            if (!isPrestigeToken(item)) return null
            val meta = item!!.itemMeta
            if (meta == null) {
                org.bukkit.Bukkit.getLogger().warning("[CCContent] getOwnerName: itemMeta is null")
                return null
            }
            val container = meta.persistentDataContainer
            val plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("CC-Content")
            if (plugin == null) {
                org.bukkit.Bukkit.getLogger().warning("[CCContent] getOwnerName: plugin is null")
                return null
            }
            val ownerName = container.get(NamespacedKey(plugin, OWNER_NAME_KEY), PersistentDataType.STRING)
            if (ownerName == null) {
                org.bukkit.Bukkit.getLogger().warning("[CCContent] getOwnerName: ownerName is null")
            }
            return ownerName
        }

        /**
         * プレイヤーが指定職業の思念アイテムをホットバーまたはオフハンドに持っているかチェック
         */
        fun hasTokenInHotbarOrOffhand(player: Player, profession: Profession): Boolean {
            val inventory = player.inventory
            
            // ホットバー（スロット0-8）をチェック
            for (i in 0..8) {
                val item = inventory.getItem(i)
                if (isValidTokenForProfession(item, profession, player.uniqueId)) {
                    return true
                }
            }
            
            // オフハンドをチェック
            val offhandItem = inventory.itemInOffHand
            if (isValidTokenForProfession(offhandItem, profession, player.uniqueId)) {
                return true
            }
            
            return false
        }

        /**
         * アイテムが指定職業かつ所有者の思念アイテムかチェック
         */
        private fun isValidTokenForProfession(item: ItemStack?, profession: Profession, ownerUuid: UUID): Boolean {
            if (!isPrestigeToken(item)) return false
            val tokenProfession = getProfession(item) ?: return false
            val tokenOwner = getOwnerUuid(item) ?: return false
            return tokenProfession == profession && tokenOwner == ownerUuid
        }

        /**
         * プレイヤーのインベントリから指定職業の思念アイテムを探す
         */
        fun findToken(player: Player, profession: Profession): ItemStack? {
            val inventory = player.inventory
            
            for (item in inventory.contents.filterNotNull()) {
                if (isValidTokenForProfession(item, profession, player.uniqueId)) {
                    return item
                }
            }
            
            return null
        }

        /**
         * プレイヤーのインベントリから指定職業の思念アイテムを削除
         */
        fun removeToken(player: Player, profession: Profession): Boolean {
            val inventory = player.inventory
            
            for (i in inventory.contents.indices) {
                val item = inventory.getItem(i) ?: continue
                if (isValidTokenForProfession(item, profession, player.uniqueId)) {
                    inventory.setItem(i, null)
                    return true
                }
            }
            
            return false
        }
    }
}