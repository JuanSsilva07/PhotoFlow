package com.example.aestheticcamera.ui.camera

import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.ColorMatrix

enum class FilterType(val displayName: String) {
    ORIGINAL("Original"),
    VINTAGE("Vintage Warm"),
    MOODY("Nordic Cool"),
    CINEMATIC("Cinematic"),
    MONO("Mono B&W"),
    KODAK("Kodak Film"),
    FUJI("Fuji Film"),
    NOIR("Noir B&W"),
    GOLDEN("Golden Hour"),
    CYBERPUNK("Cyberpunk"),
    POLAROID("Polaroid Retro"),
    PASTEL("Pastel Dream")
}

object AestheticFilters {

    // 1. Vintage (Tons quentes e leve desaturação)
    val VintageMatrix = floatArrayOf(
        0.95f, 0.05f, 0.02f, 0f, 15f,
        0.05f, 0.85f, 0.02f, 0f, 10f,
        0.02f, 0.05f, 0.75f, 0f, -10f,
        0f,    0f,    0f,    1f, 0f
    )

    // 2. Moody (Tons frios, azulados e sombras marcadas)
    val MoodyMatrix = floatArrayOf(
        0.75f, 0.05f, 0.02f, 0f, -10f,
        0.05f, 0.85f, 0.05f, 0f, -5f,
        0.02f, 0.05f, 1.05f, 0f, 15f,
        0f,    0f,    0f,    1f, 0f
    )

    // 3. Cinematic (Teal & Orange clássico)
    val CinematicMatrix = floatArrayOf(
        1.10f, 0.02f, -0.02f, 0f, 10f,
        0.02f, 0.95f, 0.02f, 0f, -5f,
        -0.05f, 0.05f, 1.15f, 0f, 10f,
        0f,    0f,    0f,    1f, 0f
    )

    // 4. Mono B&W (Preto e branco clássico)
    val MonoMatrix = floatArrayOf(
        0.299f, 0.587f, 0.114f, 0f, 0f,
        0.299f, 0.587f, 0.114f, 0f, 0f,
        0.299f, 0.587f, 0.114f, 0f, 0f,
        0f,     0f,     0f,     1f, 0f
    )

    // 5. Kodak Film (Tons quentes analógicos de filme Kodak)
    val KodakMatrix = floatArrayOf(
        1.15f, 0.05f, 0.0f, 0f, 10f,
        0.05f, 1.05f, 0.0f, 0f, 5f,
        -0.05f, -0.05f, 0.90f, 0f, -10f,
        0f, 0f, 0f, 1f, 0f
    )

    // 6. Fuji Film (Tons frios e verdes suaves de filme Fuji)
    val FujiMatrix = floatArrayOf(
        0.90f, 0.10f, 0.05f, 0f, -5f,
        0.05f, 1.10f, 0.02f, 0f, 10f,
        0.02f, 0.05f, 0.95f, 0f, 5f,
        0f, 0f, 0f, 1f, 0f
    )

    // 7. Noir B&W (Preto e branco dramático de alto contraste)
    val NoirMatrix = floatArrayOf(
        0.4f, 0.4f, 0.4f, 0f, -30f,
        0.4f, 0.4f, 0.4f, 0f, -30f,
        0.4f, 0.4f, 0.4f, 0f, -30f,
        0f, 0f, 0f, 1f, 0f
    )

    // 8. Golden Hour (Tons dourados e ensolarados)
    val GoldenMatrix = floatArrayOf(
        1.20f, 0.0f, 0.0f, 0f, 20f,
        0.05f, 1.05f, 0.0f, 0f, 10f,
        -0.10f, -0.05f, 0.85f, 0f, -15f,
        0f, 0f, 0f, 1f, 0f
    )

    // 9. Cyberpunk (Neon magenta e ciano futurista)
    val CyberpunkMatrix = floatArrayOf(
        1.15f, -0.15f, 0.20f, 0f, 15f,
        0.10f, 0.85f, -0.15f, 0f, -10f,
        -0.15f, 0.20f, 1.20f, 0f, 20f,
        0f, 0f, 0f, 1f, 0f
    )

    // 10. Polaroid Retro (Visual lavado vintage com tons pastéis)
    val PolaroidMatrix = floatArrayOf(
        0.90f, 0.05f, 0.05f, 0f, 15f,
        0.05f, 0.90f, 0.05f, 0f, 15f,
        0.05f, 0.05f, 0.85f, 0f, 25f,
        0f, 0f, 0f, 1f, 0f
    )

    // 11. Pastel Dream (Visual suave, brilhante e de baixa saturação)
    val PastelMatrix = floatArrayOf(
        0.85f, 0.10f, 0.10f, 0f, 30f,
        0.10f, 0.85f, 0.10f, 0f, 30f,
        0.10f, 0.10f, 0.85f, 0f, 30f,
        0f, 0f, 0f, 1f, 0f
    )

    val IdentityMatrix = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )

    fun getColorMatrix(filter: FilterType): FloatArray? {
        return when (filter) {
            FilterType.ORIGINAL -> null
            FilterType.VINTAGE -> VintageMatrix
            FilterType.MOODY -> MoodyMatrix
            FilterType.CINEMATIC -> CinematicMatrix
            FilterType.MONO -> MonoMatrix
            FilterType.KODAK -> KodakMatrix
            FilterType.FUJI -> FujiMatrix
            FilterType.NOIR -> NoirMatrix
            FilterType.GOLDEN -> GoldenMatrix
            FilterType.CYBERPUNK -> CyberpunkMatrix
            FilterType.POLAROID -> PolaroidMatrix
            FilterType.PASTEL -> PastelMatrix
        }
    }

    fun getInterpolatedMatrix(filter: FilterType, intensity: Float): FloatArray {
        val baseFilter = getColorMatrix(filter) ?: return IdentityMatrix
        val result = FloatArray(20)
        for (i in 0 until 20) {
            result[i] = baseFilter[i] * intensity + IdentityMatrix[i] * (1f - intensity)
        }
        return result
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun createRenderEffect(filter: FilterType, intensity: Float): RenderEffect? {
        if (filter == FilterType.ORIGINAL || intensity <= 0.01f) return null
        val matrix = getInterpolatedMatrix(filter, intensity)
        val colorMatrix = android.graphics.ColorMatrix(matrix)
        val filterEffect = ColorMatrixColorFilter(colorMatrix)
        return RenderEffect.createColorFilterEffect(filterEffect)
    }
}
