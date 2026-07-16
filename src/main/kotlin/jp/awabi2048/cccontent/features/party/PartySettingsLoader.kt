package jp.awabi2048.cccontent.features.party

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

data class PartySettings(
    val defaultCapacity: Int,
    val inviteExpirationMillis: Long,
    val offlineGraceSeconds: Long
)

class PartySettingsLoader(private val plugin: JavaPlugin) {
    fun load(): PartySettings {
        val file = File(plugin.dataFolder, "config/party/config.yml")
        if (!file.exists()) {
            file.parentFile.mkdirs()
            plugin.saveResource("config/party/config.yml", false)
        }
        val yaml = YamlConfiguration.loadConfiguration(file)
        val section = yaml.getConfigurationSection("party")
            ?: error("config/party/config.yml.party is required")
        val defaultCapacity = requiredInteger(section.get("default_capacity"), "party.default_capacity").also {
            require(it in 1..99) { "party.default_capacity must be between 1 and 99" }
        }.toInt()
        val inviteExpirationSeconds = requiredInteger(section.get("invite_expiration_seconds"), "party.invite_expiration_seconds")
        val offlineGraceSeconds = requiredInteger(section.get("offline_grace_seconds"), "party.offline_grace_seconds")
        return PartySettings(
            defaultCapacity = defaultCapacity,
            inviteExpirationMillis = inviteExpirationSeconds * 1000L,
            offlineGraceSeconds = offlineGraceSeconds
        ).also {
            require(it.defaultCapacity in 1..99) { "party.default_capacity must be between 1 and 99" }
            require(it.inviteExpirationMillis > 0) { "party.invite_expiration_seconds must be positive" }
            require(it.offlineGraceSeconds > 0) { "party.offline_grace_seconds must be positive" }
        }
    }

    private fun requiredInteger(value: Any?, path: String): Long {
        require(value is Byte || value is Short || value is Int || value is Long) { "$path must be an integer" }
        return (value as Number).toLong()
    }
}
