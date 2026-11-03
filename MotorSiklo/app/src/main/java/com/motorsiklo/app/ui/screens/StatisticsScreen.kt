@file:OptIn(ExperimentalMaterial3Api::class)
package com.motorsiklo.app.ui.screens

import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

data class ChartStat(
    val tooltipLabel: String,
    val helmet: Int,
    val overloading: Int,
    val overspeeding: Int,
    val illegalParking: Int,
    val manual: Int
) {
    val total get() = helmet + overloading + overspeeding + illegalParking + manual
    fun getValue(type: ViolationType): Int = when (type) {
        ViolationType.NO_HELMET       -> helmet
        ViolationType.OVERLOADING     -> overloading
        ViolationType.OVERSPEEDING    -> overspeeding
        ViolationType.ILLEGAL_PARKING -> illegalParking
        ViolationType.MANUAL          -> manual
    }
}

data class ExportData(
    val dH: Int, val dOl: Int, val dOs: Int, val dIp: Int, val dM: Int,
    val wH: Int, val wOl: Int, val wOs: Int, val wIp: Int, val wM: Int,
    val mH: Int, val mOl: Int, val mOs: Int, val mIp: Int, val mM: Int,
    val aH: Int, val aOl: Int, val aOs: Int, val aIp: Int, val aM: Int
)

