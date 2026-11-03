package com.motorsiklo.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.motorsiklo.app.ui.theme.*
import com.motorsiklo.app.R
import kotlinx.coroutines.delay

@Composable
fun AdministrativePanel(requestCount: Int, onClick: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(modifier = Modifier.fillMaxWidth().clickable { onClick() }, shape = RoundedCornerShape(16.dp), color = colorScheme.surface, border = BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.1f)), tonalElevation = 2.dp) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(colorScheme.primary.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) { AdminIcon(size = 28f) }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Security Dashboard", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = colorScheme.onSurface)
                Text("$requestCount pending reset requests", style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun EmptyStateView(title: String, description: String, modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(modifier = Modifier.size(200.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) { drawCircle(color = colorScheme.primary.copy(alpha = 0.05f), radius = size.minDimension / 2); drawCircle(color = colorScheme.primary.copy(alpha = 0.1f), radius = size.minDimension / 3.2f) }
            Surface(modifier = Modifier.size(80.dp), shape = RoundedCornerShape(24.dp), color = colorScheme.surface, border = BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.15f)), shadowElevation = 8.dp) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.SearchOff, null, tint = colorScheme.primary, modifier = Modifier.size(32.dp)) } }
        }
        Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = colorScheme.onBackground)
        Spacer(Modifier.height(8.dp))
        Text(description, style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
    }
}

private fun formatCountdown(remainingMs: Long): String {
    val totalSeconds = (remainingMs / 1000).coerceAtLeast(0L)
    val hours   = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%02d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}

@Composable
fun ViolationCard(entry: ViolationEntry, onClick: (ViolationEntry) -> Unit = {}) {
    val typeColor   = getViolationColor(entry.primaryType)
    val typeIcon    = getViolationIcon(entry.primaryType)
    val colorScheme = MaterialTheme.colorScheme
    val context     = LocalContext.current
    val isNewViolation = ViolationRepository.newViolationIds.contains(entry.id)
    val glowPulse by rememberInfiniteTransition(label = "newViolationGlow").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(850), repeatMode = RepeatMode.Reverse),
        label = "newViolationGlowPulse"
    )

    var remainingMs by remember(entry.id, entry.status) {
        mutableLongStateOf(ViolationRepository.getRemainingMs(entry))
    }

    LaunchedEffect(entry.id, entry.status) {
        if (entry.status.equals("Detected", ignoreCase = true)) {
            while (remainingMs > 0) {
                delay(1_000L)
                remainingMs = ViolationRepository.getRemainingMs(entry)
            }
        }
    }

    LaunchedEffect(isNewViolation, entry.id) {
        if (isNewViolation) {
            delay(8_000L)
            ViolationRepository.clearNewViolation(entry.id)
        }
    }

    val imageReq = remember(entry.imageUrl) {
        ImageRequest.Builder(context).data(entry.imageUrl).crossfade(true).build()
    }

    val isUrgent by remember(remainingMs, entry.status) {
        derivedStateOf { entry.status.equals("Detected", ignoreCase = true) && remainingMs in 1..(10 * 60 * 1000L) }
    }

    val isExpired by remember(remainingMs, entry.status) {
        derivedStateOf { entry.status.equals("Detected", ignoreCase = true) && remainingMs == 0L }
    }

    val displayStatus by remember(isExpired, entry.status) {
        derivedStateOf {
            when {
                isExpired                           -> "EXPIRED"
                entry.status.equals("Detected", ignoreCase = true) -> "DETECTED"
                entry.status.equals("Pending", ignoreCase = true) || entry.status.equals("Awaiting_Approval", ignoreCase = true) -> "PENDING"
                else                                -> entry.status.uppercase()
            }
        }
    }

    val statusColor by remember(displayStatus) {
        derivedStateOf {
            when (displayStatus) {
                "DETECTED" -> colorScheme.error
                "EXPIRED"  -> colorScheme.onSurfaceVariant
                "PENDING"  -> colorScheme.primary
                else       -> Color(0xFF4CAF50)
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isExpired) 0.dp else if (isNewViolation) 22.dp else 12.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = if (isExpired) Color.Transparent else typeColor.copy(alpha = if (isNewViolation) glowPulse else 1f),
                spotColor = if (isExpired) Color.Transparent else typeColor.copy(alpha = if (isNewViolation) glowPulse else 1f)
            )
            .clickable { onClick(entry) },
        shape = RoundedCornerShape(16.dp),
        color = colorScheme.surface,
        border = BorderStroke(1.dp, if (isExpired) colorScheme.outline.copy(alpha = 0.05f) else typeColor.copy(alpha = if (isNewViolation) glowPulse else 0.4f)),
        tonalElevation = if (isExpired) 0.dp else 1.dp
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {

            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(typeColor.copy(alpha = if (isExpired) 0.05f else 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(typeIcon, null, tint = typeColor.copy(alpha = if (isExpired) 0.4f else 1f), modifier = Modifier.size(26.dp))

                if (entry.imageUrl.isNotEmpty()) {
                    AsyncImage(
                        model = imageReq,
                        contentDescription = "Violation Thumbnail",
                        modifier = Modifier.fillMaxSize().alpha(if (isExpired) 0.4f else 1f),
                        contentScale = ContentScale.Crop
                    )
                    Box(modifier = Modifier.fillMaxSize().background(typeColor.copy(alpha = 0.1f)))
                }
            }

            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = if (entry.isMultiple) "Multiple Violations" else entry.primaryType.label, fontWeight = FontWeight.Bold, color = colorScheme.onSurface.copy(alpha = if (isExpired) 0.4f else 1f), fontSize = 15.sp)
                    if (entry.isMultiple) {
                        Spacer(Modifier.width(6.dp))
                        Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(colorScheme.errorContainer).padding(horizontal = 4.dp, vertical = 2.dp)) { Text("+${entry.types.size - 1}", fontSize = 9.sp, color = colorScheme.onErrorContainer, fontWeight = FontWeight.Black) }
                    }
                }

                // ── UI FIX: Hide Hardcoded Location for Detected, Show Custom Location for Pending/Completed ──
                val locationText = if (entry.status.equals("Detected", ignoreCase = true) || entry.status.equals("Expired", ignoreCase = true)) {
                    formatTimestamp(entry.timestamp)
                } else {
                    "${entry.location} • ${formatTimestamp(entry.timestamp)}"
                }

                Text(text = locationText, color = colorScheme.onSurfaceVariant.copy(alpha = if (isExpired) 0.4f else 1f), fontSize = 12.sp)

                if (entry.status.equals("Detected", ignoreCase = true) && remainingMs > 0) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Timer, contentDescription = "Expires in", tint = if (isUrgent) colorScheme.error else colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Expires in ${formatCountdown(remainingMs)}", fontSize = 10.sp, fontWeight = if (isUrgent) FontWeight.Bold else FontWeight.Normal, color = if (isUrgent) colorScheme.error else colorScheme.onSurfaceVariant)
                    }
                }
            }
            Box(modifier = Modifier.clip(CircleShape).background(statusColor.copy(alpha = 0.1f)).padding(horizontal = 10.dp, vertical = 4.dp)) { Text(displayStatus, color = statusColor, fontSize = 9.sp, fontWeight = FontWeight.Black) }
        }
    }
}

