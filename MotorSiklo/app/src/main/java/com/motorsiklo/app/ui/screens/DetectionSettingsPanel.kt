package com.motorsiklo.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

private object DetectionSettingsRepository {
    private const val DATABASE_URL = "https://motorsiklo-c1594-default-rtdb.firebaseio.com/"
    private const val NODE_DETECTION_SETTINGS = "detection_settings"

    private val db by lazy { Firebase.database(DATABASE_URL).reference }
    private val auth by lazy { Firebase.auth }
    private var listener: ValueEventListener? = null

    fun startListening() {
        if (listener != null) return

        listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val fallback = currentDetectionSettings.value
                currentDetectionSettings.value = DetectionSettings(
                    maxRiders = readInt(snapshot, "maxRiders", fallback.maxRiders),
                    speedLimitKmhTop = readInt(snapshot, "speedLimitKmhTop", fallback.speedLimitKmhTop),
                    speedLimitKmhBottom = readInt(snapshot, "speedLimitKmhBottom", fallback.speedLimitKmhBottom),
                    illegalParkingSeconds = readInt(
                        snapshot,
                        "illegalParkingSeconds",
                        fallback.illegalParkingSeconds
                    ),
                    updatedAt = (snapshot.child("updatedAt").value as? Long) ?: fallback.updatedAt,
                    updatedBy = snapshot.child("updatedBy").value?.toString() ?: fallback.updatedBy
                )
            }

            override fun onCancelled(error: DatabaseError) {}
        }

        db.child(NODE_DETECTION_SETTINGS).addValueEventListener(listener!!)
    }

    fun save(settings: DetectionSettings, onResult: (String) -> Unit) {
        val updatedBy = currentUserProfile.value?.username
            ?: auth.currentUser?.email
            ?: "Administrator"

        val data: Map<String, Any> = mapOf(
            "maxRiders" to settings.maxRiders,
            "speedLimitKmhTop" to settings.speedLimitKmhTop,
            "speedLimitKmhBottom" to settings.speedLimitKmhBottom,
            "illegalParkingSeconds" to settings.illegalParkingSeconds,
            "updatedAt" to ServerValue.TIMESTAMP,
            "updatedBy" to updatedBy
        )

        db.child(NODE_DETECTION_SETTINGS).updateChildren(data)
            .addOnSuccessListener { onResult("Detection settings saved.") }
            .addOnFailureListener { onResult(it.message ?: "Failed to save settings.") }
    }

    private fun readInt(snapshot: DataSnapshot, key: String, fallback: Int): Int {
        return when (val value = snapshot.child(key).value) {
            is Long -> value.toInt()
            is Int -> value
            is Double -> value.toInt()
            is String -> value.toIntOrNull() ?: fallback
            else -> fallback
        }
    }
}

@Composable
fun DetectionSettingsPanel() {
    val colorScheme = MaterialTheme.colorScheme
    val settings = currentDetectionSettings.value

    var isExpanded by remember { mutableStateOf(false) }
    var maxRidersText by remember { mutableStateOf(settings.maxRiders.toString()) }
    var topSpeedText by remember { mutableStateOf(settings.speedLimitKmhTop.toString()) }
    var bottomSpeedText by remember { mutableStateOf(settings.speedLimitKmhBottom.toString()) }
    var illegalParkingText by remember { mutableStateOf(settings.illegalParkingSeconds.toString()) }
    var message by remember { mutableStateOf<String?>(null) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "detectionSettingsChevron"
    )

    LaunchedEffect(Unit) {
        DetectionSettingsRepository.startListening()
    }

    LaunchedEffect(
        settings.maxRiders,
        settings.speedLimitKmhTop,
        settings.speedLimitKmhBottom,
        settings.illegalParkingSeconds
    ) {
        maxRidersText = settings.maxRiders.toString()
        topSpeedText = settings.speedLimitKmhTop.toString()
        bottomSpeedText = settings.speedLimitKmhBottom.toString()
        illegalParkingText = settings.illegalParkingSeconds.toString()
    }

    val maxRiders = maxRidersText.toIntOrNull()
    val topSpeed = topSpeedText.toIntOrNull()
    val bottomSpeed = bottomSpeedText.toIntOrNull()
    val illegalParkingSeconds = illegalParkingText.toIntOrNull()

    val isValid = maxRiders != null &&
            topSpeed != null &&
            bottomSpeed != null &&
            illegalParkingSeconds != null &&
            maxRiders in 1..10 &&
            topSpeed in 1..200 &&
            bottomSpeed in 1..200 &&
            illegalParkingSeconds in 10..3600

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(durationMillis = 220)),
        shape = RoundedCornerShape(16.dp),
        color = colorScheme.surface,
        border = BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.1f)),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { isExpanded = !isExpanded },
                shape = RoundedCornerShape(14.dp),
                color = if (isExpanded) colorScheme.primary.copy(alpha = 0.10f) else colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, colorScheme.outline.copy(alpha = if (isExpanded) 0.18f else 0.08f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(colorScheme.primary.copy(alpha = 0.13f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Detection Settings",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = colorScheme.onSurface
                        )
                        Text(
                            text = "Riders, speed limits, and parking timer",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse detection settings" else "Expand detection settings",
                        tint = colorScheme.primary,
                        modifier = Modifier.rotate(chevronRotation)
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(animationSpec = tween(220)) + fadeIn(animationSpec = tween(180)),
                exit = shrinkVertically(animationSpec = tween(180)) + fadeOut(animationSpec = tween(120))
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = maxRidersText,
                        onValueChange = { maxRidersText = it.filter(Char::isDigit).take(2) },
                        label = { Text("Maximum riders") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = topSpeedText,
                            onValueChange = { topSpeedText = it.filter(Char::isDigit).take(3) },
                            label = { Text("Top speed limit") },
                            suffix = { Text("km/h") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = bottomSpeedText,
                            onValueChange = { bottomSpeedText = it.filter(Char::isDigit).take(3) },
                            label = { Text("Bottom speed limit") },
                            suffix = { Text("km/h") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = illegalParkingText,
                        onValueChange = { illegalParkingText = it.filter(Char::isDigit).take(4) },
                        label = { Text("Illegal parking duration") },
                        suffix = { Text("sec") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            DetectionSettingsRepository.save(
                                settings.copy(
                                    maxRiders = maxRiders!!,
                                    speedLimitKmhTop = topSpeed!!,
                                    speedLimitKmhBottom = bottomSpeed!!,
                                    illegalParkingSeconds = illegalParkingSeconds!!
                                )
                            ) { resultMessage ->
                                message = resultMessage
                            }
                        },
                        enabled = isValid,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Save Settings", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (!isValid) {
                        Text(
                            text = "Enter valid values: riders 1-10, speed 1-200 km/h, parking 10-3600 sec.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.error
                        )
                    }

                    message?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
