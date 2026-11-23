
package com.hafran.estrestest

import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.cos

/**
 * Procesador inicial de señales rPPG.
 */
class RppgProcessor {

    data class ProcessedSignal(
        val red: List<Double>,
        val green: List<Double>,
        val blue: List<Double>,
        val timestamps: List<Long>
    )

    // Normalizamos las muestras RGB.
    fun process(samples: List<RgbSample>): ProcessedSignal? {

        if (samples.size < 2) return null

        val orderedSamples = samples.sortedBy { it.timestamp }

        val redValues = orderedSamples.map { it.red }
        val greenValues = orderedSamples.map { it.green }
        val blueValues = orderedSamples.map { it.blue }

        val redMean = redValues.average()
        val greenMean = greenValues.average()
        val blueMean = blueValues.average()

        if (redMean == 0.0 || greenMean == 0.0 || blueMean == 0.0) {
            return null
        }

        return ProcessedSignal(
            red = redValues.map { it / redMean - 1.0 },
            green = greenValues.map { it / greenMean - 1.0 },
            blue = blueValues.map { it / blueMean - 1.0 },
            timestamps = orderedSamples.map { it.timestamp }
        )
    }

    // Estimación experimental de frecuencia cardíaca.
    fun estimateHeartRate(samples: List<RgbSample>): Double? {

        if (samples.size < 50) return null

        val ordered = samples.sortedBy { it.timestamp }
        val initialTime = ordered.first().timestamp

        val times = ordered.map {
            (it.timestamp - initialTime) / 1000.0
        }

        val values = ordered.map { it.green }
        val mean = values.average()
        val signal = values.map { it - mean }

        var bestFrequency = 0.0
        var bestPower = Double.NEGATIVE_INFINITY

        var frequency = 0.7

        while (frequency <= 2.4) {

            var sinSum = 0.0
            var cosSum = 0.0

            for (i in signal.indices) {
                val angle = 2.0 * PI * frequency * times[i]

                sinSum += signal[i] * sin(angle)
                cosSum += signal[i] * cos(angle)
            }

            val power = sinSum * sinSum + cosSum * cosSum

            if (power > bestPower) {
                bestPower = power
                bestFrequency = frequency
            }

            frequency += 0.01
        }

        if (bestFrequency == 0.0) return null

        return bestFrequency * 60.0
    }
}
