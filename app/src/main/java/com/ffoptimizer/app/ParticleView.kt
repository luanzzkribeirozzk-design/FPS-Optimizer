package com.ffoptimizer.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class ParticleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    data class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var radius: Float,
        var alpha: Int,
        var alphaDir: Int,
        var color: Int,
        var pulsePhase: Float
    )

    private val particles = mutableListOf<Particle>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val neonColors = listOf(
        Color.parseColor("#00FFEA"),
        Color.parseColor("#FF6FFF"),
        Color.parseColor("#00FF88"),
        Color.parseColor("#FF9D00"),
        Color.parseColor("#00BFFF")
    )

    private var initialized = false
    private var lastTime = System.currentTimeMillis()

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    private fun initParticles() {
        particles.clear()
        val count = 40
        repeat(count) {
            particles.add(
                Particle(
                    x = Random.nextFloat() * width,
                    y = Random.nextFloat() * height,
                    vx = (Random.nextFloat() - 0.5f) * 0.8f,
                    vy = (Random.nextFloat() - 0.5f) * 0.8f,
                    radius = Random.nextFloat() * 3f + 1f,
                    alpha = Random.nextInt(80, 220),
                    alphaDir = if (Random.nextBoolean()) 1 else -1,
                    color = neonColors[Random.nextInt(neonColors.size)],
                    pulsePhase = Random.nextFloat() * Math.PI.toFloat() * 2f
                )
            )
        }
        initialized = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) initParticles()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!initialized || width == 0) return

        val now = System.currentTimeMillis()
        val delta = (now - lastTime) / 16f
        lastTime = now

        for (p in particles) {
            // Move
            p.x += p.vx * delta
            p.y += p.vy * delta

            // Wrap around edges
            if (p.x < -10) p.x = width + 10f
            if (p.x > width + 10) p.x = -10f
            if (p.y < -10) p.y = height + 10f
            if (p.y > height + 10) p.y = -10f

            // Pulse alpha
            p.pulsePhase += 0.03f * delta
            val pulse = ((sin(p.pulsePhase.toDouble()) + 1.0) / 2.0).toFloat()
            val currentAlpha = (60 + pulse * 160).toInt().coerceIn(0, 255)

            val r = Color.red(p.color)
            val g = Color.green(p.color)
            val b = Color.blue(p.color)

            // Glow outer
            glowPaint.alpha = (currentAlpha * 0.3f).toInt()
            glowPaint.color = p.color
            glowPaint.maskFilter = android.graphics.BlurMaskFilter(
                p.radius * 6f,
                android.graphics.BlurMaskFilter.Blur.NORMAL
            )
            canvas.drawCircle(p.x, p.y, p.radius * 3f, glowPaint)

            // Core dot
            paint.alpha = currentAlpha
            paint.color = Color.argb(currentAlpha, r, g, b)
            paint.maskFilter = null
            canvas.drawCircle(p.x, p.y, p.radius, paint)
        }

        // Draw faint connection lines between close particles
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = 0.5f
            style = Paint.Style.STROKE
        }
        for (i in particles.indices) {
            for (j in i + 1 until particles.size) {
                val p1 = particles[i]
                val p2 = particles[j]
                val dx = p1.x - p2.x
                val dy = p1.y - p2.y
                val dist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                if (dist < 120f) {
                    val lineAlpha = ((1f - dist / 120f) * 40f).toInt()
                    linePaint.color = Color.argb(lineAlpha, 0, 255, 234)
                    canvas.drawLine(p1.x, p1.y, p2.x, p2.y, linePaint)
                }
            }
        }

        postInvalidateOnAnimation()
    }
}
