package com.sight.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.sight.app.navigation.SightNavHost
import com.sight.app.ui.AnalysisViewModel
import com.sight.app.ui.theme.SightTheme

class MainActivity : ComponentActivity() {
    private val viewModel: AnalysisViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SightTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SightNavHost(viewModel)
                }
            }
        }
    }
}
