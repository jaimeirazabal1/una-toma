package com.jaime.teleprompter.domain

/**
 * Transformación de color afín sobre RGB normalizado (0..1): `out = A * in + b`.
 *
 * Una única representación sirve para el preview (ColorMatrixColorFilter de Android) y para la
 * exportación (RgbMatrix de Media3), de modo que lo que ves al grabar es lo que se exporta.
 * Sin dependencias de Android para poder testearla en la JVM.
 */
class ColorAffine(
    /** Matriz 3x3 en orden por filas. */
    val a: FloatArray,
    val b: FloatArray,
) {
    init {
        require(a.size == 9 && b.size == 3)
    }

    /** Aplica `this` y después [next]. */
    fun then(next: ColorAffine): ColorAffine {
        val na = FloatArray(9)
        val nb = FloatArray(3)
        for (i in 0 until 3) {
            for (j in 0 until 3) {
                var sum = 0f
                for (k in 0 until 3) sum += next.a[i * 3 + k] * a[k * 3 + j]
                na[i * 3 + j] = sum
            }
            var sumB = next.b[i]
            for (k in 0 until 3) sumB += next.a[i * 3 + k] * b[k]
            nb[i] = sumB
        }
        return ColorAffine(na, nb)
    }

    /** Interpolación lineal: 0 = `this`, 1 = [other]. Sirve para el control de intensidad. */
    fun blend(other: ColorAffine, t: Float): ColorAffine = ColorAffine(
        FloatArray(9) { a[it] + (other.a[it] - a[it]) * t },
        FloatArray(3) { b[it] + (other.b[it] - b[it]) * t },
    )

    fun apply(r: Float, g: Float, bl: Float): FloatArray = FloatArray(3) { i ->
        a[i * 3] * r + a[i * 3 + 1] * g + a[i * 3 + 2] * bl + b[i]
    }

    /**
     * Matriz 4x4 en orden de columnas (OpenGL / Media3 `RgbMatrix`). El desplazamiento `b` va en la
     * columna alfa: el vídeo es opaco (alfa = 1), así que `M * (r, g, b, 1)` suma `b`.
     */
    fun toMedia3Matrix(): FloatArray {
        val m = FloatArray(16)
        for (i in 0 until 3) {
            for (j in 0 until 3) m[j * 4 + i] = a[i * 3 + j]
            m[3 * 4 + i] = b[i]
        }
        m[3 * 4 + 3] = 1f
        return m
    }

    /** Array 4x5 de `android.graphics.ColorMatrix` (los desplazamientos van en escala 0..255). */
    fun toAndroidColorMatrix(): FloatArray = floatArrayOf(
        a[0], a[1], a[2], 0f, b[0] * 255f,
        a[3], a[4], a[5], 0f, b[1] * 255f,
        a[6], a[7], a[8], 0f, b[2] * 255f,
        0f, 0f, 0f, 1f, 0f,
    )

    companion object {
        val IDENTITY = ColorAffine(floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f), floatArrayOf(0f, 0f, 0f))

        private const val LUMA_R = 0.299f
        private const val LUMA_G = 0.587f
        private const val LUMA_B = 0.114f

        fun saturation(s: Float): ColorAffine {
            val inv = 1f - s
            return ColorAffine(
                floatArrayOf(
                    inv * LUMA_R + s, inv * LUMA_G, inv * LUMA_B,
                    inv * LUMA_R, inv * LUMA_G + s, inv * LUMA_B,
                    inv * LUMA_R, inv * LUMA_G, inv * LUMA_B + s,
                ),
                floatArrayOf(0f, 0f, 0f),
            )
        }

        fun gains(r: Float, g: Float, b: Float) =
            ColorAffine(floatArrayOf(r, 0f, 0f, 0f, g, 0f, 0f, 0f, b), floatArrayOf(0f, 0f, 0f))

        /** Contraste alrededor del gris medio. */
        fun contrast(c: Float): ColorAffine {
            val offset = 0.5f * (1f - c)
            return ColorAffine(floatArrayOf(c, 0f, 0f, 0f, c, 0f, 0f, 0f, c), floatArrayOf(offset, offset, offset))
        }

        fun brightness(v: Float) = ColorAffine(IDENTITY.a.copyOf(), floatArrayOf(v, v, v))

        /** Levanta los negros (aspecto "mate"): el negro pasa a valer [l]. */
        fun lift(l: Float) =
            ColorAffine(floatArrayOf(1f - l, 0f, 0f, 0f, 1f - l, 0f, 0f, 0f, 1f - l), floatArrayOf(l, l, l))
    }
}
