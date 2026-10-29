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
* - KHA: aplikasi TIDAK menyalin tabel PUIL/IEC; pengguna mengisi tabel KHA sendiri (KhaTable). Rekomendasi = ukuran terkecil
*   yang lolos dV DAN I_beban <= KHA * k_suhu * k_grup. Tanpa tabel, rekomendasi hanya dari dV dan ditandai belum cek KHA.
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
  INVALID_LIMIT_PERCENT, INVALID_CORRECTION_FACTOR,
}

/** Tabel KHA milik pengguna: penampang (mm²) ke KHA dasar (A) dari tabel resmi yang ia pegang. */
data class KhaTable(val name: String, val ampsBySection: Map<Double, Double>) {
  fun ampsFor(sectionMm2: Double): Double? = ampsBySection.entries.firstOrNull { kotlin.math.abs(it.key - sectionMm2) < 1e-9 }?.value
}

/** Simpan/baca daftar tabel KHA sebagai teks sederhana: satu tabel per baris "nama|1.5=14.5;2.5=19.5". */
object KhaTableCodec {
  fun encode(tables: List<KhaTable>): String = tables.joinToString("\n") { t ->
    val clean = t.name.replace("|", " ").replace("\n", " ").trim()
    clean + "|" + t.ampsBySection.entries.sortedBy { it.key }.joinToString(";") { "${it.key}=${it.value}" }
  }

  fun decode(text: String?): List<KhaTable> = text.orEmpty().lines().mapNotNull { line ->
    val parts = line.split("|", limit = 2)
    if (parts.size != 2 || parts[0].isBlank()) return@mapNotNull null
    val map = parts[1].split(";").mapNotNull { kv ->
      val (k, v) = kv.split("=").takeIf { it.size == 2 } ?: return@mapNotNull null
      val sec = k.toDoubleOrNull(); val amps = v.toDoubleOrNull()
      if (sec == null || amps == null || sec <= 0.0 || amps <= 0.0) null else sec to amps
    }.toMap()
    KhaTable(parts[0], map)
  }
}

/**
 * Hasil satu penampang. marginPercent = batas - dV% (positif = sisa, negatif = kurang).
 * deratedKhaA = KHA tabel * k_suhu * k_grup (null bila tanpa tabel / ukuran tidak ada di tabel); khaMarginA = KHA efektif - I_beban.
 */
data class SectionOption(
  val sectionMm2: Double, val resistancePerKmOhm: Double, val voltageDropV: Double,
  val voltageDropPercent: Double, val receivingVoltageV: Double, val marginPercent: Double, val withinLimit: Boolean,
  val deratedKhaA: Double? = null, val khaMarginA: Double? = null,
) {
  val khaOk: Boolean? get() = khaMarginA?.let { it >= -1e-9 }
  /** Lolos semua cek yang tersedia: dV, dan KHA bila tabel dipakai (ukuran tanpa nilai KHA dianggap tidak lolos). */
  fun passes(khaChecked: Boolean): Boolean = withinLimit && (!khaChecked || khaOk == true)
}

data class CableSizingResult(
  val circuit: CableCircuit, val material: ConductorMaterial, val resistivityMethod: ResistivityMethod,
  val rhoOhmMm2PerM: Double, val voltageV: Double, val lengthM: Double, val loadCurrentA: Double, val cosPhi: Double,
  val maxDropLimitPercent: Double,
  /** true bila tabel KHA dipakai; false = rekomendasi hanya dari dV (belum aman secara termal). */
  val khaChecked: Boolean,
  /** Penampang standar terkecil yang lolos dV (+ KHA bila dicek); null bila tak ada yang lolos. */
  val recommended: SectionOption?,
  /** Satu ukuran di bawah rekomendasi (tidak lolos); bila tak ada rekomendasi = ukuran terbesar yang dihitung. */
  val smaller: SectionOption?,
  /** Satu ukuran di atas rekomendasi. */
  val larger: SectionOption?,
  /** Seluruh opsi penampang standar (untuk tabel Perbandingan di laporan Excel). */
  val allOptions: List<SectionOption> = emptyList(),
)

/** Arus maksimum yang membatasi kapasitas kabel: susut tegangan atau KHA efektif. */
enum class CapacityLimit { VOLTAGE_DROP, KHA }

/**
 * Hasil mode balik "kabel ini sanggup berapa": penampang & panjang diketahui, cari arus maksimum.
 * maxCurrentByDropA = arus saat dV tepat di batas; deratedKhaA = KHA tabel * k_suhu * k_grup (null bila tanpa tabel).
 */
data class CableCapacityResult(
  val circuit: CableCircuit, val sectionMm2: Double, val lengthM: Double, val voltageV: Double, val cosPhi: Double,
  val maxDropLimitPercent: Double, val maxCurrentByDropA: Double, val deratedKhaA: Double?,
  /** Arus maksimum = min(batas dV, KHA efektif); tanpa tabel KHA hanya dari dV (belum aman secara termal). */
  val maxCurrentA: Double, val limitedBy: CapacityLimit,
  /** Daya beban maksimum (kW) pada [maxCurrentA]: DC V*I, 1F V*I*cos φ, 3F √3*V*I*cos φ. */
  val maxPowerKw: Double,
  /** Susut tegangan (%) pada [maxCurrentA]. */
  val dropAtMaxPercent: Double,
  /** Panjang maksimum (m) agar dV tetap di batas saat arus = KHA efektif; null bila tanpa tabel. */
  val maxLengthAtKhaM: Double?,
)

