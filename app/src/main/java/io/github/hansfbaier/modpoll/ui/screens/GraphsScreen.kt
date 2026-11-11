package io.github.hansfbaier.modpoll.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.hansfbaier.modpoll.ui.AppViewModel

@Composable
fun GraphsScreen(vm: AppViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(
                selected = tab == 0,
                onClick = { tab = 0 },
                text = { Text("Live scope") },
                icon = { Icon(Icons.Outlined.Speed, null, Modifier.size(17.dp)) },
            )
            Tab(
                selected = tab == 1,
                onClick = { tab = 1 },
                text = { Text("History") },
                icon = { Icon(Icons.Outlined.ShowChart, null, Modifier.size(17.dp)) },
            )
        }
        Box(Modifier.weight(1f)) {
            if (tab == 0) {
                ScopeScreen(vm)
            } else {
                TrendsScreen(vm)
            }
        }
    }
}
