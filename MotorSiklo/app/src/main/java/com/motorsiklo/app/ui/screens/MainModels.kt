package com.motorsiklo.app.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import java.util.Calendar

// Global Sync State to prevent infinite loading spinners
val isDataFullySynced = mutableStateOf(true)

// Global Security State for session expiration
val forceLogoutEvent = mutableStateOf(false)

sealed class BottomNavItem(val route: String, val label: String, val icon: ImageVector) {
    object Dashboard  : BottomNavItem("dashboard",  "Dashboard",  Icons.Default.Dashboard)
    object Violations : BottomNavItem("violations", "Violations", Icons.Default.Warning)
    object Statistics : BottomNavItem("statistics", "Statistics", Icons.Default.BarChart)
    object Profile    : BottomNavItem("profile",    "Profile",    Icons.Default.Person)
}

val bottomNavItems = listOf(BottomNavItem.Dashboard, BottomNavItem.Violations, BottomNavItem.Statistics, BottomNavItem.Profile)

enum class ViolationType(val label: String) {
    NO_HELMET("No/Improper Helmet"),
    OVERLOADING("Overloading"),
    OVERSPEEDING("Overspeeding"),
    ILLEGAL_PARKING("Illegal Parking"),
    MANUAL("Manual Entry")
}

fun getViolationColor(type: ViolationType): Color = when (type) {
    ViolationType.NO_HELMET       -> Color(0xFFFF5252)
    ViolationType.OVERLOADING     -> Color(0xFF64B5F6)
    ViolationType.OVERSPEEDING    -> Color(0xFFFFB74D)
    ViolationType.ILLEGAL_PARKING -> Color(0xFF9575CD)
    ViolationType.MANUAL          -> Color(0xFF4CAF50)
}

fun getViolationIcon(type: ViolationType): ImageVector = when (type) {
    ViolationType.NO_HELMET       -> Icons.Default.Warning
    ViolationType.OVERLOADING     -> Icons.Default.Group
    ViolationType.OVERSPEEDING    -> Icons.Default.Speed
    ViolationType.ILLEGAL_PARKING -> Icons.Default.NotListedLocation
    ViolationType.MANUAL          -> Icons.Default.EditNote
}

data class ViolationEntry(
    val id: String,
    val types: List<ViolationType>,
    val timestamp: Long,
    val location: String,
    val status: String = "Pending",
    val imageUrl: String = "",
    val videoUrl: String = "",       // NEW: video clip URL (populated for overspeeding)
    val badgeNumber: String = ""
) {
    val isMultiple: Boolean get() = types.size > 1
    val primaryType: ViolationType get() = types.firstOrNull() ?: ViolationType.MANUAL

    /** True when the AI has attached a video clip to this violation. */
    val hasVideoEvidence: Boolean get() = videoUrl.isNotEmpty()

    /** Calendar year this violation was recorded in (e.g. 2026). */
    val year: Int get() = Calendar.getInstance().apply { timeInMillis = timestamp }.get(Calendar.YEAR)
}

/**
 * YEAR SEGREGATION HELPERS
 * ─────────────────────────────────────────────────────────────────────────
 * MotorSiklo started collecting data in 2026, so the app should be able to
 * segregate and compare violations year-by-year (2026 vs 2027 vs 2028 ...)
 * rather than only showing "this calendar year" like the old Annual tab did.
 */

/** App launch year — used as a floor so the year picker never shows pre-launch years. */
const val APP_LAUNCH_YEAR: Int = 2021
const val APP_REPORT_END_YEAR: Int = 2030

fun getSelectableReportYears(): List<Int> = (APP_LAUNCH_YEAR..APP_REPORT_END_YEAR).toList()

fun getDefaultReportYear(): Int {
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    return currentYear.coerceIn(APP_LAUNCH_YEAR, APP_REPORT_END_YEAR)
}

/**
 * Returns every year that actually has data, plus the current year (so a brand-new
 * year always shows up even with zero violations yet), sorted descending (newest first).
 * Never returns a year before [APP_LAUNCH_YEAR].
 */
fun getAvailableYears(violations: List<ViolationEntry>): List<Int> = getSelectableReportYears()

fun getPeriodRangeForYear(timeframe: String, year: Int): Pair<Long, Long> {
    val now = Calendar.getInstance().apply {
        firstDayOfWeek = Calendar.MONDAY
        minimalDaysInFirstWeek = 4
    }
    val calendar = Calendar.getInstance().apply {
        firstDayOfWeek = Calendar.MONDAY
        minimalDaysInFirstWeek = 4
    }

    when (timeframe) {
        "Weekly" -> {
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.WEEK_OF_YEAR, now.get(Calendar.WEEK_OF_YEAR))
            calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val start = calendar.timeInMillis
            calendar.add(Calendar.DAY_OF_WEEK, 6)
            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            calendar.set(Calendar.MILLISECOND, 999)
            return start to calendar.timeInMillis
        }
        "Monthly" -> {
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, now.get(Calendar.MONTH))
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val start = calendar.timeInMillis
            calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            calendar.set(Calendar.MILLISECOND, 999)
            return start to calendar.timeInMillis
        }
        "Annual" -> {
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, Calendar.JANUARY)
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val start = calendar.timeInMillis
            calendar.set(Calendar.MONTH, Calendar.DECEMBER)
            calendar.set(Calendar.DAY_OF_MONTH, 31)
            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            calendar.set(Calendar.MILLISECOND, 999)
            return start to calendar.timeInMillis
        }
        else -> {
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, now.get(Calendar.MONTH))
            calendar.set(Calendar.DAY_OF_MONTH, now.get(Calendar.DAY_OF_MONTH))
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val start = calendar.timeInMillis
            return start to (start + 86_399_999L)
        }
    }
}

