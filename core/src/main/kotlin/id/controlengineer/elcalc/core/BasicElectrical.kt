package id.controlengineer.elcalc.core

import kotlin.math.abs
import kotlin.math.sqrt

/**
* Kalkulator Dasar Listrik (Hukum Ohm & Daya DC, Daya AC 1F/3F, dan Trafo Distribusi).
* Sumber:
* - Hukum Ohm: V = I*R, P = V*I = I^2*R = V^2/R
* - Daya AC: S_1F = V*I/1000, S_3F = sqrt(3)*V*I/1000 (kVA), P = S*cos(phi) (kW), Q = S*sin(phi) (kVAR)
* - Trafo (https://www.electrical-installation.org/enwiki/Short-circuit_current_at_the_secondary_terminals_of_a_MV/LV_distribution_transformer):
*   In = S*1000 / (sqrt(3)*V), Isc = In / (Uk / 100).
*/
enum class OhmError { NEED_EXACTLY_TWO, NON_POSITIVE_VALUE, INCONSISTENT_VALUES }
data class OhmResult(val voltageV: Double, val currentA: Double, val resistanceOhm: Double, val powerW: Double, val formulaUsed: String)
sealed class OhmOutcome {
  data class Success(val result: OhmResult) : OhmOutcome()
  data class Failure(val error: OhmError) : OhmOutcome()
}

object OhmCalculator {
  fun solve(v: Double? = null, i: Double? = null, r: Double? = null, p: Double? = null, anyInvalidText: Boolean = false): OhmOutcome {
    if (anyInvalidText) return OhmOutcome.Failure(OhmError.NON_POSITIVE_VALUE)
    val provided = listOfNotNull(v, i, r, p)
    if (provided.any { !it.isFinite() || it <= 0.0 }) return OhmOutcome.Failure(OhmError.NON_POSITIVE_VALUE)
    if (provided.size != 2) return OhmOutcome.Failure(OhmError.NEED_EXACTLY_TWO)
    val res = when {
      v != null && i != null -> OhmResult(v, i, v / i, v * i, "R = V / I, P = V × I")
      v != null && r != null -> OhmResult(v, v / r, r, (v * v) / r, "I = V / R, P = V² / R")
      v != null && p != null -> OhmResult(v, p / v, (v * v) / p, p, "I = P / V, R = V² / P")
      i != null && r != null -> OhmResult(i * r, i, r, i * i * r, "V = I × R, P = I² × R")
      i != null && p != null -> OhmResult(p / i, i, p / (i * i), p, "V = P / I, R = P / I²")
      r != null && p != null -> OhmResult(sqrt(p * r), sqrt(p / r), r, p, "V = √(P × R), I = √(P / R)")
      else -> return OhmOutcome.Failure(OhmError.NEED_EXACTLY_TWO)
    }
    if (!res.voltageV.isFinite() || !res.currentA.isFinite() || !res.resistanceOhm.isFinite() || !res.powerW.isFinite()) {
      return OhmOutcome.Failure(OhmError.INCONSISTENT_VALUES)
    }
    return OhmOutcome.Success(res)
  }
}

enum class PhaseSystem(val phaseFactor: Double) { SINGLE_PHASE(1.0), THREE_PHASE(sqrt(3.0)) }
enum class AcPowerInputMode { FROM_CURRENT, FROM_ACTIVE_POWER_KW, FROM_APPARENT_POWER_KVA }
enum class AcPowerError { INVALID_VOLTAGE, INVALID_INPUT_VALUE, INVALID_COS_PHI }

data class AcPowerResult(
  val phaseSystem: PhaseSystem, val voltageV: Double, val currentA: Double,
  val cosPhi: Double, val sinPhi: Double, val activePowerKw: Double,
  val apparentPowerKva: Double, val reactivePowerKvar: Double,
)

sealed class AcPowerOutcome {
  data class Success(val result: AcPowerResult) : AcPowerOutcome()
  data class Failure(val error: AcPowerError) : AcPowerOutcome()
}

object AcPowerCalculator {
  val VOLTAGE_PRESETS_1P = listOf(220.0, 230.0)
  val VOLTAGE_PRESETS_3P = listOf(380.0, 400.0)

