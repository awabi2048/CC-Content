package jp.awabi2048.cccontent.mob.ability

class PowerTridentThrowAbility(
    override val id: String,
    throwCooldownTicks: Long = DEFAULT_THROW_COOLDOWN_TICKS,
    triggerMinDistance: Double = DEFAULT_TRIGGER_MIN_DISTANCE,
    triggerMaxDistance: Double = DEFAULT_TRIGGER_MAX_DISTANCE,
    throwSpeed: Double = DEFAULT_THROW_SPEED,
    gravityCompensationPerBlock: Double = DEFAULT_GRAVITY_COMPENSATION_PER_BLOCK,
    spread: Double = DEFAULT_SPREAD,
    knockbackMultiplier: Double = DEFAULT_KNOCKBACK_MULTIPLIER,
    homingConfig: MobShootUtil.HomingConfig? = null
) : TridentThrowAbility(
    id = id,
    throwCooldownTicks = throwCooldownTicks,
    triggerMinDistance = triggerMinDistance,
    triggerMaxDistance = triggerMaxDistance,
    throwSpeed = throwSpeed,
    gravityCompensationPerBlock = gravityCompensationPerBlock,
    spread = spread,
    knockbackMultiplier = knockbackMultiplier,
    homingConfig = homingConfig
)
