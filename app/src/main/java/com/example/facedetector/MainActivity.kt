package com.example.facedetector

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FaceDetectorTheme {
                FaceDetectorApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaceDetectorApp() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    fun isCameraPermissionGranted() = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    var hasPermission by remember { mutableStateOf(isCameraPermissionGranted()) }
    var permissionDenied by remember { mutableStateOf(false) }

    // App state
    var appMode by remember { mutableStateOf(AppMode.CAMERA) }
    var cameraOn by remember { mutableStateOf(true) }
    var isPaused by remember { mutableStateOf(false) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_FRONT) }
    var cameraKey by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    // Settings
    var settings by remember { mutableStateOf(DetectorSettings()) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }

    // First launch onboarding check
    val prefs = remember { context.getSharedPreferences("face_detector_prefs", Context.MODE_PRIVATE) }
    var showOnboarding by remember {
        mutableStateOf(!prefs.getBoolean("onboarding_completed", false))
    }

    // Face detection results
    var liveResult by remember { mutableStateOf<FaceResult?>(null) }
    val countSmoother = remember { FaceCountSmoother(requiredConsecutiveFrames = 3) }
    var smoothedFaceCount by remember { mutableIntStateOf(0) }

    // Gallery state
    var galleryBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var galleryResult by remember { mutableStateOf<FaceResult?>(null) }
    var isAnalyzingGallery by remember { mutableStateOf(false) }

    // PreviewView reference for screenshot capture
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        permissionDenied = !granted
    }

    // Photo picker launcher (Android Photo Picker - zero storage permission required)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isAnalyzingGallery = true
                val bitmap = ImageUtils.loadUprightBitmap(context, uri)
                if (bitmap != null) {
                    galleryBitmap = bitmap
                    appMode = AppMode.GALLERY
                    try {
                        val result = ImageUtils.detectFacesOnBitmap(bitmap, settings)
                        galleryResult = result
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("Failed to detect faces in selected photo.")
                    }
                } else {
                    snackbarHostState.showSnackbar("Unable to load selected image.")
                }
                isAnalyzingGallery = false
            }
        }
    }

    // Sync permission check on resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = isCameraPermissionGranted()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Active displayed result & smoothed face count calculation
    val currentResult = when (appMode) {
        AppMode.CAMERA -> if (cameraOn && !isPaused) liveResult else null
        AppMode.GALLERY -> galleryResult
    }

    LaunchedEffect(liveResult, cameraOn, isPaused, appMode) {
        if (appMode == AppMode.CAMERA) {
            val rawCount = if (cameraOn && !isPaused) liveResult?.faces?.size ?: 0 else 0
            smoothedFaceCount = countSmoother.update(rawCount)
        } else {
            smoothedFaceCount = galleryResult?.faces?.size ?: 0
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Face Detector",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            if (appMode == AppMode.CAMERA) "Live Camera Detection" else "Gallery Photo Mode",
                            fontSize = 12.sp,
                            color = Blue
                        )
                    }
                },
                actions = {
                    // Mode Switch Button (Camera <-> Gallery)
                    IconButton(onClick = {
                        if (appMode == AppMode.CAMERA) {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        } else {
                            appMode = AppMode.CAMERA
                            galleryBitmap = null
                            galleryResult = null
                        }
                    }) {
                        Icon(
                            if (appMode == AppMode.CAMERA) Icons.Default.PhotoLibrary else Icons.Default.CameraAlt,
                            contentDescription = "Switch Mode",
                            tint = Blue
                        )
                    }

                    // Privacy Note Dialog Button
                    IconButton(onClick = { showPrivacyDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Privacy Information", tint = Blue)
                    }

                    // Settings Button
                    IconButton(onClick = { showSettingsSheet = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Blue)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Floating Status Pill
            StatusPill(
                hasPermission = hasPermission,
                error = error,
                cameraOn = cameraOn,
                isPaused = isPaused,
                faceCount = smoothedFaceCount,
                appMode = appMode
            )

            Spacer(Modifier.height(10.dp))

            // Main Viewport Container
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Navy),
                contentAlignment = Alignment.Center
            ) {
                when {
                    !hasPermission -> {
                        MessageCard(
                            if (permissionDenied) "Camera permission was denied. Face detection requires camera access to process frames."
                            else "Waiting for camera permission…"
                        ) {
                            Button(
                                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                                colors = ButtonDefaults.buttonColors(containerColor = Blue)
                            ) {
                                Text("Grant Permission", color = Color.White)
                            }
                            if (permissionDenied) {
                                Spacer(Modifier.height(8.dp))
                                TextButton(onClick = {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                    )
                                }) {
                                    Text("Open App Settings", color = SkyBlue)
                                }
                            }
                        }
                    }

                    error != null -> {
                        MessageCard(error!!) {
                            Button(
                                onClick = { error = null; cameraKey++ },
                                colors = ButtonDefaults.buttonColors(containerColor = Blue)
                            ) {
                                Text("Retry Camera", color = Color.White)
                            }
                        }
                    }

                    appMode == AppMode.GALLERY -> {
                        if (galleryBitmap != null) {
                            Image(
                                bitmap = galleryBitmap!!.asImageBitmap(),
                                contentDescription = "Selected Gallery Photo",
                                modifier = Modifier.fillMaxSize()
                            )
                            galleryResult?.let { res ->
                                BoxesOverlay(
                                    result = res,
                                    showMetrics = settings.showMetrics
                                )
                            }
                        } else {
                            MessageCard("No photo selected.") {
                                Button(
                                    onClick = {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Blue)
                                ) {
                                    Text("Choose Photo", color = Color.White)
                                }
                            }
                        }
                    }

                    !cameraOn -> {
                        MessageCard("Camera is stopped.") {
                            Button(
                                onClick = { cameraOn = true; cameraKey++ },
                                colors = ButtonDefaults.buttonColors(containerColor = Blue)
                            ) {
                                Text("Start Camera", color = Color.White)
                            }
                        }
                    }

                    else -> {
                        key(cameraKey, lensFacing, settings.accurateMode, settings.minFaceSize) {
                            CameraView(
                                lensFacing = lensFacing,
                                isPaused = isPaused,
                                settings = settings,
                                onPreviewReady = { previewViewRef = it },
                                onResult = { liveResult = it },
                                onError = {
                                    error = it
                                    liveResult = null
                                }
                            )
                        }

                        if (!isPaused && liveResult != null) {
                            BoxesOverlay(
                                result = liveResult!!,
                                showMetrics = settings.showMetrics
                            )
                        }
                    }
                }

                // Loading overlay for gallery photo processing
                if (isAnalyzingGallery) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = SkyBlue)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Action Controls
            ControlPanel(
                appMode = appMode,
                hasPermission = hasPermission,
                cameraOn = cameraOn,
                isPaused = isPaused,
                onToggleCameraPower = {
                    cameraOn = !cameraOn
                    if (!cameraOn) {
                        liveResult = null
                        countSmoother.reset()
                        smoothedFaceCount = 0
                    } else {
                        cameraKey++
                    }
                },
                onTogglePause = {
                    isPaused = !isPaused
                    if (isPaused) {
                        liveResult = null
                    }
                },
                onSwitchCamera = {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                        CameraSelector.LENS_FACING_BACK
                    } else {
                        CameraSelector.LENS_FACING_FRONT
                    }
                    cameraKey++
                },
                onCaptureScreenshot = {
                    scope.launch {
                        val bitmapToSave = if (appMode == AppMode.CAMERA) {
                            previewViewRef?.bitmap
                        } else {
                            galleryBitmap
                        }

                        if (bitmapToSave != null && currentResult != null) {
                            val msg = ScreenshotHelper.saveAnnotatedImage(
                                context = context,
                                sourceBitmap = bitmapToSave,
                                faces = currentResult.faces,
                                mirrored = currentResult.mirrored,
                                showMetrics = settings.showMetrics
                            )
                            snackbarHostState.showSnackbar(msg ?: "Image saved to Pictures.")
                        } else if (bitmapToSave != null) {
                            val msg = ScreenshotHelper.saveAnnotatedImage(
                                context = context,
                                sourceBitmap = bitmapToSave,
                                faces = emptyList(),
                                mirrored = appMode == AppMode.CAMERA && lensFacing == CameraSelector.LENS_FACING_FRONT,
                                showMetrics = false
                            )
                            snackbarHostState.showSnackbar(msg ?: "Frame saved to Pictures.")
                        } else {
                            snackbarHostState.showSnackbar("Unable to capture frame.")
                        }
                    }
                },
                onPickAnotherPhoto = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onReturnToCamera = {
                    appMode = AppMode.CAMERA
                    galleryBitmap = null
                    galleryResult = null
                }
            )

            Spacer(Modifier.height(6.dp))

            Text(
                "Detects faces strictly on your device. No biometric templates, identification, or data uploads.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        // Settings Sheet
        if (showSettingsSheet) {
            SettingsBottomSheet(
                settings = settings,
                onDismiss = { showSettingsSheet = false },
                onSettingsChanged = {
                    settings = it
                    // Re-trigger detection if on gallery photo
                    if (appMode == AppMode.GALLERY && galleryBitmap != null) {
                        scope.launch {
                            try {
                                galleryResult = ImageUtils.detectFacesOnBitmap(galleryBitmap!!, settings)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            )
        }

        // First-launch Onboarding Screen
        if (showOnboarding) {
            OnboardingDialog(
                onDismiss = {
                    prefs.edit().putBoolean("onboarding_completed", true).apply()
                    showOnboarding = false
                }
            )
        }

        // Privacy Note Dialog
        if (showPrivacyDialog) {
            PrivacyNoteDialog(onDismiss = { showPrivacyDialog = false })
        }
    }
}

/**
 * Status pill showing live state with color-coded dot indicator.
 */
@Composable
fun StatusPill(
    hasPermission: Boolean,
    error: String?,
    cameraOn: Boolean,
    isPaused: Boolean,
    faceCount: Int,
    appMode: AppMode
) {
    val (statusColor, statusText) = when {
        !hasPermission -> Pair(StatusRed, "Camera permission needed")
        error != null -> Pair(StatusRed, "Camera error")
        appMode == AppMode.GALLERY -> {
            if (faceCount > 0) Pair(StatusGreen, "$faceCount face${if (faceCount > 1) "s" else ""} detected")
            else Pair(StatusGray, "No face detected in photo")
        }
        !cameraOn -> Pair(Blue, "Camera stopped")
        isPaused -> Pair(StatusAmber, "Detection paused (preview live)")
        faceCount > 0 -> Pair(StatusGreen, "$faceCount face${if (faceCount > 1) "s" else ""} detected")
        else -> Pair(StatusGray, "No face detected")
    }

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
        modifier = Modifier.padding(horizontal = 8.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                statusText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Bottom control panel for camera switching, pause/resume, screenshot, and gallery actions.
 */
@Composable
fun ControlPanel(
    appMode: AppMode,
    hasPermission: Boolean,
    cameraOn: Boolean,
    isPaused: Boolean,
    onToggleCameraPower: () -> Unit,
    onTogglePause: () -> Unit,
    onSwitchCamera: () -> Unit,
    onCaptureScreenshot: () -> Unit,
    onPickAnotherPhoto: () -> Unit,
    onReturnToCamera: () -> Unit
) {
    if (appMode == AppMode.CAMERA) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Switch Camera (Front/Back)
            FilledTonalIconButton(
                onClick = onSwitchCamera,
                enabled = hasPermission && cameraOn,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Default.FlipCameraAndroid,
                    contentDescription = "Switch Camera (Front/Back)",
                    tint = Blue
                )
            }

            // Pause / Resume Detection
            FilledTonalIconButton(
                onClick = onTogglePause,
                enabled = hasPermission && cameraOn,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = if (isPaused) "Resume Detection" else "Pause Detection",
                    tint = if (isPaused) StatusGreen else StatusAmber
                )
            }

            // Screenshot Button
            FilledTonalIconButton(
                onClick = onCaptureScreenshot,
                enabled = hasPermission && cameraOn,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Default.PhotoCamera,
                    contentDescription = "Capture Screenshot",
                    tint = Blue
                )
            }

            // Start/Stop Camera Button
            Button(
                onClick = onToggleCameraPower,
                enabled = hasPermission,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (cameraOn) StatusRed else Blue
                ),
                modifier = Modifier.height(48.dp)
            ) {
                Icon(
                    if (cameraOn) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(if (cameraOn) "Stop" else "Start")
            }
        }
    } else {
        // Gallery Mode Controls
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onPickAnotherPhoto,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Pick Another")
            }

            FilledTonalIconButton(
                onClick = onCaptureScreenshot,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Default.SaveAlt, contentDescription = "Save Annotated Photo", tint = Blue)
            }

            Button(
                onClick = onReturnToCamera,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Blue),
                modifier = Modifier.height(48.dp)
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Live Camera")
            }
        }
    }
}

