package jp.awabi2048.cccontent.items.misc

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

/**
 * GulliverLightの設定を管理するクラス
 * 統合元プラグインのconfig.ymlをそのまま移植
 */
object GulliverConfig {
    private lateinit var config: YamlConfiguration
    private lateinit var configFile: File
    
    fun initialize(plugin: JavaPlugin) {
        val configDir = File(plugin.dataFolder, "config")
        if (!configDir.exists()) {
            configDir.mkdirs()
        }
        
        configFile = File(configDir, "gulliverlight.yml")
        if (!configFile.exists()) {
            plugin.saveResource("config/gulliverlight.yml", false)
        }
        
        reload()
    }
    
    fun reload() {
        config = YamlConfiguration.loadConfiguration(configFile)
    }
    
    // Big Light settings
    fun getBigLightMaxScale(): Double = config.getDouble("big_light.max_scale", 5.0)
    fun getBigLightScaleSpeed(): Double = config.getDouble("big_light.scale_speed", 0.05)
    
    // Small Light settings
    fun getSmallLightMinScale(): Double = config.getDouble("small_light.min_scale", 0.1)
    fun getSmallLightScaleSpeed(): Double = config.getDouble("small_light.scale_speed", 0.05)
    
    // Sound settings
    fun getUseSound(): String = config.getString("sounds.use_sound.sound") ?: "minecraft:block.beacon.activate"
    fun getUseSoundVolume(): Float = config.getDouble("sounds.use_sound.volume", 1.0).toFloat()
    fun getUseSoundPitch(): Float = config.getDouble("sounds.use_sound.pitch", 1.0).toFloat()
    
    fun getResetSound(): String = config.getString("sounds.reset_sound") ?: "minecraft:block.beacon.deactivate"
    fun getLimitSound(): String = config.getString("sounds.limit_sound") ?: "minecraft:block.note_block.bass"
    
    fun getDefaultVolume(): Float = config.getDouble("sounds.volume", 1.0).toFloat()
    fun getDefaultPitch(): Float = config.getDouble("sounds.pitch", 1.0).toFloat()
    
    // Helper method to get sound configuration
    fun getSoundConfig(soundType: String): SoundConfig {
        return when (soundType) {
            "use" -> SoundConfig(getUseSound(), getUseSoundVolume(), getUseSoundPitch())
            "reset" -> SoundConfig(getResetSound(), getDefaultVolume(), getDefaultPitch())
            "limit" -> SoundConfig(getLimitSound(), getDefaultVolume(), getDefaultPitch())
            else -> SoundConfig("minecraft:block.beacon.activate", 1.0f, 1.0f)
        }
    }
}

data class SoundConfig(
    val soundKey: String,
    val volume: Float,
    val pitch: Float
)