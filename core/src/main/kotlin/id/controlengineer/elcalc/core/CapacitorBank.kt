package id.controlengineer.elcalc.core

import kotlin.math.abs
import kotlin.math.sqrt

/**
* Kalkulator Kapasitor Bank (Kompensasi Faktor Daya 3 Fasa).
* Sumber: https://www.electrical-installation.org/enwiki/Theoretical_principles_to_improve_power_factor
* Qc = P * (tan(phi_1) - tan(phi_2)) kVAR; penurunan arus Delta_I = I_1 - I_2, %Delta_I = (1 - cos(phi_1)/cos(phi_2))*100%.
*/
enum class CapacitorBankError {
  INVALID_POWER_KW, INVALID_VOLTAGE, INVALID_INITIAL_COS_PHI,
  INVALID_TARGET_COS_PHI, TARGET_NOT_GREATER_THAN_INITIAL,
}

data class CapacitorStepSuggestion(
  val stepCount: Int, val idealStepKvar: Double, val standardStepKvar: Double,
  val totalInstalledKvar: Double, val nominalStageCurrentA: Double,
)

data class CapacitorBankResult(
  val activePowerKw: Double, val voltageV: Double, val initialCosPhi: Double, val targetCosPhi: Double,
  val tanPhi1: Double, val tanPhi2: Double, val requiredKvar: Double, val initialApparentKva: Double,
  val targetApparentKva: Double, val initialLineCurrentA: Double, val targetLineCurrentA: Double,
  val currentReductionA: Double, val currentReductionPercent: Double, val capacitorBankCurrentA: Double,
  val stepSuggestions: List<CapacitorStepSuggestion>,
)

sealed class CapacitorBankOutcome {
  data class Success(val result: CapacitorBankResult) : CapacitorBankOutcome()
  data class Failure(val error: CapacitorBankError) : CapacitorBankOutcome()
}

object CapacitorBankCalculator {
  val STANDARD_STEP_KVAR: List<Double> = listOf(2.5, 5.0, 7.5, 10.0, 12.5, 15.0, 20.0, 25.0, 30.0, 40.0, 50.0, 60.0, 75.0, 100.0)
  val DEFAULT_STEP_COUNTS: List<Int> = listOf(5, 6, 8)

  fun nearestStandardStepKvar(idealStepKvar: Double): Double = STANDARD_STEP_KVAR.minByOrNull { abs(it - idealStepKvar) } ?: idealStepKvar

  fun calculate(
    activePowerKw: Double?, voltageV: Double? = 400.0, initialCosPhi: Double?,
    targetCosPhi: Double?, stepCounts: List<Int> = DEFAULT_STEP_COUNTS,
  ): CapacitorBankOutcome {
    if (activePowerKw == null || !activePowerKw.isFinite() || activePowerKw <= 0.0) return CapacitorBankOutcome.Failure(CapacitorBankError.INVALID_POWER_KW)
    if (voltageV == null || !voltageV.isFinite() || voltageV <= 0.0) return CapacitorBankOutcome.Failure(CapacitorBankError.INVALID_VOLTAGE)
    if (initialCosPhi == null || !initialCosPhi.isFinite() || initialCosPhi <= 0.0 || initialCosPhi >= 1.0) return CapacitorBankOutcome.Failure(CapacitorBankError.INVALID_INITIAL_COS_PHI)
    if (targetCosPhi == null || !targetCosPhi.isFinite() || targetCosPhi <= 0.0 || targetCosPhi > 1.0) return CapacitorBankOutcome.Failure(CapacitorBankError.INVALID_TARGET_COS_PHI)
    if (targetCosPhi <= initialCosPhi + 1e-9) return CapacitorBankOutcome.Failure(CapacitorBankError.TARGET_NOT_GREATER_THAN_INITIAL)

    val tan1 = sqrt((1.0 - initialCosPhi * initialCosPhi).coerceAtLeast(0.0)) / initialCosPhi
    val tan2 = sqrt((1.0 - targetCosPhi * targetCosPhi).coerceAtLeast(0.0)) / targetCosPhi
    val qRequired = activePowerKw * (tan1 - tan2)
    val s1 = activePowerKw / initialCosPhi; val s2 = activePowerKw / targetCosPhi
    val sqrt3V = sqrt(3.0) * voltageV
    val i1 = (s1 * 1000.0) / sqrt3V; val i2 = (s2 * 1000.0) / sqrt3V
    val deltaI = (i1 - i2).coerceAtLeast(0.0)
    val reductionPct = (deltaI / i1) * 100.0
    val iCap = (qRequired * 1000.0) / sqrt3V
    val suggestions = stepCounts.filter { it > 0 }.map { n ->
      val ideal = qRequired / n.toDouble(); val std = nearestStandardStepKvar(ideal)
      CapacitorStepSuggestion(n, ideal, std, std * n.toDouble(), (std * 1000.0) / sqrt3V)
    }
    return CapacitorBankOutcome.Success(
      CapacitorBankResult(activePowerKw, voltageV, initialCosPhi, targetCosPhi, tan1, tan2, qRequired, s1, s2, i1, i2, deltaI, reductionPct, iCap, suggestions),
    )
  }
}
