package jp.awabi2048.cccontent.features.processing

import org.bukkit.Bukkit
import org.bukkit.block.Block
import java.util.UUID

/**
 * 加工設備を参照するための正準座標です。
 *
 * 個別機能が持つ LocationKey をそのまま共有すると、Cooking と Brewery のどちらかへ
 * 汎用層が依存してしまいます。そのため、汎用層ではワールドとブロック座標だけを扱います。
 */
data class ProcessingLocationKey(
    val worldId: UUID,
    val x: Int,
    val y: Int,
    val z: Int,
) {
    fun serialize(): String = "$worldId;$x;$y;$z"

    fun blockIfLoaded(): Block? {
        val world = Bukkit.getWorld(worldId) ?: return null
        if (!world.isChunkLoaded(x shr 4, z shr 4)) return null
        return world.getBlockAt(x, y, z)
    }

    companion object {
        fun from(block: Block): ProcessingLocationKey =
            ProcessingLocationKey(block.world.uid, block.x, block.y, block.z)

        fun deserialize(value: String): ProcessingLocationKey? {
            val parts = value.split(';')
            if (parts.size != 4) return null
            return runCatching {
                ProcessingLocationKey(
                    UUID.fromString(parts[0]),
                    parts[1].toInt(),
                    parts[2].toInt(),
                    parts[3].toInt(),
                )
            }.getOrNull()
        }
    }
}

/**
 * 設備を借用する側が要求する能力です。
 *
 * 「樽」「大釜」のような物理ブロック名ではなく、工程が必要とする操作で表現することで、
 * 将来別の設備実装へ置き換えても Cooking/Brewery のレシピ契約を変更せずに済みます。
 */
enum class ProcessingEquipmentCapability {
    MIXING,
    HEATING,
    FERMENTATION,
    DISTILLATION,
    AGING,
    PRESSING,
    FILTRATION,
}

enum class ProcessingClient {
    COOKING,
    BREWERY,
}

/**
 * 物理設備を汎用工程層へ公開する値オブジェクトです。
 *
 * canonicalLocation は設備全体を占有するキーであり、members は複数ブロック設備の各部を
 * クリックした場合にも同じ設備へ解決するための集合です。
 */
data class ProcessingEquipment(
    val id: String,
    val canonicalLocation: ProcessingLocationKey,
    val members: Set<ProcessingLocationKey>,
    val capabilities: Set<ProcessingEquipmentCapability>,
) {
    init {
        require(id.isNotBlank())
        require(members.isNotEmpty())
        require(canonicalLocation in members)
        require(capabilities.isNotEmpty())
    }

    fun contains(location: ProcessingLocationKey): Boolean = location in members

    fun supports(capability: ProcessingEquipmentCapability): Boolean = capability in capabilities
}

/** 物理設備の検出を担当する機能側アダプターです。汎用層は設備の見た目を知りません。 */
fun interface ProcessingEquipmentProvider {
    fun findAt(location: ProcessingLocationKey): ProcessingEquipment?
}

data class ProcessingEquipmentLease(
    val equipmentId: String,
    val canonicalLocation: ProcessingLocationKey,
    val capability: ProcessingEquipmentCapability,
    val client: ProcessingClient,
    val processId: String,
)

/**
 * 設備の検出と排他借用を一元化します。
 *
 * 既存の機能固有GUIロックは表示中のプレイヤーを制御するために残し、こちらは
 * 「Cooking と Brewery が同じ物理設備を同時に処理へ使わない」ための機械的な占有契約を
 * 担います。責務を分けることで、GUIを開いていない自動処理や将来の第三機能にも同じ契約を
 * 適用できます。
 */
class ProcessingEquipmentService {
    private val providers = linkedMapOf<String, ProcessingEquipmentProvider>()
    private val leases = linkedMapOf<ProcessingLocationKey, ProcessingEquipmentLease>()

    @Synchronized
    fun registerProvider(id: String, provider: ProcessingEquipmentProvider) {
        require(id.isNotBlank())
        check(id !in providers) { "Processing equipment provider is already registered: $id" }
        providers[id] = provider
    }

    @Synchronized
    fun unregisterProvider(id: String) {
        providers.remove(id)
    }

    @Synchronized
    fun findAt(location: ProcessingLocationKey): ProcessingEquipment? =
        providers.values.asSequence()
            .mapNotNull { provider -> provider.findAt(location) }
            .firstOrNull { equipment -> equipment.contains(location) }

    @Synchronized
    fun findAt(
        location: ProcessingLocationKey,
        capability: ProcessingEquipmentCapability,
    ): ProcessingEquipment? = providers.values.asSequence()
        .mapNotNull { provider -> provider.findAt(location) }
        .firstOrNull { equipment -> equipment.contains(location) && equipment.supports(capability) }

    @Synchronized
    fun tryAcquire(
        location: ProcessingLocationKey,
        capability: ProcessingEquipmentCapability,
        client: ProcessingClient,
        processId: String,
    ): ProcessingEquipmentLease? {
        require(processId.isNotBlank())
        val equipment = findAt(location, capability) ?: return null
        val existing = leases[equipment.canonicalLocation]
        if (existing != null) {
            return existing.takeIf {
                it.client == client && it.processId == processId && it.capability == capability
            }
        }
        return ProcessingEquipmentLease(
            equipment.id,
            equipment.canonicalLocation,
            capability,
            client,
            processId,
        ).also { leases[equipment.canonicalLocation] = it }
    }

    @Synchronized
    fun leaseAt(location: ProcessingLocationKey): ProcessingEquipmentLease? {
        leases[location]?.let { return it }
        val equipment = findAt(location) ?: return null
        return leases[equipment.canonicalLocation]
    }

    @Synchronized
    fun release(lease: ProcessingEquipmentLease): Boolean =
        leases.remove(lease.canonicalLocation, lease)

    @Synchronized
    fun releaseAt(
        location: ProcessingLocationKey,
        client: ProcessingClient,
        processId: String,
    ): Boolean {
        val lease = leaseAt(location) ?: return false
        if (lease.client != client || lease.processId != processId) return false
        return leases.remove(lease.canonicalLocation, lease)
    }

    @Synchronized
    fun releaseAll(client: ProcessingClient) {
        leases.entries.removeIf { (_, lease) -> lease.client == client }
    }

    @Synchronized
    fun clear() {
        leases.clear()
        providers.clear()
    }

    @Synchronized
    fun activeLeases(): List<ProcessingEquipmentLease> = leases.values.toList()
}
