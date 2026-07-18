package jp.awabi2048.cccontent.features.brewery.barrel

import jp.awabi2048.cccontent.features.brewery.model.BarrelSize
import jp.awabi2048.cccontent.features.brewery.model.BreweryLocationKey
import org.bukkit.block.BlockFace
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.UUID
import java.util.logging.Logger

class BreweryBarrelStore(
    private val file: File,
    private val matcher: BreweryBarrelMatcher,
    private val logger: Logger
) {
    fun load(): List<BreweryBarrel> {
        if (!file.isFile) return emptyList()
        val yaml = YamlConfiguration.loadConfiguration(file)
        require(yaml.getInt("schema_version", -1) == SCHEMA_VERSION) {
            "Brewery樽台帳のschema_versionが不正です"
        }
        val section = yaml.getConfigurationSection("barrels") ?: return emptyList()
        return section.getKeys(false).mapNotNull { rawId ->
            runCatching {
                val base = "barrels.$rawId"
                val stored = BreweryBarrel(
                    id = UUID.fromString(rawId),
                    size = BarrelSize.valueOf(yaml.getString("$base.size")!!),
                    origin = BreweryLocationKey.parse(yaml.getString("$base.origin")!!)!!,
                    sign = BreweryLocationKey.parse(yaml.getString("$base.sign")!!)!!,
                    outward = BlockFace.valueOf(yaml.getString("$base.outward")!!),
                    woodType = yaml.getString("$base.wood_type")!!,
                    members = emptySet()
                )
                when (val validated = matcher.validate(stored)) {
                    is BarrelMatchResult.Matched -> validated.barrel
                    is BarrelMatchResult.Failed -> {
                        logger.warning(
                            "[Brewery] 不正な樽台帳を除外しました: $rawId " +
                                "${validated.failure.location.toSerialized()} " +
                                "expected=${validated.failure.expected} actual=${validated.failure.actual}"
                        )
                        null
                    }
                }
            }.getOrElse {
                logger.warning("[Brewery] 樽台帳を読み込めません: $rawId: ${it.message}")
                null
            }
        }
    }

    fun save(barrels: Collection<BreweryBarrel>) {
        val yaml = YamlConfiguration()
        yaml.set("schema_version", SCHEMA_VERSION)
        barrels.forEach { barrel ->
            val base = "barrels.${barrel.id}"
            yaml.set("$base.size", barrel.size.name)
            yaml.set("$base.origin", barrel.origin.toSerialized())
            yaml.set("$base.sign", barrel.sign.toSerialized())
            yaml.set("$base.outward", barrel.outward.name)
            yaml.set("$base.wood_type", barrel.woodType)
        }
        file.parentFile?.mkdirs()
        yaml.save(file)
    }

    companion object {
        private const val SCHEMA_VERSION = 1
    }
}
