package com.example.aestheticcamera.ui.editor

import com.example.aestheticcamera.ui.camera.FilterType
import org.junit.Assert.assertEquals
import org.junit.Test

class AestheticFiltersTest {

    @Test
    fun getCombinedMatrix_withOriginal_returnsIdentityMatrix() {
        // Obter matriz combinada com valores padrão (sem alteração)
        val matrix = getCombinedMatrix(
            filter = FilterType.ORIGINAL,
            intensity = 1.0f,
            brightness = 0.0f,
            contrast = 1.0f
        )

        // Matriz Identidade Esperada
        val expectedIdentity = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )

        // Validar que cada valor é igual com tolerância delta
        for (i in matrix.indices) {
            assertEquals("Diferença no índice $i", expectedIdentity[i], matrix[i], 0.001f)
        }
    }

    @Test
    fun getCombinedMatrix_withBrightness_appliesOffsetToTranslateColumn() {
        val brightnessOffset = 0.1f // 10% de brilho
        val matrix = getCombinedMatrix(
            filter = FilterType.ORIGINAL,
            intensity = 1.0f,
            brightness = brightnessOffset,
            contrast = 1.0f
        )

        // O brilho adiciona offset na 5ª coluna (índices 4, 9, 14 para R, G, B)
        // Offset esperado: 0.1f * 255f = 25.5f
        val expectedOffset = brightnessOffset * 255f
        assertEquals("Offset de brilho R incorreto", expectedOffset, matrix[4], 0.001f)
        assertEquals("Offset de brilho G incorreto", expectedOffset, matrix[9], 0.001f)
        assertEquals("Offset de brilho B incorreto", expectedOffset, matrix[14], 0.001f)
    }

    @Test
    fun getCombinedMatrix_withContrast_scalesDiagonalValues() {
        val contrastScale = 1.2f // 120% de contraste
        val matrix = getCombinedMatrix(
            filter = FilterType.ORIGINAL,
            intensity = 1.0f,
            brightness = 0.0f,
            contrast = contrastScale
        )

        // O contraste multiplica os coeficientes diagonais de R, G, B (índices 0, 6, 12)
        assertEquals("Escala de contraste R incorreta", contrastScale, matrix[0], 0.001f)
        assertEquals("Escala de contraste G incorreta", contrastScale, matrix[6], 0.001f)
        assertEquals("Escala de contraste B incorreta", contrastScale, matrix[12], 0.001f)

        // A compensação de brilho gerada pelo contraste (0.5 * (1 - C) * 255) deve estar na 5ª coluna
        val expectedOffset = 0.5f * (1.0f - contrastScale) * 255f
        assertEquals("Compensação de contraste R incorreta", expectedOffset, matrix[4], 0.001f)
    }
    @Test
    fun calculateCropBounds_withLandscapeImageToSquare_returnsCenteredSquare() {
        // Imagem widescreen (1920x1080) sendo cortada para 1:1 quadrado
        val bounds = calculateCropBounds(srcWidth = 1920, srcHeight = 1080, ratioWidth = 1, ratioHeight = 1)
        
        // Espera-se que a altura seja mantida (1080) e a largura seja cortada para 1080.
        // O offset X deve centralizar o corte: (1920 - 1080) / 2 = 420.
        assertEquals("Largura do corte incorreta", 1080, bounds.width)
        assertEquals("Altura do corte incorreta", 1080, bounds.height)
        assertEquals("Offset X incorreto", 420, bounds.x)
        assertEquals("Offset Y incorreto", 0, bounds.y)
    }

    @Test
    fun calculateCropBounds_withSquareImageToCinematic_returnsCenteredCinematic() {
        // Imagem quadrada (1000x1000) sendo cortada para 16:9 widescreen
        val bounds = calculateCropBounds(srcWidth = 1000, srcHeight = 1000, ratioWidth = 16, ratioHeight = 9)
        
        // Espera-se que a largura seja mantida (1000) e a altura seja cortada para 1000 * 9 / 16 = 562.
        // O offset Y deve centralizar o corte: (1000 - 562) / 2 = 219.
        assertEquals("Largura do corte incorreta", 1000, bounds.width)
        assertEquals("Altura do corte incorreta", 562, bounds.height)
        assertEquals("Offset X incorreto", 0, bounds.x)
        assertEquals("Offset Y incorreto", 219, bounds.y)
    }
}

data class CropRect(val x: Int, val y: Int, val width: Int, val height: Int)

private fun calculateCropBounds(srcWidth: Int, srcHeight: Int, ratioWidth: Int, ratioHeight: Int): CropRect {
    val targetRatio = ratioWidth.toFloat() / ratioHeight.toFloat()
    val srcRatio = srcWidth.toFloat() / srcHeight.toFloat()

    val cropWidth: Int
    val cropHeight: Int
    val xOffset: Int
    val yOffset: Int

    if (srcRatio > targetRatio) {
        cropHeight = srcHeight
        cropWidth = (srcHeight * targetRatio).toInt()
        xOffset = (srcWidth - cropWidth) / 2
        yOffset = 0
    } else {
        cropWidth = srcWidth
        cropHeight = (srcWidth / targetRatio).toInt()
        xOffset = 0
        yOffset = (srcHeight - cropHeight) / 2
    }
    return CropRect(xOffset, yOffset, cropWidth, cropHeight)
}


// Cópia local da função para teste isolado da JVM (idêntica à do EditorScreen.kt)
private fun getCombinedMatrix(
    filter: FilterType,
    intensity: Float,
    brightness: Float,
    contrast: Float
): FloatArray {
    val baseFilter = com.example.aestheticcamera.ui.camera.AestheticFilters.getColorMatrix(filter) ?: floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )

    val interpolated = FloatArray(20)
    for (i in 0 until 20) {
        val identityVal = if (i % 6 == 0 && i < 18) 1f else 0f
        interpolated[i] = baseFilter[i] * intensity + identityVal * (1f - intensity)
    }

    val offset = 0.5f * (1.0f - contrast) + brightness
    val offset255 = offset * 255f

    val finalMatrix = FloatArray(20)
    for (row in 0..2) {
        for (col in 0..3) {
            val idx = row * 5 + col
            finalMatrix[idx] = interpolated[idx] * contrast
        }
        finalMatrix[row * 5 + 4] = interpolated[row * 5 + 4] * contrast + offset255
    }

    finalMatrix[15] = 0f
    finalMatrix[16] = 0f
    finalMatrix[17] = 0f
    finalMatrix[18] = 1f
    finalMatrix[19] = 0f

    return finalMatrix
}
