package com.omersusin.earlab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.omersusin.earlab.audio.ToneGen
import com.omersusin.earlab.ui.EarLabApp
import com.omersusin.earlab.ui.theme.EarLabTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EarLabTheme { EarLabApp() }
        }
    }

    override fun onPause() {
        super.onPause()
        ToneGen.stop()
    }
}
