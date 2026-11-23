
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

    private val foreheadPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
    }

    private val leftCheekPaint = Paint().apply {
        color = Color.CYAN
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
    }

    private val rightCheekPaint = Paint().apply {
        color = Color.YELLOW
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
    }

    private var forehead: RectF? = null
    private var leftCheek: RectF? = null
    private var rightCheek: RectF? = null

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

    fun clearRegions() {
        forehead = null
        leftCheek = null
        rightCheek = null

        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Las coordenadas ya están transformadas
        // al espacio de la vista previa de CameraX.
        forehead?.let {
            canvas.drawRect(it, foreheadPaint)
        }

        leftCheek?.let {
            canvas.drawRect(it, leftCheekPaint)
        }

        rightCheek?.let {
            canvas.drawRect(it, rightCheekPaint)
        }
    }
}
