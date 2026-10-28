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
fun MotorScreen(initialSubtab: Int = 0) {
  val context = LocalContext.current
  var powerUnit by rememberSaveable { mutableStateOf(MotorPowerUnit.KW) }
  var powerText by rememberSaveable { mutableStateOf("7.5") }
  var voltageText by rememberSaveable { mutableStateOf("400") }
  var cosPhiText by rememberSaveable { mutableStateOf("0.85") }
  var etaText by rememberSaveable { mutableStateOf("0.9") }
  var startMultText by rememberSaveable { mutableStateOf("6") }
  var method by rememberSaveable(initialSubtab) { mutableStateOf(if (initialSubtab == 1) StarterMethod.STAR_DELTA else StarterMethod.DOL) }

  val outcome = MotorCalculator.calculate(parseDecimal(powerText), powerUnit, parseDecimal(voltageText), parseDecimal(cosPhiText), parseDecimal(etaText), method, parseDecimal(startMultText))
  val dolLabel = s(R.string.motor_method_dol); val sdLabel = s(R.string.motor_method_sd)
  val disclaimer = s(R.string.independent_disclaimer)

  ScreenColumn {
    Text(s(R.string.motor_title), style = MaterialTheme.typography.titleMedium)
    Text(s(R.string.motor_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    ChipRow(MotorPowerUnit.entries, powerUnit, { if (it == MotorPowerUnit.KW) "kW" else "HP" }) { powerUnit = it }
    TwoCol {
      val pRes = if (powerUnit == MotorPowerUnit.KW) R.string.motor_power_kw_label else R.string.motor_power_hp_label
      DecimalField(powerText, { powerText = it }, pRes, "motor_power_input", outcome is MotorOutcome.Failure && outcome.error == MotorError.INVALID_POWER, Modifier.weight(1f))
      DecimalField(voltageText, { voltageText = it }, R.string.motor_voltage_label, "motor_voltage_input", outcome is MotorOutcome.Failure && outcome.error == MotorError.INVALID_VOLTAGE, Modifier.weight(1f))
    }
    TwoCol {
      DecimalField(cosPhiText, { cosPhiText = it }, R.string.acp_cosphi_label, "motor_cosphi_input", outcome is MotorOutcome.Failure && outcome.error == MotorError.INVALID_COS_PHI, Modifier.weight(1f))
      DecimalField(etaText, { etaText = it }, R.string.motor_eta_label, "motor_eta_input", outcome is MotorOutcome.Failure && outcome.error == MotorError.INVALID_EFFICIENCY, Modifier.weight(1f))
    }
    DecimalField(startMultText, { startMultText = it }, R.string.motor_start_mult_label, "motor_mult_input", outcome is MotorOutcome.Failure && outcome.error == MotorError.INVALID_START_MULTIPLIER)
    Text(s(R.string.motor_method_label), style = MaterialTheme.typography.labelLarge)
    ChipRow(StarterMethod.entries, method, { if (it == StarterMethod.DOL) dolLabel else sdLabel }) { method = it }

    ResultCard {
      when (outcome) {
        is MotorOutcome.Failure -> {
          val msg = when (outcome.error) {
            MotorError.INVALID_COS_PHI -> s(R.string.err_cosphi)
            MotorError.INVALID_EFFICIENCY -> s(R.string.err_eta)
            MotorError.INVALID_START_MULTIPLIER -> s(R.string.err_mult)
            else -> s(R.string.err_positive)
          }
          Text(msg, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("motor_error"))
        }
        is MotorOutcome.Success -> {
          val r = outcome.result
          val ex = s(R.string.motor_std_exceeds)
          val kmStd = r.mainContactorStdRatingA?.let { stringResource(R.string.motor_std_nearest, it) } ?: ex
          val kdStd = r.deltaContactorStdRatingA?.let { stringResource(R.string.motor_std_nearest, it) } ?: ex
          val kyStd = r.starContactorStdRatingA?.let { stringResource(R.string.motor_std_nearest, it) } ?: ex
          val dMin = r.deltaContactorMinCurrentA; val yMin = r.starContactorMinCurrentA

          ValueRow(R.string.motor_out_power_conv, "${fixed(r.powerKw, 2)} kW / ${fixed(r.powerHp, 2)} HP")
          ValueRow(R.string.motor_out_in, "${fixed(r.nominalCurrentInA, 2)} A")
          ValueRow(R.string.motor_out_start_i, "${fixed(r.estimatedActualStartCurrentA, 2)} A")
          ValueRow(R.string.motor_out_km, "${fixed(r.mainContactorMinCurrentA, 2)} A ($kmStd)")
          if (r.starterMethod == StarterMethod.STAR_DELTA && dMin != null && yMin != null) {
            ValueRow(R.string.motor_out_kdelta, "${fixed(dMin, 2)} A ($kdStd)")
            ValueRow(R.string.motor_out_kstar, "${fixed(yMin, 2)} A ($kyStd)")
          }
          ValueRow(R.string.motor_out_tor, "${fixed(r.torSettingCurrentA, 2)} A (${fixed(r.torRangeMinA, 2)}..${fixed(r.torRangeMaxA, 2)} A)")
          val brkStd = if (r.recommendedBreakerStdMinA != null && r.recommendedBreakerStdMaxA != null) "std ${r.recommendedBreakerStdMinA}..${r.recommendedBreakerStdMaxA} A" else ex
          ValueRow(R.string.motor_out_breaker, "${fixed(r.breakerMinRangeA, 2)}..${fixed(r.breakerMaxRangeA, 2)} A ($brkStd)")

          Spacer(Modifier.height(8.dp))
          val shareSummary = buildString {
            appendLine("Panel Calc — 3-Phase Motor (${if (r.starterMethod == StarterMethod.DOL) "DOL" else "Star-Delta"})")
            appendLine("P: ${fixed(r.powerKw, 2)} kW (${fixed(r.powerHp, 2)} HP) @ ${fmt(r.voltageV, 1)} V, cos φ = ${fmt(r.cosPhi, 2)}, η = ${fmt(r.efficiency, 2)}")
            appendLine("In: ${fixed(r.nominalCurrentInA, 2)} A | Start: ${fixed(r.estimatedActualStartCurrentA, 2)} A | KM: >= ${fixed(r.mainContactorMinCurrentA, 2)} A (${r.mainContactorStdRatingA ?: "-"} A AC-3)")
            if (r.starterMethod == StarterMethod.STAR_DELTA && dMin != null && yMin != null) {
              appendLine("KΔ: >= ${fixed(dMin, 2)} A (${r.deltaContactorStdRatingA ?: "-"} A) | KY: >= ${fixed(yMin, 2)} A (${r.starContactorStdRatingA ?: "-"} A)")
            }
            appendLine("TOR: ${fixed(r.torSettingCurrentA, 2)} A | Breaker: ${fixed(r.breakerMinRangeA, 2)}..${fixed(r.breakerMaxRangeA, 2)} A\n$disclaimer")
          }
          OutlinedButton({ sharePlainText(context, shareSummary) }, Modifier.fillMaxWidth().testTag("motor_share_btn")) { Text(s(R.string.share)) }
          ReportActionButtons(
            isPro = true,
            onUnlockPro = {},
            onGenerate = { ctx, fmt, meta ->
              when (fmt) {
                ReportFormat.PDF -> ReportExporter.generateMotorPdf(ctx, r, meta)
                ReportFormat.XLSX -> ReportExporter.generateMotorXlsx(ctx, r, meta)
              }
            },
          )
        }
      }
    }
    InfoCard {
      Text(s(R.string.motor_curve_note), style = MaterialTheme.typography.bodySmall)
      Text(s(R.string.motor_source_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    ProDisclaimerFooter()
  }
}
