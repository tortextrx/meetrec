package com.meetrec.app.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.meetrec.app.data.model.MeetingSession
import com.meetrec.app.data.model.MeetingSummary
import com.meetrec.app.data.model.MeetingTranscript
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object NativePdfGenerator {

    private const val PAGE_WIDTH = 595 // A4 standard pt
    private const val PAGE_HEIGHT = 842 // A4 standard pt
    private const val MARGIN_X = 40f
    private const val MARGIN_Y = 40f
    private const val CONTENT_WIDTH = (PAGE_WIDTH - (MARGIN_X * 2)).toInt()

    // Paleta corporativa
    private val COLOR_PRIMARY = Color.rgb(15, 23, 42) // Slate 900
    private val COLOR_ACCENT = Color.rgb(37, 99, 235) // Blue 600
    private val COLOR_TEXT = Color.rgb(51, 65, 85) // Slate 700
    private val COLOR_MUTED = Color.rgb(100, 116, 139) // Slate 500
    private val COLOR_BORDER = Color.rgb(226, 232, 240) // Slate 200
    private val COLOR_BG_LIGHT = Color.rgb(248, 250, 252) // Slate 50

    suspend fun generateMeetingPdf(
        context: Context,
        session: MeetingSession,
        summary: MeetingSummary?,
        transcript: MeetingTranscript?
    ): Result<File> = withContext(Dispatchers.IO) {
        val outputFile = File(context.filesDir, "meeting_${session.id}_report.pdf")

        val document = PdfDocument()
        var pageNumber = 1
        val pagesList = mutableListOf<PdfDocument.Page>()

        fun startNewPage(): Pair<PdfDocument.Page, Canvas> {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            val page = document.startPage(pageInfo)
            pagesList.add(page)
            pageNumber++
            return Pair(page, page.canvas)
        }

        try {
            var (currentPage, canvas) = startNewPage()
            var currentY = MARGIN_Y

            val titlePaint = TextPaint().apply {
                color = COLOR_PRIMARY
                textSize = 18f
                isFakeBoldText = true
                isAntiAlias = true
            }

            val subtitlePaint = TextPaint().apply {
                color = COLOR_ACCENT
                textSize = 9.5f
                isFakeBoldText = true
                isAntiAlias = true
                letterSpacing = 0.08f
            }

            val metaPaint = TextPaint().apply {
                color = COLOR_MUTED
                textSize = 9.5f
                isAntiAlias = true
            }

            val h2Paint = TextPaint().apply {
                color = COLOR_ACCENT
                textSize = 11.5f
                isFakeBoldText = true
                isAntiAlias = true
            }

            val bodyBoldPaint = TextPaint().apply {
                color = COLOR_PRIMARY
                textSize = 9.5f
                isFakeBoldText = true
                isAntiAlias = true
            }

            val bodyPaint = TextPaint().apply {
                color = COLOR_TEXT
                textSize = 9.5f
                isAntiAlias = true
            }

            val linePaint = Paint().apply {
                color = COLOR_BORDER
                strokeWidth = 1f
                isAntiAlias = true
            }

            fun checkPageBreak(requiredHeight: Float) {
                if (currentY + requiredHeight > (PAGE_HEIGHT - MARGIN_Y - 20f)) {
                    document.finishPage(currentPage)
                    val next = startNewPage()
                    currentPage = next.first
                    canvas = next.second
                    currentY = MARGIN_Y + 10f
                }
            }

            fun drawStaticText(text: String, paint: TextPaint, x: Float, width: Int): Float {
                val layout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(2f, 1f)
                        .setIncludePad(false)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    StaticLayout(text, paint, width, Layout.Alignment.ALIGN_NORMAL, 1f, 2f, false)
                }
                checkPageBreak(layout.height.toFloat() + 4f)
                canvas.save()
                canvas.translate(x, currentY)
                layout.draw(canvas)
                canvas.restore()
                val height = layout.height.toFloat()
                currentY += height
                return height
            }

            // 1. HEADER BANNER
            canvas.drawText("MEETREC • INFORME EJECUTIVO DE REUNIÓN", MARGIN_X, currentY + 10f, subtitlePaint)
            currentY += 18f

            drawStaticText(session.title, titlePaint, MARGIN_X, CONTENT_WIDTH)
            currentY += 4f

            val metaLine = buildString {
                append("Fecha: ").append(session.formattedDate)
                append("   •   Duración: ").append(session.formattedDuration)
                if (session.participants.isNotBlank()) {
                    append("   •   Participantes: ").append(session.participants)
                }
            }
            drawStaticText(metaLine, metaPaint, MARGIN_X, CONTENT_WIDTH)
            currentY += 8f

            // Separador
            canvas.drawLine(MARGIN_X, currentY, MARGIN_X + CONTENT_WIDTH, currentY, linePaint)
            currentY += 14f

            // 2. RESUMEN EJECUTIVO (Si existe)
            if (summary != null) {
                // Sección 1: Objetivo
                canvas.drawText("1. OBJETIVO DE LA REUNIÓN", MARGIN_X, currentY + 10f, h2Paint)
                currentY += 16f
                drawStaticText(summary.objective, bodyPaint, MARGIN_X, CONTENT_WIDTH)
                currentY += 12f

                // Sección 2: Puntos Tratados
                if (summary.topicsDiscussed.isNotEmpty()) {
                    checkPageBreak(30f)
                    canvas.drawText("2. PUNTOS TRATADOS Y DISCUSIÓN", MARGIN_X, currentY + 10f, h2Paint)
                    currentY += 16f

                    summary.topicsDiscussed.forEach { topic ->
                        checkPageBreak(25f)
                        drawStaticText("• ${topic.title}", bodyBoldPaint, MARGIN_X, CONTENT_WIDTH)
                        currentY += 2f
                        drawStaticText(topic.summary, bodyPaint, MARGIN_X + 12f, CONTENT_WIDTH - 12)
                        currentY += 6f
                    }
                    currentY += 6f
                }

                // Sección 3: Acuerdos Clave
                if (summary.keyAgreements.isNotEmpty()) {
                    checkPageBreak(30f)
                    canvas.drawText("3. ACUERDOS CLAVE", MARGIN_X, currentY + 10f, h2Paint)
                    currentY += 16f

                    summary.keyAgreements.forEach { agr ->
                        checkPageBreak(18f)
                        drawStaticText("✓  $agr", bodyPaint, MARGIN_X + 4f, CONTENT_WIDTH - 4)
                        currentY += 4f
                    }
                    currentY += 8f
                }

                // Sección 4: Plan de Acción y Tareas (To-Do List)
                if (summary.actionItems.isNotEmpty()) {
                    checkPageBreak(40f)
                    canvas.drawText("4. PLAN DE ACCIÓN Y COMPROMISOS (TO-DO LIST)", MARGIN_X, currentY + 10f, h2Paint)
                    currentY += 16f

                    // Cabecera de Tabla
                    val col1W = (CONTENT_WIDTH * 0.58f).toInt()
                    val col2W = (CONTENT_WIDTH * 0.22f).toInt()
                    val col3W = (CONTENT_WIDTH * 0.20f).toInt()

                    val tableBgPaint = Paint().apply {
                        color = COLOR_BG_LIGHT
                        style = Paint.Style.FILL
                    }
                    val tableBorderPaint = Paint().apply {
                        color = COLOR_BORDER
                        style = Paint.Style.STROKE
                        strokeWidth = 1f
                    }

                    checkPageBreak(22f)
                    val headerRect = RectF(MARGIN_X, currentY, MARGIN_X + CONTENT_WIDTH, currentY + 20f)
                    canvas.drawRoundRect(headerRect, 4f, 4f, tableBgPaint)
                    canvas.drawRoundRect(headerRect, 4f, 4f, tableBorderPaint)

                    canvas.drawText("TAREA / COMPROMISO", MARGIN_X + 8f, currentY + 14f, bodyBoldPaint)
                    canvas.drawText("RESPONSABLE", MARGIN_X + col1W + 8f, currentY + 14f, bodyBoldPaint)
                    canvas.drawText("PLAZO", MARGIN_X + col1W + col2W + 8f, currentY + 14f, bodyBoldPaint)
                    currentY += 24f

                    summary.actionItems.forEach { item ->
                        checkPageBreak(26f)
                        val rowStartY = currentY

                        // Tarea
                        val taskLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            StaticLayout.Builder.obtain(item.task, 0, item.task.length, bodyPaint, col1W - 12)
                                .build()
                        } else {
                            @Suppress("DEPRECATION")
                            StaticLayout(item.task, bodyPaint, col1W - 12, Layout.Alignment.ALIGN_NORMAL, 1f, 1f, false)
                        }

                        val rowHeight = (taskLayout.height.toFloat() + 10f).coerceAtLeast(22f)

                        val rowRect = RectF(MARGIN_X, rowStartY, MARGIN_X + CONTENT_WIDTH, rowStartY + rowHeight)
                        canvas.drawRoundRect(rowRect, 2f, 2f, tableBorderPaint)

                        canvas.save()
                        canvas.translate(MARGIN_X + 8f, rowStartY + 5f)
                        taskLayout.draw(canvas)
                        canvas.restore()

                        canvas.drawText(item.assignee, MARGIN_X + col1W + 8f, rowStartY + 14f, bodyBoldPaint)
                        canvas.drawText(item.deadline, MARGIN_X + col1W + col2W + 8f, rowStartY + 14f, metaPaint)

                        currentY += rowHeight + 4f
                    }
                    currentY += 14f
                }
            }

            // 3. TRANSCRIPCIÓN ÍNTEGRA DE RESPALDO
            if (transcript != null && transcript.fullText.isNotBlank()) {
                // Siempre forzar salto de página para que la transcripción empiece en página limpia
                document.finishPage(currentPage)
                val newP = startNewPage()
                currentPage = newP.first
                canvas = newP.second
                currentY = MARGIN_Y + 10f

                canvas.drawText("TRANSCRIPCIÓN COMPLETA DE LA REUNIÓN", MARGIN_X, currentY + 10f, h2Paint)
                currentY += 18f
                canvas.drawLine(MARGIN_X, currentY, MARGIN_X + CONTENT_WIDTH, currentY, linePaint)
                currentY += 14f

                if (transcript.segments.isNotEmpty()) {
                    transcript.segments.forEach { seg ->
                        val timeStr = seg.formattedTimestamp
                        checkPageBreak(30f)
                        canvas.drawText(timeStr, MARGIN_X, currentY + 10f, subtitlePaint)
                        currentY += 14f
                        drawStaticText(seg.text, bodyPaint, MARGIN_X + 6f, CONTENT_WIDTH - 6)
                        currentY += 8f
                    }
                } else {
                    drawStaticText(transcript.fullText, bodyPaint, MARGIN_X, CONTENT_WIDTH)
                }
            }

            // Terminar última página
            document.finishPage(currentPage)

            // Escribir archivo final
            FileOutputStream(outputFile).use { fos ->
                document.writeTo(fos)
            }
            document.close()

            Result.success(outputFile)
        } catch (e: Exception) {
            e.printStackTrace()
            try { document.close() } catch (_: Exception) {}
            Result.failure(e)
        }
    }
}