/**
 * Animated bounding boxes overlay with rounded corners, subtle pulse glow, and per-face metrics.
 */
@Composable
fun BoxesOverlay(
    result: FaceResult,
    showMetrics: Boolean
) {
    val textMeasurer = rememberTextMeasurer()

    // Subtle pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Canvas(Modifier.fillMaxSize()) {
        val viewWidth = size.width
        val viewHeight = size.height

        for (face in result.faces) {
            val coords = transformFaceBox(
                box = face.boundingBox,
                imageWidth = result.imageWidth,
                imageHeight = result.imageHeight,
                viewWidth = viewWidth,
                viewHeight = viewHeight,
                mirrored = result.mirrored
            )

            val boxWidth = coords.width
            val boxHeight = coords.height
            if (boxWidth <= 0f || boxHeight <= 0f) continue

            // 1. Subtle pulsing glow frame behind the box
            drawRoundRect(
                color = SkyBlue.copy(alpha = pulseAlpha * 0.4f),
                topLeft = Offset(coords.left - 3f, coords.top - 3f),
                size = Size(boxWidth + 6f, boxHeight + 6f),
                cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                style = Stroke(width = 6.dp.toPx())
            )

            // 2. Primary crisp rounded rectangle box
            drawRoundRect(
                color = SkyBlue,
                topLeft = Offset(coords.left, coords.top),
                size = Size(boxWidth, boxHeight),
                cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
                style = Stroke(width = 3.5.dp.toPx())
            )

            // 3. Optional per-face metrics (smile & eye openness probability)
            if (showMetrics) {
                val smileText = face.smilingProbability?.let { "😊 ${(it * 100).toInt()}%" } ?: ""
                val eyeText = if (face.leftEyeOpenProbability != null && face.rightEyeOpenProbability != null) {
                    "👁️ L:${(face.leftEyeOpenProbability * 100).toInt()}% R:${(face.rightEyeOpenProbability * 100).toInt()}%"
                } else ""

                val label = listOf(smileText, eyeText).filter { it.isNotEmpty() }.joinToString("  ")
                if (label.isNotEmpty()) {
                    val textLayout = textMeasurer.measure(
                        text = label,
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )

                    val badgeLeft = coords.left.coerceAtLeast(8f)
                    val badgeTop = (coords.top - textLayout.size.height - 12f).coerceAtLeast(8f)

                    // Badge background chip
                    drawRoundRect(
                        color = Navy.copy(alpha = 0.88f),
                        topLeft = Offset(badgeLeft, badgeTop),
                        size = Size(textLayout.size.width + 16f, textLayout.size.height + 8f),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )

                    // Badge text
                    drawText(
                        textLayoutResult = textLayout,
                        topLeft = Offset(badgeLeft + 8f, badgeTop + 4f)
                    )
                }
            }
        }
    }
}

