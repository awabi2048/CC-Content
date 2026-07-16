package jp.awabi2048.cccontent.features.brewery.garden

data class GardenPlantState(
    val plantId: String,
    var stage: Int,
    val plantedAtMillis: Long,
    var nextGrowthAtMillis: Long
)

object GardenGrowth {
    fun advance(plant: GardenPlant, state: GardenPlantState, nowMillis: Long): Boolean {
        var changed = false
        while (state.stage < plant.matureStage.id && nowMillis >= state.nextGrowthAtMillis) {
            state.stage++
            changed = true
            state.nextGrowthAtMillis = if (state.stage == plant.matureStage.id) Long.MAX_VALUE else state.nextGrowthAtMillis + plant.stages[state.stage].growthSeconds * 1000L
        }
        return changed
    }

    fun regrow(plant: GardenPlant, nowMillis: Long): GardenPlantState = GardenPlantState(
        plant.id,
        plant.regrowthStage,
        nowMillis,
        nowMillis + plant.stages[plant.regrowthStage].growthSeconds * 1000L
    )
}
