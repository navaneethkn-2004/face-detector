package com.example.facedetector

import android.graphics.Rect

/**
 * Data representing a single detected face with ML Kit classification metrics.
 */
data class DetectedFace(
    val id: Int,
    val boundingBox: Rect,
    val smilingProbability: Float? = null,
    val leftEyeOpenProbability: Float? = null,
    val rightEyeOpenProbability: Float? = null
)

/**
 * Result of face detection from either camera or gallery.
 */
data class FaceResult(
    val faces: List<DetectedFace>,
    val imageWidth: Int,
    val imageHeight: Int,
    val mirrored: Boolean,
    val rotationDegrees: Int = 0
) {
    val boxes: List<Rect> get() = faces.map { it.boundingBox }
}

/**
 * Detector configuration options.
 */
data class DetectorSettings(
    val accurateMode: Boolean = false,
    val minFaceSize: Float = 0.15f,
    val showMetrics: Boolean = true
)

/**
 * Active view mode: live camera vs static gallery photo.
 */
enum class AppMode {
    CAMERA,
    GALLERY
}
