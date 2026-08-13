package jp.awabi2048.cccontent.command

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.displayeffect.DisplayEffectStartRejection
import com.awabi2048.ccsystem.api.displayeffect.DisplayEffectStartResult
import com.awabi2048.ccsystem.api.displayeffect.DisplayEffectVector3
import com.awabi2048.ccsystem.api.displayeffect.VoxelParticleEmissionRequest
import com.awabi2048.ccsystem.api.displayeffect.VoxelParticlePatternId
import com.awabi2048.ccsystem.api.displayeffect.VoxelParticleVisibilityMode
import org.bukkit.command.CommandSender
import org.bukkit.plugin.Plugin

/** `/ccc particle` を構文解釈し、描画の責務はCC-Systemの公開APIへ委譲します。 */
class VoxelParticleCommand(private val plugin: Plugin) {
    fun execute(sender: CommandSender, args: Array<String>): Boolean {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(ContentManagementI18n.text(sender, "particle.no_permission"))
            return true
        }

        val parsed = runCatching { VoxelParticleCommandSyntax.parse(args) }.getOrElse {
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
            VoxelParticleEmissionRequest(
                patternId = VoxelParticlePatternId(parsed.patternId),
                delta = parsed.delta,
                speed = parsed.speed,
                count = parsed.count,
                visibilityMode = parsed.visibilityMode
            )
        }.getOrElse {
            sender.sendMessage(ContentManagementI18n.text(sender, "particle.invalid_argument", "detail" to it.message))
            return true
        }

        return when (val result = CCSystem.getAPI().getDisplayEffectService().emitVoxelParticles(plugin, location, request)) {
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
            1 -> CCSystem.getAPI().getDisplayEffectService().listVoxelParticlePatterns()
                .map { it.id.value }
                .filter { it.startsWith(args[0], ignoreCase = true) }
            2, 3, 4 -> listOf("~", "~1", "~-1", "^").filter { it.startsWith(args.last()) }
            5, 6, 7, 8 -> listOf("0", "0.1", "0.5").filter { it.startsWith(args.last()) }
            9 -> listOf("1", "2", "4", "8").filter { it.startsWith(args.last()) }
            10 -> listOf("normal", "force").filter { it.startsWith(args.last(), ignoreCase = true) }
            else -> emptyList()
        }
    }

    companion object {
        const val PERMISSION = "cc-content.particle"
    }
}

internal data class RawPosition(val x: String, val y: String, val z: String)

internal data class ParsedVoxelParticleCommand(
    val patternId: String,
    val position: RawPosition?,
    val delta: DisplayEffectVector3,
    val speed: Double,
    val count: Int,
    val visibilityMode: VoxelParticleVisibilityMode
)

/** バニラ `/particle` の省略形と完全形だけを許可し、曖昧な中間形を排除します。 */
internal object VoxelParticleCommandSyntax {
    fun parse(args: Array<String>): ParsedVoxelParticleCommand {
        require(args.size in setOf(1, 4, 9, 10)) { "引数の数が不正です" }
        val position = if (args.size >= 4) RawPosition(args[1], args[2], args[3]) else null
        val delta = if (args.size >= 9) DisplayEffectVector3(number(args[4]), number(args[5]), number(args[6])) else DisplayEffectVector3.ZERO
        val speed = if (args.size >= 9) number(args[7]) else 0.0
        val count = if (args.size >= 9) args[8].toIntOrNull() ?: throw IllegalArgumentException("count は整数で指定してください") else 1
        val mode = if (args.size == 10) {
            when (args[9].lowercase()) {
                "normal" -> VoxelParticleVisibilityMode.NORMAL
                "force" -> VoxelParticleVisibilityMode.FORCE
                else -> throw IllegalArgumentException("表示モードは normal または force です")
            }
        } else VoxelParticleVisibilityMode.NORMAL
        // バニラのResourceLocationと同様に、namespace省略時は minecraft を補います。
        val patternId = if (':' in args[0]) args[0] else "minecraft:${args[0]}"
        return ParsedVoxelParticleCommand(patternId, position, delta, speed, count, mode)
    }

    private fun number(raw: String): Double = raw.toDoubleOrNull()?.takeIf(Double::isFinite)
        ?: throw IllegalArgumentException("数値の指定が不正です: $raw")
}
