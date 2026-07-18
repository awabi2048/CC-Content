package jp.awabi2048.cccontent.features.rank.profession.profile

data class ProfessionCycleStatistics(
    var validActions: Long = 0,
    var specialistActions: Long = 0,
    var highQualityActions: Long = 0,
    var firstDiscoveries: Long = 0
)

data class ProfessionPrestigeRecord(
    val professionId: String,
    val specializationId: String?,
    val completedAtEpochMillis: Long,
    val cycleNumber: Int,
    val representativeStatistic: Long
)
