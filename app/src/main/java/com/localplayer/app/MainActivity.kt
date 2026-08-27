package com.localplayer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.localplayer.app.ui.PlayerScreen
import com.localplayer.app.ui.theme.LocalPlayerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LocalPlayerTheme {
                PlayerScreen()
            }
        }
    }
}
