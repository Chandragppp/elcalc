package id.controlengineer.elcalc

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import id.controlengineer.elcalc.core.*

@Composable
fun CableScreen() {
  var phase by rememberSaveable { mutableStateOf(PhaseSystem.THREE_PHASE) }
  var material by rememberSaveable { mutableStateOf(ConductorMaterial.COPPER) }
  var rhoMethod by rememberSaveable { mutableStateOf(ResistivityMethod.IEC_60364_ANNEX_G) }
  var sectionMm2 by rememberSaveable { mutableDoubleStateOf(16.0) }
  var lengthText by rememberSaveable { mutableStateOf("50") }
  var voltageText by rememberSaveable { mutableStateOf("400") }
  var loadMode by rememberSaveable { mutableStateOf(CableLoadInputMode.CURRENT_AMPS) }
  var loadText by rememberSaveable { mutableStateOf("50") }
  var cosPhiText by rememberSaveable { mutableStateOf("0.85") }
  var limitText by rememberSaveable { mutableStateOf("5") }
  var khaText by rememberSaveable { mutableStateOf("") }
  var kTempText by rememberSaveable { mutableStateOf("1.0") }
  var kGroupText by rememberSaveable { mutableStateOf("1.0") }

  val outcome = CableCalculator.calculate(
    phase, material, rhoMethod, sectionMm2, parseDecimal(lengthText), parseDecimal(voltageText),
    loadMode, parseDecimal(loadText), parseDecimal(cosPhiText), parseDecimal(limitText),
    parseDecimal(khaText), khaText.isBlank(), parseDecimal(kTempText), parseDecimal(kGroupText),
  )
  val p1 = s(R.string.phase_1p); val p3 = s(R.string.phase_3p)
  val mCu = s(R.string.cable_material_cu); val mAl = s(R.string.cable_material_al)
  val rIec = s(R.string.cable_rho_iec); val rGuide = s(R.string.cable_rho_guide)
  val lAmps = s(R.string.cable_load_mode_amps); val lKw = s(R.string.cable_load_mode_kw)

  ScreenColumn {
    Text(s(R.string.cable_title), style = MaterialTheme.typography.titleMedium)
    Text(s(R.string.cable_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    ChipRow(PhaseSystem.entries, phase, { if (it == PhaseSystem.SINGLE_PHASE) p1 else p3 }) { phase = it; voltageText = if (it == PhaseSystem.SINGLE_PHASE) "230" else "400" }
    ChipRow(ConductorMaterial.entries, material, { if (it == ConductorMaterial.COPPER) mCu else mAl }) { material = it }
    Text(s(R.string.cable_rho_method_label), style = MaterialTheme.typography.labelLarge)
    ChipRow(ResistivityMethod.entries, rhoMethod, { if (it == ResistivityMethod.IEC_60364_ANNEX_G) rIec else rGuide }) { rhoMethod = it }
    Text(s(R.string.cable_section_label), style = MaterialTheme.typography.labelLarge)
    ChipRow(CableCalculator.STANDARD_SECTIONS_MM2, sectionMm2, { "${fmt(it, 1)} mm²" }) { sectionMm2 = it }

    TwoCol {
      DecimalField(lengthText, { lengthText = it }, R.string.cable_length_label, "cable_length_input", outcome is CableOutcome.Failure && outcome.error == CableError.INVALID_LENGTH, Modifier.weight(1f))
      DecimalField(voltageText, { voltageText = it }, R.string.acp_voltage_label, "cable_voltage_input", outcome is CableOutcome.Failure && outcome.error == CableError.INVALID_VOLTAGE, Modifier.weight(1f))
    }
    ChipRow(CableLoadInputMode.entries, loadMode, { if (it == CableLoadInputMode.CURRENT_AMPS) lAmps else lKw }) { loadMode = it }
    TwoCol {
      val lRes = if (loadMode == CableLoadInputMode.CURRENT_AMPS) R.string.cable_load_amps_label else R.string.acp_mode_kw
      DecimalField(loadText, { loadText = it }, lRes, "cable_load_input", outcome is CableOutcome.Failure && outcome.error == CableError.INVALID_LOAD, Modifier.weight(1f))
      DecimalField(cosPhiText, { cosPhiText = it }, R.string.acp_cosphi_label, "cable_cosphi_input", outcome is CableOutcome.Failure && outcome.error == CableError.INVALID_COS_PHI, Modifier.weight(1f))
    }
    TwoCol {
      DecimalField(limitText, { limitText = it }, R.string.cable_limit_label, "cable_limit_input", outcome is CableOutcome.Failure && outcome.error == CableError.INVALID_LIMIT_PERCENT, Modifier.weight(1f))
      AssistChip({ limitText = "3" }, { Text(s(R.string.cable_limit_lighting)) }, Modifier.padding(top = 8.dp).testTag("cable_limit_3_chip"))
      AssistChip({ limitText = "5" }, { Text(s(R.string.cable_limit_other)) }, Modifier.padding(top = 8.dp).testTag("cable_limit_5_chip"))
    }

    ResultCard {
      Text(s(R.string.cable_kha_title), style = MaterialTheme.typography.titleSmall)
      Text(s(R.string.cable_kha_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      DecimalField(khaText, { khaText = it }, R.string.cable_kha_input_label, "cable_kha_input", outcome is CableOutcome.Failure && outcome.error == CableError.INVALID_KHA_VALUE)
      TwoCol {
        val factorErr = outcome is CableOutcome.Failure && outcome.error == CableError.INVALID_CORRECTION_FACTOR
        DecimalField(kTempText, { kTempText = it }, R.string.cable_k_temp_label, "cable_k_temp_input", factorErr, Modifier.weight(1f))
        DecimalField(kGroupText, { kGroupText = it }, R.string.cable_k_group_label, "cable_k_group_input", factorErr, Modifier.weight(1f))
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
          ValueRow("R (Ω/km)", "${fixed(r.resistancePerKmOhm, 4)} Ω/km")
          ValueRow("ΔV (V)", "${fixed(r.voltageDropV, 2)} V")
          ValueRow(R.string.cable_out_dv_pct, "${fixed(r.voltageDropPercent, 2)} %")
          ValueRow(R.string.cable_out_v_end, "${fixed(r.receivingVoltageV, 2)} V")
          val stText = if (r.withinDropLimit) stringResource(R.string.cable_status_ok, fmt(r.maxDropLimitPercent, 2)) else stringResource(R.string.cable_status_exceed, fmt(r.maxDropLimitPercent, 2))
          StatusRow("Status ΔV", stText, r.withinDropLimit, "cable_status_text")
          ValueRow(R.string.cable_out_min_section, r.minCrossSectionForLimitMm2?.let { "${fmt(it, 1)} mm²" } ?: s(R.string.cable_min_section_exceeds_300))
          r.khaCheck?.let { kc ->
            ValueRow("KHA eff.", "${fixed(kc.deratedKhaA, 2)} A")
            ValueRow("Status KHA", if (kc.passed) s(R.string.cable_kha_ok) else s(R.string.cable_kha_overload))
          }
        }
      }
    }
    InfoCard {
      Text(s(R.string.cable_local_types_title), style = MaterialTheme.typography.titleSmall)
      Text(s(R.string.cable_types_body), style = MaterialTheme.typography.bodySmall)
    }
    Text(s(R.string.cable_source_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}
