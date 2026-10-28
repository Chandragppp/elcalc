package id.controlengineer.elcalc.core

enum class ProLabelSource { LIFETIME, REWARDED, TRIAL, NONE }

/** Logika murni status Pro: beli sekali seumur hidup, trial 3 hari sejak buka pertama, atau rewarded 24 jam. */
object ProEntitlement {
  const val HOUR_MS: Long = 3_600_000L
  const val DAY_MS: Long = 24L * HOUR_MS
  const val REWARD_DURATION_MS: Long = DAY_MS
  const val TRIAL_DURATION_MS: Long = 3L * DAY_MS

  fun clampRewardedUntil(rewardedUntilMillis: Long, nowMillis: Long): Long {
    if (rewardedUntilMillis <= 0L) return 0L
    return rewardedUntilMillis.coerceAtMost(nowMillis + REWARD_DURATION_MS)
  }

  fun trialRemainingMillis(trialStartMillis: Long, nowMillis: Long): Long {
    if (trialStartMillis <= 0L) return 0L
    val elapsed = nowMillis - trialStartMillis
    if (elapsed < 0L || elapsed >= TRIAL_DURATION_MS) return 0L
    return TRIAL_DURATION_MS - elapsed
  }

  fun isTrialActive(trialStartMillis: Long, nowMillis: Long): Boolean =
    trialRemainingMillis(trialStartMillis, nowMillis) > 0L

  fun isTrialExpired(trialStartMillis: Long, nowMillis: Long): Boolean =
    trialStartMillis > 0L && (nowMillis - trialStartMillis) >= TRIAL_DURATION_MS

  fun isProActive(lifetime: Boolean, rewardedUntilMillis: Long, nowMillis: Long, trialStartMillis: Long = 0L): Boolean {
    if (lifetime) return true
    if (clampRewardedUntil(rewardedUntilMillis, nowMillis) > nowMillis) return true
    return isTrialActive(trialStartMillis, nowMillis)
  }

  fun labelSource(lifetime: Boolean, rewardedUntilMillis: Long, trialStartMillis: Long, nowMillis: Long): ProLabelSource = when {
    lifetime -> ProLabelSource.LIFETIME
    clampRewardedUntil(rewardedUntilMillis, nowMillis) > nowMillis -> ProLabelSource.REWARDED
    isTrialActive(trialStartMillis, nowMillis) -> ProLabelSource.TRIAL
    else -> ProLabelSource.NONE
  }

  fun nextExpiryMillis(lifetime: Boolean, rewardedUntilMillis: Long, trialStartMillis: Long, nowMillis: Long): Long {
    if (lifetime) return 0L
    val candidates = mutableListOf<Long>()
    val clampedReward = clampRewardedUntil(rewardedUntilMillis, nowMillis)
    if (clampedReward > nowMillis) candidates.add(clampedReward)
    val trialEnd = trialStartMillis + TRIAL_DURATION_MS
    if (trialStartMillis > 0L && nowMillis in trialStartMillis until trialEnd) candidates.add(trialEnd)
    return candidates.minOrNull() ?: 0L
  }

  fun trialDaysLeft(remainingMillis: Long): Int =
    if (remainingMillis <= 0L) 0 else ((remainingMillis + DAY_MS - 1L) / DAY_MS).toInt()

  fun trialHoursLeft(remainingMillis: Long): Int =
    if (remainingMillis <= 0L) 0 else ((remainingMillis + HOUR_MS - 1L) / HOUR_MS).toInt().coerceAtLeast(1)
}
