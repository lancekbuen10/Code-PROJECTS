@file:OptIn(ExperimentalMaterial3Api::class)
package com.motorsiklo.app.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.motorsiklo.app.ui.theme.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── OPTIMIZATION: Pre-compiled Regex Engines ──
private val LicenseRegex = Regex("^[A-Z]\\d{10}$")
private val PlateRegex = Regex("^[A-Z]{3}\\d{3,4}$")

private data class LicenseScanResult(
    val lastName: String = "",
    val firstName: String = "",
    val middleName: String = "",
    val address: String = "",
    val licenseNumber: String = ""
)

private fun cropToLicenseFrame(context: Context, uri: Uri): Bitmap? {
    val original = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it)
    } ?: return null

    val frameWidth = (original.width * 0.86f).toInt()
    val frameHeight = (frameWidth / 1.586f).toInt()

    val left = ((original.width - frameWidth) / 2).coerceAtLeast(0)
    val top = ((original.height - frameHeight) / 2).coerceAtLeast(0)

    val safeWidth = frameWidth.coerceAtMost(original.width - left)
    val safeHeight = frameHeight.coerceAtMost(original.height - top)

    return Bitmap.createBitmap(original, left, top, safeWidth, safeHeight)
}

private fun parseLicenseText(rawText: String): LicenseScanResult {
    val lines = rawText.lines()
        .map { it.trim() }
        .filter { it.isNotBlank() }

    val upperLines = lines.map { it.uppercase() }

    fun cleanValue(value: String): String {
        return value
            .replace(Regex("\\s+"), " ")
            .replace(":", "")
            .trim()
    }

    fun isBadValue(value: String): Boolean {
        val upper = value.uppercase().trim()

        if (upper.isBlank()) return true
        if (upper.length <= 1) return true

        val blockedWords = listOf(
            "REPUBLIC", "PHILIPPINES", "DEPARTMENT", "TRANSPORTATION",
            "LAND TRANSPORTATION OFFICE", "NON-PROFESSIONAL", "DRIVER",
            "LICENSE", "NATIONALITY", "SEX", "DATE", "BIRTH", "WEIGHT",
            "HEIGHT", "BLOOD", "TYPE", "EYES", "COLOR", "RESTRICTIONS",
            "CONDITIONS", "EXPIRATION", "AGENCY", "CODE", "SIGNATURE",
            "FRONT", "AUTODEAL", "NEWS", "REVIEWS", "COOKIE", "PRIVACY"
        )

        if (blockedWords.any { upper.contains(it) }) return true

        // Reject numbers like 1.55, 70, 1987/10/04, N32, 2022/10/04
        if (upper.matches(Regex("^[0-9./-]+$"))) return true
        if (upper.matches(Regex("^[A-Z]?\\d{1,3}$"))) return true
        if (upper.matches(Regex("^\\d{4}[/.-]\\d{1,2}[/.-]\\d{1,2}$"))) return true

        return false
    }

    fun cleanName(value: String): String {
        return cleanValue(value)
            .replace(Regex("[^A-Za-z Ññ.,-]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun isLikelyName(value: String): Boolean {
        val cleaned = cleanName(value)
        if (isBadValue(cleaned)) return false
        val words = cleaned.split(" ").filter { it.isNotBlank() }
        return words.isNotEmpty() &&
                words.size <= 6 &&
                cleaned.any { it.isLetter() } &&
                !cleaned.contains("UNIT", ignoreCase = true) &&
                !cleaned.contains("HOUSE", ignoreCase = true) &&
                !cleaned.contains("BARANGAY", ignoreCase = true)
    }

    fun lineAfterLabel(vararg labels: String): String {
        val index = upperLines.indexOfFirst { line ->
            labels.any { label -> line.contains(label.uppercase()) }
        }
        if (index == -1) return ""

        val sameLine = labels.firstNotNullOfOrNull { label ->
            val labelUpper = label.uppercase()
            val pos = upperLines[index].indexOf(labelUpper)
            if (pos >= 0) {
                lines[index].substring(pos + label.length)
                    .replace(":", "")
                    .trim()
                    .takeIf { !isBadValue(it) }
            } else null
        }

        if (!sameLine.isNullOrBlank()) return sameLine

        return lines.drop(index + 1)
            .firstOrNull { !isBadValue(it) }
            ?.trim()
            ?: ""
    }

    // NAME: PH license usually has:
    // "Last Name, First Name, Middle Name"
    // then value below, e.g. "DELA CRUZ, JUAN PEDRO GARCIA"
    val nameHeaderIndex = upperLines.indexOfFirst {
        it.contains("LAST") && it.contains("FIRST") && it.contains("MIDDLE")
    }

    val fullNameLine = if (nameHeaderIndex != -1) {
        lines.drop(nameHeaderIndex + 1)
            .firstOrNull { isLikelyName(it) && it.contains(",") }
            ?: ""
    } else {
        ""
    }

    var lastName = ""
    var firstName = ""
    var middleName = ""

    if (fullNameLine.contains(",")) {
        val parts = fullNameLine.split(",", limit = 2).map { cleanName(it) }
        lastName = parts.getOrNull(0).orEmpty()

        val givenParts = parts.getOrNull(1)
            .orEmpty()
            .split(" ")
            .filter { it.isNotBlank() }

        firstName = givenParts.firstOrNull().orEmpty()
        middleName = givenParts.drop(1).joinToString(" ")
    }

    if (lastName.isBlank()) {
        lastName = cleanName(lineAfterLabel("Last Name", "Surname"))
    }
    if (firstName.isBlank()) {
        firstName = cleanName(lineAfterLabel("First Name", "Given Name"))
    }
    if (middleName.isBlank()) {
        middleName = cleanName(lineAfterLabel("Middle Name"))
    }

    // ADDRESS: collect lines after Address until License No / Expiration / Agency
    val addressIndex = upperLines.indexOfFirst { it.contains("ADDRESS") }
    val addressLines = mutableListOf<String>()

    if (addressIndex != -1) {
        for (i in addressIndex + 1 until lines.size) {
            val upper = upperLines[i]

            if (
                upper.contains("LICENSE") ||
                upper.contains("EXPIRATION") ||
                upper.contains("AGENCY") ||
                upper.contains("BLOOD") ||
                upper.contains("EYES") ||
                upper.contains("RESTRICTIONS") ||
                upper.contains("CONDITIONS")
            ) {
                break
            }

            val candidate = cleanValue(lines[i])
            if (!isBadValue(candidate)) {
                addressLines.add(candidate)
            }
        }
    }

    val address = addressLines
        .take(3)
        .joinToString(", ")
        .trim()

    // LICENSE NUMBER:
    // Prefer text after License No. first.
    val licenseIndex = upperLines.indexOfFirst {
        it.contains("LICENSE") && (it.contains("NO") || it.contains("#"))
    }

    val licenseSearchText = buildString {
        if (licenseIndex != -1) {
            append(lines[licenseIndex])
            append(" ")
            append(lines.getOrNull(licenseIndex + 1).orEmpty())
        }
        append(" ")
        append(rawText)
    }.uppercase()
        .replace("O", "0")
        .replace("I", "1")
        .replace("L", "1")
        .replace("S", "5")
        .replace("B", "8")

    val licenseMatch = Regex("[A-Z0-9]\\d{2}[-\\s]?\\d{2}[-\\s]?\\d{6}")
        .find(licenseSearchText)

    var licenseNumber = licenseMatch?.value
        ?.replace("-", "")
        ?.replace(" ", "")
        ?: ""

    // Do not accept impossible first character from common OCR mistakes.
    // PH license format should display as L00-00-000000 in your app.
    if (licenseNumber.isNotBlank() && licenseNumber.first().isDigit()) {
        licenseNumber = ""
    }

    return LicenseScanResult(
        lastName = lastName,
        firstName = firstName,
        middleName = middleName,
        address = address,
        licenseNumber = licenseNumber
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Dual-evidence panel — shows photo + video clip side-by-side when both exist,
// or a single full-width photo when there is no video.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EvidencePanel(
    violation: ViolationEntry,
    onExpandImage: () -> Unit,
    onExpandVideo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current

    // ── OPTIMIZATION: Cached Image Requests ──
    val imageReq = remember(violation.imageUrl) {
        ImageRequest.Builder(context).data(violation.imageUrl).crossfade(true).build()
    }
    val videoReq = remember(violation.videoUrl) {
        ImageRequest.Builder(context).data(violation.videoUrl).crossfade(true).build()
    }

    if (violation.hasVideoEvidence) {
        Column(modifier = modifier) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EvidenceLabel(text = "SCREENSHOT", icon = Icons.Default.PhotoCamera, color = colorScheme.primary, modifier = Modifier.weight(1f))
                EvidenceLabel(text = "VIDEO CLIP", icon = Icons.Default.Videocam, color = Color(0xFFFFB74D), modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth().height(160.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Screenshot Box
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(colorScheme.surfaceVariant).clickable { onExpandImage() },
                    contentAlignment = Alignment.Center
                ) {
                    if (violation.imageUrl.isNotEmpty()) {
                        AsyncImage(
                            model = imageReq,
                            contentDescription = "Screenshot Evidence",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            error = androidx.compose.ui.graphics.painter.ColorPainter(colorScheme.error.copy(alpha = 0.2f))
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.BrokenImage, null, tint = colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(28.dp))
                            Spacer(Modifier.height(4.dp))
                            Text("No image", fontSize = 10.sp, color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        }
                    }
                    Icon(Icons.Default.ZoomIn, "Zoom", tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).size(22.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape).padding(3.dp))
                }

                // Video Clip Box
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(Color(0xFF1A1A1A)).clickable { onExpandVideo() },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = videoReq,
                        contentDescription = "Video Clip Thumbnail",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        error = androidx.compose.ui.graphics.painter.ColorPainter(Color.Transparent)
                    )
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
                    Box(modifier = Modifier.size(44.dp).background(Color.White.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.PlayArrow, "Play Video", tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                    Surface(modifier = Modifier.align(Alignment.TopStart).padding(6.dp), color = Color(0xFFFFB74D), shape = RoundedCornerShape(4.dp)) {
                        Text("CLIP", modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp), fontSize = 8.sp, fontWeight = FontWeight.Black, color = Color.Black)
                    }
                }
            }
        }
    } else if (violation.imageUrl.isNotEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)).background(colorScheme.surfaceVariant).clickable { onExpandImage() },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = imageReq,
                contentDescription = "Evidence Image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                error = androidx.compose.ui.graphics.painter.ColorPainter(colorScheme.error.copy(alpha = 0.2f))
            )
            Icon(Icons.Default.ZoomIn, "Zoom", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).size(28.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape).padding(4.dp))
        }
    }
}

