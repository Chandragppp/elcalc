package id.controlengineer.elcalc

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import id.controlengineer.elcalc.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ReportFormat { PDF, XLSX }

data class ReportMeta(
  val circuitName: String = "",
  val author: String = "",
  val company: String = "",
  val location: String = "",
  val notes: String = "",
)

private val Context.reportMetaDataStore by preferencesDataStore(name = "report_meta")

class ReportMetaStore(private val context: Context) {
  private val keyAuthor = stringPreferencesKey("author")
  private val keyCompany = stringPreferencesKey("company")
  private val keyLocation = stringPreferencesKey("location")
  private val keyNotes = stringPreferencesKey("notes")
  private val keyCircuit = stringPreferencesKey("circuit_name")

  val metaFlow: Flow<ReportMeta> = context.reportMetaDataStore.data.map { prefs ->
    ReportMeta(
      circuitName = prefs[keyCircuit] ?: "",
      author = prefs[keyAuthor] ?: "",
      company = prefs[keyCompany] ?: "",
      location = prefs[keyLocation] ?: "",
      notes = prefs[keyNotes] ?: "",
    )
  }

  suspend fun save(meta: ReportMeta) {
    context.reportMetaDataStore.edit { prefs ->
      prefs[keyCircuit] = meta.circuitName
      prefs[keyAuthor] = meta.author
      prefs[keyCompany] = meta.company
      prefs[keyLocation] = meta.location
      prefs[keyNotes] = meta.notes
    }
  }
}

data class CableReportInput(
  val circuit: CableCircuit,
  val material: ConductorMaterial,
  val rhoMethod: ResistivityMethod,
  val voltageV: Double,
  val lengthM: Double,
  val loadMode: CableLoadInputMode,
  val loadValue: Double,
  val cosPhi: Double,
  val maxDropPercent: Double,
  val useIecTable: Boolean,
  val userTableName: String?,
  val insulation: CableInsulation,
  val loadedConductors: Int,
  val iecMethod: IecMethod,
  val autoFactors: Boolean,
  val ambientTempC: Double?,
  val circuitCount: Int?,
  val groupingArrangementLabel: String,
  val kTemp: Double?,
  val kGroup: Double?,
  val result: CableSizingResult,
)

@Composable
fun ReportActionButtons(
  isPro: Boolean,
  onUnlockPro: () -> Unit,
  onGenerate: (Context, ReportFormat, ReportMeta) -> File,
) {
  val context = LocalContext.current
  val store = remember { ReportMetaStore(context) }
  val savedMeta by store.metaFlow.collectAsState(initial = ReportMeta())
  val scope = rememberCoroutineScope()
  var requestedFormat by rememberSaveable { mutableStateOf<ReportFormat?>(null) }

  TwoCol {
    OutlinedButton(
      onClick = { if (isPro) requestedFormat = ReportFormat.PDF else onUnlockPro() },
      modifier = Modifier.weight(1f).testTag("btn_report_pdf"),
    ) {
      Text(s(R.string.report_btn_pdf))
    }
    OutlinedButton(
      onClick = { if (isPro) requestedFormat = ReportFormat.XLSX else onUnlockPro() },
      modifier = Modifier.weight(1f).testTag("btn_report_xlsx"),
    ) {
      Text(s(R.string.report_btn_xlsx))
    }
  }

  requestedFormat?.let { fmt ->
    ReportHeaderDialog(
      initial = savedMeta,
      onConfirm = { meta ->
        requestedFormat = null
        scope.launch {
          store.save(meta)
          val file = onGenerate(context, fmt, meta)
          ReportExporter.shareReportFile(context, file, fmt)
        }
      },
      onDismiss = { requestedFormat = null },
    )
  }
}

@Composable
fun ReportHeaderDialog(
  initial: ReportMeta,
  onConfirm: (ReportMeta) -> Unit,
  onDismiss: () -> Unit,
) {
  var circuitName by rememberSaveable(initial.circuitName) { mutableStateOf(initial.circuitName) }
  var author by rememberSaveable(initial.author) { mutableStateOf(initial.author) }
  var company by rememberSaveable(initial.company) { mutableStateOf(initial.company) }
  var location by rememberSaveable(initial.location) { mutableStateOf(initial.location) }
  var notes by rememberSaveable(initial.notes) { mutableStateOf(initial.notes) }

  AlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier.testTag("report_header_dialog"),
    title = { Text(s(R.string.report_dialog_title)) },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          s(R.string.report_dialog_hint),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DecimalField(
          value = circuitName,
          onValueChange = { circuitName = it },
          label = s(R.string.report_field_circuit),
          modifier = Modifier.fillMaxWidth().testTag("report_circuit_input"),
          keyboardType = KeyboardType.Text,
        )
        DecimalField(
          value = author,
          onValueChange = { author = it },
          label = s(R.string.report_field_author),
          modifier = Modifier.fillMaxWidth().testTag("report_author_input"),
          keyboardType = KeyboardType.Text,
        )
        DecimalField(
          value = company,
          onValueChange = { company = it },
          label = s(R.string.report_field_company),
          modifier = Modifier.fillMaxWidth().testTag("report_company_input"),
          keyboardType = KeyboardType.Text,
        )
        DecimalField(
          value = location,
          onValueChange = { location = it },
          label = s(R.string.report_field_location),
          modifier = Modifier.fillMaxWidth().testTag("report_location_input"),
          keyboardType = KeyboardType.Text,
        )
        DecimalField(
          value = notes,
          onValueChange = { notes = it },
          label = s(R.string.report_field_notes),
          modifier = Modifier.fillMaxWidth().testTag("report_notes_input"),
          keyboardType = KeyboardType.Text,
        )
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          onConfirm(
            ReportMeta(
              circuitName = circuitName.trim(),
              author = author.trim(),
              company = company.trim(),
              location = location.trim(),
              notes = notes.trim(),
            )
          )
        },
        modifier = Modifier.testTag("btn_report_confirm"),
      ) {
        Text(s(R.string.report_btn_generate))
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.testTag("btn_report_cancel"),
      ) {
        Text(s(R.string.cable_kha_cancel))
      }
    },
  )
}

