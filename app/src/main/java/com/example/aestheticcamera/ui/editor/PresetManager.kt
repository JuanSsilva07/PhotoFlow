package com.example.aestheticcamera.ui.editor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream

/**
 * 💾 PhotoFlow Preset Manager
 *
 * Gerencia o ciclo de vida completo de presets de edição:
 * - Salvar como JSON em SharedPreferences
 * - Exportar como arquivo `.photoflowpreset` (JSON renomeado)
 * - Importar de arquivo externo
 * - Aplicar ao EditState atual
 */
object PresetManager {

    private const val PREFS_KEY  = "photoflow_presets"
    private const val PREFS_NAME = "photoflow_editor"

    // ─── Modelo de Preset ──────────────────────────────────────────────────

    data class Preset(
        val name: String,
        val createdAt: Long = System.currentTimeMillis(),
        // Ajustes básicos
        val brightness: Float     = 0f,
        val contrast: Float       = 0f,
        val saturation: Float     = 0f,
        val warmth: Float         = 0f,
        val highlights: Float     = 0f,
        val shadows: Float        = 0f,
        val vignette: Float       = 0f,
        val grain: Float          = 0f,
        val sharpness: Float      = 0f,
        val vignetteShape: String = "ELLIPSE",
        // Filtro
        val filterName: String    = "ORIGINAL",
        val filterIntensity: Float = 1.0f,
        // Light leak & Frame
        val lightLeakStyle: Int   = 0,
        val lightLeakOpacity: Float = 0f,
        val frameStyle: Int       = 0
    )

    // ─── Serialização ──────────────────────────────────────────────────────

    fun EditState.toPreset(name: String): Preset = Preset(
        name            = name,
        brightness      = brightness,
        contrast        = contrast,
        saturation      = saturation,
        warmth          = warmth,
        highlights      = highlights,
        shadows         = shadows,
        vignette        = vignette,
        grain           = grain,
        sharpness       = sharpness,
        vignetteShape   = vignetteShape,
        filterName      = activeFilter.name,
        filterIntensity = filterIntensity,
        lightLeakStyle  = lightLeakStyle,
        lightLeakOpacity= lightLeakOpacity,
        frameStyle      = frameStyle
    )

    fun Preset.applyTo(state: EditState): EditState {
        val filter = try {
            com.example.aestheticcamera.ui.camera.FilterType.valueOf(filterName)
        } catch (_: Exception) {
            com.example.aestheticcamera.ui.camera.FilterType.ORIGINAL
        }
        return state.copy(
            brightness       = brightness,
            contrast         = contrast,
            saturation       = saturation,
            warmth           = warmth,
            highlights       = highlights,
            shadows          = shadows,
            vignette         = vignette,
            grain            = grain,
            sharpness        = sharpness,
            vignetteShape    = vignetteShape,
            activeFilter     = filter,
            filterIntensity  = filterIntensity,
            lightLeakStyle   = lightLeakStyle,
            lightLeakOpacity = lightLeakOpacity,
            frameStyle       = frameStyle,
            presetName       = name
        )
    }

    private fun Preset.toJson(): JSONObject = JSONObject().apply {
        put("name",             name)
        put("createdAt",        createdAt)
        put("brightness",       brightness)
        put("contrast",         contrast)
        put("saturation",       saturation)
        put("warmth",           warmth)
        put("highlights",       highlights)
        put("shadows",          shadows)
        put("vignette",         vignette)
        put("grain",            grain)
        put("sharpness",        sharpness)
        put("vignetteShape",    vignetteShape)
        put("filter",           filterName)
        put("filterIntensity",  filterIntensity)
        put("lightLeakStyle",   lightLeakStyle)
        put("lightLeakOpacity", lightLeakOpacity)
        put("frameStyle",       frameStyle)
    }

