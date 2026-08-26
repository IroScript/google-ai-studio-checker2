package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.ui.KidsTubeScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.KidsTubeViewModel

class MainActivity : ComponentActivity() {
  private val viewModel: KidsTubeViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = Color.Black
        ) {
          KidsTubeScreen(viewModel = viewModel)
        }
      }
    }
  }
}

