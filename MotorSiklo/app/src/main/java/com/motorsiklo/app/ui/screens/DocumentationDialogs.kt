package com.motorsiklo.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.motorsiklo.app.R

@Composable
fun AboutMotorSikloDialog(onDismiss: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.85f).clip(RoundedCornerShape(24.dp)),
            color = colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Box(modifier = Modifier.fillMaxWidth().background(colorScheme.primary).padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Info, contentDescription = "About Info", tint = colorScheme.onPrimary, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("About MotorSiklo", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black), color = colorScheme.onPrimary)
                    }
                }

                // Scrollable Content
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp)) {
                    Text("Version 1.0.0 (Release Candidate)", style = MaterialTheme.typography.labelSmall, color = colorScheme.primary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))

                    Text("Project Overview", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = colorScheme.onSurface)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "MotorSiklo is an advanced, AI-driven traffic apprehension and monitoring system explicitly designed for motorcycle violations. Built as a capstone thesis project by Computer Science students specializing in Software Engineering at FEU Alabang.",
                        style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurfaceVariant, lineHeight = 20.sp
                    )

                    Spacer(Modifier.height(20.dp))

                    Text("Core Technology", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = colorScheme.onSurface)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "The system utilizes the YOLOv8 (You Only Look Once) architecture and ByteTrack framework to process live camera feeds. It autonomously detects infractions such as No Helmet, Overloading, Overspeeding, and Illegal Parking, immediately alerting field enforcers via Firebase Cloud Messaging.",
                        style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurfaceVariant, lineHeight = 20.sp
                    )

                    Spacer(Modifier.height(20.dp))

                    Text("Development Team", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = colorScheme.onSurface)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Developed with dedication to modernize traffic enforcement, reduce vehicular accidents, and promote systematic data-driven road safety in the Philippines.",
                        style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurfaceVariant, lineHeight = 20.sp
                    )
                }

                // Footer Actions
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(12.dp)) {
                        Text("Close", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun PrivacyPolicyDialog(isFirstTime: Boolean = false, onAccept: () -> Unit, onDismiss: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    var showZoomedFeuImage by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { if (!isFirstTime) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !isFirstTime,
            dismissOnClickOutside = !isFirstTime
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.85f).clip(RoundedCornerShape(24.dp)),
            color = colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Box(modifier = Modifier.fillMaxWidth().background(colorScheme.primary).padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PrivacyTip, contentDescription = "Privacy Policy", tint = colorScheme.onPrimary, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Data Privacy Policy", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black), color = colorScheme.onPrimary)
                    }
                }

                // Scrollable Content
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp)) {

                    PolicySection("Institutional Data Privacy Commitment", "As a project developed under FEU Alabang, MotorSiklo operates in alignment with the FEU Group of Schools' institutional commitment to data privacy. The university adheres to the principles of transparency, legitimate purpose, and proportionality in processing personal data, and complies with Republic Act No. 10173 (the Data Privacy Act of 2012), its Implementing Rules and Regulations, and all applicable issuances of the National Privacy Commission. The official institutional policy statement is shown below for reference.")

                    Spacer(Modifier.height(8.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { showZoomedFeuImage = true },
                        color = colorScheme.surfaceVariant,
                        tonalElevation = 1.dp
                    ) {
                        Box {
                            Image(
                                painter = painterResource(id = R.drawable.data_privacy_policy_statement_feu),
                                contentDescription = "FEU Group of Schools Data Privacy Policy Statement",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Tap-to-zoom hint badge, helps users with low vision discover the feature
                            Surface(
                                modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
                                shape = RoundedCornerShape(50),
                                color = Color.Black.copy(alpha = 0.55f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.ZoomIn, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Tap to zoom", color = Color.White, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    PolicySection("1. Introduction & Legal Compliance", "This application strictly adheres to the Data Privacy Act of 2012 (Republic Act No. 10173) of the Philippines. By using MotorSiklo, you consent to the collection, processing, and localized storage of data strictly for traffic enforcement and academic research purposes.")

                    PolicySection("2. Information We Collect from Enforcers", "To facilitate secure access, we collect your First Name, Last Name, Middle Name, Badge Number, Assigned Station, and device identifiers (Android ID). This prevents unauthorized concurrent sessions and tracks enforcement metrics.")

                    PolicySection("3. Information We Collect from the Public", "The YOLOv8 AI Core captures photographs, video snippets, and license plate information of violators exclusively during the exact moment an infraction occurs. This media is utilized strictly as proof of apprehension.")

                    PolicySection("4. Data Synchronization & Storage", "Data is securely synchronized via Google Firebase (Realtime Database & Firestore). Video clips and images are uploaded to Firebase Cloud Storage. Administrative protocols require encrypted credentials to access or void this data.")

                    PolicySection("5. Data Sharing & Third Parties", "MotorSiklo is a closed-loop system. We do not sell, distribute, or share Enforcer or Violator data with third-party marketing or external administrative agencies beyond the scope of this thesis deployment.")

                    PolicySection("6. User Rights", "System Administrators hold the right to void false-positive AI detections and remove associated media. Enforcers may request an account reset or deletion through their assigned Administrator.")

                    Spacer(Modifier.height(20.dp))
                }

                // Footer Actions
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                    if (isFirstTime) {
                        Button(
                            onClick = onAccept,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary)
                        ) {
                            Text("I Agree & Accept", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    } else {
                        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(12.dp)) {
                            Text("Close", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showZoomedFeuImage) {
        ZoomableImageDialog(
            imageResId = R.drawable.data_privacy_policy_statement_feu,
            contentDescription = "FEU Group of Schools Data Privacy Policy Statement",
            onDismiss = { showZoomedFeuImage = false }
        )
    }
}

@Composable
private fun PolicySection(title: String, body: String) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Full-screen zoomable image viewer
// Supports pinch-to-zoom, drag-to-pan, double-tap to zoom in/out, and reset.
// Intended for accessibility — lets users with low vision read fine print.
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ZoomableImageDialog(
    imageResId: Int,
    contentDescription: String?,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    val minScale = 1f
    val maxScale = 5f

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Image(
                painter = painterResource(id = imageResId),
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(minScale, maxScale)
                            scale = newScale

                            // Only allow panning once zoomed in; reset offsets at default scale
                            if (newScale > 1f) {
                                offsetX += pan.x
                                offsetY += pan.y
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1f) {
                                    // Reset to fit
                                    scale = 1f
                                    offsetX = 0f
                                    offsetY = 0f
                                } else {
                                    // Zoom in toward center
                                    scale = 2.5f
                                }
                            }
                        )
                    }
            )

            // Close button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(50))
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }

            // Hint text
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
                shape = RoundedCornerShape(50),
                color = Color.Black.copy(alpha = 0.5f)
            ) {
                Text(
                    text = "Pinch to zoom • Drag to pan • Double-tap to reset",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}