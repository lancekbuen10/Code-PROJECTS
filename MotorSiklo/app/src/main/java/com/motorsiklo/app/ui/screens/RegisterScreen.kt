package com.motorsiklo.app.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motorsiklo.app.FirebaseManager
import com.motorsiklo.app.ui.theme.*

@Composable
fun RegisterScreen(
    onSignInClick: () -> Unit,
    onBackClick: () -> Unit
) {
    var lastName        by remember { mutableStateOf("") }
    var firstName       by remember { mutableStateOf("") }
    var middleName      by remember { mutableStateOf("") }
    var badgeNumber     by remember { mutableStateOf("") }
    var station         by remember { mutableStateOf("") }
    var username        by remember { mutableStateOf("") }
    var password        by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var submitted       by remember { mutableStateOf(false) }

    val context     = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = submitted,
            enter   = fadeIn() + scaleIn(initialScale = 0.9f),
            exit    = fadeOut()
        ) {
            MotorSikloCard {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector        = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint               = colorScheme.primary,
                        modifier           = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text      = "Request Submitted",
                        style     = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                        color     = colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text      = "Your registration has been sent for administrator review. You will be notified once your account is approved.",
                        style     = MaterialTheme.typography.bodyMedium,
                        color     = colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )
                    Spacer(Modifier.height(28.dp))
                    PrimaryButton(text = "Back to Sign In", onClick = onSignInClick)
                }
            }
        }

        AnimatedVisibility(
            visible = !submitted,
            enter   = fadeIn(),
            exit    = fadeOut()
        ) {
            MotorSikloCard {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BadgeIcon(size = 42f)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                text  = "Traffic Enforcer".uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurfaceVariant
                            )
                            Text(
                                text  = "Create Account",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                                color = colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape    = MaterialTheme.shapes.medium,
                        color    = colorScheme.primaryContainer
                    ) {
                        Text(
                            text     = "Registration requires administrator approval. You will not be able to log in until your account is verified.",
                            modifier = Modifier.padding(12.dp),
                            style    = MaterialTheme.typography.bodySmall,
                            color    = colorScheme.onPrimaryContainer,
                            lineHeight = 18.sp
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    Text("Enforcer Name", style = MaterialTheme.typography.labelSmall, color = colorScheme.primary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    MotorSikloTextField(
                        value         = lastName,
                        onValueChange = { lastName = it },
                        label         = "Last Name",
                        placeholder   = "e.g. Dela Cruz"
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MotorSikloTextField(
                            value         = firstName,
                            onValueChange = { firstName = it },
                            label         = "First Name",
                            placeholder   = "e.g. Juan",
                            modifier      = Modifier.weight(1f)
                        )
                        MotorSikloTextField(
                            value         = middleName,
                            onValueChange = { middleName = it },
                            label         = "Middle Name",
                            placeholder   = "e.g. Abad",
                            modifier      = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {

                        // ── UPDATED: Badge Number with Visual Transformation ──
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)) {
                                Text("BADGE NUMBER", style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
                            }
                            OutlinedTextField(
                                value = badgeNumber,
                                onValueChange = { input ->
                                    // Store only raw letters and digits, limit to 8 chars max (5 digits + up to 3 letters)
                                    badgeNumber = input.filter { it.isLetterOrDigit() }.uppercase().take(8)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("123-45-ABC", color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f), fontSize = 14.sp) },
                                singleLine = true,
                                visualTransformation = BadgeTransformation(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colorScheme.primary,
                                    unfocusedBorderColor = colorScheme.outline.copy(alpha = 0.3f),
                                    focusedTextColor = colorScheme.onSurface,
                                    unfocusedTextColor = colorScheme.onSurface,
                                    cursorColor = colorScheme.primary
                                ),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                            )
                        }

                        MotorSikloTextField(
                            value         = station,
                            onValueChange = { station = it },
                            label         = "Assigned Station",
                            placeholder   = "Muntinlupa",
                            modifier      = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    MotorSikloTextField(
                        value         = username,
                        onValueChange = { username = it },
                        label         = "Username",
                        placeholder   = "Choose a username"
                    )
                    Spacer(Modifier.height(14.dp))
                    MotorSikloTextField(
                        value         = password,
                        onValueChange = { password = it },
                        label         = "Password",
                        placeholder   = "At least 8 characters",
                        isPassword    = true
                    )
                    Spacer(Modifier.height(14.dp))
                    MotorSikloTextField(
                        value         = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label         = "Confirm Password",
                        placeholder   = "Re-enter your password",
                        isPassword    = true
                    )

                    AnimatedVisibility(visible = confirmPassword.isNotEmpty() && password != confirmPassword) {
                        Text(
                            text     = "Passwords do not match",
                            color    = colorScheme.error,
                            style    = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    PrimaryButton(
                        text    = "Submit Registration",
                        enabled = lastName.isNotBlank() && firstName.isNotBlank() &&
                                badgeNumber.isNotBlank() && station.isNotBlank() && username.isNotBlank() &&
                                password.length >= 8 && password == confirmPassword,
                        onClick = {
                            // ── UPDATED: Clean Regex Validation ──
                            val rawBadge = badgeNumber.filter { it.isLetterOrDigit() }
                            if (!rawBadge.matches(Regex("^\\d{5}[A-Z]{1,3}$"))) {
                                Toast.makeText(context, "Badge format must be: 3 Digits - 2 Digits - 1 to 3 Letters", Toast.LENGTH_LONG).show()
                                return@PrimaryButton
                            }

                            val formattedBadge = formatBadgeString(rawBadge)

                            val duplicate = registrationRequests.any {
                                it.username.equals(username, ignoreCase = true) && it.status != "Rejected"
                            }
                            if (duplicate) {
                                Toast.makeText(context, "Username already has a pending request.", Toast.LENGTH_SHORT).show()
                                return@PrimaryButton
                            }

                            FirebaseManager.submitRegistrationRequest(
                                lastName    = lastName.trim(),
                                firstName   = firstName.trim(),
                                middleName  = middleName.trim(),
                                username    = username.trim(),
                                badgeNumber = formattedBadge, // Submit with hyphens attached
                                password    = password,
                                station     = station.trim()
                            )
                            submitted = true
                        }
                    )

                    Spacer(Modifier.height(16.dp))

                    val annotated = buildAnnotatedString {
                        withStyle(SpanStyle(color = colorScheme.onSurfaceVariant)) {
                            append("Already have an account? ")
                        }
                        pushStringAnnotation("sign_in", "sign_in")
                        withStyle(SpanStyle(color = colorScheme.primary, fontWeight = FontWeight.Bold)) {
                            append("Sign In")
                        }
                        pop()
                    }
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        ClickableText(
                            text    = annotated,
                            style   = MaterialTheme.typography.bodySmall.copy(textAlign = TextAlign.Center),
                            onClick = { offset ->
                                annotated.getStringAnnotations("sign_in", offset, offset)
                                    .firstOrNull()?.let { onSignInClick() }
                            }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    GhostButton(text = "← Back", onClick = onBackClick)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Formatters and Transformations
// ─────────────────────────────────────────────────────────────────────────────

fun formatBadgeString(raw: String): String {
    val clean = raw.uppercase()
    val out = StringBuilder()
    for (i in clean.indices) {
        if (i == 3 || i == 5) out.append("-")
        out.append(clean[i])
    }
    return out.toString()
}

class BadgeTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val clean = text.text
        var out = ""
        for (i in clean.indices) {
            if (i == 3 || i == 5) out += "-"
            out += clean[i]
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