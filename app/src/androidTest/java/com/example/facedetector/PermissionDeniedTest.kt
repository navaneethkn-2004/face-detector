package com.example.facedetector

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PermissionDeniedTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testStatusPillShowsPermissionNeededWhenNoPermission() {
        composeTestRule.setContent {
            FaceDetectorTheme {
                StatusPill(
                    hasPermission = false,
                    error = null,
                    cameraOn = true,
                    isPaused = false,
                    faceCount = 0,
                    appMode = AppMode.CAMERA
                )
            }
        }

        // Verify the status pill correctly displays the permission needed state
        composeTestRule.onNodeWithText("Camera permission needed").assertIsDisplayed()
    }

    @Test
    fun testStatusPillShowsCameraStoppedState() {
        composeTestRule.setContent {
            FaceDetectorTheme {
                StatusPill(
                    hasPermission = true,
                    error = null,
                    cameraOn = false,
                    isPaused = false,
                    faceCount = 0,
                    appMode = AppMode.CAMERA
                )
            }
        }

        composeTestRule.onNodeWithText("Camera stopped").assertIsDisplayed()
    }

    @Test
    fun testStatusPillShowsFacesDetected() {
        composeTestRule.setContent {
            FaceDetectorTheme {
                StatusPill(
                    hasPermission = true,
                    error = null,
                    cameraOn = true,
                    isPaused = false,
                    faceCount = 2,
                    appMode = AppMode.CAMERA
                )
            }
        }

        composeTestRule.onNodeWithText("2 faces detected").assertIsDisplayed()
    }
}
