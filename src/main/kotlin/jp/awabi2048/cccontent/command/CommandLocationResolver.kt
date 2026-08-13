package jp.awabi2048.cccontent.command

import org.bukkit.Location
import org.bukkit.command.BlockCommandSender
import org.bukkit.command.CommandSender
import org.bukkit.entity.Entity
import org.bukkit.util.Vector

/** 複数の管理コマンドで、バニラ同様の絶対・相対・ローカル座標を一貫して解決します。 */
internal object CommandLocationResolver {
    fun senderLocation(sender: CommandSender): Location? = when (sender) {
        is Entity -> sender.location
        is BlockCommandSender -> sender.block.location.add(0.5, 0.0, 0.5)
        else -> null
    }

    fun resolve(sender: CommandSender, xArg: String, yArg: String, zArg: String): Location {
        val anchor = senderLocation(sender)
            ?: throw IllegalArgumentException("このコマンドは位置を持つ実行者のみ使用できます")
        val args = listOf(xArg, yArg, zArg)
        return if (args.any { it.startsWith("^") }) {
            require(args.all { it.startsWith("^") }) { "ローカル座標(^)と通常座標は混在できません" }
            resolveLocal(anchor, xArg, yArg, zArg)
        } else {
            Location(
                anchor.world,
                resolveWorldCoordinate(anchor.x, xArg),
                resolveWorldCoordinate(anchor.y, yArg),
                resolveWorldCoordinate(anchor.z, zArg),
                anchor.yaw,
                anchor.pitch
            )
        }
    }

    private fun resolveWorldCoordinate(base: Double, raw: String): Double =
        if (raw.startsWith("~")) {
            base + if (raw == "~") 0.0 else parseNumber(raw.substring(1), raw)
        } else {
            parseNumber(raw, raw)
        }

    private fun resolveLocal(anchor: Location, xArg: String, yArg: String, zArg: String): Location {
        val x = parseLocalComponent(xArg)
        val y = parseLocalComponent(yArg)
        val z = parseLocalComponent(zArg)
        val forward = anchor.direction.normalize()
        var left = Vector(0, 1, 0).crossProduct(forward).normalize()
        if (left.lengthSquared() == 0.0) left = Vector(1, 0, 0)
        val up = forward.clone().crossProduct(left).normalize()
        return anchor.clone().add(left.multiply(x).add(up.multiply(y)).add(forward.multiply(z)))
    }

    private fun parseLocalComponent(raw: String): Double {
        require(raw.startsWith("^")) { "ローカル座標は ^ を使用してください: $raw" }
        return if (raw == "^") 0.0 else parseNumber(raw.substring(1), raw)
    }

    private fun parseNumber(number: String, source: String): Double =
        number.toDoubleOrNull()?.takeIf(Double::isFinite)
            ?: throw IllegalArgumentException("座標の指定が不正です: $source")
}
