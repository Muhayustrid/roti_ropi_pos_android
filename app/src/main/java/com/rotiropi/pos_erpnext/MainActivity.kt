package com.rotiropi.pos_erpnext

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rotiropi.pos_erpnext.ui.navigation.PosShell
import com.rotiropi.pos_erpnext.ui.theme.PosTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PosTheme {
                PosShell()
            }
        }
    }
}
