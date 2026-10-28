package id.controlengineer.elcalc.core

import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class XlsxSheet(
  val name: String,
  val rows: List<List<Any?>>,
  val boldRows: Set<Int> = setOf(0),
  val colWidths: List<Double> = emptyList(),
)

object XlsxWriter {
  fun write(out: OutputStream, sheets: List<XlsxSheet>) {
    require(sheets.isNotEmpty()) { "At least one sheet is required" }
    ZipOutputStream(out).use { zip ->
      putXml(zip, "[Content_Types].xml", buildContentTypes(sheets.size))
      putXml(zip, "_rels/.rels", buildRootRels())
      putXml(zip, "xl/workbook.xml", buildWorkbook(sheets))
      putXml(zip, "xl/_rels/workbook.xml.rels", buildWorkbookRels(sheets.size))
      putXml(zip, "xl/styles.xml", buildStyles())
      sheets.forEachIndexed { idx, sheet ->
        putXml(zip, "xl/worksheets/sheet${idx + 1}.xml", buildWorksheet(sheet))
      }
    }
  }

  private fun putXml(zip: ZipOutputStream, entryName: String, xml: String) {
    zip.putNextEntry(ZipEntry(entryName))
    zip.write(xml.toByteArray(StandardCharsets.UTF_8))
    zip.closeEntry()
  }

  internal fun escapeXml(s: String): String = buildString(s.length) {
    for (ch in s) {
      when (ch) {
        '&' -> append("&amp;")
        '<' -> append("&lt;")
        '>' -> append("&gt;")
        '"' -> append("&quot;")
        '\'' -> append("&apos;")
        else -> append(ch)
      }
    }
  }

  internal fun colRef(colIndexZeroBased: Int): String {
    var n = colIndexZeroBased + 1
    val sb = StringBuilder()
    while (n > 0) {
      val rem = (n - 1) % 26
      sb.append(('A'.code + rem).toChar())
      n = (n - 1) / 26
    }
    return sb.reverse().toString()
  }

  private fun sanitizeSheetName(raw: String, index: Int): String {
    val cleaned = raw.replace(Regex("[\\\\/?*\\[\\]:]"), " ").trim()
    val fallback = if (cleaned.isEmpty()) "Sheet${index + 1}" else cleaned
    return if (fallback.length > 31) fallback.substring(0, 31) else fallback
  }

  private fun buildContentTypes(sheetCount: Int): String = buildString {
    append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
    append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
    append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
    append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
    append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
    append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>")
    for (i in 1..sheetCount) {
      append("<Override PartName=\"/xl/worksheets/sheet$i.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>")
    }
    append("</Types>")
  }

  private fun buildRootRels(): String =
    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
      "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
      "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
      "</Relationships>"

  private fun buildWorkbook(sheets: List<XlsxSheet>): String = buildString {
    append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
    append("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">")
    append("<sheets>")
    sheets.forEachIndexed { idx, sheet ->
      val sheetId = idx + 1
      val safeName = escapeXml(sanitizeSheetName(sheet.name, idx))
      append("<sheet name=\"$safeName\" sheetId=\"$sheetId\" r:id=\"rId$sheetId\"/>")
    }
    append("</sheets>")
    append("</workbook>")
  }

  private fun buildWorkbookRels(sheetCount: Int): String = buildString {
    append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
    append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
    for (i in 1..sheetCount) {
      append("<Relationship Id=\"rId$i\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet$i.xml\"/>")
    }
    val stylesId = sheetCount + 1
    append("<Relationship Id=\"rId$stylesId\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>")
    append("</Relationships>")
  }

  private fun buildStyles(): String =
    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
      "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
      "<fonts count=\"2\">" +
      "<font><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
      "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
      "</fonts>" +
      "<fills count=\"2\">" +
      "<fill><patternFill patternType=\"none\"/></fill>" +
      "<fill><patternFill patternType=\"gray125\"/></fill>" +
      "</fills>" +
      "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
      "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
      "<cellXfs count=\"2\">" +
      "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
      "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>" +
      "</cellXfs>" +
      "</styleSheet>"

  private fun buildWorksheet(sheet: XlsxSheet): String = buildString {
    append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
    append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
    if (sheet.colWidths.isNotEmpty()) {
      append("<cols>")
      sheet.colWidths.forEachIndexed { idx, w ->
        val colNum = idx + 1
        val widthStr = String.format(Locale.ROOT, "%.2f", w.coerceAtLeast(6.0))
        append("<col min=\"$colNum\" max=\"$colNum\" width=\"$widthStr\" customWidth=\"1\"/>")
      }
      append("</cols>")
    }
    append("<sheetData>")
    sheet.rows.forEachIndexed { rIdx, row ->
      val rowNum = rIdx + 1
      val isBold = rIdx in sheet.boldRows
      val styleAttr = if (isBold) " s=\"1\"" else ""
      append("<row r=\"$rowNum\">")
      row.forEachIndexed { cIdx, cell ->
        if (cell != null) {
          val ref = "${colRef(cIdx)}$rowNum"
          when (cell) {
            is Number -> {
              val d = cell.toDouble()
              if (d.isFinite()) {
                val numStr = formatNumber(d)
                append("<c r=\"$ref\"$styleAttr><v>$numStr</v></c>")
              } else {
                append("<c r=\"$ref\"$styleAttr t=\"inlineStr\"><is><t>${escapeXml(d.toString())}</t></is></c>")
              }
            }
            is Boolean -> {
              append("<c r=\"$ref\"$styleAttr t=\"b\"><v>${if (cell) 1 else 0}</v></c>")
            }
            else -> {
              val text = cell.toString()
              if (text.isNotEmpty()) {
                val preserve = if (text.startsWith(" ") || text.endsWith(" ") || text.contains("\n")) " xml:space=\"preserve\"" else ""
                append("<c r=\"$ref\"$styleAttr t=\"inlineStr\"><is><t$preserve>${escapeXml(text)}</t></is></c>")
              }
            }
          }
        }
      }
      append("</row>")
    }
    append("</sheetData>")
    append("</worksheet>")
  }

  private fun formatNumber(d: Double): String {
    val asLong = d.toLong()
    return if (d == asLong.toDouble()) {
      asLong.toString()
    } else {
      if (d == 0.0) "0" else d.toBigDecimal().stripTrailingZeros().let { if (it.scale() < 0) it.setScale(0) else it }.toPlainString()
    }
  }
}