    private fun JSONObject.toPreset(): Preset = Preset(
        name             = optString("name", "Preset"),
        createdAt        = optLong("createdAt", System.currentTimeMillis()),
        brightness       = optDouble("brightness", 0.0).toFloat(),
        contrast         = optDouble("contrast", 0.0).toFloat(),
        saturation       = optDouble("saturation", 0.0).toFloat(),
        warmth           = optDouble("warmth", 0.0).toFloat(),
        highlights       = optDouble("highlights", 0.0).toFloat(),
        shadows          = optDouble("shadows", 0.0).toFloat(),
        vignette         = optDouble("vignette", 0.0).toFloat(),
        grain            = optDouble("grain", 0.0).toFloat(),
        sharpness        = optDouble("sharpness", 0.0).toFloat(),
        vignetteShape    = optString("vignetteShape", "ELLIPSE"),
        filterName       = optString("filter", "ORIGINAL"),
        filterIntensity  = optDouble("filterIntensity", 1.0).toFloat(),
        lightLeakStyle   = optInt("lightLeakStyle", 0),
        lightLeakOpacity = optDouble("lightLeakOpacity", 0.0).toFloat(),
        frameStyle       = optInt("frameStyle", 0)
    )

    // ─── SharedPreferences CRUD ────────────────────────────────────────────

    /** Retorna todos os presets salvos localmente */
    fun loadAll(context: Context): List<Preset> {
        val prefs   = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(PREFS_KEY, "[]") ?: "[]"
        return try {
            val arr = JSONArray(jsonStr)
            (0 until arr.length()).map { arr.getJSONObject(it).toPreset() }
        } catch (_: Exception) { emptyList() }
    }

    /** Salva um preset. Se já existir um com mesmo nome, sobrescreve. */
    @Synchronized
    fun save(context: Context, preset: Preset) {
        val prefs   = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = loadAll(context).filter { it.name != preset.name }.toMutableList()
        current.add(0, preset) // mais recente primeiro

        val arr = JSONArray()
        current.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(PREFS_KEY, arr.toString()).apply()
    }

    /** Remove um preset pelo nome */
    @Synchronized
    fun delete(context: Context, name: String) {
        val prefs   = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = loadAll(context).filter { it.name != name }
        val arr     = JSONArray()
        current.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(PREFS_KEY, arr.toString()).apply()
    }

    // ─── Exportação de Arquivo ────────────────────────────────────────────

    /**
     * Exporta um preset como arquivo `.youngpreset` via Intent.ACTION_SEND
     * Retorna o Intent pronto para ser lançado com `startActivity()`
     */
    fun exportIntent(context: Context, preset: Preset): Intent {
        val json     = preset.toJson().toString(2) // pretty-print
        val fileName = "${preset.name.replace(" ", "_")}.youngpreset"
        val file     = File(context.cacheDir, fileName)
        file.writeText(json, Charsets.UTF_8)

        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )

        return Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "PhotoFlow Preset: ${preset.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    // ─── Importação de Arquivo ────────────────────────────────────────────

    /**
     * Importa um preset de um arquivo `.youngpreset` obtido de um Intent de abertura de arquivo.
     * Retorna o Preset se a leitura for bem-sucedida, ou null em caso de erro.
     */
    fun importFromStream(stream: InputStream): Preset? {
        return try {
            val json = stream.bufferedReader().use { it.readText() }
            JSONObject(json).toPreset()
        } catch (_: Exception) { null }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    /**
     * Formata timestamp de criação para exibição amigável
     * Ex: "há 2 dias" / "hoje"
     */
    fun formatRelativeTime(createdAt: Long): String {
        val diffMs  = System.currentTimeMillis() - createdAt
        val diffMin = diffMs / 60_000
        val diffH   = diffMs / 3_600_000
        val diffD   = diffMs / 86_400_000
        return when {
            diffMin < 1  -> "agora"
            diffMin < 60 -> "há ${diffMin}min"
            diffH   < 24 -> "há ${diffH}h"
            diffD   == 1L -> "ontem"
            diffD   < 30 -> "há ${diffD} dias"
            else         -> "há mais de um mês"
        }
    }
}