/**
 * CameraView encapsulates CameraX initialization, image analysis, and proper resource release.
 */
@Composable
fun CameraView(
    lensFacing: Int,
    isPaused: Boolean,
    settings: DetectorSettings,
    onPreviewReady: (PreviewView) -> Unit,
    onResult: (FaceResult) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    LaunchedEffect(previewView) {
        onPreviewReady(previewView)
    }

    DisposableEffect(lifecycleOwner, lensFacing, settings.accurateMode, settings.minFaceSize) {
        val executor = Executors.newSingleThreadExecutor()

        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(
                if (settings.accurateMode) FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE
                else FaceDetectorOptions.PERFORMANCE_MODE_FAST
            )
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(settings.minFaceSize)
            .build()

        val detector = FaceDetection.getClient(options)
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        var cameraProvider: ProcessCameraProvider? = null
        var isDisposed = false

        cameraProviderFuture.addListener({
            if (isDisposed) return@addListener
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider

                val primarySelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                val selector = when {
                    provider.hasCamera(primarySelector) -> primarySelector
                    provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                    provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                    else -> {
                        onError("No camera available on this device.")
                        return@addListener
                    }
                }

                val isMirrored = selector == CameraSelector.DEFAULT_FRONT_CAMERA ||
                        lensFacing == CameraSelector.LENS_FACING_FRONT

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                var lastFrameTimestamp = 0L

                analysis.setAnalyzer(executor) { proxy ->
                    val now = System.currentTimeMillis()
                    val mediaImage = proxy.image

                    if (isDisposed || isPaused || mediaImage == null || now - lastFrameTimestamp < 70) {
                        proxy.close()
                        return@setAnalyzer
                    }
                    lastFrameTimestamp = now

                    val rotation = proxy.imageInfo.rotationDegrees
                    val (uprightW, uprightH) = getUprightDimensions(proxy.width, proxy.height, rotation)

                    try {
                        val inputImage = InputImage.fromMediaImage(mediaImage, rotation)
                        detector.process(inputImage)
                            .addOnSuccessListener { faces ->
                                if (!isDisposed && !isPaused) {
                                    val detected = faces.mapIndexed { idx, f ->
                                        DetectedFace(
                                            id = f.trackingId ?: idx,
                                            boundingBox = f.boundingBox,
                                            smilingProbability = f.smilingProbability,
                                            leftEyeOpenProbability = f.leftEyeOpenProbability,
                                            rightEyeOpenProbability = f.rightEyeOpenProbability
                                        )
                                    }
                                    onResult(
                                        FaceResult(
                                            faces = detected,
                                            imageWidth = uprightW,
                                            imageHeight = uprightH,
                                            mirrored = isMirrored,
                                            rotationDegrees = rotation
                                        )
                                    )
                                }
                            }
                            .addOnCompleteListener { proxy.close() }
                    } catch (e: Exception) {
                        proxy.close()
                    }
                }

                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
            } catch (e: Exception) {
                onError("Could not start camera. It may be in use by another app.")
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            isDisposed = true
            cameraProvider?.unbindAll()
            executor.shutdown()
            detector.close()
        }
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

/**
 * Message placeholder inside the camera viewport.
 */
@Composable
private fun MessageCard(text: String, actions: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .padding(24.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text, color = Color.White, textAlign = TextAlign.Center, fontSize = 15.sp)
        Spacer(Modifier.height(14.dp))
        actions()
    }
}

/**
 * Settings Bottom Sheet for toggling Fast/Accurate mode, min face size, and metrics.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    settings: DetectorSettings,
    onDismiss: () -> Unit,
    onSettingsChanged: (DetectorSettings) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "Detection Settings",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(20.dp))

            // Fast vs Accurate Mode
            Text("Performance Mode", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(
                    selected = !settings.accurateMode,
                    onClick = { onSettingsChanged(settings.copy(accurateMode = false)) },
                    label = { Text("Fast Mode") },
                    leadingIcon = {
                        if (!settings.accurateMode) Icon(Icons.Default.Check, contentDescription = null)
                    }
                )
                FilterChip(
                    selected = settings.accurateMode,
                    onClick = { onSettingsChanged(settings.copy(accurateMode = true)) },
                    label = { Text("Accurate Mode") },
                    leadingIcon = {
                        if (settings.accurateMode) Icon(Icons.Default.Check, contentDescription = null)
                    }
                )
            }
            Text(
                if (settings.accurateMode) "Accurate mode detects smaller faces and subtle poses at the cost of slight latency."
                else "Fast mode is optimized for maximum frame rate and battery efficiency.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(20.dp))

            // Minimum Face Size Slider
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Minimum Face Size", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text("${(settings.minFaceSize * 100).toInt()}%", fontWeight = FontWeight.Bold, color = Blue)
            }
            Slider(
                value = settings.minFaceSize,
                onValueChange = { onSettingsChanged(settings.copy(minFaceSize = it)) },
                valueRange = 0.10f..0.80f,
                steps = 14
            )
            Text(
                "Lower values detect smaller or more distant faces; higher values focus on prominent foreground faces.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(20.dp))

            // Show Per-Face Metrics Toggle
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Per-Face Metrics", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(
                        "Display smile and eye-openness probabilities next to each detected face box.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.showMetrics,
                    onCheckedChange = { onSettingsChanged(settings.copy(showMetrics = it)) }
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

/**
 * First-launch onboarding dialog explaining privacy and offline operation.
 */
@Composable
fun OnboardingDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(Blue.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = Blue, modifier = Modifier.size(32.dp))
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    "Welcome to Face Detector",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(12.dp))
                Text(
                    "• 100% On-Device: All detection models run entirely locally via Google ML Kit.\n\n" +
                            "• Zero Uploads: No network permission exists in this app. Images never leave your phone.\n\n" +
                            "• No Face Recognition: This app detects face presence and expression metrics only. It cannot and does not identify people.\n\n" +
                            "• Your Media, Your Control: Photos are only accessed via Android's Photo Picker when you choose them.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )

                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Blue),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Get Started", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * In-app privacy note dialog.
 */
@Composable
fun PrivacyNoteDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.PrivacyTip, contentDescription = null, tint = Blue) },
        title = { Text("Privacy Guarantee", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Face Detector is designed with privacy by default:\n\n" +
                            "1. No Internet Access: The application does not declare android.permission.INTERNET. It is technically impossible for the app to transmit data.\n\n" +
                            "2. No Face Identification: The bundled ML Kit detector performs geometry and landmark detection only. No facial templates or identities are generated.\n\n" +
                            "3. Scoped Storage: Android Photo Picker allows you to pick individual photos without granting storage access to your entire library.\n\n" +
                            "4. Local Frame Saving: Screenshots are saved directly to your device's Pictures folder using standard MediaStore APIs.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Blue)
            }
        }
    )
}
