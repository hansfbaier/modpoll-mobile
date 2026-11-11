package io.github.hansfbaier.modpoll

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.hansfbaier.modpoll.ui.AppViewModel
import io.github.hansfbaier.modpoll.ui.ModPollRoot
import io.github.hansfbaier.modpoll.ui.theme.ModPollTheme

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
