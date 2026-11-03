package com.motorsiklo.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motorsiklo.app.FirebaseManager
import com.motorsiklo.app.ui.theme.*
import java.util.Calendar

@Composable
fun AdminDashboardScreen(
    username: String,
    onLogout: () -> Unit,
    onNavigateToRequests: () -> Unit
) {
    val scroll = rememberScrollState()
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current

    val health = currentSystemHealth.value
    val idleCount = FirebaseManager.currentIdleDevices.value
    val violationsList = ViolationRepository.violations

    // ── BULLETPROOF NAME FORMATTING (Surname, Initials) ──
    val userProfile = currentUserProfile.value
    val rawLastName = userProfile?.lastName?.trim() ?: ""
    val rawFirstName = userProfile?.firstName?.trim() ?: ""
    val rawMiddleName = userProfile?.middleName?.trim() ?: ""

    // Using .take(1) prevents Char? compiler errors
    val fInitial = if (rawFirstName.isNotBlank()) "${rawFirstName.take(1).uppercase()}." else ""
    val mInitial = if (rawMiddleName.isNotBlank()) "${rawMiddleName.take(1).uppercase()}." else ""

    val initials = listOf(fInitial, mInitial).filter { it.isNotBlank() }.joinToString(" ")

    val displayName = if (rawLastName.isNotBlank()) {
        if (initials.isNotBlank()) "$rawLastName, $initials" else rawLastName
    } else {
        username
    }

    var selectedTimeframe by remember { mutableStateOf("Daily") }
    var isUnapprehendedExpanded by remember { mutableStateOf(false) }

    // ── YEAR-OVER-YEAR COMPARISON STATE ──
    val availableYears = remember(violationsList.size) { getAvailableYears(violationsList) }
    var compareYearA by remember { mutableStateOf(availableYears.first()) }
    var compareYearB by remember { mutableStateOf(availableYears.getOrElse(1) { availableYears.first() }) }

    // ── Menu State ──
    var showMenu by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var viewedViolation by remember { mutableStateOf<ViolationEntry?>(null) }

    // ── OPTIMIZATION: Deep State Tracking (derivedStateOf) ──
    // Tracks status changes instantly without locking strictly to list size.
    // Heavy Calendar logic is only executed when the timeframe actually changes or database updates.
    val periodViolations by remember(selectedTimeframe) {
        derivedStateOf {
            val calendar = Calendar.getInstance()
            val start: Long
            val end: Long

            when (selectedTimeframe) {
                "Weekly" -> {
                    calendar.firstDayOfWeek = Calendar.MONDAY
                    calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    calendar.set(Calendar.HOUR_OF_DAY, 0)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    start = calendar.timeInMillis

                    calendar.add(Calendar.DAY_OF_WEEK, 6)
                    calendar.set(Calendar.HOUR_OF_DAY, 23)
                    calendar.set(Calendar.MINUTE, 59)
                    calendar.set(Calendar.SECOND, 59)
                    calendar.set(Calendar.MILLISECOND, 999)
                    end = calendar.timeInMillis
                }
                "Monthly" -> {
                    calendar.set(Calendar.DAY_OF_MONTH, 1)
                    calendar.set(Calendar.HOUR_OF_DAY, 0)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    start = calendar.timeInMillis

                    calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
                    calendar.set(Calendar.HOUR_OF_DAY, 23)
                    calendar.set(Calendar.MINUTE, 59)
                    calendar.set(Calendar.SECOND, 59)
                    calendar.set(Calendar.MILLISECOND, 999)
                    end = calendar.timeInMillis
                }
                "Annual" -> {
                    calendar.set(Calendar.MONTH, Calendar.JANUARY)
                    calendar.set(Calendar.DAY_OF_MONTH, 1)
                    calendar.set(Calendar.HOUR_OF_DAY, 0)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    start = calendar.timeInMillis

                    calendar.set(Calendar.MONTH, Calendar.DECEMBER)
                    calendar.set(Calendar.DAY_OF_MONTH, 31)
                    calendar.set(Calendar.HOUR_OF_DAY, 23)
                    calendar.set(Calendar.MINUTE, 59)
                    calendar.set(Calendar.SECOND, 59)
                    calendar.set(Calendar.MILLISECOND, 999)
                    end = calendar.timeInMillis
                }
                else -> { // Daily
                    calendar.set(Calendar.HOUR_OF_DAY, 0)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    start = calendar.timeInMillis

                    calendar.set(Calendar.HOUR_OF_DAY, 23)
                    calendar.set(Calendar.MINUTE, 59)
                    calendar.set(Calendar.SECOND, 59)
                    calendar.set(Calendar.MILLISECOND, 999)
                    end = calendar.timeInMillis
                }
            }
            violationsList.filter { it.timestamp in start..end }
        }
    }

    // ── OPTIMIZATION: Smart Counter Caching ──
    val helmetCount by remember { derivedStateOf { periodViolations.count { it.types.contains(ViolationType.NO_HELMET) } } }
    val overloadingCount by remember { derivedStateOf { periodViolations.count { it.types.contains(ViolationType.OVERLOADING) } } }
    val overspeedCount by remember { derivedStateOf { periodViolations.count { it.types.contains(ViolationType.OVERSPEEDING) } } }
    val parkingCount by remember { derivedStateOf { periodViolations.count { it.types.contains(ViolationType.ILLEGAL_PARKING) } } }
    val manualCount by remember { derivedStateOf { periodViolations.count { it.types.contains(ViolationType.MANUAL) } } }

    val pendingViolations by remember {
        derivedStateOf { violationsList.filter { it.status == "Pending" || it.status == "Awaiting_Approval" } }
    }
    val unapprehendedCount by remember { derivedStateOf { pendingViolations.size } }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = colorScheme.surface,
            title = {
                Text("Confirm Logout", fontWeight = FontWeight.Bold, color = colorScheme.onSurface)
            },
            text = {
                Text("Are you sure you want to log out of the Administrator dashboard?", color = colorScheme.onSurfaceVariant)
            },
            confirmButton = {
                Button(
                    onClick = { showLogoutDialog = false; onLogout() },
                    colors = ButtonDefaults.buttonColors(containerColor = colorScheme.error)
                ) {
                    Text("Logout", color = colorScheme.onError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel", color = colorScheme.primary)
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "System Overview",
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                        color = colorScheme.onSurface
                    )
                    Text(
                        text = "ADMINISTRATOR",
                        style = MaterialTheme.typography.labelSmall.copy(color = colorScheme.primary, fontWeight = FontWeight.Bold)
                    )
                }

                // ── Animated Sliding Menu ──
                Box(contentAlignment = Alignment.CenterEnd) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AnimatedVisibility(
                            visible = showMenu,
                            enter = slideInHorizontally(initialOffsetX = { it / 2 }) + fadeIn(animationSpec = tween(300)),
                            exit = slideOutHorizontally(targetOffsetX = { it / 2 }) + fadeOut(animationSpec = tween(300))
                        ) {
                            Row(
                                modifier = Modifier.padding(end = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clickable {
                                            ThemeManager.toggleTheme(context)
                                            showMenu = false
                                        }
                                ) {
                                    Icon(
                                        imageVector = if (ThemeManager.isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                        contentDescription = "Theme",
                                        tint = colorScheme.primary,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }

                                Surface(
                                    shape = CircleShape,
                                    color = colorScheme.errorContainer,
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clickable {
                                            showMenu = false
                                            showLogoutDialog = true
                                        }
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Logout,
                                        contentDescription = "Logout",
                                        tint = colorScheme.error,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = if (showMenu) colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable { showMenu = !showMenu }
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options",
                                modifier = Modifier.padding(12.dp),
                                tint = if (showMenu) colorScheme.primary else colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
            SectionHeaderLabel("ADMINISTRATIVE ACTIONS")
            Spacer(Modifier.height(12.dp))
            AdminRequestsPanel(pendingCount = totalPendingRequests, onClick = onNavigateToRequests)
            Spacer(Modifier.height(12.dp))
            DetectionSettingsPanel()

            Spacer(Modifier.height(32.dp))
            SectionHeaderLabel("SYSTEM HEALTH & TELEMETRY")
            Spacer(Modifier.height(12.dp))
            SystemHealthPanel(health, idleCount)

            Spacer(Modifier.height(28.dp))
            SectionHeaderLabel("UNAPPREHENDED VIOLATIONS")
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Detected by AI — not yet ticketed by an enforcer",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            if (unapprehendedCount == 0) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = colorScheme.surface,
                    border = BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.1f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("No unapprehended detections", color = colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                }
            } else {
                val displayList = if (isUnapprehendedExpanded) pendingViolations else pendingViolations.take(5)
                displayList.forEach { entry ->
                    ViolationCard(entry = entry, onClick = { viewedViolation = entry })
                    Spacer(Modifier.height(10.dp))
                }
                if (unapprehendedCount > 5) {
                    TextButton(onClick = { isUnapprehendedExpanded = !isUnapprehendedExpanded }) {
                        Text(
                            text = if (isUnapprehendedExpanded) "Show less ←" else "View all $unapprehendedCount →",
                            color = colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            // ── Global Counters Vertical Stack Shift ──
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                SectionHeaderLabel("GLOBAL COUNTERS")
                Spacer(Modifier.height(12.dp))
                TimeframeToggle(selectedTimeframe) { selectedTimeframe = it }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SummaryCard(Modifier.weight(1f).fillMaxHeight(), "Helmet", "$helmetCount", Icons.Default.Security, Color(0xFFFF5252))
                SummaryCard(Modifier.weight(1f).fillMaxHeight(), "Overspeeding", "$overspeedCount", Icons.Default.Speed, Color(0xFFCE93D8))
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SummaryCard(Modifier.weight(1f).fillMaxHeight(), "Overloading", "$overloadingCount", Icons.Default.Groups, Color(0xFF64B5F6))
                SummaryCard(Modifier.weight(1f).fillMaxHeight(), "Illegal Parking", "$parkingCount", Icons.Default.LocalParking, Color(0xFFFFB74D))
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.Center
            ) {
                SummaryCard(Modifier.fillMaxWidth(0.5f).fillMaxHeight(), "Manual Entry", "$manualCount", Icons.Default.Warning, Color(0xFF9E9E9E))
            }

            Spacer(Modifier.height(32.dp))
            SectionHeaderLabel("YEAR-OVER-YEAR COMPARISON")
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Select two years in the app, then compare totals and graphs instantly",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            if (availableYears.size < 2) {
                Surface(
                    modifier = Modifier.fillMaxWidth().height(70.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = colorScheme.surface,
                    border = BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.1f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("Need at least two years of data to compare", color = colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                }
            } else {
                YearComparisonCard(
                    availableYears = availableYears,
                    yearA = compareYearA,
                    yearB = compareYearB,
                    onYearAChange = { compareYearA = it },
                    onYearBChange = { compareYearB = it },
                    violations = violationsList
                )
            }

            Spacer(Modifier.height(100.dp))
        }

        viewedViolation?.let { violation ->
            EvidenceViewerDialog(violation = violation, onDismiss = { viewedViolation = null })
        }

        // ── CRITICAL FIX: Concurrent Session Lockout UI for Admin ──
        if (forceLogoutEvent.value) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                AlertDialog(
                    onDismissRequest = { /* Cannot be dismissed */ },
                    containerColor = colorScheme.surface,
                    title = { Text("Session Expired", fontWeight = FontWeight.Bold, color = colorScheme.error) },
                    text = {
                        Text(
                            "Your account was just signed in on another device. For security reasons, you have been automatically logged out of this session.",
                            color = colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = { forceLogoutEvent.value = false; onLogout() },
                            colors = ButtonDefaults.buttonColors(containerColor = colorScheme.error)
                        ) {
                            Text("Acknowledge & Sign Out", fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }
        }
    }
}

// ── Horizontal Scroll added for small devices ──
@Composable
fun TimeframeToggle(selected: String, onSelect: (String) -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colorScheme.surfaceVariant.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf("Daily", "Weekly", "Monthly", "Annual").forEach { option ->
                val isSelected = selected == option
                val tabColor by animateColorAsState(
                    targetValue = if (isSelected) colorScheme.primary else Color.Transparent,
                    animationSpec = tween(durationMillis = 160),
                    label = "timeframeTabColor"
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
                    animationSpec = tween(durationMillis = 160),
                    label = "timeframeTextColor"
                )
                Box(
                    modifier = Modifier
                        .height(42.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(tabColor)
                        .clickable { onSelect(option) }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = option,
                        color = textColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun AdminRequestsPanel(pendingCount: Int, onClick: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = colorScheme.surface,
        border = BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.1f)),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                AdminIcon(size = 28f)
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Pending Requests",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colorScheme.onSurface
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RequestTypePill("Reg", registrationRequests.count { it.status == "Pending" }, Color(0xFF64B5F6))
                    RequestTypePill("Reset", resetRequests.count { it.status == "Pending" }, Color(0xFFFFB74D))
                    RequestTypePill("ChgPW", changePasswordRequests.count { it.status == "Pending" }, Color(0xFFCE93D8))
                }
            }

            if (pendingCount > 0) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(colorScheme.error),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$pendingCount",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Spacer(Modifier.width(8.dp))
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
fun RequestTypePill(label: String, count: Int, color: Color) {
    if (count > 0) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(color.copy(alpha = 0.15f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = "$count $label",
                color = color,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun SectionHeaderLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold
    )
}

@Composable
fun SystemHealthPanel(health: SystemHealth, idleCount: Int) {
    val colorScheme = MaterialTheme.colorScheme
    fun statusColor(s: String) = when (s) {
        "Connected", "Active" -> Color(0xFF4CAF50)
        "Inactive", "Not Connected" -> Color(0xFFFFB74D)
        else -> Color(0xFFFF5252)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = colorScheme.surface,
        border = BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.1f)),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HealthRow("AI Core Stream", health.aiCoreStatus, statusColor(health.aiCoreStatus))
            HorizontalDivider(color = colorScheme.outline.copy(alpha = 0.05f))
            HealthRow("Firebase Synchronization", health.firebaseSyncStatus, statusColor(health.firebaseSyncStatus))
            HorizontalDivider(color = colorScheme.outline.copy(alpha = 0.05f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Active Devices",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF4CAF50).copy(alpha = 0.15f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4CAF50))
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "${health.activeDevices} Online",
                                fontSize = 11.sp,
                                color = Color(0xFF4CAF50),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    val idleColor = Color(0xFFFFB74D)
                    Surface(
                        shape = CircleShape,
                        color = idleColor.copy(alpha = 0.15f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(idleColor)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "$idleCount Idle",
                                fontSize = 11.sp,
                                color = idleColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun YearComparisonCard(
    availableYears: List<Int>,
    yearA: Int,
    yearB: Int,
    onYearAChange: (Int) -> Unit,
    onYearBChange: (Int) -> Unit,
    violations: List<ViolationEntry>
) {
    val colorScheme = MaterialTheme.colorScheme
    var comparisonType by remember { mutableStateOf("Annual") }
    val statsA = remember(yearA, comparisonType, violations.size) { computePeriodBreakdown(violations, yearA, comparisonType) }
    val statsB = remember(yearB, comparisonType, violations.size) { computePeriodBreakdown(violations, yearB, comparisonType) }

    val totalChange = statsB.total - statsA.total
    val pctChange = if (statsA.total != 0) (totalChange.toFloat() / statsA.total * 100f) else if (statsB.total > 0) 100f else 0f
    val isIncrease = totalChange > 0
    val trendColor = if (totalChange == 0) colorScheme.onSurfaceVariant else if (isIncrease) Color(0xFFE53935) else Color(0xFF4CAF50)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = colorScheme.surface,
        border = BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.1f)),
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "$yearA vs $yearB",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                color = colorScheme.onSurface
            )
            Text(
                text = "$comparisonType comparison by violation type",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "SELECT YEARS TO COMPARE",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                color = colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            // ── Year Pickers ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                YearPickerChip(label = "BASE YEAR", year = yearA, availableYears = availableYears, onSelect = onYearAChange, accentColor = Color(0xFF64B5F6))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                YearPickerChip(label = "COMPARE WITH", year = yearB, availableYears = availableYears, onSelect = onYearBChange, accentColor = Color(0xFFFFB74D))
            }

            Spacer(Modifier.height(16.dp))
            TimeframeToggle(comparisonType) { comparisonType = it }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = colorScheme.outline.copy(alpha = 0.08f))
            Spacer(Modifier.height(16.dp))

            // ── Total Comparison Row ──
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$yearA", style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    RollingNumberText(value = "${statsA.total}", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black), color = colorScheme.onSurface)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (totalChange == 0) Icons.Default.Remove else if (isIncrease) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                        contentDescription = null, tint = trendColor, modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "${if (totalChange >= 0) "+" else ""}${"%.1f".format(pctChange)}%",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = trendColor
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$yearB", style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    RollingNumberText(value = "${statsB.total}", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black), color = colorScheme.onSurface)
                }
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = colorScheme.outline.copy(alpha = 0.08f))
            Spacer(Modifier.height(16.dp))

            // ── Per-Type Comparison Bars ──
            ViolationType.entries.forEachIndexed { index, type ->
                val valA = statsA.getValue(type)
                val valB = statsB.getValue(type)
                Column(modifier = Modifier.padding(bottom = 16.dp)) {
                    Text(type.label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    ComparisonSplitBarRow(
                        valueA = valA,
                        valueB = valB,
                        yearA = yearA,
                        yearB = yearB,
                        colorA = Color(0xFF64B5F6),
                        colorB = Color(0xFFFFB74D)
                    )
                    if (index != ViolationType.entries.lastIndex) {
                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(color = colorScheme.outline.copy(alpha = 0.08f))
                    }
                }
            }
        }
    }
}

private fun computePeriodBreakdown(violations: List<ViolationEntry>, year: Int, timeframe: String): YearlyBreakdown {
    val periodViolations = getViolationsForPeriod(violations, timeframe, year)
    return YearlyBreakdown(
        year = year,
        helmet = periodViolations.count { it.types.contains(ViolationType.NO_HELMET) },
        overloading = periodViolations.count { it.types.contains(ViolationType.OVERLOADING) },
        overspeeding = periodViolations.count { it.types.contains(ViolationType.OVERSPEEDING) },
        illegalParking = periodViolations.count { it.types.contains(ViolationType.ILLEGAL_PARKING) },
        manual = periodViolations.count { it.types.contains(ViolationType.MANUAL) }
    )
}

@Composable
private fun ComparisonSplitBarRow(
    valueA: Int,
    valueB: Int,
    yearA: Int,
    yearB: Int,
    colorA: Color,
    colorB: Color
) {
    val colorScheme = MaterialTheme.colorScheme
    val total = valueA + valueB
    val shareA = if (total > 0) valueA.toFloat() / total else 0f
    val shareB = if (total > 0) valueB.toFloat() / total else 0f
    var revealBars by remember(valueA, valueB) { mutableStateOf(false) }
    LaunchedEffect(valueA, valueB) {
        revealBars = false
        withFrameNanos { }
        revealBars = true
    }
    val revealProgress by animateFloatAsState(
        targetValue = if (revealBars) 1f else 0f,
        animationSpec = tween(durationMillis = 700),
        label = "comparisonSplitBarProgress"
    )
    val animatedShareA = shareA * revealProgress
    val animatedShareB = shareB * revealProgress

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$yearA",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                color = colorA
            )
            Text(
                text = "$yearB",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                color = colorB
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            colorA.copy(alpha = 0.10f),
                            colorScheme.onSurface.copy(alpha = 0.045f),
                            colorB.copy(alpha = 0.10f)
                        )
                    )
                )
        ) {
            if (total > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedShareA.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .align(Alignment.CenterStart)
                        .background(Brush.horizontalGradient(listOf(colorA.copy(alpha = 0.78f), colorA))),
                    contentAlignment = Alignment.Center
                ) {
                    if (shareA >= 0.16f && revealProgress > 0.55f) {
                        Text("${(shareA * 100).toInt()}%", color = Color.Black.copy(alpha = 0.72f), fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedShareB.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .align(Alignment.CenterEnd)
                        .background(Brush.horizontalGradient(listOf(colorB, colorB.copy(alpha = 0.82f)))),
                    contentAlignment = Alignment.Center
                ) {
                    if (shareB >= 0.16f && revealProgress > 0.55f) {
                        Text("${(shareB * 100).toInt()}%", color = Color.Black.copy(alpha = 0.72f), fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RollingNumberText(
                    value = "$valueA",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = colorA.copy(alpha = 0.72f)
                )
                Spacer(Modifier.width(4.dp))
                Text("violations", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = colorA.copy(alpha = 0.72f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RollingNumberText(
                    value = "$valueB",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = colorB.copy(alpha = 0.72f)
                )
                Spacer(Modifier.width(4.dp))
                Text("violations", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = colorB.copy(alpha = 0.72f))
            }
        }
    }
}

@Composable
private fun YearPickerChip(
    label: String,
    year: Int,
    availableYears: List<Int>,
    onSelect: (Int) -> Unit,
    accentColor: Color
) {
    val colorScheme = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Box {
            Surface(
                onClick = { expanded = true },
                shape = RoundedCornerShape(10.dp),
                color = accentColor.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("$year", color = accentColor, fontWeight = FontWeight.Black)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                availableYears.forEach { y ->
                    DropdownMenuItem(text = { Text("$y") }, onClick = { onSelect(y); expanded = false })
                }
            }
        }
    }
}

@Composable
fun HealthRow(label: String, status: String, statusColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            shape = CircleShape,
            color = statusColor.copy(alpha = 0.15f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = status.uppercase(),
                    fontSize = 11.sp,
                    color = statusColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
