package jp.awabi2048.cccontent.features.party

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

data class PartyStoreState(
    val parties: List<PartySnapshot>,
    val invites: List<PartyInvite>,
    val pendingDepartures: List<PendingPartyDeparture> = emptyList()
)

/** feature無効化や再起動で失わないよう、パーティー状態を独立保存する。 */
class PartyStore(private val file: Path) {
    fun load(): PartyStoreState {
        if (!Files.exists(file) || Files.size(file) == 0L) return PartyStoreState(emptyList(), emptyList(), emptyList())
        val yaml = YamlConfiguration().also { it.load(file.toFile()) }
        val version = yaml.requiredInt("version")
        require(version == 1) { "unsupported party data version: $version" }
        val parties = yaml.getConfigurationSection("parties")?.let(::readParties) ?: emptyList()
        val invites = yaml.getConfigurationSection("invites")?.let(::readInvites) ?: emptyList()
        val pending = yaml.getConfigurationSection("pending_departures")?.let(::readPendingDepartures) ?: emptyList()
        return PartyStoreState(parties, invites, pending)
    }

    @Synchronized
    fun save(state: PartyStoreState) {
        file.parent?.let(Files::createDirectories)
        val yaml = YamlConfiguration()
        yaml.set("version", 1)
        state.parties.forEach { party ->
            val path = "parties.${party.id}"
            yaml.set("$path.name", party.name)
            yaml.set("$path.description", party.description)
            yaml.set("$path.capacity", party.capacity)
            yaml.set("$path.recruiting", party.recruiting)
            yaml.set("$path.leader", party.leader.toString())
            yaml.set("$path.members", party.members.map(UUID::toString))
        }
        state.invites.forEachIndexed { index, invite ->
            val path = "invites.$index"
            yaml.set("$path.party_id", invite.partyId.toString())
            yaml.set("$path.inviter", invite.inviter.toString())
            yaml.set("$path.invitee", invite.invitee.toString())
            yaml.set("$path.expires_at", invite.expiresAtMillis)
        }
        state.pendingDepartures.forEach { departure ->
            val path = "pending_departures.${departure.member}"
            yaml.set("$path.party_id", departure.partyId.toString())
            yaml.set("$path.expires_at", departure.expiresAtMillis)
        }

        val temporary = file.resolveSibling("${file.fileName}.tmp-${UUID.randomUUID()}")
        try {
            yaml.save(temporary.toFile())
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (exception: IOException) {
            Files.deleteIfExists(temporary)
            throw exception
        }
    }

    private fun readParties(section: ConfigurationSection): List<PartySnapshot> =
        section.getKeys(false).map { id ->
            val item = section.getConfigurationSection(id) ?: error("party '$id' must be a map")
            val partyId = parseUuid(id, "party id")
            val name = item.requiredString("name")
            val description = item.requiredString("description")
            val capacity = item.requiredInt("capacity")
            require(capacity in 1..99) { "party '$id' capacity is invalid" }
            val recruiting = item.requiredBoolean("recruiting")
            val leader = item.requiredUuid("leader")
            val members = item.requiredStringList("members").map { value -> parseUuid(value, "${item.currentPath}.members") }.toCollection(LinkedHashSet())
            require(members.isNotEmpty() && leader in members) { "party '$id' must contain its leader" }
            PartySnapshot(partyId, name, description, capacity, recruiting, leader, members)
        }

    private fun readInvites(section: ConfigurationSection): List<PartyInvite> =
        section.getKeys(false).map { key ->
            val item = section.getConfigurationSection(key) ?: error("invite '$key' must be a map")
            PartyInvite(
                item.requiredUuid("party_id"),
                item.requiredUuid("inviter"),
                item.requiredUuid("invitee"),
                item.requiredLong("expires_at")
            )
        }

    private fun readPendingDepartures(section: ConfigurationSection): List<PendingPartyDeparture> =
        section.getKeys(false).map { member ->
            val item = section.getConfigurationSection(member) ?: error("pending departure '$member' must be a map")
            PendingPartyDeparture(
                item.requiredUuid("party_id"),
                parseUuid(member, "pending departure member"),
                item.requiredLong("expires_at")
            )
        }

    private fun ConfigurationSection.requiredString(key: String): String =
        (get(key) as? String) ?: error("$currentPath.$key must be a string")

    private fun ConfigurationSection.requiredStringList(key: String): List<String> {
        val value = get(key) ?: error("$currentPath.$key is required")
        val list = value as? List<*> ?: error("$currentPath.$key must be a list")
        return list.map { it as? String ?: error("$currentPath.$key must contain strings") }
    }

    private fun ConfigurationSection.requiredBoolean(key: String): Boolean =
        (get(key) as? Boolean) ?: error("$currentPath.$key must be a boolean")

    private fun ConfigurationSection.requiredInt(key: String): Int {
        val number = get(key) as? Number ?: error("$currentPath.$key must be an integer")
        require(number.toDouble() == number.toInt().toDouble()) { "$currentPath.$key must be an integer" }
        return number.toInt()
    }

    private fun ConfigurationSection.requiredLong(key: String): Long {
        val number = get(key) as? Number ?: error("$currentPath.$key must be an integer")
        require(number.toDouble() == number.toLong().toDouble()) { "$currentPath.$key must be an integer" }
        return number.toLong()
    }

    private fun ConfigurationSection.requiredUuid(key: String): UUID = parseUuid(requiredString(key), "$currentPath.$key")
    private fun YamlConfiguration.requiredInt(key: String): Int {
        val number = get(key) as? Number ?: error("$key must be an integer")
        require(number.toDouble() == number.toInt().toDouble()) { "$key must be an integer" }
        return number.toInt()
    }

    private fun parseUuid(value: String, field: String): UUID = runCatching { UUID.fromString(value) }.getOrElse { error("$field must be a UUID") }
}
