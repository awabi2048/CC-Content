package jp.awabi2048.cccontent.features.npc.shop

import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.UUID

class OageShrineShopState(private val file: File) {
    private var yaml = YamlConfiguration.loadConfiguration(file)

    fun reload() {
        yaml = YamlConfiguration.loadConfiguration(file)
    }

    fun clearDaily() {
        yaml.set("purchase_daily", null)
        save()
    }

    fun clearWeekly() {
        yaml.set("purchase_weekly", null)
        save()
    }

    fun getDailyCount(playerId: UUID, key: String): Int {
        return yaml.getInt("purchase_daily.$playerId.$key", 0)
    }

    fun getWeeklyCount(playerId: UUID, key: String): Int {
        return yaml.getInt("purchase_weekly.$playerId.$key", 0)
    }

    fun getDailyTabCount(playerId: UUID, tabId: String): Int {
        return yaml.getInt("purchase_daily.$playerId.__tab.$tabId", 0)
    }

    fun getWeeklyTabCount(playerId: UUID, tabId: String): Int {
        return yaml.getInt("purchase_weekly.$playerId.__tab.$tabId", 0)
    }

    fun incrementDaily(playerId: UUID, key: String) {
        yaml.set("purchase_daily.$playerId.$key", getDailyCount(playerId, key) + 1)
        save()
    }

    fun incrementWeekly(playerId: UUID, key: String) {
        yaml.set("purchase_weekly.$playerId.$key", getWeeklyCount(playerId, key) + 1)
        save()
    }

    fun incrementDailyTab(playerId: UUID, tabId: String) {
        yaml.set("purchase_daily.$playerId.__tab.$tabId", getDailyTabCount(playerId, tabId) + 1)
        save()
    }

    fun incrementWeeklyTab(playerId: UUID, tabId: String) {
        yaml.set("purchase_weekly.$playerId.__tab.$tabId", getWeeklyTabCount(playerId, tabId) + 1)
        save()
    }

    fun canPurchase(playerId: UUID, tab: OageShrineShopTabDefinition, item: OageShrineShopItemDefinition): PurchaseLimitState {
        val dailyLimit = item.purchaseLimitDaily ?: tab.purchaseLimitDaily
        val weeklyLimit = item.purchaseLimitWeekly ?: tab.purchaseLimitWeekly
        val dailyCount = getDailyCount(playerId, item.goodsId)
        val weeklyCount = getWeeklyCount(playerId, item.goodsId)
        val tabDailyCount = getDailyTabCount(playerId, tab.tabId)
        val tabWeeklyCount = getWeeklyTabCount(playerId, tab.tabId)

        if (dailyLimit != null && dailyCount >= dailyLimit) {
            return PurchaseLimitState.DAILY_LIMIT
        }
        if (weeklyLimit != null && weeklyCount >= weeklyLimit) {
            return PurchaseLimitState.WEEKLY_LIMIT
        }
        if (tab.purchaseLimitDaily != null && tabDailyCount >= tab.purchaseLimitDaily) {
            return PurchaseLimitState.TAB_DAILY_LIMIT
        }
        if (tab.purchaseLimitWeekly != null && tabWeeklyCount >= tab.purchaseLimitWeekly) {
            return PurchaseLimitState.TAB_WEEKLY_LIMIT
        }
        return PurchaseLimitState.ALLOWED
    }

    fun recordPurchase(playerId: UUID, tab: OageShrineShopTabDefinition, item: OageShrineShopItemDefinition) {
        incrementDaily(playerId, item.goodsId)
        incrementWeekly(playerId, item.goodsId)
        incrementDailyTab(playerId, tab.tabId)
        incrementWeeklyTab(playerId, tab.tabId)
    }

    private fun save() {
        file.parentFile?.mkdirs()
        yaml.save(file)
    }
}

enum class PurchaseLimitState {
    ALLOWED,
    DAILY_LIMIT,
    WEEKLY_LIMIT,
    TAB_DAILY_LIMIT,
    TAB_WEEKLY_LIMIT
}
