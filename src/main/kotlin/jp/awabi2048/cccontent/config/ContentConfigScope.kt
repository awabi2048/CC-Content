package jp.awabi2048.cccontent.config

/** CC-Content設定を、停止単位となるfeatureへ分類する。 */
object ContentConfigScope {
    @JvmStatic
    fun featureIdForResource(resourcePath: String): String {
        val relative = resourcePath.replace('\\', '/').removePrefix("config/")
        val first = relative.substringBefore('/')
        return when (first) {
            "core.yml" -> "core"
            "custom_item" -> "custom_items"
            "npc" -> "oage_shrine"
            "ingredient_definition.yml", "mob_definition.yml" -> "shared"
            else -> first.removeSuffix(".yml")
        }
    }

    @JvmStatic
    fun featureIdFromValidationError(error: String): String {
        val normalized = error.replace('\\', '/')
        val marker = "/config/"
        val index = normalized.indexOf(marker)
        if (index < 0) return "core"
        val resource = normalized.substring(index + marker.length).lineSequence().first().trim()
        return featureIdForResource("config/$resource")
    }
}
