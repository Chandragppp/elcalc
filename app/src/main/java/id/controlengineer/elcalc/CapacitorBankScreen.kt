package id.controlengineer.elcalc

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import id.controlengineer.elcalc.core.*

@Composable
fun CapacitorBankScreen() {
  val context = LocalContext.current
  var pText by rememberSaveable { mutableStateOf("100") }
  var vText by rememberSaveable { mutableStateOf("400") }
  var cos1Text by rememberSaveable { mutableStateOf("0.75") }
  var cos2Text by rememberSaveable { mutableStateOf("0.95") }

  val outcome = CapacitorBankCalculator.calculate(parseDecimal(pText), parseDecimal(vText), parseDecimal(cos1Text), parseDecimal(cos2Text))
  val disclaimer = s(R.string.independent_disclaimer)

  ScreenColumn {
    Text(s(R.string.cap_title), style = MaterialTheme.typography.titleMedium)
    Text(s(R.string.cap_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    TwoCol {
      DecimalField(pText, { pText = it }, R.string.acp_mode_kw, "cap_p_input", outcome is CapacitorBankOutcome.Failure && outcome.error == CapacitorBankError.INVALID_POWER_KW, Modifier.weight(1f))
      DecimalField(vText, { vText = it }, R.string.motor_voltage_label, "cap_v_input", outcome is CapacitorBankOutcome.Failure && outcome.error == CapacitorBankError.INVALID_VOLTAGE, Modifier.weight(1f))
    }
    TwoCol {
      DecimalField(cos1Text, { cos1Text = it }, R.string.cap_cosphi1_label, "cap_cosphi1_input", outcome is CapacitorBankOutcome.Failure && outcome.error == CapacitorBankError.INVALID_INITIAL_COS_PHI, Modifier.weight(1f))
      val errCos2 = outcome is CapacitorBankOutcome.Failure && (outcome.error == CapacitorBankError.INVALID_TARGET_COS_PHI || outcome.error == CapacitorBankError.TARGET_NOT_GREATER_THAN_INITIAL)
      DecimalField(cos2Text, { cos2Text = it }, R.string.cap_cosphi2_label, "cap_cosphi2_input", errCos2, Modifier.weight(1f))
    }

    ResultCard {
      when (outcome) {
        is CapacitorBankOutcome.Failure -> {
          val msg = when (outcome.error) {
            CapacitorBankError.TARGET_NOT_GREATER_THAN_INITIAL -> s(R.string.err_target_cosphi)
            CapacitorBankError.INVALID_INITIAL_COS_PHI, CapacitorBankError.INVALID_TARGET_COS_PHI -> s(R.string.err_cosphi)
            else -> s(R.string.err_positive)
          }
          Text(msg, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("cap_error"))
        }
        is CapacitorBankOutcome.Success -> {
          val r = outcome.result
          ValueRow(R.string.cap_out_q, "${fixed(r.requiredKvar, 2)} kVAR")
          ValueRow("I1 / I2", "${fixed(r.initialLineCurrentA, 2)} A / ${fixed(r.targetLineCurrentA, 2)} A")
          ValueRow(R.string.cap_out_i_saved, "${fixed(r.currentReductionA, 2)} A (${fixed(r.currentReductionPercent, 2)} %)")
          ValueRow("S1 / S2", "${fixed(r.initialApparentKva, 2)} kVA / ${fixed(r.targetApparentKva, 2)} kVA")
          ValueRow("Ic", "${fixed(r.capacitorBankCurrentA, 2)} A")

          HorizontalDivider(Modifier.padding(vertical = 4.dp))
          Text(s(R.string.cap_steps_title), style = MaterialTheme.typography.titleSmall)
          for (st in r.stepSuggestions) {
            Text(
              stringResource(R.string.cap_step_row, st.stepCount, fixed(st.idealStepKvar, 2), fmt(st.standardStepKvar, 1), fmt(st.totalInstalledKvar, 1), fixed(st.nominalStageCurrentA, 2)),
              style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, modifier = Modifier.testTag("cap_step_${st.stepCount}"),
            )
          }
          Spacer(Modifier.height(8.dp))
          val shareSummary = buildString {
            appendLine("Panel Calc — Capacitor Bank (kVAR)")
            appendLine("P = ${fixed(r.activePowerKw, 2)} kW @ ${fmt(r.voltageV, 1)} V | cos φ: ${fixed(r.initialCosPhi, 2)} to ${fixed(r.targetCosPhi, 2)}")
            appendLine("Required Qc: ${fixed(r.requiredKvar, 2)} kVAR | ΔI: ${fixed(r.currentReductionA, 2)} A (${fixed(r.currentReductionPercent, 2)} %)")
            for (st in r.stepSuggestions) appendLine("- ${st.stepCount} steps: ${fmt(st.standardStepKvar, 1)} kVAR × ${st.stepCount} = ${fmt(st.totalInstalledKvar, 1)} kVAR")
            appendLine("\n$disclaimer")
          }
          OutlinedButton({ sharePlainText(context, shareSummary) }, Modifier.fillMaxWidth().testTag("cap_share_btn")) { Text(s(R.string.share)) }
        }
      }
    }
    InfoCard { Text(s(R.string.cap_source_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    ProDisclaimerFooter()
  }
}
