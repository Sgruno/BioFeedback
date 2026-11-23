
package com.hafran.estrestest

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class FaceOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // Regiones faciales
    private val foreheadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    private val leftCheekPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.CYAN
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    private val rightCheekPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    private var forehead: RectF? = null
    private var leftCheek: RectF? = null
    private var rightCheek: RectF? = null

    // Progreso de la captura
    private var captureProgress = 0f

    // Óvalo de fondo del indicador
    private val progressBackgroundPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#40566A")
            style = Paint.Style.STROKE
            strokeWidth = dpToPx(5f)
        }

    // Arco que avanza durante la captura
    private val progressPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#79D8C8")
            style = Paint.Style.STROKE
            strokeWidth = dpToPx(5f)
            strokeCap = Paint.Cap.ROUND
        }

    // Actualizar las regiones faciales
    fun updateRegions(
        forehead: RectF,
        leftCheek: RectF,
        rightCheek: RectF,
        imageWidth: Int,
        imageHeight: Int
    ) {
        this.forehead = RectF(forehead)
        this.leftCheek = RectF(leftCheek)
        this.rightCheek = RectF(rightCheek)

        postInvalidate()
    }

    // Limpiar las regiones faciales
    fun clearRegions() {
        forehead = null
        leftCheek = null
        rightCheek = null

        postInvalidate()
    }

    // Actualizar el progreso (valor entre 0 y 1)
    fun setCaptureProgress(progress: Float) {
        captureProgress = progress.coerceIn(0f, 1f)
        postInvalidate()
    }

    // Reiniciar el progreso
    fun resetCaptureProgress() {
        captureProgress = 0f
        postInvalidate()
    }

    private fun dpToPx(value: Float): Float {
        return value * resources.displayMetrics.density
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Dibujar las regiones faciales
        forehead?.let {
            canvas.drawRect(it, foreheadPaint)
        }

        leftCheek?.let {
            canvas.drawRect(it, leftCheekPaint)
        }

        rightCheek?.let {
            canvas.drawRect(it, rightCheekPaint)
        }

        // Dibujar el indicador alrededor del óvalo facial
        val ovalWidth = dpToPx(190f)
        val ovalHeight = dpToPx(250f)
        val margin = dpToPx(8f)

        val centerX = width / 2f
        val centerY = height / 2f

        val progressOval = RectF(
            centerX - ovalWidth / 2f - margin,
            centerY - ovalHeight / 2f - margin,
            centerX + ovalWidth / 2f + margin,
            centerY + ovalHeight / 2f + margin
        )

        // Contorno completo del indicador
        canvas.drawOval(
            progressOval,
            progressBackgroundPaint
        )

        // Arco que representa el avance
        if (captureProgress > 0f) {
            canvas.drawArc(
                progressOval,
                -90f,
                captureProgress * 360f,
                false,
                progressPaint
            )
        }
    }
}
