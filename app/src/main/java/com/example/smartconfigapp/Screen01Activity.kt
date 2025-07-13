package com.example.smartconfigapp

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

class Screen01Activity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen01)

        val btnNext = findViewById<ImageButton>(R.id.btnNext)
        btnNext.setOnClickListener {
            // Chuyển sang Screen02Activity
            startActivity(Intent(this, Screen02Activity::class.java))
        }
    }
}
