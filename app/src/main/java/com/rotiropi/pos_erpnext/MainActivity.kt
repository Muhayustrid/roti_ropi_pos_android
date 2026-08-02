package com.rotiropi.pos_erpnext

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.rotiropi.pos_erpnext.databinding.Task4RootBinding
import com.rotiropi.pos_erpnext.ui.Task4RootHost

class MainActivity : ComponentActivity() {
    private lateinit var binding: Task4RootBinding
    private lateinit var task4RootHost: Task4RootHost

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = Task4RootBinding.inflate(layoutInflater)
        setContentView(binding.root)
        task4RootHost = Task4RootHost(
            activity = this,
            application = application as MobilePosApplication,
            binding = binding,
        )
    }
}
