package jp.awabi2048.cccontent.features.resourcecollection

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class ResourceCollectionSettings(
    val enabled: Boolean,
    val normalBonusEnabled: Map<ResourceCollectionKind, Boolean>
) {
    companion object {
        private const val CONFIG_PATH = "config/resource_collection/config.yml"

        fun load(plugin: JavaPlugin): ResourceCollectionSettings {
            val file = ensureFile(plugin, CONFIG_PATH)
            migrateIfRequired(plugin, file)
            archiveLegacyCollectionRules(plugin, file.parentFile)
            val config = YamlConfiguration.loadConfiguration(file)
            require(config.get("config_version") is Number && config.getInt("config_version") == 3) {
                "$CONFIG_PATH.config_version must be the integer 3"
            }
            return ResourceCollectionSettings(
                requireBoolean(config, "enabled"),
                ResourceCollectionKind.entries.associateWith { kind ->
                    requireBoolean(config, "normal_bonus.${kind.name.lowercase()}")
                }
            )
        }

        private fun migrateIfRequired(plugin: JavaPlugin, file: File) {
            val existing = YamlConfiguration.loadConfiguration(file)
            val version = existing.getInt("config_version", -1)
            if (version == 3) return
            require(version == 2) { "Unsupported resource collection config version: $version" }
            val migrated = YamlConfiguration.loadConfiguration(file)
            migrated.set("config_version", 3)
            migrated.set("worlds", null)
            ResourceCollectionKind.entries.forEach { kind ->
                migrated.set("normal_bonus.${kind.name.lowercase()}", true)
            }
            val backup = File(file.parentFile, "${file.name}.bak-v2")
            if (!backup.exists()) Files.copy(file.toPath(), backup.toPath())
            val temporary = File(file.parentFile, "${file.name}.migrating")
            runCatching {
                migrated.save(temporary)
                runCatching {
                    Files.move(
                        temporary.toPath(), file.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING
                    )
                }.getOrElse {
                    Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            }.onFailure {
                temporary.delete()
                throw IllegalStateException("Resource collection config migration failed; original was preserved", it)
            }
            plugin.logger.info("Resource collection config migrated from version 2 to 3; backup=${backup.name}")
        }

        private fun archiveLegacyCollectionRules(plugin: JavaPlugin, directory: File) {
            val legacy = File(directory, "collection.yml")
            if (!legacy.exists()) return
            var backup = File(directory, "collection.yml.bak-v2")
            var suffix = 1
            while (backup.exists()) {
                backup = File(directory, "collection.yml.bak-v2-$suffix")
                suffix++
            }
            Files.move(legacy.toPath(), backup.toPath())
            plugin.logger.info("Legacy resource collection EXP and craft rules were archived as ${backup.name}")
        }

        private fun ensureFile(plugin: JavaPlugin, path: String): File {
            val file = File(plugin.dataFolder, path)
            if (!file.exists()) {
                file.parentFile.mkdirs()
                check(plugin.getResource(path) != null) { "Bundled resource is missing: $path" }
                plugin.saveResource(path, false)
            }
            return file
        }

        private fun requireBoolean(config: YamlConfiguration, key: String): Boolean {
            val value = config.get(key)
            require(value is Boolean) { "$CONFIG_PATH.$key must be a boolean" }
            return value
        }
    }
}
