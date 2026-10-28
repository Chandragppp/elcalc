package id.controlengineer.elcalc

import android.content.*
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.util.Locale

@Composable fun s(id: Int): String = stringResource(id)

@Composable
fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
fun TwoCol(content: @Composable RowScope.() -> Unit) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
fun ResultCard(content: @Composable ColumnScope.() -> Unit) {
  Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = content) }
}

@Composable
fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
  Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
  }
}

/** Baris label + nilai monospace; ketuk untuk menyalin nilai. */
@Composable
fun ValueRow(label: String, value: String, modifier: Modifier = Modifier) {
  val clipboard = LocalClipboard.current; val scope = rememberCoroutineScope(); val context = LocalContext.current; val copied = s(R.string.copied)
  Row(modifier.fillMaxWidth().clickable {
    scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, value))); Toast.makeText(context, copied, Toast.LENGTH_SHORT).show() }
  }.padding(vertical = 6.dp)) {
    if (label.isNotEmpty()) Text(label, Modifier.width(152.dp), style = MaterialTheme.typography.labelLarge)
    Text(value, Modifier.weight(1f).testTag("value:$label"), fontFamily = FontFamily.Monospace)
  }
}

@Composable fun ValueRow(labelRes: Int, value: String) = ValueRow(s(labelRes), value)

@Composable
fun StatusRow(label: String, text: String, isOk: Boolean, tag: String) {
  Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
    Text(label, Modifier.weight(0.44f), style = MaterialTheme.typography.labelLarge)
    Text(text, Modifier.weight(0.56f).testTag(tag), color = if (isOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, enabled: (T) -> Boolean = { true }, onSelect: (T) -> Unit) {
  FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    for (o in options) FilterChip(o == selected, { onSelect(o) }, { Text(label(o)) }, Modifier.testTag("chip:${label(o)}"), enabled(o))
  }
}

/** Kotak input angka/teks monospace satu baris. */
@Composable
fun DecimalField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, isError: Boolean = false) {
  OutlinedTextField(
    value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true, isError = isError,
    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text), modifier = modifier,
  )
}

@Composable
fun DecimalField(value: String, onChange: (String) -> Unit, labelRes: Int, tag: String, isError: Boolean = false, modifier: Modifier = Modifier.fillMaxWidth()) {
  DecimalField(value, onChange, s(labelRes), modifier.testTag(tag), isError)
}

@Composable
fun ProDisclaimerFooter() {
  Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
    Text(s(R.string.independent_disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(12.dp).testTag("pro_disclaimer"))
  }
}

fun sharePlainText(context: Context, text: String) {
  val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
  context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Angka desimal dengan titik, maksimal [decimals] digit, tanpa nol di belakang. */
fun fmt(v: Double, decimals: Int = 4): String {
  if (v.isNaN() || v.isInfinite()) return v.toString()
  var str = String.format(Locale.ROOT, "%.${decimals}f", v)
  if (str.contains('.')) str = str.trimEnd('0').trimEnd('.')
  return if (str == "-0") "0" else str
}

/** Angka dengan jumlah desimal tetap (mis. 14.15 A, 69.15 kVAR), titik sebagai pemisah. */
fun fixed(v: Double, decimals: Int): String {
  if (v.isNaN() || v.isInfinite()) return v.toString()
  val str = String.format(Locale.ROOT, "%.${decimals}f", v)
  return if (str.trimStart('-').all { it == '0' || it == '.' }) str.trimStart('-') else str
}

/** Menerima koma atau titik sebagai pemisah desimal. */
fun parseDecimal(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()
