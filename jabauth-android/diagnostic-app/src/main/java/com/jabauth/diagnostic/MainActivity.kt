package com.jabauth.diagnostic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme

/**
 * Main activity for diagnostic app
 * 
 * Hosts the navigation graph and bottom navigation bar
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                DiagnosticApp()
            }
        }
    }
}
