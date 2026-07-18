package jp.awabi2048.cccontent.features.rank.profession.profile

import jp.awabi2048.cccontent.features.rank.profession.Profession

enum class FishingInformationMode {
    BRIEF,
    DETAIL,
    HIDDEN
}

data class ProfessionFeatureToggles(
    var batchProcessingEnabled: Boolean = false,
    var leafCleanupEnabled: Boolean = true,
    var automaticReplantEnabled: Boolean = true,
    var areaTillingEnabled: Boolean = false,
    var areaHarvestEnabled: Boolean = false,
    var fishingInformationMode: FishingInformationMode = FishingInformationMode.BRIEF
) {
    companion object {
        fun defaultsFor(profession: Profession): ProfessionFeatureToggles = when (profession) {
            Profession.MINER -> ProfessionFeatureToggles(
                leafCleanupEnabled = false,
                automaticReplantEnabled = false
            )
            Profession.LUMBERJACK -> ProfessionFeatureToggles()
            Profession.FARMER -> ProfessionFeatureToggles(
                leafCleanupEnabled = false
            )
            Profession.FISHER -> ProfessionFeatureToggles(
                leafCleanupEnabled = false,
                automaticReplantEnabled = false
            )
            Profession.BREWER, Profession.COOK -> ProfessionFeatureToggles(
                leafCleanupEnabled = false,
                automaticReplantEnabled = false
            )
            else -> ProfessionFeatureToggles(
                leafCleanupEnabled = false,
                automaticReplantEnabled = false
            )
        }
    }
}
