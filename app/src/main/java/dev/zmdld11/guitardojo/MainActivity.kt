package dev.zmdld11.guitardojo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.zmdld11.guitardojo.ui.theme.GuitarDojoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            GuitarDojoTheme {
                GuitarDojoApp()
            }
        }
    }
}