@Composable
private fun EvidenceLabel(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, tint = color, modifier = Modifier.size(12.dp))
        Text(text, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp), color = color)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LocalEvidencePanel — preview of a freshly-captured camera photo, before it
// has been uploaded to Firebase Storage. Used for manual entry tickets only.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LocalEvidencePanel(uri: Uri, modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val imageReq = remember(uri) {
        ImageRequest.Builder(context).data(uri).crossfade(true).build()
    }

    Column(modifier = modifier) {
        EvidenceLabel(text = "PHOTO CAPTURED (NOT YET UPLOADED)", icon = Icons.Default.PhotoCamera, color = Color(0xFF4CAF50))
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)).background(colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = imageReq,
                contentDescription = "Captured Evidence Photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                error = androidx.compose.ui.graphics.painter.ColorPainter(colorScheme.error.copy(alpha = 0.2f))
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// VideoPlayerDialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun VideoPlayerDialog(videoUrl: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(videoUrl) {
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(videoUrl)).apply {
            setDataAndType(android.net.Uri.parse(videoUrl), "video/mp4")
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            val browserIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(videoUrl)).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(browserIntent)
        }
        onDismiss()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DigitalTicketDialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun DigitalTicketDialog(
    violation: ViolationEntry,
    enforcerName: String,
    role: String,
    existingTicket: TicketResult? = null,
    localEvidenceUri: Uri? = null,
    onDismiss: () -> Unit,
    onVoid: () -> Unit = {},
    onDone: (TicketResult) -> Unit
) {
    val userProfile = currentUserProfile.value
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val isAdmin = role == "Administrator"
    val isEditable = !isAdmin

    var topNumber by remember { mutableStateOf(existingTicket?.topNumber ?: "2108${(1000..9999).random()}-${(0..9).random()}") }

    val nameParts = existingTicket?.violatorName?.split(" ") ?: emptyList()
    var violatorLastName by remember { mutableStateOf(existingTicket?.violatorName?.substringBefore(",")?.trim() ?: "") }
    var violatorFirstName by remember { mutableStateOf(if (nameParts.size > 1) existingTicket?.violatorName?.substringAfter(", ")?.substringBeforeLast(" ")?.trim() ?: "" else "") }
    var violatorMiddleName by remember { mutableStateOf(if (nameParts.size > 2) nameParts.last().trim() else "") }

    var violatorAddress by remember { mutableStateOf(existingTicket?.violatorAddress ?: "") }

    var licenseNumber by remember { mutableStateOf(existingTicket?.licenseNumber?.replace("-", "") ?: "") }
    var licenseType by remember { mutableStateOf(existingTicket?.licenseType.takeIf { !it.isNullOrBlank() } ?: "Non-Pro") }

    var plateNumber by remember { mutableStateOf(existingTicket?.plateNumber?.replace(" ", "") ?: "") }
    var makeAndType by remember { mutableStateOf(existingTicket?.makeAndType ?: "") }
    var placeOfApprehension by remember { mutableStateOf(existingTicket?.placeOfApprehension ?: "") }

    var apprehensionDate by remember { mutableStateOf(existingTicket?.apprehensionDate ?: SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()).format(Date())) }
    var apprehensionTime by remember { mutableStateOf(existingTicket?.apprehensionTime ?: SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())) }

    var confiscateLicense by remember { mutableStateOf(existingTicket?.confiscatedItems?.contains("Driver's License") == true) }
    var confiscateVehicle by remember { mutableStateOf(existingTicket?.confiscatedItems?.contains("Motor Vehicle") == true) }
    var remarks by remember { mutableStateOf(existingTicket?.remarks ?: "") }

    val officerLastName  = existingTicket?.officerLastName.takeIf { !it.isNullOrBlank() } ?: userProfile?.lastName ?: ""
    val officerFirstName = existingTicket?.officerFirstName.takeIf { !it.isNullOrBlank() } ?: userProfile?.firstName ?: ""
    val officerMI        = existingTicket?.officerMiddleInitial.takeIf { !it.isNullOrBlank() } ?: userProfile?.middleName?.trim()?.firstOrNull()?.uppercase()?.let { "$it." } ?: ""
    val officerId        = existingTicket?.officerId.takeIf { !it.isNullOrBlank() } ?: userProfile?.badgeNumber ?: ""
    val unitStation      = existingTicket?.unitStation.takeIf { !it.isNullOrBlank() } ?: userProfile?.station?.takeIf { it.isNotBlank() } ?: ""

    val offenseOptions = listOf("No/Improper Helmet", "Overspeeding", "Overloading", "Illegal Parking")

    var isMultipleViolations by remember { mutableStateOf(existingTicket?.offenseItems?.let { it.size > 1 } ?: violation.isMultiple) }
    var selectedOffenses by remember {
        val defaultLabels = existingTicket?.offenseItems ?: violation.types.map { it.label }
        mutableStateOf(defaultLabels.filter { offenseOptions.contains(it) }.toSet())
    }

    // ── NEW: Law Hyperlinks Map for Professor's Recommendation ──
    val legalRefs = mapOf(
        "No/Improper Helmet" to Pair("R.A. 10054 (Motorcycle Helmet Act of 2009)", "https://lawphil.net/statutes/repacts/ra2010/ra_10054_2010.html"),
        "Overspeeding" to Pair("R.A. 4136 (Land Transportation and Traffic Code)", "https://lawphil.net/statutes/repacts/ra1964/ra_4136_1964.html"),
        "Overloading" to Pair("R.A. 4136 (Land Transportation and Traffic Code)", "https://lawphil.net/statutes/repacts/ra1964/ra_4136_1964.html"),
        "Illegal Parking" to Pair("R.A. 4136 (Land Transportation and Traffic Code)", "https://lawphil.net/statutes/repacts/ra1964/ra_4136_1964.html")
    )

    var showFullImage by remember { mutableStateOf(false) }
    var showVideoPlayer by remember { mutableStateOf(false) }
    var isScanningLicense by remember { mutableStateOf(false) }
    var showLicenseCameraDialog by remember { mutableStateOf(false) }

    // ── One recognizer client for the lifetime of this dialog instead of a new
    // one per scan — avoids leaking native ML Kit resources on repeated scans. ──
    val textRecognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    DisposableEffect(Unit) {
        onDispose { textRecognizer.close() }
    }

    fun applyLicenseScan(result: LicenseScanResult) {
        if (result.lastName.isNotBlank()) violatorLastName = result.lastName
        if (result.firstName.isNotBlank()) violatorFirstName = result.firstName
        if (result.middleName.isNotBlank()) violatorMiddleName = result.middleName
        if (result.address.isNotBlank()) violatorAddress = result.address
        if (result.licenseNumber.isNotBlank()) licenseNumber = result.licenseNumber.take(11)
    }

    val licenseScanPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                showLicenseCameraDialog = true
            } else {
                Toast.makeText(
                    context,
                    "Camera permission is required to scan a license.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    fun requestLicenseScan() {
        Toast.makeText(context, "Scan button pressed", Toast.LENGTH_SHORT).show()

        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            showLicenseCameraDialog = true
        } else {
            licenseScanPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val isPlateValid by remember(plateNumber) { derivedStateOf { plateNumber.matches(PlateRegex) } }
    val isLicenseValid by remember(licenseNumber) { derivedStateOf { licenseNumber.matches(LicenseRegex) } }
    // Manual entries (created by the enforcer, not the AI) must have a photo
    // attached — either the freshly-captured local preview, or (for an
    // already-submitted manual ticket being re-opened) a real imageUrl.
    val isManual = violation.types.contains(ViolationType.MANUAL)
    val hasRequiredEvidence = !isManual || localEvidenceUri != null || violation.imageUrl.isNotEmpty()
    val isFormValid by remember(violatorLastName, violatorFirstName, selectedOffenses, isLicenseValid, isPlateValid, hasRequiredEvidence) {
        derivedStateOf { violatorLastName.isNotBlank() && violatorFirstName.isNotBlank() && selectedOffenses.isNotEmpty() && isLicenseValid && isPlateValid && hasRequiredEvidence }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Scaffold(
            containerColor = colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(if (isAdmin && existingTicket != null) "Review Operator's Permit" else "Temporary Operator's Permit", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("No. $topNumber", style = MaterialTheme.typography.labelSmall, color = colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") } }
                )
            },
            bottomBar = {
                Surface(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
                    tonalElevation = 8.dp, shadowElevation = 8.dp, color = colorScheme.surface
                ) {
                    Box(modifier = Modifier.padding(20.dp).padding(bottom = 62.dp)) {
                        if (isAdmin && existingTicket != null) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = onVoid,
                                    colors = ButtonDefaults.buttonColors(containerColor = colorScheme.errorContainer, contentColor = colorScheme.onErrorContainer),
                                    modifier = Modifier.weight(1f).height(52.dp),
                                    shape = RoundedCornerShape(14.dp)
                                ) { Text("VOID TICKET", fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                                Button(
                                    onClick = {
                                        val items = mutableListOf<String>()
                                        if (confiscateLicense) items.add("Driver's License")
                                        if (confiscateVehicle) items.add("Motor Vehicle")
                                        onDone(TicketResult(
                                            violation = violation, topNumber = topNumber, violatorName = "$violatorLastName, $violatorFirstName $violatorMiddleName".trim(), violatorAddress = violatorAddress,
                                            licenseNumber = formatLicenseString(licenseNumber), licenseType = if (confiscateLicense) licenseType else "", plateNumber = formatPlateString(plateNumber),
                                            makeAndType = makeAndType, placeOfApprehension = placeOfApprehension, apprehensionDate = apprehensionDate, apprehensionTime = apprehensionTime, confiscatedItems = items, offenseItems = selectedOffenses.toList(),
                                            remarks = remarks, officerLastName = officerLastName, officerFirstName = officerFirstName, officerMiddleInitial = officerMI, officerId = officerId, unitStation = unitStation
                                        ))
                                    },
                                    enabled = isFormValid,
                                    colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary),
                                    modifier = Modifier.weight(1f).height(52.dp),
                                    shape = RoundedCornerShape(14.dp)
                                ) { Text("APPROVE", fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                            }
                        } else {
                            PrimaryButton(
                                text = "SUBMIT FOR APPROVAL",
                                enabled = isFormValid,
                                onClick = {
                                    val items = mutableListOf<String>()
                                    if (confiscateLicense) items.add("Driver's License")
                                    if (confiscateVehicle) items.add("Motor Vehicle")
                                    onDone(TicketResult(
                                        violation = violation, topNumber = topNumber, violatorName = "$violatorLastName, $violatorFirstName $violatorMiddleName".trim(), violatorAddress = violatorAddress,
                                        licenseNumber = formatLicenseString(licenseNumber), licenseType = if (confiscateLicense) licenseType else "", plateNumber = formatPlateString(plateNumber),
                                        makeAndType = makeAndType, placeOfApprehension = placeOfApprehension, apprehensionDate = apprehensionDate, apprehensionTime = apprehensionTime, confiscatedItems = items, offenseItems = selectedOffenses.toList(),
                                        remarks = remarks, officerLastName = officerLastName, officerFirstName = officerFirstName, officerMiddleInitial = officerMI, officerId = officerId, unitStation = unitStation
                                    ))
                                }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 20.dp).verticalScroll(rememberScrollState())
            ) {
                Spacer(Modifier.height(16.dp))

                val hasAnyEvidence = violation.imageUrl.isNotEmpty() || violation.hasVideoEvidence
                if (localEvidenceUri != null) {
                    LocalEvidencePanel(uri = localEvidenceUri, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(20.dp))
                } else if (hasAnyEvidence) {
                    EvidencePanel(violation = violation, onExpandImage = { showFullImage = true }, onExpandVideo = { showVideoPlayer = true }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(20.dp))
                } else if (isManual) {
                    // No photo at all yet — enforcer dismissed/cancelled the camera somehow.
                    Surface(
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        color = colorScheme.errorContainer.copy(alpha = 0.5f), border = BorderStroke(1.dp, colorScheme.error)
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = colorScheme.error)
                            Spacer(Modifier.width(10.dp))
                            Text("A photo is required for manual entries. Please retake the photo.", color = colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    color = colorScheme.errorContainer.copy(alpha = 0.4f), border = BorderStroke(1.dp, colorScheme.error.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = "Warning", tint = colorScheme.error)
                            Spacer(Modifier.width(8.dp))
                            Text("VIOLATOR'S DETAILS", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black), color = colorScheme.error)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("WARNING: Please enter the details of the apprehended driver below. Do NOT enter your own Enforcer details here.", style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant, lineHeight = 16.sp)
                    }
                }

                Spacer(Modifier.height(24.dp))
                if (isEditable) {
                    OutlinedButton(
                        onClick = { requestLicenseScan() },
                        enabled = !isScanningLicense,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isScanningLicense) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(if (isScanningLicense) "Scanning License..." else "Scan Driver's License", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(16.dp))
                }
                MotorSikloTextField(violatorLastName, { violatorLastName = it }, "Last Name", "Dela Cruz", isRequired = true, enabled = isEditable)
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MotorSikloTextField(violatorFirstName, { violatorFirstName = it }, "First Name", "Juan", isRequired = true, modifier = Modifier.weight(1f), enabled = isEditable)
                    MotorSikloTextField(violatorMiddleName, { violatorMiddleName = it }, "Middle Name", "Abad", modifier = Modifier.weight(1f), enabled = isEditable)
                }

                Spacer(Modifier.height(12.dp))
                MotorSikloTextField(violatorAddress, { violatorAddress = it }, "Address", "City/Municipality", enabled = isEditable)
                Spacer(Modifier.height(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)) {
                        Text("LICENSE #", style = MaterialTheme.typography.labelSmall, color = if (isEditable) colorScheme.onSurfaceVariant else colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        Text(" *", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black), color = colorScheme.error)
                    }
                    OutlinedTextField(
                        value = licenseNumber,
                        onValueChange = { input -> licenseNumber = input.replace("-", "").uppercase().take(11) },
                        modifier = Modifier.fillMaxWidth(), enabled = isEditable,
                        placeholder = { Text("L00-00-000000", color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f), fontSize = 14.sp) },
                        singleLine = true, visualTransformation = LicenseTransformation(), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colorScheme.primary, unfocusedBorderColor = colorScheme.outline.copy(alpha = 0.3f), disabledBorderColor = colorScheme.outline.copy(alpha = 0.1f), focusedTextColor = colorScheme.onSurface, unfocusedTextColor = colorScheme.onSurface, disabledTextColor = colorScheme.onSurface.copy(alpha = 0.5f), cursorColor = colorScheme.primary),
                        shape = RoundedCornerShape(12.dp)
                    )
                    AnimatedVisibility(visible = licenseNumber.isNotEmpty() && !isLicenseValid) { Text("Format must be: L00-00-000000", color = colorScheme.error, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp)) }
                }

                Spacer(Modifier.height(24.dp))
                SectionTitle("VEHICLE INFORMATION")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)) {
                            Text("PLATE NO.", style = MaterialTheme.typography.labelSmall, color = if (isEditable) colorScheme.onSurfaceVariant else colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            Text(" *", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black), color = colorScheme.error)
                        }
                        OutlinedTextField(
                            value = plateNumber,
                            onValueChange = { input -> plateNumber = input.replace(" ", "").uppercase().take(7) },
                            modifier = Modifier.fillMaxWidth(), enabled = isEditable,
                            placeholder = { Text("ABC 1234", color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f), fontSize = 14.sp) },
                            singleLine = true, visualTransformation = PlateTransformation(), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colorScheme.primary, unfocusedBorderColor = colorScheme.outline.copy(alpha = 0.3f), disabledBorderColor = colorScheme.outline.copy(alpha = 0.1f), focusedTextColor = colorScheme.onSurface, unfocusedTextColor = colorScheme.onSurface, disabledTextColor = colorScheme.onSurface.copy(alpha = 0.5f), cursorColor = colorScheme.primary),
                            shape = RoundedCornerShape(12.dp)
                        )
                        AnimatedVisibility(visible = plateNumber.isNotEmpty() && !isPlateValid) { Text("Format must be: ABC 123 or ABC 1234", color = colorScheme.error, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp)) }
                    }
                    MotorSikloTextField(makeAndType, { makeAndType = it }, "Make and Type", "HONDA Click", modifier = Modifier.weight(1f), enabled = isEditable)
                }

                Spacer(Modifier.height(24.dp))
                SectionTitle("APPREHENSION DETAILS")
                MotorSikloTextField(placeOfApprehension, { placeOfApprehension = it }, "Place of Apprehension", "Location", enabled = isEditable)
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MotorSikloTextField(apprehensionDate, { apprehensionDate = it }, "Date (MM/DD/YYYY)", "", modifier = Modifier.weight(1f), enabled = isEditable)
                    MotorSikloTextField(apprehensionTime, { apprehensionTime = it }, "Time", "", modifier = Modifier.weight(1f), enabled = isEditable)
                }

                Spacer(Modifier.height(24.dp))
                SectionTitle("CONFISCATED ITEMS")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = confiscateLicense, onCheckedChange = if (isEditable) { { confiscateLicense = it } } else null, enabled = isEditable)
                    Text("Driver's License", style = MaterialTheme.typography.bodyMedium, color = if (isEditable) colorScheme.onSurface else colorScheme.onSurface.copy(alpha = 0.5f))
                    Spacer(Modifier.width(24.dp))
                    Checkbox(checked = confiscateVehicle, onCheckedChange = if (isEditable) { { confiscateVehicle = it } } else null, enabled = isEditable)
                    Text("Motor Vehicle", style = MaterialTheme.typography.bodyMedium, color = if (isEditable) colorScheme.onSurface else colorScheme.onSurface.copy(alpha = 0.5f))
                }

                if (confiscateLicense) {
                    Spacer(Modifier.height(12.dp))
                    Column {
                        Text("LICENSE TYPE", style = MaterialTheme.typography.labelSmall, color = colorScheme.primary, fontWeight = FontWeight.Bold)
                        var expanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.padding(top = 8.dp)) {
                            OutlinedCard(onClick = { if (isEditable) expanded = true }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, colorScheme.outline.copy(alpha = if (isEditable) 0.3f else 0.1f))) {
                                Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(licenseType, fontSize = 14.sp, color = if (isEditable) colorScheme.onSurface else colorScheme.onSurface.copy(alpha = 0.5f))
                                    if (isEditable) Icon(Icons.Default.ArrowDropDown, null)
                                }
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                listOf("Pro", "Non-Pro", "Student License").forEach { DropdownMenuItem(text = { Text(it) }, onClick = { licenseType = it; expanded = false }) }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                SectionTitle("OFFENSE & REMARKS")

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                    Switch(
                        checked = isMultipleViolations,
                        onCheckedChange = if (isEditable) { {
                            isMultipleViolations = it
                            if (!it && selectedOffenses.size > 1) { selectedOffenses = setOf(selectedOffenses.first()) }
                        } } else null,
                        enabled = isEditable,
                        colors = SwitchDefaults.colors(checkedThumbColor = colorScheme.primary, checkedTrackColor = colorScheme.primary.copy(alpha = 0.3f))
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Multiple Violations", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = if (isEditable) colorScheme.onSurface else colorScheme.onSurface.copy(alpha = 0.5f))
                }

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("SELECT OFFENSES", style = MaterialTheme.typography.labelSmall, color = colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text(" *", style = MaterialTheme.typography.labelSmall, color = colorScheme.error)
                    }
                    var expanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.padding(top = 8.dp)) {
                        OutlinedCard(onClick = { if (isEditable) expanded = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, colorScheme.outline.copy(alpha = if (isEditable) 0.3f else 0.1f))) {
                            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(if (selectedOffenses.isEmpty()) "Select Violations" else selectedOffenses.joinToString(", "), fontSize = 14.sp, modifier = Modifier.weight(1f), color = if (isEditable) colorScheme.onSurface else colorScheme.onSurface.copy(alpha = 0.5f))
                                if (isEditable) Icon(Icons.Default.ArrowDropDown, null)
                            }
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            offenseOptions.forEach { offense ->
                                DropdownMenuItem(
                                    text = { Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = offense in selectedOffenses, onCheckedChange = null); Spacer(Modifier.width(8.dp)); Text(offense) } },
                                    onClick = {
                                        if (isMultipleViolations) {
                                            selectedOffenses = if (offense in selectedOffenses) selectedOffenses - offense else selectedOffenses + offense
                                        } else {
                                            selectedOffenses = setOf(offense)
                                            expanded = false
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // ── NEW: Law Hyperlinks Feature ──
                if (selectedOffenses.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    SectionTitle("LEGAL REFERENCES")
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = colorScheme.primary.copy(alpha = 0.05f),
                        border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.15f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Tap to view full Republic Act guidelines:", style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            selectedOffenses.forEach { offense ->
                                val ref = legalRefs[offense]
                                if (ref != null) {
                                    Text(
                                        text = "• ${ref.first}",
                                        color = colorScheme.primary,
                                        style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Bold),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { uriHandler.openUri(ref.second) }
                                            .padding(vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                MotorSikloTextField(remarks, { remarks = it }, "Additional Remarks", "Describe the incident...", enabled = isEditable)

                Spacer(Modifier.height(24.dp))
                SectionTitle("APPREHENDING OFFICER")
                MotorSikloTextField(officerLastName, {}, "Last Name", "", enabled = false)
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MotorSikloTextField(officerFirstName, {}, "First Name", "", modifier = Modifier.weight(1f), enabled = false)
                    MotorSikloTextField(officerMI, {}, "M.I.", "", modifier = Modifier.weight(0.4f), enabled = false)
                }
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MotorSikloTextField(officerId, {}, "Dep ID No.", "", modifier = Modifier.weight(1f), enabled = false)
                    MotorSikloTextField(unitStation, {}, "Unit/Station", "", modifier = Modifier.weight(1f), enabled = false)
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }

    if (showFullImage) { ZoomableImageDialog(imageUrl = violation.imageUrl, onDismiss = { showFullImage = false }) }
    if (showVideoPlayer && violation.videoUrl.isNotEmpty()) { VideoPlayerDialog(videoUrl = violation.videoUrl, onDismiss = { showVideoPlayer = false }) }
    if (showLicenseCameraDialog) {
        LicenseCameraDialog(
            onDismiss = {
                showLicenseCameraDialog = false
            },
            onPhotoCaptured = { file, uri ->
                showLicenseCameraDialog = false
                isScanningLicense = true

                val croppedBitmap = cropToLicenseFrame(context, uri)
                val image = if (croppedBitmap != null) {
                    InputImage.fromBitmap(croppedBitmap, 0)
                } else {
                    InputImage.fromFilePath(context, uri)
                }

                textRecognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        val parsed = parseLicenseText(visionText.text)
                        applyLicenseScan(parsed)

                        val foundAny = listOf(
                            parsed.lastName,
                            parsed.firstName,
                            parsed.middleName,
                            parsed.address,
                            parsed.licenseNumber
                        ).any { it.isNotBlank() }

                        Toast.makeText(
                            context,
                            if (foundAny) "License details scanned." else "No license details detected.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(
                            context,
                            "License scan failed: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .addOnCompleteListener {
                        isScanningLicense = false
                        file.delete()
                    }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// EvidenceViewerDialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun EvidenceViewerDialog(violation: ViolationEntry, onDismiss: () -> Unit) {
    var showFullImage by remember { mutableStateOf(false) }
    var showVideoPlayer by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxWidth(0.9f).clip(RoundedCornerShape(16.dp)), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Raw Detection Evidence", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))

                val hasAnyEvidence = violation.imageUrl.isNotEmpty() || violation.hasVideoEvidence
                if (hasAnyEvidence) {
                    EvidencePanel(violation = violation, onExpandImage = { showFullImage = true }, onExpandVideo = { showVideoPlayer = true }, modifier = Modifier.fillMaxWidth())
                } else {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                        Text("No evidence asset synchronized", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(Modifier.height(24.dp))
                PrimaryButton(text = "Close Viewer", onClick = onDismiss)
            }
        }
    }

    if (showFullImage && violation.imageUrl.isNotEmpty()) { ZoomableImageDialog(imageUrl = violation.imageUrl, onDismiss = { showFullImage = false }) }
    if (showVideoPlayer && violation.videoUrl.isNotEmpty()) { VideoPlayerDialog(videoUrl = violation.videoUrl, onDismiss = { showVideoPlayer = false }) }
}

// ─────────────────────────────────────────────────────────────────────────────
// ZoomableImageDialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ZoomableImageDialog(imageUrl: String, onDismiss: () -> Unit) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val context = LocalContext.current
    val imageReq = remember(imageUrl) { ImageRequest.Builder(context).data(imageUrl).crossfade(true).build() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true)) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.9f)).pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    if (scale > 1f) offset += pan else offset = Offset.Zero
                }
            },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(model = imageReq, contentDescription = "Zoomable Evidence", modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y), contentScale = ContentScale.Fit)
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)) {
                Icon(Icons.Default.Close, "Close", tint = Color.White)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ReceiptDialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ReceiptDialog(result: TicketResult, onClose: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    var showFullImage by remember { mutableStateOf(false) }
    var showVideoPlayer by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f)).padding(24.dp), contentAlignment = Alignment.Center) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)).background(colorScheme.surface)) {
                    val violation = result.violation
                    val hasAnyEvidence = violation.imageUrl.isNotEmpty() || violation.hasVideoEvidence

                    if (hasAnyEvidence) {
                        Box(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
                            EvidencePanel(violation = violation, onExpandImage = { showFullImage = true }, onExpandVideo = { showVideoPlayer = true }, modifier = Modifier.fillMaxWidth())
                        }
                    }

                    Column(modifier = Modifier.padding(24.dp)) {
                        Text("TEMPORARY OPERATOR'S PERMIT", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black), color = colorScheme.onSurface)
                        Text("TOP No. ${result.topNumber}", style = MaterialTheme.typography.labelMedium, color = colorScheme.primary, fontWeight = FontWeight.Bold)

                        Spacer(Modifier.height(24.dp))

                        ReceiptRow("Ticket ID", "#${result.violation.id}")
                        ReceiptRow("Violator", result.violatorName)
                        ReceiptRow("Plate #", result.plateNumber)
                        ReceiptRow("Make", result.makeAndType)
                        ReceiptRow("Offense(s)", result.offenseItems.joinToString(", "))
                        ReceiptRow("Location", result.placeOfApprehension)
                        ReceiptRow("Date/Time", "${result.apprehensionDate} ${result.apprehensionTime}")
                        ReceiptRow("Officer", result.officerFullName)

                        if (result.confiscatedItems.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            ReceiptRow("Confiscated", result.confiscatedItems.joinToString(", "))
                            if (result.licenseType.isNotEmpty()) ReceiptRow("License Type", result.licenseType)
                        }
                    }
                    ReceiptZigZagBottom()
                }
                Spacer(Modifier.height(24.dp))
                PrimaryButton(text = "Close Receipt", onClick = onClose)
                Spacer(modifier = Modifier.height(62.dp))
            }
        }
    }

    if (showFullImage && result.violation.imageUrl.isNotEmpty()) { ZoomableImageDialog(imageUrl = result.violation.imageUrl, onDismiss = { showFullImage = false }) }
    if (showVideoPlayer && result.violation.videoUrl.isNotEmpty()) { VideoPlayerDialog(videoUrl = result.violation.videoUrl, onDismiss = { showVideoPlayer = false }) }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp), color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 12.dp))
}