sealed class CableCapacityOutcome {
  data class Success(val result: CableCapacityResult) : CableCapacityOutcome()
  data class Failure(val error: CableError) : CableCapacityOutcome()
}

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

  /** Mode balik: arus & daya maksimum untuk penampang dan panjang tertentu (dV dan, bila ada tabel, KHA). */
  fun capacity(
    circuit: CableCircuit, material: ConductorMaterial = ConductorMaterial.COPPER,
    resistivityMethod: ResistivityMethod = ResistivityMethod.IEC_60364_ANNEX_G,
    sectionMm2: Double, lengthM: Double?, voltageV: Double?, cosPhi: Double?, maxDropLimitPercent: Double?,
    khaTable: KhaTable? = null, tempCorrectionFactor: Double? = 1.0, groupCorrectionFactor: Double? = 1.0,
  ): CableCapacityOutcome {
    if (voltageV == null || !voltageV.isFinite() || voltageV <= 0.0) return CableCapacityOutcome.Failure(CableError.INVALID_VOLTAGE)
    if (lengthM == null || !lengthM.isFinite() || lengthM <= 0.0) return CableCapacityOutcome.Failure(CableError.INVALID_LENGTH)
    if (!sectionMm2.isFinite() || sectionMm2 <= 0.0) return CableCapacityOutcome.Failure(CableError.INVALID_LOAD)
    val pf = if (circuit == CableCircuit.DC) 1.0 else cosPhi
    if (pf == null || !pf.isFinite() || pf <= 0.0 || pf > 1.0) return CableCapacityOutcome.Failure(CableError.INVALID_COS_PHI)
    if (maxDropLimitPercent == null || !maxDropLimitPercent.isFinite() || maxDropLimitPercent <= 0.0 || maxDropLimitPercent >= 100.0) return CableCapacityOutcome.Failure(CableError.INVALID_LIMIT_PERCENT)
    val khaChecked = khaTable != null
    if (khaChecked && (tempCorrectionFactor == null || !tempCorrectionFactor.isFinite() || tempCorrectionFactor <= 0.0 || tempCorrectionFactor > 2.0 ||
        groupCorrectionFactor == null || !groupCorrectionFactor.isFinite() || groupCorrectionFactor <= 0.0 || groupCorrectionFactor > 2.0)
    ) return CableCapacityOutcome.Failure(CableError.INVALID_CORRECTION_FACTOR)

    // dV linier terhadap I dan L: hitung dV untuk 1 A × 1 m lalu balikkan
    val dropPerAmpMeter = voltageDropForSection(circuit, material, resistivityMethod, sectionMm2, 1.0, 1.0, pf)
    val allowedDropV = maxDropLimitPercent / 100.0 * voltageV
    val iByDrop = allowedDropV / (dropPerAmpMeter * lengthM)
    val derated = khaTable?.ampsFor(sectionMm2)?.let { it * tempCorrectionFactor!! * groupCorrectionFactor!! }
    val limitedByKha = derated != null && derated < iByDrop
    val iMax = if (limitedByKha) derated!! else iByDrop
    val powerKw = circuit.powerFactor * voltageV * iMax * pf / 1000.0
    return CableCapacityOutcome.Success(
      CableCapacityResult(
        circuit, sectionMm2, lengthM, voltageV, pf, maxDropLimitPercent, iByDrop, derated, iMax,
        if (limitedByKha) CapacityLimit.KHA else CapacityLimit.VOLTAGE_DROP, powerKw,
        dropPerAmpMeter * iMax * lengthM / voltageV * 100.0,
        derated?.let { allowedDropV / (dropPerAmpMeter * it) },
      ),
    )
  }

  fun calculate(
    circuit: CableCircuit, material: ConductorMaterial = ConductorMaterial.COPPER,
    resistivityMethod: ResistivityMethod = ResistivityMethod.IEC_60364_ANNEX_G,
    lengthM: Double?, voltageV: Double?, loadInputMode: CableLoadInputMode = CableLoadInputMode.CURRENT_AMPS,
    loadValue: Double?, cosPhi: Double?, maxDropLimitPercent: Double?,
    khaTable: KhaTable? = null,
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
    val khaChecked = khaTable != null
    if (khaChecked && (tempCorrectionFactor == null || !tempCorrectionFactor.isFinite() || tempCorrectionFactor <= 0.0 || tempCorrectionFactor > 2.0 ||
        groupCorrectionFactor == null || !groupCorrectionFactor.isFinite() || groupCorrectionFactor <= 0.0 || groupCorrectionFactor > 2.0)
    ) return CableOutcome.Failure(CableError.INVALID_CORRECTION_FACTOR)
    val kTotal = if (khaChecked) tempCorrectionFactor!! * groupCorrectionFactor!! else 1.0

    val rho = resistivityOhmMm2PerM(material, resistivityMethod)
    val options = STANDARD_SECTIONS_MM2.map { s ->
      val dV = voltageDropForSection(circuit, material, resistivityMethod, s, lengthM, loadCurrentA, pf)
      val pct = dV / voltageV * 100.0
      val derated = khaTable?.ampsFor(s)?.let { it * kTotal }
      SectionOption(
        s, rho * 1000.0 / s, dV, pct, (voltageV - dV).coerceAtLeast(0.0), maxDropLimitPercent - pct, pct <= maxDropLimitPercent + 1e-9,
        derated, derated?.let { it - loadCurrentA },
      )
    }
    val recIdx = options.indexOfFirst { it.passes(khaChecked) }
    val recommended = options.getOrNull(recIdx)
    val smaller = if (recIdx < 0) options.last() else options.getOrNull(recIdx - 1)
    val larger = if (recIdx < 0) null else options.getOrNull(recIdx + 1)
    return CableOutcome.Success(
      CableSizingResult(
        circuit, material, resistivityMethod, rho, voltageV, lengthM, loadCurrentA, pf,
        maxDropLimitPercent, khaChecked, recommended, smaller, larger, options,
      ),
    )
  }
}
