package com.ffoptimizer.app

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin
import kotlin.random.Random

class ParticleView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    data class Particle(
        var x: Float, var y: Float,
        var speed: Float, var size: Float,
        var alpha: Int, var char: String
    )

    private val particles = mutableListOf<Particle>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
    }
    private val matrixChars = "01アイウエオカキクケコサシスセソタチツテトナニヌネノ"
    private var initialized = false
    private var lastTime = System.currentTimeMillis()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) initParticles()
    }

    private fun initParticles() {
        particles.clear()
        val cols = (width / 18f).toInt()
        repeat(cols) { col ->
            particles.add(Particle(
                x = col * 18f + 9f,
                y = Random.nextFloat() * height,
                speed = Random.nextFloat() * 3f + 1.5f,
                size = Random.nextFloat() * 5f + 9f,
                alpha = Random.nextInt(60, 200),
                char = matrixChars[Random.nextInt(matrixChars.length)].toString()
            ))
        }
        initialized = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!initialized) return

        val now = System.currentTimeMillis()
        val delta = (now - lastTime) / 16f
        lastTime = now

        for (p in particles) {
            p.y += p.speed * delta
            if (p.y > height + 20) {
                p.y = -20f
                p.char = matrixChars[Random.nextInt(matrixChars.length)].toString()
                p.alpha = Random.nextInt(60, 200)
            }

            // Glow verde
            paint.textSize = p.size
            paint.color = Color.argb((p.alpha * 0.3f).toInt(), 0, 255, 0)
            paint.maskFilter = BlurMaskFilter(p.size * 0.8f, BlurMaskFilter.Blur.NORMAL)
            canvas.drawText(p.char, p.x, p.y, paint)

            // Char principal
            paint.color = Color.argb(p.alpha, 26, 255, 26)
            paint.maskFilter = null
            canvas.drawText(p.char, p.x, p.y, paint)
        }

        postInvalidateOnAnimation()
    }
}
