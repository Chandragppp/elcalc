package id.controlengineer.elcalc

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import id.controlengineer.elcalc.core.*

/** Layar Tab 0 (Dasar — GRATIS): Hukum Ohm & Daya DC, Daya AC 1 Fasa / 3 Fasa, dan Trafo. */
@Composable
fun BasicScreen(initialSubTab: Int = 0) {
  var subTab by rememberSaveable(initialSubTab) { mutableIntStateOf(initialSubTab.coerceIn(0, 2)) }
  Column(Modifier.fillMaxSize()) {
    TabRow(selectedTabIndex = subTab) {
      Tab(subTab == 0, { subTab = 0 }, Modifier.testTag("subtab_ohm"), text = { Text(s(R.string.subtab_ohm)) })
      Tab(subTab == 1, { subTab = 1 }, Modifier.testTag("subtab_ac_power"), text = { Text(s(R.string.subtab_ac_power)) })
      Tab(subTab == 2, { subTab = 2 }, Modifier.testTag("subtab_trafo"), text = { Text(s(R.string.subtab_trafo)) })
    }
    when (subTab) {
      0 -> OhmSubScreen()
      1 -> AcPowerSubScreen()
      else -> TransformerSubScreen()
    }
  }
}

@Composable
fun OhmSubScreen() {
  var vText by rememberSaveable { mutableStateOf("230") }
  var iText by rememberSaveable { mutableStateOf("10") }
  var rText by rememberSaveable { mutableStateOf("") }
  var pText by rememberSaveable { mutableStateOf("") }

  val vVal = parseDecimal(vText); val iVal = parseDecimal(iText); val rVal = parseDecimal(rText); val pVal = parseDecimal(pText)
  val anyBad = (vText.isNotBlank() && vVal == null) || (iText.isNotBlank() && iVal == null) || (rText.isNotBlank() && rVal == null) || (pText.isNotBlank() && pVal == null)
  val outcome = OhmCalculator.solve(vVal, iVal, rVal, pVal, anyBad)

  ScreenColumn {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
      Text(s(R.string.ohm_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
      TextButton({ vText = ""; iText = ""; rText = ""; pText = "" }, Modifier.testTag("ohm_clear")) { Text(s(R.string.clear)) }
    }
    Text(s(R.string.ohm_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    TwoCol {
      DecimalField(vText, { vText = it }, R.string.ohm_v_label, "ohm_v_input", vText.isNotBlank() && (vVal == null || vVal <= 0.0), Modifier.weight(1f))
      DecimalField(iText, { iText = it }, R.string.ohm_i_label, "ohm_i_input", iText.isNotBlank() && (iVal == null || iVal <= 0.0), Modifier.weight(1f))
    }
    TwoCol {
      DecimalField(rText, { rText = it }, R.string.ohm_r_label, "ohm_r_input", rText.isNotBlank() && (rVal == null || rVal <= 0.0), Modifier.weight(1f))
      DecimalField(pText, { pText = it }, R.string.ohm_p_label, "ohm_p_input", pText.isNotBlank() && (pVal == null || pVal <= 0.0), Modifier.weight(1f))
    }
    when (outcome) {
      is OhmOutcome.Failure -> Text(if (outcome.error == OhmError.NEED_EXACTLY_TWO) s(R.string.ohm_err_need_two) else s(R.string.err_positive), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("ohm_error_text"))
      is OhmOutcome.Success -> {
        val r = outcome.result
        ResultCard {
          ValueRow("V", "${fixed(r.voltageV, 2)} V")
          ValueRow("I", "${fixed(r.currentA, 2)} A")
          ValueRow("R", "${fixed(r.resistanceOhm, 2)} Ω")
          ValueRow("P", "${fixed(r.powerW, 2)} W (${fixed(r.powerW / 1000.0, 3)} kW)")
          ValueRow(R.string.ohm_formula_used, r.formulaUsed)
        }
      }
    }
  }
}

@Composable
fun AcPowerSubScreen() {
  var phaseName by rememberSaveable { mutableStateOf(PhaseSystem.THREE_PHASE.name) }
  var voltageText by rememberSaveable { mutableStateOf("400") }
  var modeName by rememberSaveable { mutableStateOf(AcPowerInputMode.FROM_ACTIVE_POWER_KW.name) }
  var inputText by rememberSaveable { mutableStateOf("15") }
  var cosPhiText by rememberSaveable { mutableStateOf("0.85") }

  val phase = PhaseSystem.valueOf(phaseName)
  val mode = AcPowerInputMode.valueOf(modeName)
  val p1 = s(R.string.phase_1p); val p3 = s(R.string.phase_3p)
  val mI = s(R.string.acp_mode_current); val mKw = s(R.string.acp_mode_kw); val mKva = s(R.string.acp_mode_kva)
  val voltageVal = parseDecimal(voltageText); val inputVal = parseDecimal(inputText); val cosPhiVal = parseDecimal(cosPhiText)
  val outcome = AcPowerCalculator.calculate(phase, voltageVal, mode, inputVal, cosPhiVal)

  ScreenColumn {
    Text(s(R.string.acp_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(s(R.string.acp_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    ChipRow(PhaseSystem.entries, phase, { if (it == PhaseSystem.SINGLE_PHASE) p1 else p3 }) {
      phaseName = it.name; voltageText = if (it == PhaseSystem.SINGLE_PHASE) "230" else "400"
    }
    Text(s(R.string.acp_voltage_label), style = MaterialTheme.typography.labelLarge)
    ChipRow(listOf("220", "230", "380", "400"), voltageText.trim(), { "$it V" }) { voltageText = it }
    DecimalField(voltageText, { voltageText = it }, R.string.acp_voltage_label, "acp_voltage_input", voltageVal == null || voltageVal <= 0.0)

    Text(s(R.string.acp_input_mode_label), style = MaterialTheme.typography.labelLarge)
    val modeLabel: (AcPowerInputMode) -> String = { if (it == AcPowerInputMode.FROM_CURRENT) mI else if (it == AcPowerInputMode.FROM_ACTIVE_POWER_KW) mKw else mKva }
    ChipRow(AcPowerInputMode.entries, mode, modeLabel) { modeName = it.name }
    TwoCol {
      DecimalField(inputText, { inputText = it }, modeLabel(mode), Modifier.weight(1f).testTag("acp_value_input"), inputVal == null || inputVal <= 0.0)
      DecimalField(cosPhiText, { cosPhiText = it }, R.string.acp_cosphi_label, "acp_cosphi_input", cosPhiVal == null || cosPhiVal <= 0.0 || cosPhiVal > 1.0, Modifier.weight(1f))
    }
    when (outcome) {
      is AcPowerOutcome.Failure -> Text(if (outcome.error == AcPowerError.INVALID_COS_PHI) s(R.string.err_cosphi) else s(R.string.err_positive), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("acp_error_text"))
      is AcPowerOutcome.Success -> {
        val r = outcome.result
        ResultCard {
          ValueRow(R.string.acp_out_current, "${fixed(r.currentA, 2)} A")
          ValueRow(R.string.acp_out_p, "${fixed(r.activePowerKw, 2)} kW")
          ValueRow(R.string.acp_out_s, "${fixed(r.apparentPowerKva, 2)} kVA")
          ValueRow(R.string.acp_out_q, "${fixed(r.reactivePowerKvar, 2)} kVAR")
          ValueRow("cos φ / sin φ", "${fixed(r.cosPhi, 2)} / ${fixed(r.sinPhi, 2)}")
        }
      }
    }
  }
}

@Composable
fun TransformerSubScreen() {
  var modeName by rememberSaveable { mutableStateOf(TransformerInputMode.FROM_KVA.name) }
  var inputText by rememberSaveable { mutableStateOf("1000") }
  var vPriText by rememberSaveable { mutableStateOf("20000") }
  var vSecText by rememberSaveable { mutableStateOf("400") }
  var ukText by rememberSaveable { mutableStateOf("6") }

  val mode = TransformerInputMode.valueOf(modeName)
  val mKva = s(R.string.trafo_mode_kva); val mIsec = s(R.string.trafo_mode_isec)
  val inputVal = parseDecimal(inputText); val vPriVal = parseDecimal(vPriText); val vSecVal = parseDecimal(vSecText); val ukVal = parseDecimal(ukText)
  val outcome = TransformerCalculator.calculate(PhaseSystem.THREE_PHASE, mode, inputVal, vPriVal, vSecVal, ukVal)

  ScreenColumn {
    Text(s(R.string.trafo_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(s(R.string.trafo_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    ChipRow(TransformerInputMode.entries, mode, { if (it == TransformerInputMode.FROM_KVA) mKva else mIsec }) { modeName = it.name }
    TwoCol {
      val inRes = if (mode == TransformerInputMode.FROM_KVA) R.string.trafo_kva_label else R.string.trafo_isec_label
      DecimalField(inputText, { inputText = it }, inRes, "trafo_value_input", inputVal == null || inputVal <= 0.0, Modifier.weight(1f))
      DecimalField(ukText, { ukText = it }, R.string.trafo_uk_label, "trafo_uk_input", ukVal == null || ukVal <= 0.0 || ukVal >= 100.0, Modifier.weight(1f))
    }
    TwoCol {
      DecimalField(vPriText, { vPriText = it }, R.string.trafo_vpri_label, "trafo_vpri_input", vPriVal == null || vPriVal <= 0.0, Modifier.weight(1f))
      DecimalField(vSecText, { vSecText = it }, R.string.trafo_vsec_label, "trafo_vsec_input", vSecVal == null || vSecVal <= 0.0, Modifier.weight(1f))
    }
    when (outcome) {
      is TransformerOutcome.Failure -> Text(if (outcome.error == TransformerError.INVALID_UK_PERCENT) s(R.string.err_percent) else s(R.string.err_positive), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("trafo_error_text"))
      is TransformerOutcome.Success -> {
        val r = outcome.result
        ResultCard {
          ValueRow(R.string.trafo_out_kva, "${fixed(r.kva, 2)} kVA")
          ValueRow(R.string.trafo_out_ipri, "${fixed(r.primaryNominalCurrentA, 2)} A")
          ValueRow(R.string.trafo_out_isec, "${fixed(r.secondaryNominalCurrentA, 2)} A")
          ValueRow(R.string.trafo_out_isc, "${fixed(r.secondaryShortCircuitCurrentKa, 2)} kA (${fixed(r.secondaryShortCircuitCurrentA, 0)} A)")
        }
        Text(s(R.string.trafo_formula_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }
}
