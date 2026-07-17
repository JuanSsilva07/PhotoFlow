package com.example.aestheticcamera

import androidx.navigation3.runtime.NavKey
import com.example.aestheticcamera.ui.camera.GuideType
import kotlinx.serialization.Serializable

@Serializable
data object Onboarding : NavKey

@Serializable
data object Main : NavKey

@Serializable
data class Camera(val initialGuide: GuideType = GuideType.NONE) : NavKey

@Serializable
data class Editor(val imageUriString: String) : NavKey

@Serializable
data object Lessons : NavKey
