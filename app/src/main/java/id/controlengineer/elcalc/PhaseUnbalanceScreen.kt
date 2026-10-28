package id.controlengineer.elcalc

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import id.controlengineer.elcalc.core.*

@Composable
fun PhaseUnbalanceScreen() {
  val context = LocalContext.current
  var quantity by rememberSaveable { mutableStateOf(UnbalanceQuantity.VOLTAGE) }
  var val1Text by rememberSaveable { mutableStateOf("398") }
  var val2Text by rememberSaveable { mutableStateOf("400") }
  var val3Text by rememberSaveable { mutableStateOf("410") }

  val outcome = PhaseUnbalanceCalculator.calculate(quantity, parseDecimal(val1Text), parseDecimal(val2Text), parseDecimal(val3Text))
  val vLabel = s(R.string.unb_mode_voltage); val iLabel = s(R.string.unb_mode_current)
  val isV = quantity == UnbalanceQuantity.VOLTAGE
  val unit = if (isV) "V" else "A"
  val disclaimer = s(R.string.independent_disclaimer)

  ScreenColumn {
    Text(s(R.string.unb_title), style = MaterialTheme.typography.titleMedium)
    Text(s(R.string.unb_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    ChipRow(UnbalanceQuantity.entries, quantity, { if (it == UnbalanceQuantity.VOLTAGE) vLabel else iLabel }) {
      quantity = it
      if (it == UnbalanceQuantity.VOLTAGE) { val1Text = "398"; val2Text = "400"; val3Text = "410" } else { val1Text = "24"; val2Text = "25"; val3Text = "27" }
    }

    val isErr = outcome is PhaseUnbalanceOutcome.Failure
    DecimalField(val1Text, { val1Text = it }, stringResource(R.string.unb_phase_label, if (isV) "L1-L2" else "L1", unit), Modifier.fillMaxWidth().testTag("unb_val1_input"), isErr)
    DecimalField(val2Text, { val2Text = it }, stringResource(R.string.unb_phase_label, if (isV) "L2-L3" else "L2", unit), Modifier.fillMaxWidth().testTag("unb_val2_input"), isErr)
    DecimalField(val3Text, { val3Text = it }, stringResource(R.string.unb_phase_label, if (isV) "L3-L1" else "L3", unit), Modifier.fillMaxWidth().testTag("unb_val3_input"), isErr)

    ResultCard {
      when (outcome) {
        is PhaseUnbalanceOutcome.Failure -> Text(s(R.string.err_positive), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("unb_error"))
        is PhaseUnbalanceOutcome.Success -> {
          val r = outcome.result
          ValueRow(R.string.unb_out_avg, "${fixed(r.average, 2)} $unit")
          ValueRow(R.string.unb_out_max_dev, "${fixed(r.maxDeviation, 2)} $unit")
          ValueRow(R.string.unb_out_pct, "${fixed(r.unbalancePercent, 2)} %")
          r.extraTempRisePercent?.let { ValueRow(R.string.unb_out_temp_rise, "+${fixed(it, 2)} %") }

          val statusText = when (r.status) {
            UnbalanceStatus.OK_WITHIN_1_PERCENT -> s(R.string.unb_status_v_ok)
            UnbalanceStatus.WARNING_EXCEEDS_1_PERCENT -> s(R.string.unb_status_v_warn)
            UnbalanceStatus.CRITICAL_EXCEEDS_5_PERCENT -> s(R.string.unb_status_v_crit)
            UnbalanceStatus.CURRENT_NORMAL_WITHIN_10_PERCENT -> s(R.string.unb_status_i_ok)
            UnbalanceStatus.CURRENT_WARNING_EXCEEDS_10_PERCENT -> s(R.string.unb_status_i_warn)
          }
          val isOk = r.status == UnbalanceStatus.OK_WITHIN_1_PERCENT || r.status == UnbalanceStatus.CURRENT_NORMAL_WITHIN_10_PERCENT
          StatusRow("Status", statusText, isOk, "unb_status_text")

          Spacer(Modifier.height(8.dp))
          val shareSummary = buildString {
            appendLine("Panel Calc — 3-Phase Unbalance (NEMA MG 1)")
            appendLine("Phase values: ${fmt(r.v1, 2)} / ${fmt(r.v2, 2)} / ${fmt(r.v3, 2)} $unit")
            appendLine("Avg: ${fixed(r.average, 2)} $unit | Max Dev: ${fixed(r.maxDeviation, 2)} $unit | Unbalance: ${fixed(r.unbalancePercent, 2)} %")
            appendLine("Status: $statusText\n\n$disclaimer")
          }
          OutlinedButton({ sharePlainText(context, shareSummary) }, Modifier.fillMaxWidth().testTag("unb_share_btn")) { Text(s(R.string.share)) }
        }
      }
    }
    InfoCard { Text(s(R.string.unb_derating_note), style = MaterialTheme.typography.bodySmall) }
    ProDisclaimerFooter()
  }
}
