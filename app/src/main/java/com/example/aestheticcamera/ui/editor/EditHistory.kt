package com.example.aestheticcamera.ui.editor

import android.graphics.PointF
import com.example.aestheticcamera.ui.camera.FilterType
import com.example.aestheticcamera.ui.editor.model.TextOverlayItem
import com.example.aestheticcamera.ui.editor.model.StickerOverlayItem
import com.example.aestheticcamera.ui.editor.model.TiltShiftConfig

data class EditState(
    val activeFilter: FilterType = FilterType.ORIGINAL,
    val filterIntensity: Float = 1.0f,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val vignette: Float = 0f,
    val grain: Float = 0f,
    val sharpness: Float = 0f,
    val rotationAngle: Float = 0f,
    val selectedCropRatio: CropRatio = CropRatio.FREE,
    val rgbCurve: List<PointF> = listOf(PointF(0f, 0f), PointF(1f, 1f)),
    val redCurve: List<PointF> = listOf(PointF(0f, 0f), PointF(1f, 1f)),
    val greenCurve: List<PointF> = listOf(PointF(0f, 0f), PointF(1f, 1f)),
    val blueCurve: List<PointF> = listOf(PointF(0f, 0f), PointF(1f, 1f)),
    val hslAdjustments: Map<HslColorChannel, HslAdjustment> = HslColorChannel.values().associateWith { HslAdjustment() },
    val lightLeakStyle: Int = 0,
    val lightLeakOpacity: Float = 0f,
    val dateStampEnabled: Boolean = false,
    val frameStyle: Int = 0,
    val textOverlays: List<TextOverlayItem> = emptyList(),
    val stickerOverlays: List<StickerOverlayItem> = emptyList(),
    val tiltShiftConfig: TiltShiftConfig = TiltShiftConfig()
)

class EditHistory(initialState: EditState) {
    private val states = mutableListOf(initialState)
    private var currentIndex = 0

    fun getCurrentState(): EditState = states[currentIndex]

    fun pushState(state: EditState) {
        // Discard redo states
        while (states.size > currentIndex + 1) {
            states.removeAt(states.size - 1)
        }
        // Only push if the state is actually different from current
        if (states[currentIndex] != state) {
            states.add(state)
            currentIndex++
        }
    }

    fun undo(): EditState? {
        if (canUndo()) {
            currentIndex--
            return states[currentIndex]
        }
        return null
    }

    fun redo(): EditState? {
        if (canRedo()) {
            currentIndex++
            return states[currentIndex]
        }
        return null
    }

    fun canUndo(): Boolean = currentIndex > 0
    fun canRedo(): Boolean = currentIndex < states.size - 1

    fun reset(initialState: EditState) {
        states.clear()
        states.add(initialState)
        currentIndex = 0
    }
}
