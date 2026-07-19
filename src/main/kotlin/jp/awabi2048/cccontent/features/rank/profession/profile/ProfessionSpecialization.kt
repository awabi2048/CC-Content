package jp.awabi2048.cccontent.features.rank.profession.profile

import jp.awabi2048.cccontent.features.rank.profession.Profession

enum class ProfessionSpecialization(
    val id: String,
    val profession: Profession
) {
    TUNNEL_MINING("tunnel_mining", Profession.MINER),
    PRECISION_MINING("precision_mining", Profession.MINER),
    FELLING("felling", Profession.LUMBERJACK),
    WOOD_UTILIZATION("wood_utilization", Profession.LUMBERJACK),
    CULTIVATION("cultivation", Profession.FARMER),
    WILD_GATHERING("wild_gathering", Profession.FARMER),
    ROD_HANDLING("rod_handling", Profession.FISHER),
    FISHING_GROUND_KNOWLEDGE("fishing_ground_knowledge", Profession.FISHER),
    FERMENTATION("fermentation", Profession.BREWER),
    DISTILLATION_AGING("distillation_aging", Profession.BREWER),
    BULK_COOKING("bulk_cooking", Profession.COOK),
    PRECISION_COOKING("precision_cooking", Profession.COOK);

    companion object {
        const val UNLOCK_LEVEL = 15

        fun fromId(profession: Profession, id: String?): ProfessionSpecialization? {
            if (id == null) return null
            return entries.firstOrNull { it.profession == profession && it.id == id }
        }

        fun optionsFor(profession: Profession): List<ProfessionSpecialization> =
            entries.filter { it.profession == profession }
    }
}
