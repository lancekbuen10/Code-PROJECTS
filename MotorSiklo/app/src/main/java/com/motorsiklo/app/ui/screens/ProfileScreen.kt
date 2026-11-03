package com.motorsiklo.app.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motorsiklo.app.FirebaseManager
import com.motorsiklo.app.ui.theme.*

@Composable
fun ProfileScreen(role: String, username: String, onLogout: () -> Unit) {
    val scroll               = rememberScrollState()
    var showLogoutDialog     by remember { mutableStateOf(false) }
    var showChangePassDialog by remember { mutableStateOf(false) }
    var showAboutDialog      by remember { mutableStateOf(false) }
    var showPrivacyDialog    by remember { mutableStateOf(false) }

    val colorScheme = MaterialTheme.colorScheme
    val context     = LocalContext.current
    val isEnforcer  = role == "Traffic Enforcer"

    val userProfile = currentUserProfile.value

    // ── STRICT NAME FORMATTING (LastName, FirstName MiddleName) ──
    val rawLastName  = userProfile?.lastName?.trim() ?: ""
    val rawFirstName = userProfile?.firstName?.trim() ?: ""
    val rawMiddleName = userProfile?.middleName?.trim() ?: ""

    val displayTopName = if (rawLastName.isNotBlank()) "$rawLastName," else username
    val displayBottomName = "$rawFirstName $rawMiddleName".trim()

    val station = userProfile?.station?.takeIf { it.isNotBlank() } ?: "N/A"
    val userBadge = userProfile?.badgeNumber ?: ""

    val avatarLetters = if (rawFirstName.isNotBlank() && rawLastName.isNotBlank()) {
        "${rawFirstName.first()}${rawLastName.first()}".uppercase()
    } else {
        username.take(2).uppercase()
    }

    // ── STRICT ROLE-BASED PERSONAL COUNTERS ──
    val personalCount = remember(ViolationRepository.violations.size, userBadge) {
        if (isEnforcer) {
            // ENFORCER: Strictly count tickets attached to their specific Badge Number
            ViolationRepository.violations.count {
                (it.status == "Completed" || it.status == "Awaiting_Approval") && it.badgeNumber == userBadge
            }
        } else {
            // ADMIN: Track system-wide confirmed/approved violations
            ViolationRepository.violations.count { it.status == "Completed" }
        }
    }

    val personalLabel = if (isEnforcer) "Personal Apprehensions Logged" else "Violations Confirmed"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(32.dp))

        // ── Avatar ──
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                modifier = Modifier.size(100.dp),
                shape    = CircleShape,
                color    = colorScheme.primaryContainer,
                border   = BorderStroke(2.dp, colorScheme.primary)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text  = avatarLetters,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            color      = colorScheme.primary,
                            fontWeight = FontWeight.Black,
                            fontSize   = 32.sp
                        )
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            // ── FIX: Sync top name color with theme ──
            Text(
                text  = displayTopName,
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp, fontWeight = FontWeight.Bold),
                color = if (ThemeManager.isDarkTheme) Amber400 else colorScheme.onSurface
            )

            // Render Bottom Name (FirstName MiddleName)
            if (displayBottomName.isNotEmpty()) {
                Text(
                    text  = displayBottomName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colorScheme.primary
                )
            }

            Spacer(Modifier.height(10.dp))
            Surface(
                shape  = RoundedCornerShape(50),
                color  = colorScheme.primary.copy(alpha = 0.1f),
                border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Text(
                    text     = role.uppercase(),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    style    = MaterialTheme.typography.labelSmall.copy(
                        color      = colorScheme.primary,
                        fontSize   = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        // ── Activity ──
        SectionLabel("MY FIELD ACTIVITY")
        Spacer(Modifier.height(12.dp))
        ActivityCard(Modifier.fillMaxWidth().height(100.dp), personalLabel, "$personalCount", colorScheme.primary)

        Spacer(Modifier.height(32.dp))

        // ── Account info ──
        SectionLabel("ACCOUNT INFORMATION")
        Spacer(Modifier.height(12.dp))
        ProfileInfoCard {
            ProfileInfoRow(Icons.Default.Person,       "Username", username)
            ProfileDivider()
            ProfileInfoRow(Icons.Default.Badge,        "Role",    role)
            ProfileDivider()
            ProfileInfoRow(Icons.Default.LocationCity, "Station", station)
        }

        Spacer(Modifier.height(32.dp))

        // ── Settings ──
        SectionLabel("SETTINGS")
        Spacer(Modifier.height(12.dp))
        ProfileInfoCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBox(if (ThemeManager.isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode, colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(16.dp))
                    Text("Dark Mode", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = colorScheme.onSurface)
                }
                Switch(
                    checked         = ThemeManager.isDarkTheme,
                    onCheckedChange = { ThemeManager.toggleTheme(context) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colorScheme.primary,
                        checkedTrackColor = colorScheme.primary.copy(alpha = 0.3f)
                    )
                )
            }
            ProfileDivider()
            ProfileActionRow(
                icon    = Icons.Default.Lock,
                label   = "Change Password",
                onClick = { showChangePassDialog = true }
            )

            ProfileDivider()
            ProfileActionRow(icon = Icons.Default.Info, label = "About MotorSiklo", onClick = { showAboutDialog = true })
            ProfileDivider()
            ProfileActionRow(icon = Icons.Default.PrivacyTip, label = "Privacy Policy", onClick = { showPrivacyDialog = true })
        }

        Spacer(Modifier.height(40.dp))

        // ── Sign out ──
        Button(
            onClick  = { showLogoutDialog = true },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape    = RoundedCornerShape(16.dp),
            colors   = ButtonDefaults.buttonColors(
                containerColor = colorScheme.error.copy(alpha = 0.1f),
                contentColor   = colorScheme.error
            )
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text("Sign Out", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
        }
        Spacer(Modifier.height(48.dp))
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor   = MaterialTheme.colorScheme.surface,
            title = { Text("Sign Out", fontWeight = FontWeight.Bold) },
            text  = { Text("Are you sure you want to log out of your account?", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = { showLogoutDialog = false; onLogout() }) {
                    Text("Sign Out", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    if (showChangePassDialog) {
        ChangePasswordDialog(
            username  = username,
            badgeNumber = userBadge,
            isEnforcer = isEnforcer,
            onDismiss = { showChangePassDialog = false }
        )
    }

    if (showAboutDialog) {
        AboutMotorSikloDialog { showAboutDialog = false }
    }

    if (showPrivacyDialog) {
        PrivacyPolicyDialog(isFirstTime = false, onAccept = {}, onDismiss = { showPrivacyDialog = false })
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text       = text,
        style      = MaterialTheme.typography.labelSmall,
        color      = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold
    )
}

@Composable
fun ProfileDivider() {
    HorizontalDivider(
        color    = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f),
        modifier = Modifier.padding(vertical = 14.dp)
    )
}

@Composable
fun ProfileInfoCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier      = Modifier.fillMaxWidth(),
        shape         = RoundedCornerShape(16.dp),
        color         = MaterialTheme.colorScheme.surface,
        border        = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun ProfileInfoRow(icon: ImageVector, label: String, value: String) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBox(icon, cs.primary)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = cs.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = cs.onSurface)
        }
    }
}

