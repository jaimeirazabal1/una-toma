package com.jaime.teleprompter.data

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.util.Size
import androidx.annotation.OptIn
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.jaime.teleprompter.domain.FilterTimeline

/**
 * Suavizado de piel con shader propio: un filtro bilateral de 16 muestras (dos anillos) que
 * promedia solo con vecinos de color parecido —así conserva los bordes de ojos, labios y pelo—
 * aplicado únicamente a píxeles con tono de piel (máscara en YCbCr). La fuerza varía con el
 * tiempo según la línea de tiempo de la grabación.
 *
 * El mismo algoritmo existe en AGSL para el preview en vivo (`ui/record/PreviewFilter.kt`);
 * si cambias uno, cambia el otro.
 */
@OptIn(UnstableApi::class)
class SkinSmoothingEffect(private val timeline: FilterTimeline) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        SkinSmoothingProgram(timeline)

    private class SkinSmoothingProgram(private val timeline: FilterTimeline) :
        BaseGlShaderProgram(/* useHighPrecisionColorComponents = */ false, /* texturePoolCapacity = */ 1) {

        private val program: GlProgram = try {
            GlProgram(VERTEX_SHADER, FRAGMENT_SHADER).apply {
                setBufferAttribute(
                    "aFramePosition",
                    GlUtil.getNormalizedCoordinateBounds(),
                    GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE,
                )
                setFloatsUniform("uTransformationMatrix", GlUtil.create4x4IdentityMatrix())
                setFloatsUniform("uTexTransformationMatrix", GlUtil.create4x4IdentityMatrix())
                setFloatUniform("uRadius", RADIUS_FRACTION)
            }
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }

        override fun configure(inputWidth: Int, inputHeight: Int): Size {
            program.setFloatUniform("uAspect", inputWidth.toFloat() / inputHeight)
            return Size(inputWidth, inputHeight)
        }

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            try {
                program.use()
                program.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex = */ 0)
                program.setFloatUniform("uStrength", timeline.selectionAt(presentationTimeUs / 1_000).smoothing)
                program.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e, presentationTimeUs)
            }
        }

        override fun release() {
            super.release()
            try {
                program.delete()
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e)
            }
        }
    }

    private companion object {
        /** Radio del primer anillo como fracción del ancho del cuadro: independiente de la resolución. */
        const val RADIUS_FRACTION = 0.0035f

        const val VERTEX_SHADER = """
attribute vec4 aFramePosition;
uniform mat4 uTransformationMatrix;
uniform mat4 uTexTransformationMatrix;
varying vec2 vTexSamplingCoord;
void main() {
  gl_Position = uTransformationMatrix * aFramePosition;
  vec4 texturePosition = vec4(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5, 0.0, 1.0);
  vTexSamplingCoord = (uTexTransformationMatrix * texturePosition).xy;
}
"""

        const val FRAGMENT_SHADER = """
precision highp float;
uniform sampler2D uTexSampler;
uniform float uStrength;
uniform float uAspect;
uniform float uRadius;
varying vec2 vTexSamplingCoord;

float skinMask(vec3 c) {
  float cb = 0.5 - 0.168736 * c.r - 0.331264 * c.g + 0.5 * c.b;
  float cr = 0.5 + 0.5 * c.r - 0.418688 * c.g - 0.081312 * c.b;
  return smoothstep(0.28, 0.34, cb) * (1.0 - smoothstep(0.46, 0.52, cb))
       * smoothstep(0.50, 0.54, cr) * (1.0 - smoothstep(0.66, 0.72, cr));
}

void main() {
  vec4 base = texture2D(uTexSampler, vTexSamplingCoord);
  float mask = skinMask(base.rgb) * uStrength;
  if (mask < 0.001) {
    gl_FragColor = base;
    return;
  }
  vec3 acc = base.rgb;
  float wsum = 1.0;
  for (int ring = 1; ring <= 2; ring++) {
    float r = uRadius * float(ring);
    float spatial = ring == 1 ? 1.0 : 0.6;
    for (int i = 0; i < 8; i++) {
      float a = float(i) * 0.785398;
      vec2 offset = vec2(cos(a), sin(a) * uAspect) * r;
      vec3 s = texture2D(uTexSampler, vTexSamplingCoord + offset).rgb;
      vec3 d = s - base.rgb;
      float w = spatial * exp(-dot(d, d) * 60.0);
      acc += s * w;
      wsum += w;
    }
  }
  gl_FragColor = vec4(mix(base.rgb, acc / wsum, mask), base.a);
}
"""
    }
}
