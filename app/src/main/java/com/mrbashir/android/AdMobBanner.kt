package com.mrbashir.android

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/**
 * Standard 320×50 AdMob banner, wrapped in AndroidView so it works inside
 * Jetpack Compose. Placed in Scaffold's bottomBar so it never overlaps content.
 *
 * Ad unit ID is picked automatically:
 *   debug build  → Google's official test banner (policy-safe)
 *   release build → your production unit ID from AdMob console
 *
 * To get your production IDs:
 *   1. Go to https://admob.google.com → Apps → Add app → Android
 *   2. Register the package com.mrbashir.android
 *   3. Copy the App ID → add as PROD_ADMOB_APP_ID in ~/.gradle/gradle.properties
 *   4. Create a Banner ad unit → copy the Ad Unit ID → PROD_ADMOB_BANNER_ID
 */
@Composable
fun AdMobBanner(
    modifier: Modifier = Modifier,
    adUnitId: String = BuildConfig.ADMOB_BANNER_ID
) {
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                this.adUnitId = adUnitId
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}
