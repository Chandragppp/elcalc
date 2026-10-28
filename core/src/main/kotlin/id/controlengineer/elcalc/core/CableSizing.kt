package id.controlengineer.elcalc.core

import kotlin.math.sqrt

/**
* Kalkulator Susut Tegangan Kabel (1F & 3F), Penampang Minimum, dan Pemeriksaan KHA input pengguna.
* Sumber:
* - Rumus: 1F dV = 2*I*L*(R*cos(phi) + X*sin(phi)); 3F dV = sqrt(3)*I*L*(R*cos(phi) + X*sin(phi)).
* - Metode 1 (IEC 60364-5-52 Annex G via https://electrician.wiki/calculators/voltage-drop/ -> PERLU CEK MANUAL):
*   rho_Cu = 0.0225, rho_Al = 0.0360 Ohm*mm2/m, X = 0.00008 Ohm/m (0.08 Ohm/km).
* - Metode 2 (Electrical Installation Guide, https://www.electrical-installation.org/enwiki/Calculation_of_voltage_drop_in_steady_load_conditions):
*   rho_Cu = 0.0237, rho_Al = 0.0376 Ohm*mm2/m, X = 0.00008 Ohm/m.
* - Batas default 3% penerangan & 5% lainnya (https://www.electrical-installation.org/enwiki/Maximum_voltage_drop_limit -> PERLU CEK MANUAL).
* - KHA: Aplikasi TIDAK menyalin tabel PUIL/IEC; mengevaluasi I_beban <= KHA_input * k_suhu * k_grup.
*/
enum class ConductorMaterial(val rhoIec60364OhmMm2PerM: Double, val rhoInstallationGuideOhmMm2PerM: Double) {
  COPPER(0.0225, 0.0237), ALUMINIUM(0.0360, 0.0376),
}
enum class ResistivityMethod { IEC_60364_ANNEX_G, INSTALLATION_GUIDE }
enum class CableLoadInputMode { CURRENT_AMPS, POWER_KW }
enum class LocalCableType(val code: String) { NYA("NYA"), NYM("NYM"), NYY("NYY"), NYFGBY("NYFGbY"), NYAF("NYAF") }
enum class CableError {
  INVALID_VOLTAGE, INVALID_LENGTH, INVALID_LOAD, INVALID_COS_PHI,
  INVALID_LIMIT_PERCENT, INVALID_KHA_VALUE, INVALID_CORRECTION_FACTOR,
}

data class KhaCheckResult(val baseKhaA: Double, val tempFactor: Double, val groupFactor: Double, val deratedKhaA: Double, val loadCurrentA: Double, val passed: Boolean)

data class CableVoltageDropResult(
  val phaseSystem: PhaseSystem, val material: ConductorMaterial, val resistivityMethod: ResistivityMethod,
  val rhoOhmMm2PerM: Double, val reactanceOhmPerM: Double, val crossSectionMm2: Double,
  val lengthM: Double, val voltageV: Double, val loadCurrentA: Double, val cosPhi: Double,
  val sinPhi: Double, val resistancePerKmOhm: Double, val voltageDropV: Double,
  val voltageDropPercent: Double, val receivingVoltageV: Double, val maxDropLimitPercent: Double,
  val withinDropLimit: Boolean, val minCrossSectionForLimitMm2: Double?, val khaCheck: KhaCheckResult?,
)

sealed class CableOutcome {
  data class Success(val result: CableVoltageDropResult) : CableOutcome()
  data class Failure(val error: CableError) : CableOutcome()
}

object CableCalculator {
  val STANDARD_SECTIONS_MM2: List<Double> = listOf(1.5, 2.5, 4.0, 6.0, 10.0, 16.0, 25.0, 35.0, 50.0, 70.0, 95.0, 120.0, 150.0, 185.0, 240.0, 300.0)
  const val DEFAULT_REACTANCE_OHM_PER_M = 0.00008
  const val DEFAULT_REACTANCE_OHM_PER_KM = 0.08
  const val DEFAULT_LIMIT_LIGHTING_PCT = 3.0
  const val DEFAULT_LIMIT_OTHER_PCT = 5.0

  fun resistivityOhmMm2PerM(material: ConductorMaterial, method: ResistivityMethod = ResistivityMethod.IEC_60364_ANNEX_G): Double =
    if (method == ResistivityMethod.IEC_60364_ANNEX_G) material.rhoIec60364OhmMm2PerM else material.rhoInstallationGuideOhmMm2PerM

