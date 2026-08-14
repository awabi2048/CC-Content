package jp.awabi2048.cccontent.util

import com.awabi2048.ccsystem.api.localization.LocalizationCatalogContract
import com.awabi2048.ccsystem.api.localization.LocalizationKey

/**
 * 設定・レジストリなど、コンパイル時に値が確定しない識別子を生成済みcatalogへ接続します。
 *
 * 通常の呼び出しは生成済み [LocalizationKey] を直接使い、この境界は外部データまたは
 * 有限レジストリの初期化時検証にだけ使用します。接頭辞も同時に検証して子機能間の越境を防ぎます。
 */
object ContentLocalizationKeys {
    fun text(key: String, vararg allowedPrefixes: String): LocalizationKey<String> {
        requireDomain(key, allowedPrefixes)
        return LocalizationCatalogContract.resolveText(key)
    }

    fun textList(key: String, vararg allowedPrefixes: String): LocalizationKey<List<String>> {
        requireDomain(key, allowedPrefixes)
        return LocalizationCatalogContract.resolveTextList(key)
    }

    /** 仕様上任意の補足文だけに使用し、存在する場合の型不一致は例外にします。 */
    fun optionalText(key: String, vararg allowedPrefixes: String): LocalizationKey<String>? {
        requireDomain(key, allowedPrefixes)
        return if (LocalizationCatalogContract.contains(key)) LocalizationCatalogContract.resolveText(key) else null
    }

    fun hasText(key: String, vararg allowedPrefixes: String): Boolean =
        optionalText(key, *allowedPrefixes) != null

    private fun requireDomain(key: String, allowedPrefixes: Array<out String>) {
        require(allowedPrefixes.isNotEmpty()) { "言語キードメインを指定してください" }
        require(allowedPrefixes.any(key::startsWith)) {
            "許可されていない言語キードメインです: key=$key, allowed=${allowedPrefixes.joinToString()}"
        }
    }
}
