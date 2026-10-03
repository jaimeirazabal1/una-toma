package com.jaime.teleprompter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jaime.teleprompter.ui.AppNavHost
import com.jaime.teleprompter.ui.theme.TeleprompterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as TeleprompterApp).container
        setContent {
            TeleprompterTheme {
                AppNavHost(container)
            }
        }
    }
}
