package jp.awabi2048.cccontent.features.brewery.barrel

import jp.awabi2048.cccontent.features.brewery.model.BreweryLocationKey
import java.util.UUID

class BreweryBarrelRegistry {
    private val byId = linkedMapOf<UUID, BreweryBarrel>()
    private val byMember = mutableMapOf<BreweryLocationKey, UUID>()

    fun register(barrel: BreweryBarrel): Boolean {
        if (barrel.members.any(byMember::containsKey)) {
            return false
        }
        byId[barrel.id] = barrel
        barrel.members.forEach { byMember[it] = barrel.id }
        return true
    }

    fun replaceAll(barrels: Collection<BreweryBarrel>) {
        byId.clear()
        byMember.clear()
        barrels.forEach { register(it) }
    }

    fun findById(id: UUID): BreweryBarrel? = byId[id]

    fun findByBlock(key: BreweryLocationKey): BreweryBarrel? =
        byMember[key]?.let(byId::get)

    fun unregister(id: UUID): BreweryBarrel? {
        val removed = byId.remove(id) ?: return null
        removed.members.forEach(byMember::remove)
        return removed
    }

    fun all(): List<BreweryBarrel> = byId.values.toList()
}
