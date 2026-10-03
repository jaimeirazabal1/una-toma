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

/**
 * Look elegido: filtro de color + cuánto se aplica (0..1) + suavizado de piel (0..1).
 * El suavizado es independiente del filtro de color.
 */
data class FilterSelection(
    val filter: VideoFilter = VideoFilter.ORIGINAL,
    val intensity: Float = 1f,
    val smoothing: Float = 0f,
) {
    val colorIsIdentity: Boolean get() = filter == VideoFilter.ORIGINAL || intensity <= 0f
    val hasSmoothing: Boolean get() = smoothing > 0f
    val isIdentity: Boolean get() = colorIsIdentity && !hasSmoothing

    fun affine(): ColorAffine =
        if (colorIsIdentity) ColorAffine.IDENTITY else ColorAffine.IDENTITY.blend(filter.grade.toAffine(), intensity)

    companion object {
        val NONE = FilterSelection()
    }
}

/**
 * Qué look estaba activo en cada instante de la grabación. Permite cambiar de filtro o de suavizado
 * mientras grabas: la exportación aplica cada uno en su tramo.
 */
class FilterTimeline private constructor(val entries: List<Entry>) {

    data class Entry(val atMs: Long, val selection: FilterSelection)

    val hasColor: Boolean get() = entries.any { !it.selection.colorIsIdentity }
    val hasSmoothing: Boolean get() = entries.any { it.selection.hasSmoothing }
    val isIdentity: Boolean get() = !hasColor && !hasSmoothing

    fun selectionAt(ms: Long): FilterSelection =
        entries.lastOrNull { it.atMs <= ms }?.selection ?: entries.firstOrNull()?.selection ?: FilterSelection.NONE

    /** Forma con tipos primitivos, para pasarla a WorkManager (`Data` no admite objetos). */
    class Packed(
        val atMs: LongArray,
        val filters: IntArray,
        val intensities: FloatArray,
        val smoothing: FloatArray,
    )

    fun pack() = Packed(
        atMs = LongArray(entries.size) { entries[it].atMs },
        filters = IntArray(entries.size) { entries[it].selection.filter.ordinal },
        intensities = FloatArray(entries.size) { entries[it].selection.intensity },
        smoothing = FloatArray(entries.size) { entries[it].selection.smoothing },
    )

    class Builder(initial: FilterSelection) {
        private val entries = mutableListOf(Entry(0L, initial))

        /**
         * Arrastrar un slider genera decenas de cambios por segundo: los cambios muy seguidos se funden
         * en uno y hay un tope de entradas (WorkManager limita el tamaño de los datos a ~10 KB).
         */
        fun add(atMs: Long, selection: FilterSelection) {
            val last = entries.last()
            val time = maxOf(atMs, last.atMs)
            val collapse = entries.size > 1 && (time - last.atMs < MIN_GAP_MS || entries.size >= MAX_ENTRIES)
            if (collapse) entries[entries.lastIndex] = Entry(last.atMs, selection) else entries += Entry(time, selection)
        }

        fun build() = FilterTimeline(entries.toList())
    }

    companion object {
        const val MIN_GAP_MS = 200L
        const val MAX_ENTRIES = 150

        val NONE = FilterTimeline(listOf(Entry(0L, FilterSelection.NONE)))

        fun unpack(packed: Packed): FilterTimeline {
            val filters = VideoFilter.entries
            val list = packed.atMs.indices.map { i ->
                Entry(
                    packed.atMs[i],
                    FilterSelection(
                        filter = filters.getOrElse(packed.filters[i]) { VideoFilter.ORIGINAL },
                        intensity = packed.intensities[i],
                        smoothing = packed.smoothing[i],
                    ),
                )
            }
            return if (list.isEmpty()) NONE else FilterTimeline(list)
        }
    }
}
