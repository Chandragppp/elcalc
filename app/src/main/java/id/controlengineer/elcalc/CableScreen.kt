package id.controlengineer.elcalc

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.controlengineer.elcalc.core.*
import kotlinx.coroutines.launch

@Composable
fun CableScreen() {
  var circuit by rememberSaveable { mutableStateOf(CableCircuit.THREE_PHASE) }
  var material by rememberSaveable { mutableStateOf(ConductorMaterial.COPPER) }
  var rhoMethod by rememberSaveable { mutableStateOf(ResistivityMethod.IEC_60364_ANNEX_G) }
  var lengthText by rememberSaveable { mutableStateOf("50") }
  var voltageText by rememberSaveable { mutableStateOf("400") }
  var loadMode by rememberSaveable { mutableStateOf(CableLoadInputMode.CURRENT_AMPS) }
  var loadText by rememberSaveable { mutableStateOf("50") }
  var cosPhiText by rememberSaveable { mutableStateOf("0.85") }
  var limitText by rememberSaveable { mutableStateOf("5") }
  var kTempText by rememberSaveable { mutableStateOf("1.0") }
  var kGroupText by rememberSaveable { mutableStateOf("1.0") }

  // Tabel KHA milik pengguna (tersimpan di perangkat)
  val context = LocalContext.current
  val store = remember { KhaTableStore(context) }
  val scope = rememberCoroutineScope()
  val tables by store.tables.collectAsState(initial = emptyList())
  val selectedName by store.selectedName.collectAsState(initial = null)
  val khaTable = tables.firstOrNull { it.name == selectedName }
  // null = dialog tertutup; "" = tabel baru; selain itu = ubah tabel bernama itu
  var editing by rememberSaveable { mutableStateOf<String?>(null) }

  val isDc = circuit == CableCircuit.DC
  val outcome = CableCalculator.calculate(
    circuit, material, rhoMethod, parseDecimal(lengthText), parseDecimal(voltageText),
    loadMode, parseDecimal(loadText), parseDecimal(cosPhiText), parseDecimal(limitText),
    khaTable, parseDecimal(kTempText), parseDecimal(kGroupText),
  )
  val cDc = s(R.string.cable_circuit_dc); val p1 = s(R.string.phase_1p); val p3 = s(R.string.phase_3p)
  val mCu = s(R.string.cable_material_cu); val mAl = s(R.string.cable_material_al)
  val rIec = s(R.string.cable_rho_iec); val rGuide = s(R.string.cable_rho_guide)
  val lAmps = s(R.string.cable_load_mode_amps); val lKw = s(R.string.cable_load_mode_kw)
  val noTable = s(R.string.cable_kha_none)
  fun isErr(e: CableError) = outcome is CableOutcome.Failure && outcome.error == e

  ScreenColumn {
    Text(s(R.string.cable_title), style = MaterialTheme.typography.titleMedium)
    Text(s(R.string.cable_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

    // Jenis rangkaian: DC kontrol panel, 1 fasa, 3 fasa
    ChipRow(CableCircuit.entries, circuit, { when (it) { CableCircuit.DC -> cDc; CableCircuit.SINGLE_PHASE -> p1; else -> p3 } }) {
      circuit = it
      voltageText = when (it) { CableCircuit.DC -> "24"; CableCircuit.SINGLE_PHASE -> "230"; else -> "400" }
    }
    val presets = when (circuit) {
      CableCircuit.DC -> CableCalculator.DC_VOLTAGE_PRESETS
      CableCircuit.SINGLE_PHASE -> CableCalculator.AC_1P_VOLTAGE_PRESETS
      else -> CableCalculator.AC_3P_VOLTAGE_PRESETS
    }
    ChipRow(presets, parseDecimal(voltageText) ?: -1.0, { "${fmt(it, 0)} V" }) { voltageText = fmt(it, 0) }

    TwoCol {
      DecimalField(voltageText, { voltageText = it }, R.string.acp_voltage_label, "cable_voltage_input", isErr(CableError.INVALID_VOLTAGE), Modifier.weight(1f))
      DecimalField(lengthText, { lengthText = it }, R.string.cable_length_label, "cable_length_input", isErr(CableError.INVALID_LENGTH), Modifier.weight(1f))
    }
    ChipRow(CableLoadInputMode.entries, loadMode, { if (it == CableLoadInputMode.CURRENT_AMPS) lAmps else lKw }) { loadMode = it }
    TwoCol {
      val lRes = if (loadMode == CableLoadInputMode.CURRENT_AMPS) R.string.cable_load_amps_label else R.string.acp_mode_kw
      DecimalField(loadText, { loadText = it }, lRes, "cable_load_input", isErr(CableError.INVALID_LOAD), Modifier.weight(1f))
      // DC tidak memakai cos φ
      if (!isDc) DecimalField(cosPhiText, { cosPhiText = it }, R.string.acp_cosphi_label, "cable_cosphi_input", isErr(CableError.INVALID_COS_PHI), Modifier.weight(1f))
    }
    TwoCol {
      DecimalField(limitText, { limitText = it }, R.string.cable_limit_label, "cable_limit_input", isErr(CableError.INVALID_LIMIT_PERCENT), Modifier.weight(1f))
      AssistChip({ limitText = "3" }, { Text(s(R.string.cable_limit_lighting)) }, Modifier.padding(top = 8.dp).testTag("cable_limit_3_chip"))
      AssistChip({ limitText = "5" }, { Text(s(R.string.cable_limit_other)) }, Modifier.padding(top = 8.dp).testTag("cable_limit_5_chip"))
    }
    ChipRow(ConductorMaterial.entries, material, { if (it == ConductorMaterial.COPPER) mCu else mAl }) { material = it }
    Text(s(R.string.cable_rho_method_label), style = MaterialTheme.typography.labelLarge)
    ChipRow(ResistivityMethod.entries, rhoMethod, { if (it == ResistivityMethod.IEC_60364_ANNEX_G) rIec else rGuide }) { rhoMethod = it }

    // Pilih / buat tabel KHA
    ResultCard {
      Text(s(R.string.cable_kha_title), style = MaterialTheme.typography.titleSmall)
      Text(s(R.string.cable_kha_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      ChipRow(listOf<String?>(null) + tables.map { it.name }, khaTable?.name, { it ?: noTable }) { name -> scope.launch { store.select(name) } }
      TwoCol {
        OutlinedButton({ editing = "" }, Modifier.testTag("btn_kha_new")) { Text(s(R.string.cable_kha_new)) }
        if (khaTable != null) OutlinedButton({ editing = khaTable.name }, Modifier.testTag("btn_kha_edit")) { Text(s(R.string.cable_kha_edit)) }
      }
      if (khaTable != null) {
        TwoCol {
          val factorErr = isErr(CableError.INVALID_CORRECTION_FACTOR)
          DecimalField(kTempText, { kTempText = it }, R.string.cable_k_temp_label, "cable_k_temp_input", factorErr, Modifier.weight(1f))
          DecimalField(kGroupText, { kGroupText = it }, R.string.cable_k_group_label, "cable_k_group_input", factorErr, Modifier.weight(1f))
        }
      }
    }

    ResultCard {
      when (outcome) {
        is CableOutcome.Failure -> {
          val msg = when (outcome.error) {
            CableError.INVALID_COS_PHI -> s(R.string.err_cosphi)
            CableError.INVALID_LIMIT_PERCENT -> s(R.string.err_percent)
            CableError.INVALID_CORRECTION_FACTOR -> s(R.string.err_factor)
            else -> s(R.string.err_positive)
          }
          Text(msg, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("cable_error"))
        }
        is CableOutcome.Success -> {
          val r = outcome.result
          ValueRow(R.string.cable_load_amps_label, "${fixed(r.loadCurrentA, 2)} A")
          Text(s(if (r.khaChecked) R.string.cable_rec_title else R.string.cable_rec_title_dv_only), style = MaterialTheme.typography.titleSmall)
          val rec = r.recommended
          if (rec != null) {
            Text(
              "${fmt(rec.sectionMm2, 2)} mm²",
              style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
              color = if (r.khaChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
              modifier = Modifier.testTag("cable_rec_text"),
            )
          } else {
            Text(s(R.string.cable_none_fits), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("cable_rec_text"))
          }
          // Tanpa tabel KHA: hasil BELUM aman secara termal
          if (!r.khaChecked) {
            Text(s(R.string.cable_kha_warning), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("cable_kha_warning"))
          }
          Text(s(R.string.cable_compare_title), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
          r.smaller?.let { SectionOptionRow(it, false, r.khaChecked, "cable_opt_smaller") }
          rec?.let { SectionOptionRow(it, true, r.khaChecked, "cable_opt_rec") }
          r.larger?.let { SectionOptionRow(it, false, r.khaChecked, "cable_opt_larger") }
          Text(s(R.string.cable_rec_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }

    InfoCard {
      Text(s(R.string.cable_local_types_title), style = MaterialTheme.typography.titleSmall)
      Text(s(R.string.cable_types_body), style = MaterialTheme.typography.bodySmall)
    }
    Text(s(R.string.cable_source_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }

  editing?.let { name ->
    KhaTableEditorDialog(
      initial = tables.firstOrNull { it.name == name && name.isNotEmpty() },
      existingNames = tables.map { it.name },
      onSave = { t -> scope.launch { store.save(t, name.ifEmpty { null }) }; editing = null },
      onDelete = { scope.launch { store.delete(name) }; editing = null },
      onDismiss = { editing = null },
    )
  }
}

/** Satu baris perbandingan ukuran: penampang, ΔV, dan (bila ada tabel) KHA efektif dengan sisa/kurang. */
@Composable
private fun SectionOptionRow(o: SectionOption, isRecommended: Boolean, khaChecked: Boolean, tag: String) {
  val margin = if (o.withinLimit) stringResource(R.string.cable_margin_left, fixed(o.marginPercent, 2))
  else stringResource(R.string.cable_margin_over, fixed(-o.marginPercent, 2))
  val title = "${fmt(o.sectionMm2, 2)} mm²" + if (isRecommended) " · ${s(R.string.cable_tag_rec)}" else ""
  val ok = MaterialTheme.colorScheme.primary; val bad = MaterialTheme.colorScheme.error
  Column(Modifier.padding(vertical = 4.dp).semantics(mergeDescendants = true) {}.testTag(tag)) {
    Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = if (isRecommended) FontWeight.Bold else FontWeight.Normal)
    Text(
      "ΔV ${fixed(o.voltageDropPercent, 2)} % (${fixed(o.voltageDropV, 2)} V) · $margin",
      fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium,
      color = if (o.withinLimit) ok else bad,
    )
    if (khaChecked) {
      val kha = o.deratedKhaA
      val khaText = when {
        kha == null -> s(R.string.cable_kha_missing)
        o.khaOk == true -> stringResource(R.string.cable_kha_left, fixed(kha, 1), fixed(o.khaMarginA!!, 1))
        else -> stringResource(R.string.cable_kha_short, fixed(kha, 1), fixed(-o.khaMarginA!!, 1))
      }
      Text(khaText, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium, color = if (o.khaOk == true) ok else bad)
    }
  }
}

/** Dialog isi tabel KHA: nama + nilai KHA (A) per penampang; ukuran yang kosong dianggap tidak tersedia. */
@Composable
private fun KhaTableEditorDialog(
  initial: KhaTable?, existingNames: List<String>,
  onSave: (KhaTable) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit,
) {
  var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
  val values = remember {
    mutableStateMapOf<Double, String>().apply {
      CableCalculator.STANDARD_SECTIONS_MM2.forEach { sec -> put(sec, initial?.ampsFor(sec)?.let { fmt(it, 2) } ?: "") }
    }
  }
  val cleanName = name.replace("|", " ").trim()
  val nameTaken = cleanName != initial?.name && cleanName in existingNames
  val amps = values.mapNotNull { (sec, txt) -> parseDecimal(txt)?.takeIf { it > 0.0 }?.let { sec to it } }.toMap()
  val canSave = cleanName.isNotEmpty() && !nameTaken && amps.isNotEmpty()

  AlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier.testTag("kha_dialog"),
    title = { Text(s(R.string.cable_kha_dialog_title)) },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState())) {
        Text(s(R.string.cable_kha_dialog_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        DecimalField(name, { name = it }, s(R.string.cable_kha_name), Modifier.padding(top = 8.dp).testTag("kha_name_input"), nameTaken)
        CableCalculator.STANDARD_SECTIONS_MM2.chunked(2).forEach { pair ->
          TwoCol {
            pair.forEach { sec ->
              DecimalField(values[sec] ?: "", { values[sec] = it }, "${fmt(sec, 2)} mm²", Modifier.weight(1f).testTag("kha_field_${fmt(sec, 2)}"))
            }
            if (pair.size == 1) Column(Modifier.weight(1f)) {}
          }
        }
      }
    },
    confirmButton = {
      TextButton({ onSave(KhaTable(cleanName, amps)) }, enabled = canSave, modifier = Modifier.testTag("btn_kha_save")) { Text(s(R.string.cable_kha_save)) }
    },
    dismissButton = {
      if (initial != null) TextButton(onDelete, Modifier.testTag("btn_kha_delete")) { Text(s(R.string.cable_kha_delete)) }
      TextButton(onDismiss) { Text(s(R.string.cable_kha_cancel)) }
    },
  )
}
