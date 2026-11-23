
package com.hafran.estrestest

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class FaceOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // Pintura para la frente.
    private val foreheadPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
    }

    // Pintura para la mejilla izquierda.
    private val leftCheekPaint = Paint().apply {
        color = Color.CYAN
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
    }

    // Pintura para la mejilla derecha.
    private val rightCheekPaint = Paint().apply {
        color = Color.YELLOW
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
    }

    // Regiones faciales normalizadas.
    private var forehead: RectF? = null
    private var leftCheek: RectF? = null
    private var rightCheek: RectF? = null

    // Dimensiones de la imagen procesada por MediaPipe.
    private var imageWidth = 0
    private var imageHeight = 0

    // Recibimos las regiones y dimensiones de la imagen.
    fun updateRegions(
        forehead: RectF,
        leftCheek: RectF,
        rightCheek: RectF,
        imageWidth: Int,
        imageHeight: Int
    ) {

        this.forehead = forehead
        this.leftCheek = leftCheek
        this.rightCheek = rightCheek

        this.imageWidth = imageWidth
        this.imageHeight = imageHeight

        postInvalidate()
    }

    // Limpiamos las regiones cuando no hay rostro.
    fun clearRegions() {

        forehead = null
        leftCheek = null
        rightCheek = null

        postInvalidate()
    }

    // Dibujamos las regiones sobre la cámara.
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (imageWidth <= 0 || imageHeight <= 0) {
            return
        }

        // Calculamos la escala para mantener la proporción.
        val scale = min(
            width.toFloat() / imageWidth,
            height.toFloat() / imageHeight
        )

        // Calculamos el espacio sobrante.
        val offsetX = (width - imageWidth * scale) / 2f
        val offsetY = (height - imageHeight * scale) / 2f

        // Convertimos coordenadas normalizadas a píxeles.
        fun convertToCanvas(rect: RectF): RectF {

            return RectF(
                offsetX + rect.left * imageWidth * scale,
                offsetY + rect.top * imageHeight * scale,
                offsetX + rect.right * imageWidth * scale,
                offsetY + rect.bottom * imageHeight * scale
            )
        }

        // Dibujamos la frente.
        forehead?.let {
            canvas.drawRect(
                convertToCanvas(it),
                foreheadPaint
            )
        }

        // Dibujamos la mejilla izquierda.
        leftCheek?.let {
            canvas.drawRect(
                convertToCanvas(it),
                leftCheekPaint
            )
        }

        // Dibujamos la mejilla derecha.
        rightCheek?.let {
            canvas.drawRect(
                convertToCanvas(it),
                rightCheekPaint
            )
        }
    }
}
