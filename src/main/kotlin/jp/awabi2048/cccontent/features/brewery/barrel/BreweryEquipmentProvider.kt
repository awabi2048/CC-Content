package jp.awabi2048.cccontent.features.brewery.barrel

import jp.awabi2048.cccontent.features.brewery.BrewerySettings
import jp.awabi2048.cccontent.features.brewery.model.BreweryLocationKey
import jp.awabi2048.cccontent.features.processing.ProcessingEquipment
import jp.awabi2048.cccontent.features.processing.ProcessingEquipmentCapability
import jp.awabi2048.cccontent.features.processing.ProcessingEquipmentProvider
import jp.awabi2048.cccontent.features.processing.ProcessingLocationKey
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.Barrel
import org.bukkit.block.Sign
import org.bukkit.block.BrewingStand
import org.bukkit.block.data.type.WallSign
import org.bukkit.block.sign.Side

/**
 * Breweryの物理設備を汎用加工設備へ変換するアダプターです。
 *
 * Brewery固有の樽台帳・発酵用看板・バニラ醸造台の判定はここへ閉じ込め、Cooking側が
 * Breweryのデータ構造を参照しないようにします。これにより、同じ能力を別設備へ割り当てる
 * ときも利用側のレシピと借用処理はそのまま再利用できます。
 */
class BreweryEquipmentProvider(
    private val settingsProvider: () -> BrewerySettings,
    private val barrelRegistry: BreweryBarrelRegistry,
) : ProcessingEquipmentProvider {
    override fun findAt(location: ProcessingLocationKey): ProcessingEquipment? {
        val block = location.blockIfLoaded() ?: return null
        fermentationBarrelFor(block)?.let { barrel ->
            val sign = fermentationSignFor(barrel) ?: return null
            val barrelKey = ProcessingLocationKey.from(barrel)
            return ProcessingEquipment(
                id = "brewery:fermentation_barrel",
                canonicalLocation = barrelKey,
                members = setOf(barrelKey, ProcessingLocationKey.from(sign)),
                capabilities = setOf(ProcessingEquipmentCapability.FERMENTATION),
            )
        }

        if (block.state is BrewingStand) {
            val key = ProcessingLocationKey.from(block)
            return ProcessingEquipment(
                id = "brewery:brewing_stand",
                canonicalLocation = key,
                members = setOf(key),
                capabilities = setOf(ProcessingEquipmentCapability.DISTILLATION),
            )
        }

        barrelRegistry.findByBlock(BreweryLocationKey.fromBlock(block))?.let { barrel ->
            val members = barrel.members.map { member ->
                ProcessingLocationKey(member.worldUid, member.x, member.y, member.z)
            }.toSet()
            val canonical = ProcessingLocationKey(
                barrel.origin.worldUid,
                barrel.origin.x,
                barrel.origin.y,
                barrel.origin.z,
            )
            return ProcessingEquipment(
                id = "brewery:aging_barrel:${barrel.id}",
                canonicalLocation = canonical,
                members = members,
                capabilities = setOf(ProcessingEquipmentCapability.AGING),
            )
        }
        return null
    }

    /** BreweryControllerのGUI入口も同じ看板判定を使えるように公開します。 */
    fun fermentationBarrelFor(block: Block): Block? =
        fermentationSignFor(block)?.let { attachedVanillaBarrel(it) }
            ?: if (block.state is Barrel) fermentationSignFor(block)?.let { block } else null

    /** 看板編集時に、看板の裏側へ接続されたバニラ樽を検証するための入口です。 */
    fun vanillaBarrelForSign(signBlock: Block): Block? = attachedVanillaBarrel(signBlock)

    private fun fermentationSignFor(block: Block): Block? {
        if (block.state is Barrel) {
            return HORIZONTAL_FACES.asSequence()
                .map(block::getRelative)
                .firstOrNull { sign -> attachedVanillaBarrel(sign) == block && isFermentationSign(sign) }
        }
        return block.takeIf(::isFermentationSign)
            ?.takeIf { attachedVanillaBarrel(it) != null }
    }

    private fun attachedVanillaBarrel(signBlock: Block): Block? {
        val wallSign = signBlock.blockData as? WallSign ?: return null
        return signBlock.getRelative(wallSign.facing.oppositeFace)
            .takeIf { it.state is Barrel }
    }

    private fun isFermentationSign(block: Block): Boolean {
        val sign = block.state as? Sign ?: return false
        val line = PlainTextComponentSerializer.plainText()
            .serialize(sign.getSide(Side.FRONT).line(0))
            .trim()
        return line.equals(settingsProvider().fermentationSignKeyword, ignoreCase = true)
    }

    private companion object {
        val HORIZONTAL_FACES = listOf(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST)
    }
}
