package com.example.facedetector

import android.graphics.Rect

/**
 * Transformed box coordinates in the view coordinate space.
 */
data class TransformedBoxCoordinates(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
}

/**
 * Core transformation calculation using primitive coordinates.
 */
fun transformBox(
    boxLeft: Int,
    boxTop: Int,
    boxRight: Int,
    boxBottom: Int,
    imageWidth: Int,
    imageHeight: Int,
    viewWidth: Float,
    viewHeight: Float,
    mirrored: Boolean
): TransformedBoxCoordinates {
    if (imageWidth <= 0 || imageHeight <= 0 || viewWidth <= 0f || viewHeight <= 0f) {
        return TransformedBoxCoordinates(0f, 0f, 0f, 0f)
    }

    val scale = maxOf(viewWidth / imageWidth.toFloat(), viewHeight / imageHeight.toFloat())
    val dx = (viewWidth - imageWidth * scale) / 2f
    val dy = (viewHeight - imageHeight * scale) / 2f

    var left = boxLeft * scale + dx
    var right = boxRight * scale + dx
    val top = boxTop * scale + dy
    val bottom = boxBottom * scale + dy

    if (mirrored) {
        val flippedLeft = viewWidth - right
        val flippedRight = viewWidth - left
        left = flippedLeft
        right = flippedRight
    }

    return TransformedBoxCoordinates(
        left = left,
        top = top,
        right = right,
        bottom = bottom
    )
}

/**
 * Transforms an ML Kit bounding box from image coordinates into View/Canvas coordinates
 * matching PreviewView.ScaleType.FILL_CENTER.
 */
fun transformFaceBox(
    box: Rect,
    imageWidth: Int,
    imageHeight: Int,
    viewWidth: Float,
    viewHeight: Float,
    mirrored: Boolean
): TransformedBoxCoordinates {
    return transformBox(
        boxLeft = box.left,
        boxTop = box.top,
        boxRight = box.right,
        boxBottom = box.bottom,
        imageWidth = imageWidth,
        imageHeight = imageHeight,
        viewWidth = viewWidth,
        viewHeight = viewHeight,
        mirrored = mirrored
    )
}

/**
 * Normalizes image dimensions according to rotation degrees (e.g., 90 or 270 deg swapped).
 */
fun getUprightDimensions(rawWidth: Int, rawHeight: Int, rotationDegrees: Int): Pair<Int, Int> {
    return if (rotationDegrees % 180 == 0) {
        Pair(rawWidth, rawHeight)
    } else {
        Pair(rawHeight, rawWidth)
    }
}

/**
 * Smoother that stabilizes face count updates to prevent single-frame flickering.
 * A new count must be observed for [requiredConsecutiveFrames] consecutive frames before updating.
 */
class FaceCountSmoother(val requiredConsecutiveFrames: Int = 3) {
    private var candidateCount: Int = 0
    private var consecutiveCount: Int = 0
    var smoothedCount: Int = 0
        private set

    fun update(newCount: Int): Int {
        if (newCount == smoothedCount) {
            candidateCount = newCount
            consecutiveCount = requiredConsecutiveFrames
            return smoothedCount
        }

        if (newCount == candidateCount) {
            consecutiveCount++
            if (consecutiveCount >= requiredConsecutiveFrames) {
                smoothedCount = newCount
            }
        } else {
            candidateCount = newCount
            consecutiveCount = 1
            if (requiredConsecutiveFrames <= 1) {
                smoothedCount = newCount
            }
        }
        return smoothedCount
    }

    fun reset() {
        candidateCount = 0
        consecutiveCount = 0
        smoothedCount = 0
    }
}
