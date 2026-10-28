package id.controlengineer.elcalc.core

import kotlin.math.sqrt

/**
* Kalkulator Motor Induksi 3 Fasa & Komponen Starter (DOL dan Star-Delta).
* Sumber:
* - 1 HP = 745.7 W (NIST SP 811 Appendix B.9: https://www.nist.gov/pml/special-publication-811/nist-guide-si-appendix-b-conversion-factors/nist-guide-si-appendix-b9).
* - In = (P_kW * 1000) / (sqrt(3) * V * cos(phi) * eta).
* - Faktor DOL & Star-Delta (https://industrialmonitordirect.com/blogs/knowledgebase/star-delta-contactor-sizing-iec-5833-formula-guide
*   & https://stoklink.com/blogs/technical/how-to-select-set-overload-relay-motor -> PERLU CEK MANUAL ke IEC 60947-4-1).
*/
enum class MotorPowerUnit { KW, HP }
enum class StarterMethod { DOL, STAR_DELTA }
enum class MotorError { INVALID_POWER, INVALID_VOLTAGE, INVALID_COS_PHI, INVALID_EFFICIENCY, INVALID_START_MULTIPLIER }

data class MotorStarterResult(
  val inputPowerValue: Double, val inputPowerUnit: MotorPowerUnit, val powerKw: Double, val powerHp: Double,
  val voltageV: Double, val cosPhi: Double, val efficiency: Double, val starterMethod: StarterMethod,
  val nominalCurrentInA: Double, val dolStartCurrentMultiple: Double, val estimatedDolStartCurrentA: Double,
  val estimatedActualStartCurrentA: Double, val mainContactorMinCurrentA: Double, val mainContactorStdRatingA: Int?,
  val deltaContactorMinCurrentA: Double?, val deltaContactorStdRatingA: Int?,
  val starContactorMinCurrentA: Double?, val starContactorStdRatingA: Int?,
  val torSettingCurrentA: Double, val torRangeMinA: Double, val torRangeMaxA: Double,
  val breakerMinRangeA: Double, val breakerMaxRangeA: Double,
  val recommendedBreakerStdMinA: Int?, val recommendedBreakerStdMaxA: Int?,
)

sealed class MotorOutcome {
  data class Success(val result: MotorStarterResult) : MotorOutcome()
  data class Failure(val error: MotorError) : MotorOutcome()
}

object MotorCalculator {
  const val WATTS_PER_HP = 745.7
  const val STAR_DELTA_MAIN_FACTOR = 0.58
  const val STAR_DELTA_STAR_FACTOR = 0.33
  val STANDARD_CONTACTOR_AC3_AMPS: List<Int> = listOf(9, 12, 18, 25, 32, 40, 50, 65, 80, 95, 115, 150, 185, 225, 265, 330, 400, 500, 630)
  val STANDARD_BREAKER_AMPS: List<Int> = listOf(6, 10, 16, 20, 25, 32, 40, 50, 63, 80, 100, 125, 160, 200, 250, 315, 400, 500, 630)

  fun hpToKw(hp: Double): Double = (hp * WATTS_PER_HP) / 1000.0
  fun kwToHp(kw: Double): Double = (kw * 1000.0) / WATTS_PER_HP
  fun nextStandardContactor(requiredAmps: Double): Int? = STANDARD_CONTACTOR_AC3_AMPS.firstOrNull { it.toDouble() >= requiredAmps - 1e-9 }
  fun nextStandardBreaker(requiredAmps: Double): Int? = STANDARD_BREAKER_AMPS.firstOrNull { it.toDouble() >= requiredAmps - 1e-9 }

  fun calculate(
    powerValue: Double?, powerUnit: MotorPowerUnit = MotorPowerUnit.KW, voltageV: Double?,
    cosPhi: Double?, efficiency: Double?, starterMethod: StarterMethod = StarterMethod.DOL, dolStartMultiple: Double? = 6.0,
  ): MotorOutcome {
    if (powerValue == null || !powerValue.isFinite() || powerValue <= 0.0) return MotorOutcome.Failure(MotorError.INVALID_POWER)
    if (voltageV == null || !voltageV.isFinite() || voltageV <= 0.0) return MotorOutcome.Failure(MotorError.INVALID_VOLTAGE)
    if (cosPhi == null || !cosPhi.isFinite() || cosPhi <= 0.0 || cosPhi > 1.0) return MotorOutcome.Failure(MotorError.INVALID_COS_PHI)
    if (efficiency == null || !efficiency.isFinite() || efficiency <= 0.0 || efficiency > 1.0) return MotorOutcome.Failure(MotorError.INVALID_EFFICIENCY)
    if (dolStartMultiple == null || !dolStartMultiple.isFinite() || dolStartMultiple < 1.0 || dolStartMultiple > 15.0) return MotorOutcome.Failure(MotorError.INVALID_START_MULTIPLIER)

    val powerKw = if (powerUnit == MotorPowerUnit.KW) powerValue else hpToKw(powerValue)
    val powerHp = if (powerUnit == MotorPowerUnit.KW) kwToHp(powerValue) else powerValue
    val inAmps = (powerKw * 1000.0) / (sqrt(3.0) * voltageV * cosPhi * efficiency)
    val dolStartAmps = inAmps * dolStartMultiple
    val mainMinA = if (starterMethod == StarterMethod.DOL) inAmps else inAmps * STAR_DELTA_MAIN_FACTOR
    val deltaMinA = if (starterMethod == StarterMethod.DOL) null else inAmps * STAR_DELTA_MAIN_FACTOR
    val starMinA = if (starterMethod == StarterMethod.DOL) null else inAmps * STAR_DELTA_STAR_FACTOR
    val torSetA = if (starterMethod == StarterMethod.DOL) inAmps else inAmps * STAR_DELTA_MAIN_FACTOR
    val actualStartAmps = if (starterMethod == StarterMethod.DOL) dolStartAmps else dolStartAmps / 3.0
    val brkMin = inAmps * 1.25; val brkMax = inAmps * 2.00

    return MotorOutcome.Success(
      MotorStarterResult(
        powerValue, powerUnit, powerKw, powerHp, voltageV, cosPhi, efficiency, starterMethod,
        inAmps, dolStartMultiple, dolStartAmps, actualStartAmps,
        mainMinA, nextStandardContactor(mainMinA), deltaMinA, deltaMinA?.let(::nextStandardContactor),
        starMinA, starMinA?.let(::nextStandardContactor), torSetA, torSetA * 0.90, torSetA * 1.05,
        brkMin, brkMax, nextStandardBreaker(brkMin), nextStandardBreaker(brkMax),
      ),
    )
  }
}
