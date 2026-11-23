
package com.hafran.estrestest

import android.content.Context
import android.util.Log
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import java.nio.ByteBuffer

class FaceLandmarkerHelper(
    context: Context,
    private val resultListener: (
        FaceLandmarkerResult,
        MPImage
    ) -> Unit,
    private val errorListener: (RuntimeException) -> Unit
) {

    private val faceLandmarker: FaceLandmarker



    init {
        val archivos = context.assets.list("") ?: emptyArray()

        Log.e(
            "MediaPipe",
            "ARCHIVOS EN ASSETS: ${archivos.joinToString()}"
        )

        val modelBytes = context.assets.open("face_landmarker.task").use {
            it.readBytes()
        }

        // Creamos el buffer del modelo.
        val modelBuffer = ByteBuffer.allocateDirect(modelBytes.size).apply {
            put(modelBytes)
            rewind()
        }

        // Configuramos las opciones del modelo.
        val baseOptions = BaseOptions.builder()
            .setModelAssetBuffer(modelBuffer)
            .build()

        // Configuramos MediaPipe para detección continua.
        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumFaces(1)
            .setResultListener { result, input ->
                resultListener(result, input)
            }
            .setErrorListener { error ->
                errorListener(error)
            }
            .build()

        // Inicializamos el detector.
        faceLandmarker = FaceLandmarker.createFromOptions(
            context,
            options
        )
    }

    // Enviamos una imagen al detector.
    fun detectAsync(image: MPImage, timestampMs: Long) {
        faceLandmarker.detectAsync(image, timestampMs)
    }

    // Liberamos los recursos.
    fun close() {
        faceLandmarker.close()
    }
}
