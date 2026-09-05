package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.CCContent
import jp.awabi2048.cccontent.util.SystemEntityMarker
import org.bukkit.entity.Entity
import org.bukkit.plugin.Plugin

/** CC-Content内で生成するシステム管理Entityを共通台帳へ登録します。 */
internal fun Entity.markAsCcContentSystemEntity(owner: Plugin = CCContent.instance) {
    SystemEntityMarker.mark(this, owner)
}