object ReportExporter {
  private fun formatDisplayDate(now: Date): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(now)

  private fun formatFileTimestamp(now: Date): String =
    SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(now)

  private fun sanitizeTag(raw: String): String {
    val slug = raw.lowercase(Locale.ROOT)
      .replace(Regex("[^a-z0-9]+"), "-")
      .trim('-')
    return if (slug.isEmpty()) "calc" else slug.take(24)
  }

  fun createReportFile(context: Context, kind: String, meta: ReportMeta, ext: String, now: Date = Date()): File {
    val dir = File(context.cacheDir, "reports").apply { mkdirs() }
    val tag = sanitizeTag(meta.circuitName.ifBlank { meta.company })
    val ts = formatFileTimestamp(now)
    return File(dir, "panelcalc_${kind}_${tag}_${ts}.$ext")
  }

  fun shareReportFile(context: Context, file: File, format: ReportFormat) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val mime = when (format) {
      ReportFormat.PDF -> "application/pdf"
      ReportFormat.XLSX -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    }
    val send = Intent(Intent.ACTION_SEND).apply {
      type = mime
      putExtra(Intent.EXTRA_STREAM, uri)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, null))
  }

  private fun headerPairs(context: Context, meta: ReportMeta, dateStr: String): List<Pair<String, String>> = buildList {
    add(context.getString(R.string.report_label_date) to dateStr)
    if (meta.circuitName.isNotBlank()) add(context.getString(R.string.report_field_circuit) to meta.circuitName)
    if (meta.author.isNotBlank()) add(context.getString(R.string.report_field_author) to meta.author)
    if (meta.company.isNotBlank()) add(context.getString(R.string.report_field_company) to meta.company)
    if (meta.location.isNotBlank()) add(context.getString(R.string.report_field_location) to meta.location)
    if (meta.notes.isNotBlank()) add(context.getString(R.string.report_field_notes) to meta.notes)
  }

  private fun appendMetaRowsXlsx(
    rows: MutableList<List<Any?>>,
    boldRows: MutableSet<Int>,
    title: String,
    headerList: List<Pair<String, String>>,
  ) {
    boldRows.add(rows.size)
    rows.add(listOf(title))
    for ((k, v) in headerList) {
      rows.add(listOf(k, v))
    }
    rows.add(emptyList())
  }

  // ==================== 1. CABLE SIZING REPORT ====================

  fun generateCablePdf(context: Context, input: CableReportInput, meta: ReportMeta, now: Date = Date()): File {
    val dateStr = formatDisplayDate(now)
    val footerLeft = "${context.getString(R.string.report_footer_brand)} · $dateStr"
    val pdf = PdfReportBuilder(footerLeft)
    val r = input.result
    val ktVal = input.kTemp ?: 1.0
    val kgVal = input.kGroup ?: 1.0

    val title = context.getString(R.string.cable_title)
    pdf.addDocumentHeader(title, meta.circuitName.takeIf { it.isNotBlank() })

    pdf.addSectionTitle(context.getString(R.string.report_section_header))
    pdf.addKeyValueBlock(headerPairs(context, meta, dateStr))

    val circuitLabel = when (input.circuit) {
      CableCircuit.DC -> context.getString(R.string.cable_circuit_dc)
      CableCircuit.SINGLE_PHASE -> context.getString(R.string.phase_1p)
      CableCircuit.THREE_PHASE -> context.getString(R.string.phase_3p)
    }
    val matLabel = if (input.material == ConductorMaterial.COPPER) context.getString(R.string.cable_material_cu) else context.getString(R.string.cable_material_al)
    val rhoLabel = if (input.rhoMethod == ResistivityMethod.IEC_60364_ANNEX_G) context.getString(R.string.cable_rho_iec) else context.getString(R.string.cable_rho_guide)

    pdf.addSectionTitle(context.getString(R.string.report_section_inputs))
    val inputPairs = buildList {
      add(context.getString(R.string.report_kha_circuit_label) to circuitLabel)
      add(context.getString(R.string.acp_voltage_label) to "${fmt(input.voltageV, 1)} V")
      if (input.loadMode == CableLoadInputMode.CURRENT_AMPS) {
        add(context.getString(R.string.cable_load_amps_label) to "${fixed(input.loadValue, 2)} A")
      } else {
        add(context.getString(R.string.acp_mode_kw) to "${fixed(input.loadValue, 2)} kW (${fixed(r.loadCurrentA, 2)} A)")
      }
      if (input.circuit != CableCircuit.DC) {
        add(context.getString(R.string.acp_cosphi_label) to fmt(input.cosPhi, 2))
      }
      add(context.getString(R.string.cable_length_label) to "${fmt(input.lengthM, 2)} m")
      add(context.getString(R.string.cable_limit_label) to "${fmt(input.maxDropPercent, 2)} %")
      add(context.getString(R.string.report_material_label) to matLabel)
      add(context.getString(R.string.cable_rho_method_label) to "$rhoLabel (ρ = ${fmt(r.rhoOhmMm2PerM, 4)} Ω·mm²/m)")
    }
    pdf.addKeyValueBlock(inputPairs)

    pdf.addSectionTitle(context.getString(R.string.report_section_kha))
    val khaPairs = buildList {
      if (input.useIecTable) {
        val insLabel = if (input.insulation == CableInsulation.PVC) context.getString(R.string.cable_insul_pvc) else context.getString(R.string.cable_insul_xlpe)
        val loadedLabel = if (input.loadedConductors == 2) context.getString(R.string.cable_loaded_2) else context.getString(R.string.cable_loaded_3)
        add(context.getString(R.string.report_kha_source_label) to context.getString(R.string.cable_kha_iec))
        add(context.getString(R.string.report_kha_insulation_label) to insLabel)
        add(context.getString(R.string.cable_loaded_label) to loadedLabel)
        add(context.getString(R.string.cable_method_label) to input.iecMethod.code)
        if (input.autoFactors) {
          add(context.getString(R.string.cable_ambient_label) to "${input.ambientTempC?.let { fmt(it, 1) } ?: "-"} °C (k_t = ${fmt(ktVal, 2)})")
          add(context.getString(R.string.cable_circuits_label) to "${input.circuitCount ?: 1} (${input.groupingArrangementLabel}, k_g = ${fmt(kgVal, 2)})")
        } else {
          add(context.getString(R.string.cable_k_temp_label) to fmt(ktVal, 2))
          add(context.getString(R.string.cable_k_group_label) to fmt(kgVal, 2))
        }
      } else {
        add(context.getString(R.string.report_kha_source_label) to (input.userTableName ?: "-"))
        add(context.getString(R.string.cable_k_temp_label) to fmt(ktVal, 2))
        add(context.getString(R.string.cable_k_group_label) to fmt(kgVal, 2))
      }
    }
    pdf.addKeyValueBlock(khaPairs)

    pdf.addSectionTitle(context.getString(R.string.report_section_results))
    val recTitle = context.getString(if (r.khaChecked) R.string.cable_rec_title else R.string.cable_rec_title_dv_only)
    val recValue = r.recommended?.let { "${fmt(it.sectionMm2, 2)} mm²" } ?: context.getString(R.string.cable_none_fits)
    pdf.addKeyValueBlock(
      listOf(
        context.getString(R.string.cable_load_amps_label) to "${fixed(r.loadCurrentA, 2)} A",
        recTitle to recValue,
      )
    )
    if (!r.khaChecked) {
      pdf.addParagraph(context.getString(R.string.cable_kha_warning), bold = true, isWarning = true)
    }

    pdf.addSectionTitle(context.getString(R.string.report_section_comparison))
    val displayedOpts = listOfNotNull(r.smaller, r.recommended, r.larger).distinctBy { it.sectionMm2 }
    val headers = listOf(
      context.getString(R.string.report_col_section),
      context.getString(R.string.report_col_r_ohm_km),
      context.getString(R.string.report_col_dv_v),
      context.getString(R.string.report_col_dv_pct),
      context.getString(R.string.report_col_dv_margin),
      context.getString(R.string.report_col_kha_eff),
      context.getString(R.string.report_col_kha_margin),
      context.getString(R.string.report_col_status),
    )
    val highlightIdx = mutableSetOf<Int>()
    val tableRows = displayedOpts.mapIndexed { idx, o ->
      val isRec = r.recommended?.sectionMm2 == o.sectionMm2
      if (isRec) highlightIdx.add(idx)
      val dvMarginStr = if (o.withinLimit) "+${fixed(o.marginPercent, 2)} %" else "-${fixed(-o.marginPercent, 2)} %"
      val khaEffStr = o.deratedKhaA?.let { "${fixed(it, 1)} A" } ?: "-"
      val khaMarginStr = o.khaMarginA?.let { if (it >= 0.0) "+${fixed(it, 1)} A" else "-${fixed(-it, 1)} A" } ?: "-"
      val statusStr = when {
        isRec -> context.getString(R.string.report_status_rec)
        o.passes(r.khaChecked) -> context.getString(R.string.report_status_ok)
        else -> context.getString(R.string.report_status_fail)
      }
      listOf(
        "${fmt(o.sectionMm2, 2)} mm²",
        fixed(o.resistancePerKmOhm, 3),
        fixed(o.voltageDropV, 2),
        fixed(o.voltageDropPercent, 2),
        dvMarginStr,
        khaEffStr,
        khaMarginStr,
        statusStr,
      )
    }
    pdf.addTable(
      headers = headers,
      rows = tableRows,
      colWeights = listOf(1.1f, 1.0f, 0.9f, 0.9f, 1.1f, 1.0f, 1.1f, 1.3f),
      highlightRowIndices = highlightIdx,
    )

    pdf.addSectionTitle(context.getString(R.string.report_section_notes))
    pdf.addParagraph(context.getString(R.string.cable_rec_note))
    pdf.addParagraph(context.getString(R.string.cable_source_note))
    pdf.addParagraph(context.getString(R.string.independent_disclaimer), bold = true)
    pdf.addSignatureBlock(
      leftLabel = context.getString(R.string.report_label_Prepared_by),
      leftName = meta.author,
      rightLabel = context.getString(R.string.report_label_checked_by),
    )

    val outFile = createReportFile(context, "cable", meta, "pdf", now)
    pdf.writeTo(outFile)
    return outFile
  }

  fun generateCableXlsx(context: Context, input: CableReportInput, meta: ReportMeta, now: Date = Date()): File {
    val dateStr = formatDisplayDate(now)
    val r = input.result
    val ktVal = input.kTemp ?: 1.0
    val kgVal = input.kGroup ?: 1.0
    val rows1 = mutableListOf<List<Any?>>()
    val bold1 = mutableSetOf<Int>()

    appendMetaRowsXlsx(rows1, bold1, context.getString(R.string.cable_title), headerPairs(context, meta, dateStr))

    val circuitLabel = when (input.circuit) {
      CableCircuit.DC -> context.getString(R.string.cable_circuit_dc)
      CableCircuit.SINGLE_PHASE -> context.getString(R.string.phase_1p)
      CableCircuit.THREE_PHASE -> context.getString(R.string.phase_3p)
    }
    val matLabel = if (input.material == ConductorMaterial.COPPER) context.getString(R.string.cable_material_cu) else context.getString(R.string.cable_material_al)
    val rhoLabel = if (input.rhoMethod == ResistivityMethod.IEC_60364_ANNEX_G) context.getString(R.string.cable_rho_iec) else context.getString(R.string.cable_rho_guide)

    bold1.add(rows1.size)
    rows1.add(listOf(context.getString(R.string.report_col_parameter), context.getString(R.string.report_col_value), context.getString(R.string.report_col_unit)))
    rows1.add(listOf(context.getString(R.string.report_kha_circuit_label), circuitLabel, ""))
    rows1.add(listOf(context.getString(R.string.acp_voltage_label), input.voltageV, "V"))
    rows1.add(listOf(context.getString(R.string.cable_load_amps_label), r.loadCurrentA, "A"))
    if (input.loadMode == CableLoadInputMode.POWER_KW) {
      rows1.add(listOf(context.getString(R.string.acp_mode_kw), input.loadValue, "kW"))
    }
    if (input.circuit != CableCircuit.DC) {
      rows1.add(listOf(context.getString(R.string.acp_cosphi_label), input.cosPhi, ""))
    }
    rows1.add(listOf(context.getString(R.string.cable_length_label), input.lengthM, "m"))
    rows1.add(listOf(context.getString(R.string.cable_limit_label), input.maxDropPercent, "%"))
    rows1.add(listOf(context.getString(R.string.report_material_label), matLabel, ""))
    rows1.add(listOf(context.getString(R.string.cable_rho_method_label), rhoLabel, ""))
    rows1.add(listOf("ρ", r.rhoOhmMm2PerM, "Ω·mm²/m"))

    if (input.useIecTable) {
      val insLabel = if (input.insulation == CableInsulation.PVC) context.getString(R.string.cable_insul_pvc) else context.getString(R.string.cable_insul_xlpe)
      rows1.add(listOf(context.getString(R.string.report_kha_source_label), context.getString(R.string.cable_kha_iec), ""))
      rows1.add(listOf(context.getString(R.string.report_kha_insulation_label), insLabel, ""))
      rows1.add(listOf(context.getString(R.string.cable_loaded_label), input.loadedConductors, ""))
      rows1.add(listOf(context.getString(R.string.cable_method_label), input.iecMethod.code, ""))
      if (input.autoFactors) {
        input.ambientTempC?.let { rows1.add(listOf(context.getString(R.string.cable_ambient_label), it, "°C")) }
        input.circuitCount?.let { rows1.add(listOf(context.getString(R.string.cable_circuits_label), it, input.groupingArrangementLabel)) }
      }
    } else {
      rows1.add(listOf(context.getString(R.string.report_kha_source_label), input.userTableName ?: "-", ""))
    }
    rows1.add(listOf("k_t", ktVal, ""))
    rows1.add(listOf("k_g", kgVal, ""))

    val recTitle = context.getString(if (r.khaChecked) R.string.cable_rec_title else R.string.cable_rec_title_dv_only)
    rows1.add(listOf(recTitle, r.recommended?.sectionMm2 ?: context.getString(R.string.cable_none_fits), if (r.recommended != null) "mm²" else ""))
    if (!r.khaChecked) {
      rows1.add(listOf("Warning", context.getString(R.string.cable_kha_warning), ""))
    }
    rows1.add(emptyList())

    val compHeaders = listOf(
      context.getString(R.string.report_col_section),
      context.getString(R.string.report_col_r_ohm_km),
      context.getString(R.string.report_col_dv_v),
      context.getString(R.string.report_col_dv_pct),
      context.getString(R.string.report_col_dv_margin),
      context.getString(R.string.report_col_kha_eff),
      context.getString(R.string.report_col_kha_margin),
      context.getString(R.string.report_col_status),
    )

    fun optionRow(o: SectionOption): List<Any?> {
      val isRec = r.recommended?.sectionMm2 == o.sectionMm2
      val statusStr = when {
        isRec -> context.getString(R.string.report_status_rec)
        o.passes(r.khaChecked) -> context.getString(R.string.report_status_ok)
        else -> context.getString(R.string.report_status_fail)
      }
      return listOf(
        o.sectionMm2,
        o.resistancePerKmOhm,
        o.voltageDropV,
        o.voltageDropPercent,
        o.marginPercent,
        o.deratedKhaA ?: "-",
        o.khaMarginA ?: "-",
        statusStr,
      )
    }

    bold1.add(rows1.size)
    rows1.add(compHeaders)
    val displayedOpts = listOfNotNull(r.smaller, r.recommended, r.larger).distinctBy { it.sectionMm2 }
    for (o in displayedOpts) {
      if (r.recommended?.sectionMm2 == o.sectionMm2) bold1.add(rows1.size)
      rows1.add(optionRow(o))
    }
    rows1.add(emptyList())
    rows1.add(listOf(context.getString(R.string.cable_source_note)))
    rows1.add(listOf(context.getString(R.string.independent_disclaimer)))

    // Sheet 2: "Perbandingan" berisi SEMUA penampang standar (19 ukuran)
    val rows2 = mutableListOf<List<Any?>>()
    val bold2 = mutableSetOf<Int>()
    bold2.add(0)
    rows2.add(compHeaders)
    val allOpts = r.allOptions
    for (o in allOpts) {
      if (r.recommended?.sectionMm2 == o.sectionMm2) bold2.add(rows2.size)
      rows2.add(optionRow(o))
    }

    val sheet1 = XlsxSheet(
      name = context.getString(R.string.tab_cable),
      rows = rows1,
      boldRows = bold1,
      colWidths = listOf(28.0, 16.0, 14.0, 14.0, 16.0, 16.0, 16.0, 18.0),
    )
    val sheet2 = XlsxSheet(
      name = "Perbandingan",
      rows = rows2,
      boldRows = bold2,
      colWidths = listOf(18.0, 14.0, 14.0, 14.0, 16.0, 16.0, 16.0, 18.0),
    )

    val outFile = createReportFile(context, "cable", meta, "xlsx", now)
    FileOutputStream(outFile).use { out ->
      XlsxWriter.write(out, listOf(sheet1, sheet2))
    }
    return outFile
  }

  // ==================== 2. MOTOR STARTER REPORT ====================

  fun generateMotorPdf(context: Context, r: MotorStarterResult, meta: ReportMeta, now: Date = Date()): File {
    val dateStr = formatDisplayDate(now)
    val footerLeft = "${context.getString(R.string.report_footer_brand)} · $dateStr"
    val pdf = PdfReportBuilder(footerLeft)

    val title = context.getString(R.string.motor_title)
    pdf.addDocumentHeader(title, meta.circuitName.takeIf { it.isNotBlank() })

    pdf.addSectionTitle(context.getString(R.string.report_section_header))
    pdf.addKeyValueBlock(headerPairs(context, meta, dateStr))

    val methodLabel = if (r.starterMethod == StarterMethod.DOL) context.getString(R.string.motor_method_dol) else context.getString(R.string.motor_method_sd)
    pdf.addSectionTitle(context.getString(R.string.report_section_inputs))
    pdf.addKeyValueBlock(
      listOf(
        context.getString(R.string.motor_out_power_conv) to "${fixed(r.powerKw, 2)} kW / ${fixed(r.powerHp, 2)} HP",
        context.getString(R.string.motor_voltage_label) to "${fmt(r.voltageV, 1)} V",
        context.getString(R.string.acp_cosphi_label) to fmt(r.cosPhi, 2),
        context.getString(R.string.motor_eta_label) to fmt(r.efficiency, 2),
        context.getString(R.string.motor_start_mult_label) to "${fmt(r.dolStartCurrentMultiple, 2)} × In",
        context.getString(R.string.motor_method_label) to methodLabel,
      )
    )

    pdf.addSectionTitle(context.getString(R.string.report_section_results))
    val ex = context.getString(R.string.motor_std_exceeds)
    val kmStd = r.mainContactorStdRatingA?.let { context.getString(R.string.motor_std_nearest, it) } ?: ex
    val kdStd = r.deltaContactorStdRatingA?.let { context.getString(R.string.motor_std_nearest, it) } ?: ex
    val kyStd = r.starContactorStdRatingA?.let { context.getString(R.string.motor_std_nearest, it) } ?: ex
    val brkStd = if (r.recommendedBreakerStdMinA != null && r.recommendedBreakerStdMaxA != null) {
      "std ${r.recommendedBreakerStdMinA}..${r.recommendedBreakerStdMaxA} A"
    } else ex

    val resultRows = mutableListOf(
      listOf(context.getString(R.string.motor_out_power_conv), "${fixed(r.powerKw, 2)} kW / ${fixed(r.powerHp, 2)} HP"),
      listOf(context.getString(R.string.motor_out_in), "${fixed(r.nominalCurrentInA, 2)} A"),
      listOf(context.getString(R.string.motor_out_start_i), "${fixed(r.estimatedActualStartCurrentA, 2)} A"),
      listOf(context.getString(R.string.motor_out_km), "${fixed(r.mainContactorMinCurrentA, 2)} A ($kmStd)"),
    )
    if (r.starterMethod == StarterMethod.STAR_DELTA && r.deltaContactorMinCurrentA != null && r.starContactorMinCurrentA != null) {
      resultRows.add(listOf(context.getString(R.string.motor_out_kdelta), "${fixed(r.deltaContactorMinCurrentA!!, 2)} A ($kdStd)"))
      resultRows.add(listOf(context.getString(R.string.motor_out_kstar), "${fixed(r.starContactorMinCurrentA!!, 2)} A ($kyStd)"))
    }
    resultRows.add(listOf(context.getString(R.string.motor_out_tor), "${fixed(r.torSettingCurrentA, 2)} A (${fixed(r.torRangeMinA, 2)}..${fixed(r.torRangeMaxA, 2)} A)"))
    resultRows.add(listOf(context.getString(R.string.motor_out_breaker), "${fixed(r.breakerMinRangeA, 2)}..${fixed(r.breakerMaxRangeA, 2)} A ($brkStd)"))

    pdf.addTable(
      headers = listOf(context.getString(R.string.report_col_parameter), context.getString(R.string.report_col_value)),
      rows = resultRows,
      colWeights = listOf(1.1f, 1.3f),
    )

    pdf.addSectionTitle(context.getString(R.string.report_section_notes))
    pdf.addParagraph(context.getString(R.string.motor_curve_note))
    pdf.addParagraph(context.getString(R.string.motor_source_note))
    pdf.addParagraph(context.getString(R.string.independent_disclaimer), bold = true)
    pdf.addSignatureBlock(
      leftLabel = context.getString(R.string.report_label_Prepared_by),
      leftName = meta.author,
      rightLabel = context.getString(R.string.report_label_checked_by),
    )

    val outFile = createReportFile(context, "motor", meta, "pdf", now)
    pdf.writeTo(outFile)
    return outFile
  }

  fun generateMotorXlsx(context: Context, r: MotorStarterResult, meta: ReportMeta, now: Date = Date()): File {
    val dateStr = formatDisplayDate(now)
    val rows = mutableListOf<List<Any?>>()
    val bold = mutableSetOf<Int>()

    appendMetaRowsXlsx(rows, bold, context.getString(R.string.motor_title), headerPairs(context, meta, dateStr))

    val methodLabel = if (r.starterMethod == StarterMethod.DOL) context.getString(R.string.motor_method_dol) else context.getString(R.string.motor_method_sd)
    bold.add(rows.size)
    rows.add(listOf(context.getString(R.string.report_col_parameter), context.getString(R.string.report_col_value), context.getString(R.string.report_col_unit)))
    rows.add(listOf("P (kW)", r.powerKw, "kW"))
    rows.add(listOf("P (HP)", r.powerHp, "HP"))
    rows.add(listOf(context.getString(R.string.motor_voltage_label), r.voltageV, "V"))
    rows.add(listOf(context.getString(R.string.acp_cosphi_label), r.cosPhi, ""))
    rows.add(listOf(context.getString(R.string.motor_eta_label), r.efficiency, ""))
    rows.add(listOf(context.getString(R.string.motor_start_mult_label), r.dolStartCurrentMultiple, "x In"))
    rows.add(listOf(context.getString(R.string.motor_method_label), methodLabel, ""))
    rows.add(listOf(context.getString(R.string.motor_out_in), r.nominalCurrentInA, "A"))
    rows.add(listOf(context.getString(R.string.motor_out_start_i), r.estimatedActualStartCurrentA, "A"))
    rows.add(listOf(context.getString(R.string.motor_out_km), r.mainContactorMinCurrentA, "A"))
    rows.add(listOf("KM Std AC-3", r.mainContactorStdRatingA ?: "-", "A"))
    if (r.starterMethod == StarterMethod.STAR_DELTA && r.deltaContactorMinCurrentA != null && r.starContactorMinCurrentA != null) {
      rows.add(listOf(context.getString(R.string.motor_out_kdelta), r.deltaContactorMinCurrentA, "A"))
      rows.add(listOf("KΔ Std AC-3", r.deltaContactorStdRatingA ?: "-", "A"))
      rows.add(listOf(context.getString(R.string.motor_out_kstar), r.starContactorMinCurrentA, "A"))
      rows.add(listOf("KY Std AC-3", r.starContactorStdRatingA ?: "-", "A"))
    }
    rows.add(listOf(context.getString(R.string.motor_out_tor), r.torSettingCurrentA, "A"))
    rows.add(listOf("TOR Min", r.torRangeMinA, "A"))
    rows.add(listOf("TOR Max", r.torRangeMaxA, "A"))
    rows.add(listOf("Breaker Min", r.breakerMinRangeA, "A"))
    rows.add(listOf("Breaker Max", r.breakerMaxRangeA, "A"))
    rows.add(listOf("Breaker Std Min", r.recommendedBreakerStdMinA ?: "-", "A"))
    rows.add(listOf("Breaker Std Max", r.recommendedBreakerStdMaxA ?: "-", "A"))
    rows.add(emptyList())
    rows.add(listOf(context.getString(R.string.motor_source_note)))
    rows.add(listOf(context.getString(R.string.independent_disclaimer)))

    val sheet = XlsxSheet(
      name = context.getString(R.string.tab_motor),
      rows = rows,
      boldRows = bold,
      colWidths = listOf(32.0, 18.0, 14.0),
    )
    val outFile = createReportFile(context, "motor", meta, "xlsx", now)
    FileOutputStream(outFile).use { out ->
      XlsxWriter.write(out, listOf(sheet))
    }
    return outFile
  }

  // ==================== 3. CAPACITOR BANK REPORT ====================

  fun generateCapacitorPdf(context: Context, r: CapacitorBankResult, meta: ReportMeta, now: Date = Date()): File {
    val dateStr = formatDisplayDate(now)
    val footerLeft = "${context.getString(R.string.report_footer_brand)} · $dateStr"
    val pdf = PdfReportBuilder(footerLeft)

    val title = context.getString(R.string.cap_title)
    pdf.addDocumentHeader(title, meta.circuitName.takeIf { it.isNotBlank() })

    pdf.addSectionTitle(context.getString(R.string.report_section_header))
    pdf.addKeyValueBlock(headerPairs(context, meta, dateStr))

    pdf.addSectionTitle(context.getString(R.string.report_section_inputs))
    pdf.addKeyValueBlock(
      listOf(
        context.getString(R.string.acp_mode_kw) to "${fixed(r.activePowerKw, 2)} kW",
        context.getString(R.string.motor_voltage_label) to "${fmt(r.voltageV, 1)} V",
        context.getString(R.string.cap_cosphi1_label) to fixed(r.initialCosPhi, 2),
        context.getString(R.string.cap_cosphi2_label) to fixed(r.targetCosPhi, 2),
      )
    )

    pdf.addSectionTitle(context.getString(R.string.report_section_results))
    pdf.addTable(
      headers = listOf(context.getString(R.string.report_col_parameter), context.getString(R.string.report_col_value)),
      rows = listOf(
        listOf(context.getString(R.string.cap_out_q), "${fixed(r.requiredKvar, 2)} kVAR"),
        listOf("I1 / I2", "${fixed(r.initialLineCurrentA, 2)} A / ${fixed(r.targetLineCurrentA, 2)} A"),
        listOf(context.getString(R.string.cap_out_i_saved), "${fixed(r.currentReductionA, 2)} A (${fixed(r.currentReductionPercent, 2)} %)"),
        listOf("S1 / S2", "${fixed(r.initialApparentKva, 2)} kVA / ${fixed(r.targetApparentKva, 2)} kVA"),
        listOf("Ic", "${fixed(r.capacitorBankCurrentA, 2)} A"),
      ),
      colWeights = listOf(1.1f, 1.3f),
    )

    pdf.addSectionTitle(context.getString(R.string.report_section_steps))
    val stepHeaders = listOf(
      context.getString(R.string.report_col_steps),
      context.getString(R.string.report_col_ideal_step),
      context.getString(R.string.report_col_std_step),
      context.getString(R.string.report_col_total_kvar),
      context.getString(R.string.report_col_stage_current),
    )
    val stepRows = r.stepSuggestions.map { st ->
      listOf(
        st.stepCount.toString(),
        fixed(st.idealStepKvar, 2),
        fmt(st.standardStepKvar, 1),
        fmt(st.totalInstalledKvar, 1),
        fixed(st.nominalStageCurrentA, 2),
      )
    }
    pdf.addTable(
      headers = stepHeaders,
      rows = stepRows,
      colWeights = listOf(0.8f, 1.1f, 1.1f, 1.2f, 1.1f),
    )

    pdf.addSectionTitle(context.getString(R.string.report_section_notes))
    pdf.addParagraph(context.getString(R.string.cap_source_note))
    pdf.addParagraph(context.getString(R.string.independent_disclaimer), bold = true)
    pdf.addSignatureBlock(
      leftLabel = context.getString(R.string.report_label_Prepared_by),
      leftName = meta.author,
      rightLabel = context.getString(R.string.report_label_checked_by),
    )

    val outFile = createReportFile(context, "capacitor", meta, "pdf", now)
    pdf.writeTo(outFile)
    return outFile
  }

  fun generateCapacitorXlsx(context: Context, r: CapacitorBankResult, meta: ReportMeta, now: Date = Date()): File {
    val dateStr = formatDisplayDate(now)
    val rows = mutableListOf<List<Any?>>()
    val bold = mutableSetOf<Int>()

    appendMetaRowsXlsx(rows, bold, context.getString(R.string.cap_title), headerPairs(context, meta, dateStr))

    bold.add(rows.size)
    rows.add(listOf(context.getString(R.string.report_col_parameter), context.getString(R.string.report_col_value), context.getString(R.string.report_col_unit)))
    rows.add(listOf(context.getString(R.string.acp_mode_kw), r.activePowerKw, "kW"))
    rows.add(listOf(context.getString(R.string.motor_voltage_label), r.voltageV, "V"))
    rows.add(listOf(context.getString(R.string.cap_cosphi1_label), r.initialCosPhi, ""))
    rows.add(listOf(context.getString(R.string.cap_cosphi2_label), r.targetCosPhi, ""))
    rows.add(listOf(context.getString(R.string.cap_out_q), r.requiredKvar, "kVAR"))
    rows.add(listOf("I1", r.initialLineCurrentA, "A"))
    rows.add(listOf("I2", r.targetLineCurrentA, "A"))
    rows.add(listOf("ΔI (A)", r.currentReductionA, "A"))
    rows.add(listOf("ΔI (%)", r.currentReductionPercent, "%"))
    rows.add(listOf("S1", r.initialApparentKva, "kVA"))
    rows.add(listOf("S2", r.targetApparentKva, "kVA"))
    rows.add(listOf("Ic", r.capacitorBankCurrentA, "A"))
    rows.add(emptyList())

    bold.add(rows.size)
    rows.add(
      listOf(
        context.getString(R.string.report_col_steps),
        context.getString(R.string.report_col_ideal_step),
        context.getString(R.string.report_col_std_step),
        context.getString(R.string.report_col_total_kvar),
        context.getString(R.string.report_col_stage_current),
      )
    )
    for (st in r.stepSuggestions) {
      rows.add(
        listOf(
          st.stepCount,
          st.idealStepKvar,
          st.standardStepKvar,
          st.totalInstalledKvar,
          st.nominalStageCurrentA,
        )
      )
    }
    rows.add(emptyList())
    rows.add(listOf(context.getString(R.string.cap_source_note)))
    rows.add(listOf(context.getString(R.string.independent_disclaimer)))

    val sheet = XlsxSheet(
      name = context.getString(R.string.tab_cap),
      rows = rows,
      boldRows = bold,
      colWidths = listOf(28.0, 18.0, 18.0, 20.0, 18.0),
    )
    val outFile = createReportFile(context, "capacitor", meta, "xlsx", now)
    FileOutputStream(outFile).use { out ->
      XlsxWriter.write(out, listOf(sheet))
    }
    return outFile
  }

  // ==================== 4. 3-PHASE UNBALANCE REPORT ====================

  fun generateUnbalancePdf(context: Context, r: PhaseUnbalanceResult, meta: ReportMeta, now: Date = Date()): File {
    val dateStr = formatDisplayDate(now)
    val footerLeft = "${context.getString(R.string.report_footer_brand)} · $dateStr"
    val pdf = PdfReportBuilder(footerLeft)

    val title = context.getString(R.string.unb_title)
    pdf.addDocumentHeader(title, meta.circuitName.takeIf { it.isNotBlank() })

    pdf.addSectionTitle(context.getString(R.string.report_section_header))
    pdf.addKeyValueBlock(headerPairs(context, meta, dateStr))

    val isV = r.quantity == UnbalanceQuantity.VOLTAGE
    val unit = if (isV) "V" else "A"
    val modeLabel = if (isV) context.getString(R.string.unb_mode_voltage) else context.getString(R.string.unb_mode_current)
    val statusText = when (r.status) {
      UnbalanceStatus.OK_WITHIN_1_PERCENT -> context.getString(R.string.unb_status_v_ok)
      UnbalanceStatus.WARNING_EXCEEDS_1_PERCENT -> context.getString(R.string.unb_status_v_warn)
      UnbalanceStatus.CRITICAL_EXCEEDS_5_PERCENT -> context.getString(R.string.unb_status_v_crit)
      UnbalanceStatus.CURRENT_NORMAL_WITHIN_10_PERCENT -> context.getString(R.string.unb_status_i_ok)
      UnbalanceStatus.CURRENT_WARNING_EXCEEDS_10_PERCENT -> context.getString(R.string.unb_status_i_warn)
    }

    pdf.addSectionTitle(context.getString(R.string.report_section_inputs))
    pdf.addKeyValueBlock(
      listOf(
        context.getString(R.string.report_col_parameter) to modeLabel,
        context.getString(R.string.unb_phase_label, if (isV) "L1-L2" else "L1", unit) to "${fmt(r.v1, 2)} $unit",
        context.getString(R.string.unb_phase_label, if (isV) "L2-L3" else "L2", unit) to "${fmt(r.v2, 2)} $unit",
        context.getString(R.string.unb_phase_label, if (isV) "L3-L1" else "L3", unit) to "${fmt(r.v3, 2)} $unit",
      )
    )

    pdf.addSectionTitle(context.getString(R.string.report_section_results))
    val resultRows = mutableListOf(
      listOf(context.getString(R.string.unb_out_avg), "${fixed(r.average, 2)} $unit"),
      listOf(context.getString(R.string.unb_out_max_dev), "${fixed(r.maxDeviation, 2)} $unit"),
      listOf(context.getString(R.string.unb_out_pct), "${fixed(r.unbalancePercent, 2)} %"),
      listOf(context.getString(R.string.report_col_status), statusText),
    )
    r.extraTempRisePercent?.let {
      resultRows.add(listOf(context.getString(R.string.unb_out_temp_rise), "+${fixed(it, 2)} %"))
    }
    pdf.addTable(
      headers = listOf(context.getString(R.string.report_col_parameter), context.getString(R.string.report_col_value)),
      rows = resultRows,
      colWeights = listOf(1.1f, 1.3f),
    )

    pdf.addSectionTitle(context.getString(R.string.report_section_notes))
    pdf.addParagraph(context.getString(R.string.unb_derating_note))
    pdf.addParagraph(context.getString(R.string.independent_disclaimer), bold = true)
    pdf.addSignatureBlock(
      leftLabel = context.getString(R.string.report_label_Prepared_by),
      leftName = meta.author,
      rightLabel = context.getString(R.string.report_label_checked_by),
    )

    val outFile = createReportFile(context, "unbalance", meta, "pdf", now)
    pdf.writeTo(outFile)
    return outFile
  }

  fun generateUnbalanceXlsx(context: Context, r: PhaseUnbalanceResult, meta: ReportMeta, now: Date = Date()): File {
    val dateStr = formatDisplayDate(now)
    val rows = mutableListOf<List<Any?>>()
    val bold = mutableSetOf<Int>()

    appendMetaRowsXlsx(rows, bold, context.getString(R.string.unb_title), headerPairs(context, meta, dateStr))

    val isV = r.quantity == UnbalanceQuantity.VOLTAGE
    val unit = if (isV) "V" else "A"
    val modeLabel = if (isV) context.getString(R.string.unb_mode_voltage) else context.getString(R.string.unb_mode_current)
    val statusText = when (r.status) {
      UnbalanceStatus.OK_WITHIN_1_PERCENT -> context.getString(R.string.unb_status_v_ok)
      UnbalanceStatus.WARNING_EXCEEDS_1_PERCENT -> context.getString(R.string.unb_status_v_warn)
      UnbalanceStatus.CRITICAL_EXCEEDS_5_PERCENT -> context.getString(R.string.unb_status_v_crit)
      UnbalanceStatus.CURRENT_NORMAL_WITHIN_10_PERCENT -> context.getString(R.string.unb_status_i_ok)
      UnbalanceStatus.CURRENT_WARNING_EXCEEDS_10_PERCENT -> context.getString(R.string.unb_status_i_warn)
    }

    bold.add(rows.size)
    rows.add(listOf(context.getString(R.string.report_col_parameter), context.getString(R.string.report_col_value), context.getString(R.string.report_col_unit)))
    rows.add(listOf("Mode", modeLabel, ""))
    rows.add(listOf(if (isV) "L1-L2" else "L1", r.v1, unit))
    rows.add(listOf(if (isV) "L2-L3" else "L2", r.v2, unit))
    rows.add(listOf(if (isV) "L3-L1" else "L3", r.v3, unit))
    rows.add(listOf(context.getString(R.string.unb_out_avg), r.average, unit))
    rows.add(listOf(context.getString(R.string.unb_out_max_dev), r.maxDeviation, unit))
    rows.add(listOf(context.getString(R.string.unb_out_pct), r.unbalancePercent, "%"))
    r.extraTempRisePercent?.let {
      rows.add(listOf(context.getString(R.string.unb_out_temp_rise), it, "%"))
    }
    rows.add(listOf(context.getString(R.string.report_col_status), statusText, ""))
    rows.add(emptyList())
    rows.add(listOf(context.getString(R.string.unb_derating_note)))
    rows.add(listOf(context.getString(R.string.independent_disclaimer)))

    val sheet = XlsxSheet(
      name = context.getString(R.string.tab_unbalance),
      rows = rows,
      boldRows = bold,
      colWidths = listOf(30.0, 22.0, 12.0),
    )
    val outFile = createReportFile(context, "unbalance", meta, "xlsx", now)
    FileOutputStream(outFile).use { out ->
      XlsxWriter.write(out, listOf(sheet))
    }
    return outFile
  }
}