@Composable
fun EvidenceCard(violation: ViolationEntry, onClick: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    val context     = LocalContext.current

    var remainingMs by remember(violation.id, violation.status) { mutableLongStateOf(ViolationRepository.getRemainingMs(violation)) }

    LaunchedEffect(violation.id, violation.status) {
        if (violation.status.equals("Detected", ignoreCase = true)) {
            while (remainingMs > 0) {
                delay(1_000L)
                remainingMs = ViolationRepository.getRemainingMs(violation)
            }
        }
    }

    val imageReq = remember(violation.imageUrl) {
        ImageRequest.Builder(context).data(violation.imageUrl).crossfade(true).build()
    }

    val isExpired by remember(remainingMs, violation.status) {
        derivedStateOf { violation.status.equals("Detected", ignoreCase = true) && remainingMs == 0L }
    }

    val displayStatus by remember(isExpired, violation.status) {
        derivedStateOf {
            when {
                isExpired -> "EXPIRED"
                violation.status.equals("Detected", ignoreCase = true) -> "DETECTED"
                violation.status.equals("Pending", ignoreCase = true) || violation.status.equals("Awaiting_Approval", ignoreCase = true) -> "PENDING"
                else -> violation.status.uppercase()
            }
        }
    }

    val statusColor by remember(displayStatus) {
        derivedStateOf {
            when (displayStatus) {
                "DETECTED" -> colorScheme.error
                "EXPIRED" -> colorScheme.onSurfaceVariant
                "PENDING" -> colorScheme.primary
                else -> Color(0xFF4CAF50)
            }
        }
    }

    Surface(modifier = Modifier.width(260.dp).height(160.dp).clickable { onClick() }, shape = RoundedCornerShape(16.dp), color = colorScheme.surface, border = BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.1f)), tonalElevation = 2.dp) {
        Box(contentAlignment = Alignment.Center) {

            Icon(getViolationIcon(violation.primaryType), null, tint = colorScheme.onSurfaceVariant.copy(alpha = 0.2f), modifier = Modifier.size(64.dp))

            AsyncImage(model = imageReq, contentDescription = "Violation preview", modifier = Modifier.fillMaxSize().alpha(if (isExpired) 0.4f else 1f), contentScale = ContentScale.Crop)

            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)), startY = 200f)))
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text(text = if (violation.isMultiple) "Multiple Violations" else violation.primaryType.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }

                // ── UI FIX: Hide Hardcoded Location for Detected, Show Custom Location for Pending/Completed ──
                val locationOrTime = if (violation.status.equals("Detected", ignoreCase = true) || violation.status.equals("Expired", ignoreCase = true)) {
                    formatTimestamp(violation.timestamp)
                } else {
                    "${violation.location} • ${formatTimestamp(violation.timestamp)}"
                }
                Text(text = locationOrTime, color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp)
            }

            // ── EXPIRED/DETECTED BADGE ──
            Surface(modifier = Modifier.padding(12.dp).align(Alignment.TopEnd), color = statusColor, shape = RoundedCornerShape(6.dp)) {
                Text(displayStatus, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = if (isExpired) colorScheme.surface else colorScheme.onError, fontSize = 9.sp, fontWeight = FontWeight.Black)
            }

            if (violation.hasVideoEvidence) {
                Surface(modifier = Modifier.padding(12.dp).align(Alignment.TopStart), color = Color(0xFFFFB74D), shape = RoundedCornerShape(6.dp)) {
                    Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(Icons.Default.Videocam, null, tint = Color.Black, modifier = Modifier.size(10.dp))
                        Text("CLIP", fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color.Black)
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryCard(modifier: Modifier = Modifier, label: String, value: String, icon: ImageVector, iconTint: Color) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = colorScheme.surface, border = BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.1f)), tonalElevation = 1.dp) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(iconTint.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.height(16.dp))
            RollingNumberText(value = value, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black), color = colorScheme.onSurface)
            Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun StatCard(title: String, value: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = colorScheme.surface, border = BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.1f)), tonalElevation = 2.dp) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = color, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.height(16.dp))
            RollingNumberText(value = value, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black), color = colorScheme.onSurface)
            Text(title, style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun MiniStatCard(modifier: Modifier = Modifier, label: String, value: String, color: Color) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = colorScheme.surface, border = BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.1f)), tonalElevation = 1.dp) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.Center) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            RollingNumberText(value = value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black), color = color)
        }
    }
}

