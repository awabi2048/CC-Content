package jp.awabi2048.cccontent.util

import com.awabi2048.ccsystem.CCSystem
import jp.awabi2048.cccontent.CCContent
import org.bukkit.entity.Entity
import org.bukkit.plugin.Plugin

/**
 * CC-Contentが生成する全種類のEntityへ共通のシステムマーカーを付けます。
 * ownerを省略できるため、静的マネージャーからの生成も同じ契約へ統一できます。
 */
object SystemEntityMarker {
    fun mark(entity: Entity, owner: Plugin = CCContent.instance) {
        CCSystem.getAPI().getSystemEntityRegistry().mark(entity, owner)
    }
}