fun getViolationsForPeriod(violations: List<ViolationEntry>, timeframe: String, year: Int): List<ViolationEntry> {
    val (start, end) = getPeriodRangeForYear(timeframe, year)
    return violations.filter { it.timestamp in start..end }
}

/** Per-violation-type breakdown for a single calendar year. */
data class YearlyBreakdown(
    val year: Int,
    val helmet: Int,
    val overloading: Int,
    val overspeeding: Int,
    val illegalParking: Int,
    val manual: Int
) {
    val total: Int get() = helmet + overloading + overspeeding + illegalParking + manual
    fun getValue(type: ViolationType): Int = when (type) {
        ViolationType.NO_HELMET       -> helmet
        ViolationType.OVERLOADING     -> overloading
        ViolationType.OVERSPEEDING    -> overspeeding
        ViolationType.ILLEGAL_PARKING -> illegalParking
        ViolationType.MANUAL          -> manual
    }
}

/** Computes the [YearlyBreakdown] for one specific year from the full violations list. */
fun computeYearlyBreakdown(violations: List<ViolationEntry>, year: Int): YearlyBreakdown {
    val yearViolations = violations.filter { it.year == year }
    return YearlyBreakdown(
        year = year,
        helmet = yearViolations.count { it.types.contains(ViolationType.NO_HELMET) },
        overloading = yearViolations.count { it.types.contains(ViolationType.OVERLOADING) },
        overspeeding = yearViolations.count { it.types.contains(ViolationType.OVERSPEEDING) },
        illegalParking = yearViolations.count { it.types.contains(ViolationType.ILLEGAL_PARKING) },
        manual = yearViolations.count { it.types.contains(ViolationType.MANUAL) }
    )
}

/** Computes a [YearlyBreakdown] for every year in [years], sorted ascending (oldest first) for charting. */
fun computeAllYearlyBreakdowns(violations: List<ViolationEntry>, years: List<Int>): List<YearlyBreakdown> =
    years.sorted().map { computeYearlyBreakdown(violations, it) }

data class TicketResult(
    val violation: ViolationEntry,
    val topNumber: String,
    val violatorName: String,
    val violatorAddress: String,
    val licenseNumber: String,
    val licenseType: String,
    val plateNumber: String,
    val makeAndType: String,
    val placeOfApprehension: String,
    val apprehensionDate: String,
    val apprehensionTime: String,
    val confiscatedItems: List<String>,
    val offenseItems: List<String>,
    val remarks: String,
    val officerLastName: String,
    val officerFirstName: String,
    val officerMiddleInitial: String,
    val officerId: String,
    val unitStation: String
) {
    val officerFullName: String get() = "$officerFirstName $officerMiddleInitial $officerLastName".trim()
}

data class UserProfile(
    val uid: String = "",
    val username: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val middleName: String = "",
    val badgeNumber: String = "",
    val station: String = "",
    val role: String = ""
)
val currentUserProfile = mutableStateOf<UserProfile?>(null)

data class GlobalStats(
    val manualTicketCount: Int = 0,
    val helmetCount: Int = 0,
    val overspeedingCount: Int = 0,
    val overloadingCount: Int = 0,
    val illegalParkingCount: Int = 0
) {
    val totalAiDetections: Int get() = helmetCount + overspeedingCount + overloadingCount + illegalParkingCount
}
val currentGlobalStats = mutableStateOf(GlobalStats())

data class DetectionSettings(
    val maxRiders: Int = 2,
    val speedLimitKmhTop: Int = 35,
    val speedLimitKmhBottom: Int = 40,
    val illegalParkingSeconds: Int = 180,
    val updatedAt: Long = 0L,
    val updatedBy: String = ""
)
val currentDetectionSettings = mutableStateOf(DetectionSettings())

data class SystemHealth(
    val aiCoreStatus: String = "Not Connected",
    val firebaseSyncStatus: String = "Inactive",
    val activeDevices: Int = 0
)
val currentSystemHealth = mutableStateOf(SystemHealth())

data class EnforcerActivity(val badgeNumber: String = "", val name: String = "", val citationCount: Int = 0, val isOnline: Boolean = false)
val enforcerActivities = mutableStateListOf<EnforcerActivity>()

data class RegistrationRequest(
    val id: String = "", val lastName: String = "", val firstName: String = "", val middleName: String = "", val username: String = "",
    val badgeNumber: String = "", val password: String = "", val station: String = "", val timestamp: Long = 0L, var status: String = "Pending"
) { val fullName: String get() = "$lastName, $firstName $middleName".trim() }

data class ResetRequest(val id: String = "", val username: String = "", val badgeNumber: String = "", val desiredPassword: String = "", val timestamp: Long = 0L, var status: String = "Pending")
data class ChangePasswordRequest(val id: String = "", val username: String = "", val badgeNumber: String = "", val desiredPassword: String = "", val timestamp: Long = 0L, var status: String = "Pending")

val registrationRequests  = mutableStateListOf<RegistrationRequest>()
val resetRequests         = mutableStateListOf<ResetRequest>()
val changePasswordRequests = mutableStateListOf<ChangePasswordRequest>()

val totalPendingRequests: Int
    get() = registrationRequests.count { it.status == "Pending" } +
            resetRequests.count { it.status == "Pending" } +
            changePasswordRequests.count { it.status == "Pending" }
