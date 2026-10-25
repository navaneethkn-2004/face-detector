package com.example.facedetector

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max

object ImageUtils {

    /**
     * Loads a properly oriented bitmap from a content Uri, constraining max dimensions.
     */
    suspend fun loadUprightBitmap(context: Context, uri: Uri, maxDimension: Int = 2048): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                // Read bounds first
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)
                }

                var sampleSize = 1
                val largest = max(options.outWidth, options.outHeight)
                while (largest / sampleSize > maxDimension) {
                    sampleSize *= 2
                }

                // Decode bitmap with calculated sample size
                val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val rawBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOptions)
                } ?: return@withContext null

                // Correct Exif orientation
                val orientation = context.contentResolver.openInputStream(uri)?.use { stream ->
                    try {
                        val exif = ExifInterface(stream)
                        exif.getAttributeInt(
                            ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL
                        )
                    } catch (e: Exception) {
                        ExifInterface.ORIENTATION_NORMAL
                    }
                } ?: ExifInterface.ORIENTATION_NORMAL

                val matrix = Matrix()
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                }

                if (!matrix.isIdentity) {
                    val rotated = Bitmap.createBitmap(
                        rawBitmap,
                        0,
                        0,
                        rawBitmap.width,
                        rawBitmap.height,
                        matrix,
                        true
                    )
                    if (rotated != rawBitmap) {
                        rawBitmap.recycle()
                    }
                    rotated
                } else {
                    rawBitmap
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    /**
     * Runs face detection on a static bitmap with the given settings.
     */
    suspend fun detectFacesOnBitmap(
        bitmap: Bitmap,
        settings: DetectorSettings
    ): FaceResult = suspendCancellableCoroutine { continuation ->
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(
                if (settings.accurateMode) FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE
                else FaceDetectorOptions.PERFORMANCE_MODE_FAST
            )
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(settings.minFaceSize)
            .build()

        val detector = FaceDetection.getClient(options)
        val inputImage = InputImage.fromBitmap(bitmap, 0)

        detector.process(inputImage)
            .addOnSuccessListener { faces ->
                val detected = faces.mapIndexed { idx, f ->
                    DetectedFace(
                        id = f.trackingId ?: idx,
                        boundingBox = f.boundingBox,
                        smilingProbability = f.smilingProbability,
                        leftEyeOpenProbability = f.leftEyeOpenProbability,
                        rightEyeOpenProbability = f.rightEyeOpenProbability
                    )
                }
                detector.close()
                continuation.resume(
                    FaceResult(
                        faces = detected,
                        imageWidth = bitmap.width,
                        imageHeight = bitmap.height,
                        mirrored = false,
                        rotationDegrees = 0
                    )
                )
            }
            .addOnFailureListener { exception ->
                detector.close()
                continuation.resumeWithException(exception)
            }
    }
}
