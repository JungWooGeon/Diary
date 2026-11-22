package com.pass.diary.core.billing

import android.content.Context
import android.util.Log
import com.pass.diary.core.sync.DriveAuth
import com.pass.diary.core.sync.SyncLocalStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class LinkAction { NONE, LINK, CHECK }

/** What 마이/프리미엄 says under "프리미엄 사용 중". LOCAL_ONLY = the server turned this purchase down (400): no retry. */
enum class LinkState { SIGNED_OUT, PENDING, LINKED, ELSEWHERE, LOCAL_ONLY }

data class LinkPlan(val action: LinkAction, val state: LinkState, val dropServerPremium: Boolean = false)

/**
 * docs/v2/SERVER.md "Android". [signedIn] = Drive connected and not opted out by 계정 연동 해제; [linked] / [elsewhere] /
 * [rejected] = the last `/v1/entitlement/play` answer (2xx / 409 / other 4xx) for this account and purchase token.
 * Only network errors, 5xx and 401 are retried. Local Play ownership is never touched here.
 */
fun linkPlan(
    signedIn: Boolean, localOwned: Boolean, serverPremium: Boolean, linked: Boolean, elsewhere: Boolean = false, rejected: Boolean = false,
): LinkPlan = when {
    !signedIn -> LinkPlan(LinkAction.NONE, LinkState.SIGNED_OUT, dropServerPremium = serverPremium)
    localOwned -> LinkPlan(
        if (linked || elsewhere || rejected) LinkAction.NONE else LinkAction.LINK,
        when { linked -> LinkState.LINKED; elsewhere -> LinkState.ELSEWHERE; rejected -> LinkState.LOCAL_ONLY; else -> LinkState.PENDING },
    )
    else -> LinkPlan(LinkAction.CHECK, if (serverPremium) LinkState.LINKED else LinkState.PENDING)
}

/** oni-api calls (blocking: call from Dispatchers.IO). Returns the HTTP status; network errors throw IOException. */
class EntitlementApi(
    private val base: String = BuildConfig.API_BASE,
    private val http: OkHttpClient = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build(),
) {
    fun link(bearer: String, productId: String, purchaseToken: String): Int {
        val body = buildJsonObject { put("productId", productId); put("purchaseToken", purchaseToken) }.toString()
        return send(request("v1/entitlement/play", bearer).post(body.toRequestBody(JSON))).first
    }

    /** (status, premium) — premium is null unless the server answered 2xx with a boolean. */
    fun entitlement(bearer: String): Pair<Int, Boolean?> = send(request("v1/entitlement", bearer).get()).let { (code, body) ->
        code to if (code in 200..299) runCatching { Json.parseToJsonElement(body).jsonObject["premium"]?.jsonPrimitive?.booleanOrNull }.getOrNull() else null
    }

    fun deleteMe(bearer: String): Int = send(request("v1/me", bearer).delete()).first

    private fun request(path: String, bearer: String) = Request.Builder().url("${base.trimEnd('/')}/$path").header("Authorization", "Bearer $bearer")

    private fun send(b: Request.Builder): Pair<Int, String> = http.newCall(b.build()).execute().use { it.code to it.body.string() }

    private companion object { val JSON = "application/json".toMediaType() }
}

/**
 * Links the Play purchase to the Google account Drive backup uses, so the web drops ads too (SERVER.md). Everything is
 * best effort: failures are logged and retried on the next [sync]; the app never waits on or needs the server.
 */
@Singleton
class PremiumLink @Inject constructor(
    @ApplicationContext context: Context,
    private val billing: Billing,
    private val store: SyncLocalStore,
    private val auth: DriveAuth,
) {
    private val api = EntitlementApi()
    private val prefs = context.getSharedPreferences("premium_link", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val _state = MutableStateFlow(LinkState.SIGNED_OUT)
    val state: StateFlow<LinkState> = _state.asStateFlow()

    /** App start, and again whenever Play's answer changes (purchase, restore, refund). Never returns. */
    suspend fun run(): Nothing = billing.ownedToken.collect { sync() }

    /** [reconnected] = the user just connected Drive (or tapped 다시 연결): lifts a previous 계정 연동 해제. */
    suspend fun sync(reconnected: Boolean = false) = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val account = store.meta(DriveAuth.META_ACCOUNT)
                if (reconnected && account != null) prefs.edit().remove(KEY_OPT_OUT).remove(KEY_STOP).apply()
                val plan = plan(account)
                if (plan.dropServerPremium) billing.setServerPremium(false)
                _state.value = plan.state
                when (plan.action) {
                    LinkAction.NONE -> Unit
                    LinkAction.LINK -> {
                        val token = billing.ownedToken.value ?: return@runCatching // pre-2.0.1 purchase: restore fills it in
                        val code = authed { api.link(it, Billing.PRODUCT_ID, token) }
                        when {
                            code in 200..299 -> prefs.edit().putString(KEY_LINKED, "$account\t$token").remove(KEY_STOP).apply()
                            code in 400..499 && code != 401 -> prefs.edit().putString(KEY_STOP, "$account\t$token\t$code").apply()
                        }
                        if (code !in 200..299) Log.w(TAG, "link: HTTP $code")
                    }
                    LinkAction.CHECK -> {
                        var premium: Boolean? = null
                        val code = authed { t -> api.entitlement(t).also { premium = it.second }.first }
                        premium?.let(billing::setServerPremium) ?: Log.w(TAG, "entitlement: HTTP $code")
                    }
                }
                _state.value = plan(account).state
            }.onFailure { Log.w(TAG, "sync failed", it) }
        }
    }

    /** 계정 연동 해제: DELETE /v1/me. Diaries, Drive backup and the Play purchase stay. false = server not reached. */
    suspend fun unlink(): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            val account = store.meta(DriveAuth.META_ACCOUNT) ?: return@withContext false
            val ok = runCatching { authed { api.deleteMe(it) } in 200..299 }.onFailure { Log.w(TAG, "unlink failed", it) }.getOrDefault(false)
            if (ok) {
                prefs.edit().remove(KEY_LINKED).remove(KEY_STOP).putString(KEY_OPT_OUT, account).apply()
                billing.setServerPremium(false)
                _state.value = plan(account).state
            }
            ok
        }
    }

    private fun plan(account: String?): LinkPlan {
        // "account\ttoken\tcode": a 4xx answer for exactly this account + purchase; another account/purchase tries again.
        val stop = prefs.getString(KEY_STOP, null)?.split('\t')
            ?.takeIf { it.size == 3 && account != null && it[0] == account && it[1] == billing.ownedToken.value }?.get(2)
        return linkPlan(
            signedIn = account != null && prefs.getString(KEY_OPT_OUT, null) != account,
            localOwned = billing.localOwned.value,
            serverPremium = billing.serverPremium,
            linked = account != null && prefs.getString(KEY_LINKED, null) == "$account\t${billing.ownedToken.value}", // a new purchase links again
            elsewhere = stop == "409",
            rejected = stop != null && stop != "409",
        )
    }

    /** One retry with a fresh token when the server says 401 (a cached Google token can be revoked early). */
    private suspend fun authed(call: (String) -> Int): Int {
        val token = auth.token()
        val code = call(token)
        if (code != 401) return code
        auth.invalidate(token)
        return call(auth.token())
    }

    private companion object {
        const val TAG = "PremiumLink"
        const val KEY_LINKED = "linked_account"
        const val KEY_STOP = "stop"
        const val KEY_OPT_OUT = "opt_out_account"
    }
}
