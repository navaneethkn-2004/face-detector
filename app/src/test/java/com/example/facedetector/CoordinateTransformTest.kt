package com.example.facedetector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordinateTransformTest {

    @Test
    fun testExact1to1MappingUnmirrored() {
        val result = transformBox(
            boxLeft = 10,
            boxTop = 20,
            boxRight = 50,
            boxBottom = 80,
            imageWidth = 100,
            imageHeight = 100,
            viewWidth = 100f,
            viewHeight = 100f,
            mirrored = false
        )
        assertEquals(10f, result.left, 0.001f)
        assertEquals(20f, result.top, 0.001f)
        assertEquals(50f, result.right, 0.001f)
        assertEquals(80f, result.bottom, 0.001f)
        assertEquals(40f, result.width, 0.001f)
        assertEquals(60f, result.height, 0.001f)
    }

    @Test
    fun testMirroredFrontCameraFlipping() {
        // When mirrored horizontally on a 100-wide view:
        // box [10, 50] becomes [100 - 50, 100 - 10] = [50, 90]
        val result = transformBox(
            boxLeft = 10,
            boxTop = 20,
            boxRight = 50,
            boxBottom = 80,
            imageWidth = 100,
            imageHeight = 100,
            viewWidth = 100f,
            viewHeight = 100f,
            mirrored = true
        )
        assertEquals(50f, result.left, 0.001f)
        assertEquals(90f, result.right, 0.001f)
        assertEquals(20f, result.top, 0.001f)
        assertEquals(80f, result.bottom, 0.001f)
        assertEquals(40f, result.width, 0.001f)
    }

    @Test
    fun testFillCenterCropVertical() {
        // Image: 100 x 200 (aspect ratio 1:2)
        // View:  100 x 100 (aspect ratio 1:1)
        // Scale = max(100/100, 100/200) = 1.0.
        // dy = (100 - 200 * 1.0) / 2 = -50 (crops 50px top and bottom)
        val result = transformBox(
            boxLeft = 20,
            boxTop = 60,
            boxRight = 80,
            boxBottom = 160,
            imageWidth = 100,
            imageHeight = 200,
            viewWidth = 100f,
            viewHeight = 100f,
            mirrored = false
        )
        assertEquals(20f, result.left, 0.001f)
        assertEquals(80f, result.right, 0.001f)
        assertEquals(10f, result.top, 0.001f)    // 60 - 50 = 10
        assertEquals(110f, result.bottom, 0.001f) // 160 - 50 = 110
    }

    @Test
    fun testFillCenterCropHorizontal() {
        // Image: 200 x 100 (aspect ratio 2:1)
        // View:  100 x 100 (aspect ratio 1:1)
        // Scale = max(100/200, 100/100) = 1.0.
        // dx = (100 - 200 * 1.0) / 2 = -50
        val result = transformBox(
            boxLeft = 60,
            boxTop = 20,
            boxRight = 160,
            boxBottom = 80,
            imageWidth = 200,
            imageHeight = 100,
            viewWidth = 100f,
            viewHeight = 100f,
            mirrored = false
        )
        assertEquals(10f, result.left, 0.001f)    // 60 - 50 = 10
        assertEquals(110f, result.right, 0.001f)  // 160 - 50 = 110
        assertEquals(20f, result.top, 0.001f)
        assertEquals(80f, result.bottom, 0.001f)
    }

    @Test
    fun testLandscapeRotationDimensions() {
        // In landscape (90 or 270 deg), a 640x480 frame is rotated to 480x640 upright
        val (w90, h90) = getUprightDimensions(640, 480, 90)
        assertEquals(480, w90)
        assertEquals(640, h90)

        val (w180, h180) = getUprightDimensions(640, 480, 180)
        assertEquals(640, w180)
        assertEquals(480, h180)

        val (w0, h0) = getUprightDimensions(640, 480, 0)
        assertEquals(640, w0)
        assertEquals(480, h0)
    }

    @Test
    fun testEdgeCasesZeroSizes() {
        val result = transformBox(
            boxLeft = 10,
            boxTop = 10,
            boxRight = 20,
            boxBottom = 20,
            imageWidth = 0,
            imageHeight = 100,
            viewWidth = 100f,
            viewHeight = 100f,
            mirrored = false
        )
        assertEquals(0f, result.left, 0.001f)
        assertEquals(0f, result.top, 0.001f)
    }

    @Test
    fun testFaceCountSmoother3ConsecutiveFrames() {
        val smoother = FaceCountSmoother(requiredConsecutiveFrames = 3)
        assertEquals(0, smoother.smoothedCount)

        // Single frame blip to 1 face: should not change smoothed count
        smoother.update(1)
        assertEquals(0, smoother.smoothedCount)

        // Second frame: still 1 face: not yet 3 consecutive frames
        smoother.update(1)
        assertEquals(0, smoother.smoothedCount)

        // Third frame: 1 face: now 3 consecutive frames, smoothed count updates to 1!
        smoother.update(1)
        assertEquals(1, smoother.smoothedCount)

        // A single drop to 0 (noise/blink): should ignore!
        smoother.update(0)
        assertEquals(1, smoother.smoothedCount)

        // Drops back to 1: remains 1
        smoother.update(1)
        assertEquals(1, smoother.smoothedCount)

        // Count drops to 0 for 3 frames:
        smoother.update(0)
        assertEquals(1, smoother.smoothedCount)
        smoother.update(0)
        assertEquals(1, smoother.smoothedCount)
        smoother.update(0)
        assertEquals(0, smoother.smoothedCount)
    }
}
