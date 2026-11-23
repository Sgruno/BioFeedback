
package com.hafran.estrestest

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.hafran.estrestest.databinding.ActivityCaptureBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

class CaptureActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCaptureBinding
    private lateinit var faceLandmarkerHelper: FaceLandmarkerHelper
    private lateinit var analysisExecutor: ExecutorService

    private val isProcessing = AtomicBoolean(false)

    private var detectorReady = false

    // Solicitud de permiso para utilizar la cámara.
    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->

        if (granted) {
            startCamera()
        } else {
            Toast.makeText(
                this,
                "Se necesita permiso para utilizar la cámara",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityCaptureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        analysisExecutor = Executors.newSingleThreadExecutor()

        // Inicializamos MediaPipe.
        try {

            faceLandmarkerHelper = FaceLandmarkerHelper(
                this,

                // Recibimos los resultados de la detección facial.
                { result, input ->

                    val facesDetected = result.faceLandmarks().size

                    if (facesDetected == 0) {
                        Log.w("FaceTracking", "Se perdió el rostro")
                    } else {
                        Log.d("FaceTracking", "Rostro localizado correctamente")
                    }

                    if (facesDetected > 0) {

                        val landmarks = result.faceLandmarks()[0]

                        Log.d(
                            "MediaPipe",
                            "Puntos faciales detectados: ${landmarks.size}"
                        )

                        // Calculamos el ancho aproximado del rostro.
                        val faceWidth = abs(
                            landmarks[454].x() - landmarks[234].x()
                        )

                        // Calculamos la región de la frente.
                        val forehead = RectF(
                            landmarks[10].x() - faceWidth * 0.15f,
                            landmarks[10].y() + faceWidth * 0.03f,
                            landmarks[10].x() + faceWidth * 0.15f,
                            landmarks[10].y() + faceWidth * 0.17f
                        )

                        // Calculamos la región de la mejilla izquierda.
                        val leftCheek = RectF(
                            landmarks[117].x() - faceWidth * 0.08f,
                            landmarks[117].y() - faceWidth * 0.06f,
                            landmarks[117].x() + faceWidth * 0.08f,
                            landmarks[117].y() + faceWidth * 0.06f
                        )

                        // Calculamos la región de la mejilla derecha.
                        val rightCheek = RectF(
                            landmarks[346].x() - faceWidth * 0.08f,
                            landmarks[346].y() - faceWidth * 0.06f,
                            landmarks[346].x() + faceWidth * 0.08f,
                            landmarks[346].y() + faceWidth * 0.06f
                        )

                        // Registramos las regiones en Logcat.
                        logRoi(
                            "FRENTE",
                            landmarks[10].x(),
                            landmarks[10].y() + faceWidth * 0.10f,
                            faceWidth * 0.30f,
                            faceWidth * 0.14f
                        )

                        logRoi(
                            "MEJILLA A",
                            landmarks[117].x(),
                            landmarks[117].y(),
                            faceWidth * 0.16f,
                            faceWidth * 0.12f
                        )

                        logRoi(
                            "MEJILLA B",
                            landmarks[346].x(),
                            landmarks[346].y(),
                            faceWidth * 0.16f,
                            faceWidth * 0.12f
                        )

                        // Enviamos las regiones a la vista de dibujo.
                        // Las coordenadas todavía están normalizadas.
                        runOnUiThread {

                            binding.faceOverlay.updateRegions(
                                forehead,
                                leftCheek,
                                rightCheek,
                                input.width,
                                input.height
                            )
                        }

                    } else {

                        // Si no se detecta un rostro, limpiamos el dibujo.
                        runOnUiThread {
                            binding.faceOverlay.clearRegions()
                        }
                    }

                    Log.d(
                        "MediaPipe",
                        "Rostros detectados: $facesDetected"
                    )

                    // Permitimos procesar el siguiente fotograma.
                    isProcessing.set(false)
                },

                // Recibimos posibles errores de MediaPipe.
                { error ->

                    Log.e(
                        "MediaPipe",
                        "Error en Face Landmarker",
                        error
                    )

                    isProcessing.set(false)
                }
            )

            detectorReady = true

            Log.d(
                "MediaPipe",
                "Detector facial inicializado correctamente"
            )

        } catch (exception: Exception) {

            detectorReady = false

            Log.e(
                "MediaPipe",
                "Error al inicializar el detector",
                exception
            )

            Toast.makeText(
                this,
                "No se pudo inicializar el detector facial",
                Toast.LENGTH_LONG
            ).show()
        }

        // Botón para regresar.
        binding.btnBack.setOnClickListener {
            finish()
        }

        // Comprobamos el permiso de cámara.
        checkCameraPermission()
    }

    // Calcula los límites de una región facial para Logcat.
    private fun logRoi(
        name: String,
        centerX: Float,
        centerY: Float,
        width: Float,
        height: Float
    ) {

        val left = (centerX - width / 2).coerceIn(0f, 1f)
        val right = (centerX + width / 2).coerceIn(0f, 1f)

        val top = (centerY - height / 2).coerceIn(0f, 1f)
        val bottom = (centerY + height / 2).coerceIn(0f, 1f)

        Log.d(
            "FaceROI",
            "$name: izquierda=$left, derecha=$right, arriba=$top, abajo=$bottom"
        )
    }

    // Comprobamos si tenemos permiso para utilizar la cámara.
    private fun checkCameraPermission() {

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {

            startCamera()

        } else {

            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    // Iniciamos CameraX.
    private fun startCamera() {

        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({

            val cameraProvider = cameraProviderFuture.get()

            // Configuramos la vista previa.
            val preview = Preview.Builder().build()

            preview.surfaceProvider =
                binding.cameraPreview.surfaceProvider

            // Configuramos el análisis de imágenes.
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(
                    ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                )
                .setOutputImageFormat(
                    ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888
                )
                .build()

            // Procesamos las imágenes de la cámara.
            imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->

                if (!detectorReady || isFinishing || isDestroyed) {
                    imageProxy.close()
                    return@setAnalyzer
                }

                // Evitamos procesar varios fotogramas simultáneamente.
                if (!isProcessing.compareAndSet(false, true)) {
                    imageProxy.close()
                    return@setAnalyzer
                }

                try {

                    // Convertimos la imagen de cámara a Bitmap.
                    val bitmap = imageProxyToBitmap(imageProxy)

                    // Creamos la imagen que recibe MediaPipe.
                    val mpImage = BitmapImageBuilder(bitmap).build()

                    // Utilizamos el tiempo de la cámara en milisegundos.
                    val timestamp =
                        imageProxy.imageInfo.timestamp / 1_000_000

                    // Enviamos la imagen al detector facial.
                    faceLandmarkerHelper.detectAsync(
                        mpImage,
                        timestamp
                    )

                } catch (exception: Exception) {

                    isProcessing.set(false)

                    Log.e(
                        "MediaPipe",
                        "Error al procesar imagen",
                        exception
                    )

                } finally {

                    imageProxy.close()
                }
            }

            // Seleccionamos la cámara frontal.
            val cameraSelector =
                CameraSelector.DEFAULT_FRONT_CAMERA

            try {

                cameraProvider.unbindAll()

                // Vinculamos la cámara, la vista previa y el análisis.
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )

                Log.d(
                    "CameraX",
                    "Cámara y análisis iniciados correctamente"
                )

            } catch (exception: Exception) {

                Log.e(
                    "CameraX",
                    "Error al iniciar cámara",
                    exception
                )

                Toast.makeText(
                    this,
                    "No se pudo iniciar la cámara",
                    Toast.LENGTH_LONG
                ).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    // Convierte una imagen RGBA de CameraX a Bitmap.
    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {

        val plane = image.planes[0]
        val buffer = plane.buffer
        val startPosition = buffer.position()

        val width = image.width
        val height = image.height

        val pixels = IntArray(width * height)

        for (y in 0 until height) {

            for (x in 0 until width) {

                val index = startPosition +
                        y * plane.rowStride +
                        x * plane.pixelStride

                val r = buffer.get(index).toInt() and 0xFF
                val g = buffer.get(index + 1).toInt() and 0xFF
                val b = buffer.get(index + 2).toInt() and 0xFF
                val a = buffer.get(index + 3).toInt() and 0xFF

                pixels[y * width + x] =
                    (a shl 24) or
                            (r shl 16) or
                            (g shl 8) or
                            b
            }
        }

        val bitmap = Bitmap.createBitmap(
            width,
            height,
            Bitmap.Config.ARGB_8888
        )

        bitmap.setPixels(
            pixels,
            0,
            width,
            0,
            0,
            width,
            height
        )

        // Corregimos la orientación de la imagen.
        val rotation = image.imageInfo.rotationDegrees

        if (rotation == 0) {
            return bitmap
        }

        val matrix = Matrix().apply {
            postRotate(rotation.toFloat())
        }

        return Bitmap.createBitmap(
            bitmap,
            0,
            0,
            width,
            height,
            matrix,
            true
        )
    }

    // Liberamos los recursos al cerrar la pantalla.
    override fun onDestroy() {
        super.onDestroy()

        detectorReady = false

        if (::faceLandmarkerHelper.isInitialized) {
            faceLandmarkerHelper.close()
        }

        if (::analysisExecutor.isInitialized) {
            analysisExecutor.shutdown()
        }
    }
}
