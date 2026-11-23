
package com.hafran.estrestest

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.Toast
import android.content.Intent

import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.TransformExperimental
import androidx.camera.view.transform.CoordinateTransform
import androidx.camera.view.transform.ImageProxyTransformFactory
import androidx.camera.view.transform.OutputTransform
import androidx.core.content.ContextCompat

import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.hafran.estrestest.databinding.ActivityCaptureBinding

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@TransformExperimental
class CaptureActivity : AppCompatActivity() {

    // ================================================
    // VARIABLES PRINCIPALES
    // ================================================

    private lateinit var binding: ActivityCaptureBinding
    private lateinit var faceLandmarkerHelper: FaceLandmarkerHelper
    private lateinit var analysisExecutor: ExecutorService

    private val isProcessing = AtomicBoolean(false)

    @Volatile
    private var detectorReady = false

    // ================================================
    // VARIABLES PARA PROCESAMIENTO DE IMÁGENES
    // ================================================

    @Volatile
    private var pendingBitmap: Bitmap? = null

    @Volatile
    private var pendingOutputTransform: OutputTransform? = null

    @Volatile
    private var pendingTimestampMs: Long = 0L

    private val imageProxyTransformFactory =
        ImageProxyTransformFactory().apply {
            setUsingCropRect(false)
            setUsingRotationDegrees(true)
        }

    // ================================================
    // LISTAS DE MUESTRAS RGB
    // ================================================

    private val foreheadSamples = mutableListOf<RgbSample>()
    private val leftCheekSamples = mutableListOf<RgbSample>()
    private val rightCheekSamples = mutableListOf<RgbSample>()

    // ================================================
    // TEMPORIZADOR DE CAPTURA
    // ================================================

    private val captureDurationMs = 40_000L

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var isCapturing = false

    private var hasCaptureStarted = false

    private var captureElapsedBeforePause = 0L

    private var captureSegmentStart = 0L

    private val captureTimer = object : Runnable {

        override fun run() {

            if (!isCapturing) return

            val currentElapsed =
                captureElapsedBeforePause +
                        (SystemClock.elapsedRealtime() - captureSegmentStart)

            val progress =
                currentElapsed.toFloat() / captureDurationMs

            binding.faceOverlay.setCaptureProgress(progress)

            if (currentElapsed >= captureDurationMs) {

                finishCapture()

            } else {

                mainHandler.postDelayed(this, 50)
            }
        }
    }

    // ================================================
    // PERMISO DE CÁMARA
    // ================================================

