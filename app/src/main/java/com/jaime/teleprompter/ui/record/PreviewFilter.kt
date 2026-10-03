package com.jaime.teleprompter.ui.record

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.view.View
import com.jaime.teleprompter.domain.ColorAffine
import com.jaime.teleprompter.domain.FilterSelection

/**
 * Aplica el look (color + suavizado de piel) al preview en vivo.
 *
 * - Android 13+: un shader AGSL hace el suavizado y el color en una sola pasada, con el mismo
 *   algoritmo que la exportación (`data/SkinSmoothingEffect.kt`).
 * - Android 10-12: no hay shaders AGSL; se aplica solo el color (ColorMatrix). El suavizado sí
 *   sale en el video exportado, pero no se previsualiza.
 */
class PreviewFilterController(private val view: View) {

    private var selection = FilterSelection.NONE

    private val shader: RuntimeShader? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) RuntimeShader(AGSL) else null

    init {
        // El radio del suavizado depende del tamaño de la vista, que cambia al cambiar de modo.
        view.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or, ob ->
            if (r - l != or - ol || b - t != ob - ot) apply()
        }
    }

    fun update(newSelection: FilterSelection) {
        selection = newSelection
        apply()
    }

    private fun apply() {
        if (selection.isIdentity) {
            clear()
        } else if (shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            applyShader(shader)
        } else {
            applyColorFilter()
        }
    }

    private fun clear() {
        view.setLayerType(View.LAYER_TYPE_NONE, null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) view.setRenderEffect(null)
    }

    private fun applyColorFilter() {
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(selection.affine().toAndroidColorMatrix()))
        }
        view.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun applyShader(shader: RuntimeShader) {
        val color: ColorAffine = selection.affine()
        shader.setFloatUniform("uStrength", selection.smoothing)
        shader.setFloatUniform("uRadiusPx", RADIUS_FRACTION * view.width.coerceAtLeast(1))
        // AGSL usa matrices en orden de columnas.
        shader.setFloatUniform("uColorA", FloatArray(9) { color.a[(it % 3) * 3 + it / 3] })
        shader.setFloatUniform("uColorB", color.b)
        view.setLayerType(View.LAYER_TYPE_NONE, null)
        view.setRenderEffect(RenderEffect.createRuntimeShaderEffect(shader, "inputShader"))
    }

    private companion object {
        /** Igual que `RADIUS_FRACTION` del shader de exportación. */
        const val RADIUS_FRACTION = 0.0035f

        const val AGSL = """
uniform shader inputShader;
uniform float uStrength;
uniform float uRadiusPx;
uniform float3x3 uColorA;
uniform float3 uColorB;

float skinMask(float3 c) {
  float cb = 0.5 - 0.168736 * c.r - 0.331264 * c.g + 0.5 * c.b;
  float cr = 0.5 + 0.5 * c.r - 0.418688 * c.g - 0.081312 * c.b;
  return smoothstep(0.28, 0.34, cb) * (1.0 - smoothstep(0.46, 0.52, cb))
       * smoothstep(0.50, 0.54, cr) * (1.0 - smoothstep(0.66, 0.72, cr));
}

half4 main(float2 p) {
  float4 base = float4(inputShader.eval(p));
  float3 rgb = base.rgb;
  float mask = skinMask(rgb) * uStrength;
  if (mask > 0.001) {
    float3 acc = rgb;
    float wsum = 1.0;
    for (int ring = 1; ring <= 2; ring++) {
      float r = uRadiusPx * float(ring);
      float spatial = ring == 1 ? 1.0 : 0.6;
      for (int i = 0; i < 8; i++) {
        float a = float(i) * 0.785398;
        float3 s = float4(inputShader.eval(p + float2(cos(a), sin(a)) * r)).rgb;
        float3 d = s - base.rgb;
        float w = spatial * exp(-dot(d, d) * 60.0);
        acc += s * w;
        wsum += w;
      }
    }
    rgb = mix(rgb, acc / wsum, mask);
  }
  rgb = clamp(uColorA * rgb + uColorB, 0.0, 1.0);
  return half4(half3(rgb), half(base.a));
}
"""
    }
}