@RequiresApi(Build.VERSION_CODES.Q)
@Composable
fun StatisticsScreen(role: String) {
    val context = LocalContext.current
    val scroll = rememberScrollState()
    val colorScheme = MaterialTheme.colorScheme
    val allViolations = ViolationRepository.violations

    var showDownloadDialog by remember { mutableStateOf(false) }
    var showExportYearDialog by remember { mutableStateOf(false) }
    // null = "All Years" (a single file covering every year on record)
    var exportYearChoice by remember { mutableStateOf<Int?>(null) }

    val availableTimeframes = if (role == "Administrator") {
        listOf("Daily", "Weekly", "Monthly", "Annual")
    } else {
        listOf("Daily", "Weekly")
    }

    var selectedTimeframe by remember { mutableStateOf(availableTimeframes.first()) }
    var highlightedType by remember { mutableStateOf<ViolationType?>(null) }

    // ── YEAR SEGREGATION: which year the Annual tab is showing ──
    // (MotorSiklo launched in 2026, so this list grows as new years pass: 2026, 2027, 2028 ...)
    val availableYears = remember(allViolations.size) { getAvailableYears(allViolations) }
    var selectedYear by remember { mutableStateOf(getDefaultReportYear()) }

    // ── OPTIMIZATION: Background State Calculation ──
    var isCalculating by remember { mutableStateOf(true) }
    var periodViolations by remember { mutableStateOf<List<ViolationEntry>>(emptyList()) }
    var chartStats by remember { mutableStateOf<List<ChartStat>>(emptyList()) }
    var typeDistribution by remember { mutableStateOf<Map<ViolationType, Int>>(emptyMap()) }
    var exportData by remember { mutableStateOf<ExportData?>(null) }

    LaunchedEffect(allViolations.size, selectedTimeframe, selectedYear) {
        isCalculating = true
        withContext(Dispatchers.Default) {
            val safeList = allViolations.toList()
            val (startOfDay, endOfDay) = getPeriodRangeForYear("Daily", selectedYear)
            val (startOfWeek, endOfWeek) = getPeriodRangeForYear("Weekly", selectedYear)
            val (startOfMonth, endOfMonth) = getPeriodRangeForYear("Monthly", selectedYear)
            val (startOfYear, endOfYear) = getPeriodRangeForYear("Annual", selectedYear)

            // Segregate data
            val dailyV = safeList.filter { it.timestamp in startOfDay..endOfDay }
            val weeklyV = safeList.filter { it.timestamp in startOfWeek..endOfWeek }
            val monthlyV = safeList.filter { it.timestamp in startOfMonth..endOfMonth }
            val annualV = safeList.filter { it.timestamp in startOfYear..endOfYear }

            val pViolations = when (selectedTimeframe) {
                "Annual" -> annualV
                "Monthly" -> monthlyV
                "Weekly" -> weeklyV
                else -> dailyV
            }

            val cStats = when (selectedTimeframe) {
                "Annual" -> deriveAnnualStats(pViolations, startOfYear)
                "Monthly" -> deriveMonthlyStats(pViolations, startOfMonth)
                "Weekly" -> deriveWeeklyStats(pViolations, startOfWeek)
                else -> deriveHourlyStats(pViolations, startOfDay)
            }

            val tDistribution = ViolationType.entries.associateWith { type -> pViolations.count { it.types.contains(type) } }

            val eData = ExportData(
                dH = dailyV.count { it.types.contains(ViolationType.NO_HELMET) }, dOl = dailyV.count { it.types.contains(ViolationType.OVERLOADING) }, dOs = dailyV.count { it.types.contains(ViolationType.OVERSPEEDING) }, dIp = dailyV.count { it.types.contains(ViolationType.ILLEGAL_PARKING) }, dM = dailyV.count { it.types.contains(ViolationType.MANUAL) },
                wH = weeklyV.count { it.types.contains(ViolationType.NO_HELMET) }, wOl = weeklyV.count { it.types.contains(ViolationType.OVERLOADING) }, wOs = weeklyV.count { it.types.contains(ViolationType.OVERSPEEDING) }, wIp = weeklyV.count { it.types.contains(ViolationType.ILLEGAL_PARKING) }, wM = weeklyV.count { it.types.contains(ViolationType.MANUAL) },
                mH = monthlyV.count { it.types.contains(ViolationType.NO_HELMET) }, mOl = monthlyV.count { it.types.contains(ViolationType.OVERLOADING) }, mOs = monthlyV.count { it.types.contains(ViolationType.OVERSPEEDING) }, mIp = monthlyV.count { it.types.contains(ViolationType.ILLEGAL_PARKING) }, mM = monthlyV.count { it.types.contains(ViolationType.MANUAL) },
                aH = annualV.count { it.types.contains(ViolationType.NO_HELMET) }, aOl = annualV.count { it.types.contains(ViolationType.OVERLOADING) }, aOs = annualV.count { it.types.contains(ViolationType.OVERSPEEDING) }, aIp = annualV.count { it.types.contains(ViolationType.ILLEGAL_PARKING) }, aM = annualV.count { it.types.contains(ViolationType.MANUAL) }
            )

            // Safely post back to the UI Thread
            withContext(Dispatchers.Main) {
                periodViolations = pViolations
                chartStats = cStats
                typeDistribution = tDistribution
                exportData = eData
                isCalculating = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Analytics", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black), color = colorScheme.onBackground)
                Text("System-wide violation trends", style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
            }

            if (role == "Administrator") {
                Button(
                    onClick = { showExportYearDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primaryContainer, contentColor = colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    modifier = Modifier.height(40.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = "Download Report", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Export", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        if (role == "Administrator" && availableYears.size > 1) {
            YearFilterSelector(
                selectedYear = selectedYear,
                availableYears = availableYears,
                onYearSelected = { selectedYear = it }
            )
            Spacer(Modifier.height(20.dp))
        }

        if (availableTimeframes.size <= 2) {
            TabRow(
                selectedTabIndex = availableTimeframes.indexOf(selectedTimeframe).coerceAtLeast(0),
                containerColor = Color.Transparent,
                contentColor = colorScheme.primary,
                divider = { HorizontalDivider(color = colorScheme.outline.copy(alpha = 0.1f)) }
            ) {
                availableTimeframes.forEach { option ->
                    Tab(
                        selected = selectedTimeframe == option,
                        onClick = { selectedTimeframe = option },
                        text = { Text(option, fontWeight = FontWeight.Bold) }
                    )
                }
            }
        } else {
            ScrollableTabRow(
                selectedTabIndex = availableTimeframes.indexOf(selectedTimeframe).coerceAtLeast(0),
                containerColor = Color.Transparent,
                contentColor = colorScheme.primary,
                edgePadding = 0.dp,
                divider = { HorizontalDivider(color = colorScheme.outline.copy(alpha = 0.1f)) }
            ) {
                availableTimeframes.forEach { option ->
                    Tab(
                        selected = selectedTimeframe == option,
                        onClick = { selectedTimeframe = option },
                        text = { Text(option, fontWeight = FontWeight.Bold) }
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        if (isCalculating) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colorScheme.primary)
            }
        } else {
            PeakInsightsCard(timeframe = selectedTimeframe, violations = periodViolations)

            Spacer(Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ViolationType.entries.forEach { type ->
                    val isSelected = highlightedType == type
                    val color = getViolationColor(type)
                    Surface(
                        modifier = Modifier.clip(RoundedCornerShape(50)).clickable { highlightedType = if (isSelected) null else type },
                        color = if (isSelected) color.copy(alpha = 0.15f) else Color.Transparent,
                        border = BorderStroke(1.dp, if (isSelected) color else colorScheme.outline.copy(alpha = 0.2f))
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(if (isSelected) color else color.copy(alpha = 0.4f)))
                            Spacer(Modifier.width(8.dp))
                            Text(type.label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = if (isSelected) color else colorScheme.onSurfaceVariant))
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = colorScheme.surfaceVariant, border = BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.1f)), tonalElevation = 2.dp) {
                Column(modifier = Modifier.padding(24.dp)) {

                    val incidentLabel = when (selectedTimeframe) {
                        "Annual" -> "ANNUAL INCIDENTS · $selectedYear"
                        "Monthly" -> "INCIDENTS THIS MONTH - $selectedYear"
                        "Weekly" -> "INCIDENTS THIS WEEK - $selectedYear"
                        else -> "INCIDENTS TODAY - $selectedYear"
                    }

                    val chartLabel = when (selectedTimeframe) {
                        "Annual" -> "ANNUAL VIEW · $selectedYear"
                        "Monthly" -> "MONTHLY VIEW - $selectedYear"
                        "Weekly" -> "WEEKLY VIEW - $selectedYear"
                        else -> "HOURLY VIEW - $selectedYear"
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                        Column {
                            RollingNumberText(value = "${periodViolations.size}", style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, fontSize = 44.sp), color = colorScheme.onSurface)
                            Text(incidentLabel, style = MaterialTheme.typography.labelSmall.copy(color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontWeight = FontWeight.Bold, letterSpacing = 2.sp))
                        }
                        Icon(Icons.Default.Info, null, tint = colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
                    }

                    Spacer(Modifier.height(32.dp))

                    Text(chartLabel, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)), letterSpacing = 1.sp)
                    Text(highlightedType?.label ?: "All Violations", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = colorScheme.onSurface)

                    Spacer(Modifier.height(24.dp))

                    val maxValInt = (chartStats.maxOfOrNull { it.total }?.times(1.5)?.toInt() ?: 5).coerceAtLeast(5)
                    Row(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                        Column(modifier = Modifier.fillMaxHeight().padding(end = 12.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
                            Text(maxValInt.toString(), fontSize = 10.sp, color = colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                            Text((maxValInt / 2).toString(), fontSize = 10.sp, color = colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                            Text("0", fontSize = 10.sp, color = colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                        }
                        SmoothLineChart(
                            stats = chartStats,
                            highlightedType = highlightedType,
                            maxVal = maxValInt.toFloat(),
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }

                    Spacer(Modifier.height(32.dp))
                    HorizontalDivider(color = colorScheme.outline.copy(alpha = 0.1f))
                    Spacer(Modifier.height(32.dp))

                    Text(
                        "TYPE DISTRIBUTION",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 1.5.sp, color = colorScheme.onSurface)
                    )
                    Spacer(Modifier.height(20.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        ViolationType.entries.forEach { type ->
                            val count = typeDistribution[type] ?: 0
                            val percentage = if (periodViolations.isNotEmpty()) count.toFloat() / periodViolations.size else 0f
                            DistributionRow(label = type.label, count = count, percentage = percentage, color = getViolationColor(type))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(40.dp))
    }

    // ── STEP 1: Which year(s) should the report cover? ──
    if (showExportYearDialog) {
        ExportYearDialog(
            availableYears = availableYears,
            onDismiss = { showExportYearDialog = false },
            onConfirm = { yearChoice ->
                exportYearChoice = yearChoice
                showExportYearDialog = false
                showDownloadDialog = true
            }
        )
    }

    // ── STEP 2: PDF or CSV? ──
    if (showDownloadDialog && exportData != null) {
        val e = remember(exportYearChoice, allViolations.size) {
            buildExportDataForYears(allViolations.toList(), exportYearChoice, availableYears)
        }
        val yearlyBreakdowns = remember(exportYearChoice, allViolations.size) {
            val years = exportYearChoice?.let { listOf(it) } ?: availableYears
            computeAllYearlyBreakdowns(allViolations.toList(), years)
        }

        DownloadFormatDialog(
            reportScopeLabel = exportYearChoice?.let { "Year $it" } ?: "All Years",
            onDismiss = { showDownloadDialog = false },
            onBack = {
                showDownloadDialog = false
                showExportYearDialog = true
            },
            onDownloadPdf = {
                showDownloadDialog = false
                try {
                    StatisticsPdfGenerator.generatePdf(
                        context = context,
                        dailyHelmet = e.dH, dailyOverloading = e.dOl, dailyOverspeeding = e.dOs, dailyIllegalParking = e.dIp, dailyManual = e.dM,
                        weeklyHelmet = e.wH, weeklyOverloading = e.wOl, weeklyOverspeeding = e.wOs, weeklyIllegalParking = e.wIp, weeklyManual = e.wM,
                        monthlyHelmet = e.mH, monthlyOverloading = e.mOl, monthlyOverspeeding = e.mOs, monthlyIllegalParking = e.mIp, monthlyManual = e.mM,
                        yearlyBreakdowns = yearlyBreakdowns
                    )
                } catch (ex: Exception) { Toast.makeText(context, "Error generating PDF: ${ex.message}", Toast.LENGTH_SHORT).show() }
            },
            onDownloadCsv = {
                showDownloadDialog = false
                try {
                    StatisticsCsvGenerator.generateCsv(
                        context = context,
                        dailyHelmet = e.dH, dailyOverloading = e.dOl, dailyOverspeeding = e.dOs, dailyIllegalParking = e.dIp, dailyManual = e.dM,
                        weeklyHelmet = e.wH, weeklyOverloading = e.wOl, weeklyOverspeeding = e.wOs, weeklyIllegalParking = e.wIp, weeklyManual = e.wM,
                        monthlyHelmet = e.mH, monthlyOverloading = e.mOl, monthlyOverspeeding = e.mOs, monthlyIllegalParking = e.mIp, monthlyManual = e.mM,
                        yearlyBreakdowns = yearlyBreakdowns
                    )
                } catch (ex: Exception) { Toast.makeText(context, "Error generating CSV: ${ex.message}", Toast.LENGTH_SHORT).show() }
            }
        )
    }
}

@Composable
fun YearFilterSelector(
    selectedYear: Int,
    availableYears: List<Int>,
    onYearSelected: (Int) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = colorScheme.secondaryContainer.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, colorScheme.secondary.copy(alpha = 0.22f))
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = colorScheme.secondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("YEAR", style = MaterialTheme.typography.labelSmall, color = colorScheme.secondary, fontWeight = FontWeight.Black)
            }

            availableYears.forEach { year ->
                val isSelected = selectedYear == year
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { onYearSelected(year) },
                    color = if (isSelected) colorScheme.secondary else colorScheme.surface,
                    border = BorderStroke(1.dp, if (isSelected) colorScheme.secondary else colorScheme.outline.copy(alpha = 0.16f))
                ) {
                    Text(
                        text = year.toString(),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (isSelected) colorScheme.onSecondary else colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun buildExportDataForYears(
    violations: List<ViolationEntry>,
    exportYearChoice: Int?,
    availableYears: List<Int>
): ExportData {
    val years = exportYearChoice?.let { listOf(it) } ?: availableYears
    val dailyV = years.flatMap { getViolationsForPeriod(violations, "Daily", it) }
    val weeklyV = years.flatMap { getViolationsForPeriod(violations, "Weekly", it) }
    val monthlyV = years.flatMap { getViolationsForPeriod(violations, "Monthly", it) }
    val annualV = years.flatMap { getViolationsForPeriod(violations, "Annual", it) }

    return ExportData(
        dH = dailyV.count { it.types.contains(ViolationType.NO_HELMET) }, dOl = dailyV.count { it.types.contains(ViolationType.OVERLOADING) }, dOs = dailyV.count { it.types.contains(ViolationType.OVERSPEEDING) }, dIp = dailyV.count { it.types.contains(ViolationType.ILLEGAL_PARKING) }, dM = dailyV.count { it.types.contains(ViolationType.MANUAL) },
        wH = weeklyV.count { it.types.contains(ViolationType.NO_HELMET) }, wOl = weeklyV.count { it.types.contains(ViolationType.OVERLOADING) }, wOs = weeklyV.count { it.types.contains(ViolationType.OVERSPEEDING) }, wIp = weeklyV.count { it.types.contains(ViolationType.ILLEGAL_PARKING) }, wM = weeklyV.count { it.types.contains(ViolationType.MANUAL) },
        mH = monthlyV.count { it.types.contains(ViolationType.NO_HELMET) }, mOl = monthlyV.count { it.types.contains(ViolationType.OVERLOADING) }, mOs = monthlyV.count { it.types.contains(ViolationType.OVERSPEEDING) }, mIp = monthlyV.count { it.types.contains(ViolationType.ILLEGAL_PARKING) }, mM = monthlyV.count { it.types.contains(ViolationType.MANUAL) },
        aH = annualV.count { it.types.contains(ViolationType.NO_HELMET) }, aOl = annualV.count { it.types.contains(ViolationType.OVERLOADING) }, aOs = annualV.count { it.types.contains(ViolationType.OVERSPEEDING) }, aIp = annualV.count { it.types.contains(ViolationType.ILLEGAL_PARKING) }, aM = annualV.count { it.types.contains(ViolationType.MANUAL) }
    )
}

private fun deriveHourlyStats(violations: List<ViolationEntry>, startOfDay: Long): List<ChartStat> {
    val stats = mutableListOf<ChartStat>()
    val tooltipFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    for (hour in 0..23) {
        val cal = Calendar.getInstance().apply { timeInMillis = startOfDay; set(Calendar.HOUR_OF_DAY, hour) }
        val hourStart = cal.timeInMillis
        val hourEnd = hourStart + 3599999L
        val hourViolations = violations.filter { it.timestamp in hourStart..hourEnd }
        stats.add(ChartStat(tooltipLabel = tooltipFormat.format(cal.time), helmet = hourViolations.count { it.types.contains(ViolationType.NO_HELMET) }, overloading = hourViolations.count { it.types.contains(ViolationType.OVERLOADING) }, overspeeding = hourViolations.count { it.types.contains(ViolationType.OVERSPEEDING) }, illegalParking = hourViolations.count { it.types.contains(ViolationType.ILLEGAL_PARKING) }, manual = hourViolations.count { it.types.contains(ViolationType.MANUAL) }))
    }
    return stats
}

private fun deriveWeeklyStats(violations: List<ViolationEntry>, startOfWeek: Long): List<ChartStat> {
    val stats = mutableListOf<ChartStat>()
    val tooltipFormat = SimpleDateFormat("EEEE", Locale.getDefault())

    for (offset in 0..6) {
        val cal = Calendar.getInstance().apply { timeInMillis = startOfWeek; add(Calendar.DAY_OF_YEAR, offset) }
        val dayStart = cal.timeInMillis
        val dayEnd = dayStart + 86399999L
        val dayViolations = violations.filter { it.timestamp in dayStart..dayEnd }
        stats.add(ChartStat(tooltipLabel = tooltipFormat.format(cal.time), helmet = dayViolations.count { it.types.contains(ViolationType.NO_HELMET) }, overloading = dayViolations.count { it.types.contains(ViolationType.OVERLOADING) }, overspeeding = dayViolations.count { it.types.contains(ViolationType.OVERSPEEDING) }, illegalParking = dayViolations.count { it.types.contains(ViolationType.ILLEGAL_PARKING) }, manual = dayViolations.count { it.types.contains(ViolationType.MANUAL) }))
    }
    return stats
}

private fun deriveMonthlyStats(violations: List<ViolationEntry>, startOfMonth: Long): List<ChartStat> {
    val stats = mutableListOf<ChartStat>()
    val tooltipFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
    val cal = Calendar.getInstance().apply { timeInMillis = startOfMonth }
    val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

    for (day in 1..maxDays) {
        val dayStartCal = Calendar.getInstance().apply { timeInMillis = startOfMonth; set(Calendar.DAY_OF_MONTH, day) }
        val dayStart = dayStartCal.timeInMillis
        val dayEnd = dayStart + 86399999L
        val dayViolations = violations.filter { it.timestamp in dayStart..dayEnd }
        stats.add(ChartStat(tooltipLabel = tooltipFormat.format(dayStartCal.time), helmet = dayViolations.count { it.types.contains(ViolationType.NO_HELMET) }, overloading = dayViolations.count { it.types.contains(ViolationType.OVERLOADING) }, overspeeding = dayViolations.count { it.types.contains(ViolationType.OVERSPEEDING) }, illegalParking = dayViolations.count { it.types.contains(ViolationType.ILLEGAL_PARKING) }, manual = dayViolations.count { it.types.contains(ViolationType.MANUAL) }))
    }
    return stats
}

private fun deriveAnnualStats(violations: List<ViolationEntry>, startOfYear: Long): List<ChartStat> {
    val stats = mutableListOf<ChartStat>()
    val tooltipFormat = SimpleDateFormat("MMM", Locale.getDefault())

    for (month in 0..11) {
        val monthStartCal = Calendar.getInstance().apply { timeInMillis = startOfYear; set(Calendar.MONTH, month) }
        val monthStart = monthStartCal.timeInMillis
        val monthEndCal = Calendar.getInstance().apply { timeInMillis = monthStart; set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH)); set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999) }
        val monthEnd = monthEndCal.timeInMillis
        val monthViolations = violations.filter { it.timestamp in monthStart..monthEnd }
        stats.add(ChartStat(tooltipLabel = tooltipFormat.format(monthStartCal.time), helmet = monthViolations.count { it.types.contains(ViolationType.NO_HELMET) }, overloading = monthViolations.count { it.types.contains(ViolationType.OVERLOADING) }, overspeeding = monthViolations.count { it.types.contains(ViolationType.OVERSPEEDING) }, illegalParking = monthViolations.count { it.types.contains(ViolationType.ILLEGAL_PARKING) }, manual = monthViolations.count { it.types.contains(ViolationType.MANUAL) }))
    }
    return stats
}

fun getPeakHour(violations: List<ViolationEntry>): String {
    if (violations.isEmpty()) return "N/A"
    val counts = violations.groupBy { Calendar.getInstance().apply { timeInMillis = it.timestamp }.get(Calendar.HOUR_OF_DAY) }.mapValues { it.value.size }
    val peakHour = counts.maxByOrNull { it.value }?.key ?: return "N/A"
    return SimpleDateFormat("h:00 a", Locale.getDefault()).format(Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, peakHour) }.time)
}

fun getPeakDay(violations: List<ViolationEntry>): String {
    if (violations.isEmpty()) return "N/A"
    val counts = violations.groupBy { Calendar.getInstance().apply { timeInMillis = it.timestamp }.get(Calendar.DAY_OF_WEEK) }.mapValues { it.value.size }
    val peakDay = counts.maxByOrNull { it.value }?.key ?: return "N/A"
    return SimpleDateFormat("EEEE", Locale.getDefault()).format(Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, peakDay) }.time)
}

fun getPeakWeek(violations: List<ViolationEntry>): String {
    if (violations.isEmpty()) return "N/A"
    val counts = violations.groupBy { Calendar.getInstance().apply { timeInMillis = it.timestamp }.get(Calendar.WEEK_OF_MONTH) }.mapValues { it.value.size }
    val peakWeek = counts.maxByOrNull { it.value }?.key ?: return "N/A"
    return "Week $peakWeek"
}

fun getPeakMonth(violations: List<ViolationEntry>): String {
    if (violations.isEmpty()) return "N/A"
    val counts = violations.groupBy { Calendar.getInstance().apply { timeInMillis = it.timestamp }.get(Calendar.MONTH) }.mapValues { it.value.size }
    val peakMonth = counts.maxByOrNull { it.value }?.key ?: return "N/A"
    return SimpleDateFormat("MMMM", Locale.getDefault()).format(Calendar.getInstance().apply { set(Calendar.MONTH, peakMonth) }.time)
}

@Composable
fun PeakInsightsCard(timeframe: String, violations: List<ViolationEntry>) {
    val colorScheme = MaterialTheme.colorScheme
    val p1Label = when (timeframe) { "Annual" -> "PEAK MONTH"; "Monthly" -> "PEAK WEEK"; "Weekly" -> "PEAK DAY"; else -> "PEAK HOUR" }
    val p1Value = when (timeframe) { "Annual" -> getPeakMonth(violations); "Monthly" -> getPeakWeek(violations); "Weekly" -> getPeakDay(violations); else -> getPeakHour(violations) }

    val p2Label = when (timeframe) { "Annual" -> "PEAK DAY"; "Monthly" -> "PEAK DAY"; "Weekly" -> "PEAK HOUR"; else -> "" }
    val p2Value = when (timeframe) { "Annual" -> getPeakDay(violations); "Monthly" -> getPeakDay(violations); "Weekly" -> getPeakHour(violations); else -> "" }

    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = colorScheme.primaryContainer.copy(alpha = 0.5f), border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.3f))) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
            Icon(Icons.Default.Insights, null, tint = colorScheme.primary, modifier = Modifier.size(28.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(p1Label, style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
                Text(p1Value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = colorScheme.primary)
            }

            if (timeframe != "Daily") {
                Box(modifier = Modifier.height(30.dp).width(1.dp).background(colorScheme.primary.copy(alpha = 0.2f)))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(p2Label, style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
                    Text(p2Value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun SmoothLineChart(
    stats: List<ChartStat>,
    highlightedType: ViolationType?,
    maxVal: Float,
    modifier: Modifier = Modifier
) {
    if (stats.size < 2) return
    var selectedPointIndex by remember { mutableStateOf<Int?>(null) }
    var selectedTouchX by remember { mutableStateOf<Float?>(null) }
    val colorScheme = MaterialTheme.colorScheme
    val revealKey = remember(stats, highlightedType, maxVal) {
        "${stats.hashCode()}-${highlightedType?.name.orEmpty()}-$maxVal"
    }
    var chartFullyVisible by remember(revealKey) { mutableStateOf(false) }
    var revealChart by remember(stats, highlightedType, maxVal) { mutableStateOf(false) }
    LaunchedEffect(stats, highlightedType, maxVal, chartFullyVisible) {
        if (!chartFullyVisible) {
            revealChart = false
            return@LaunchedEffect
        }
        revealChart = false
        withFrameNanos { }
        revealChart = true
    }
    val chartRevealProgress by animateFloatAsState(
        targetValue = if (chartFullyVisible && revealChart) 1f else 0f,
        animationSpec = tween(durationMillis = 850),
        label = "lineChartReveal"
    )
    val animatedTouchX by animateFloatAsState(
        targetValue = selectedTouchX ?: 0f,
        animationSpec = tween(durationMillis = 90),
        label = "lineChartTouchSlide"
    )

    Box(modifier = modifier.onFullyVisibleOnce(revealKey) { chartFullyVisible = true }) {
        Canvas(
            modifier = Modifier.fillMaxSize().pointerInput(stats) {
                detectTapGestures(onPress = { offset ->
                    val spacing = size.width / (stats.size - 1)
                    selectedTouchX = offset.x.coerceIn(0f, size.width.toFloat())
                    selectedPointIndex = (selectedTouchX!! / spacing).roundToInt().coerceIn(0, stats.size - 1)
                    tryAwaitRelease()
                    selectedPointIndex = null
                    selectedTouchX = null
                })
            }.pointerInput(stats) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val x = offset.x.coerceIn(0f, size.width.toFloat())
                        selectedTouchX = x
                        selectedPointIndex = (x / (size.width / (stats.size - 1))).roundToInt().coerceIn(0, stats.size - 1)
                    },
                    onDragEnd = { selectedPointIndex = null; selectedTouchX = null },
                    onDragCancel = { selectedPointIndex = null; selectedTouchX = null },
                    onDrag = { change, _ ->
                        val x = change.position.x.coerceIn(0f, size.width.toFloat())
                        selectedTouchX = x
                        selectedPointIndex = (x / (size.width / (stats.size - 1))).roundToInt().coerceIn(0, stats.size - 1)
                    }
                )
            }
        ) {
            val width = size.width
            val height = size.height
            val spacing = width / (stats.size - 1)
            val revealRight = width * chartRevealProgress.coerceIn(0f, 1f)

            for (i in 0..4) {
                val y = height - (height / 4) * i
                drawLine(colorScheme.onSurface.copy(alpha = 0.05f), Offset(0f, y), Offset(width, y), strokeWidth = 1.dp.toPx())
            }

            ViolationType.entries.forEach { type ->
                val isSelected = highlightedType == type
                val isLineActive = highlightedType == null || isSelected

                val color = if (isLineActive) getViolationColor(type) else colorScheme.onSurface.copy(alpha = 0.05f)
                val strokeWidth = if (isSelected) 4.dp.toPx() else 2.dp.toPx()
                val lineAlpha = if (isLineActive) 1f else 0.2f

                val points = stats.mapIndexed { index, dayStat ->
                    val value = dayStat.getValue(type)
                    Offset(index * spacing, height - (value.toFloat() / maxVal) * height)
                }

                val path = Path()
                path.moveTo(points[0].x, points[0].y)
                for (i in 0 until points.size - 1) {
                    val p0 = points[i]
                    val p1 = points[i + 1]
                    path.cubicTo(p0.x + spacing / 2, p0.y, p1.x - spacing / 2, p1.y, p1.x, p1.y)
                }

                clipRect(right = revealRight) {
                    if (isLineActive && (highlightedType == type || highlightedType == null)) {
                        val fillPath = Path().apply { addPath(path); lineTo(points.last().x, height); lineTo(points.first().x, height); close() }
                        drawPath(path = fillPath, brush = Brush.verticalGradient(colors = listOf(color.copy(alpha = if (isSelected) 0.15f else 0.05f), Color.Transparent)))
                    }

                    drawPath(path = path, color = color.copy(alpha = lineAlpha), style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))

                    if (isLineActive) {
                        selectedPointIndex?.let { activeIdx ->
                            if (activeIdx == points.indexOf(points[activeIdx.coerceIn(points.indices)])) {
                                val point = points[activeIdx]
                                drawCircle(color = color, radius = 6.dp.toPx(), center = point)
                                drawCircle(color = colorScheme.surfaceVariant, radius = 2.5.dp.toPx(), center = point)
                            }
                        }
                    }
                }
            }

            selectedPointIndex?.let { index ->
                val x = animatedTouchX.coerceIn(0f, width)
                drawLine(color = colorScheme.onSurface.copy(alpha = 0.2f), start = Offset(x, 0f), end = Offset(x, height), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
            }
        }

        selectedPointIndex?.let { index ->
            val dayStat = stats[index]
            Surface(modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp), shape = RoundedCornerShape(12.dp), color = colorScheme.surface, border = BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.1f)), shadowElevation = 8.dp) {
                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(dayStat.tooltipLabel, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black), color = colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))

                    if (highlightedType != null) {
                        Text("${highlightedType.label}: ${dayStat.getValue(highlightedType)}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold), color = getViolationColor(highlightedType))
                    } else {
                        ViolationType.entries.forEach { type ->
                            val valType = dayStat.getValue(type)
                            if (valType > 0) Text("${type.label}: $valType", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = getViolationColor(type))
                        }
                        if (dayStat.total == 0) Text("No Violations", color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun DistributionRow(label: String, count: Int, percentage: Float, color: Color) {
    val colorScheme = MaterialTheme.colorScheme
    var isFullyVisible by remember(label, count, percentage) { mutableStateOf(false) }
    val animatedPercentage by animateFloatAsState(
        targetValue = if (isFullyVisible) percentage.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(durationMillis = 650),
        label = "distributionProgress"
    )
    Column(modifier = Modifier.onFullyVisibleOnce("$label-$count-$percentage") { isFullyVisible = true }) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
            RollingNumberText(value = "$count", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = colorScheme.onSurface)
        }
        Spacer(Modifier.height(8.dp))
        Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(colorScheme.onSurface.copy(alpha = 0.05f))) {
            Box(modifier = Modifier.fillMaxWidth(animatedPercentage).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(color))
        }
    }
}

@Composable
private fun Modifier.onFullyVisibleOnce(key: Any?, onVisible: () -> Unit): Modifier {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    var hasTriggered by remember(key) { mutableStateOf(false) }

    return onGloballyPositioned { coordinates ->
        if (hasTriggered) return@onGloballyPositioned
        val bounds = coordinates.boundsInWindow()
        if (bounds.top >= 0f && bounds.bottom <= screenHeightPx) {
            hasTriggered = true
            onVisible()
        }
    }
}

@Composable
fun ExportYearDialog(
    availableYears: List<Int>,
    onDismiss: () -> Unit,
    onConfirm: (Int?) -> Unit // null = All Years
) {
    val colorScheme = MaterialTheme.colorScheme

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colorScheme.surface,
        title = { Text("Select Report Year", fontWeight = FontWeight.Bold, color = colorScheme.onSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Choose which year's data to include. \"All Years\" combines every year into a single file with a year-by-year comparison table — recommended if you want the full history at a glance.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {

                // ── ALL YEARS OPTION ──
                Surface(
                    onClick = { onConfirm(null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = colorScheme.primaryContainer.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Insights, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("All Years", fontWeight = FontWeight.Bold, color = colorScheme.onSurface)
                            Text("Combined report + year-by-year comparison", fontSize = 11.sp, color = colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // ── INDIVIDUAL YEAR OPTIONS ──
                    }
                items(availableYears) { year ->
                    Surface(
                        onClick = { onConfirm(year) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        border = BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.1f))
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(year.toString(), fontWeight = FontWeight.Bold, color = colorScheme.onSurface)
                        }
                    }
                }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold) }
        }
    )
}

@Composable
fun DownloadFormatDialog(
    reportScopeLabel: String,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    onDownloadPdf: () -> Unit,
    onDownloadCsv: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colorScheme.surface,
        title = { Text("Download Analytics Report", fontWeight = FontWeight.Bold, color = colorScheme.onSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Select the format for your data export. The file will be saved directly to your Downloads folder.", style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurfaceVariant)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = colorScheme.primaryContainer.copy(alpha = 0.28f),
                    border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.18f))
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Selected report scope", fontSize = 11.sp, color = colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                            Text(reportScopeLabel, fontWeight = FontWeight.Black, color = colorScheme.onSurface)
                        }
                    }
                }

                // ── PDF OPTION ──
                Surface(
                    onClick = onDownloadPdf,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    border = BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.1f))
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFFFEBEE)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF", tint = Color(0xFFE53935), modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("PDF Document", fontWeight = FontWeight.Bold, color = colorScheme.onSurface)
                            Text("Best for printing and sharing", fontSize = 12.sp, color = colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // ── CSV OPTION ──
                Surface(
                    onClick = onDownloadCsv,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    border = BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.1f))
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFE3F2FD)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.TableChart, contentDescription = "CSV", tint = Color(0xFF1E88E5), modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("CSV Spreadsheet", fontWeight = FontWeight.Bold, color = colorScheme.onSurface)
                            Text("Best for Excel and data analysis", fontSize = 12.sp, color = colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onBack) { Text("Back", color = colorScheme.primary, fontWeight = FontWeight.Bold) }
        }
    )
}
