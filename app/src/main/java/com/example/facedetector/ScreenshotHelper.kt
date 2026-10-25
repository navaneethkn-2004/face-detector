package com.example.facedetector

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object ScreenshotHelper {

    /**
     * Draws detected face boxes and optional metrics onto a copy of the source bitmap,
     * then saves it to MediaStore Pictures/FaceDetector.
     */
    suspend fun saveAnnotatedImage(
        context: Context,
        sourceBitmap: Bitmap,
        faces: List<DetectedFace>,
        mirrored: Boolean,
        showMetrics: Boolean
    ): String? = withContext(Dispatchers.IO) {
        try {
            val annotated = sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(annotated)

            val boxPaint = Paint().apply {
                color = android.graphics.Color.parseColor("#4FC3F7")
                style = Paint.Style.STROKE
                strokeWidth = maxOf(4f, annotated.width * 0.006f)
                isAntiAlias = true
            }

            val textBgPaint = Paint().apply {
                color = android.graphics.Color.parseColor("#CC0D2B5C")
                style = Paint.Style.FILL
                isAntiAlias = true
            }

            val textPaint = Paint().apply {
                color = AndroidColor.WHITE
                textSize = maxOf(24f, annotated.width * 0.03f)
                isAntiAlias = true
            }

            val cornerRadius = maxOf(12f, annotated.width * 0.015f)

            for (face in faces) {
                val b = face.boundingBox
                var left = b.left.toFloat()
                var right = b.right.toFloat()
                if (mirrored) {
                    val flippedLeft = annotated.width - right
                    val flippedRight = annotated.width - left
                    left = flippedLeft
                    right = flippedRight
                }
                val rectF = RectF(left, b.top.toFloat(), right, b.bottom.toFloat())
                canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, boxPaint)

                if (showMetrics) {
                    val smileText = face.smilingProbability?.let { "😊 ${(it * 100).toInt()}%" } ?: ""
                    val eyeText = if (face.leftEyeOpenProbability != null && face.rightEyeOpenProbability != null) {
                        "👁️ L:${(face.leftEyeOpenProbability * 100).toInt()}% R:${(face.rightEyeOpenProbability * 100).toInt()}%"
                    } else ""

                    val label = listOf(smileText, eyeText).filter { it.isNotEmpty() }.joinToString("  ")
                    if (label.isNotEmpty()) {
                        val textBounds = Rect()
                        textPaint.getTextBounds(label, 0, label.length, textBounds)
                        val badgeY = maxOf(textBounds.height().toFloat() + 16f, rectF.top - 12f)
                        val badgeBg = RectF(
                            rectF.left,
                            badgeY - textBounds.height() - 10f,
                            rectF.left + textBounds.width() + 20f,
                            badgeY + 8f
                        )
                        canvas.drawRoundRect(badgeBg, 8f, 8f, textBgPaint)
                        canvas.drawText(label, rectF.left + 10f, badgeY, textPaint)
                    }
                }
            }

            saveToMediaStore(context, annotated)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun saveToMediaStore(context: Context, bitmap: Bitmap): String? {
        val filename = "FaceDetector_${System.currentTimeMillis()}.jpg"
        val resolver = context.contentResolver

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/FaceDetector")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val uri: Uri? = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
                }
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
                "Saved to Pictures/FaceDetector/$filename"
            } else null
        } else {
            // Legacy Android fallback: write to public pictures directory or app files
            try {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val targetDir = File(picturesDir, "FaceDetector")
                if (!targetDir.exists()) targetDir.mkdirs()
                val file = File(targetDir, filename)
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }
                "Saved to ${file.name}"
            } catch (e: Exception) {
                // If permission fails on legacy, save to app specific external files
                val appDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                val file = File(appDir, filename)
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }
                "Saved to Pictures/$filename"
            }
        }
    }
}
