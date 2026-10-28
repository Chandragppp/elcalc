package id.controlengineer.elcalc

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/**
 * Reusable A4 (595 x 842 pt, margin 36 pt) PDF report builder using android.graphics.pdf.PdfDocument.
 * Renders all pages cleanly and writes the standard footer with "1/N" page numbers.
 */
class PdfReportBuilder(
  private val footerLeftText: String,
) {
  companion object {
    const val PAGE_WIDTH = 595
    const val PAGE_HEIGHT = 842
    const val MARGIN = 36f
    const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN
    private const val FOOTER_RESERVED = 28f
    const val CONTENT_BOTTOM = PAGE_HEIGHT - MARGIN - FOOTER_RESERVED
  }

  private sealed interface DrawOp {
    fun draw(canvas: Canvas)
  }

  private class TextOp(
    val text: String,
    val x: Float,
    val y: Float,
    val size: Float,
    val bold: Boolean,
    val color: Int,
    val align: Paint.Align = Paint.Align.LEFT,
  ) : DrawOp {
    override fun draw(canvas: Canvas) {
      val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        typeface = if (bold) Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) else Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        this.color = color
        textAlign = align
      }
      canvas.drawText(text, x, y, p)
    }
  }

  private class RectOp(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val fillColor: Int?,
    val strokeColor: Int?,
    val strokeWidth: Float = 0.75f,
  ) : DrawOp {
    override fun draw(canvas: Canvas) {
      if (fillColor != null) {
        val fp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          style = Paint.Style.FILL
          color = fillColor
        }
        canvas.drawRect(left, top, right, bottom, fp)
      }
      if (strokeColor != null) {
        val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          style = Paint.Style.STROKE
          color = strokeColor
          this.strokeWidth = strokeWidth
        }
        canvas.drawRect(left, top, right, bottom, sp)
      }
    }
  }

  private class LineOp(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val color: Int,
    val strokeWidth: Float = 0.75f,
  ) : DrawOp {
    override fun draw(canvas: Canvas) {
      val lp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        this.color = color
        this.strokeWidth = strokeWidth
      }
      canvas.drawLine(x1, y1, x2, y2, lp)
    }
  }

  private val pages = mutableListOf<MutableList<DrawOp>>(mutableListOf())
  private var cursorY = MARGIN

  private fun currentOps(): MutableList<DrawOp> = pages.last()

  private fun newPage() {
    pages.add(mutableListOf())
    cursorY = MARGIN
  }

  private fun ensureSpace(heightNeeded: Float) {
    if (cursorY + heightNeeded > CONTENT_BOTTOM && cursorY > MARGIN + 1f) {
      newPage()
    }
  }

  private fun measurePaint(size: Float, bold: Boolean): Paint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      textSize = size
      typeface = if (bold) Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) else Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }

  private fun wrapText(text: String, maxWidth: Float, size: Float, bold: Boolean): List<String> {
    if (text.isEmpty()) return listOf("")
    val paint = measurePaint(size, bold)
    val result = mutableListOf<String>()
    for (paragraph in text.split("\n")) {
      if (paragraph.isBlank()) {
        result.add("")
        continue
      }
      val words = paragraph.trim().split(Regex("\\s+"))
      var currentLine = ""
      for (word in words) {
        val candidate = if (currentLine.isEmpty()) word else "$currentLine $word"
        if (paint.measureText(candidate) <= maxWidth) {
          currentLine = candidate
        } else {
          if (currentLine.isNotEmpty()) {
            result.add(currentLine)
            currentLine = word
          } else {
            var chunk = ""
            for (ch in word) {
              val nextChunk = chunk + ch
              if (paint.measureText(nextChunk) <= maxWidth || chunk.isEmpty()) {
                chunk = nextChunk
              } else {
                result.add(chunk)
                chunk = ch.toString()
              }
            }
            currentLine = chunk
          }
        }
      }
      if (currentLine.isNotEmpty()) result.add(currentLine)
    }
    return if (result.isEmpty()) listOf("") else result
  }

  fun addDocumentHeader(title: String, subtitle: String? = null) {
    val titleLines = wrapText(title, CONTENT_WIDTH, 15f, true)
    val subLines = if (!subtitle.isNullOrBlank()) wrapText(subtitle, CONTENT_WIDTH, 10f, false) else emptyList()
    val needed = titleLines.size * 19f + subLines.size * 14f + 12f
    ensureSpace(needed)
    for (line in titleLines) {
      cursorY += 15f
      currentOps().add(TextOp(line, MARGIN, cursorY, 15f, true, Color.rgb(13, 71, 161)))
      cursorY += 4f
    }
    for (line in subLines) {
      cursorY += 11f
      currentOps().add(TextOp(line, MARGIN, cursorY, 10f, false, Color.DKGRAY))
      cursorY += 3f
    }
    cursorY += 4f
    currentOps().add(LineOp(MARGIN, cursorY, PAGE_WIDTH - MARGIN, cursorY, Color.rgb(13, 71, 161), 1.2f))
    cursorY += 8f
  }

  fun addSectionTitle(title: String) {
    val lines = wrapText(title, CONTENT_WIDTH, 11f, true)
    ensureSpace(lines.size * 15f + 14f)
    cursorY += 4f
    for (line in lines) {
      cursorY += 12f
      currentOps().add(TextOp(line, MARGIN, cursorY, 11f, true, Color.rgb(25, 45, 85)))
      cursorY += 3f
    }
    cursorY += 2f
  }

  fun addKeyValueBlock(pairs: List<Pair<String, String>>) {
    val nonEmpty = pairs.filter { it.second.isNotBlank() }
    if (nonEmpty.isEmpty()) return
    val keyWidth = CONTENT_WIDTH * 0.36f
    val valWidth = CONTENT_WIDTH * 0.64f - 8f
    for ((k, v) in nonEmpty) {
      val kLines = wrapText(k, keyWidth, 9.5f, true)
      val vLines = wrapText(v, valWidth, 9.5f, false)
      val lineCount = max(kLines.size, vLines.size)
      val rowH = lineCount * 13f + 4f
      ensureSpace(rowH)
      val baseTop = cursorY
      for (i in 0 until lineCount) {
        val y = baseTop + (i + 1) * 11f + i * 2f
        if (i < kLines.size) {
          currentOps().add(TextOp(kLines[i], MARGIN, y, 9.5f, true, Color.rgb(55, 55, 55)))
        }
        if (i < vLines.size) {
          currentOps().add(TextOp(vLines[i], MARGIN + keyWidth + 6f, y, 9.5f, false, Color.BLACK))
        }
      }
      cursorY += rowH
    }
    cursorY += 4f
  }

  fun addTable(
    headers: List<String>,
    rows: List<List<String>>,
    colWeights: List<Float> = List(headers.size) { 1f },
    highlightRowIndices: Set<Int> = emptySet(),
  ) {
    if (headers.isEmpty()) return
    val totalWeight = colWeights.sum().coerceAtLeast(0.001f)
    val colWidths = colWeights.map { (it / totalWeight) * CONTENT_WIDTH }
    val padX = 4f
    val padY = 4f
    val fontSize = if (headers.size >= 7) 8f else 9f
    val lineStep = fontSize + 2.5f

    fun measureRowHeight(cells: List<String>, bold: Boolean): Pair<Float, List<List<String>>> {
      val wrapped = cells.mapIndexed { idx, txt ->
        val w = (colWidths.getOrElse(idx) { 40f } - 2 * padX).coerceAtLeast(16f)
        wrapText(txt, w, fontSize, bold)
      }
      val maxLines = wrapped.maxOfOrNull { it.size } ?: 1
      return (maxLines * lineStep + 2 * padY) to wrapped
    }

    fun drawRow(
      wrappedCells: List<List<String>>,
      rowHeight: Float,
      bold: Boolean,
      bgColor: Int?,
    ) {
      var x = MARGIN
      val top = cursorY
      val bottom = top + rowHeight
      if (bgColor != null) {
        currentOps().add(RectOp(MARGIN, top, MARGIN + CONTENT_WIDTH, bottom, bgColor, null))
      }
      wrappedCells.forEachIndexed { idx, lines ->
        val w = colWidths.getOrElse(idx) { 40f }
        currentOps().add(RectOp(x, top, x + w, bottom, null, Color.rgb(185, 190, 200), 0.6f))
        lines.forEachIndexed { lineIdx, line ->
          val textY = top + padY + (lineIdx + 1) * fontSize + lineIdx * 2.5f - 1f
          currentOps().add(TextOp(line, x + padX, textY, fontSize, bold, Color.BLACK))
        }
        x += w
      }
      cursorY = bottom
    }

    val (headerH, headerWrapped) = measureRowHeight(headers, true)
    val firstRowH = if (rows.isNotEmpty()) measureRowHeight(rows.first(), false).first else 0f
    ensureSpace(headerH + firstRowH + 4f)
    drawRow(headerWrapped, headerH, true, Color.rgb(230, 236, 245))

    rows.forEachIndexed { rIdx, row ->
      val isHighlighted = rIdx in highlightRowIndices
      val (rH, rWrapped) = measureRowHeight(row, isHighlighted)
      if (cursorY + rH > CONTENT_BOTTOM) {
        newPage()
        drawRow(headerWrapped, headerH, true, Color.rgb(230, 236, 245))
      }
      val bg = when {
        isHighlighted -> Color.rgb(232, 245, 233)
        rIdx % 2 == 1 -> Color.rgb(250, 251, 252)
        else -> null
      }
      drawRow(rWrapped, rH, isHighlighted, bg)
    }
    cursorY += 6f
  }

  fun addParagraph(text: String, bold: Boolean = false, isWarning: Boolean = false, size: Float = 9f) {
    if (text.isBlank()) return
    val lines = wrapText(text, CONTENT_WIDTH, size, bold)
    val step = size + 3.5f
    ensureSpace(lines.size * step + 4f)
    val color = if (isWarning) Color.rgb(183, 28, 28) else Color.rgb(60, 60, 60)
    for (line in lines) {
      cursorY += size + 1.5f
      currentOps().add(TextOp(line, MARGIN, cursorY, size, bold, color))
      cursorY += 2f
    }
    cursorY += 3f
  }

  fun addSignatureBlock(leftLabel: String, leftName: String, rightLabel: String) {
    val blockHeight = 56f
    ensureSpace(blockHeight)
    cursorY += 8f
    val colW = (CONTENT_WIDTH - 24f) / 2f
    val leftX = MARGIN
    val rightX = MARGIN + colW + 24f
    val topY = cursorY
    currentOps().add(TextOp(leftLabel, leftX, topY + 10f, 9f, true, Color.DKGRAY))
    currentOps().add(TextOp(rightLabel, rightX, topY + 10f, 9f, true, Color.DKGRAY))
    val lineY = topY + 42f
    currentOps().add(LineOp(leftX, lineY, leftX + colW * 0.8f, lineY, Color.GRAY, 0.75f))
    currentOps().add(LineOp(rightX, lineY, rightX + colW * 0.8f, lineY, Color.GRAY, 0.75f))
    if (leftName.isNotBlank()) {
      currentOps().add(TextOp(leftName, leftX, lineY - 4f, 9f, false, Color.BLACK))
    }
    cursorY = lineY + 8f
  }

  fun writeTo(file: File) {
    file.parentFile?.mkdirs()
    val doc = PdfDocument()
    try {
      val totalPages = pages.size.coerceAtLeast(1)
      pages.forEachIndexed { idx, ops ->
        val pageNum = idx + 1
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas
        canvas.drawColor(Color.WHITE)
        for (op in ops) {
          op.draw(canvas)
        }
        val footerLineY = PAGE_HEIGHT - MARGIN - 14f
        LineOp(MARGIN, footerLineY, PAGE_WIDTH - MARGIN, footerLineY, Color.LTGRAY, 0.6f).draw(canvas)
        val footerTextY = PAGE_HEIGHT - MARGIN - 2f
        TextOp(footerLeftText, MARGIN, footerTextY, 8f, false, Color.GRAY, Paint.Align.LEFT).draw(canvas)
        TextOp("$pageNum/$totalPages", PAGE_WIDTH - MARGIN, footerTextY, 8f, false, Color.GRAY, Paint.Align.RIGHT).draw(canvas)
        doc.finishPage(page)
      }
      FileOutputStream(file).use { out ->
        doc.writeTo(out)
      }
    } finally {
      doc.close()
    }
  }
}
