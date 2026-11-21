package com.pass.diary.core.billing

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.pass.diary.core.designsystem.Od
import com.pass.diary.core.designsystem.OdPalette

/** True when a slot may render here: allowlisted screen, not premium, SDK ready. */
@Composable
private fun canShowAd(): AdManager? {
    if (!LocalAdsAllowed.current) return null
    val ads = LocalAdManager.current ?: return null
    val premium by ads.billing.isPremium.collectAsState()
    return ads.takeIf { !premium && it.ready }
}

/**
 * 320×50 banner in the 시안 slot (`ad` background, 14dp corners, 56dp tall). Takes no space until an ad loads, and
 * none at all for premium (README §1.4: 빈칸 남기지 않기).
 */
@Composable
fun AdBanner(modifier: Modifier = Modifier) {
    val ads = canShowAd() ?: return
    var loaded by remember { mutableStateOf(false) }
    val c = Od.c
    val context = LocalContext.current
    val view = remember {
        AdView(context).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = BuildConfig.BANNER_AD_UNIT_ID
            adListener = object : AdListener() {
                override fun onAdLoaded() { loaded = true }
                override fun onAdFailedToLoad(e: LoadAdError) { Log.w("Ads", "banner: ${e.message}") }
            }
            loadAd(AdRequest.Builder().build())
        }
    }
    DisposableEffect(ads) { onDispose { view.destroy() } }
    Box(
        (if (loaded) modifier.fillMaxWidth().height(56.dp) else Modifier.height(0.dp)).clip(RoundedCornerShape(14.dp)).background(c.ad),
        contentAlignment = Alignment.Center,
    ) { AndroidView({ view }) }
}

/** One in-feed native card styled like the timeline cards, labelled "광고". Nothing until it loads. */
@Composable
fun AdNative(modifier: Modifier = Modifier) {
    canShowAd() ?: return
    val context = LocalContext.current
    var ad by remember { mutableStateOf<NativeAd?>(null) }
    DisposableEffect(Unit) {
        var disposed = false
        AdLoader.Builder(context, BuildConfig.NATIVE_AD_UNIT_ID)
            .forNativeAd { if (disposed) it.destroy() else ad = it }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(e: LoadAdError) { Log.w("Ads", "native: ${e.message}") }
            })
            .withNativeAdOptions(NativeAdOptions.Builder().setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT).build())
            .build()
            .loadAd(AdRequest.Builder().build())
        onDispose { disposed = true; ad?.destroy() }
    }
    val loaded = ad ?: return
    val p = Od.c
    AndroidView({ buildCard(it, p) }, modifier.fillMaxWidth(), update = { bind(it, loaded) })
}

private fun Context.dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

private fun rounded(ctx: Context, color: Color, radius: Int, stroke: Color? = null) = GradientDrawable().apply {
    setColor(color.toArgb()); cornerRadius = ctx.dp(radius).toFloat()
    if (stroke != null) setStroke(ctx.dp(1), stroke.toArgb())
}

/** README §5 ad slot: `ad` background, 20dp corners, "광고" outlined tag; icon + headline, body, 16:9 media, CTA. */
private fun buildCard(ctx: Context, c: OdPalette): NativeAdView {
    val view = NativeAdView(ctx)
    val root = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(ctx, c.ad, 20)
        setPadding(ctx.dp(14), ctx.dp(14), ctx.dp(14), ctx.dp(14))
    }
    val header = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    val icon = ImageView(ctx).apply {
        clipToOutline = true; background = rounded(ctx, c.surface2, 10)
        layoutParams = LinearLayout.LayoutParams(ctx.dp(44), ctx.dp(44)).apply { marginEnd = ctx.dp(12) }
    }
    val titles = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f) }
    val tag = TextView(ctx).apply {
        text = "광고"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f); setTypeface(typeface, Typeface.BOLD); setTextColor(c.ink2.toArgb())
        background = rounded(ctx, Color.Transparent, 4, c.ink3); setPadding(ctx.dp(4), 0, ctx.dp(4), 0)
        layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { bottomMargin = ctx.dp(3) }
    }
    val headline = TextView(ctx).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); setTypeface(typeface, Typeface.BOLD); setTextColor(c.ink.toArgb())
        maxLines = 1; ellipsize = TextUtils.TruncateAt.END
    }
    val advertiser = TextView(ctx).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); setTextColor(c.ink2.toArgb()); maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
    titles.addView(tag); titles.addView(headline); titles.addView(advertiser)
    header.addView(icon); header.addView(titles)
    val body = TextView(ctx).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setTextColor(c.ink2.toArgb()); maxLines = 2; ellipsize = TextUtils.TruncateAt.END
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = ctx.dp(10) }
    }
    val media = AspectMediaView(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = ctx.dp(10) }
        setImageScaleType(ImageView.ScaleType.CENTER_CROP)
    }
    val cta = Button(ctx).apply {
        isAllCaps = false; setTextColor(c.onPrimary.toArgb()); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setTypeface(typeface, Typeface.BOLD); background = rounded(ctx, c.primary, 14); stateListAnimator = null
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, ctx.dp(44)).apply { topMargin = ctx.dp(12) }
    }
    root.addView(header); root.addView(body); root.addView(media); root.addView(cta)
    view.addView(root)
    view.iconView = icon; view.headlineView = headline; view.advertiserView = advertiser
    view.bodyView = body; view.mediaView = media; view.callToActionView = cta
    return view
}

private fun bind(view: NativeAdView, ad: NativeAd) {
    fun TextView.show(s: String?) { text = s; visibility = if (s.isNullOrBlank()) View.GONE else View.VISIBLE }
    (view.headlineView as TextView).text = ad.headline
    (view.bodyView as TextView).show(ad.body)
    (view.advertiserView as TextView).show(ad.advertiser)
    (view.callToActionView as Button).show(ad.callToAction)
    (view.iconView as ImageView).apply { setImageDrawable(ad.icon?.drawable); visibility = if (ad.icon == null) View.GONE else View.VISIBLE }
    view.mediaView?.mediaContent = ad.mediaContent
    view.setNativeAd(ad)
}

/** MediaView locked to 16:9 so tall creatives can't take over the timeline. */
private class AspectMediaView(ctx: Context) : MediaView(ctx) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = View.MeasureSpec.getSize(widthMeasureSpec)
        super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(w * 9 / 16, View.MeasureSpec.EXACTLY))
    }
}
