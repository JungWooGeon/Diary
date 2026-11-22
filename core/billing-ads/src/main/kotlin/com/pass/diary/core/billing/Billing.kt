package com.pass.diary.core.billing

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/** Buying / restoring the one product. */
interface PurchaseController {
    /** Localized price ("₩3,300"); null until Play answers or when the product isn't set up. */
    val price: StateFlow<String?>
    /** Opens the Play purchase sheet. false = product not available (not set up in Play Console / offline). */
    fun purchase(activity: Activity): Boolean
    /** "구매 복원": re-reads owned purchases. [onResult] runs once (main thread not guaranteed). */
    fun restore(onResult: (RestoreResult) -> Unit = {})
}

enum class RestoreResult { RESTORED, NOTHING, FAILED }

/** What entitlement.json on Drive says (ARCHITECTURE §2.2 / §7). Null fields until a purchase is seen. */
data class Entitlement(val premium: Boolean, val orderId: String?, val purchasedAt: Long?, val updatedAt: Long)

/**
 * One-time "프리미엄" (Play Console in-app product [PRODUCT_ID], ₩3,300). Play owns the truth: purchases follow the
 * Google account, so a new phone restores automatically on the first [start]. The last answer is cached so ads stay
 * off offline and on cold start before Play replies.
 */
@Singleton
class Billing @Inject constructor(@ApplicationContext private val context: Context) : PremiumState, PurchaseController {
    private val prefs = context.getSharedPreferences("billing", Context.MODE_PRIVATE)
    /** Debug builds only, for screenshots/QA without a Play account: `debug_force` in shared_prefs/billing.xml. */
    private val forced = BuildConfig.DEBUG && prefs.getBoolean("debug_force", false)
    /** Play's own answer on this phone. Always wins: a local owner is never downgraded by the server. */
    private val _local = MutableStateFlow(prefs.getBoolean(KEY_PREMIUM, false))
    val localOwned: StateFlow<Boolean> = _local.asStateFlow()
    /** The owned purchase's token (null = not owned, or owned before 2.0.1 stored it: the next restore fills it in). */
    private val _token = MutableStateFlow(if (_local.value) prefs.getString(KEY_TOKEN, null) else null)
    val ownedToken: StateFlow<String?> = _token.asStateFlow()
    /** oni-api `/v1/entitlement` for the signed-in Google account (cached; see [PremiumLink]). */
    private val _server = MutableStateFlow(prefs.getBoolean(KEY_SERVER, false))
    val serverPremium: Boolean get() = _server.value
    private val _premium = MutableStateFlow(forced || _local.value || _server.value)
    override val isPremium: StateFlow<Boolean> = _premium.asStateFlow()

    private val _price = MutableStateFlow<String?>(null)
    override val price: StateFlow<String?> = _price.asStateFlow()
    private var product: ProductDetails? = null

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener { result, purchases -> if (result.responseCode == BillingClient.BillingResponseCode.OK) purchases?.forEach(::handle) }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    val entitlement: Entitlement
        get() = Entitlement(
            _local.value, prefs.getString(KEY_ORDER, null), prefs.getLong(KEY_AT, 0).takeIf { it > 0 }, prefs.getLong(KEY_UPDATED, 0),
        )

    /** Connects, loads the price and restores an existing purchase (ARCHITECTURE §7: on every app open). Safe to repeat. */
    fun start() = connect {
        val query = QueryProductDetailsParams.newBuilder().setProductList(listOf(
            QueryProductDetailsParams.Product.newBuilder().setProductId(PRODUCT_ID).setProductType(BillingClient.ProductType.INAPP).build(),
        )).build()
        client.queryProductDetailsAsync(query) { _, result ->
            product = result.productDetailsList.firstOrNull()
            _price.value = product?.oneTimePurchaseOfferDetails?.formattedPrice
        }
        restore()
    }

