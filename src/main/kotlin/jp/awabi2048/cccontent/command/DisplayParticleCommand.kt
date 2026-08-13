package jp.awabi2048.cccontent.command

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.displayeffect.DisplayEffectStartRejection
import com.awabi2048.ccsystem.api.displayeffect.DisplayEffectStartResult
import com.awabi2048.ccsystem.api.displayeffect.DisplayEffectVector3
import com.awabi2048.ccsystem.api.displayeffect.DisplayParticleEmissionRequest
import com.awabi2048.ccsystem.api.displayeffect.DisplayParticleCollisionMode
import com.awabi2048.ccsystem.api.displayeffect.DisplayParticleCollisionProperties
import com.awabi2048.ccsystem.api.displayeffect.DisplayParticleMotionPresetId
import com.awabi2048.ccsystem.api.displayeffect.DisplayParticleMotionProperties
import com.awabi2048.ccsystem.api.displayeffect.DisplayParticlePresetId
import com.awabi2048.ccsystem.api.displayeffect.DisplayParticleVisibilityMode
import org.bukkit.command.CommandSender
import org.bukkit.plugin.Plugin

/** `/ccc particle` を構文解釈し、描画の責務はCC-Systemの公開APIへ委譲します。 */
class DisplayParticleCommand(private val plugin: Plugin) {
    fun execute(sender: CommandSender, args: Array<String>): Boolean {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(ContentManagementI18n.text(sender, "particle.no_permission"))
            return true
        }

        val parsed = runCatching { DisplayParticleCommandSyntax.parse(args) }.getOrElse {
            sender.sendMessage(ContentManagementI18n.text(sender, "particle.invalid_argument", "detail" to it.message))
            sender.sendMessage(ContentManagementI18n.text(sender, "particle.usage"))
            return true
        }
        val location = runCatching {
            parsed.position?.let { CommandLocationResolver.resolve(sender, it.x, it.y, it.z) }
                ?: CommandLocationResolver.senderLocation(sender)?.clone()
                ?: throw IllegalArgumentException("座標省略は位置を持つ実行者のみ使用できます")
        }.getOrElse {
            sender.sendMessage(ContentManagementI18n.text(sender, "particle.invalid_argument", "detail" to it.message))
            return true
        }

        val request = runCatching {
            DisplayParticleEmissionRequest(
                presetId = DisplayParticlePresetId(parsed.patternId),
                motionPresetId = DisplayParticleMotionPresetId(parsed.motionId),
                collisionMode = parsed.collisionMode,
                motionProperties = parsed.motionProperties,
                collisionProperties = parsed.collisionProperties,
                delta = parsed.delta,
                speed = parsed.speed,
                count = parsed.count,
                visibilityMode = parsed.visibilityMode
            )
        }.getOrElse {
            sender.sendMessage(ContentManagementI18n.text(sender, "particle.invalid_argument", "detail" to it.message))
            return true
        }