  fun voltageDropForSection(
    phaseSystem: PhaseSystem, material: ConductorMaterial, method: ResistivityMethod,
    crossSectionMm2: Double, lengthM: Double, loadCurrentA: Double, cosPhi: Double,
    reactanceOhmPerM: Double = DEFAULT_REACTANCE_OHM_PER_M,
  ): Double {
    val rPerM = resistivityOhmMm2PerM(material, method) / crossSectionMm2
    val sinPhi = sqrt((1.0 - cosPhi * cosPhi).coerceAtLeast(0.0))
    val b = if (phaseSystem == PhaseSystem.SINGLE_PHASE) 2.0 else sqrt(3.0)
    return b * loadCurrentA * lengthM * (rPerM * cosPhi + reactanceOhmPerM * sinPhi)
  }

  fun calculate(
    phaseSystem: PhaseSystem, material: ConductorMaterial = ConductorMaterial.COPPER,
    resistivityMethod: ResistivityMethod = ResistivityMethod.IEC_60364_ANNEX_G, crossSectionMm2: Double,
    lengthM: Double?, voltageV: Double?, loadInputMode: CableLoadInputMode = CableLoadInputMode.CURRENT_AMPS,
    loadValue: Double?, cosPhi: Double?, maxDropLimitPercent: Double?,
    userKhaTableA: Double? = null, userKhaTextBlank: Boolean = true,
    tempCorrectionFactor: Double? = 1.0, groupCorrectionFactor: Double? = 1.0,
  ): CableOutcome {
    if (voltageV == null || !voltageV.isFinite() || voltageV <= 0.0) return CableOutcome.Failure(CableError.INVALID_VOLTAGE)
    if (lengthM == null || !lengthM.isFinite() || lengthM <= 0.0) return CableOutcome.Failure(CableError.INVALID_LENGTH)
    if (loadValue == null || !loadValue.isFinite() || loadValue <= 0.0 || crossSectionMm2 <= 0.0 || !crossSectionMm2.isFinite()) return CableOutcome.Failure(CableError.INVALID_LOAD)
    if (cosPhi == null || !cosPhi.isFinite() || cosPhi <= 0.0 || cosPhi > 1.0) return CableOutcome.Failure(CableError.INVALID_COS_PHI)
    if (maxDropLimitPercent == null || !maxDropLimitPercent.isFinite() || maxDropLimitPercent <= 0.0 || maxDropLimitPercent >= 100.0) return CableOutcome.Failure(CableError.INVALID_LIMIT_PERCENT)

    val loadCurrentA = if (loadInputMode == CableLoadInputMode.CURRENT_AMPS) loadValue else (loadValue * 1000.0) / (phaseSystem.phaseFactor * voltageV * cosPhi)
    val khaCheck = if (!userKhaTextBlank) {
      if (userKhaTableA == null || !userKhaTableA.isFinite() || userKhaTableA <= 0.0) return CableOutcome.Failure(CableError.INVALID_KHA_VALUE)
      if (tempCorrectionFactor == null || !tempCorrectionFactor.isFinite() || tempCorrectionFactor <= 0.0 || tempCorrectionFactor > 2.0 ||
        groupCorrectionFactor == null || !groupCorrectionFactor.isFinite() || groupCorrectionFactor <= 0.0 || groupCorrectionFactor > 2.0
      ) return CableOutcome.Failure(CableError.INVALID_CORRECTION_FACTOR)
      val derated = userKhaTableA * tempCorrectionFactor * groupCorrectionFactor
      KhaCheckResult(userKhaTableA, tempCorrectionFactor, groupCorrectionFactor, derated, loadCurrentA, loadCurrentA <= derated + 1e-9)
    } else null

    val rho = resistivityOhmMm2PerM(material, resistivityMethod)
    val rPerKm = (rho * 1000.0) / crossSectionMm2
    val sinPhi = sqrt((1.0 - cosPhi * cosPhi).coerceAtLeast(0.0))
    val dropV = voltageDropForSection(phaseSystem, material, resistivityMethod, crossSectionMm2, lengthM, loadCurrentA, cosPhi)
    val dropPct = (dropV / voltageV) * 100.0
    val receivingV = (voltageV - dropV).coerceAtLeast(0.0)
    val withinLimit = dropPct <= maxDropLimitPercent + 1e-9
    val minSection = STANDARD_SECTIONS_MM2.firstOrNull { cand ->
      (voltageDropForSection(phaseSystem, material, resistivityMethod, cand, lengthM, loadCurrentA, cosPhi) / voltageV) * 100.0 <= maxDropLimitPercent + 1e-9
    }
    return CableOutcome.Success(
      CableVoltageDropResult(
        phaseSystem, material, resistivityMethod, rho, DEFAULT_REACTANCE_OHM_PER_M,
        crossSectionMm2, lengthM, voltageV, loadCurrentA, cosPhi, sinPhi, rPerKm,
        dropV, dropPct, receivingV, maxDropLimitPercent, withinLimit, minSection, khaCheck,
      ),
    )
  }
}
