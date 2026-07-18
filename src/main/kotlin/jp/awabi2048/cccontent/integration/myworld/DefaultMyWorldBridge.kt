package jp.awabi2048.cccontent.integration.myworld

import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.myworldmanager.api.service.ApiBedrockFormService
import me.awabi2048.myworldmanager.api.service.ApiWorldRepository
import me.awabi2048.myworldmanager.model.WorldData
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import java.util.UUID

class DefaultMyWorldBridge @JvmOverloads constructor(
    private val pluginAvailable: () -> Boolean = {
        Bukkit.getPluginManager().isPluginEnabled("MyWorldManager")
    },
    private val repositoryProvider: () -> ApiWorldRepository? = MyWorldManagerApi::getWorldRepository,
    private val bedrockServiceProvider: () -> ApiBedrockFormService? = MyWorldManagerApi::getBedrockFormService,
    private val pointCapabilityProvider: () -> Boolean = MyWorldManagerApi::isWorldPointEconomyEnabled,
    private val pointServiceAvailableProvider: () -> Boolean = MyWorldManagerApi::isWorldPointServiceAvailable,
    private val pointGrant: (UUID, Int) -> Int = MyWorldManagerApi::addWorldPoint
) : MyWorldBridge {
    override fun isAvailable(): Boolean = pluginAvailable() && runCatching { repositoryProvider() != null }.getOrDefault(false)

    override fun findMyWorldByWorldKey(worldKey: NamespacedKey): MyWorldRef? {
        if (!pluginAvailable()) return null
        return runCatching { repositoryProvider()?.findByWorldKey(worldKey.toString())?.toRef() }.getOrNull()
    }

    override fun findMyWorldByUuid(worldUuid: UUID): MyWorldRef? {
        if (!pluginAvailable()) return null
        return runCatching { repositoryProvider()?.findByUuid(worldUuid)?.toRef() }.getOrNull()
    }

    override fun isBedrock(player: Player): Boolean {
        if (!pluginAvailable()) return false
        return runCatching { bedrockServiceProvider()?.isBedrock(player) == true }.getOrDefault(false)
    }

    override fun isWorldPointCapabilityEnabled(): Boolean =
        pluginAvailable() && runCatching(pointCapabilityProvider).getOrDefault(false)

    override fun grantWorldPoints(playerId: UUID, amount: Int): WorldPointGrantResult {
        require(amount > 0) { "amount must be positive" }
        if (!pluginAvailable()) return WorldPointGrantResult.Unavailable(MyWorldUnavailableReason.PLUGIN_MISSING)
        val capabilityEnabled = runCatching(pointCapabilityProvider).getOrElse {
            return WorldPointGrantResult.Unavailable(MyWorldUnavailableReason.API_UNAVAILABLE)
        }
        if (!capabilityEnabled) return WorldPointGrantResult.Unavailable(MyWorldUnavailableReason.CAPABILITY_DISABLED)
        val serviceAvailable = runCatching(pointServiceAvailableProvider).getOrElse {
            return WorldPointGrantResult.Unavailable(MyWorldUnavailableReason.API_UNAVAILABLE)
        }
        if (!serviceAvailable) return WorldPointGrantResult.Unavailable(MyWorldUnavailableReason.SERVICE_UNAVAILABLE)
        return runCatching { WorldPointGrantResult.Success(pointGrant(playerId, amount)) }
            .getOrElse { WorldPointGrantResult.Failure(it.message ?: it.javaClass.simpleName) }
    }

    private fun WorldData.toRef(): MyWorldRef? {
        val key = NamespacedKey.fromString(worldKey) ?: return null
        return MyWorldRef(uuid, key, owner, members.toSet(), moderators.toSet())
    }
}