    private fun connect(then: () -> Unit) {
        if (client.isReady) return then()
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) then() else Log.w(TAG, "setup: ${result.debugMessage}")
            }
            override fun onBillingServiceDisconnected() = Unit
        })
    }

    override fun restore(onResult: (RestoreResult) -> Unit) {
        val once = OnceResult(onResult)
        // Without Play (no account / offline) the connection never answers: report FAILED instead of waiting forever.
        Handler(Looper.getMainLooper()).postDelayed({ once(RestoreResult.FAILED) }, RESTORE_TIMEOUT_MS)
        connect {
            client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { result, purchases ->
                val ok = result.responseCode == BillingClient.BillingResponseCode.OK
                val owned = purchases.firstOrNull { PRODUCT_ID in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED }
                if (ok) {
                    if (owned == null) setPremium(false, null) else handle(owned)
                } else Log.w(TAG, "restore: ${result.responseCode} ${result.debugMessage}")
                once(restoreResult(ok, owned != null))
            }
        }
    }

    override fun purchase(activity: Activity): Boolean {
        val details = product ?: return false.also { start() }
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).build(),
        )).build()
        return client.launchBillingFlow(activity, params).responseCode == BillingClient.BillingResponseCode.OK
    }

    private fun handle(p: Purchase) {
        if (PRODUCT_ID !in p.products || p.purchaseState != Purchase.PurchaseState.PURCHASED) return
        setPremium(true, p)
        // Unacknowledged purchases are refunded by Play after 3 days.
        if (!p.isAcknowledged) client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()) {
            Log.i(TAG, "acknowledge: ${it.responseCode}")
        }
    }

    private fun setPremium(v: Boolean, p: Purchase?) {
        val changed = _local.value != v || (p != null && prefs.getString(KEY_ORDER, null) != p.orderId)
        if (prefs.getString(KEY_TOKEN, null) != p?.purchaseToken) prefs.edit().putString(KEY_TOKEN, p?.purchaseToken).apply()
        _local.value = v
        _token.value = if (v) p?.purchaseToken else null
        publish()
        if (!changed) return
        prefs.edit().putBoolean(KEY_PREMIUM, v).putString(KEY_ORDER, p?.orderId).putLong(KEY_AT, p?.purchaseTime ?: 0)
            .putLong(KEY_UPDATED, System.currentTimeMillis()).apply()
    }

    fun setServerPremium(v: Boolean) {
        if (_server.value == v) return
        _server.value = v
        prefs.edit().putBoolean(KEY_SERVER, v).apply()
        publish()
    }

    private fun publish() { _premium.value = forced || _local.value || _server.value }

    companion object {
        /** Create in Play Console → 수익 창출 → 인앱 상품 (one-time, ₩3,300). */
        const val PRODUCT_ID = "premium"
        private const val TAG = "Billing"
        private const val KEY_PREMIUM = "premium"
        private const val KEY_ORDER = "order_id"
        private const val KEY_AT = "purchased_at"
        private const val KEY_UPDATED = "updated_at"
        private const val KEY_TOKEN = "purchase_token"
        private const val KEY_SERVER = "server_premium"
        private const val RESTORE_TIMEOUT_MS = 6_000L
    }
}

internal fun restoreResult(ok: Boolean, owned: Boolean) = when {
    !ok -> RestoreResult.FAILED
    owned -> RestoreResult.RESTORED
    else -> RestoreResult.NOTHING
}

/** Delivers only the first result (Play's answer or the timeout, whichever comes first). */
internal class OnceResult(private val onResult: (RestoreResult) -> Unit) : (RestoreResult) -> Unit {
    private val done = AtomicBoolean(false)
    override fun invoke(r: RestoreResult) { if (done.compareAndSet(false, true)) onResult(r) }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BillingModule {
    @Binds abstract fun premium(b: Billing): PremiumState
    @Binds abstract fun purchases(b: Billing): PurchaseController
}
