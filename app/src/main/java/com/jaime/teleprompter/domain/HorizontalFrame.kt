package com.jaime.teleprompter.domain

/**
 * Geometría de la franja 16:9 que se recorta del cuadro vertical para el video horizontal.
 * La franja ocupa todo el ancho; lo único que se elige es a qué altura está (reencuadre).
 */
object HorizontalFrame {
    const val DEFAULT_CENTER = 0.5f

    /** Fracción de la altura que ocupa la franja 16:9 en un cuadro 9:16 exacto (81/256). */
    const val PORTRAIT_BAND_FRACTION = 81f / 256f

    /** Fracción de la altura del cuadro (ya orientado) que ocupa una franja 16:9. */
    fun bandFraction(frameWidth: Int, frameHeight: Int): Float =
        (frameWidth * 9f / 16f) / frameHeight

    /** Limita el centro para que la franja nunca se salga del cuadro. */
    fun clampCenter(center: Float, bandFraction: Float): Float =
        center.coerceIn(bandFraction / 2f, 1f - bandFraction / 2f)

    /**
     * Límites verticales del recorte en coordenadas normalizadas de dispositivo (-1..1, y hacia arriba),
     * que es lo que espera `Crop` de Media3. Devuelve (bottom, top).
     */
    fun cropNdc(center: Float, bandFraction: Float): Pair<Float, Float> {
        val c = clampCenter(center, bandFraction)
        val top = 1f - 2f * (c - bandFraction / 2f)
        val bottom = 1f - 2f * (c + bandFraction / 2f)
        return bottom to top
    }
}
