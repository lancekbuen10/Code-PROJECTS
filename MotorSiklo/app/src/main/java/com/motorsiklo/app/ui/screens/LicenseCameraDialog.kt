package com.motorsiklo.app.ui.screens

import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.compose.ui.text.font.FontWeight
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private fun createLicenseCameraFile(context: Context): Pair<File, Uri> {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val file = File(context.cacheDir, "license_scan_$timestamp.jpg")
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    return file to uri
}

@Composable
fun LicenseCameraDialog(
    onDismiss: () -> Unit,
    onPhotoCaptured: (File, Uri) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val colorScheme = MaterialTheme.colorScheme

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var isCapturing by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }

                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()

                        val preview = Preview.Builder().build().also { previewUseCase ->
                            previewUseCase.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val capture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                            .build()

                        imageCapture = capture

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                capture
                            )
                        } catch (e: Exception) {
                            Toast.makeText(ctx, "Unable to start camera: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                }
            )

            LicenseFrameOverlay(modifier = Modifier.fillMaxSize())

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Align Driver's License",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Place the license inside the frame",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Button(
                onClick = {
                    val capture = imageCapture ?: return@Button
                    val (file, uri) = createLicenseCameraFile(context)
                    val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

                    isCapturing = true
                    capture.takePicture(
                        outputOptions,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                isCapturing = false
                                onPhotoCaptured(file, uri)
                            }

                            override fun onError(exception: ImageCaptureException) {
                                isCapturing = false
                                file.delete()
                                Toast.makeText(
                                    context,
                                    "Capture failed: ${exception.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                },
                enabled = !isCapturing,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 90.dp)
                    .height(58.dp),
                shape = CircleShape
            ) {
                if (isCapturing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text("Capture License", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun LicenseFrameOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val frameWidth = size.width * 0.86f
        val frameHeight = frameWidth / 1.586f
        val left = (size.width - frameWidth) / 2f
        val top = (size.height - frameHeight) / 2f
        val right = left + frameWidth
        val bottom = top + frameHeight

        val shade = Color.Black.copy(alpha = 0.62f)

        drawRect(
            color = shade,
            topLeft = androidx.compose.ui.geometry.Offset.Zero,
            size = Size(size.width, top)
        )
        drawRect(
            color = shade,
            topLeft = androidx.compose.ui.geometry.Offset(0f, bottom),
            size = Size(size.width, size.height - bottom)
        )
        drawRect(
            color = shade,
            topLeft = androidx.compose.ui.geometry.Offset(0f, top),
            size = Size(left, frameHeight)
        )
        drawRect(
            color = shade,
            topLeft = androidx.compose.ui.geometry.Offset(right, top),
            size = Size(size.width - right, frameHeight)
        )

        drawRoundRect(
            color = Color.White,
            topLeft = androidx.compose.ui.geometry.Offset(left, top),
            size = Size(frameWidth, frameHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx()),
            style = Stroke(width = 3.dp.toPx())
        )

        drawRoundRect(
            color = Color(0xFFFFC107),
            topLeft = androidx.compose.ui.geometry.Offset(left + 8.dp.toPx(), top + 8.dp.toPx()),
            size = Size(frameWidth - 16.dp.toPx(), frameHeight - 16.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx()),
            style = Stroke(width = 1.5.dp.toPx())
        )
    }
}