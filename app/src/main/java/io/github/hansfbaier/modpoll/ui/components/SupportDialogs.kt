package io.github.hansfbaier.modpoll.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.hansfbaier.modpoll.BuildConfig

@Composable
fun UpgradePrompt(onDismiss: () -> Unit) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.padding(24.dp).widthIn(max = 520.dp),
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Outlined.FavoriteBorder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text("Support ModPoll Mobile", style = MaterialTheme.typography.titleLarge)
                Text(
                    "The Google Play version is supported by banner ads. " +
                        "Remove ads with ModPoll Mobile Pro, or get the ad-free build free on F-Droid.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = {
                        openProListing(context)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Get ModPoll Mobile Pro")
                }
                if (BuildConfig.SHOW_FDROID_PROMO && BuildConfig.FDROID_URL.isNotBlank()) {
                    OutlinedButton(
                        onClick = {
                            openUrl(context, BuildConfig.FDROID_URL)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Free and ad-free on F-Droid")
                    }
                    Text(
                        "F-Droid builds are signed separately. Export your profiles before switching.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Not now")
                }
            }
        }
    }
}

@Composable
fun DonationPrompt(onDismiss: () -> Unit) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.padding(24.dp).widthIn(max = 520.dp),
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Outlined.FavoriteBorder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text("Support ModPoll Mobile", style = MaterialTheme.typography.titleLarge)
                Text(
                    "This F-Droid build is free and ad-free. If it is useful to you, " +
                        "a donation supports maintenance and new features.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (BuildConfig.DONATION_URL.isNotBlank()) {
                    Button(
                        onClick = {
                            openUrl(context, BuildConfig.DONATION_URL)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Donate via Liberapay")
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Not now")
                }
            }
        }
    }
}

fun openProListing(context: Context) {
    val market =
        Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=${BuildConfig.PLAY_PRO_PACKAGE}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(market)
    } catch (_: ActivityNotFoundException) {
        openUrl(
            context,
            "https://play.google.com/store/apps/details?id=${BuildConfig.PLAY_PRO_PACKAGE}",
        )
    }
}

fun openUrl(
    context: Context,
    url: String,
) {
    if (url.isBlank()) return
    val intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // No browser or store app is available.
    }
}