@Composable
fun RollingNumberText(value: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    var previousValue by remember { mutableStateOf(value) }
    val previousNumber = previousValue.filter(Char::isDigit).toLongOrNull() ?: 0L
    val targetNumber = value.filter(Char::isDigit).toLongOrNull() ?: previousNumber
    val rollsUp = targetNumber >= previousNumber

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        value.forEachIndexed { index, char ->
            val previousChar = previousValue.getOrNull(index)
            AnimatedContent(
                targetState = char,
                transitionSpec = {
                    if (targetState == initialState) {
                        fadeIn(animationSpec = tween(0)) togetherWith fadeOut(animationSpec = tween(0))
                    } else {
                        val enterOffset: (Int) -> Int = { height -> if (rollsUp) height else -height }
                        val exitOffset: (Int) -> Int = { height -> if (rollsUp) -height else height }
                        (slideInVertically(animationSpec = tween(260), initialOffsetY = enterOffset) + fadeIn(animationSpec = tween(180))) togetherWith
                                (slideOutVertically(animationSpec = tween(260), targetOffsetY = exitOffset) + fadeOut(animationSpec = tween(180)))
                    }.using(SizeTransform(clip = true))
                },
                label = "rollingNumberDigit"
            ) { targetChar ->
                Text(targetChar.toString(), style = style, color = color)
            }

            if (previousChar == null && index == value.lastIndex) {
                Spacer(Modifier.width(0.dp))
            }
        }
    }

    LaunchedEffect(value) {
        previousValue = value
    }
}

@Composable
fun LegendChip(type: ViolationType, isSelected: Boolean, onClick: () -> Unit) {
    val color = getViolationColor(type)
    val colorScheme = MaterialTheme.colorScheme
    Surface(modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onClick() }, color = if (isSelected) color.copy(alpha = 0.15f) else colorScheme.surface, border = BorderStroke(1.dp, if (isSelected) color.copy(alpha = 0.5f) else colorScheme.outline.copy(alpha = 0.1f))) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(color))
            Spacer(Modifier.width(8.dp))
            Text(type.label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium), color = if (isSelected) color else colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun MotorSikloCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(modifier = modifier.widthIn(max = 400.dp).fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = colorScheme.surface, border = BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.1f)), tonalElevation = 1.dp) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, colorScheme.primary, Color.Transparent))))
            Column(modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 20.dp, bottom = 28.dp)) { content() }
        }
    }
}