        return when (val result = CCSystem.getAPI().getDisplayEffectService().emitDisplayParticles(plugin, location, request)) {
            is DisplayEffectStartResult.Started -> {
                sender.sendMessage(ContentManagementI18n.text(sender, "particle.started", "pattern" to parsed.patternId, "count" to parsed.count))
                true
            }
            is DisplayEffectStartResult.Rejected -> {
                val key = when (result.reason) {
                    DisplayEffectStartRejection.UNKNOWN_PATTERN -> "particle.unknown_pattern"
                    DisplayEffectStartRejection.NO_VIEWERS -> "particle.no_viewers"
                    else -> "particle.rejected"
                }
                sender.sendMessage(ContentManagementI18n.text(sender, key, "pattern" to parsed.patternId, "detail" to result.message))
                true
            }
        }
    }

    fun complete(sender: CommandSender, args: Array<String>): List<String> {
        if (!sender.hasPermission(PERMISSION)) return emptyList()
        return when (args.size) {
            1 -> CCSystem.getAPI().getDisplayEffectService().listDisplayParticlePresets()
                .map { it.id.value }
                .filter { it.startsWith(args[0], ignoreCase = true) }
            2 -> CCSystem.getAPI().getDisplayEffectService().listDisplayParticleMotionPresets()
                .map { it.id.value }
                .filter { it.startsWith(args[1], ignoreCase = true) }
            3 -> DisplayParticleCollisionMode.entries.map { it.name.lowercase() }
                .filter { it.startsWith(args[2], ignoreCase = true) }
            else -> completeTail(args)
        }
    }

    private fun completeTail(args: Array<String>): List<String> {
        val separator = args.indexOf("--")
        if (separator >= 0) {
            return propertySuggestions(args.getOrNull(1), args.getOrNull(2))
                .filter { it.startsWith(args.last(), ignoreCase = true) }
        }
        return when (args.size) {
            4, 5, 6 -> listOf("~", "~1", "~-1", "^").filter { it.startsWith(args.last()) }
            7, 8, 9, 10 -> listOf("0", "0.1", "0.5").filter { it.startsWith(args.last()) }
            11 -> listOf("1", "2", "4", "8").filter { it.startsWith(args.last()) }
            12 -> listOf("normal", "force", "--").filter { it.startsWith(args.last(), ignoreCase = true) }
            13 -> listOf("--").filter { it.startsWith(args.last()) }
            else -> emptyList()
        }
    }

    companion object {
        const val PERMISSION = "cc-content.particle"
    }

    private fun propertySuggestions(motionId: String?, collisionId: String?): List<String> {
        val motion = motionId?.substringAfter(':')?.lowercase()
        val commonVelocity = listOf("motion.initial-velocity=0,0,0")
        val motionValues = when (motion) {
            "static" -> emptyList()
            "inertial" -> commonVelocity + "motion.retention=0.98"
            "ballistic" -> commonVelocity + listOf("motion.acceleration=0,-0.012,0", "motion.retention=0.96")
            "buoyant", "drift" -> commonVelocity + listOf(
                "motion.acceleration=0,0.002,0", "motion.retention=0.96",
                "motion.turbulence=0.0045", "motion.frequency=0.22"
            )
            "burst" -> commonVelocity + listOf(
                "motion.acceleration=0,-0.004,0", "motion.retention=0.92", "motion.radial-speed=0.085"
            )
            "orbit" -> commonVelocity + listOf(
                "motion.spawn-radius=0.45", "motion.orbit-speed=0.18", "motion.radial-pull=0.02"
            )
            "attract" -> commonVelocity + listOf(
                "motion.spawn-radius=0.8", "motion.attraction=0.012", "motion.retention=0.96", "motion.max-speed=0.16"
            )
            else -> emptyList()
        }
        return if (collisionId.equals("bounce", ignoreCase = true)) {
            motionValues + "collision.restitution=0.5"
        } else motionValues
    }
}

internal data class RawPosition(val x: String, val y: String, val z: String)

internal data class ParsedDisplayParticleCommand(
    val patternId: String,
    val motionId: String,
    val collisionMode: DisplayParticleCollisionMode,
    val position: RawPosition?,
    val delta: DisplayEffectVector3,
    val speed: Double,
    val count: Int,
    val visibilityMode: DisplayParticleVisibilityMode,
    val motionProperties: DisplayParticleMotionProperties,
    val collisionProperties: DisplayParticleCollisionProperties
)

