package io.github.secnewt.template

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.secnewt.template.ui.WelcomeScreen
import io.github.secnewt.ui.NewtTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NewtTheme {
                WelcomeScreen()
            }
        }
    }
}
