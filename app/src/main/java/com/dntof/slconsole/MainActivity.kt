package com.dntof.slconsole

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dntof.slconsole.ui.AppRoot
import com.dntof.slconsole.ui.theme.SLConsoleTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SLConsoleTheme {
                AppRoot()
            }
        }
    }
}