/** バニラ `/particle` の省略形と完全形だけを許可し、曖昧な中間形を排除します。 */
internal object DisplayParticleCommandSyntax {
    fun parse(args: Array<String>): ParsedDisplayParticleCommand {
        val separator = args.indexOf("--")
        val base = if (separator < 0) args.toList() else args.take(separator)
        val properties = if (separator < 0) emptyList() else args.drop(separator + 1)
        require(base.size in setOf(3, 6, 11, 12)) { "引数の数が不正です" }
        require(properties.none { it == "--" }) { "-- は1回だけ指定してください" }
        val position = if (base.size >= 6) RawPosition(base[3], base[4], base[5]) else null
        val delta = if (base.size >= 11) DisplayEffectVector3(number(base[6]), number(base[7]), number(base[8])) else DisplayEffectVector3.ZERO
        val speed = if (base.size >= 11) number(base[9]) else 0.0
        val count = if (base.size >= 11) base[10].toIntOrNull() ?: throw IllegalArgumentException("count は整数で指定してください") else 1
        val mode = if (base.size == 12) {
            when (base[11].lowercase()) {
                "normal" -> DisplayParticleVisibilityMode.NORMAL
                "force" -> DisplayParticleVisibilityMode.FORCE
                else -> throw IllegalArgumentException("表示モードは normal または force です")
            }
        } else DisplayParticleVisibilityMode.NORMAL
        // 組み込みプリセットはCC独自表現であり、namespace省略時もその所有元を明示します。
        val patternId = namespaced(base[0])
        val motionId = namespaced(base[1])
        val collision = runCatching { DisplayParticleCollisionMode.valueOf(base[2].uppercase()) }
            .getOrElse { throw IllegalArgumentException("衝突処理は none/remove/stop/slide/bounce です") }
        val values = parseProperties(properties)
        return ParsedDisplayParticleCommand(
            patternId, motionId, collision, position, delta, speed, count, mode,
            DisplayParticleMotionProperties(
                initialVelocity = values.vector("motion.initial-velocity"),
                acceleration = values.vector("motion.acceleration"),
                velocityRetention = values.propertyNumber("motion.retention"),
                turbulenceStrength = values.propertyNumber("motion.turbulence"),
                turbulenceFrequency = values.propertyNumber("motion.frequency"),
                radialSpeed = values.propertyNumber("motion.radial-speed"),
                spawnRadius = values.propertyNumber("motion.spawn-radius"),
                orbitSpeed = values.propertyNumber("motion.orbit-speed"),
                radialPull = values.propertyNumber("motion.radial-pull"),
                attraction = values.propertyNumber("motion.attraction"),
                maxSpeed = values.propertyNumber("motion.max-speed")
            ),
            DisplayParticleCollisionProperties(values.propertyNumber("collision.restitution"))
        )
    }

    private fun parseProperties(arguments: List<String>): Map<String, String> {
        val supported = setOf(
            "motion.initial-velocity", "motion.acceleration", "motion.retention", "motion.turbulence",
            "motion.frequency", "motion.radial-speed", "motion.spawn-radius", "motion.orbit-speed",
            "motion.radial-pull", "motion.attraction", "motion.max-speed", "collision.restitution"
        )
        val values = linkedMapOf<String, String>()
        arguments.forEach { argument ->
            val split = argument.split('=', limit = 2)
            require(split.size == 2 && split[0] in supported && split[1].isNotBlank()) { "未対応のプロパティです: $argument" }
            require(values.put(split[0], split[1]) == null) { "プロパティが重複しています: ${split[0]}" }
        }
        return values
    }

    private fun Map<String, String>.propertyNumber(key: String): Double? = get(key)?.let { number(it) }

    private fun Map<String, String>.vector(key: String): DisplayEffectVector3? = get(key)?.let { raw ->
        val values = raw.split(',')
        require(values.size == 3) { "$key はx,y,z形式で指定してください" }
        DisplayEffectVector3(number(values[0]), number(values[1]), number(values[2]))
    }

    private fun namespaced(value: String): String = if (':' in value) value else "cc:$value"

    private fun number(raw: String): Double = raw.toDoubleOrNull()?.takeIf(Double::isFinite)
        ?: throw IllegalArgumentException("数値の指定が不正です: $raw")
}