  fun calculate(phaseSystem: PhaseSystem, voltageV: Double?, inputMode: AcPowerInputMode, inputValue: Double?, cosPhi: Double?): AcPowerOutcome {
    if (voltageV == null || !voltageV.isFinite() || voltageV <= 0.0) return AcPowerOutcome.Failure(AcPowerError.INVALID_VOLTAGE)
    if (inputValue == null || !inputValue.isFinite() || inputValue <= 0.0) return AcPowerOutcome.Failure(AcPowerError.INVALID_INPUT_VALUE)
    if (cosPhi == null || !cosPhi.isFinite() || cosPhi <= 0.0 || cosPhi > 1.0) return AcPowerOutcome.Failure(AcPowerError.INVALID_COS_PHI)
    val k = phaseSystem.phaseFactor
    val sinPhi = sqrt((1.0 - cosPhi * cosPhi).coerceAtLeast(0.0))
    val currentA: Double; val apparentKva: Double; val activeKw: Double
    when (inputMode) {
      AcPowerInputMode.FROM_CURRENT -> { currentA = inputValue; apparentKva = (k * voltageV * currentA) / 1000.0; activeKw = apparentKva * cosPhi }
      AcPowerInputMode.FROM_ACTIVE_POWER_KW -> { activeKw = inputValue; apparentKva = activeKw / cosPhi; currentA = (apparentKva * 1000.0) / (k * voltageV) }
      AcPowerInputMode.FROM_APPARENT_POWER_KVA -> { apparentKva = inputValue; activeKw = apparentKva * cosPhi; currentA = (apparentKva * 1000.0) / (k * voltageV) }
    }
    return AcPowerOutcome.Success(AcPowerResult(phaseSystem, voltageV, currentA, cosPhi, sinPhi, activeKw, apparentKva, apparentKva * sinPhi))
  }
}

enum class TransformerInputMode { FROM_KVA, FROM_SECONDARY_CURRENT }
enum class TransformerError { INVALID_PRIMARY_VOLTAGE, INVALID_SECONDARY_VOLTAGE, INVALID_INPUT_VALUE, INVALID_UK_PERCENT }

data class TransformerResult(
  val phaseSystem: PhaseSystem, val kva: Double, val primaryVoltageV: Double, val secondaryVoltageV: Double,
  val ukPercent: Double, val primaryNominalCurrentA: Double, val secondaryNominalCurrentA: Double,
  val secondaryShortCircuitCurrentA: Double, val secondaryShortCircuitCurrentKa: Double,
)

sealed class TransformerOutcome {
  data class Success(val result: TransformerResult) : TransformerOutcome()
  data class Failure(val error: TransformerError) : TransformerOutcome()
}

object TransformerCalculator {
  fun calculate(
    phaseSystem: PhaseSystem = PhaseSystem.THREE_PHASE, mode: TransformerInputMode = TransformerInputMode.FROM_KVA,
    inputValue: Double?, primaryVoltageV: Double?, secondaryVoltageV: Double?, ukPercent: Double?,
  ): TransformerOutcome {
    if (primaryVoltageV == null || !primaryVoltageV.isFinite() || primaryVoltageV <= 0.0) return TransformerOutcome.Failure(TransformerError.INVALID_PRIMARY_VOLTAGE)
    if (secondaryVoltageV == null || !secondaryVoltageV.isFinite() || secondaryVoltageV <= 0.0) return TransformerOutcome.Failure(TransformerError.INVALID_SECONDARY_VOLTAGE)
    if (inputValue == null || !inputValue.isFinite() || inputValue <= 0.0) return TransformerOutcome.Failure(TransformerError.INVALID_INPUT_VALUE)
    if (ukPercent == null || !ukPercent.isFinite() || ukPercent <= 0.0 || ukPercent >= 100.0) return TransformerOutcome.Failure(TransformerError.INVALID_UK_PERCENT)

    val k = phaseSystem.phaseFactor
    val kva = if (mode == TransformerInputMode.FROM_KVA) inputValue else (k * secondaryVoltageV * inputValue) / 1000.0
    val iSec = if (mode == TransformerInputMode.FROM_KVA) (kva * 1000.0) / (k * secondaryVoltageV) else inputValue
    val iPri = (kva * 1000.0) / (k * primaryVoltageV)
    val iscA = iSec / (ukPercent / 100.0)
    if (abs(iscA).isInfinite() || iscA.isNaN()) return TransformerOutcome.Failure(TransformerError.INVALID_UK_PERCENT)
    return TransformerOutcome.Success(TransformerResult(phaseSystem, kva, primaryVoltageV, secondaryVoltageV, ukPercent, iPri, iSec, iscA, iscA / 1000.0))
  }
}