@Composable
private fun ReceiptZigZagBottom() {
    val bgColor = MaterialTheme.colorScheme.surface
    Canvas(modifier = Modifier.fillMaxWidth().height(12.dp)) {
        val path = Path()
        val segmentWidth = 15.dp.toPx()
        val count = (size.width / segmentWidth).toInt() + 1

        path.moveTo(0f, 0f)
        for (i in 0 until count) {
            val nextX = (i + 1) * segmentWidth
            val midX = nextX - (segmentWidth / 2)
            path.lineTo(midX, size.height)
            path.lineTo(nextX, 0f)
        }
        path.lineTo(size.width, size.height)
        path.lineTo(0f, size.height)
        path.close()

        drawPath(path = path, color = bgColor)
    }
}

@Composable
private fun ReceiptRow(label: String, value: String) {
    val colorScheme = MaterialTheme.colorScheme
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(text = label, color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 12.sp, modifier = Modifier.weight(0.4f))
        Text(text = value, color = colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = TextAlign.End, modifier = Modifier.weight(0.6f))
    }
}

fun formatLicenseString(raw: String): String {
    val clean = raw.uppercase().take(11)
    val out = StringBuilder()
    for (i in clean.indices) {
        out.append(clean[i])
        if ((i == 2 || i == 4) && i != clean.lastIndex) out.append("-")
    }
    return out.toString()
}

fun formatPlateString(raw: String): String {
    val clean = raw.uppercase().take(7)
    val out = StringBuilder()
    for (i in clean.indices) {
        out.append(clean[i])
        if (i == 2 && i != clean.lastIndex) out.append(" ")
    }
    return out.toString()
}

class LicenseTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val clean = text.text
        var out = ""
        for (i in clean.indices) {
            out += clean[i]
            if ((i == 2 || i == 4) && i != clean.lastIndex) out += "-"
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 3) return offset
                if (offset <= 5) return offset + 1
                return offset + 2
            }
            override fun transformedToOriginal(offset: Int): Int {
                if (offset <= 3) return offset
                if (offset <= 6) return offset - 1
                return offset - 2
            }
        }
        return TransformedText(AnnotatedString(out), mapping)
    }
}

class PlateTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val clean = text.text
        var out = ""
        for (i in clean.indices) {
            out += clean[i]
            if (i == 2 && i != clean.lastIndex) out += " "
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 3) return offset
                return offset + 1
            }
            override fun transformedToOriginal(offset: Int): Int {
                if (offset <= 3) return offset
                return offset - 1
            }
        }
        return TransformedText(AnnotatedString(out), mapping)
    }
}