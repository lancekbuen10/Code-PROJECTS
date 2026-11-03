@file:OptIn(ExperimentalMaterial3Api::class)
package com.motorsiklo.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motorsiklo.app.FirebaseManager

@Composable
fun AdminRequestsScreen(onBack: () -> Unit) {
    var selectedTab by remember { mutableStateOf(0) }
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Administrative Actions", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.background,
                    titleContentColor = colorScheme.onBackground
                )
            )
        },
        containerColor = colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = colorScheme.primary,
                divider = { HorizontalDivider(color = colorScheme.outline.copy(alpha = 0.1f)) }
            ) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Registrations", fontWeight = FontWeight.Bold) })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Resets", fontWeight = FontWeight.Bold) })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Passwords", fontWeight = FontWeight.Bold) })
            }

            Spacer(Modifier.height(16.dp))

            when (selectedTab) {
                0 -> RegistrationTab(context)
                1 -> ResetTab(context)
                2 -> ChangePasswordTab(context)
            }
        }
    }
}

@Composable
fun RegistrationTab(context: android.content.Context) {
    val pending = registrationRequests.filter { it.status == "Pending" }

    if (pending.isEmpty()) {
        EmptyStateView(
            title = "No Pending Requests",
            description = "There are currently no registration requests from enforcers."
        )
    } else {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Added key = { it.id } for Optimistic UI fast deletion
            items(pending, key = { it.id }) { req ->
                RequestCard(
                    title = req.fullName,
                    tag = "Registration",
                    details = listOf("Username: ${req.username}", "Badge No: ${req.badgeNumber}", "Station: ${req.station}"),
                    onApprove = {
                        FirebaseManager.approveRegistrationRequest(context, req) { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onReject = {
                        FirebaseManager.rejectRegistrationRequest(req)
                        Toast.makeText(context, "Registration rejected.", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

@Composable
fun ResetTab(context: android.content.Context) {
    val pending = resetRequests.filter { it.status == "Pending" }

    if (pending.isEmpty()) {
        EmptyStateView(
            title = "No Pending Resets",
            description = "There are currently no account recovery requests."
        )
    } else {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Added key = { it.id } for Optimistic UI fast deletion
            items(pending, key = { it.id }) { req ->
                RequestCard(
                    title = req.username,
                    tag = "Account Reset",
                    details = listOf("Badge No: ${req.badgeNumber}", "Timestamp: ${formatTimestamp(req.timestamp)}"),
                    onApprove = {
                        FirebaseManager.approveResetRequest(req)
                        // Updated Toast to inform Admin of Backend processing
                        Toast.makeText(context, "Approved. Backend is processing the new password.", Toast.LENGTH_LONG).show()
                    },
                    onReject = {
                        FirebaseManager.denyResetRequest(req)
                        Toast.makeText(context, "Account reset denied.", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

@Composable
fun ChangePasswordTab(context: android.content.Context) {
    val pending = changePasswordRequests.filter { it.status == "Pending" }

    if (pending.isEmpty()) {
        EmptyStateView(
            title = "No Pending Changes",
            description = "There are currently no password change requests."
        )
    } else {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Added key = { it.id } for Optimistic UI fast deletion
            items(pending, key = { it.id }) { req ->
                RequestCard(
                    title = req.username,
                    tag = "Password Change",
                    details = listOf("Badge No: ${req.badgeNumber}", "Timestamp: ${formatTimestamp(req.timestamp)}"),
                    onApprove = {
                        FirebaseManager.approveChangePasswordRequest(req)
                        // Updated Toast to inform Admin of Backend processing
                        Toast.makeText(context, "Approved. Backend is syncing the new password.", Toast.LENGTH_LONG).show()
                    },
                    onReject = {
                        FirebaseManager.denyChangePasswordRequest(req)
                        Toast.makeText(context, "Password change denied.", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

@Composable
fun RequestCard(title: String, tag: String, details: List<String>, onApprove: () -> Unit, onReject: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colorScheme.surface,
        border = BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.1f)),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = colorScheme.onSurface)
                Surface(color = colorScheme.primary.copy(alpha = 0.1f), shape = RoundedCornerShape(6.dp)) {
                    Text(tag, color = colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            details.forEach { detail ->
                Text(detail, fontSize = 13.sp, color = colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
            }
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onReject) {
                    Text("REJECT", color = colorScheme.error, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = onApprove,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary)
                ) {
                    Text("APPROVE", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}