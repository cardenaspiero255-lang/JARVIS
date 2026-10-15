package com.cardenaspiero255.jarvis

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import com.cardenaspiero255.jarvis.core.OmegaMotionPolicy
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Low-overhead OMEGA reactor: the supplied emblem is a static bitmap;
 * only two small pre-rendered orbit layers rotate on the render thread.
 * No Choreographer loops, network, sensors or per-frame bitmap allocations.
 */
class OmegaPulseCore(context: Context) : FrameLayout(context) {
    private val policy = OmegaMotionPolicy()
    private val outer = OrbitRing(context, false)
    private val inner = OrbitRing(context, true)
    private val center = ImageView(context).apply {
        setImageResource(R.drawable.jarvis_reference)
        contentDescription = "JARVIS OMEGA: núcleo de inteligencia artificial"
        scaleType = ImageView.ScaleType.FIT_CENTER
    }
    private val outerSpin = ObjectAnimator.ofFloat(outer, View.ROTATION, 0f, 360f).apply {
        duration = 20_000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
    }
    private val innerSpin = ObjectAnimator.ofFloat(inner, View.ROTATION, 360f, 0f).apply {
        duration = 13_000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
    }
    private var attached = false
    private var focused = false
    private var scrollActive = false

    init {
        val size = dp(276)
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(294))
        minimumHeight = dp(294)
        isClickable = false
        isFocusable = false
        outer.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        inner.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        addView(outer, LayoutParams(size, size, Gravity.CENTER))
        addView(inner, LayoutParams(dp(244), dp(244), Gravity.CENTER))
        addView(center, LayoutParams(dp(192), dp(192), Gravity.CENTER))
        contentDescription = "Núcleo visual OMEGA. Animación decorativa."
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    fun setSpeaking(speaking: Boolean) {
        center.animate().cancel()
        val scale = if (speaking) 1.045f else 1f
        center.animate().scaleX(scale).scaleY(scale)
            .setDuration(if (speaking) 190L else 260L).start()
    }

    fun pauseMotion() { scrollActive = true; refresh() }
    fun resumeMotion() { scrollActive = false; refresh() }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        focused = hasWindowFocus()
        refresh()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        focused = hasWindowFocus
        refresh()
    }

    override fun onDetachedFromWindow() {
        attached = false
        focused = false
        center.animate().cancel()
        outerSpin.cancel()
        innerSpin.cancel()
        super.onDetachedFromWindow()
    }

    private fun refresh() {
        val running = policy.shouldAnimate(
            attached, focused, ValueAnimator.areAnimatorsEnabled(), scrollActive
        )
        for (anim in listOf(outerSpin, innerSpin)) {
            if (running) {
                if (anim.isPaused) anim.resume()
                else if (!anim.isStarted) anim.start()
            } else if (anim.isRunning) {
                anim.pause()
            }
        }
    }

    private class OrbitRing(context: Context, private val inside: Boolean) : View(context) {
        private val lines = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (inside) Color.rgb(231, 66, 91) else Color.rgb(172, 40, 62)
            style = Paint.Style.STROKE
            strokeWidth = context.resources.displayMetrics.density * if (inside) 1.4f else 2f
        }
        private val dots = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 117, 139)
            style = Paint.Style.FILL
        }
        private val rect = RectF()
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val radius = min(cx, cy) * 0.91f
            rect.set(cx - radius, cy - radius, cx + radius, cy + radius)
            val segments = if (inside) 12 else 8
            val sweep = 360f / segments
            for (i in 0 until segments) {
                val start = (i * sweep) + if (inside) 8f else 2f
                canvas.drawArc(rect, start, sweep * if (inside) .51f else .78f,
                    false, lines)
                val angle = Math.toRadians((start + sweep / 2).toDouble())
                val x = cx + radius * cos(angle).toFloat()
                val y = cy + radius * sin(angle).toFloat()
                canvas.drawCircle(x, y, resources.displayMetrics.density * 2f, dots)
            }
        }
    }
}
