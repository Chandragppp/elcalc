package id.controlengineer.elcalc.core

import kotlin.math.sqrt

/**
* Rekomendasi Penampang Kabel berdasarkan Susut Tegangan (DC, 1F & 3F) + Pemeriksaan KHA input pengguna.
* Sumber:
* - Rumus: DC & 1F dV = 2*I*L*(R*cos(phi) + X*sin(phi)) (DC: cos(phi) = 1, suku X hilang); 3F dV = sqrt(3)*I*L*(R*cos(phi) + X*sin(phi)).
* - Metode 1 (IEC 60364-5-52 Annex G via https://electrician.wiki/calculators/voltage-drop/ -> PERLU CEK MANUAL):
*   rho_Cu = 0.0225, rho_Al = 0.0360 Ohm*mm2/m, X = 0.00008 Ohm/m (0.08 Ohm/km).
* - Metode 2 (Electrical Installation Guide, https://www.electrical-installation.org/enwiki/Calculation_of_voltage_drop_in_steady_load_conditions):
*   rho_Cu = 0.0237, rho_Al = 0.0376 Ohm*mm2/m, X = 0.00008 Ohm/m.
* - Batas default 3% penerangan & 5% lainnya (https://www.electrical-installation.org/enwiki/Maximum_voltage_drop_limit -> PERLU CEK MANUAL).
* - Rekomendasi HANYA dari susut tegangan; KHA tetap dicek pengguna (aplikasi TIDAK menyalin tabel PUIL/IEC).
*/
enum class ConductorMaterial(val rhoIec60364OhmMm2PerM: Double, val rhoInstallationGuideOhmMm2PerM: Double) {
  COPPER(0.0225, 0.0237), ALUMINIUM(0.0360, 0.0376),
}
enum class ResistivityMethod { IEC_60364_ANNEX_G, INSTALLATION_GUIDE }
enum class CableLoadInputMode { CURRENT_AMPS, POWER_KW }

/** Jenis rangkaian kabel: DC kontrol (2 konduktor), AC 1 fasa, AC 3 fasa. */
enum class CableCircuit(val dropFactor: Double, val powerFactor: Double) {
  DC(2.0, 1.0), SINGLE_PHASE(2.0, 1.0), THREE_PHASE(sqrt(3.0), sqrt(3.0)),
}
enum class LocalCableType(val code: String) { NYA("NYA"), NYM("NYM"), NYY("NYY"), NYFGBY("NYFGbY"), NYAF("NYAF") }
enum class CableError {
  INVALID_VOLTAGE, INVALID_LENGTH, INVALID_LOAD, INVALID_COS_PHI,
  INVALID_LIMIT_PERCENT, INVALID_KHA_VALUE, INVALID_CORRECTION_FACTOR,
}

data class KhaCheckResult(val baseKhaA: Double, val tempFactor: Double, val groupFactor: Double, val deratedKhaA: Double, val loadCurrentA: Double, val passed: Boolean)

/** Hasil susut tegangan untuk satu penampang; marginPercent = batas - dV% (positif = sisa, negatif = kurang). */
data class SectionOption(
  val sectionMm2: Double, val resistancePerKmOhm: Double, val voltageDropV: Double,
  val voltageDropPercent: Double, val receivingVoltageV: Double, val marginPercent: Double, val withinLimit: Boolean,
)

data class CableSizingResult(
  val circuit: CableCircuit, val material: ConductorMaterial, val resistivityMethod: ResistivityMethod,
  val rhoOhmMm2PerM: Double, val voltageV: Double, val lengthM: Double, val loadCurrentA: Double, val cosPhi: Double,
  val maxDropLimitPercent: Double,
  /** Penampang standar terkecil dengan dV <= batas; null bila 300 mm² pun melebihi batas. */
  val recommended: SectionOption?,
  /** Satu ukuran di bawah rekomendasi (melebihi batas); bila tak ada rekomendasi = 300 mm². */
  val smaller: SectionOption?,
  /** Satu ukuran di atas rekomendasi (sisa lebih besar). */
  val larger: SectionOption?,
  val khaCheck: KhaCheckResult?,
)

sealed class CableOutcome {
  data class Success(val result: CableSizingResult) : CableOutcome()
  data class Failure(val error: CableError) : CableOutcome()
}

object CableCalculator {
  /** Penampang standar; 0,5–1 mm² untuk pengawatan kontrol di panel. */
  val STANDARD_SECTIONS_MM2: List<Double> = listOf(
    0.5, 0.75, 1.0, 1.5, 2.5, 4.0, 6.0, 10.0, 16.0, 25.0, 35.0, 50.0, 70.0, 95.0, 120.0, 150.0, 185.0, 240.0, 300.0,
  )
  /** Tegangan cepat: DC kontrol panel dan AC jaringan. */
  val DC_VOLTAGE_PRESETS: List<Double> = listOf(12.0, 24.0, 48.0, 110.0)
  val AC_1P_VOLTAGE_PRESETS: List<Double> = listOf(220.0, 230.0)
  val AC_3P_VOLTAGE_PRESETS: List<Double> = listOf(380.0, 400.0)
  const val DEFAULT_REACTANCE_OHM_PER_M = 0.00008
  const val DEFAULT_REACTANCE_OHM_PER_KM = 0.08
  const val DEFAULT_LIMIT_LIGHTING_PCT = 3.0
  const val DEFAULT_LIMIT_OTHER_PCT = 5.0

