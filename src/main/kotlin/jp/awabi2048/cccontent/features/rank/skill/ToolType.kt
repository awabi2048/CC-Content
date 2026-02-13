package jp.awabi2048.cccontent.features.rank.skill

import org.bukkit.Material

/**
 * 職業ツールタイプ
 */
enum class ToolType(val materials: Set<Material>) {
    PICKAXE(setOf(
        Material.WOODEN_PICKAXE,
        Material.STONE_PICKAXE,
        Material.IRON_PICKAXE,
        Material.GOLDEN_PICKAXE,
        Material.DIAMOND_PICKAXE,
        Material.NETHERITE_PICKAXE
    )),

    AXE(setOf(
        Material.WOODEN_AXE,
        Material.STONE_AXE,
        Material.IRON_AXE,
        Material.GOLDEN_AXE,
        Material.DIAMOND_AXE,
        Material.NETHERITE_AXE
    )),

    SHOVEL(setOf(
        Material.WOODEN_SHOVEL,
        Material.STONE_SHOVEL,
        Material.IRON_SHOVEL,
        Material.GOLDEN_SHOVEL,
        Material.DIAMOND_SHOVEL,
        Material.NETHERITE_SHOVEL
    )),

    HOE(setOf(
        Material.WOODEN_HOE,
        Material.STONE_HOE,
        Material.IRON_HOE,
        Material.GOLDEN_HOE,
        Material.DIAMOND_HOE,
        Material.NETHERITE_HOE
    )),

    SWORD(setOf(
        Material.WOODEN_SWORD,
        Material.STONE_SWORD,
        Material.IRON_SWORD,
        Material.GOLDEN_SWORD,
        Material.DIAMOND_SWORD,
        Material.NETHERITE_SWORD
    )),

    SHEARS(setOf(Material.SHEARS));

    companion object {
        /**
         * MaterialからToolTypeを取得
         */
        fun fromMaterial(material: Material): ToolType? {
            return values().find { it.materials.contains(material) }
        }

        /**
         * プレイヤーがアイテムを持っているかチェック
         */
        fun isHoldingTool(material: Material): Boolean {
            return fromMaterial(material) != null
        }
    }
}
