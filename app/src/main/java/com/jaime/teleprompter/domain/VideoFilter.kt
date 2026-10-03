package com.jaime.teleprompter.domain

/** Parámetros de un "look". Valores neutros = sin cambios. */
data class ColorGrade(
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val red: Float = 1f,
    val green: Float = 1f,
    val blue: Float = 1f,
    val lift: Float = 0f,
) {
    fun toAffine(): ColorAffine = ColorAffine.saturation(saturation)
        .then(ColorAffine.gains(red, green, blue))
        .then(ColorAffine.lift(lift))
        .then(ColorAffine.contrast(contrast))
        .then(ColorAffine.brightness(brightness))
}

enum class VideoFilter(val grade: ColorGrade) {
    ORIGINAL(ColorGrade()),
    NATURAL(ColorGrade(brightness = 0.03f, contrast = 1.05f, saturation = 1.08f)),
    GLOW(ColorGrade(brightness = 0.05f, contrast = 0.92f, saturation = 1.05f, red = 1.04f, blue = 0.98f, lift = 0.04f)),
    ROSE(ColorGrade(brightness = 0.04f, contrast = 0.95f, saturation = 1.15f, red = 1.06f, green = 0.98f, blue = 1.03f)),
    WARM(ColorGrade(contrast = 1.05f, saturation = 1.1f, red = 1.08f, blue = 0.9f)),
    COOL(ColorGrade(saturation = 0.95f, red = 0.94f, green = 0.99f, blue = 1.08f)),
    VIVID(ColorGrade(contrast = 1.12f, saturation = 1.35f)),
    CINE(ColorGrade(contrast = 1.15f, saturation = 0.85f, red = 1.03f, blue = 0.95f, lift = 0.03f)),
    VINTAGE(ColorGrade(contrast = 0.95f, saturation = 0.7f, red = 1.08f, green = 1.02f, blue = 0.9f, lift = 0.08f)),
    BLACK_AND_WHITE(ColorGrade(contrast = 1.1f, saturation = 0f)),
}

/** Filtro elegido + cuánto se aplica (0 = nada, 1 = completo). */
data class FilterSelection(val filter: VideoFilter = VideoFilter.ORIGINAL, val intensity: Float = 1f) {
    val isIdentity: Boolean get() = filter == VideoFilter.ORIGINAL || intensity <= 0f

    fun affine(): ColorAffine =
        if (isIdentity) ColorAffine.IDENTITY else ColorAffine.IDENTITY.blend(filter.grade.toAffine(), intensity)

    companion object {
        val NONE = FilterSelection()
    }
}

/**
 * Qué filtro estaba activo en cada instante de la grabación. Permite cambiar de filtro mientras
 * grabas: la exportación aplica cada uno en su tramo.
 */
class FilterTimeline private constructor(private val entries: List<Entry>) {

    data class Entry(val atMs: Long, val selection: FilterSelection)

    val isIdentity: Boolean get() = entries.all { it.selection.isIdentity }

    fun selectionAt(ms: Long): FilterSelection =
        entries.lastOrNull { it.atMs <= ms }?.selection ?: entries.firstOrNull()?.selection ?: FilterSelection.NONE

    class Builder(initial: FilterSelection) {
        private val entries = mutableListOf(Entry(0L, initial))

        fun add(atMs: Long, selection: FilterSelection) {
            entries += Entry(maxOf(atMs, entries.last().atMs), selection)
        }

        fun build() = FilterTimeline(entries.toList())
    }

    companion object {
        val NONE = FilterTimeline(listOf(Entry(0L, FilterSelection.NONE)))
    }
}
