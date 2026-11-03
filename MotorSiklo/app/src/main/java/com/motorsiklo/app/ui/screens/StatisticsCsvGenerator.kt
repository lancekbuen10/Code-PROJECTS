package com.motorsiklo.app.ui.screens

import android.content.ContentValues
import android.content.Context
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

object StatisticsCsvGenerator {

    @RequiresApi(Build.VERSION_CODES.Q)
    fun generateCsv(
        context: Context,
        dailyHelmet: Int, dailyOverloading: Int, dailyOverspeeding: Int, dailyIllegalParking: Int, dailyManual: Int,
        weeklyHelmet: Int, weeklyOverloading: Int, weeklyOverspeeding: Int, weeklyIllegalParking: Int, weeklyManual: Int,
        monthlyHelmet: Int, monthlyOverloading: Int, monthlyOverspeeding: Int, monthlyIllegalParking: Int, monthlyManual: Int,
        // ── YEAR SEGREGATION: one entry per year the user chose to include. ──
        // Single entry = one specific year was picked. Multiple entries = "All Years" was picked.
        yearlyBreakdowns: List<YearlyBreakdown>
    ) {
        val dailyTotal   = dailyHelmet + dailyOverloading + dailyOverspeeding + dailyIllegalParking + dailyManual
        val weeklyTotal  = weeklyHelmet + weeklyOverloading + weeklyOverspeeding + weeklyIllegalParking + weeklyManual
        val monthlyTotal = monthlyHelmet + monthlyOverloading + monthlyOverspeeding + monthlyIllegalParking + monthlyManual
        val annualTotal  = yearlyBreakdowns.sumOf { it.total }

        // ── Early-exit guard checks ALL PERIODS ──
        val sb = java.lang.StringBuilder()
        val allViolations = ViolationRepository.violations
        val isMultiYear = yearlyBreakdowns.size > 1

        if (isMultiYear && dailyTotal == 0 && weeklyTotal == 0 && monthlyTotal == 0 && annualTotal == 0) {
            Toast.makeText(context, "No data available to generate CSV.", Toast.LENGTH_SHORT).show()
            return
        }

        // ── 1. SUMMARY TABLE ──
        sb.appendLine("MOTORSIKLO ANALYTICS REPORT")
        sb.appendLine("Generated on:,${SimpleDateFormat("MMMM dd yyyy hh:mm a", Locale.getDefault()).format(Date())}")
        val yearLabel = if (isMultiYear) "All Years (${yearlyBreakdowns.first().year}-${yearlyBreakdowns.last().year})" else yearlyBreakdowns.firstOrNull()?.year?.toString() ?: "N/A"
        sb.appendLine("Report year scope:,$yearLabel")
        sb.appendLine()

        if (!isMultiYear && yearlyBreakdowns.firstOrNull()?.total == 0) {
            sb.appendLine("Year: ${yearlyBreakdowns.first().year} — No data recorded for this year yet.")
            sb.appendLine()
            saveCsv(context, sb.toString(), isMultiYear, yearLabel)
            return
        }

        val annualColumnLabel = if (isMultiYear) "ALL YEARS" else yearLabel
        sb.appendLine("VIOLATION TYPE,DAILY,WEEKLY,MONTHLY,$annualColumnLabel")
        sb.appendLine("No/Improper Helmet,$dailyHelmet,$weeklyHelmet,$monthlyHelmet,${yearlyBreakdowns.sumOf { it.helmet }}")
        sb.appendLine("Overloading,$dailyOverloading,$weeklyOverloading,$monthlyOverloading,${yearlyBreakdowns.sumOf { it.overloading }}")
        sb.appendLine("Overspeeding,$dailyOverspeeding,$weeklyOverspeeding,$monthlyOverspeeding,${yearlyBreakdowns.sumOf { it.overspeeding }}")
        sb.appendLine("Illegal Parking,$dailyIllegalParking,$weeklyIllegalParking,$monthlyIllegalParking,${yearlyBreakdowns.sumOf { it.illegalParking }}")
        sb.appendLine("Manual Entry,$dailyManual,$weeklyManual,$monthlyManual,${yearlyBreakdowns.sumOf { it.manual }}")
        sb.appendLine("TOTAL,$dailyTotal,$weeklyTotal,$monthlyTotal,$annualTotal")
        sb.appendLine()

        // ── 2. HOURLY TREND (Today) ──
        sb.appendLine("HOURLY TREND (Today)")
        sb.appendLine("Time,Total,Helmet,Overloading,Overspeeding,Parking,Manual")
        val startOfDay = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis
        val todayViolations = allViolations.filter { it.timestamp >= startOfDay }

        for (hour in 0..23) {
            val cal = Calendar.getInstance().apply { timeInMillis = startOfDay; set(Calendar.HOUR_OF_DAY, hour) }
            val hourStart = cal.timeInMillis
            val hourEnd = hourStart + 3599999L
            val slice = todayViolations.filter { it.timestamp in hourStart..hourEnd }

            val h = slice.count { it.types.contains(ViolationType.NO_HELMET) }
            val ol = slice.count { it.types.contains(ViolationType.OVERLOADING) }
            val os = slice.count { it.types.contains(ViolationType.OVERSPEEDING) }
            val ip = slice.count { it.types.contains(ViolationType.ILLEGAL_PARKING) }
            val m = slice.count { it.types.contains(ViolationType.MANUAL) }

            val label = SimpleDateFormat("h:00 a", Locale.getDefault()).format(cal.time)
            sb.appendLine("$label,${slice.size},$h,$ol,$os,$ip,$m")
        }
        sb.appendLine()

        // ── 3. WEEKLY TREND (This Week) ──
        sb.appendLine("WEEKLY TREND (This Week)")
        sb.appendLine("Day,Total,Helmet,Overloading,Overspeeding,Parking,Manual")
        val startOfWeek = Calendar.getInstance().apply { firstDayOfWeek = Calendar.MONDAY; set(Calendar.DAY_OF_WEEK, Calendar.MONDAY); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis
        val weekViolations = allViolations.filter { it.timestamp >= startOfWeek }

        for (offset in 0..6) {
            val cal = Calendar.getInstance().apply { timeInMillis = startOfWeek; add(Calendar.DAY_OF_YEAR, offset) }
            val dayStart = cal.timeInMillis
            val dayEnd = dayStart + 86399999L
            val slice = weekViolations.filter { it.timestamp in dayStart..dayEnd }

            val h = slice.count { it.types.contains(ViolationType.NO_HELMET) }
            val ol = slice.count { it.types.contains(ViolationType.OVERLOADING) }
            val os = slice.count { it.types.contains(ViolationType.OVERSPEEDING) }
            val ip = slice.count { it.types.contains(ViolationType.ILLEGAL_PARKING) }
            val m = slice.count { it.types.contains(ViolationType.MANUAL) }

            val label = SimpleDateFormat("EEEE", Locale.getDefault()).format(cal.time)
            sb.appendLine("$label,${slice.size},$h,$ol,$os,$ip,$m")
        }
        sb.appendLine()

        // ── 4. MONTHLY TREND (Current Month) ──
        sb.appendLine("MONTHLY TREND (This Month)")
        sb.appendLine("Date,Total,Helmet,Overloading,Overspeeding,Parking,Manual")
        val startOfMonthCal = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }
        val startOfMonth = startOfMonthCal.timeInMillis
        val maxDays = startOfMonthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val monthViolations = allViolations.filter { it.timestamp >= startOfMonth }

        for (day in 1..maxDays) {
            val cal = Calendar.getInstance().apply { timeInMillis = startOfMonth; set(Calendar.DAY_OF_MONTH, day) }
            val dayStart = cal.timeInMillis
            val dayEnd = dayStart + 86399999L
            val slice = monthViolations.filter { it.timestamp in dayStart..dayEnd }

            val h = slice.count { it.types.contains(ViolationType.NO_HELMET) }
            val ol = slice.count { it.types.contains(ViolationType.OVERLOADING) }
            val os = slice.count { it.types.contains(ViolationType.OVERSPEEDING) }
            val ip = slice.count { it.types.contains(ViolationType.ILLEGAL_PARKING) }
            val m = slice.count { it.types.contains(ViolationType.MANUAL) }

            val label = SimpleDateFormat("MMM dd", Locale.getDefault()).format(cal.time)
            sb.appendLine("$label,${slice.size},$h,$ol,$os,$ip,$m")
        }
        sb.appendLine()

        // ── 5. ANNUAL TREND — one block per selected year ──
        yearlyBreakdowns.forEach { yearStat ->
            sb.appendLine("ANNUAL TREND (${yearStat.year})")
            if (yearStat.total == 0) {
                sb.appendLine("Year: ${yearStat.year} - No data available yet.")
            }
            sb.appendLine("Month,Total,Helmet,Overloading,Overspeeding,Parking,Manual")

            val startOfYearCal = Calendar.getInstance().apply {
                set(Calendar.YEAR, yearStat.year); set(Calendar.MONTH, Calendar.JANUARY); set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            }
            val startOfYear = startOfYearCal.timeInMillis
            val yearViolations = allViolations.filter { it.year == yearStat.year }

            for (month in 0..11) {
                val calStart = Calendar.getInstance().apply { timeInMillis = startOfYear; set(Calendar.MONTH, month) }
                val mStart = calStart.timeInMillis
                val calEnd = Calendar.getInstance().apply { timeInMillis = mStart; set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH)); set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59) }
                val mEnd = calEnd.timeInMillis