@Composable
fun MotorSikloTextField(value: String, onValueChange: (String) -> Unit, label: String, placeholder: String, isPassword: Boolean = false, enabled: Boolean = true, isRequired: Boolean = false, keyboardType: KeyboardType = KeyboardType.Text, modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    var passwordVisible by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = if (enabled) colorScheme.onSurfaceVariant else colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            if (isRequired) {
                Text(" *", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black), color = colorScheme.error)
            }
        }
        OutlinedTextField(value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(), enabled = enabled, placeholder = { Text(placeholder, color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f), fontSize = 14.sp) }, singleLine = true, visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None, keyboardOptions = KeyboardOptions(keyboardType = if (isPassword) KeyboardType.Password else keyboardType, imeAction = ImeAction.Done), trailingIcon = if (isPassword) { { IconButton(onClick = { passwordVisible = !passwordVisible }, enabled = enabled) { Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null, tint = colorScheme.onSurfaceVariant) } } } else null, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colorScheme.primary, unfocusedBorderColor = colorScheme.outline.copy(alpha = 0.3f), disabledBorderColor = colorScheme.outline.copy(alpha = 0.1f), focusedTextColor = colorScheme.onSurface, unfocusedTextColor = colorScheme.onSurface, disabledTextColor = colorScheme.onSurface.copy(alpha = 0.5f), cursorColor = colorScheme.primary), shape = RoundedCornerShape(12.dp))
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)) { Text(text, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
}

@Composable
fun OutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)) { Text(text, fontWeight = FontWeight.Medium, fontSize = 14.sp) }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().height(48.dp), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) { Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
}

@Composable
fun MotorSikloLogo(size: Float = 100f) {
    val isDark = ThemeManager.isDarkTheme
    val logoRes = if (isDark) R.drawable.ic_motorsiklo_gold_logo else R.drawable.ic_motorsiklo_olive_logo
    Image(painter = painterResource(id = logoRes), contentDescription = "MotorSiklo App Logo", modifier = Modifier.size(size.dp), contentScale = ContentScale.Fit)
}

@Composable
fun BadgeIcon(size: Float = 38f) {
    val dpSize = size.dp
    val primaryColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.size(dpSize)) {
        val w = this.size.width; val h = this.size.height; val color = primaryColor; val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val shield = Path().apply { moveTo(w * 0.5f, h * 0.05f); lineTo(w * 0.9f, h * 0.22f); lineTo(w * 0.9f, h * 0.55f); cubicTo(w * 0.9f, h * 0.75f, w * 0.5f, h * 0.95f, w * 0.5f, h * 0.95f); cubicTo(w * 0.5f, h * 0.95f, w * 0.1f, h * 0.75f, w * 0.1f, h * 0.55f); lineTo(w * 0.1f, h * 0.22f); close() }
        drawPath(shield, color = color.copy(alpha = 0.1f)); drawPath(shield, color = color, style = stroke)
        drawCircle(color = color, radius = w * 0.12f, center = Offset(w * 0.5f, h * 0.42f), style = stroke)
        val shoulders = Path().apply { moveTo(w * 0.29f, h * 0.74f); cubicTo(w * 0.29f, h * 0.64f, w * 0.38f, h * 0.55f, w * 0.5f, h * 0.55f); cubicTo(w * 0.62f, h * 0.55f, w * 0.71f, h * 0.64f, w * 0.71f, h * 0.74f) }
        drawPath(shoulders, color = color, style = stroke)
    }
}

@Composable
fun AdminIcon(size: Float = 38f) {
    val dpSize = size.dp
    val primaryColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.size(dpSize)) {
        val w = this.size.width; val h = this.size.height; val cx = w / 2f; val cy = h / 2f; val color = primaryColor
        drawCircle(color = color, radius = w * 0.14f, center = Offset(cx, cy), style = Stroke(width = 2.dp.toPx()))
        for (i in 0..7) {
            val rad = Math.toRadians(i * 45.0).toFloat(); val innerR = w * 0.20f; val outerR = w * 0.38f
            drawLine(color = color, start = Offset(cx + kotlin.math.cos(rad) * innerR, cy + kotlin.math.sin(rad) * innerR), end = Offset(cx + kotlin.math.cos(rad) * outerR, cy + kotlin.math.sin(rad) * outerR), strokeWidth = 2.5f, cap = StrokeCap.Round)
        }
    }
}

@Composable
fun RoleCard(title: String, subtitle: String, icon: @Composable () -> Unit, onClick: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(16.dp), color = colorScheme.surface, border = BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.1f)), tonalElevation = 1.dp) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f)) { Text(title, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = colorScheme.onSurface); Text(subtitle, fontSize = 12.sp, color = colorScheme.onSurfaceVariant) }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
        }
    }
}

fun formatTimestamp(timestamp: Long): String {
    if (timestamp == 0L) return "Unknown time"
    val sdf = java.text.SimpleDateFormat("MMM dd, yyyy • hh:mm a", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}