  fun resistivityOhmMm2PerM(material: ConductorMaterial, method: ResistivityMethod = ResistivityMethod.IEC_60364_ANNEX_G): Double =
    if (method == ResistivityMethod.IEC_60364_ANNEX_G) material.rhoIec60364OhmMm2PerM else material.rhoInstallationGuideOhmMm2PerM

  fun voltageDropForSection(
    circuit: CableCircuit, material: ConductorMaterial, method: ResistivityMethod,
    crossSectionMm2: Double, lengthM: Double, loadCurrentA: Double, cosPhi: Double,
    reactanceOhmPerM: Double = DEFAULT_REACTANCE_OHM_PER_M,
  ): Double {
    val rPerM = resistivityOhmMm2PerM(material, method) / crossSectionMm2
    val pf = if (circuit == CableCircuit.DC) 1.0 else cosPhi
    val sinPhi = sqrt((1.0 - pf * pf).coerceAtLeast(0.0))
    return circuit.dropFactor * loadCurrentA * lengthM * (rPerM * pf + reactanceOhmPerM * sinPhi)
  }

  fun calculate(
    circuit: CableCircuit, material: ConductorMaterial = ConductorMaterial.COPPER,
    resistivityMethod: ResistivityMethod = ResistivityMethod.IEC_60364_ANNEX_G,
    lengthM: Double?, voltageV: Double?, loadInputMode: CableLoadInputMode = CableLoadInputMode.CURRENT_AMPS,
    loadValue: Double?, cosPhi: Double?, maxDropLimitPercent: Double?,
    userKhaTableA: Double? = null, userKhaTextBlank: Boolean = true,
    tempCorrectionFactor: Double? = 1.0, groupCorrectionFactor: Double? = 1.0,
  ): CableOutcome {
    if (voltageV == null || !voltageV.isFinite() || voltageV <= 0.0) return CableOutcome.Failure(CableError.INVALID_VOLTAGE)
    if (lengthM == null || !lengthM.isFinite() || lengthM <= 0.0) return CableOutcome.Failure(CableError.INVALID_LENGTH)
    if (loadValue == null || !loadValue.isFinite() || loadValue <= 0.0) return CableOutcome.Failure(CableError.INVALID_LOAD)
    // DC tidak memakai cos φ (selalu 1)
    val pf = if (circuit == CableCircuit.DC) 1.0 else cosPhi
    if (pf == null || !pf.isFinite() || pf <= 0.0 || pf > 1.0) return CableOutcome.Failure(CableError.INVALID_COS_PHI)
    if (maxDropLimitPercent == null || !maxDropLimitPercent.isFinite() || maxDropLimitPercent <= 0.0 || maxDropLimitPercent >= 100.0) return CableOutcome.Failure(CableError.INVALID_LIMIT_PERCENT)

    val loadCurrentA = if (loadInputMode == CableLoadInputMode.CURRENT_AMPS) loadValue else (loadValue * 1000.0) / (circuit.powerFactor * voltageV * pf)
    val khaCheck = if (!userKhaTextBlank) {
      if (userKhaTableA == null || !userKhaTableA.isFinite() || userKhaTableA <= 0.0) return CableOutcome.Failure(CableError.INVALID_KHA_VALUE)
      if (tempCorrectionFactor == null || !tempCorrectionFactor.isFinite() || tempCorrectionFactor <= 0.0 || tempCorrectionFactor > 2.0 ||
        groupCorrectionFactor == null || !groupCorrectionFactor.isFinite() || groupCorrectionFactor <= 0.0 || groupCorrectionFactor > 2.0
      ) return CableOutcome.Failure(CableError.INVALID_CORRECTION_FACTOR)
      val derated = userKhaTableA * tempCorrectionFactor * groupCorrectionFactor
      KhaCheckResult(userKhaTableA, tempCorrectionFactor, groupCorrectionFactor, derated, loadCurrentA, loadCurrentA <= derated + 1e-9)
    } else null

    val rho = resistivityOhmMm2PerM(material, resistivityMethod)
    val options = STANDARD_SECTIONS_MM2.map { s ->
      val dV = voltageDropForSection(circuit, material, resistivityMethod, s, lengthM, loadCurrentA, pf)
      val pct = dV / voltageV * 100.0
      SectionOption(s, rho * 1000.0 / s, dV, pct, (voltageV - dV).coerceAtLeast(0.0), maxDropLimitPercent - pct, pct <= maxDropLimitPercent + 1e-9)
    }
    val recIdx = options.indexOfFirst { it.withinLimit }
    val recommended = options.getOrNull(recIdx)
    val smaller = if (recIdx < 0) options.last() else options.getOrNull(recIdx - 1)
    val larger = if (recIdx < 0) null else options.getOrNull(recIdx + 1)
    return CableOutcome.Success(
      CableSizingResult(
        circuit, material, resistivityMethod, rho, voltageV, lengthM, loadCurrentA, pf,
        maxDropLimitPercent, recommended, smaller, larger, khaCheck,
      ),
    )
  }
}
