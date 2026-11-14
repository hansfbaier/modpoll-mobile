package io.github.hansfbaier.modpoll.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import io.github.hansfbaier.modpoll.BuildConfig

/**
 * Play-free banner. Banner only: this variant intentionally has no interstitial
 * or rewarded-video ads.
 */
@Composable
fun BannerAdSlot(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val configuration = LocalConfiguration.current
    val widthDp = configuration.screenWidthDp.coerceAtLeast(320)

    var loaded by remember { mutableStateOf(false) }
    val adView =
        remember(context, widthDp) {
            AdView(context).apply {
                adUnitId = BuildConfig.ADMOB_BANNER_UNIT_ID
                val adSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp)
                setAdSize(adSize)
            }
        }

    DisposableEffect(lifecycleOwner, adView) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> adView.resume()
                    Lifecycle.Event.ON_PAUSE -> adView.pause()
                    Lifecycle.Event.ON_DESTROY -> adView.destroy()
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            adView.destroy()
        }
    }

    LaunchedEffect(adView, activity) {
        if (activity == null) return@LaunchedEffect

        adView.adListener =
            object : AdListener() {
                override fun onAdLoaded() {
                    loaded = true
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loaded = false
                }
            }

        // Test ads in debug builds do not need the production consent form.
        // Release builds use Google's UMP flow before requesting any ad.
        if (BuildConfig.DEBUG) {
            MobileAds.initialize(context) {
                adView.loadAd(AdRequest.Builder().build())
            }
            return@LaunchedEffect
        }

        val consentInformation: ConsentInformation =
            UserMessagingPlatform.getConsentInformation(context)
        val params = ConsentRequestParameters.Builder().build()
        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError == null && consentInformation.canRequestAds()) {
                        MobileAds.initialize(context) {
                            adView.loadAd(AdRequest.Builder().build())
                        }
                    }
                }
            },
            {
                if (consentInformation.canRequestAds()) {
                    MobileAds.initialize(context) {
                        adView.loadAd(AdRequest.Builder().build())
                    }
                }
            },
        )
    }

    if (loaded) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { adView },
                modifier = Modifier.fillMaxWidth().height(50.dp),
            )
            Text(
                "AD",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.TopStart),
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