    private val requestCameraPermission =
        registerForActivityResult(
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

                finish()
            }
        }

    // ================================================
    // ONCREATE
    // ================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityCaptureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        analysisExecutor = Executors.newSingleThreadExecutor()

        // Inicializamos MediaPipe.
        try {

            faceLandmarkerHelper = FaceLandmarkerHelper(
                this,

                // Callback de resultados de detección facial.
                { result, input ->

                    try {

                        val landmarks =
                            result.faceLandmarks().firstOrNull()

                        if (landmarks == null) {

                            pendingBitmap = null

                            runOnUiThread {
                                binding.faceOverlay.clearRegions()
                            }

                        } else {

                            // =================================
                            // CALCULAR REGIONES FACIALES
                            // =================================

                            val faceWidth =
                                kotlin.math.abs(
                                    landmarks[454].x() -
                                            landmarks[234].x()
                                )

                            // Frente.
                            val forehead = RectF(
                                landmarks[10].x() - faceWidth * 0.15f,
                                landmarks[10].y() + faceWidth * 0.03f,
                                landmarks[10].x() + faceWidth * 0.15f,
                                landmarks[10].y() + faceWidth * 0.17f
                            )

                            // Mejilla izquierda.
                            val leftCheek = RectF(
                                landmarks[117].x() - faceWidth * 0.08f,
                                landmarks[117].y() - faceWidth * 0.06f,
                                landmarks[117].x() + faceWidth * 0.08f,
                                landmarks[117].y() + faceWidth * 0.06f
                            )

                            // Mejilla derecha.
                            val rightCheek = RectF(
                                landmarks[346].x() - faceWidth * 0.08f,
                                landmarks[346].y() - faceWidth * 0.06f,
                                landmarks[346].x() + faceWidth * 0.08f,
                                landmarks[346].y() + faceWidth * 0.06f
                            )

                            // =================================
                            // OBTENER FOTOGRAMA Y TRANSFORMACIÓN
                            // =================================

                            val sourceTransform =
                                pendingOutputTransform

                            val frameBitmap =
                                pendingBitmap

                            val timestamp =
                                pendingTimestampMs

                            // =================================
                            // EXTRAER VALORES RGB
                            // =================================

                            if (isCapturing && frameBitmap != null) {

                                val foreheadRgb =
                                    calculateAverageRgb(
                                        frameBitmap,
                                        forehead
                                    )

                                val leftCheekRgb =
                                    calculateAverageRgb(
                                        frameBitmap,
                                        leftCheek
                                    )

                                val rightCheekRgb =
                                    calculateAverageRgb(
                                        frameBitmap,
                                        rightCheek
                                    )

                                if (
                                    foreheadRgb != null &&
                                    leftCheekRgb != null &&
                                    rightCheekRgb != null
                                ) {

                                    synchronized(foreheadSamples) {

                                        foreheadSamples.add(
                                            RgbSample(
                                                red = foreheadRgb.first,
                                                green = foreheadRgb.second,
                                                blue = foreheadRgb.third,
                                                timestamp = timestamp
                                            )
                                        )

                                        leftCheekSamples.add(
                                            RgbSample(
                                                red = leftCheekRgb.first,
                                                green = leftCheekRgb.second,
                                                blue = leftCheekRgb.third,
                                                timestamp = timestamp
                                            )
                                        )

                                        rightCheekSamples.add(
                                            RgbSample(
                                                red = rightCheekRgb.first,
                                                green = rightCheekRgb.second,
                                                blue = rightCheekRgb.third,
                                                timestamp = timestamp
                                            )
                                        )
                                    }

                                    Log.d(
                                        "RGB",
                                        "Muestras: " +
                                                "Frente=${foreheadSamples.size}, " +
                                                "MejillaA=${leftCheekSamples.size}, " +
                                                "MejillaB=${rightCheekSamples.size}"
                                    )
                                }
                            }

                            // =================================
                            // TRANSFORMAR COORDENADAS
                            // =================================

                            runOnUiThread {

                                if (
                                    sourceTransform == null ||
                                    binding.cameraPreview.outputTransform == null
                                ) {
                                    return@runOnUiThread
                                }

                                val coordinateTransform =
                                    CoordinateTransform(
                                        sourceTransform,
                                        binding.cameraPreview.outputTransform!!
                                    )

                                fun convertToView(rect: RectF): RectF {

                                    val convertedRect = RectF(
                                        rect.left * input.width,
                                        rect.top * input.height,
                                        rect.right * input.width,
                                        rect.bottom * input.height
                                    )

                                    coordinateTransform.mapRect(convertedRect)

                                    return convertedRect
                                }

                                val foreheadView =
                                    convertToView(forehead)

                                val leftCheekView =
                                    convertToView(leftCheek)

                                val rightCheekView =
                                    convertToView(rightCheek)

                                binding.faceOverlay.updateRegions(
                                    foreheadView,
                                    leftCheekView,
                                    rightCheekView,
                                    binding.cameraPreview.width,
                                    binding.cameraPreview.height
                                )
                            }

                            pendingBitmap = null
                        }

                    } catch (exception: Exception) {

                        Log.e(
                            "FaceTracking",
                            "Error al procesar resultados faciales",
                            exception
                        )

                    } finally {

                        isProcessing.set(false)
                    }
                },

                // Callback de errores de MediaPipe.
                { error ->

                    Log.e(
                        "MediaPipe",
                        "Error del detector facial",
                        error
                    )

                    pendingBitmap = null
                    isProcessing.set(false)
                }
            )

            detectorReady = true

        } catch (exception: Exception) {

            Log.e(
                "MediaPipe",
                "Error al inicializar el detector",
                exception
            )

            Toast.makeText(
                this,
                "No se pudo inicializar la detección facial",
                Toast.LENGTH_LONG
            ).show()
        }

        // ================================================
        // CONFIGURAR BOTONES
        // ================================================

        // Estado inicial: pantalla de encuadre.
        binding.btnReady.visibility = View.VISIBLE
        binding.btnPause.visibility = View.GONE

        // Botón para iniciar la captura.
        binding.btnReady.setOnClickListener {

            if (!detectorReady) {
                Toast.makeText(
                    this,
                    "El detector facial todavía no está listo",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            binding.btnReady.visibility = View.GONE
            binding.btnPause.visibility = View.VISIBLE

            startCapture()
        }

        // Botón para pausar o continuar.
        binding.btnPause.setOnClickListener {

            if (isCapturing) {

                pauseCapture()

            } else {

                startCapture()
            }
        }

        // Botón para regresar al menú.
        binding.btnBack.setOnClickListener {
            finish()
        }

        // Comprobar permiso de cámara.
        checkCameraPermission()
    }

    // ================================================
    // COMPROBAR PERMISO DE CÁMARA
    // ================================================

    private fun checkCameraPermission() {

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {

            startCamera()

        } else {

            requestCameraPermission.launch(
                Manifest.permission.CAMERA
            )
        }
    }

    // ================================================
    // INICIAR O REANUDAR CAPTURA
    // ================================================

    private fun startCapture() {

        if (isCapturing || !detectorReady) return

        if (!hasCaptureStarted) {

            synchronized(foreheadSamples) {
                foreheadSamples.clear()
                leftCheekSamples.clear()
                rightCheekSamples.clear()
            }

            binding.faceOverlay.resetCaptureProgress()

            hasCaptureStarted = true
            captureElapsedBeforePause = 0L

            binding.txtTitle.text = "Señal en curso"

            binding.txtInstructions.text =
                "Mantén tu rostro dentro del óvalo"

            binding.txtLighting.visibility = View.GONE
        }

        isCapturing = true

        captureSegmentStart =
            SystemClock.elapsedRealtime()

        binding.btnPause.text = "Pausar captura"

        mainHandler.removeCallbacks(captureTimer)
        mainHandler.post(captureTimer)

        Log.d(
            "Capture",
            "Captura iniciada o reanudada"
        )
    }

    // ================================================
    // PAUSAR CAPTURA
    // ================================================

    private fun pauseCapture() {

        if (!isCapturing) return

        captureElapsedBeforePause +=
            SystemClock.elapsedRealtime() - captureSegmentStart

        isCapturing = false

        mainHandler.removeCallbacks(captureTimer)

        binding.btnPause.text = "Continuar captura"

        Log.d(
            "Capture",
            "Captura pausada"
        )
    }

    // ================================================
    // FINALIZAR CAPTURA
    // ================================================

    private fun finishCapture() {

        isCapturing = false

        mainHandler.removeCallbacks(captureTimer)

        binding.faceOverlay.setCaptureProgress(1f)

        binding.txtTitle.text = "Captura completada"

        binding.txtInstructions.text =
            "Hemos terminado de recopilar la señal."

        binding.btnPause.text = "Captura finalizada"
        binding.btnPause.isEnabled = false

        Log.d(
            "Capture",
            "Captura finalizada. Muestras: " +
                    "Frente=${foreheadSamples.size}, " +
                    "MejillaA=${leftCheekSamples.size}, " +
                    "MejillaB=${rightCheekSamples.size}"
        )
        // Creamos el procesador de señales rPPG.
        val rppgProcessor = RppgProcessor()

// Procesamos las muestras de las tres regiones.
        val foreheadSignal = rppgProcessor.process(foreheadSamples.toList())
        val leftCheekSignal = rppgProcessor.process(leftCheekSamples.toList())
        val rightCheekSignal = rppgProcessor.process(rightCheekSamples.toList())

        // Estimamos la frecuencia cardíaca de cada región.
        val foreheadBpm =
            rppgProcessor.estimateHeartRate(foreheadSamples.toList())

        val leftCheekBpm =
            rppgProcessor.estimateHeartRate(leftCheekSamples.toList())

        val rightCheekBpm =
            rppgProcessor.estimateHeartRate(rightCheekSamples.toList())

// Mostramos los resultados en Logcat.
        Log.d("HeartRate", "Frente: $foreheadBpm BPM")
        Log.d("HeartRate", "Mejilla A: $leftCheekBpm BPM")
        Log.d("HeartRate", "Mejilla B: $rightCheekBpm BPM")

// Comprobamos si se generaron las señales.
        Log.d("rPPG", "Frente procesada: ${foreheadSignal != null}")
        Log.d("rPPG", "Mejilla A procesada: ${leftCheekSignal != null}")
        Log.d("rPPG", "Mejilla B procesada: ${rightCheekSignal != null}")

// Mostramos algunos valores normalizados del canal verde.
        foreheadSignal?.let {
            Log.d("rPPG", "Frente - Verde: ${it.green.take(10)}")
        }

        leftCheekSignal?.let {
            Log.d("rPPG", "Mejilla A - Verde: ${it.green.take(10)}")
        }

        rightCheekSignal?.let {
            Log.d("rPPG", "Mejilla B - Verde: ${it.green.take(10)}")
        }

        Toast.makeText(
            this,
            "Captura finalizada",
            Toast.LENGTH_SHORT
        ).show()
        // Pasamos automáticamente al ejercicio de respiración.
        val intent = Intent(this, BiofeedbackActivity::class.java)
        startActivity(intent)
        finish()
    }

    // ================================================
    // CALCULAR PROMEDIO RGB DE UNA REGIÓN
    // ================================================

    private fun calculateAverageRgb(
        bitmap: Bitmap,
        normalizedRect: RectF
    ): Triple<Double, Double, Double>? {

        val left =
            (normalizedRect.left * bitmap.width)
                .toInt()
                .coerceIn(0, bitmap.width - 1)

        val top =
            (normalizedRect.top * bitmap.height)
                .toInt()
                .coerceIn(0, bitmap.height - 1)

        val right =
            (normalizedRect.right * bitmap.width)
                .toInt()
                .coerceIn(left + 1, bitmap.width)

        val bottom =
            (normalizedRect.bottom * bitmap.height)
                .toInt()
                .coerceIn(top + 1, bitmap.height)

        var redSum = 0L
        var greenSum = 0L
        var blueSum = 0L

        var count = 0L

        for (y in top until bottom) {

            for (x in left until right) {

                val pixel = bitmap.getPixel(x, y)

                redSum += Color.red(pixel)
                greenSum += Color.green(pixel)
                blueSum += Color.blue(pixel)

                count++
            }
        }

        if (count == 0L) return null

        return Triple(
            redSum.toDouble() / count,
            greenSum.toDouble() / count,
            blueSum.toDouble() / count
        )
    }

    // ================================================
    // CONVERTIR IMAGEPROXY A BITMAP
    // ================================================

    private fun imageProxyToBitmap(
        image: ImageProxy
    ): Bitmap {

        val plane = image.planes[0]
        val buffer = plane.buffer

        val startPosition = buffer.position()

        val width = image.width
        val height = image.height

        val pixels = IntArray(width * height)

        for (y in 0 until height) {

            for (x in 0 until width) {

                val index =
                    startPosition +
                            y * plane.rowStride +
                            x * plane.pixelStride

                val r =
                    buffer.get(index).toInt() and 0xFF

                val g =
                    buffer.get(index + 1).toInt() and 0xFF

                val b =
                    buffer.get(index + 2).toInt() and 0xFF

                val a =
                    buffer.get(index + 3).toInt() and 0xFF

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

        val rotation =
            image.imageInfo.rotationDegrees

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

    // ================================================
    // INICIAR CÁMARA CON CAMERAX
    // ================================================

    private fun startCamera() {

        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({

            val cameraProvider =
                cameraProviderFuture.get()

            val preview = Preview.Builder().build()

            preview.surfaceProvider =
                binding.cameraPreview.surfaceProvider

            val imageAnalysis =
                ImageAnalysis.Builder()
                    .setBackpressureStrategy(
                        ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                    )
                    .setOutputImageFormat(
                        ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888
                    )
                    .build()

            imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->

                if (
                    !detectorReady ||
                    isFinishing ||
                    isDestroyed
                ) {
                    imageProxy.close()
                    return@setAnalyzer
                }

                if (!isProcessing.compareAndSet(false, true)) {
                    imageProxy.close()
                    return@setAnalyzer
                }

                try {

                    pendingOutputTransform =
                        imageProxyTransformFactory.getOutputTransform(
                            imageProxy
                        )

                    val bitmap =
                        imageProxyToBitmap(imageProxy)

                    pendingBitmap = bitmap

                    pendingTimestampMs =
                        imageProxy.imageInfo.timestamp / 1_000_000

                    val mpImage =
                        BitmapImageBuilder(bitmap).build()

                    faceLandmarkerHelper.detectAsync(
                        mpImage,
                        pendingTimestampMs
                    )

                } catch (exception: Exception) {

                    pendingBitmap = null
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

            val cameraSelector =
                CameraSelector.DEFAULT_FRONT_CAMERA

            try {

                cameraProvider.unbindAll()

                val viewPort =
                    binding.cameraPreview.viewPort

                if (viewPort == null) {

                    Log.e(
                        "CameraX",
                        "No se pudo obtener el ViewPort"
                    )

                    return@addListener
                }

                val useCaseGroup =
                    UseCaseGroup.Builder()
                        .setViewPort(viewPort)
                        .addUseCase(preview)
                        .addUseCase(imageAnalysis)
                        .build()

                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    useCaseGroup
                )

                Log.d(
                    "CameraX",
                    "Cámara frontal iniciada correctamente"
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

    // ================================================
    // DESTRUIR ACTIVITY
    // ================================================

    override fun onDestroy() {

        isCapturing = false

        mainHandler.removeCallbacks(captureTimer)

        detectorReady = false

        pendingBitmap = null

        if (::faceLandmarkerHelper.isInitialized) {
            faceLandmarkerHelper.close()
        }

        if (::analysisExecutor.isInitialized) {
            analysisExecutor.shutdown()
        }

        super.onDestroy()
    }
}
