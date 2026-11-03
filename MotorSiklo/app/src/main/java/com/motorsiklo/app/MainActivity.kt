package com.motorsiklo.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging
import com.motorsiklo.app.navigation.MotorSikloNavGraph
import com.motorsiklo.app.ui.theme.MotorSikloTheme
import com.motorsiklo.app.ui.theme.ThemeManager

class MainActivity : ComponentActivity() {

    private lateinit var networkMonitor: NetworkMonitor

    // Handles Android 13+ runtime notification permissions
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("MotorSiklo", "Notification permission granted.")
            Firebase.messaging.subscribeToTopic("live_violations")
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) Log.d("MotorSiklo", "Successfully subscribed to live alerts.")
                }
        } else {
            Log.w("MotorSiklo", "Notification permission denied.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // ── OPTIMIZATION 1: Global Image Caching (Coil) ──
        // Prevents re-downloading heavy evidence images when scrolling
        val imageLoader = ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25) // Use 25% of app memory for fast image loading
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("evidence_image_cache"))
                    .maxSizeBytes(50 * 1024 * 1024) // 50MB max disk cache footprint
                    .build()
            }
            .crossfade(true)
            .respectCacheHeaders(false) // Force cache even if GCS headers discourage it
            .build()
        Coil.setImageLoader(imageLoader)

        // ── OPTIMIZATION 2: Firebase Offline Persistence ──
        // Keeps data loaded even if the enforcer drives into a dead zone
        try {
            val database = Firebase.database("https://motorsiklo-c1594-default-rtdb.firebaseio.com/")
            database.setPersistenceEnabled(true)
            database.reference.keepSynced(true)
        } catch (e: Exception) {
            Log.d("MotorSiklo", "Firebase persistence already enabled.")
        }

        ThemeManager.init(this)
        networkMonitor = NetworkMonitor(this)
        askNotificationPermission()

        setContent {
            val networkStatus by networkMonitor.status.collectAsStateWithLifecycle(
                initialValue = NetworkStatus.Available
            )

            val isDatabaseConnected by FirebaseManager.isDatabaseConnected

            MotorSikloTheme {
                // ── This is the engine that starts all Firebase syncs! ──
                LaunchedEffect(Unit) {
                    FirebaseManager.monitorDatabaseConnection()
                    FirebaseManager.startListeningForViolations()
                    FirebaseManager.syncSystemHealth()
                    FirebaseManager.syncStatistics()
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        MotorSikloNavGraph()

                        // Blocks the UI and shows an alert if internet is lost
                        if (networkStatus == NetworkStatus.Unavailable || !isDatabaseConnected) {
                            ConnectionLostOverlay(
                                message = if (networkStatus == NetworkStatus.Unavailable) {
                                    "MotorSiklo requires an internet connection to sync violation data and tickets."
                                } else {
                                    "Database connection lost. Reconnecting in 5 seconds..."
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Global Touch Interceptor for Idle Tracking ──
    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        FirebaseManager.resetActivity()
        return super.dispatchTouchEvent(ev)
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                Firebase.messaging.subscribeToTopic("live_violations")
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) Log.d("MotorSiklo", "Successfully subscribed to live alerts.")
                    }
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            // Android 12 and below don't require runtime permission for notifications
            Firebase.messaging.subscribeToTopic("live_violations")
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) Log.d("MotorSiklo", "Successfully subscribed to live alerts.")
                }
        }
    }
}

@Composable
fun ConnectionLostOverlay(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    }
                }
            }
            .background(Color.Black.copy(alpha = 0.75f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.padding(32.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = null,
                    modifier = Modifier.size(52.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Connection Lost",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = message,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
