package com.motorsiklo.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.motorsiklo.app.FirebaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Creates a fresh content:// Uri (via FileProvider) pointing at a new, empty
 *  JPEG file in the app's cache dir, ready for TakePicture() to write into. */
private fun createCameraOutputUri(context: Context): Uri {
    val file = File.createTempFile("manual_evidence_", ".jpg", context.cacheDir)
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

@Composable
fun ViolationsScreen(role: String, username: String) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }

    var selectedTab by remember { mutableStateOf(0) } // 0 = Detected, 1 = Pending, 2 = Completed
    var highlightedType by remember { mutableStateOf<ViolationType?>(null) }

    var selectedViolation by remember { mutableStateOf<ViolationEntry?>(null) }
    var viewedViolation   by remember { mutableStateOf<ViolationEntry?>(null) }
    var fetchedTicketForApproval by remember { mutableStateOf<TicketResult?>(null) }
    var ticketResult      by remember { mutableStateOf<TicketResult?>(null) }
    var isLoadingReceipt  by remember { mutableStateOf(false) }

    // ── Manual entry evidence capture ──
    // Holds the in-progress camera output Uri while the system camera app is open,
    // and the confirmed local photo once the enforcer accepts it.
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var manualEvidenceUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            manualEvidenceUri = pendingCameraUri
            val currentTime = System.currentTimeMillis()
            selectedViolation = ViolationEntry(id = "MAN-${currentTime.toString().takeLast(6)}", types = listOf(ViolationType.MANUAL), timestamp = currentTime, location = "Manual Apprehension", status = "Detected")
        } else {
            pendingCameraUri = null
            Toast.makeText(context, "Photo capture cancelled. A photo is required for manual entries.", Toast.LENGTH_SHORT).show()
        }
    }

    // Launches the camera once CAMERA permission is confirmed granted.
    fun launchCameraForManualEntry() {
        val uri = createCameraOutputUri(context)
        pendingCameraUri = uri
        cameraLauncher.launch(uri)
    }

    // Requests runtime CAMERA permission, then launches the camera on grant.
    // Without this, calling cameraLauncher.launch() while the permission is
    // not yet granted throws a SecurityException and crashes the app.
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            launchCameraForManualEntry()
        } else {
            Toast.makeText(context, "Camera permission is required to create a manual ticket.", Toast.LENGTH_LONG).show()
        }
    }

    fun requestCameraThenLaunch() {
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            launchCameraForManualEntry()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val colorScheme = MaterialTheme.colorScheme
    val isEnforcer  = role == "Traffic Enforcer"
    val isAdmin     = role == "Administrator"

    var displayList by remember { mutableStateOf<List<ViolationEntry>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var pullDistance by remember { mutableFloatStateOf(0f) }
    val listState = rememberLazyListState()

    fun refreshLatestViolations() {
        if (isRefreshing) return
        isRefreshing = true
        FirebaseManager.refreshViolations {
            isRefreshing = false
            pullDistance = 0f
            Toast.makeText(context, "Latest violations refreshed.", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(searchQuery, highlightedType, selectedTab, ViolationRepository.violations.size) {
        isSearching = true
        withContext(Dispatchers.Default) {
            val safeList = ViolationRepository.violations.toList()

            val filtered = safeList.filter { entry ->
                val matchesSearch = searchQuery.isBlank() ||
                        entry.types.any { type -> type.label.contains(searchQuery, ignoreCase = true) } ||
                        entry.location.contains(searchQuery, ignoreCase = true)
                val matchesType = highlightedType == null || entry.types.contains(highlightedType)
                matchesSearch && matchesType
            }

            val finalDisplay = when (selectedTab) {
                0 -> filtered.filter { it.status.equals("Detected", ignoreCase = true) }
                1 -> filtered.filter { it.status.equals("Pending", ignoreCase = true) || it.status.equals("Awaiting_Approval", ignoreCase = true) }
                else -> filtered.filter { it.status.equals("Completed", ignoreCase = true) || it.status.equals("Voided", ignoreCase = true) }
            }

            withContext(Dispatchers.Main) {
                displayList = finalDisplay
                isSearching = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(isRefreshing) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        val isAtTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                        if (!isRefreshing && isAtTop && dragAmount > 0) {
                            pullDistance = (pullDistance + dragAmount).coerceIn(0f, 180f)
                            change.consume()
                        }
                    },
                    onDragEnd = {
                        if (pullDistance > 110f) refreshLatestViolations() else pullDistance = 0f
                    },
                    onDragCancel = { pullDistance = 0f }
                )
            }
    ) {

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 100.dp)
        ) {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Violations Repository", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black), color = colorScheme.onBackground)
                    Text("Detected motorcycle traffic incidents", style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
                }

                Spacer(Modifier.height(28.dp))
                OutlinedTextField(
                    value = searchQuery, onValueChange = { searchQuery = it }, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search by location or type...", color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = colorScheme.onSurfaceVariant) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colorScheme.primary, unfocusedBorderColor = colorScheme.outline.copy(alpha = 0.3f), focusedContainerColor = colorScheme.surface, unfocusedContainerColor = colorScheme.surface, focusedTextColor = colorScheme.onSurface, unfocusedTextColor = colorScheme.onSurface),
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(Modifier.height(20.dp))

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = colorScheme.primary,
                    divider = { HorizontalDivider(color = colorScheme.outline.copy(alpha = 0.1f)) }
                ) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Detected", fontWeight = FontWeight.Bold) })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Pending", fontWeight = FontWeight.Bold) })
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Completed", fontWeight = FontWeight.Bold) })
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ViolationType.entries.forEach { type ->
                        val isSelected = highlightedType == type
                        val typeColor = getViolationColor(type)
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { highlightedType = if (isSelected) null else type },
                            color = if (isSelected) typeColor.copy(alpha = 0.15f) else colorScheme.surface,
                            border = BorderStroke(1.dp, if (isSelected) typeColor else colorScheme.outline.copy(alpha = 0.1f))
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(typeColor))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = type.label,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium),
                                    color = if (isSelected) typeColor else colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
            }

            if (ViolationRepository.violations.isEmpty()) {
                item {
                    if (!isDataFullySynced.value) {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = colorScheme.primary)
                            Spacer(Modifier.height(16.dp))
                            Text("Waiting for data sync...", color = colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        EmptyStateView("No violations detected yet", "The AI system is active but hasn't logged any incidents.", Modifier.padding(top = 40.dp))
                    }
                }
            } else {
                if (isSearching) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = colorScheme.primary, modifier = Modifier.size(24.dp))
                        }
                    }
                } else if (displayList.isEmpty()) {
                    item {
                        val stateTxt = when (selectedTab) { 0 -> "detected"; 1 -> "pending"; else -> "completed" }
                        Text("No $stateTxt incidents match your criteria.", color = colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                    }
                } else {
                    items(displayList, key = { it.id }) { entry ->
                        ViolationCard(entry = entry, onClick = {
                            val status = entry.status
                            val isExpired = status.equals("Detected", ignoreCase = true) && ViolationRepository.isExpiredFromUi(entry)

                            if (status.equals("Detected", ignoreCase = true)) {
                                if (isExpired) {
                                    if (isEnforcer) Toast.makeText(context, "This detection has expired and can no longer be ticketed.", Toast.LENGTH_SHORT).show()
                                    else viewedViolation = entry
                                } else {
                                    if (isEnforcer) selectedViolation = entry else viewedViolation = entry
                                }
                            }
                            else if (status.equals("Pending", ignoreCase = true) || status.equals("Awaiting_Approval", ignoreCase = true)) {
                                isLoadingReceipt = true
                                FirebaseManager.fetchTicketResult(entry.id) { result ->
                                    isLoadingReceipt = false
                                    if (result != null) {
                                        if (isAdmin) fetchedTicketForApproval = result else ticketResult = result
                                    } else Toast.makeText(context, "Cannot retrieve ticket data.", Toast.LENGTH_SHORT).show()
                                }
                            }
                            else { // Completed or Voided
                                isLoadingReceipt = true
                                FirebaseManager.fetchTicketResult(entry.id) { result ->
                                    isLoadingReceipt = false
                                    if (result != null) ticketResult = result
                                    else if (status.equals("Voided", ignoreCase = true)) Toast.makeText(context, "This incident was voided by an Administrator.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        })
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }

        if (isEnforcer) {
            Box(modifier = Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.BottomCenter) {
                Button(
                    onClick = {
                        // Manual entries require a photo first. requestCameraThenLaunch()
                        // checks CAMERA permission, requests it if needed, and only
                        // launches the camera once granted — avoids the SecurityException
                        // crash that happens if the permission isn't granted yet.
                        requestCameraThenLaunch()
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp), elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("CREATE A TICKET", fontSize = 15.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }
            }
        }

        if (isRefreshing || pullDistance > 0f) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp),
                shape = RoundedCornerShape(50),
                color = colorScheme.surface,
                border = BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.12f)),
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = colorScheme.primary
                        )
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = colorScheme.primary,
                            progress = { (pullDistance / 110f).coerceIn(0f, 1f) }
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(if (isRefreshing) "Refreshing..." else "Pull to refresh", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colorScheme.onSurface)
                }
            }
        }

        fetchedTicketForApproval?.let { result ->
            DigitalTicketDialog(
                violation = result.violation,
                enforcerName = username,
                role = role,
                existingTicket = result,
                onDismiss = { fetchedTicketForApproval = null },
                onVoid = {
                    FirebaseManager.voidViolation(result.violation.id)
                    ViolationRepository.updateStatus(result.violation.id, "Voided")
                    fetchedTicketForApproval = null
                    Toast.makeText(context, "Incident voided & removed from active records.", Toast.LENGTH_SHORT).show()
                },
                onDone = { updatedResult ->
                    FirebaseManager.saveTicketResult(updatedResult, isEnforcerSubmission = false)
                    ViolationRepository.updateStatus(updatedResult.violation.id, "Completed")
                    ticketResult = updatedResult
                    fetchedTicketForApproval = null
                    Toast.makeText(context, "Ticket formally approved & archived.", Toast.LENGTH_SHORT).show()
                }
            )
        }

        selectedViolation?.let { violation ->
            DigitalTicketDialog(
                violation = violation,
                enforcerName = username,
                role = role,
                existingTicket = null,
                localEvidenceUri = if (violation.types.contains(ViolationType.MANUAL)) manualEvidenceUri else null,
                onDismiss = {
                    selectedViolation = null
                    manualEvidenceUri = null
                    pendingCameraUri = null
                },
                onDone = onDone@{ result ->
                    if (violation.types.contains(ViolationType.MANUAL)) {
                        val photoUri = manualEvidenceUri
                        if (photoUri == null) {
                            Toast.makeText(context, "A photo is required for manual entries.", Toast.LENGTH_SHORT).show()
                            return@onDone
                        }
                        // Upload the photo first, then write the violation (with its
                        // real imageUrl attached) and the ticket together.
                        FirebaseManager.uploadManualEvidence(photoUri, violation.id) { uploadedUrl ->
                            if (uploadedUrl == null) {
                                Toast.makeText(context, "Evidence upload failed. Please try again.", Toast.LENGTH_SHORT).show()
                                return@uploadManualEvidence
                            }
                            val violationWithEvidence = violation.copy(imageUrl = uploadedUrl)
                            FirebaseManager.addViolation(violationWithEvidence) {
                                FirebaseManager.saveTicketResult(result.copy(violation = violationWithEvidence), isEnforcerSubmission = true)
                            }
                            if (!ViolationRepository.violations.any { it.id == violation.id }) ViolationRepository.addViolation(violationWithEvidence)
                            ViolationRepository.updateStatus(violation.id, "Pending")
                            selectedViolation = null
                            manualEvidenceUri = null
                            pendingCameraUri = null
                            Toast.makeText(context, "Ticket submitted to Admin for verification.", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        FirebaseManager.saveTicketResult(result, isEnforcerSubmission = true)
                        if (!ViolationRepository.violations.any { it.id == violation.id }) ViolationRepository.addViolation(violation)
                        ViolationRepository.updateStatus(violation.id, "Pending")
                        selectedViolation = null
                        Toast.makeText(context, "Ticket submitted to Admin for verification.", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        viewedViolation?.let { violation -> EvidenceViewerDialog(violation = violation, onDismiss = { viewedViolation = null }) }
        ticketResult?.let { result -> ReceiptDialog(result = result, onClose = { ticketResult = null }) }

        if (isLoadingReceipt) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colorScheme.primary)
            }
        }
    }
}
