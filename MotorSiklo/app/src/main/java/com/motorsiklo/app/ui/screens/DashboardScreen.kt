package com.motorsiklo.app.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motorsiklo.app.FirebaseManager
import com.motorsiklo.app.ui.theme.*

@Composable
fun DashboardScreen(role: String, username: String, onLogout: () -> Unit = {}, onNavigateToResets: () -> Unit = {}) {
    val scroll      = rememberScrollState()
    val colorScheme = MaterialTheme.colorScheme
    val context     = LocalContext.current

    var showMenu          by remember { mutableStateOf(false) }
    var showLogoutDialog  by remember { mutableStateOf(false) }
    var selectedViolation by remember { mutableStateOf<ViolationEntry?>(null) }
    var ticketResult      by remember { mutableStateOf<TicketResult?>(null) }
    var isLoadingReceipt  by remember { mutableStateOf(false) }

    val violationsList    = ViolationRepository.violations

    // ── FIX 5: Live Feed specifically pulls "Detected" AI Detections ──
    val detectedViolations by remember(violationsList.size) {
        derivedStateOf {
            violationsList.filter { it.status.equals("Detected", ignoreCase = true) }.take(5)
        }
    }

    val userProfile = currentUserProfile.value
    val userBadge = userProfile?.badgeNumber ?: ""

    val rawLastName = userProfile?.lastName?.trim() ?: ""
    val rawFirstName = userProfile?.firstName?.trim() ?: ""
    val rawMiddleName = userProfile?.middleName?.trim() ?: ""

    val fInitial = if (rawFirstName.isNotBlank()) "${rawFirstName.take(1).uppercase()}." else ""
    val mInitial = if (rawMiddleName.isNotBlank()) "${rawMiddleName.take(1).uppercase()}." else ""

    val initials = listOf(fInitial, mInitial).filter { it.isNotBlank() }.joinToString(" ")

    val displayName = if (rawLastName.isNotBlank()) {
        if (initials.isNotBlank()) "$rawLastName, $initials" else rawLastName
    } else {
        username
    }

    val myLoggedCount = remember(violationsList.size, userBadge) {
        violationsList.count {
            (it.status == "Completed" || it.status == "Awaiting_Approval") && it.badgeNumber == userBadge
        }
    }

    val isIdle = FirebaseManager.isLocalDeviceIdle.value
    val statusText = if (isIdle) "Idle" else "On Duty"
    val statusColor = if (isIdle) Color(0xFFFFB74D) else Color(0xFF4CAF50)
    val statusIcon = if (isIdle) Icons.Default.AccessTime else Icons.Default.RadioButtonChecked

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Field Operations", style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
                    Text(displayName, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black), color = colorScheme.onSurface)
                    Text(role.uppercase(), style = MaterialTheme.typography.labelSmall.copy(color = colorScheme.primary, fontWeight = FontWeight.Bold))
                }

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
            Text("DETECTED INCIDENTS", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))

            if (detectedViolations.isEmpty()) {
                Surface(modifier = Modifier.fillMaxWidth().height(180.dp), shape = RoundedCornerShape(16.dp), color = colorScheme.surface, border = BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.1f)), tonalElevation = 1.dp) {
                    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        if (!isDataFullySynced.value) {
                            CircularProgressIndicator(color = colorScheme.primary)
                            Spacer(Modifier.height(16.dp))
                            Text("Syncing live camera feed...", color = colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        } else {
                            Icon(Icons.Default.PhotoLibrary, null, tint = colorScheme.onSurfaceVariant.copy(alpha = 0.3f), modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("No detected incidents", color = colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        }
                    }
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 20.dp)) {
                    items(detectedViolations) { violation ->
                        EvidenceCard(violation = violation, onClick = {
                            val isExpired = ViolationRepository.isExpiredFromUi(violation)

                            if (isExpired) {
                                Toast.makeText(context, "This detection has expired and can no longer be ticketed.", Toast.LENGTH_LONG).show()
                            } else if (violation.status == "Detected") {
                                selectedViolation = violation
                            } else {
                                isLoadingReceipt = true
                                FirebaseManager.fetchTicketResult(violation.id) { result ->
                                    if (result != null) ticketResult = result
                                    else Toast.makeText(context, "Cannot retrieve historic mock ticket parsing data.", Toast.LENGTH_SHORT).show()
                                    isLoadingReceipt = false
                                }
                            }
                        })
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
            Text("OPERATIONAL STATUS", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryCard(Modifier.weight(1f).fillMaxHeight(), "My Logged Violations", "$myLoggedCount", Icons.Default.Assignment, colorScheme.primary)
                SummaryCard(Modifier.weight(1f).fillMaxHeight(), "Active Status", statusText, statusIcon, statusColor)
            }

            Spacer(Modifier.height(32.dp))
            Text("TODAY'S SUMMARY", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {

                // This tracks tickets actually waiting for admin approval
                val pendingCount by remember(violationsList.size) {
                    derivedStateOf { violationsList.count { it.status.equals("Pending", ignoreCase = true) || it.status.equals("Awaiting_Approval", ignoreCase = true) } }
                }
                val completedCount by remember(violationsList.size) {
                    derivedStateOf { violationsList.count { it.status == "Completed" } }
                }

                StatCard(title = "Pending", value = pendingCount.toString(), icon = Icons.Default.HourglassEmpty, color = colorScheme.primary, modifier = Modifier.weight(1f))
                StatCard(title = "Processed", value = completedCount.toString(), icon = Icons.Default.CheckCircle, color = Color(0xFF4CAF50), modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(100.dp))
        }

        if (isLoadingReceipt) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = colorScheme.primary) }
        }

        if (forceLogoutEvent.value) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
                AlertDialog(
                    onDismissRequest = { /* Cannot be dismissed */ },
                    containerColor = colorScheme.surface,
                    title = { Text("Session Expired", fontWeight = FontWeight.Bold, color = colorScheme.error) },
                    text = { Text("Your account was just signed in on another device. For security reasons, you have been automatically logged out of this session.", color = colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) },
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

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false }, containerColor = colorScheme.surface,
            title = { Text("Sign Out", fontWeight = FontWeight.Bold, color = colorScheme.onSurface) },
            text = { Text("Are you sure you want to log out of your account?", color = colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { TextButton(onClick = { showLogoutDialog = false; onLogout() }) { Text("Sign Out", color = colorScheme.error, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel", color = colorScheme.onSurfaceVariant) } }
        )
    }

    selectedViolation?.let { violation ->
        DigitalTicketDialog(
            violation = violation,
            enforcerName = username,
            role = role,
            existingTicket = null,
            onDismiss = { selectedViolation = null },
            onDone = { result ->

                val claimedViolation = violation.copy(badgeNumber = userBadge)

                if (claimedViolation.types.contains(ViolationType.MANUAL)) {
                    FirebaseManager.addViolation(claimedViolation) { FirebaseManager.saveTicketResult(result, isEnforcerSubmission = true) }
                } else {
                    FirebaseManager.saveTicketResult(result, isEnforcerSubmission = true)
                }

                val index = ViolationRepository.violations.indexOfFirst { it.id == claimedViolation.id }
                if (index != -1) {
                    ViolationRepository.violations[index] = claimedViolation
                } else {
                    ViolationRepository.addViolation(claimedViolation)
                }

                ViolationRepository.updateStatus(claimedViolation.id, "Awaiting_Approval")
                selectedViolation = null
                Toast.makeText(context, "Ticket submitted to Admin for approval.", Toast.LENGTH_SHORT).show()
            }
        )
    }

    ticketResult?.let { result -> ReceiptDialog(result = result, onClose = { ticketResult = null }) }
}