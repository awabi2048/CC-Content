package jp.awabi2048.cccontent.features.rank.skill

data class SkillEffect(
    val type: String,
    val params: Map<String, Any>,
    val evaluationMode: EvaluationMode = EvaluationMode.CACHED,
    val combineRule: CombineRule = CombineRule.ADD
) {
    fun getParam(key: String, defaultValue: Any? = null): Any? {
        return params[key] ?: defaultValue
    }

    fun getIntParam(key: String, defaultValue: Int = 0): Int {
        return when (val value = params[key]) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull() ?: defaultValue
            else -> defaultValue
        }
    }

    fun getDoubleParam(key: String, defaultValue: Double = 0.0): Double {
        return when (val value = params[key]) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull() ?: defaultValue
            else -> defaultValue
        }
    }

    fun getStringParam(key: String, defaultValue: String = ""): String {
        return when (val value = params[key]) {
            is String -> value
            else -> defaultValue
        }
    }

    fun getStringListParam(key: String): List<String> {
        return when (val value = params[key]) {
            is List<*> -> value.filterIsInstance<String>()
            else -> emptyList()
        }
    }

    fun getBooleanParam(key: String, defaultValue: Boolean = false): Boolean {
        return when (val value = params[key]) {
            is Boolean -> value
            is String -> value.toBooleanStrictOrNull() ?: defaultValue
            is Number -> value.toInt() != 0
            else -> defaultValue
        }
    }
}
