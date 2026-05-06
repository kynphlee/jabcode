package com.jabauth.diagnostic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.jabauth.diagnostic.navigation.DiagnosticNavHost
import com.jabauth.diagnostic.ui.theme.AppContext
import com.jabauth.diagnostic.ui.theme.JABAuthTheme

/**
 * Main activity for JABAuth Diagnostic Application
 * 
 * Sets up Compose UI and navigation between Dashboard, Scanner, and Settings screens.
 * 
 * Note: Hilt DI temporarily disabled due to Gradle 9.0 + kapt compatibility.
 */
class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            JABAuthTheme(
                context = AppContext.Healthcare,
                darkTheme = true  // Diagnostic UI optimized for dark theme
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DiagnosticNavHost()
                }
            }
        }
    }
}
