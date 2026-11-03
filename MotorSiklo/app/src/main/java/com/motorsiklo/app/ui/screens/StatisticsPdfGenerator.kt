package com.motorsiklo.app.ui.screens

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object StatisticsPdfGenerator {

    private val generatedDate = SimpleDateFormat("MMMM dd, yyyy hh:mm a", Locale.getDefault()).format(Date())

    // ── Violation Colors ──
    private val colorHelmet = Color.parseColor("#FF5252")
    private val colorOverloading = Color.parseColor("#64B5F6")
    private val colorOverspeeding = Color.parseColor("#CE93D8")
    private val colorParking = Color.parseColor("#FFB74D")
    private val colorManual = Color.parseColor("#9E9E9E")

    // ── Per-year theme colors, cycled if there are more years than colors ──
    private val yearThemeColors = listOf(
        Color.parseColor("#F57C00"), Color.parseColor("#00897B"), Color.parseColor("#5E35B1"),
        Color.parseColor("#C2185B"), Color.parseColor("#3949AB"), Color.parseColor("#558B2F")
    )

    @RequiresApi(Build.VERSION_CODES.Q)
    fun generatePdf(
        context: Context,
        dailyHelmet: Int, dailyOverloading: Int, dailyOverspeeding: Int, dailyIllegalParking: Int, dailyManual: Int,
        weeklyHelmet: Int, weeklyOverloading: Int, weeklyOverspeeding: Int, weeklyIllegalParking: Int, weeklyManual: Int,
        monthlyHelmet: Int, monthlyOverloading: Int, monthlyOverspeeding: Int, monthlyIllegalParking: Int, monthlyManual: Int,
        // ── YEAR SEGREGATION: one entry per year chosen in the export dialog. ──
        // Single entry = a specific year. Multiple entries = "All Years" was picked, which also
        // appends a year-by-year comparison page at the end.
        yearlyBreakdowns: List<YearlyBreakdown>
    ) {
        val dailyTotal = dailyHelmet + dailyOverloading + dailyOverspeeding + dailyIllegalParking + dailyManual
        val weeklyTotal = weeklyHelmet + weeklyOverloading + weeklyOverspeeding + weeklyIllegalParking + weeklyManual
        val monthlyTotal = monthlyHelmet + monthlyOverloading + monthlyOverspeeding + monthlyIllegalParking + monthlyManual
        val annualTotal = yearlyBreakdowns.sumOf { it.total }

        val pdfDocument = PdfDocument()
        val allViolations = ViolationRepository.violations
        val isMultiYear = yearlyBreakdowns.size > 1

        if (isMultiYear && dailyTotal == 0 && weeklyTotal == 0 && monthlyTotal == 0 && annualTotal == 0) {
            Toast.makeText(context, "No data available to generate PDF.", Toast.LENGTH_SHORT).show()
            pdfDocument.close()
            return
        }

        if (!isMultiYear && yearlyBreakdowns.firstOrNull()?.total == 0) {
            drawEmptyYearReportPage(pdfDocument, yearlyBreakdowns.first().year)
            savePdfDocument(context, pdfDocument, yearlyBreakdowns, isMultiYear = false)
            return
        }

        // Total page count: Daily + Weekly + Monthly + 1 page per year (+1 comparison page if multi-year)
        val totalPages = 3 + yearlyBreakdowns.size + (if (isMultiYear) 1 else 0)

        // ── PAGE 1: DAILY (Blue Theme) — real per-hour totals, sampled every 4 hours ──
        val hourlyTotals = deriveHourlyTotals(allViolations)
        val dailyLabels = listOf("12 AM", "4 AM", "8 AM", "12 PM", "4 PM", "8 PM")
        val dailyTrendValues = listOf(0, 4, 8, 12, 16, 20).map { hourlyTotals[it] }
        drawReportPage(
            document = pdfDocument, pageNum = 1, totalPages = totalPages, title = "DAILY VIEW", total = dailyTotal,
            helmet = dailyHelmet, overloading = dailyOverloading, overspeeding = dailyOverspeeding, parking = dailyIllegalParking, manual = dailyManual,
            themeColor = Color.parseColor("#1976D2"), trendLabels = dailyLabels, trendValues = dailyTrendValues
        )

        // ── PAGE 2: WEEKLY (Green Theme) — real per-day totals, Mon–Sun ──
        val weeklyLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val weeklyTrendValues = deriveWeeklyTotals(allViolations)
        drawReportPage(
            document = pdfDocument, pageNum = 2, totalPages = totalPages, title = "WEEKLY VIEW", total = weeklyTotal,
            helmet = weeklyHelmet, overloading = weeklyOverloading, overspeeding = weeklyOverspeeding, parking = weeklyIllegalParking, manual = weeklyManual,
            themeColor = Color.parseColor("#388E3C"), trendLabels = weeklyLabels, trendValues = weeklyTrendValues
        )

        // ── PAGE 3: MONTHLY (Purple Theme) — real per-day totals for the current month ──
        val (monthlyTrendLabels, monthlyTrendValues) = deriveMonthlyTotals(allViolations)
        drawReportPage(
            document = pdfDocument, pageNum = 3, totalPages = totalPages, title = "MONTHLY VIEW", total = monthlyTotal,
            helmet = monthlyHelmet, overloading = monthlyOverloading, overspeeding = monthlyOverspeeding, parking = monthlyIllegalParking, manual = monthlyManual,
            themeColor = Color.parseColor("#7B1FA2"), trendLabels = monthlyTrendLabels, trendValues = monthlyTrendValues
        )

        // ── PAGE 4+: ONE ANNUAL PAGE PER SELECTED YEAR — real per-month totals, Jan–Dec ──
        val annualLabels = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        yearlyBreakdowns.forEachIndexed { index, yearStat ->
            val pageNum = 4 + index
            val annualTrendValues = deriveAnnualTotalsForYear(allViolations, yearStat.year)
            val themeColor = yearThemeColors[index % yearThemeColors.size]
            drawReportPage(
                document = pdfDocument, pageNum = pageNum, totalPages = totalPages, title = "ANNUAL VIEW \u00b7 ${yearStat.year}", total = yearStat.total,
                helmet = yearStat.helmet, overloading = yearStat.overloading, overspeeding = yearStat.overspeeding, parking = yearStat.illegalParking, manual = yearStat.manual,
                themeColor = themeColor, trendLabels = annualLabels, trendValues = annualTrendValues,
                emptyMessage = if (yearStat.total == 0) "Year: ${yearStat.year} - No data available yet." else null
            )
        }

        // ── LAST PAGE: YEAR-BY-YEAR COMPARISON (only when "All Years" was selected) ──
        if (isMultiYear) {
            drawYearComparisonPage(
                document = pdfDocument, pageNum = totalPages, totalPages = totalPages,
                yearlyBreakdowns = yearlyBreakdowns
            )
        }

        // ── SAVE TO MEDIASTORE ──
        try {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
            val fileSuffix = if (isMultiYear) "AllYears" else yearlyBreakdowns.firstOrNull()?.year?.toString() ?: "Report"
            val fileName = "MotorSiklo_Analytics_${fileSuffix}_$stamp.pdf"
            val resolver = context.contentResolver

            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MotorSiklo Reports")
            }

            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    pdfDocument.writeTo(outputStream)
                }
                Toast.makeText(context, "PDF saved to Downloads/MotorSiklo Reports", Toast.LENGTH_LONG).show()
                Log.d("PDF_SAVE", "Successfully saved PDF: $fileName")
            } else {
                Toast.makeText(context, "Failed to create PDF file.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to save PDF: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("PDF_ERROR", "Error saving PDF", e)
        } finally {
            pdfDocument.close()
        }
    }

    private fun savePdfDocument(
        context: Context,
        pdfDocument: PdfDocument,
        yearlyBreakdowns: List<YearlyBreakdown>,
        isMultiYear: Boolean
    ) {
        try {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
            val fileSuffix = if (isMultiYear) "AllYears" else yearlyBreakdowns.firstOrNull()?.year?.toString() ?: "Report"
            val fileName = "MotorSiklo_Analytics_${fileSuffix}_$stamp.pdf"
            val resolver = context.contentResolver

            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MotorSiklo Reports")
            }

            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    pdfDocument.writeTo(outputStream)
                }
                Toast.makeText(context, "PDF saved to Downloads/MotorSiklo Reports", Toast.LENGTH_LONG).show()
                Log.d("PDF_SAVE", "Successfully saved PDF: $fileName")
            } else {
                Toast.makeText(context, "Failed to create PDF file.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to save PDF: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("PDF_ERROR", "Error saving PDF", e)
        } finally {
            pdfDocument.close()
        }
    }

    private fun drawEmptyYearReportPage(document: PdfDocument, year: Int) {
        val pageWidth = 595
        val pageHeight = 842
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint()
        val themeColor = Color.parseColor("#263238")

        paint.color = themeColor
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 120f, paint)

        paint.color = Color.WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 28f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("MotorSiklo Analytics", 40f, 60f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 12f
        canvas.drawText("Generated: $generatedDate", 40f, 90f, paint)

        paint.color = Color.BLACK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 20f
        canvas.drawText("YEAR REPORT", 40f, 190f, paint)

        paint.color = Color.DKGRAY
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 15f
        canvas.drawText("Year: $year — No data recorded for this year yet.", 40f, 230f, paint)

        paint.color = Color.GRAY
        paint.textSize = 10f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("MotorSiklo Automated Traffic Apprehension System - Page 1 of 1", pageWidth / 2f, pageHeight - 30f, paint)

        document.finishPage(page)
    }

    private fun drawReportPage(
        document: PdfDocument, pageNum: Int, totalPages: Int, title: String, total: Int,
        helmet: Int, overloading: Int, overspeeding: Int, parking: Int, manual: Int,
        themeColor: Int, trendLabels: List<String>, trendValues: List<Int>,
        emptyMessage: String? = null
    ) {
        // Standard A4 Size
        val pageWidth = 595
        val pageHeight = 842
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint()

        // ── 1. Header Background ──
        paint.color = themeColor
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 120f, paint)

        // ── 2. Header Text ──
        paint.color = Color.WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 28f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("MotorSiklo Analytics", 40f, 60f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 12f
        canvas.drawText("Generated: $generatedDate", 40f, 90f, paint)

        paint.textAlign = Paint.Align.RIGHT
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 20f
        canvas.drawText(title, pageWidth - 40f, 70f, paint)

        // ── 3. Total Metric Box ──
        val metricY = 160f
        paint.color = Color.parseColor("#F5F5F5")
        canvas.drawRoundRect(RectF(40f, metricY, pageWidth - 40f, metricY + 80f), 12f, 12f, paint)

        paint.color = Color.BLACK
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 14f
        canvas.drawText("TOTAL INCIDENTS", 60f, metricY + 35f, paint)

        paint.color = themeColor
        paint.textSize = 42f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("$total", 60f, metricY + 70f, paint)

        if (emptyMessage != null) {
            paint.color = Color.DKGRAY
            paint.textSize = 13f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(emptyMessage, 220f, metricY + 48f, paint)
        }

        // ── 4. Type Distribution (Pie Chart & Legend) ──
        val distributionY = 280f
        paint.color = Color.BLACK
        paint.textSize = 16f
        canvas.drawText("TYPE DISTRIBUTION", 40f, distributionY, paint)

        val pieRect = RectF(60f, distributionY + 20f, 260f, distributionY + 220f)
        if (total == 0) {
            paint.color = Color.LTGRAY
            canvas.drawArc(pieRect, 0f, 360f, true, paint)
        } else {
            var startAngle = 0f
            val drawSegment = { value: Int, color: Int ->
                if (value > 0) {
                    val sweepAngle = (value.toFloat() / total) * 360f
                    paint.color = color
                    canvas.drawArc(pieRect, startAngle, sweepAngle, true, paint)
                    startAngle += sweepAngle
                }
            }
            drawSegment(helmet, colorHelmet)
            drawSegment(overloading, colorOverloading)
            drawSegment(overspeeding, colorOverspeeding)
            drawSegment(parking, colorParking)
            drawSegment(manual, colorManual)
        }

        // Legend
        val legendX = 300f
        var legendY = distributionY + 50f
        val drawLegendItem = { label: String, count: Int, color: Int ->
            paint.color = color
            canvas.drawRect(legendX, legendY - 10f, legendX + 15f, legendY + 5f, paint)
            paint.color = Color.DKGRAY
            paint.textSize = 12f
            canvas.drawText("$label: $count", legendX + 25f, legendY + 2f, paint)
            legendY += 30f
        }

        drawLegendItem("No/Improper Helmet", helmet, colorHelmet)
        drawLegendItem("Overloading", overloading, colorOverloading)
        drawLegendItem("Overspeeding", overspeeding, colorOverspeeding)
        drawLegendItem("Illegal Parking", parking, colorParking)
        drawLegendItem("Manual Entry", manual, colorManual)

        // ── 5. Trend Line Graph ──
        val graphY = 560f
        paint.color = Color.BLACK
        paint.textSize = 16f
        canvas.drawText("TREND ANALYSIS", 40f, graphY, paint)

        val graphRect = RectF(40f, graphY + 30f, pageWidth - 40f, graphY + 200f)

        // Draw Grid Lines
        paint.color = Color.parseColor("#E0E0E0")
        paint.strokeWidth = 1f
        val steps = 4
        for (i in 0..steps) {
            val y = graphRect.bottom - (i * (graphRect.height() / steps))
            canvas.drawLine(graphRect.left, y, graphRect.right, y, paint)
        }

        // Real per-period data points (computed from actual violation timestamps)
        val points = mutableListOf<PointF>()
        val spacing = graphRect.width() / (trendLabels.size - 1).coerceAtLeast(1)

        val hasData = trendValues.any { it > 0 }
        if (hasData) {
            val maxVal = trendValues.maxOrNull()?.toFloat()?.coerceAtLeast(1f) ?: 1f

            trendValues.forEachIndexed { index, value ->
                val x = graphRect.left + (index * spacing)
                val normalizedY = (value / maxVal) * graphRect.height()
                val y = graphRect.bottom - normalizedY
                points.add(PointF(x, y))
            }

            // Draw Smooth Bezier Curve
            val linePath = Path()
            linePath.moveTo(points[0].x, points[0].y)
            for (i in 0 until points.size - 1) {
                val cp1x = points[i].x + spacing / 2f
                val cp1y = points[i].y
                val cp2x = points[i + 1].x - spacing / 2f
                val cp2y = points[i + 1].y
                linePath.cubicTo(cp1x, cp1y, cp2x, cp2y, points[i + 1].x, points[i + 1].y)
            }

            // Path Stroke
            paint.reset()
            paint.color = themeColor
            paint.strokeWidth = 3f
            paint.style = Paint.Style.STROKE
            paint.isAntiAlias = true
            canvas.drawPath(linePath, paint)

            // Draw Dots
            points.forEach { pt ->
                paint.reset()
                paint.color = themeColor
                paint.style = Paint.Style.FILL
                canvas.drawCircle(pt.x, pt.y, 5f, paint)

                paint.color = Color.WHITE
                canvas.drawCircle(pt.x, pt.y, 2.5f, paint)
            }
        } else {
            // Flat line for zero data across the period
            paint.color = Color.LTGRAY
            paint.strokeWidth = 3f
            canvas.drawLine(graphRect.left, graphRect.bottom, graphRect.right, graphRect.bottom, paint)
        }

        // X-Axis Labels
        paint.reset()
        paint.color = Color.DKGRAY
        paint.textSize = 10f
        paint.textAlign = Paint.Align.CENTER
        trendLabels.forEachIndexed { index, label ->
            // For dense periods (e.g. 28-31 daily points), only draw every few labels to avoid crowding
            val labelStep = if (trendLabels.size > 12) 5 else 1
            if (index % labelStep == 0 || index == trendLabels.size - 1) {
                val x = graphRect.left + (index * spacing)
                canvas.drawText(label, x, graphRect.bottom + 20f, paint)
            }
        }

        // ── 6. Footer ──
        paint.color = Color.GRAY
        paint.textSize = 10f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("MotorSiklo Automated Traffic Apprehension System \u2022 Page $pageNum of $totalPages", pageWidth / 2f, pageHeight - 30f, paint)

        document.finishPage(page)
    }

    /**
     * Final summary page when "All Years" is selected: a grouped bar chart of totals per
     * violation type across every year, plus a year-over-year totals table underneath.
     */
    private fun drawYearComparisonPage(
        document: PdfDocument, pageNum: Int, totalPages: Int,
        yearlyBreakdowns: List<YearlyBreakdown>
    ) {
        val pageWidth = 595
        val pageHeight = 842
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint()
        val headerColor = Color.parseColor("#263238")

        // ── Header ──
        paint.color = headerColor
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 120f, paint)

        paint.color = Color.WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 28f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("MotorSiklo Analytics", 40f, 60f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 12f
        canvas.drawText("Generated: $generatedDate", 40f, 90f, paint)

        paint.textAlign = Paint.Align.RIGHT
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 20f
        canvas.drawText("YEAR COMPARISON", pageWidth - 40f, 70f, paint)

        // ── Grouped Bar Chart: total per year ──
        paint.color = Color.BLACK
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 16f
        canvas.drawText("TOTAL VIOLATIONS BY YEAR", 40f, 160f, paint)

        val chartRect = RectF(60f, 190f, pageWidth - 40f, 420f)
        paint.color = Color.parseColor("#E0E0E0")
        paint.strokeWidth = 1f
        for (i in 0..4) {
            val y = chartRect.bottom - (i * (chartRect.height() / 4))
            canvas.drawLine(chartRect.left, y, chartRect.right, y, paint)
        }

        val maxTotal = yearlyBreakdowns.maxOfOrNull { it.total }?.toFloat()?.coerceAtLeast(1f) ?: 1f
        val slotWidth = chartRect.width() / yearlyBreakdowns.size
        val barWidth = slotWidth * 0.5f

        yearlyBreakdowns.forEachIndexed { index, yearStat ->
            val barHeight = (yearStat.total / maxTotal) * chartRect.height()
            val left = chartRect.left + index * slotWidth + (slotWidth - barWidth) / 2f
            val top = chartRect.bottom - barHeight
            val right = left + barWidth

            paint.color = yearThemeColors[index % yearThemeColors.size]
            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(RectF(left, top, right, chartRect.bottom), 6f, 6f, paint)

            paint.color = Color.DKGRAY
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = 11f
            canvas.drawText("${yearStat.total}", left + barWidth / 2f, top - 8f, paint)

            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("${yearStat.year}", left + barWidth / 2f, chartRect.bottom + 20f, paint)
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        // ── Year-by-Year table ──
        paint.color = Color.BLACK
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 16f
        canvas.drawText("YEAR-BY-YEAR BREAKDOWN", 40f, 470f, paint)

        val tableTop = 495f
        val rowHeight = 26f
        val colWidths = listOf(150f) + List(yearlyBreakdowns.size) { (pageWidth - 40f - 40f - 150f) / yearlyBreakdowns.size }
        val rowLabels = listOf("No/Improper Helmet", "Overloading", "Overspeeding", "Illegal Parking", "Manual Entry", "TOTAL")

        // Header row
        var rowY = tableTop
        paint.color = Color.parseColor("#F5F5F5")
        canvas.drawRect(40f, rowY, pageWidth - 40f, rowY + rowHeight, paint)
        paint.color = Color.BLACK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 11f
        var colX = 40f + colWidths[0]
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("Violation Type", 50f, rowY + 17f, paint)
        paint.textAlign = Paint.Align.CENTER
        yearlyBreakdowns.forEachIndexed { i, yearStat ->
            canvas.drawText("${yearStat.year}", colX + colWidths[i + 1] / 2f, rowY + 17f, paint)
            colX += colWidths[i + 1]
        }
        rowY += rowHeight

        // Data rows
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        rowLabels.forEachIndexed { rIdx, label ->
            if (rIdx % 2 == 1) {
                paint.color = Color.parseColor("#FAFAFA")
                canvas.drawRect(40f, rowY, pageWidth - 40f, rowY + rowHeight, paint)
            }
            paint.color = if (label == "TOTAL") Color.BLACK else Color.DKGRAY
            paint.typeface = if (label == "TOTAL") Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textAlign = Paint.Align.LEFT
            paint.textSize = 11f
            canvas.drawText(label, 50f, rowY + 17f, paint)

            colX = 40f + colWidths[0]
            paint.textAlign = Paint.Align.CENTER
            yearlyBreakdowns.forEach { yearStat ->
                val value = when (label) {
                    "No/Improper Helmet" -> yearStat.helmet
                    "Overloading" -> yearStat.overloading
                    "Overspeeding" -> yearStat.overspeeding
                    "Illegal Parking" -> yearStat.illegalParking
                    "Manual Entry" -> yearStat.manual
                    else -> yearStat.total
                }
                canvas.drawText("$value", colX + (colWidths[yearlyBreakdowns.indexOf(yearStat) + 1]) / 2f, rowY + 17f, paint)
                colX += colWidths[yearlyBreakdowns.indexOf(yearStat) + 1]
            }
            rowY += rowHeight
        }

        // ── Footer ──
        paint.color = Color.GRAY
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 10f
        canvas.drawText("MotorSiklo Automated Traffic Apprehension System \u2022 Page $pageNum of $totalPages", pageWidth / 2f, pageHeight - 30f, paint)

        document.finishPage(page)
    }

    // ── Real per-period trend totals (computed from actual violation timestamps) ──

    /** Returns total violation count for each of the 24 hours of today. */
    private fun deriveHourlyTotals(allViolations: List<ViolationEntry>): List<Int> {
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0);      set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val todayViolations = allViolations.filter { it.timestamp >= startOfDay }

        return (0..23).map { hour ->
            val hourStart = Calendar.getInstance().apply { timeInMillis = startOfDay; set(Calendar.HOUR_OF_DAY, hour) }.timeInMillis
            val hourEnd = hourStart + 3599999L
            todayViolations.count { it.timestamp in hourStart..hourEnd }
        }
    }

    /** Returns total violation count for each day Mon..Sun of this week. */
    private fun deriveWeeklyTotals(allViolations: List<ViolationEntry>): List<Int> {
        val startOfWeek = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0);      set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val weekViolations = allViolations.filter { it.timestamp >= startOfWeek }

        return (0..6).map { offset ->
            val dayStart = Calendar.getInstance().apply { timeInMillis = startOfWeek; add(Calendar.DAY_OF_YEAR, offset) }.timeInMillis
            val dayEnd = dayStart + 86399999L
            weekViolations.count { it.timestamp in dayStart..dayEnd }
        }
    }

    /** Returns (day-of-month labels, totals) for every day of the current month. */
    private fun deriveMonthlyTotals(allViolations: List<ViolationEntry>): Pair<List<String>, List<Int>> {
        val startOfMonthCal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0);      set(Calendar.MILLISECOND, 0)
        }
        val startOfMonth = startOfMonthCal.timeInMillis
        val maxDays = startOfMonthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val monthViolations = allViolations.filter { it.timestamp >= startOfMonth }

        val labels = mutableListOf<String>()
        val totals = mutableListOf<Int>()
        for (day in 1..maxDays) {
            val dayStart = Calendar.getInstance().apply { timeInMillis = startOfMonth; set(Calendar.DAY_OF_MONTH, day) }.timeInMillis
            val dayEnd = dayStart + 86399999L
            totals.add(monthViolations.count { it.timestamp in dayStart..dayEnd })
            labels.add(day.toString())
        }
        return Pair(labels, totals)
    }

    /** Returns total violation count for each month Jan..Dec of the given [year] (any year, not just the current one). */
    private fun deriveAnnualTotalsForYear(allViolations: List<ViolationEntry>, year: Int): List<Int> {
        val startOfYear = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, Calendar.JANUARY); set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0);      set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val yearViolations = allViolations.filter { it.year == year }

        return (0..11).map { month ->
            val mStart = Calendar.getInstance().apply { timeInMillis = startOfYear; set(Calendar.MONTH, month) }.timeInMillis
            val mEnd = Calendar.getInstance().apply {
                timeInMillis = mStart
                set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
            }.timeInMillis
            yearViolations.count { it.timestamp in mStart..mEnd }
        }
    }
}
