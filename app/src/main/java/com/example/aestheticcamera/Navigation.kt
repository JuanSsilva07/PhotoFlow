package com.example.aestheticcamera

import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.aestheticcamera.ui.camera.CameraScreen
import com.example.aestheticcamera.ui.camera.CameraViewModel
import com.example.aestheticcamera.ui.editor.EditorScreen
import com.example.aestheticcamera.ui.lessons.LessonsScreen
import com.example.aestheticcamera.ui.main.MainScreen
import com.example.aestheticcamera.ui.onboarding.OnboardingScreen

@Composable
fun MainNavigation() {
  val context = LocalContext.current
  val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
  val hasSeenOnboarding = remember { prefs.getBoolean("has_seen_onboarding", false) }
  val startDestination = if (hasSeenOnboarding) Main else Onboarding

  val backStack = rememberNavBackStack(startDestination)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Onboarding> {
          OnboardingScreen(
            onFinishOnboarding = {
              prefs.edit().putBoolean("has_seen_onboarding", true).apply()
              backStack.removeLastOrNull()
              backStack.add(Main)
            }
          )
        }
        entry<Main> {
          MainScreen(
            onNavigateToCamera = { backStack.add(Camera()) },
            onNavigateToEditor = { uri -> backStack.add(Editor(uri)) },
            onNavigateToLessons = { backStack.add(Lessons) }
          )
        }
        entry<Camera> { cameraKey ->
          val cameraViewModel: CameraViewModel = viewModel()
          // Configurar o guia inicial se a navegação partiu de uma lição
          if (cameraKey.initialGuide != com.example.aestheticcamera.ui.camera.GuideType.NONE) {
            cameraViewModel.selectGuide(cameraKey.initialGuide)
          }
          CameraScreen(
            onNavigateToEditor = { uri -> backStack.add(Editor(uri)) },
            onNavigateBack = { backStack.removeLastOrNull() },
            viewModel = cameraViewModel
          )
        }
        entry<Editor> { editorKey ->
          EditorScreen(
            imageUriString = editorKey.imageUriString,
            onNavigateBack = { backStack.removeLastOrNull() }
          )
        }
        entry<Lessons> {
          LessonsScreen(
            onSelectGuideAndShoot = { guide -> backStack.add(Camera(initialGuide = guide)) },
            onNavigateBack = { backStack.removeLastOrNull() }
          )
        }
      },
  )
}
