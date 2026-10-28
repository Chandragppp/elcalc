package id.controlengineer.elcalc.core

/** Isolasi kabel untuk tabel IEC: PVC (70 °C) atau XLPE/EPR (90 °C). */
enum class CableInsulation { PVC, XLPE }

/**
 * Cara pemasangan IEC 60364-5-52 (Tabel B.52.1). E/F/G memakai tabel B.52.10–B.52.13 (udara bebas/tray berlubang).
 * F_FLAT/F_TREFOIL dan G_* hanya untuk 3 konduktor berbeban (kabel inti tunggal).
 */
enum class IecMethod(val code: String) {
  A1("A1"), A2("A2"), B1("B1"), B2("B2"), C("C"), D1("D1"), D2("D2"),
  E("E"), F("F"), F_FLAT("F flat"), G_HORIZ("G horiz"), G_VERT("G vert");

  /** Metode D (di tanah) acuan suhu tanah 20 °C; faktor B.52.14 (udara) tidak berlaku. */
  val inGround: Boolean get() = this == D1 || this == D2
  /** Susunan B.52.17 yang sesuai (1 = digabung, 2 = satu lapis di dinding, 4 = tray berlubang). */
  val defaultGroupingArrangement: Int get() = when (this) { C -> 2; E, F, F_FLAT, G_HORIZ, G_VERT -> 4; else -> 1 }
}

object IecKha {
  /** Metode yang tersedia untuk jumlah konduktor berbeban tertentu. */
  fun methodsFor(loadedConductors: Int): List<IecMethod> =
    if (loadedConductors == 2) IecMethod.entries.filter { it.ordinal <= IecMethod.F.ordinal } else IecMethod.entries.toList()

  /**
   * Tabel KHA bawaan IEC untuk kombinasi bahan/isolasi/jumlah konduktor berbeban (2 atau 3)/metode;
   * null bila kombinasi tidak ada di standar (mis. F flat untuk 2 konduktor).
   */
  fun table(material: ConductorMaterial, insulation: CableInsulation, loadedConductors: Int, method: IecMethod): KhaTable? {
    val cu = material == ConductorMaterial.COPPER
    val freeAir = method.ordinal >= IecMethod.E.ordinal
    val xlpe = insulation == CableInsulation.XLPE
    // A1–D2: B.52.2 (PVC 2), B.52.3 (XLPE 2), B.52.4 (PVC 3), B.52.5 (XLPE 3); E–G: B.52.10–13 per isolasi & bahan
    val number = if (!freeAir) 2 + (if (loadedConductors == 3) 2 else 0) + (if (xlpe) 1 else 0)
    else 10 + (if (xlpe) 2 else 0) + (if (cu) 0 else 1)
    val tableKey = "b52_${number}_" + if (cu) "cu" else "al"
    val column = when (method) {
      IecMethod.E -> if (loadedConductors == 2) "E_2" else "E_3"
      IecMethod.F -> if (loadedConductors == 2) "F_2" else "F_3_trefoil"
      IecMethod.F_FLAT -> if (loadedConductors == 2) return null else "F_3_flat"
      IecMethod.G_HORIZ -> if (loadedConductors == 2) return null else "G_horiz"
      IecMethod.G_VERT -> if (loadedConductors == 2) return null else "G_vert"
      else -> method.code
    }
    val amps = IecKhaData.tables[tableKey]?.get(column) ?: return null
    val name = "IEC ${method.code} · ${if (insulation == CableInsulation.PVC) "PVC" else "XLPE"} · $loadedConductors · ${if (cu) "Cu" else "Al"}"
    return KhaTable(name, amps)
  }

  /** Faktor suhu udara B.52.14; memakai baris suhu berikutnya ke atas (aman). null = di atas batas isolasi. */
  fun ambientFactor(insulation: CableInsulation, ambientC: Double): Double? {
    val row = IecKhaData.ambientAir.firstOrNull { it.first >= ambientC - 1e-9 } ?: return null
    return if (insulation == CableInsulation.PVC) row.second else row.third
  }

  /** Faktor pengelompokan B.52.17 untuk susunan 1..5; jumlah sirkit di antara kolom memakai kolom berikutnya (aman). */
  fun groupingFactor(arrangement: Int, circuits: Int): Double? {
    if (arrangement !in 1..IecKhaData.grouping.size || circuits < 1) return null
    val row = IecKhaData.grouping[arrangement - 1]
    val idx = IecKhaData.groupingColumns.indexOfFirst { it >= circuits }.let { if (it < 0) IecKhaData.groupingColumns.lastIndex else it }
    // Susunan 2–5: di atas 9 sirkit tidak ada reduksi tambahan (pakai kolom 9)
    return row[idx] ?: row[IecKhaData.groupingColumns.indexOf(9)]
  }
}