@Composable
fun ProfileActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier              = Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBox(icon, cs.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = cs.onSurface)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = cs.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
    }
}

@Composable
fun IconBox(icon: ImageVector, tint: Color) {
    Box(
        modifier        = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun ActivityCard(modifier: Modifier, label: String, value: String, color: Color) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier      = modifier,
        shape         = RoundedCornerShape(16.dp),
        color         = cs.surface,
        border        = BorderStroke(1.dp, cs.outline.copy(alpha = 0.1f)),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier             = Modifier.padding(16.dp),
            horizontalAlignment  = Alignment.CenterHorizontally,
            verticalArrangement  = Arrangement.Center
        ) {
            Text(value, style = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp, color = color, fontWeight = FontWeight.Black))
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), textAlign = TextAlign.Center, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
fun ChangePasswordDialog(username: String, badgeNumber: String, isEnforcer: Boolean, onDismiss: () -> Unit) {
    var desiredPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colorScheme.surface,
        title = { Text("Change Password", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (isEnforcer) {
                    Text("Submitting this will require Administrator approval.", style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                }
                MotorSikloTextField(desiredPassword, { desiredPassword = it }, "New Password", "Min 8 chars", isPassword = true)
                MotorSikloTextField(confirmPassword, { confirmPassword = it }, "Confirm Password", "Re-enter password", isPassword = true)

                AnimatedVisibility(visible = confirmPassword.isNotEmpty() && desiredPassword != confirmPassword) {
                    Text("Passwords do not match", color = colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = desiredPassword.length >= 8 && desiredPassword == confirmPassword,
                shape = RoundedCornerShape(12.dp),
                onClick = {
                    FirebaseManager.submitChangePasswordRequest(username, badgeNumber, desiredPassword)
                    Toast.makeText(context, "Password change request sent.", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            ) { Text("Confirm", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = colorScheme.onSurfaceVariant) } }
    )
}