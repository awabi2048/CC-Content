package jp.awabi2048.cccontent.localization

import java.nio.file.Path

/** Declares the finite language keys produced dynamically by one feature. */
fun interface LanguageKeyRequirementProvider {
    fun requiredKeys(resourcesRoot: Path): List<LanguageKeyRequirement>
}
