
package com.hafran.estrestest

import android.animation.ValueAnimator
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.hafran.estrestest.databinding.ActivityBiofeedbackBinding

class BiofeedbackActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBiofeedbackBinding

    private var breathingAnimator: ValueAnimator? = null
    private var isPaused = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityBiofeedbackBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnPauseSession.setOnClickListener {
            if (isPaused) {
                resumeBreathing()
            } else {
                pauseBreathing()
            }
        }
        binding.btnFinishSession.setOnClickListener {
            breathingAnimator?.cancel()
            breathingAnimator = null

            finish()
        }

        startBreathing()
    }

    private fun startBreathing() {

        breathingAnimator?.cancel()

        breathingAnimator = ValueAnimator.ofFloat(0f, 1f).apply {

            duration = 10_000L
            repeatCount = ValueAnimator.INFINITE

            addUpdateListener { animator ->

                val progress = animator.animatedValue as Float

                if (progress <= 0.4f) {

                    // Inhalación: 4 segundos.
                    val inhaleProgress = progress / 0.4f

                    val scale = 0.75f + (0.40f * inhaleProgress)

                    binding.txtBreathing.text = "Inhala"

                    binding.breathingSphere.scaleX = scale
                    binding.breathingSphere.scaleY = scale

                } else {

                    // Exhalación: 6 segundos.
                    val exhaleProgress = (progress - 0.4f) / 0.6f

                    val scale = 1.15f - (0.40f * exhaleProgress)

                    binding.txtBreathing.text = "Exhala"

                    binding.breathingSphere.scaleX = scale
                    binding.breathingSphere.scaleY = scale
                }
            }

            start()
        }
    }

    private fun pauseBreathing() {
        breathingAnimator?.pause()
        isPaused = true
        binding.btnPauseSession.text = "Continuar sesión"
    }

    private fun resumeBreathing() {
        breathingAnimator?.resume()
        isPaused = false
        binding.btnPauseSession.text = "Pausar sesión"
    }

    override fun onDestroy() {
        breathingAnimator?.cancel()
        breathingAnimator = null
        super.onDestroy()
    }
}
