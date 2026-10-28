package id.controlengineer.elcalc.core

import kotlin.math.abs

/**
* Kalkulator Ketidakseimbangan Tegangan / Arus 3 Fasa berdasarkan definisi NEMA MG 1.
* Sumber: U.S. DOE Motor Systems Tip Sheet #7 (NEMA MG 1-2011 Fig. 20-2),
* https://www.energy.gov/sites/prod/files/2014/04/f15/eliminate_voltage_unbalanced_motor_systemts7.pdf
*/
enum class UnbalanceQuantity { VOLTAGE, CURRENT }
enum class UnbalanceStatus {
  OK_WITHIN_1_PERCENT, WARNING_EXCEEDS_1_PERCENT, CRITICAL_EXCEEDS_5_PERCENT,
  CURRENT_NORMAL_WITHIN_10_PERCENT, CURRENT_WARNING_EXCEEDS_10_PERCENT,
}
enum class PhaseUnbalanceError { INVALID_OR_NON_POSITIVE_VALUES }

data class PhaseUnbalanceResult(
  val quantity: UnbalanceQuantity, val v1: Double, val v2: Double, val v3: Double,
  val average: Double, val dev1: Double, val dev2: Double, val dev3: Double,
  val maxDeviation: Double, val unbalancePercent: Double, val status: UnbalanceStatus,
  val extraTempRisePercent: Double?,
)

sealed class PhaseUnbalanceOutcome {
  data class Success(val result: PhaseUnbalanceResult) : PhaseUnbalanceOutcome()
  data class Failure(val error: PhaseUnbalanceError) : PhaseUnbalanceOutcome()
}

object PhaseUnbalanceCalculator {
  const val VOLTAGE_WARNING_THRESHOLD_PCT = 1.0
  const val VOLTAGE_CRITICAL_THRESHOLD_PCT = 5.0
  const val CURRENT_WARNING_THRESHOLD_PCT = 10.0

  fun calculate(quantity: UnbalanceQuantity = UnbalanceQuantity.VOLTAGE, v1: Double?, v2: Double?, v3: Double?): PhaseUnbalanceOutcome {
    if (v1 == null || v2 == null || v3 == null || !v1.isFinite() || !v2.isFinite() || !v3.isFinite() || v1 <= 0.0 || v2 <= 0.0 || v3 <= 0.0) {
      return PhaseUnbalanceOutcome.Failure(PhaseUnbalanceError.INVALID_OR_NON_POSITIVE_VALUES)
    }
    val avg = (v1 + v2 + v3) / 3.0
    val d1 = abs(v1 - avg); val d2 = abs(v2 - avg); val d3 = abs(v3 - avg)
    val maxDev = maxOf(d1, d2, d3)
    val unbalancePct = (maxDev / avg) * 100.0
    val status = when (quantity) {
      UnbalanceQuantity.VOLTAGE -> when {
        unbalancePct > VOLTAGE_CRITICAL_THRESHOLD_PCT + 1e-9 -> UnbalanceStatus.CRITICAL_EXCEEDS_5_PERCENT
        unbalancePct > VOLTAGE_WARNING_THRESHOLD_PCT + 1e-9 -> UnbalanceStatus.WARNING_EXCEEDS_1_PERCENT
        else -> UnbalanceStatus.OK_WITHIN_1_PERCENT
      }
      UnbalanceQuantity.CURRENT -> if (unbalancePct > CURRENT_WARNING_THRESHOLD_PCT + 1e-9) UnbalanceStatus.CURRENT_WARNING_EXCEEDS_10_PERCENT else UnbalanceStatus.CURRENT_NORMAL_WITHIN_10_PERCENT
    }
    val extraTempRisePct = if (quantity == UnbalanceQuantity.VOLTAGE) 2.0 * unbalancePct * unbalancePct else null
    return PhaseUnbalanceOutcome.Success(PhaseUnbalanceResult(quantity, v1, v2, v3, avg, d1, d2, d3, maxDev, unbalancePct, status, extraTempRisePct))
  }
}
