package com.motorsiklo.app.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motorsiklo.app.FirebaseManager
import com.motorsiklo.app.ui.theme.*

@Composable
fun LoginScreen(
    role: String,
    onLoginSuccess: (String) -> Unit,
    onBackClick: () -> Unit
) {
    var username        by remember { mutableStateOf("") }
    var password        by remember { mutableStateOf("") }
    var showResetDialog by remember { mutableStateOf(false) }
    var errorMessage    by remember { mutableStateOf("") }
    var isLoading       by remember { mutableStateOf(false) }

    val context     = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val isEnforcer  = role == "Traffic Enforcer"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        MotorSikloCard {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // ── Header ──────────────────────────────────────────────────────
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        if (isEnforcer) BadgeIcon(size = 42f) else AdminIcon(size = 42f)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            text  = role.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = colorScheme.primary
                        )
                        Text(
                            text  = "Sign In",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                            color = colorScheme.onSurface
                        )
                    }
                }

                Spacer(Modifier.height(28.dp))

                MotorSikloTextField(
                    value         = username,
                    onValueChange = { username = it; errorMessage = "" },
                    label         = "Username",
                    placeholder   = "Enter your username",
                    enabled       = !isLoading
                )
                Spacer(Modifier.height(14.dp))
                MotorSikloTextField(
                    value         = password,
                    onValueChange = { password = it; errorMessage = "" },
                    label         = "Password",
                    placeholder   = "Enter your password",
                    isPassword    = true,
                    enabled       = !isLoading
                )

                // Error message
                AnimatedVisibility(visible = errorMessage.isNotEmpty()) {
                    Text(
                        text     = errorMessage,
                        color    = colorScheme.error,
                        style    = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 6.dp, start = 4.dp)
                    )
                }

                // Forgot password (enforcer only)
                if (isEnforcer) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        TextButton(onClick = { showResetDialog = true }, enabled = !isLoading) {
                            Text(
                                text  = "Forgot Password?",
                                color = colorScheme.primary,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.height(12.dp))
                }

                PrimaryButton(
                    text    = if (isLoading) "Signing in..." else "Login",
                    enabled = !isLoading && username.isNotBlank() && password.isNotBlank(),
                    onClick = {
                        errorMessage = ""
                        if (username.isBlank() || password.isBlank()) {
                            errorMessage = "Please enter your credentials."
                            return@PrimaryButton
                        }

                        // ── Real Firebase Authentication (with Task 2: Role Verification) ────────────────────────
                        isLoading = true
                        FirebaseManager.signIn(username.trim(), password, role) { success, error ->
                            isLoading = false
                            if (success) {
                                onLoginSuccess(username)
                            } else {
                                errorMessage = error ?: "Invalid credentials. Please try again."
                            }
                        }
                    }
                )

                Spacer(Modifier.height(12.dp))
                GhostButton(text = "Go Back", onClick = onBackClick, enabled = !isLoading)
            }
        }
    }

    if (showResetDialog) {
        EnforcerResetDialog(onDismiss = { showResetDialog = false })
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Forgot Password Dialog
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun EnforcerResetDialog(onDismiss: () -> Unit) {
    var username         by remember { mutableStateOf("") }
    var badgeNumber      by remember { mutableStateOf("") }
    var desiredPassword  by remember { mutableStateOf("") }
    var confirmPassword  by remember { mutableStateOf("") }
    var submitted        by remember { mutableStateOf(false) }

    val context     = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = colorScheme.surface,
        title = {
            Text(
                if (submitted) "Request Sent" else "Reset Password",
                color       = colorScheme.onSurface,
                fontWeight  = FontWeight.Bold
            )
        },
        text = {
            if (submitted) {
                Text(
                    "Your password reset request has been submitted. The administrator will review and apply your new password.",
                    color  = colorScheme.onSurfaceVariant,
                    style  = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        "Provide your details and desired new password. An administrator must approve this request.",
                        color  = colorScheme.onSurfaceVariant,
                        style  = MaterialTheme.typography.bodySmall,
                        lineHeight = 18.sp
                    )
                    MotorSikloTextField(
                        value         = username,
                        onValueChange = { username = it },
                        label         = "Username",
                        placeholder   = "Your account username"
                    )
                    MotorSikloTextField(
                        value         = badgeNumber,
                        onValueChange = { badgeNumber = it },
                        label         = "Badge Number",
                        placeholder   = "MTMB-XXXXX"
                    )
                    MotorSikloTextField(
                        value         = desiredPassword,
                        onValueChange = { desiredPassword = it },
                        label         = "New Password",
                        placeholder   = "At least 8 characters",
                        isPassword    = true
                    )
                    MotorSikloTextField(
                        value         = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label         = "Confirm New Password",
                        placeholder   = "Re-enter new password",
                        isPassword    = true
                    )
                    AnimatedVisibility(visible = confirmPassword.isNotEmpty() && desiredPassword != confirmPassword) {
                        Text("Passwords do not match", color = colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        },
        confirmButton = {
            if (submitted) {
                Button(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) {
                    Text("Done", fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    enabled = username.isNotBlank() && badgeNumber.isNotBlank() &&
                            desiredPassword.length >= 8 && desiredPassword == confirmPassword,
                    onClick = {
                        FirebaseManager.submitResetRequest(
                            username        = username.trim(),
                            badgeNumber     = badgeNumber.trim().uppercase(),
                            desiredPassword = desiredPassword
                        )
                        submitted = true
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Send Request", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = if (!submitted) {
            { TextButton(onClick = onDismiss) { Text("Cancel", color = colorScheme.onSurfaceVariant) } }
        } else null
    )
}