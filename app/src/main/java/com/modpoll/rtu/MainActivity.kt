package com.modpoll.rtu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.modpoll.rtu.ui.AppViewModel
import com.modpoll.rtu.ui.ModPollRoot
import com.modpoll.rtu.ui.theme.ModPollTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ModPollTheme {
                val vm: AppViewModel = viewModel()
                ModPollRoot(vm)
            }
        }
    }
}