                val slice = yearViolations.filter { it.timestamp in mStart..mEnd }

                val h = slice.count { it.types.contains(ViolationType.NO_HELMET) }
                val ol = slice.count { it.types.contains(ViolationType.OVERLOADING) }
                val os = slice.count { it.types.contains(ViolationType.OVERSPEEDING) }
                val ip = slice.count { it.types.contains(ViolationType.ILLEGAL_PARKING) }
                val m = slice.count { it.types.contains(ViolationType.MANUAL) }

                val label = SimpleDateFormat("MMMM", Locale.getDefault()).format(calStart.time)
                sb.appendLine("$label,${slice.size},$h,$ol,$os,$ip,$m")
            }
            sb.appendLine()
        }

        // ── 6. YEAR-BY-YEAR COMPARISON (only when "All Years" was selected) ──
        if (isMultiYear) {
            sb.appendLine("YEAR-BY-YEAR COMPARISON")
            val yearHeader = yearlyBreakdowns.joinToString(",") { it.year.toString() }
            sb.appendLine("VIOLATION TYPE,$yearHeader")
            sb.appendLine("No/Improper Helmet,${yearlyBreakdowns.joinToString(",") { it.helmet.toString() }}")
            sb.appendLine("Overloading,${yearlyBreakdowns.joinToString(",") { it.overloading.toString() }}")
            sb.appendLine("Overspeeding,${yearlyBreakdowns.joinToString(",") { it.overspeeding.toString() }}")
            sb.appendLine("Illegal Parking,${yearlyBreakdowns.joinToString(",") { it.illegalParking.toString() }}")
            sb.appendLine("Manual Entry,${yearlyBreakdowns.joinToString(",") { it.manual.toString() }}")
            sb.appendLine("TOTAL,${yearlyBreakdowns.joinToString(",") { it.total.toString() }}")
            sb.appendLine()

            // Year-over-year % change, e.g. 2026 -> 2027
            sb.appendLine("YEAR-OVER-YEAR CHANGE")
            sb.appendLine("Comparison,Total (Prior),Total (Next),Change,% Change")
            for (i in 0 until yearlyBreakdowns.size - 1) {
                val prior = yearlyBreakdowns[i]
                val next = yearlyBreakdowns[i + 1]
                val change = next.total - prior.total
                val pctChange = if (prior.total != 0) (change.toFloat() / prior.total * 100f) else 0f
                sb.appendLine("${prior.year} vs ${next.year},${prior.total},${next.total},$change,${"%.1f".format(pctChange)}%")
            }
        }

        // ── Save via MediaStore ──
        try {
            val stamp    = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
            val fileSuffix = if (isMultiYear) "AllYears" else yearLabel
            val fileName = "MotorSiklo_Report_${fileSuffix}_$stamp.csv"
            val resolver = context.contentResolver

            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MotorSiklo Reports")
            }

            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { it.write(sb.toString().toByteArray(Charsets.UTF_8)) }
                Toast.makeText(context, "CSV saved to Downloads/MotorSiklo Reports", Toast.LENGTH_LONG).show()
                Log.d("CSV_SAVE", "Saved CSV: $fileName")
            } else {
                Toast.makeText(context, "Failed to create CSV file.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to save CSV: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("CSV_ERROR", "Error saving CSV", e)
        }
    }

    private fun saveCsv(context: Context, content: String, isMultiYear: Boolean, yearLabel: String) {
        try {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
            val fileSuffix = if (isMultiYear) "AllYears" else yearLabel
            val fileName = "MotorSiklo_Report_${fileSuffix}_$stamp.csv"
            val resolver = context.contentResolver

            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MotorSiklo Reports")
            }

            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                Toast.makeText(context, "CSV saved to Downloads/MotorSiklo Reports", Toast.LENGTH_LONG).show()
                Log.d("CSV_SAVE", "Saved CSV: $fileName")
            } else {
                Toast.makeText(context, "Failed to create CSV file.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to save CSV: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("CSV_ERROR", "Error saving CSV", e)
        }
    }
}
