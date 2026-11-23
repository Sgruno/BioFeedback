
package com.hafran.estrestest

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.hafran.estrestest.databinding.ActivityCaptureBinding

class CaptureActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCaptureBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityCaptureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Regresar a la pantalla anterior
        binding.btnBack.setOnClickListener {
            finish()
        }
    }
}
